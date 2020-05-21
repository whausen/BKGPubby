package de.fuberlin.wiwiss.pubby.sources;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.riot.WebContent;

import org.apache.jena.query.QueryException;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.sparql.engine.http.HttpQuery;
import org.apache.jena.sparql.engine.http.QueryEngineHTTP;

import de.fuberlin.wiwiss.pubby.ConfigurationException;
import de.fuberlin.wiwiss.pubby.VocabularyStore.CachedPropertyCollection;

/**
 * A data source backed by a SPARQL endpoint accessed through
 * the SPARQL protocol.
 */
public class RemoteSPARQLDataSource implements DataSource {
	private final String endpointURL;
	private final String defaultGraphURI;
	private final boolean supportsSPARQL11;

	private final Set<String> resourceQueries;
	private final Set<String> propertyQueries;
	private final Set<String> inversePropertyQueries;
	private final Set<String> anonPropertyQueries;
	private final Set<String> anonInversePropertyQueries;
	
	private final CachedPropertyCollection highIndegreeProperties;
	private final CachedPropertyCollection highOutdegreeProperties;
	
	private String previousDescribeQuery;
	private String contentType = null;
	private final Set<String[]> queryParamsSelect = new HashSet<String[]>();
	private final Set<String[]> queryParamsGraph = new HashSet<String[]>();
	
	public RemoteSPARQLDataSource(String endpointURL, String defaultGraphURI) {
		this(endpointURL, defaultGraphURI, false, null, null, null, null, null, null, null);
	}
	
	public RemoteSPARQLDataSource(String endpointURL, String defaultGraphURI,
			boolean supportsSPARQL11,
			Set<String> resourceQueries, 
			Set<String> propertyQueries, Set<String> inversePropertyQueries,
			Set<String> anonPropertyQueries, Set<String> anonInversePropertyQueries,
			CachedPropertyCollection highIndegreeProperties, CachedPropertyCollection highOutdegreeProperties) {
		this.endpointURL = endpointURL;
		this.defaultGraphURI = defaultGraphURI;
		this.supportsSPARQL11 = supportsSPARQL11;
		if (resourceQueries == null || resourceQueries.isEmpty()) {
			resourceQueries = supportsSPARQL11 ?
				new HashSet<String>(Arrays.asList(new String[]{
					"CONSTRUCT {?__this__ ?p ?o} WHERE {?__this__ ?p ?o. FILTER (?p NOT IN ?__high_outdegree_properties__)}",
					"CONSTRUCT {?s ?p ?__this__} WHERE {?s ?p ?__this__. FILTER (?p NOT IN ?__high_indegree_properties__)}"
				})) :
				Collections.singleton("DESCRIBE ?__this__");
		}
		if (propertyQueries == null || propertyQueries.isEmpty()) {
			propertyQueries = Collections.singleton(
					"CONSTRUCT {?__this__ ?__property__ ?x} WHERE {?__this__ ?__property__ ?x. FILTER (!isBlank(?x))}");
		}
		if (inversePropertyQueries == null || inversePropertyQueries.isEmpty()) {
			inversePropertyQueries = Collections.singleton(
					"CONSTRUCT {?x ?__property__ ?__this__} WHERE {?x ?__property__ ?__this__. FILTER (!isBlank(?x))}");
		}
		if (anonPropertyQueries == null || anonPropertyQueries.isEmpty()) {
			anonPropertyQueries = Collections.singleton(
					"DESCRIBE ?x WHERE {?__this__ ?__property__ ?x. FILTER (isBlank(?x))}");
		}
		if (anonInversePropertyQueries == null || anonInversePropertyQueries.isEmpty()) {
			anonInversePropertyQueries = Collections.singleton(
					"DESCRIBE ?x WHERE {?x ?__property__ ?__this__. FILTER (isBlank(?x))}");
		}
		this.resourceQueries = resourceQueries;
		this.propertyQueries = propertyQueries;
		this.inversePropertyQueries = inversePropertyQueries;
		this.anonPropertyQueries = anonPropertyQueries;
		this.anonInversePropertyQueries = anonInversePropertyQueries;
		
		this.highIndegreeProperties = highIndegreeProperties;
		this.highOutdegreeProperties = highOutdegreeProperties;
	}
	
	/**
	 * Sets the content type to ask for in graph requests (CONSTRUCT and
	 * DESCRIBE) to the remote SPARQL endpoint.
	 */
	public void setGraphContentType(String mediaType) {
		this.contentType = mediaType;
	}

	public void addGraphQueryParam(String param) {
		queryParamsGraph.add(parseQueryParam(param));
	}
	
	public void addSelectQueryParam(String param) {
		queryParamsSelect.add(parseQueryParam(param));
	}
	
	private String[] parseQueryParam(String param) {
		Matcher match = queryParamPattern.matcher(param);
		if (!match.matches()) {
			throw new ConfigurationException("Query parameter \"" + param + 
					"\" is not in \"param=value\" form");
		}
		return new String[]{match.group(1), match.group(2)};
	}
	
	private Pattern queryParamPattern = Pattern.compile("(.*?)=(.*)");
			
	@Override
	public boolean canDescribe(String absoluteIRI) {
		return true;
	}

	@Override
	public Model describeResource(String resourceURI) {
		// Loop over resource description queries, join results in a single model.
		// Process each query to replace place-holders of the given resource.
		Model model = ModelFactory.createDefaultModel();
		for (String query: resourceQueries) {
			Model result = execQueryGraph(preProcessQuery(query, resourceURI));
			model.add(result);
			model.setNsPrefixes(result);
		}
		return model;
	}

	@Override
	public Map<Property, Integer> getHighIndegreeProperties(String resourceURI) {
		return getHighDegreeProperties(
				"SELECT ?p (COUNT(?s) AS ?count) " +
				"WHERE { " +
				"  ?s ?p ?__this__. " +
				"  FILTER (?p IN ?__high_indegree_properties__)" +
				"}" +
				"GROUP BY ?p",
				resourceURI);
	}

	@Override
	public Map<Property, Integer> getHighOutdegreeProperties(String resourceURI) {
		return getHighDegreeProperties(
				"SELECT ?p (COUNT(?o) AS ?count) " +
				"WHERE { " +
				"  ?__this__ ?p ?o. " +
				"  FILTER (?p IN ?__high_outdegree_properties__)" +
				"}" +
				"GROUP BY ?p", 
				resourceURI);
	}

	private Map<Property, Integer> getHighDegreeProperties(String query, 
			String resourceURI) {
		if (!supportsSPARQL11) return null;
		query = preProcessQuery(query, resourceURI);
		ResultSet rs = execQuerySelect(query);
		Map<Property, Integer> results = new HashMap<Property, Integer>();
		while (rs.hasNext()) {
			QuerySolution solution = rs.next();
			if (!solution.contains("p") || !solution.contains("count")) continue;
			Resource p = solution.get("p").asResource();
			int count = solution.get("count").asLiteral().getInt();
			results.put(ResourceFactory.createProperty(p.getURI()), count);
		}
		return results;
	}

	@Override
	public Model listPropertyValues(String resourceURI, Property property, 
			boolean isInverse) {
		// Loop over the queries, join results in a single model.
		// Process each query to replace place-holders of the given resource and property.
		List<String> queries = new ArrayList<String>();
		queries.addAll(isInverse ? inversePropertyQueries : propertyQueries);
		queries.addAll(isInverse ? anonInversePropertyQueries : anonPropertyQueries);
		Model model = ModelFactory.createDefaultModel();
		for (String query: queries) {
			String preprocessed = preProcessQuery(query, resourceURI, property);
			Model result = execQueryGraph(preprocessed);
			model.add(result);
			model.setNsPrefixes(result);
		}
		return model;
	}
	
	@Override
	public List<Resource> getIndex() {
		List<Resource> result = new ArrayList<Resource>();
		ResultSet rs = execQuerySelect(
				"SELECT DISTINCT ?s { " +
				"?s ?p ?o " +
				"FILTER (isURI(?s)) " +
				"} LIMIT " + DataSource.MAX_INDEX_SIZE);
		while (rs.hasNext()) {
			result.add(rs.next().getResource("s"));
		}
		if (result.size() < DataSource.MAX_INDEX_SIZE) {
			rs = execQuerySelect(
					"SELECT DISTINCT ?o { " +
					"?s ?p ?o " +
					"FILTER (isURI(?o)) " +
					"} LIMIT " + (DataSource.MAX_INDEX_SIZE - result.size()));
			while (rs.hasNext()) {
				result.add(rs.next().getResource("o"));
			}
		}
		return result;
	}

	public String getPreviousDescribeQuery() {
		return previousDescribeQuery;
	}
	
	private Model execQueryGraph(String query) {
		Model model = ModelFactory.createDefaultModel();
		previousDescribeQuery = query;

		// Since we don't know the exact query type (e.g. DESCRIBE or CONSTRUCT),
		// and com.hp.hpl.jena.query.QueryFactory could throw exceptions on
		// vendor-specific sections of the query, we use the lower-level
		// com.hp.hpl.jena.sparql.engine.http.HttpQuery to execute the query and
		// read the results into model.
		
		HttpQuery httpQuery = new HttpQuery(endpointURL);
		httpQuery.addParam("query", query);
		if (defaultGraphURI != null) {
			httpQuery.addParam("default-graph-uri", defaultGraphURI);
		}
		for (String[] param: queryParamsGraph) {
			httpQuery.addParam(param[0], param[1]);
		}
		
		// The rest is more or less a copy of QueryEngineHTTP.execModel()
		httpQuery.setAccept(contentType);
		InputStream in = httpQuery.exec();

		// Don't assume the endpoint actually gives back the content type we
		// asked for
		String actualContentType = httpQuery.getContentType();

		// If the server fails to return a Content-Type then we will assume
		// the server returned the type we asked for
		if (actualContentType == null || actualContentType.equals("")) {
			actualContentType = contentType;
		}

		// Try to select language appropriately here based on the model content
		// type
		Lang lang = WebContent.contentTypeToLangResultSet(actualContentType);
		if (!RDFLanguages.isTriples(lang))
			throw new QueryException("Endpoint <" + endpointURL + 
					"> returned Content Type: " + actualContentType
					+ " which is not a supported RDF graph syntax");
		RDFDataMgr.read(model, in, lang);

		// Skip prefixes ns1, ns2, etc, which are usually
		// auto-assigned by the endpoint and do more harm than good
		for (String prefix: model.getNsPrefixMap().keySet()) {
            if (prefix.matches("^ns[0-9]+$")) {
            	model.removeNsPrefix(prefix);
            }
        }
		
		return model;
	}
	
	private ResultSet execQuerySelect(String query) {
		QueryEngineHTTP endpoint = new QueryEngineHTTP(endpointURL, query);
		if (defaultGraphURI != null) {
			endpoint.setDefaultGraphURIs(Collections.singletonList(defaultGraphURI));
		}
		for (String[] param: queryParamsSelect) {
			endpoint.addParam(param[0], param[1]);
		}
		return endpoint.execSelect();
	}
	
	private String preProcessQuery(String query, String resourceURI) {
		return preProcessQuery(query, resourceURI, null);
	}
	
	private String preProcessQuery(String query, String resourceURI, Property property) {
		String result = replaceString(query, "?__this__", "<" + resourceURI + ">");
		if (property != null) {
			result = replaceString(result, "?__property__", "<" + property.getURI() + ">");
		}
		result = replaceString(result, "?__high_indegree_properties__", 
				toSPARQLArgumentList(highIndegreeProperties == null ? null : highIndegreeProperties.get()));
		result = replaceString(result, "?__high_outdegree_properties__", 
				toSPARQLArgumentList(highOutdegreeProperties == null ? null : highOutdegreeProperties.get()));
		return result;
	}
	
	private String replaceString(String text, String searchString, String replacement) {
		int start = 0;
		int end = text.indexOf(searchString, start);
		if (end == -1) {
			return text;
		}

		int replacementLength = searchString.length();
		StringBuffer buf = new StringBuffer();
		while (end != -1) {
			buf.append(text.substring(start, end)).append(replacement);
			start = end + replacementLength;
			end = text.indexOf(searchString, start);
		}
		buf.append(text.substring(start));
		return buf.toString();
	}
	
	private String toSPARQLArgumentList(Collection<? extends RDFNode> values) {
		if (values == null) return "()";
		StringBuilder result = new StringBuilder();
		result.append('(');
		boolean isFirst = true;
		for (RDFNode term: values) {
			if (!isFirst) {
				result.append(", ");
			}
			if (term.isURIResource()) {
				result.append('<');
				result.append(term.asResource().getURI());
				result.append('>');
			} else {
				throw new IllegalArgumentException(
						"toSPARQLArgumentList is only implemented for URIs; " + 
						"called with term " + term);
			}
			isFirst = false;
		}
		result.append(')');
		return result.toString();
	}
}

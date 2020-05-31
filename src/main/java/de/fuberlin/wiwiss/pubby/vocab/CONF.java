package de.fuberlin.wiwiss.pubby.vocab;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;

public class CONF {

	public static final String NS = "http://richard.cyganiak.de/2007/pubby/config.rdf#";
	
	private static final Model m = ModelFactory.createDefaultModel();
	
	public static final Resource Configuration = m.createResource(NS + "Configuration"); 

	// Configuration properties
	public static final Property projectName = m.createProperty(NS + "projectName"); 
	public static final Property projectHomepage = m.createProperty(NS + "projectHomepage");
	public static final Property webBase = m.createProperty(NS + "webBase"); 
	public static final Property usePrefixesFrom = m.createProperty(NS + "usePrefixesFrom");
	public static final Property labelProperty = m.createProperty(NS + "labelProperty");
	public static final Property commentProperty = m.createProperty(NS + "commentProperty");
	public static final Property imageProperty = m.createProperty(NS + "imageProperty");
	public static final Property defaultLanguage = m.createProperty(NS + "defaultLanguage");
	public static final Property indexResource = m.createProperty(NS + "indexResource");
	public static final Property dataset = m.createProperty(NS + "dataset");
	public static final Property showLabels = m.createProperty(NS + "showLabels");
	public static final Property loadVocabulary = m.createProperty(NS + "loadVocabulary");

	// Dataset subclasses
	public static final Resource AnnotationProvider = m.createResource(NS + "AnnotationProvider");
	
	// Dataset properties
	public static final Property datasetBase = m.createProperty(NS + "datasetBase");
	public static final Property datasetURIPattern = m.createProperty(NS + "datasetURIPattern");
	public static final Property webResourcePrefix = m.createProperty(NS + "webResourcePrefix"); 
	public static final Property supportsIRIs = m.createProperty(NS + "supportsIRIs");
	public static final Property addSameAsStatements = m.createProperty(NS + "addSameAsStatements");
	public static final Property sparqlEndpoint = m.createProperty(NS + "sparqlEndpoint"); 
	public static final Property sparqlDefaultGraph = m.createProperty(NS + "sparqlDefaultGraph");
	public static final Property supportsSPARQL11 = m.createProperty(NS + "supportsSPARQL11");
	public static final Property loadRDF = m.createProperty(NS + "loadRDF");
	public static final Property rdfDocumentMetadata = m.createProperty(NS + "rdfDocumentMetadata");
	public static final Property metadataTemplate = m.createProperty(NS + "metadataTemplate");
	public static final Property contentType = m.createProperty(NS + "contentType");
	public static final Property resourceDescriptionQuery = m.createProperty(NS + "resourceDescriptionQuery");
	public static final Property propertyListQuery = m.createProperty(NS + "propertyListQuery");
	public static final Property inversePropertyListQuery = m.createProperty(NS + "inversePropertyListQuery");
	public static final Property anonymousPropertyDescriptionQuery = m.createProperty(NS + "anonymousPropertyDescriptionQuery");
	public static final Property anonymousInversePropertyDescriptionQuery = m.createProperty(NS
			+ "anonymousInversePropertyDescriptionQuery");
	public static final Property browsableNamespace = m.createProperty(NS + "browsableNamespace");
	public static final Property queryParamSelect = m.createProperty(NS + "queryParamSelect");
	public static final Property queryParamGraph = m.createProperty(NS + "queryParamGraph");
	public static final Property wdImage=m.createProperty("http://www.wikidata.org/prop/direct/P18");
	
	// Terms to annotate vocabulary
	public static final Property weight = m.createProperty(NS + "weight");
	public static final Property pluralLabel = m.createProperty(NS + "pluralLabel");
	public static final Resource HighOutdregreeProperty = m.createResource(NS + "HighOutdegreeProperty");
	public static final Resource HighIndregreeProperty = m.createResource(NS + "HighIndegreeProperty");
}

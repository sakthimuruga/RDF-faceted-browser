package org.deri.rdf.browser.sparql;

import java.util.Collection;
import java.util.Set;

import org.deri.rdf.browser.model.Facet;
import org.deri.rdf.browser.model.MainFilter;
import org.deri.rdf.browser.model.RdfDecoratedValue;
import org.deri.rdf.browser.util.SparqlUtil;

public class NaiveFederatedSparqlEngine{

	public String getFocusItemsSparql(String[] endpoints, MainFilter mainFilter, Collection<Facet> facets, int start, int length){
		StringBuilder builder = new StringBuilder("SELECT DISTINCT ?");
		builder.append(mainFilter.getVarname()).append(" WHERE{");
		builder.append(getWhereClause(endpoints, mainFilter, facets));
		builder.append("} ORDER BY ?").append(mainFilter.getVarname()).append(" OFFSET ").append(start).append(" LIMIT ").append(length);
		return builder.toString();
	}
	
	public String countFocusItemsSparql(String[] endpoints, MainFilter mainFilter, Collection<Facet> facets) {
		StringBuilder builder = new StringBuilder("SELECT (COUNT(DISTINCT ?");
		builder.append(mainFilter.getVarname()).append(") AS ?count) WHERE{");
		builder.append(getWhereClause(endpoints, mainFilter, facets));
		builder.append("}");
		return builder.toString();
	}
	
	public String getFacetValuesSparql(String[] endpoints, MainFilter mainFilter, Collection<Facet> facets, Facet focusFacet) {
		if(!facets.contains(focusFacet)){
			throw new RuntimeException("Facets set should contain the focus facet");
		}
		StringBuilder builder = new StringBuilder();
		builder.append("SELECT ?").append(focusFacet.getVarname()).append(" (COUNT(DISTINCT ?").append(mainFilter.getVarname()).append(") AS ?count) WHERE{");
		appendMainFilter(builder, endpoints, mainFilter);
		
		for(Facet f:facets){
			if(f.equals(focusFacet)){
				appendFocusFacet(builder, endpoints, mainFilter, focusFacet);
			}else{
				builder.append(getFacetFilter(endpoints, f, mainFilter.getVarname()));
			}
		}
		builder.append("} GROUP BY ?").append(focusFacet.getVarname());
		return builder.toString();
	}
	
	public String countItemsMissingFacetSparql(String[] endpoints,MainFilter mainFilter, Collection<Facet> facets, Facet focusFacet) {
		if(!facets.contains(focusFacet)){
			throw new RuntimeException("Facets set should contain the focus facet");
		}
		StringBuilder builder = new StringBuilder();
		builder.append("SELECT (COUNT(DISTINCT ?").append(mainFilter.getVarname()).append(") AS ?count) WHERE{");
		appendMainFilter(builder, endpoints, mainFilter);
		for(Facet f:facets){
			if(f.equals(focusFacet)){
				builder.append("OPTIONAL {");
				appendFocusFacet(builder, endpoints, mainFilter, focusFacet);
				builder.append("} FILTER(!bound(?").append(f.getVarname()).append(")) .");
			}else{
				builder.append(getFacetFilter(endpoints,f, mainFilter.getVarname()));
			}
		}
		builder.append("} GROUP BY ?").append(focusFacet.getVarname());
		return builder.toString();
	}
		
	public String resourcesDetailsSparql(String[] endpoints, Set<String> uris, Set<String> properties) {
		StringBuilder builder = new StringBuilder();
		builder.append(RESOURCES_DETAILS_SPARQL_PROLOG);
		
		for(String ep:endpoints){
			builder.append("{SERVICE<").append(ep).append("> {");
			builder.append("?s ?p ?o .");
			builder.append(orFilter("p",properties));
			builder.append(orFilter("s",uris));
			builder.append("}}UNION");
		}
		SparqlUtil.getRidOfLastUnion(builder);		
		builder.append("}");
		return builder.toString();
	}
	
	private String getWhereClause(String[] endpoints, MainFilter mainFilter, Collection<Facet> facets) {
		StringBuilder builder = new StringBuilder();
		appendMainFilter(builder,endpoints,mainFilter);
		
		for(Facet f:facets){
			String filter = getFacetFilter(endpoints, f,mainFilter.getFacetsVarname());
			builder.append(filter);
		}
		return builder.toString();
	}
	
	private void appendMainFilter(StringBuilder builder, String[] endpoints,
			MainFilter mainFilter) {
		for(String ep:endpoints){
			builder.append("{").append("SERVICE <").append(ep).append(">{");
			builder.append(mainFilter.getSparqlPattern());
			builder.append("}}UNION");
		}
		SparqlUtil.getRidOfLastUnion(builder);		
	}
	
	private void appendFocusFacet(StringBuilder builder, String[] endpoints, MainFilter mainFilter, Facet focusFacet){
		for(String ep:endpoints){
			builder.append("{SERVICE <").append(ep).append(">{");
			builder.append("{?").append(mainFilter.getVarname()).append(" ").append(focusFacet.getFilter().getPattern()).append(" ?").append(focusFacet.getVarname()).append(" .}");
			builder.append("}}UNION");
		}
		//get rid of the last UNION
		SparqlUtil.getRidOfLastUnion(builder);
	}

	private String getFacetFilter(String[] endpoints, Facet f, String varname){
		if(!f.hasSelection()){
			return "";
		}
		StringBuilder filter = new StringBuilder();
		for(String ep:endpoints){
			StringBuilder subfilter = new StringBuilder();
			
			for(RdfDecoratedValue v:f.getSelections()){
				subfilter.append("{?").append(varname).append(" ").append(f.getFilter().getPattern()).append(" ").append(v.inSparqlFilter(f.getVarname())).append(" .}").append("UNION");
			}
			if(f.missingValueSelected()){
				subfilter.append("{?").append(varname).append(" ").append(f.getFilter().getPattern()).append(" ?").append(f.getVarname()).append(DISAMBIGUATION_SUFFIX).append(" .}").append("UNION");
			}
			SparqlUtil.getRidOfLastUnion(subfilter);
			subfilter.replace(0,0,"{SERVICE <"+ep+">{");
			subfilter.append("}}UNION");
			
			filter.append(subfilter);
		}
		//get rid of the last UNION
		SparqlUtil.getRidOfLastUnion(filter);

		if(f.missingValueSelected()){
			//left join
			filter.replace(0, 0, "OPTIONAL{");
			filter.append("} FILTER(!bound(?").append(f.getVarname()).append(DISAMBIGUATION_SUFFIX).append(")) .");
		}
		return filter.toString();
	}
	
	private String orFilter(String varname, Set<String> vals) {
		if(vals.isEmpty()){
			return "";
		}
		StringBuilder builder = new StringBuilder();
		builder.append("FILTER(");
		for(String val:vals){
			builder.append("?"); builder.append(varname);
			builder.append("=<"); builder.append(val); builder.append("> || ");
		}
		//get rid of the last  ||
		int lngth = builder.length();
		builder.delete(lngth-4, lngth);
		builder.append(").");
		return builder.toString();
		
	}
	
	
	private static final String DISAMBIGUATION_SUFFIX = "_v";
	private static final String RESOURCES_DETAILS_SPARQL_PROLOG = "SELECT ?s ?p ?o WHERE{" ;

}

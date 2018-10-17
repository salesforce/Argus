package com.salesforce.dva.argus.util;

import java.util.ArrayList;
import java.util.List;

import com.salesforce.dva.argus.service.metric.transform.TransformFactory.Function;

public class QueryContext {

	private Function transform = null;
    
    private QueryContext parentContext = null;
    
    private List<QueryContext> childContexts = new ArrayList<QueryContext>();
    
    private List<QueryExpression> childExpressions = new ArrayList<QueryExpression>();

 	public Function getTransformName() {
		return transform;
	}

	public void setTransformName(String transformName) {
		this.transform = Function.fromString(transformName.toUpperCase());
	}
	
	public QueryContext getParentContext() {
		return parentContext;
	}

	public void setParentContext(QueryContext parentContext) {
		this.parentContext = parentContext;
	}
	
	public List<QueryContext> getChildContexts() {
		return childContexts;
	}

	public void setChildContexts(List<QueryContext> childContexts) {
		this.childContexts = childContexts;
	}

	public List<QueryExpression> getChildExpressions() {
		return childExpressions;
	}

	public void setChildExpressions(List<QueryExpression> childExpressions) {
		this.childExpressions = childExpressions;
	}

	public List<QueryContext> getChildQueryContexts() {
		return childContexts;
	}

	public void setChildQueryContexts(List<QueryContext> childQueryContexts) {
		this.childContexts = childQueryContexts;
	}
}

package com.salesforce.dva.argus.util;

import java.util.ArrayList;
import java.util.List;

import com.salesforce.dva.argus.service.metric.transform.TransformFactory.Function;

public class QueryContext {

	private Function transform = null;
	
	private List<String> constants = null;
    
    private QueryContext parentContext = null;
    
    private List<QueryContext> childContexts = new ArrayList<QueryContext>();
    
    private List<TSDBQueryExpression> childExpressions = new ArrayList<TSDBQueryExpression>();

 	public Function getTransformName() {
		return transform;
	}

	public void setTransformName(String transformName) {
		this.transform = Function.fromString(transformName.toUpperCase());
	}

	public List<String> getConstants() {
		return constants;
	}

	public void setConstants(List<String> constants) {
		this.constants = constants;
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

	public List<TSDBQueryExpression> getChildExpressions() {
		return childExpressions;
	}

	public void setChildExpressions(List<TSDBQueryExpression> childExpressions) {
		this.childExpressions = childExpressions;
	}

	public List<QueryContext> getChildQueryContexts() {
		return childContexts;
	}

	public void setChildQueryContexts(List<QueryContext> childQueryContexts) {
		this.childContexts = childQueryContexts;
	}
}

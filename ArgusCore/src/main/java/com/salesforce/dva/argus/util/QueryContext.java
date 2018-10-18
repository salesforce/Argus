package com.salesforce.dva.argus.util;

import java.util.ArrayList;
import java.util.List;

import com.salesforce.dva.argus.service.metric.transform.TransformFactory.Function;

/*
 * This class encapsulates the parsed query expression tree. 
 * 
 * Each node in the tree corresponds to a transform function specified in the expression.
 *  
 * Moreover each node has references to its constants, child transforms, parent transform and the associated tsdb query expressions
 * 
 */
public class QueryContext {

	private Function transform = null;
	
	private List<String> constants = null;
    
    private QueryContext parentContext = null;
    
    private List<QueryContext> childContexts = new ArrayList<QueryContext>();
    
    private List<TSDBQueryExpression> childExpressions = new ArrayList<TSDBQueryExpression>();

 	public Function getTransform() {
		return transform;
	}

	public void setTransform(String transformName) {
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

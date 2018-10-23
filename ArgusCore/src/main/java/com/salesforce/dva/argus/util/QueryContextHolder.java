package com.salesforce.dva.argus.util;

/*
 * This class is just used as a wrapper class for the underlying QueryContext
 * 
 * This is useful when parsing the expression using MetricReader wherein this wrapper object can be 
 * passed by reference to various methods and all the calling methods can see updates the underlying QueryContext object.
 * 
 */
public class QueryContextHolder {

	private QueryContext currentQueryContext = new QueryContext();

	public QueryContext getCurrentQueryContext() {
		return currentQueryContext;
	}

	public void setCurrentQueryContext(QueryContext currentQuerycontext) {
		this.currentQueryContext = currentQuerycontext;
	}
	
	public void setContextToRootElement() {
		while(currentQueryContext.getParentContext()!=null) {
			currentQueryContext = currentQueryContext.getParentContext();
		}
	}
}

package com.salesforce.dva.argus.entity;

import com.salesforce.dva.argus.system.SystemAssert;

public class SchemaQuery {
	
	protected int limit; 
    protected int page;
    
    public int getLimit() {
		return limit;
	}
    
    public SchemaQuery(int limit, int page) {
    	setLimit(limit);
    	setPage(page);
    }

	public void setLimit(int limit) {
		SystemAssert.requireArgument(limit > 0, "Limit must be a positive integer.");
		this.limit = limit;
	}

	public int getPage() {
		return page;
	}

	public void setPage(int page) {
		SystemAssert.requireArgument(page > 0, "Page no. must be a positive integer.");
		this.page = page;
	}

}

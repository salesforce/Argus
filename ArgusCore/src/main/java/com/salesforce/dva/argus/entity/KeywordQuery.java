package com.salesforce.dva.argus.entity;

import java.text.MessageFormat;

public class KeywordQuery extends SchemaQuery {

	private String query;
    private boolean isNative;
    
    public KeywordQuery(String query, boolean isNative, int limit, int page) {
		super(limit, page);
		setQuery(query);
		setNative(isNative);
	}

	public String getQuery() {
		return query;
	}

	public void setQuery(String query) {
		this.query = query;
	}

	public boolean isNative() {
		return isNative;
	}

	public void setNative(boolean isNative) {
		this.isNative = isNative;
	}
	
	@Override
    public String toString() {
        return MessageFormat.format("KeywordQuery = (Query = {0}, IsNative = {1}, Limit = {2}, Page = {3})", query, isNative, limit, page);
    }
    
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + (isNative ? 1231 : 1237);
		result = prime * result + ((query == null) ? 0 : query.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		KeywordQuery other = (KeywordQuery) obj;
		if (isNative != other.isNative)
			return false;
		if (query == null) {
			if (other.query != null)
				return false;
		} else if (!query.equals(other.query))
			return false;
		return true;
	}



	public static class KeywordQueryBuilder {
		
		private String query;
		private boolean isNative;
		private int limit = 10;
		private int page = 1;
		
		public KeywordQueryBuilder() {}
		
		public KeywordQueryBuilder query(String query) {
			this.query = query;
			return this;
		}
		
		public KeywordQueryBuilder isNative(boolean isNative) {
			this.isNative = isNative;
			return this;
		}
		
		public KeywordQueryBuilder limit(int limit) {
	    	this.limit = limit;
	    	return this;
	    }
	    
	    public KeywordQueryBuilder page(int page) {
	    	this.page = page;
	    	return this;
	    }
		
		public KeywordQuery build() {
			return new KeywordQuery(query, isNative, limit, page);
		}
	}
	
}

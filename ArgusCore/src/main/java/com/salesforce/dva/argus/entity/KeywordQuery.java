package com.salesforce.dva.argus.entity;

import java.text.MessageFormat;

import com.salesforce.dva.argus.service.SchemaService.RecordType;

public class KeywordQuery extends SchemaQuery {

	private String namespace;
    private String scope;
    private String metric;
    private String tagKey;
    private String tagValue; 
	private String query;
	private RecordType type;
    
    public KeywordQuery(String scope, String metric, String tagKey, String tagValue, String namespace, RecordType type, String query, int limit, int page) {
    	super(limit, page);
    	setScope(scope);
    	setMetric(metric);
    	setTagKey(tagKey);
    	setTagValue(tagValue);
    	setNamespace(namespace);
    	setType(type);
    	setQuery(query);
    }
    
	public String getNamespace() {
		return namespace;
	}

	public void setNamespace(String namespace) {
		this.namespace = namespace;
	}

	public String getScope() {
		return scope;
	}

	public void setScope(String scope) {
		this.scope = scope;
	}

	public String getMetric() {
		return metric;
	}

	public void setMetric(String metric) {
		this.metric = metric;
	}

	public String getTagKey() {
		return tagKey;
	}

	public void setTagKey(String tagKey) {
		this.tagKey = tagKey;
	}

	public String getTagValue() {
		return tagValue;
	}

	public void setTagValue(String tagValue) {
		this.tagValue = tagValue;
	}

	public RecordType getType() {
		return type;
	}

	public void setType(RecordType type) {
		this.type = type;
	}

	public String getQuery() {
		return query;
	}

	public void setQuery(String query) {
		this.query = query;
	}
	
	@Override
    public String toString() {
		return MessageFormat.format("KeywordQuery = (Scope = {0}, Metric = {1}, Tagk = {2}, Tagv = {3}, Namespace = {4}, "
				+ "Query = {5}, Limit = {6}, Page = {7})", scope, metric, tagKey, tagValue, namespace, query, limit, page);
    }
	
	

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((metric == null) ? 0 : metric.hashCode());
		result = prime * result + ((namespace == null) ? 0 : namespace.hashCode());
		result = prime * result + ((query == null) ? 0 : query.hashCode());
		result = prime * result + ((scope == null) ? 0 : scope.hashCode());
		result = prime * result + ((tagKey == null) ? 0 : tagKey.hashCode());
		result = prime * result + ((tagValue == null) ? 0 : tagValue.hashCode());
		result = prime * result + ((type == null) ? 0 : type.hashCode());
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
		if (metric == null) {
			if (other.metric != null)
				return false;
		} else if (!metric.equals(other.metric))
			return false;
		if (namespace == null) {
			if (other.namespace != null)
				return false;
		} else if (!namespace.equals(other.namespace))
			return false;
		if (query == null) {
			if (other.query != null)
				return false;
		} else if (!query.equals(other.query))
			return false;
		if (scope == null) {
			if (other.scope != null)
				return false;
		} else if (!scope.equals(other.scope))
			return false;
		if (tagKey == null) {
			if (other.tagKey != null)
				return false;
		} else if (!tagKey.equals(other.tagKey))
			return false;
		if (tagValue == null) {
			if (other.tagValue != null)
				return false;
		} else if (!tagValue.equals(other.tagValue))
			return false;
		if (type != other.type)
			return false;
		return true;
	}



	public static class KeywordQueryBuilder {
		
		private String namespace;
	    private String scope;
	    private String metric;
	    private String tagKey;
	    private String tagValue;
	    private RecordType type;
		private String query;
		private int limit = 10;
		private int page = 1;
		
		public KeywordQueryBuilder() {}
		
		public KeywordQueryBuilder scope(String scope) {
	    	this.scope = scope;
	    	return this;
	    }
		
		public KeywordQueryBuilder metric(String metric) {
	    	this.metric = metric;
	    	return this;
	    }
		
		public KeywordQueryBuilder tagKey(String tagKey) {
	    	this.tagKey = tagKey;
	    	return this;
	    }
		
		public KeywordQueryBuilder tagValue(String tagValue) {
	    	this.tagValue = tagValue;
	    	return this;
	    }
		
		public KeywordQueryBuilder namespace(String namespace) {
	    	this.namespace = namespace;
	    	return this;
	    }
		
		public KeywordQueryBuilder type(RecordType type) {
	    	this.type = type;
	    	return this;
	    }
		
		public KeywordQueryBuilder query(String query) {
			this.query = query;
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
			return new KeywordQuery(scope, metric, tagKey, tagValue, namespace, type, query, limit, page);
		}
	}
	
}

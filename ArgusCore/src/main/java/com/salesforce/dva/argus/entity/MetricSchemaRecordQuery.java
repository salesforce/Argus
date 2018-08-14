/*
 * Copyright (c) 2016, Salesforce.com, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
	 
package com.salesforce.dva.argus.entity;

import com.salesforce.dva.argus.system.SystemAssert;
import java.text.MessageFormat;
import java.util.Objects;

/**
 * Represents a query against the metric schema data.  This is used for the discovery service and to expand wildcards.
 *
 * @author  Bhinav Sura (bsura@salesforce.com)
 */
public class MetricSchemaRecordQuery extends SchemaQuery {

    public static final String REGEX_MATCH_ANY = "*";

    //~ Instance fields ******************************************************************************************************************************

    private String namespace;
    private String scope;
    private String metric;
    private String tagKey;
    private String tagValue; 
    private MetricSchemaRecord scanFrom;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new MetricSchemaRecordQuery object.
     *
     * @param  namespace  The namespace.  Can be null.
     * @param  scope      The scope.  Cannot be null or empty.
     * @param  metric     The metric.  Cannot be null or empty.
     * @param  tagKey     The tag key.  Can be null.
     * @param  tagValue   The tag value.  Can be null.
     */
    private MetricSchemaRecordQuery(String namespace, String scope, String metric, String tagKey, String tagValue, 
    		int limit, int page, MetricSchemaRecord scanFrom) {
    	super(limit, page);
        setNamespace(namespace);
        setScope(scope);
        setMetric(metric);
        setTagKey(tagKey);
        setTagValue(tagValue);
        setScanFrom(scanFrom);
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Indicates the namespace of the query.
     *
     * @return  The namespace.  Can be null.
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Specifies the namespace for the query.
     *
     * @param  namespace  The namespace.  Can be null.
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * Indicates the scope of the query.
     *
     * @return  The scope of the query.  Will never return null.
     */
    public String getScope() {
        return scope;
    }

    /**
     * Specifies the scope of the query.
     *
     * @param  scope  The scope of the query.  Cannot be null or empty.
     */
    public void setScope(String scope) {
        SystemAssert.requireArgument(scope != null && !scope.isEmpty(), "Scope cannot be null or empty.");
        this.scope = scope;
    }

    /**
     * Indicates the metric name of the query.
     *
     * @return  The metric name of the query.  Will never return null.
     */
    public String getMetric() {
        return metric;
    }

    /**
     * Specifies the metric name of the query.
     *
     * @param  metric  The metric name of the query.  Cannot be null or empty.
     */
    public void setMetric(String metric) {
        SystemAssert.requireArgument(metric != null && !metric.isEmpty(), "Metric cannot be null or empty.");
        this.metric = metric;
    }

    /**
     * Indicates the tag key of the query.
     *
     * @return  The tag key of the query.  Can be null.
     */
    public String getTagKey() {
        return tagKey;
    }

    /**
     * Specifies the tag key of the query.
     *
     * @param  tagKey  The tag key of the query.  Can be null.
     */
    public void setTagKey(String tagKey) {
        this.tagKey = tagKey;
    }

    /**
     * Indicates the tag value of the query.
     *
     * @return  The tag value of the query.  Can be null.
     */
    public String getTagValue() {
        return tagValue;
    }

    /**
     * Specifies the tag value of the query.
     *
     * @param  tagValue  The tag value of the query.  Can be null.
     */
    public void setTagValue(String tagValue) {
        this.tagValue = tagValue;
    }

	public MetricSchemaRecord getScanFrom() {
		return scanFrom;
	}

	public boolean isQueryOnlyOnScope() {

		if (Objects.equals(this.namespace, REGEX_MATCH_ANY) &&
			Objects.equals(this.metric, REGEX_MATCH_ANY) &&
			Objects.equals(this.tagKey, REGEX_MATCH_ANY) &&
			Objects.equals(this.tagValue, REGEX_MATCH_ANY))
		{
			return true;
		}

		return false;
	}

	public boolean isQueryOnlyOnScopeAndMetric() {

		if (Objects.equals(this.namespace, REGEX_MATCH_ANY) &&
				Objects.equals(this.tagKey, REGEX_MATCH_ANY) &&
				Objects.equals(this.tagValue, REGEX_MATCH_ANY))
		{
			return true;
		}

		return false;
	}

	public void setScanFrom(MetricSchemaRecord scanFrom) {
		this.scanFrom = scanFrom;
	}
	
	@Override
    public String toString() {
        return MessageFormat.format("MetricSchemaRecordQuery = (Namespace = {0}, Scope = {1}, Metric = {2}, TagKey = {3}, "
        		+ "TagValue = {4}, Limit = {5}, Page = {6}, ScanFrom = {7})", 
        		namespace, scope, metric, tagKey, tagValue, limit, page, scanFrom);
    }

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((metric == null) ? 0 : metric.hashCode());
		result = prime * result + ((namespace == null) ? 0 : namespace.hashCode());
		result = prime * result + ((scanFrom == null) ? 0 : scanFrom.hashCode());
		result = prime * result + ((scope == null) ? 0 : scope.hashCode());
		result = prime * result + ((tagKey == null) ? 0 : tagKey.hashCode());
		result = prime * result + ((tagValue == null) ? 0 : tagValue.hashCode());
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
		MetricSchemaRecordQuery other = (MetricSchemaRecordQuery) obj;
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
		if (scanFrom == null) {
			if (other.scanFrom != null)
				return false;
		} else if (!scanFrom.equals(other.scanFrom))
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
		return true;
	}
	
	public static class MetricSchemaRecordQueryBuilder {
		
		private String namespace;
	    private String scope;
	    private String metric;
	    private String tagKey;
	    private String tagValue;
	    private int limit = 10; 
	    private int page = 1; 
	    private MetricSchemaRecord scanFrom;
	    
	    public MetricSchemaRecordQueryBuilder() {}
	    
	    public MetricSchemaRecordQueryBuilder namespace(String namespace) {
	    	this.namespace = namespace;
	    	return this;
	    }
	    
	    public MetricSchemaRecordQueryBuilder scope(String scope) {
	    	this.scope = scope;
	    	return this;
	    }
	    
	    public MetricSchemaRecordQueryBuilder metric(String metric) {
	    	this.metric = metric;
	    	return this;
	    }
	    
	    public MetricSchemaRecordQueryBuilder tagKey(String tagKey) {
	    	this.tagKey = tagKey;
	    	return this;
	    }
	    
	    public MetricSchemaRecordQueryBuilder tagValue(String tagValue) {
	    	this.tagValue = tagValue;
	    	return this;
	    }
	    
	    public MetricSchemaRecordQueryBuilder limit(int limit) {
	    	this.limit = limit;
	    	return this;
	    }
	    
	    public MetricSchemaRecordQueryBuilder page(int page) {
	    	this.page = page;
	    	return this;
	    }
	    
	    public MetricSchemaRecordQueryBuilder scanFrom(MetricSchemaRecord scanFrom) {
	    	this.scanFrom = scanFrom;
	    	return this;
	    }
		
	    public MetricSchemaRecordQuery build() {
	    	return new MetricSchemaRecordQuery(namespace, scope, metric, tagKey, tagValue, limit, page, scanFrom);
	    }
	}
    
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

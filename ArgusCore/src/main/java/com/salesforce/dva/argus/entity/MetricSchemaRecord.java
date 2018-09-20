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

import com.fasterxml.jackson.annotation.JsonProperty;
import java.text.MessageFormat;

/**
 * Represents a search result row for metric discovery queries.  Metric schema records are used to represent the Cartesian product of metric identifier fields and are provided
 * as the result of a metric discovery request.  Metric discovery is the mechanism by which a user can search for metric identifier fields to determine
 * which metrics are available in Argus along with their corresponding identifier fields.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class MetricSchemaRecord {
    public static final String RETENTION_DISCOVERY = "_retention_discovery_";
    public static final int DEFAULT_RETENTION_DISCOVERY = 45;
    public static final int MAX_RETENTION_DISCOVERY = 120;

    //~ Instance fields ******************************************************************************************************************************

    private String namespace;
    private String scope;
    private String metric;
    @JsonProperty("tagk")
    private String tagKey;
    @JsonProperty("tagv")
    private String tagValue;
    @JsonProperty(RETENTION_DISCOVERY)
    private Integer retentionDiscovery;

    //~ Constructors *********************************************************************************************************************************

    /** Creates a new MetricSchemaRecord object. */
    public MetricSchemaRecord() { }

    /**
     * Creates a new MetricSchemaRecord object.
     *
     * @param  scope   The scope of the metric schema.
     * @param  metric  The metric schema name.
     */
    public MetricSchemaRecord(String scope, String metric) {
        this(null, scope, metric, null, null, null);
    }

    /**
     * Creates a new MetricSchemaRecord object.
     *
     * @param  namespace  The metric schema namespace.
     * @param  scope      The metric schema scope.
     * @param  metric     The metric schema name.
     * @param  tagKey     The metric schema tag key.
     * @param  tagValue   The metric schema tag value.
     */
    public MetricSchemaRecord(String namespace, String scope, String metric, String tagKey, String tagValue) {
        this(namespace, scope, metric, tagKey, tagValue, null);
    }

    /**
     * Creates a new MetricSchemaRecord object.
     *
     * @param  namespace  The metric schema namespace.
     * @param  scope      The metric schema scope.
     * @param  metric     The metric schema name.
     * @param  tagKey     The metric schema tag key.
     * @param  tagValue   The metric schema tag value.
     * @param  retentionDiscovery The metric schema retention discovery
     */
    public MetricSchemaRecord(String namespace, String scope, String metric, String tagKey, String tagValue, Integer retentionDiscovery) {
        setNamespace(namespace);
        setScope(scope);
        setMetric(metric);
        setTagKey(tagKey);
        setTagValue(tagValue);
        setRetentionDiscovery(retentionDiscovery);
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the namespace associated with the result.
     *
     * @return  The namespace.  Can return null.
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Specifies the namespace associated with the result.
     *
     * @param  namespace  The namespace.  Can be null.
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * Indicates the scope associated with the result.
     *
     * @return  The scope.  Can be null or empty.
     */
    public String getScope() {
        return scope;
    }

    /**
     * Specifies the scope associated with the result.
     *
     * @param  scope  The scope.  Can be null or empty.
     */
    public void setScope(String scope) { 
        this.scope = scope;
    }

    /**
     * Indicates the metric name associated with the result.
     *
     * @return  The metric name.  Can be null or empty.
     */
    public String getMetric() {
        return metric;
    }

    /**
     * Specifies the metric name associated with the result.
     *
     * @param  metric  The metric name.  Can be null or empty.
     */
    public void setMetric(String metric) { 
        this.metric = metric;
    }

    /**
     * Indicates the tag key associated with the result.
     *
     * @return  The tag key.  Can be null.
     */
    public String getTagKey() {
        return tagKey;
    }

    /**
     * Specifies the tag key associated with the result.
     *
     * @param  tagKey  The tag key.  Can be null.
     */
    public void setTagKey(String tagKey) {
        this.tagKey = tagKey;
    }

    /**
     * Indicates the tag value associated with the result.
     *
     * @return  The tag value.  Can be null.
     */
    public String getTagValue() {
        return tagValue;
    }

    /**
     * Specifies the tag value associated with the result.
     *
     * @param  tagValue  The tag value.  Can be null.
     */
    public void setTagValue(String tagValue) {
        this.tagValue = tagValue;
    }

    public Integer getRetentionDiscovery() {
        return retentionDiscovery;
    }

    public void setRetentionDiscovery(Integer retentionDiscovery) {
        if (retentionDiscovery!=null
                && (retentionDiscovery < DEFAULT_RETENTION_DISCOVERY || retentionDiscovery > MAX_RETENTION_DISCOVERY)) {
            this.retentionDiscovery = DEFAULT_RETENTION_DISCOVERY;
        }
        else {
            this.retentionDiscovery = retentionDiscovery;
        }
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;

        result = prime * result + ((namespace == null) ? 0 : namespace.hashCode());
        result = prime * result + ((scope == null) ? 0 : scope.hashCode());
        result = prime * result + ((metric == null) ? 0 : metric.hashCode());
        result = prime * result + ((tagKey == null) ? 0 : tagKey.hashCode());
        result = prime * result + ((tagValue == null) ? 0 : tagValue.hashCode());
        //member retentionDiscovery has been left out intentionally
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        MetricSchemaRecord other = (MetricSchemaRecord) obj;

        if (metric == null) {
            if (other.metric != null) {
                return false;
            }
        } else if (!metric.equals(other.metric)) {
            return false;
        }
        if (namespace == null) {
            if (other.namespace != null) {
                return false;
            }
        } else if (!namespace.equals(other.namespace)) {
            return false;
        }
        if (scope == null) {
            if (other.scope != null) {
                return false;
            }
        } else if (!scope.equals(other.scope)) {
            return false;
        }
        if (tagKey == null) {
            if (other.tagKey != null) {
                return false;
            }
        } else if (!tagKey.equals(other.tagKey)) {
            return false;
        }
        if (tagValue == null) {
            if (other.tagValue != null) {
                return false;
            }
        } else if (!tagValue.equals(other.tagValue)) {
            return false;
        }
        //member retentionDiscovery has been left out intentionally
        return true;
    }

    @Override
    public String toString() {
        return MessageFormat.format("MetricSchemaRecord = (Namespace = {0}, Scope = {1}, Metric = {2}, TagKey = {3}, TagValue = {4}, RetentionDiscovery = {5})", namespace,
            scope, metric, tagKey, tagValue, retentionDiscovery);
    }
    /*
     * Returns the Metric Schema Record constructed from a given string
     * @param s String representing the metric expression in the format scope:metric{tagKey=tagValue}:namespace 
     */
    public static MetricSchemaRecord constructSchemaRecord(String s){
    	
    	if(s==null || s.length()==0)
    		return null;
    	
    	String[] querySplit=s.split(":"); 

		String scope=querySplit[0], namespace=querySplit.length==3?querySplit[2]:null,metric, tagKey, tagValue;

		if(querySplit[1].contains("{")){
			metric=querySplit[1].substring(0, querySplit[1].indexOf('{'));
			String tagKeyTagValue=querySplit[1].substring(querySplit[1].indexOf('{'), querySplit[1].length()-1);
			String[] tagKeyValueSplit=tagKeyTagValue.split("=");
			tagKey=tagKeyValueSplit[0].substring(1);
			tagValue=tagKeyValueSplit[1];

		}else{
			metric=querySplit[1];
			tagKey=null;
			tagValue=null;
		}
    	return new MetricSchemaRecord(namespace, scope, metric, tagKey, tagValue, null);
    }

    public static String print(MetricSchemaRecord msr) {
    	
    	StringBuilder sb = new StringBuilder(msr.getScope());
    	sb.append(":");
    	sb.append(msr.getMetric());
    	
    	if(msr.getTagKey() != null) {
    		sb.append("{").append(msr.getTagKey()).append("=").append(msr.getTagValue()).append("}");
    	}
    	
    	if(msr.getNamespace() != null) {
    		sb.append(":").append(msr.getNamespace());
    	}

        //member retentionDiscovery has been left out intentionally

    	return sb.toString();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

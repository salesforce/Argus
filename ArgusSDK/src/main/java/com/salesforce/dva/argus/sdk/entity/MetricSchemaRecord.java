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
package com.salesforce.dva.argus.sdk.entity;

import java.util.Objects;

/**
 * Represents a search result row for metric discovery queries. Metric schema records are used to represent the Cartesian product of metric identifier
 * fields and are provided as the result of a metric discovery request. Metric discovery is the mechanism by which a user can search for metric
 * identifier fields to determine which metrics are available in Argus along with their corresponding identifier fields.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public final class MetricSchemaRecord {

    //~ Instance fields ******************************************************************************************************************************

    private String namespace;
    private String scope;
    private String metric;
    private String tagKey;
    private String tagValue;

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
        this(null, scope, metric, null, null);
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
        setNamespace(namespace);
        setScope(scope);
        setMetric(metric);
        setTagKey(tagKey);
        setTagValue(tagValue);
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the namespace associated with the result.
     *
     * @return  The namespace. Can return null.
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Specifies the namespace associated with the result.
     *
     * @param  namespace  The namespace. Can be null.
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    /**
     * Indicates the scope associated with the result.
     *
     * @return  The scope. Cannot be null or empty.
     */
    public String getScope() {
        return scope;
    }

    /**
     * Specifies the scope associated with the result.
     *
     * @param  scope  The scope. Cannot be null or empty.
     */
    public void setScope(String scope) {
        this.scope = scope;
    }

    /**
     * Indicates the metric name associated with the result.
     *
     * @return  The metric name. Cannot be null or empty.
     */
    public String getMetric() {
        return metric;
    }

    /**
     * Specifies the metric name associated with the result.
     *
     * @param  metric  The metric name. Cannot be null or empty.
     */
    public void setMetric(String metric) {
        this.metric = metric;
    }

    /**
     * Indicates the tag key associated with the result.
     *
     * @return  The tag key. Can be null.
     */
    public String getTagKey() {
        return tagKey;
    }

    /**
     * Specifies the tag key associated with the result.
     *
     * @param  tagKey  The tag key. Can be null.
     */
    public void setTagKey(String tagKey) {
        this.tagKey = tagKey;
    }

    /**
     * Indicates the tag value associated with the result.
     *
     * @return  The tag value. Can be null.
     */
    public String getTagValue() {
        return tagValue;
    }

    /**
     * Specifies the tag value associated with the result.
     *
     * @param  tagValue  The tag value. Can be null.
     */
    public void setTagValue(String tagValue) {
        this.tagValue = tagValue;
    }

    @Override
    public int hashCode() {
        int hash = 5;

        hash = 89 * hash + Objects.hashCode(this.namespace);
        hash = 89 * hash + Objects.hashCode(this.scope);
        hash = 89 * hash + Objects.hashCode(this.metric);
        hash = 89 * hash + Objects.hashCode(this.tagKey);
        hash = 89 * hash + Objects.hashCode(this.tagValue);
        return hash;
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

        final MetricSchemaRecord other = (MetricSchemaRecord) obj;

        if (!Objects.equals(this.namespace, other.namespace)) {
            return false;
        }
        if (!Objects.equals(this.scope, other.scope)) {
            return false;
        }
        if (!Objects.equals(this.metric, other.metric)) {
            return false;
        }
        if (!Objects.equals(this.tagKey, other.tagKey)) {
            return false;
        }
        return Objects.equals(this.tagValue, other.tagValue);
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

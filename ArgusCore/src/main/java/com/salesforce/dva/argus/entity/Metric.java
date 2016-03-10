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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.system.SystemAssert;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Time series metric entity object. This entity encapsulates all the information needed to represent a time series for a metric within a single
 * scope. The following tag names are reserved. Any methods that set tags, which use these reserved tag names, will throw a runtime exception.
 *
 * <ul>
 *   <li>metric</li>
 *   <li>displayName</li>
 *   <li>units</li>
 * </ul>
 *
 * @author  Tom Valine (tvaline@salesforce.com), Bhinav Sura (bhinav.sura@salesforce.com)
 */
@SuppressWarnings("serial")
public class Metric extends TSDBEntity implements Serializable {

    //~ Instance fields ******************************************************************************************************************************

    private String _namespace;
    private String _displayName;
    private String _units;
    private final Map<Long, String> _datapoints;
    private MetricQuery _query;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new Metric object by performing a shallow copy of the given Metric object.
     *
     * @param  metric  The metric object to clone. Cannot be null.
     */
    public Metric(Metric metric) {
        SystemAssert.requireArgument(metric != null, "Metric to clone cannot be null.");
        setScope(metric.getScope());
        setMetric(metric.getMetric());
        setTags(metric.getTags());
        _datapoints = new TreeMap<>();
        setDatapoints(metric.getDatapoints());
        setNamespace(metric.getNamespace());
        setDisplayName(metric.getDisplayName());
        setUnits(metric.getUnits());
        setQuery(metric.getQuery());
    }

    /**
     * Creates a new Metric object.
     *
     * @param  scope   The reverse dotted name of the collection scope. Cannot be null or empty.
     * @param  metric  The name of the metric. Cannot be null or empty.
     */
    public Metric(String scope, String metric) {
        this();
        setScope(scope);
        setMetric(metric);
    }

    /** Creates a new Metric object. */
    protected Metric() {
        super(null, null);
        _datapoints = new TreeMap<>();
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    protected void setScope(String scope) {
        requireArgument(scope != null && !scope.trim().isEmpty(), "Scope cannot be null or empty.");
        super.setScope(scope);
    }

    @Override
    public void setMetric(String metric) {
        requireArgument(metric != null && !metric.trim().isEmpty(), "Metric cannot be null or empty.");
        super.setMetric(metric);
    }

    /**
     * Returns the namespace for the metric.
     *
     * @return  The namespace of the metric or null if the metric belongs to the global namespace.
     */
    public String getNamespace() {
        return _namespace;
    }

    /**
     * Sets the namespace for the metric.
     *
     * @param  namespace  The namespace for the metric.  If null, the metric will belong to the global namespace.
     */
    public void setNamespace(String namespace) {
        _namespace = namespace;
    }

    /**
     * Returns an unmodifiable map of time series data points which is backed by the entity objects internal data.
     *
     * @return  The map of time series data points. Will never be null, but may be empty.
     */
    public Map<Long, String> getDatapoints() {
        return Collections.unmodifiableMap(_datapoints);
    }

    /**
     * Deletes the current set of data points and replaces them with a new set.
     *
     * @param  datapoints  The new set of data points. If null or empty, only the deletion of the current set of data points is performed.
     */
    public void setDatapoints(Map<Long, String> datapoints) {
        _datapoints.clear();
        if (datapoints != null) {
            _datapoints.putAll(datapoints);
        }
    }

    /**
     * Adds the current set of data points to the current set.
     *
     * @param  datapoints  The set of data points to add. If null or empty, only the deletion of the current set of data points is performed.
     */
    public void addDatapoints(Map<Long, String> datapoints) {
        if (datapoints != null) {
            _datapoints.putAll(datapoints);
        }
    }

    /**
     * Sets the display name for the metric.
     *
     * @param  displayName  The display name for the metric. Can be null or empty.
     */
    public void setDisplayName(String displayName) {
        _displayName = displayName;
    }

    /**
     * Returns the display name for the metric.
     *
     * @return  The display name for the metric. Can be null or empty.
     */
    public String getDisplayName() {
        return _displayName;
    }

    /**
     * Sets the units of the time series data point values.
     *
     * @param  units  The units of the time series data point values. Can be null or empty.
     */
    public void setUnits(String units) {
        _units = units;
    }

    /**
     * Returns the units of the time series data point values.
     *
     * @return  The units of the time series data point values. Can be null or empty.
     */
    public String getUnits() {
        return _units;
    }

    /**
     * A meta data field which can be used to specify the query that was used to obtain this metric.
     *
     * @param  query  The query used to retrieve the metric or null if the metric was not created as the result of a query.
     */
    public void setQuery(MetricQuery query) {
        this._query = query;
    }

    /**
     * Indicates the query  that was used to obtain this metric.
     *
     * @return  The query used to retrieve the metric or null if the metric was not created as the result of a query.
     */
    public MetricQuery getQuery() {
        return _query;
    }

    /**
     * Constructs a native TSDB metric name for this metric.
     * @todo This is implementation specific and should be moved to a service interface method.
     *
     * @return  The native TSDB metric name for this metric.  This method should never return null.
     */
    public String constructTSDBMetricName() {
        StringBuilder sb = new StringBuilder(getScope());

        if (_namespace != null && !_namespace.isEmpty()) {
            sb.append(getNamespace());
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        Object[] params = {getNamespace(), getScope(), getMetric(), getTags(), getDatapoints() };
        String format = "namespace=>{0}, scope=>{1}, metric=>{2}, tags=>{3}, datapoints=>{4}";

        return MessageFormat.format(format, params);
    }

    /**
     * To return an identifier string, the format is &lt;namespace&gt;:&lt;scope&gt;:&lt;name&gt;{&lt;tags&gt;}
     *
     * @return  Returns a metric identifier for the metric.  Will never return null.
     */
    @JsonIgnore
    public String getIdentifier() {
        Map<String, String> sortedTags = new TreeMap<>();

        sortedTags.putAll(getTags());

        StringBuilder tagListBuffer = new StringBuilder();

        tagListBuffer.append("{");
        for (String tagKey : sortedTags.keySet()) {
            tagListBuffer.append(tagKey).append('=').append(sortedTags.get(tagKey)).append(',');
        }

        String tagList = tagListBuffer.toString();

        tagList = tagList.substring(0, tagList.length() - 1).concat("}");

        Object[] params = { getNamespace(), getScope(), getMetric(), tagList };
        String format = "{0}:{1}:{2}" + "{3}";

        return MessageFormat.format(format, params);
    }
    
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

/*
 * Copyright (c) 2019, Salesforce.com, Inc.
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

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.salesforce.dva.argus.system.SystemAssert;

/**
 * Time series histogram entity object. This entity encapsulates all the information needed to represent a time series for a histogram within a single
 * scope. The following tag names are reserved. Any methods that set tags, which use these reserved tag names, will throw a runtime exception.
 *
 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
 */
@SuppressWarnings("serial")
public class Histogram extends TSDBEntity implements Serializable {

    //~ Instance fields ******************************************************************************************************************************
    private String _displayName;
    private String _units;
    @JsonSerialize (keyUsing = HistogramBucketSerializer.class)
    @JsonDeserialize (keyUsing = HistogramBucketDeserializer.class)
    private Map<HistogramBucket, Long> _buckets;
    private Long _underflow = 0L;
    private Long _overflow = 0L;
    private Long _timestamp;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new Histogram object by performing a shallow copy of the given Histogram object.
     *
     * @param  histogram  The histogram object to clone. Cannot be null.
     */
    public Histogram(Histogram histogram) {
        SystemAssert.requireArgument(histogram != null, "Histogram to clone cannot be null.");
        setScope(histogram.getScope());
        setMetric(histogram.getMetric());
        setTags(histogram.getTags());
        _buckets = new TreeMap<>();
        setBuckets(histogram.getBuckets());
        setDisplayName(histogram.getDisplayName());
        setUnits(histogram.getUnits());
    }

    /**
     * Creates a new Histogram object.
     *
     * @param  scope   The reverse dotted name of the collection scope. Cannot be null or empty.
     * @param  metric  The name of the metric. Cannot be null or empty.
     */
    public Histogram(String scope, String metric) {
        this();
        setScope(scope);
        setMetric(metric);
    }

    /** Creates a new Histogram object. */
    protected Histogram() {
        super(null, null);
        _buckets = new TreeMap<>();
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public void setScope(String scope) {
        requireArgument(scope != null && !scope.trim().isEmpty(), "Scope cannot be null or empty.");
        super.setScope(scope);
    }

    @Override
    public void setMetric(String metric) {
        requireArgument(metric != null && !metric.trim().isEmpty(), "Metric cannot be null or empty.");
        super.setMetric(metric);
    }

    /**
     * Returns the optional overflow for the histogram.
     *
     * @return  The overflow for the histogram.
     */    
    public Long getOverflow() {
        return _overflow;
    }

    /**
     * Sets the overflow for the histogram.
     *
     * @param  overflow  The overflow for the histogram
     */
    public void setOverflow(Long overflow) {
        _overflow = overflow;
    } 
    
    /**
     * Returns the optional underflow for the histogram.
     *
     * @return  The underflow for the histogram.
     */
    public Long getUnderflow() {
        return _underflow;
    }
    
    /**
     * Sets the underflow for the histogram.
     *
     * @param  underflow  The underflow for the histogram.
     */
    public void setUnderflow(Long underflow) {
        _underflow = underflow;
    }    
    
    /**
     * Returns an unmodifiable map of histogram buckets which is backed by the entity objects internal data.
     *
     * @return  The map of histogram buckets. Will never be null, but may be empty.
     */
    public Map<HistogramBucket, Long> getBuckets() {
        return Collections.unmodifiableMap(_buckets);
    }

    /**
     * Add a new bucket with count to exisitng buckets
     *
     * @param  lowerBound  lower bound of bucket
     * @param  upperBound  upper bound of bucket
     * @param  count       count within this bucket
     */
    public void addBucket(float lowerBound, float upperBound, long count) {
        _buckets.put(new HistogramBucket(lowerBound, upperBound), count);
    }

    /**
     * Deletes the current map of histogram buckets and replaces them with a new map.
     *
     * @param  buckets  The new map of histogram buckets. If null or empty, only the deletion of the current set of histogram buckets is performed.
     */
    public void setBuckets(Map<HistogramBucket, Long> buckets) {
        _buckets.clear();
        if (buckets != null) {
            _buckets.putAll(buckets);
        }
    }
    
    /*
     *  Deletes the current map of histogram buckets
     */
    public void clearBuckets() {
        _buckets.clear();
    }

    /**
     * Sets the display name for the histogram.
     *
     * @param  displayName  The display name for the histogram. Can be null or empty.
     */
    public void setDisplayName(String displayName) {
        _displayName = displayName;
    }

    /**
     * Returns the display name for the histogram.
     *
     * @return  The display name for the histogram. Can be null or empty.
     */
    public String getDisplayName() {
        return _displayName;
    }

    /**
     * Sets the units of the histogram values.
     *
     * @param  units  The units of the histogram values. Can be null or empty.
     */
    public void setUnits(String units) {
        _units = units;
    }

    /**
     * Returns the units of the histogram values.
     *
     * @return  The units of the histogram values. Can be null or empty.
     */
    public String getUnits() {
        return _units;
    }
    
    /**
     * Sets the time stamp at which the histogram exists.
     *
     * @param  timestamp  The time stamp for the histogram. Cannot be null.
     */
    public void setTimestamp(Long timestamp) {
        requireArgument(timestamp != null, "Timestamp cannot be null.");
        _timestamp = timestamp;
    }

    /**
     * Returns the time stamp of the histogram.
     *
     * @return  The time stamp of the histogram. Will never be null.
     */
    public Long getTimestamp() {
        return _timestamp;
    }

    @Override
    public String toString() {
        Object[] params = {getTimestamp(), getScope(), getMetric(), getTags(), getBuckets(), getUnderflow(), getOverflow() };
        String format = "timestamp=>{0,number,#}, scope=>{1}, metric=>{2}, tags=>{3}, buckets=>{4}, underflow=>{5}, overflow=>{6}";

        return MessageFormat.format(format, params);
    }

    /**
     * To return an identifier string, the format is &lt;scope&gt;:&lt;name&gt;{&lt;tags&gt;}
     *
     * @return  Returns a metric identifier for the histogram.  Will never return null.
     */
    @JsonIgnore
    public String getIdentifier() {

        String tags = "";
        Map<String, String> sortedTags = getTags();
        if(!sortedTags.isEmpty()) {
            StringBuilder tagListBuffer = new StringBuilder("{");
            for (String tagKey : sortedTags.keySet()) {
                tagListBuffer.append(tagKey).append('=').append(sortedTags.get(tagKey)).append(',');
            }

            tags = tagListBuffer.substring(0, tagListBuffer.length() - 1).concat("}");
        }

        Object[] params = { getScope(), getMetric(), tags };
        String format = "{0}:{1}" + "{2}";

        return MessageFormat.format(format, params);
    }

}
/* Copyright (c) 2019, Salesforce.com, Inc.  All rights reserved. */
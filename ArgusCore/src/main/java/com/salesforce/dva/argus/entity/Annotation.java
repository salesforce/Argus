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

import java.io.Serializable;
import java.text.MessageFormat;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Time series annotation entity object. Encapsulates all information needed to represent an annotation.
 *
 * @author  Tom Valine (tvaline@salesforce.com), Bhinav Sura (bhinav.sura@salesforce.com)
 */
@SuppressWarnings("serial")
public class Annotation extends TSDBEntity implements Serializable {

    //~ Instance fields ******************************************************************************************************************************

    private String _type;
    private Map<String, String> _fields;
    private Long _timestamp;
    private String _source;
    private String _id;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new Annotation object. If metric is null, the annotation is global within the specified scope, type and tags. Uniqueness of an
     * annotation within the context of a scope, metric, tags and type is determined by the timestamp, source and ID fields. Multiple annotations can
     * be stored at a single timestamp if the source and ID are unique. If there is an existing annotation having the same source ID at a given
     * timestamp, it will be overwritten with the new value.
     *
     * @param  source     Used to describe the data source the annotation was collected from (e.g. SPLUNK). Cannot be null or empty.
     * @param  id         The data source specific ID of the annotation (e.g. ID-82140). Cannot be null or empty.
     * @param  type       Describes the category for an annotation (e.g. ERELEASE). Cannot be null or empty.
     * @param  scope      The scope of the annotation object (e.g. NA1). Cannot be null or empty.
     * @param  metric     The metric name associated with the metric. If not null, cannot be empty.
     * @param  timestamp  The timestamp at which the annotated event occurs. Can not be null.
     */
    public Annotation(String source, String id, String type, String scope, String metric, Long timestamp) {
        this();
        setSource(source);
        setId(id);
        setTimestamp(timestamp);
        setMetric(metric);
        setScope(scope);
        setType(type);
    }

    /** Creates a new Metric object. */
    protected Annotation() {
        super(null, null);
        _fields = new HashMap<>();
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the source of the annotation.
     *
     * @return  The source of the annotation. Will never be null.
     */
    public String getSource() {
        return _source;
    }

    /**
     * Returns the ID of the annotation as indicated by the data source.
     *
     * @return  The ID of the annotation. Will never be null.
     */
    public String getId() {
        return _id;
    }

    /**
     * Returns the category of the metric.
     *
     * @return  The category of the metric. Will never be null.
     */
    public String getType() {
        return _type;
    }

    /**
     * Returns the user defined fields associated with the annotation. This information can be used to relate information about the annotation such as
     * the event name, the associated user or any other relevant information.
     *
     * @return  The user defined fields for the annotation. Will never be null but may be empty.
     */
    public Map<String, String> getFields() {
        return Collections.unmodifiableMap(_fields);
    }

    /**
     * Returns the time stamp of the annotation.
     *
     * @return  The time stamp of the annotation. Will never be null.
     */
    public Long getTimestamp() {
        return _timestamp;
    }

    /**
     * Returns the hash code for the annotation, based on the scope, metric, tags, type, source, ID and timestamp.
     *
     * @return  The hash code for the annotation.
     */
    @Override
    public int hashCode() {
        int hash = 7;

        hash = 71 * hash + super.hashCode();
        hash = 71 * hash + (_type != null ? _type.hashCode() : 0);
        hash = 71 * hash + (_timestamp != null ? _timestamp.hashCode() : 0);
        hash = 71 * hash + (_source != null ? _source.hashCode() : 0);
        hash = 71 * hash + (_id != null ? _id.hashCode() : 0);
        return hash;
    }

    /**
     * Determines if another object is equivalent to this annotation.
     *
     * @param   obj  The object with which to compare. Can be null.
     *
     * @return  True if the object is an annotation having the same scope, metric, tags, type, source, ID and timestamp.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        final Annotation other = (Annotation) obj;

        if (!super.equals(other)) {
            return false;
        }
        if ((_type == null) ? (other._type != null) : !_type.equals(other._type)) {
            return false;
        }
        if ((_timestamp == null) ? (other._timestamp != null) : !_timestamp.equals(other._timestamp)) {
            return false;
        }
        if ((_source == null) ? (other._source != null) : !_source.equals(other._source)) {
            return false;
        }
        if ((_id == null) ? (other._id != null) : !_id.equals(other._id)) {
            return false;
        }
        return true;
    }

    /**
     * Sets the scope of the collection of the annotation.
     *
     * @param  scope  The scope of the collection. Cannot be null or empty.
     */
    @Override
    protected void setScope(String scope) {
        requireArgument(scope != null && !scope.trim().isEmpty(), "Scope cannot be null or empty.");
        super.setScope(scope);
    }

    /**
     * Sets the metric with which the annotation is associated.
     *
     * @param  metric  The metric with which the annotation is associated. If not null, it cannot be empty.
     */
    @Override
    protected void setMetric(String metric) {
        requireArgument(metric == null || !metric.trim().isEmpty(), "Metric can be null, but if specified, cannot be empty");
        super.setMetric(metric);
    }

    /**
     * Sets the category of the metric.
     *
     * @param  type  The category of the metric. Cannot be null or empty.
     */
    private void setType(String type) {
        requireArgument(type != null && !type.trim().isEmpty(), "Type cannot be null or empty.");
        _type = type;
    }

    /**
     * Sets the time stamp at which the annotation exists.
     *
     * @param  timestamp  THe time stamp for the annotation. Cannot be null.
     */
    private void setTimestamp(Long timestamp) {
        requireArgument(timestamp != null, "Timestamp cannot be null.");
        _timestamp = timestamp;
    }

    /**
     * Sets the source of the annotation.
     *
     * @param  source  The source of the annotation. Cannot be null or empty.
     */
    private void setSource(String source) {
        requireArgument(source != null && !source.trim().isEmpty(), "Source cannot be null or empty.");
        _source = source;
    }

    /**
     * Sets the ID of the annotation as indicated by the data source.
     *
     * @param  id  The ID of the annotation. Will never be null.
     */
    private void setId(String id) {
        requireArgument(id != null && !id.trim().isEmpty(), "ID cannot be null or empty.");
        _id = id;
    }

    /**
     * Replaces the user defined fields associated with the annotation. This information can be used to store information about the annotation such as
     * the event name, the associated user or any other relevant information. Existing fields will always be deleted.
     *
     * @param  fields  The user defined fields. May be null.
     */
    public void setFields(Map<String, String> fields) {
        _fields.clear();
        if (fields != null) {
            _fields.putAll(fields);
        }
    }

    @Override
    public String toString() {
        Object[] params = { getTimestamp(), getScope(), getMetric(), getTags(), getType(), getSource(), getId(), getFields() };
        String format = "timestamp=>{0,number,#}, scope=>{1}, metric=>{2}, tags=>{3}, type=>{4}, source=>{5}, sourceId=>{6}, fields=>{7}";

        return MessageFormat.format(format, params);
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

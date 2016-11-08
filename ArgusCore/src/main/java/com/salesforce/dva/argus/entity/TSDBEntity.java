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
import java.util.TreeMap;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * The base class for TSDB annotations and metrics.
 *
 * @author  Tom Valine (tvaline@salesforce.com), Bhinav Sura (bhinav.sura@salesforce.com)
 */
@SuppressWarnings("serial")
public abstract class TSDBEntity implements Serializable {

    //~ Instance fields ******************************************************************************************************************************

    private String _uid;
    private String _scope;
    private String _metric;
    private final Map<String, String> _tags = new HashMap<>(0);

    //~ Constructors *********************************************************************************************************************************

    /** Creates a new TSDBEntity object. */
    protected TSDBEntity() { }

    /**
     * Creates a new TSDBEntity object. No parameter validation is performed but is instead deferred to sub-class implementations.
     *
     * @param  scope   The collection scope of the metric (e.g. 00D000000000062.na1).
     * @param  metric  The metric being collected.
     */
    protected TSDBEntity(String scope, String metric) {
        _scope = scope;
        _metric = metric;
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Indicates the scope of the collection for the metric.
     *
     * @return  The reverse dotted name of the collection scope.
     */
    public String getScope() {
        return _scope;
    }

    /**
     * Sets the scope of collection for the metric.
     *
     * @param  scope  The reverse dotted name of the collection scope.
     */
    protected void setScope(String scope) {
        _scope = scope;
    }

    /**
     * Returns the name of the collected metric.
     *
     * @return  The name of the collected metric.
     */
    public String getMetric() {
        return _metric;
    }

    /**
     * Sets the name of the collected metric.
     *
     * @param  metric  The name of the collected metric.
     */
    protected void setMetric(String metric) {
        _metric = metric;
    }

    /**
     * Returns the unique ID assigned by TSDB.
     *
     * @return  The unique ID for the metric.
     */
    public String getUid() {
        return _uid;
    }

    /**
     * Sets the unique ID for the metric. This will never be set using calling code, but only by reflection when de-serializing from TSDB.
     *
     * @param  uid  The unique ID for the metric.
     */
    protected void setUid(String uid) {
        _uid = uid;
    }

    /**
     * Returns an unmodifiable collection of tags associated with the metric.
     *
     * @return  The tags for a metric. Will never be null but may be empty.
     */
    public Map<String, String> getTags() {
        Map<String, String> result = new HashMap<>();

        for (Map.Entry<String, String> entry : _tags.entrySet()) {
            String key = entry.getKey();

            if (!ReservedField.isReservedField(key)) {
                result.put(key, entry.getValue());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Replaces the tags for a metric. Tags cannot use any of the reserved tag names.
     *
     * @param  tags  The new tags for the metric.
     */
    public void setTags(Map<String, String> tags) {
        _tags.clear();
        if (tags != null) {
            Map<String, String> updatedTags = new TreeMap<>();

            for (Map.Entry<String, String> entry : tags.entrySet()) {
                String key = entry.getKey();

                requireArgument(!Metric.ReservedField.isReservedField(key), MessageFormat.format("Tag {0} is a reserved tag name.", key));
                updatedTags.put(key, entry.getValue());
            }
            _tags.putAll(updatedTags);
        }
    }

    /**
     * Sets a single tag. The tag may not use any of the reserved tag names.
     *
     * @param  key    The name of the tag. May not be null or empty.
     * @param  value  The value of the tag. Can be null or empty.
     */
    public void setTag(String key, String value) {
        setTag(key, value, true);
    }

    /**
     * Returns the value of a tag. Will always return null for a reserved tag name.
     *
     * @param   key  The name of the tag. Cannot be null or empty.
     *
     * @return  The value of the tag or null if no value for the key exists..
     */
    public String getTag(String key) {
        return getTag(key, true);
    }

    /**
     * Returns the hash code for the entity based on the scope, metric and tags.
     *
     * @return  The hash code for the entity.
     */
    @Override
    public int hashCode() {
        int hash = 7;

        hash = 23 * hash + (_scope != null ? _scope.hashCode() : 0);
        hash = 23 * hash + (_metric != null ? _metric.hashCode() : 0);
        hash = 23 * hash + (_tags != null ? _tags.hashCode() : 0);
        return hash;
    }

    /**
     * Determines if another object is equivalent to this entity.
     *
     * @param   obj  The object with which to compare.
     *
     * @return  True if the object is a TSDB entity and the scope, metric and tags are equal.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        final TSDBEntity other = TSDBEntity.class.cast(obj);

        if ((_scope == null) ? (other._scope != null) : !_scope.equals(other._scope)) {
            return false;
        }
        if ((_metric == null) ? (other._metric != null) : !_metric.equals(other._metric)) {
            return false;
        }
        if (getTags() != other.getTags() && (getTags() == null || !getTags().equals(other.getTags()))) {
            return false;
        }
        return true;
    }

    private void setTag(String key, String value, boolean isUserTag) {
        requireArgument(key != null && !key.isEmpty(), "Tag cannot be null or empty.");
        requireArgument(isUserTag ? !Metric.ReservedField.isReservedField(key) : true, MessageFormat.format("Tag {0} is a reserved tag name.", key));
        if (value == null || value.isEmpty()) {
            _tags.remove(key);
        } else {
            _tags.put(key, value);
        }
    }

    private String getTag(String key, boolean isUserTag) {
        requireArgument(key != null && !key.isEmpty(), "Tag cannot be null or empty.");
        return (!isUserTag || !Metric.ReservedField.isReservedField(key)) ? _tags.get(key) : null;
    }

    //~ Enums ****************************************************************************************************************************************

    /**
     * Represents the set of reserved tag names. Attempting to use a reserved tag name will result in an exception.
     *
     * @author  Tom Valine (tvaline@salesforce.com), Bhinav Sura (bhinav.sura@salesforce.com)
     */
    public static enum ReservedField {

        META("meta"),
        UNITS("units"),
        DISPLAY_NAME("displayName");

        private final String _key;

        private ReservedField(String key) {
            _key = key;
        }

        /**
         * Indicates if a key corresponds to a reserved field.
         *
         * @param   key  The key to evaluate.
         *
         * @return  True if the key is a reserved field or if the key is null.
         */
        public static boolean isReservedField(String key) {
            if (key == null) {
                return true;
            } else {
                for (ReservedField field : ReservedField.values()) {
                    if (field.getKey().equals(key)) {
                        return true;
                    }
                }
            }
            return false;
        }

        /**
         * Returns the key name.
         *
         * @return  The key name.
         */
        public String getKey() {
            return _key;
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

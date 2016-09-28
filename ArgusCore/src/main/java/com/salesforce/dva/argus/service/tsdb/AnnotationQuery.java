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
	 
package com.salesforce.dva.argus.service.tsdb;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.TSDBEntity.ReservedField;
import com.salesforce.dva.argus.system.SystemException;

import java.io.UnsupportedEncodingException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;
import static java.net.URLEncoder.encode;

/**
 * Encapsulates parameters used to drive a query of annotation data.
 *
 * @author  Tom Valine (tvaline@salesforce.com), Bhinav Sura (bhinav.sura@salesforce.com)
 */
public class AnnotationQuery {

    //~ Instance fields ******************************************************************************************************************************

    protected String _scope;
    protected String _metric;
    protected Map<String, String> _tags;
    protected Long _startTimestamp;
    protected Long _endTimestamp;
    private String _type;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new AnnotationQuery object.
     *
     * @param  scope           The scope of the annotations. Cannot be null or empty.
     * @param  metric          The metric associated with the annotations. If not null, cannot be empty.
     * @param  tags            The tags associated with the annotations. Can be null or empty.
     * @param  type            The type of annotations to retrieve. Cannot be null or empty.
     * @param  startTimestamp  The start time stamp for the query. Cannot be null.
     * @param  endTimestamp    The end time for the query. If null, defaults to the current system time.
     */
    public AnnotationQuery(String scope, String metric, Map<String, String> tags, String type, Long startTimestamp, Long endTimestamp) {
        this(scope, metric, tags, startTimestamp, endTimestamp);
        requireArgument(type != null && !type.trim().isEmpty(), "Type cannot be null or empty.");
        _type = type;
    }

    /** Creates a new AnnotationQuery object. */
    protected AnnotationQuery() {
        _tags = new HashMap<>();
    }

    /**
     * Creates a new AnnotationQuery object.
     *
     * @param  scope           The scope of the annotations. Cannot be null or empty.
     * @param  metric          The metric associated with the annotations. If not null, cannot be empty.
     * @param  tags            The tags associated with the annotations. Can be null or empty.
     * @param  startTimestamp  The start time stamp for the query. Cannot be null.
     * @param  endTimestamp    The end time for the query. If null, defaults to the current system time.
     */
    protected AnnotationQuery(String scope, String metric, Map<String, String> tags, Long startTimestamp, Long endTimestamp) {
        this();
        requireArgument(startTimestamp != null, "Start Timestamp cannot be null.");
        requireArgument(scope != null, "Scope cannot be null");
        requireArgument(metric == null || !metric.isEmpty(), "Metric can be null, but if specified, cannot be empty");
        _startTimestamp = startTimestamp;
        _endTimestamp = endTimestamp;
        _scope = scope;
        _metric = metric;
        if (tags != null) {
            setTags(tags);
        }
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the type of annotations to retrieve.
     *
     * @return  The type of annotations to retrieve. Will never be null.
     */
    public String getType() {
        return _type;
    }

    /**
     * Returns the scope of the query.
     *
     * @return  The scope of the query. Will never be null.
     */
    public String getScope() {
        return _scope;
    }

    /**
     * Returns the metric associated with the annotations to query.
     *
     * @return  The associated metric. May return null.
     */
    public String getMetric() {
        return _metric;
    }

    /**
     * Returns the tags associated with the query.
     *
     * @return  The tags associated with the query. Will never return null, but may be empty.
     */
    public Map<String, String> getTags() {
        return _tags;
    }

    /**
     * Returns the start time stamp.
     *
     * @return  The start time stamp. Will never return null.
     */
    public Long getStartTimestamp() {
        return _startTimestamp;
    }

    /**
     * Returns the end time stamp. If no end time was specified for the query the current system time is returned.
     *
     * @return  The end time stamp.
     */
    public Long getEndTimestamp() {
        if (_endTimestamp == null) {
            return System.currentTimeMillis();
        } else {
            return _endTimestamp;
        }
    }

    /**
     * Sets the start time stamp.
     *
     * @param  timestamp  The start epoch timestamp.
     */
    public void setStartTimestamp(Long timestamp) {
        _startTimestamp = timestamp;
    }

    /**
     * Sets the end time stamp.
     *
     * @param  timestamp  The end epoch timestamp.
     */
    public void setEndTimestamp(Long timestamp) {
        _endTimestamp = timestamp;
    }

    /**
     * Sets a single tag for the query.
     *
     * @param  key    The key for the tag. Cannot null, empty or a reserved field name.
     * @param  value  The tag value. If the value is null, the tag is removed from the query tags.
     */
    @JsonIgnore
    public void setTag(String key, String value) {
        requireArgument(key != null && !key.trim().isEmpty(), "Tag key cannot be null.");
        requireArgument(!ReservedField.isReservedField(key), "Tag is a reserved tag name.");
        if (value == null || value.isEmpty()) {
            _tags.remove(key);
        } else {
            _tags.put(key, value);
        }
    }

    /**
     * Returns the tag value for the given key.
     *
     * @param   key  The tag key. May be null.
     *
     * @return  The value of the tag, or null if no value exists.
     */
    @JsonIgnore
    public String getTag(String key) {
        return (!Metric.ReservedField.isReservedField(key)) ? _tags.get(key) : null;
    }

    /**
     * Replaces the tags for the query.
     *
     * @param  tags  The new tags. May be null.
     */
    public final void setTags(Map<String, String> tags) {
        Map<String, String> updatedTags = new TreeMap<>();

        if (tags != null) {
            for (Map.Entry<String, String> entry : tags.entrySet()) {
                String key = entry.getKey();

                requireArgument(!Metric.ReservedField.isReservedField(key), MessageFormat.format("Tag {0} is a reserved tag name.", key));
                updatedTags.put(key, entry.getValue());
            }
        }
        _tags.clear();
        _tags.putAll(updatedTags);
    }

    /**
     * Sets the scope of the query.
     *
     * @param  scope  The scope of the query.  Cannot be null.
     */
    protected void setScope(String scope) {
        requireArgument(scope != null && !scope.isEmpty(), "Scope cannot be null or empty.");
        _scope = scope;
    }

    /**
     * Sets the metric name for the query.
     *
     * @param  metric  The metric name.  Cannot be null.
     */
    protected void setMetric(String metric) {
        requireArgument(metric != null && !metric.isEmpty(), "Metric name cannot be null or empty.");
        _metric = metric;
    }

    /**
     * Returns the tags in TSDB query string format.
     *
     * @param   tags  The tags to convert. Can be null.
     *
     * @return  The formatted tags.
     *
     * @throws  UnsupportedEncodingException  If UTF-8 is not supported.
     */
    protected String toTagParameterArray(Map<String, String> tags) throws UnsupportedEncodingException {
    	if(tags == null || tags.isEmpty()) {
    		return "";
    	}
    	
        StringBuilder sb = new StringBuilder(encode("{", "UTF-8"));
        for (Map.Entry<String, String> tagEntry : tags.entrySet()) {
            sb.append(tagEntry.getKey()).append("=");

            String tagV = tagEntry.getValue().replaceAll("\\|", encode("|", "UTF-8"));

            sb.append(tagV).append(",");
        }
        sb.replace(sb.length() - 1, sb.length(), encode("}", "UTF-8"));
        
        return sb.toString();
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;

        result = prime * result + ((_endTimestamp == null) ? 0 : _endTimestamp.hashCode());
        result = prime * result + ((_metric == null) ? 0 : _metric.hashCode());
        result = prime * result + ((_scope == null) ? 0 : _scope.hashCode());
        result = prime * result + ((_startTimestamp == null) ? 0 : _startTimestamp.hashCode());
        result = prime * result + ((_tags == null) ? 0 : _tags.hashCode());
        result = prime * result + ((_type == null) ? 0 : _type.hashCode());
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

        AnnotationQuery other = (AnnotationQuery) obj;

        if (_endTimestamp == null) {
            if (other._endTimestamp != null) {
                return false;
            }
        } else if (!_endTimestamp.equals(other._endTimestamp)) {
            return false;
        }
        if (_metric == null) {
            if (other._metric != null) {
                return false;
            }
        } else if (!_metric.equals(other._metric)) {
            return false;
        }
        if (_scope == null) {
            if (other._scope != null) {
                return false;
            }
        } else if (!_scope.equals(other._scope)) {
            return false;
        }
        if (_startTimestamp == null) {
            if (other._startTimestamp != null) {
                return false;
            }
        } else if (!_startTimestamp.equals(other._startTimestamp)) {
            return false;
        }
        if (_tags == null) {
            if (other._tags != null) {
                return false;
            }
        } else if (!_tags.equals(other._tags)) {
            return false;
        }
        if (_type == null) {
            if (other._type != null) {
                return false;
            }
        } else if (!_type.equals(other._type)) {
            return false;
        }
        return true;
    }

    /**
     * Returns the TSDB formatted representation of the query.
     *
     * @return  The TSDB formatted representation of the query.
     *
     * @throws  SystemException  If UTF-8 encoding is not supported on the system.
     */
    @Override
    public String toString() {
        String pattern = "start={0,number,#}&end={1,number,#}&m=avg:{2}{3}&ms=true&show_tsuids=true";
        long start = Math.max(0, getStartTimestamp() - 1);
        long end = Math.max(start, getEndTimestamp() + 1);
        
        String scope = DefaultTSDBService.toAnnotationKey(_scope, _metric, _type, _tags);
        //When creating the corresponding argus Metric for the annotations, _type is used as metric name. 
        String tsdbMetricName = DefaultTSDBService.constructTSDBMetricName(new Metric(scope, _type));
        Map<String, String> tags = new HashMap<>(getTags());
        
        try {
            return MessageFormat.format(pattern, start, end, tsdbMetricName, toTagParameterArray(tags));
        } catch (UnsupportedEncodingException ex) {
            throw new SystemException(ex);
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

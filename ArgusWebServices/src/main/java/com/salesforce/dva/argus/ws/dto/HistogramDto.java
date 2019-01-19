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
     
package com.salesforce.dva.argus.ws.dto;

import com.salesforce.dva.argus.entity.Histogram;
import com.salesforce.dva.argus.entity.HistogramBucket;
import com.salesforce.dva.argus.entity.Metric;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

/**
 * The histogram DTO.
 *
 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
 */
public class HistogramDto extends TSDBEntityDto {

    //~ Instance fields ******************************************************************************************************************************
    private String displayName;
    private String units;
    private Long timestamp;
    private Long underflow = 0L;
    private Long overflow = 0L;
    private Map<HistogramBucket, Long> buckets;

    //~ Methods **************************************************************************************************************************************

    /**
     * Converts a histogram entity to a DTO.
     *
     * @param   histogram  The histogram to convert.
     *
     * @return  The corresponding DTO.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    public static HistogramDto transformToDto(Histogram histogram) {
        if (histogram == null) {
            throw new WebApplicationException("Null entity object cannot be converted to Dto object.", Status.INTERNAL_SERVER_ERROR);
        }

        HistogramDto result = createDtoObject(HistogramDto.class, histogram);

        return result;
    }

    /**
     * Converts list of histogram entity objects to list of HistogramDto objects.
     *
     * @param   histograms  List of histogram entities. Cannot be null.
     *
     * @return  List of HistogramDto objects.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    public static List<HistogramDto> transformToDto(List<Histogram> histograms) {
        if (histograms == null) {
            throw new WebApplicationException("Null entity object cannot be converted to Dto object.", Status.INTERNAL_SERVER_ERROR);
        }

        List<HistogramDto> result = new ArrayList<>();

        for (Histogram histogram : histograms) {
            result.add(transformToDto(histogram));
        }
        return result;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public Object createExample() {
        HistogramDto result = new HistogramDto();
        Map<HistogramBucket, Long> buckets = new TreeMap<>();

        buckets.put(new HistogramBucket(1,5), 10L);
        result.setDisplayName("A description of the metric");
        result.setMetric("metric");
        result.setScope("scope");

        Map<String, String> sampleTags = new HashMap<>();

        sampleTags.put("tagk", "tagv");
        result.setTags(sampleTags);
        result.setUnits("ms");
        return result;
    }

    /**
     * Returns the display name.
     *
     * @return  The display name.
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * Sets the display name.
     *
     * @param  displayName  The display name.
     */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /**
     * Returns the metric units.
     *
     * @return  The metric units.
     */
    public String getUnits() {
        return units;
    }

    /**
     * Sets the metric units.
     *
     * @param  units  The metric units.
     */
    public void setUnits(String units) {
        this.units = units;
    }

    /**
     * Returns the histogram time stamp.
     *
     * @return  The histogram time stamp.
     */
    public Long getTimestamp() {
        return timestamp;
    }

    /**
     * Specifies the histogram time stamp.
     *
     * @param  timestamp  The histogram time stamp.
     */
    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * Returns the optional overflow for the histogram.
     *
     * @return  The overflow for the histogram.
     */    
    public Long getOverflow() {
        return overflow;
    }

    /**
     * Sets the overflow for the histogram.
     *
     * @param  overflow  The overflow for the metric. Can be null or empty.
     */
    public void setOverflow(Long overflow) {
        this.overflow = overflow;
    } 
    
    /**
     * Returns the optional underflow for the histogram.
     *
     * @return  The underflow for the histogram.
     */
    public Long getUnderflow() {
        return underflow;
    }
    
    /**
     * Sets the underflow for the histogram.
     *
     * @param  underflow  The underflow for the metric. Can be null or empty.
     */
    public void setUnderflow(Long underflow) {
        this.underflow = underflow;
    }    
    
    /**
     * Returns the histogram buckets.
     *
     * @return  The histogram buckets.
     */
    public Map<HistogramBucket, Long> getBuckets() {
        return buckets;
    }

    /**
     * Sets the histogram buckets.
     *
     * @param  buckets  The histogram buckets.
     */
    public void setBuckets(Map<HistogramBucket, Long> buckets) {
        this.buckets = buckets;
    }
    
    /**
     * Add a new bucket with count to exisitng buckets
     *
     * @param  lowerBound  lower bound of bucket
     * @param  upperBound  upper bound of bucket
     * @param  count       count within this bucket
     */
    public void addBucket(float lowerBound, float upperBound, long count) {
        this.buckets.put(new HistogramBucket(lowerBound, upperBound), count);
    }
}
/* Copyright (c) 2019, Salesforce.com, Inc.  All rights reserved. */
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
	 
package com.salesforce.dva.argus.ws.dto;

import com.salesforce.dva.argus.entity.Metric;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

/**
 * The metric DTO.
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
public class MetricDto extends TSDBEntityDto {

    //~ Instance fields ******************************************************************************************************************************

    private String namespace;
    private String displayName;
    private String units;
    private Map<Long, String> datapoints;

    //~ Methods **************************************************************************************************************************************

    /**
     * Converts a metric entity to a DTO.
     *
     * @param   metric  The metric to convert.
     *
     * @return  The corresponding DTO.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    public static MetricDto transformToDto(Metric metric) {
        if (metric == null) {
            throw new WebApplicationException("Null entity object cannot be converted to Dto object.", Status.INTERNAL_SERVER_ERROR);
        }

        MetricDto result = createDtoObject(MetricDto.class, metric);

        return result;
    }

    /**
     * Converts list of alert entity objects to list of alertDto objects.
     *
     * @param   metrics  List of alert entities. Cannot be null.
     *
     * @return  List of alertDto objects.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    public static List<MetricDto> transformToDto(List<Metric> metrics) {
        if (metrics == null) {
            throw new WebApplicationException("Null entity object cannot be converted to Dto object.", Status.INTERNAL_SERVER_ERROR);
        }

        List<MetricDto> result = new ArrayList<>();

        for (Metric metric : metrics) {
            result.add(transformToDto(metric));
        }
        return result;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public Object createExample() {
        MetricDto result = new MetricDto();
        Map<Long, String> dps = new TreeMap<>();

        dps.put(System.currentTimeMillis(), "1.2");
        result.setDatapoints(dps);
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
     * Returns the namespace.
     *
     * @return  The namespace.
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Sets the namespace.
     *
     * @param  namespace  The namespace.
     */
    public void setNamespace(String namespace) {
        this.namespace = namespace;
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
     * Returns the metric data points.
     *
     * @return  The metric data points.
     */
    public Map<Long, String> getDatapoints() {
        return datapoints;
    }

    /**
     * Sets the metric data points.
     *
     * @param  datapoints  The metric data points.
     */
    public void setDatapoints(Map<Long, String> datapoints) {
        this.datapoints = datapoints;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

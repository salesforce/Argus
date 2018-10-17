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
	 
package com.salesforce.dva.argus.service.metric.transform;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.util.QueryContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * This class transforms a list of metrics in a mapping way, which means apply the same function to every metric. More specifically, an interface
 * valueMapping will be passed in , which implements how to apply a mapping function to datapoints of every metric.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class MetricMappingTransform implements Transform {

    //~ Instance fields ******************************************************************************************************************************

    private final ValueMapping valueMapping;
    private final String defaultScope;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new ReduceTransform object.
     *
     * @param  valueMapping  The valueMapping.
     */
    protected MetricMappingTransform(ValueMapping valueMapping) {
        this.valueMapping = valueMapping;
        this.defaultScope = valueMapping.name();
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public String getResultScopeName() {
        return defaultScope;
    }

    @Override
    public List<Metric> transform(QueryContext context, List<Metric> metrics) {
        return mapping(metrics);
    }

    /**
     * Mapping a list of metric, only massage its datapoints.
     *
     * @param   metrics  The list of metrics to be mapped.
     *
     * @return  A list of metrics after mapping.
     */
    private List<Metric> mapping(List<Metric> metrics) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform empty metric/metrics");
        if (metrics.isEmpty()) {
            return metrics;
        }

        List<Metric> newMetricsList = new ArrayList<Metric>();

        for (Metric metric : metrics) {
            Map<Long, Double> cleanDatapoints = cleanDPs(metric.getDatapoints());

            metric.setDatapoints(this.valueMapping.mapping(cleanDatapoints));
            newMetricsList.add(metric);
        }
        return newMetricsList;
    }

    @Override
    public List<Metric> transform(QueryContext queryContext, List<Metric> metrics, List<String> constants) {
        return mapping(metrics, constants);
    }

    private List<Metric> mapping(List<Metric> metrics, List<String> constants) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform empty metric/metrics");
        if (metrics.isEmpty()) {
            return metrics;
        }

        List<Metric> newMetricsList = new ArrayList<Metric>();

        for (Metric metric : metrics) {
            Map<Long, Double> cleanDatapoints = cleanDPs(metric.getDatapoints());

            metric.setDatapoints(this.valueMapping.mapping(cleanDatapoints, constants));
            newMetricsList.add(metric);
        }
        return newMetricsList;
    }

    private Map<Long, Double> cleanDPs(Map<Long, Double> originalDPs) {
        Map<Long, Double> cleanDPs = new TreeMap<>();

        for (Map.Entry<Long, Double> entry : originalDPs.entrySet()) {
            if (entry.getValue() == null) {
                cleanDPs.put(entry.getKey(), 0.0);
            } else {
                cleanDPs.put(entry.getKey(), entry.getValue());
            }
        }
        return cleanDPs;
    }

    @Override
    public List<Metric> transform(QueryContext queryContext, List<Metric>... listOfList) {
        throw new UnsupportedOperationException("Mapping doesn't need list of list!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

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

import java.util.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * COUNT, GROUP, UNION.
 *
 * @author  rzhang
 */
public class MetricUnionTransform implements Transform {

    //~ Static fields/initializers *******************************************************************************************************************

    /** The metric name for this transform is result. */
    public static final String RESULT_METRIC_NAME = "result";

    //~ Instance fields ******************************************************************************************************************************

    private final ValueReducer valueUnionReducer;
    private final String defaultScope;
    private final String defaultMetricName;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new ReduceTransform object.
     *
     * @param  valueUnionReducer  valueReducerOrMapping The valueMapping.
     */
    protected MetricUnionTransform(ValueReducer valueUnionReducer) {
        this.defaultScope = TransformFactory.Function.UNION.name();
        this.defaultMetricName = TransformFactory.DEFAULT_METRIC_NAME;
        this.valueUnionReducer = valueUnionReducer;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public String getResultScopeName() {
        return defaultScope;
    }

    /**
     * If constants is not null, apply mapping transform to metrics list. Otherwise, apply reduce transform to metrics list
     * @param   metrics  The metrics to transform.
     *
     * @return  The transformed metrics.
     */
    @Override
    public List<Metric> transform(QueryContext context, List<Metric> metrics) {
        return union(metrics);
    }

    /**
     * Performs a columnar union of metrics.
     *
     * @param   metrics  The metrics to merge.
     *
     * @return  The merged metrics.
     */
    public List<Metric> union(List<Metric> metrics) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform empty metric/metrics");
        if (metrics.isEmpty()) {
            return metrics;
        }

        Metric newMetric = reduce(metrics);
        Map<Long, Double> reducedDatapoints = newMetric.getDatapoints();
        Set<Long> sharedTimestamps = reducedDatapoints.keySet();
        Map<Long, Double> unionDatapoints = new TreeMap<>();

        for (Metric metric : metrics) {
            for (Map.Entry<Long, Double> entry : metric.getDatapoints().entrySet()) {
                if (!sharedTimestamps.contains(entry.getKey())) {
                    unionDatapoints.put(entry.getKey(), entry.getValue());
                }
            }
        }
        
        newMetric.addDatapoints(unionDatapoints);
        return Arrays.asList(newMetric);
    }

    /**
     * Reduce transform for the list of metrics.
     *
     * @param   metrics  The list of metrics to reduce.
     *
     * @return  The reduced metric.
     */
    protected Metric reduce(List<Metric> metrics) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform empty metric/metrics");

        /*
         * if (metrics.isEmpty()) { return new Metric(defaultScope, defaultMetricName); }
         */
        MetricDistiller distiller = new MetricDistiller();

        distiller.distill(metrics);

        Map<Long, List<Double>> collated = collate(metrics);
        Map<Long, Double> minDatapoints = reduce(collated, metrics);
        String newMetricName = distiller.getMetric() == null ? defaultMetricName : distiller.getMetric();
        Metric newMetric = new Metric(defaultScope, newMetricName);

        newMetric.setDisplayName(distiller.getDisplayName());
        newMetric.setUnits(distiller.getUnits());
        newMetric.setTags(distiller.getTags());
        newMetric.setDatapoints(minDatapoints);
        return newMetric;
    }

    private Map<Long, List<Double>> collate(List<Metric> metrics) {
        Map<Long, List<Double>> collated = new HashMap<>();

        for (Metric metric : metrics) {
            for (Map.Entry<Long, Double> point : metric.getDatapoints().entrySet()) {
                if (!collated.containsKey(point.getKey())) {
                    collated.put(point.getKey(), new ArrayList<Double>());
                }
                collated.get(point.getKey()).add(point.getValue());
            }
        }
        return collated;
    }

    private Map<Long, Double> reduce(Map<Long, List<Double>> collated, List<Metric> metrics) {
        Map<Long, Double> reducedDatapoints = new HashMap<>();

        for (Map.Entry<Long, List<Double>> entry : collated.entrySet()) {
            if (entry.getValue().size() < metrics.size()) {
                continue;
            }
            reducedDatapoints.put(entry.getKey(), this.valueUnionReducer.reduce(entry.getValue()));
        }
        return reducedDatapoints;
    }

    @Override
    public List<Metric> transform(QueryContext queryContext, List<Metric> metrics, List<String> constants) {
        throw new UnsupportedOperationException("Union transform can't be used with constants!");
    }

    @Override
    public List<Metric> transform(QueryContext queryContext, List<Metric>... listOfList) {
        throw new UnsupportedOperationException("Union doesn't need list of list");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

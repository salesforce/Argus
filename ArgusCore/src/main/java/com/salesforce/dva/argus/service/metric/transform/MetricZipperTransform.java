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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class transforms a list of metrics in a mapping way, which means apply the same function to every metric. More specifically, an interface
 * valueMapping will be passed in , which implements how to apply a mapping function to datapoints of every metric.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class MetricZipperTransform implements Transform {

    //~ Instance fields ******************************************************************************************************************************

    private final ValueZipper valueZipper;
    private final String defaultScope;
    protected static String FULLJOIN = "UNION";
    protected Boolean fulljoinIndicator = false;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new ReduceTransform object.
     *
     * @param  valueZipper  The valueZipper.
     */
    protected MetricZipperTransform(ValueZipper valueZipper) {
        this.valueZipper = valueZipper;
        this.defaultScope = valueZipper.name();
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public String getResultScopeName() {
        return defaultScope;
    }

    @Override
    public List<Metric> transform(QueryContext queryContext, List<Metric> metrics, List<String> constants) {
        SystemAssert.requireArgument(constants != null && constants.size() == 1 && FULLJOIN.equals(constants.get(0).toUpperCase()), 
        		"Zipper transforms only support UNION indicator as a constant!");
        
        fulljoinIndicator = true;
        return transform(null, metrics);
    }

    @Override
    public List<Metric> transform(QueryContext context, List<Metric> metrics) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform null metrics.");
        if (metrics.isEmpty()) {
            return metrics;
        }
        
        SystemAssert.requireArgument(metrics.size() >= 2 && metrics.get(metrics.size() - 1) != null,
            "Cannot transform without a base metric as second param!");
        
        return zip(metrics.subList(0, metrics.size() - 1), metrics.get(metrics.size() - 1));
    }

    /**
     * Merges a list of metrics.
     *
     * @param   metrics     The metrics to merge.
     * @param   baseMetric  The base metric.
     *
     * @return  The merged metrics.
     */
    public List<Metric> zip(List<Metric> metrics, Metric baseMetric) {
        SystemAssert.requireArgument(baseMetric != null, "Zipper transform requires base metric as second param!");

        List<Metric> zippedMetrics = new ArrayList<Metric>();
        Map<Long, Double> baseDatapoints = baseMetric.getDatapoints();

        for (Metric metric : metrics) {
            Map<Long, Double> originalDatapoints = metric.getDatapoints();
            Map<Long, Double> zippedDatadpoints = this.zip(originalDatapoints, baseDatapoints);

            metric.setDatapoints(zippedDatadpoints);
            zippedMetrics.add(metric);
        }
        return zippedMetrics;
    }

    /**
     * Merges data points.
     *
     * @param   originalDatapoints  The original data points.
     * @param   baseDatapoints      The base data points.
     *
     * @return  The merged data points.
     */
    public Map<Long, Double> zip(Map<Long, Double> originalDatapoints, Map<Long, Double> baseDatapoints) {
        SystemAssert.requireArgument(baseDatapoints != null && !baseDatapoints.isEmpty(),
            "Zipper transform requires valid baseDatapoints from base metric!");

        Map<Long, Double> zippedDP = new HashMap<>();

        for (Map.Entry<Long, Double> originalDP : originalDatapoints.entrySet()) {
            Long originalKey = originalDP.getKey();
            Double originalVal = originalDP.getValue();

            // if base datapoints doesn't have the key, give it null
            Double baseVal = baseDatapoints.containsKey(originalKey) ? baseDatapoints.get(originalKey) : null;

            zippedDP.put(originalKey, this.valueZipper.zip(originalVal, baseVal));
        }
        
        // if a point exists in the baseDP but does not exist in the original set, 
        // then only add it to the result when fullJoinIndicator is true.
        if(fulljoinIndicator) {
        	for (Map.Entry<Long, Double> baseDP : baseDatapoints.entrySet()) {
                Long baseDPKey = baseDP.getKey();

                if(!zippedDP.containsKey(baseDPKey)) {
                    zippedDP.put(baseDPKey, this.valueZipper.zip(null, baseDP.getValue()));
                }
            }
        }
        
        return zippedDP;
    }

    @Override
    public List<Metric> transform(QueryContext queryContext, List<Metric>... listOfList) {
        throw new UnsupportedOperationException("Zipper doesn't need list of list!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

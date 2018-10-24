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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Prepare the datapoints of every metric for count transform.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class CountTransformWrapUnion implements Transform {

    //~ Static fields/initializers *******************************************************************************************************************

    /** The default metric name. */
    public static final String DEFAULT_METRIC_NAME = "result";

    //~ Methods **************************************************************************************************************************************

    @Override
    public List<Metric> transform(QueryContext context, List<Metric> metrics) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform null metrics.");
        
        if (metrics.isEmpty()) {
            return metrics;
        }
        
        MetricDistiller distiller = new MetricDistiller();
        distiller.distill(metrics);

        String newMetricName = distiller.getMetric() == null ? TransformFactory.DEFAULT_METRIC_NAME : distiller.getMetric();
        Metric newMetric = new Metric(getResultScopeName(), newMetricName);

        newMetric.setDisplayName(distiller.getDisplayName());
        newMetric.setUnits(distiller.getUnits());
        newMetric.setTags(distiller.getTags());
        newMetric.setDatapoints(_collate(metrics));

        return Arrays.asList(newMetric);
    }
    
    private Map<Long, Double> _collate(List<Metric> metrics) {
        Map<Long, Double> collated = new HashMap<>();

        for (Metric metric : metrics) {
            for (Map.Entry<Long, Double> point : metric.getDatapoints().entrySet()) {
                if (!collated.containsKey(point.getKey())) {
                    collated.put(point.getKey(), 1.0);
                } else {
                	double oldValue = collated.get(point.getKey());
                	collated.put(point.getKey(), oldValue + 1.0);
                }
            }
        }
        return collated;
    }

    @Override
    public List<Metric> transform(QueryContext queryContext, List<Metric> metrics, List<String> constants) {
        throw new UnsupportedOperationException("COUNT Transform is not supposed to be used with a constant");
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.COUNT.name();
    }

    @Override
    public List<Metric> transform(QueryContext queryContext, List<Metric>... listOfList) {
        throw new UnsupportedOperationException("Count doesn't need list of list!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

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

/**
 * Prepare expected metrics for group transform.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class GroupTransformWrapUnion implements Transform {

    //~ Static fields/initializers *******************************************************************************************************************

    /** The default metric name for results. */
    public static final String DEFAULT_METRIC_NAME = "result";
    private static final String INCLUSIVE = "inclusive";
    private static final String EXCLUSIVE = "exclusive";

    //~ Methods **************************************************************************************************************************************

    @Override
    public List<Metric> transform(QueryContext context, List<Metric> metrics) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform null metric/metrics");
        if (metrics.isEmpty()) {
            return metrics;
        }

        Transform unionTransform = new MetricUnionTransform(new UnionValueUnionReducer());

        return unionTransform.transform(null, metrics);
    }

    @Override
    public List<Metric> transform(QueryContext queryContext, List<Metric> metrics, List<String> constants) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform null metric/metrics");
        if (metrics.isEmpty()) {
            return metrics;
        }
        SystemAssert.requireArgument(constants != null && constants.size() == 2,
            "Group transform require regex and type, only exactly two constants allowed, regex and type");
        SystemAssert.requireArgument(!"".equals(constants.get(0)), "expression can't be an empty string");
        SystemAssert.requireArgument(INCLUSIVE.equals(constants.get(1)) || EXCLUSIVE.equals(constants.get(1)), "Input type value is not correct.");

        String expr = constants.get(0);
        String type = constants.get(1);
        List<Metric> matchMetrics = filterMetrics(metrics, expr, type);

        return transform(null, matchMetrics);
    }

    private List<Metric> filterMetrics(List<Metric> metrics, String expr, String type) {
        List<Metric> matchMetricList = new ArrayList<Metric>();

        for (Metric metric : metrics) {
            String name = metric.getIdentifier();
            boolean isMatch = name.matches(expr);

            if (isMatch && type.equals(INCLUSIVE)) {
                matchMetricList.add(metric);
            } else if (!isMatch && type.equals(EXCLUSIVE)) {
                matchMetricList.add(metric);
            }
        }
        return matchMetricList;
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.GROUP.name();
    }

    @Override
    public List<Metric> transform(QueryContext queryContext, List<Metric>... listOfList) {
        throw new UnsupportedOperationException("Group doesn't need list of list!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

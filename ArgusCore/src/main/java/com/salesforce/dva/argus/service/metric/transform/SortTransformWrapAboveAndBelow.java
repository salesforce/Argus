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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Call either Below transform or Above transform according to order input.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class SortTransformWrapAboveAndBelow implements Transform {

    //~ Static fields/initializers *******************************************************************************************************************

    /** Sort descending. */
    private static final String DES = "descending";
    /** Sort ascending. */
    private static final String ASC = "ascending";

    //~ Methods **************************************************************************************************************************************

    @Override
    public List<Metric> transform(QueryContext context, List<Metric> metrics) {
        throw new UnsupportedOperationException("Sort transform need constants input.");
    }

    @Override
    public List<Metric> transform(QueryContext queryContext, List<Metric> metrics, List<String> constants) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform null metric/metrics");
        SystemAssert.requireArgument(constants != null && constants.size() >= 2 && constants.size() <= 3,
            "Sort transform require type and order. Limit is optional.");
        if (metrics.isEmpty()) {
            return metrics;
        }

        // check type and order are valid input
        Set<String> typeSet = new HashSet<String>(Arrays.asList("maxima", "minima", "name", "dev"));
        Set<String> orderSet = new HashSet<String>(Arrays.asList(ASC, DES));

        if (constants.size() == 2) { // if only two constants provided, it must include type and order
            SystemAssert.requireArgument(typeSet.contains(constants.get(0)), "Please provide a valid type!");
            SystemAssert.requireArgument(orderSet.contains(constants.get(1)), "Please provide a valid order!");
            constants.add(0, String.valueOf(Integer.MAX_VALUE)); // Adding limit
        } else if (constants.size() == 3) { // if all three constants provided, check them one by one
            try {
                Integer.parseInt(constants.get(0));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Please provide a valid integral value for limit!", e);
            }
            SystemAssert.requireArgument(typeSet.contains(constants.get(1)), "Please provide a valid type!");
            SystemAssert.requireArgument(orderSet.contains(constants.get(2)), "Please provide a valid order!");
        }

        // remove order, now the constants include limit and type which are consistent with constants of filter transform
        String order = constants.get(constants.size() - 1);

        constants.remove(constants.size() - 1);

        Transform sortTransform;

        if (order.equals(ASC)) {
            sortTransform = new MetricFilterWithInteralReducerTransform(new LowestValueFilter());
            // sortTransform = transformFactory.createMetricFilterWithInternalReducerTransform(Function.LOWEST);
        } else if (order.equals(DES)) {
            sortTransform = new MetricFilterWithInteralReducerTransform(new HighestValueFilter());
            // sortTransform = transformFactory.createMetricFilterWithInternalReducerTransform(Function.HIGHEST);
        } else {
            throw new UnsupportedOperationException("Only ASC or DES are suppored for ordering option.");
        }
        return sortTransform.transform(null, metrics, constants);
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.SORT.name();
    }

    @Override
    public List<Metric> transform(QueryContext queryContext, List<Metric>... listOfList) {
        throw new UnsupportedOperationException("Sort transform doesn't support list of metric list!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

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

import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import com.salesforce.dva.argus.system.SystemAssert;
import org.apache.commons.math.stat.descriptive.rank.Percentile;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Removes data points from metrics if their evaluated value is below a limit.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class CullBelowValueMapping implements ValueMapping {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final String PERCENTILE = "percentile";
    private static final String VALUE = "value";

    //~ Methods **************************************************************************************************************************************

    @Override
    public Map<Long, String> mapping(Map<Long, String> originalDatapoints) {
        throw new UnsupportedOperationException("Cull Below Transform needs a limit and a type.");
    }

    @Override
    public Map<Long, String> mapping(Map<Long, String> originalDatapoints, List<String> constants) {
        SystemAssert.requireArgument(constants != null, "Moving Average Transform needs a window size of time interval");
        SystemAssert.requireArgument(constants.size() == 2, "Cull Below Transform must provide exactly 2 constants which are limit and type.");

        final Double limit = Double.parseDouble(constants.get(0));
        String type = constants.get(1);

        SystemAssert.requireArgument(type.equals(PERCENTILE) || type.equals(VALUE), "Only percentil and value is allowed for type input.");

        final Double pivot = type.equals(PERCENTILE) ? findPivot(originalDatapoints, limit) : limit;
        Predicate<Map.Entry<Long, String>> isBelow = new Predicate<Map.Entry<Long, String>>() {

                @Override
                public boolean apply(Map.Entry<Long, String> datapoint) {
                    return Double.parseDouble(datapoint.getValue()) >= pivot;
                }
            };

        Map<Long, String> result = new HashMap<Long, String>();

        result.putAll(Maps.filterEntries(originalDatapoints, isBelow));
        return result;
    }

    /*
     * If type is percentile, find out the  estimate of the limit(th) percentile in the datapoint sorted values. Then execute the same as type is
     * value. That means to cull the elements greater than value or pivotValue prerequisite: array must be sorted
     */
    private Double findPivot(Map<Long, String> datapoints, Double limit) {
        double[] doubleValues = new double[datapoints.size()];
        int k = 0;

        for (Map.Entry<Long, String> entry : datapoints.entrySet()) {
            doubleValues[k] = Double.parseDouble(entry.getValue());
            k++;
        }
        Arrays.sort(doubleValues);

        double pivotValue = Double.MAX_VALUE;

        try {
            pivotValue = new Percentile().evaluate(doubleValues, (double) limit);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Please provide a valid percentile number!");
        }
        return pivotValue;
    }

    @Override
    public String name() {
        return TransformFactory.Function.CULL_BELOW.name();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

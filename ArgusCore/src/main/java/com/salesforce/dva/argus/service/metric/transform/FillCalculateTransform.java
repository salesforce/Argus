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

import com.google.common.primitives.Doubles;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.util.QueryContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.math3.stat.descriptive.rank.Percentile;

/**
 * Creates a constant line based on the calculated value.<br>
 * <tt>FILL_CALCULATE(&lt;expr&gt;, &lt;interval&gt;, &lt;interval&gt;, &lt;constant&gt;)</tt>
 *
 * @author  Jigna Bhatt(jbhatt@salesforce.com)
 */
public class FillCalculateTransform implements Transform {

    //~ Methods **************************************************************************************************************************************

    private static Map<Long, Double> fillCalculateMetricTransform(Metric metric, String calculationType) {
        // Calculate min, max, avg, dev, or a percentile value
        Double result = calculateResult(metric, calculationType);

        // return a new time series with the constant values for each time stamp
        Map<Long, Double> resultMap = new TreeMap<>();

        for (Map.Entry<Long, Double> entry : metric.getDatapoints().entrySet()) {
            Long timestamp = entry.getKey();

            resultMap.put(timestamp, result);
        }
        return resultMap;
    }

    private static Double calculateResult(Metric metric, String calculationType) {
        // Find the values from metric
        List<Double> valueList = new ArrayList<>(metric.getDatapoints().values());
        Double result = null;

        // if percentile transform requested, parse the string p0...p100.
        String rex = "^[pP](100|[0-9]{1,2})$";
        Pattern myPattern = Pattern.compile(rex);
        Matcher matcher = myPattern.matcher(calculationType);

        if (matcher.matches()) {
            Integer target = Integer.valueOf(matcher.group(1));

            result = new Percentile().evaluate(Doubles.toArray(valueList), target);
        } else {
            switch (calculationType) {
                case "min":

                    MinValueReducer minr = new MinValueReducer();

                    result = minr.reduce(valueList);
                    break;
                case "max":

                    MaxValueReducer maxr = new MaxValueReducer();

                    result = maxr.reduce(valueList);
                    break;
                case "avg":

                    AverageValueReducer avgr = new AverageValueReducer();

                    result = avgr.reduce(valueList);
                    break;
                case "dev":
                default:
                    throw new UnsupportedOperationException("Deviation Transform with Fill_Calculate is not yet supported!");
            }
        }
        return result;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public List<Metric> transform(QueryContext context, List<Metric> metrics) {
        throw new UnsupportedOperationException("Fill Transform need a type!");
    }

    @Override
    public List<Metric> transform(QueryContext queryContext, List<Metric> metrics, List<String> constants) {
        List<Metric> fillCalculateMetricList = new ArrayList<Metric>();

        SystemAssert.requireArgument(metrics != null, "Cannot transform null or empty metrics!");
        SystemAssert.requireArgument(constants != null && !constants.isEmpty(), "Fill_Calculate Transform needs a type!");
        SystemAssert.requireArgument(!constants.isEmpty(), "Fill Transform needs at least a type!");
        SystemAssert.requireArgument(constants.size() <= 3, "Fill Transform needs an interval, an offset and a type!");

        String calculationType = constants.get(0);

        for (Metric metric : metrics) {
            Metric newMetric = new Metric(metric);

            newMetric.setDatapoints(fillCalculateMetricTransform(metric, calculationType));
            fillCalculateMetricList.add(newMetric);
        }

        // If interval and offset are provided, run additional Fill transform on the list of metrics
        if (constants.size() > 1 && constants.size() <= 3) {
            List<Metric> fillCalculateMetricListWithOffset = new ArrayList<Metric>();

            for (Metric metric : fillCalculateMetricList) {
                Transform fillTransform = new FillTransform();
                Double calculateResult = calculateResult(metric, calculationType);

                // replace the 3rd value of constants with results.
                List<String> newConstants = new ArrayList<String>();

                newConstants.add(constants.get(1));
                newConstants.add(constants.get(2));
                newConstants.add(String.valueOf(calculateResult));
                newConstants.add(String.valueOf(System.currentTimeMillis()));
                newConstants.add("false");

                List<Metric> singleMetric = new ArrayList<>();

                singleMetric.add(metric);
                fillCalculateMetricListWithOffset.addAll(fillTransform.transform(null, singleMetric, newConstants));
            }
            return fillCalculateMetricListWithOffset;
        }
        return fillCalculateMetricList;
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.FILL_CALCULATE.name();
    }

    @Override
    public List<Metric> transform(QueryContext queryContext, List<Metric>... listOfList) {
        throw new UnsupportedOperationException("Fill_Calculate doesn't need list of list!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

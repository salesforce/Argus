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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Creates a constant line based on the calculated value.<br/>
 * <tt>FILL_CALCUALTE(<expr>, <interval>, <interval>, <constant>)</tt>
 *
 * @param   metrics   The list of metrics to evaluate. Cannot be null or empty.
 * @param   interval  The interval at which to fill data points. For example 10m would create data points every 10 minutes if a gap greater than 10
 *                    minutes was encountered.
 * @param   offset    The offset for the created data points. For example, 1m would shift the timestamp of the created data point 1 minute forward.
 * @param   value     The value of the generated data points.
 * @param   start     The start time. Must occur prior to the end time. Cannot be null.
 * @param   end       The end time. Must occur later to the start time. Cannot be null.
 * @param   interval  The interval at which to fill data points. For example 10m would create data points every 10 minutes if a gap greater than 10
 * @param   offset    The offset for the created data points. For example, 1m would shift the timestamp of the created data point 1 minute forward.
 * @param   value     The value of the generated data points.
 *
 * @author  Jigna Bhatt(jbhatt@salesforce.com)
 */
public class FillCalculateTransform implements Transform {

    //~ Methods **************************************************************************************************************************************

    private static Map<Long, String> fillCalculateMetricTransform(Metric metric, String calculationType) {
        // Calculate min, max, avg, dev, or a percentile value
        String result = calculateResult(metric, calculationType);

        // return a new time series with the constant values for each time stamp
        Map<Long, String> resultMap = new TreeMap<>();

        for (Map.Entry<Long, String> entry : metric.getDatapoints().entrySet()) {
            Long timestamp = entry.getKey();

            resultMap.put(timestamp, result);
        }
        return resultMap;
    }

    private static String calculateResult(Metric metric, String calculationType) {
        // Find the values from metric
        List<String> valueList = new ArrayList<>(metric.getDatapoints().values());
        String result = null;

        // if percentile transform requested, parse the string p0...p100.
        String rex = "^[pP](100|[0-9]{1,2})$";
        Pattern myPattern = Pattern.compile(rex);
        Matcher matcher = myPattern.matcher(calculationType);

        if (matcher.matches()) {
            Integer target = Integer.valueOf(matcher.group(1));

            result = PercentileTransform.calculateNthPercentile(valueList, target);
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
    public List<Metric> transform(List<Metric> metrics) {
        throw new UnsupportedOperationException("Fill Transform need a type!");
    }

    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
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
                String calculateResult = calculateResult(metric, calculationType);

                // replace the 3rd value of constants with results.
                List<String> newConstants = new ArrayList<String>();

                newConstants.add(constants.get(1));
                newConstants.add(constants.get(2));
                newConstants.add(calculateResult);

                List<Metric> singleMetric = new ArrayList<>();

                singleMetric.add(metric);
                fillCalculateMetricListWithOffset.addAll(fillTransform.transform(singleMetric, newConstants));
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
    public List<Metric> transform(List<Metric>... listOfList) {
        throw new UnsupportedOperationException("Fill_Calculate doesn't need list of list!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

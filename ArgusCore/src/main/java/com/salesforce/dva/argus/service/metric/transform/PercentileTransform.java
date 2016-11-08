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
import com.salesforce.dva.argus.service.metric.MetricReader;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Calculates a percentile transform.
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
public class PercentileTransform implements Transform {

    //~ Methods **************************************************************************************************************************************

    /**
     * Calculates nth percentile of a set of values using the Nearest Neighbor Method.
     *
     * @param   values  The values to evaluate.
     * @param   n       The n-th percentile to calculate.
     *
     * @return  The nth-percentile value.
     */
    static String calculateNthPercentile(List<String> values, int n) {
        String[] valuesArr = new String[values.size()];

        valuesArr = values.toArray(valuesArr);
        Arrays.sort(valuesArr);

        int ordinalRank = (int) Math.ceil(n * values.size() / 100.0);

        return valuesArr[ordinalRank - 1];
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public List<Metric> transform(List<Metric> metrics) {
        throw new UnsupportedOperationException("Percentile Transform needs a constant window.");
    }

    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform null or empty metrics");
        if (metrics.isEmpty()) {
            return metrics;
        }
        SystemAssert.requireArgument(constants != null && constants.size() == 2,
            "Percentile Transform must provide exactly 2 constants. n -> The nth percentile to calculate, windowSize -> Window size in seconds");

        long windowInSeconds = 0;
        int n = 0;

        try {
            n = Integer.parseInt(constants.get(0));
            SystemAssert.requireArgument(n > 0 && n < 100, "For Percentile Transform, 0 < n < 100.");
            windowInSeconds = getWindowInSeconds(constants.get(1));
        } catch (NumberFormatException nfe) {
            throw new SystemException("Illegal window size supplied to percentile transform", nfe);
        }
        for (Metric metric : metrics) {
            metric.setDatapoints(_calculatePercenTileSeries(metric.getDatapoints(), n, windowInSeconds));
        }
        return metrics;
    }

    private long getWindowInSeconds(String window) {
        MetricReader.TimeUnit timeunit = null;

        try {
            timeunit = MetricReader.TimeUnit.fromString(window.substring(window.length() - 1));

            long timeDigits = Long.parseLong(window.substring(0, window.length() - 1));

            return timeDigits * timeunit.getValue() / 1000;
        } catch (Exception t) {
            return Long.parseLong(window);
        }
    }

    private Map<Long, String> _calculatePercenTileSeries(Map<Long, String> originalDatapoints, int n, long windowInSeconds) {
        Map<Long, String> transformedDatapoints = new HashMap<>();
        Map<Long, String> sortedDatapoints = new TreeMap<>(originalDatapoints);
        List<String> values = new ArrayList<>();
        Long windowStart = 0L;

        for (Map.Entry<Long, String> entry : sortedDatapoints.entrySet()) {
            Long timestamp = entry.getKey();
            String value = entry.getValue();

            if (values.isEmpty()) {
                values.add(value);
                windowStart = timestamp;
            } else {
                if (timestamp > windowStart + windowInSeconds * 1000) {
                    transformedDatapoints.put(windowStart, calculateNthPercentile(values, n));
                    values.clear();
                    windowStart = timestamp;
                }
                values.add(value);
            }
        }
        if (!values.isEmpty()) {
            transformedDatapoints.put(windowStart, calculateNthPercentile(values, n));
        }
        return transformedDatapoints;
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.PERCENTILE.name();
    }

    @Override
    public List<Metric> transform(List<Metric>... listOfList) {
        throw new UnsupportedOperationException("This class is deprecated.");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

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

import com.google.common.collect.TreeMultiset;
import com.salesforce.dva.argus.service.metric.MetricReader;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Calculates the Nth percentile. If a window size is specified, each metric will be evaluated individually using that window. Otherwise, the set of
 * data points across metrics at each given timestamp are evaluated resulting in a single metric result.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class PercentileValueReducerOrMapping implements ValueReducerOrMapping {

    //~ Static fields/initializers *******************************************************************************************************************

    private static Double percentile = Double.MAX_VALUE;
    private static String windowSize = "";

    //~ Instance fields ******************************************************************************************************************************

    private Logger _logger = LoggerFactory.getLogger(getClass());

    //~ Methods **************************************************************************************************************************************

    @Override
    public String reduce(List<String> values) {
        throw new UnsupportedOperationException("Percentile Transform with reducer is not supposed to be used without a constant");
    }

    @Override
    public String reduce(List<String> values, List<String> constants) {
        parseConstants(constants);
        return _calculateNthPercentile(values, percentile);
    }

    private void parseConstants(List<String> constants) {
        SystemAssert.requireArgument(constants != null && !constants.isEmpty(),
            "Percentile Transform must provide at least percentile to calculate.");
        SystemAssert.requireArgument(Double.parseDouble(constants.get(0)) > 0.0 && Double.parseDouble(constants.get(0)) < 100.0,
            "For Percentile Transform, 0.0 < percentile < 100.0.");
        PercentileValueReducerOrMapping.percentile = Double.parseDouble(constants.get(0));
        if (constants.size() > 1) {
            try {
                PercentileValueReducerOrMapping.windowSize = constants.get(1);
            } catch (NumberFormatException nfe) {
                throw new SystemException("Illegal window size supplied to percentile transform", nfe);
            }
        }
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

    @Override
    public Map<Long, String> mapping(Map<Long, String> originalDatapoints) {
        throw new UnsupportedOperationException("Percentile Transform with mapping is not supposed to be used without a constant");
    }

    @Override
    public Map<Long, String> mapping(Map<Long, String> originalDatapoints, List<String> constants) {
        parseConstants(constants);
        return _calculateNthPercentileForOneMetric(originalDatapoints, percentile, getWindowInSeconds(windowSize));
    }

    @Override
    public String name() {
        return TransformFactory.Function.PERCENTILE.name();
    }

    private Map<Long, String> _calculateNthPercentileForOneMetric(Map<Long, String> originalDatapoints, Double percentileValue,
        long windowInSeconds) {
        Map<Long, String> percentileDatapoints = new TreeMap<>();

        for (Map.Entry<Long, String> entry : originalDatapoints.entrySet()) {
            if (entry.getValue() == null || entry.getValue().equals("")) {
                entry.setValue(String.valueOf(0.0));
            }
        }

        Long[] timestamps = new Long[originalDatapoints.size()];

        originalDatapoints.keySet().toArray(timestamps);

        // TreeSet allowing duplicate elements.
        TreeMultiset<String> values = TreeMultiset.create(new Comparator<String>() {

                @Override
                public int compare(String s1, String s2) {
                    return Double.compare(Double.parseDouble(s1), Double.parseDouble(s2));
                }
            });

        long start = System.currentTimeMillis();

        values.add(originalDatapoints.get(timestamps[0]));
        if (timestamps.length == 1) {
            percentileDatapoints.put(timestamps[0], _calculateNthPercentile(values, percentileValue));
        }

        Long firstTimestamp = timestamps[0];

        for (int head = 1, tail = 0; head < timestamps.length; head++) {
            // When moving window, maintain a invariant that timestamps[head] - timestamps[end] < windowSize
            // if timestamps[head] - timestamps[end] == windowSize, some points need to be kicked off
            // For first window, exclude timestamps[head]
            // For a regular window, exclude timestamps[tail]
            if (tail == 0) {
                while (timestamps[head] - windowInSeconds * 1000 < firstTimestamp) {
                    // run out of points before hitting the end of first window
                    if (head >= timestamps.length - 1) {
                        break;
                    }

                    // do a partial calculation if not enough points
                    percentileDatapoints.put(timestamps[head - 1], _calculateNthPercentile(values, percentileValue));
                    values.add(originalDatapoints.get(timestamps[head]));
                    head++;
                }
                percentileDatapoints.put(timestamps[head - 1], _calculateNthPercentile(values, percentileValue));
            }
            values.add(originalDatapoints.get(timestamps[head]));
            while (timestamps[tail] <= timestamps[head] - windowInSeconds * 1000) {
                values.remove(originalDatapoints.get(timestamps[tail]));
                tail++;
            }
            percentileDatapoints.put(timestamps[head], _calculateNthPercentile(values, percentileValue));
        }
        _logger.debug("Time to calculate percentile = " + (System.currentTimeMillis() - start) + "ms");
        return percentileDatapoints;
    }

    private String _calculateNthPercentile(List<String> values, Double percentileValue) {
        Collections.sort(values, new Comparator<String>() {

                @Override
                public int compare(String s1, String s2) {
                    double d1 = Double.parseDouble(s1);
                    double d2 = Double.parseDouble(s2);

                    return Double.compare(d1, d2);
                }
            });

        int ordinalRank = (int) Math.ceil(percentileValue * values.size() / 100.0);

        return values.get(ordinalRank - 1);
    }

    // O(n) operation to return percentile value from a sorted list.
    private String _calculateNthPercentile(TreeMultiset<String> values, Double percentileValue) {
        int ordinalRank = (int) Math.ceil(percentileValue * values.size() / 100.0);
        int index = 1;

        for (String value : values) {
            if (index++ == ordinalRank) {
                return value;
            }
        }
        throw new SystemException("This should never happen.");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

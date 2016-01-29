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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Calculates the moving average.
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
public class MovingAverageTransform implements Transform {

    //~ Instance fields ******************************************************************************************************************************

    private final Logger _logger = LoggerFactory.getLogger(MovingAverageTransform.class);

    //~ Methods **************************************************************************************************************************************

    @Override
    public List<Metric> transform(List<Metric> metrics) {
        throw new UnsupportedOperationException("Moving Average Transform needs a window size either as fixed number" +
            " of past points or time interval");
    }

    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform null or empty metrics");
        if (metrics.isEmpty()) {
            return metrics;
        }
        SystemAssert.requireArgument(constants != null && constants.size() == 1,
            "Moving Average Transform " +
            "must provide exactly 1 constant. windowSize -> Either fixed number of past points or time interval");

        String window = constants.get(0);
        MetricReader.TimeUnit timeunit = null;

        try {
            long windowSizeInSeconds = 0;

            timeunit = MetricReader.TimeUnit.fromString(window.substring(window.length() - 1));

            long timeDigits = Long.parseLong(window.substring(0, window.length() - 1));

            windowSizeInSeconds = timeDigits * timeunit.getValue() / 1000;
            for (Metric metric : metrics) {
                metric.setDatapoints(_calculateMovingAverageUsingTimeInterval(metric.getDatapoints(), windowSizeInSeconds));
            }
            return metrics;
        } catch (Exception t) {
            long windowSize = Long.parseLong(window);

            for (Metric metric : metrics) {
                metric.setDatapoints(_calculateMovingAverageUsingFixedNoOfPastPoints(metric.getDatapoints(), windowSize));
            }
            return metrics;
        }
    }

    private Map<Long, String> _calculateMovingAverageUsingTimeInterval(Map<Long, String> originalDatapoints, long windowSizeInSeconds) {
        SystemAssert.requireArgument(windowSizeInSeconds != 0, "Time Interval cannot be 0 for Moving Average Transform");

        Map<Long, String> transformedDatapoints = new TreeMap<Long, String>();
        Map<Long, String> sortedDatapoints = new TreeMap<Long, String>(originalDatapoints);
        Long[] timestamps = new Long[sortedDatapoints.size()];

        sortedDatapoints.keySet().toArray(timestamps);

        double sum = 0.0;

        try {
            sum = Double.parseDouble(sortedDatapoints.get(timestamps[0]));
        } catch (NumberFormatException | NullPointerException e) {
            _logger.warn("Failed to parse datapointL: " + sortedDatapoints.get(timestamps[0]));
        }

        Long firstTimestamp = timestamps[0];
        int count = 1;

        for (int i = 1, j = 0; i < timestamps.length; i++) {
            if (j == 0) {
                while (timestamps[i] - windowSizeInSeconds * 1000 < firstTimestamp) {
                    try {
                        sum += Double.parseDouble(sortedDatapoints.get(timestamps[i]));
                    } catch (NumberFormatException | NullPointerException e) {
                        _logger.warn("Failed to parse datapoint: " + sortedDatapoints.get(timestamps[i]));
                    }
                    transformedDatapoints.put(timestamps[i - 1], null);
                    i++;
                    count++;
                }
                transformedDatapoints.put(timestamps[i - 1], String.valueOf(sum / count));
            }
            try {
                sum += Double.parseDouble(sortedDatapoints.get(timestamps[i]));
                while (timestamps[j] <= timestamps[i] - windowSizeInSeconds * 1000) {
                    sum = _subtractWithinWindow(sum, sortedDatapoints, timestamps[j], timestamps[i]);
                    count--;
                    j++;
                }
            } catch (NumberFormatException | NullPointerException e) {
                _logger.warn("Failed to parse datapoint: " + sortedDatapoints.get(timestamps[i]));
            }
            count++;
            transformedDatapoints.put(timestamps[i], String.valueOf(sum / count));
        }
        return transformedDatapoints;
    }

    private double _subtractWithinWindow(double sum, Map<Long, String> sortedDatapoints, long end, long start) {
        try {
            sum -= Double.parseDouble(sortedDatapoints.get(end));
        } catch (NumberFormatException | NullPointerException e) {
            _logger.warn("Failed to parse datapoint: " + sortedDatapoints.get(start));
        }
        return sum;
    }

    private Map<Long, String> _calculateMovingAverageUsingFixedNoOfPastPoints(Map<Long, String> originalDatapoints, long window) {
        SystemAssert.requireArgument(window != 0, "Window cannot be 0 for Moving Average Transform");

        Map<Long, String> transformedDatapoints = new TreeMap<Long, String>();
        Map<Long, String> sortedDatapoints = new TreeMap<Long, String>(originalDatapoints);
        double sum = 0.0, firstValueInInterval = 0.0;
        Long[] timestamps = new Long[sortedDatapoints.size()];

        sortedDatapoints.keySet().toArray(timestamps);
        for (int i = 0, j = 0; i < timestamps.length; i++) {
            if (i + 1 < window) {
                try {
                    sum += Double.parseDouble(sortedDatapoints.get(timestamps[i]));
                } catch (NumberFormatException | NullPointerException e) {
                    _logger.warn("Failed to parse datapoint: " + sortedDatapoints.get(timestamps[i]) + "Skipping this one.");
                }
                transformedDatapoints.put(timestamps[i], null);
            } else {
                try {
                    sum += Double.parseDouble(sortedDatapoints.get(timestamps[i]));
                    sum -= firstValueInInterval;
                    firstValueInInterval = Double.parseDouble(sortedDatapoints.get(timestamps[j]));
                } catch (NumberFormatException | NullPointerException e) {
                    _logger.warn("Failed to parse datapoint: " + sortedDatapoints.get(timestamps[i]) + "Skipping this one.");
                }
                transformedDatapoints.put(timestamps[i], String.valueOf(sum / window));
                j++;
            }
        }
        return transformedDatapoints;
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.MOVINGAVERAGE.name();
    }

    @Override
    public List<Metric> transform(List<Metric>... listOfList) {
        throw new UnsupportedOperationException("This class is deprecated!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

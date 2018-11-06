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
import com.salesforce.dva.argus.service.DiscoveryService;
import com.salesforce.dva.argus.service.metric.MetricReader;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemException;
import com.salesforce.dva.argus.util.QueryContext;
import com.salesforce.dva.argus.util.QueryUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Creates additional data points to fill gaps.<br>
 * <tt>FILL(&lt;expr&gt;, &lt;interval&gt;, &lt;interval&gt;, &lt;constant&gt;)</tt>
 *
 * @author  Ruofan Zhang(rzhang@salesforce.com)
 */
public class FillTransform implements Transform {

    //~ Static fields/initializers *******************************************************************************************************************

    /** The default metric name for results. */
    public static final String DEFAULT_METRIC_NAME = "result";

    /** The default metric scope for results. */
    public static final String DEFAULT_SCOPE_NAME = "scope";
    
    public static final int MAX_DATAPOINTS_FOR_FILL = DiscoveryService.MAX_DATAPOINTS_PER_RESPONSE;    

    //~ Methods **************************************************************************************************************************************

    private static Map<Long, Double> _fillMetricTransform(QueryContext queryContext, Metric metric, long windowSizeInSeconds, long offsetInSeconds, double value) {
    	if(metric == null || metric.getDatapoints() == null || metric.getDatapoints().isEmpty()) {
    		return Collections.emptyMap();
    	}
    	
        Map<Long, Double> filledDatapoints = new TreeMap<>();
        Map<Long, Double> sortedDatapoints = new TreeMap<>(metric.getDatapoints());
        Long[] sortedTimestamps = new Long[sortedDatapoints.size()];

        sortedDatapoints.keySet().toArray(sortedTimestamps);

        Long[] startAndEndTimestamps = QueryUtils.getStartAndEndTimesWithMaxInterval(queryContext);
        
        Long startTimestamp = startAndEndTimestamps[0]>0 ? startAndEndTimestamps[0] : sortedTimestamps[0];
        Long endTimestamp = startAndEndTimestamps[1]>0 ? startAndEndTimestamps[1] : sortedTimestamps[sortedTimestamps.length - 1];

        // create a new datapoints map propagateDatpoints, which have all the
        // expected timestamps, then fill the missing value
        int index = 1;
        int numDatapoints = 0;
        while (startTimestamp <= endTimestamp && numDatapoints++ < MAX_DATAPOINTS_FOR_FILL) {
            filledDatapoints.put(startTimestamp, sortedDatapoints.containsKey(startTimestamp) ? sortedDatapoints.get(startTimestamp) : null);
            if (index >= sortedDatapoints.size()) {
              	startTimestamp = startTimestamp + windowSizeInSeconds * 1000;
                continue;
            }
            if ((startTimestamp + windowSizeInSeconds * 1000) < sortedTimestamps[index]) {
                startTimestamp = startTimestamp + windowSizeInSeconds * 1000;
            } else {
                startTimestamp = sortedTimestamps[index];
                index++;
            }
        }

        int newLength = filledDatapoints.size();
        List<Long> newTimestamps = new ArrayList<Long>();
        List<Double> newValues = new ArrayList<>();
        
        for (Map.Entry<Long, Double> entry : filledDatapoints.entrySet()) {
            newTimestamps.add(entry.getKey());
            newValues.add(entry.getValue());
        }
        for (int i = 0; i < newLength; i++) {
            if (newValues.get(i) != null) {
                continue;
            } else {
                filledDatapoints.put(newTimestamps.get(i) + offsetInSeconds * 1000, value);
            }
        }

        Map<Long, Double> cleanFilledDatapoints = new TreeMap<>();

        for (Map.Entry<Long, Double> entry : filledDatapoints.entrySet()) {
            if (entry.getValue() != null) {
                cleanFilledDatapoints.put(entry.getKey(), entry.getValue());
            }
        }
        return cleanFilledDatapoints;
    }

    private static long _parseTimeIntervalInSeconds(String interval) {
        MetricReader.TimeUnit timeunit = null;

        try {
            long intervalInSeconds = 0;

            timeunit = MetricReader.TimeUnit.fromString(interval.substring(interval.length() - 1));

            long timeDigits = Long.parseLong(interval.substring(0, interval.length() - 1));

            intervalInSeconds = timeDigits * timeunit.getValue() / 1000;
            return intervalInSeconds;
        } catch (Exception t) {
            throw new SystemException("Please input a valid time interval!");
        }
    }

    //~ Methods **************************************************************************************************************************************

    private List<Metric> _fillLine(List<String> constants, long relativeTo) {
        SystemAssert.requireArgument(constants != null && constants.size() == 5,
            "Line Filling Transform needs 5 constants (start, end, interval, offset, value)!");

        long startTimestamp = _parseStartAndEndTimestamps(constants.get(0), relativeTo);
        long endTimestamp = _parseStartAndEndTimestamps(constants.get(1), relativeTo);
        long windowSizeInSeconds = _parseTimeIntervalInSeconds(constants.get(2));
        long offsetInSeconds = _parseTimeIntervalInSeconds(constants.get(3));
        double value = Double.parseDouble(constants.get(4));

        SystemAssert.requireArgument(startTimestamp < endTimestamp, "End time must occure later than start time!");
        SystemAssert.requireArgument(windowSizeInSeconds >= 0, "Window size must be greater than ZERO!");

        // snapping start and end time
        long startSnapping = startTimestamp % (windowSizeInSeconds * 1000);
        startTimestamp = startTimestamp - startSnapping;
        long endSnapping = endTimestamp % (windowSizeInSeconds * 1000);
        endTimestamp = endTimestamp - endSnapping;

        Metric metric = new Metric(DEFAULT_SCOPE_NAME, DEFAULT_METRIC_NAME);
        Map<Long, Double> filledDatapoints = new TreeMap<>();
        int numDatapoints = 0;
        
        while (startTimestamp < endTimestamp && numDatapoints++ < MAX_DATAPOINTS_FOR_FILL) {
            filledDatapoints.put(startTimestamp, value);
            startTimestamp += windowSizeInSeconds * 1000;
        }
        filledDatapoints.put(endTimestamp, value);

        Map<Long, Double> newFilledDatapoints = new TreeMap<>();

        for (Map.Entry<Long, Double> entry : filledDatapoints.entrySet()) {
            newFilledDatapoints.put(entry.getKey() + offsetInSeconds * 1000, entry.getValue());
        }
        metric.setDatapoints(newFilledDatapoints);

        List<Metric> lineMetrics = new ArrayList<Metric>();

        lineMetrics.add(metric);
        return lineMetrics;
    }

    private long _parseStartAndEndTimestamps(String timeStr, long relativeTo) {
        if (timeStr == null || timeStr.isEmpty()) {
            return relativeTo;
        }
        try {
            if (timeStr.charAt(0) == '-') {
                long timeToDeductInSeconds = _parseTimeIntervalInSeconds(timeStr.substring(1));

                return (relativeTo - timeToDeductInSeconds * 1000);
            }
            return Long.parseLong(timeStr);
        } catch (NumberFormatException nfe) {
            throw new SystemException("Could not parse time.", nfe);
        }
    }

    @Override
    public List<Metric> transform(QueryContext context, List<Metric> metrics) {
        throw new UnsupportedOperationException("Fill Transform needs interval, offset and value!");
    }

    @Override
    public List<Metric> transform(QueryContext queryContext, List<Metric> metrics, List<String> constants) {
    	
    	// Last 2 constants for FILL Transform are added by MetricReader. 
    	// The last constant is used to distinguish between FILL(expr, #constants#) and FILL(#constants#).
    	// The second last constant is the timestamp using which relative start and end timestamps 
    	// should be calculated for fillLine ( FILL(#constants#) ).
    	
    	boolean constantsOnly = false;
    	if(constants != null && !constants.isEmpty()) {
    		constantsOnly = Boolean.parseBoolean(constants.get(constants.size()-1));
    		constants.remove(constants.size()-1);
    	}
    	
    	long relativeTo = System.currentTimeMillis();
    	if(constants != null && !constants.isEmpty()) {
    		relativeTo = Long.parseLong(constants.get(constants.size()-1));
    		constants.remove(constants.size() - 1);
    	}
    	
        if (constantsOnly) {
            return _fillLine(constants, relativeTo);
        }
        
        SystemAssert.requireArgument(metrics != null, "Cannot transform null metrics list!");
        SystemAssert.requireArgument(constants != null && constants.size() == 3, 
        		"Fill Transform needs exactly three constants: interval, offset, value");

        String interval = constants.get(0);
        long windowSizeInSeconds = _parseTimeIntervalInSeconds(interval);

        SystemAssert.requireArgument(windowSizeInSeconds >= 0, "Window size must be greater than ZERO!");

        String offset = constants.get(1);
        long offsetInSeconds = _parseTimeIntervalInSeconds(offset);
        double value = Double.parseDouble(constants.get(2));

        List<Metric> fillMetricList = new ArrayList<Metric>();
        for (Metric metric : metrics) {
            Metric newMetric = new Metric(metric);

            newMetric.setDatapoints(_fillMetricTransform(queryContext, metric, windowSizeInSeconds, offsetInSeconds, value));
            fillMetricList.add(newMetric);
        }
        return fillMetricList;
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.FILL.name();
    }

    @Override
    public List<Metric> transform(QueryContext queryContext, List<Metric>... listOfList) {
        throw new UnsupportedOperationException("Fill doesb't need list of list!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
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
import com.salesforce.dva.argus.util.QueryContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Forward fills gaps with the last known value at the start (earliest occurring time) of the gap.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class PropagateTransform implements Transform {

    //~ Methods **************************************************************************************************************************************

    private void _propagateMetricTransform(Metric metric, long windowSizeInSeconds) {
    	
    	// if the datapoint set is empty or has a single datapoint, return directly
    	if(metric.getDatapoints().isEmpty() || metric.getDatapoints().size() == 1) {
    		return;
    	}
    	
        Map<Long, Double> propagateDatapoints = new TreeMap<>();
        Map<Long, Double> sortedDatapoints = new TreeMap<>(metric.getDatapoints());
        Long[] sortedTimestamps = new Long[sortedDatapoints.size()];
        sortedDatapoints.keySet().toArray(sortedTimestamps);

        Long startTimestamp = sortedTimestamps[0];
        Long endTimestamp = sortedTimestamps[sortedTimestamps.length - 1];

        // create a new datapoints map propagateDatpoints, which have all the
        // expected timestamps, then fill the missing value
        int index = 1;
        while (startTimestamp <= endTimestamp) {
            propagateDatapoints.put(startTimestamp, sortedDatapoints.containsKey(startTimestamp) ? sortedDatapoints.get(startTimestamp) : null);
            if (index >= sortedDatapoints.size()) {
                break;
            }
            if ((startTimestamp + windowSizeInSeconds * 1000) < sortedTimestamps[index]) {
                startTimestamp = startTimestamp + windowSizeInSeconds * 1000;
            } else {
                startTimestamp = sortedTimestamps[index];
                index++;
            }
        }

        int newLength = propagateDatapoints.size();
        List<Long> newTimestamps = new ArrayList<Long>();
        List<Double> newValues = new ArrayList<>();

        for (Map.Entry<Long, Double> entry : propagateDatapoints.entrySet()) {
            newTimestamps.add(entry.getKey());
            newValues.add(entry.getValue());
        }

        Double prev = null;

        for (int i = 0; i < newLength; i++) {
            if (newValues.get(i) != null) {
                prev = newValues.get(i);
            } else {
                propagateDatapoints.put(newTimestamps.get(i), prev);
            }
        }
        
        metric.setDatapoints(propagateDatapoints);
    }

    private static long parseTimeIntervalInSeconds(String interval) {
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

    @Override
    public List<Metric> transform(QueryContext context, List<Metric> metrics) {
        throw new UnsupportedOperationException("Propagate Transform needs a max window size");
    }

    @Override
    public List<Metric> transform(QueryContext queryContext, List<Metric> metrics, List<String> constants) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform null or empty metrics");
        if (metrics.isEmpty()) {
            return metrics;
        }
        SystemAssert.requireArgument(constants != null && !constants.isEmpty(), "Propagate Transform needs a max window size");

        String window = constants.get(0);
        long windowSizeInSeconds = parseTimeIntervalInSeconds(window);

        for (Metric metric : metrics) {
            _propagateMetricTransform(metric, windowSizeInSeconds);
        }
        return metrics;
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.PROPAGATE.name();
    }

    @Override
    public List<Metric> transform(QueryContext queryContext, List<Metric>... listOfList) {
        throw new UnsupportedOperationException("Propagate Transform doesn't accept list of metric list!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

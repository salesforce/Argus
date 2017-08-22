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
import com.salesforce.dva.argus.service.tsdb.MetricScanner;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemException;
import java.util.ArrayList;
import java.util.Arrays;
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
    
    private void _propagateMetricTransformScanner(MetricScanner scanner, long windowSizeInSeconds, List<Metric> result) {
    	Metric m = new Metric(scanner.getMetric());
    	Map.Entry<Long, Double> dp = null;
    	if (!scanner.hasNextDP()) {
    		return;
    	}
    	
    	dp = scanner.getNextDP();
    	if (!scanner.hasNextDP()) {
    		result.add(m);
    		return;
    	}
    	
    	Map<Long, Double> propagateDatapoints = new TreeMap<>();
    	Map<Long, Double> datapoints = new TreeMap<>();
    	datapoints.put(dp.getKey(), dp.getValue());
    	List<Long> timestamps = new ArrayList<>();
    	timestamps.add(dp.getKey());
    	
    	Long startTimestamp = timestamps.get(0);
    	int index = 1;
    	
		if (!scanner.hasNextDP()) {
			result.add(m);
			return;
		}
    	do {
    		propagateDatapoints.put(startTimestamp, datapoints.containsKey(startTimestamp) ? datapoints.get(startTimestamp) : null);
    		
    		dp = scanner.getNextDP();
    		datapoints.put(dp.getKey(), dp.getValue());
    		timestamps.add(dp.getKey());
    		
    		if ((startTimestamp + windowSizeInSeconds * 1000) < timestamps.get(index)) {
    			startTimestamp = startTimestamp + windowSizeInSeconds * 1000;
    		} else {
    			startTimestamp = timestamps.get(index);
    			index++;
    		}
    	} while (scanner.hasNextDP());
		propagateDatapoints.put(startTimestamp, datapoints.containsKey(startTimestamp) ? datapoints.get(startTimestamp) : null);
    	
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
    	
    	m.setDatapoints(propagateDatapoints);
    	result.add(m);
    }
    
    private void _propagateMetricTransformPager(MetricScanner scanner, long windowSizeInSeconds, Long start, Long end, List<Metric> result) {
    	if ((scanner.getMetric().getDatapoints().isEmpty() || scanner.getMetric().getDatapoints().size() == 1) && !scanner.hasNextDP()) {
    		result.add(scanner.getMetric());
    		return;
    	}
    	TreeMap<Long, Double> propagateDatapoints = new TreeMap<>();
    	List<Long> timestamps = new ArrayList<>();
    	Map.Entry<Long, Double> next = scanner.peek();
    	int index = 1;
    	
    	if (next == null || next.getKey() > end) {
    		TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
    		// try to go before the start key to accurately generate those datapoints before it
    		Long startKey = dps.floorKey(start - 1) == null ? dps.ceilingKey(start) : dps.floorKey(start - 1);
    		// try to go past the end key to accurately generate those datapoints after it
    		Long endKey = dps.ceilingKey(end + 1) == null ? dps.floorKey(end) : dps.ceilingKey(end + 1);
    		if (startKey != null && endKey != null && startKey <= endKey) {
    			timestamps.addAll(dps.subMap(startKey, endKey + 1).keySet());
    			Long startTimestamp = timestamps.get(0);
    			Long endTimestamp = Math.min(end, timestamps.get(timestamps.size() - 1));
    			while (startTimestamp <= endTimestamp) {
    				propagateDatapoints.put(startTimestamp, dps.containsKey(startTimestamp) ? dps.get(startTimestamp) : null);
    				if (index >= timestamps.size()) {
    					break;
    				}
    				if ((startTimestamp + windowSizeInSeconds * 1000) < timestamps.get(index)) {
    					startTimestamp += windowSizeInSeconds * 1000;
    				} else {
    					startTimestamp = timestamps.get(index);
    					index++;
    				}
    			}
    		}
    	} else {
    		Long startTimestamp = null;
    		if (next.getKey() > start) {
    			TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
    			Long startKey = dps.floorKey(start - 1) == null ? dps.ceilingKey(start) : dps.floorKey(start - 1);
    			Long endKey = dps.floorKey(next.getKey());
    			if (startKey != null && endKey != null && startKey < endKey) {
    				timestamps.addAll(dps.subMap(startKey, endKey).keySet());
    				startTimestamp = timestamps.get(0);
    				Long endTimestamp = timestamps.get(timestamps.size() - 1);
    				
    				while (startTimestamp <= endTimestamp) {
    					propagateDatapoints.put(startTimestamp, dps.containsKey(startTimestamp) ? dps.get(startTimestamp) : null);
    					if (index >= timestamps.size()) {
    						break;
    					}
    					if ((startTimestamp + windowSizeInSeconds * 1000) < timestamps.get(index)) {
    						startTimestamp += windowSizeInSeconds * 1000;
    					} else {
    						startTimestamp = timestamps.get(index);
    						index++;
    					}
    				}
    			}
    		} else {
    			while (scanner.peek() != null && scanner.peek().getKey() < start) {
    				scanner.getNextDP();
    			}
    		}
    		
    		if (scanner.hasNextDP()) {
    			Map.Entry<Long, Double> dp = scanner.getNextDP();
    			timestamps.add(dp.getKey());
    			if (startTimestamp == null) {
    				startTimestamp = dp.getKey();
    				propagateDatapoints.put(startTimestamp, dp.getValue());
    			}
    			
    			while (scanner.peek() != null && startTimestamp <= end) {
    				dp = scanner.getNextDP();
    				timestamps.add(dp.getKey());
    				
    				propagateDatapoints.put(startTimestamp, scanner.getMetric().getDatapoints().containsKey(startTimestamp) ?
    						scanner.getMetric().getDatapoints().get(startTimestamp) : null);
    				while ((startTimestamp + windowSizeInSeconds * 1000) < timestamps.get(index)) {
    					startTimestamp += windowSizeInSeconds * 1000;
    					propagateDatapoints.put(startTimestamp, null);
    				}
    				startTimestamp = timestamps.get(index);
    				propagateDatapoints.put(startTimestamp, scanner.getMetric().getDatapoints().get(startTimestamp));
    				index++;
    			}
    			
    			if (scanner.peek() != null) {
    				// find out how far we should fill up to
    				timestamps.add(scanner.peek().getKey());
    			}
    			
    			while (startTimestamp + windowSizeInSeconds * 1000 < timestamps.get(timestamps.size() - 1)) {
    				startTimestamp += windowSizeInSeconds * 1000;
    				propagateDatapoints.put(startTimestamp, null);
    			}
    			startTimestamp = timestamps.get(timestamps.size() - 1);
    			propagateDatapoints.put(startTimestamp, scanner.getMetric().getDatapoints().get(startTimestamp));
    		}
    	}
    	
    	List<Long> newTimestamps = new ArrayList<>(propagateDatapoints.keySet());
    	Double prev = null;
    	
    	for (Long time : newTimestamps) {
    		if (propagateDatapoints.get(time) != null) {
    			prev = propagateDatapoints.get(time);
    		} else {
    			propagateDatapoints.put(time, prev);
    		}
	}
		Long startKey = propagateDatapoints.ceilingKey(start);
		Long endKey = propagateDatapoints.floorKey(end);
		Metric m = scanner.getMetric();
		if (startKey != null && endKey != null && startKey <= endKey) {
			m.setDatapoints(propagateDatapoints.subMap(startKey, endKey + 1));
		} else {
			m.setDatapoints(new TreeMap<>());
		}
		result.add(m);
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
    public List<Metric> transform(List<Metric> metrics) {
        throw new UnsupportedOperationException("Propagate Transform needs a max window size");
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners) {
        throw new UnsupportedOperationException("Propagate Transform needs a max window size");
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, Long start, Long end) {
    	throw new UnsupportedOperationException("Propagate Transform needs a max window size");
    }

    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
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
    public List<Metric> transformScanner(List<MetricScanner> scanners, List<String> constants) {
    	SystemAssert.requireArgument(scanners != null, "Cannot transform null or empty metric scanners.");
    	if (scanners.isEmpty()) {
    		return new ArrayList<Metric>();
    	}
    	SystemAssert.requireArgument(constants != null && !constants.isEmpty(), "Propagate Transform needs a max window size");
    	
    	String window = constants.get(0);
    	long windowSizeInSeconds = parseTimeIntervalInSeconds(window);
    	
    	List<Metric> result = new ArrayList<>();
    	for (MetricScanner scanner : scanners) {
    		_propagateMetricTransformScanner(scanner, windowSizeInSeconds, result);
    	}
    	return result;
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, List<String> constants, Long start, Long end) {
    	SystemAssert.requireArgument(scanners != null, "Cannot transform null or empty metric scanners.");
    	if (scanners.isEmpty()) {
    		return new ArrayList<>();
    	}
    	SystemAssert.requireArgument(constants != null && !constants.isEmpty(), "Propagate Transform needs a max window size");
    	
    	String window = constants.get(0);
    	long windowSizeInSeconds = parseTimeIntervalInSeconds(window);
    	
    	List<Metric> result = new ArrayList<>();
    	for (MetricScanner scanner : scanners) {
    		_propagateMetricTransformPager(scanner, windowSizeInSeconds, start, end, result);
    	}
    	return result;
    }
    
    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.PROPAGATE.name();
    }

    @Override
    public List<Metric> transform(List<Metric>... listOfList) {
        throw new UnsupportedOperationException("Propagate Transform doesn't accept list of metric list!");
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner>... listOfList) {
        throw new UnsupportedOperationException("Propagate Transform doesn't accept list of metric list!");
    }
    
    @Override
    public List<Metric> transformToPagerListOfList(List<List<MetricScanner>> scanners, Long start, Long end) {
    	throw new UnsupportedOperationException("Propagate Transform doesn't accept list of metric list!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

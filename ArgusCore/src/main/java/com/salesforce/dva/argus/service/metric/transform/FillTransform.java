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

import com.google.common.primitives.Longs;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.metric.MetricReader;
import com.salesforce.dva.argus.service.tsdb.MetricScanner;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Creates additional data points to fill gaps.<br/>
 * <tt>FILL(<expr>, <interval>, <interval>, <constant>)</tt>
 *
 * @author  Ruofan Zhang(rzhang@salesforce.com)
 */
public class FillTransform implements Transform {

    //~ Static fields/initializers *******************************************************************************************************************

    /** The default metric name for results. */
    public static final String DEFAULT_METRIC_NAME = "result";

    /** The default metric scope for results. */
    public static final String DEFAULT_SCOPE_NAME = "scope";

    //~ Methods **************************************************************************************************************************************

    private static Map<Long, Double> _fillMetricTransform(Metric metric, long windowSizeInSeconds, long offsetInSeconds, double value) {
    	Map<Long, Double> filledDatapoints = new TreeMap<>();
        Map<Long, Double> sortedDatapoints = new TreeMap<>(metric.getDatapoints());
        Long[] sortedTimestamps = new Long[sortedDatapoints.size()];

        sortedDatapoints.keySet().toArray(sortedTimestamps);

        Long startTimestamp = sortedTimestamps[0];
        Long endTimestamp = sortedTimestamps[sortedTimestamps.length - 1];

        // create a new datapoints map propagateDatpoints, which have all the
        // expected timestamps, then fill the missing value
        int index = 1;

        while (startTimestamp <= endTimestamp) {
            filledDatapoints.put(startTimestamp, sortedDatapoints.containsKey(startTimestamp) ? sortedDatapoints.get(startTimestamp) : null);
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
    
    private static Map<Long, Double> _fillMetricTransformScanner(MetricScanner scanner, long windowSizeInSeconds, long offsetInSeconds, double value) {
    	Map<Long, Double> filledDatapoints = new TreeMap<>();
    	
    	List<Long> timestamps = new ArrayList<>();
    	
    	int index = 1;
    	
    	Map.Entry<Long, Double> dp = scanner.getNextDP();
		timestamps.add(dp.getKey());
		Long startTimestamp = dp.getKey();
		filledDatapoints.put(startTimestamp, dp.getValue());
		
		while (scanner.hasNextDP()) {
			dp = scanner.getNextDP();
			timestamps.add(dp.getKey());
			
			filledDatapoints.put(startTimestamp, scanner.getMetric().getDatapoints().containsKey(startTimestamp) ?
					scanner.getMetric().getDatapoints().get(startTimestamp) : null);
			while ((startTimestamp + windowSizeInSeconds * 1000) < timestamps.get(index)) {
				startTimestamp = startTimestamp + windowSizeInSeconds * 1000;
				filledDatapoints.put(startTimestamp, null);
			}
			startTimestamp = timestamps.get(index);
			filledDatapoints.put(startTimestamp, scanner.getMetric().getDatapoints().get(startTimestamp));
			index++;
		}
		
		if (scanner.peek() != null) {
			// add next time in case we never entered while loop
			timestamps.add(scanner.peek().getKey());
		}
		
		while (startTimestamp + windowSizeInSeconds * 1000 < timestamps.get(timestamps.size() - 1)) {
			startTimestamp = startTimestamp + windowSizeInSeconds * 1000;
			filledDatapoints.put(startTimestamp, null);
		}
		filledDatapoints.put(startTimestamp, scanner.getMetric().getDatapoints().get(startTimestamp));
    	    	
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
    
    private static Map<Long, Double> _fillMetricTransformPager(MetricScanner scanner, long windowSizeInSeconds, long offsetInSeconds, double value, Long start, Long end) {
        Map<Long, Double> filledDatapoints = new TreeMap<>();
    	int index = 1;
    	Map.Entry<Long, Double> next = scanner.peek();
    	
    	if (next == null || next.getKey() > end) {
    		/* all in the metric version */
    		TreeMap<Long, Double> sortedDatapoints = new TreeMap<>(scanner.getMetric().getDatapoints());
    		// try to go before the start key to fill the gap up to that key
    		Long startKey = sortedDatapoints.floorKey(start - 1) == null ? sortedDatapoints.ceilingKey(start) :
    			sortedDatapoints.floorKey(start - 1);
    		// try to go up past the end key to fill in to the next key
    		Long endKey = sortedDatapoints.ceilingKey(end + 1) == null ? sortedDatapoints.floorKey(end) :
    			sortedDatapoints.ceilingKey(end + 1);
    		if (startKey != null && endKey != null && startKey <= endKey) {
    			TreeMap<Long, Double> dps = new TreeMap<>(sortedDatapoints.subMap(startKey, endKey + 1));
    			Long[] sortedTimestamps = new Long[dps.size()];
    			dps.keySet().toArray(sortedTimestamps);
    			Long startTimestamp = sortedTimestamps[0];
    			Long endTimestamp = Math.min(end, sortedTimestamps[sortedTimestamps.length - 1]);
    			
    			while (startTimestamp <= endTimestamp) {
    				filledDatapoints.put(startTimestamp, sortedDatapoints.containsKey(startTimestamp) ? sortedDatapoints.get(startTimestamp) : null);
    				if (index >= sortedTimestamps.length) {
    					break;
    				}
    				if ((startTimestamp + windowSizeInSeconds * 1000) < sortedTimestamps[index]) {
    					startTimestamp = startTimestamp + windowSizeInSeconds * 1000;
    				} else {
    					startTimestamp = sortedTimestamps[index];
    					index++;
    				}
    			}
    		}
    	} else {
    		Long startTimestamp = null;
    		List<Long> sortedTimestamps = new ArrayList<>();
    		if (next.getKey() > start) {
    			TreeMap<Long, Double> sortedDatapoints = new TreeMap<>(scanner.getMetric().getDatapoints());
        		Long startKey = sortedDatapoints.floorKey(start - 1) == null ? sortedDatapoints.ceilingKey(start) :
        			sortedDatapoints.floorKey(start - 1);
        		Long endKey = sortedDatapoints.floorKey(next.getKey());
        		if (startKey != null && endKey != null && startKey < endKey - 1) {
        			TreeMap<Long, Double> dps = new TreeMap<>(sortedDatapoints.subMap(startKey, endKey));
        			sortedTimestamps = new ArrayList<>(dps.keySet());
        			
        			startTimestamp = sortedTimestamps.get(0);
        			Long endTimestamp = sortedTimestamps.get(sortedTimestamps.size() - 1);
        			
        			while (startTimestamp <= endTimestamp) {
        				filledDatapoints.put(startTimestamp, sortedDatapoints.containsKey(startTimestamp) ?
        						sortedDatapoints.get(startTimestamp) : null);
        				if (index >= dps.size()) {
        					break;
        				}
        				if ((startTimestamp + windowSizeInSeconds * 1000) < sortedTimestamps.get(index)) {
        					startTimestamp = startTimestamp + windowSizeInSeconds * 1000;
        				} else {
        					startTimestamp = sortedTimestamps.get(index);
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
    			sortedTimestamps.add(dp.getKey());
    			if (startTimestamp == null) {
    				/* had all scanner */
    				startTimestamp = dp.getKey();
    				filledDatapoints.put(startTimestamp, dp.getValue());
    			}
    			while (scanner.peek() != null && startTimestamp <= end) {
    				dp = scanner.getNextDP();
    				sortedTimestamps.add(dp.getKey());
    				
    				filledDatapoints.put(startTimestamp, scanner.getMetric().getDatapoints().containsKey(startTimestamp) ?
    						scanner.getMetric().getDatapoints().get(startTimestamp) : null);
    				while ((startTimestamp + windowSizeInSeconds * 1000) < sortedTimestamps.get(index)) {
    					startTimestamp = startTimestamp + windowSizeInSeconds * 1000;
    					filledDatapoints.put(startTimestamp, null);
    				}
					startTimestamp = sortedTimestamps.get(index);

					filledDatapoints.put(startTimestamp, scanner.getMetric().getDatapoints().get(startTimestamp));
					index++;
    			}
    			
    			if (scanner.peek() != null) {
    				// find the next time we should iterate up to
    				sortedTimestamps.add(scanner.peek().getKey());
    			}
    			
    			while (startTimestamp + windowSizeInSeconds * 1000 < sortedTimestamps.get(sortedTimestamps.size() - 1)) {
					startTimestamp = startTimestamp + windowSizeInSeconds * 1000;
					filledDatapoints.put(startTimestamp, null);
				}
    			startTimestamp = sortedTimestamps.get(sortedTimestamps.size() - 1);
    			filledDatapoints.put(startTimestamp, scanner.getMetric().getDatapoints().get(startTimestamp));
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

        while (startTimestamp < endTimestamp) {
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
    
    private List<Metric> _fillLine(List<String> constants, long relativeTo, Long start, Long end) {
    	SystemAssert.requireArgument(constants != null && constants.size() == 5,
                "Line Filling Transform needs 5 constants (start, end, interval, offset, value)!");
    	
    	long startTimestamp = Math.max(_parseStartAndEndTimestamps(constants.get(0), relativeTo), start);
    	long endTimestamp = Math.min(_parseStartAndEndTimestamps(constants.get(1), relativeTo), end);
    	long windowSizeInSeconds = _parseTimeIntervalInSeconds(constants.get(2));
    	long offsetInSeconds = _parseTimeIntervalInSeconds(constants.get(3));
    	double value = Double.parseDouble(constants.get(4));
    	
    	if (startTimestamp >= endTimestamp) {
    		Metric metric = new Metric(DEFAULT_SCOPE_NAME, DEFAULT_METRIC_NAME);
    		metric.setDatapoints(new TreeMap<>());
    		return Arrays.asList(metric);
    	}
    	SystemAssert.requireArgument(windowSizeInSeconds >= 0, "Window size must be greater than ZERO!");
    	
    	// snapping start and end time
    	long startSnapping = startTimestamp % (windowSizeInSeconds * 1000);
    	startTimestamp -= startSnapping;
    	long endSnapping = endTimestamp % (windowSizeInSeconds * 1000);
    	endTimestamp -= endSnapping;
    	
    	Metric metric = new Metric(DEFAULT_SCOPE_NAME, DEFAULT_METRIC_NAME);
    	Map<Long, Double> filledDatapoints = new TreeMap<>();
    	
    	while (startTimestamp < endTimestamp) {
    		filledDatapoints.put(startTimestamp, value);
    		startTimestamp += windowSizeInSeconds * 1000;
    	}
    	if (endTimestamp == _parseStartAndEndTimestamps(constants.get(1), relativeTo) || endSnapping == 0) {
    		// here we want to add the last point -> if we are just cutting off due to page length, don't add
    		// also add the datapoint if it occurs exactly on the second mark (it should be included)
    		filledDatapoints.put(endTimestamp, value);
    	}
    	
    	Map<Long, Double> newFilledDatapoints = new TreeMap<>();
    	
    	for (Map.Entry<Long, Double> entry : filledDatapoints.entrySet()) {
    		newFilledDatapoints.put(entry.getKey() + offsetInSeconds * 1000, entry.getValue());
    	}
    	metric.setDatapoints(newFilledDatapoints);
    	
    	return Arrays.asList(metric);
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
    public List<Metric> transform(List<Metric> metrics) {
        throw new UnsupportedOperationException("Fill Transform needs interval, offset and value!");
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners) {
        throw new UnsupportedOperationException("Fill Transform needs interval, offset and value!");
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, Long start, Long end) {
    	throw new UnsupportedOperationException("Fill transform needs interval, offset and value!");
    }

    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
    	
        if (metrics == null || metrics.isEmpty()) {
        	// Last constant is added by MetricReader. It is the timestamp using which relative start and end timestamps 
        	// should be calculated
        	String relativeTo = "";
        	if(constants != null && !constants.isEmpty()) {
        		relativeTo = constants.remove(constants.size() - 1);
        	}
            return _fillLine(constants, Long.parseLong(relativeTo));
        }
        SystemAssert.requireArgument(metrics != null, "Cannot transform null or empty metrics!");
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

            newMetric.setDatapoints(_fillMetricTransform(metric, windowSizeInSeconds, offsetInSeconds, value));
            fillMetricList.add(newMetric);
        }
        return fillMetricList;
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners, List<String> constants) {
    	
    	if (scanners == null || scanners.isEmpty()) {
    		String relativeTo = "";
    		if (constants != null && !constants.isEmpty()) {
    			relativeTo = constants.remove(constants.size() - 1);
    		}
    		return _fillLine(constants, Long.parseLong(relativeTo)); 	// just use the metric version, this is fine
    	}
    	
    	SystemAssert.requireArgument(scanners != null, "Cannot transform null or empty metric scanners!");
        SystemAssert.requireArgument(constants != null && constants.size() == 3, 
        		"Fill Transform needs exactly three constants: interval, offset, value");
        
        String interval = constants.get(0);
        long windowSizeInSeconds = _parseTimeIntervalInSeconds(interval);
        
        SystemAssert.requireArgument(windowSizeInSeconds >= 0, "Window size must be greater than ZERO!");
        
        String offset = constants.get(0);
        long offsetInSeconds = _parseTimeIntervalInSeconds(offset);
        double value = Double.parseDouble(constants.get(2));
        
        List<Metric> fillMetricList = new ArrayList<>();
        for (MetricScanner scanner : scanners) {
        	Metric newMetric = new Metric(scanner.getMetric());
        	newMetric.setDatapoints(_fillMetricTransformScanner(scanner, windowSizeInSeconds, offsetInSeconds, value));
        	fillMetricList.add(newMetric);
        }
        return fillMetricList;
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, List<String> constants, Long start, Long end) {
    	if (scanners == null || scanners.isEmpty()) {
    		String relativeTo = "";
    		if (constants != null && !constants.isEmpty()) {
    			relativeTo = constants.remove(constants.size() - 1);
    		}
    		return _fillLine(constants, Long.parseLong(relativeTo), start, end);
    	}
    	
    	SystemAssert.requireArgument(scanners != null, "Cannot transform null or empty metric scanners!");
    	SystemAssert.requireArgument(constants != null && constants.size() == 3,
    			"Fill Transform needs exactly three constants: interval, offset, value");
    	
    	String interval = constants.get(0);
    	long windowSizeInSeconds = _parseTimeIntervalInSeconds(interval);
    	
    	SystemAssert.requireArgument(windowSizeInSeconds >= 0, "Window size must be greater than ZERO!");
    	
    	String offset = constants.get(1);
    	long offsetInSeconds = _parseTimeIntervalInSeconds(offset);
    	double value = Double.parseDouble(constants.get(2));
    	
    	List<Metric> fillMetricList = new ArrayList<>();
    	for (MetricScanner scanner : scanners) {
    		Long startFrom = start;
    		if (!scanner.getMetric().getDatapoints().isEmpty()) {
    			TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
    			startFrom = dps.floorKey(start - 1000 * offsetInSeconds) == null ? start : dps.floorKey(start - 1000 * offsetInSeconds - 1000);
    		}
    		TreeMap<Long, Double> res = new TreeMap<>(_fillMetricTransformPager(scanner, windowSizeInSeconds, offsetInSeconds, value,
    				startFrom, end));
    		Long startKey = res.ceilingKey(start);
    		Long endKey = res.floorKey(end);
			Metric m = new Metric(scanner.getMetric());
    		if (startKey != null && endKey != null && startKey <= endKey) {
    			m.setDatapoints(res.subMap(startKey, endKey + 1));
    		} else {
    			m.setDatapoints(new HashMap<>());
    		}
    		fillMetricList.add(m);
    	}
    	return fillMetricList;
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.FILL.name();
    }

    @Override
    public List<Metric> transform(List<Metric>... listOfList) {
        throw new UnsupportedOperationException("Fill doesn't need list of list!");
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner>... listOfList) {
        throw new UnsupportedOperationException("Fill doesn't need list of list!");
    }
    
    @Override
    public List<Metric> transformToPagerListOfList(List<List<MetricScanner>> scanners, Long start, Long end) {
    	throw new UnsupportedOperationException("Fill doesn't need list of list!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

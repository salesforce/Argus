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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
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
    public List<Metric> transformScanner(List<MetricScanner> scanners) {
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

        long windowSizeInSeconds = 0;
        try {
            timeunit = MetricReader.TimeUnit.fromString(window.substring(window.length() - 1));
            long timeDigits = Long.parseLong(window.substring(0, window.length() - 1));
            windowSizeInSeconds = timeDigits * timeunit.getValue() / 1000;
        } catch (Exception t) {
            long windowSize = Long.parseLong(window);

            for (Metric metric : metrics) {
                metric.setDatapoints(_calculateMovingAverageUsingFixedNoOfPastPoints(metric.getDatapoints(), windowSize));
            }
            return metrics;
        }
        
        for (Metric metric : metrics) {
            metric.setDatapoints(_calculateMovingAverageUsingTimeInterval(metric.getDatapoints(), windowSizeInSeconds));
        }
        return metrics;
    }
	
	public List<Metric> transformScanner(List<MetricScanner> scanners, List<String> constants) {
    		SystemAssert.requireArgument(scanners != null, "Cannot transform null or empty metrics");
    		List<Metric> result = new ArrayList<>();
        if (scanners.isEmpty()) {
            return result;
        }
        SystemAssert.requireArgument(constants != null && constants.size() == 1,
            "Moving Average Transform " +
            "must provide exactly 1 constant. windowSize -> Either fixed number of past points or time interval");

        String window = constants.get(0);
        MetricReader.TimeUnit timeunit = null;
        
        long windowSizeInSeconds = 0;
        try {
        		timeunit = MetricReader.TimeUnit.fromString(window.substring(window.length() - 1));
        		long timeDigits = Long.parseLong(window.substring(0, window.length() - 1));
        		windowSizeInSeconds = timeDigits * timeunit.getValue() / 1000;
        }
        catch (Exception t) {
        		long windowSize = Long.parseLong(window);
        		
        		for (MetricScanner scanner : scanners) {
        			Metric m = scanner.getMetric();
        			m.setDatapoints(_calculateMovingAverageUsingFixedNoOfPastPointsScanner(scanner, windowSize));
        			result.add(m);
        		}
        		return result;
        }
        
        for (MetricScanner scanner : scanners) {
        		Metric m = scanner.getMetric();
        		m.setDatapoints(_calculateMovingAverageUsingTimeIntervalScanner(scanner, windowSizeInSeconds));
        		result.add(m);
        }
        return result;
    }

    private Map<Long, Double> _calculateMovingAverageUsingTimeInterval(Map<Long, Double> originalDatapoints, long windowSizeInSeconds) {
        SystemAssert.requireArgument(windowSizeInSeconds != 0, "Time Interval cannot be 0 for Moving Average Transform");

        Map<Long, Double> transformedDatapoints = new TreeMap<>();
        Map<Long, Double> sortedDatapoints = new TreeMap<>(originalDatapoints);
        Long[] timestamps = new Long[sortedDatapoints.size()];

        sortedDatapoints.keySet().toArray(timestamps);

        double sum = sortedDatapoints.get(timestamps[0]);
        Long firstTimestamp = timestamps[0];
        int count = 1;

        for (int i = 1, j = 0; i < timestamps.length; i++) {
            if (j == 0) {
                while (timestamps[i] - windowSizeInSeconds * 1000 < firstTimestamp) {
                    try {
                        sum += sortedDatapoints.get(timestamps[i]);
                    } catch (NumberFormatException | NullPointerException e) {
                        _logger.warn("Failed to parse datapoint: " + sortedDatapoints.get(timestamps[i]));
                    }
                    transformedDatapoints.put(timestamps[i - 1], null);
                    i++;
					if (i >= timestamps.length) {
                    	break;
                    }
                    count++;
                }
                transformedDatapoints.put(timestamps[i - 1], (sum / count));
            }
            try {
				if (i >= timestamps.length) {
            		break;
            	}
                sum += sortedDatapoints.get(timestamps[i]);
                while (timestamps[j] <= timestamps[i] - windowSizeInSeconds * 1000) {
                    sum = _subtractWithinWindow(sum, sortedDatapoints, timestamps[j], timestamps[i]);
                    count--;
                    j++;
                }
            } catch (NumberFormatException | NullPointerException e) {
                _logger.warn("Failed to parse datapoint: " + sortedDatapoints.get(timestamps[i]));
            }
            count++;
            transformedDatapoints.put(timestamps[i], (sum / count));
        }
        return transformedDatapoints;
    }
	
	private Map<Long, Double> _calculateMovingAverageUsingTimeIntervalScanner(MetricScanner scanner, long windowSizeInSeconds) {
        SystemAssert.requireArgument(windowSizeInSeconds != 0, "Time Interval cannot be 0 for Moving Average Transform");
        
        Map<Long, Double> transformedDatapoints = new TreeMap<>();
        Map<Long, Double> sortedDatapoints = new TreeMap<>();
        List<Long> timestamps = new ArrayList<>();
        
        Map.Entry<Long, Double> dp = null;
        synchronized(scanner) {
        	SystemAssert.requireArgument(scanner.hasNextDP(), "Scanner needs to have at least one datapoint!");
        	dp = scanner.getNextDP();
        }
        
        double sum = dp.getValue();
        Long firstTimestamp = dp.getKey();
        timestamps.add(firstTimestamp);
        sortedDatapoints.put(dp.getKey(), dp.getValue());
        int count = 1;
        
        int i = 0;
        int j = 0;
        
        synchronized(scanner) {
	        while (scanner.hasNextDP()) {
	        		dp = scanner.getNextDP();
	        		timestamps.add(dp.getKey());
	        		sortedDatapoints.put(dp.getKey(), dp.getValue());
	        		if (j == 0) {
		        		while(timestamps.get(i) - windowSizeInSeconds * 1000 < firstTimestamp) {	// still within the first window
		        			try {
		        				sum += sortedDatapoints.get(timestamps.get(i));
		        			} catch (NumberFormatException | NullPointerException e) {
		        				_logger.warn("Failed to parse datapoint: " + dp.getValue());
		        			}
		        			transformedDatapoints.put(timestamps.get(timestamps.indexOf(dp.getKey()) - 1), null);
		        			i++;
		        			count++;
		        			if (!scanner.hasNextDP()) {
		        				break;
		        			}
		        			else {
		        				dp = scanner.getNextDP();
		        				timestamps.add(dp.getKey());
		        				sortedDatapoints.put(dp.getKey(), dp.getValue());
		        			}
		        		}
		        		transformedDatapoints.put(timestamps.get(i-1), (sum / count));
	        		}
	        		try {
	        			sum += sortedDatapoints.get(timestamps.get(i));	// this should exist by this point
	        			while (timestamps.get(j) <= timestamps.get(i) - windowSizeInSeconds * 1000) {
	        				sum = _subtractWithinWindow(sum, sortedDatapoints, timestamps.get(j), timestamps.get(i));
	        				count--;
	        				j++;
	        				
	        			}
	        		} catch (NumberFormatException | NullPointerException e) {
	        			//_logger.warn("Failed to parse datapoint: " + sortedDatapoints.get(timestamps.get(i)));
	        			System.out.println("Failed to parse datapoint: " + sortedDatapoints.get(timestamps.get(i)));
	        		}
	        		count++;
	        		transformedDatapoints.put(timestamps.get(i), (sum / count));
	        		i++; // increment around the loop
	        }
        }
        return transformedDatapoints;
    }

    private double _subtractWithinWindow(double sum, Map<Long, Double> sortedDatapoints, long end, long start) {
        sum -= sortedDatapoints.get(end);
        return sum;
    }

    private Map<Long, Double> _calculateMovingAverageUsingFixedNoOfPastPoints(Map<Long, Double> originalDatapoints, long window) {
        SystemAssert.requireArgument(window != 0, "Window cannot be 0 for Moving Average Transform");

        Map<Long, Double> transformedDatapoints = new TreeMap<>();
        Map<Long, Double> sortedDatapoints = new TreeMap<>(originalDatapoints);
        double sum = 0.0, firstValueInInterval = 0.0;
        Long[] timestamps = new Long[sortedDatapoints.size()];

        sortedDatapoints.keySet().toArray(timestamps);
        for (int i = 0, j = 0; i < timestamps.length; i++) {
            if (i + 1 < window) {
                try {
                    sum += sortedDatapoints.get(timestamps[i]);
                } catch (NumberFormatException | NullPointerException e) {
                    _logger.warn("Failed to parse datapoint: " + sortedDatapoints.get(timestamps[i]) + "Skipping this one.");
                }
                transformedDatapoints.put(timestamps[i], null);
            } else {
                try {
                    sum += sortedDatapoints.get(timestamps[i]);
                    sum -= firstValueInInterval;
                    firstValueInInterval = sortedDatapoints.get(timestamps[j]);
                } catch (NumberFormatException | NullPointerException e) {
                    _logger.warn("Failed to parse datapoint: " + sortedDatapoints.get(timestamps[i]) + "Skipping this one.");
                }
                transformedDatapoints.put(timestamps[i], (sum / window));
                j++;
            }
        }
        return transformedDatapoints;
    }
	
	private Map<Long, Double> _calculateMovingAverageUsingFixedNoOfPastPointsScanner(MetricScanner scanner, long window) {
    		SystemAssert.requireArgument(window != 0, "Window cannot be 0 for Moving Average Transform");
    		
    		Map<Long, Double> transformedDatapoints = new TreeMap<>();
    		Map<Long, Double> sortedDatapoints = new TreeMap<>();
    		double sum = 0.0;
    		double firstValueInInterval = 0.0;
    		List<Long> timestamps = new ArrayList<>();
    		
    		int i = 0;
    		int j = 0;
    		
    		synchronized(scanner) {
	    		while (scanner.hasNextDP()) {
	    			Map.Entry<Long, Double> dp = scanner.getNextDP();	// basically sortedDatapoints.get(timestamps[i])
	    			sortedDatapoints.put(dp.getKey(), dp.getValue());
	    			timestamps.add(dp.getKey());
	    			if (i + 1 < window) {	// can put another point in here
	    				try {
	    					sum += sortedDatapoints.get(timestamps.get(i));
	    				} catch (NumberFormatException | NullPointerException e) {
	    					_logger.warn("Failed to parse datapoint: " + sortedDatapoints.get(timestamps.get(i)) + " Skipping this one.");
	    				}
	    				transformedDatapoints.put(timestamps.get(i), null);
	    			} else {
	    				try {
	    					sum += sortedDatapoints.get(timestamps.get(i));
	    					sum -= firstValueInInterval;
	    					firstValueInInterval = sortedDatapoints.get(timestamps.get(j));
	    				} catch (NumberFormatException | NullPointerException e) {
	    					_logger.warn("Failed to parse datapoint: " + sortedDatapoints.get(timestamps.get(i)) + " Skipping this one.");
	    				}
	    				transformedDatapoints.put(timestamps.get(i), sum / window);
	    				j++;
	    			}
	    			i++; // increment looping variable
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
	
	@Override
    public List<Metric> transformScanner(List<MetricScanner>... listOfList) {
        throw new UnsupportedOperationException("This class is deprecated!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

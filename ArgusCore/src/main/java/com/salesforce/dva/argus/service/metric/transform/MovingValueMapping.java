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

import com.salesforce.dva.argus.service.metric.MetricReader;
import com.salesforce.dva.argus.service.tsdb.MetricScanner;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemException;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.math.stat.descriptive.rank.Percentile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Evaluates input metrics using a moving window. calculate either median or average value of the window
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class MovingValueMapping implements ValueMapping {

    //~ Instance fields ******************************************************************************************************************************

    private final Logger _logger = LoggerFactory.getLogger(MetricMappingTransform.class);

    //~ Methods **************************************************************************************************************************************

    @Override
    public Map<Long, Double> mapping(Map<Long, Double> originalDatapoints) {
        throw new UnsupportedOperationException("Moving Average Transform needs a window size of time interval");
    }
	
	@Override
    public Map<Long, Double> mappingScanner(MetricScanner scanner) {
        throw new UnsupportedOperationException("Moving Average Transform needs a window size of time interval");
    }

    @Override
    public Map<Long, Double> mapping(Map<Long, Double> originalDatapoints, List<String> constants) {
        SystemAssert.requireArgument(constants != null, "Moving Average Transform needs a window size of time interval");
        SystemAssert.requireArgument(!constants.isEmpty(),
            "Moving Average Transform must provide at least 1 constant which is windowSize of time interval.");
        if (constants.size() > 1) {
            SystemAssert.requireArgument(constants.size() == 2, "Moving Average Transform can't have more than 2 contants!");
            SystemAssert.requireArgument((InternalReducerType.AVG.getName().equals(constants.get(1)) ||
                    InternalReducerType.MEDIAN.getName().equals(constants.get(1))), "Type can only be average or median.");
        }

        long windowSizeInSeconds = getWindowInSeconds(constants.get(0));

        SystemAssert.requireArgument(windowSizeInSeconds != 0, "Time Interval cannot be 0 for Moving Average Transform");

        boolean isMedian = false;
        String reducerType = InternalReducerType.AVG.getName();

        if (constants.size() == 2) {
            reducerType = constants.get(1);
        }

        InternalReducerType type = InternalReducerType.fromString(reducerType);

        if (type.equals(InternalReducerType.MEDIAN)) {
            isMedian = true;
        }

        Map<Long, Double> movingDatapoints = new TreeMap<>();
        Map<Long, Double> sortedDatapoints = new TreeMap<>(originalDatapoints);

        for (Map.Entry<Long, Double> entry : originalDatapoints.entrySet()) {
            if (entry.getValue() == null) {
                sortedDatapoints.put(entry.getKey(), 0.0);
            } else {
                sortedDatapoints.put(entry.getKey(), entry.getValue());
            }
        }

        Long[] timestamps = new Long[sortedDatapoints.size()];

        sortedDatapoints.keySet().toArray(timestamps);

        double sum = 0.0;
        double value = 0.0;
        List<Double> numberArr = new ArrayList<Double>();

        try {
            sum = sortedDatapoints.get(timestamps[0]);
            numberArr.add(sortedDatapoints.get(timestamps[0]));
        } catch (NumberFormatException | NullPointerException e) {
            _logger.warn("Failed to parse datapoint: " + sortedDatapoints.get(timestamps[0]));
            throw new UnsupportedOperationException("Bad datapoint!");
        }

        // if only one point
        movingDatapoints.put(timestamps[0], sortedDatapoints.get(timestamps[0]));

        Long firstTimestamp = timestamps[0];
        int count = 1;

        for (int head = 1, tail = 0; head < timestamps.length; head++) {
            // When moving window, maintain a invariant that timestamps[head] - timestamps[end] < windowSize
            // if timestamps[head] - timestamps[end] == windowSize, some points need to be kicked off
            // For first window, exclude timestamps[head]
            // For a regular window, exclude timestamps[tail]
            if (tail == 0) {
                while (timestamps[head] - firstTimestamp < windowSizeInSeconds * 1000) {
                    try {
                        // run out of points before hitting the end of first window
                        if (head >= timestamps.length - 1) {
                            break;
                        }
                        if (isMedian) {
                            double[] numbers = ArrayUtils.toPrimitive(numberArr.toArray(new Double[numberArr.size()]));

                            value = new Percentile().evaluate(numbers, 50.0);
                        } else {
                            value = (sum / count);
                        }
                        movingDatapoints.put(timestamps[head - 1], value);
                        sum += sortedDatapoints.get(timestamps[head]);
                        numberArr.add(sortedDatapoints.get(timestamps[head]));
                    } catch (NumberFormatException | NullPointerException e) {
                        _logger.warn("Failed to parse datapoint: " + sortedDatapoints.get(timestamps[head]));
                        throw new IllegalArgumentException("Bad datapoint!");
                    }
                    head++;
                    count++;
                }
                
                if (isMedian) {
                    double[] numbers = ArrayUtils.toPrimitive(numberArr.toArray(new Double[numberArr.size()]));

                    value = new Percentile().evaluate(numbers, 50.0);
                } else {
                    value = (sum / count);
                }
                movingDatapoints.put(timestamps[head - 1], value);
            }
            
            try {
                sum += sortedDatapoints.get(timestamps[head]);
                numberArr.add(sortedDatapoints.get(timestamps[head]));
                while (timestamps[head] - timestamps[tail] >= windowSizeInSeconds * 1000) {
                    sum = _subtractWithinWindow(sum, sortedDatapoints, timestamps[tail], timestamps[head]);
                    numberArr.remove(sortedDatapoints.get(timestamps[tail]));
                    count--;
                    tail++;
                }
            } catch (NumberFormatException | NullPointerException e) {
                _logger.warn("Failed to parse datapoint: " + sortedDatapoints.get(timestamps[head]));
                throw new UnsupportedOperationException("Bad datapoint!");
            }
            count++;
            if (isMedian) {
                double[] numbers = ArrayUtils.toPrimitive(numberArr.toArray(new Double[numberArr.size()]));

                value = new Percentile().evaluate(numbers, 50.0);
            } else {
                value = (sum / count);
            }
            movingDatapoints.put(timestamps[head], value);
        } // end for
        return movingDatapoints;
    }
	
	@Override
    public Map<Long, Double> mappingScanner(MetricScanner scanner, List<String> constants) {
    	SystemAssert.requireArgument(constants != null, "Moving Average Transform needs a window size of time interval");
        SystemAssert.requireArgument(!constants.isEmpty(),
            "Moving Average Transform must provide at least 1 constant which is windowSize of time interval.");
        if (constants.size() > 1) {
            SystemAssert.requireArgument(constants.size() == 2, "Moving Average Transform can't have more than 2 contants!");
            SystemAssert.requireArgument((InternalReducerType.AVG.getName().equals(constants.get(1)) ||
                    InternalReducerType.MEDIAN.getName().equals(constants.get(1))), "Type can only be average or median.");
        }

        long windowSizeInSeconds = getWindowInSeconds(constants.get(0));

        SystemAssert.requireArgument(windowSizeInSeconds != 0, "Time Interval cannot be 0 for Moving Average Transform");

        boolean isMedian = false;
        String reducerType = InternalReducerType.AVG.getName();

        if (constants.size() == 2) {
            reducerType = constants.get(1);
        }

        InternalReducerType type = InternalReducerType.fromString(reducerType);

        if (type.equals(InternalReducerType.MEDIAN)) {
            isMedian = true;
        }
        
        Map<Long, Double> movingDatapoints = new TreeMap<>();
        Map<Long, Double> sortedDatapoints = new TreeMap<>();
        List<Long> times = new ArrayList<>();
        
        while (scanner.hasNextDP()) {
	       	Map.Entry<Long, Double> dp = scanner.getNextDP();
	   		if (dp.getValue() == null) {
	       		sortedDatapoints.put(dp.getKey(), 0.0);
	      	} else {
	       		sortedDatapoints.put(dp.getKey(), dp.getValue());
	       	}
	   		times.add(dp.getKey());
	    }
        
        Long[] timestamps = new Long[times.size()];
        times.toArray(timestamps);
        
        double sum = 0.0;
        double value = 0.0;
        List<Double> numberArr = new ArrayList<Double>();
        
        try {
	        	sum = sortedDatapoints.get(timestamps[0]);
	        	numberArr.add(sortedDatapoints.get(timestamps[0]));
        } catch (NumberFormatException | NullPointerException e) {
	        	_logger.warn("Failed to parse datapoint " + sortedDatapoints.get(timestamps[0]));
	        	throw new UnsupportedOperationException("Bad datapoint!");
        }
        
        movingDatapoints.put(timestamps[0], sortedDatapoints.get(timestamps[0]));
        
        Long firstTimestamp = timestamps[0];
        int count = 1;
        
        for (int head = 1, tail = 0; head < timestamps.length; head++) {
        		if (tail == 0) {
        			while (timestamps[head] - firstTimestamp < windowSizeInSeconds * 1000) {
        				try {
	        				if (head >= timestamps.length - 1) {
	        					break;
	        				}
	        				if (isMedian) {
	        					double[] numbers = ArrayUtils.toPrimitive(numberArr.toArray(new Double[numberArr.size()]));
	        					value = new Percentile().evaluate(numbers, 50.0);
	        				} else {
	        					value = (sum / count);
	        				}
	        				movingDatapoints.put(timestamps[head - 1], value);
	        				sum += sortedDatapoints.get(timestamps[head]);
	        				numberArr.add(sortedDatapoints.get(timestamps[head]));
        				} catch (NumberFormatException | NullPointerException e) {
        					_logger.warn("Failed to parse datapoint: " + sortedDatapoints.get(timestamps[head]));
        					throw new IllegalArgumentException("Bad datapoint!");
        				}
        				head++;
        				count++;
        			}
        			
        			if (isMedian) {
        				double[] numbers = ArrayUtils.toPrimitive(numberArr.toArray(new Double[numberArr.size()]));
        				value = new Percentile().evaluate(numbers, 50.0);
        			} else {
        				value = (sum / count);
        			}
        			movingDatapoints.put(timestamps[head - 1], value);
        		}
        		
        		try {
        			sum += sortedDatapoints.get(timestamps[head]);
        			numberArr.add(sortedDatapoints.get(timestamps[head]));
        			while (timestamps[head] - timestamps[tail] >= windowSizeInSeconds * 1000) {
        				sum = _subtractWithinWindow(sum, sortedDatapoints, timestamps[tail], timestamps[head]);
        				numberArr.remove(sortedDatapoints.get(timestamps[tail]));
        				count--;
        				tail++;
        			}
        		} catch (NumberFormatException | NullPointerException e) {
        			_logger.warn("Failed to parse datapoint: " + sortedDatapoints.get(timestamps[head]));
        			throw new UnsupportedOperationException("Bad datapoint");
        		}
        		count++;
        		if (isMedian) {
        			double[] numbers = ArrayUtils.toPrimitive(numberArr.toArray(new Double[numberArr.size()]));
        			value = new Percentile().evaluate(numbers, 50.0);
        		} else {
        			value = sum / count;
        		}
        		movingDatapoints.put(timestamps[head], value);
        }
        return movingDatapoints;
    }

    @Override
    public String name() {
        return TransformFactory.Function.MOVING.name();
    }

    private double _subtractWithinWindow(double sum, Map<Long, Double> sortedDatapoints, long end, long start) {
        sum -= sortedDatapoints.get(end);
        return sum;
    }

    private long getWindowInSeconds(String window) {
        SystemAssert.requireArgument(!window.startsWith("-"), "Window size doesn't allow negative value.");

        MetricReader.TimeUnit timeunit = null;

        try {
            SystemAssert.requireArgument(!window.startsWith("-"), "Window size doesn't allow negative value.");
            timeunit = MetricReader.TimeUnit.fromString(window.substring(window.length() - 1));

            long timeDigits = Long.parseLong(window.substring(0, window.length() - 1));

            return timeDigits * timeunit.getValue() / 1000;
        } catch (NumberFormatException nfe) {
            throw new SystemException("Failed to parse time window.", nfe);
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

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

import com.google.common.primitives.Doubles;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.tsdb.MetricPageScanner;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.service.tsdb.MetricScanner;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemMain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.math3.stat.descriptive.rank.Percentile;

/**
 * Creates a constant line based on the calculated value.<br/>
 * <tt>FILL_CALCULATE(<expr>, <interval>, <interval>, <constant>)</tt>
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

    private static Map<Long, Double> fillCalculateMetricTransform(Metric metric, String calculationType) {
        // Calculate min, max, avg, dev, or a percentile value
        Double result = calculateResult(metric, calculationType);

        // return a new time series with the constant values for each time stamp
        Map<Long, Double> resultMap = new TreeMap<>();

        for (Map.Entry<Long, Double> entry : metric.getDatapoints().entrySet()) {
            Long timestamp = entry.getKey();

            resultMap.put(timestamp, result);
        }
        return resultMap;
    }
    
    private static Map<Long, Double> fillCalculateMetricTransformScanner(MetricScanner scanner, String calculationType) {
    	Double result = calculateResultScanner(scanner, calculationType);
    	
    	Map<Long, Double> resultMap = new TreeMap<>();
    	
    	SystemAssert.requireState(!scanner.hasNextDP(), "The scanner should be fully explored by this point.");
    	for (Map.Entry<Long, Double> entry : scanner.getMetric().getDatapoints().entrySet()) {
    		resultMap.put(entry.getKey(), result);
    	}
    	return resultMap;
    }
    
    private static Map<Long, Double> fillCalculateMetricTransformToPager(MetricScanner scanner, String calculationType, Long start, Long end) {
    	Double result = calculateResultPager(scanner, calculationType);
    	Map<Long, Double> resultMap = new TreeMap<>();
    	SystemAssert.requireArgument(!scanner.hasNextDP(), "The scanner should be fully explored by this point in order to reduce.");
    	for (Long time : scanner.getMetric().getDatapoints().keySet()) {
    		resultMap.put(time, result);
    	}
    	return resultMap;
    }

    private static Double calculateResult(Metric metric, String calculationType) {
        // Find the values from metric
        List<Double> valueList = new ArrayList<>(metric.getDatapoints().values());
        Double result = null;

        // if percentile transform requested, parse the string p0...p100.
        String rex = "^[pP](100|[0-9]{1,2})$";
        Pattern myPattern = Pattern.compile(rex);
        Matcher matcher = myPattern.matcher(calculationType);

        if (matcher.matches()) {
            Integer target = Integer.valueOf(matcher.group(1));

            result = new Percentile().evaluate(Doubles.toArray(valueList), target);
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
    
    private static Double calculateResultScanner(MetricScanner scanner, String calculationType) {
    	Double result = null;
    	
    	String rex = "^[pP](100|[0-9]{1,2})$";
    	Pattern myPattern = Pattern.compile(rex);
    	Matcher matcher = myPattern.matcher(calculationType);
    	
    	if (matcher.matches()) {
    		Integer target = Integer.valueOf(matcher.group(1));
    		List<Double> values = new ArrayList<>();
    		while (scanner.hasNextDP()) {
    			values.add(scanner.getNextDP().getValue());
    		}
    		result = new Percentile().evaluate(Doubles.toArray(values), target);
    	} else {
    		switch (calculationType) {
    		case "min":
    			MinValueReducer minr = new MinValueReducer();
    			result = minr.reduceScanner(scanner);
    			break;
    		case "max":
    			MaxValueReducer maxr = new MaxValueReducer();
    			result = maxr.reduceScanner(scanner);
    			break;
    		case "avg":
    			AverageValueReducer avgr = new AverageValueReducer();
    			result = avgr.reduceScanner(scanner);
    			break;
    		case "dev":
    		default:
    			throw new UnsupportedOperationException("Deviation Transform with Fill_Calculate is not yet supported!");
    		}
    	}
    	return result;
    }
    
    private static Double calculateReduction(MetricScanner scanner, ValueReducer red) {
    	Map.Entry<Long, Double> next = scanner.peek();
    	List<Double> vals = new ArrayList<>();
    	if (next == null) {
    		vals.addAll(scanner.getMetric().getDatapoints().values());
    		return red.reduce(vals);
    	} else if (next.getKey().equals(Collections.min(scanner.getMetric().getDatapoints().keySet()))) {
    		return red.reduceScanner(scanner);
    	} else {
    		while (scanner.hasNextDP()) {
    			scanner.getNextDP();
    		}
    		vals.addAll(scanner.getMetric().getDatapoints().values());
    		return red.reduce(vals);
    	}
    }
    private static Double calculateResultPager(MetricScanner scanner, String calculationType) {
    	Double result = null;
    	
    	String rex = "^[pP](100|[0-9]{1,2})$";
    	Pattern myPattern = Pattern.compile(rex);
    	Matcher matcher = myPattern.matcher(calculationType);
    	
    	if (matcher.matches()) {
    		Integer target = Integer.valueOf(matcher.group(1));
    		List<Double> values = new ArrayList<>();
    		Map.Entry<Long, Double> next = scanner.peek();
    		if (next == null) {
    			values.addAll(scanner.getMetric().getDatapoints().values());
    		} else {
    			if (!next.getKey().equals(Collections.min(scanner.getMetric().getDatapoints().keySet()))) {
    				TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
    				Long startKey = dps.firstKey();
    				Long endKey = dps.floorKey(next.getKey());
    				if (startKey != null && endKey != null && startKey < endKey) {
    					values.addAll(dps.subMap(startKey, endKey).values());
    				}
    			}
    			while (scanner.hasNextDP()) {
    				values.add(scanner.getNextDP().getValue());
    			}
    		}
    		result = new Percentile().evaluate(Doubles.toArray(values), target);
    	} else {
    		switch (calculationType) {
    		case "min":
    			MinValueReducer minr = new MinValueReducer();
    			result = calculateReduction(scanner, minr);
    			break;
    		case "max":
    			MaxValueReducer maxr = new MaxValueReducer();
    			result = calculateReduction(scanner, maxr);
    			break;
    		case "avg":
    			AverageValueReducer avgr = new AverageValueReducer();
    			result = calculateReduction(scanner, avgr);
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
    public List<Metric> transformScanner(List<MetricScanner> scanners) {
    	throw new UnsupportedOperationException("Fill Transform needs a type!");
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, Long start, Long end) {
    	throw new UnsupportedOperationException("Fill Transform needs a type!");
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
                Double calculateResult = calculateResult(metric, calculationType);

                // replace the 3rd value of constants with results.
                List<String> newConstants = new ArrayList<String>();

                newConstants.add(constants.get(1));
                newConstants.add(constants.get(2));
                newConstants.add(String.valueOf(calculateResult));

                List<Metric> singleMetric = new ArrayList<>();

                singleMetric.add(metric);
                fillCalculateMetricListWithOffset.addAll(fillTransform.transform(singleMetric, newConstants));
            }
            return fillCalculateMetricListWithOffset;
        }
        return fillCalculateMetricList;
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners, List<String> constants) {
    	List<Metric> fillCalculateMetricList = new ArrayList<Metric>();
    	
        SystemAssert.requireArgument(scanners != null, "Cannot transform null or empty metrics!");
        SystemAssert.requireArgument(constants != null && !constants.isEmpty(), "Fill_Calculate Transform needs a type!");
        SystemAssert.requireArgument(constants.size() <= 3, "Fill Transform needs an interval, an offset and a type!");
        
        String calculationType = constants.get(0);
        
        for (MetricScanner scanner : scanners) {
        	Metric newMetric = new Metric(scanner.getMetric());
        	
        	newMetric.setDatapoints(fillCalculateMetricTransformScanner(scanner, calculationType));
        	fillCalculateMetricList.add(newMetric);
        }
        
        if (constants.size() > 1 && constants.size() <= 3) {
        	List<Metric> fillCalculateMetricListWithOffset = new ArrayList<Metric>();
        	
        	for (Metric metric : fillCalculateMetricList) {
        		Transform fillTransform = new FillTransform();
        		Double calculateResult = calculateResult(metric, calculationType);
        		List<String> newConstants = new ArrayList<String>();
        		
        		newConstants.add(constants.get(1));
        		newConstants.add(constants.get(2));
        		newConstants.add(String.valueOf(calculateResult));
        		
        		List<Metric> singleMetric = new ArrayList<>();
        		
        		singleMetric.add(metric);
        		fillCalculateMetricListWithOffset.addAll(fillTransform.transform(singleMetric, newConstants));
        	}
        	return fillCalculateMetricListWithOffset;
        }
        return fillCalculateMetricList;
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, List<String> constants, Long start, Long end) {
    	List<Metric> fillCalculateMetricList = new ArrayList<Metric>();
    	
    	SystemAssert.requireArgument(scanners != null, "Cannot transform null or empty metrics!");
    	SystemAssert.requireArgument(constants != null && !constants.isEmpty(), "Fill_Calculate Transform needs a type!");
    	SystemAssert.requireArgument(constants.size() <= 3, "Fill Transform needs an interval, an offset, and a type!");
    	
    	String calculationType = constants.get(0);
    	
    	for (MetricScanner scanner : scanners) {
    		Map<Long, Double> dps = fillCalculateMetricTransformToPager(scanner, calculationType, start, end);
    		Metric newMetric = new Metric(scanner.getMetric());
    		newMetric.setDatapoints(dps);
    		fillCalculateMetricList.add(newMetric);
    	}
    	
    	if (constants.size() > 1 && constants.size() <= 3) {
    		List<Metric> fillCalculateMetricListWithOffset = new ArrayList<Metric>();
    		
    		for (Metric metric : fillCalculateMetricList) {
    			Transform fillTransform = new FillTransform();
    			Double calculateResult = calculateResult(metric, calculationType);
    			List<String> newConstants = new ArrayList<>();
    			newConstants.add(constants.get(1));
    			newConstants.add(constants.get(2));
    			newConstants.add(String.valueOf(calculateResult));
    			MetricScanner s = new MetricPageScanner(metric, (Long t) -> t);
    			s.dispose();
    			
    			fillCalculateMetricListWithOffset.addAll(fillTransform.transformToPager(Arrays.asList(s), newConstants, start, end));
    		}
    		return fillCalculateMetricListWithOffset;
    	}
    	
    	List<Metric> res = new ArrayList<>();
    	for (Metric m : fillCalculateMetricList) {
    		TreeMap<Long, Double> dps = new TreeMap<>(m.getDatapoints());
    		Long startKey = dps.ceilingKey(start);
    		Long endKey = dps.floorKey(end);
    		if (startKey != null && endKey != null && startKey <= endKey) {
    			m.setDatapoints(dps.subMap(startKey, endKey + 1));
    		} else {
    			m.setDatapoints(new TreeMap<>());
    		}
    		res.add(m);
    	}
    	return res;
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.FILL_CALCULATE.name();
    }

    @Override
    public List<Metric> transform(List<Metric>... listOfList) {
        throw new UnsupportedOperationException("Fill_Calculate doesn't need list of list!");
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner>... listOfList) {
    	throw new UnsupportedOperationException("Fill_Calculate doesn't need list of list!");
    }
    
    @Override
    public List<Metric> transformToPagerListOfList(List<List<MetricScanner>> scanners, Long start, Long end) {
    	throw new UnsupportedOperationException("Fill_Calculate doesn't need list of list!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

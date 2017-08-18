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
import com.salesforce.dva.argus.service.tsdb.MetricScanner;
import com.salesforce.dva.argus.system.SystemAssert;

import sun.tools.java.Scanner;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

/**
 * Normalizes the data point values of time series. If a normal constant is supplied, it is used as the unit normal,
 * if a metric normal is supplied the value at each timestamp of the metric normal is used as the unit normal,
 * otherwise, the unit normal is the sum of data point values at each time stamp.
 *
 * Corner cases:
 * 1. without constants, some metrics have missing points
 *      i.e.
 *      list of metrics:
 *  m1 -- dp{[1L, 1][2L, 2][3L, 3]}
 *  m2 -- dp{[1L, 10][2L, 20]}
 *
 *      expected(value-only) :
 *      sum vector should be [11 ,22, 3]
 *      normalized m1 should be [1/11, 2/22, 3/3]
 *      normalized m2 should be [10/11,20/22, 0/3]
 *
 * 2. without constants, some metrics have null or empty value
 *      give it a "0.0", its normalized value will always be "0.0"
 *
 * 3. with constants, some metrics have null or empty value
 *      give it a "0.0", its normalized value will always be "0.0"
 *
 * <br/>
 * <tt>NORMALIZE(<expr>)</tt>
 * <br/>
 * <tt>NORMALIZE(<expr>, <constant>)</tt>
 *
 * @param metrics The list of metrics to evaluate. Cannot be null or empty.
 * @param limit The value to be used as the unit normal value. Can be null.
 * @return One or more metrics corresponding to the input metrics, having had their data point values normalized.
 *
 *
 * @author Ruofan Zhang(rzhang@salesforce.com)
 */
public class NormalizeTransformWrap implements Transform {

    //~ Methods **************************************************************************************************************************************

    @Override
    public List<Metric> transform(List<Metric> metrics) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform null metric");
        if (metrics.isEmpty()) {
            return metrics;
        }

        // do a union transform, the reducer perform a sum calculation
        Transform unionTransform = new MetricUnionTransform(new CountValueUnionReducer());
        List<Metric> sumUnitMetric = unionTransform.transform(metrics);

        // union of all timestamp
        Set<Long> unionKeyset = sumUnitMetric.get(0).getDatapoints().keySet();
        
        // create a list of metrics, for every metric it has union of all timestamps
        List<Metric> paddingMetrics = new ArrayList<Metric>();

        // Padding Zeros for every datapoints map in every metric
        for (Metric metric : metrics) {
            Map<Long, Double> paddingDatapoints = new TreeMap<>();
            Set<Long> metricDPKeyset = metric.getDatapoints().keySet();

            // Calculate those timestamps this datapoint map doesn't have
            for (Long unionKey : unionKeyset) {
                if (!metricDPKeyset.contains(unionKey)) {
                    paddingDatapoints.put(unionKey, 0.0);
                }
            }
            
            // create paddingDatapoints
            paddingDatapoints.putAll(metric.getDatapoints());
            metric.setDatapoints(paddingDatapoints);
            paddingMetrics.add(metric);
        }

        // adding base metric
        paddingMetrics.add(sumUnitMetric.get(0));

        // do a divide_v transform
        // Transform divide_vTransform = transformFactory.createMetricZipperTransform(Function.DIVIDE_V);
        Transform divide_vTransform = new MetricZipperTransform(new DivideValueZipper());

        return divide_vTransform.transform(paddingMetrics);
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners) {
    	SystemAssert.requireArgument(scanners != null, "Cannot transform null metric scanner");
    	if (scanners.isEmpty()) {
    		return new ArrayList<Metric>();
    	}
    	
    	Transform unionTransform = new MetricUnionTransform(new CountValueUnionReducer());
    	List<Metric> sumUnitMetric = unionTransform.transformScanner(scanners);
    	
    	Set<Long> unionKeyset = sumUnitMetric.get(0).getDatapoints().keySet();
    	    	
    	List<Metric> paddingMetrics = new ArrayList<Metric>();
    	
    	for (MetricScanner scanner : scanners) {
    		SystemAssert.requireState(!scanner.hasNextDP(), "Cannot pad data of an incompletely searched scanner.");
    		Map<Long, Double> paddingDatapoints = new TreeMap<>();
    		Set<Long> metricDPKeyset = scanner.getMetric().getDatapoints().keySet();
    		
    		for (Long unionKey : unionKeyset) {
    			if (!metricDPKeyset.contains(unionKey)) {
    				paddingDatapoints.put(unionKey, 0.0);
    			}
    		}
    		
    		paddingDatapoints.putAll(scanner.getMetric().getDatapoints());
    		
    		Metric m = new Metric(scanner.getMetric());
    		m.setDatapoints(paddingDatapoints);
    		paddingMetrics.add(m);
    	}
    	
    	
    	paddingMetrics.add(sumUnitMetric.get(0));
    	
    	Transform divide_vTransform = new MetricZipperTransform(new DivideValueZipper());
    	
    	return divide_vTransform.transform(paddingMetrics);
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, Long start, Long end) {
    	SystemAssert.requireArgument(scanners != null, "Cannot transform null metric scanner");
    	if (scanners.isEmpty()) {
    		return new ArrayList<>();
    	}
    	Transform unionTransform = new MetricUnionTransform(new CountValueUnionReducer());
    	List<Metric> sumUnitMetric = unionTransform.transformToPager(scanners, start, end);
    	
    	Set<Long> unionKeyset = sumUnitMetric.get(0).getDatapoints().keySet();  	
    	List<Metric> paddingMetrics = new ArrayList<Metric>();
    	
    	for (MetricScanner scanner : scanners) {
    		SystemAssert.requireState(scanner.peek() == null || scanner.peek().getKey() > end,
    				"The scanner must be explored within the page range by this point");
    		TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
    		Long startKey = dps.ceilingKey(start);
    		Long endKey = dps.floorKey(end);
    		
    		Map<Long, Double> paddingDatapoints = new HashMap<>();
    		if (startKey != null && endKey != null && startKey <= endKey) {
    			paddingDatapoints.putAll(dps.subMap(startKey, endKey + 1));
    		}
    		
    		for (Long unionKey : unionKeyset) {
    			if (!paddingDatapoints.containsKey(unionKey)) {
    				paddingDatapoints.put(unionKey, 0.0);
    			}
    		}
			Metric m = new Metric(scanner.getMetric());
			m.setDatapoints(paddingDatapoints);
			paddingMetrics.add(m);
    	}

		paddingMetrics.add(sumUnitMetric.get(0));
		Transform divide_vTransform = new MetricZipperTransform(new DivideValueZipper());
		return divide_vTransform.transform(paddingMetrics);
    }

    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform null metric");
        if (metrics.isEmpty()) {
            return metrics;
        }
        if (constants == null || constants.isEmpty()) {
            return transform(metrics);
        } else {
            SystemAssert.requireArgument(constants.size() == 1, "Normalize Transform must provide only one constants if any.");
            SystemAssert.requireArgument(Double.parseDouble(constants.get(0)) != 0.0, "Normalize unit can't be ZERO.");
        }

        Transform divideByConstantTransform = new MetricMappingTransform(new DivideByConstantValueMapping());

        return divideByConstantTransform.transform(metrics, constants);
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners, List<String> constants) {
    	SystemAssert.requireArgument(scanners != null, "Cannot transform null metric scanner");
    	if (scanners.isEmpty()) {
    		return new ArrayList<Metric>();
    	}
    	if (constants == null || constants.isEmpty()) {
    		return transformScanner(scanners);
    	} else {
    		SystemAssert.requireArgument(constants.size() == 1, "Normalize Transform must provide only one constants if any.");
            SystemAssert.requireArgument(Double.parseDouble(constants.get(0)) != 0.0, "Normalize unit can't be ZERO.");
    	}
    	
    	Transform divideByConstantTransform = new MetricMappingTransform(new DivideByConstantValueMapping());
    	
    	return divideByConstantTransform.transformScanner(scanners, constants);
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, List<String> constants, Long start, Long end) {
    	SystemAssert.requireArgument(scanners != null, "Cannot transform null metric scanner");
    	if (scanners.isEmpty()) {
    		return new ArrayList<>();
    	}
    	if (constants == null || constants.isEmpty()) {
    		return transformToPager(scanners, start, end);
    	}
    	SystemAssert.requireArgument(constants.size() == 1, "Normalize Transform must provide only one constant if any.");
    	SystemAssert.requireArgument(Double.parseDouble(constants.get(0)) != 0.0, "Normalize unit can't be ZERO.");
    	
    	Transform divideByConstantTransform = new MetricMappingTransform(new DivideByConstantValueMapping());
    	return divideByConstantTransform.transformToPager(scanners, constants, start, end);
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.NORMALIZE.name();
    }

    @Override
    public List<Metric> transform(List<Metric>... listOfList) {
        throw new UnsupportedOperationException("NormalizeTransformWrap doesn't support list of list!");
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner>... listOfList) {
        throw new UnsupportedOperationException("NormalizeTransformWrap doesn't support list of list!");
    }
    
    @Override
    public List<Metric> transformToPagerListOfList(List<List<MetricScanner>> scanners, Long start, Long end) {
    	throw new UnsupportedOperationException("NormalizeTransformWrap doesn't support list of list!");
    }

    //~ Inner Classes ********************************************************************************************************************************

    /**
     * Actual performs the divide operation for the normalization.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    private static class DivideByConstantValueMapping implements ValueMapping {

        @Override
        public Map<Long, Double> mapping(Map<Long, Double> originalDatapoints, List<String> constants) {
            Map<Long, Double> divideByConstantDatapoints = new HashMap<>();

            for (Entry<Long, Double> entry : originalDatapoints.entrySet()) {
                Double adivideByConstantValue = null;

                if (entry.getValue() == null) {
                    adivideByConstantValue = 0.0;
                } else {
                    adivideByConstantValue = entry.getValue() / Double.parseDouble(constants.get(0));
                }
                
                divideByConstantDatapoints.put(entry.getKey(), adivideByConstantValue);
            }
            return divideByConstantDatapoints;
        }
        
        @Override
        public Map<Long, Double> mappingScanner(MetricScanner scanner, List<String> constants) {
        	Map<Long, Double> divideByConstantDatapoints = new HashMap<>();
    	
        	while (scanner.hasNextDP()) {
        		Map.Entry<Long, Double> dp = scanner.getNextDP();
        		
        		Double val = dp.getValue() == null ? 0.0 : dp.getValue() / Double.parseDouble(constants.get(0));
        		divideByConstantDatapoints.put(dp.getKey(), val);
        	}
        	return divideByConstantDatapoints;
        }
        
        @Override
        public Map<Long, Double> mappingToPager(MetricScanner scanner, List<String> constants, Long start, Long end) {
        	Map<Long, Double> divideByConstantDatapoints = new HashMap<>();
        	Map.Entry<Long, Double> next = scanner.peek();
        	Double divisor = Double.parseDouble(constants.get(0));
        	
        	if (next == null || next.getKey() > end) {
        		TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
        		Long startKey = dps.ceilingKey(start);
        		Long endKey = dps.floorKey(end);
        		if (startKey != null && endKey != null && startKey <= endKey) {
        			for (Map.Entry<Long, Double> entry : dps.subMap(startKey, endKey + 1).entrySet()) {
        				Double val = entry.getValue() == null ? 0.0 : entry.getValue() / divisor;
        				divideByConstantDatapoints.put(entry.getKey(), val);
        			}
        		}
        	} else {
        		if (next.getKey() > start) {
        			TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
        			Long startKey = dps.ceilingKey(start);
        			Long endKey = dps.floorKey(next.getKey());
        			if (startKey != null && endKey != null && startKey < endKey) {
        				for (Map.Entry<Long, Double> entry : dps.subMap(startKey, endKey).entrySet()) {
        					Double val = entry.getValue() == null ? 0.0 : entry.getValue() / divisor;
        					divideByConstantDatapoints.put(entry.getKey(), val);
        				}
        			}
        		} else {
        			while (scanner.peek() != null && scanner.peek().getKey() < start) {
        				scanner.getNextDP();
        			}
        		}
        		
        		while (scanner.peek() != null && scanner.peek().getKey() <= end) {
        			Map.Entry<Long, Double> dp = scanner.getNextDP();
        			Double val = dp.getValue() == null ? 0.0 : dp.getValue() / divisor;
        			divideByConstantDatapoints.put(dp.getKey(), val);
        		}
        	}
        	return divideByConstantDatapoints;
        }

        @Override
        public Map<Long, Double> mapping(Map<Long, Double> originalDatapoints) {
            throw new UnsupportedOperationException("Divide By Constant transform needs a constant!");
        }
        
        @Override
        public Map<Long, Double> mappingScanner(MetricScanner scanner) {
            throw new UnsupportedOperationException("Divide By Constant transform needs a constant!");
        }
        
        @Override
        public Map<Long, Double> mappingToPager(MetricScanner scanner, Long start, Long end) {
        	throw new UnsupportedOperationException("Divide By Constant transform needs a constant!");
        }

        @Override
        public String name() {
            return TransformFactory.Function.DIVIDE.name();
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

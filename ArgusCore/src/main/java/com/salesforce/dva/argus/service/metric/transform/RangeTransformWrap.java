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

import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.tsdb.MetricScanner;
import com.sun.tools.javac.main.Main.Result;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Calculate the difference between the maximum and minimum values at each time stamp. If a single time series is specified, the minimum and maximum
 * of all values in the time series is returned.
 *
 * <p>Tricky part here is that Range reducer behaviour is similar to the reducer in MetricReducerOrMapping not MetricReducer</p>
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class RangeTransformWrap implements Transform {

    //~ Methods **************************************************************************************************************************************

    @Override
    public List<Metric> transform(List<Metric> metrics) {
        if (metrics.size() == 1) {
            return rangeOfOneMetric(metrics.get(0));
        } else {
            return new MetricReducerOrMappingTransform(new RangeValueReducerOrMapping()).transform(metrics);
        }
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners) {
    	if (scanners.size() == 1) {
    		return rangeOfOneMetricScanner(scanners.get(0));
    	} else {
    		return new MetricReducerOrMappingTransform(new RangeValueReducerOrMapping()).transformScanner(scanners);
    	}
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, Long start, Long end) {
    	if (scanners.size() == 1) {
    		return rangeOfOneMetricToPager(scanners.get(0), start, end);
    	} else {
    		return new MetricReducerOrMappingTransform(new RangeValueReducerOrMapping()).transformToPager(scanners, start, end);
    	}
    }

    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
        throw new UnsupportedOperationException("Range Transform doesn't accept constants!");
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners, List<String> constants) {
        throw new UnsupportedOperationException("Range Transform doesn't accept constants!");
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, List<String> constants, Long start, Long end) {
    	throw new UnsupportedOperationException("Range Transform doesn't accept constants!");
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.RANGE.name();
    }

    private List<Metric> rangeOfOneMetric(Metric metric) {
        Map<Long, Double> cleanDPs = new HashMap<>();

        for (Map.Entry<Long, Double> entry : metric.getDatapoints().entrySet()) {
            if (entry.getValue() == null) {
                cleanDPs.put(entry.getKey(), 0.0);
            } else {
                cleanDPs.put(entry.getKey(), entry.getValue());
            }
        }

        List<Metric> result = new ArrayList<Metric>();
        final List<Double> dpNum = new ArrayList<Double>();

        for (Double value : cleanDPs.values()) {
            dpNum.add(value);
        }
        Collections.sort(dpNum);

        @SuppressWarnings("serial")
        final Set<Double> minMaxSet = new HashSet<Double>() {

                {
                    add(dpNum.get(0));
                    add(dpNum.get(dpNum.size() - 1));
                }
            };

        Predicate<Map.Entry<Long, Double>> isMinMax = new Predicate<Map.Entry<Long, Double>>() {

                @Override
                public boolean apply(Map.Entry<Long, Double> datapoint) {
                    return minMaxSet.contains(datapoint.getValue());
                }
            };

        Map<Long, Double> resultDatapoints = new HashMap<>();

        resultDatapoints.putAll(Maps.filterEntries(cleanDPs, isMinMax));
        metric.setDatapoints(resultDatapoints);
        result.add(metric);
        return result;
    }
    
    private List<Metric> rangeOfOneMetricScanner(MetricScanner scanner) {
    	Map<Long, Double> cleanDPs = new HashMap<>();
    	List<Metric> result = new ArrayList<>();
    	final List<Double> dpNum = new ArrayList<>();
    	
    	while (scanner.hasNextDP()) {
    		Map.Entry<Long, Double> dp = scanner.getNextDP();
    		if (dp.getValue() == null) {
    			cleanDPs.put(dp.getKey(), 0.0);
    			dpNum.add(0.0);
    		}
    		else {
    			cleanDPs.put(dp.getKey(), dp.getValue());
    			dpNum.add(dp.getValue());
    		}
    	}
    	    		
    	Collections.sort(dpNum);
    	
    	@SuppressWarnings("serial")
    	final Set<Double> minMaxSet = new HashSet<Double>() {
    		{
    			add(dpNum.get(0));
    			add(dpNum.get(dpNum.size() - 1));
    		}
    	};
    	
    	Predicate<Map.Entry<Long, Double>> isMinMax = new Predicate<Map.Entry<Long, Double>>() {
    		
    		@Override
    		public boolean apply(Map.Entry<Long, Double> datapoint) {
    			return minMaxSet.contains(datapoint.getValue());
    		}
    	};
    	
    	Map<Long, Double> resultDatapoints = new HashMap<>();
    	
    	resultDatapoints.putAll(Maps.filterEntries(cleanDPs, isMinMax));
    	Metric m = new Metric(scanner.getMetric());
    	m.setDatapoints(resultDatapoints);
    	result.add(m);
    	return result;
    }
    
    private List<Metric> rangeOfOneMetricToPager(MetricScanner scanner, Long start, Long end) {
    	Map<Long, Double> cleanDPs = new HashMap<>();
    	List<Metric> result = new ArrayList<>();
    	
    	Map.Entry<Long, Double> next = scanner.peek();
    	
    	if (next == null) {
    		for (Map.Entry<Long, Double> entry : scanner.getMetric().getDatapoints().entrySet()) {
    			cleanDPs.put(entry.getKey(), entry.getValue() == null ? 0.0 : entry.getValue());
    		}
    	} else if (!next.getKey().equals(Collections.min(scanner.getMetric().getDatapoints().keySet()))) {
    		TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
    		Long startKey = dps.firstKey();
    		Long endKey = dps.floorKey(next.getKey());
    		if (startKey != null && endKey != null && startKey < endKey) {
    			for (Map.Entry<Long, Double> entry : dps.subMap(startKey, endKey).entrySet()) {
    				cleanDPs.put(entry.getKey(), entry.getValue() == null ? 0.0 : entry.getValue());
    			}
    		}
    	} else {
    		while (scanner.peek() != null && scanner.peek().getKey() < start) {
    			Map.Entry<Long, Double> dp = scanner.getNextDP();
    			cleanDPs.put(dp.getKey(), dp.getValue() == null ? 0.0 : dp.getValue());
    		}
    	}
    	
    	while (scanner.hasNextDP()) {
    		Map.Entry<Long, Double> dp = scanner.getNextDP();
    		cleanDPs.put(dp.getKey(), dp.getValue() == null ? 0.0 : dp.getValue());
    	}
    	
    	if (cleanDPs.isEmpty()) {
    		return new ArrayList<>();
    	}
    	
    	@SuppressWarnings("serial")
		final Set<Double> minMaxSet = new HashSet<Double>() {
    		{
    			add(Collections.max(cleanDPs.values()));
    			add(Collections.min(cleanDPs.values()));
    		}
    	};
    	    	
    	Predicate<Map.Entry<Long, Double>> isMinMax = new Predicate<Map.Entry<Long, Double>>() {
    		
    		@Override
    		public boolean apply(Map.Entry<Long, Double> datapoint) {
    			return minMaxSet.contains(datapoint.getValue());
    		}
    	};
    	
    	TreeMap<Long, Double> resultDatapoints = new TreeMap<>();
    	resultDatapoints.putAll(Maps.filterEntries(cleanDPs, isMinMax));
    	Long startKey = resultDatapoints.ceilingKey(start);
    	Long endKey = resultDatapoints.floorKey(end);
    	Metric m = new Metric(scanner.getMetric());
    	if (startKey == null || endKey == null || startKey > endKey) {
    		m.setDatapoints(new TreeMap<>());
    	} else {
    		m.setDatapoints(resultDatapoints.subMap(startKey, endKey + 1));
    	}
    	result.add(m);
    	return result;
    }

    @Override
    public List<Metric> transform(List<Metric>... listOfList) {
        throw new UnsupportedOperationException("Range Transform doesn't accept list of metric list!");
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner>... listOfList) {
        throw new UnsupportedOperationException("Range Transform doesn't accept list of metric list!");
    }
    
    @Override
    public List<Metric> transformToPagerListOfList(List<List<MetricScanner>> scanners, Long start, Long end) {
    	throw new UnsupportedOperationException("Range Transform doesn't accept list of metric list!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

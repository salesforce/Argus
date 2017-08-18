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
import com.google.common.primitives.Doubles;
import com.salesforce.dva.argus.service.tsdb.MetricScanner;
import com.salesforce.dva.argus.system.SystemAssert;
import org.apache.commons.math.stat.descriptive.rank.Percentile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Removes data points from metrics if their evaluated value is above a limit.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class CullAboveValueMapping implements ValueMapping {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final String PERCENTILE = "percentile";
    private static final String VALUE = "value";

    //~ Methods **************************************************************************************************************************************

    @Override
    public Map<Long, Double> mapping(Map<Long, Double> originalDatapoints) {
        throw new UnsupportedOperationException("Cull Above Transform needs a limit and a type.");
    }
    
    @Override
    public Map<Long, Double> mappingScanner(MetricScanner scanner) {
    		throw new UnsupportedOperationException("Cull Above Transform needs a limit and a type.");
    }
    
    @Override
    public Map<Long, Double> mappingToPager(MetricScanner scanner, Long start, Long end) {
    	throw new UnsupportedOperationException("Cull Above Transform needs a limit and a type.");
    }

    @Override
    public Map<Long, Double> mapping(Map<Long, Double> originalDatapoints, List<String> constants) {
        SystemAssert.requireArgument(constants != null, "Moving Average Transform needs a window size of time interval");
        SystemAssert.requireArgument(constants.size() == 2, "Cull Above Transform must provide exactly 2 constants which are limit and type.");

        final Double limit = Double.parseDouble(constants.get(0));
        String type = constants.get(1);

        SystemAssert.requireArgument(type.equals(PERCENTILE) || type.equals(VALUE), "Only percentile and value is allowed for type input.");
        if (type.equals(PERCENTILE)) {
            SystemAssert.requireArgument(limit > 0 && limit <= 100, "Percentile number must in (0,100] !");
        }

        final Double pivot = type.equals(PERCENTILE) ? findPivot(originalDatapoints, limit) : limit;
        Predicate<Map.Entry<Long, Double>> isAbove = new Predicate<Map.Entry<Long, Double>>() {

                @Override
                public boolean apply(Map.Entry<Long, Double> datapoint) {
                    return datapoint.getValue() <= pivot;
                }
            };

        Map<Long, Double> result = new HashMap<>();

        result.putAll(Maps.filterEntries(originalDatapoints, isAbove));
        return result;
    }
    
    @Override
    public Map<Long, Double> mappingScanner(MetricScanner scanner, List<String> constants) {
		SystemAssert.requireArgument(constants != null, "Moving Average Transform needs a window size of time interval");
		SystemAssert.requireArgument(constants.size() == 2, "Cull Above Transform must provide exactly 2 constants which are limit and type.");
		
		final Double limit = Double.parseDouble(constants.get(0));
		String type = constants.get(1);
		
		SystemAssert.requireArgument(type.equals(PERCENTILE) || type.equals(VALUE), "Only percentile and value is allowed for type input");
		if (type.equals(PERCENTILE)) {
			SystemAssert.requireArgument(limit > 0 && limit <= 100, "Percentile number must be in (0, 100] !");
		}
		    		
		final Double pivot = type.equals(PERCENTILE) ? findPivotScanner(scanner, limit) : limit;
		if (type.equals(VALUE)) {
			findDps(scanner);
		}
		Predicate<Map.Entry<Long, Double>> isAbove = new Predicate<Map.Entry<Long, Double>>() {
			@Override
			public boolean apply(Map.Entry<Long, Double> datapoint) {
				return datapoint.getValue() <= pivot;
			}
		};
		
		Map<Long, Double> result = new HashMap<>();
		result.putAll(Maps.filterEntries(scanner.getMetric().getDatapoints(), isAbove));
		return result;
	}
    
    @Override
    public Map<Long, Double> mappingToPager(MetricScanner scanner, List<String> constants, Long start, Long end) {
    	SystemAssert.requireArgument(constants != null, "Moving Average Transform needs a window size of time interval");
		SystemAssert.requireArgument(constants.size() == 2, "Cull Above Transform must provide exactly 2 constants which are limit and type.");
		
		final Double limit = Double.parseDouble(constants.get(0));
		String type = constants.get(1);
		SystemAssert.requireArgument(type.equals(PERCENTILE) || type.equals(VALUE), "Only percentile and value is allowed for type input");
		
		Map.Entry<Long, Double> next = scanner.peek();
		if (type.equals(PERCENTILE)) {
			if (next == null) {
				/* scanner has been fully explored */
				TreeMap<Long, Double> res = new TreeMap<>(mapping(scanner.getMetric().getDatapoints(), constants));
				Long startKey = res.ceilingKey(start);
				Long endKey = res.floorKey(end);
				if (startKey == null || endKey == null || startKey > endKey) {
					return new TreeMap<>();
				}
				return res.subMap(startKey, endKey + 1);
			} else if (next.getKey().equals(Collections.min(scanner.getMetric().getDatapoints().keySet()))) {
				/* not explored at all */
				TreeMap<Long, Double> res = new TreeMap<>(mappingScanner(scanner, constants));
				Long startKey = res.ceilingKey(start);
				Long endKey = res.floorKey(end);
				if (startKey == null || endKey == null || startKey > endKey) {
					return new TreeMap<>();
				}
				return res.subMap(start, endKey + 1);
			} else {
				/* Partially explored, but still need to look at all of the data to determine the percentile */
				while (scanner.hasNextDP()) {
					scanner.getNextDP();
				}
				TreeMap<Long, Double> res = new TreeMap<>(mapping(scanner.getMetric().getDatapoints(), constants));
				Long startKey = res.ceilingKey(start);
				Long endKey = res.floorKey(end);
				if (startKey == null || endKey == null || startKey > endKey) {
					return new TreeMap<>();
				}
				return res.subMap(startKey, endKey + 1);
			}
		}
		
		Map<Long, Double> result = new HashMap<>();
		if (next == null || next.getKey() > end) {
			TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
			Long startKey = dps.ceilingKey(start);
			Long endKey = dps.floorKey(end);
			if (startKey == null || endKey == null || startKey > endKey) {
				return new TreeMap<>();
			}
			return mapping(dps.subMap(startKey, endKey + 1));
		} else if (next.getKey() > start) {
			TreeMap<Long, Double> dps = new TreeMap<Long, Double>(scanner.getMetric().getDatapoints());
			Long startKey = dps.ceilingKey(start);
			Long endKey = dps.floorKey(next.getKey());
			if (startKey != null && endKey != null && startKey < endKey) {
				result.putAll(mapping(dps.subMap(startKey, endKey), constants));
			}
		} else {
			while (scanner.peek() != null && scanner.peek().getKey() < start) {
				scanner.getNextDP();
			}
		}
		while (scanner.peek() != null && scanner.peek().getKey() <= end) {
			Map.Entry<Long, Double> nextDP = scanner.getNextDP();
			if (nextDP.getValue() <= limit) {
				result.put(nextDP.getKey(), nextDP.getValue());
			}
		}
		return result;
    }
    
    private void findDps(MetricScanner scanner) {    		
		while (scanner.hasNextDP()) {
			scanner.getNextDP();
		}
    }

    @Override
    public String name() {
        return TransformFactory.Function.CULL_ABOVE.name();
    }

    /*
     * If type is percentile, find out the estimate of the limit(th) percentile in the datapoint sorted values. Then execute the same as type is
     * value. That means to cull the elements greater than value or pivotValue prerequisite: array must be sorted
     */
    private Double findPivot(Map<Long, Double> datapoints, Double limit) {
        double[] doubleValues = new double[datapoints.size()];
        int k = 0;

        for (Map.Entry<Long, Double> entry : datapoints.entrySet()) {
            doubleValues[k] = entry.getValue();
            k++;
        }
        Arrays.sort(doubleValues);

        double pivotValue = Double.MAX_VALUE;

        try {
            pivotValue = new Percentile().evaluate(doubleValues, (double) limit);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Please provide a valid percentile number!");
        }
        return pivotValue;
    }
    
    /*
     * If type is percentile, find out the estimate of the limit(th) percentile in the datapoints encapsulated by the scanner.
     * The execute the same as if the type was value, culling the elements greater than value of pivotValue. The array must be sorted.
     */
    private Double findPivotScanner(MetricScanner scanner, Double limit) {
    	List<Double> doubleValuesList = new ArrayList<Double>();
    	
		while(scanner.hasNextDP()) {
			doubleValuesList.add(scanner.getNextDP().getValue());
		}
    	double[] doubleValues = Doubles.toArray(doubleValuesList);
    	Arrays.sort(doubleValues);
    	
    	double pivotValue = Double.MAX_VALUE;
    	
    	try {
    		pivotValue = new Percentile().evaluate(doubleValues, (double) limit);
    	} catch (IllegalArgumentException e) {
    		throw new IllegalArgumentException("Please provide a valid percentile number!");
    	}
    	return pivotValue;
    }
    
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

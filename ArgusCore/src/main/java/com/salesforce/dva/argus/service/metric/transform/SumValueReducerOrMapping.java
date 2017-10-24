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

import com.salesforce.dva.argus.service.tsdb.MetricScanner;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Calculates an arithmetic sum. If a constant is provided, it is added to each data point in the set of input metrics, otherwise the data point
 * values of each time stamp are summed.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class SumValueReducerOrMapping implements ValueReducerOrMapping {

    //~ Methods **************************************************************************************************************************************

    @Override
    public Double reduce(List<Double> values) {
        return Reducers.sumReducer(values);
    }
    
    @Override
    public Double reduceScanner(MetricScanner scanner) {
    		return Reducers.sumReducerScanner(scanner);
    }

    @Override
    public Map<Long, Double> mapping(Map<Long, Double> originalDatapoints) {
        throw new UnsupportedOperationException("Sum Transform with mapping is not supposed to be used without a constant");
    }
    
    @Override
    public Map<Long, Double> mappingScanner(MetricScanner scanner) {
        throw new UnsupportedOperationException("Sum Transform with mapping is not supposed to be used without a constant");
    }
    
    @Override
    public Map<Long, Double> mappingToPager(MetricScanner scanner, Long start, Long end) {
    	throw new UnsupportedOperationException("Sum Transform with mapping is not supposed to be used without a constant");
    }

    @Override
    public Map<Long, Double> mapping(Map<Long, Double> originalDatapoints, List<String> constants) {
        SystemAssert.requireArgument(constants != null && constants.size() == 1,
            "If constants provided for sum transform, only exactly one constant allowed.");

        try {
            double addend = Double.parseDouble(constants.get(0));

            return originalDatapoints.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            e -> e.getValue() + addend
                    ));
        } catch (NullPointerException|NumberFormatException nfe) {
            throw new SystemException("Illegal constant value supplied to sum transform", nfe);
        }
    }
    
    @Override
    public Map<Long, Double> mappingScanner(MetricScanner scanner, List<String> constants) {
		SystemAssert.requireArgument(constants != null && constants.size() == 1,
            "If constants provided for sum transform, only exactly one constant allowed.");
		
		Map<Long, Double> mappedDatapoints = new HashMap<>();
		try {
			double addend = Double.parseDouble(constants.get(0));
			
   			while (scanner.hasNextDP()) {
   				Map.Entry<Long, Double> dp = scanner.getNextDP();
   				if (dp.getValue() == null) {
   					continue;
    			}
    			mappedDatapoints.put(dp.getKey(), dp.getValue() + addend);
    		}
		} catch (NullPointerException | NumberFormatException nfe)  {
			throw new SystemException("Illegal constant value supplied to sum transform", nfe);
		}
		return mappedDatapoints;
    }
    
    @Override
    public Map<Long, Double> mappingToPager(MetricScanner scanner, List<String> constants, Long start, Long end) {
    	SystemAssert.requireArgument(constants != null && constants.size() == 1, 
    			"If constants provided for sum transform, only exactly one constant allowed.");
    	
    	Map<Long, Double> mappedDatapoints = new HashMap<>();
    	Map.Entry<Long, Double> next = scanner.peek();
    	if (next == null || next.getKey() > end) {
    		TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
    		Long startKey = dps.ceilingKey(start);
    		Long endKey = dps.floorKey(end);
    		if (startKey == null || endKey == null || startKey > endKey) {
    			return new TreeMap<>();
    		}
    		mappedDatapoints = mapping(dps.subMap(startKey, endKey + 1));
    		return mappedDatapoints;
    	} else if (next.getKey() > start) {
    		TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
    		Long startKey = dps.ceilingKey(start);
    		Long endKey = dps.floorKey(next.getKey());
    		if (startKey != null && endKey != null && startKey < endKey) {
    			mappedDatapoints.putAll(mapping(dps.subMap(startKey, endKey), constants));
    		}
    	} else {
    		while (scanner.peek() != null && scanner.peek().getKey() < start) {
    			scanner.getNextDP();
    		}
    	}
    	
    	try {
    		double addend = Double.parseDouble(constants.get(0));
    		
    		while (scanner.peek() != null && scanner.peek().getKey() <= end) {
    			Map.Entry<Long, Double> dp = scanner.getNextDP();
    			if (dp.getValue() == null) {
    				continue;
    			}
    			mappedDatapoints.put(dp.getKey(), dp.getValue() + addend);
    		}
    	} catch (NumberFormatException nfe) {
    		throw new SystemException("Illegal constant value supplied to sum transform", nfe);
    	}
    	return mappedDatapoints;
    }
 
    @Override
    public Double reduce(List<Double> values, List<String> constants) {
        throw new UnsupportedOperationException("Sum Transform with reducer is not supposed to be used with a constant");
    }
    
    @Override
    public Double reduceScanner(MetricScanner scanner, List<String> constants) {
        throw new UnsupportedOperationException("Sum Transform with reducer is not supposed to be used with a constant");
    }

    @Override
    public String name() {
        return TransformFactory.Function.SUM.name();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

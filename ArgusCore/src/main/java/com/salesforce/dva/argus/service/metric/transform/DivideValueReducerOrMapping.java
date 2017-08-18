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

/**
 * Calculates an quotient. If a constant is provided, it is divided into each data point in the set of input metrics, otherwise the data point values
 * of each time stamp for all but the first metric are divided into the data point value of the first metric.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class DivideValueReducerOrMapping implements ValueReducerOrMapping {

    //~ Methods **************************************************************************************************************************************

    @Override
    public Double reduce(List<Double> values) {
        Double quotient = values.get(0);

        for (int i = 1; i < values.size(); i++) {
            Double value = values.get(i);

            if (value == null) {
                continue;
            }
            if (value == 0.0) { // Sys Require argument
                throw new ArithmeticException("this datapoints sets have a value of zero!");
            }
            quotient /= value;
        }
        return quotient;
    }
    
    @Override
    public Double reduceScanner(MetricScanner scanner) {
    	SystemAssert.requireArgument(scanner.hasNextDP(), "The Metric Scanner must contain a datapoint");
    	
    	Double quotient = scanner.getNextDP().getValue();
    	SystemAssert.requireArgument(quotient != null, "The Metric Scanner must contain a non-null first datapoint.");
    	
    	while (scanner.hasNextDP()) {
    		Double value = scanner.getNextDP().getValue();
    		
    		if (value == null) {
    			continue;
    		}
    		SystemAssert.requireArgument(value != 0.0, "This scanner datapoint set has a value of zero!");
    		quotient /= value;
    	}
    	return quotient;
    }

    @Override
    public Map<Long, Double> mapping(Map<Long, Double> originalDatapoints) {
        throw new UnsupportedOperationException("Divide Transform with mapping is not supposed to be used without a constant");
    }
    
    @Override
    public Map<Long, Double> mappingScanner(MetricScanner scanner) {
        throw new UnsupportedOperationException("Divide Transform with mapping is not supposed to be used without a constant");
    }
    
    @Override
    public Map<Long, Double> mappingToPager(MetricScanner scanner, Long start, Long end) {
    	throw new UnsupportedOperationException("Divide Transform with mapping is not supposed to be used without a constant");
    }

    @Override
    public Map<Long, Double> mapping(Map<Long, Double> originalDatapoints, List<String> constants) {
        SystemAssert.requireArgument(constants != null && constants.size() == 1,
            "If constants provided for divide transform, only exactly one constant allowed.");

        Map<Long, Double> divideDatapoints = new HashMap<>();

        try {
            double divisor = Double.parseDouble(constants.get(0));

            SystemAssert.requireArgument(divisor != 0, "The constant divisor cannot be zero.");
            for (Map.Entry<Long, Double> entry : originalDatapoints.entrySet()) {
                Double dividend = entry.getValue();
                double quotientValue = dividend / divisor;

                divideDatapoints.put(entry.getKey(), quotientValue);
            }
        } catch (NumberFormatException nfe) {
            throw new SystemException("Illegal constant value supplied to divide transform", nfe);
        }
        return divideDatapoints;
    }
    
    @Override
    public Map<Long, Double> mappingScanner(MetricScanner scanner, List<String> constants) {
    	SystemAssert.requireArgument(constants != null && constants.size() == 1, "If constants provided for divide transform, only "
    			+ "exactly one constant allowed.");
    	
    	Map<Long, Double> divideDatapoints = new HashMap<>();
    	
    	try {
    		double divisor = Double.parseDouble(constants.get(0));   		
    		SystemAssert.requireArgument(divisor != 0, "The constant divisor cannot be zero.");
    		
       		while (scanner.hasNextDP()) {
    			Map.Entry<Long, Double> dp = scanner.getNextDP();
    			divideDatapoints.put(dp.getKey(), dp.getValue() / divisor);
    		}
    	} catch (NumberFormatException nfe) {
            throw new SystemException("Illegal constant value supplied to divide transform", nfe);
    	}
    	return divideDatapoints;
    }
    
    @Override
    public Map<Long, Double> mappingToPager(MetricScanner scanner, List<String> constants, Long start, Long end) {
    	SystemAssert.requireArgument(constants != null && constants.size() == 1, "If constants provided for divide transform, only "
    			+ " exactly one constant allowed.");
    	
    	Map<Long, Double> divideDatapoints = new HashMap<>();
    	Map.Entry<Long, Double> next = scanner.peek();
    	if (next == null || next.getKey() > end) {
    		TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
    		Long startKey = dps.ceilingKey(start);
    		Long endKey = dps.floorKey(end);
    		if (startKey == null || endKey == null || startKey > endKey) {
    			return new HashMap<>();
    		}
    		divideDatapoints = mapping(dps.subMap(startKey, endKey + 1), constants);
    		return divideDatapoints;
    	} else if (next.getKey() > start) {
    		TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
    		Long startKey = dps.ceilingKey(start);
    		Long endKey = dps.floorKey(next.getKey());
    		if (startKey != null && endKey != null && startKey < endKey) {
    			divideDatapoints.putAll(mapping(dps.subMap(startKey, endKey), constants));
    		}
    	} else {
    		while (scanner.peek() != null && scanner.peek().getKey() < start) {
    			scanner.getNextDP();
    		}
    	}
    	
    	try {
    		double divisor = Double.parseDouble(constants.get(0));
    		SystemAssert.requireArgument(divisor != 0.0, "The constant divisor cannot be zero.");
    		
    		while(scanner.peek() != null && scanner.peek().getKey() <= end) {
    			Map.Entry<Long, Double> dp = scanner.getNextDP();
    			divideDatapoints.put(dp.getKey(), dp.getValue() / divisor);
    		}
    	} catch (NumberFormatException nfe) {
    		throw new SystemException("Illegal constant value supplied to divide transform" , nfe);
    	}
       	return divideDatapoints;
    }

    @Override
    public Double reduce(List<Double> values, List<String> constants) {
        throw new UnsupportedOperationException("Divide Transform with reducer is not supposed to be used with a constant");
    }
    
    @Override
    public Double reduceScanner(MetricScanner scanner, List<String> constants) {
        throw new UnsupportedOperationException("Divide Transform with reducer is not supposed to be used with a constant");
    }

    @Override
    public String name() {
        return TransformFactory.Function.DIVIDE.name();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

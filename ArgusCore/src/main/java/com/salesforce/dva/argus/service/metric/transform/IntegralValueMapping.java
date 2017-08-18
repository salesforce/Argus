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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import com.salesforce.dva.argus.service.tsdb.MetricScanner;

import java.util.TreeMap;

/**
 * Calculates the discrete time integral. *
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class IntegralValueMapping implements ValueMapping {

    //~ Methods **************************************************************************************************************************************

    @Override
    public Map<Long, Double> mapping(Map<Long, Double> originalDatapoints) {
        Map<Long, Double> sortedDatapoints = new TreeMap<>();
        Double prevSum = 0.0;

        sortedDatapoints.putAll(originalDatapoints);
        for (Entry<Long, Double> entry : sortedDatapoints.entrySet()) {
            prevSum += entry.getValue();
            sortedDatapoints.put(entry.getKey(), prevSum);
        }
        return sortedDatapoints;
    }
    
    @Override
    public Map<Long, Double> mappingScanner(MetricScanner scanner) {
		Map<Long, Double> integralDP = new TreeMap<>();
		Double prevSum = 0.0;
		
		while (scanner.hasNextDP()) {
			Map.Entry<Long, Double> dp = scanner.getNextDP();
			prevSum += dp.getValue();
			integralDP.put(dp.getKey(), prevSum);
		}
		return integralDP;
    }
    
    @Override
    public Map<Long, Double> mappingToPager(MetricScanner scanner, Long start, Long end) {
    	TreeMap<Long, Double> integralDP = new TreeMap<>();
    	Map.Entry<Long, Double> next = scanner.peek();
    	Double lastSum = 0.0;
    	if (next == null || next.getKey() > end) {
    		TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
    		Long startKey = dps.ceilingKey(start);
    		Long endKey = dps.floorKey(end);
    		if (startKey == null || endKey == null || startKey > endKey) {
    			return new TreeMap<>();
    		}
    		TreeMap<Long, Double> result = new TreeMap<>(mapping(dps.subMap(dps.firstKey(), endKey + 1)));
    		integralDP.putAll(result.subMap(startKey, endKey + 1));
    		return integralDP;
    	} else if (next.getKey() > start || next.getKey() > Collections.min(scanner.getMetric().getDatapoints().keySet())) {
    		TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
    		Long startKey = dps.ceilingKey(start);
    		Long endKey = dps.floorKey(next.getKey());
    		if (endKey != null) {
    			TreeMap<Long, Double> result = new TreeMap<>(mapping(dps.subMap(dps.firstKey(), endKey)));
    			lastSum = result.size() == 0 ? 0.0 : result.lastEntry().getValue();
	    		if (startKey != null && endKey != null && startKey < endKey) {
	    			integralDP.putAll(result.subMap(startKey, endKey));
	    		}
    		}
    	}
    	
    	while (scanner.peek() != null && scanner.peek().getKey() < start) {
    		lastSum += scanner.getNextDP().getValue();
    	}
    	
    	while (scanner.peek() != null && scanner.peek().getKey() <= end) {
    		Map.Entry<Long, Double> dp = scanner.getNextDP();
    		lastSum += dp.getValue();
    		integralDP.put(dp.getKey(), lastSum);
    	}
    	
    	return integralDP;
    }

    @Override
    public Map<Long, Double> mapping(Map<Long, Double> originalDatapoints, List<String> constants) {
        throw new UnsupportedOperationException("Integral Transform is not supposed to be used with a constant");
    }
    
    @Override
    public Map<Long, Double> mappingScanner(MetricScanner scanner, List<String> constants) {
        throw new UnsupportedOperationException("Integral Transform is not supposed to be used with a constant");
    }
    
    @Override
    public Map<Long, Double> mappingToPager(MetricScanner scanner, List<String> constants, Long start, Long end) {
    	throw new UnsupportedOperationException("Integral Transform is not supposed to be used with a constant");
    }

    @Override
    public String name() {
        return TransformFactory.Function.INTEGRAL.name();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

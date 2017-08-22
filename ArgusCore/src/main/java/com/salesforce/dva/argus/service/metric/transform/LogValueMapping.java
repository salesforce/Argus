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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Calculates the logarithm according to the specified base.<br/>
 * <tt>LOG(<expr>, <constant>)</tt>
 *
 * @param   metrics  The list of metrics to evaluate. Cannot be null or empty.
 * @param   base     The base used for the calculation. Base 10 is used if omitted.
 *
 * @author  Ruofan Zhang (rzhang@saleforce.com)
 */
public class LogValueMapping implements ValueMapping {

    //~ Methods **************************************************************************************************************************************

    @Override
    public Map<Long, Double> mapping(Map<Long, Double> originalDatapoints) {
        List<String> constants = new ArrayList<String>();

        constants.add("10");
        return mapping(originalDatapoints, constants);
    }
    
    @Override
    public Map<Long, Double> mappingScanner(MetricScanner scanner) {
    	List<String> constants = new ArrayList<String>();
    	
    	constants.add("10");
    	return mappingScanner(scanner, constants);
    }
    
    @Override
    public Map<Long, Double> mappingToPager(MetricScanner scanner, Long start, Long end) {
    	List<String> constants = new ArrayList<String>();
    	
    	constants.add("10");
    	return mappingToPager(scanner, constants, start, end);
    }

    @Override
    public Map<Long, Double> mapping(Map<Long, Double> originalDatapoints, List<String> constants) {
        if (constants == null || constants.isEmpty()) {
            return mapping(originalDatapoints);
        }
        SystemAssert.requireArgument(constants.size() == 1, "Log Transform requires exactly one constant!");

        Double base = Double.parseDouble(constants.get(0));
        Map<Long, Double> logDatapoints = new HashMap<>();

        for (Entry<Long, Double> entry : originalDatapoints.entrySet()) {
            Double logValue = Math.log(entry.getValue()) / Math.log(base);

            logDatapoints.put(entry.getKey(), logValue);
        }
        return logDatapoints;
    }
    
    @Override
    public Map<Long, Double> mappingScanner(MetricScanner scanner, List<String> constants) {
    	if (constants == null || constants.isEmpty()) {
    		return mappingScanner(scanner);
    	}
        SystemAssert.requireArgument(constants.size() == 1, "Log Transform requires exactly one constant!");
        
        Double base = Double.parseDouble(constants.get(0));
        Map<Long, Double> logDatapoints = new HashMap<>();
        
        while (scanner.hasNextDP()) {
        	Map.Entry<Long, Double> dp = scanner.getNextDP();
        	Double logValue = Math.log(dp.getValue()) / Math.log(base);
        	logDatapoints.put(dp.getKey(), logValue);
        }
        
        return logDatapoints;
    }
    
    @Override
    public Map<Long, Double> mappingToPager(MetricScanner scanner, List<String> constants, Long start, Long end) {
    	if (constants == null || constants.isEmpty()) {
    		return mappingToPager(scanner, start, end);
    	}
    	SystemAssert.requireArgument(constants.size() == 1, "Log Transform requires exactly one constant!");
    	
    	Double base = Double.parseDouble(constants.get(0));
    	Map<Long, Double> logDatapoints = new HashMap<>();
    	
    	Map.Entry<Long, Double> next = scanner.peek();
    	if (next == null || next.getKey() > end) {
    		TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
    		Long startKey = dps.ceilingKey(start);
    		Long endKey = dps.floorKey(end);
    		if (startKey == null || endKey == null || startKey > endKey) {
    			return new TreeMap<>();
    		}
    		logDatapoints = mapping(dps.subMap(startKey, endKey + 1), constants);
    		return logDatapoints;
    	} else if (next.getKey() > start) {
    		TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
    		Long startKey = dps.ceilingKey(start);
    		Long endKey = dps.floorKey(next.getKey());
    		if (startKey != null && endKey != null && startKey < endKey) {
    			logDatapoints.putAll(mapping(dps.subMap(startKey, endKey), constants));
    		}
    	} else {
    		while (scanner.peek() != null && scanner.peek().getKey() < start) {
    			scanner.getNextDP();
    		}
    	}
    	
    	while (scanner.peek() != null && scanner.peek().getKey() <= end) {
    		Map.Entry<Long, Double> dp = scanner.getNextDP();
    		Double logValue = Math.log(dp.getValue()) / Math.log(base);
    		logDatapoints.put(dp.getKey(), logValue);
    	}
    	
    	return logDatapoints;
    }

    @Override
    public String name() {
        return TransformFactory.Function.LOG.name();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

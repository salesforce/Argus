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

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Dilip Devaraj.
 *
 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
 */
public class ZeroIfMissingSum implements Transform {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final String RESULT_METRIC_NAME = "result";

    //~ Methods **************************************************************************************************************************************

    @Override
    public List<Metric> transform(List<Metric> metrics) {
    	SystemAssert.requireArgument(metrics != null, "The metrics list cannot be null or empty while performing arithmetic transformations.");

        List<Metric> resultMetrics = new ArrayList<>();

        if (metrics.isEmpty()) {
            return resultMetrics;
        }

        Metric result = new Metric(getResultScopeName(), RESULT_METRIC_NAME);
        Map<Long, Double> resultDatapoints = new HashMap<>();

        for (Metric metric : metrics) {
            Iterator<Entry<Long, Double>> it = metric.getDatapoints().entrySet().iterator();

            while (it.hasNext()) {
                Entry<Long, Double> entry = it.next();

                if (resultDatapoints.containsKey(entry.getKey())) {
                    resultDatapoints.put(entry.getKey(), performOperation(resultDatapoints.get(entry.getKey()), entry.getValue()));
                } else {
                    resultDatapoints.put(entry.getKey(), entry.getValue());
                }
            }
        }
        result.setDatapoints(resultDatapoints);
        MetricDistiller.setCommonAttributes(metrics, result);
        Collections.addAll(resultMetrics, result);
        return resultMetrics;
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners) {
    		SystemAssert.requireArgument(scanners != null, "The metric scanners list cannot be null or empty while performing arithmetic transformations.");
    		
    		List<Metric> resultMetrics = new ArrayList<>();
    		
    		if (scanners.isEmpty()) {return resultMetrics;}
    		
    		Metric result = new Metric(getResultScopeName(), RESULT_METRIC_NAME);
    		Map<Long, Double> resultDatapoints = new HashMap<>();
    		boolean done = false;
    		
    		while (!done) {
    			done = true;
    			for (MetricScanner scanner : scanners) {
    				if (scanner.hasNextDP()) {
    					done = false;
    					Entry<Long, Double> entry = scanner.getNextDP();
    					
    					if (resultDatapoints.containsKey(entry.getKey())) {
    						resultDatapoints.put(entry.getKey(), performOperation(resultDatapoints.get(entry.getKey()), entry.getValue()));
    					} else {
    						resultDatapoints.put(entry.getKey(), entry.getValue());
    					}
    				}
				}
    		}
    		result.setDatapoints(resultDatapoints);
    		MetricDistiller.setCommonScannerAttributes(scanners, result);
    		Collections.addAll(resultMetrics, result);
    		return resultMetrics;
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, Long start, Long end) {
    	SystemAssert.requireArgument(scanners != null, "The metric scanners list cannot be null or empty while performing arithmetic transformations.");
    	
    	List<Metric> resultMetrics = new ArrayList<>();
    	
    	if (scanners.isEmpty()) {
    		return resultMetrics;
    	}
    	
    	Metric result = new Metric(getResultScopeName(), RESULT_METRIC_NAME);
    	Map<Long, Double> resultDatapoints = new HashMap<>();
    	
    	for (MetricScanner scanner : scanners) {
    		Map.Entry<Long, Double> next = scanner.peek();
    		if (next == null || next.getKey() > end) {
    			TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
    			Long startKey = dps.ceilingKey(start);
    			Long endKey = dps.floorKey(end);
    			if (startKey == null || endKey == null || startKey > endKey) {
    				continue;
    			}
    			for (Map.Entry<Long, Double> en : dps.subMap(startKey, endKey + 1).entrySet()) {
    				resultDatapoints.put(en.getKey(), resultDatapoints.containsKey(en.getKey()) ?
    						resultDatapoints.get(en.getKey()) + en.getValue() : en.getValue());
    			}
    			continue;
    		} else if (next.getKey() > start) {
    			TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
    			Long startKey = dps.ceilingKey(start);
    			Long endKey = dps.floorKey(next.getKey());
    			if (startKey != null && endKey != null && startKey < endKey) {
        			for (Map.Entry<Long, Double> en : dps.subMap(startKey, endKey).entrySet()) {
        				resultDatapoints.put(en.getKey(), resultDatapoints.containsKey(en.getKey()) ?
        						resultDatapoints.get(en.getKey()) + en.getValue() : en.getValue());
        			}
    			}
    		} else {
    			while (scanner.peek() != null && scanner.peek().getKey() < start) {
    				scanner.getNextDP();
    			}
    		}
    		while (scanner.peek() != null && scanner.peek().getKey() <= end) {
    			Map.Entry<Long, Double> dp = scanner.getNextDP();
    			resultDatapoints.put(dp.getKey(), resultDatapoints.containsKey(dp.getKey()) ?
    					resultDatapoints.get(dp.getKey()) + dp.getValue() : dp.getValue());
    		}
    	}
    	result.setDatapoints(resultDatapoints);
    	MetricDistiller.setCommonScannerAttributes(scanners, result);
    	Collections.addAll(resultMetrics, result);
    	return resultMetrics;
    }

    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
        throw new UnsupportedOperationException("Zero if missing Sum Transform is not supposed to be used with a constant");
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners, List<String> constants) {
        throw new UnsupportedOperationException("Zero if missing Sum Transform is not supposed to be used with a constant");
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, List<String> constants, Long start, Long end) {
    	throw new UnsupportedOperationException("Zero if missing Sum Transform is not supposed to be used with a constant");
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.ZEROIFMISSINGSUM.name();
    }

    private Double performOperation(Double operand1, Double operand2) {
        if(operand1 == null) {
        	return operand2;
        }
        
        if(operand2 == null) {
        	return operand1;
        }

        return operand1 + operand2;
    }

    @Override
    public List<Metric> transform(List<Metric>... listOfList) {
        throw new UnsupportedOperationException("Zero if missing Sum Transform is not supposed to be used with a list of metric list!");
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner>... listOfList) {
        throw new UnsupportedOperationException("Zero if missing Sum Transform is not supposed to be used with a list of metric list!");
    }
    
    @Override
    public List<Metric> transformToPagerListOfList(List<List<MetricScanner>> scanners, Long start, Long end) {
    	throw new UnsupportedOperationException("Zero if missing Sum Transform is not supposed to be used with a list of metric list!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

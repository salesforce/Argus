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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import java.util.TreeMap;

/**
 * Raj Sarkapally.
 *
 * @author  Raj Sarkapally (rsarkapally@salesforce.com)
 */
public abstract class AbstractArithmeticTransform implements Transform {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final String RESULT_METRIC_NAME = "result";

    //~ Methods **************************************************************************************************************************************

    @Override
    public List<Metric> transform(List<Metric> metrics) {
    	SystemAssert.requireArgument(metrics != null, "The metrics list cannot be null or empty while performing arithmetic transformations.");
        if (metrics.isEmpty()) {
            return metrics;
        }

        Metric result = new Metric(getResultScopeName(), RESULT_METRIC_NAME);
        Map<Long, Double> resultDatapoints = new HashMap<>();
        Iterator<Entry<Long, Double>> it = metrics.get(0).getDatapoints().entrySet().iterator();

        while (it.hasNext()) {
            Entry<Long, Double> entry = it.next();
            List<Double> operands = null;

            try {
                operands = getOperands(entry.getKey(), metrics);
            } catch (MissingDataException mde) {
                continue;
            }
            resultDatapoints.put(entry.getKey(), performOperation(operands));
        }
        result.setDatapoints(resultDatapoints);
        MetricDistiller.setCommonAttributes(metrics, result);

        List<Metric> resultMetrics = new ArrayList<>();

        Collections.addAll(resultMetrics, result);
        return resultMetrics;
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners) {
    	SystemAssert.requireArgument(scanners != null, "The metric scanners list cannot be null or empty while performing arithmetic transformations.");
    	if (scanners.isEmpty()) {
    		return new ArrayList<>();
    	}
    	
    	Metric result = new Metric(getResultScopeName(), RESULT_METRIC_NAME);
    	Map<Long, Double> resultDatapoints = new HashMap<>();
    	List<MetricScanner> remainingScanners = scanners.subList(1, scanners.size());
    	List<Map<Long, Double>> datapoints = new ArrayList<>(remainingScanners.size());
    	for (int i = 0; i < remainingScanners.size(); i++) {
    		datapoints.add(new HashMap<>());
    	}
    	
    	while (scanners.get(0).hasNextDP()) {
    		Map.Entry<Long, Double> dp = scanners.get(0).getNextDP();
    		List<Double> operands = new ArrayList<>();
    		operands.add(dp.getValue());
    		
    		try {
    			for (MetricScanner scanner : remainingScanners) {
	    			if (scanner.hasNextDP()) {
	    				Map.Entry<Long, Double> nextDP = scanner.getNextDP();
	    				datapoints.get(remainingScanners.indexOf(scanner)).put(nextDP.getKey(), nextDP.getValue());
    				}
    				if (datapoints.get(remainingScanners.indexOf(scanner)).containsKey(dp.getKey())) {
    					operands.add(datapoints.get(remainingScanners.indexOf(scanner)).get(dp.getKey())); // add the value associated with this timestamp
    				}
    				else if (Collections.max(datapoints.get(remainingScanners.indexOf(scanner)).keySet()) > dp.getKey()) { // that metric doesn't have this
    					throw new MissingDataException(MessageFormat.format("Datapoint does not exist for timestamp: {0} for metric: {1}", dp.getKey(),
    							scanner.getMetric()));
    				}
    				else {
    					Map.Entry<Long, Double> opDp = null;
    					do {
		    				if (!scanner.hasNextDP()) {
		    					throw new MissingDataException(MessageFormat.format("Datapoint does not exist for timestamp: {0} for metric: {1}", dp.getKey(),
		    							scanner.getMetric()));
    						}
		    				opDp = scanner.getNextDP();
		    				datapoints.get(remainingScanners.indexOf(scanner)).put(opDp.getKey(), opDp.getValue());
    					} while (opDp.getKey() < dp.getKey());
    					if (datapoints.get(remainingScanners.indexOf(scanner)).containsKey(dp.getKey())) {
    						operands.add(datapoints.get(remainingScanners.indexOf(scanner)).get(dp.getKey()));
    					}
    					else {	// we know it doesn't exist at this point
    						throw new MissingDataException(MessageFormat.format("Datapoint does not exist for timestamp: {0} for metric: {1}", dp.getKey(),
    								scanner.getMetric()));
    					}
    				}
    				
    			}
    		} catch (MissingDataException mde) {
    			continue;
    		}
    		resultDatapoints.put(dp.getKey(), performOperation(operands));
    	}
    	
    	for (MetricScanner scanner : remainingScanners) {
			if (scanner.hasNextDP()) {
				scanner.dispose();
			}
		}
    	
    	result.setDatapoints(resultDatapoints);
    	MetricDistiller.setCommonScannerAttributes(scanners, result); // can still use this here, don't need any datapoints
    	
    	List<Metric> resultMetrics = new ArrayList<>();
    	Collections.addAll(resultMetrics, result);
    	return resultMetrics;
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, Long start, Long end) {
    	SystemAssert.requireArgument(scanners != null, "The metric scanners list cannot be null or empty while performing arithmetic transformations");
    	if (scanners.isEmpty()) {
    		return new ArrayList<>();
    	}
    	
    	Metric result = new Metric(getResultScopeName(), RESULT_METRIC_NAME);
    	Map<Long, Double> resultDatapoints = new HashMap<>();
    	
    	MetricScanner main = scanners.get(0);
    	Map.Entry<Long, Double> next = main.peek();
    	
    	if (next == null || next.getKey() > end) {
    		TreeMap<Long, Double> dps = new TreeMap<>(main.getMetric().getDatapoints());
    		Long startKey = dps.ceilingKey(start);
    		Long endKey = dps.floorKey(end);
    		if (startKey != null && endKey != null && startKey <= endKey) {
		    	Map<Long, Double> range = dps.subMap(startKey, endKey + 1);
		    	for (Long time : range.keySet()) {
		    		List<Double> operands = getOperandsPager(time, scanners);
		    		if (operands == null) {
		    			continue;
		    		}
		    		resultDatapoints.put(time, performOperation(operands));
    			}
    		}
    	} else if (next.getKey() > start) {
    		TreeMap<Long, Double> dps = new TreeMap<>(main.getMetric().getDatapoints());
    		Long startKey = dps.ceilingKey(start);
    		Long endKey = dps.floorKey(next.getKey());
    		if (startKey != null && endKey != null && startKey < endKey) {
    			Map<Long, Double> range = dps.subMap(startKey, endKey);
    			for (Long time : range.keySet()) {
    				List<Double> operands = getOperandsPager(time, scanners);
    				if (operands == null) {
    					continue;
    				}
    				resultDatapoints.put(time, performOperation(operands));
    			}
    		}
    	} else {
    		while (main.peek() != null && main.peek().getKey() < start) {
    			main.getNextDP();
    		}
    	}
    	
    	while (main.peek() != null && main.peek().getKey() <= end) {
    		Map.Entry<Long, Double> dp = main.getNextDP();
    		List<Double> operands = getOperandsPager(dp.getKey(), scanners);
    		if (operands == null) {
    			continue;
    		}
    		resultDatapoints.put(dp.getKey(), performOperation(operands));
    	}

    	result.setDatapoints(resultDatapoints);
    	MetricDistiller.setCommonScannerAttributes(scanners, result);
    	
    	List<Metric> resultMetrics = new ArrayList<>();
    	Collections.addAll(resultMetrics, result);
    	return resultMetrics;
    }

    private List<Double> getOperands(Long timestamp, List<Metric> metrics) {
        List<Double> operands = new ArrayList<>();

        for (Metric metric : metrics) {
            Double operand = metric.getDatapoints().get(timestamp);

            if (operand == null) {
                throw new MissingDataException(MessageFormat.format("Datapoint does not exist for timestamp: {0} for metric: {1}", timestamp,
                        metric));
            }
            operands.add(operand);
        }
        return operands;
    }
    

    private List<Double> getOperandsPager(Long time, List<MetricScanner> remainingScanners) {
    	List<Double> operands = null;
		try {
			operands = new ArrayList<>();
			for (MetricScanner scanner : remainingScanners) {
				if (scanner.getMetric().getDatapoints().containsKey(time)) {
					operands.add(scanner.getMetric().getDatapoints().get(time));
				} else if (scanner.peek() != null && scanner.peek().getKey() > time) {
					throw new MissingDataException(MessageFormat.format("Data point does not exist for timestamp: {0} for metric {1}", time, scanner.getMetric()));
				} else {
					while (scanner.peek() != null && scanner.peek().getKey() < time) {
						scanner.getNextDP();
					}
					Map.Entry<Long, Double> dp = scanner.getNextDP();
					if (dp.getKey().equals(time)) {
						operands.add(dp.getValue());
					} else {
						throw new MissingDataException(MessageFormat.format("Data point does not exist for timestamp: {0} for metric {1}", time, scanner.getMetric()));
					}
				}
			}
		} catch (MissingDataException mde) {
			return null;
		}
		return operands;
    }

    /**
     * Performs the arithmetic operation defined in sub class.
     *
     * @param   operands  param1 First parameter
     *
     * @return  The result of arithmetic operation between first parameter and second parameter.
     */
    protected abstract Double performOperation(List<Double> operands);
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

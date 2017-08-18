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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Prepare the datapoints of every metric for count transform.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class CountTransformWrapUnion implements Transform {

    //~ Static fields/initializers *******************************************************************************************************************

    /** The default metric name. */
    public static final String DEFAULT_METRIC_NAME = "result";

    //~ Methods **************************************************************************************************************************************

    @Override
    public List<Metric> transform(List<Metric> metrics) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform null metrics.");
        
        if (metrics.isEmpty()) {
            return metrics;
        }
        
        MetricDistiller distiller = new MetricDistiller();
        distiller.distill(metrics);

        String newMetricName = distiller.getMetric() == null ? TransformFactory.DEFAULT_METRIC_NAME : distiller.getMetric();
        Metric newMetric = new Metric(getResultScopeName(), newMetricName);

        newMetric.setDisplayName(distiller.getDisplayName());
        newMetric.setUnits(distiller.getUnits());
        newMetric.setTags(distiller.getTags());
        newMetric.setDatapoints(_collate(metrics));

        return Arrays.asList(newMetric);
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners) {
		SystemAssert.requireArgument(scanners != null, "Cannot transform null metric scanners.");
		
		if (scanners.isEmpty()) {
			return new ArrayList<Metric>(0);
		}
		
		MetricDistiller distiller = new MetricDistiller();
		distiller.distillScanner(scanners);
		
		String newMetricName = distiller.getMetric() == null ? TransformFactory.DEFAULT_METRIC_NAME : distiller.getMetric();
		Metric newMetric = new Metric(getResultScopeName(), newMetricName);
		
		newMetric.setDisplayName(distiller.getDisplayName());
		newMetric.setUnits(distiller.getUnits());
		newMetric.setTags(distiller.getTags());
		newMetric.setDatapoints(_collateScanners(scanners));
		
		return Arrays.asList(newMetric);
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, Long start, Long end) {
    	SystemAssert.requireArgument(scanners != null, "Cannot transform null metric scanners.");
    	
    	if (scanners.isEmpty()) {
    		return new ArrayList<Metric>(0);
    	}
    	
    	MetricDistiller distiller = new MetricDistiller();
    	distiller.distillScanner(scanners);
    	
    	String newMetricName = distiller.getMetric() == null ? TransformFactory.DEFAULT_METRIC_NAME : distiller.getMetric();
    	Metric newMetric = new Metric(getResultScopeName(), newMetricName);
    	
    	newMetric.setDisplayName(distiller.getDisplayName());
    	newMetric.setUnits(distiller.getUnits());
    	newMetric.setTags(distiller.getTags());
    	newMetric.setDatapoints(_collatePager(scanners, start, end));

    	return Arrays.asList(newMetric);
    }
    
    private Map<Long, Double> _collate(List<Metric> metrics) {
        Map<Long, Double> collated = new HashMap<>();

        for (Metric metric : metrics) {
            for (Map.Entry<Long, Double> point : metric.getDatapoints().entrySet()) {
                if (!collated.containsKey(point.getKey())) {
                    collated.put(point.getKey(), 1.0);
                } else {
                	double oldValue = collated.get(point.getKey());
                	collated.put(point.getKey(), oldValue + 1.0);
                }
            }
        }
        return collated;
    }
    
    private Map<Long, Double> _collateScanners(List<MetricScanner> scanners) {
		Map<Long, Double> collated = new HashMap<>();
		
		for (MetricScanner scanner : scanners) {
			while (scanner.hasNextDP()) {
				Map.Entry<Long, Double> dp = scanner.getNextDP();
				if (!collated.containsKey(dp.getKey())) {
					collated.put(dp.getKey(), 1.0);
				} else {
					collated.put(dp.getKey(), collated.get(dp.getKey()) + 1.0);
				}
			}
		}
		return collated;
    }
    
    private Map<Long, Double> _collatePager(List<MetricScanner> scanners, Long start, Long end) {
    	Map<Long, Double> collated = new HashMap<>();
    	for (MetricScanner scanner : scanners) {
    		Map.Entry<Long, Double> next = scanner.peek();
    		if (next == null || next.getKey() > end) {
    			TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
    			Long startKey = dps.ceilingKey(start);
    			Long endKey = dps.floorKey(end);
    			if (startKey != null && endKey != null && startKey <= endKey) {
    				for (Map.Entry<Long, Double> entry : dps.subMap(startKey, endKey + 1).entrySet()) {
    					collated.put(entry.getKey(), collated.containsKey(entry.getKey()) ? 
    							collated.get(entry.getKey()) + 1.0 : 1.0);
    				}
    			}
    		} else if (next.getKey() > start) {
    			TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
    			Long startKey = dps.ceilingKey(start);
    			Long endKey = dps.floorKey(next.getKey());
    			if (startKey != null && endKey != null && startKey < endKey) {
    				for (Map.Entry<Long, Double> entry : dps.subMap(startKey, endKey).entrySet()) {
    					collated.put(entry.getKey(), collated.containsKey(entry.getKey()) ? 
    							collated.get(entry.getKey()) + 1.0 : 1.0);
    				}
    			}
    		} else {
    			while (scanner.peek() != null && scanner.peek().getKey() < start) {
    				scanner.getNextDP();
    			}
    		}
    		
    		while (scanner.peek() != null && scanner.peek().getKey() <= end) {
    			Map.Entry<Long, Double> dp = scanner.getNextDP();
    			collated.put(dp.getKey(), collated.containsKey(dp.getKey()) ? 
    					collated.get(dp.getKey()) + 1.0 : 1.0);
    		}
    	}
    	return collated;
    }

    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
        throw new UnsupportedOperationException("COUNT Transform is not supposed to be used with a constant");
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners, List<String> constants) {
    	throw new UnsupportedOperationException("COUNT Transform is not supposed to be used with a constant");
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, List<String> constants, Long start, Long end) {
    	throw new UnsupportedOperationException("COUNT Transform is not supposed to be used with a constant");
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.COUNT.name();
    }

    @Override
    public List<Metric> transform(List<Metric>... listOfList) {
        throw new UnsupportedOperationException("Count doesn't need list of list!");
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner>... listOfList) {
    	throw new UnsupportedOperationException("Count doesn't need list of list!");
    }
    
    @Override
    public List<Metric> transformToPagerListOfList(List<List<MetricScanner>> scanners, Long start, Long end) {
    	throw new UnsupportedOperationException("Count doesn't need list of list!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

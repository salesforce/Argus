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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Joins multiple lists of metrics into a single list.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class JoinTransform implements Transform {

    //~ Methods **************************************************************************************************************************************

    @Override
    public List<Metric> transform(List<Metric>... listOfList) {
        List<Metric> result = new ArrayList<>();

        for (List<Metric> list : listOfList) {
            for (Metric metric : list) {
                result.add(metric);
            }
        }
        return result;
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner>... listOfList) {
    	List<Metric> result = new ArrayList<>();
    	
    	for (List<MetricScanner> list : listOfList) {
    		for (MetricScanner scanner : list) {
    			Metric m = new Metric(scanner.getMetric());
    			Map<Long, Double> dps = new HashMap<>();
    			while (scanner.hasNextDP()) {
    				Map.Entry<Long, Double> dp = scanner.getNextDP();
    				dps.put(dp.getKey(), dp.getValue());
    			}
    			m.setDatapoints(dps);
    			result.add(m);
    		}
    	}
    	return result;
    }
    
    @Override
    public List<Metric> transformToPagerListOfList(List<List<MetricScanner>> scanners, Long start, Long end) {
    	List<Metric> result = new ArrayList<>();
    	
    	for (List<MetricScanner> list : scanners) {
    		for (MetricScanner scanner : list) {
    			Metric m = buildMetricRange(scanner, start, end);
    			result.add(m);
    		}
    	}
    	return result;
    }
    
    private Metric buildMetricRange(MetricScanner scanner, Long start, Long end) {
    	Metric m = scanner.getMetric();
    	TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
    	if (dps.lastKey() < end) {
	    	while (scanner.peek() != null && scanner.peek().getKey() <= end) {
	    		Map.Entry<Long, Double> dp = scanner.getNextDP();
	    		dps.put(dp.getKey(), dp.getValue());
	    	}
    	}
    	Long startKey = dps.ceilingKey(start);
    	Long endKey = dps.floorKey(end);
    	if (startKey == null || endKey == null || startKey > endKey) {
    		m.setDatapoints(new TreeMap<>());
    		return m;
    	}
    	m.setDatapoints(dps.subMap(startKey, endKey + 1));
    	return m;
    }

    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
        throw new UnsupportedOperationException("Join transform doesn't need constant!");
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners, List<String> constants) {
        throw new UnsupportedOperationException("Join transform doesn't need constant!");
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, List<String> constants, Long start, Long end) {
    	throw new UnsupportedOperationException("Join transform doesn't need constant!");
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.JOIN.name();
    }

    @Override
    public List<Metric> transform(List<Metric> metrics) {
        return metrics;
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners) {
    	List<Metric> result = new ArrayList<>();
    	for (MetricScanner scanner : scanners) {    	
    		while (scanner.hasNextDP()) {
    			scanner.getNextDP();
    		}
       		result.add(scanner.getMetric());
    	}
    	return result;
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, Long start, Long end) {
    	List<Metric> result = new ArrayList<>();
    	for (MetricScanner scanner : scanners) {
    		Metric m = buildMetricRange(scanner, start, end);
    		result.add(m);
    	}
    	return result;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

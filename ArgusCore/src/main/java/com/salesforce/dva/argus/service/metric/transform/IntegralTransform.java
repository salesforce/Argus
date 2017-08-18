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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Implementation of Integral transform.
 *
 * @author  Raj Sarkapally (rsarkapally@salesforce.com)
 */
public class IntegralTransform implements Transform {

    //~ Methods **************************************************************************************************************************************

    @Override
    public List<Metric> transform(List<Metric> metrics) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform null metric/metrics");

        List<Metric> result = new ArrayList<>(metrics.size());

        for (Metric metric : metrics) {
            Map<Long, Double> sortedDatapoints = new TreeMap<>();
            Double prevSum = 0.0;

            sortedDatapoints.putAll(metric.getDatapoints());
            for (Entry<Long, Double> entry : sortedDatapoints.entrySet()) {
                prevSum += entry.getValue();
                sortedDatapoints.put(entry.getKey(), prevSum);
            }
            metric.setDatapoints(sortedDatapoints);
            result.add(metric);
        }
        return result;
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners) {
		SystemAssert.requireArgument(scanners != null, "Cannot transform null metric scanner/scanners");
		
		List<Metric> result = new ArrayList<>(scanners.size());
		
		for (MetricScanner scanner : scanners) {
			Double prevSum = 0.0;
			Map<Long, Double> integralDP = new TreeMap<>();
			
			while (scanner.hasNextDP()) {
				Map.Entry<Long, Double> dp = scanner.getNextDP();
				prevSum += dp.getValue();
				integralDP.put(dp.getKey(), prevSum);
			}
			
			Metric m = new Metric(scanner.getMetric());
			m.setDatapoints(integralDP);
			result.add(m);
		}
		return result;
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, Long start, Long end) {
    	SystemAssert.requireArgument(scanners != null, "Cannot transform null metric scanner/scanners");
    	    	
    	List<Metric> result = new ArrayList<>();
    	
    	for (MetricScanner scanner : scanners) {
    		Double prevSum = 0.0;
    		Metric m = scanner.getMetric();
    		Metric mInteg = new Metric(m);
    		mInteg.setDatapoints(new HashMap<>());
    		Map.Entry<Long, Double> next = scanner.peek();
    		
    		if (next == null || next.getKey() > end) {
    			TreeMap<Long, Double>  dps = new TreeMap<>(m.getDatapoints());
    			Long startKey = dps.firstKey();
    			Long endKey = dps.floorKey(end);
    			Metric holder = new Metric(m);
    			if (startKey == null || endKey == null || startKey > endKey) {
    				holder.setDatapoints(new HashMap<>());
    			} else {
    				holder.setDatapoints(dps.subMap(startKey, endKey + 1));
    			}
    			mInteg.setDatapoints(transform(Arrays.asList(holder)).get(0).getDatapoints());    
    		} else if (next.getKey() > start) {
    			TreeMap<Long, Double> dps = new TreeMap<>(m.getDatapoints());
    			Long startKey = dps.firstKey();
    			Long endKey = dps.floorKey(next.getKey());
    			if (startKey != null && endKey != null && startKey < endKey) {
    				Metric holder = new Metric(m);
    				holder.setDatapoints(dps.subMap(startKey, endKey));
    				List<Metric> h = new ArrayList<>();
    				h.add(holder);
    				mInteg.setDatapoints(transform(h).get(0).getDatapoints());
    				prevSum = mInteg.getDatapoints().isEmpty() ? 0.0 : mInteg.getDatapoints().get(Collections.max(mInteg.getDatapoints().keySet()));
    			}
    		} else {
    			while (scanner.peek() != null && scanner.peek().getKey() < start) {
    				prevSum += scanner.getNextDP().getValue();
    			}
    		}
    		
    		Map<Long, Double> integralDP = new HashMap<>();
    		
    		while (scanner.peek() != null && scanner.peek().getKey() <= end) {
    			Map.Entry<Long, Double> dp = scanner.getNextDP();
    			prevSum += dp.getValue();
    			integralDP.put(dp.getKey(), prevSum);
    		}
    		mInteg.addDatapoints(integralDP);
			TreeMap<Long, Double> finalize = new TreeMap<>(mInteg.getDatapoints());
			Long sKey = finalize.ceilingKey(start);
			Long eKey = finalize.floorKey(end);
			if (sKey != null && eKey != null && sKey <= eKey) {
				mInteg.setDatapoints(finalize.subMap(sKey, eKey + 1));
			} else {
				mInteg.setDatapoints(new TreeMap<>());
			}
			result.add(mInteg);
    	}
    	return result;
    }

    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
        throw new UnsupportedOperationException("Integral Transform is not supposed to be used with a constant");
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners, List<String> constants) {
        throw new UnsupportedOperationException("Integral Transform is not supposed to be used with a constant");
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, List<String> constants, Long start, Long end) {
    	throw new UnsupportedOperationException("Integral Transform is not supposed to be used with a constant");
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.INTEGRAL.name();
    }

    @Override
    public List<Metric> transform(List<Metric>... listOfList) {
        throw new UnsupportedOperationException("This class is deprecated!");
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner>... listOfList) {
        throw new UnsupportedOperationException("This class is deprecated!");
    }
    
    @Override
    public List<Metric> transformToPagerListOfList(List<List<MetricScanner>> scanners, Long start, Long end) {
    	throw new UnsupportedOperationException("This class is deprecated!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

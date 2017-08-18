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
import com.salesforce.dva.argus.system.SystemException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Culls metrics that are below the average value.
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
public class AverageBelowTransform implements Transform {

    //~ Methods **************************************************************************************************************************************

    @Override
    public List<Metric> transform(List<Metric> metrics) {
        throw new UnsupportedOperationException("This Transform cannot be used without a constant");
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners) {
    	throw new UnsupportedOperationException("This Transform cannot be used without a constant");
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, Long start, Long end) {
    	throw new UnsupportedOperationException("This Transform cannot be used without a constant");
    }

    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform null or empty metrics");
        if (metrics.isEmpty()) {
            return metrics;
        }
        SystemAssert.requireArgument(constants != null && constants.size() == 1, "Average Below Transform must provide exactly 1 constant.");

        Double value = 0.0;

        try {
            value = Double.parseDouble(constants.get(0));
        } catch (NumberFormatException nfe) {
            throw new SystemException("Illegal constant value supplied to average below transform", nfe);
        }

        List<Metric> result = new ArrayList<Metric>(metrics.size());

        for (Metric metric : metrics) {
            if (calculateAverage(metric.getDatapoints()) < value) {
                result.add(metric);
            }
        }
        return result;
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners, List<String> constants) {
    	SystemAssert.requireArgument(scanners != null, "Cannot transform null or empty metric scanners");
    	List<Metric> result = new ArrayList<Metric>();
    	if (scanners.isEmpty()) {
    		return result;
    	}
    	
    	SystemAssert.requireArgument(constants != null && constants.size() == 1, "Average Below Transform must provide exactly 1 constant.");
    	
    	Double value = 0.0;
    	
    	try {
    		value = Double.parseDouble(constants.get(0));
    	} catch (NumberFormatException nfe) {
    		throw new SystemException("Illegal constant value supplied to average below transform" , nfe);
    	}
    	    	
    	for (MetricScanner scanner : scanners) {
    		if (calculateScannerAverage(scanner) < value) {
    			result.add(scanner.getMetric());
    		}
    	}
    	return result;
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, List<String> constants, Long start, Long end) {
    	SystemAssert.requireArgument(scanners != null, "Cannot transform null or empty metric scanners");
    	List<Metric> result = new ArrayList<Metric>();
    	if (scanners.isEmpty()) {
    		return result;
    	} 	
    	SystemAssert.requireArgument(constants != null && constants.size() == 1, "Average Below Transform must provide exactly 1 constant.");
    	
    	Double value = 0.0;

        try {
            value = Double.parseDouble(constants.get(0));
        } catch (NumberFormatException nfe) {
            throw new SystemException("Illegal constant value supplied to average below transform", nfe);
        }
        
        List<Metric> full = new ArrayList<>();
    	for (MetricScanner scanner : scanners) {
    		Map.Entry<Long, Double> next = scanner.peek();
    		if (next == null) {
    			/* already fully explored */
    			if (calculateAverage(scanner.getMetric().getDatapoints()) < value) {
    				full.add(scanner.getMetric());
    			}
    		} else if (next.getKey().equals(Collections.min(scanner.getMetric().getDatapoints().keySet()))) {
    			/* not explored at all */
    			if (calculateScannerAverage(scanner) < value) {
    				full.add(scanner.getMetric());
    			}
    		} else {
    			/* do partial of each */
    			TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
    			Long startKey = dps.ceilingKey(start);
    			Long endKey = dps.floorKey(next.getKey());
    			Double mAvg = 0.0;
    			int mSize = 1;
    			if (startKey != null && endKey != null && startKey < endKey) {
    				mSize = dps.subMap(startKey, endKey).size();
    				mAvg = calculateAverage(dps.subMap(startKey, endKey));
    			}
    			Double sAvg = calculateScannerAverage(scanner);
    			int sSize = scanner.getMetric().getDatapoints().size() - mSize;
    			
    			if ((mAvg * mSize + sAvg * sSize) / (mSize + sSize) < value) {
    				full.add(scanner.getMetric());
    			}
    		}
    	}
    	
    	for (Metric m : full) {
    		TreeMap<Long, Double> dps = new TreeMap<>(m.getDatapoints());
    		Long startKey = dps.ceilingKey(start);
    		Long endKey = dps.floorKey(end);
    		Metric mCopy = new Metric(m);
    		if (startKey != null && endKey != null && startKey <= endKey) {
    			mCopy.setDatapoints(dps.subMap(startKey, endKey + 1));
    		} else {
    			mCopy.setDatapoints(new TreeMap<>());
    		}
    		result.add(mCopy);
    	}
    	return result;
    }

    private Double calculateAverage(Map<Long, Double> datapoints) {
        Double sum = 0.0;

        for (Double value : datapoints.values()) {
        	sum += value;
        }
        return sum / datapoints.size();
    }
    
    private Double calculateScannerAverage(MetricScanner scanner) {
    	Double sum = 0.0;
    	int dpNumber = 0;
    	
    	while (scanner.hasNextDP()) {
    		dpNumber++;
    		sum += scanner.getNextDP().getValue();
    	}
    	return sum / dpNumber;
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.AVERAGEBELOW.name();
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

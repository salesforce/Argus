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
import com.salesforce.dva.argus.service.metric.MetricReader;
import com.salesforce.dva.argus.service.tsdb.MetricScanner;
import com.salesforce.dva.argus.system.SystemAssert;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * This class transforms a list of metrics in a mapping way, which means apply the same function to every metric. More specifically, an interface
 * valueMapping will be passed in , which implements how to apply a mapping function to datapoints of every metric.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class MetricMappingTransform implements Transform {

    //~ Instance fields ******************************************************************************************************************************

    private final ValueMapping valueMapping;
    private final String defaultScope;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new ReduceTransform object.
     *
     * @param  valueMapping  The valueMapping.
     */
    protected MetricMappingTransform(ValueMapping valueMapping) {
        this.valueMapping = valueMapping;
        this.defaultScope = valueMapping.name();
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public String getResultScopeName() {
        return defaultScope;
    }

    @Override
    public List<Metric> transform(List<Metric> metrics) {
        return mapping(metrics);
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners) {
    	return mappingScanner(scanners);
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, Long start, Long end) {
    	return mappingToPager(scanners, start, end);
    }

    /**
     * Mapping a list of metric, only massage its datapoints.
     *
     * @param   metrics  The list of metrics to be mapped.
     *
     * @return  A list of metrics after mapping.
     */
    private List<Metric> mapping(List<Metric> metrics) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform empty metric/metrics");
        if (metrics.isEmpty()) {
            return metrics;
        }

        List<Metric> newMetricsList = new ArrayList<Metric>();

        for (Metric metric : metrics) {
            Map<Long, Double> cleanDatapoints = cleanDPs(metric.getDatapoints());

            metric.setDatapoints(this.valueMapping.mapping(cleanDatapoints));
            newMetricsList.add(metric);
        }
        return newMetricsList;
    }
    
    private List<Metric> mappingScanner(List<MetricScanner> scanners) {
    	SystemAssert.requireArgument(scanners != null, "Cannot transform empty metric scanner/scanners");
    	List<Metric> newMetricsList = new ArrayList<Metric>();
    	
    	for (MetricScanner scanner : scanners) {
    		Map<Long, Double> cleanDatapoints = cleanDPScanner(scanner);
    		
    		Metric m = new Metric(scanner.getMetric());
    		m.setDatapoints(this.valueMapping.mapping(cleanDatapoints));
    		newMetricsList.add(m);
    	}
    	
    	return newMetricsList;
    }
    
    private List<Metric> mappingToPager(List<MetricScanner> scanners, Long start, Long end) {
    	SystemAssert.requireArgument(scanners != null, "Cannot transform empty metric scanner/scanners");
    	List<Metric> newMetricsList = new ArrayList<Metric>();
    	
    	for (MetricScanner scanner : scanners) {
    		Map<Long, Double> cleanDatapoints = cleanDPPagerFull(scanner);
    		
    		if (!cleanDatapoints.isEmpty()) {
	    		Metric m = new Metric(scanner.getMetric());
	    		TreeMap<Long, Double> res = new TreeMap<>(this.valueMapping.mapping(cleanDatapoints));

	    		Long startKey = res.ceilingKey(start);
	    		Long endKey = res.floorKey(end);
	    		if (startKey != null && endKey != null && startKey <= endKey) {
	    			m.setDatapoints(res.subMap(startKey, endKey + 1));
	    		} else {
	    			m.setDatapoints(new TreeMap<>());
	    		}
	    		newMetricsList.add(m);
    		}
    	}
    	return newMetricsList;
    }

    private long getOffsetInSeconds(String offset) {
        MetricReader.TimeUnit timeunit = null;
        Long backwards = 1L;

        try {
            if (offset.startsWith("-")) {
                backwards = -1L;
                offset = offset.substring(1);
            }
            if (offset.startsWith("+")) {
                offset = offset.substring(1);
            }
            timeunit = MetricReader.TimeUnit.fromString(offset.substring(offset.length() - 1));

            long timeDigits = Long.parseLong(offset.substring(0, offset.length() - 1));

            return backwards * timeDigits * timeunit.getValue() / 1000;
        } catch (Exception t) {
            throw new IllegalArgumentException("Fail to parse offset!");
        }
    }
    
    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
        return mapping(metrics, constants);
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners, List<String> constants) {
    	return mappingScanner(scanners, constants);
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, List<String> constants, Long start, Long end) {
    	return mappingToPager(scanners, constants, start, end);
    }

    private List<Metric> mapping(List<Metric> metrics, List<String> constants) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform empty metric/metrics");
        if (metrics.isEmpty()) {
            return metrics;
        }

        List<Metric> newMetricsList = new ArrayList<Metric>();

        for (Metric metric : metrics) {
            Map<Long, Double> cleanDatapoints = cleanDPs(metric.getDatapoints());

            metric.setDatapoints(this.valueMapping.mapping(cleanDatapoints, constants));
            newMetricsList.add(metric);
        }
        return newMetricsList;
    }
    
    private List<Metric> mappingScanner(List<MetricScanner> scanners, List<String> constants) {
    	SystemAssert.requireArgument(scanners != null, "Cannot transform empty metric scanner/scanners");
    	
    	List<Metric> newMetricsList = new ArrayList<Metric>();
    	
    	if(scanners.isEmpty()) {
    		return newMetricsList;
    	}
    	
    	for (MetricScanner scanner : scanners) {
    		Map<Long, Double> cleanDatapoints = cleanDPScanner(scanner);
    		Metric m = new Metric(scanner.getMetric());
    		m.setDatapoints(this.valueMapping.mapping(cleanDatapoints, constants));
    		newMetricsList.add(m);
    	}
    	return newMetricsList;
    }
    
    private List<Metric> mappingToPager(List<MetricScanner> scanners, List<String> constants, Long start, Long end) {
    	SystemAssert.requireArgument(scanners != null, "Cannot transform empty metric scanner/scanners");
    	
    	List<Metric> newMetricsList = new ArrayList<Metric>();
    	if (scanners.isEmpty()) {
    		return newMetricsList;
    	}
    	
    	for (MetricScanner scanner : scanners) {
    		/* use full version here in case the value mapping needs all of the data */
    		Map<Long, Double> cleanDatapoints = cleanDPPagerFull(scanner);
    		Metric m = new Metric(scanner.getMetric());
    		TreeMap<Long, Double> res = new TreeMap<>(this.valueMapping.mapping(cleanDatapoints, constants));
    		
    		Long shift = 0L;
    		if (ShiftValueMapping.class.isInstance(this.valueMapping)) {
    			shift = getOffsetInSeconds(constants.get(0)) * 1000;
    		}
    		Long startKey = res.ceilingKey(start + shift);
    		Long endKey = res.floorKey(end + shift);
    		if (startKey != null && endKey != null && startKey <= endKey) {
    			m.setDatapoints(res.subMap(startKey, endKey + 1));
    		} else {
    			m.setDatapoints(new TreeMap<>());
    		}
    		newMetricsList.add(m);
    	}
    	return newMetricsList;
    }

    private Map<Long, Double> cleanDPs(Map<Long, Double> originalDPs) {
        Map<Long, Double> cleanDPs = new TreeMap<>();

        for (Map.Entry<Long, Double> entry : originalDPs.entrySet()) {
            if (entry.getValue() == null) {
                cleanDPs.put(entry.getKey(), 0.0);
            } else {
                cleanDPs.put(entry.getKey(), entry.getValue());
            }
        }
        return cleanDPs;
    }
    
    private Map<Long, Double> cleanDPScanner(MetricScanner scanner) {
    	Map<Long, Double> cleanDPs = new HashMap<>();
    	
		while (scanner.hasNextDP()) {
    		Map.Entry<Long, Double> dp = scanner.getNextDP();
    		if (dp.getValue() == null) {
    			cleanDPs.put(dp.getKey(), 0.0);
    		} else {
    			cleanDPs.put(dp.getKey(), dp.getValue());
    		}
    	}
    	return cleanDPs;
    }

    private Map<Long, Double> cleanDPPagerFull(MetricScanner scanner) {
    	Map<Long, Double> cleanDPs = new TreeMap<>();
    	Map.Entry<Long, Double> next = scanner.peek();
    	
    	if (next == null) {
    		return cleanDPs(scanner.getMetric().getDatapoints());
    	} else if (!next.getKey().equals(Collections.min(scanner.getMetric().getDatapoints().keySet()))) {
    		TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
    		Long startKey = dps.firstKey();
    		Long endKey = dps.floorKey(next.getKey());
    		if (startKey != null && endKey != null && startKey < endKey) {
    			cleanDPs = cleanDPs(dps.subMap(startKey, endKey));
    		}
    	} 
   
    	while (scanner.hasNextDP()) {
    		Map.Entry<Long, Double> dp = scanner.getNextDP();
    		cleanDPs.put(dp.getKey(), dp.getValue() == null ? 0.0 : dp.getValue());
    	}
    	
    	return cleanDPs;
    }

    @Override
    public List<Metric> transform(List<Metric>... listOfList) {
        throw new UnsupportedOperationException("Mapping doesn't need list of list!");
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner>... listOfList) {
        throw new UnsupportedOperationException("Mapping doesn't need list of list!");
    }
    
    @Override
    public List<Metric> transformToPagerListOfList(List<List<MetricScanner>> scanners, Long start, Long end) {
    	throw new UnsupportedOperationException("Mapping doesn't need list of list!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

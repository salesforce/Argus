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
import java.util.TreeMap;

/**
 * For some function, it either does a mapping transform or reduce transform which depends on the constant input This class provides a general
 * transform for either mapping if no constant input, or reduing with a constant input.
 *
 * <p>So far, Such functions include: List<Metric> DIFF(List <Metric> metrics, Double constant); List<Metric> DIVIDE(List<Metric> metrics, Double
 * constant); List<Metric> SCALE(List<Metric> metrics, Double constant); List<Metric> SUM(List<Metric> metrics, Double constant);</p>
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class MetricReducerOrMappingTransform implements Transform {

    //~ Instance fields ******************************************************************************************************************************

    protected final ValueReducerOrMapping valueReducerOrMapping;
    protected final String defaultScope;
    protected final String defaultMetricName;
    protected static final String FULLJOIN = "UNION";
    protected Boolean fulljoinIndicator=false;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new ReduceTransform object.
     *
     * @param  valueReducerOrMapping  The valueMapping.
     */
    protected MetricReducerOrMappingTransform(ValueReducerOrMapping valueReducerOrMapping) {
        this.valueReducerOrMapping = valueReducerOrMapping;
        this.defaultScope = valueReducerOrMapping.name();
        this.defaultMetricName = TransformFactory.DEFAULT_METRIC_NAME;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public String getResultScopeName() {
        return defaultScope;
    }

    @Override
    public List<Metric> transform(List<Metric> metrics) {
        return Arrays.asList(reduce(metrics, null));
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners) {
    	return Arrays.asList(reduceScanner(scanners, null));
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, Long start, Long end) {
    	Metric m = reduceToPager(scanners, null, start, end);
    	if (m.getDatapoints().isEmpty()) {
    		m.setDatapoints(new TreeMap<>());
    		return Arrays.asList(m);
    	}
    	TreeMap<Long, Double> dps = new TreeMap<>(m.getDatapoints());
    	Long startKey = dps.ceilingKey(start);
    	Long endKey = dps.floorKey(end);
    	if (startKey == null || endKey == null || startKey > endKey) {
    		m.setDatapoints(new TreeMap<>());
    	} else {
    		m.setDatapoints(dps.subMap(startKey, endKey + 1));
    	}
    	return Arrays.asList(m);
    }

    /**
     * If constants is not null, apply mapping transform to metrics list. Otherwise, apply reduce transform to metrics list
     *
     * @param   metrics    list of metrics
     * @param   constants  constants input
     *
     * @return  A list of metrics after mapping.
     */
    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
        if (constants == null || constants.isEmpty()) {
            return transform(metrics);
        }
        
        if (constants.size() == 1 && constants.get(0).toUpperCase().equals(FULLJOIN)){
        	fulljoinIndicator=true;
        	return transform(metrics);
        }
        
        return mapping(metrics, constants);
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners, List<String> constants) {
    	if (constants == null || constants.isEmpty()) {
    		return transformScanner(scanners);
    	}
    	
    	if (constants.size() == 1 && constants.get(0).toUpperCase().equals(FULLJOIN)) {
    		fulljoinIndicator = true;
    		return transformScanner(scanners);
    	}
    	
    	return mappingScanner(scanners, constants);
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, List<String> constants, Long start, Long end) {
    	if (constants == null || constants.isEmpty()) {
    		return transformToPager(scanners, start, end);
    	}
    	if (constants.size() == 1 && constants.get(0).toUpperCase().equals(FULLJOIN)) {
    		fulljoinIndicator = true;
    		return transformToPager(scanners, start, end);
    	}
    	
    	return mappingToPager(scanners, constants, start, end);
    }

    /**
     * Mapping a list of metric, only massage its datapoints.
     *
     * @param   metrics    The list of metrics to be mapped. constants The list of constants used for mapping
     * @param   constants  constants input
     *
     * @return  A list of metrics after mapping.
     */
    protected List<Metric> mapping(List<Metric> metrics, List<String> constants) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform null metrics");
        
        if (metrics.isEmpty()) {
            return metrics;
        }

        List<Metric> newMetricsList = new ArrayList<Metric>();

        for (Metric metric : metrics) {
            metric.setDatapoints(this.valueReducerOrMapping.mapping(metric.getDatapoints(), constants));
            newMetricsList.add(metric);
        }
        return newMetricsList;
    }
    
    protected List<Metric> mappingScanner(List<MetricScanner> scanners, List<String> constants) {
    	SystemAssert.requireArgument(scanners != null, "Cannot transform null metric scanners");
    	
    	if (scanners.isEmpty()) {
    		return new ArrayList<>();
    	}
    	
    	List<Metric> newMetricsList = new ArrayList<Metric>();
    	
    	for (MetricScanner scanner : scanners) {
    		Metric m = new Metric(scanner.getMetric());
    		m.setDatapoints(this.valueReducerOrMapping.mappingScanner(scanner, constants));
    		newMetricsList.add(m);
    	}
    	return newMetricsList;
    }
    
    protected List<Metric> mappingToPager(List<MetricScanner> scanners, List<String> constants, Long start, Long end) {
    	SystemAssert.requireArgument(scanners != null, "Cannot transform null metric scanners");
    	
    	if (scanners.isEmpty()) {
    		return new ArrayList<>();
    	}
    	List<Metric> newMetricsList = new ArrayList<Metric>();
    	
    	for (MetricScanner scanner : scanners) {
    		Map.Entry<Long, Double> next = scanner.peek();
    		TreeMap<Long, Double> dps = new TreeMap<>();
    		Metric m = new Metric(scanner.getMetric());
    		if (next == null) {
    			dps.putAll(this.valueReducerOrMapping.mapping(scanner.getMetric().getDatapoints(), constants));
    		} else if (next.getKey().equals(Collections.min(scanner.getMetric().getDatapoints().keySet()))) {
    			dps.putAll(this.valueReducerOrMapping.mappingScanner(scanner, constants));
    		} else {
    			while (scanner.hasNextDP()) {
    				scanner.getNextDP();
    			}
    			dps.putAll(this.valueReducerOrMapping.mapping(scanner.getMetric().getDatapoints(), constants));
    		}
    		    		
    		Long startKey = dps.ceilingKey(start);
    		Long endKey = dps.floorKey(end);
    		
    		if (startKey != null && endKey != null && startKey <= endKey) {
    			m.setDatapoints(dps.subMap(startKey, endKey + 1));
    		} else {
    			m.setDatapoints(new TreeMap<>());
    		}
    		newMetricsList.add(m);
    	}
    	return newMetricsList;
    }

    /**
     * Reduce transform for the list of metrics.
     *
     * @param   metrics  The list of metrics to reduce.
     *
     * @return  The reduced metric.
     */
    protected Metric reduce(List<Metric> metrics, List<String> constants) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform null metrics");
        SystemAssert.requireArgument(!(valueReducerOrMapping instanceof DivideValueReducerOrMapping) || metrics.size() >= 2, 
        		"DIVIDE Transform needs at least 2 metrics to perform the operation.");
        

        MetricDistiller distiller = new MetricDistiller();

        distiller.distill(metrics);

        Map<Long, List<Double>> collated = collate(metrics);
        Map<Long, Double> minDatapoints = reduce(collated, constants, metrics);
        String newMetricName = distiller.getMetric() == null ? defaultMetricName : distiller.getMetric();
        String newScopeName = distiller.getScope() == null ? defaultScope : distiller.getScope();
        Metric newMetric = new Metric(newScopeName, newMetricName);

        newMetric.setDisplayName(distiller.getDisplayName());
        newMetric.setUnits(distiller.getUnits());
        newMetric.setTags(distiller.getTags());
        newMetric.setDatapoints(minDatapoints);
        return newMetric;
    }
    
    protected Metric reduceScanner(List<MetricScanner> scanners, List<String> constants) {
    	SystemAssert.requireArgument(scanners != null, "Cannot transform null metric scanners");
    	SystemAssert.requireArgument(!(valueReducerOrMapping instanceof DivideValueReducerOrMapping) || scanners.size() >= 2, 
    			"DIVIDE Transform needs at least 2 metric scanners to perform the operation.");
    	
    	MetricDistiller distiller = new MetricDistiller();
    	
    	distiller.distillScanner(scanners);
    	
    	Map<Long, Double> minDatapoints = collateAndReduceScanner(scanners, constants);
    	String newMetricName = distiller.getMetric() == null ? defaultMetricName : distiller.getMetric();
    	String newScopeName = distiller.getScope() == null ? defaultScope : distiller.getScope();
    	Metric newMetric = new Metric(newScopeName, newMetricName);
    	
    	newMetric.setDisplayName(distiller.getDisplayName());
    	newMetric.setUnits(distiller.getUnits());
    	newMetric.setTags(distiller.getTags());
    	newMetric.setDatapoints(minDatapoints);
    	return newMetric;
    }
    
    protected Metric reduceToPager(List<MetricScanner> scanners, List<String> constants, Long start, Long end) {
    	SystemAssert.requireArgument(scanners != null, "Cannot transform null metric scanners");
    	SystemAssert.requireArgument(!(valueReducerOrMapping instanceof DivideValueReducerOrMapping) || scanners.size() >= 2, 
    			"DIVIDE Transform needs at least 2 metric scanners to perform the operation.");
    	
    	MetricDistiller distiller = new MetricDistiller();
    	distiller.distillScanner(scanners);
    	
    	Map<Long, Double> minDatapoints = collateAndReducePager(scanners, constants, start, end);
    	String newMetricName = distiller.getMetric() == null ? defaultMetricName : distiller.getMetric();
    	String newScopeName = distiller.getScope() == null ? defaultScope : distiller.getScope();
    	Metric newMetric = new Metric(newScopeName, newMetricName);
    	
    	newMetric.setDisplayName(distiller.getDisplayName());
    	newMetric.setUnits(distiller.getUnits());
    	newMetric.setTags(distiller.getTags());
    	newMetric.setDatapoints(minDatapoints);
    	return newMetric;
    }
    
    protected Map<Long, Double> reduce(Map<Long, List<Double>> collated, List<String> constants, List<Metric> metrics) {
    	Map<Long, Double> reducedDatapoints = new HashMap<>();
    	
    	for (Map.Entry<Long, List<Double>> entry : collated.entrySet()) {
    		if (entry.getValue().size() < metrics.size() && !fulljoinIndicator) {
    			continue;
    		}
    		Double reducedValue = constants == null || constants.isEmpty() ? 
    				this.valueReducerOrMapping.reduce(entry.getValue()) :
    				this.valueReducerOrMapping.reduce(entry.getValue(), constants);
    		reducedDatapoints.put(entry.getKey(), reducedValue);
    	}
    	return reducedDatapoints;
    }
            
    private Map<Long, List<Double>> collate(List<Metric> metrics) {
        Map<Long, List<Double>> collated = new HashMap<>();

        for (Metric metric : metrics) {
            for (Map.Entry<Long, Double> point : metric.getDatapoints().entrySet()) {
                if (!collated.containsKey(point.getKey())) {
                    collated.put(point.getKey(), new ArrayList<Double>());
                }
                collated.get(point.getKey()).add(point.getValue());
            }
        }
        return collated;
    }
    
    private Map<Long, Double> collateAndReduceScanner(List<MetricScanner> scanners, List<String> constants) {
    	Map<Long, List<Double>> collated = new HashMap<>();
    	
    	for (MetricScanner scanner : scanners) {
    		while (scanner.hasNextDP()) {
    			Map.Entry<Long, Double> dp = scanner.getNextDP();
    			if (!collated.containsKey(dp.getKey())) {
    				collated.put(dp.getKey(), new ArrayList<Double>());
    			}
    			collated.get(dp.getKey()).add(dp.getValue());
    		}
    	}
    	
    	Map<Long, Double> reducedDatapoints = new HashMap<>();
    	for (Map.Entry<Long, List<Double>> entry : collated.entrySet()) {
    		if (entry.getValue().size() < scanners.size() && !fulljoinIndicator) {
    			continue;
    		}
    		Double reducedValue = constants == null || constants.isEmpty() ?
    				this.valueReducerOrMapping.reduce(entry.getValue()) :
    				this.valueReducerOrMapping.reduce(entry.getValue(), constants);
    		reducedDatapoints.put(entry.getKey(), reducedValue);
    	}
    	return reducedDatapoints;
    }
    
    private Map<Long, Double> collateAndReducePager(List<MetricScanner> scanners, List<String> constants, Long start, Long end) {
    	Map<Long, List<Double>> collated = new HashMap<>();
    	
    	for (MetricScanner scanner : scanners) {
    		Map.Entry<Long, Double> next = scanner.peek();
    		if (next == null || next.getKey() > end) {
    			TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
    			Long startKey = dps.ceilingKey(start);
    			Long endKey = dps.floorKey(end);
    			if (startKey != null && endKey != null && startKey <= endKey) {
    				for (Map.Entry<Long, Double> entry : dps.subMap(startKey, endKey + 1).entrySet()) {
    					if (!collated.containsKey(entry.getKey())) {
    						collated.put(entry.getKey(), new ArrayList<>());
    					}
    					collated.get(entry.getKey()).add(entry.getValue());
    				}
    			}
    		} else if (next.getKey() > start) {
    			TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
    			Long startKey = dps.ceilingKey(start);
    			Long endKey = dps.floorKey(next.getKey());
    			if (startKey != null && endKey != null && startKey < endKey) {
    				for (Map.Entry<Long, Double> entry : dps.subMap(startKey, endKey).entrySet()) {
    					if (!collated.containsKey(entry.getKey())) {
    						collated.put(entry.getKey(), new ArrayList<>());
    					}
    					collated.get(entry.getKey()).add(entry.getValue());
    				}
    			}
    		} else {
    			while (scanner.peek() != null && scanner.peek().getKey() < start) {
    				scanner.getNextDP();
    			}
    		}
    		
    		while(scanner.peek() != null && scanner.peek().getKey() <= end) {
    			Map.Entry<Long, Double> dp = scanner.getNextDP();
    			if (!collated.containsKey(dp.getKey())) {
    				collated.put(dp.getKey(), new ArrayList<>());
    			}
    			collated.get(dp.getKey()).add(dp.getValue());
    		}
    	}
    	
    	Map<Long, Double> reducedDatapoints = new HashMap<>();
    	for (Map.Entry<Long, List<Double>> entry : collated.entrySet()) {
    		if (entry.getValue().size() < scanners.size() && !fulljoinIndicator) {
    			continue;
    		}
    		Double reducedValue = constants == null || constants.isEmpty() ?
    				this.valueReducerOrMapping.reduce(entry.getValue()) :
    				this.valueReducerOrMapping.reduce(entry.getValue(), constants);
    		reducedDatapoints.put(entry.getKey(), reducedValue);
    	}
    	return reducedDatapoints;
    }

   
    @Override
    public List<Metric> transform(List<Metric>... listOfList) {
        throw new UnsupportedOperationException("ReducerOrMapping doesn't need list of list!");
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner>... listOfList) {
        throw new UnsupportedOperationException("ReducerOrMapping doesn't need list of list!");
    }
    
    @Override
    public List<Metric> transformToPagerListOfList(List<List<MetricScanner>> scanners, Long start, Long end) {
    	throw new UnsupportedOperationException("ReducerOrMapping doesn't need list of list!");
    }

}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

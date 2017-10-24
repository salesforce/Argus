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

import com.google.common.primitives.Doubles;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.tsdb.MetricScanner;
import com.salesforce.dva.argus.system.SystemAssert;
import org.apache.commons.math.stat.descriptive.moment.Mean;
import org.apache.commons.math.stat.descriptive.moment.StandardDeviation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Filter transform is used by transform functions which cull the metric based on the result of evaluation against their datapoints.
 *
 * <p>So far, this transform funcs use this filter ABOVE, BELOW, HIGHEST, LOWEST, SORT, DOWNSAMPLER</p>
 *
 * <p>evaluations covers: ABOVE/BELOW/HIGHEST/LOWEST: average, min, max, recent SORT : maxima, minima, name, dev DOWNSAMPLER : avg, min, max, sum, dev
 * </p>
 *
 * @author  Ruofan Zhang(rzhang@salesforce.com)
 */
public class MetricFilterWithInteralReducerTransform implements Transform {

    //~ Instance fields ******************************************************************************************************************************

    private final ValueFilter valueFilter;
    private final String defaultScope;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new ReduceTransform object.
     *
     * @param  valueFilter  valueUnionReducer valueReducerOrMapping The valueMapping.
     */
    protected MetricFilterWithInteralReducerTransform(ValueFilter valueFilter) {
        this.valueFilter = valueFilter;
        this.defaultScope = valueFilter.name();
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Reduces the give metric to a single value based on the specified reducer.
     *
     * @param   metric       The metric to reduce.
     * @param   reducerType  The type of reduction to perform.
     *
     * @return  The reduced value.
     *
     * @throws  UnsupportedOperationException  If an unknown reducer type is specified.
     */
    public static String internalReducer(Metric metric, String reducerType) {
        Map<Long, Double> sortedDatapoints = new TreeMap<>();

        sortedDatapoints.putAll(metric.getDatapoints());
        
        List<Double> operands = new ArrayList<Double>();

        for (Double value : sortedDatapoints.values()) {
            if (reducerType.equals("name")) {
                break;
            }
            
            if (value == null) {
                operands.add(0.0);
            } else {
                operands.add(value);
            }
        }

        InternalReducerType type = InternalReducerType.fromString(reducerType);

        switch (type) {
            case AVG:
                return String.valueOf((new Mean()).evaluate(Doubles.toArray(operands)));
            case MIN:
                return String.valueOf(Collections.min(operands));
            case MAX:
                return String.valueOf(Collections.max(operands));
            case RECENT:
                return String.valueOf(operands.get(operands.size() - 1));
            case MAXIMA:
                return String.valueOf(Collections.max(operands));
            case MINIMA:
                return String.valueOf(Collections.min(operands));
            case NAME:
                return metric.getMetric();
            case DEVIATION:
                return String.valueOf((new StandardDeviation()).evaluate(Doubles.toArray(operands)));
            default:
                throw new UnsupportedOperationException(reducerType);
        }
    }
    
    /**
     * Reduces the given metric scanner to a single value based on the specified reducer.
     * 
     * @param scanner 		The Metric scanner to reduce.
     * @param reducerType	The type of reduction to perform.
     * 
     * @return The reduced value.
     * 
     * @throws UnsupportedOperationException	If an unknown reducer type is specified.
     */
    public static String internalReducerScanner(MetricScanner scanner, String reducerType, Map<Long, Double> sortedDatapoints) {
    	List<Double> operands = new ArrayList<Double>();
    	
    	while (scanner.hasNextDP()) {
    		if (reducerType.equals("name")) {
    			break;
    		}
    		
    		Map.Entry<Long, Double> dp = scanner.getNextDP();
    		
    		if (dp.getValue() == null) {
    			operands.add(0.0);
    		} else {
    			operands.add(dp.getValue());
    		}
    	}
    	
    	InternalReducerType type = InternalReducerType.fromString(reducerType);
    	
    	switch(type) {
	    	case AVG:
	    		return String.valueOf((new Mean()).evaluate(Doubles.toArray(operands)));
	    	case MIN:
	    		return String.valueOf(Collections.min(operands));
	    	case MAX:
	    		return String.valueOf(Collections.max(operands));
	    	case RECENT:
	    		return String.valueOf(operands.get(operands.size() - 1));
	    	case MAXIMA:
	    		return String.valueOf(Collections.max(operands));
	    	case MINIMA:
	    		return String.valueOf(Collections.min(operands));
	    	case NAME:
	    		return scanner.getMetricName();
	    	case DEVIATION:
	    		return String.valueOf((new StandardDeviation()).evaluate(Doubles.toArray(operands)));
	    	default:
	    		throw new UnsupportedOperationException(reducerType);
    	}
    }
    
    public static String internalReducerPager(MetricScanner scanner, String reducerType) {
    	List<Double> operands = new ArrayList<Double>();
    	
    	Map.Entry<Long, Double> next = scanner.peek();
    	if (next == null) {
    		Map<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
    		for (Double value : dps.values()) {
    			if (reducerType.equals("name")) {
    				break;
    			}
    			operands.add(value == null ? 0.0 : value);    			
    		}
    	} else if (!next.getKey().equals(Collections.min(scanner.getMetric().getDatapoints().keySet()))) {
    		TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
    		Long startKey = dps.firstKey();
    		Long endKey = dps.floorKey(next.getKey());
    		if (startKey != null && endKey != null && startKey <= endKey) {
	    		for (Double value : new TreeMap<>(dps.subMap(startKey, endKey + 1)).values()) {
	    			if (reducerType.equals("name")) {
	    				break;
	    			}
	    			operands.add(value == null ? 0.0 : value);
	    		}
    		}
    	}
    	
    	while (scanner.hasNextDP()) {
    		if (reducerType.equals("name")) {
    			break;
    		}
    		Double value = scanner.getNextDP().getValue();
    		operands.add(value == null ? 0.0 : value);
    	}
    	
    	InternalReducerType type = InternalReducerType.fromString(reducerType);
    	
    	switch (type) {
	    	case AVG:
	    		return String.valueOf((new Mean()).evaluate(Doubles.toArray(operands)));
    		case MIN:
    			return String.valueOf(Collections.min(operands));
    		case MAX:
    			return String.valueOf(Collections.max(operands));
    		case RECENT:
    			return String.valueOf(operands.get(operands.size() - 1));
    		case MAXIMA:
    			return String.valueOf(Collections.max(operands));
    		case MINIMA:
    			return String.valueOf(Collections.min(operands));
    		case NAME:
    			return scanner.getMetricName();
    		case DEVIATION:
    			return String.valueOf((new StandardDeviation()).evaluate(Doubles.toArray(operands)));
    		default:
    			throw new UnsupportedOperationException(reducerType);
    	}
    }

    /**
     * Sorts a metric.
     *
     * @param   map  The metrics to sort.
     *
     * @return  The sorted metrics.
     */
    public static Map<Metric, String> sortByValue(Map<Metric, String> map, final String reducerType) {
        List<Map.Entry<Metric, String>> list = new LinkedList<>(map.entrySet());

        Collections.sort(list, new Comparator<Map.Entry<Metric, String>>() {

                @Override
                public int compare(Map.Entry<Metric, String> o1, Map.Entry<Metric, String> o2) {
                	if(reducerType.equals("name")) {
                		return o1.getValue().compareTo(o2.getValue());
                	}
                	
                	Double d1 = Double.parseDouble(o1.getValue());
                	Double d2 = Double.parseDouble(o2.getValue());
                    return (d1.compareTo(d2));
                }
            });

        Map<Metric, String> result = new LinkedHashMap<>();

        for (Map.Entry<Metric, String> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public String getResultScopeName() {
        return defaultScope;
    }

    @Override
    public List<Metric> transform(List<Metric> metrics) {
        throw new UnsupportedOperationException("Filter transform need constant input!");
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners) {
        throw new UnsupportedOperationException("Filter transform need constant input!");
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, Long start, Long end) {
    	throw new UnsupportedOperationException("Filter trnasform need constant input!");
    }

    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform empty metric/metrics");
        if (metrics.isEmpty()) {
            return metrics;
        }
        SystemAssert.requireArgument(!constants.isEmpty(), "Filter Transform must provide at least limit");
        if (constants.size() == 1) {
            constants.add(InternalReducerType.AVG.reducerName);
        }

        String limit = constants.get(0);
        String type = constants.get(1);
        Map<Metric, String> extendedSortedMap = createExtendedMap(metrics, type);
        List<Metric> filteredMetricList = this.valueFilter.filter(extendedSortedMap, limit);

        return filteredMetricList;
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners, List<String> constants) {
    	SystemAssert.requireArgument(scanners != null, "Cannot transform emtpy metric scanner/scanners");
    	if (scanners.isEmpty()) {
    		return new ArrayList<>();
    	}
    	SystemAssert.requireArgument(!constants.isEmpty(), "Filter Transform must provide at least limit");
    	if (constants.size() == 1) {
    		constants.add(InternalReducerType.AVG.reducerName);
    	}
    	
    	String limit = constants.get(0);
    	String type = constants.get(1);
    	Map<Metric, String> extendedSortedMap = createExtendedMapScanner(scanners, type);
    	List<Metric> filteredMetricList = this.valueFilter.filter(extendedSortedMap, limit);
    	
    	return filteredMetricList;
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, List<String> constants, Long start, Long end) {
    	SystemAssert.requireArgument(scanners != null, "Cannot transform empty metric scanner/scanners");
    	if (scanners.isEmpty()) {
    		return new ArrayList<>();
    	}
    	SystemAssert.requireArgument(!constants.isEmpty(), "Filter Transform must provide at least limit");
    	if (constants.size() == 1) {
    		constants.add(InternalReducerType.AVG.reducerName);
    	}
    	
    	String limit = constants.get(0);
    	String type = constants.get(1);
    	
    	Map<Metric, String> extendedSortedMap = createExtendedMapPager(scanners, type, start, end);
    	List<Metric> filteredMetricList = this.valueFilter.filter(extendedSortedMap, limit);
    	
    	return filteredMetricList;
    }

    private Map<Metric, String> createExtendedMap(List<Metric> metrics, String type) {
        Map<Metric, String> extendedSortedMap = new HashMap<Metric, String>();

        for (Metric metric : metrics) {
            String extendedEvaluation = internalReducer(metric, type);

            extendedSortedMap.put(metric, extendedEvaluation);
        }
        return sortByValue(extendedSortedMap, type);
    }
    
    private Map<Metric, String> createExtendedMapScanner(List<MetricScanner> scanners, String type) {
    	Map<Metric, String> extendedSortedMap = new HashMap<Metric, String>();
    	
    	for (MetricScanner scanner : scanners) {
    		Map<Long, Double> dps = new HashMap<>();
    		String extendedEvaluation = internalReducerScanner(scanner, type, dps);
    		if (type.equals("name")) {
    			buildMetric(scanner);
    		}
    		
    		extendedSortedMap.put(scanner.getMetric(), extendedEvaluation);
    	}
    	return sortByValue(extendedSortedMap, type);
    }
    
    private Map<Metric, String> createExtendedMapPager(List<MetricScanner> scanners, String type, Long start, Long end) {
    	Map<Metric, String> extendedSortedMap = new HashMap<Metric, String>();
    	
    	for (MetricScanner scanner : scanners) {
    		String extendedEvaluation = internalReducerPager(scanner, type);
    		Metric m = metricRange(scanner, start, end);
    		extendedSortedMap.put(m, extendedEvaluation);
    	}
    	return sortByValue(extendedSortedMap, type);
    }
    
    private Metric metricRange(MetricScanner scanner, Long start, Long end) {
    	Map.Entry<Long, Double> next = scanner.peek();
    	Metric m = scanner.getMetric();
    	m.setDatapoints(new HashMap<>());
    	if (next == null || next.getKey() > end) {
    		TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
    		Long startKey = dps.ceilingKey(start);
    		Long endKey = dps.floorKey(end);
    		if (startKey != null && endKey != null && startKey <= endKey) {
    			m.setDatapoints(dps.subMap(startKey, endKey + 1));
    			return m;
    		}
    		m.setDatapoints(new HashMap<>());
    		return m;
    	} else if (next.getKey() > start) {
    		TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
    		Long startKey = dps.ceilingKey(start);
    		Long endKey = dps.floorKey(next.getKey());
    		if (startKey != null && endKey != null && startKey < endKey) {
    			m.setDatapoints(dps.subMap(startKey, endKey));
    		}
    	} else {
    		while (scanner.peek() != null && scanner.peek().getKey() < start) {
    			scanner.getNextDP();
    		}
    	}
    	
    	Map<Long, Double> extraDps = new HashMap<>();
    	while (scanner.peek() != null && scanner.peek().getKey() <= end) {
    		Map.Entry<Long, Double> dp = scanner.getNextDP();
    		extraDps.put(dp.getKey(), dp.getValue());
    	}
    	m.addDatapoints(extraDps);
    	return m;
    }
    
    private void buildMetric(MetricScanner scanner) {
		while(scanner.hasNextDP()) {
			scanner.getNextDP();
		}
    }

    @Override
    public List<Metric> transform(List<Metric>... listOfList) {
        throw new UnsupportedOperationException("Filter doesn't need list of list!");
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner>... listOfList) {
        throw new UnsupportedOperationException("Filter doesn't need list of list!");
    }
    
    @Override
    public List<Metric> transformToPagerListOfList(List<List<MetricScanner>> scanners, Long start, Long end) {
    	throw new UnsupportedOperationException("Filter doesn't need list of list!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

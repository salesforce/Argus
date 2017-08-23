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
import com.salesforce.dva.argus.service.metric.MetricReader;
import com.salesforce.dva.argus.service.tsdb.MetricScanner;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.commons.math3.stat.descriptive.rank.Percentile;

/**
 * Calculates the Nth percentile. If a window size is specified, each metric will be evaluated individually using that window. Otherwise, the set of
 * data points across metrics at each given timestamp are evaluated resulting in a single metric result.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class PercentileValueReducerOrMapping implements ValueReducerOrMapping {

    //~ Static fields/initializers *******************************************************************************************************************

    private static Double percentile = Double.MAX_VALUE;
    private static final String INDIVIDUAL = "INDIVIDUAL";

    //~ Methods **************************************************************************************************************************************

    @Override
    public Double reduce(List<Double> values) {
        throw new UnsupportedOperationException("Percentile Transform with reducer is not supposed to be used without a constant");
    }
    
    @Override
    public Double reduceScanner(MetricScanner scanner) {
        throw new UnsupportedOperationException("Percentile Transform with reducer is not supposed to be used without a constant");
    }

    @Override
    public Double reduce(List<Double> values, List<String> constants) {
        parseConstants(constants);
        return _calculateNthPercentile(values, percentile);
    }
    
    @Override
    public Double reduceScanner(MetricScanner scanner, List<String> constants) {
    	parseConstants(constants);
        List<Double> values = new ArrayList<>();
        while (scanner.hasNextDP()) {
            values.add(scanner.getNextDP().getValue());
        }
    	return _calculateNthPercentile(values, percentile);
    }

    private void parseConstants(List<String> constants) {
        SystemAssert.requireArgument(constants != null && !constants.isEmpty(),
            "Percentile Transform must provide at least percentile to calculate.");
        SystemAssert.requireArgument(Double.parseDouble(constants.get(0)) > 0.0 && Double.parseDouble(constants.get(0)) < 100.0,
            "For Percentile Transform, 0.0 < percentile < 100.0.");
        PercentileValueReducerOrMapping.percentile = Double.parseDouble(constants.get(0));
        if (constants.size() > 1) {
            if (!INDIVIDUAL.equalsIgnoreCase(constants.get(1))) {
                String window = constants.get(1);
                try {
                    MetricReader.TimeUnit.fromString(window.substring(window.length() - 1));
                    Long.parseLong(window.substring(0, window.length() - 1));
                } catch (Exception t) {
                    throw new IllegalArgumentException("Invalid timeWindow: " + window + ". Please specify a valid window (E.g. 1s, 1m, 1h, 1d) ");
                }
            }
        }
    }

    @Override
    public Map<Long, Double> mapping(Map<Long, Double> originalDatapoints) {
        throw new UnsupportedOperationException("Percentile Transform with mapping is not supposed to be used without a constant");
    }
    
    @Override
    public Map<Long, Double> mappingScanner(MetricScanner scanner) {
        throw new UnsupportedOperationException("Percentile Transform with mapping is not supposed to be used without a constant");
    }
    
    @Override
    public Map<Long, Double> mappingToPager(MetricScanner scanner, Long start, Long end) {
    	throw new UnsupportedOperationException("Percentile Transform with mapping is not supposed to be used without a constant");
    }

    @Override
    public Map<Long, Double> mapping(Map<Long, Double> originalDatapoints, List<String> constants) {
        parseConstants(constants);
        return _calculateNthPercentileForOneMetric(originalDatapoints, percentile);
    }
    
    @Override
    public Map<Long, Double> mappingScanner(MetricScanner scanner, List<String> constants) {
    	parseConstants(constants);
    	return _calculateNthPercentileForOneMetricScanner(scanner, percentile);
    }
    
    @Override
    public Map<Long, Double> mappingToPager(MetricScanner scanner, List<String> constants, Long start, Long end) {
    	parseConstants(constants);
    	Map.Entry<Long, Double> next = scanner.peek();
    	TreeMap<Long, Double> res = new TreeMap<>();
        Long firstTimestamp = null;
        List<Double> values = new ArrayList<>();
    	if (next == null) {
            firstTimestamp = Collections.min(scanner.getMetric().getDatapoints().keySet());
            values.addAll(scanner.getMetric().getDatapoints().values());
        } else if (next.getKey() > Collections.min(scanner.getMetric().getDatapoints().keySet())) {
            TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
            firstTimestamp = dps.firstKey();
            Long endKey = dps.floorKey(next.getKey());
            if (firstTimestamp != null && endKey != null && firstTimestamp < endKey) {
                values.addAll(dps.subMap(firstTimestamp, endKey).values());
            }
        } else {
            firstTimestamp = next.getKey();
        }
        while (scanner.hasNextDP()) {
            values.add(scanner.getNextDP().getValue());
        }
        res.put(firstTimestamp, _calculateNthPercentile(values, percentile));

    	Long startKey = res.ceilingKey(start);
		Long endKey = res.floorKey(end);
		if (startKey == null || endKey == null || startKey > endKey) {
			return new TreeMap<>();
		}
		return res.subMap(startKey, endKey + 1);
    }

    @Override
    public String name() {
        return TransformFactory.Function.PERCENTILE.name();
    }

    private Map<Long, Double> _calculateNthPercentileForOneMetric(Map<Long, Double> originalDatapoints, Double percentileValue) {
    	
        Map<Long, Double> result = new TreeMap<>();
        for (Long timestamp : originalDatapoints.keySet()) {
            result.put(timestamp, _calculateNthPercentile(originalDatapoints.values(), percentileValue));
            break;
        }
        return result;
    }
    
    private Map<Long, Double> _calculateNthPercentileForOneMetricScanner(MetricScanner scanner, Double percentileValue) {
    	Map<Long, Double> result = new TreeMap<>();
        Long firstTimestamp = scanner.peek().getKey();
        List<Double> values = new ArrayList<>();
        while (scanner.hasNextDP()) {
            values.add(scanner.getNextDP().getValue());
        }
        result.put(firstTimestamp, _calculateNthPercentile(values, percentileValue));
        return result;
    }

    private Double _calculateNthPercentile(Collection<Double> values, Double percentileValue) {
        return new Percentile().evaluate(Doubles.toArray(values), percentileValue);
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

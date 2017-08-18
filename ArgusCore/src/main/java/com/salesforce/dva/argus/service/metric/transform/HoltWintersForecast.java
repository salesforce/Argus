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
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.service.tsdb.MetricScanner;
import com.salesforce.dva.argus.system.SystemAssert;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Calculates the Holt-Winters forecast.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class HoltWintersForecast extends HoltWintersAnalysis implements Transform {

    //~ Instance fields ******************************************************************************************************************************

    private final TSDBService _tsdbService;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new HoltWintersForecast object.
     *
     * @param  tsdbService  The TSDB service to use.  Cannot be null.
     */
    public HoltWintersForecast(TSDBService tsdbService) {
        _tsdbService = tsdbService;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public List<Metric> transform(List<Metric> metrics) {
        throw new UnsupportedOperationException("HoltWinters Transform needs 4 constants. 3 parameters(alpha, beta and gamma) and season length.");
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners) {
    	throw new UnsupportedOperationException("HoltWinters Transform needs 4 constants. 3 parameters(alpha, beta and gamma) and season length.");
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, Long start, Long end) {
    	throw new UnsupportedOperationException("HoltWinters Transform needs 4 constants. 3 parameters(alpha, beta and gamma) and season length.");
    }

    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
        SystemAssert.requireArgument(metrics != null, "Metrics List cannot be null");
        SystemAssert.requireArgument(constants != null && constants.size() == 4, "Constants List cannot be null and its size must be equal to 4.");

        double alpha = Double.parseDouble(constants.get(0));
        double beta = Double.parseDouble(constants.get(1));
        double gamma = Double.parseDouble(constants.get(2));
        int seasonLength = Integer.parseInt(constants.get(3));
        List<Metric> result = new ArrayList<Metric>(metrics.size());

        for (Metric metric : metrics) {
            MetricQuery oneWeekBeforeQuery = new MetricQuery(metric.getQuery());

            oneWeekBeforeQuery.setEndTimestamp(oneWeekBeforeQuery.getStartTimestamp());
            oneWeekBeforeQuery.setStartTimestamp(oneWeekBeforeQuery.getStartTimestamp() - ONE_WEEK_IN_MILLIS);

            List<Metric> metricsList = _tsdbService.getMetrics(Arrays.asList(new MetricQuery[] { oneWeekBeforeQuery })).get(oneWeekBeforeQuery);
            Metric oneWeekBeforeMetric = null;

            for (Metric m : metricsList) {
                if (metric.equals(m)) {
                    oneWeekBeforeMetric = m;
                    break;
                }
            }

            Map<Long, Double> bootstrappedDps = new TreeMap<>(metric.getDatapoints());

            if (oneWeekBeforeMetric != null) {
                bootstrappedDps.putAll(oneWeekBeforeMetric.getDatapoints());
            }

            Metric resultMetric = new Metric(metric);

            resultMetric.setDatapoints(_performHoltWintersAnalysis(bootstrappedDps, alpha, beta, gamma, seasonLength,
                    metric.getQuery().getStartTimestamp().longValue()).getForecastedDatapoints());
            result.add(resultMetric);
        }
        return result;
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners, List<String> constants) {
    	SystemAssert.requireArgument(scanners != null, "Metric Scanners list cannot be null.");
    	SystemAssert.requireArgument(constants != null && constants.size() == 4, "Constants List cannot be null and its size must be equal to 4.");
    	
    	double alpha = Double.parseDouble(constants.get(0));
    	double beta = Double.parseDouble(constants.get(1));
    	double gamma = Double.parseDouble(constants.get(2));
    	int seasonLength = Integer.parseInt(constants.get(3));
    	List<Metric> result = new ArrayList<Metric>(scanners.size());
    	
    	for (MetricScanner scanner : scanners) {
    		MetricQuery oneWeekBeforeQuery = new MetricQuery(scanner.getQuery());
    		
    		oneWeekBeforeQuery.setEndTimestamp(oneWeekBeforeQuery.getStartTimestamp());
    		oneWeekBeforeQuery.setStartTimestamp(oneWeekBeforeQuery.getStartTimestamp() - ONE_WEEK_IN_MILLIS);
    		
    		List<MetricScanner> scannersList = _tsdbService.getMetricScanners(Arrays.asList(new MetricQuery[] { oneWeekBeforeQuery })).get(oneWeekBeforeQuery);
    		MetricScanner oneWeekBeforeScanner = null;
    		
    		for (MetricScanner s : scannersList) {
    			if (scanner.getMetric().equals(s.getMetric())) {
    				oneWeekBeforeScanner = s;
    				break;
    			}
    		}
    		
    		Map<Long, Double> bootstrappedDps = new TreeMap<>();
    		while (scanner.hasNextDP()) {
    			Map.Entry<Long, Double> dp = scanner.getNextDP();
    			bootstrappedDps.put(dp.getKey(), dp.getValue());
    		}
    	    			
    		if (oneWeekBeforeScanner != null) {
    			while (oneWeekBeforeScanner.hasNextDP()) {
    				Map.Entry<Long, Double> dp = oneWeekBeforeScanner.getNextDP();
    				bootstrappedDps.put(dp.getKey(), dp.getValue());
    			}
    		}
    		
    		Metric resultMetric = new Metric(scanner.getMetric());
    		
    		resultMetric.setDatapoints(_performHoltWintersAnalysis(bootstrappedDps, alpha, beta, gamma, seasonLength,
    				scanner.getQuery().getStartTimestamp().longValue()).getForecastedDatapoints());
    		result.add(resultMetric);
    	}
    	return result;
    }
    
    private Map<Long, Double> addDatapointsToMap(Map<Long, Double> bootstrappedDps, MetricScanner scanner, Long start, Long end) {
    	Map.Entry<Long, Double> next = scanner.peek();
    	if (next == null) {
    		for (Map.Entry<Long, Double> entry : scanner.getMetric().getDatapoints().entrySet()) {
    			bootstrappedDps.put(entry.getKey(), entry.getValue());
    		}
    	} else if (!next.getKey().equals(Collections.min(scanner.getMetric().getDatapoints().keySet()))) {
    		TreeMap<Long, Double> dps = new TreeMap<>(scanner.getMetric().getDatapoints());
    		Long startKey = dps.firstKey();
    		Long endKey = dps.floorKey(next.getKey());
    		if (startKey != null && endKey != null && startKey < endKey) {
    			for (Map.Entry<Long, Double> entry : dps.subMap(startKey, endKey).entrySet()) {
    				bootstrappedDps.put(entry.getKey(), entry.getValue());
    			}
    		}
    	} else {
    		while (scanner.peek() != null && scanner.peek().getKey() < start) {
    			Map.Entry<Long, Double> dp = scanner.getNextDP();
    			bootstrappedDps.put(dp.getKey(), dp.getValue());
    		}
    	}
    	
    	while (scanner.hasNextDP()) {
    		Map.Entry<Long, Double> dp = scanner.getNextDP();
    		bootstrappedDps.put(dp.getKey(), dp.getValue());
    	}
    	
    	return bootstrappedDps;
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, List<String> constants, Long start, Long end) {
    	SystemAssert.requireArgument(scanners != null, "Metric Scanners list cannot be null.");
    	SystemAssert.requireArgument(constants != null && constants.size() == 4, "Constants List cannot be null and its size must be equal to 4.");
    	
    	double alpha = Double.parseDouble(constants.get(0));
    	double beta = Double.parseDouble(constants.get(1));
    	double gamma = Double.parseDouble(constants.get(2));
    	int seasonLength = Integer.parseInt(constants.get(3));
    	List<Metric> result = new ArrayList<Metric>();
    	
    	for (MetricScanner scanner : scanners) {
    		MetricQuery oneWeekBeforeQuery = new MetricQuery(scanner.getQuery());
    		
    		oneWeekBeforeQuery.setEndTimestamp(oneWeekBeforeQuery.getStartTimestamp());
    		oneWeekBeforeQuery.setStartTimestamp(oneWeekBeforeQuery.getStartTimestamp() - ONE_WEEK_IN_MILLIS);
    		
    		List<MetricScanner> scannersList = _tsdbService.getMetricScanners(Arrays.asList(new MetricQuery [] { oneWeekBeforeQuery })).get(oneWeekBeforeQuery);
    		MetricScanner oneWeekBeforeScanner = null;
    		
    		for (MetricScanner s : scannersList) {
    			if (scanner.getMetric().equals(s.getMetric())) {
    				oneWeekBeforeScanner = s;
    				break;
    			}
    		}
    		
    		Map<Long, Double> bootstrappedDps = addDatapointsToMap(new TreeMap<>(), scanner, start, end);
    		if (oneWeekBeforeScanner != null) {
    			bootstrappedDps = addDatapointsToMap(bootstrappedDps, oneWeekBeforeScanner, start, end);
    		}
    		
    		TreeMap<Long, Double> res = new TreeMap<>(_performHoltWintersAnalysis(bootstrappedDps, alpha, beta, gamma, seasonLength,
    				scanner.getQuery().getStartTimestamp().longValue()).getForecastedDatapoints());
    	    		
    		Long startKey = res.ceilingKey(start);
    		Long endKey = res.floorKey(end);
    		Metric m = new Metric(scanner.getMetric());
    		if (startKey != null && endKey != null && startKey <= endKey) {
    			m.setDatapoints(res.subMap(startKey, endKey + 1));
    		} else {
    			m.setDatapoints(new TreeMap<>());
    		}
    		result.add(m);
    	}
    	return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Metric> transform(List<Metric>... listOfList) {
        // TODO Auto-generated method stub
        return null;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public List<Metric> transformScanner(List<MetricScanner>... listOfList) {
    	// TODO Auto-generated method stub
    	return null;
    }
    
    @Override
    public List<Metric> transformToPagerListOfList(List<List<MetricScanner>> scanners, Long start, Long end) {
    	// TODO Auto-generated method stub
    	return null;
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.HW_FORECAST.getName();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

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

import com.github.brandtg.stl.StlDecomposition;
import com.github.brandtg.stl.StlResult;
import com.google.common.primitives.Doubles;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.tsdb.MetricScanner;
import com.salesforce.dva.argus.system.SystemAssert;

import sun.tools.java.Scanner;

import java.util.*;


/**
 * Created by vmuruganantham on 7/13/16.
 */
public class AnomalySTLTransform implements Transform {

    //~ Variables **************************************************************************************************************************************

    private double max_norm_prob = normPDF(0);

    //~ Methods **************************************************************************************************************************************

    @Override
    public List<Metric> transform(List<Metric> metrics) {
        List<String> l = new ArrayList<String>();
        int size = metrics.get(0).getDatapoints().size();
        if (size >= 52 * 2) {
            l.add(0, "52");
        } else {
            l.add(0, Integer.toString(size/2));
        }
        
        return transform(metrics, l);
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners) {
    	List<String> l = new ArrayList<String>();
    	List<Metric> mList = new ArrayList<>();
    	
    	int dpCount = 0;
    	Map<Long, Double> dps = new TreeMap<>();
    	
    	while (scanners.get(0).hasNextDP()) {
    		dpCount++;
    		Map.Entry<Long, Double> dp = scanners.get(0).getNextDP();
    		dps.put(dp.getKey(), dp.getValue());
    	}
    	
    	if (dpCount >= 52 * 2) {
    		l.add(0, "52");
    	} else {
    		l.add(0, Integer.toString(dpCount / 2));
    	}
    	
    	Metric m = new Metric(scanners.get(0).getMetric());
    	m.setDatapoints(dps);
    	mList.add(m);
    	
    	return transform(mList, l);
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, Long start, Long end) {
    	List<String> l = new ArrayList<String>();
    	List<Metric> mList = new ArrayList<>();
    	
    	Map.Entry<Long, Double> next = scanners.get(0).peek();
    	int size = 0;
    	if (next == null) {
    		size = scanners.get(0).getMetric().getDatapoints().size();
    	} else {
    		if (!next.getKey().equals(Collections.min(scanners.get(0).getMetric().getDatapoints().keySet()))) {
    			TreeMap<Long, Double> dps = new TreeMap<>(scanners.get(0).getMetric().getDatapoints());
    			Long startKey = dps.firstKey();
    			Long endKey = dps.floorKey(next.getKey());
    			if (startKey != null && endKey != null && startKey <= endKey) {
    				size += dps.subMap(startKey, endKey + 1).size();
    			}
    		}
    		while (scanners.get(0).hasNextDP()) {
    			scanners.get(0).getNextDP();
    			size++;
    		}
    	}
    	
    	if (size >= 52 * 2) {
			l.add(0, "52");
		} else {
			l.add(0, Integer.toString(size / 2));
		}
    	
    	mList.add(scanners.get(0).getMetric());
    	List<Metric> res = transform(mList, l);
    	List<Metric> result = new ArrayList<>();
    	
    	for (Metric m : res) {
    		TreeMap<Long, Double> dps = new TreeMap<>(m.getDatapoints());
    		Long startKey = dps.ceilingKey(start);
    		Long endKey = dps.floorKey(end);
    		if (startKey != null && endKey != null && startKey <= endKey) {
    			m.setDatapoints(dps.subMap(startKey, endKey + 1));
    			result.add(m);
    		} else {
    			m.setDatapoints(new HashMap<>());
    			result.add(m);
    		}
    	}
    	return result;
    }

    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform null metric/metrics");
        SystemAssert.requireState(metrics.size() == 1, "Anomaly Detection Transform can only be used on one metric.");
        SystemAssert.requireState(metrics.get(0) != null, "Anomaly Detection Transform cannot be used with a null " +
                "metric.");
        SystemAssert.requireState(constants.size() <= 2, "Anomaly Detection Transform can only be used with one or " +
                "two integer constants.");
        SystemAssert.requireState(metrics.get(0).getDatapoints().size() >= 4, "STL Anomaly Detection Transform can " +
                "only be used if there are at least 4 points.");

        if (constants.size() == 0) {
            return transform(metrics);
        } else if (constants.size() == 2) {
            SystemAssert.requireState(constants.get(1).equals("resid") || constants.get(1).equals("anomalyScore"),
                    "The only options for STL Anomaly Detection Transform are '$resid' to view the residuals or " +
                    "'$anomalyScore' to view the anomaly Score. Entering no option defaults to '$anomalyScore'.");
        }

        SystemAssert.requireState(Integer.parseInt(constants.get(0)) >= 2, "STL Anomaly Detection Transform can only " +
                "be used with a season size of at least 2.");

        // argument passed in to determine what one "season" is; later passed to StlDecomposition
        int season = Integer.parseInt(constants.get(0));

        Metric metric = metrics.get(0);
        Map<Long, Double> datapoints = metric.getDatapoints();

        double[] values = new double[datapoints.size()];
        List<Long> time_list = new ArrayList<>(datapoints.keySet());
        Collections.sort(time_list);
        double[] times = new double[datapoints.size()];

        for (int i = 0; time_list.size() > i; i++) {
            values[i] = datapoints.get(time_list.get(i));
            times[i] = (double) time_list.get(i);
        }

        // The argument to StlDecomposition specifies what fraction of a year one season is
        // The decomposition then splits up the timeseries into trend, seasonal, and residual components
        StlResult stl = new StlDecomposition(season).decompose(times, values);

        // Trend and seasonal components of stl can also be accessed using stl.getTrend() and stl.getSeasonal() respectively
        double[] remainder = stl.getRemainder();

        double mean = calcMean(remainder);
        double sd = calcSD(remainder, mean);

        HashMap<Long, Double> remainder_map = new HashMap<>();

        if (constants.size() == 2 && constants.get(1).equals("resid")) {
            for (int i = 0; i < time_list.size(); i++) {
                remainder_map.put((long) times[i], remainder[i]);
            }
        } else if (constants.size() == 2 && constants.get(1).equals("anomalyScore")) {
            for (int i = 0; i < time_list.size(); i++) {
                remainder_map.put((long) times[i], anomalyScore(remainder[i], mean, sd));
            }
        } else {
            for (int i = 0; i < time_list.size(); i++) {
                remainder_map.put((long) times[i], anomalyScore(remainder[i], mean, sd));
            }
        }

        Metric remainder_metric = new Metric(getResultScopeName(), "STL Anomaly Score");
        remainder_metric.setDatapoints(remainder_map);
        List<Metric> result = new ArrayList<>(metrics.size());
        result.add(0, remainder_metric);

        return result;
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners, List<String> constants) {
    	SystemAssert.requireArgument(scanners != null, "Cannot transform null metric scanner/scanners");
        SystemAssert.requireState(scanners.size() == 1, "Anomaly Detection Transform can only be used on one metric scanner.");
        SystemAssert.requireState(scanners.get(0) != null, "Anomaly Detection Transform cannot be used with a null " +
                "metric scanner.");
        SystemAssert.requireState(constants.size() <= 2, "Anomaly Detection Transform can only be used with one or " +
                "two integer constants.");

        if (constants.size() == 0) {
            return transformScanner(scanners);
        } else if (constants.size() == 2) {
            SystemAssert.requireState(constants.get(1).equals("resid") || constants.get(1).equals("anomalyScore"),
                    "The only options for STL Anomaly Detection Transform are '$resid' to view the residuals or " +
                    "'$anomalyScore' to view the anomaly Score. Entering no option defaults to '$anomalyScore'.");
        }

        SystemAssert.requireState(Integer.parseInt(constants.get(0)) >= 2, "STL Anomaly Detection Transform can only " +
                "be used with a season size of at least 2.");

        // argument passed in to determine what one "season" is; later passed to StlDecomposition
        int season = Integer.parseInt(constants.get(0));
        
        MetricScanner scanner = scanners.get(0);
        Map<Long, Double> datapoints = new TreeMap<>();
        List<Long> timestamps = new ArrayList<>();
        
        int dpCount = 0; // make sure we have enough points -> need at least 4
        
	    while (scanner.hasNextDP()) {
	    	dpCount++;
	    	Map.Entry<Long, Double> dp = scanner.getNextDP();
	    	datapoints.put(dp.getKey(), dp.getValue());
	    	timestamps.add(dp.getKey());
        }
        
        SystemAssert.requireState(dpCount >= 4, "STL Anomaly Detection Transform can " +
                "only be used if there are at least 4 points.");
        
        double[] values = Doubles.toArray(datapoints.values());
        double[] times = Doubles.toArray(timestamps);
        
        StlResult stl = new StlDecomposition(season).decompose(times, values);
        double[] remainder = stl.getRemainder();
        
        double mean = calcMean(remainder);
        double sd = calcSD(remainder, mean);
        
        HashMap<Long, Double> remainder_map = new HashMap<>();
        
        if (constants.size() == 2 && constants.get(1).equals("resid")) {
        	for (int i = 0; i < dpCount; i++) {
        		remainder_map.put((long) times[i], remainder[i]);
        	}
        } else {
        	for (int i = 0; i < dpCount; i++) {
        		remainder_map.put((long) times[i], anomalyScore(remainder[i], mean, sd)); 
        	}
        }
        
        Metric remainder_metric = new Metric(getResultScopeName(), "STL Anomaly Score");
        remainder_metric.setDatapoints(remainder_map);
        List<Metric> result = new ArrayList<>(scanners.size());
        result.add(remainder_metric);
        
        return result;
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, List<String> constants, Long start, Long end) {
    	SystemAssert.requireArgument(scanners != null, "Cannot transform null metric scanner/scanners");
        SystemAssert.requireState(scanners.size() == 1, "Anomaly Detection Transform can only be used on one metric scanner.");
        SystemAssert.requireState(scanners.get(0) != null, "Anomaly Detection Transform cannot be used with a null " +
                "metric scanner.");
        SystemAssert.requireState(constants.size() <= 2, "Anomaly Detection Transform can only be used with one or " +
                "two integer constants.");

        if (constants.size() == 0) {
            return transformScanner(scanners);
        } else if (constants.size() == 2) {
            SystemAssert.requireState(constants.get(1).equals("resid") || constants.get(1).equals("anomalyScore"),
                    "The only options for STL Anomaly Detection Transform are '$resid' to view the residuals or " +
                    "'$anomalyScore' to view the anomaly Score. Entering no option defaults to '$anomalyScore'.");
        }

        SystemAssert.requireState(Integer.parseInt(constants.get(0)) >= 2, "STL Anomaly Detection Transform can only " +
                "be used with a season size of at least 2.");
        
        
        Map.Entry<Long, Double> next = scanners.get(0).peek();
        List<Metric> res = new ArrayList<>();
        if (next == null) {
        	List<Metric> mList = new ArrayList<>();
        	mList.add(scanners.get(0).getMetric());
        	res = transform(mList, constants);
        } else if (next.getKey().equals(Collections.min(scanners.get(0).getMetric().getDatapoints().keySet()))) {
        	res = transformScanner(scanners, constants);
        } else {
        	int season = Integer.parseInt(constants.get(0));
        	List<Long> timestamps = new ArrayList<>();
        	TreeMap<Long, Double> dps = new TreeMap<>(scanners.get(0).getMetric().getDatapoints());
        	Long startKey = dps.firstKey();
        	Long endKey = dps.floorKey(next.getKey());
        	int dpCount = 0;
        	if (startKey != null && endKey != null && startKey < endKey) {
        		timestamps.addAll(dps.subMap(startKey, endKey).keySet());
        		dpCount += dps.subMap(startKey, endKey).size();
        	}
        	
        	while (scanners.get(0).hasNextDP()) {
        		dpCount++;
        		Map.Entry<Long, Double> dp = scanners.get(0).getNextDP();
        		timestamps.add(dp.getKey());
        	}
        	
        	SystemAssert.requireState(dpCount >= 4, "STL Anomaly Detection Transform can " + 
        			" only be used if there are at least 4 points.");
        	
        	double[] values = Doubles.toArray(scanners.get(0).getMetric().getDatapoints().values());
        	double[] times = Doubles.toArray(timestamps);
        	
        	StlResult stl = new StlDecomposition(season).decompose(times, values);
        	double[] remainder = stl.getRemainder();
        	
        	double mean = calcMean(remainder);
        	double sd = calcSD(remainder, mean);
        	
        	HashMap<Long, Double> remainder_map = new HashMap<>();
        	
        	if (constants.size() == 2 && constants.get(1).equals("resid")) {
        		for (int i = 0; i < dpCount; i++) {
        			remainder_map.put((long) times[i], remainder[i]); 
        		}
        	} else {
        		for (int i = 0; i < dpCount; i++) {
        			remainder_map.put((long) times[i], anomalyScore(remainder[i], mean, sd)); 
        		}
        	}
        	
        	Metric remainder_metric = new Metric(getResultScopeName(), "STL Anomaly Score");
        	remainder_metric.setDatapoints(remainder_map);
        	res.add(remainder_metric);
        }
        
        List<Metric> result = new ArrayList<>();
        for (Metric m : res) {
        	TreeMap<Long, Double> dps = new TreeMap<>(m.getDatapoints());
        	Long startKey = dps.ceilingKey(start);
        	Long endKey = dps.floorKey(end);
        	if (startKey != null && endKey != null && startKey <= endKey) {
        		m.setDatapoints(dps.subMap(startKey, endKey + 1));
        		result.add(m);
        	} else {
        		m.setDatapoints(new HashMap<>());
        		result.add(m);
        	}
        }
        
        return result;
    }

    // Computes anomaly score based on time series statistics (mean and standard deviation)
    // Input: value of datapoint, mean of time series, standard deviation of time series
    // Output: anomaly score between 0 and 100
    private double anomalyScore(double x, double mean, double sd) {
        x = (x - mean) / sd;
        x = normPDF(x);
        x = (max_norm_prob - x)/max_norm_prob;
        x *= 100;
        return x;
    }

    // Calculates probability of a point corresponding to the standard normal distribution
    // Input: point on normal distribution
    // Output: probability of given point occurring
    private double normPDF(double x) {
        return Math.exp(-x*x / 2) / (Math.sqrt(2 * Math.PI));
    }

    // Calculates mean of array
    // Input: array of doubles
    // Output: mean of array
    private double calcMean(double[] arr) {
        double mean = 0;
        for (double resid : arr) {
            mean += resid;
        }
        mean /= arr.length;
        return mean;
    }

    // Calculates standard deviation of array
    // Input: array of doubles
    // Output: standard deviation of array
    private double calcSD(double[] arr, double mean) {
        double sd = 0;
        for (double resid : arr) {
            sd += Math.pow(resid - mean, 2);
        }
        sd = Math.sqrt(sd/arr.length);
        return sd;
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.ANOMALY_STL.name();
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

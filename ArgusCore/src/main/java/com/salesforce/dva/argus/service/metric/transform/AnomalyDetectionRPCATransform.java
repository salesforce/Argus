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

import sun.tools.java.Scanner;

import java.util.*;

/**
 * Anomaly detection based on the Robust Principal Component Analysis (RPCA)
 * technique developed by Netflix. Particularly well-suited for detecting
 * anomalies in seasonal data.
 *
 * Sources:
 * (i) Article: http://techblog.netflix.com/2015/02/rad-outlier-detection-on-big-data.html
 * (ii) Source code: https://github.com/Netflix/Surus
 *
 * Ex: ANOMALY_RPCA(-100d:-0d:foo:bar:sum, $7d), where $7d indicates that the length of
 * a season of the metric is 7 days.
 *
 * @author  Shouvik Mani & Vignesh Muruganantham
 */
public class AnomalyDetectionRPCATransform extends AnomalyDetectionTransform {

    private int frequency;
    private Long[] timestamps;
    private double[] metricValues;
    private RPCA rpca;
    private static final String RESULT_METRIC_NAME = "RPCA anomaly score";

    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform null metric/metrics");
        SystemAssert.requireState(metrics.size() == 1, "Anomaly Detection Transform can only be used on one metric.");
        SystemAssert.requireState(metrics.get(0) != null, "Anomaly Detection Transform cannot be used with a null " +
                "metric.");
        SystemAssert.requireState(constants.size() == 1, "Anomaly Detection RPCA Transform can only be used with " +
                "one constant for the length of a season");

        //Create a sorted array of the metric's timestamps
        Map<Long, Double> completeDatapoints = metrics.get(0).getDatapoints();
        SystemAssert.requireState(completeDatapoints.size() != 0, "Cannot transform metric with no data points.");
        SystemAssert.requireState(completeDatapoints.size() > 1, "Cannot transform metric with only one data point.");
        timestamps = completeDatapoints.keySet().toArray(new Long[completeDatapoints.size()]);
        Arrays.sort(timestamps);

        String seasonLengthInput = constants.get(0);
        long seasonLengthInMilliseconds = super.getTimePeriodInSeconds(seasonLengthInput) * 1000;
        frequency = calculateFrequency(seasonLengthInMilliseconds);

        //Array of the metric's standardized values ordered by time
        metricValues = new double[completeDatapoints.size()];
        for (int i = 0; i < metricValues.length; i++) {
            Double value = completeDatapoints.get(timestamps[i]);
            metricValues[i] = value;
        }
        standardize(metricValues);

        trainModel();
        Metric predictions = predictAnomalies();
        Metric predictionsNormalized = normalizePredictions(predictions);

        List<Metric> resultMetrics = new ArrayList<>();
        resultMetrics.add(predictionsNormalized);
        return resultMetrics;
    }

    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners, List<String> constants) {
    	SystemAssert.requireArgument(scanners != null, "Cannot transform null metric/metrics");
        SystemAssert.requireState(scanners.size() == 1, "Anomaly Detection Transform can only be used on one metric.");
        SystemAssert.requireState(scanners.get(0) != null, "Anomaly Detection Transform cannot be used with a null " +
                "metric.");
        SystemAssert.requireState(constants.size() == 1, "Anomaly Detection RPCA Transform can only be used with " +
                "one constant for the length of a season");
        
        List<Long> times = new ArrayList<>();
        SystemAssert.requireState(scanners.get(0).hasNextDP(), "Cannot transform metric scanner with no data point.");
        
        String seasonLengthInput = constants.get(0);
        long seasonLengthInMilliseconds = super.getTimePeriodInSeconds(seasonLengthInput) * 1000;
        
        List<Double> metricVals = new ArrayList<>();
        
        while (scanners.get(0).hasNextDP()) {
        	Map.Entry<Long, Double> dp = scanners.get(0).getNextDP();
        	times.add(dp.getKey());
        	metricVals.add(dp.getValue());
        }
        
        timestamps = times.toArray(new Long[times.size()]);
        SystemAssert.requireState(timestamps.length > 1, "Cannot transform metric scanner with one or fewer data points.");
        frequency = calculateFrequency(seasonLengthInMilliseconds);
        metricValues = Doubles.toArray(metricVals);
        standardize(metricValues);
        
        trainModel();
        Metric predictions = predictAnomalies();
        Metric predictionsNormalized = normalizePredictions(predictions);
        
        List<Metric> resultMetrics = new ArrayList<>();
        resultMetrics.add(predictionsNormalized);
        return resultMetrics;
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanners, List<String> constants, Long start, Long end) {
    	SystemAssert.requireArgument(scanners != null, "Cannot transform null metric/metrics");
        SystemAssert.requireState(scanners.size() == 1, "Anomaly Detection Transform can only be used on one metric.");
        SystemAssert.requireState(scanners.get(0) != null, "Anomaly Detection Transform cannot be used with a null " +
                "metric.");
        SystemAssert.requireState(constants.size() == 1, "Anomaly Detection RPCA Transform can only be used with " +
                "one constant for the length of a season");
        
        SystemAssert.requireState(!scanners.get(0).getMetric().getDatapoints().isEmpty() || scanners.get(0).hasNextDP(),
        		"Cannot transform metric scanner with no data point.");
        
        Map.Entry<Long, Double> next = scanners.get(0).peek();
        List<Metric> res = new ArrayList<>();
        if (next == null) {
        	/* scanner is fully explored */
        	List<Metric> mList = new ArrayList<>();
        	mList.add(scanners.get(0).getMetric());
        	res = transform(mList, constants);
        } else if (next.getKey().equals(Collections.min(scanners.get(0).getMetric().getDatapoints().keySet()))) {
        	/* scanner is not explored at all */
        	res = transformScanner(scanners, constants);
        } else {
        	/* partially explored */
        	TreeMap<Long, Double> metricData = new TreeMap<>(scanners.get(0).getMetric().getDatapoints());
        	List<Long> times = new ArrayList<>();
        	
        	String seasonLengthInput = constants.get(0);
        	long seasonLengthInMilliseconds = super.getTimePeriodInSeconds(seasonLengthInput) * 1000;
        	
        	Long startKey = metricData.ceilingKey(start);
        	Long endKey = metricData.floorKey(next.getKey());
        	if (startKey != null && endKey != null && startKey < endKey) {
        		Map<Long, Double> dps = metricData.subMap(startKey, endKey);
        		times.addAll(dps.keySet());
        	}
        	
        	while (scanners.get(0).hasNextDP()) {
        		Map.Entry<Long, Double> dp = scanners.get(0).getNextDP();
        		times.add(dp.getKey());
        		metricData.put(dp.getKey(), dp.getValue());
        	}
        	
        	timestamps = times.toArray(new Long[times.size()]);
            SystemAssert.requireState(timestamps.length > 1, "Cannot transform metric scanner with one or fewer datapoints");
        	frequency = calculateFrequency(seasonLengthInMilliseconds);
        	metricValues = Doubles.toArray(metricData.values());
        	standardize(metricValues);
        	
        	trainModel();
        	Metric predictions = predictAnomalies();
        	Metric predictionsNormalized = normalizePredictions(predictions);
        	res.add(predictionsNormalized);
        }
        
    	TreeMap<Long, Double> finalize = new TreeMap<>(res.get(0).getDatapoints());
    	Long startKey = finalize.ceilingKey(start);
    	Long endKey = finalize.floorKey(end);
    	if (startKey == null || endKey == null || startKey > endKey) {
    		res.get(0).setDatapoints(new HashMap<>());
    		return res;
    	}
    	Map<Long, Double> range = finalize.subMap(startKey, endKey + 1);
    	res.get(0).setDatapoints(range);
    	return res;
    }
    
    @Override
    public List<Metric> transform(List<Metric> metrics) {
        throw new UnsupportedOperationException("RPCA transform requires a constant for the length of a season.");
    }
    
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanner) {
    	throw new UnsupportedOperationException("RPCA transform requires a constant for the length of a season.");
    }
    
    @Override
    public List<Metric> transformToPager(List<MetricScanner> scanner, Long start, Long end) {
    	throw new UnsupportedOperationException("RPCA transform requires a constant for the length of a season.");
    }
    

    /*
     * Calculates the frequency of the metric (the number of data
     * points in one season of the metric)
     */
    private int calculateFrequency(long seasonLength) {
        long startTime = timestamps[0];
        //Prevents frequency from being 0
        if (timestamps[1] - timestamps[0] > seasonLength) {
            throw new RuntimeException("Provided length of season is too small. No data points exist " +
                    "within the first season of the metric.");
        }

        int numDatapointsInSeason = 0;
        for (long timestamp : timestamps) {
            if ((timestamp - startTime) < seasonLength) {
                numDatapointsInSeason++;
            } else {
                break;
            }
        }

        return numDatapointsInSeason;
    }

    private void trainModel() {
        //Frequency cannot be zero here, checked in calculateFrequency()
        int numSeasons =  metricValues.length/frequency;
        double[][] metricDataMatrix = vectorToMatrix(metricValues, frequency, numSeasons);
        rpca = new RPCA(metricDataMatrix, 1, 1.4/3);
    }

    /*
     * Assigns an anomaly score to each data point, indicating how likely it is
     * to be an anomaly relative to other points and seasonality factors.
     */
    private Metric predictAnomalies() {
        Metric predictions = new Metric(getResultScopeName(), getResultMetricName());
        Map<Long, Double> predictionDatapoints = new HashMap<>();

        double[][] noiseMatrix = rpca.getE().getData();
        double[] noiseVector = matrixToVector(noiseMatrix);

        for (int i = 0; i < noiseVector.length; i++) {
            Long timestamp = timestamps[i];
            double noiseValue = noiseVector[i];
            double anomalyScore = calculateAnomalyScore(noiseValue);
            predictionDatapoints.put(timestamp, anomalyScore);
        }

        predictions.setDatapoints(predictionDatapoints);
        return predictions;
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.ANOMALY_RPCA.name();
    }

    @Override
    public String getResultMetricName() {
        return RESULT_METRIC_NAME;
    }

    @Override
    public double calculateAnomalyScore(double value) {
        return Math.abs(value);
    }

    /*
     * Standardizes the values to have zero mean and unit variance.
     * Formula: standardizedValue = (rawValue - mean) / standardDeviation
     */
    private void standardize(double[] values) {
        double mean = calculateMean(values);
        double stdDev = calculateStandardDeviation(values, mean);

        /*
         * If standard deviation is 0, all values of the metric are
         * identical. Return vector of 0s.
         */
        if (stdDev == 0.0) {
            for (int i = 0; i < values.length; i++) {
                values[i] = 0.0;
            }
            return;
        };

        for (int i = 0; i < values.length; i++) {
            double standardizedValue = (values[i] - mean) / stdDev;
            values[i] = standardizedValue;
        }
    }

    private double calculateMean(double[] values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum/values.length;
    }

    private double calculateStandardDeviation(double[] values, double mean) {
        double squaredSum = 0.0;
        for (double value : values) {
            squaredSum += Math.pow((value - mean), 2);
        }
        double variance = squaredSum/values.length;
        return Math.sqrt(variance);
    }

    private double[][] vectorToMatrix(double[] x, int rows, int cols) {
        double[][] input2DArray = new double[rows][cols];
        for (int n = 0; n < rows * cols; n++) {
            int i = n % rows;
            int j = (int) Math.floor(n / rows);
            input2DArray[i][j] = x[n];
        }
        return input2DArray;
    }

    private double[] matrixToVector(double[][] matrix) {
        int rows = matrix.length;
        int cols = matrix[0].length;
        double[] outputVector = new double[rows * cols];
        for (int n = 0; n < outputVector.length; n++) {
            int i = n % rows;
            int j = (int) Math.floor(n / rows);
            outputVector[n] = matrix[i][j];
        }
        return outputVector;
    }

}

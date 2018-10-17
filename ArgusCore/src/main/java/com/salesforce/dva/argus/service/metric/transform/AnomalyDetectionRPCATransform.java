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
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.util.QueryContext;

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
 * @author  Shouvik Mani &amp; Vignesh Muruganantham
 */
public class AnomalyDetectionRPCATransform extends AnomalyDetectionTransform {

    private int frequency;
    private Long[] timestamps;
    private double[] metricValues;
    private RPCA rpca;
    private static final String RESULT_METRIC_NAME = "RPCA anomaly score";

    @Override
    public List<Metric> transform(QueryContext queryContext, List<Metric> metrics, List<String> constants) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform null metric/metrics");
        SystemAssert.requireState(metrics.size() == 1, "Anomaly Detection Transform can only be used on one metric.");
        SystemAssert.requireState(metrics.get(0) != null, "Anomaly Detection Transform cannot be used with a null " +
                "metric.");
        SystemAssert.requireState(constants.size() == 1, "Anomaly Detection RPCA Transform can only be used with " +
                "one constant for the length of a season");

        //Create a sorted array of the metric's timestamps
        Map<Long, Double> completeDatapoints = metrics.get(0).getDatapoints();
        SystemAssert.requireState(completeDatapoints.size() != 0, "Cannot transform metric with no data points.");
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
    public List<Metric> transform(QueryContext context, List<Metric> metrics) {
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

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
import com.salesforce.dva.argus.util.QueryContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Abstract class for Gaussian distribution based anomaly detection transforms.
 *
 * Estimates mean and variance parameters to build a model from the data. Then
 * calculates an anomaly score for each data point, indicating how likely it is
 * to be an anomaly relative to other points.
 *
 * @author  Shouvik Mani (shouvik.mani@salesforce.com)
 */
public abstract class AnomalyDetectionGaussianTransform extends AnomalyDetectionTransform {

    //Parameters for Gaussian distribution
    protected double mean;
    protected double variance;

    @Override
    public List<Metric> transform(QueryContext context, List<Metric> metrics) {
        if (metrics == null) {
            throw new MissingDataException("The metrics list cannot be null or empty while performing transforms.");
        }
        if (metrics.size() != 1) {
            throw new UnsupportedOperationException("Anomaly Detection Transform can only be used with one metric.");
        }

        Metric metric = metrics.get(0);
        Map<Long, Double> metricData = metric.getDatapoints();
        if (metricData.size() == 0) {
            throw new MissingDataException("Metric must contain data points to perform transforms.");
        }

        fitParameters(metricData);
        Metric predictions = predictAnomalies(metricData);
        Metric predictionsNormalized = normalizePredictions(predictions);

        List<Metric> resultMetrics = new ArrayList<>();
        resultMetrics.add(predictionsNormalized);
        return resultMetrics;
    }

    //Fits the mean and variance parameters to the data
    private void fitParameters(Map<Long, Double> metricData) {
        mean = getMetricMean(metricData);
        variance = getMetricVariance(metricData);
    }

    /**
     * Assigns an anomaly score to each data point, indicating how likely it is
     * to be an anomaly relative to other points.
     */
    private Metric predictAnomalies(Map<Long, Double> metricData) {
        Metric predictions = new Metric(getResultScopeName(), getResultMetricName());
        Map<Long, Double> predictionDatapoints = new HashMap<>();

        if (variance == 0.0) {
            /**
             * If variance is 0, there are no anomalies.
             * Also, using 0 for variance would cause divide by zero operations
             * in Gaussian anomaly formulas. This condition avoids such operations.
             */
            for (Entry<Long, Double> entry : metricData.entrySet()) {
                Long timestamp = entry.getKey();
                predictionDatapoints.put(timestamp, 0.0);
            }
        } else {
            for (Entry<Long, Double> entry : metricData.entrySet()) {
                Long timestamp = entry.getKey();
                double value = entry.getValue();
                try {
                    double anomalyScore = calculateAnomalyScore(value);
                    predictionDatapoints.put(timestamp, anomalyScore);
                } catch (ArithmeticException e) {
                    continue;
                }
            }
        }

        predictions.setDatapoints(predictionDatapoints);
        return predictions;
    }

    private double getMetricMean(Map<Long, Double> metricData) {
        double sum = 0;
        for (Double value : metricData.values()) {
            sum += value;
        }
        return sum/metricData.size();
    }

    private double getMetricVariance(Map<Long, Double> metricData) {
        double sumSquareDiff = 0;
        for (Double value : metricData.values()) {
            sumSquareDiff += Math.pow((value - mean), 2);
        }
        return sumSquareDiff/metricData.size();
    }

}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

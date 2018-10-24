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
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.util.QueryContext;

import java.util.*;
import java.util.Map.Entry;

/**
 * Abstract class for anomaly detection transforms
 *
 * @author  Shouvik Mani (shouvik.mani@salesforce.com)
 */
public abstract class AnomalyDetectionTransform implements Transform {

    @Override
    /**
     * This implementation of transform() handles contextual anomaly detection, which
     * identifies anomalies within pre-defined intervals of the metric
     *
     * Ex: ANOMALY_DENSITY(-100d:-0d:foo:bar:sum, $7d), where the interval is 7 days
     */
    public List<Metric> transform(QueryContext queryContext, List<Metric> metrics, List<String> constants) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform null or empty metrics");
        SystemAssert.requireArgument(metrics.size() == 1, "Anomaly Detection Transform can only be used with one metric.");

        Metric predictions = new Metric(getResultScopeName(), getResultMetricName());
        Map<Long, Double> predictionDatapoints = new HashMap<>();

        long detectionIntervalInSeconds = getTimePeriodInSeconds(constants.get(0));

        //Create a sorted array of the metric's timestamps
        Map<Long, Double> completeDatapoints = metrics.get(0).getDatapoints();
        Long[] timestamps = completeDatapoints.keySet().toArray(new Long[completeDatapoints.size()]);
        Arrays.sort(timestamps);

        int currentIndex = 0;
        currentIndex = advanceCurrentIndexByInterval(currentIndex, predictionDatapoints,
                        timestamps, detectionIntervalInSeconds);
        calculateContextualAnomalyScores(predictionDatapoints, completeDatapoints, timestamps,
                currentIndex, detectionIntervalInSeconds);

        predictions.setDatapoints(predictionDatapoints);
        List<Metric> resultMetrics = new ArrayList<>();
        resultMetrics.add(predictions);
        return resultMetrics;
    }

    @Override
    public List<Metric> transform(QueryContext queryContext, List<Metric>... metrics) {
        throw new UnsupportedOperationException("This transform only supports anomaly detection on a single list of metrics");
    }

    public long getTimePeriodInSeconds(String timePeriod) {
        try {
            //Parse constant for time period
            int timeUnitIndex = timePeriod.length() - 1;
            String timeValueString = timePeriod.substring(0, timeUnitIndex);
            String timeUnitString = timePeriod.substring(timeUnitIndex);
            Long timeValue = Long.parseLong(timeValueString);
            MetricReader.TimeUnit timeUnit = MetricReader.TimeUnit.fromString(timeUnitString);
            //Convert time period to seconds
            long timePeriodInSeconds = timeValue * timeUnit.getValue() / 1000;
            return timePeriodInSeconds;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid expression for time period constant.");
        }
    }

    /**
     * Normalize predictions into a range of [0, 100]. Lowest value will map
     * to 0, highest value will map to 100, rest will map to values in between.
     * Normalization formula: normalizedValue = (rawValue - min) * (100 / (max - min))
     *
     * @param predictions Metric to normalize
     * @return Normalized metric
     */
    public Metric normalizePredictions(Metric predictions) {
        Map<Long, Double> metricData = predictions.getDatapoints();
        Map<String, Double> minMax = getMinMax(metricData);
        double min = minMax.get("min");
        double max = minMax.get("max");

        Metric predictionsNormalized = new Metric(getResultScopeName(), getResultMetricName());
        Map<Long, Double> metricDataNormalized = new HashMap<>();

        if (max - min == 0.0) {
            /**
             * If (max - min) == 0.0, all data points in the predictions metric
             * have the same value. So, all data points in the normalized metric
             * will have value 0. This avoids divide by zero operations later on.
             */
            for (Long timestamp : metricData.keySet()) {
                metricDataNormalized.put(timestamp, 0.0);
            }
        } else {
            double normalizationConstant = 100.0 / (max - min);

            for (Entry<Long, Double> entry : metricData.entrySet()) {
                Long timestamp = entry.getKey();
                Double value = entry.getValue();

                // Formula: normalizedValue = (rawValue - min) * (100 / (max - min))
                Double valueNormalized = (value - min) * normalizationConstant;
                metricDataNormalized.put(timestamp, valueNormalized);
            }
        }

        predictionsNormalized.setDatapoints(metricDataNormalized);
        return predictionsNormalized;
    }

    /**
     * Identifies the min and max values of a metric
     *
     * @param metricData Metric to find the min and max values of
     * @return Map containing the min and max values of the metric
     */
    private Map<String, Double> getMinMax(Map<Long, Double> metricData) {
        double min = 0.0;
        double max = 0.0;
        boolean isMinMaxSet = false;
        for (Double value : metricData.values()) {
            double valueDouble = value;
            if (!isMinMaxSet) {
                min = valueDouble;
                max = valueDouble;
                isMinMaxSet = true;
            } else {
                if (valueDouble < min) {
                    min = valueDouble;
                } else if (valueDouble > max) {
                    max = valueDouble;
                }
            }
        }

        Map<String, Double> minMax = new HashMap<>();
        minMax.put("min", min);
        minMax.put("max", max);
        return minMax;
    }

    /**
     * Advances currentIndex to a point where it is one anomaly detection
     * interval beyond the first timestamp. Sets the anomaly scores for
     * these intermediate points to 0 in the predictions metric (since
     * there is not enough data in its past for a complete interval).
     *
     * @param currentIndex index that gets advanced to one anomaly detection interval
     *                     beyond the first timestamp
     * @param predictionDatapoints datapoints that get filled with anomaly scores of 0
     * @param timestamps sorted timestamps of the original metric
     * @param detectionIntervalInSeconds anomaly detection interval
     * @return new advanced value of currentIndex
     */
    private int advanceCurrentIndexByInterval(int currentIndex, Map<Long, Double> predictionDatapoints,
                                               Long[] timestamps, long detectionIntervalInSeconds) {
        //Projected end of interval
        long firstIntervalEndTime = timestamps[0] + detectionIntervalInSeconds;
        while (true) {
            if (currentIndex >= timestamps.length || timestamps[currentIndex] > firstIntervalEndTime) {
                //Stop once the interval ends (or the entire metric is exhausted)
                break;
            } else {
                predictionDatapoints.put(timestamps[currentIndex], 0.0);
                currentIndex += 1;
            }
        }
        return currentIndex;
    }

    /**
     * Creates an interval for each data point (after currentIndex) in the metric
     * and calculates the anomaly score for that data point using only other data
     * points in that same interval, i.e. "a moving contextual anomaly score"
     *
     * @param predictionDatapoints data points to fill with contextual anomaly scores
     * @param completeDatapoints original metric data points
     * @param timestamps sorted timestamps of the original metric
     * @param currentIndex index at which to start contextual anomaly detection
     * @param detectionIntervalInSeconds anomaly detection interval
     */
    private void calculateContextualAnomalyScores(Map<Long, Double> predictionDatapoints,
                                                  Map<Long, Double> completeDatapoints,
                                                  Long[] timestamps,
                                                  int currentIndex, long detectionIntervalInSeconds) {
        for (int i = currentIndex; i < timestamps.length; i++) {
            long timestampAtCurrentIndex = timestamps[i];
            long projectedIntervalStartTime = timestampAtCurrentIndex - detectionIntervalInSeconds;

            Metric intervalMetric = createIntervalMetric(i, completeDatapoints, timestamps,
                                        projectedIntervalStartTime);
            List<Metric> intervalRawDataMetrics = new ArrayList<>();
            intervalRawDataMetrics.add(intervalMetric);

            //Apply the anomaly detection transform to each interval separately
            Metric intervalAnomaliesMetric = transform(null, intervalRawDataMetrics).get(0);
            Map<Long, Double> intervalAnomaliesMetricData = intervalAnomaliesMetric.getDatapoints();
            predictionDatapoints.put(timestamps[i],
                    intervalAnomaliesMetricData.get(timestamps[i]));
        }
    }

    /**
     * Creates an interval metric containing data points from a starting point
     * (defined by the detection interval) to an ending point (currentDatapointIndex)
     *
     * @param currentDatapointIndex index of the current data point to create an interval for,
     *                              will serve as the ending point of the interval
     * @param completeDatapoints original metric data points
     * @param timestamps sorted timestamps of the original metric
     * @param projectedIntervalStartTime starting point of the interval
     * @return Metric containing data points for the interval
     */
    private Metric createIntervalMetric(int currentDatapointIndex, Map<Long, Double> completeDatapoints,
                                        Long[] timestamps, long projectedIntervalStartTime) {
        Metric intervalMetric = new Metric(getResultScopeName(), getResultMetricName());
        Map<Long, Double> intervalMetricData = new HashMap<>();

        //Decrease intervalStartIndex until it's at the start of the interval
        int intervalStartIndex = currentDatapointIndex;
        while (intervalStartIndex >= 0 && timestamps[intervalStartIndex] >= projectedIntervalStartTime) {
            long tempTimestamp = timestamps[intervalStartIndex];
            //Fill in the intervalMetricData as we traverse backwards through the interval
            intervalMetricData.put(tempTimestamp, completeDatapoints.get(tempTimestamp));
            intervalStartIndex--;
        }

        intervalMetric.setDatapoints(intervalMetricData);
        return intervalMetric;
    }

    @Override
    abstract public List<Metric> transform(QueryContext context, List<Metric> metrics);

    @Override
    abstract public String getResultScopeName();

    abstract public String getResultMetricName();

    abstract public double calculateAnomalyScore(double value);
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

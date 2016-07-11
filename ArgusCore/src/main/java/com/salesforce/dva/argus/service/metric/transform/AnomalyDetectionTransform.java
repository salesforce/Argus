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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Abstract class for anomaly detection transforms
 *
 * @author  Shouvik Mani (shouvik.mani@salesforce.com)
 */
public abstract class AnomalyDetectionTransform implements Transform {
    @Override
    abstract public List<Metric> transform(List<Metric> metrics);

    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
        throw new UnsupportedOperationException("Anomaly Detection Transform is not supposed to be used with constants");
    }

    @Override
    public List<Metric> transform(List<Metric>... metrics) {
        throw new UnsupportedOperationException("This transform only supports anomaly detection on a single list of metrics");
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
        Map<Long, String> metricData = predictions.getDatapoints();
        Map<String, Double> minMax = getMinMax(metricData);
        double min = minMax.get("min");
        double max = minMax.get("max");

        Metric predictionsNormalized = new Metric(getResultScopeName(), getResultMetricName());
        Map<Long, String> metricDataNormalized = new HashMap<>();

        if (max - min == 0.0) {
            /**
             * If (max - min) == 0.0, all data points in the predictions metric
             * have the same value. So, all data points in the normalized metric
             * will have value 0. This avoids divide by zero operations later on.
             */
            for (Long timestamp : metricData.keySet()) {
                metricDataNormalized.put(timestamp, "0.0");
            }
        } else {
            double normalizationConstant = 100.0 / (max - min);

            for (Entry<Long, String> entry : metricData.entrySet()) {
                Long timestamp = entry.getKey();
                Double value = Double.parseDouble(entry.getValue());

                // Formula: normalizedValue = (rawValue - min) * (100 / (max - min))
                Double valueNormalized = (value - min) * normalizationConstant;
                metricDataNormalized.put(timestamp, String.valueOf(valueNormalized));
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
    private Map<String, Double> getMinMax(Map<Long, String> metricData) {
        double min = 0.0;
        double max = 0.0;
        boolean isMinMaxSet = false;
        for (String valueString : metricData.values()) {
            double valueDouble = Double.parseDouble(valueString);
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

    @Override
    abstract public String getResultScopeName();

    abstract public String getResultMetricName();

    abstract public double calculateAnomalyScore(double value);
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

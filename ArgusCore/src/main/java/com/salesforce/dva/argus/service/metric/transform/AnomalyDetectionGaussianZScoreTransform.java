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

/**
 * Gaussian-based anomaly detection using z-score calculation.
 * Source: http://trevorwhitney.com/data_mining/anomaly_detection
 *
 * @author  Shouvik Mani (shouvik.mani@salesforce.com)
 */
public class AnomalyDetectionGaussianZScoreTransform extends AnomalyDetectionGaussianTransform {

    private static final String RESULT_METRIC_NAME = "z-score (abs value)";

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.ANOMALY_ZSCORE.name();
    }

    @Override
    public String getResultMetricName() {
        return RESULT_METRIC_NAME;
    }

    /**
     * Calculates the z-score of the data point, which measures how many
     * standard deviations the data point is away from the mean.
     *
     * @param value the value of the data point
     * @return the absolute value of the z-score of the data point
     */
    @Override
    public double calculateAnomalyScore(double value) {
        double zScore = (value - mean) / Math.sqrt(variance);
        //Taking absolute value for a more human-readable anomaly score
        return Math.abs(zScore);
    }

}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

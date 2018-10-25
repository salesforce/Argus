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
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

public class AnomalyDetectionGaussianDensityTransformTest {

    private static final String TEST_SCOPE = "test-scope";
    private static final String TEST_METRIC = "test-metric";
    private Transform gaussianDensityTransform;
    private List<Metric> metrics;
    private Metric metric;
    private Map<Long, Double> metricData;
    private Map<Long, Double> expected;

    @Before
    public void setup() {
        gaussianDensityTransform = new AnomalyDetectionGaussianDensityTransform();
        metrics = new ArrayList<>();
        metric = new Metric(TEST_SCOPE, TEST_METRIC);
        metricData = new HashMap<>();
        expected = new HashMap<>();
    }

    @Test
    public void gaussianDensityTransformSimpleTest1() {
        metricData.put(1000L, 5.0);
        metricData.put(2000L, 10.0);
        metricData.put(3000L, 15.0);
        metric.setDatapoints(metricData);
        metrics.add(metric);

        List<Metric> results = gaussianDensityTransform.transform(null, metrics);
        Map<Long, Double> resultDatapoints = results.get(0).getDatapoints();

        expected.put(1000L, 99.99);
        expected.put(2000L, 0.0);
        expected.put(3000L, 99.99);

        for (Long timestamp : expected.keySet()) {
            assertEquals(expected.get(timestamp), resultDatapoints.get(timestamp), 0.01);
        }
    }

    @Test
    public void gaussianDensityTransformSimpleTest2() {
        metricData.put(1000L, 84.0);
        metricData.put(2000L, 21.0);
        metricData.put(3000L, 904.0);
        metricData.put(4000L, 485.0);
        metricData.put(5000L, 38.0);
        metricData.put(6000L, 85408.0);
        metricData.put(7000L, 283497.0);
        metricData.put(8000L, 43.0);
        metric.setDatapoints(metricData);
        metrics.add(metric);

        List<Metric> results = gaussianDensityTransform.transform(null, metrics);
        Map<Long, Double> resultDatapoints = results.get(0).getDatapoints();

        expected.put(1000L, 1.11);
        expected.put(2000L, 1.12);
        expected.put(3000L, 0.97);
        expected.put(4000L, 1.04);
        expected.put(5000L, 1.11);
        expected.put(6000L, 0.0);
        expected.put(7000L, 100.0);
        expected.put(8000L, 1.12);

        for (Long timestamp : expected.keySet()) {
            assertEquals(expected.get(timestamp), resultDatapoints.get(timestamp), 0.01);
        }
    }

    @Test
    public void gaussianDensityTransformSimpleTest3() {
        metricData.put(1000L, 0.0);
        metricData.put(2000L, 8.0);
        metricData.put(3000L, -98.0);
        metricData.put(4000L, 400.0);
        metricData.put(5000L, -268.0);
        metricData.put(6000L, -900.0);
        metricData.put(7000L, 68.0);
        metricData.put(8000L, 300.0);
        metricData.put(9000L, -12.0);
        metricData.put(10000L, 314.0);
        metric.setDatapoints(metricData);
        metrics.add(metric);

        List<Metric> results = gaussianDensityTransform.transform(null, metrics);
        Map<Long, Double> resultDatapoints = results.get(0).getDatapoints();

        expected.put(1000L, 0.03);
        expected.put(2000L, 0.08);
        expected.put(3000L, 0.80);
        expected.put(4000L, 22.58);
        expected.put(5000L, 7.99);
        expected.put(6000L, 100.0);
        expected.put(7000L, 0.96);
        expected.put(8000L, 13.08);
        expected.put(9000L, 0.0);
        expected.put(10000L, 14.25);

        for (Long timestamp : expected.keySet()) {
            assertEquals(expected.get(timestamp), resultDatapoints.get(timestamp), 0.01);
        }
    }

    @Test
    public void gaussianDensityTransformWithDetectionIntervalTest1() {
        metricData.put(2L, -1.20);
        metricData.put(4L, -1.64);
        metricData.put(6L, -1.68);
        metricData.put(8L, -0.46);
        metricData.put(10L, -1.21);
        metricData.put(12L, -0.29);
        metricData.put(14L, 0.32);
        metricData.put(16L, 0.35);
        metricData.put(18L, -2.26);
        metricData.put(20L, -1.41);
        metricData.put(22L, 0.47);
        metric.setDatapoints(metricData);
        metrics.add(metric);

        List<String> constants = new ArrayList<>();
        String detectionInterval = "10s";
        constants.add(detectionInterval);

        List<Metric> results = gaussianDensityTransform.transform(null, metrics, constants);
        Map<Long, Double> resultDatapoints = results.get(0).getDatapoints();

        expected.put(2L, 0.0);
        expected.put(4L, 0.0);
        expected.put(6L, 0.0);
        expected.put(8L, 0.0);
        expected.put(10L, 0.0);
        expected.put(12L, 0.0);
        expected.put(14L, 100.0);
        expected.put(16L, 50.80);
        expected.put(18L, 100.0);
        expected.put(20L, 10.82);
        expected.put(22L, 26.83);

        for (Long timestamp : expected.keySet()) {
            assertEquals(expected.get(timestamp), resultDatapoints.get(timestamp), 0.01);
        }
    }

    @Test
    public void gaussianDensityTransformWithDetectionIntervalTest2() {
        metricData.put(0L, 0.35);
        metricData.put(10800L, -0.16);
        metricData.put(21600L, 1.82);
        metricData.put(32400L, -0.37);
        metricData.put(43200L, -2.16);
        metricData.put(54000L, -0.05);
        metricData.put(64800L, -1.76);
        metricData.put(75600L, 2.13);
        metricData.put(86400L, 0.18);
        metricData.put(97200L, -0.07);
        metricData.put(108000L, 0.81);
        metricData.put(118800L, 0.47);
        metricData.put(129600L, 0.60);
        metric.setDatapoints(metricData);
        metrics.add(metric);

        List<String> constants = new ArrayList<>();
        String detectionInterval = "12h";
        constants.add(detectionInterval);

        List<Metric> results = gaussianDensityTransform.transform(null, metrics, constants);
        Map<Long, Double> resultDatapoints = results.get(0).getDatapoints();

        expected.put(0L, 0.0);
        expected.put(10800L, 0.0);
        expected.put(21600L, 0.0);
        expected.put(32400L, 0.0);
        expected.put(43200L, 0.0);
        expected.put(54000L, 0.43);
        expected.put(64800L, 28.97);
        expected.put(75600L, 99.99);
        expected.put(86400L, 3.05);
        expected.put(97200L, 0.37);
        expected.put(108000L, 7.34);
        expected.put(118800L, 2.15);
        expected.put(129600L, 16.65);

        for (Long timestamp : expected.keySet()) {
            assertEquals(expected.get(timestamp), resultDatapoints.get(timestamp), 0.01);
        }
    }

    @Test
    public void gaussianDensityTransformWithDetectionIntervalTest3() {
        metricData.put(0L, 0.64);
        metricData.put(151200L, -1.13);
        metricData.put(302400L, 0.00);
        metricData.put(453600L, 0.90);
        metricData.put(604800L, -0.96);
        metricData.put(756000L, -0.52);
        metricData.put(907200L, 0.24);
        metricData.put(1058400L, -0.01);
        metricData.put(1209600L, 0.53);
        metricData.put(1360800L, -0.34);
        metricData.put(1512000L, 1.11);
        metricData.put(1663200L, -0.21);
        metricData.put(1814400L, 0.54);
        metric.setDatapoints(metricData);
        metrics.add(metric);

        List<String> constants = new ArrayList<>();
        String detectionInterval = "7d";
        constants.add(detectionInterval);

        List<Metric> results = gaussianDensityTransform.transform(null, metrics, constants);
        Map<Long, Double> resultDatapoints = results.get(0).getDatapoints();

        expected.put(0L, 0.0);
        expected.put(151200L, 0.0);
        expected.put(302400L, 0.0);
        expected.put(453600L, 0.0);
        expected.put(604800L, 0.0);
        expected.put(756000L, 0.0);
        expected.put(907200L, 9.67);
        expected.put(1058400L, 0.0);
        expected.put(1209600L, 67.34);
        expected.put(1360800L, 33.82);
        expected.put(1512000L, 100.0);
        expected.put(1663200L, 17.42);
        expected.put(1814400L, 0.72);

        for (Long timestamp : expected.keySet()) {
            assertEquals(expected.get(timestamp), resultDatapoints.get(timestamp), 0.01);
        }
    }

    @Test
    /**
     * Edge Case: When the detection interval is greater than the time range
     * of the metric, the transform should return 0 for the anomaly score of
     * every point (since there is not enough data to learn from)
     */
    public void gaussianDensityTransformWithDetectionIntervalTest4() {
        metricData.put(0L, 10.0);
        metricData.put(1000L, -1.13);
        metricData.put(2000L, 0.00);
        metricData.put(3000L, 0.90);
        metricData.put(4000L, -0.96);
        metricData.put(5000L, -0.52);
        metricData.put(6000L, 0.24);
        metricData.put(7000L, -0.01);
        metricData.put(8000L, 0.53);
        metricData.put(9000L, -0.34);
        metricData.put(10000L, 1.11);
        metricData.put(11000L, -0.21);
        metricData.put(12000L, 0.54);
        metric.setDatapoints(metricData);
        metrics.add(metric);

        List<String> constants = new ArrayList<>();
        String detectionInterval = "100d";  //Detection interval > time range of metricData
        constants.add(detectionInterval);

        List<Metric> results = gaussianDensityTransform.transform(null, metrics, constants);
        Map<Long, Double> resultDatapoints = results.get(0).getDatapoints();

        expected.put(0L, 0.0);
        expected.put(1000L, 0.0);
        expected.put(2000L, 0.0);
        expected.put(3000L, 0.0);
        expected.put(4000L, 0.0);
        expected.put(5000L, 0.0);
        expected.put(6000L, 0.0);
        expected.put(7000L, 0.0);
        expected.put(8000L, 0.0);
        expected.put(9000L, 0.0);
        expected.put(10000L, 0.0);
        expected.put(11000L, 0.0);
        expected.put(12000L, 0.0);

        for (Long timestamp : expected.keySet()) {
            assertEquals(expected.get(timestamp), resultDatapoints.get(timestamp), 0.01);
        }
    }

    @Test
    //If variance is 0, none of the points should be anomalies
    public void gaussianDensityTransformWithZeroVarianceTest() {
        //These points have 0 variance
        metricData.put(1000L, 100.0);
        metricData.put(2000L, 100.0);
        metricData.put(3000L, 100.0);
        metricData.put(4000L, 100.0);
        metricData.put(5000L, 100.0);
        metric.setDatapoints(metricData);
        metrics.add(metric);

        List<Metric> results = gaussianDensityTransform.transform(null, metrics);
        Map<Long, Double> resultDatapoints = results.get(0).getDatapoints();

        expected.put(1000L, 0.0);
        expected.put(2000L, 0.0);
        expected.put(3000L, 0.0);
        expected.put(4000L, 0.0);
        expected.put(5000L, 0.0);

        for (Long timestamp : expected.keySet()) {
            assertEquals(expected.get(timestamp), resultDatapoints.get(timestamp), 0.01);
        }
    }

    @Test
    /**
     * Large variances in data causes floating point underflow during
     * the probability density calculation. Since underflow results in 0.0
     * and we cannot take the negative log of 0.0, data points that cause
     * underflow are omitted from the anomaly score results.
     *
     */
    public void gaussianDensityTransformWithLogOfZeroTest() {
        for (long i = 1; i < 10001; i++) {
            metricData.put(i, 0.0);
        }
        //This point will get omitted in the result because
        //it will cause underflow in the calculation
        metricData.put(10001L, 9e150);
        metric.setDatapoints(metricData);
        metrics.add(metric);

        List<Metric> results = gaussianDensityTransform.transform(null, metrics);
        Map<Long, Double> resultDatapoints = results.get(0).getDatapoints();

        for (long i = 1; i < 10001; i++) {
            expected.put(i, 0.0);
            assertEquals(expected.get(i), resultDatapoints.get(i), 0.01);
        }
        //Omitted point
        expected.put(10001L, null);

        assertEquals(expected.get(10001L), resultDatapoints.get(10001L));
    }

    @Test(expected = MissingDataException.class)
    public void gaussianDensityTransformWithNoDataTest() {
        //metricData map is empty
        metric.setDatapoints(metricData);
        metrics.add(metric);

        List<Metric> results = gaussianDensityTransform.transform(null, metrics);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void gaussianDensityTransformWithTwoMetricsTest() {
        metricData.put(1000L, 1.0);
        metricData.put(2000L, 2.0);
        metricData.put(3000L, 3.0);
        metric.setDatapoints(metricData);

        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);
        Map<Long, Double> metricData_2 = new HashMap<>();
        metricData_2.put(1000L, 4.0);
        metricData_2.put(2000L, 5.0);
        metricData_2.put(3000L, 6.0);
        metric_2.setDatapoints(metricData_2);

        metrics.add(metric);
        metrics.add(metric_2);

        List<Metric> results = gaussianDensityTransform.transform(null, metrics);
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

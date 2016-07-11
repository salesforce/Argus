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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class AnomalyDetectionGaussianDensityTransformTest {

    private static final String TEST_SCOPE = "test-scope";
    private static final String TEST_METRIC = "test-metric";
    private Transform gaussianDensityTransform;
    private List<Metric> metrics;
    private Metric metric;
    private Map<Long, String> metricData;
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
        metricData.put(1000L, "5");
        metricData.put(2000L, "10");
        metricData.put(3000L, "15");
        metric.setDatapoints(metricData);
        metrics.add(metric);

        List<Metric> results = gaussianDensityTransform.transform(metrics);
        Map<Long, String> resultDatapoints = results.get(0).getDatapoints();

        expected.put(1000L, 99.99);
        expected.put(2000L, 0.0);
        expected.put(3000L, 99.99);

        assertEquals(expected.get(1000L), Double.parseDouble(resultDatapoints.get(1000L)), 0.01);
        assertEquals(expected.get(2000L), Double.parseDouble(resultDatapoints.get(2000L)), 0.01);
        assertEquals(expected.get(3000L), Double.parseDouble(resultDatapoints.get(3000L)), 0.01);
    }

    @Test
    public void gaussianDensityTransformSimpleTest2() {
        metricData.put(1000L, "84");
        metricData.put(2000L, "21");
        metricData.put(3000L, "904");
        metricData.put(4000L, "485");
        metricData.put(5000L, "38");
        metricData.put(6000L, "85408");
        metricData.put(7000L, "283497");
        metricData.put(8000L, "43");
        metric.setDatapoints(metricData);
        metrics.add(metric);

        List<Metric> results = gaussianDensityTransform.transform(metrics);
        Map<Long, String> resultDatapoints = results.get(0).getDatapoints();

        expected.put(1000L, 1.11);
        expected.put(2000L, 1.12);
        expected.put(3000L, 0.97);
        expected.put(4000L, 1.04);
        expected.put(5000L, 1.11);
        expected.put(6000L, 0.0);
        expected.put(7000L, 100.0);
        expected.put(8000L, 1.12);

        assertEquals(expected.get(1000L), Double.parseDouble(resultDatapoints.get(1000L)), 0.01);
        assertEquals(expected.get(2000L), Double.parseDouble(resultDatapoints.get(2000L)), 0.01);
        assertEquals(expected.get(3000L), Double.parseDouble(resultDatapoints.get(3000L)), 0.01);
        assertEquals(expected.get(4000L), Double.parseDouble(resultDatapoints.get(4000L)), 0.01);
        assertEquals(expected.get(5000L), Double.parseDouble(resultDatapoints.get(5000L)), 0.01);
        assertEquals(expected.get(6000L), Double.parseDouble(resultDatapoints.get(6000L)), 0.01);
        assertEquals(expected.get(7000L), Double.parseDouble(resultDatapoints.get(7000L)), 0.01);
        assertEquals(expected.get(8000L), Double.parseDouble(resultDatapoints.get(8000L)), 0.01);
    }

    @Test
    public void gaussianDensityTransformSimpleTest3() {
        metricData.put(1000L, "0");
        metricData.put(2000L, "8");
        metricData.put(3000L, "-98");
        metricData.put(4000L, "400");
        metricData.put(5000L, "-268");
        metricData.put(6000L, "-900");
        metricData.put(7000L, "68");
        metricData.put(8000L, "300");
        metricData.put(9000L, "-12");
        metricData.put(10000L, "314");
        metric.setDatapoints(metricData);
        metrics.add(metric);

        List<Metric> results = gaussianDensityTransform.transform(metrics);
        Map<Long, String> resultDatapoints = results.get(0).getDatapoints();

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

        assertEquals(expected.get(1000L), Double.parseDouble(resultDatapoints.get(1000L)), 0.01);
        assertEquals(expected.get(2000L), Double.parseDouble(resultDatapoints.get(2000L)), 0.01);
        assertEquals(expected.get(3000L), Double.parseDouble(resultDatapoints.get(3000L)), 0.01);
        assertEquals(expected.get(4000L), Double.parseDouble(resultDatapoints.get(4000L)), 0.01);
        assertEquals(expected.get(5000L), Double.parseDouble(resultDatapoints.get(5000L)), 0.01);
        assertEquals(expected.get(6000L), Double.parseDouble(resultDatapoints.get(6000L)), 0.01);
        assertEquals(expected.get(7000L), Double.parseDouble(resultDatapoints.get(7000L)), 0.01);
        assertEquals(expected.get(8000L), Double.parseDouble(resultDatapoints.get(8000L)), 0.01);
        assertEquals(expected.get(9000L), Double.parseDouble(resultDatapoints.get(9000L)), 0.01);
        assertEquals(expected.get(10000L), Double.parseDouble(resultDatapoints.get(10000L)), 0.01);
    }

    @Test
    //If variance is 0, none of the points should be anomalies
    public void gaussianDensityTransformWithZeroVarianceTest() {
        //These points have 0 variance
        metricData.put(1000L, "100");
        metricData.put(2000L, "100");
        metricData.put(3000L, "100");
        metricData.put(4000L, "100");
        metricData.put(5000L, "100");
        metric.setDatapoints(metricData);
        metrics.add(metric);

        List<Metric> results = gaussianDensityTransform.transform(metrics);
        Map<Long, String> resultDatapoints = results.get(0).getDatapoints();

        expected.put(1000L, 0.0);
        expected.put(2000L, 0.0);
        expected.put(3000L, 0.0);
        expected.put(4000L, 0.0);
        expected.put(5000L, 0.0);

        assertEquals(expected.get(1000L), Double.parseDouble(resultDatapoints.get(1000L)), 0.01);
        assertEquals(expected.get(2000L), Double.parseDouble(resultDatapoints.get(2000L)), 0.01);
        assertEquals(expected.get(3000L), Double.parseDouble(resultDatapoints.get(3000L)), 0.01);
        assertEquals(expected.get(4000L), Double.parseDouble(resultDatapoints.get(4000L)), 0.01);
        assertEquals(expected.get(5000L), Double.parseDouble(resultDatapoints.get(5000L)), 0.01);
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
            metricData.put(i, "0.0");
        }
        //This point will get omitted in the result because
        //it will cause underflow in the calculation
        metricData.put(10001L, "9e150");
        metric.setDatapoints(metricData);
        metrics.add(metric);

        List<Metric> results = gaussianDensityTransform.transform(metrics);
        Map<Long, String> resultDatapoints = results.get(0).getDatapoints();

        for (long i = 1; i < 10001; i++) {
            expected.put(i, 0.0);
            assertEquals(expected.get(i), Double.parseDouble(resultDatapoints.get(i)), 0.01);
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

        List<Metric> results = gaussianDensityTransform.transform(metrics);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void gaussianDensityTransformWithTwoMetricsTest() {
        metricData.put(1000L, "1");
        metricData.put(2000L, "2");
        metricData.put(3000L, "3");
        metric.setDatapoints(metricData);

        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);
        Map<Long, String> metricData_2 = new HashMap<>();
        metricData_2.put(1000L, "4");
        metricData_2.put(2000L, "5");
        metricData_2.put(3000L, "6");
        metric_2.setDatapoints(metricData_2);

        metrics.add(metric);
        metrics.add(metric_2);

        List<Metric> results = gaussianDensityTransform.transform(metrics);
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

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

public class AnomalyDetectionGaussianZScoreTransformTest {

    private static final String TEST_SCOPE = "test-scope";
    private static final String TEST_METRIC = "test-metric";
    private Transform gaussianZScoreTransform;
    private List<Metric> metrics;
    private Metric metric;
    private Map<Long, String> metricData;
    private Map<Long, Double> expected;

    @Before
    public void setup() {
        gaussianZScoreTransform = new AnomalyDetectionGaussianZScoreTransform();
        metrics = new ArrayList<>();
        metric = new Metric(TEST_SCOPE, TEST_METRIC);
        metricData = new HashMap<>();
        expected = new HashMap<>();
    }

    @Test
    public void gaussianZScoreTransformSimpleTest1() {
        metricData.put(1000L, "5");
        metricData.put(2000L, "10");
        metricData.put(3000L, "15");
        metric.setDatapoints(metricData);
        metrics.add(metric);

        List<Metric> results = gaussianZScoreTransform.transform(metrics);
        Map<Long, String> resultDatapoints = results.get(0).getDatapoints();

        expected.put(1000L, 1.22);
        expected.put(2000L, 0.0);
        expected.put(3000L, 1.22);

        assertEquals(expected.get(1000L), Double.parseDouble(resultDatapoints.get(1000L)), 0.01);
        assertEquals(expected.get(2000L), Double.parseDouble(resultDatapoints.get(2000L)), 0.01);
        assertEquals(expected.get(3000L), Double.parseDouble(resultDatapoints.get(3000L)), 0.01);
    }

    @Test
    public void gaussianZScoreTransformSimpleTest2() {
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

        List<Metric> results = gaussianZScoreTransform.transform(metrics);
        Map<Long, String> resultDatapoints = results.get(0).getDatapoints();

        expected.put(1000L, 0.49);
        expected.put(2000L, 0.49);
        expected.put(3000L, 0.48);
        expected.put(4000L, 0.48);
        expected.put(5000L, 0.49);
        expected.put(6000L, 0.41);
        expected.put(7000L, 2.52);
        expected.put(8000L, 0.49);

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
    //If variance is 0, none of the points should be anomalies
    public void gaussianZScoreTransformWithZeroVarianceTest() {
        //These points have 0 variance
        metricData.put(1000L, "100");
        metricData.put(2000L, "100");
        metricData.put(3000L, "100");
        metricData.put(4000L, "100");
        metricData.put(5000L, "100");
        metric.setDatapoints(metricData);
        metrics.add(metric);

        List<Metric> results = gaussianZScoreTransform.transform(metrics);
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

    @Test(expected = MissingDataException.class)
    public void gaussianZScoreTransformWithNoDataTest() {
        //metricData map is empty
        metric.setDatapoints(metricData);
        metrics.add(metric);

        List<Metric> results = gaussianZScoreTransform.transform(metrics);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void gaussianZScoreTransformWithTwoMetricsTest() {
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

        List<Metric> results = gaussianZScoreTransform.transform(metrics);
    }

}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

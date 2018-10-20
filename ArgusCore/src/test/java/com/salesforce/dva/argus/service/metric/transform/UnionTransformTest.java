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
import org.junit.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class UnionTransformTest {

    private static final String TEST_SCOPE = "test-scope";
    private static final String TEST_METRIC = "test-metric";

    @Test(expected = UnsupportedOperationException.class)
    public void testUnionTransformWithConstant() {
        Transform unionTransform = new MetricUnionTransform(new UnionValueUnionReducer());
        Map<Long, Double> datapoints = new HashMap<Long, Double>();

        datapoints.put(1000L, 1.0);

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>(1);

        unionTransform.transform(null, metrics, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSumTransformWithoutMetrics() {
        Transform unionTransform = new MetricUnionTransform(new UnionValueUnionReducer());
        List<Metric> metrics = null;

        unionTransform.transform(null, metrics);
    }

    @Test
    public void testUnionTransformWithAllSharedTimestamps() {
        Transform unionTransform = new MetricUnionTransform(new UnionValueUnionReducer());
        Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

        datapoints_1.put(1000L, 1.0);
        datapoints_1.put(2000L, 2.0);
        datapoints_1.put(3000L, 3.0);

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        Map<Long, Double> datapoints_2 = new HashMap<Long, Double>();

        datapoints_2.put(1000L, 10.0);
        datapoints_2.put(2000L, 100.0);
        datapoints_2.put(3000L, 1000.0);

        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(1000L, 1.0);
        expected.put(2000L, 2.0);
        expected.put(3000L, 3.0);

        List<Metric> result = unionTransform.transform(null, metrics);

        assertEquals(result.get(0).getDatapoints().size(), 3);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testUnionTransformWithoutSharedTimestamps() {
        Transform unionTransform = new MetricUnionTransform(new UnionValueUnionReducer());
        Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

        datapoints_1.put(1000L, 1.0);
        datapoints_1.put(2000L, 2.0);
        datapoints_1.put(3000L, 3.0);

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        Map<Long, Double> datapoints_2 = new HashMap<Long, Double>();

        datapoints_2.put(100L, 10.0);
        datapoints_2.put(200L, 100.0);
        datapoints_2.put(300L, 1000.0);

        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(1000L, 1.0);
        expected.put(2000L, 2.0);
        expected.put(3000L, 3.0);
        expected.put(100L, 10.0);
        expected.put(200L, 100.0);
        expected.put(300L, 1000.0);

        List<Metric> result = unionTransform.transform(null, metrics);

        assertEquals(result.get(0).getDatapoints().size(), 6);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testUnionTransformWithSomeSharedTimestamps() {
        Transform unionTransform = new MetricUnionTransform(new UnionValueUnionReducer());
        Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

        datapoints_1.put(1000L, 1.0);
        datapoints_1.put(2000L, 2.0);
        datapoints_1.put(3000L, 3.0);

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        Map<Long, Double> datapoints_2 = new HashMap<Long, Double>();

        datapoints_2.put(1000L, 10.0);
        datapoints_2.put(200L, 100.0);
        datapoints_2.put(300L, 1000.0);

        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(1000L, 1.0);
        expected.put(2000L, 2.0);
        expected.put(3000L, 3.0);
        expected.put(200L, 100.0);
        expected.put(300L, 1000.0);

        List<Metric> result = unionTransform.transform(null, metrics);

        assertEquals(result.get(0).getDatapoints().size(), 5);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testUnionTransformWithOnlyOneMetric() {
        Transform unionTransform = new MetricUnionTransform(new UnionValueUnionReducer());
        Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

        datapoints_1.put(1000L, 1.0);
        datapoints_1.put(2000L, 2.0);
        datapoints_1.put(3000L, 3.0);

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(1000L, 1.0);
        expected.put(2000L, 2.0);
        expected.put(3000L, 3.0);

        List<Metric> result = unionTransform.transform(null, metrics);

        assertEquals(result.get(0).getDatapoints().size(), 3);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testUnionTransformWithMissingPoints() {
        Transform unionTransform = new MetricUnionTransform(new UnionValueUnionReducer());
        Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

        datapoints_1.put(1000L, null);
        datapoints_1.put(2000L, 2.0);
        datapoints_1.put(3000L, 3.0);

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        Map<Long, Double> datapoints_2 = new HashMap<Long, Double>();

        datapoints_2.put(1000L, 10.0);
        datapoints_2.put(2000L, null);
        datapoints_2.put(300L, null);

        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(1000L, null);
        expected.put(2000L, 2.0);
        expected.put(3000L, 3.0);
        expected.put(300L, null);

        List<Metric> result = unionTransform.transform(null, metrics);

        assertEquals(result.get(0).getDatapoints().size(), 4);
        assertEquals(expected, result.get(0).getDatapoints());
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

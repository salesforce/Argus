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
import com.salesforce.dva.argus.system.SystemException;
import org.junit.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class SumTransformTest {

    private static final String TEST_SCOPE = "test-scope";
    private static final String TEST_METRIC = "test-metric";

    @Test(expected = SystemException.class)
    public void testSumTransformWithIllegalConstant() {
        Transform sumTransform = new MetricReducerOrMappingTransform(new SumValueReducerOrMapping());
        Map<Long, Double> datapoints = new HashMap<Long, Double>();

        datapoints.put(1000L, 1.0);

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>(1);

        constants.add("5w");
        sumTransform.transform(null, metrics, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSumTransformWithoutMetrics() {
        Transform sumTransform = new MetricReducerOrMappingTransform(new SumValueReducerOrMapping());
        List<Metric> metrics = null;
        List<String> constants = new ArrayList<String>(1);

        constants.add("5");
        sumTransform.transform(null, metrics, constants);
    }

    @Test
    public void testSumTransformWithOneConstantAgainstOneMetric() {
        Transform sumTransform = new MetricReducerOrMappingTransform(new SumValueReducerOrMapping());
        Map<Long, Double> datapoints = new HashMap<Long, Double>();

        datapoints.put(1000L, 1.0);
        datapoints.put(2000L, 2.0);
        datapoints.put(3000L, 3.0);

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>(1);

        constants.add("5");

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(1000L, 6.0);
        expected.put(2000L, 7.0);
        expected.put(3000L, 8.0);

        List<Metric> result = sumTransform.transform(null, metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), 3);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testSumTransformWithOneConstantAgainstMetricList() {
        Transform sumTransform = new MetricReducerOrMappingTransform(new SumValueReducerOrMapping());
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

        List<String> constants = new ArrayList<String>(1);

        constants.add("5");

        Map<Long, Double> expected_1 = new HashMap<Long, Double>();

        expected_1.put(1000L, 6.0);
        expected_1.put(2000L, 7.0);
        expected_1.put(3000L, 8.0);

        Map<Long, Double> expected_2 = new HashMap<Long, Double>();

        expected_2.put(1000L, 15.0);
        expected_2.put(2000L, 105.0);
        expected_2.put(3000L, 1005.0);

        List<Metric> result = sumTransform.transform(null, metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), 3);
        assertEquals(expected_1, result.get(0).getDatapoints());
        assertEquals(result.get(1).getDatapoints().size(), 3);
        assertEquals(expected_2, result.get(1).getDatapoints());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSumTransformWithTwoConstants() {
        Transform sumTransform = new MetricReducerOrMappingTransform(new SumValueReducerOrMapping());
        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);
        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        constants.add("5");
        constants.add("10");
        sumTransform.transform(null, metrics, constants);
    }

    @Test
    public void testSumTransformWithNoConstantShareCommonDPs() {
        Transform sumTransform = new MetricReducerOrMappingTransform(new SumValueReducerOrMapping());
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

        List<String> constants = new ArrayList<String>();
        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(1000L, 11.0);
        expected.put(2000L, 102.0);
        expected.put(3000L, 1003.0);

        List<Metric> result = sumTransform.transform(null, metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), 3);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testSumTransformWithNoConstantShareNoCommonDPs() {
        Transform sumTransform = new MetricReducerOrMappingTransform(new SumValueReducerOrMapping());
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

        List<String> constants = new ArrayList<String>();
        Map<Long, Double> expected = new HashMap<Long, Double>();
        List<Metric> result = sumTransform.transform(null, metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), 0);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testSumTransformWithNoConstantShareSomeCommonDPs() {
        Transform sumTransform = new MetricReducerOrMappingTransform(new SumValueReducerOrMapping());
        Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

        datapoints_1.put(1000L, 1.0);
        datapoints_1.put(2000L, 2.0);
        datapoints_1.put(3000L, 3.0);

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        Map<Long, Double> datapoints_2 = new HashMap<Long, Double>();

        datapoints_2.put(100L, 10.0);
        datapoints_2.put(200L, 100.0);
        datapoints_2.put(3000L, 1000.0);

        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);

        List<String> constants = new ArrayList<String>(1);
        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(3000L, 1003.0);

        List<Metric> result = sumTransform.transform(null, metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), 1);
        assertEquals(expected, result.get(0).getDatapoints());
    }
    
    @Test
    public void testSumTransformWithFulljoinConstantShareSomeCommonDPs() {
        Transform sumTransform = new MetricReducerOrMappingTransform(new SumValueReducerOrMapping());
        Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

        datapoints_1.put(1000L, 1.0);
        datapoints_1.put(2000L, 2.0);
        datapoints_1.put(3000L, 3.0);

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        Map<Long, Double> datapoints_2 = new HashMap<Long, Double>();

        datapoints_2.put(100L, 10.0);
        datapoints_2.put(200L, 100.0);
        datapoints_2.put(3000L, 1000.0);

        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);

        List<String> constants = new ArrayList<String>();
        constants.add("union");
        Map<Long, Double> expected = new HashMap<Long, Double>();
        expected.put(100L, 10.0);
        expected.put(200L, 100.0);
        expected.put(1000L, 1.0);
        expected.put(2000L, 2.0);
        expected.put(3000L, 1003.0);
        List<Metric> result = sumTransform.transform(null, metrics, constants);
        assertEquals(result.get(0).getDatapoints().size(), expected.size());
        assertEquals(expected, result.get(0).getDatapoints());
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

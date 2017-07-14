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

public class DeviationTransformTest {

    private static final String TEST_SCOPE = "test-scope";
    private static final String TEST_METRIC = "test-metric";

    @Test(expected = IllegalArgumentException.class)
    public void testDevTransformWithoutMetrics() {
        Transform devTransform = new MetricReducerOrMappingWithConstantTransform(new DeviationValueReducerOrMapping());
        List<Metric> metrics = null;
        List<String> constants = new ArrayList<String>(1);

        constants.add("5");
        devTransform.transform(metrics, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDevTransformWithoutConstants() {
        Transform devTransform = new MetricReducerOrMappingWithConstantTransform(new DeviationValueReducerOrMapping());
        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);
        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        devTransform.transform(metrics, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDevTransformWithIllegalTolerance() {
        Transform devTransform = new MetricReducerOrMappingWithConstantTransform(new DeviationValueReducerOrMapping());
        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);
        List<Metric> metrics = new ArrayList<Metric>();
        Map<Long, Double> datapoints = new HashMap<Long, Double>();

        datapoints.put(1000L, 1.0);
        datapoints.put(2000L, 2.0);
        datapoints.put(3000L, 3.0);
        metric.setDatapoints(datapoints);
        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        constants.add("-1");
        devTransform.transform(metrics, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDevTransformWithIllegalPointNum() {
        Transform devTransform = new MetricReducerOrMappingWithConstantTransform(new DeviationValueReducerOrMapping());
        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);
        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        constants.add("0.1");
        constants.add("-99");
        devTransform.transform(metrics, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSumTransformWithTwoConstants() {
        Transform devTransform = new MetricReducerOrMappingWithConstantTransform(new DeviationValueReducerOrMapping());
        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);
        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        constants.add("5");
        constants.add("10");
        devTransform.transform(metrics, constants);
    }

    @Test
    public void testDevTransformOneMetricWithNumLessThanRangeUnderTolerance() {
        Transform devTransform = new MetricReducerOrMappingWithConstantTransform(new DeviationValueReducerOrMapping());
        Map<Long, Double> datapoints = new HashMap<Long, Double>();

        datapoints.put(1000L, 6.0);
        datapoints.put(2000L, 6.0);

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        constants.add("0.99");
        constants.add("3");

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(2000L, 0.0);

        List<Metric> result = devTransform.transform(metrics, constants);

        assertEquals(result.size(), 1);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testDevTransformOneMetricWithNumEqualToRangeBeyondTolerance() {
        Transform devTransform = new MetricReducerOrMappingWithConstantTransform(new DeviationValueReducerOrMapping());
        Map<Long, Double> datapoints = new HashMap<Long, Double>();

        datapoints.put(1000L, 2.0);
        datapoints.put(2000L, 4.0);
        datapoints.put(3000L, 6.0);

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        constants.add("0.99");
        constants.add("3");

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(3000L, 2.0);

        List<Metric> result = devTransform.transform(metrics, constants);

        assertEquals(result.size(), 1);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testDevTransformOneMetricWithNumGreateThanRaneUnderTolerance() {
        Transform devTransform = new MetricReducerOrMappingWithConstantTransform(new DeviationValueReducerOrMapping());
        Map<Long, Double> datapoints = new HashMap<Long, Double>();

        datapoints.put(1000L, 3.0);
        datapoints.put(2000L, 6.0);
        datapoints.put(3000L, 9.0);
        datapoints.put(4000L, 12.0);

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        constants.add("0.99");
        constants.add("3");

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(4000L, 3.0);

        List<Metric> result = devTransform.transform(metrics, constants);

        assertEquals(result.size(), 1);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testDevTransformMetricListWithNumLessGreateEqualRangeUnderTolerance() {
        Transform devTransform = new MetricReducerOrMappingWithConstantTransform(new DeviationValueReducerOrMapping());
        Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

        datapoints_1.put(1000L, 6.0);
        datapoints_1.put(2000L, 6.0);

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        Map<Long, Double> datapoints_2 = new HashMap<Long, Double>();

        datapoints_2.put(1000L, 2.0);
        datapoints_2.put(2000L, 4.0);
        datapoints_2.put(3000L, 6.0);

        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        Map<Long, Double> datapoints_3 = new HashMap<Long, Double>();

        datapoints_3.put(1000L, 3.0);
        datapoints_3.put(2000L, 6.0);
        datapoints_3.put(3000L, 9.0);
        datapoints_3.put(4000L, 12.0);

        Metric metric_3 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_3.setDatapoints(datapoints_3);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);
        metrics.add(metric_3);

        List<String> constants = new ArrayList<String>();

        constants.add("0.99");
        constants.add("3");

        Map<Long, Double> expected_1 = new HashMap<Long, Double>();

        expected_1.put(2000L, 0.0);

        Map<Long, Double> expected_2 = new HashMap<Long, Double>();

        expected_2.put(3000L, 2.0);

        Map<Long, Double> expected_3 = new HashMap<Long, Double>();

        expected_3.put(4000L, 3.0);

        List<Metric> result = devTransform.transform(metrics, constants);

        assertEquals(result.size(), 3);
        assertEquals(expected_1, result.get(0).getDatapoints());
        assertEquals(expected_2, result.get(1).getDatapoints());
        assertEquals(expected_3, result.get(2).getDatapoints());
    }

    @Test
    public void testDevTransformMetricListWithNumLessGreateEqualRangeBeyondTolerance() {
        Transform devTransform = new MetricReducerOrMappingWithConstantTransform(new DeviationValueReducerOrMapping());
        Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

        datapoints_1.put(1000L, 6.0);
        datapoints_1.put(2000L, null);

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        Map<Long, Double> datapoints_2 = new HashMap<Long, Double>();

        datapoints_2.put(1000L, 2.0);
        datapoints_2.put(2000L, 4.0);
        datapoints_2.put(3000L, null);

        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        Map<Long, Double> datapoints_3 = new HashMap<Long, Double>();

        datapoints_3.put(1000L, 3.0);
        datapoints_3.put(2000L, 6.0);
        datapoints_3.put(3000L, 9.0);
        datapoints_3.put(4000L, null);

        Metric metric_3 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_3.setDatapoints(datapoints_3);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);
        metrics.add(metric_3);

        List<String> constants = new ArrayList<String>();

        constants.add("0.00001");
        constants.add("3");

        Map<Long, Double> expected_1 = new HashMap<Long, Double>();

        expected_1.put(2000L, null);

        Map<Long, Double> expected_2 = new HashMap<Long, Double>();

        expected_2.put(3000L, null);

        Map<Long, Double> expected_3 = new HashMap<Long, Double>();

        expected_3.put(4000L, null);

        List<Metric> result = devTransform.transform(metrics, constants);

        assertEquals(result.size(), 3);
        assertEquals(expected_1, result.get(0).getDatapoints());
        assertEquals(expected_2, result.get(1).getDatapoints());
        assertEquals(expected_3, result.get(2).getDatapoints());
    }

    @Test
    public void testDevTransformWithNoNumShareCommonDPsUnderTolerance() {
        Transform devTransform = new MetricReducerOrMappingWithConstantTransform(new DeviationValueReducerOrMapping());
        Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

        datapoints_1.put(1000L, 1.0);
        datapoints_1.put(2000L, 2.0);
        datapoints_1.put(3000L, 3.0);

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        Map<Long, Double> datapoints_2 = new HashMap<Long, Double>();

        datapoints_2.put(1000L, 2.0);
        datapoints_2.put(2000L, 4.0);
        datapoints_2.put(3000L, 6.0);

        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        Map<Long, Double> datapoints_3 = new HashMap<Long, Double>();

        datapoints_3.put(1000L, 3.0);
        datapoints_3.put(2000L, 6.0);
        datapoints_3.put(3000L, 9.0);

        Metric metric_3 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_3.setDatapoints(datapoints_3);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);
        metrics.add(metric_3);

        List<String> constants = new ArrayList<String>();

        constants.add("0.99");

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(1000L, 1.0);
        expected.put(2000L, 2.0);
        expected.put(3000L, 3.0);

        List<Metric> result = devTransform.transform(metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), 3);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testDevTransformWithNoNumShareCommonDPsBeyondTolerance() {
        Transform devTransform = new MetricReducerOrMappingWithConstantTransform(new DeviationValueReducerOrMapping());
        Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

        datapoints_1.put(1000L, 1.0);
        datapoints_1.put(2000L, 2.0);
        datapoints_1.put(3000L, null);

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        Map<Long, Double> datapoints_2 = new HashMap<Long, Double>();

        datapoints_2.put(1000L, 2.0);
        datapoints_2.put(2000L, 4.0);
        datapoints_2.put(3000L, null);

        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        Map<Long, Double> datapoints_3 = new HashMap<Long, Double>();

        datapoints_3.put(1000L, 3.0);
        datapoints_3.put(2000L, 6.0);
        datapoints_3.put(3000L, 9.0);

        Metric metric_3 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_3.setDatapoints(datapoints_3);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);
        metrics.add(metric_3);

        List<String> constants = new ArrayList<String>();

        constants.add("0.01");

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(1000L, 1.0);
        expected.put(2000L, 2.0);
        expected.put(3000L, null);

        List<Metric> result = devTransform.transform(metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), 3);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testDevTransformWithNoNumShareNoCommonDPsUnderTolerance() {
        Transform devTransform = new MetricReducerOrMappingWithConstantTransform(new DeviationValueReducerOrMapping());
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

        constants.add("0.1");

        Map<Long, Double> expected = new HashMap<Long, Double>();
        List<Metric> result = devTransform.transform(metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), 0);
        assertEquals(expected, result.get(0).getDatapoints());
    }
    
    @Test
    public void testDevTransformWithNoNumWithUNIONShareNoCommonDPsUnderTolerance() {
        Transform devTransform = new MetricReducerOrMappingWithConstantTransform(new DeviationValueReducerOrMapping());
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

        constants.add("0.1");
        constants.add("UNION");

        Map<Long, Double> expected = new HashMap<Long, Double>();
        expected.put(100L, 0.0);
        expected.put(200L, 0.0);
        expected.put(300L, 0.0);
        expected.put(1000L, 0.0);
        expected.put(2000L, 0.0);
        expected.put(3000L, 0.0);
        
        List<Metric> result = devTransform.transform(metrics, constants);

        assertEquals(6, result.get(0).getDatapoints().size());
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testDevTransformWithNoConstantShareSomeCommonDPsBeyondTolerance() {
        Transform devTransform = new MetricReducerOrMappingWithConstantTransform(new DeviationValueReducerOrMapping());
        Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

        datapoints_1.put(1000L, 1.0);
        datapoints_1.put(2000L, 2.0);
        datapoints_1.put(3000L, 2.0);
        datapoints_1.put(4000L, 3.0);

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        Map<Long, Double> datapoints_2 = new HashMap<Long, Double>();

        datapoints_2.put(100L, 1.0);
        datapoints_2.put(200L, 2.0);
        datapoints_2.put(3000L, 2.0);
        datapoints_2.put(4000L, null);

        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);

        List<String> constants = new ArrayList<String>(1);

        constants.add("0.4");

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(3000L, 0.0);
        expected.put(4000L, null);

        List<Metric> result = devTransform.transform(metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), 2);
        assertEquals(expected, result.get(0).getDatapoints());
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

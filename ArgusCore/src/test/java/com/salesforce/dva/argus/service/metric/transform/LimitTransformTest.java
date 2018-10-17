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

public class LimitTransformTest {

    private static final String TEST_SCOPE = "test-scope";
    private static final String TEST_METRIC = "test-metric";

    @Test(expected = IllegalArgumentException.class)
    public void testLimitTransformWithoutMetrics() {
        Transform limitTransform = new LimitTransform();
        List<Metric> metrics = null;
        List<String> constants = new ArrayList<String>();

        constants.add("1");
        limitTransform.transform(null, metrics, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLimitTransformWithoutConstant() {
        Transform limitTransform = new LimitTransform();
        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);
        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);

        List<String> constants = new ArrayList<String>();

        limitTransform.transform(null, metrics, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testLimitTransformWithIllegalLimitNumber() {
        Transform limitTransform = new LimitTransform();
        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);
        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);

        List<String> constants = new ArrayList<String>();

        constants.add("-1");
        limitTransform.transform(null, metrics, constants);
    }

    @Test
    public void testLimitTransformWithLimitLessThanMetricsLen() {
        Transform limitTransform = new LimitTransform();
        Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

        datapoints_1.put(1000L, 1.0);
        datapoints_1.put(2000L, 2.0);
        datapoints_1.put(3000L, 3.0);

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        Map<Long, Double> datapoints_2 = new HashMap<Long, Double>();

        datapoints_2.put(4000L, 10.0);
        datapoints_2.put(5000L, 100.0);
        datapoints_2.put(6000L, 1000.0);

        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        Map<Long, Double> datapoints_3 = new HashMap<Long, Double>();

        datapoints_3.put(7000L, 0.1);
        datapoints_3.put(8000L, 0.01);
        datapoints_3.put(9000L, 0.0001);

        Metric metric_3 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_3.setDatapoints(datapoints_3);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);
        metrics.add(metric_3);

        List<String> constants = new ArrayList<String>();

        constants.add("2");

        Map<Long, Double> expected_1 = new HashMap<Long, Double>();

        expected_1.put(1000L, 1.0);
        expected_1.put(2000L, 2.0);
        expected_1.put(3000L, 3.0);

        Map<Long, Double> expected_2 = new HashMap<Long, Double>();

        expected_2.put(4000L, 10.0);
        expected_2.put(5000L, 100.0);
        expected_2.put(6000L, 1000.0);

        List<Metric> result = limitTransform.transform(null, metrics, constants);

        assertEquals(result.size(), 2);
        assertEquals(expected_1, result.get(0).getDatapoints());
        assertEquals(expected_2, result.get(1).getDatapoints());
    }

    @Test
    public void testLimitTransformWithLimitEqualToMetricsLen() {
        Transform limitTransform = new LimitTransform();
        Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

        datapoints_1.put(1000L, 1.0);
        datapoints_1.put(2000L, 2.0);
        datapoints_1.put(3000L, 3.0);

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        Map<Long, Double> datapoints_2 = new HashMap<Long, Double>();

        datapoints_2.put(4000L, 10.0);
        datapoints_2.put(5000L, 100.0);
        datapoints_2.put(6000L, 1000.0);

        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        Map<Long, Double> datapoints_3 = new HashMap<Long, Double>();

        datapoints_3.put(7000L, 0.1);
        datapoints_3.put(8000L, 0.01);
        datapoints_3.put(9000L, 0.0001);

        Metric metric_3 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_3.setDatapoints(datapoints_3);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);
        metrics.add(metric_3);

        List<String> constants = new ArrayList<String>();

        constants.add("3");

        Map<Long, Double> expected_1 = new HashMap<Long, Double>();

        expected_1.put(1000L, 1.0);
        expected_1.put(2000L, 2.0);
        expected_1.put(3000L, 3.0);

        Map<Long, Double> expected_2 = new HashMap<Long, Double>();

        expected_2.put(4000L, 10.0);
        expected_2.put(5000L, 100.0);
        expected_2.put(6000L, 1000.0);

        Map<Long, Double> expected_3 = new HashMap<Long, Double>();

        expected_3.put(7000L, 0.1);
        expected_3.put(8000L, 0.01);
        expected_3.put(9000L, 0.0001);

        List<Metric> result = limitTransform.transform(null, metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), 3);
        assertEquals(expected_1, result.get(0).getDatapoints());
        assertEquals(expected_2, result.get(1).getDatapoints());
        assertEquals(expected_3, result.get(2).getDatapoints());
    }

    @Test
    public void testLimitTransformWithGreaterThanThanMetricsLen() {
        Transform limitTransform = new LimitTransform();
        Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

        datapoints_1.put(1000L, 1.0);
        datapoints_1.put(2000L, 2.0);
        datapoints_1.put(3000L, 3.0);

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        Map<Long, Double> datapoints_2 = new HashMap<Long, Double>();

        datapoints_2.put(4000L, 10.0);
        datapoints_2.put(5000L, 100.0);
        datapoints_2.put(6000L, 1000.0);

        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        Map<Long, Double> datapoints_3 = new HashMap<Long, Double>();

        datapoints_3.put(7000L, 0.1);
        datapoints_3.put(8000L, 0.01);
        datapoints_3.put(9000L, 0.0001);

        Metric metric_3 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_3.setDatapoints(datapoints_3);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);
        metrics.add(metric_3);

        List<String> constants = new ArrayList<String>();

        constants.add("100");

        Map<Long, Double> expected_1 = new HashMap<Long, Double>();

        expected_1.put(1000L, 1.0);
        expected_1.put(2000L, 2.0);
        expected_1.put(3000L, 3.0);

        Map<Long, Double> expected_2 = new HashMap<Long, Double>();

        expected_2.put(4000L, 10.0);
        expected_2.put(5000L, 100.0);
        expected_2.put(6000L, 1000.0);

        Map<Long, Double> expected_3 = new HashMap<Long, Double>();

        expected_3.put(7000L, 0.1);
        expected_3.put(8000L, 0.01);
        expected_3.put(9000L, 0.0001);

        List<Metric> result = limitTransform.transform(null, metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), 3);
        assertEquals(expected_1, result.get(0).getDatapoints());
        assertEquals(expected_2, result.get(1).getDatapoints());
        assertEquals(expected_3, result.get(2).getDatapoints());
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

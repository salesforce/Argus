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

public class FillCalculateTransformTest {

    private static final String TEST_SCOPE = "test-scope";
    private static final String TEST_METRIC = "test-metric";
    private static final Map<Long, Double> input = new HashMap<>();

    static {
        input.put(1000L, 1.0);
        input.put(2000L, 2.0);
        input.put(3000L, 3.0);
        input.put(4000L, 4.0);
        input.put(5000L, 5.0);
        input.put(6000L, 6.0);
        input.put(7000L, 7.0);
        input.put(8000L, 8.0);
        input.put(9000L, 9.0);
        input.put(10000L, 10.0);
    }

    @Test
    public void testMetricListWithPercentile() {
        Transform fillCalculateTransform = new FillCalculateTransform();
        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_1.setDatapoints(input);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);

        List<String> constants = new ArrayList<>();

        constants.add("p95");

        Map<Long, Double> expected_1 = new HashMap<>();

        expected_1.put(1000L, 10.0);
        expected_1.put(2000L, 10.0);
        expected_1.put(3000L, 10.0);
        expected_1.put(4000L, 10.0);
        expected_1.put(5000L, 10.0);
        expected_1.put(6000L, 10.0);
        expected_1.put(7000L, 10.0);
        expected_1.put(8000L, 10.0);
        expected_1.put(9000L, 10.0);
        expected_1.put(10000L, 10.0);

        List<Metric> result = fillCalculateTransform.transform(null, metrics, constants);

        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getDatapoints().size(), 10);
        assertEquals(expected_1, result.get(0).getDatapoints());
    }

    @Test
    public void testMetricListIntervalOffsetWithPercentile() {
        Transform fillCalculateTransform = new FillCalculateTransform();
        Map<Long, Double> input1 = new HashMap<>();

        input1.put(1000L, 1.0);
        input1.put(4000L, 4.0);
        input1.put(5000L, 5.0);
        input1.put(6000L, 6.0);
        input1.put(7000L, 7.0);
        input1.put(8000L, 8.0);
        input1.put(9000L, 9.0);
        input1.put(10000L, 10.0);

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_1.setDatapoints(input1);

        List<Metric> metrics = new ArrayList<>();

        metrics.add(metric_1);

        List<String> constants = new ArrayList<>();

        constants.add("p80");
        constants.add("1s");
        constants.add("1s");

        Map<Long, Double> expected_1 = new HashMap<>();

        expected_1.put(1000L, 9.2);
        expected_1.put(3000L, 9.2);
        expected_1.put(4000L, 9.2);
        expected_1.put(5000L, 9.2);
        expected_1.put(6000L, 9.2);
        expected_1.put(7000L, 9.2);
        expected_1.put(8000L, 9.2);
        expected_1.put(9000L, 9.2);
        expected_1.put(10000L, 9.2);

        List<Metric> result = fillCalculateTransform.transform(null, metrics, constants);

        assertEquals(result.size(), 1);
        assertEquals(expected_1, result.get(0).getDatapoints());
    }

    @Test
    public void testMetricListWithMin() {
        Transform fillCalculateTransform = new FillCalculateTransform();
        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_1.setDatapoints(input);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);

        List<String> constants = new ArrayList<>();

        constants.add("min");

        Map<Long, Double> expected_1 = new HashMap<>();

        expected_1.put(1000L, 1.0);
        expected_1.put(2000L, 1.0);
        expected_1.put(3000L, 1.0);
        expected_1.put(4000L, 1.0);
        expected_1.put(5000L, 1.0);
        expected_1.put(6000L, 1.0);
        expected_1.put(7000L, 1.0);
        expected_1.put(8000L, 1.0);
        expected_1.put(9000L, 1.0);
        expected_1.put(10000L, 1.0);

        List<Metric> result = fillCalculateTransform.transform(null, metrics, constants);

        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getDatapoints().size(), 10);
        assertEquals(expected_1, result.get(0).getDatapoints());
    }

    @Test
    public void testMetricListWithMax() {
        Transform fillCalculateTransform = new FillCalculateTransform();
        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_1.setDatapoints(input);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);

        List<String> constants = new ArrayList<>();

        constants.add("max");

        Map<Long, Double> expected_1 = new HashMap<>();

        expected_1.put(1000L, 10.0);
        expected_1.put(2000L, 10.0);
        expected_1.put(3000L, 10.0);
        expected_1.put(4000L, 10.0);
        expected_1.put(5000L, 10.0);
        expected_1.put(6000L, 10.0);
        expected_1.put(7000L, 10.0);
        expected_1.put(8000L, 10.0);
        expected_1.put(9000L, 10.0);
        expected_1.put(10000L, 10.0);

        List<Metric> result = fillCalculateTransform.transform(null, metrics, constants);

        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getDatapoints().size(), 10);
        assertEquals(expected_1, result.get(0).getDatapoints());
    }

    @Test
    public void testMetricListWithAvg() {
        Transform fillCalculateTransform = new FillCalculateTransform();
        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_1.setDatapoints(input);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);

        List<String> constants = new ArrayList<>();

        constants.add("avg");

        Map<Long, Double> expected_1 = new HashMap<>();

        expected_1.put(1000L, 5.5);
        expected_1.put(2000L, 5.5);
        expected_1.put(3000L, 5.5);
        expected_1.put(4000L, 5.5);
        expected_1.put(5000L, 5.5);
        expected_1.put(6000L, 5.5);
        expected_1.put(7000L, 5.5);
        expected_1.put(8000L, 5.5);
        expected_1.put(9000L, 5.5);
        expected_1.put(10000L, 5.5);

        List<Metric> result = fillCalculateTransform.transform(null, metrics, constants);

        assertEquals(result.size(), 1);
        assertEquals(result.get(0).getDatapoints().size(), 10);
        assertEquals(expected_1, result.get(0).getDatapoints());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testMetricListWithDev() {
        Transform fillCalculateTransform = new FillCalculateTransform();
        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_1.setDatapoints(input);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);

        List<String> constants = new ArrayList<>();

        constants.add("dev");
        fillCalculateTransform.transform(null, metrics, constants);
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

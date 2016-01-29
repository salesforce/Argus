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

public class PropagateTransformTest {

    private static final String TEST_SCOPE = "test-scope";
    private static final String TEST_METRIC = "test-metric";

    @Test(expected = IllegalArgumentException.class)
    public void testPercentileTransformWithoutConstants() {
        Transform propagateTransform = new PropagateTransform();
        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);
        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        propagateTransform.transform(metrics, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPercentileTransformWithoutMetrics() {
        Transform propagateTransform = new PropagateTransform();
        List<Metric> metrics = null;
        List<String> constants = new ArrayList<String>();

        constants.add("9s");
        propagateTransform.transform(metrics, constants);
    }

    @Test(expected = SystemException.class)
    public void testWithIllegalTimeunit() {
        Transform propagateTransform = new PropagateTransform();
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.put(1000L, "1");
        datapoints.put(2000L, "2");
        datapoints.put(3000L, "3");

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>(1);

        constants.add("5w");
        propagateTransform.transform(metrics, constants);
    }

    @Test
    public void testWithWindowLessThanPropagateRange() {
        Transform propagateTransform = new PropagateTransform();
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.put(1000L, "1");
        datapoints.put(4000L, "4");
        datapoints.put(5000L, "5");
        datapoints.put(6000L, "6");

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        constants.add("1s");

        Map<Long, String> expected = new HashMap<Long, String>();

        expected.put(1000L, "1");
        expected.put(2000L, "1");
        expected.put(3000L, "1");
        expected.put(4000L, "4");
        expected.put(5000L, "5");
        expected.put(6000L, "6");

        List<Metric> result = propagateTransform.transform(metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), 6);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testWithWindowEqualsToPropagateRange() {
        Transform propagateTransform = new PropagateTransform();
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.put(1000L, "1");
        datapoints.put(4000L, "4");
        datapoints.put(5000L, "5");
        datapoints.put(6000L, "6");

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        constants.add("3s");

        Map<Long, String> expected = new HashMap<Long, String>();

        expected.put(1000L, "1");
        expected.put(4000L, "4");
        expected.put(5000L, "5");
        expected.put(6000L, "6");

        List<Metric> result = propagateTransform.transform(metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), 4);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testWithWindowGreaterThanPropagateRange() {
        Transform propagateTransform = new PropagateTransform();
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.put(1000L, "1");
        datapoints.put(4000L, "4");
        datapoints.put(5000L, "5");
        datapoints.put(6000L, "6");

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        constants.add("4s");

        Map<Long, String> expected = new HashMap<Long, String>();

        expected.put(1000L, "1");
        expected.put(4000L, "4");
        expected.put(5000L, "5");
        expected.put(6000L, "6");

        List<Metric> result = propagateTransform.transform(metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), 4);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testWithMultipePropagateRange() {
        Transform propagateTransform = new PropagateTransform();
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.put(1000L, "1");
        datapoints.put(3000L, "3");
        datapoints.put(6000L, "6");

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        constants.add("1s");

        Map<Long, String> expected = new HashMap<Long, String>();

        expected.put(1000L, "1");
        expected.put(2000L, "1");
        expected.put(3000L, "3");
        expected.put(4000L, "3");
        expected.put(5000L, "3");
        expected.put(6000L, "6");

        List<Metric> result = propagateTransform.transform(metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), 6);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testMetricListWithMultipePropagateRange() {
        Transform propagateTransform = new PropagateTransform();
        Map<Long, String> datapoints_1 = new HashMap<Long, String>();

        datapoints_1.put(1000L, "1");
        datapoints_1.put(3000L, "3");
        datapoints_1.put(6000L, "6");

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        Map<Long, String> datapoints_2 = new HashMap<Long, String>();

        datapoints_2.put(1000L, "21");
        datapoints_2.put(3000L, "23");
        datapoints_2.put(6000L, "26");

        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);

        List<String> constants = new ArrayList<String>();

        constants.add("1s");

        Map<Long, String> expected_1 = new HashMap<Long, String>();

        expected_1.put(1000L, "1");
        expected_1.put(2000L, "1");
        expected_1.put(3000L, "3");
        expected_1.put(4000L, "3");
        expected_1.put(5000L, "3");
        expected_1.put(6000L, "6");

        Map<Long, String> expected_2 = new HashMap<Long, String>();

        expected_2.put(1000L, "21");
        expected_2.put(2000L, "21");
        expected_2.put(3000L, "23");
        expected_2.put(4000L, "23");
        expected_2.put(5000L, "23");
        expected_2.put(6000L, "26");

        List<Metric> result = propagateTransform.transform(metrics, constants);

        assertEquals(result.size(), 2);
        assertEquals(result.get(1).getDatapoints().size(), 6);
        assertEquals(expected_1, result.get(0).getDatapoints());
        assertEquals(expected_2, result.get(1).getDatapoints());
    }

    @Test
    public void testWithOnlyOneDP() {
        Transform propagateTransform = new PropagateTransform();
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.put(1000L, "1");

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        constants.add("100s");

        Map<Long, String> expected = new HashMap<Long, String>();

        expected.put(1000L, "1");

        List<Metric> result = propagateTransform.transform(metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), 1);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testNoMetricWithMissingPointAndOtherTimeUnit() {
        Transform propagateTransform = new PropagateTransform();
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.put(1000L, "1");
        datapoints.put(2000L, "2");
        datapoints.put(3000L, "3");

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        constants.add("5m");

        Map<Long, String> expected = new HashMap<Long, String>();

        expected.put(1000L, "1");
        expected.put(2000L, "2");
        expected.put(3000L, "3");

        List<Metric> result = propagateTransform.transform(metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), 3);
        assertEquals(expected, result.get(0).getDatapoints());
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

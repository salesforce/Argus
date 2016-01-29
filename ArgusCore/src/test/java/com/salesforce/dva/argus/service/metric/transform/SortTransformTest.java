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
import org.junit.Ignore;
import org.junit.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;

public class SortTransformTest {

    private static final String TEST_SCOPE = "test-scope";
    private static final String TEST_METRIC = "test-metric";

    @Test(expected = IllegalArgumentException.class)
    public void testSortTransformWithIllegalLimit() {
        Transform sortTransform = new SortTransformWrapAboveAndBelow();
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.put(1000L, "1");

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        constants.add("5w");
        constants.add("average");
        constants.add("ascending");
        sortTransform.transform(metrics, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSortTransformWithIllegalType() {
        Transform sortTransform = new SortTransformWrapAboveAndBelow();
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.put(1000L, "1");

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        constants.add("5");
        constants.add("foobar");
        constants.add("ascending");
        sortTransform.transform(metrics, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSortTransformWithIllegalOrder() {
        Transform sortTransform = new SortTransformWrapAboveAndBelow();
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.put(1000L, "1");

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        constants.add("5");
        constants.add("name");
        constants.add("bad-type");
        sortTransform.transform(metrics, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSortTransformWithoutMetrics() {
        Transform sortTransform = new SortTransformWrapAboveAndBelow();
        List<Metric> metrics = null;
        List<String> constants = new ArrayList<String>();

        constants.add("2");
        constants.add("average");
        constants.add("descending");
        sortTransform.transform(metrics, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSortTransformWithoutConstants() {
        Transform sortTransform = new SortTransformWrapAboveAndBelow();
        Map<Long, String> datapoints_1 = new HashMap<Long, String>();

        datapoints_1.put(1000L, "1");
        datapoints_1.put(2000L, "1");
        datapoints_1.put(3000L, "1");

        Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);
        metric_1.setDisplayName("c");

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);

        List<String> constants = new ArrayList<String>();

        sortTransform.transform(metrics, constants);
    }

    @Ignore
    @Test
    public void testSortTransformWithLimitAndType() {
        Transform sortTransform = new SortTransformWrapAboveAndBelow();
        Map<Long, String> datapoints_1 = new HashMap<Long, String>();

        datapoints_1.put(1000L, "1");
        datapoints_1.put(2000L, "1");
        datapoints_1.put(3000L, "1");

        Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);
        metric_1.setDisplayName("c");

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);

        List<String> constants = new ArrayList<String>();

        constants.add("2");
        constants.add("minima");

        Map<Long, String> expected = new HashMap<Long, String>();

        expected.put(1000L, "1");
        expected.put(2000L, "1");
        expected.put(3000L, "1");

        List<Metric> result = sortTransform.transform(metrics, constants);

        sortTransform.transform(metrics, constants);
        assertEquals(result.size(), 1);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSortTransformWithLimitAndOrder() {
        Transform sortTransform = new SortTransformWrapAboveAndBelow();
        Map<Long, String> datapoints_1 = new HashMap<Long, String>();

        datapoints_1.put(1000L, "1");
        datapoints_1.put(2000L, "1");
        datapoints_1.put(3000L, "1");

        Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);
        metric_1.setDisplayName("c");

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);

        List<String> constants = new ArrayList<String>();

        constants.add("2");
        constants.add("descending");
        sortTransform.transform(metrics, constants);
    }

    @Test
    public void testSortTransformWithLimitLessThanLenNameDes() {
        Transform sortTransform = new SortTransformWrapAboveAndBelow();
        Map<Long, String> datapoints_1 = new HashMap<Long, String>();

        datapoints_1.put(1000L, "1");
        datapoints_1.put(2000L, "1");
        datapoints_1.put(3000L, "1");

        Metric metric_1 = mock(Metric.class);

        when(metric_1.getDatapoints()).thenReturn(datapoints_1);
        when(metric_1.getMetric()).thenReturn("a");

        Map<Long, String> datapoints_2 = new HashMap<Long, String>();

        datapoints_2.put(1000L, "2");
        datapoints_2.put(2000L, "2");
        datapoints_2.put(3000L, "2");

        Metric metric_2 = mock(Metric.class);

        when(metric_2.getDatapoints()).thenReturn(datapoints_2);
        when(metric_2.getMetric()).thenReturn("b");

        Map<Long, String> datapoints_3 = new HashMap<Long, String>();

        datapoints_3.put(1000L, "3");
        datapoints_3.put(2000L, "3");
        datapoints_3.put(3000L, "3");

        Metric metric_3 = mock(Metric.class);

        when(metric_3.getDatapoints()).thenReturn(datapoints_3);
        when(metric_3.getMetric()).thenReturn("c");

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);
        metrics.add(metric_3);

        List<String> constants = new ArrayList<String>();

        constants.add("2");
        constants.add("name");
        constants.add("descending");

        Map<Long, String> expected_1 = new HashMap<Long, String>();

        expected_1.put(1000L, "3");
        expected_1.put(2000L, "3");
        expected_1.put(3000L, "3");

        Map<Long, String> expected_2 = new HashMap<Long, String>();

        expected_2.put(1000L, "2");
        expected_2.put(2000L, "2");
        expected_2.put(3000L, "2");

        List<Metric> result = sortTransform.transform(metrics, constants);

        assertEquals(result.size(), 2);
        assertEquals(expected_1, result.get(0).getDatapoints());
        assertEquals(expected_2, result.get(1).getDatapoints());
    }

    @Test
    public void testSortTransformWithLimitEqualToLenMinAsc() {
        Transform sortTransform = new SortTransformWrapAboveAndBelow();
        Map<Long, String> datapoints_1 = new HashMap<Long, String>();

        datapoints_1.put(1000L, "1");
        datapoints_1.put(2000L, "100");
        datapoints_1.put(3000L, "100");

        Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        Map<Long, String> datapoints_2 = new HashMap<Long, String>();

        datapoints_2.put(1000L, "2");
        datapoints_2.put(2000L, "200");
        datapoints_2.put(3000L, "200");

        Metric metric_2 = new Metric(TEST_SCOPE + "2", TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        Map<Long, String> datapoints_3 = new HashMap<Long, String>();

        datapoints_3.put(1000L, "3");
        datapoints_3.put(2000L, "300");
        datapoints_3.put(3000L, "300");

        Metric metric_3 = new Metric(TEST_SCOPE + "3", TEST_METRIC);

        metric_3.setDatapoints(datapoints_3);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);
        metrics.add(metric_3);

        List<String> constants = new ArrayList<String>();

        constants.add("3");
        constants.add("minima");
        constants.add("ascending");

        Map<Long, String> expected_1 = new HashMap<Long, String>();

        expected_1.put(1000L, "1");
        expected_1.put(2000L, "100");
        expected_1.put(3000L, "100");

        Map<Long, String> expected_2 = new HashMap<Long, String>();

        expected_2.put(1000L, "2");
        expected_2.put(2000L, "200");
        expected_2.put(3000L, "200");

        Map<Long, String> expected_3 = new HashMap<Long, String>();

        expected_3.put(1000L, "3");
        expected_3.put(2000L, "300");
        expected_3.put(3000L, "300");

        List<Metric> result = sortTransform.transform(metrics, constants);

        assertEquals(result.size(), 3);
        assertEquals(expected_1, result.get(0).getDatapoints());
        assertEquals(expected_2, result.get(1).getDatapoints());
        assertEquals(expected_3, result.get(2).getDatapoints());
    }

    @Test
    public void testSortTransformWithLimitGreaterThanLenMaxDes() {
        Transform sortTransform = new SortTransformWrapAboveAndBelow();
        Map<Long, String> datapoints_1 = new HashMap<Long, String>();

        datapoints_1.put(1000L, "1");
        datapoints_1.put(2000L, "1");
        datapoints_1.put(3000L, "100");

        Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        Map<Long, String> datapoints_2 = new HashMap<Long, String>();

        datapoints_2.put(1000L, "2");
        datapoints_2.put(2000L, "2");
        datapoints_2.put(3000L, "200");

        Metric metric_2 = new Metric(TEST_SCOPE + "2", TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        Map<Long, String> datapoints_3 = new HashMap<Long, String>();

        datapoints_3.put(1000L, "3");
        datapoints_3.put(2000L, "3");
        datapoints_3.put(3000L, "300");

        Metric metric_3 = new Metric(TEST_SCOPE + "3", TEST_METRIC);

        metric_3.setDatapoints(datapoints_3);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);
        metrics.add(metric_3);

        List<String> constants = new ArrayList<String>();

        constants.add("100");
        constants.add("maxima");
        constants.add("descending");

        Map<Long, String> expected_1 = new HashMap<Long, String>();

        expected_1.put(1000L, "3");
        expected_1.put(2000L, "3");
        expected_1.put(3000L, "300");

        Map<Long, String> expected_2 = new HashMap<Long, String>();

        expected_2.put(1000L, "2");
        expected_2.put(2000L, "2");
        expected_2.put(3000L, "200");

        Map<Long, String> expected_3 = new HashMap<Long, String>();

        expected_3.put(1000L, "1");
        expected_3.put(2000L, "1");
        expected_3.put(3000L, "100");

        List<Metric> result = sortTransform.transform(metrics, constants);

        assertEquals(result.size(), 3);
        assertEquals(expected_1, result.get(0).getDatapoints());
        assertEquals(expected_2, result.get(1).getDatapoints());
        assertEquals(expected_3, result.get(2).getDatapoints());
    }

    @Test
    public void testSortTransformWithLimitLessThanLenDevAsc() {
        Transform sortTransform = new SortTransformWrapAboveAndBelow();
        Map<Long, String> datapoints_1 = new HashMap<Long, String>();

        datapoints_1.put(1000L, "1");
        datapoints_1.put(2000L, "4");
        datapoints_1.put(3000L, "7");

        Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        Map<Long, String> datapoints_2 = new HashMap<Long, String>();

        datapoints_2.put(1000L, "1");
        datapoints_2.put(2000L, "3");
        datapoints_2.put(3000L, "5");

        Metric metric_2 = new Metric(TEST_SCOPE + "2", TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        Map<Long, String> datapoints_3 = new HashMap<Long, String>();

        datapoints_3.put(1000L, "1");
        datapoints_3.put(2000L, "2");
        datapoints_3.put(3000L, "3");

        Metric metric_3 = new Metric(TEST_SCOPE + "3", TEST_METRIC);

        metric_3.setDatapoints(datapoints_3);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);
        metrics.add(metric_3);

        List<String> constants = new ArrayList<String>();

        constants.add("2");
        constants.add("dev");
        constants.add("ascending");

        Map<Long, String> expected_1 = new HashMap<Long, String>();

        expected_1.put(1000L, "1");
        expected_1.put(2000L, "2");
        expected_1.put(3000L, "3");

        Map<Long, String> expected_2 = new HashMap<Long, String>();

        expected_2.put(1000L, "1");
        expected_2.put(2000L, "3");
        expected_2.put(3000L, "5");

        List<Metric> result = sortTransform.transform(metrics, constants);

        assertEquals(result.size(), 2);
        assertEquals(expected_1, result.get(0).getDatapoints());
        assertEquals(expected_2, result.get(1).getDatapoints());
    }

    @Test
    public void testSortTransformWithLimitGreaterThanLenMaxDesHavingNull() {
        Transform sortTransform = new SortTransformWrapAboveAndBelow();
        Map<Long, String> datapoints_1 = new HashMap<Long, String>();

        datapoints_1.put(1000L, null);
        datapoints_1.put(2000L, null);
        datapoints_1.put(3000L, "100");

        Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        Map<Long, String> datapoints_2 = new HashMap<Long, String>();

        datapoints_2.put(1000L, null);
        datapoints_2.put(2000L, null);
        datapoints_2.put(3000L, "200");

        Metric metric_2 = new Metric(TEST_SCOPE + "2", TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        Map<Long, String> datapoints_3 = new HashMap<Long, String>();

        datapoints_3.put(1000L, null);
        datapoints_3.put(2000L, null);
        datapoints_3.put(3000L, "300");

        Metric metric_3 = new Metric(TEST_SCOPE + "3", TEST_METRIC);

        metric_3.setDatapoints(datapoints_3);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);
        metrics.add(metric_3);

        List<String> constants = new ArrayList<String>();

        constants.add("100");
        constants.add("maxima");
        constants.add("descending");

        Map<Long, String> expected_1 = new HashMap<Long, String>();

        expected_1.put(1000L, null);
        expected_1.put(2000L, null);
        expected_1.put(3000L, "300");

        Map<Long, String> expected_2 = new HashMap<Long, String>();

        expected_2.put(1000L, null);
        expected_2.put(2000L, null);
        expected_2.put(3000L, "200");

        Map<Long, String> expected_3 = new HashMap<Long, String>();

        expected_3.put(1000L, null);
        expected_3.put(2000L, null);
        expected_3.put(3000L, "100");

        List<Metric> result = sortTransform.transform(metrics, constants);

        assertEquals(result.size(), 3);
        assertEquals(expected_1, result.get(0).getDatapoints());
        assertEquals(expected_2, result.get(1).getDatapoints());
        assertEquals(expected_3, result.get(2).getDatapoints());
    }

    @Test
    public void testSortTransformBug_W2802798() {
        Transform sortTransform = new SortTransformWrapAboveAndBelow();
        Map<Long, String> datapoints_1 = new HashMap<Long, String>();

        datapoints_1.put(0L, "2");
        datapoints_1.put(60000L, "1");
        datapoints_1.put(120000L, "3");

        Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        Map<Long, String> datapoints_2 = new HashMap<Long, String>();

        datapoints_2.put(0L, "3");
        datapoints_2.put(60000L, "6");
        datapoints_2.put(120000L, "9");

        Metric metric_2 = new Metric(TEST_SCOPE + "2", TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);

        List<String> constants = new ArrayList<String>();

        constants.add("minima");
        constants.add("ascending");

        Map<Long, String> expected_1 = new HashMap<Long, String>();

        expected_1.put(0L, "2");
        expected_1.put(60000L, "1");
        expected_1.put(120000L, "3");

        Map<Long, String> expected_2 = new HashMap<Long, String>();

        expected_2.put(0L, "3");
        expected_2.put(60000L, "6");
        expected_2.put(120000L, "9");

        List<Metric> result = sortTransform.transform(metrics, constants);

        assertEquals(result.size(), 2);
        assertEquals(expected_1, result.get(0).getDatapoints());
        assertEquals(expected_2, result.get(1).getDatapoints());
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

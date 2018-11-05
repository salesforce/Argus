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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class Sum_VTransformTest {

    private static final String TEST_SCOPE = "test-scope";
    private static final String TEST_METRIC = "test-metric";

    @Test(expected = IllegalArgumentException.class)
    public void testSum_VTransformWithoutMetrics() {
        Transform sum_vTransform = new MetricZipperTransform(new SumValueZipper());
        List<Metric> metrics = null;

        sum_vTransform.transform(null, metrics);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSum_VTransformWithOnlyOneMetric() {
        Transform sum_vTransform = new MetricZipperTransform(new SumValueZipper());
        List<Metric> metrics = new ArrayList<Metric>();
        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metrics.add(metric);
        sum_vTransform.transform(null, metrics);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSum_VTransformWithConstants() {
        Transform sum_vTransform = new MetricZipperTransform(new SumValueZipper());
        List<Metric> metrics = new ArrayList<Metric>();
        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        sum_vTransform.transform(null, metrics, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSum_VTransformVectorWithoutPoints() {
        Transform sum_vTransform = new MetricZipperTransform(new SumValueZipper());
        Map<Long, Double> datapoints = new HashMap<Long, Double>();

        datapoints.put(1000L, 1.0);

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        Metric vector = new Metric(TEST_SCOPE, TEST_METRIC);
        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);
        metrics.add(vector);
        sum_vTransform.transform(null, metrics);
    }

    @Test
    public void testSum_VTransformWithSameLenVectorAgainstOneMetric() {
        Transform sum_vTransform = new MetricZipperTransform(new SumValueZipper());
        Map<Long, Double> datapoints = new HashMap<Long, Double>();

        datapoints.put(1000L, 1.0);
        datapoints.put(2000L, 2.0);
        datapoints.put(3000L, 3.0);

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        Map<Long, Double> vector_datapoints = new HashMap<Long, Double>();

        vector_datapoints.put(1000L, 1.0);
        vector_datapoints.put(2000L, 1.0);
        vector_datapoints.put(3000L, 1.0);

        Metric vector = new Metric(TEST_SCOPE, TEST_METRIC);

        vector.setDatapoints(vector_datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);
        metrics.add(vector);

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(1000L, 2.0);
        expected.put(2000L, 3.0);
        expected.put(3000L, 4.0);

        List<Metric> result = sum_vTransform.transform(null, metrics);

        assertEquals(result.get(0).getDatapoints().size(), 3);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testSum_VTransformWithLongerLenVectorAgainstOneMetric() {
        Transform sum_vTransform = new MetricZipperTransform(new SumValueZipper());
        Map<Long, Double> datapoints = new HashMap<Long, Double>();

        datapoints.put(1000L, 1.0);
        datapoints.put(2000L, 2.0);
        datapoints.put(3000L, 3.0);

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        Map<Long, Double> vector_datapoints = new HashMap<Long, Double>();

        vector_datapoints.put(1000L, 1.0);
        vector_datapoints.put(2000L, 1.0);
        vector_datapoints.put(3000L, 1.0);
        vector_datapoints.put(4000L, 1.0);

        Metric vector = new Metric(TEST_SCOPE, TEST_METRIC);

        vector.setDatapoints(vector_datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);
        metrics.add(vector);

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(1000L, 2.0);
        expected.put(2000L, 3.0);
        expected.put(3000L, 4.0);

        List<Metric> result = sum_vTransform.transform(null, metrics);

        assertEquals(result.get(0).getDatapoints().size(), 3);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testSum_VTransformWithShorterLenVectorAgainstOneMetric() {
        Transform sum_vTransform = new MetricZipperTransform(new SumValueZipper());
        Map<Long, Double> datapoints = new HashMap<Long, Double>();

        datapoints.put(1000L, 1.0);
        datapoints.put(2000L, 2.0);
        datapoints.put(3000L, 3.0);

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        Map<Long, Double> vector_datapoints = new HashMap<Long, Double>();

        vector_datapoints.put(1000L, 1.0);
        vector_datapoints.put(2000L, 1.0);

        Metric vector = new Metric(TEST_SCOPE, TEST_METRIC);

        vector.setDatapoints(vector_datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);
        metrics.add(vector);

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(1000L, 2.0);
        expected.put(2000L, 3.0);
        expected.put(3000L, 3.0);

        List<Metric> result = sum_vTransform.transform(null, metrics);

        assertEquals(result.get(0).getDatapoints().size(), 3);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testSum_VTransformWithMidMissingPointVectorAgainstOneMetric() {
        Transform sum_vTransform = new MetricZipperTransform(new SumValueZipper());
        Map<Long, Double> datapoints = new HashMap<Long, Double>();

        datapoints.put(1000L, 1.0);
        datapoints.put(2000L, 2.0);
        datapoints.put(3000L, 3.0);

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        Map<Long, Double> vector_datapoints = new HashMap<Long, Double>();

        vector_datapoints.put(1000L, 1.0);
        vector_datapoints.put(3000L, 1.0);

        Metric vector = new Metric(TEST_SCOPE, TEST_METRIC);

        vector.setDatapoints(vector_datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);
        metrics.add(vector);

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(1000L, 2.0);
        expected.put(2000L, 2.0);
        expected.put(3000L, 4.0);

        List<Metric> result = sum_vTransform.transform(null, metrics);

        assertEquals(result.get(0).getDatapoints().size(), 3);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testSum_VTransformWithNullPointVectorAgainstOneMetric() {
        Transform sum_vTransform = new MetricZipperTransform(new SumValueZipper());
        Map<Long, Double> datapoints = new HashMap<Long, Double>();

        datapoints.put(1000L, 1.0);
        datapoints.put(2000L, 2.0);
        datapoints.put(3000L, 3.0);

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        Map<Long, Double> vector_datapoints = new HashMap<Long, Double>();

        vector_datapoints.put(1000L, 1.0);
        vector_datapoints.put(2000L, null);
        vector_datapoints.put(3000L, 1.0);

        Metric vector = new Metric(TEST_SCOPE, TEST_METRIC);

        vector.setDatapoints(vector_datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);
        metrics.add(vector);

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(1000L, 2.0);
        expected.put(2000L, 2.0);
        expected.put(3000L, 4.0);

        List<Metric> result = sum_vTransform.transform(null, metrics);

        assertEquals(result.get(0).getDatapoints().size(), 3);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testSum_VTransformWithVectorAgainstOneNullPointMetric() {
        Transform sum_vTransform = new MetricZipperTransform(new SumValueZipper());
        Map<Long, Double> datapoints = new HashMap<Long, Double>();

        datapoints.put(1000L, 1.0);
        datapoints.put(2000L, null);
        datapoints.put(3000L, 3.0);

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        Map<Long, Double> vector_datapoints = new HashMap<Long, Double>();

        vector_datapoints.put(1000L, 1.0);
        vector_datapoints.put(2000L, 1.0);
        vector_datapoints.put(3000L, 1.0);

        Metric vector = new Metric(TEST_SCOPE, TEST_METRIC);

        vector.setDatapoints(vector_datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);
        metrics.add(vector);

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(1000L, 2.0);
        expected.put(2000L, 1.0);
        expected.put(3000L, 4.0);

        List<Metric> result = sum_vTransform.transform(null, metrics);

        assertEquals(result.get(0).getDatapoints().size(), 3);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testSum_VTransformWithSameShorterLongerVectorAgainstMetricList() {
        Transform sum_vTransform = new MetricZipperTransform(new SumValueZipper());
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
        datapoints_2.put(4000L, 10000.0);

        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        Map<Long, Double> datapoints_3 = new HashMap<Long, Double>();

        datapoints_3.put(1000L, 0.1);
        datapoints_3.put(2000L, 0.01);

        Metric metric_3 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_3.setDatapoints(datapoints_3);

        Map<Long, Double> vector_datapoints = new HashMap<Long, Double>();

        vector_datapoints.put(1000L, 1.0);
        vector_datapoints.put(2000L, 1.0);
        vector_datapoints.put(3000L, 1.0);

        Metric vector = new Metric(TEST_SCOPE, TEST_METRIC);

        vector.setDatapoints(vector_datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);
        metrics.add(metric_3);
        metrics.add(vector);

        Map<Long, Double> expected_1 = new HashMap<Long, Double>();

        expected_1.put(1000L, 2.0);
        expected_1.put(2000L, 3.0);
        expected_1.put(3000L, 4.0);

        Map<Long, Double> expected_2 = new HashMap<Long, Double>();

        expected_2.put(1000L, 11.0);
        expected_2.put(2000L, 101.0);
        expected_2.put(3000L, 1001.0);
        expected_2.put(4000L, 10000.0);

        Map<Long, Double> expected_3 = new HashMap<Long, Double>();

        expected_3.put(1000L, 1.1);
        expected_3.put(2000L, 1.01);

        List<Metric> result = sum_vTransform.transform(null, metrics);

        assertEquals(result.get(0).getDatapoints().size(), 3);
        assertEquals(expected_1, result.get(0).getDatapoints());
        assertEquals(result.get(1).getDatapoints().size(), 4);
        assertEquals(expected_2, result.get(1).getDatapoints());
        assertEquals(result.get(2).getDatapoints().size(), 2);
        assertEquals(expected_3, result.get(2).getDatapoints());
    }

    @Test
    public void testSum_VTransformWithMissingPointNullPointVectorAgainstNullPointMetricList() {
        Transform sum_vTransform = new MetricZipperTransform(new SumValueZipper());
        Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

        datapoints_1.put(1000L, 1.0);
        datapoints_1.put(2000L, 2.0);
        datapoints_1.put(3000L, 3.0);

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        Map<Long, Double> datapoints_2 = new HashMap<Long, Double>();

        datapoints_2.put(1000L, 10.0);
        datapoints_2.put(2000L, 100.0);
        datapoints_2.put(4000L, 1000.0);
        datapoints_2.put(5000L, 10000.0);

        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        Map<Long, Double> datapoints_3 = new HashMap<Long, Double>();

        datapoints_3.put(1000L, 0.1);
        datapoints_3.put(2000L, 0.01);
        datapoints_3.put(4000L, 0.001);
        datapoints_3.put(5000L, null);

        Metric metric_3 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_3.setDatapoints(datapoints_3);

        Map<Long, Double> vector_datapoints = new HashMap<Long, Double>();

        vector_datapoints.put(1000L, 1.0);
        vector_datapoints.put(2000L, 1.0);
        vector_datapoints.put(4000L, 1.0);
        vector_datapoints.put(5000L, null);

        Metric vector = new Metric(TEST_SCOPE, TEST_METRIC);

        vector.setDatapoints(vector_datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);
        metrics.add(metric_3);
        metrics.add(vector);

        Map<Long, Double> expected_1 = new HashMap<Long, Double>();

        expected_1.put(1000L, 2.0);
        expected_1.put(2000L, 3.0);
        expected_1.put(3000L, 3.0);

        Map<Long, Double> expected_2 = new HashMap<Long, Double>();

        expected_2.put(1000L, 11.0);
        expected_2.put(2000L, 101.0);
        expected_2.put(4000L, 1001.0);
        expected_2.put(5000L, 10000.0);

        Map<Long, Double> expected_3 = new HashMap<Long, Double>();

        expected_3.put(1000L, 1.1);
        expected_3.put(2000L, 1.01);
        expected_3.put(4000L, 1.001);
        expected_3.put(5000L, 0.0);

        List<Metric> result = sum_vTransform.transform(null, metrics);

        assertEquals(result.get(0).getDatapoints().size(), 3);
        assertEquals(expected_1, result.get(0).getDatapoints());
        assertEquals(result.get(1).getDatapoints().size(), 4);
        assertEquals(expected_2, result.get(1).getDatapoints());
        assertEquals(result.get(2).getDatapoints().size(), 4);
        assertEquals(expected_3, result.get(2).getDatapoints());
    }
    
    @Test
    public void testSum_VTransformWithSameShorterLongerVectorAgainstMetricList_fullJoinIndicator() {
        Transform sum_vTransform = new MetricZipperTransform(new SumValueZipper());
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
        datapoints_2.put(4000L, 10000.0);

        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        Map<Long, Double> datapoints_3 = new HashMap<Long, Double>();

        datapoints_3.put(1000L, 0.1);
        datapoints_3.put(2000L, 0.01);

        Metric metric_3 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_3.setDatapoints(datapoints_3);

        Map<Long, Double> vector_datapoints = new HashMap<Long, Double>();

        vector_datapoints.put(1000L, 1.0);
        vector_datapoints.put(2000L, 1.0);
        vector_datapoints.put(3000L, 1.0);

        Metric vector = new Metric(TEST_SCOPE, TEST_METRIC);

        vector.setDatapoints(vector_datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);
        metrics.add(metric_3);
        metrics.add(vector);

        Map<Long, Double> expected_1 = new HashMap<Long, Double>();

        expected_1.put(1000L, 2.0);
        expected_1.put(2000L, 3.0);
        expected_1.put(3000L, 4.0);

        Map<Long, Double> expected_2 = new HashMap<Long, Double>();

        expected_2.put(1000L, 11.0);
        expected_2.put(2000L, 101.0);
        expected_2.put(3000L, 1001.0);
        expected_2.put(4000L, 10000.0);

        Map<Long, Double> expected_3 = new HashMap<Long, Double>();

        expected_3.put(1000L, 1.1);
        expected_3.put(2000L, 1.01);
        expected_3.put(3000L, 1.0);
        
        List<Metric> result = sum_vTransform.transform(null, metrics, Arrays.asList("UNION"));

        assertEquals(3, result.get(0).getDatapoints().size());
        assertEquals(expected_1, result.get(0).getDatapoints());
        assertEquals(4, result.get(1).getDatapoints().size());
        assertEquals(expected_2, result.get(1).getDatapoints());
        assertEquals(3, result.get(2).getDatapoints().size());
        assertEquals(expected_3, result.get(2).getDatapoints());
    }
    
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

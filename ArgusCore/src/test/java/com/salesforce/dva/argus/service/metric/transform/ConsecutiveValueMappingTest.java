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
import static org.junit.Assert.assertEquals;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Test;
import com.salesforce.dva.argus.entity.Metric;

public class ConsecutiveValueMappingTest {
	private static final String TEST_SCOPE = "test-scope";
    private static final String TEST_METRIC = "test-metric";
    
    @Test(expected = IllegalArgumentException.class)
    public void testconsecutiveTransformWithoutMetrics() {
        Transform transform = new MetricMappingTransform(new ConsecutiveValueMapping());
        List<Metric> metrics = null;
        List<String> constants = new ArrayList<String>();

        constants.add("1s");
        constants.add("2s");
        transform.transform(null, metrics, constants);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testconsecutiveTransformWithoutThreshold() {
        Transform transform = new MetricMappingTransform(new ConsecutiveValueMapping());
        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);
        List<Metric> metrics = new ArrayList<Metric>();
        metrics.add(metric_1);
        
        List<String> constants = new ArrayList<String>();
        transform.transform(null, metrics, constants);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testconsecutiveTransformWithOnlyOneThreshold() {
        Transform transform = new MetricMappingTransform(new ConsecutiveValueMapping());
        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);
        List<Metric> metrics = new ArrayList<Metric>();
        metrics.add(metric_1);
        
        List<String> constants = new ArrayList<String>();
        constants.add("1s");
        transform.transform(null, metrics, constants);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testconsecutiveTransformWithZeroconsecutiveThreshold() {
        Transform transform = new MetricMappingTransform(new ConsecutiveValueMapping());
        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);
        List<Metric> metrics = new ArrayList<Metric>();
        metrics.add(metric_1);
        
        List<String> constants = new ArrayList<String>();
        constants.add("0");
        constants.add("0");
        transform.transform(null, metrics, constants);
    }
    
    @Test
    public void testconsecutiveValueMappingSingleBaseCases() {
        Transform transform = new MetricMappingTransform(new ConsecutiveValueMapping());
        Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

        datapoints_1.put(1000L, 0.0);
        datapoints_1.put(3000L, 1.0);
        datapoints_1.put(4000L, 1.0);
        datapoints_1.put(5000L, 1.0);
        datapoints_1.put(7000L, 1.0);
        datapoints_1.put(8000L, 1.0);

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);
        metric_1.setDatapoints(datapoints_1);
        List<Metric> metrics = new ArrayList<Metric>();
        metrics.add(metric_1);
        
        List<String> constants = new ArrayList<String>();
        constants.add("1s");
        constants.add("1s");
        
        Map<Long, Double> expected = new HashMap<Long, Double>();
        expected.put(3000L, 1.0);
        expected.put(4000L, 1.0);
        expected.put(5000L, 1.0);
        expected.put(7000L, 1.0);
        expected.put(8000L, 1.0);
        List<Metric> result = transform.transform(null,metrics, constants);

        assertEquals("Result length should match",result.get(0).getDatapoints().size(), expected.size());
        assertEquals("Result value should match",expected, result.get(0).getDatapoints());
    }

    @Test
    public void testconsecutiveValueMappingSingleEdgeCases() {
        Transform transform = new MetricMappingTransform(new ConsecutiveValueMapping());
        Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

        datapoints_1.put(1000L, 0.0);
        datapoints_1.put(3000L, 1.0);
        datapoints_1.put(5000L, 1.0);
        datapoints_1.put(6000L, 1.0);
        datapoints_1.put(7000L, 1.0);
        datapoints_1.put(8000L, 1.0);

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);
        metric_1.setDatapoints(datapoints_1);
        List<Metric> metrics = new ArrayList<Metric>();
        metrics.add(metric_1);
        
        List<String> constants = new ArrayList<String>();
        constants.add("3s");
        constants.add("1s");
        
        Map<Long, Double> expected = new HashMap<Long, Double>();
        expected.put(5000L, 1.0);
        expected.put(6000L, 1.0);
        expected.put(7000L, 1.0);
        expected.put(8000L, 1.0);
        List<Metric> result = transform.transform(null,metrics, constants);

        assertEquals("Result length should match",result.get(0).getDatapoints().size(), expected.size());
        assertEquals("Result value should match",expected, result.get(0).getDatapoints());
    }
    
    @Test
    public void testconsecutiveValueMappingSingleIntervalCases() {
        Transform transform = new MetricMappingTransform(new ConsecutiveValueMapping());
        Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

        datapoints_1.put(1000000L, 0.0);
        datapoints_1.put(3000000L, 1.0);
        datapoints_1.put(5000000L, 1.0);
        datapoints_1.put(5500000L, 1.0);
        datapoints_1.put(6000000L, 1.0);
        datapoints_1.put(9000000L, 1.0);

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);
        metric_1.setDatapoints(datapoints_1);
        List<Metric> metrics = new ArrayList<Metric>();
        metrics.add(metric_1);
        
        List<String> constants = new ArrayList<String>();
        constants.add("10m");
        constants.add("10m");
        
        Map<Long, Double> expected = new HashMap<Long, Double>();
        expected.put(5000000L, 1.0);
        expected.put(5500000L, 1.0);
        expected.put(6000000L, 1.0);
        List<Metric> result = transform.transform(null,metrics, constants);

        assertEquals("Result length should match",result.get(0).getDatapoints().size(), expected.size());
        assertEquals("Result value should match",expected, result.get(0).getDatapoints());
    }
    
    @Test
    public void testconsecutiveValueMappingSingleZeroCases() {
        Transform transform = new MetricMappingTransform(new ConsecutiveValueMapping());
        Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

        datapoints_1.put(1000L, 0.0);
        datapoints_1.put(2000L, 1.0);
        datapoints_1.put(3000L, 1.0);

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);
        metric_1.setDatapoints(datapoints_1);
        List<Metric> metrics = new ArrayList<Metric>();
        metrics.add(metric_1);
        
        List<String> constants = new ArrayList<String>();
        constants.add("4s");
        constants.add("1s");
        
        Map<Long, Double> expected = new HashMap<Long, Double>();
        List<Metric> result = transform.transform(null,metrics, constants);

        assertEquals("Result length should match",result.get(0).getDatapoints().size(), expected.size());
        assertEquals("Result value should match",expected, result.get(0).getDatapoints());
    }
    
    @Test
    public void testconsecutiveValueMappingEmptySeriesCases() {
        Transform transform = new MetricMappingTransform(new ConsecutiveValueMapping());
        Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();
        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);
        metric_1.setDatapoints(datapoints_1);
        List<Metric> metrics = new ArrayList<Metric>();
        metrics.add(metric_1);
        
        List<String> constants = new ArrayList<String>();
        constants.add("4s");
        constants.add("1s");
        
        Map<Long, Double> expected = new HashMap<Long, Double>();
        List<Metric> result = transform.transform(null,metrics, constants);

        assertEquals("Result length should match",result.get(0).getDatapoints().size(), expected.size());
        assertEquals("Result value should match",expected, result.get(0).getDatapoints());
    }
    
    @Test
    public void testconsecutiveValueMappingMultipleSeriesCases() {
    	Transform transform = new MetricMappingTransform(new ConsecutiveValueMapping());
        Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();
        datapoints_1.put(1000L, 0.0);
        datapoints_1.put(3000L, 1.0);
        datapoints_1.put(5000L, 1.0);
        datapoints_1.put(6000L, 1.0);
        datapoints_1.put(7000L, 1.0);
        
        Map<Long, Double> datapoints_2 = new HashMap<Long, Double>();
        datapoints_2.put(4000L, 0.0);
        datapoints_2.put(5000L, 1.0);
        datapoints_2.put(6000L, 1.0);
        datapoints_2.put(11000L, 1.0);

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);
        metric_1.setDatapoints(datapoints_1);
        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);
        metric_2.setDatapoints(datapoints_2);
        List<Metric> metrics = new ArrayList<Metric>();
        metrics.add(metric_1);
        metrics.add(metric_2);
        
        List<String> constants = new ArrayList<String>();
        constants.add("2s");
        constants.add("1s");
        Map<Long, Double> expected_1 = new TreeMap<Long, Double>();
        expected_1.put(5000L, 1.0);
        expected_1.put(6000L, 1.0);
        expected_1.put(7000L, 1.0);
        
        Map<Long, Double> expected_2 = new TreeMap<Long, Double>();
        expected_2.put(4000L, 0.0);
        expected_2.put(5000L, 1.0);
        expected_2.put(6000L, 1.0);
        List<Metric> result = transform.transform(null,metrics, constants);
        assertEquals("Result length should match",result.get(0).getDatapoints().size(), expected_1.size());
        assertEquals("Result value should match",expected_1, result.get(0).getDatapoints());
        assertEquals("Result length should match",result.get(1).getDatapoints().size(), expected_2.size());
        assertEquals("Result value should match",expected_2, result.get(1).getDatapoints());
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

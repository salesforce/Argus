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
import org.junit.Test;
import com.salesforce.dva.argus.entity.Metric;

public class ConsectiveAboveValueMappingTest {
	private static final String TEST_SCOPE = "test-scope";
    private static final String TEST_METRIC = "test-metric";
    
    @Test(expected = IllegalArgumentException.class)
    public void testShiftTransformWithoutMetrics() {
        Transform transform = new MetricMappingTransform(new ConsectiveAboveValueMapping());
        List<Metric> metrics = null;
        List<String> constants = new ArrayList<String>();

        constants.add("1");
        constants.add("2");
        transform.transform(metrics, constants);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testShiftTransformWithoutThreshold() {
        Transform transform = new MetricMappingTransform(new ConsectiveAboveValueMapping());
        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);
        List<Metric> metrics = new ArrayList<Metric>();
        metrics.add(metric_1);
        
        List<String> constants = new ArrayList<String>();
        transform.transform(metrics, constants);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testShiftTransformWithOnlyOneThreshold() {
        Transform transform = new MetricMappingTransform(new ConsectiveAboveValueMapping());
        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);
        List<Metric> metrics = new ArrayList<Metric>();
        metrics.add(metric_1);
        
        List<String> constants = new ArrayList<String>();
        constants.add("1");
        transform.transform(metrics, constants);
    }
    
    @Test(expected = IllegalArgumentException.class)
    public void testShiftTransformWithZeroConsectiveThreshold() {
        Transform transform = new MetricMappingTransform(new ConsectiveAboveValueMapping());
        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);
        List<Metric> metrics = new ArrayList<Metric>();
        metrics.add(metric_1);
        
        List<String> constants = new ArrayList<String>();
        constants.add("1");
        constants.add("0");
        transform.transform(metrics, constants);
    }
    
    @Test
    public void testConsectiveAboveValueMappingSingleBaseCases() {
        Transform transform = new MetricMappingTransform(new ConsectiveAboveValueMapping());
        Map<Long, String> datapoints_1 = new HashMap<Long, String>();

        datapoints_1.put(1000L, "3.0");
        datapoints_1.put(2000L, "0.0");
        datapoints_1.put(3000L, "3.0");
        datapoints_1.put(4000L, "5.0");
        datapoints_1.put(5000L, "3.0");
        datapoints_1.put(6000L, "1.0");

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);
        metric_1.setDatapoints(datapoints_1);
        List<Metric> metrics = new ArrayList<Metric>();
        metrics.add(metric_1);
        
        List<String> constants = new ArrayList<String>();
        constants.add("3");
        constants.add("2");
        
        Map<Long, String> expected = new HashMap<Long, String>();
        expected.put(3000L, "3.0");
        expected.put(4000L, "5.0");
        expected.put(5000L, "3.0");
        
        List<Metric> result = transform.transform(metrics,constants);
        assertEquals("For single metric, base cases, result length should match",result.get(0).getDatapoints().size(), expected.size());
        assertEquals("For single metric, base cases, result value should match",expected, result.get(0).getDatapoints());
    }
    
    @Test
    public void testConsectiveAboveValueMappingSingleEdgeCases() {
        Transform transform = new MetricMappingTransform(new ConsectiveAboveValueMapping());
        Map<Long, String> datapoints_1 = new HashMap<Long, String>();

        datapoints_1.put(1000L, "3.0");
        datapoints_1.put(2000L, "0.0");
        datapoints_1.put(3000L, "3.0");
        datapoints_1.put(4000L, "5.0");
        datapoints_1.put(5000L, "3.0");
        datapoints_1.put(6000L, "9.0");

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);
        metric_1.setDatapoints(datapoints_1);
        List<Metric> metrics = new ArrayList<Metric>();
        metrics.add(metric_1);
        
        List<String> constants = new ArrayList<String>();
        constants.add("2.95");
        constants.add("1");
       
        Map<Long, String> expected = new HashMap<Long, String>();
        expected.put(1000L, "3.0");
        expected.put(3000L, "3.0");
        expected.put(4000L, "5.0");
        expected.put(5000L, "3.0");
        expected.put(6000L, "9.0");
        
        List<Metric> result = transform.transform(metrics,constants);
        assertEquals("For single metric, edge cases(Float, first, last match), result length should match",result.get(0).getDatapoints().size(), expected.size());
        assertEquals("For single metric, edge cases(Float, first, last match), result value should match",expected, result.get(0).getDatapoints());
    }
    
    @Test
    public void testConsectiveAboveValueMappingSingleZeroCases() {
        Transform transform = new MetricMappingTransform(new ConsectiveAboveValueMapping());
        Map<Long, String> datapoints_1 = new HashMap<Long, String>();
        datapoints_1.put(1000L, "-20.0");
        datapoints_1.put(2000L, "0.0");

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);
        metric_1.setDatapoints(datapoints_1);
        List<Metric> metrics = new ArrayList<Metric>();
        metrics.add(metric_1);
        
        List<String> constants = new ArrayList<String>();
        constants.add("0");
        constants.add("10");
       
        Map<Long, String> expected = new HashMap<Long, String>();
        
        List<Metric> result = transform.transform(metrics,constants);
        assertEquals("For single metric, zero cases, result length should match",result.get(0).getDatapoints().size(), expected.size());
        assertEquals("For single metric, zero cases, result value should match",expected, result.get(0).getDatapoints());
    }
    
    @Test
    public void testConsectiveAboveValueMappingSingleSerilesMissingDatasCases() {
        Transform transform = new MetricMappingTransform(new ConsectiveAboveValueMapping());
        Map<Long, String> datapoints_1 = new HashMap<Long, String>();
        datapoints_1.put(1000L, "-20.0");
        datapoints_1.put(2000L, "0.0");
        datapoints_1.put(6000L, "4.0");
        datapoints_1.put(11000L, "5.0");


        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);
        metric_1.setDatapoints(datapoints_1);
        List<Metric> metrics = new ArrayList<Metric>();
        metrics.add(metric_1);
        
        List<String> constants = new ArrayList<String>();
        constants.add("2");
        constants.add("2");
       
        Map<Long, String> expected = new HashMap<Long, String>();
        expected.put(6000L, "4.0");
        expected.put(11000L, "5.0");
        
        List<Metric> result = transform.transform(metrics,constants);
        assertEquals("For single metric, when metric missing data, should detect neigbour data points as consective points. result length should match",result.get(0).getDatapoints().size(), expected.size());
        assertEquals("For single metric, when metric missing data, should detect neigbour data points as consective points. result value should match",expected, result.get(0).getDatapoints());
    }
    
    
    @Test
    public void testConsectiveAboveValueMappingMultipleCases() {
        Transform transform = new MetricMappingTransform(new ConsectiveAboveValueMapping());
        Map<Long, String> datapoints_1 = new HashMap<Long, String>();
        datapoints_1.put(1000L, "3.0");
        datapoints_1.put(2000L, "0.0");
        datapoints_1.put(3000L, "3.0");
        datapoints_1.put(4000L, "5.0");
        datapoints_1.put(5000L, "3.0");
        datapoints_1.put(6000L, "1.0");
        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);
        metric_1.setDatapoints(datapoints_1);
               
        Map<Long, String> datapoints_2 = new HashMap<Long, String>();
        datapoints_2.put(1000L, "3.0");
        datapoints_2.put(2000L, "12.0");
        datapoints_2.put(3000L, "0.0");
        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);
        metric_2.setDatapoints(datapoints_2);
        
        List<Metric> metrics = new ArrayList<Metric>();
        metrics.add(metric_1);
        metrics.add(metric_2);

        List<String> constants = new ArrayList<String>();
        constants.add("2.5");
        constants.add("2");
        
        Map<Long, String> expected_1 = new HashMap<Long, String>();
        expected_1.put(3000L, "3.0");
        expected_1.put(4000L, "5.0");
        expected_1.put(5000L, "3.0");
        Map<Long, String> expected_2 = new HashMap<Long, String>();
        expected_2.put(1000L, "3.0");
        expected_2.put(2000L, "12.0");
        
        List<Metric> result = transform.transform(metrics,constants);
        assertEquals("For mulitple metric, the first seriles, result length should match",result.get(0).getDatapoints().size(), expected_1.size());
        assertEquals("For mulitple metric, the first seriles, result value should match",expected_1, result.get(0).getDatapoints());
        assertEquals("For mulitple metric, the second seriles, result length should match",result.get(1).getDatapoints().size(), expected_2.size());
        assertEquals("For mulitple metric, the second seriles, result value should match",expected_2, result.get(1).getDatapoints());
    }

}

/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
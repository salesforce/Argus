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
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class FillTransformTest {

    private static final String TEST_SCOPE = "test-scope";
    private static final String TEST_METRIC = "test-metric";

    @Test(expected = IllegalArgumentException.class)
    public void testFillTransformWithoutConstants() {
        Transform fillTransform = new FillTransform();
        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);
        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        fillTransform.transform(null, metrics, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFillWithMetricTransformWithIncorrectNumOfConstants() {
        Transform fillTransform = new FillTransform();
        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);
        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        fillTransform.transform(null, metrics, constants);
    }

    @Test(expected = SystemException.class)
    public void testFillWithMetricTransformWithInvalidIntervalFormat() {
        Transform fillTransform = new FillTransform();
        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);
        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();
        constants.add("1w");
        constants.add("2s");
        constants.add("100.0");
        constants.add(System.currentTimeMillis() + "");
        constants.add("false");
        
        fillTransform.transform(null, metrics, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFillWithMetricTransformWithInvalidIntervalValue() {
        Transform fillTransform = new FillTransform();
        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);
        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();
        constants.add("-1s");
        constants.add("2s");
        constants.add("100.0");
        constants.add("false");
        
        fillTransform.transform(null, metrics, constants);
    }

    @Test(expected = SystemException.class)
    public void testFillWithMetricTransformWithInvalidOffsetFormat() {
        Transform fillTransform = new FillTransform();
        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);
        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();
        constants.add("1s");
        constants.add("2w");
        constants.add("100.0");
        constants.add(System.currentTimeMillis() + "");
        constants.add("false");
        
        fillTransform.transform(null, metrics, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFillLineTransformWithIncorrectNumOfConstants() {
        Transform fillTransform = new FillTransform();
        List<String> constants = new ArrayList<String>(5);

        fillTransform.transform(null, null, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFillLineTransformWithInvalidStartFormat() {
        Transform fillTransform = new FillTransform();
        List<String> constants = new ArrayList<String>(5);

        constants.add("1w");
        fillTransform.transform(null, null, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFillLineTransformWithStartGreaterEqualEnd() {
        Transform fillTransform = new FillTransform();
        List<String> constants = new ArrayList<String>(5);

        constants.add("10s");
        constants.add("2s");
        fillTransform.transform(null, null, constants);
    }

    @Test
    public void testWithIntervalLessThanFillRangeOffsetPositive() {
        Transform fillTransform = new FillTransform();
        Map<Long, Double> datapoints = new HashMap<Long, Double>();

        datapoints.put(1000L, 1.0);
        datapoints.put(4000L, 4.0);
        datapoints.put(5000L, 5.0);
        datapoints.put(6000L, 6.0);

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        constants.add("1s");
        constants.add("1s");
        constants.add("100.0");
        constants.add(System.currentTimeMillis() + "");
        constants.add("false");

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(1000L, 1.0);
        expected.put(3000L, 100.0);
        expected.put(4000L, 100.0);
        expected.put(5000L, 5.0);
        expected.put(6000L, 6.0);

        List<Metric> result = fillTransform.transform(null, metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), 5);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testWithIntervalEqualsToFillRangeOffsetNegative() {
        Transform fillTransform = new FillTransform();
        Map<Long, Double> datapoints = new HashMap<Long, Double>();

        datapoints.put(1000L, 1.0);
        datapoints.put(4000L, 4.0);
        datapoints.put(5000L, 5.0);
        datapoints.put(6000L, 6.0);

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        constants.add("2s");
        constants.add("-1s");
        constants.add("100.0");
        constants.add(System.currentTimeMillis() + "");
        constants.add("false");

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(1000L, 1.0);
        expected.put(2000L, 100.0);
        expected.put(4000L, 4.0);
        expected.put(5000L, 5.0);
        expected.put(6000L, 6.0);

        List<Metric> result = fillTransform.transform(null, metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), 5);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testWithIntervalGreaterThanFillRangeOffsetNegative() {
        Transform fillTransform = new FillTransform();
        Map<Long, Double> datapoints = new HashMap<Long, Double>();

        datapoints.put(1000L, 1.0);
        datapoints.put(4000L, 4.0);
        datapoints.put(5000L, 5.0);
        datapoints.put(6000L, 6.0);

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        constants.add("3s");
        constants.add("-1s");
        constants.add("100.0");
        constants.add(System.currentTimeMillis() + "");
        constants.add("false");

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(1000L, 1.0);
        expected.put(4000L, 4.0);
        expected.put(5000L, 5.0);
        expected.put(6000L, 6.0);

        List<Metric> result = fillTransform.transform(null, metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), 4);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testWithMultipeFillRangeOffsetPositive() {
        Transform fillTransform = new FillTransform();
        Map<Long, Double> datapoints = new HashMap<Long, Double>();

        datapoints.put(1000L, 1.0);
        datapoints.put(3000L, 3.0);
        datapoints.put(6000L, 6.0);

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        constants.add("100s");
        constants.add("1s");
        constants.add("100.0");
        constants.add(System.currentTimeMillis() + "");
        constants.add("false");

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(1000L, 1.0);
        expected.put(3000L, 3.0);
        expected.put(6000L, 6.0);

        List<Metric> result = fillTransform.transform(null, metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), 3);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testMetricListWithMultipeFillRangeOffsetNegative() {
        Transform fillTransform = new FillTransform();
        Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

        datapoints_1.put(1000L, 1.0);
        datapoints_1.put(3000L, 3.0);
        datapoints_1.put(6000L, 6.0);

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        Map<Long, Double> datapoints_2 = new HashMap<Long, Double>();

        datapoints_2.put(1000L, 21.0);
        datapoints_2.put(3000L, 23.0);
        datapoints_2.put(6000L, 26.0);

        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);

        List<String> constants = new ArrayList<String>();

        constants.add("1s");
        constants.add("-1s");
        constants.add("100.0");
        constants.add(System.currentTimeMillis() + "");
        constants.add("false");

        Map<Long, Double> expected_1 = new HashMap<Long, Double>();

        expected_1.put(1000L, 100.0);
        expected_1.put(3000L, 100.0);
        expected_1.put(4000L, 100.0);
        expected_1.put(6000L, 6.0);

        Map<Long, Double> expected_2 = new HashMap<Long, Double>();

        expected_2.put(1000L, 100.0);
        expected_2.put(3000L, 100.0);
        expected_2.put(4000L, 100.0);
        expected_2.put(6000L, 26.0);

        List<Metric> result = fillTransform.transform(null, metrics, constants);

        assertEquals(result.size(), 2);
        assertEquals(result.get(1).getDatapoints().size(), 4);
        assertEquals(expected_1, result.get(0).getDatapoints());
        assertEquals(expected_2, result.get(1).getDatapoints());
    }

    @Test
    public void testWithOnlyOneDPOffsetPostitiveShouldShiftOnly() {
        Transform fillTransform = new FillTransform();
        Map<Long, Double> datapoints = new HashMap<Long, Double>();

        datapoints.put(1000L, 1.0);

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        constants.add("100s");
        constants.add("1s");
        constants.add("100.0");
        constants.add(System.currentTimeMillis() + "");
        constants.add("false");

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(1000L, 1.0);

        List<Metric> result = fillTransform.transform(null, metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), 1);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testNoMetricWithMissingPointAndOtherTimeUnitOffsetPositive() {
        Transform fillTransform = new FillTransform();
        Map<Long, Double> datapoints = new HashMap<Long, Double>();

        datapoints.put(1000L, 1.0);
        datapoints.put(2000L, 2.0);
        datapoints.put(3000L, 3.0);

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        constants.add("5m");
        constants.add("2s");
        constants.add("100.0");
        constants.add(System.currentTimeMillis() + "");
        constants.add("false");

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(1000L, 1.0);
        expected.put(2000L, 2.0);
        expected.put(3000L, 3.0);

        List<Metric> result = fillTransform.transform(null, metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), 3);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testFillLineWithIntervalLessThanFillRangeOffsetPositive() {
        Transform fillTransform = new FillTransform();
        List<String> constants = new ArrayList<String>();

        constants.add("1000");
        constants.add("3000");
        constants.add("1s");
        constants.add("1s");
        constants.add("100.0");
        constants.add(String.valueOf(System.currentTimeMillis()));
        constants.add("true");

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(2000L, 100.0);
        expected.put(3000L, 100.0);
        expected.put(4000L, 100.0);

        List<Metric> result = fillTransform.transform(null, null, constants);

        assertEquals(result.get(0).getDatapoints().size(), 3);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testFillLineWithIntervalEqualsToFillRangeOffsetNegative() {
        Transform fillTransform = new FillTransform();
        List<String> constants = new ArrayList<String>();

        constants.add("1000");
        constants.add("3000");
        constants.add("2s");
        constants.add("-1s");
        constants.add("100.0");
        constants.add(String.valueOf(System.currentTimeMillis()));
        constants.add("true");

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(-1000L, 100.0);
        expected.put(1000L, 100.0);

        List<Metric> result = fillTransform.transform(null, null, constants);

        assertEquals(result.get(0).getDatapoints().size(), 2);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testFillLineWithIntervalGreaterThanFillRangeOffsetNegative() {
        Transform fillTransform = new FillTransform();
        List<String> constants = new ArrayList<String>();

        constants.add("1000");
        constants.add("3000");
        constants.add("3s");
        constants.add("-1s");
        constants.add("100.0");
        constants.add(String.valueOf(System.currentTimeMillis()));
        constants.add("true");

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(-1000L, 100.0);
        expected.put(2000L, 100.0);

        List<Metric> result = fillTransform.transform(null, null, constants);

        assertEquals(result.get(0).getDatapoints().size(), 2);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testFillLineWithFillRangeZeroAfterSnappingOffsetZero() {
        Transform fillTransform = new FillTransform();
        List<String> constants = new ArrayList<String>();

        constants.add("1000");
        constants.add("3000");
        constants.add("4s");
        constants.add("0s");
        constants.add("100.0");
        constants.add(String.valueOf(System.currentTimeMillis()));
        constants.add("true");

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(0L, 100.0);

        List<Metric> result = fillTransform.transform(null, null, constants);

        assertEquals(result.get(0).getDatapoints().size(), 1);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testMinusTimestamp_1() {
    	long now = System.currentTimeMillis();
    	
    	Transform fillTransform = new FillTransform();
        List<String> constants = new ArrayList<String>();

        constants.add("-1d");
        constants.add("-12h");
        constants.add("10m");
        constants.add("0m");
        constants.add("100.0");
        constants.add(String.valueOf(now));
        constants.add("true");

        Long expectedStartTimestamp = now - 1L * 86400L * 1000L;
        Long expectedEndTimestamp = now - 12L * 3600L * 1000L;
        int expectedSize = (int) ((expectedEndTimestamp - expectedStartTimestamp) / (10 * 60 * 1000) + 1);
        List<Metric> result = fillTransform.transform(null, null, constants);

        assertEquals(result.get(0).getDatapoints().size(), expectedSize);

        Long[] timestampSet = new Long[result.get(0).getDatapoints().keySet().size()];

        result.get(0).getDatapoints().keySet().toArray(timestampSet);
        assertEquals(true, timestampSet[0] - expectedStartTimestamp <= 1000L);
        assertEquals(true, timestampSet[timestampSet.length - 1] - expectedEndTimestamp <= 1000L);
        assertEquals(new HashSet<Double>(Arrays.asList(100.0)), new HashSet<Double>(result.get(0).getDatapoints().values()));
    }

    @Test
    public void testMinusTimestamp_2() {
    	long now = System.currentTimeMillis();
    	
        Transform fillTransform = new FillTransform();
        List<String> constants = new ArrayList<String>();

        constants.add("-7d");
        constants.add("-0m");
        constants.add("10m");
        constants.add("0m");
        constants.add("100.0");
        constants.add(String.valueOf(now));
        constants.add("true");
        
        Long expectedStartTimestamp = now - 7L * 86400L * 1000L;
        Long expectedEndTimestamp = now - 0L * 60L * 1000L;
        int expectedSize = (int) ((expectedEndTimestamp - expectedStartTimestamp) / (10 * 60 * 1000) + 1);
        List<Metric> result = fillTransform.transform(null, null, constants);

        assertEquals(result.get(0).getDatapoints().size(), expectedSize);

        Long[] timestampSet = new Long[result.get(0).getDatapoints().keySet().size()];

        result.get(0).getDatapoints().keySet().toArray(timestampSet);
        assertEquals(true, timestampSet[0] - expectedStartTimestamp <= 1000L);
        assertEquals(true, timestampSet[timestampSet.length - 1] - expectedEndTimestamp <= 1000L);
        assertEquals(new HashSet<Double>(Arrays.asList(100.0)), new HashSet<Double>(result.get(0).getDatapoints().values()));
    }

    @Test
    public void testEmptyTimestamp() {
        Transform fillTransform = new FillTransform();
        List<String> constants = new ArrayList<String>();

        constants.add("-1d");
        constants.add("");
        constants.add("10m");
        constants.add("0m");
        constants.add("100.0");
        constants.add(String.valueOf(System.currentTimeMillis()));
        constants.add("true");

        Long expectedStartTimestamp = System.currentTimeMillis() - 1L * 86400L * 1000L;
        Long expectedEndTimestamp = System.currentTimeMillis();
        int expectedSize = (int) ((expectedEndTimestamp - expectedStartTimestamp) / (10 * 60 * 1000) + 1);
        List<Metric> result = fillTransform.transform(null, null, constants);

        assertEquals(result.get(0).getDatapoints().size(), expectedSize);

        Long[] timestampSet = new Long[result.get(0).getDatapoints().keySet().size()];

        result.get(0).getDatapoints().keySet().toArray(timestampSet);
        assertEquals(true, timestampSet[0] - expectedStartTimestamp <= 1000L);
        assertEquals(true, timestampSet[timestampSet.length - 1] - expectedEndTimestamp <= 1000L);
        assertEquals(new HashSet<Double>(Arrays.asList(100.0)), new HashSet<Double>(result.get(0).getDatapoints().values()));
    }
    
    @Test
    public void testFillTransform_MetricWithEmptyDatapointsMap() {
    	Transform fillTransform = new FillTransform();
    	List<String> constants = new ArrayList<>();
    	
    	Metric m = new Metric("scope", "metric");
    	
    	constants.add("100s");
        constants.add("1s");
        constants.add("100.0");
        constants.add(System.currentTimeMillis() + "");
        constants.add("false");
        
        List<Metric> metrics = fillTransform.transform(null, Arrays.asList(m), constants);
    	assertEquals(metrics.size(), 1);
    }
    
    @Test
    public void testFillTransform_EmptyMetricsList() {
    	Transform fillTransform = new FillTransform();
    	List<String> constants = new ArrayList<>();
    	
    	constants.add("100s");
        constants.add("1s");
        constants.add("100.0");
        constants.add(System.currentTimeMillis() + "");
        constants.add("false");
        
        List<Metric> metrics = fillTransform.transform(null, Arrays.asList(), constants);
    	assertEquals(metrics.size(), 0);
    }
    
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

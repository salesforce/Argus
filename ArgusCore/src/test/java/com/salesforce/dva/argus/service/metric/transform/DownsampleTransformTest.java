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

public class DownsampleTransformTest {

    private static final String TEST_SCOPE = "test-scope";
    private static final String TEST_METRIC = "test-metric";

    @Test(expected = IllegalArgumentException.class)
    public void testDownsampleTransformWithIllegalUnit() {
        Transform downsampleTransform = new DownsampleTransform();
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.put(1000L, "1");

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        constants.add("2k-avg");
        downsampleTransform.transform(metrics, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDownsampelTransformWithIllegalType() {
        Transform downsampleTransform = new DownsampleTransform();
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.put(1000L, "1");

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        constants.add("2s-foobar");
        downsampleTransform.transform(metrics, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDownsampleTransformWithoutUnit() {
        Transform downsampleTransform = new DownsampleTransform();
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.put(1000L, "1");

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        constants.add("-min");

        downsampleTransform.transform(metrics, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDownsampleTransformWithoutType() {
        Transform downsampleTransform = new DownsampleTransform();
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.put(1000L, "1");

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        constants.add("6s-");
        downsampleTransform.transform(metrics, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDownsampleTransformWithIllegalExpFormat() {
        Transform downsampleTransform = new DownsampleTransform();
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.put(1000L, "1");

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        constants.add("***test");
        downsampleTransform.transform(metrics, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDownsampleTransformWithoutMetrics() {
        Transform downsampleTransform = new DownsampleTransform();
        List<Metric> metrics = null;
        List<String> constants = new ArrayList<String>();

        constants.add("2");
        constants.add("average");
        downsampleTransform.transform(metrics, constants);
    }

    @Test
    public void testDownsampleTransformAvgOneMetric() {
        Transform downsampleTransform = new DownsampleTransform();
        Map<Long, String> datapoints_1 = new HashMap<Long, String>();

        datapoints_1.put(1000L, "1");
        datapoints_1.put(2000L, "2");
        datapoints_1.put(3000L, "3");
        datapoints_1.put(4000L, "4");
        datapoints_1.put(5000L, "5");
        datapoints_1.put(6000L, "6");
        datapoints_1.put(7000L, "7");
        datapoints_1.put(8000L, "8");
        datapoints_1.put(9000L, "9");

        Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);

        List<String> constants = new ArrayList<String>();

        constants.add("2s-avg");

        Map<Long, String> expected_1 = new HashMap<Long, String>();

        expected_1.put(1000L, "2.0");
        expected_1.put(4000L, "5.0");
        expected_1.put(7000L, "8.0");

        List<Metric> result = downsampleTransform.transform(metrics, constants);

        assertEquals(result.size(), 1);
        assertEquals(expected_1, result.get(0).getDatapoints());
    }

    @Test
    public void testDownsampleTransformMinOneMetric() {
        Transform downsampleTransform = new DownsampleTransform();
        Map<Long, String> datapoints_1 = new HashMap<Long, String>();

        datapoints_1.put(1000L, "1");
        datapoints_1.put(2000L, "2");
        datapoints_1.put(3000L, "3");
        datapoints_1.put(4000L, "4");
        datapoints_1.put(5000L, "5");
        datapoints_1.put(6000L, "6");
        datapoints_1.put(7000L, "7");
        datapoints_1.put(8000L, "8");
        datapoints_1.put(9000L, "9");

        Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);

        List<String> constants = new ArrayList<String>();

        constants.add("2s-min");

        Map<Long, String> expected_1 = new HashMap<Long, String>();

        expected_1.put(1000L, "1.0");
        expected_1.put(4000L, "4.0");
        expected_1.put(7000L, "7.0");

        List<Metric> result = downsampleTransform.transform(metrics, constants);

        assertEquals(result.size(), 1);
        assertEquals(expected_1, result.get(0).getDatapoints());
    }

    @Test
    public void testDownsampleTransformMaxOneMetric() {
        Transform downsampleTransform = new DownsampleTransform();
        Map<Long, String> datapoints_1 = new HashMap<Long, String>();

        datapoints_1.put(1000L, "1");
        datapoints_1.put(2000L, "2");
        datapoints_1.put(3000L, "3");
        datapoints_1.put(4000L, "4");
        datapoints_1.put(5000L, "5");
        datapoints_1.put(6000L, "6");
        datapoints_1.put(7000L, "7");
        datapoints_1.put(8000L, "8");
        datapoints_1.put(9000L, "9");

        Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);

        List<String> constants = new ArrayList<String>();

        constants.add("2s-max");

        Map<Long, String> expected_1 = new HashMap<Long, String>();

        expected_1.put(1000L, "3.0");
        expected_1.put(4000L, "6.0");
        expected_1.put(7000L, "9.0");

        List<Metric> result = downsampleTransform.transform(metrics, constants);

        assertEquals(result.size(), 1);
        assertEquals(expected_1, result.get(0).getDatapoints());
    }

    @Test
    public void testDownsampleTransformSumOneMetric() {
        Transform downsampleTransform = new DownsampleTransform();
        Map<Long, String> datapoints_1 = new HashMap<Long, String>();

        datapoints_1.put(1000L, "1");
        datapoints_1.put(2000L, "2");
        datapoints_1.put(3000L, "3");
        datapoints_1.put(4000L, "4");
        datapoints_1.put(5000L, "5");
        datapoints_1.put(6000L, "6");
        datapoints_1.put(7000L, "7");
        datapoints_1.put(8000L, "8");
        datapoints_1.put(9000L, "9");

        Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);

        List<String> constants = new ArrayList<String>();

        constants.add("2s-sum");

        Map<Long, String> expected_1 = new HashMap<Long, String>();

        expected_1.put(1000L, "6.0");
        expected_1.put(4000L, "15.0");
        expected_1.put(7000L, "24.0");

        List<Metric> result = downsampleTransform.transform(metrics, constants);

        assertEquals(result.size(), 1);
        assertEquals(expected_1, result.get(0).getDatapoints());
    }

    @Test
    public void testDownsampleTransformDevOneMetric() {
        Transform downsampleTransform = new DownsampleTransform();
        Map<Long, String> datapoints_1 = new HashMap<Long, String>();

        datapoints_1.put(1000L, "1");
        datapoints_1.put(2000L, "2");
        datapoints_1.put(3000L, "3");
        datapoints_1.put(4000L, "4");
        datapoints_1.put(5000L, "6");
        datapoints_1.put(6000L, "8");
        datapoints_1.put(7000L, "9");
        datapoints_1.put(8000L, "12");
        datapoints_1.put(9000L, "15");

        Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);

        List<String> constants = new ArrayList<String>();

        constants.add("2s-dev");

        Map<Long, String> expected_1 = new HashMap<Long, String>();

        expected_1.put(1000L, "1.0");
        expected_1.put(4000L, "2.0");
        expected_1.put(7000L, "3.0");

        List<Metric> result = downsampleTransform.transform(metrics, constants);

        assertEquals(result.size(), 1);
        assertEquals(expected_1, result.get(0).getDatapoints());
    }

    @Test
    public void testDownsampleTransformAvgMultipleMetrics() {
        Transform downsampleTransform = new DownsampleTransform();
        Map<Long, String> datapoints_1 = new HashMap<Long, String>();

        datapoints_1.put(1000L, "1");
        datapoints_1.put(2000L, "2");
        datapoints_1.put(3000L, "3");
        datapoints_1.put(4000L, "4");
        datapoints_1.put(5000L, "5");
        datapoints_1.put(6000L, "6");
        datapoints_1.put(7000L, "7");
        datapoints_1.put(8000L, "8");
        datapoints_1.put(9000L, "9");

        Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        Map<Long, String> datapoints_2 = new HashMap<Long, String>();

        datapoints_2.put(1000L, "100");
        datapoints_2.put(2000L, "200");
        datapoints_2.put(3000L, "300");
        datapoints_2.put(4000L, "400");
        datapoints_2.put(5000L, "500");
        datapoints_2.put(6000L, "600");
        datapoints_2.put(7000L, "700");
        datapoints_2.put(8000L, "800");
        datapoints_2.put(9000L, "900");

        Metric metric_2 = new Metric(TEST_SCOPE + "2", TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);

        List<String> constants = new ArrayList<String>();

        constants.add("2s-avg");

        Map<Long, String> expected_1 = new HashMap<Long, String>();

        expected_1.put(1000L, "2.0");
        expected_1.put(4000L, "5.0");
        expected_1.put(7000L, "8.0");

        Map<Long, String> expected_2 = new HashMap<Long, String>();

        expected_2.put(1000L, "200.0");
        expected_2.put(4000L, "500.0");
        expected_2.put(7000L, "800.0");

        List<Metric> result = downsampleTransform.transform(metrics, constants);

        assertEquals(result.size(), 2);
        assertEquals(expected_1, result.get(0).getDatapoints());
        assertEquals(expected_2, result.get(1).getDatapoints());
    }

    @Test
    public void testDownsampleTransformWindowGreaterThanRangeOneMetric() {
        Transform downsampleTransform = new DownsampleTransform();
        Map<Long, String> datapoints_1 = new HashMap<Long, String>();

        datapoints_1.put(1000L, "1");
        datapoints_1.put(2000L, "2");
        datapoints_1.put(3000L, "3");
        datapoints_1.put(4000L, "4");
        datapoints_1.put(5000L, "5");
        datapoints_1.put(6000L, "6");
        datapoints_1.put(7000L, "7");
        datapoints_1.put(8000L, "8");
        datapoints_1.put(9000L, "9");

        Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);

        List<String> constants = new ArrayList<String>();

        constants.add("100s-avg");

        Map<Long, String> expected_1 = new HashMap<Long, String>();

        expected_1.put(1000L, "5.0");

        List<Metric> result = downsampleTransform.transform(metrics, constants);

        assertEquals(result.size(), 1);
        assertEquals(expected_1, result.get(0).getDatapoints());
    }

    @Test
    public void testDownsampleTransformWindowLessThanUnitOneMetric() {
        Transform downsampleTransform = new DownsampleTransform();
        Map<Long, String> datapoints_1 = new HashMap<Long, String>();

        datapoints_1.put(1000L, "1");
        datapoints_1.put(5000L, "5");
        datapoints_1.put(9000L, "9");

        Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);

        List<String> constants = new ArrayList<String>();

        constants.add("2s-avg");

        Map<Long, String> expected_1 = new HashMap<Long, String>();

        expected_1.put(1000L, "1.0");
        expected_1.put(5000L, "5.0");
        expected_1.put(9000L, "9.0");

        List<Metric> result = downsampleTransform.transform(metrics, constants);

        assertEquals(result.size(), 1);
        assertEquals(expected_1, result.get(0).getDatapoints());
    }

    @Test
    public void testDownsampleTransformMinOneMetricHavingNull() {
        Transform downsampleTransform = new DownsampleTransform();
        Map<Long, String> datapoints_1 = new HashMap<Long, String>();

        datapoints_1.put(1000L, null);
        datapoints_1.put(2000L, null);
        datapoints_1.put(3000L, "3");
        datapoints_1.put(4000L, null);
        datapoints_1.put(5000L, "5");
        datapoints_1.put(6000L, null);
        datapoints_1.put(7000L, "7");
        datapoints_1.put(8000L, null);
        datapoints_1.put(9000L, "9");

        Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);

        List<String> constants = new ArrayList<String>();

        constants.add("2s-min");

        Map<Long, String> expected_1 = new HashMap<Long, String>();

        expected_1.put(1000L, "0.0");
        expected_1.put(4000L, "0.0");
        expected_1.put(7000L, "0.0");

        List<Metric> result = downsampleTransform.transform(metrics, constants);

        assertEquals(result.size(), 1);
        assertEquals(expected_1, result.get(0).getDatapoints());
    }
    
    @Test//_W-2905322
    public void testDownsampleTransformBug_OnHourLevel() {
        Transform downsampleTransform = new DownsampleTransform();
        Map<Long, String> datapoints_1 = new HashMap<Long, String>();

        datapoints_1.put(1453798890000L, "1");
        datapoints_1.put(1453802750000L, "2");
        datapoints_1.put(1453806510000L, "3");
        datapoints_1.put(1453809690000L, "4");

        Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);

        List<String> constants = new ArrayList<String>();

        constants.add("1h-min");

        Map<Long, String> expected_1 = new HashMap<Long, String>();
        
        expected_1.put(1453798800000L, "1.0");
        expected_1.put(1453802400000L, "2.0");
        expected_1.put(1453806000000L, "3.0");
        expected_1.put(1453809600000L, "4.0");

        List<Metric> result = downsampleTransform.transform(metrics, constants);

        assertEquals(result.size(), 1);
        assertEquals(expected_1, result.get(0).getDatapoints());
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
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

public class PercentileTransformTest {

    private static final String TEST_SCOPE = "test-scope";
    private static final String TEST_METRIC = "test-metric";

    @Test
    public void testPercentileTransformWithOneConstantShareCommonDPs() {
        Transform percentileTransform = new MetricReducerOrMappingWithConstantTransform(new PercentileValueReducerOrMapping());
        Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

        datapoints_1.put(1000L, 20.0);

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        Map<Long, Double> datapoints_2 = new HashMap<Long, Double>();

        datapoints_2.put(1000L, 15.0);

        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        Map<Long, Double> datapoints_3 = new HashMap<Long, Double>();

        datapoints_3.put(1000L, 50.0);

        Metric metric_3 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_3.setDatapoints(datapoints_3);

        Map<Long, Double> datapoints_4 = new HashMap<Long, Double>();

        datapoints_4.put(1000L, 35.0);

        Metric metric_4 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_4.setDatapoints(datapoints_4);

        Map<Long, Double> datapoints_5 = new HashMap<Long, Double>();

        datapoints_5.put(1000L, 40.0);

        Metric metric_5 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_5.setDatapoints(datapoints_5);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);
        metrics.add(metric_3);
        metrics.add(metric_4);
        metrics.add(metric_5);

        List<String> constants = new ArrayList<String>();

        constants.add("30");

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(1000L, 19.0);

        List<Metric> result = percentileTransform.transform(null, metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), 1);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testPercentileTransformWithOneConstantShareSomeCommonDPs() {
        Transform percentileTransform = new MetricReducerOrMappingWithConstantTransform(new PercentileValueReducerOrMapping());
        Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

        datapoints_1.put(1000L, 20.0);
        datapoints_1.put(2000L, 20.0);

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        Map<Long, Double> datapoints_2 = new HashMap<Long, Double>();

        datapoints_2.put(1000L, 15.0);
        datapoints_2.put(3000L, 15.0);

        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        Map<Long, Double> datapoints_3 = new HashMap<Long, Double>();

        datapoints_3.put(1000L, 50.0);
        datapoints_3.put(4000L, 50.0);

        Metric metric_3 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_3.setDatapoints(datapoints_3);

        Map<Long, Double> datapoints_4 = new HashMap<Long, Double>();

        datapoints_4.put(1000L, 35.0);
        datapoints_4.put(5000L, 35.0);

        Metric metric_4 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_4.setDatapoints(datapoints_4);

        Map<Long, Double> datapoints_5 = new HashMap<Long, Double>();

        datapoints_5.put(1000L, 40.0);
        datapoints_5.put(6000L, 40.0);

        Metric metric_5 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_5.setDatapoints(datapoints_5);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);
        metrics.add(metric_3);
        metrics.add(metric_4);
        metrics.add(metric_5);

        List<String> constants = new ArrayList<String>();

        constants.add("30");

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(1000L, 19.0);

        List<Metric> result = percentileTransform.transform(null, metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), 1);
        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testPercentileTransformWithOneConstantShareNoCommonDPs() {
        Transform percentileTransform = new MetricReducerOrMappingWithConstantTransform(new PercentileValueReducerOrMapping());
        Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

        datapoints_1.put(1000L, 20.0);

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        Map<Long, Double> datapoints_2 = new HashMap<Long, Double>();

        datapoints_2.put(2000L, 15.0);

        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        Map<Long, Double> datapoints_3 = new HashMap<Long, Double>();

        datapoints_3.put(3000L, 50.0);

        Metric metric_3 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_3.setDatapoints(datapoints_3);

        Map<Long, Double> datapoints_4 = new HashMap<Long, Double>();

        datapoints_4.put(4000L, 35.0);

        Metric metric_4 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_4.setDatapoints(datapoints_4);

        Map<Long, Double> datapoints_5 = new HashMap<Long, Double>();

        datapoints_5.put(5000L, 40.0);

        Metric metric_5 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_5.setDatapoints(datapoints_5);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);
        metrics.add(metric_3);
        metrics.add(metric_4);
        metrics.add(metric_5);

        List<String> constants = new ArrayList<String>();

        constants.add("30");

        Map<Long, Double> expected = new HashMap<Long, Double>();
        List<Metric> result = percentileTransform.transform(null, metrics, constants);

        assertEquals(result.get(0).getDatapoints().size(), 0);
        assertEquals(expected, result.get(0).getDatapoints());
    }
    
    
    @Test
    public void testPercentileTransformWithOneConstantShareNoCommonDPs_fullJoinIndicator() {
        Transform percentileTransform = new MetricReducerOrMappingWithConstantTransform(new PercentileValueReducerOrMapping());
        Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();
        Map<Long, Double> expected = new HashMap<Long, Double>();

        datapoints_1.put(1000L, 20.0);
        expected.put(1000L, 20.0);

        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_1.setDatapoints(datapoints_1);

        Map<Long, Double> datapoints_2 = new HashMap<Long, Double>();

        datapoints_2.put(2000L, 15.0);
        expected.put(2000L, 15.0);

        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_2.setDatapoints(datapoints_2);

        Map<Long, Double> datapoints_3 = new HashMap<Long, Double>();

        datapoints_3.put(3000L, 50.0);
        expected.put(3000L, 50.0);

        Metric metric_3 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_3.setDatapoints(datapoints_3);

        Map<Long, Double> datapoints_4 = new HashMap<Long, Double>();

        datapoints_4.put(4000L, 35.0);
        expected.put(4000L, 35.0);

        Metric metric_4 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_4.setDatapoints(datapoints_4);

        Map<Long, Double> datapoints_5 = new HashMap<Long, Double>();

        datapoints_5.put(5000L, 40.0);
        expected.put(5000L, 40.0);

        Metric metric_5 = new Metric(TEST_SCOPE, TEST_METRIC);

        metric_5.setDatapoints(datapoints_5);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);
        metrics.add(metric_3);
        metrics.add(metric_4);
        metrics.add(metric_5);

        List<String> constants = new ArrayList<String>();

        constants.add("30");
        constants.add("UNION");

        List<Metric> result = percentileTransform.transform(null, metrics, constants);

        assertEquals(expected.size(), result.get(0).getDatapoints().size());
        assertEquals(expected, result.get(0).getDatapoints());
    }
    

    @Test(expected = IllegalArgumentException.class)
    public void testPercentileTransformWithoutConstants() {
        Transform percentileTransform = new MetricReducerOrMappingWithConstantTransform(new PercentileValueReducerOrMapping());
        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);
        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>();

        percentileTransform.transform(null, metrics, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPercentileTransformWithIllegalTimeunit() {
        Transform percentileTransform = new MetricReducerOrMappingWithConstantTransform(new PercentileValueReducerOrMapping());
        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);
        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>(1);

        constants.add("90");
        constants.add("3w");
        percentileTransform.transform(null, metrics, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPercentileTransformWithIllegalPercentile() {
        Transform percentileTransform = new MetricReducerOrMappingWithConstantTransform(new PercentileValueReducerOrMapping());
        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);
        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>(1);

        constants.add("101");
        constants.add("3s");
        percentileTransform.transform(null, metrics, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPercentileTransformWithoutMetrics() {
        Transform percentileTransform = new MetricReducerOrMappingWithConstantTransform(new PercentileValueReducerOrMapping());
        List<Metric> metrics = null;
        List<String> constants = new ArrayList<String>();

        constants.add("90");
        constants.add("3h");
        percentileTransform.transform(null, metrics, constants);
    }

    @Test
    public void testPercentile95TransformSingleMetric_LegacySupport() {
        Transform percentileTransform = new MetricReducerOrMappingWithConstantTransform(new PercentileValueReducerOrMapping());
        Map<Long, Double> datapoints = new HashMap<Long, Double>();

        datapoints.put(1000L, 1.0);
        datapoints.put(2000L, 2.0);
        datapoints.put(3000L, 3.0);
        datapoints.put(4000L, 4.0);
        datapoints.put(5000L, 5.0);
        datapoints.put(6000L, 6.0);
        datapoints.put(7000L, 7.0);
        datapoints.put(8000L, 8.0);
        datapoints.put(9000L, 9.0);
        datapoints.put(10000L, 10.0);

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>(1);

        constants.add("90");
        constants.add("3s");

        List<Metric> result = percentileTransform.transform(null, metrics, constants);
        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(1000L, 9.9);
        assertEquals(expected, result.get(0).getDatapoints());
    }
    
    @Test
    public void testPercentile95TransformMultipleMetrics_LegacySupport() {
        Transform percentileTransform = new MetricReducerOrMappingWithConstantTransform(new PercentileValueReducerOrMapping());
        Map<Long, Double> datapoints = new HashMap<Long, Double>();

        datapoints.put(1000L, 1.0);
        datapoints.put(2000L, 2.0);
        datapoints.put(3000L, 3.0);
        datapoints.put(4000L, 4.0);
        datapoints.put(5000L, 5.0);
        datapoints.put(6000L, 6.0);
        datapoints.put(7000L, 7.0);
        datapoints.put(8000L, 8.0);
        datapoints.put(9000L, 9.0);
        datapoints.put(10000L, 10.0);

        Metric metric1 = new Metric(TEST_SCOPE, TEST_METRIC + "1");

        metric1.setDatapoints(datapoints);
        
        datapoints = new HashMap<Long, Double>();

        datapoints.put(1000L, 1.0);
        datapoints.put(2000L, 2.0);
        datapoints.put(3000L, 3.0);
        datapoints.put(4000L, 4.0);
        datapoints.put(5000L, 5.0);
        datapoints.put(6000L, 6.0);
        datapoints.put(7000L, 7.0);
        datapoints.put(8000L, 8.0);
        datapoints.put(9000L, 9.0);
        datapoints.put(10000L, 10.0);

        Metric metric2 = new Metric(TEST_SCOPE, TEST_METRIC + "2");

        metric2.setDatapoints(datapoints);

        List<Metric> metrics = Arrays.asList(metric1, metric2);
        List<String> constants = Arrays.asList("90", "3s");
        
        List<Metric> result = percentileTransform.transform(null, metrics, constants);
        
        Map<Long, Double> expected = new HashMap<Long, Double>();
        expected.put(1000L, 9.9);
        
        assertEquals(2, result.size());
        assertEquals(expected, result.get(0).getDatapoints());
        assertEquals(expected, result.get(1).getDatapoints());
    }

    @Test
    public void testPercentile95TransformSingleMetric_individualEvaluation() {
        Transform percentileTransform = new MetricReducerOrMappingWithConstantTransform(new PercentileValueReducerOrMapping());
        Map<Long, Double> datapoints = new HashMap<Long, Double>();

        datapoints.put(1000L, 1.0);
        datapoints.put(2000L, 2.0);
        datapoints.put(3000L, 3.0);
        datapoints.put(4000L, 4.0);
        datapoints.put(5000L, 5.0);
        datapoints.put(6000L, 6.0);
        datapoints.put(7000L, 7.0);
        datapoints.put(8000L, 8.0);
        datapoints.put(9000L, 9.0);
        datapoints.put(10000L, 10.0);

        Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

        metric.setDatapoints(datapoints);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric);

        List<String> constants = new ArrayList<String>(1);

        constants.add("90");
        constants.add("individual");

        List<Metric> result = percentileTransform.transform(null, metrics, constants);
        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(1000L, 9.9);
        assertEquals(expected, result.get(0).getDatapoints());
    }
    
    @Test
    public void testPercentile95TransformMultipleMetrics_individualEvaluation() {
        Transform percentileTransform = new MetricReducerOrMappingWithConstantTransform(new PercentileValueReducerOrMapping());
        Map<Long, Double> datapoints = new HashMap<Long, Double>();

        datapoints.put(1000L, 1.0);
        datapoints.put(2000L, 2.0);
        datapoints.put(3000L, 3.0);
        datapoints.put(4000L, 4.0);
        datapoints.put(5000L, 5.0);
        datapoints.put(6000L, 6.0);
        datapoints.put(7000L, 7.0);
        datapoints.put(8000L, 8.0);
        datapoints.put(9000L, 9.0);
        datapoints.put(10000L, 10.0);

        Metric metric1 = new Metric(TEST_SCOPE, TEST_METRIC + "1");

        metric1.setDatapoints(datapoints);
        
        datapoints = new HashMap<Long, Double>();

        datapoints.put(1000L, 1.0);
        datapoints.put(2000L, 2.0);
        datapoints.put(3000L, 3.0);
        datapoints.put(4000L, 4.0);
        datapoints.put(5000L, 5.0);
        datapoints.put(6000L, 6.0);
        datapoints.put(7000L, 7.0);
        datapoints.put(8000L, 8.0);
        datapoints.put(9000L, 9.0);
        datapoints.put(10000L, 10.0);

        Metric metric2 = new Metric(TEST_SCOPE, TEST_METRIC + "2");

        metric2.setDatapoints(datapoints);

        List<Metric> metrics = Arrays.asList(metric1, metric2);
        List<String> constants = Arrays.asList("90", "individual");
        
        List<Metric> result = percentileTransform.transform(null, metrics, constants);
        
        Map<Long, Double> expected = new HashMap<Long, Double>();
        expected.put(1000L, 9.9);
        
        assertEquals(2, result.size());
        assertEquals(expected, result.get(0).getDatapoints());
        assertEquals(expected, result.get(1).getDatapoints());
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

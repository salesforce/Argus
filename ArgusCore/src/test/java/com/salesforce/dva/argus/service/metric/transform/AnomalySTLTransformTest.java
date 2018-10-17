package com.salesforce.dva.argus.service.metric.transform;

import com.salesforce.dva.argus.entity.Metric;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

/**
 * Created by vmuruganantham on 7/26/16.
 */
public class AnomalySTLTransformTest {

    public static final String DEFAULT_METRIC_NAME = "result";
    private static Transform anomalySTLTransform;
    private List<Metric> metrics;
    private Metric metric;
    private List<String> constants;
    private String constant;
    private Map<Long, Double> datapoints;
    private Map<Long, Double> expected;

    @Before
    public void setup() {
        anomalySTLTransform = new AnomalySTLTransform();
        metrics = new ArrayList<Metric>();
        metric = new Metric("test-scope", "test-metric");
        constants = new ArrayList<String>();
        datapoints = new HashMap<Long, Double>();
        expected = new HashMap<Long, Double>();
    }

    @Test(expected = IllegalStateException.class)
    public void NullMetricsSTLTest() {
        List<Metric> transformedMetrics = anomalySTLTransform.transform(null, metrics, constants);
    }

    @Test(expected = IllegalStateException.class)
    public void NullMetricSTLTest() {
        metrics.add(metric);

        List<Metric> transformedMetrics = anomalySTLTransform.transform(null, metrics, constants);
    }

    @Test(expected = IllegalStateException.class)
    public void EmptySTLTest() {
        metric.setDatapoints(datapoints);
        metrics.add(metric);

        List<Metric> transformedMetrics = anomalySTLTransform.transform(null, metrics, constants);
    }

    @Test(expected = IllegalStateException.class)
    public void TooShortSTLTest() {
        constant = "2";
        constants.add(constant);

        datapoints.put(1000L, 1000.0);
        datapoints.put(2000L, 1000.0);
        datapoints.put(3000L, 1000.0);

        metric.setDatapoints(datapoints);
        metrics.add(metric);

        List<Metric> transformedMetrics = anomalySTLTransform.transform(null, metrics, constants);
    }

    @Test
    public void STLTest1() {
        constant = "2";
        constants.add(constant);

        datapoints.put(1000L, 1000.0);
        datapoints.put(2000L, 1000.0);
        datapoints.put(3000L, 1000.0);
        datapoints.put(4000L, 1000.0);
        datapoints.put(5000L, 1000.0);
        datapoints.put(6000L, 1000.0);
        datapoints.put(7000L, 1000.0);
        datapoints.put(8000L, 1000.0);


        metric.setDatapoints(datapoints);
        metrics.add(metric);

        List<Metric> transformedMetrics = anomalySTLTransform.transform(null, metrics, constants);
        Map<Long, Double> anomalyScores = transformedMetrics.get(0).getDatapoints();

        expected.put(1000L, 29.6493052);
        expected.put(2000L, 4.49420750);
        expected.put(3000L, 22.549729);
        expected.put(4000L, 17.725911);
        expected.put(5000L, 22.60506118);
        expected.put(6000L, 44.259967);
        expected.put(7000L, 32.898948);
        expected.put(8000L, 85.221552);

        assertEquals(expected.keySet(), anomalyScores.keySet());

        for (Long time : expected.keySet()) {
            assertEquals(expected.get(time), anomalyScores.get(time), 0.01);
        }
    }

    @Test
    public void STLTest2() {
        constant = "2";
        constants.add(constant);

        datapoints.put(1000L, 10000.0);
        datapoints.put(2000L, 11000.0);
        datapoints.put(3000L, 12000.0);
        datapoints.put(4000L, 13000.0);
        datapoints.put(5000L, 14000.0);
        datapoints.put(6000L, 15000.0);
        datapoints.put(7000L, 16000.0);
        datapoints.put(8000L, 17000.0);
        datapoints.put(9000L, 15000.0);
        datapoints.put(10000L, 16000.0);
        datapoints.put(11000L, 17000.0);


        metric.setDatapoints(datapoints);
        metrics.add(metric);

        List<Metric> transformedMetrics = anomalySTLTransform.transform(null, metrics, constants);
        Map<Long, Double> anomalyScores = transformedMetrics.get(0).getDatapoints();

        expected.put(1000L, 12.83596);
        expected.put(2000L, 3.116556);
        expected.put(3000L, 37.694090);
        expected.put(4000L, 0.0711116);
        expected.put(5000L, 8.2428727);
        expected.put(6000L, 16.935739);
        expected.put(7000L, 20.458516);
        expected.put(8000L, 29.430941);
        expected.put(9000L, 73.325526);
        expected.put(10000L, 12.45959);
        expected.put(11000L, 92.219514);

        assertEquals(expected.keySet(), anomalyScores.keySet());

        for (Long time : expected.keySet()) {
            assertEquals(expected.get(time), anomalyScores.get(time), 0.01);
        }
    }

    @Test
    public void STLTest3() {
        constant = "2";
        constants.add(constant);

        datapoints.put(1000L, 10000.0);
        datapoints.put(2000L, 11000.0);
        datapoints.put(3000L, 12000.0);
        datapoints.put(4000L, 13000.0);
        datapoints.put(5000L, 14000.0);
        datapoints.put(6000L, 15000.0);
        datapoints.put(7000L, 16000.0);
        datapoints.put(8000L, 17000.0);
        datapoints.put(9000L, 18000.0);
        datapoints.put(10000L, 19000.0);
        datapoints.put(11000L, 40000.0);


        metric.setDatapoints(datapoints);
        metrics.add(metric);

        List<Metric> transformedMetrics = anomalySTLTransform.transform(null, metrics, constants);
        Map<Long, Double> anomalyScores = transformedMetrics.get(0).getDatapoints();

        expected.put(1000L, 0.932050);
        expected.put(2000L, 0.013450);
        expected.put(3000L, 26.718715);
        expected.put(4000L, 0.9751890);
        expected.put(5000L, 1.8400037);
        expected.put(6000L, 6.4613769);
        expected.put(7000L, 23.428043);
        expected.put(8000L, 8.8260322);
        expected.put(9000L, 40.497435);
        expected.put(10000L, 0.7422608);
        expected.put(11000L, 98.498233);

        assertEquals(expected.keySet(), anomalyScores.keySet());

        for (Long time : expected.keySet()) {
            assertEquals(expected.get(time), anomalyScores.get(time), 0.01);
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

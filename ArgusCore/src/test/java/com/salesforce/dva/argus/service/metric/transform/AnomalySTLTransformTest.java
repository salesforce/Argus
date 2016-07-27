package com.salesforce.dva.argus.service.metric.transform;

import com.salesforce.dva.argus.entity.Metric;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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
    private Map<Long, String> datapoints;
    private Map<Long, Double> expected;

    @Before
    public void setup() {
        anomalySTLTransform = new AnomalySTLTransform();
        metrics = new ArrayList<Metric>();
        metric = new Metric("test-scope", "test-metric");
        constants = new ArrayList<String>();
        datapoints = new HashMap<Long, String>();
        expected = new HashMap<Long, Double>();
    }

    @Test(expected = IllegalStateException.class)
    public void NullMetricsSTLTest() {
        List<Metric> transformedMetrics = anomalySTLTransform.transform(metrics, constants);
    }

    @Test(expected = IllegalStateException.class)
    public void NullMetricSTLTest() {
        metrics.add(metric);

        List<Metric> transformedMetrics = anomalySTLTransform.transform(metrics, constants);
    }

    @Test(expected = IllegalStateException.class)
    public void EmptySTLTest() {
        metric.setDatapoints(datapoints);
        metrics.add(metric);

        List<Metric> transformedMetrics = anomalySTLTransform.transform(metrics, constants);
    }

    @Test(expected = IllegalStateException.class)
    public void TooShortSTLTest() {
        constant = "2";
        constants.add(constant);

        datapoints.put(1000L, "1000");
        datapoints.put(2000L, "1000");
        datapoints.put(3000L, "1000");

        metric.setDatapoints(datapoints);
        metrics.add(metric);

        List<Metric> transformedMetrics = anomalySTLTransform.transform(metrics, constants);
    }

    @Test
    public void STLTest1() {
        constant = "2";
        constants.add(constant);

        datapoints.put(1000L, "1000");
        datapoints.put(2000L, "1000");
        datapoints.put(3000L, "1000");
        datapoints.put(4000L, "1000");
        datapoints.put(5000L, "1000");
        datapoints.put(6000L, "1000");
        datapoints.put(7000L, "1000");
        datapoints.put(8000L, "1000");


        metric.setDatapoints(datapoints);
        metrics.add(metric);

        List<Metric> transformedMetrics = anomalySTLTransform.transform(metrics, constants);
        Map<Long, String> anomalyScores = transformedMetrics.get(0).getDatapoints();

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
            assertEquals(expected.get(time), Double.parseDouble(anomalyScores.get(time)), 0.01);
        }
    }

    @Test
    public void STLTest2() {
        constant = "2";
        constants.add(constant);

        datapoints.put(1000L, "10000");
        datapoints.put(2000L, "11000");
        datapoints.put(3000L, "12000");
        datapoints.put(4000L, "13000");
        datapoints.put(5000L, "14000");
        datapoints.put(6000L, "15000");
        datapoints.put(7000L, "16000");
        datapoints.put(8000L, "17000");


        metric.setDatapoints(datapoints);
        metrics.add(metric);

        List<Metric> transformedMetrics = anomalySTLTransform.transform(metrics, constants);
        Map<Long, String> anomalyScores = transformedMetrics.get(0).getDatapoints();

        expected.put(1000L, 30.152681);
        expected.put(2000L, 3.1488910);
        expected.put(3000L, 27.640121);
        expected.put(4000L, 15.240546);
        expected.put(5000L, 23.264395);
        expected.put(6000L, 48.57453);
        expected.put(7000L, 29.181591);
        expected.put(8000L, 84.203514);

        assertEquals(expected.keySet(), anomalyScores.keySet());

        for (Long time : expected.keySet()) {
            assertEquals(expected.get(time), Double.parseDouble(anomalyScores.get(time)), 0.01);
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

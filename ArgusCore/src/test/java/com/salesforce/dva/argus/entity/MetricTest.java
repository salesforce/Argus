package com.salesforce.dva.argus.entity;

import com.salesforce.dva.argus.util.AlertUtils;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class MetricTest {

    @Test
    public void testEquals() {
        String scope = "argus.core";
        String name = "test.metric.name";
        Map<String, String> tags = new HashMap<>();
        tags.put("host", "test.com");
        tags.put("user", "testuser");
        Map<String, String> tagsCopy = new HashMap<>(tags);
        Metric metric1 = new Metric(scope, name);
        metric1.setTags(tags);
        Metric metric2 = new Metric(scope, name);
        metric2.setTags(tagsCopy);

        assertNotSame(tags, tagsCopy);
        assertEquals(tags, tagsCopy);
        assertNotSame(metric1, metric2);
        assertEquals(metric1, metric2);
    }

    @Test
    public void clearMetricsTest() {

        Metric metric = new Metric("scope", "metric");

        Map<Long, Double> datapoints = new HashMap<>();

        long start = System.currentTimeMillis() - 1000L;

        for (int j = 0; j < 5; j++) {
            datapoints.put(start + j * 10, (double)j);
        }

        metric.setDatapoints(datapoints);

        assertTrue(metric.getDatapoints().size() == 5);

        metric.clearDatapoints();

        assertTrue(metric.getDatapoints().size() == 0);
    }

    @Test
    public void MergeMetricsTest() {

        Metric metric = new Metric("scope", "metric");

        Map<Long, Double> datapoints = new HashMap<>();

        long start = System.currentTimeMillis() - 1000L;

        for (int j = 0; j < 5; j++) {
            datapoints.put(start + j * 10, (double)j);
        }

        metric.setDatapoints(datapoints);

        for (int j = 0; j < 8; j++) {
            datapoints.put(start + j * 10, (double)j + 2);
        }

        metric.addIfNotExistsDatapoints(datapoints);

        assertTrue(metric.getDatapoints().size() == 8);
    }
}

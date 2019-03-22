package com.salesforce.perfeng.akc.consumer;

import static com.salesforce.perfeng.akc.consumer.InstrumentationService.LatencyMetricValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.Arrays;

public class LatencyMetricValueTest {
    private double[] latencyBucketLimits = InstrumentationService.LATENCY_METRIC_BUCKET_LIMITS_MILLISECONDS.get(
            InstrumentationService.QUOTA_EVALUATE_LATENCY);

    @Test
    public void constructor1_test() {
        double value = 12.345;
        LatencyMetricValue lmv = new LatencyMetricValue(latencyBucketLimits, value);

        assertEquals(1, lmv.getCount());
        assertEquals(0, Double.compare(value, lmv.getMaxValue()));
        assertEquals(0, Double.compare(value, lmv.getMinValue()));
        assertEquals(0, Double.compare(value, lmv.getSum()));
        assertEquals(0, Double.compare(value, lmv.getMeanValue()));
        int[] expectedBucketCounts = new int[] {0, 0, 1, 0, 0, 0};
        assertTrue(Arrays.equals(expectedBucketCounts, lmv.getBucketCounts()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor1_testNullBucketLimits() {
        new LatencyMetricValue(null, 12.345);

        fail("Test failed, it should throw IllegalArgumentException");
    }

    @Test
    public void constructor2_test() {
        double[] values = new double[] {12.345, 23.45, 56.789};
        LatencyMetricValue lmv = new LatencyMetricValue(latencyBucketLimits, values[0]);

        lmv = new LatencyMetricValue(latencyBucketLimits, lmv, values[1]);
        assertEquals(2, lmv.getCount());
        assertEquals(0, Double.compare(values[1], lmv.getMaxValue()));
        assertEquals(0, Double.compare(values[0], lmv.getMinValue()));
        double sum = values[0] + values[1];
        assertEquals(0, Double.compare(sum, lmv.getSum()));
        assertEquals(0, Double.compare(sum/2, lmv.getMeanValue()));
        int[] expectedBucketCounts = new int[] {0, 0, 1, 1, 0, 0};
        assertTrue(Arrays.equals(expectedBucketCounts, lmv.getBucketCounts()));

        lmv = new LatencyMetricValue(latencyBucketLimits, lmv, values[2]);
        assertEquals(3, lmv.getCount());
        assertEquals(0, Double.compare(values[2], lmv.getMaxValue()));
        assertEquals(0, Double.compare(values[0], lmv.getMinValue()));
        sum += values[2];
        assertEquals(0, Double.compare(sum, lmv.getSum()));
        assertEquals(0, Double.compare(sum/3, lmv.getMeanValue()));
        expectedBucketCounts = new int[] {0, 0, 1, 1, 1, 0};
        assertTrue(Arrays.equals(expectedBucketCounts, lmv.getBucketCounts()));
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor2_testNullBucketLimits() {
        LatencyMetricValue lmv = new LatencyMetricValue(latencyBucketLimits, 123.45);

        new LatencyMetricValue(null, lmv, 98.765);

        fail("Test failed, it should throw IllegalArgumentException");
    }

}

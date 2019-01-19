package com.salesforce.dva.argus.entity;

import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class HistogramTest {

    @Test
    public void clearAndSetHistogramBucketsTest() {
        Histogram histogram = new Histogram("scope", "metric");
        long timestamp = System.currentTimeMillis();
        Map<HistogramBucket, Long> buckets = new HashMap<>();
        float lowerBound = 0;
        float upperBound = 50;
        for (int i = 0; i < 5; i++) {
            HistogramBucket histogramBucket= new  HistogramBucket(lowerBound, upperBound);
            buckets.put(histogramBucket, 4L);
            lowerBound = upperBound;
            upperBound = upperBound + 100;
        }
        histogram.setBuckets(buckets);
        histogram.setTimestamp(timestamp);
        assertTrue(histogram.getBuckets().size() == 5);
        histogram.clearBuckets();
        assertTrue(histogram.getBuckets().size() == 0);
        histogram.setBuckets(buckets);
        assertTrue(histogram.getBuckets().size() == 5);
    }
    
    @Test
    public void addHistogramBucketsTest() {
        Histogram histogram = new Histogram("scope", "metric");
        histogram.addBucket(0, 5, 2L);
        assertTrue(histogram.getBuckets().size() == 1);
        histogram.addBucket(5, 10, 3L);
        assertTrue(histogram.getBuckets().size() == 2);
    }
}

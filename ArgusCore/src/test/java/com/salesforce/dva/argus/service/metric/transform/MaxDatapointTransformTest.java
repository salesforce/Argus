package com.salesforce.dva.argus.service.metric.transform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.NumberOperations;

public class MaxDatapointTransformTest {

    private Map<Long, Number> getDps() {
        Map<Long, Number> dps = new HashMap<>();
        dps.put(1000L, 1L);
        dps.put(2000L, 10.0);
        dps.put(3000L, 6.0);
        dps.put(4000L, 3L);
        dps.put(5000L, 5L);
        dps.put(6000L, 2.0);
        dps.put(7000L, 9L);
        dps.put(8000L, 4.0);
        dps.put(9000L, 7.0);
        dps.put(10000L, 8L);
        return dps;
    }
    
    private Map<Long, Number> getDoubleDps() {
        Map<Long, Number> dps = new HashMap<>();
        dps.put(1000L, 1.0);
        dps.put(2000L, 10.0);
        dps.put(3000L, 6.0);
        dps.put(4000L, 3.0);
        dps.put(5000L, 5.0);
        dps.put(6000L, 2.0);
        dps.put(7000L, 9.0);
        dps.put(8000L, 4.0);
        dps.put(9000L, 7.0);
        dps.put(10000L, 8.0);
        return dps;
    }

    @Test
    public void testEveryNthUneven() {
        Map<Long, Number> dps = getDps();
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);

        String filterTo = "4";
        String filter = "nth";
        List<Metric> result = new MaxDatapointTransform().transform(Arrays.asList(metric),
                Arrays.asList(filterTo, filter));

        Map<Long, Number> expectedDps = new HashMap<>();
        expectedDps.put(3000L, 6.0);
        expectedDps.put(6000L, 2.0);
        expectedDps.put(8000L, 4.0);
        expectedDps.put(10000L, 8L);

        assertEquals(expectedDps, result.get(0).getDatapoints());
    }

    @Test
    public void testEveryNthEven() {
        Map<Long, Number> dps = getDps();
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);

        String filterTo = "5";
        String filter = "nth";
        List<Metric> result = new MaxDatapointTransform().transform(Arrays.asList(metric),
                Arrays.asList(filterTo, filter));

        Map<Long, Number> expectedDps = new HashMap<>();
        expectedDps.put(2000L, 10.0);
        expectedDps.put(4000L, 3L);
        expectedDps.put(6000L, 2.0);
        expectedDps.put(8000L, 4.0);
        expectedDps.put(10000L, 8L);

        assertEquals(expectedDps, result.get(0).getDatapoints());
    }

    @Test
    public void testShouldNotFilter() {
        Map<Long, Number> dps = getDps();
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);

        String filterTo = "10";
        String filter = "median";
        List<Metric> result = new MaxDatapointTransform().transform(Arrays.asList(metric),
                Arrays.asList(filterTo, filter));

        assertEquals(getDps(), result.get(0).getDatapoints());
    }

    @Test
    public void testGetMaxUneven() {
        Map<Long, Number> dps = getDps();
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);

        String filterTo = "3";
        String filter = "max";
        List<Metric> results = new MaxDatapointTransform().transform(Arrays.asList(metric),
                Arrays.asList(filterTo, filter));

        Map<Long, Number> expected = new HashMap<>();
        expected.put(2000L, 10.0);
        expected.put(7000L, 9L);
        expected.put(10000L, 8L);

        assertEquals(expected, results.get(0).getDatapoints());
    }

    @Test
    public void testGetMaxEven() {
        Map<Long, Number> dps = getDps();
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);

        String filterTo = "2";
        String filter = "max";
        List<Metric> results = new MaxDatapointTransform().transform(Arrays.asList(metric),
                Arrays.asList(filterTo, filter));

        Map<Long, Number> expectedDps = new HashMap<>();
        expectedDps.put(2000L, 10.0);
        expectedDps.put(7000L, 9L);

        assertEquals(expectedDps, results.get(0).getDatapoints());
    }

    @Test
    public void testGetMinUneven() {
        Map<Long, Number> dps = getDps();
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);

        String filterTo = "4";
        String filter = "min";
        List<Metric> results = new MaxDatapointTransform().transform(Arrays.asList(metric),
                Arrays.asList(filterTo, filter));

        Map<Long, Number> expectedDps = new HashMap<>();
        expectedDps.put(1000L, 1L);
        expectedDps.put(6000L, 2.0);
        expectedDps.put(8000L, 4.0);
        expectedDps.put(9000L, 7.0);

        assertEquals(expectedDps, results.get(0).getDatapoints());
    }

    @Test
    public void testGetMinEven() {
        Map<Long, Number> dps = getDps();
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);

        String filterTo = "5";
        String filter = "min";
        List<Metric> results = new MaxDatapointTransform().transform(Arrays.asList(metric),
                Arrays.asList(filterTo, filter));

        Map<Long, Number> expectedDps = new HashMap<>();
        expectedDps.put(1000L, 1L);
        expectedDps.put(4000L, 3L);
        expectedDps.put(6000L, 2.0);
        expectedDps.put(8000L, 4.0);
        expectedDps.put(9000L, 7.0);

        assertEquals(expectedDps, results.get(0).getDatapoints());
    }

    @Test
    public void testGetAvgUneven() {
        Map<Long, Number> dps = getDps();
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);

        String filterTo = "6";
        String filter = "avg";
        List<Metric> results = new MaxDatapointTransform().transform(Arrays.asList(metric),
                Arrays.asList(filterTo, filter));

        Map<Long, Number> expectedDps = new HashMap<>();
        expectedDps.put(2000L, 5.5);
        expectedDps.put(4000L, 4.5);
        expectedDps.put(6000L, 3.5);
        expectedDps.put(8000L, 6.5);
        expectedDps.put(9000L, 7.0);
        expectedDps.put(10000L, 8.0);

        assertEquals(expectedDps, results.get(0).getDatapoints());
    }

    @Test
    public void testGetAvgEven() {
        Map<Long, Number> dps = getDps();
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);

        String filterTo = "5";
        String filter = "avg";
        List<Metric> results = new MaxDatapointTransform().transform(Arrays.asList(metric),
                Arrays.asList(filterTo, filter));

        Map<Long, Number> expected = new HashMap<>();
        expected.put(2000L, 5.5);
        expected.put(4000L, 4.5);
        expected.put(6000L, 3.5);
        expected.put(8000L, 6.5);
        expected.put(10000L, 7.5);

        assertEquals(expected, results.get(0).getDatapoints());
    }

    @Test
    public void testAvgEvenUnexplicit() {
        Map<Long, Number> dps = getDps();
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);

        String filterTo = "2";
        List<Metric> result = new MaxDatapointTransform().transform(Arrays.asList(metric), Arrays.asList(filterTo));

        Map<Long, Number> expectedDps = new HashMap<>();
        expectedDps.put(5000L, 5.0);
        expectedDps.put(10000L, 6.0);

        assertEquals(expectedDps, result.get(0).getDatapoints());
    }

    @Test
    public void testGetMedianUneven() {
        Map<Long, Number> dps = getDps();
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);

        String filterTo = "4";
        String filter = "median";
        List<Metric> results = new MaxDatapointTransform().transform(Arrays.asList(metric),
                Arrays.asList(filterTo, filter));

        Map<Long, Number> expectedDps = new HashMap<>();
        expectedDps.put(3000L, 6.0);
        expectedDps.put(4000L, 3L);
        expectedDps.put(8000L, 6.5);
        expectedDps.put(10000L, 7.5);

        assertEquals(expectedDps, results.get(0).getDatapoints());
    }

    @Test
    public void testGetMedianEven() {
        Map<Long, Number> dps = getDps();
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);

        String filterTo = "2";
        String filter = "median";
        List<Metric> results = new MaxDatapointTransform().transform(Arrays.asList(metric),
                Arrays.asList(filterTo, filter));

        Map<Long, Number> expected = new HashMap<>();
        expected.put(5000L, 5L);
        expected.put(9000L, 7.0);

        assertEquals(expected, results.get(0).getDatapoints());
    }

    @Test
    public void testSampleOne() {
        Map<Long, Number> dps = getDps();
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);

        String filterTo = "1";
        String filter = "sample";
        List<Metric> results = new MaxDatapointTransform().transform(Arrays.asList(metric),
                Arrays.asList(filterTo, filter));

        assertEquals(1, results.get(0).getDatapoints().size());
        for (Long time : results.get(0).getDatapoints().keySet()) {
            assertEquals(getDps().get(time), results.get(0).getDatapoints().get(time));
        }
    }

    @Test
    public void testSampleAllExceptOne() {
        Map<Long, Number> dps = getDps();
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);

        String filterTo = "9";
        String filter = "sample";
        List<Metric> results = new MaxDatapointTransform().transform(Arrays.asList(metric),
                Arrays.asList(filterTo, filter));

        assertEquals(9, results.get(0).getDatapoints().size());
        for (Long time : results.get(0).getDatapoints().keySet()) {
            assertEquals(getDps().get(time), results.get(0).getDatapoints().get(time));
        }
    }

    @Test
    public void testSampleZero() {
        Map<Long, Number> dps = getDps();
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);

        String filterTo = "0";
        String filter = "sample";
        List<Metric> results = new MaxDatapointTransform().transform(Arrays.asList(metric),
                Arrays.asList(filterTo, filter));

        assertEquals(0, results.get(0).getDatapoints().size());
    }

    @Test
    public void testZimSumUneven() {
        Map<Long, Number> dps = getDps();
        dps.put(4000L, null); // update this point
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);

        String filterTo = "4";
        String filter = "zimsum";
        List<Metric> result = new MaxDatapointTransform().transform(Arrays.asList(metric),
                Arrays.asList(filterTo, filter));

        Map<Long, Number> expected = new HashMap<>();
        expected.put(3000L, 17.0);
        expected.put(6000L, 7.0);
        expected.put(8000L, 13.0);
        expected.put(10000L, 15.0);

        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testZimSumEven() {
        Map<Long, Number> dps = getDps();
        dps.put(3000L, null);
        dps.put(4000L, null);
        dps.put(9000L, null);
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);

        String filterTo = "5";
        String filter = "zimsum";
        List<Metric> results = new MaxDatapointTransform().transform(Arrays.asList(metric),
                Arrays.asList(filterTo, filter));

        Map<Long, Number> expected = new HashMap<>();
        expected.put(2000L, 11.0);
        expected.put(4000L, 0);
        expected.put(6000L, 7.0);
        expected.put(8000L, 13.0);
        expected.put(10000L, 8L);

        assertEquals(expected, results.get(0).getDatapoints());
    }
    
    @Test
    public void testSumUneven() {
        Map<Long, Number> dps = getDps();
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);

        String filterTo = "4";
        String filter = "sum";
        List<Metric> result = new MaxDatapointTransform().transform(Arrays.asList(metric),
                Arrays.asList(filterTo, filter));

        Map<Long, Number> expected = new HashMap<>();
        expected.put(3000L, 17.0);
        expected.put(6000L, 10.0);
        expected.put(8000L, 13.0);
        expected.put(10000L, 15.0);

        assertEquals(expected, result.get(0).getDatapoints());
    }

    @Test
    public void testSumEven() {
        Map<Long, Number> dps = getDps();
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);

        String filterTo = "5";
        String filter = "sum";
        List<Metric> results = new MaxDatapointTransform().transform(Arrays.asList(metric),
                Arrays.asList(filterTo, filter));

        Map<Long, Number> expected = new HashMap<>();
        expected.put(2000L, 11.0);
        expected.put(4000L, 9.0);
        expected.put(6000L, 7.0);
        expected.put(8000L, 13.0);
        expected.put(10000L, 15.0);

        assertEquals(expected, results.get(0).getDatapoints());
    }
    
    @Test
    public void testCountUneven() {
        Map<Long, Number> dps = getDps();
        dps.put(6000L, null);
        dps.put(4000L, null);
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);
        
        String filterTo = "3";
        String filter = "count";
        List<Metric> results = new MaxDatapointTransform().transform(Arrays.asList(metric), Arrays.asList(filterTo, filter));
        
        Map<Long, Number> expected = new HashMap<>();
        expected.put(4000L, 3);
        expected.put(7000L, 2);
        expected.put(10000L, 3);
        
        assertEquals(expected, results.get(0).getDatapoints());
    }
    
    @Test
    public void testCountEven() {
        Map<Long, Number> dps = getDps();
        dps.put(7000L, null);
        dps.put(8000L, null);
        dps.put(1000L, null);
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);
        
        String filterTo = "5";
        String filter = "count";
        List<Metric> results = new MaxDatapointTransform().transform(Arrays.asList(metric), Arrays.asList(filterTo, filter));
        
        Map<Long, Number> expected = new HashMap<>();
        expected.put(2000L, 1);
        expected.put(4000L, 2);
        expected.put(6000L, 2);
        expected.put(8000L, 0);
        expected.put(10000L, 2);
        
        assertEquals(expected, results.get(0).getDatapoints());
    }
    
    @Test
    public void testPercentileUneven() {
        Map<Long, Number> dps = new HashMap<>();
        dps.put(1000L, 20.0);
        dps.put(2000L, 15.0);
        dps.put(3000L, 50.0);
        dps.put(4000L, 35.0);
        dps.put(5000L, 40.0);
        dps.put(6000L, 13.0);
        dps.put(7000L, 17.0);
        dps.put(8000L, 19.0);
        dps.put(9000L, 23.0);
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);
        
        String filterTo = "2";
        String filter = "p30";
        List<Metric> results = new MaxDatapointTransform().transform(Arrays.asList(metric), Arrays.asList(filterTo, filter));
        
        Map<Long, Number> expected = new HashMap<>();
        expected.put(5000L, 19.0);
        expected.put(9000L, 15.0);
        
        assertEquals(expected, results.get(0).getDatapoints());
    }
    
    @Test
    public void testPercentileEven() {
        Map<Long, Number> dps = getDoubleDps();
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);
        
        String filterTo = "5";
        String filter = "p90";
        List<Metric> results = new MaxDatapointTransform().transform(Arrays.asList(metric), Arrays.asList(filterTo, filter));
        
        Map<Long, Number> expected = new HashMap<>();
        expected.put(2000L, 10.0);
        expected.put(4000L, 6.0);
        expected.put(6000L, 5.0);
        expected.put(8000L, 9.0);
        expected.put(10000L, 8.0);
        
        assertEquals(expected, results.get(0).getDatapoints());
    }
    
    @Test(expected = UnsupportedOperationException.class)
    public void testPercentileUnsupported() {
        Map<Long, Number> dps = getDps();
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);
        
        String filterTo = "4";
        String filter = "p63";
        new MaxDatapointTransform().transform(Arrays.asList(metric), Arrays.asList(filterTo, filter));
    }
    
    @Test(expected = IllegalArgumentException.class) 
    public void testPercentileBadPercentileString() {
        Map<Long, Number> dps = getDps();
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);
        
        String filterTo = "2";
        String filter = "percentile-45";
        new MaxDatapointTransform().transform(Arrays.asList(metric), Arrays.asList(filterTo, filter));
    }
    
    @Test
    public void testDeviationUneven() {
        Map<Long, Number> dps = getDoubleDps();
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);
        
        String filterTo = "3";
        String filter = "dev";
        List<Metric> results = new MaxDatapointTransform().transform(Arrays.asList(metric), Arrays.asList(filterTo, filter));
        
        Map<Long, Number> expected = new HashMap<>();
        expected.put(4000L, Math.sqrt(46.0/3));
        expected.put(7000L, Math.sqrt(111.0/9));
        expected.put(10000L, Math.sqrt(39.0/9));
        
        assertEquals(expected.size(), results.get(0).getDatapoints().size());
        for (Map.Entry<Long, Number> entry : expected.entrySet()) {
            assertTrue(NumberOperations.isLessThan(NumberOperations.getAbsValue(
                    NumberOperations.subtract(entry.getValue(), results.get(0).getDatapoints().get(entry.getKey())
                            )), 0.001));
        }
    }

    @Test
    public void testMinChunkLargerThanRange() {
        Map<Long, Number> dps = getDps();
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);

        String filterTo = "5";
        String interval = "1m";
        List<Metric> results = new MaxDatapointTransform().transform(Arrays.asList(metric),
                Arrays.asList(filterTo, interval));

        Map<Long, Number> expected = new HashMap<>();
        expected.put(10000L, 5.5);

        assertEquals(expected, results.get(0).getDatapoints());
    }

    @Test
    public void testZimSumChunkChangesSomeGroupings() {
        Map<Long, Number> dps = getDps();
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);
        
        String filterTo = "4";
        String interval = "3s";
        String filter = "zimsum";
        List<Metric> results = new MaxDatapointTransform().transform(Arrays.asList(metric), Arrays.asList(filterTo, interval, filter));

        Map<Long, Number> expected = new HashMap<>();
        expected.put(1000L, 1L);
        expected.put(4000L, 19.0);
        expected.put(7000L, 16.0);
        expected.put(10000L, 19.0);
        
        assertEquals(expected, results.get(0).getDatapoints());
    }
    
    @Test
    public void testChunkSameAsRange() {
        Map<Long, Number> dps = getDps();
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);
        
        String filterTo = "5";
        String interval = "2s";
        List<Metric> result = new MaxDatapointTransform().transform(Arrays.asList(metric), Arrays.asList(filterTo, interval));
        
        Map<Long, Number> expected = new HashMap<>();
        expected.put(2000L, 5.5);
        expected.put(4000L, 4.5);
        expected.put(6000L, 3.5);
        expected.put(8000L, 6.5);
        expected.put(10000L, 7.5);
        
        assertEquals(expected, result.get(0).getDatapoints());
    }
    
    @Test
    public void testChunkGreaterThanRangeMax() {
        Map<Long, Number> dps = getDps();
        Metric metric = new Metric("test-scope", "test-metric");
        metric.setDatapoints(dps);
        
        String filterTo = "3";
        String interval = "1s";
        String filter = "max";
        List<Metric> results = new MaxDatapointTransform().transform(Arrays.asList(metric), Arrays.asList(filterTo, interval, filter));
        
        Map<Long, Number> expected = new HashMap<>();
        expected.put(2000L, 10.0);
        expected.put(7000L, 9L);
        expected.put(10000L, 8L);
        
        assertEquals(expected, results.get(0).getDatapoints());
    }
}

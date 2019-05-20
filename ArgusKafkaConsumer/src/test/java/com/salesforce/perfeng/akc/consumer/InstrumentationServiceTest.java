package com.salesforce.perfeng.akc.consumer;

import com.google.common.collect.ImmutableMap;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.monitor.CounterMetric;
import com.salesforce.dva.argus.service.monitor.MetricMXBean;
import com.salesforce.perfeng.akc.AKCConfiguration;
import com.salesforce.perfeng.akc.consumer.InstrumentationService.LatencyMetricValue;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.stream.DoubleStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;

/**
 * The type Instrumentation service test.
 */
@RunWith(PowerMockRunner.class )
@SuppressStaticInitializationFor("com.salesforce.perfeng.akc.AKCConfiguration")
public class InstrumentationServiceTest {

    /**
     * The type Argus metric matcher.
     */
    public class MetricMatcher implements ArgumentMatcher<ArrayList<Metric>> {

        private Metric left;

        /**
         * Instantiates a new Argus metric matcher.
         *
         * @param metric the metric
         */
        public MetricMatcher(Metric metric) {
            this.left = metric;
        }

        @Override
        public boolean matches(ArrayList<Metric> argusMetrics) {
            if (argusMetrics.size() < 1) return false;
            Metric argusMetric = argusMetrics.get(0);

            if (!left.getScope().equals(argusMetric.getScope())) return false;
                if (!left.getMetric().equals(argusMetric.getMetric())) return false;
            Map<String, String> tagsMap = left.getTags();
            for (Map.Entry<String, String> entry : tagsMap.entrySet()) {
                if (argusMetric.getTag(entry.getKey()) == null
                        || !argusMetric.getTag(entry.getKey()).equals(entry.getValue())) return false;
            }
            Map<Long, Double> datapointsMap = left.getDatapoints();
            for (Map.Entry<Long, Double> entry : datapointsMap.entrySet()) {
                if (!argusMetric.getDatapoints().containsValue(entry.getValue())) return false;
            }

            return true;
        }
    }

    /**
     * The Mock tsdb service.
     */
    @Mock
    private TSDBService mockTSDBService;
    @Mock
    private MBeanServer mBeanServer;
    private InstrumentationService instrumentationService;

    private static final Logger LOGGER = LoggerFactory.getLogger(InstrumentationServiceTest.class);
    private String myHostname;
    private String myGroupid = "mygroupid";
    private String bootstrapServers = "bootstrap_servers_test";
    private String myParameterId = "myparameterid";

    /**
     * Sets up.
     *
     * @throws Exception the exception
     */
    @Before
    public void setUp() throws Exception {
        try {
            myHostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            myHostname = "unknown-host";
        }

        PowerMockito.mockStatic(AKCConfiguration.class);
        when(AKCConfiguration.getParameter(AKCConfiguration.Parameter.ID)).thenReturn(this.myParameterId);
        when(AKCConfiguration.getParameter(AKCConfiguration.Parameter.GROUP_ID)).thenReturn(myGroupid);
        when(AKCConfiguration.getParameter(AKCConfiguration.Parameter.BOOTSTRAP_SERVERS)).thenReturn(bootstrapServers);
        when(AKCConfiguration.getParameter(AKCConfiguration.Parameter.CONSUMER_TYPE)).thenReturn("METRICS");

        reset(mockTSDBService);
        instrumentationService = new InstrumentationService(mockTSDBService, true, mBeanServer);
    }

    @Test
    public void constructMetric_testConstructWithQuotaOptimizeTags() throws InterruptedException {
        String metricName = "mymetricname";
        Map<String, String>tags = new HashMap<>();
        tags.put("tag1", "val1");

        Metric expectedMetric = new Metric("ajna.consumer", metricName);
        Map<String, String>expectedTags = new HashMap<>();
        expectedTags.put("tag1", "val1");
        expectedTags.put("device", this.myHostname);
        expectedTags.put("groupId", this.myGroupid);
        expectedMetric.setTags(expectedTags);

        Metric actualMetric = instrumentationService.constructMetric(metricName, tags, true);
        assertEquals(expectedMetric, actualMetric);
    }

    @Test
    public void constructMetric_testConstructWithNoQuotaOptimizeTags() throws InterruptedException {
        String metricName = "mymetricname";
        Map<String, String>tags = new HashMap<>();
        tags.put("tag1", "val1");

        Metric expectedMetric = new Metric("ajna.consumer", metricName);
        Map<String, String>expectedTags = new HashMap<>();
        expectedTags.put("tag1", "val1");
        expectedTags.put("cluster", this.bootstrapServers);
        expectedTags.put("uuid", this.myHostname + this.myParameterId);
        expectedMetric.setTags(expectedTags);

        Metric actualMetric = instrumentationService.constructMetric(metricName, tags, false);
        assertEquals(expectedMetric, actualMetric);
    }

    @Test
    public void constructMetricCopy_test() {
        long timestamp = System.currentTimeMillis();
        Metric metricToCopy = new Metric("ajna.consumer", "testmetricname");
        Map<String, String> tags = ImmutableMap.of("tag1", "val1");
        metricToCopy.setTags(tags);
        double value = 9876.54;
        Map<Long, Double> datapoints = ImmutableMap.of(timestamp, value);
        metricToCopy.addDatapoints(datapoints);

        long newTimestamp = timestamp + 100000;
        double newValue = 1234.56;
        String newMetricName = "newmetricname";
        Metric copiedMetric = instrumentationService.constructMetricCopy(metricToCopy, newMetricName, newTimestamp, newValue);

        assertEquals(newMetricName, copiedMetric.getMetric());
        assertEquals(metricToCopy.getScope(), copiedMetric.getScope());
        assertEquals(tags, copiedMetric.getTags());
        Map<Long, Double> copiedDatapoints = copiedMetric.getDatapoints();
        assertEquals(1, copiedDatapoints.size());
        for (Map.Entry<Long, Double> e : copiedDatapoints.entrySet()) {
            assertEquals(0, Long.compare(newTimestamp, e.getKey()));
            assertEquals(0, Double.compare(newValue, e.getValue()));
        }
    }

    @Test
    public void updateCounter_test() throws Exception {
        String metricName = "mymetricname";
        double expectedDelta = 23.0;

        Map<Metric, DoubleAdder> expectedInstrumentedMetrics = new ConcurrentHashMap<>();
        Metric expectedMetricKey = new Metric("ajna.consumer", metricName);
        Map<String, String>expectedTags = new HashMap<>();
        expectedTags.put("tag1", "val1");
        expectedTags.put("cluster", this.bootstrapServers);
        expectedTags.put("uuid", this.myHostname + this.myParameterId);
        expectedMetricKey.setTags(expectedTags);
        expectedInstrumentedMetrics.computeIfAbsent(expectedMetricKey, k -> new DoubleAdder()).add(expectedDelta);

        // method under test
        instrumentationService.updateCounter(metricName, expectedDelta, expectedTags);
        instrumentationService.updateCounter(metricName, expectedDelta, expectedTags);
        instrumentationService.updateCounter(metricName, expectedDelta, expectedTags);

        // verify
        CounterMetric expectedCounterMetric = new CounterMetric(expectedMetricKey, ".count");
        ArgumentCaptor<CounterMetric> counterArgumentCaptor = ArgumentCaptor.forClass(CounterMetric.class);
        ArgumentCaptor<ObjectName> nameArgumentCaptor = ArgumentCaptor.forClass(ObjectName.class);
        verify(mBeanServer, times(1)).registerMBean(counterArgumentCaptor.capture(), nameArgumentCaptor.capture());
        assertEquals(expectedCounterMetric.getObjectName(), nameArgumentCaptor.getValue().toString());
        assertEquals(expectedDelta*3, counterArgumentCaptor.getValue().getValue(), 0.0);

        Map<Metric, CounterMetric> actualInstrumentedMetrics = instrumentationService.getInstrumentedCounterMetrics();
        assertTrue(actualInstrumentedMetrics.containsKey(expectedMetricKey));
        // check if the deltas have been computed correctly
        assertEquals(expectedDelta*3, actualInstrumentedMetrics.get(expectedMetricKey).getCurrentGaugeAdderValue(), 0.0);

        Map<Metric, MetricMXBean> actualRegisteredMetrics = instrumentationService.getRegisteredMetrics();
        assertTrue(actualRegisteredMetrics.containsKey(expectedMetricKey));
        assertEquals(expectedDelta*3, actualRegisteredMetrics.get(expectedMetricKey).getValue(), 0.0);
    }

    @Test
    public void updateTimer_test() throws Exception {
        String metricName = InstrumentationService.QUOTA_EVALUATE_LATENCY;
        Map<String, String> tags = ImmutableMap.of("tag1", "val1",
                "cluster", this.bootstrapServers,
                "uuid", this.myHostname + this.myParameterId);
        double[] latencies = new double[] {123.456, 4567.89}; // sorted order necessary for verifications at the end of this test
        double latencySum = 0;
        double latencyMax = 0;
        double latencyMin = Double.MAX_VALUE;

        // method under test
        for (int i = 0; i < latencies.length; i++){
            instrumentationService.updateTimer(metricName, latencies[i], tags);
            latencySum += latencies[i];
            latencyMax = Math.max(latencyMax, latencies[i]);
            latencyMin = Math.min(latencyMin, latencies[i]);
        }

        Map<Metric, LatencyMetricValue> actualInstrumentedMetrics = instrumentationService.getInstrumentedLatencyMetrics();

        assertEquals(1, actualInstrumentedMetrics.size());
        Map.Entry<Metric, LatencyMetricValue> e = actualInstrumentedMetrics.entrySet().iterator().next();
        Metric m = e.getKey();
        LatencyMetricValue lmv = e.getValue();
        assertEquals(metricName, m.getMetric());
        assertEquals(tags, m.getTags());
        assertEquals(0, Double.compare(latencySum, lmv.getSum()));
        assertEquals(0, Double.compare(latencyMax, lmv.getMaxValue()));
        assertEquals(0, Double.compare(latencyMin, lmv.getMinValue()));
        assertEquals(0, Double.compare(latencySum/latencies.length, lmv.getMeanValue()));
        assertEquals(latencies.length, lmv.getCount());

        double[] latencyMetricBuckets = InstrumentationService.LATENCY_METRIC_BUCKET_LIMITS_MILLISECONDS.get(metricName);
        assertEquals(latencyMetricBuckets.length + 1, lmv.getBucketCounts().length);
        int[] expectedBucketCounts = new int[lmv.getBucketCounts().length];
        // this loop relies on the latencies array to be sorted in ascending order
        for (double l : latencies) {
            for (int j = 0; j < latencyMetricBuckets.length; j++) {
                if (l < latencyMetricBuckets[j]) {
                    expectedBucketCounts[j]++;
                    break;
                }
            }
            if (l > latencyMetricBuckets[latencyMetricBuckets.length - 1]) {
                expectedBucketCounts[expectedBucketCounts.length - 1]++;
            }
        }
        assertTrue(Arrays.equals(expectedBucketCounts, lmv.getBucketCounts()));
    }

    @Test
    public void testMetricsAreSent() throws Exception {
        Map<String, String> tags = ImmutableMap.of("tag1", "val1");
        String counterName = "testCounter1";
        String timerName = InstrumentationService.QUOTA_EVALUATE_LATENCY;
        String timerName2 = "testTimer2";

        InstrumentationService.InstrumentMetricsThread instrumenter = instrumentationService.new InstrumentMetricsThread();
        instrumenter.pushInstrumentedMetrics();

        double[] counts = new double[] {10.0, 11.0};
        for (int i = 0; i < counts.length; i++) {
            instrumentationService.updateCounter(counterName, counts[i], tags);
        }

        Double[] latencies = new Double[] {123.4, 987.6};
        Double[] latencies2 = new Double[] {456.7, 2345.6};
        double timerSum = 0.0;
        double timerSum2 = 0.0;
        for (int i = 0; i < latencies.length; i++) {
            instrumentationService.updateTimer(timerName, latencies[i], tags);
            timerSum += latencies[i];
            instrumentationService.updateTimer(timerName2, latencies2[i], tags);
            timerSum2 += latencies2[i];
        }

        // simulate putMetrics() being called a second time
        instrumenter.pushInstrumentedMetrics();
        instrumentationService.dispose();

        ArgumentCaptor<List<Metric>> tsdbMetricsCapture = ArgumentCaptor.forClass(List.class);
        verify(mockTSDBService, times(2)).putMetrics(tsdbMetricsCapture.capture());
        List<List<Metric>> tsdbMetricsCapturedValueList = tsdbMetricsCapture.getAllValues();

        // first putMetrics() invocation
        List<Metric> tsdbMetrics = tsdbMetricsCapturedValueList.get(0);
        assertEquals(15, tsdbMetrics.size());
        assertTrue(tsdbMetrics.stream().anyMatch(m -> m.getMetric().equals(InstrumentationService.DATAPOINTS_POSTED)));
        assertTrue(tsdbMetrics.stream().anyMatch(m -> m.getMetric().equals(InstrumentationService.DATAPOINTS_CONSUMED)));
        assertTrue(tsdbMetrics.stream().anyMatch(m -> m.getMetric().equals(InstrumentationService.DATAPOINTS_DROPPED)));
        assertTrue(tsdbMetrics.stream().anyMatch(m -> m.getMetric().equals(InstrumentationService.HISTOGRAM_POSTED)));
        assertTrue(tsdbMetrics.stream().anyMatch(m -> m.getMetric().equals(InstrumentationService.HISTOGRAM_CONSUMED)));
        assertTrue(tsdbMetrics.stream().anyMatch(m -> m.getMetric().equals(InstrumentationService.HISTOGRAM_DROPPED)));

        // second putMetrics() invocation
        tsdbMetrics = tsdbMetricsCapturedValueList.get(1);
        assertEquals(39, tsdbMetrics.size());
        assertTrue(tsdbMetrics.stream().anyMatch(m -> m.getMetric().equals(counterName)));
        assertTrue(tsdbMetrics.stream().anyMatch(m -> m.getMetric().equals(InstrumentationService.DATAPOINTS_POSTED)));
        assertTrue(tsdbMetrics.stream().anyMatch(m -> m.getMetric().equals(InstrumentationService.DATAPOINTS_CONSUMED)));
        assertTrue(tsdbMetrics.stream().anyMatch(m -> m.getMetric().equals(InstrumentationService.DATAPOINTS_DROPPED)));
        assertTrue(tsdbMetrics.stream().anyMatch(m -> m.getMetric().equals(InstrumentationService.HISTOGRAM_POSTED)));
        assertTrue(tsdbMetrics.stream().anyMatch(m -> m.getMetric().equals(InstrumentationService.HISTOGRAM_CONSUMED)));
        assertTrue(tsdbMetrics.stream().anyMatch(m -> m.getMetric().equals(InstrumentationService.HISTOGRAM_DROPPED)));

        for (String timerMetricName : Arrays.asList(timerName, timerName2)) {
            assertTrue(tsdbMetrics.stream().anyMatch(m -> m.getMetric().equals(timerMetricName + ".sum")));
            assertTrue(tsdbMetrics.stream().anyMatch(m -> m.getMetric().equals(timerMetricName + ".max")));
            assertTrue(tsdbMetrics.stream().anyMatch(m -> m.getMetric().equals(timerMetricName + ".min")));
            assertTrue(tsdbMetrics.stream().anyMatch(m -> m.getMetric().equals(timerMetricName + ".count")));
            assertTrue(tsdbMetrics.stream().anyMatch(m -> m.getMetric().equals(timerMetricName + ".mean")));
            int i = 0;
            double prevBucketLimit = 0;
            double[] latencyBucketLimits = instrumentationService.getLatencyMetricBucketLimits(timerMetricName);
            String bucketCountMetricName = timerMetricName + ".bucketCount";
            String bucketLimitTagName = "bucketLimit";
            for (; i < latencyBucketLimits.length; i++) {
                double currBucketLimit = latencyBucketLimits[i];
                String latencyBucketTag = String.format("%.0f-%.0f", prevBucketLimit, currBucketLimit);
                boolean found = false;
                assertTrue(tsdbMetrics.stream().anyMatch(m -> m.getMetric().equals(bucketCountMetricName) &&
                        m.getTags().get(bucketLimitTagName).equals(latencyBucketTag)));
                prevBucketLimit = currBucketLimit;
            }
            String latencyBucketTag = String.format("%.0f-max", prevBucketLimit);
            assertTrue(tsdbMetrics.stream().anyMatch(m -> m.getMetric().equals(bucketCountMetricName) &&
                    m.getTags().get(bucketLimitTagName).equals(latencyBucketTag)));
        }

        // capture beans registered
        ArgumentCaptor<CounterMetric> counterArgumentCaptor = ArgumentCaptor.forClass(CounterMetric.class);
        ArgumentCaptor<ObjectName> nameArgumentCaptor = ArgumentCaptor.forClass(ObjectName.class);
        verify(mBeanServer, times(39)).registerMBean(counterArgumentCaptor.capture(), nameArgumentCaptor.capture());
        // convert list of beans registered into a map of bean object name to counter value
        Map<String, Double> nameToValueMap = new HashMap<>();
        for (MetricMXBean mb : counterArgumentCaptor.getAllValues()) {
            nameToValueMap.put(mb.getObjectName(), mb.getValue());
        }
 //       assertEquals(DoubleStream.of(counts).sum(), mapLookup(nameToValueMap, "ArgusMetrics:type=Counter,scope=ajna.consumer,metric=testCounter1.count,tag1=val1"), 0.0);
        assertEquals(Collections.max(Arrays.<Double>asList(latencies2)), mapLookup(nameToValueMap, "ArgusMetrics:type=Counter,scope=ajna.consumer,metric=testTimer2.max,cluster=bootstrap_servers_test,tag1=val1"), 0.0);
        assertEquals(Collections.min(Arrays.<Double>asList(latencies2)), mapLookup(nameToValueMap, "ArgusMetrics:type=Counter,scope=ajna.consumer,metric=testTimer2.min,cluster=bootstrap_servers_test,tag1=val1"), 0.0);
        assertEquals(timerSum2/latencies2.length, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=testTimer2.mean,cluster=bootstrap_servers_test,tag1=val1"), 0.0);
        assertEquals(timerSum2, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=testTimer2.sum,cluster=bootstrap_servers_test,tag1=val1"), 0.0);
        assertEquals(latencies2.length, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=testTimer2.count,cluster=bootstrap_servers_test,tag1=val1"), 0.0);
        assertEquals(0, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=testTimer2.bucketCount,bucketLimit=0-10,cluster=bootstrap_servers_test"), 0.0);
        assertEquals(0, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=testTimer2.bucketCount,bucketLimit=10-20,cluster=bootstrap_servers_test"), 0.0);
        assertEquals(0, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=testTimer2.bucketCount,bucketLimit=20-50,cluster=bootstrap_servers_test"), 0.0);
        assertEquals(0, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=testTimer2.bucketCount,bucketLimit=50-100,cluster=bootstrap_servers_test"), 0.0);
        assertEquals(0, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=testTimer2.bucketCount,bucketLimit=100-200,cluster=bootstrap_servers_test"), 0.0);
        assertEquals(1, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=testTimer2.bucketCount,bucketLimit=200-500,cluster=bootstrap_servers_test"), 0.0);
        assertEquals(1, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=testTimer2.bucketCount,bucketLimit=500-max,cluster=bootstrap_servers_test"), 0.0);
        assertEquals(Collections.max(Arrays.<Double>asList(latencies)), mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=quota.evaluate.latency.max,cluster=bootstrap_servers_test,tag1=val1"), 0.0);
        assertEquals(Collections.min(Arrays.<Double>asList(latencies)), mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=quota.evaluate.latency.min,cluster=bootstrap_servers_test,tag1=val1"), 0.0);
        assertEquals(timerSum/latencies.length, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=quota.evaluate.latency.mean,cluster=bootstrap_servers_test,tag1=val1"), 0.0);
        assertEquals(timerSum, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=quota.evaluate.latency.sum,cluster=bootstrap_servers_test,tag1=val1"), 0.0);
        assertEquals(latencies.length, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=quota.evaluate.latency.count,cluster=bootstrap_servers_test,tag1=val1"), 0.0);
        assertEquals(0, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=quota.evaluate.latency.bucketCount,bucketLimit=0-5,cluster=bootstrap_servers_test"), 0.0);
        assertEquals(0, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=quota.evaluate.latency.bucketCount,bucketLimit=5-10,cluster=bootstrap_servers_test"), 0.0);
        assertEquals(0, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=quota.evaluate.latency.bucketCount,bucketLimit=10-20,cluster=bootstrap_servers_test"), 0.0);
        assertEquals(0, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=quota.evaluate.latency.bucketCount,bucketLimit=20-50,cluster=bootstrap_servers_test"), 0.0);
        assertEquals(0, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=quota.evaluate.latency.bucketCount,bucketLimit=50-100,cluster=bootstrap_servers_test"), 0.0);
        assertEquals(2, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=quota.evaluate.latency.bucketCount,bucketLimit=100-max,cluster=bootstrap_servers_test"), 0.0);

        assertEquals(DoubleStream.of(counts).sum(), mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=testCounter1.count,cluster=bootstrap_servers_test,tag1=val1"), 0.0);
        assertEquals(Collections.max(Arrays.<Double>asList(latencies2)), mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=testTimer2.max,cluster=bootstrap_servers_test,tag1=val1"), 0.0);
        assertEquals(Collections.min(Arrays.<Double>asList(latencies2)), mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=testTimer2.min,cluster=bootstrap_servers_test,tag1=val1"), 0.0);
        assertEquals(timerSum2/latencies2.length, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=testTimer2.mean,cluster=bootstrap_servers_test,tag1=val1"), 0.0);
        assertEquals(timerSum2, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=testTimer2.sum,cluster=bootstrap_servers_test,tag1=val1"), 0.0);
        assertEquals(latencies2.length, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=testTimer2.count,cluster=bootstrap_servers_test,tag1=val1"), 0.0);
        assertEquals(0, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=testTimer2.bucketCount,bucketLimit=0-10,cluster=bootstrap_servers_test"), 0.0);
        assertEquals(0, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=testTimer2.bucketCount,bucketLimit=10-20,cluster=bootstrap_servers_test"), 0.0);
        assertEquals(0, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=testTimer2.bucketCount,bucketLimit=20-50,cluster=bootstrap_servers_test"), 0.0);
        assertEquals(0, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=testTimer2.bucketCount,bucketLimit=50-100,cluster=bootstrap_servers_test"), 0.0);
        assertEquals(0, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=testTimer2.bucketCount,bucketLimit=100-200,cluster=bootstrap_servers_test"), 0.0);
        assertEquals(1, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=testTimer2.bucketCount,bucketLimit=200-500,cluster=bootstrap_servers_test"), 0.0);
        assertEquals(1, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=testTimer2.bucketCount,bucketLimit=500-max,cluster=bootstrap_servers_test"), 0.0);
        assertEquals(Collections.max(Arrays.<Double>asList(latencies)), mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=quota.evaluate.latency.max,cluster=bootstrap_servers_test,tag1=val1"), 0.0);
        assertEquals(Collections.min(Arrays.<Double>asList(latencies)), mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=quota.evaluate.latency.min,cluster=bootstrap_servers_test,tag1=val1"), 0.0);
        assertEquals(timerSum/latencies.length, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=quota.evaluate.latency.mean,cluster=bootstrap_servers_test,tag1=val1"), 0.0);
        assertEquals(timerSum, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=quota.evaluate.latency.sum,cluster=bootstrap_servers_test,tag1=val1"), 0.0);
        assertEquals(latencies.length, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=quota.evaluate.latency.count,cluster=bootstrap_servers_test,tag1=val1"), 0.0);
        assertEquals(0, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=quota.evaluate.latency.bucketCount,bucketLimit=0-5,cluster=bootstrap_servers_test"), 0.0);
        assertEquals(0, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=quota.evaluate.latency.bucketCount,bucketLimit=5-10,cluster=bootstrap_servers_test"), 0.0);
        assertEquals(0, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=quota.evaluate.latency.bucketCount,bucketLimit=10-20,cluster=bootstrap_servers_test"), 0.0);
        assertEquals(0, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=quota.evaluate.latency.bucketCount,bucketLimit=20-50,cluster=bootstrap_servers_test"), 0.0);
        assertEquals(0, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=quota.evaluate.latency.bucketCount,bucketLimit=50-100,cluster=bootstrap_servers_test"), 0.0);
        assertEquals(2, mapLookup(nameToValueMap,"ArgusMetrics:type=Counter,scope=ajna.consumer,metric=quota.evaluate.latency.bucketCount,bucketLimit=100-max,cluster=bootstrap_servers_test"), 0.0);
        // default datapoint metrics
        for (String defaultMetric : InstrumentationService.DATAPOINT_METRICS) {
            Metric m = instrumentationService.constructMetric(defaultMetric, null, false);
            CounterMetric cm = instrumentationService.getCounterMXBeanInstance(m);
            assertEquals(0, nameToValueMap.get(cm.getObjectName()), 0.0);
        }
    }

    @Test
    @Ignore
    public void testNewMetricsAreSent() throws InterruptedException {
        Map<String, String> testMap = new HashMap<>();
        testMap.put("tag1", "val1");

        instrumentationService.updateCounter("testMetric", 10.0, testMap);
        instrumentationService.updateCounter("testMetric", 11.0, testMap);

        instrumentationService.instrument();
        Thread.sleep(100);
        instrumentationService.dispose();

        Metric metric = new Metric("ajna.consumer", "testMetric");
        metric.setTags(testMap);
        Map<Long, Double> datapointsMap = new HashMap<>();
        datapointsMap.put(0L, 21.0);
        metric.addDatapoints(datapointsMap);
        verify(mockTSDBService).putMetrics(argThat(new MetricMatcher(metric)));
    }


    @Test
    @Ignore
    public void benchmarkNewUpdateCounter() throws InterruptedException {
        long timebefore = System.currentTimeMillis();

        InstrumentationService instrumentationService = InstrumentationService.getInstance(mockTSDBService);

        ExecutorService exec = Executors.newFixedThreadPool(20);
        for (int i = 0; i < 20; i++) {
            exec.execute(new NewUpdateCounterThread(instrumentationService));
        }

        exec.shutdown();
        while (!exec.awaitTermination(1L, TimeUnit.SECONDS)) {
            LOGGER.info("Not yet. Still waiting for termination");
        }

        long timeafter = System.currentTimeMillis();
        LOGGER.info("benchmarkNewUpdateCounter: This test took " + (timeafter - timebefore) + "mills");
    }

    private <V> V mapLookup(Map<String, V> map, String keyPrefix) {
        for (Map.Entry<String, V> e : map.entrySet()) {
            if (e.getKey().startsWith(keyPrefix)) {
                return e.getValue();
            }
        }
        return null;
    }

    private class NewUpdateCounterThread implements Runnable {

        private InstrumentationService instrumentationService;

        public NewUpdateCounterThread(InstrumentationService instrumentationService) {
            this.instrumentationService = instrumentationService;
        }

        @Override
        public void run() {
            Map<String, String> testMap = new HashMap<>();
            Random random = new Random();

            for (int i = 0; i < 30000; i++) {

                testMap.put("tag1", "val" + random.nextInt(20));
                testMap.put("tag2", "val" + random.nextInt(100));
                testMap.put("tag3", "val" + random.nextInt(20));

                instrumentationService.updateCounter("testMetric", 10.0, testMap);
            }
        }
    }

}

package com.salesforce.perfeng.akc.consumer;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.salesforce.dva.argus.entity.Histogram;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.service.AnnotationStorageService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.mandm.ajna.AjnaWire;
import com.salesforce.mandm.ajna.LatencyType;
import com.salesforce.mandm.avro.SchemaField;
import com.salesforce.mandm.avro.util.AjnaWireFormatDecoder;
import com.salesforce.perfeng.akc.AKCConfiguration;
import com.salesforce.quota.IBlacklistService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class )
@SuppressStaticInitializationFor("com.salesforce.perfeng.akc.AKCConfiguration")
public class MetricConsumerTest {

    private TSDBService tsdbService = PowerMockito.mock(TSDBService.class);
    private SchemaService schemaService = PowerMockito.mock(SchemaService.class);
    private AnnotationStorageService annotationStorageService = PowerMockito.mock(AnnotationStorageService.class);
    private InstrumentationService instrumentationService = PowerMockito.mock(InstrumentationService.class);
    private IBlacklistService blacklistService = PowerMockito.mock(IBlacklistService.class);
    private AjnaConsumerTask task;

    @Before
    public void setup() {
        System.setProperty("akc.common.configuration", "src/test/resources/akc.config");

        mockStatic(AKCConfiguration.class);
        when(AKCConfiguration.getParameter(AKCConfiguration.Parameter.ID)).thenReturn("1234");
        when(AKCConfiguration.getParameter(AKCConfiguration.Parameter.BOOTSTRAP_SERVERS)).thenReturn("bootstrap_servers_test");
        when(AKCConfiguration.getParameter(AKCConfiguration.Parameter.CONSUMER_TYPE)).thenReturn("METRICS");
        when(AKCConfiguration.getParameter(AKCConfiguration.Parameter.RETRIES)).thenReturn("10");
        when(AKCConfiguration.getParameter(AKCConfiguration.Parameter.METRICS_BATCH_SIZE)).thenReturn("100");
        when(AKCConfiguration.getParameter(AKCConfiguration.Parameter.SCHEMA_BATCH_SIZE)).thenReturn("100");
        when(AKCConfiguration.getParameter(AKCConfiguration.Parameter.ANNOTATIONS_BATCH_SIZE)).thenReturn("100");
        when(AKCConfiguration.getParameter(AKCConfiguration.Parameter.SCHEMA_LOOKUP_TIMEOUT_MS)).thenReturn("100");
        when(AKCConfiguration.getParameter(AKCConfiguration.Parameter.QUOTA_SWITCH)).thenReturn("ON");
        when(AKCConfiguration.getParameter(AKCConfiguration.Parameter.MAX_ANNOTATION_SIZE_BYTES)).thenReturn("2000");
        when(AKCConfiguration.getParameter(AKCConfiguration.Parameter.ENABLE_MAX_METRICS_AGE)).thenReturn("true");
        when(AKCConfiguration.getParameter(AKCConfiguration.Parameter.MAX_METRICS_AGE_MS)).thenReturn("1296000000");

        Properties props = new Properties();
        props.put(AKCConfiguration.Parameter.ID, "1234");
        props.put(AKCConfiguration.Parameter.BOOTSTRAP_SERVERS, "bootstrap_servers_test");
        props.put(AKCConfiguration.Parameter.CONSUMER_TYPE, "METRICS");
        props.put(AKCConfiguration.Parameter.RETRIES, "10");
        props.put(AKCConfiguration.Parameter.METRICS_BATCH_SIZE, "100");
        props.put(AKCConfiguration.Parameter.SCHEMA_BATCH_SIZE, "100");
        props.put(AKCConfiguration.Parameter.ANNOTATIONS_BATCH_SIZE, "100");
        props.put(AKCConfiguration.Parameter.SCHEMA_LOOKUP_TIMEOUT_MS, "100");
        props.put(AKCConfiguration.Parameter.QUOTA_SWITCH, "ON");
        props.put(AKCConfiguration.Parameter.MAX_ANNOTATION_SIZE_BYTES, "2000");
        props.put(AKCConfiguration.Parameter.ENABLE_MAX_METRICS_AGE, "true");
        props.put(AKCConfiguration.Parameter.MAX_METRICS_AGE_MS, "1296000000");

        when(AKCConfiguration.getConfiguration()).thenReturn(props);

        doNothing().when(tsdbService).putHistograms(anyList());

        task = new AjnaConsumerTask();
        task.init(tsdbService,
                schemaService,
                annotationStorageService,
                instrumentationService,
                blacklistService
        );
    }

    @Test
    public void processAjnaMetricKafkaRecordsForHistogram() {
        task.metricConsumer.metricAvroDecoder = PowerMockito.mock(AjnaWireFormatDecoder.class);
        task.metricConsumer.histogramConsumer.histogramAvroDecoder = PowerMockito.mock(AjnaWireFormatDecoder.class);

        when(task.metricConsumer.metricAvroDecoder.ajnaWireFromBytes(any()))
                .thenReturn(new AjnaWire(MetricConsumer.HISTOGRAM_SCHEMA_FINGERPRINT, Collections.emptyMap(), Collections.emptyList()));
        com.salesforce.mandm.ajna.Histogram ajnaHistogram = new com.salesforce.mandm.ajna.Histogram("service", "subservice", 1,
                1L, 1L, ImmutableMap.of("20,30", 10L, "30,300", 2L), null,
                Lists.newArrayList("metricName"), System.currentTimeMillis(),  LatencyType.NORMAL);
        when(task.metricConsumer.histogramConsumer.histogramAvroDecoder.listFromAjnaWire(any(), any())).thenReturn(Lists.newArrayList(ajnaHistogram));

        List<Histogram> argusHistograms = Lists.newArrayList();
        Map<String, Metric> argusMetrics = Maps.newHashMap();
        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<>("topic", 1, 1, null, null);
        task.metricConsumer.processAjnaMetricKafkaRecords(new ConsumerRecords<>(ImmutableMap.of(new TopicPartition("topic", 1),
                Lists.newArrayList(record))), argusMetrics, argusHistograms);
        assertEquals(1, argusHistograms.size());
        assertEquals(0, argusMetrics.size());
    }

    @Test
    public void processAjnaMetricKafkaRecordsForMetric() {
        task.metricConsumer.metricAvroDecoder = PowerMockito.mock(AjnaWireFormatDecoder.class);

        when(task.metricConsumer.metricAvroDecoder.ajnaWireFromBytes(any()))
                .thenReturn(new AjnaWire(BaseMetricConsumer.METRIC_SCHEMA_FINGERPRINT, Collections.emptyMap(), Collections.emptyList()));

        com.salesforce.mandm.ajna.Metric ajnaMetric = new com.salesforce.mandm.ajna.Metric("service", "subservice", 1,
                null, Lists.newArrayList("metricName"), 1.0, System.currentTimeMillis(),  LatencyType.NORMAL);
        when(task.metricConsumer.metricAvroDecoder.listFromAjnaWire(any(), any())).thenReturn(Lists.newArrayList(ajnaMetric));


        List<Histogram> argusHistograms = Lists.newArrayList();
        Map<String, Metric> argusMetrics = Maps.newHashMap();
        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<>("topic", 1, 1, null, null);
        task.metricConsumer.processAjnaMetricKafkaRecords(new ConsumerRecords<>(ImmutableMap.of(new TopicPartition("topic", 1),
                Lists.newArrayList(record))), argusMetrics, argusHistograms);
        assertEquals(0, argusHistograms.size());
        assertEquals(1, argusMetrics.size());
        Map<Long, Double> datapoints = argusMetrics.values().iterator().next().getDatapoints();
        assertEquals(1, datapoints.size());
        assertEquals(1.0, datapoints.values().iterator().next(), 0.001);
    }

    @Test
    public void processAjnaMetricKafkaRecordsForInvalidEntity() {
        task.metricConsumer.metricAvroDecoder = PowerMockito.mock(AjnaWireFormatDecoder.class);

        when(task.metricConsumer.metricAvroDecoder.ajnaWireFromBytes(any()))
                .thenReturn(new AjnaWire("InvalidFingerPrint", Collections.emptyMap(), Collections.emptyList()));

        com.salesforce.mandm.ajna.Metric ajnaMetric = new com.salesforce.mandm.ajna.Metric("service", "subservice", 1,
                null, Lists.newArrayList("metricName"), 1.0, System.currentTimeMillis(),  LatencyType.NORMAL);
        when(task.metricConsumer.metricAvroDecoder.listFromAjnaWire(any(), any())).thenThrow(new RuntimeException("bad entity"));


        List<Histogram> argusHistograms = Lists.newArrayList();
        Map<String, Metric> argusMetrics = Maps.newHashMap();
        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<>("topic", 1, 1, null, null);
        task.metricConsumer.processAjnaMetricKafkaRecords(new ConsumerRecords<>(ImmutableMap.of(new TopicPartition("topic", 1),
                Lists.newArrayList(record))), argusMetrics, argusHistograms);
        assertEquals(0, argusHistograms.size());
        assertEquals(0, argusMetrics.size());
    }

    @Test
    public void transformToArgusMetric() {
        Map<CharSequence, CharSequence> tags = Maps.newHashMap();
        tags.put(SchemaField.TAG_KEY_DATACENTER, "d$^c&");
        tags.put("tagk", "tagv");
        com.salesforce.mandm.ajna.Metric ajnaMetric = new com.salesforce.mandm.ajna.Metric("SERVICE", "", 1,
               tags, Lists.newArrayList("metric#Name"), 1.0, System.currentTimeMillis(), LatencyType.NORMAL);

        Metric argusMetric = task.metricConsumer.transformToArgusMetric(ajnaMetric, ImmutableMap.of("dpTagk", "dpTagv"), null, null, null, null);

        assertNotNull(argusMetric);
        assertEquals("service.D__C__", argusMetric.getScope());
        assertEquals("metric__Name", argusMetric.getMetric());
        assertEquals(1, argusMetric.getTags().size());
        assertTrue(argusMetric.getTags().containsKey("tagk"));
        assertEquals(1.0, argusMetric.getDatapoints().values().iterator().next(), 0.001);
        assertEquals("D$^C&", argusMetric.getMetatagsRecord().getMetatagValue(SchemaField.TAG_KEY_DATACENTER));
    }

    @Test
    public void processAjnaWireRecord() {
        MetricConsumer consumer = new MetricConsumer(tsdbService, instrumentationService, blacklistService);

        consumer.metricAvroDecoder = PowerMockito.mock(AjnaWireFormatDecoder.class);

        com.salesforce.mandm.ajna.Metric ajnaMetric = new com.salesforce.mandm.ajna.Metric("service", "subservice", 1,
                null, Lists.newArrayList("metricName"), 1.0, System.currentTimeMillis(),  LatencyType.NORMAL);
        when(consumer.metricAvroDecoder.listFromAjnaWire(any(), any())).thenReturn(Lists.newArrayList(ajnaMetric));

        Map<String, Metric> results = Maps.newHashMap();
        consumer.processAjnaWireRecord(new ConsumerRecord<>("topic", 1, 1L, null, null), results, null, Maps.newHashMap());
        assertEquals(1, results.size());
    }

    @Test
    public void createMetatagsRecordKey() {
        String s = BaseMetricConsumer.createMetatagsRecordKey(ImmutableMap.of(SchemaField.SERVICE, "service",
                SchemaField.TAG_KEY_SUPERPOD, "superpod"));

        assertEquals("service..superpod.", s);
    }

    @Test
    public void isMetricSizeSafe() {
        final String tooLong = "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";
        final String legalLenth = "xxx";
        assertTrue(BaseMetricConsumer.isMetricSizeSafe(new Metric(legalLenth, legalLenth)));
        assertFalse(BaseMetricConsumer.isMetricSizeSafe(new Metric(tooLong, legalLenth)));
        assertFalse(BaseMetricConsumer.isMetricSizeSafe(new Metric(legalLenth, tooLong)));

        Metric m = new Metric(legalLenth, legalLenth);
        m.setTags(ImmutableMap.of(tooLong, legalLenth));
        assertFalse(BaseMetricConsumer.isMetricSizeSafe(m));

        Metric m2 = new Metric(legalLenth, legalLenth);
        m2.setNamespace(tooLong);
        assertFalse(BaseMetricConsumer.isMetricSizeSafe(m2));

        Metric m3 = new Metric(legalLenth, legalLenth);
        m3.setDisplayName(tooLong);
        assertFalse(BaseMetricConsumer.isMetricSizeSafe(m3));

        Metric m4 = new Metric(legalLenth, legalLenth);
        m4.setUnits(tooLong);
        assertFalse(BaseMetricConsumer.isMetricSizeSafe(m4));
    }

    @Test
    public void buildArgusMetricName() {
        assertEquals("part1.part2.part3", BaseConsumer.buildArgusMetricName(Lists.newArrayList("part1", "part2", "part3")));
        assertEquals("k1-v1.k2-v2",
                BaseConsumer.buildArgusMetricName(Lists.newArrayList(ImmutableMap.of("k1", "v1"),ImmutableMap.of("k2", "v2"))));
    }

    @Test
    public void extractAjnaTags() {
        Metric metric = new Metric("s", "m");
        task.metricConsumer.extractAjnaTags(ImmutableMap.of("k@1", "v@1", "k$2", "v$2"), metric);
        Map<String, String> tags = metric.getTags();

        assertEquals(2, tags.size());
        assertTrue(tags.containsKey("k__1"));
        assertTrue(tags.containsKey("k__2"));
        assertTrue(tags.containsValue("v__1"));
        assertTrue(tags.containsValue("v__2"));
    }
}
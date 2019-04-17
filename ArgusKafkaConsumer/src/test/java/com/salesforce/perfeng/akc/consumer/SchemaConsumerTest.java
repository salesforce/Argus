package com.salesforce.perfeng.akc.consumer;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.salesforce.dva.argus.entity.Histogram;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.mandm.ajna.AjnaWire;
import com.salesforce.mandm.ajna.LatencyType;
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

import static com.salesforce.perfeng.akc.consumer.InstrumentationService.HISTOGRAM_SCHEMA_BATCH_COUNT;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.HISTOGRAM_SCHEMA_DROPPED;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.HISTOGRAM_SCHEMA_POSTED;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.SCHEMA_BATCH_COUNT;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.SCHEMA_DROPPED;
import static com.salesforce.perfeng.akc.consumer.InstrumentationService.SCHEMA_POSTED;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class )
@SuppressStaticInitializationFor("com.salesforce.perfeng.akc.AKCConfiguration")
public class SchemaConsumerTest {

    private TSDBService tsdbService = PowerMockito.mock(TSDBService.class);
    private SchemaService schemaService = PowerMockito.mock(SchemaService.class);
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
                instrumentationService,
                blacklistService);
    }

    @Test
    public void processAjnaMetricKafkaRecords() {
        task.schemaConsumer.metricAvroDecoder = PowerMockito.mock(AjnaWireFormatDecoder.class);

        when(task.schemaConsumer.metricAvroDecoder.ajnaWireFromBytes(any()))
                .thenReturn(new AjnaWire(BaseMetricConsumer.METRIC_SCHEMA_FINGERPRINT, Collections.emptyMap(), Collections.emptyList()));

        com.salesforce.mandm.ajna.Metric ajnaMetric = new com.salesforce.mandm.ajna.Metric("service", "subservice", 1,
                null, Lists.newArrayList("metricName"), 1.0, System.currentTimeMillis(),  LatencyType.NORMAL);
        when(task.schemaConsumer.metricAvroDecoder.listFromAjnaWire(any(), any())).thenReturn(Lists.newArrayList(ajnaMetric));

        List<Histogram> argusHistograms = Lists.newArrayList();
        Map<String, Metric> argusMetrics = Maps.newHashMap();
        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<>("topic", 1, 1, null, null);
        task.schemaConsumer.processAjnaMetricKafkaRecords(new ConsumerRecords<>(ImmutableMap.of(new TopicPartition("topic", 1),
                Lists.newArrayList(record))), argusMetrics, argusHistograms);

        assertEquals(0, argusHistograms.size());
        assertEquals(1, argusMetrics.size());
        assertTrue(argusMetrics.values().iterator().next().getDatapoints().isEmpty());  //this test ensures schema consumer clears a Metric's datapoints

        verify(instrumentationService, times(1)).updateCounter(eq(SCHEMA_BATCH_COUNT), eq(1.0d), any());
        verify(instrumentationService, times(0)).updateCounter(eq(HISTOGRAM_SCHEMA_BATCH_COUNT), anyDouble(), any());
        verify(schemaService, times(1)).put(anyList());
        verify(instrumentationService, times(1)).updateCounter(eq(SCHEMA_POSTED), eq(1.0d), any());
        verify(instrumentationService, times(0)).updateCounter(eq(SCHEMA_DROPPED), anyDouble(), any());
    }

    @Test
    public void processAjnaMetricKafkaRecordsForHistogram() {
        task.schemaConsumer.metricAvroDecoder = PowerMockito.mock(AjnaWireFormatDecoder.class);
        task.schemaConsumer.histogramConsumer.histogramAvroDecoder = PowerMockito.mock(AjnaWireFormatDecoder.class);

        when(task.schemaConsumer.metricAvroDecoder.ajnaWireFromBytes(any()))
                .thenReturn(new AjnaWire(BaseMetricConsumer.HISTOGRAM_SCHEMA_FINGERPRINT, Collections.emptyMap(), Collections.emptyList()));

        com.salesforce.mandm.ajna.Histogram ajnaHistogram = new com.salesforce.mandm.ajna.Histogram("service", "subservice", 1,
                1L, 1L, ImmutableMap.of("20,30", 10L, "30,300", 2L), null,
                Lists.newArrayList("metricName"), System.currentTimeMillis(),  LatencyType.NORMAL);
        when(task.schemaConsumer.histogramConsumer.histogramAvroDecoder.listFromAjnaWire(any(), any())).thenReturn(Lists.newArrayList(ajnaHistogram));

        List<Histogram> argusHistograms = Lists.newArrayList();
        Map<String, Metric> argusMetrics = Maps.newHashMap();
        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<>("topic", 1, 1, null, null);

        task.schemaConsumer.processAjnaMetricKafkaRecords(new ConsumerRecords<>(ImmutableMap.of(new TopicPartition("topic", 1),
                Lists.newArrayList(record))), argusMetrics, argusHistograms);

        assertEquals(1, argusHistograms.size());
        assertEquals(0, argusMetrics.size());
        Histogram argusHistogram = argusHistograms.get(0);
        assertTrue(argusHistogram.getScope().contains("subservice"));
        assertEquals(2, argusHistogram.getBuckets().size());

        verify(instrumentationService, times(0)).updateCounter(eq(SCHEMA_BATCH_COUNT), anyDouble(), any());
        verify(instrumentationService, times(1)).updateCounter(eq(HISTOGRAM_SCHEMA_BATCH_COUNT), eq(1.0d), any());
        verify(schemaService, times(1)).put(anyList());
        verify(instrumentationService, times(1)).updateCounter(eq(HISTOGRAM_SCHEMA_POSTED), eq(1.0d), any());
        verify(instrumentationService, times(0)).updateCounter(eq(HISTOGRAM_SCHEMA_DROPPED), anyDouble(), any());
    }

}
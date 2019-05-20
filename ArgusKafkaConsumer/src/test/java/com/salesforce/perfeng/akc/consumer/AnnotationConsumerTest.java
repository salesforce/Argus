package com.salesforce.perfeng.akc.consumer;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.service.AnnotationStorageService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.mandm.ajna.AjnaWire;
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
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class )
@SuppressStaticInitializationFor("com.salesforce.perfeng.akc.AKCConfiguration")
public class AnnotationConsumerTest {

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
                blacklistService);
    }

    @Test
    public void processAjnaAnnotationKafkaRecords() {
        task.annotationConsumer.annotationAvroDecoder = PowerMockito.mock(AjnaWireFormatDecoder.class);

        when(task.annotationConsumer.annotationAvroDecoder.ajnaWireFromBytes(any()))
                .thenReturn(new AjnaWire(AnnotationConsumer.ANNOTATION_SCHEMA_FINGERPRINT, Collections.emptyMap(), Collections.emptyList()));

        com.salesforce.mandm.ajna.Annotation ajnaAnnotation = new com.salesforce.mandm.ajna.Annotation("scope&", "relatedMetric",
                ImmutableMap.of("tagk^", "tagv"), "source&", "id", System.currentTimeMillis(), "type&", ImmutableMap.of("fieldk(", "fieldv"));
        when(task.annotationConsumer.annotationAvroDecoder.listFromBytes(any(), any())).thenReturn(Lists.newArrayList(ajnaAnnotation));

        List<Annotation> argusAnnotations = Lists.newArrayList();
        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<>("topic", 1, 1, null, null);
        task.annotationConsumer.processAjnaAnnotationKafkaRecords(new ConsumerRecords<>(ImmutableMap.of(new TopicPartition("topic", 1), Lists.newArrayList(record))), argusAnnotations);

        assertEquals(1, argusAnnotations.size());
    }

    @Test
    public void transformToArgusAnnotation() {
        com.salesforce.mandm.ajna.Annotation ajnaAnnotation = new com.salesforce.mandm.ajna.Annotation("scope&", "relatedMetric",
                ImmutableMap.of("tagk^", "tagv"), "source&", "id", System.currentTimeMillis(), "type&", ImmutableMap.of("fieldk(", "fieldv"));

        Annotation argusAnnotation = task.annotationConsumer.transformToArgusAnnotation(ajnaAnnotation);

        assertNotNull(argusAnnotation);
        assertEquals("scope__", argusAnnotation.getScope());
        assertEquals("source&", argusAnnotation.getSource());
        assertEquals("type&", argusAnnotation.getType());
        assertTrue(argusAnnotation.getFields().containsKey("fieldk("));
        assertTrue(argusAnnotation.getTags().containsKey("tagk__"));
    }

    @Test
    public void isAnnotationSizeSafe() {
        assertTrue(AnnotationConsumer.isAnnotationSizeSafe(new Annotation("source", "id", "type", "scope", "metric", 1L)));
    }
}
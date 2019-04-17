package com.salesforce.perfeng.akc.consumer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.salesforce.dva.argus.entity.Histogram;
import com.salesforce.dva.argus.entity.HistogramBucket;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.mandm.ajna.LatencyType;
import com.salesforce.mandm.avro.util.AjnaWireFormatDecoder;
import com.salesforce.perfeng.akc.AKCConfiguration;
import com.salesforce.quota.IBlacklistService;
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
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class )
@SuppressStaticInitializationFor("com.salesforce.perfeng.akc.AKCConfiguration")
public class HistogramConsumerTest {
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
    public void processAjnaWireRecord() {
        HistogramConsumer consumer = new HistogramConsumer(tsdbService, instrumentationService ,blacklistService);
        consumer.histogramAvroDecoder = PowerMockito.mock(AjnaWireFormatDecoder.class);

        long timestamp = System.currentTimeMillis();
        Map<CharSequence, Long> ajnaBuckets = Maps.newHashMap();
        ajnaBuckets.put("10,20", 1L);
        ajnaBuckets.put("20,100", 50L);
        com.salesforce.mandm.ajna.Histogram ajnaHistogram1 = new com.salesforce.mandm.ajna.Histogram("service", "subservice", 1, 1L, 1L,
                ajnaBuckets, null,
                Lists.newArrayList("metricName"), timestamp, LatencyType.NORMAL);
        com.salesforce.mandm.ajna.Histogram ajnaHistogram2 = new com.salesforce.mandm.ajna.Histogram("service", "subservice", 1, 1L, 1L,
                ajnaBuckets, null,
                Lists.newArrayList("metricName"), timestamp+10000, LatencyType.NORMAL);
        com.salesforce.mandm.ajna.Histogram ajnaHistogram3 = new com.salesforce.mandm.ajna.Histogram("service", "subservice", 1, 1L, 1L,
                ajnaBuckets, null,
                Lists.newArrayList("metricName"), timestamp+20000, LatencyType.NORMAL);

        when(consumer.histogramAvroDecoder.listFromAjnaWire(any(), any())).thenReturn(Lists.newArrayList(ajnaHistogram1, ajnaHistogram2, ajnaHistogram3));

        List<Histogram> results = Lists.newArrayList();
        consumer.processAjnaWireRecord(results, null, Maps.newHashMap());
        assertEquals(3, results.size());
    }

    @Test
    public void transformToArgusHistogram() {
        Map<CharSequence, Long> buckets = Maps.newHashMap();
        buckets.put("20,30", 1L);
        buckets.put("30,50", 2L);
        buckets.put("50,90", 3L);
        Map<CharSequence, CharSequence> tags = Maps.newHashMap();
        tags.put("tagk1", "tagv1");
        tags.put("tagk2", "tagv2");

        final String metricName = "testMetricName";

        com.salesforce.mandm.ajna.Histogram ajnaHistogram = new com.salesforce.mandm.ajna.Histogram("SERVICE", "subservice", 1, 2L, 3L,
                buckets, tags, Lists.newArrayList(metricName), System.currentTimeMillis(), LatencyType.NORMAL);

        //happy path
        Histogram histogram = task.metricConsumer.histogramConsumer.transformToArgusHistogram(ajnaHistogram, Collections.emptyMap(),
                null, null, null, null);
        assertEquals("service.subservice", histogram.getScope());
        assertEquals(2L, histogram.getOverflow().longValue());
        assertEquals(3L, histogram.getUnderflow().longValue());
        assertEquals(metricName, histogram.getMetric());
        assertEquals(histogram.getTags(), tags);
        Map<HistogramBucket, Long> argusBuckets = histogram.getBuckets();
        assertEquals(3, argusBuckets.size());

        //now try again with an invalid timestamp
        ajnaHistogram.setTimestamp(0L);
        assertNull(task.metricConsumer.histogramConsumer.transformToArgusHistogram(ajnaHistogram, Collections.emptyMap(),
                null, task.metricConsumer.histogramConsumer::updateHistogramTimestampInvalid, null, null));
    }

    @Test
    public void isHistogramSizeSafe() {
        //quick happy path test
        Histogram histogram = new Histogram("scope", "metric");
        assertTrue(HistogramConsumer.isHistogramSizeSafe(histogram));

        //see if the check correctly returns false as soon as a basic member becomes unsafe.  More extensive testing should be already covered in BaseConsumer checks
        histogram = new Histogram("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
                "metric");
        assertFalse(HistogramConsumer.isHistogramSizeSafe(histogram));
    }
}
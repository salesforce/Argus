package com.salesforce.perfeng.akc.consumer;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.service.AnnotationStorageService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.system.SystemMain;
import com.salesforce.mandm.ajna.Annotation;
import com.salesforce.mandm.ajna.LatencyType;
import com.salesforce.mandm.ajna.Metric;
import com.salesforce.mandm.avro.exception.AvroSerializerException;
import com.salesforce.mandm.avro.util.AjnaWireFormatDecoder;
import com.salesforce.mandm.avro.util.AjnaWireFormatEncoder;
import com.salesforce.perfeng.akc.AKCConfiguration;
import com.salesforce.quota.*;
import org.apache.commons.lang.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.record.TimestampType;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.core.classloader.annotations.SuppressStaticInitializationFor;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.function.Consumer;

import static com.salesforce.mandm.avro.SchemaField.TAG_KEY_DATACENTER;
import static com.salesforce.mandm.avro.SchemaField.TAG_KEY_POD;
import static com.salesforce.mandm.avro.SchemaField.TAG_KEY_SUPERPOD;
import static com.salesforce.dva.argus.entity.MetricSchemaRecord.RETENTION_DISCOVERY;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;

@RunWith(PowerMockRunner.class )
@SuppressStaticInitializationFor("com.salesforce.perfeng.akc.AKCConfiguration")
@PrepareForTest(QuotaUtilFactory.class)
public class AjnaConsumerTaskTest {
    private AjnaConsumerTask task;
    private TSDBService mockTsdbService = PowerMockito.mock(TSDBService.class);
    private SchemaService mockSchemaService = PowerMockito.mock(SchemaService.class);
    private AnnotationStorageService mockAnnotationStorageService = PowerMockito.mock(AnnotationStorageService.class);
    private InstrumentationService mockInstrumentationService = PowerMockito.mock(InstrumentationService.class);
    private IQuotaService mockQuotaService = PowerMockito.mock(IQuotaService.class);
    private IQuotaInfoProvider mockQuotaInfoProvider = PowerMockito.mock(IQuotaInfoProvider.class);
    private Properties props;
    private SystemMain system;
    private AjnaWireFormatEncoder<Metric> ajnaMetricEncoder;
    private AjnaWireFormatEncoder<Annotation> ajnaAnnEncoder;
    private AjnaWireFormatDecoder<Metric> metricAvroDecoder;
    private IBlacklistService mockBlacklistService = PowerMockito.mock(IBlacklistService.class);
    private String prdTopicName = "sfdc-metrics-prd";
    private static final int MAX_ANNOTATION_SIZE_BYTES = 2000;



    public com.salesforce.dva.argus.entity.Metric createArgusMetric() {
        String service = "argus.unittest";
        String subservice = "mysub";
        String mName = "unittestingmetric1";
        String hostname = "myhost.com";
        String dc = "PRD";
        String sp = "SP1";
        String pod = "na1";
        long ts = 12345;
        double val = 12345;
        Map<Long, Double> datapoints = new HashMap<>();
        datapoints.put(ts, val);
        Map<String, String> tags = new TreeMap<>();
        tags.put("hostname", hostname);
        tags.put("longtag", "tagval");
        com.salesforce.dva.argus.entity.Metric argusMetric =
            new com.salesforce.dva.argus.entity.Metric(service + "." +
                                                       subservice + "." +
                                                       dc + "." +
                                                       sp + "." +
                                                       pod,
                                                       mName);
        argusMetric.setDatapoints(datapoints);
        argusMetric.setTags(tags);
        return argusMetric;
    }

    public Metric createAjnaMetric() {
        String service = "argus.unittest";
        String subservice = "mysub";
        String mName = "unittestingmetric1";
        String hostname = "myhost.com";
        String dc = "PRD";
        String sp = "SP1";
        String pod = "na1";
        long ts = System.currentTimeMillis();
        double val = 12345;

        Metric ajnaMetric = new Metric();
        ajnaMetric.setService(service);
        ajnaMetric.setSubservice(subservice);
        ajnaMetric.setMetricName(Lists.newArrayList(mName));
        ajnaMetric.setTags(new HashMap<CharSequence, CharSequence>() {{
            put("hostname", hostname);
            put("datacenter", dc);
            put("superpod", sp);
            put("pod", pod);
            put("longtag", "tagval");}});
        ajnaMetric.setTimestamp(ts);
        ajnaMetric.setMetricValue(val);
        ajnaMetric.setLatency(LatencyType.NORMAL);

        return ajnaMetric;
    }

    public Annotation createAjnaAnnotation() {
        String scope = "argus.scope";
        String mName = "unittestingmetric1";
        String hostname = "myhost.com";
        String dc = "PRD";
        String sp = "SP1";
        String pod = "na1";
        long ts = 12345;
        String source = "mytestsource";
        String annId = "mytestannId";
        String annType = "mytestanntype";

        Annotation ajnaAnnotation = new Annotation();
        ajnaAnnotation.setScope(scope);
        ajnaAnnotation.setRelatedMetric(mName);
        ajnaAnnotation.setTags(new HashMap<CharSequence, CharSequence>() {{
            put("hostname", hostname);
            put("datacenter", dc);
            put("superpod", sp);
            put("pod", pod);}});
        ajnaAnnotation.setTimestamp(ts);
        ajnaAnnotation.setSource(source);
        ajnaAnnotation.setId(annId);
        ajnaAnnotation.setType(annType);
        ajnaAnnotation.setFields(new HashMap<CharSequence, CharSequence>() {{
            put("releasename", "218 winter release");
            put("phaseofmoon", "gibbous moon");}});


        return ajnaAnnotation;
    }

    @Before
    public void setUp() {
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
        when(AKCConfiguration.getParameter(AKCConfiguration.Parameter.MAX_ANNOTATION_SIZE_BYTES)).thenReturn(Integer.toString(MAX_ANNOTATION_SIZE_BYTES));
        when(AKCConfiguration.getParameter(AKCConfiguration.Parameter.ENABLE_MAX_METRICS_AGE)).thenReturn("true");
        when(AKCConfiguration.getParameter(AKCConfiguration.Parameter.MAX_METRICS_AGE_MS)).thenReturn("1296000000");

        props = new Properties();
        props.put(AKCConfiguration.Parameter.ID, "1234");
        props.put(AKCConfiguration.Parameter.BOOTSTRAP_SERVERS, "bootstrap_servers_test");
        props.put(AKCConfiguration.Parameter.CONSUMER_TYPE, "METRICS");
        props.put(AKCConfiguration.Parameter.RETRIES, "10");
        props.put(AKCConfiguration.Parameter.METRICS_BATCH_SIZE, "100");
        props.put(AKCConfiguration.Parameter.SCHEMA_BATCH_SIZE, "100");
        props.put(AKCConfiguration.Parameter.ANNOTATIONS_BATCH_SIZE, "100");
        props.put(AKCConfiguration.Parameter.SCHEMA_LOOKUP_TIMEOUT_MS, "100");
        props.put(AKCConfiguration.Parameter.QUOTA_SWITCH, "ON");
        props.put(AKCConfiguration.Parameter.MAX_ANNOTATION_SIZE_BYTES, Integer.toString(MAX_ANNOTATION_SIZE_BYTES));
        props.put(AKCConfiguration.Parameter.ENABLE_MAX_METRICS_AGE, "true");
        props.put(AKCConfiguration.Parameter.MAX_METRICS_AGE_MS, "1296000000");

        when(AKCConfiguration.getConfiguration()).thenReturn(props);

        doNothing().when(mockTsdbService).putMetrics(anyList());

        task = new AjnaConsumerTask();
        String schemaFingerprint = "naeZyIFmr7k8KwWzct0WvQ";
        ajnaMetricEncoder = new AjnaWireFormatEncoder<>(schemaFingerprint, new HashMap<>());
        ajnaAnnEncoder = new AjnaWireFormatEncoder<>("hH6S6XUhtqB45wmzVRv-6g", new HashMap<>());

        metricAvroDecoder = new AjnaWireFormatDecoder<>();
    }

    @Test
    public void testProcessInBatches() {
        List<Integer> numbers = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8));
        List<Integer> expected = new ArrayList<>(Arrays.asList(1, 1, 1, 4, 4, 4, 7, 7));
        List<Integer> actual = new ArrayList<>();
        Consumer<List<Integer>> numberProcessor = (nums) -> {
            int i = nums.get(0);
            for (int j = 0; j < nums.size(); j++) {
                actual.add(i);
            }
        };
        BaseConsumer.processInBatches(numbers, 3, numberProcessor);
        assertEquals(expected, actual);

        // Test when list length is divisible by batch size
        numbers.add(9);
        expected.add(7);
        actual.clear();
        BaseConsumer.processInBatches(numbers, 3, numberProcessor);
        assertEquals(expected, actual);

        // Test when list length is one more than batch-divisible size
        numbers.add(10);
        expected.add(10);
        actual.clear();
        BaseConsumer.processInBatches(numbers, 3, numberProcessor);
        assertEquals(expected, actual);

        // Test 0 batchSize -- process entire batch as one
        actual.clear();
        for (int i = 0; i < expected.size(); i++) {
            expected.set(i, 1);
        }
        BaseConsumer.processInBatches(numbers, 0, numberProcessor);
        assertEquals(expected, actual);

        // Test processing empty list
        numbers.clear();
        expected.clear();
        actual.clear();
        BaseConsumer.processInBatches(numbers, 3, numberProcessor);
        assertEquals(expected, actual);
    }
    @Test
    public void testMetricSizeNormal() {
        String scopeName = StringUtils.repeat("s", 250);
        com.salesforce.dva.argus.entity.Metric argusMetric =
            new com.salesforce.dva.argus.entity.Metric(scopeName, "metric");
        Map<String, String> tags = new HashMap<>();
        tags.put("tagkey", "blah");
        argusMetric.setTags(tags);
        argusMetric.setNamespace("mynamespace");
        argusMetric.setDisplayName("mydisplayname");
        argusMetric.setUnits("myblahunits");

        boolean retVal = BaseMetricConsumer.isMetricSizeSafe(argusMetric);
        assertTrue(retVal);
    }

    @Test
    public void testMetricSizeLargeScope() {
        String scopeName = StringUtils.repeat("s", 257);
        com.salesforce.dva.argus.entity.Metric argusMetric =
            new com.salesforce.dva.argus.entity.Metric(scopeName, "metric");
        boolean retVal = BaseMetricConsumer.isMetricSizeSafe(argusMetric);
        assertFalse(retVal);
    }

    @Test
    public void testMetricSizeLargeMetricname() {
        String fieldname = StringUtils.repeat("s", 2570);
        com.salesforce.dva.argus.entity.Metric argusMetric =
            new com.salesforce.dva.argus.entity.Metric("scope", fieldname);
        boolean retVal = BaseMetricConsumer.isMetricSizeSafe(argusMetric);
        assertFalse(retVal);
    }

    @Test
    public void testMetricSizeLargeTagkey() {
        String fieldname = StringUtils.repeat("s", 2570);
        com.salesforce.dva.argus.entity.Metric argusMetric =
            new com.salesforce.dva.argus.entity.Metric("scope", "metric");
        Map<String, String> tags = new HashMap<>();
        tags.put(fieldname, "blah");
        argusMetric.setTags(tags);
        boolean retVal = BaseMetricConsumer.isMetricSizeSafe(argusMetric);
        assertFalse(retVal);
    }

    @Test
    public void testMetricSizeLargeTagValue() {
        String fieldname = StringUtils.repeat("s", 2570);
        com.salesforce.dva.argus.entity.Metric argusMetric =
            new com.salesforce.dva.argus.entity.Metric("scope", "metric");
        Map<String, String> tags = new HashMap<>();
        tags.put("tagkeyname", fieldname);
        argusMetric.setTags(tags);
        boolean retVal = BaseMetricConsumer.isMetricSizeSafe(argusMetric);
        assertFalse(retVal);
    }

    @Test
    public void testMetricSizeLargeNamespace() {
        String fieldname = StringUtils.repeat("s", 2570);
        com.salesforce.dva.argus.entity.Metric argusMetric =
            new com.salesforce.dva.argus.entity.Metric("scope", "metric");
        argusMetric.setNamespace(fieldname);
        boolean retVal = BaseMetricConsumer.isMetricSizeSafe(argusMetric);
        assertFalse(retVal);
    }

    @Test
    public void testMetricSizeLargeDisplayName() {
        String fieldname = StringUtils.repeat("s", 2570);
        com.salesforce.dva.argus.entity.Metric argusMetric =
            new com.salesforce.dva.argus.entity.Metric("scope", "metric");
        argusMetric.setDisplayName(fieldname);
        boolean retVal = BaseMetricConsumer.isMetricSizeSafe(argusMetric);
        assertFalse(retVal);
    }

    @Test
    public void testMetricSizeLargeUnits() {
        String fieldname = StringUtils.repeat("s", 2570);
        com.salesforce.dva.argus.entity.Metric argusMetric =
            new com.salesforce.dva.argus.entity.Metric("scope", "metric");
        argusMetric.setUnits(fieldname);
        boolean retVal = BaseMetricConsumer.isMetricSizeSafe(argusMetric);
        assertFalse(retVal);
    }

    // ToDO : once MetatagsRecord is available in ArgusCore built version, then
    // add size checks tests for it here.
    // argusMetric.getMetatagsRecord() ....

    @Test
    public void testTransformToArgusMetricRejectsOldMetrics() {
        task.init(mockTsdbService,
                mockSchemaService,
                mockAnnotationStorageService,
                mockInstrumentationService,
                mockBlacklistService);
        Metric ajnaMetric = new Metric();
        String service = "argus.unittest";
        String subservice = "mysub";
        String mName = "unittestingmetric1";
        ajnaMetric.setService(service);
        ajnaMetric.setSubservice(subservice);
        ajnaMetric.setMetricName(Lists.newArrayList(mName));
        ajnaMetric.setTags(Maps.newHashMap());
        ajnaMetric.setMetricValue(0.0);
        ajnaMetric.setTimestamp(System.currentTimeMillis()/1000 - BaseConsumer.MAX_METRICS_AGE_MS/1000 - 1);
        assertNull(task.metricConsumer.transformToArgusMetric(ajnaMetric,
                new HashMap<>(),
                task.metricConsumer::updateDataPointsTooOld,
                task.metricConsumer::updateDataPointsTimestampInvalid,
                task.metricConsumer::updateDataPointsBlocked,
                task.metricConsumer::updateDataPointsDropped));

        // Test that metric is always processed when this config is disabled
        when(AKCConfiguration.getParameter(AKCConfiguration.Parameter.ENABLE_MAX_METRICS_AGE)).thenReturn("false");
        task = new AjnaConsumerTask();
        task.init(mockTsdbService,
                mockSchemaService,
                mockAnnotationStorageService,
                mockInstrumentationService,
                mockBlacklistService);
        assertNotNull(task.metricConsumer.transformToArgusMetric(ajnaMetric,
                new HashMap<>(),
                task.metricConsumer::updateDataPointsTooOld,
                task.metricConsumer::updateDataPointsTimestampInvalid,
                task.metricConsumer::updateDataPointsBlocked,
                task.metricConsumer::updateDataPointsDropped));

        // Invalid timestamps should still be blocked when config option is false
        ajnaMetric.setTimestamp(1L);
        assertNull(task.metricConsumer.transformToArgusMetric(ajnaMetric,
                new HashMap<>(),
                task.metricConsumer::updateDataPointsTooOld,
                task.metricConsumer::updateDataPointsTimestampInvalid,
                task.metricConsumer::updateDataPointsBlocked,
                task.metricConsumer::updateDataPointsDropped));

        // Two transformToArgusMetric calls had max age exceeded, should have been instrumented
        verify(mockInstrumentationService,
                times(2)).updateCounter(eq(InstrumentationService.DATAPOINTS_TOO_OLD),
                anyDouble(),
                anyMap());
        // Invalid timestamp should have been counted as dropped
        verify(mockInstrumentationService,
                times(1)).updateCounter(eq(InstrumentationService.DATAPOINTS_TIMESTAMP_INVALID),
                anyDouble(),
                anyMap());
    }

    @Test
    public void testTransformToArgusMetric() {
        String service = "argus.unittest";
        String subservice = "mysub";
        String mName = "unittestingmetric1";
        Metric ajnaMetric = new Metric();
        String hostname = "myhost.com";
        String dc = "PRD";
        String sp = "SP1";
        String pod = "na1";
        long ts = System.currentTimeMillis();
        double val = 12345;

        task.init(mockTsdbService,
                mockSchemaService,
                mockAnnotationStorageService,
                mockInstrumentationService,
                mockBlacklistService);

        Map<Long, Double> datapoints = new HashMap<>();
        datapoints.put(ts, val);
        Map<String, String> tags = new TreeMap<>();
        tags.put("hostname", hostname);
        com.salesforce.dva.argus.entity.Metric expectedArgusMetric =
            new com.salesforce.dva.argus.entity.Metric(service + "." +
                                                       subservice + "." +
                                                       dc + "." +
                                                       sp + "." +
                                                       pod,
                                                       mName);
        expectedArgusMetric.setDatapoints(datapoints);
        expectedArgusMetric.setTags(tags);


        ajnaMetric.setService(service);
        ajnaMetric.setSubservice(subservice);
        ajnaMetric.setMetricName(Lists.newArrayList(mName));
        ajnaMetric.setTags(new HashMap<CharSequence, CharSequence>() {{
            put("hostname", hostname);
            put("datacenter", dc);
            put("superpod", sp);
            put("pod", pod);
            put(RETENTION_DISCOVERY, "15");
        }});

        ajnaMetric.setTimestamp(ts);
        ajnaMetric.setMetricValue(val);

        Map<String, String> dpTags = new HashMap<>();
        com.salesforce.dva.argus.entity.Metric actualArgusMetric =
            task.metricConsumer.transformToArgusMetric(ajnaMetric, dpTags, null, null,null, null);

        assertEquals(expectedArgusMetric, actualArgusMetric);
        assertEquals("expect the retention value to exist in metatags","15", actualArgusMetric.getMetatagsRecord().getMetatags().get(RETENTION_DISCOVERY));
        assertEquals("something wrong with datacenter metatag value", dc, actualArgusMetric.getMetatagsRecord().getMetatags().get(TAG_KEY_DATACENTER));
        assertEquals("something wrong with superpod metatag value", sp, actualArgusMetric.getMetatagsRecord().getMetatags().get(TAG_KEY_SUPERPOD));
        assertEquals("something wrong with pod metatag value", pod, actualArgusMetric.getMetatagsRecord().getMetatags().get(TAG_KEY_POD));
    }

    @Test
    public void testIsTimestampInvalidOrOld() {
        task.init(mockTsdbService,
                mockSchemaService,
                mockAnnotationStorageService,
                mockInstrumentationService,
                mockBlacklistService);
        Metric ajnaMetric = new Metric();
        String service = "argus.unittest";
        String subservice = "mysub";
        String mName = "unittestingmetric1";
        ajnaMetric.setService(service);
        ajnaMetric.setSubservice(subservice);
        ajnaMetric.setMetricName(Lists.newArrayList(mName));
        ajnaMetric.setTags(Maps.newHashMap());
        ajnaMetric.setMetricValue(0.0);

        long nowInMillis = System.currentTimeMillis();
        long nowInSeconds = nowInMillis / 1000;
        Map<String, String> dpTags = new HashMap<>();

        // Test correct positives
        ajnaMetric.setTimestamp(nowInMillis - BaseConsumer.MAX_METRICS_AGE_MS - 1000);
        assertTrue(task.metricConsumer.isTimestampInvalidOrOld(ajnaMetric.getTimestamp(), dpTags, task.metricConsumer::updateDataPointsTooOld, task.metricConsumer::updateDataPointsTimestampInvalid));
        ajnaMetric.setTimestamp(nowInSeconds - BaseConsumer.MAX_METRICS_AGE_MS/1000 - 1);
        assertTrue(task.metricConsumer.isTimestampInvalidOrOld(ajnaMetric.getTimestamp(), dpTags, task.metricConsumer::updateDataPointsTooOld, task.metricConsumer::updateDataPointsTimestampInvalid));
        // Test correct negatives
        ajnaMetric.setTimestamp(nowInMillis - BaseConsumer.MAX_METRICS_AGE_MS + 1000);
        assertFalse(task.metricConsumer.isTimestampInvalidOrOld(ajnaMetric.getTimestamp(), dpTags, task.metricConsumer::updateDataPointsTooOld, task.metricConsumer::updateDataPointsTimestampInvalid));
        ajnaMetric.setTimestamp(nowInSeconds - BaseConsumer.MAX_METRICS_AGE_MS/1000 + 1);
        assertFalse(task.metricConsumer.isTimestampInvalidOrOld(ajnaMetric.getTimestamp(), dpTags, task.metricConsumer::updateDataPointsTooOld, task.metricConsumer::updateDataPointsTimestampInvalid));
        // Very futuristic dates before Nov 20, 2286 should still pass
        ajnaMetric.setTimestamp(9999999999999L);
        assertFalse(task.metricConsumer.isTimestampInvalidOrOld(ajnaMetric.getTimestamp(), dpTags, task.metricConsumer::updateDataPointsTooOld, task.metricConsumer::updateDataPointsTimestampInvalid));
        ajnaMetric.setTimestamp(9999999999L);
        assertFalse(task.metricConsumer.isTimestampInvalidOrOld(ajnaMetric.getTimestamp(), dpTags, task.metricConsumer::updateDataPointsTooOld, task.metricConsumer::updateDataPointsTimestampInvalid));
        // Ridiculously futuristic or old dates should be negative anyway
        ajnaMetric.setTimestamp(99999999999999L);
        assertTrue(task.metricConsumer.isTimestampInvalidOrOld(ajnaMetric.getTimestamp(), dpTags, task.metricConsumer::updateDataPointsTooOld, task.metricConsumer::updateDataPointsTimestampInvalid));
        ajnaMetric.setTimestamp(999999999999L);
        assertTrue(task.metricConsumer.isTimestampInvalidOrOld(ajnaMetric.getTimestamp(), dpTags, task.metricConsumer::updateDataPointsTooOld, task.metricConsumer::updateDataPointsTimestampInvalid));
        ajnaMetric.setTimestamp(999999999L);
        assertTrue(task.metricConsumer.isTimestampInvalidOrOld(ajnaMetric.getTimestamp(), dpTags, task.metricConsumer::updateDataPointsTooOld, task.metricConsumer::updateDataPointsTimestampInvalid));
        ajnaMetric.setTimestamp(1L);
        assertTrue(task.metricConsumer.isTimestampInvalidOrOld(ajnaMetric.getTimestamp(), dpTags, task.metricConsumer::updateDataPointsTooOld, task.metricConsumer::updateDataPointsTimestampInvalid));

        verify(mockInstrumentationService,
                times(2)).updateCounter(eq(InstrumentationService.DATAPOINTS_TOO_OLD),
                anyDouble(),
                anyMap());
        verify(mockInstrumentationService,
                times(4)).updateCounter(eq(InstrumentationService.DATAPOINTS_TIMESTAMP_INVALID),
                anyDouble(),
                anyMap());
    }

    @Test
    public void testSendQuotaCounters() {
        Metric ajnaMetric = createAjnaMetric();
        task.init(mockTsdbService,
                mockSchemaService,
                mockAnnotationStorageService,
                mockInstrumentationService,
                mockBlacklistService);
        task.metricConsumer.sendQuotaCounters(ajnaMetric);
        // verify quota service consumed counter is updated
        verify(mockInstrumentationService,
               atLeast(1)).updateCounter(eq(InstrumentationService.QUOTA_SERVICE_CONSUMED),
                                         anyDouble(),
                                         anyMap(),
                                         anyBoolean());
        // verify quota subservice consumed counter is updated
        verify(mockInstrumentationService,
               atLeast(1)).updateCounter(eq(InstrumentationService.QUOTA_SUBSERVICE_CONSUMED),
                                         anyDouble(),
                                         anyMap(),
                                         anyBoolean());
        // verify quota consumed counter is updated
        verify(mockInstrumentationService,
               atLeast(1)).updateCounter(eq(InstrumentationService.QUOTA_CONSUMED),
                                         anyDouble(),
                                         anyMap(),
                                         anyBoolean());
        // verify datapoints consumed counter is updated
        verify(mockInstrumentationService,
               atLeast(1)).updateCounter(eq(InstrumentationService.DATAPOINTS_CONSUMED_LATEST),
                                         anyDouble(),
                                         anyMap(),
                                         anyBoolean());
    }

    @Test(expected = AvroSerializerException.class)
    public void testExtractAjnaMetricsAvroFailure() {
        task.init(mockTsdbService,
                mockSchemaService,
                mockAnnotationStorageService,
                mockInstrumentationService,
                mockBlacklistService);

        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<byte[], byte[]>(prdTopicName,
                                                                                   123,
                                                                                   1,
                                                                                   12234,
                                                                                   TimestampType.CREATE_TIME,
                                                                                   0L,
                                                                                   0,
                                                                                   0,
                                                                                   "rawkey".getBytes(),
                                                                                   "".getBytes());
        task.metricConsumer.extractAjnaMetrics(record, metricAvroDecoder.ajnaWireFromBytes(record.value()));
        verify(mockInstrumentationService, atLeast(4)).updateCounter(eq("blh"),
                                                                     anyDouble(),
                                                                     anyMap(),
                                                                     anyBoolean());

    }

    @Test
    public void testExtractAjnaMetrics() {
        task.init(mockTsdbService,
                mockSchemaService,
                mockAnnotationStorageService,
                mockInstrumentationService,
                mockBlacklistService);

        Metric expectedAjnaMetric = createAjnaMetric();
        List<Metric> expectedAjnaMetricList = new ArrayList<>();
        expectedAjnaMetricList.add(expectedAjnaMetric);
        byte [] payload = ajnaMetricEncoder.toBytes(expectedAjnaMetric);

        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<byte[], byte[]>(prdTopicName,
                                                                                   123,
                                                                                   1,
                                                                                   12234,
                                                                                   TimestampType.CREATE_TIME,
                                                                                   0L,
                                                                                   0,
                                                                                   0,
                                                                                   "rawkey".getBytes(),
                                                                                   payload);
        // Function under test
        List<Metric> actualAjnaMetrics = task.metricConsumer.extractAjnaMetrics(record, metricAvroDecoder.ajnaWireFromBytes(record.value()));
        // verify if returned metric is as expected
        assertEquals(expectedAjnaMetricList, actualAjnaMetrics);
    }

    @Test
    public void testProcessArgusMetrics() {
        task.init(mockTsdbService,
                mockSchemaService,
                mockAnnotationStorageService,
                mockInstrumentationService,
                mockBlacklistService);

        List<com.salesforce.dva.argus.entity.Metric> metrics = new ArrayList<>();
        com.salesforce.dva.argus.entity.Metric argusMetric = createArgusMetric();
        metrics.add(argusMetric);
        metrics.add(argusMetric);
        metrics.add(argusMetric);
        metrics.add(argusMetric);

        // function under test
        task.metricConsumer.processArgusMetrics(metrics);
        // verify if putmetrics is called
        verify(mockTsdbService, atLeast(1)).putMetrics(anyList());
        // verify if internal argus metric func is called
        verify(mockInstrumentationService, atLeast(1)).updateCounter(eq(InstrumentationService.DATAPOINTS_POSTED),
                                                                     anyDouble(),
                                                                     any());
        // verify metric process latency is recorded
        verify(mockInstrumentationService).updateTimer(eq(InstrumentationService.METRIC_PROCESS_LATENCY),
                anyDouble(),
                isNull());
    }

    @Test
    public void testProcessArgusMetricSchemas() {
        task.init(mockTsdbService,
                mockSchemaService,
                mockAnnotationStorageService,
                mockInstrumentationService,
                mockBlacklistService);

        List<com.salesforce.dva.argus.entity.Metric> metrics = new ArrayList<>();
        com.salesforce.dva.argus.entity.Metric argusMetric = createArgusMetric();
        metrics.add(argusMetric);
        metrics.add(argusMetric);
        metrics.add(argusMetric);
        metrics.add(argusMetric);

        // function under test
        task.schemaConsumer.processArgusMetricSchemas(metrics);
        // verify if schema put is called
        verify(mockSchemaService, atLeast(1)).put(anyList());
        // verify if internal argus metric func is called
        verify(mockInstrumentationService, atLeast(1)).updateCounter(eq(InstrumentationService.SCHEMA_POSTED),
                                                                     anyDouble(),
                                                                     any());
        // verify schema process latency is recorded
        verify(mockInstrumentationService).updateTimer(eq(InstrumentationService.SCHEMA_PROCESS_LATENCY),
                anyDouble(),
                isNull());
    }

    @Test
    public void testProcessAjnaAnnotationKafkaRecords() {
        when(AKCConfiguration.getParameter(AKCConfiguration.Parameter.CONSUMER_TYPE)).thenReturn("ANNOTATIONS");

        task.init(mockTsdbService,
                mockSchemaService,
                mockAnnotationStorageService,
                mockInstrumentationService,
                mockBlacklistService);

        Annotation expectedAjnaAnnotation = createAjnaAnnotation();
        byte [] payload = ajnaAnnEncoder.toBytes(expectedAjnaAnnotation);

        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<byte[], byte[]>(prdTopicName,
                                                                                   123,
                                                                                   1,
                                                                                   12234,
                                                                                   TimestampType.CREATE_TIME,
                                                                                   0L,
                                                                                   0,
                                                                                   0,
                                                                                   "rawkey".getBytes(),
                                                                                   payload);

        Map<TopicPartition, List<ConsumerRecord<byte[], byte[]>>> records = new LinkedHashMap<>();
        records.put(new TopicPartition("mytopicsname", 1), Arrays.asList(record, record));
        ConsumerRecords<byte[], byte[]> consumerRecords = new ConsumerRecords<>(records);

        // function under test
        task.annotationConsumer.processAjnaAnnotationKafkaRecords(consumerRecords, Lists.newArrayList());

        Map<String, String> topicMap = new HashMap<>();
        topicMap.put("topic", prdTopicName);
        // verify if consumed counters are updated
        verify(mockInstrumentationService, atLeast(1)).
            updateCounter(eq(InstrumentationService.ANNOTATIONS_CONSUMED),
                          eq(1.0d),
                          eq(topicMap));

        // verify if posted counters are updated
        verify(mockInstrumentationService, atLeast(1)).
            updateCounter(eq(InstrumentationService.ANNOTATIONS_POSTED),
                          eq(2.0d),
                          any());

        //verify annotation batch counter
        verify(mockInstrumentationService).updateCounter(eq(InstrumentationService.ANNOTATIONS_BATCH_COUNT),
                          anyDouble(),
                          isNull());
        // verify annotation process latency is recorded
        verify(mockInstrumentationService).updateTimer(eq(InstrumentationService.ANNOTATIONS_PROCESS_LATENCY),
                anyDouble(),
                isNull());
    }

    @Test
    public void testProcessAjnaMetricKafkaRecords() {
        AjnaConsumerTask metricTask = new AjnaConsumerTask();

        metricTask.init(mockTsdbService,
                mockSchemaService,
                mockAnnotationStorageService,
                mockInstrumentationService,
                mockBlacklistService);

        Metric expectedAjnaMetric = createAjnaMetric();
        byte [] payload = ajnaMetricEncoder.toBytes(expectedAjnaMetric);

        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<byte[], byte[]>(prdTopicName,
                                                                                   123,
                                                                                   1,
                                                                                   System.currentTimeMillis(),
                                                                                   TimestampType.CREATE_TIME,
                                                                                   0L,
                                                                                   0,
                                                                                   0,
                                                                                   "rawkey".getBytes(),
                                                                                   payload);

        Map<TopicPartition, List<ConsumerRecord<byte[], byte[]>>> records = new LinkedHashMap<>();
        // add the metric twice
        records.put(new TopicPartition(prdTopicName, 1), Arrays.asList(record, record));
        ConsumerRecords<byte[], byte[]> consumerRecords = new ConsumerRecords<>(records);

        Map<String, String> topicMap = new HashMap<>();
        topicMap.put("topic", prdTopicName);
        // function under test
        metricTask.metricConsumer.processAjnaMetricKafkaRecords(consumerRecords, Maps.newHashMap(), Lists.newArrayList());
        // verify if datapoints were consumed are updated
        verify(mockInstrumentationService, atLeast(1)).
            updateCounter(eq(InstrumentationService.DATAPOINTS_CONSUMED),
                          eq(1.0d),
                          eq(topicMap));

        // verify if datapoints deduped counter are updated
        verify(mockInstrumentationService, atLeast(1)).
            updateCounter(eq(InstrumentationService.METRIC_DATAPOINTS_DEDUPED),
                          eq(1.0d),
                          any());

        // verify if datapoints posted counter are updated
        verify(mockInstrumentationService, atLeast(1)).
            updateCounter(eq(InstrumentationService.DATAPOINTS_POSTED),
                          eq(1.0d),
                          any());

        //verify batch counter
        verify(mockInstrumentationService).updateCounter(eq(InstrumentationService.METRIC_BATCH_COUNT),
                          anyDouble(),
                          isNull());
        // verify metric process latency is recorded
        verify(mockInstrumentationService).updateTimer(eq(InstrumentationService.METRIC_PROCESS_LATENCY),
                anyDouble(),
                isNull());
    }

    @Test
    public void isAnnotationSizeSafe_testSafeSize() {
        com.salesforce.dva.argus.entity.Annotation a = new com.salesforce.dva.argus.entity.Annotation("source",
                "id",
                "type",
                "scope",
                "metric",
                System.currentTimeMillis());
        assertTrue(AnnotationConsumer.isAnnotationSizeSafe(a));
    }

    @Test
    public void isAnnotationSizeSafe_testUnsafeSize() {
        com.salesforce.dva.argus.entity.Annotation a = new com.salesforce.dva.argus.entity.Annotation("source",
                "id",
                "type",
                "scope",
                "metric",
                System.currentTimeMillis());
        final int TAG_SIZE = 100;
        final int NUM_TAGS = MAX_ANNOTATION_SIZE_BYTES / TAG_SIZE / 2;
        for (int i = 0; i < NUM_TAGS; i++) {
            a.setTag(RandomStringUtils.random(TAG_SIZE), RandomStringUtils.random(TAG_SIZE));
        }
        final Map<String, String> fields = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            fields.put(RandomStringUtils.random(10), RandomStringUtils.random(10));
        }
        a.setFields(fields);
        assertFalse(AnnotationConsumer.isAnnotationSizeSafe(a));
    }

    @Test
    public void isAnnotationSizeSafe_testNullAnnotation() {
        com.salesforce.dva.argus.entity.Annotation a = null;
        assertTrue(AnnotationConsumer.isAnnotationSizeSafe(a));
    }

    @Test
    public void processAjnaAnnotationKafkaRecords_testAnnotationUnsafeSize() {
        when(AKCConfiguration.getParameter(AKCConfiguration.Parameter.CONSUMER_TYPE)).thenReturn("ANNOTATIONS");

        task.init(mockTsdbService,
                mockSchemaService,
                mockAnnotationStorageService,
                mockInstrumentationService,
                mockBlacklistService);

        Annotation expectedAjnaAnnotation = createAjnaAnnotation();
        // blow up the size of annotation above the max size limit
        expectedAjnaAnnotation.setId(RandomStringUtils.random(MAX_ANNOTATION_SIZE_BYTES));
        byte [] payload = ajnaAnnEncoder.toBytes(expectedAjnaAnnotation);

        ConsumerRecord<byte[], byte[]> record = new ConsumerRecord<byte[], byte[]>(prdTopicName,
                123,
                1,
                12234,
                TimestampType.CREATE_TIME,
                0L,
                0,
                0,
                "rawkey".getBytes(),
                payload);

        Map<TopicPartition, List<ConsumerRecord<byte[], byte[]>>> records = new LinkedHashMap<>();
        records.put(new TopicPartition("mytopicsname", 1), Arrays.asList(record, record));
        ConsumerRecords<byte[], byte[]> consumerRecords = new ConsumerRecords<>(records);

        // function under test
        task.annotationConsumer.processAjnaAnnotationKafkaRecords(consumerRecords, Lists.newArrayList());

        Map<String, String> tagsMap1 = new HashMap<>();
        tagsMap1.put("topic", prdTopicName);
        Map<String, String> tagsMap2 = new HashMap<>(tagsMap1);
        tagsMap2.put("source", expectedAjnaAnnotation.getSource().toString());

        // verify if dropped counters are updated
        verify(mockInstrumentationService, atLeast(1)).
                updateCounter(eq(InstrumentationService.ANNOTATIONS_DROPPED_TOOLARGE),
                        eq(1.0d),
                        eq(tagsMap2));

        // verify if consumed counters are updated
        verify(mockInstrumentationService, atLeast(1)).
                updateCounter(eq(InstrumentationService.ANNOTATIONS_CONSUMED),
                        eq(1.0d),
                        eq(tagsMap1));

    }

    @Test
    public void testQuota_scopeLevelDisabled() {
        //disable scope level quotas
        when(AKCConfiguration.getParameter(AKCConfiguration.Parameter.QUOTA_SCOPE_LEVEL_ENABLED)).thenReturn("false");

        mockStatic(QuotaUtilFactory.class);
        when(QuotaUtilFactory.getQuotaService()).thenReturn(mockQuotaService);
        when(QuotaUtilFactory.getQuotaInfoProvider()).thenReturn(mockQuotaInfoProvider);

        // ensure no infractions are returned
        when(mockQuotaService.evaluate(any(), anyLong(), anyLong())).thenReturn(new InMemoryInfractionList());
        when(mockQuotaInfoProvider.getGroupLevelPrincipal()).thenReturn(new InMemoryPrincipal("test_group_principal"));

        task.init(mockTsdbService,
                mockSchemaService,
                mockAnnotationStorageService,
                mockInstrumentationService,
                mockBlacklistService);

        Metric ajnaMetric = createAjnaMetric();

        // should ingest since there are no infractions
        assertTrue(task.metricConsumer.canIngest(ajnaMetric, 2000));
    }

    @Test
    public void testQuota_scopeLevelEnabled() {
        //enable scope level quotas
        when(AKCConfiguration.getParameter(AKCConfiguration.Parameter.QUOTA_SCOPE_LEVEL_ENABLED)).thenReturn("true");

        mockStatic(QuotaUtilFactory.class);
        when(QuotaUtilFactory.getQuotaService()).thenReturn(mockQuotaService);
        when(QuotaUtilFactory.getQuotaInfoProvider()).thenReturn(mockQuotaInfoProvider);

        // test infraction list, principal, etc.
        InMemoryPrincipal principal = new InMemoryPrincipal("test_scope_principal");
        InMemoryPolicy policy = new InMemoryPolicy("test", "testPolicy", 60, 0, PolicyTrigger.DROP, PolicyScope.OVERALL);
        InMemoryInfraction infraction = new InMemoryInfraction(principal, policy, 0L, 0L);
        InMemoryInfractionList infractionList = new InMemoryInfractionList();
        List<IPrincipal> principalList = new ArrayList<>();
        principalList.add(principal);
        infractionList.addInfraction(infraction);

        // ensure an infraction is returned for scope level principal
        when(mockQuotaService.evaluate(any(), anyLong(), anyLong())).thenReturn(infractionList);

        Metric ajnaMetric = createAjnaMetric();

        when(mockQuotaInfoProvider.getGroupLevelPrincipal()).thenReturn(new InMemoryPrincipal("test_group_principal"));
        when(mockQuotaInfoProvider.extractPrincipalsFromMetric(ajnaMetric)).thenReturn(principalList);

        task.init(mockTsdbService,
                mockSchemaService,
                mockAnnotationStorageService,
                mockInstrumentationService,
                mockBlacklistService);

        // should not ingest since there are infractions
        assertFalse(task.metricConsumer.canIngest(ajnaMetric, 2000));
    }

    @Test
    public void testQuota_groupThrottlingDisabled() {
        IInfractionList mockInfractionList = PowerMockito.mock(IInfractionList.class);

        //disable group level throttling
        when(AKCConfiguration.getParameter(AKCConfiguration.Parameter.QUOTA_GROUP_THROTTLING_ENABLED)).thenReturn("false");

        mockStatic(QuotaUtilFactory.class);
        when(QuotaUtilFactory.getQuotaService()).thenReturn(mockQuotaService);
        when(QuotaUtilFactory.getQuotaInfoProvider()).thenReturn(mockQuotaInfoProvider);

        // test principal
        InMemoryPrincipal inMemoryPrincipal = new InMemoryPrincipal("test_group_principal");
        when(mockQuotaInfoProvider.getGroupLevelPrincipal()).thenReturn(inMemoryPrincipal);

        // ensure an infraction list is returned for group level principal
        when(mockQuotaService.evaluate(eq(inMemoryPrincipal), anyLong(), anyLong())).thenReturn(mockInfractionList);

        task.init(mockTsdbService,
                mockSchemaService,
                mockAnnotationStorageService,
                mockInstrumentationService,
                mockBlacklistService);

        Metric ajnaMetric = createAjnaMetric();

        // evaluate should not be called for the group level principal
        task.metricConsumer.canIngest(ajnaMetric, 2000);
        verify(mockInfractionList, times(0)).getInfractionByPolicyAction(any());
    }

    @Test
    public void testQuota_groupThrottlingEnabled() {
        //enable group level throttling
        when(AKCConfiguration.getParameter(AKCConfiguration.Parameter.QUOTA_GROUP_THROTTLING_ENABLED)).thenReturn("true");

        mockStatic(QuotaUtilFactory.class);
        when(QuotaUtilFactory.getQuotaService()).thenReturn(mockQuotaService);
        when(QuotaUtilFactory.getQuotaInfoProvider()).thenReturn(mockQuotaInfoProvider);

        // test principal
        InMemoryPrincipal inMemoryPrincipal = new InMemoryPrincipal("test_group_principal");
        when(mockQuotaInfoProvider.getGroupLevelPrincipal()).thenReturn(inMemoryPrincipal);

        // ensure an infraction list is returned for group level principal
        when(mockQuotaService.evaluate(eq(inMemoryPrincipal), anyLong(), anyLong())).thenReturn(new InMemoryInfractionList());

        task.init(mockTsdbService,
                mockSchemaService,
                mockAnnotationStorageService,
                mockInstrumentationService,
                mockBlacklistService);

        Metric ajnaMetric = createAjnaMetric();

        // evaluate be called for the group level principal
        task.metricConsumer.canIngest(ajnaMetric, 2000);
        verify(mockQuotaService, times(1)).evaluate(eq(inMemoryPrincipal), anyLong(), anyLong());
    }

    @Test
    public void testBlacklisting_blacklistedMetricScope() {
        mockStatic(QuotaUtilFactory.class);
        when(QuotaUtilFactory.getBlacklistService()).thenReturn(mockBlacklistService);

        AjnaConsumerTask metricTask = new AjnaConsumerTask();
        Metric ajnaMetric = createAjnaMetric();

        String ajnaMetricScope = "argus.unittest.mysub.PRD.SP1.na1"; //scope of above metric

        when(mockBlacklistService.isBlacklistedMetricScope(ajnaMetricScope)).thenReturn(true);

        metricTask.init(mockTsdbService,
                mockSchemaService,
                mockAnnotationStorageService,
                mockInstrumentationService,
                mockBlacklistService);

        Map<String, String> dpTags = new HashMap<>();
        com.salesforce.dva.argus.entity.Metric actualArgusMetric =
                metricTask.metricConsumer.transformToArgusMetric(ajnaMetric,
                        dpTags,
                        metricTask.metricConsumer::updateDataPointsTooOld,
                        metricTask.metricConsumer::updateDataPointsTimestampInvalid,
                        metricTask.metricConsumer::updateDataPointsBlocked,
                        metricTask.metricConsumer::updateDataPointsDropped);

        assertNull(actualArgusMetric);
    }

    @Test
    public void testBlacklisting_blacklistedAnnotationScope() {
        mockStatic(QuotaUtilFactory.class);
        when(QuotaUtilFactory.getBlacklistService()).thenReturn(mockBlacklistService);

        AjnaConsumerTask metricTask = new AjnaConsumerTask();
        Annotation ajnaAnnotation = createAjnaAnnotation();
        String ajnaAnnotationScope = "argus.scope"; // scope of above annotation

        when(mockBlacklistService.isBlacklistedAnnotationScope(ajnaAnnotationScope)).thenReturn(true);

        metricTask.init(mockTsdbService,
                mockSchemaService,
                mockAnnotationStorageService,
                mockInstrumentationService,
                mockBlacklistService);

        assertNull(metricTask.annotationConsumer.transformToArgusAnnotation(ajnaAnnotation));
    }

    @Test
    public void testBlacklisting_blacklistedTags() {
        mockStatic(QuotaUtilFactory.class);
        when(QuotaUtilFactory.getBlacklistService()).thenReturn(mockBlacklistService);

        when(mockBlacklistService.isBlacklistedTag("longtag")).thenReturn(true);

        task.init(mockTsdbService,
                mockSchemaService,
                mockAnnotationStorageService,
                mockInstrumentationService,
                mockBlacklistService);
        String service = "argus.unittest";
        String subservice = "mysub";
        String mName = "unittestingmetric1";
        String hostname = "myhost.com";
        String dc = "PRD";
        String sp = "SP1";
        String pod = "na1";
        long ts = 12345;
        double val = 12345;

        Map<Long, Double> datapoints = new HashMap<>();
        datapoints.put(ts, val);
        Map<String, String> tags = new TreeMap<>();
        tags.put("hostname", hostname);
        com.salesforce.dva.argus.entity.Metric expectedArgusMetric =
                new com.salesforce.dva.argus.entity.Metric(service + "." +
                        subservice + "." +
                        dc + "." +
                        sp + "." +
                        pod,
                        mName);
        expectedArgusMetric.setDatapoints(datapoints);
        expectedArgusMetric.setTags(tags);

        Map<String, String> dpTags = new HashMap<>();
        com.salesforce.dva.argus.entity.Metric actualArgusMetric = task.metricConsumer
                .transformToArgusMetric(createAjnaMetric(),
                        dpTags,
                        task.metricConsumer::updateDataPointsTooOld,
                        task.metricConsumer::updateDataPointsTimestampInvalid,
                        task.metricConsumer::updateDataPointsBlocked,
                        task.metricConsumer::updateDataPointsDropped);

        assertEquals(expectedArgusMetric, actualArgusMetric);
    }

    @Test
    public void testBlacklisting_unblacklistedMetricScope() {
        mockStatic(QuotaUtilFactory.class);
        when(QuotaUtilFactory.getBlacklistService()).thenReturn(mockBlacklistService);

        Metric ajnaMetric = createAjnaMetric();
        String ajnaMetricScope = "argus.unittest.mysub.PRD.SP1.na1"; //scope of above metric
        when(mockBlacklistService.isBlacklistedMetricScope(ajnaMetricScope)).thenReturn(false);

        task.init(mockTsdbService,
                mockSchemaService,
                mockAnnotationStorageService,
                mockInstrumentationService,
                mockBlacklistService);

        // create transformed metric to test against
        String service = "argus.unittest";
        String subservice = "mysub";
        String mName = "unittestingmetric1";
        String hostname = "myhost.com";
        String dc = "PRD";
        String sp = "SP1";
        String pod = "na1";
        long ts = 12345;
        double val = 12345;

        Map<Long, Double> datapoints = new HashMap<>();
        datapoints.put(ts, val);
        Map<String, String> tags = new TreeMap<>();
        tags.put("hostname", hostname);
        tags.put("longtag", "tagval");
        com.salesforce.dva.argus.entity.Metric expectedArgusMetric =
                new com.salesforce.dva.argus.entity.Metric(service + "." +
                        subservice + "." +
                        dc + "." +
                        sp + "." +
                        pod,
                        mName);
        expectedArgusMetric.setDatapoints(datapoints);
        expectedArgusMetric.setTags(tags);

        // transform ajna metric
        Map<String, String> dpTags = new HashMap<>();
        com.salesforce.dva.argus.entity.Metric actualArgusMetric =
                task.metricConsumer.transformToArgusMetric(ajnaMetric,
                        dpTags,
                        task.metricConsumer::updateDataPointsTooOld,
                        task.metricConsumer::updateDataPointsTimestampInvalid,
                        task.metricConsumer::updateDataPointsBlocked,
                        task.metricConsumer::updateDataPointsDropped);

        assertEquals(expectedArgusMetric, actualArgusMetric);
    }

    @Test
    public void testBlacklisting_unblacklistedAnnotationScope() {
        mockStatic(QuotaUtilFactory.class);
        when(QuotaUtilFactory.getBlacklistService()).thenReturn(mockBlacklistService);

        Annotation ajnaAnnotation = createAjnaAnnotation();
        String ajnaAnnotationScope = "argus.scope";
        when(mockBlacklistService.isBlacklistedAnnotationScope(ajnaAnnotationScope)).thenReturn(false);

        task.init(mockTsdbService,
                mockSchemaService,
                mockAnnotationStorageService,
                mockInstrumentationService,
                mockBlacklistService);

        //create argus annotation to test against
        com.salesforce.dva.argus.entity.Annotation argusAnnotation = new com.salesforce.dva.argus.entity.Annotation("mytestsource",
                "mytestannId",
                "mytestanntype",
                "argus.scope",
                "unittestingmetric1",
                12345L);
        Map<String, String> tags = new TreeMap<>();
        tags.put("hostname", "myhost.com");
        tags.put("pod", "na1");
        tags.put("datacenter", "PRD");
        tags.put("superpod", "SP1");

        Map<String, String> fields = new TreeMap<>();
        fields.put("releasename", "218 winter release");
        fields.put("phaseofmoon", "phaseofmoon moon");

        argusAnnotation.setTags(tags);
        argusAnnotation.setFields(fields);

        // transform ajna annotation
        com.salesforce.dva.argus.entity.Annotation actualArgusMetric =
                task.annotationConsumer.transformToArgusAnnotation(ajnaAnnotation);

        assertEquals(argusAnnotation, actualArgusMetric);
    }

    @Test
    public void testBlacklisting_unblacklistedTags() {
        mockStatic(QuotaUtilFactory.class);
        when(QuotaUtilFactory.getBlacklistService()).thenReturn(mockBlacklistService);

        when(mockBlacklistService.isBlacklistedTag("longtag")).thenReturn(false);
        task.init(mockTsdbService,
                mockSchemaService,
                mockAnnotationStorageService,
                mockInstrumentationService,
                mockBlacklistService);
        String service = "argus.unittest";
        String subservice = "mysub";
        String mName = "unittestingmetric1";
        String hostname = "myhost.com";
        String dc = "PRD";
        String sp = "SP1";
        String pod = "na1";
        long ts = 12345;
        double val = 12345;

        Map<Long, Double> datapoints = new HashMap<>();
        datapoints.put(ts, val);
        Map<String, String> tags = new TreeMap<>();
        tags.put("hostname", hostname);
        tags.put("longtag", "tagval");
        com.salesforce.dva.argus.entity.Metric expectedArgusMetric =
                new com.salesforce.dva.argus.entity.Metric(service + "." +
                        subservice + "." +
                        dc + "." +
                        sp + "." +
                        pod,
                        mName);
        expectedArgusMetric.setDatapoints(datapoints);
        expectedArgusMetric.setTags(tags);

        Metric ajnaMetric = createAjnaMetric();
        Map<String, String> dpTags = new HashMap<>();
        com.salesforce.dva.argus.entity.Metric actualArgusMetric =
                task.metricConsumer.transformToArgusMetric(ajnaMetric,
                        dpTags,
                        task.metricConsumer::updateDataPointsTooOld,
                        task.metricConsumer::updateDataPointsTimestampInvalid,
                        task.metricConsumer::updateDataPointsBlocked,
                        task.metricConsumer::updateDataPointsDropped);

        assertEquals(expectedArgusMetric, actualArgusMetric);
    }
}

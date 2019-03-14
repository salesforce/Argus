package com.salesforce.dva.argus.service.collect;

import com.fasterxml.jackson.databind.type.CollectionType;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.entity.Histogram;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.MQService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.MonitorService.Counter;
import com.salesforce.dva.argus.service.NamespaceService;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.service.WardenService;
import org.apache.commons.lang.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.salesforce.dva.argus.service.MQService.MQQueue.ANNOTATION;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(org.mockito.runners.MockitoJUnitRunner.class)
public class DefaultCollectionServiceTest extends AbstractTest {

    @Mock MQService mqService;
    @Mock TSDBService tsdbService;
    @Mock AuditService auditService;
    @Mock SchemaService schemaService;
    @Mock WardenService wardenService;
    @Mock MonitorService monitorService;
    @Mock NamespaceService namespaceService;
    DefaultCollectionService collectionService;
    PrincipalUser user;

    @Before
    public void setup() {
        collectionService = new DefaultCollectionService(mqService, tsdbService, auditService, system.getConfiguration(), schemaService, wardenService, monitorService, namespaceService);
        UserService userService = system.getServiceFactory().getUserService();
        user = new PrincipalUser(userService.findAdminUser(), "aUser", "aUser@mycompany.abc");
        when(wardenService.isWardenServiceEnabled()).thenReturn(true);
    }

    Metric createMetricWithIncreasingResolution(long minResolution, int dpCount) {
        Metric metric = createMetric();
        metric.clearDatapoints();
        long time = System.currentTimeMillis();
        Map<Long, Double> dps = new HashMap<>();
        for (int i = dpCount; i > 0; i--) {
            time -= i * minResolution;
            dps.put(time, 1.0);
        }
        metric.setDatapoints(dps);
        return metric;
    }

    @Test
    public void testParseMetricData() {
        long bigRes = 10000;
        Metric metricBiggerRes = createMetricWithIncreasingResolution(bigRes, 6);

        collectionService.submitMetric(user, metricBiggerRes);
        verify(wardenService).updatePolicyCounter(user, WardenService.PolicyCounter.MINIMUM_RESOLUTION_MS, bigRes);

        long smallRes = 1000;
        Metric metricSmallerRes = createMetricWithIncreasingResolution(smallRes, 6);
        metricSmallerRes.setTag("source", "unittest2");

        collectionService.submitMetrics(user, Arrays.asList(metricBiggerRes, metricSmallerRes));
        verify(wardenService).updatePolicyCounter(user, WardenService.PolicyCounter.MINIMUM_RESOLUTION_MS, smallRes);
    }

    @Test
    public void testSubmitMetric() {
        when(namespaceService.isPermitted(any(), any())).thenReturn(true);
        int metricCount = 60;
        List<Metric> metrics = new ArrayList<>(metricCount);
        for (int i = 0; i < metricCount; i++) {
            metrics.add(createMetricWithIncreasingResolution(1000, 2));
        }
        collectionService.submitMetrics(user, metrics);
        verify(monitorService).modifyCounter(MonitorService.Counter.DATAPOINT_WRITES, metricCount * 2, null);
        verify(wardenService).modifyPolicyCounter(user, WardenService.PolicyCounter.METRICS_PER_HOUR, metricCount);
        verify(wardenService).modifyPolicyCounter(user, WardenService.PolicyCounter.DATAPOINTS_PER_HOUR, metricCount * 2);
    }

    @Test
    public void testSubmitAnnotation() {
        Annotation annotation = createAnnotation();
        collectionService.submitAnnotation(user, annotation);
        verify(monitorService).modifyCounter(MonitorService.Counter.ANNOTATION_WRITES, 1, null);
    }
    
    @Test
    public void testSubmitHistogram() {
        Histogram histogram = createHistogram(3);
        collectionService.submitHistogram(user, histogram);
        verify(monitorService).modifyCounter(MonitorService.Counter.HISTOGRAM_WRITES, 1, null);
    }

    @Test
    public void testSubmitHistogramBucketsExceeded() {
        Histogram histogram = createHistogram(101);
        collectionService.submitHistogram(user, histogram);
        verify(monitorService).modifyCounter(MonitorService.Counter.HISTOGRAM_DROPPED, 1, null);
    }
    
    @Test
    public void testSubmitHistogramBucketsEmpty() {
        Histogram histogram = createHistogram(0);
        collectionService.submitHistogram(user, histogram);
        verify(monitorService).modifyCounter(MonitorService.Counter.HISTOGRAM_DROPPED, 1, null);
    }
    
    @Test
    public void testSubmitHistogramBucketsWrongBounds() {
        Histogram histogram = createHistogramWrongBounds(2);
        collectionService.submitHistogram(user, histogram);
        verify(monitorService).modifyCounter(MonitorService.Counter.HISTOGRAM_DROPPED, 1, null);
    }
    
    @Test
    public void testCommitMetrics() {
        List<Serializable> messages = Arrays.asList(
                new ArrayList<>(Arrays.asList(createMetric())),
                new ArrayList<>(Arrays.asList(createMetric()))
        );
        when(mqService.dequeue(eq(MQService.MQQueue.METRIC.getQueueName()), any(CollectionType.class), anyInt(), anyInt())).thenReturn(messages);
        assertEquals(2, collectionService.commitMetrics(2, 60000).size());
    }

    @Test
    public void testCommitMetricSchema() {
        List<Serializable> messages = Arrays.asList(
                new ArrayList<>(Arrays.asList(createMetric())),
                new ArrayList<>(Arrays.asList(createMetric()))
        );
        when(mqService.dequeue(eq(MQService.MQQueue.METRIC.getQueueName()), any(CollectionType.class), anyInt(), anyInt())).thenReturn(messages);
        assertEquals(2, collectionService.commitMetricSchema(2, 60000));
    }

    @Test
    public void testCommitAnnotations() {
        List<Annotation> messages = Arrays.asList(createAnnotation(), createAnnotation());
        when(mqService.dequeue(eq(MQService.MQQueue.ANNOTATION.getQueueName()), eq(Annotation.class), anyInt(), anyInt())).thenReturn(messages);
        assertEquals(2, collectionService.commitAnnotations(2, 60000));
    }
    
    @Test
    public void testCommitHistograms() {
        List<Histogram> messages = Arrays.asList(createHistogram(4), createHistogram(5));
        when(mqService.dequeue(eq(MQService.MQQueue.HISTOGRAM.getQueueName()), eq(Histogram.class), anyInt(), anyInt())).thenReturn(messages);
        assertEquals(2, collectionService.commitHistograms(2, 60000));
    }

    @Test
    public void submitAnnotations_testAnnotationSizeLessThanMax() {
        Annotation a = createAnnotation();

        // test
        collectionService.submitAnnotations(user, ImmutableList.of(a));

        // verify
        verify(monitorService).modifyCounter(Counter.ANNOTATION_WRITES, 1, null);
        ArgumentCaptor<List> annotationListCaptor = ArgumentCaptor.forClass(List.class);
        verify(mqService).enqueue(eq(ANNOTATION.getQueueName()), annotationListCaptor.capture());
        assertEquals(1, annotationListCaptor.getValue().size());
        assertTrue(annotationListCaptor.getValue().contains(a));
    }

    @Test
    public void submitAnnotations_testListContainingOneAnnotationSizeGreaterThanMax() {
        Annotation a = createAnnotation();
        Annotation tooLargeAnnotation = createAnnotationWithSizeTooLarge();

        // test
        collectionService.submitAnnotations(user, ImmutableList.of(a, tooLargeAnnotation));

        // verify
        verify(monitorService).modifyCounter(Counter.ANNOTATION_DROPS_MAXSIZEEXCEEDED, 1, ImmutableMap.of("source", tooLargeAnnotation.getSource()));
        verify(monitorService).modifyCounter(Counter.ANNOTATION_WRITES, 1, null);
        ArgumentCaptor<List> annotationListCaptor = ArgumentCaptor.forClass(List.class);
        verify(mqService).enqueue(eq(ANNOTATION.getQueueName()), annotationListCaptor.capture());
        assertEquals(1, annotationListCaptor.getValue().size());
    }

    @Test
    public void updateAnnotations_testOnlyOneAnnotationSizeGreaterThanMax() {
        Annotation tooLargeAnnotation = createAnnotationWithSizeTooLarge();

        // test
        collectionService.submitAnnotations(user, ImmutableList.of(tooLargeAnnotation));

        // verify
        verify(monitorService).modifyCounter(Counter.ANNOTATION_DROPS_MAXSIZEEXCEEDED, 1, ImmutableMap.of("source", tooLargeAnnotation.getSource()));
        verify(monitorService).modifyCounter(Counter.ANNOTATION_WRITES, 0, null);
        verify(mqService, never()).enqueue(any(), (List)any());
    }

    private Annotation createAnnotationWithSizeTooLarge() {
        Annotation tooLargeAnnotation = new Annotation("source2",
                "id2",
                "type2",
                "scope2",
                "metric2",
                System.currentTimeMillis());
        // set up annotation with size larger than max size allowed
        final int TAG_SIZE = 100;
        final int NUM_TAGS = DefaultCollectionService.MAX_ANNOTATION_SIZE_BYTES / TAG_SIZE / 2;
        for (int i = 0; i < NUM_TAGS; i++) {
            tooLargeAnnotation.setTag(RandomStringUtils.random(TAG_SIZE), RandomStringUtils.random(TAG_SIZE));
        }
        final Map<String, String> fields = new HashMap<>();
        for (int i = 0; i < 10; i++) {
            fields.put(RandomStringUtils.random(10), RandomStringUtils.random(10));
        }
        tooLargeAnnotation.setFields(fields);
        return tooLargeAnnotation;
    }
}

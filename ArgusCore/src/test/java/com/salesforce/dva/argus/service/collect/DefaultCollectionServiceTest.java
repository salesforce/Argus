package com.salesforce.dva.argus.service.collect;

import com.fasterxml.jackson.databind.type.CollectionType;
import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.MQService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.NamespaceService;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.service.WardenService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
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
}

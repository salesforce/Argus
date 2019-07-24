package com.salesforce.dva.argus.service.metric.metadata;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;

public class IDBMetadataServiceTest {
    @Test
    public void testMetadataEqualsErrors() {
        IDBClient client = mock(IDBClient.class);
        doReturn(new HashMap<>()).when(client).get(any());
        MetadataService service = new IDBMetadataService(new SystemConfiguration(new Properties()), client, mock(MonitorService.class));
        List<Metric> metrics = Arrays.asList(new Metric("system.dc1.sp.pod", "metricName"));
        Function<Metric, String> f = m -> "";

        service.isMetadataEquals(metrics, "idb.host.operationalStatus", "prod", true, f);
        service.isMetadataEquals(metrics, "idb.cluster.dr", "prod", true, f);
        try {
            service.isMetadataEquals(metrics, "idb.host.environment", "prod", true, f);
            fail("Previous line should have thrown an Exception");
        } catch (IllegalArgumentException ex) {}
        try {
            service.isMetadataEquals(metrics, "idb.host.", "prod", true, f);
            fail("Previous line should have thrown an Exception");
        } catch (IllegalArgumentException ex) {}
        try {
            service.isMetadataEquals(metrics, "idb.cluster.badkey", "prod", true, f);
            fail("Previous line should have thrown an Exception");
        } catch (IllegalArgumentException ex) {}
        try {
            service.isMetadataEquals(metrics, "totallyInvalidKey", "", true, f);
            fail("Previous line should have thrown an Exception");
        } catch (SystemException ex) {}
    }

    @Test
    public void testMetadataEqualsSuccess() {
        IDBClient client = mock(IDBClient.class);
        doAnswer(invocationOnMock -> {
            Collection<IDBFieldQuery> queries = invocationOnMock.getArgument(0, Collection.class);
            Map<IDBFieldQuery, Optional<String>> results = new HashMap<>();
            queries.forEach(query -> {
                if (query.getResourceName().toLowerCase().startsWith("activeresource")) {
                    results.put(query, Optional.of("ACTIVE"));
                } else {
                    results.put(query, Optional.of("INACTIVE"));
                }
            });
            return results;
        }).when(client).get(any());
        MetadataService service = new IDBMetadataService(new SystemConfiguration(new Properties()), client, mock(MonitorService.class));
        Metric activeMetric = new Metric("system.dc1.sp.pod", "activeResource");
        Metric inactiveMetric = new Metric("system.dc2", "inactiveResource");
        List<Metric> metrics = Arrays.asList(activeMetric, inactiveMetric);
        Function<Metric, String> f = Metric::getMetric;

        Map<Metric, Boolean> equalities = service.isMetadataEquals(metrics, "idb.host.operationalStatus", "ACTIVE", true, f);
        assertTrue(equalities.get(activeMetric));
        assertFalse(equalities.get(inactiveMetric));
        equalities = service.isMetadataEquals(metrics, "idb.host.operationalStatus", "CHAOS", true, f);
        assertFalse(equalities.get(activeMetric));
        assertFalse(equalities.get(inactiveMetric));
    }

    @Test
    public void testDefaultBoolean() {
        IDBClient client = mock(IDBClient.class);
        doAnswer(invocationOnMock -> {
            Collection<IDBFieldQuery> queries = invocationOnMock.getArgument(0, Collection.class);
            Map<IDBFieldQuery, Optional<String>> results = new HashMap<>();
            queries.forEach(query -> results.put(query, Optional.empty()));
            return results;
        }).when(client).get(any());
        MetadataService service = new IDBMetadataService(new SystemConfiguration(new Properties()), client, mock(MonitorService.class));
        List<Metric> metrics = Arrays.asList(new Metric("system.dc1.sp.pod", "metricName"), new Metric("system.dc2", "metricName2"));
        Function<Metric, String> f = m -> "";

        Map<Metric, Boolean> equalities = service.isMetadataEquals(metrics, "idb.host.operationalStatus", "prod", true, f);
        assertTrue(!equalities.values().contains(false));
        equalities = service.isMetadataEquals(metrics, "idb.host.operationalStatus", "prod", false, f);
        assertTrue(!equalities.values().contains(true));
    }

    @Test
    public void testExtractDatacenter() {
        IDBClient client = mock(IDBClient.class);
        IDBMetadataService service = new IDBMetadataService(new SystemConfiguration(new Properties()), client, mock(MonitorService.class));
        Metric metric = new Metric("system.", "test-metric");
        try {
            service.extractDatacenter(metric);
            fail("Previous line should have thrown an Exception");
        } catch (SystemException ex) {}
        try {
            metric.setScope("system.toolong.sp.pod");
            service.extractDatacenter(metric);
            fail("Previous line should have thrown an Exception");
        } catch (SystemException ex) {}
        try {
            metric.setScope("system.extra.stuff.toolong.sp.pod");
            service.extractDatacenter(metric);
            fail("Previous line should have thrown an Exception");
        } catch (SystemException ex) {}

        metric.setScope("system.dc1");
        assertEquals("dc1", service.extractDatacenter(metric));
        metric.setScope("system.dc1.sp.pod");
        assertEquals("dc1", service.extractDatacenter(metric));
        metric.setScope("system.extra.stuff.dc1.sp.pod");
        assertEquals("dc1", service.extractDatacenter(metric));
        metric.setScope("system.dc1.sp.pod.extra.stuff");
        assertEquals("dc1", service.extractDatacenter(metric));
    }
}

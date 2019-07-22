package com.salesforce.dva.argus.service.metric.transform;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.metric.metadata.MetadataService;
import com.salesforce.dva.argus.system.SystemException;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;

public class MetadataIncludeTest {
    @Test
    public void testArgumentCount() {
        Transform t = new MetadataInclude(Mockito.mock(MetadataService.class));
        List<Metric> metrics = Arrays.asList(new Metric("test-scope", "test-metric"));

        try {
            t.transform(null, metrics, Arrays.asList("host.status", "active"));
            fail("Previous line should have thrown an Exception");
        } catch (IllegalArgumentException ex) {}
        try {
            t.transform(null, metrics, Arrays.asList("host.status", "active", MetadataInclude.HOST_FROM_TAG_MODE));
            fail("Previous line should have thrown an Exception");
        } catch (IllegalArgumentException ex) {}
        try {
            t.transform(null, metrics, Arrays.asList("host.status", "active", MetadataInclude.IDENTIFIER_EXTRACT_MODE));
            fail("Previous line should have thrown an Exception");
        } catch (IllegalArgumentException ex) {}
    }

    @Test
    public void testUnsupportedShouldFail() {
        Transform t = new MetadataInclude(Mockito.mock(MetadataService.class));
        try {
            t.transform(null, Collections.emptyList());
            fail("Previous line should have thrown an Exception");
        } catch (UnsupportedOperationException ex) { }
        try {
            t.transform(null, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
            fail("Previous line should have thrown an Exception");
        } catch (UnsupportedOperationException ex) { }
    }

    @Test
    public void testIdentifierModeSuccess() {
        MetadataService metadataService = Mockito.mock(MetadataService.class);
        doAnswer(invocationOnMock -> {
            List<Metric> metrics = invocationOnMock.getArgument(0, List.class);
            Function<Metric, String> extractor = invocationOnMock.getArgument(4, Function.class);

            Map<Metric, Boolean> results = new HashMap<>();
            metrics.forEach(metric -> results.put(metric, extractor.apply(metric).startsWith("argus")));
            return results;
        }).when(metadataService).isMetadataEquals(any(), any(), any(), anyBoolean(), any());
        Transform t = new MetadataInclude(metadataService);

        Metric matchingMetric = new Metric("test-scope", "test-metric");
        matchingMetric.setTag("device", "argushost1.data.net");

        Metric notMatching1 = new Metric(matchingMetric);
        notMatching1.setTag("device", "arrrr.data.net");
        Metric notMatching2 = new Metric(matchingMetric);
        notMatching2.removeTag("device");
        notMatching2.setTag("device", "argon.data.net");
        List<String> constants = Arrays.asList(
                "idb.host.operationalStatus",
                "ACTIVE",
                MetadataInclude.IDENTIFIER_EXTRACT_MODE,
                "device=(ar.*\\.net)");
        List<Metric> results = t.transform(null, Arrays.asList(matchingMetric, notMatching1, notMatching2), constants);
        assertEquals(results.size(), 1);
        assertEquals(results.get(0), matchingMetric);
    }

    @Test
    public void testIdentifierModeErrors() {
        Transform t = new MetadataInclude(Mockito.mock(MetadataService.class));
        List<Metric> metrics = Arrays.asList(new Metric("test-scope", "test-metric"));
        List<String> constants = Arrays.asList("idb.host.operationalStatus", "ACTIVE", "identifier", "noparenregex.*");
        try {
            t.transform(null, metrics, constants);
            fail("Previous line should have thrown an Exception");
        } catch (IllegalArgumentException ex) {}
        constants.set(3, "device=(argus.*\\.net");
        try {
            t.transform(null, metrics, constants);
            fail("Previous line should have thrown an Exception");
        } catch (IllegalArgumentException ex) {}
        constants.set(3, "device=argus.*\\.net)");
        try {
            t.transform(null, metrics, constants);
            fail("Previous line should have thrown an Exception");
        } catch (IllegalArgumentException ex) {}
        constants.set(3, "device=)argus.*\\.net(");
        try {
            t.transform(null, metrics, constants);
            fail("Previous line should have thrown an Exception");
        } catch (IllegalArgumentException ex) {}
    }

    @Test
    public void testNameModeSuccess() {
        MetadataService metadataService = Mockito.mock(MetadataService.class);
        Transform t = new MetadataInclude(metadataService);
        doAnswer(invocationOnMock -> {
            List<Metric> metrics = invocationOnMock.getArgument(0, List.class);
            Function<Metric, String> extractor = invocationOnMock.getArgument(4, Function.class);

            Map<Metric, Boolean> results = new HashMap<>();
            metrics.forEach(metric -> results.put(metric, extractor.apply(metric).startsWith("include")));
            return results;
        }).when(metadataService).isMetadataEquals(any(), any(), any(), anyBoolean(), any());

        String matchingNames = "include1,include2";
        String notMatching1 = "include1,excludeme";
        String notMatching2 = "excludeme";
        List<Metric> metrics = Arrays.asList(new Metric("test-scope", "test-metric"));
        List<String> constants = Arrays.asList(
                "idb.host.operationalStatus",
                "ACTIVE",
                MetadataInclude.NAME_CSV_MODE,
                matchingNames);

        List<Metric> results = t.transform(null, metrics, constants);
        assertEquals(results.size(), 1);
        assertEquals(results.get(0), metrics.get(0));

        constants.set(3, notMatching1);
        results = t.transform(null, metrics, constants);
        assertEquals(results.size(), 0);

        constants.set(3, notMatching2);
        results = t.transform(null, metrics, constants);
        assertEquals(results.size(), 0);
    }

    @Test
    public void testDeviceModeSuccess() {
        MetadataService metadataService = Mockito.mock(MetadataService.class);
        doAnswer(invocationOnMock -> {
            List<Metric> metrics = invocationOnMock.getArgument(0, List.class);
            Function<Metric, String> extractor = invocationOnMock.getArgument(4, Function.class);

            Map<Metric, Boolean> results = new HashMap<>();
            metrics.forEach(metric -> results.put(metric, extractor.apply(metric).startsWith("argus")));
            return results;
        }).when(metadataService).isMetadataEquals(any(), any(), any(), anyBoolean(), any());
        Transform t = new MetadataInclude(metadataService);

        Metric matchingMetric = new Metric("test-scope", "test-metric");
        matchingMetric.setTag("device", "argushost1.data.net");
        Metric notMatching = new Metric(matchingMetric);
        notMatching.setTag("device", "notArgus.data.net");

        List<String> constants = Arrays.asList(
                "idb.host.operationalStatus",
                "ACTIVE",
                MetadataInclude.HOST_FROM_TAG_MODE,
                "device");
        List<Metric> results = t.transform(null, Arrays.asList(matchingMetric, notMatching), constants);
        assertEquals(results.size(), 1);
        assertEquals(results.get(0), matchingMetric);
    }

    @Test
    public void testDeviceModeErrors() {
        Metric metricWithoutDevice = new Metric("test-scope", "test-metric");
        metricWithoutDevice.setTag("someTag", "someTagVal");

        Transform t = new MetadataInclude(Mockito.mock(MetadataService.class));

        List<String> constants = Arrays.asList("idb.host.operationalStatus", "ACTIVE", MetadataInclude.HOST_FROM_TAG_MODE, "device");
        try {
            t.transform(null, Arrays.asList(metricWithoutDevice), constants);
            fail("Previous line should have thrown an Exception");
        } catch (SystemException ex) {}
    }

    @Test
    public void testScopeModeSuccess() {
        MetadataService metadataService = Mockito.mock(MetadataService.class);
        doAnswer(invocationOnMock -> {
            List<Metric> metrics = invocationOnMock.getArgument(0, List.class);
            Function<Metric, String> extractor = invocationOnMock.getArgument(4, Function.class);

            Map<Metric, Boolean> results = new HashMap<>();
            metrics.forEach(metric -> results.put(metric, extractor.apply(metric).startsWith("argus")));
            return results;
        }).when(metadataService).isMetadataEquals(any(), any(), any(), anyBoolean(), any());
        Transform t = new MetadataInclude(metadataService);

        Metric matchingMetric = new Metric("system.dc.superpod.argus", "test-metric");
        Metric notMatching = new Metric("system.dc.superpod.notArgus", "test-metric");

        List<String> constants = Arrays.asList(
                "idb.cluster.operationalStatus",
                "ACTIVE",
                MetadataInclude.CLUSTER_FROM_SCOPE_MODE);
        List<Metric> results = t.transform(null, Arrays.asList(matchingMetric, notMatching), constants);
        assertEquals(results.size(), 1);
        assertEquals(results.get(0), matchingMetric);

        // Should also expect pod as last token if there are extra tokens in the front
        matchingMetric.setScope("system.foo.bar.dc.superpod.argus");
        results = t.transform(null, Arrays.asList(matchingMetric), constants);
        assertEquals(results.size(), 1);
        assertEquals(results.get(0), matchingMetric);
    }

    @Test
    public void testScopeModeErrors() {
        Metric metricBadScope = new Metric("test-scope", "test-metric");

        Transform t = new MetadataInclude(Mockito.mock(MetadataService.class));

        List<String> constants = Arrays.asList("idb.cluster.operationalStatus", "ACTIVE", MetadataInclude.CLUSTER_FROM_SCOPE_MODE);
        try {
            t.transform(null, Arrays.asList(metricBadScope), constants);
            fail("Previous line should have thrown an Exception");
        } catch (SystemException ex) {}
    }

    @Test
    public void testAllModesShouldHaveEmptyResultWhenMetadataFalse() {
        MetadataService metadataService = Mockito.mock(MetadataService.class);
        doAnswer(invocationOnMock -> {
            List<Metric> metrics = invocationOnMock.getArgument(0, List.class);
            Map<Metric, Boolean> results = new HashMap<>();
            metrics.forEach(metric -> {
                results.put(metric, false);
            });
            return results;
        }).when(metadataService).isMetadataEquals(any(), any(), any(), anyBoolean(), any());
        Transform t = new MetadataInclude(metadataService);
        Metric metric = new Metric("system.dc.sp.pod", "test-metric");
        metric.setTag("device", "aDevice");
        List<Metric> metrics = Arrays.asList(metric);

        List<String> constants = Arrays.asList("idb.host.operationalStatus", "ACTIVE", MetadataInclude.IDENTIFIER_EXTRACT_MODE, "(.*)");
        assertEquals(t.transform(null, metrics, constants).size(), 0);

        constants = Arrays.asList("idb.host.operationalStatus", "ACTIVE", MetadataInclude.NAME_CSV_MODE, "name");
        assertEquals(t.transform(null, metrics, constants).size(), 0);

        constants = Arrays.asList("idb.host.operationalStatus", "ACTIVE", MetadataInclude.HOST_FROM_TAG_MODE, "device");
        assertEquals(t.transform(null, metrics, constants).size(), 0);

        constants = Arrays.asList("idb.cluster.operationalStatus", "ACTIVE", MetadataInclude.CLUSTER_FROM_SCOPE_MODE);
        assertEquals(t.transform(null, metrics, constants).size(), 0);
    }
}

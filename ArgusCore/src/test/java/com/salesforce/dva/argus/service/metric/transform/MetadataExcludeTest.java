package com.salesforce.dva.argus.service.metric.transform;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.metric.metadata.MetadataService;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doAnswer;

public class MetadataExcludeTest {
    @Test
    public void testExclusion() {
        MetadataService metadataService = Mockito.mock(MetadataService.class);
        doAnswer(invocationOnMock -> {
            List<Metric> metrics = invocationOnMock.getArgument(0, List.class);
            Function<Metric, String> extractor = invocationOnMock.getArgument(4, Function.class);

            Map<Metric, Boolean> results = new HashMap<>();
            metrics.forEach(metric -> results.put(metric, extractor.apply(metric).startsWith("argus")));
            return results;
        }).when(metadataService).isMetadataEquals(any(), any(), any(), anyBoolean(), any());
        Transform t = new MetadataExclude(metadataService);

        Metric matchingMetric = new Metric("test-scope", "test-metric");
        matchingMetric.setTag("device", "argushost1.data.net");
        Metric notMatching = new Metric(matchingMetric);
        notMatching.setTag("device", "notArgus.data.net");

        List<String> constants = Arrays.asList(
                "idb.host.operationalStatus",
                "ACTIVE",
                MetadataInclude.HOST_FROM_TAG_MODE,
                "device");
        List<Metric> results = t.transform(null, new ArrayList<>(Arrays.asList(matchingMetric, notMatching)), constants);
        assertEquals(results.size(), 1);
        assertEquals(results.get(0), notMatching);
    }
}

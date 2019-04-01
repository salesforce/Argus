package com.salesforce.dva.argus.service.metric;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Provider;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.TSDBEntity;
import com.salesforce.dva.argus.service.DiscoveryService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.metric.transform.TransformFactory;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

import org.junit.BeforeClass;
import org.junit.AfterClass;
import com.salesforce.dva.argus.system.SystemMain;
import com.salesforce.dva.argus.TestUtils;

public class MetricServiceSnapshotTest {
    final String RELATIVE_TO_TIMESTAMP_KEY = "RELATIVE_TO_TIMESTAMP_KEY";
    ObjectMapper mapper = new ObjectMapper();
    DefaultMetricService metricService;
    DiscoveryService discoveryService;
    TSDBService tsdbService;

    static private SystemMain system;

    @BeforeClass
    static public void setUpClass() {
        system = TestUtils.getInstance();
        system.start();
    }

    @AfterClass
    static public void tearDownClass() {
        if (system != null) {
            system.getServiceFactory().getManagementService().cleanupRecords();
            system.stop();
        }
    }

    @Before
    public void setUp() {
        discoveryService = spy(system.getServiceFactory().getDiscoveryService());
        tsdbService = spy(system.getServiceFactory().getTSDBService());
        MetricQueryProcessor processor = new MetricQueryProcessor(
                tsdbService,
                discoveryService,
                system.getServiceFactory().getMonitorService(),
                new TransformFactory(tsdbService)
        );
        Provider metricsProvider = (Provider<MetricReader<Metric>>) () -> new MetricReader<>(tsdbService, discoveryService, new TransformFactory(tsdbService));
        Provider queryProvider = (Provider<MetricReader<MetricQuery>>) () -> new MetricReader<>(tsdbService, discoveryService, new TransformFactory(tsdbService));
        metricService = new DefaultMetricService(
                system.getServiceFactory().getMonitorService(),
                processor,
                metricsProvider,
                queryProvider,
                system.getConfiguration()
        );
        try {
            byte[] discoveryData = Files.readAllBytes(Paths.get(getClass().getResource("MetricServiceSnapshotTest.discovery.json").getPath()));
            byte[] tsdbData = Files.readAllBytes(Paths.get(getClass().getResource("MetricServiceSnapshotTest.tsdb.json").getPath()));
            List<List<JsonNode>> rawDiscoveryJson = mapper.readValue(discoveryData, new TypeReference<List<List<JsonNode>>>() {});
            List<List<JsonNode>> rawTsdbJson = mapper.readValue(tsdbData, new TypeReference<List<List<JsonNode>>>() {});

            Map<MetricQuery, List<MetricQuery>> discoveryMocks = new LinkedHashMap<>() ;
            Map<MetricQuery, List<Metric>> tsdbMocks = new LinkedHashMap<>();

            for (List<JsonNode> entry: rawDiscoveryJson) {
                MetricQuery query = mapper.treeToValue(entry.get(0), MetricQuery.class);
                List<MetricQuery> matchingQueries = mapper.readValue(entry.get(1).toString(), new TypeReference<List<MetricQuery>>() {});
                discoveryMocks.put(query, matchingQueries);
            }
            for (List<JsonNode> entry: rawTsdbJson) {
                MetricQuery query = mapper.treeToValue(entry.get(0), MetricQuery.class);
                List<Metric> results = mapper.readValue(entry.get(1).toString(), new TypeReference<List<Metric>>() {});
                tsdbMocks.put(query, results);
            }

            doAnswer((Answer<List<MetricQuery>>) invocationOnMock -> {
                MetricQuery query = invocationOnMock.getArgumentAt(0, MetricQuery.class);
                return discoveryMocks.get(query);
            }).when(discoveryService).getMatchingQueries(any());

            doAnswer((Answer<Map<MetricQuery, List<Metric>>>) invocationOnMock -> {
                List<MetricQuery> queries = invocationOnMock.getArgumentAt(0, List.class);
                Map<MetricQuery, List<Metric>> results = new LinkedHashMap<>();
                for (MetricQuery query: queries) {
                    results.put(query, tsdbMocks.get(query));
                }
                return results;
            }).when(tsdbService).getMetrics(any());
        } catch (IOException ex) {
            ex.printStackTrace();
            fail();
        }
    }

    @Test
    public void testQueriesFromFile() throws IOException {
        Map<String, List<Metric>> exprToResult = mapper.readValue(new File(getClass().getResource("MetricServiceSnapshotTest.results.json").getFile()), new TypeReference<Map<String, List<Metric>>>() {});
        // Extract the special metric that stores the relativeTo timestamp used in snapshot generation
        if (!exprToResult.containsKey(RELATIVE_TO_TIMESTAMP_KEY)) {
            fail("Expecting RELATIVE_TO_TIMESTAMP_KEY JSON key which holds the relativeTo timestamp, inside file MetricServiceSnapshotTest.results.json");
        }
        Metric magicMetric = exprToResult.remove(RELATIVE_TO_TIMESTAMP_KEY).get(0);
        long relativeTo = Long.valueOf(magicMetric.getScope());
        exprToResult.forEach((expression, expectedResult) -> {
            List<Metric> actualResult = metricService.getMetrics(expression, relativeTo).getMetricsList();
            expectedResult.sort(Comparator.comparing(TSDBEntity::getScope).thenComparing(TSDBEntity::getMetric));
            actualResult.sort(Comparator.comparing(TSDBEntity::getScope).thenComparing(TSDBEntity::getMetric));
            assertEquals(expectedResult, actualResult);
        });
    }
}

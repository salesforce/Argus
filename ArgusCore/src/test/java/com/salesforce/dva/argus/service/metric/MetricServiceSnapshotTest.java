package com.salesforce.dva.argus.service.metric;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Provider;
import com.salesforce.dva.argus.TestUtils;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.DiscoveryService;
import com.salesforce.dva.argus.service.QueryStoreService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.metric.transform.TransformFactory;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.system.SystemMain;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.stubbing.Answer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;

public class MetricServiceSnapshotTest {
    final String RELATIVE_TO_TIMESTAMP_KEY = "RELATIVE_TO_TIMESTAMP_KEY";
    ObjectMapper mapper = new ObjectMapper();
    DefaultMetricService metricService;
    DiscoveryService discoveryService;
    TSDBService tsdbService;
    QueryStoreService queryStoreService;

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
        queryStoreService =spy(system.getServiceFactory().getQueryStoreService());
        MetricQueryProcessor processor = new MetricQueryProcessor(
                tsdbService,
                discoveryService,
                system.getServiceFactory().getMonitorService(),
                new TransformFactory(tsdbService, null), queryStoreService
        );
        Provider metricsProvider = (Provider<MetricReader<Metric>>) () -> new MetricReader<>(tsdbService, discoveryService, new TransformFactory(tsdbService, null));
        Provider queryProvider = (Provider<MetricReader<MetricQuery>>) () -> new MetricReader<>(tsdbService, discoveryService, new TransformFactory(tsdbService, null));
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
                MetricQuery query = invocationOnMock.getArgument(0, MetricQuery.class);
                List<MetricQuery> results = discoveryMocks.get(query);
                // Return deep clone
                return results.stream().map(resultQuery -> new MetricQuery(resultQuery)).collect(Collectors.toList());
            }).when(discoveryService).getMatchingQueries(any());

            doAnswer((Answer<Map<MetricQuery, List<Metric>>>) invocationOnMock -> {
                List<MetricQuery> queries = invocationOnMock.getArgument(0, List.class);
                Map<MetricQuery, List<Metric>> results = new LinkedHashMap<>();
                for (MetricQuery query: queries) {
                    // Put deep clone of this query-result pair
                    List<Metric> resultMetrics = tsdbMocks.get(query);
                    List<Metric> resultClone =  resultMetrics.stream().map(resultMetric -> new Metric(resultMetric)).collect(Collectors.toList());
                    results.put(new MetricQuery(query), resultClone);
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
        exprToResult.forEach((expression, expectedMetrics) -> {
            Collections.sort(expectedMetrics);
            List<Metric> actualMetrics = metricService.getMetrics(expression, relativeTo).getMetricsList();
            Collections.sort(actualMetrics);
            List<Map<Long, Double>> expectedDatapoints = expectedMetrics.stream().map(m -> m.getDatapoints()).collect(Collectors.toList());
            List<Map<Long, Double>> actualDatapoints = actualMetrics.stream().map(m -> m.getDatapoints()).collect(Collectors.toList());
            assertEquals("Expression should return the same scope,metrics,tags when ES and TSDB are mocked " + expression, expectedMetrics, actualMetrics);
            assertEquals("Expression should return the same datapoints when ES and TSDB are mocked " + expression, expectedDatapoints, actualDatapoints);
        });
    }
}

package com.salesforce.dva.argus.querysnapshots;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.google.inject.Provider;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.DiscoveryService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.metric.DefaultMetricService;
import com.salesforce.dva.argus.service.metric.MetricQueryProcessor;
import com.salesforce.dva.argus.service.metric.MetricQueryResult;
import com.salesforce.dva.argus.service.metric.MetricReader;
import com.salesforce.dva.argus.service.metric.transform.TransformFactory;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.system.SystemMain;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class Main {
    final String TEST_NAME = "MetricServiceSnapshotTest";
    final String RELATIVE_TO_TIMESTAMP_KEY = "RELATIVE_TO_TIMESTAMP_KEY";
    final String outputPath;
    SystemMain system;
    MetricService metricService;

    /**
     * This gets serialized to:
     * [
     *   [
     *     {query},
     *     [ {matchingQuery}, {matchingQuery}, ...]
     *   ],
     *   [ ... ]
     * ]
     */
    Map<MetricQuery, List<MetricQuery>> discoveryLog = new LinkedHashMap<>() ;
    /**
     * This gets serialized to:
     * [
     *   [
     *     {query},
     *     [ {Metric scope: , metric: , datapoints: , ...}, {Metric}, ...]
     *   ],
     *   [ ... ]
     * ]
     */
    Map<MetricQuery, List<Metric>> tsdbLog = new LinkedHashMap<>();
    /**
     * This gets serialized to:
     * {
     *     "<expression>": [ {Metric}, {Metric} ... ]
     * }
     */
    Map<String, List<Metric>> exprToFinalResult = new LinkedHashMap<>();
    ObjectMapper mapper = new ObjectMapper();

    public Main() {
        mapper.configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
        // Expecting this to be /path/to/Argus
        outputPath = System.getProperty("user.dir") + "/../ArgusCore/src/test/resources/com/salesforce/dva/argus/service/metric";
    }

    public void run() {
        initServices();
        recordSnapshots();
        writeDiscoverySnapshots();
        writeTSDBSnapshots();
        writeResults();
        system.stop();
        System.out.println("Done");
    }

    public void initServices() {
        system = getInstance();
        system.start();

        DiscoveryService discoveryService = new LoggingDiscoveryService(
                system.getServiceFactory().getSchemaService(),
                system.getConfiguration(),
                system.getServiceFactory().getMonitorService(),
                discoveryLog);
        LoggingTSDBService tsdbService = new LoggingTSDBService(
                system.getConfiguration(),
                system.getServiceFactory().getMonitorService(),
                tsdbLog);
        MetricQueryProcessor processor = new MetricQueryProcessor(
                tsdbService,
                discoveryService,
                system.getServiceFactory().getMonitorService(),
                new TransformFactory(tsdbService),
                system.getServiceFactory().getQueryStoreService());

        Provider metricsProvider = (Provider<MetricReader<Metric>>) () -> new MetricReader<>(tsdbService, discoveryService, new TransformFactory(tsdbService));
        Provider queryProvider = (Provider<MetricReader<MetricQuery>>) () -> new MetricReader<>(tsdbService, discoveryService, new TransformFactory(tsdbService));
        metricService = new DefaultMetricService(
                system.getServiceFactory().getMonitorService(),
                processor,
                metricsProvider,
                queryProvider,
                system.getConfiguration());
    }

    public void recordSnapshots() {
        File file = new File(System.getProperty("inputPath", "./queries.txt"));
        long relative = System.currentTimeMillis();
        // Put a special metric that stores the relativeTo timestamp
        Metric magicMetric = new Metric(String.valueOf(relative), "metric");
        exprToFinalResult.put(RELATIVE_TO_TIMESTAMP_KEY, Arrays.asList(magicMetric));

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String expression;
            while ((expression = br.readLine()) != null) {
                System.out.println("[ \u001b[34mPROCESSING\u001b[0m ] " + expression);
                try {
                    long start = System.currentTimeMillis();
                    MetricQueryResult result = metricService.getMetrics(expression, relative);
                    long time = System.currentTimeMillis() - start;
                    if (result.getMetricsList().stream().allMatch(metric -> metric.getDatapoints().isEmpty())) {
                        System.out.println("[ \u001b[33mSKIPPING\u001b[0m ] All metric results of the above expression had no datapoints. Skipping it");
                    } else {
                        exprToFinalResult.put(expression, result.getMetricsList());
                        System.out.println("[ \u001b[32mSUCCESS\u001b[0m ] Snapshot successfully recorded in " + time + "ms of the above expression");
                    }
                } catch (Exception ex) {
                    System.err.println("[ \u001b[31mERROR\u001b[0m ] Received error within getMetrics call, skipping this expression:");
                    ex.printStackTrace();
                    continue;
                }
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void writeDiscoverySnapshots() {
        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("[");

        for (Map.Entry<MetricQuery, List<MetricQuery>> entry: discoveryLog.entrySet()) {
            try {
                String query = mapper.writeValueAsString(entry.getKey());
                String matchingQueries = mapper.writeValueAsString(entry.getValue());
                logBuilder.append("[").append(query).append(",").append(matchingQueries).append("],");
            } catch (JsonProcessingException ex) {
                System.err.println("Failed to write to JSON log: " + entry);
                continue;
            }
        }
        // Remove last comma for valid JSON
        logBuilder.deleteCharAt(logBuilder.length() - 1);
        logBuilder.append("]");

        try {
            FileWriter fw = new FileWriter(outputPath + "/" + TEST_NAME + ".discovery.json", false);
            fw.write(logBuilder.toString());
            fw.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void writeTSDBSnapshots() {
        ObjectMapper mapper = new ObjectMapper();
        StringBuilder logBuilder = new StringBuilder();
        logBuilder.append("[");

        for (Map.Entry<MetricQuery, List<Metric>> entry: tsdbLog.entrySet()) {
            try {
                String query = mapper.writeValueAsString(entry.getKey());
                String resultMetrics = mapper.writeValueAsString(entry.getValue());
                logBuilder.append("[").append(query).append(",").append(resultMetrics).append("],");
            } catch (JsonProcessingException ex) {
                System.err.println("Failed to write to JSON log: " + entry);
                continue;
            }
        }
        // Remove last comma for valid JSON
        logBuilder.deleteCharAt(logBuilder.length() - 1);
        logBuilder.append("]");

        try {
            FileWriter fw = new FileWriter(outputPath + "/" + TEST_NAME + ".tsdb.json", false);
            fw.write(logBuilder.toString());
            fw.close();
        } catch (IOException ex) {
            ex.printStackTrace();
        }
    }

    public void writeResults() {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.writeValue(new File(outputPath + "/" + TEST_NAME + ".results.json"), exprToFinalResult);
        } catch (IOException ex) {
            ex.printStackTrace();
        }

    }

    public static SystemMain getInstance() {
        Properties config = new Properties();
        InputStream is = null;

        try {
            is = Main.class.getResourceAsStream("argus.properties");
            config.load(is);
        } catch (IOException ex) {
            ex.printStackTrace();
            System.exit(1);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ex) {
                    assert false : "This should never occur.";
                }
            }
        }
        return SystemMain.getInstance(config);
    }

    public static void main(String[] args) {
        new Main().run();
        System.exit(0);
    }
}

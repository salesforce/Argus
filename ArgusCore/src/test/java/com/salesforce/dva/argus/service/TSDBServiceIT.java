/*
 * Copyright (c) 2016, Salesforce.com, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
     
package com.salesforce.dva.argus.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.IntegrationTest;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.tsdb.AnnotationQuery;
import com.salesforce.dva.argus.service.tsdb.DefaultTSDBService;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.system.SystemException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
public class TSDBServiceIT extends AbstractTest {

    private static final long SLEEP_AFTER_PUT_IN_MILLIS = 2000;

    protected static MetricQuery toQuery(Metric metric) {
        TreeMap<Long, String> datapoints = new TreeMap<>(metric.getDatapoints());
        Long start = datapoints.firstKey();
        Long end = datapoints.lastKey();

        return new MetricQuery(metric.getScope(), metric.getMetric(), metric.getTags(), start, end);
    }

    protected List<Metric> createRandomMetrics(String scope, String metric, int count) {
        List<Metric> result = new ArrayList<>(count);

        scope = scope == null ? createRandomName() : scope;

        String tag = createRandomName();

        for (int i = 0; i < count; i++) {
            String metricName = metric == null ? createRandomName() : metric;
            Metric met = new Metric(scope, metricName);
            int datapointCount = random.nextInt(25) + 1;
            Map<Long, String> datapoints = new HashMap<>();
            long start = System.currentTimeMillis() - 60000L;

            for (int j = 0; j < datapointCount; j++) {
                datapoints.put(start - (j * 60000L), String.valueOf(random.nextInt(100) + 1));
            }
            met.setDatapoints(datapoints);
            met.setDisplayName(createRandomName());
            met.setUnits(createRandomName());
            met.setTag(tag, String.valueOf(i));
            result.add(met);
        }
        return result;
    }

    protected List<Annotation> createRandomAnnotations(String scope, String metric, int count) {
        List<Annotation> result = new ArrayList<>(count);

        scope = scope == null ? createRandomName() : scope;

        long start = System.currentTimeMillis() - 60000;

        for (int i = 0; i < count; i++) {
            long timestamp = start - (i * 60000L);
            String metricName = metric == null ? createRandomName() : metric;
            Annotation annotation = new Annotation("splunk", String.valueOf(i), "erelease", scope, metricName, timestamp);
            Map<String, String> fields = new HashMap<>();

            fields.put("displayName", createRandomName());
            fields.put("dataCenter", createRandomName());
            annotation.setFields(fields);
            result.add(annotation);
        }
        return result;
    }

    private List<Metric> _coalesceMetrics(Map<MetricQuery, List<Metric>> metricsMap) {
        List<Metric> metrics = new ArrayList<Metric>();

        for (List<Metric> list : metricsMap.values()) {
            metrics.addAll(list);
        }
        return metrics;
    }

    @Test
    public void testPutAndGetMetrics() throws InterruptedException {
        TSDBService service = system.getServiceFactory().getTSDBService();
        List<Metric> expected = createRandomMetrics(null, null, 10);

        try {
            service.putMetrics(expected);
            Thread.sleep(SLEEP_AFTER_PUT_IN_MILLIS);

            List<MetricQuery> queries = toQueries(expected);
            List<Metric> actual = _coalesceMetrics(service.getMetrics(queries));

            Assert.assertEquals(new HashSet<>(expected), new HashSet<>(actual));
            for (Metric metric : expected) {
                Metric actualMetric = actual.remove(actual.indexOf(metric));

                assertEquals(metric.getDisplayName(), actualMetric.getDisplayName());
                assertEquals(metric.getUnits(), actualMetric.getUnits());
                assertEquals(metric.getDatapoints(), actualMetric.getDatapoints());
                assertNotNull(actualMetric.getUid());
            }
        } finally {
            service.dispose();
        }
    }

    private List<MetricQuery> toQueries(List<Metric> expected) {
        List<MetricQuery> queries = new LinkedList<>();

        for (Metric metric : expected) {
            queries.add(toQuery(metric));
        }
        return queries;
    }

    @Test
    public void testGetMultipleTimeseries() throws InterruptedException, JsonProcessingException {
        String scope = createRandomName();
        String metric = "app_record.count";
        String[] recordTypes = { "A", "U", "V", "R" };
        TSDBService service = system.getServiceFactory().getTSDBService();

        try {
            List<Metric> expected = createRandomMetrics(scope, metric, recordTypes.length);
            Long start = null;
            Long end = null;

            for (int i = 0; i < recordTypes.length; i++) {
                String recordType = recordTypes[i];
                Metric forType = expected.get(i);

                forType.setTag("recordType", recordType);

                TreeMap<Long, String> dp = new TreeMap<>(forType.getDatapoints());
                long earliest = dp.firstKey();
                long latest = dp.lastKey();

                if (start == null || earliest < start) {
                    start = earliest;
                }
                if (end == null || latest > end) {
                    end = latest;
                }
            }
            service.putMetrics(expected);
            Thread.sleep(SLEEP_AFTER_PUT_IN_MILLIS);

            Map<String, String> tags = new HashMap<>();

            tags.put("recordType", "A|U|V|R");

            MetricQuery query = new MetricQuery(scope, metric, tags, start, end);
            List<MetricQuery> queries = Arrays.asList(new MetricQuery[] { query });
            List<Metric> actual = new LinkedList<>(_coalesceMetrics(service.getMetrics(queries)));

            assertTrue(actual.size() == expected.size());
        } finally {
            service.dispose();
        }
    }

    @Test
    public void testPutAndGetAnnotations() throws InterruptedException {
        TSDBService service = system.getServiceFactory().getTSDBService();
        List<Annotation> expected = createRandomAnnotations(null, null, 10);

        try {
            service.putAnnotations(expected);
            Thread.sleep(SLEEP_AFTER_PUT_IN_MILLIS);

            List<AnnotationQuery> queries = new LinkedList<>();

            for (Annotation annotation : expected) {
                queries.add(toQuery(annotation));
            }

            List<Annotation> actual = service.getAnnotations(queries);

            Assert.assertEquals(new HashSet<>(expected), new HashSet<>(actual));
            for (Annotation annotation : expected) {
                Annotation actualAnnotation = actual.remove(actual.indexOf(annotation));

                assertEquals(annotation.getFields(), actualAnnotation.getFields());
            }
        } finally {
            service.dispose();
        }
    }

    @Test
    public void testMultipleAnnotationsAtSingleTimestamp() throws InterruptedException {
        TSDBService service = system.getServiceFactory().getTSDBService();
        List<Annotation> expected = new LinkedList<>();
        String source = "splunk";
        String type = "erelease";
        String scope = createRandomName();
        long start = System.currentTimeMillis() - 60000L;

        for (int i = 0; i < 10; i++) {
            long timestamp = start - ((i % 3) * 60000L);
            String id = String.valueOf(i);
            Annotation annotation = new Annotation(source, id, type, scope, null, timestamp);
            Map<String, String> fields = new HashMap<>();

            fields.put(String.valueOf(i), String.valueOf(i));
            annotation.setFields(fields);
            expected.add(annotation);
        }
        try {
            service.putAnnotations(expected);
            Thread.sleep(SLEEP_AFTER_PUT_IN_MILLIS);

            List<AnnotationQuery> queries = new LinkedList<>();

            queries.add(new AnnotationQuery(scope, null, null, type, start - (4 * 60000L), System.currentTimeMillis()));

            List<Annotation> actual = service.getAnnotations(queries);

            Assert.assertEquals(new HashSet<>(expected), new HashSet<>(actual));
            for (Annotation annotation : expected) {
                Annotation actualAnnotation = actual.remove(actual.indexOf(annotation));

                assertEquals(annotation.getFields(), actualAnnotation.getFields());
            }
        } finally {
            service.dispose();
        }
    }

    @Test
    public void testPutAndGetScopeAnnotations() throws InterruptedException {
        TSDBService service = system.getServiceFactory().getTSDBService();
        List<Annotation> expected = new LinkedList<>();
        String source = "splunk";
        String type = "erelease";
        String scope = createRandomName();
        long timestamp = System.currentTimeMillis() - 60000L;

        for (int i = 0; i < 10; i++) {
            String id = String.valueOf(i);
            Annotation annotation = new Annotation(source, id, type, scope, null, timestamp);
            Map<String, String> fields = new HashMap<>();

            fields.put(String.valueOf(i), String.valueOf(i));
            annotation.setFields(fields);
            expected.add(annotation);
            timestamp -= 60000L;
        }
        try {
            service.putAnnotations(expected);
            Thread.sleep(SLEEP_AFTER_PUT_IN_MILLIS);

            List<AnnotationQuery> queries = new LinkedList<>();

            queries.add(new AnnotationQuery(scope, null, null, type, timestamp - 60000L, System.currentTimeMillis()));

            List<Annotation> actual = service.getAnnotations(queries);

            Assert.assertEquals(new HashSet<>(expected), new HashSet<>(actual));
            for (Annotation annotation : expected) {
                Annotation actualAnnotation = actual.remove(actual.indexOf(annotation));

                assertEquals(annotation.getFields(), actualAnnotation.getFields());
            }
        } finally {
            service.dispose();
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testFractureMetrics() {
        TSDBService service = new DefaultTSDBService(system.getConfiguration(), system.getServiceFactory().getMonitorService());
        Metric metric = new Metric("testscope", "testMetric");
        Map<Long, String> datapoints = new HashMap<>();

        for (int i = 0; i <= 200; i++) {
            datapoints.put(System.currentTimeMillis() + (i * 60000L), String.valueOf(random.nextInt(50)));
        }
        metric.setDatapoints(datapoints);
        try {
            Method method = DefaultTSDBService.class.getDeclaredMethod("fractureMetric", Metric.class);

            method.setAccessible(true);

            List<Metric> metricList = (List<Metric>) method.invoke(service, metric);

            assertEquals(3, metricList.size());
            assertEquals(100, metricList.get(0).getDatapoints().size());
            assertEquals(100, metricList.get(1).getDatapoints().size());
            assertEquals(1, metricList.get(2).getDatapoints().size());
        } catch (NoSuchMethodException | IllegalAccessException | IllegalArgumentException | InvocationTargetException ex) {
            throw new SystemException("Failed to construct fracture metric method using reflection");
        }
    }

    private AnnotationQuery toQuery(Annotation annotation) {
        String scope = annotation.getScope();
        String metric = annotation.getMetric();
        Map<String, String> tags = annotation.getTags();
        String type = annotation.getType();
        Long timestamp = annotation.getTimestamp();

        return new AnnotationQuery(scope, metric, tags, type, timestamp, null);
    }

    @Test
    public void isTSDBServiceSingleton() {
        TSDBService service1 = system.getServiceFactory().getTSDBService();
        TSDBService service2 = system.getServiceFactory().getTSDBService();

        assertTrue(service1 == service2);
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

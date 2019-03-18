package com.salesforce.dva.argus.service.tsdb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.salesforce.dva.argus.service.MonitorService;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.system.SystemException;

public class AbstractTSDBServiceTest extends AbstractTest {
    static final int RUNS = 100;
    static final int THREADS = 20;
    CloseableHttpClient readHttpClient;
    CloseableHttpClient writeHttpClient;

    private String getReply1 = String.join("\n",
            "[" +
            "    {" +
            "        \"metric\": \"TestType1-__-TestScope1.6f94d354\"," +
            "        \"tags\": {" +
            "            \"TestTag\": \"TagValue\"," +
            "            \"meta\": \"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOm51bGx9\"" +
            "        }," +
            "        \"aggregateTags\": []," +
            "        \"tsuids\": [" +
            "            \"000089A6D1A70000000000010000000000060000000000B00000010F7CB800000000028C00000E2548D1\"" +
            "        ]," +
            "        \"dps\": {" +
            "            \"1\": 0.0" +
            "        }" +
            "    }" +
            "]");

    private String getReply2 = String.join("\n",
    "[" +
            "    {" +
            "        \"metric\": \"TestType2-__-TestScope2.6f9a5d64\"," +
            "        \"tags\": {" +
            "            \"TestTag\": \"TagValue\"," +
            "            \"meta\": \"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOm51bGx9\"" +
            "        }," +
            "        \"aggregateTags\": []," +
            "        \"tsuids\": [" +
            "            \"000089A6D1A70000000000010000000000060000000000B00000010F7CB800000000028C00000E2548D2\"" +
            "        ]," +
            "        \"dps\": {" +
            "            \"1\": 0.0" +
            "        }" +
            "    }" +
            "]");

    private String getReply3 = String.join("\n",
    "[" +
            "    {" +
            "        \"metric\": \"TestType3-__-TestScope3.6f9fe774\"," +
            "        \"tags\": {" +
            "            \"TestTag\": \"TagValue\"," +
            "            \"meta\": \"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOm51bGx9\"" +
            "        }," +
            "        \"aggregateTags\": []," +
            "        \"tsuids\": [" +
            "            \"000089A6D1A70000000000010000000000060000000000B00000010F7CB800000000028C00000E2548D3\"" +
            "        ]," +
            "        \"dps\": {" +
            "            \"1\": 0.0" +
            "        }" +
            "    }" +
            "]");

    private String postReply = "";

    private String postBody = String.join("\n",
    "[" +
            "    {" +
            "        \"metric\":\"TestType1-__-TestScope1.6f94d354\"," +
            "        \"timestamp\":1," +
            "        \"value\":0.0," +
            "        \"tags\":{" +
            "            \"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOm51bGx9\"," +
            "            \"TestTag\":\"TagValue1\"" +
            "        }" +
            "    }," +
            "    {" +
            "        \"metric\":\"TestType3-__-TestScope3.6f9fe774\"," +
            "        \"timestamp\":1," +
            "        \"value\":0.0," +
            "        \"tags\":{" +
            "            \"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOm51bGx9\"," +
            "            \"TestTag\":\"TagValue3\"" +
            "        }" +
            "    }," +
            "    {\"" +
            "        metric\":\"TestType2-__-TestScope2.6f9a5d64\"," +
            "        \"timestamp\":1," +
            "        \"value\":0.0," +
            "        \"tags\":{" +
            "            \"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOm51bGx9\"," +
            "            \"TestTag\":\"TagValue2\"" +
            "        }" +
            "    }" +
            "]");

    private String getBody1 = String.join("\n",
    "{" +
            "    \"start\":0," +
            "    \"end\":3," +
            "    \"noAnnotations\":true," +
            "    \"msResolution\":true," +
            "    \"showTSUIDs\":true," +
            "    \"queries\":" +
            "    [" +
            "        {" +
            "            \"aggregator\":\"avg\"," +
            "            \"metric\":\"TestType1-__-TestScope1.6f94d354\"," +
            "            \"tags\":{" +
            "                \"TestTag\":\"TagValue1\"" +
            "            }" +
            "        }" +
            "    ]" +
            "}");

    private String getBody2 = String.join("\n",
    "{" +
            "    \"start\":0," +
            "    \"end\":3," +
            "    \"noAnnotations\":true," +
            "    \"msResolution\":true," +
            "    \"showTSUIDs\":true," +
            "    \"queries\":" +
            "    [" +
            "        {" +
            "            \"aggregator\":\"avg\"," +
            "            \"metric\":\"TestType2-__-TestScope2.6f9a5d64\"," +
            "            \"tags\":{" +
            "                \"TestTag\":\"TagValue2\"" +
            "            }" +
            "        }" +
            "    ]" +
            "}");

    private String getBody3 = String.join("\n",
    "{" +
            "    \"start\":0," +
            "    \"end\":3," +
            "    \"noAnnotations\":true," +
            "    \"msResolution\":true," +
            "    \"showTSUIDs\":true," +
            "    \"queries\":" +
            "    [" +
            "        {" +
            "            \"aggregator\":\"avg\"," +
            "            \"metric\":\"TestType3-__-TestScope3.6f9fe774\"," +
            "            \"tags\":{" +
            "                \"TestTag\":\"TagValue3\"" +
            "            }" +
            "        }" +
            "    ]" +
            "}");
    
    private String getAnnotationReply = String.join("\n",
            "[" +
            "    {" +
            "        \"metric\": \"TestType1-__-TestScope1.6f94d354\"," +
            "        \"tags\": {" +
            "            \"TestTag\": \"TagValue\"," +
            "            \"meta\": \"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOm51bGx9\"" +
            "        }," +
            "        \"aggregateTags\": []," +
            "        \"tsuids\": [" +
            "            \"000089A6D1A70000000000010000000000060000000000B00000010F7CB800000000028C00000E2548D1\"" +
            "        ]," +
            "        \"type\": \"ANNOTATION\"," +
            "        \"fields\": {" +
            "            \"owner\": \"jdoe\"" +
            "        }" +
            "    }" +
            "]");

    @Test
    public void testAnnotationWorkflow() throws IOException  {
        DefaultTSDBService service = new DefaultTSDBService(system.getConfiguration(), system.getServiceFactory().getMonitorService());

        List<Annotation> annotations = new ArrayList<>();
        annotations.add(_constructAnnotation('1'));
        annotations.add(_constructAnnotation('2'));
        annotations.add(_constructAnnotation('3'));

        AbstractTSDBService spyService = _initializeSpyService(service, postReply, getReply1, getReply2, getReply3, postReply);

        spyService.putAnnotations(annotations);

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<StringEntity> contentCaptor = ArgumentCaptor.forClass(StringEntity.class);

        verify(spyService, times(5)).executeHttpRequest(any(), urlCaptor.capture(), any(), contentCaptor.capture());

        List<String> urls = urlCaptor.getAllValues();
        List<StringEntity> contentEntities = contentCaptor.getAllValues();

        List<String> contents = new ArrayList<>();

        for(StringEntity contentEntity : contentEntities) {
            contents.add(EntityUtils.toString(contentEntity));
        }

        assertTrue(urls.get(0).contains("put"));
        assertEquals(postBody.replaceAll("\\s+",""), contents.get(0));

        assertTrue(urls.get(1).contains("query"));
        assertTrue(contents.contains(getBody1.replaceAll("\\s+","")));

        assertTrue(urls.get(2).contains("query"));
        assertTrue(contents.contains(getBody2.replaceAll("\\s+","")));

        assertTrue(urls.get(3).contains("query"));
        assertTrue(contents.contains(getBody3.replaceAll("\\s+","")));

        List<AnnotationQuery> queries = new ArrayList<>();
        queries.add(toQuery(annotations.get(0)));
        
        spyService = _initializeSpyService(service, getAnnotationReply);
        spyService.getAnnotations(queries);
        
        verify(spyService, times(1)).executeHttpRequest(any(), urlCaptor.capture(), any(), contentCaptor.capture());
    }

    private AnnotationQuery toQuery(Annotation annotation) {
        String scope = annotation.getScope();
        String metric = annotation.getMetric();
        Map<String, String> tags = annotation.getTags();
        String type = annotation.getType();
        Long timestamp = annotation.getTimestamp();

        return new AnnotationQuery(scope, metric, tags, type, timestamp, null);
    }

    private AbstractTSDBService _initializeSpyService(AbstractTSDBService service, String... replies) {

        readHttpClient =  mock(CloseableHttpClient.class);
        writeHttpClient =  mock(CloseableHttpClient.class);

        service.SetTsdbClients(readHttpClient, writeHttpClient);

        AbstractTSDBService spyService = spy(service);

        doAnswer(new Answer() {
            private int count = 0;

            public Object answer(InvocationOnMock invocation) {
                String reply = replies[count];
                count++;
                return reply;
            }
        }).when(spyService).extractResponse(any());

        return spyService;
    }

    private Annotation _constructAnnotation(char appendChar) {
        Annotation result = new Annotation("TestSource"+ appendChar,
                "TestID"+ appendChar,
                "TestType"+ appendChar,
                "TestScope"+ appendChar,
                "TestMetric"+ appendChar,
                1472282830936L);

        Map<String, String> fields = new TreeMap<>();
        Map<String, String> tags = new TreeMap<>();

        fields.put("TestField", "FieldValue" + appendChar);
        tags.put("TestTag", "TagValue" + appendChar);
        result.setFields(fields);
        result.setTags(tags);
        return result;
    }

    @Test
    public void testWrapperListUsedInBatching() {

        List<Map<String, AbstractTSDBService.AnnotationWrapper>> wrapperList = new ArrayList<>();

        AbstractTSDBService.AnnotationWrapper wrapper1 = new AbstractTSDBService.AnnotationWrapper("1", _constructAnnotation('1'));
        AbstractTSDBService.AnnotationWrapper wrapper2 = new AbstractTSDBService.AnnotationWrapper("1", _constructAnnotation('2'));
        AbstractTSDBService.AnnotationWrapper wrapper3 = new AbstractTSDBService.AnnotationWrapper("2", _constructAnnotation('3'));
        AbstractTSDBService.AnnotationWrapper wrapper4 = new AbstractTSDBService.AnnotationWrapper("3", _constructAnnotation('2'));

        AbstractTSDBService.addToWrapperList(wrapperList, wrapper1);
        AbstractTSDBService.addToWrapperList(wrapperList, wrapper2);
        AbstractTSDBService.addToWrapperList(wrapperList, wrapper3);
        AbstractTSDBService.addToWrapperList(wrapperList, wrapper4);

        assertEquals(wrapperList.get(0).size(), 3);
        assertEquals(wrapperList.get(1).size(), 1);
    }

    @Test
    public void testFractureMetrics() {
        TSDBService service = new AbstractTSDBService(system.getConfiguration(), system.getServiceFactory().getMonitorService());
        Metric metric = new Metric("testscope", "testMetric");
        Map<Long, Double> datapoints = new HashMap<>();

        for (int i = 0; i <= 200; i++) {
            datapoints.put(System.currentTimeMillis() + (i * 60000L), (double)(random.nextInt(50)));
        }
        metric.setDatapoints(datapoints);
        try {
            Method method = AbstractTSDBService.class.getDeclaredMethod("fractureMetric", Metric.class);

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

    @Test
    public void testCyclingIterator() {
        AbstractTSDBService service = new AbstractTSDBService(system.getConfiguration(), system.getServiceFactory().getMonitorService());
        String[][] endpointTrials = {
                {"1"},
                {"1", "2", "3", "4"},
                {"4", "6", "133", "200", "1133", "2244", "4466", "8989", "19200", "320"}
        };

        for (int j = 0; j < endpointTrials.length; j++) {
            String[] endpoints = endpointTrials[j];
            Iterator<String> iter = service.constructCyclingIterator(endpoints);
            List<Thread> threads = new ArrayList<>();
            ConcurrentLinkedQueue<String> queue = new ConcurrentLinkedQueue<>();
            System.out.println(String.format("Trying %d .next() calls with %d threads calling cycling iterator on endpoints %s", RUNS, THREADS, String.join(", ", endpoints)));
            for (int i = 0; i < THREADS; i++) {
                Thread thread = new Thread(new IterateTask(iter, queue));
                threads.add(thread);
                thread.start();
            }
            for (Thread t : threads) {
                try {
                    t.join();
                } catch (InterruptedException ex) {
                    return;
                }
            }
            Map<String, Integer> counts = new HashMap<>();
            String[] actuals = queue.toArray(new String[0]);
            for (String s: actuals) {
                Integer count = counts.get(s);
                if (count == null) {
                    counts.put(s, 1);
                } else {
                    counts.put(s, count + 1);
                }
            }
            /* The inserted items should each have been inserted ( #insertions / #element) times */
            for (int count: counts.values()) {
                assertEquals(RUNS * THREADS / endpoints.length, count);
            }
        }
    }

    @Test
    public void testConstructNotEqualsQuery() throws IOException {
        DefaultTSDBService service = new DefaultTSDBService(system.getConfiguration(), mock(MonitorService.class));
        ObjectMapper mapper = service.getMapper();
        MetricQuery query = new MetricQuery(
                "scope",
                "metric",
                new ImmutableMap.Builder<String, String>().put("tagk", "~tagv").build(),
                0L,
                1L);
        String queryJson = mapper.writeValueAsString(query);
        JsonNode root = mapper.readTree(queryJson);
        String tagValue = root.get("queries").get(0).get("tags").get("tagk").asText();
        assertEquals(MetricQuery.TAG_NOT_EQUALS_TSDB_PREFIX + "tagv" + MetricQuery.TAG_NOT_EQUALS_TSDB_SUFFIX, tagValue);
    }

    class IterateTask implements Runnable {
        Random random = new Random();
        Iterator<String> iter;
        ConcurrentLinkedQueue<String> queue;

        IterateTask(Iterator<String> iterator, ConcurrentLinkedQueue<String> queue) {
            this.iter = iterator;
            this.queue = queue;
        }
        @Override
        public void run() {
            try {
                for (int i = 0; i < RUNS; i++) {
                    queue.add(iter.next());
                    Thread.sleep(3 + random.nextInt(5));
                }
            } catch (InterruptedException ex) {
                return;
            }
        }
    }
}

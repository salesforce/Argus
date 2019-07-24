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


package com.salesforce.dva.argus.service.annotation;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.salesforce.dva.argus.TestUtils;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.schema.ElasticSearchUtils;
import com.salesforce.dva.argus.service.tsdb.AnnotationQuery;
import com.salesforce.dva.argus.system.SystemConfiguration;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.io.IOException;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;

@PowerMockIgnore("*.ssl.*")
@RunWith(PowerMockRunner.class)
@PrepareForTest(ElasticSearchUtils.class)
public class ElasticSearchAnnotationServiceTest {

    private RestClient restClient;
    private String createSucessReply = String.join("\n",
            "{" +
                    "    \"took\": 55," +
                    "    \"errors\": false," +
                    "    \"items\": [" +
                    "        {" +
                    "            \"create\": {" +
                    "                \"_index\": \"argus-annotation\"," +
                    "                \"_id\": \"cdfd12850d42746257f8217899647c8b\"," +
                    "                \"_version\": 1," +
                    "                \"result\": \"created\"," +
                    "                \"_shards\": {" +
                    "                    \"total\": 2," +
                    "                    \"successful\": 1," +
                    "                    \"failed\": 0" +
                    "                }," +
                    "                \"created\": true," +
                    "                \"status\": 201" +
                    "            }" +
                    "        }" +
                    "    ]" +
            "}");

    private String getReply = String.join("\n",
            "{",
            "    \"took\": 9,",
            "    \"timed_out\": false,",
            "    \"_shards\": {",
            "        \"total\": 6,",
            "        \"successful\": 6,",
            "        \"skipped\": 0,",
            "        \"failed\": 0",
            "    },",
            "    \"hits\": {",
            "        \"total\": 1,",
            "        \"max_score\": 0.0,",
            "        \"hits\": [",
            "               {",
            "                   \"_index\": \"argus-annotation-2019-05\",",
            "                   \"_id\": \"44618b179e858ef9fb7b49997ebdba0c\",",
            "                   \"_score\": 0.0,",
            "                   \"_source\": {",
            "                       \"metric\": \"metric1\",",
            "                       \"scope\": \"scope1\",",            
            "                       \"source\": \"unittest\",",
            "                       \"id\": \"16ab4b56311\",",
            "                       \"type\": \"unittest\",",
            "                       \"fields\": \"{}\",",
            "                       \"tags\": \"{}\",",
            "                       \"sid\": \"f9c22bcbd813474ec99f7011ae50b080\",",
            "                       \"ts\": \"1557809559073\"",
            "                     }",
            "               }",
            "         ]",
            "    }",
            "}");

    private String annotationQueryMustTermRange = String.join("\n",
            "{",
            "  \"query\": {",
            "    \"bool\": {",
            "      \"must\": [",
            "        {",
            "          \"term\": {",
            "            \"sid.raw\": \"f9c22bcbd813474ec99f7011ae50b080\"",
            "          }",
            "        },",
            "        {",
            "          \"range\": {",
            "            \"ts\": {",
            "              \"gte\": \"1557809359073\",",
            "              \"lte\": \"1557809599073\"",            
            "            }",
            "          }",
            "        }",            
            "      ]",
            "    }",
            "  },",
            "  \"from\": 0,",
            "  \"size\": 10000",
            "}");

    private static SystemConfiguration systemConfig;
    private static ElasticSearchAnnotationService esAnnotationService;
    private static ObjectMapper mapper = new ObjectMapper();
    
    private String convertToPrettyJson(String jsonString) {
        JsonParser parser = new JsonParser();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        JsonElement el = parser.parse(jsonString);
        return gson.toJson(el);
    }

    @BeforeClass
    public static void setUpClass() {
        Properties config = new Properties();
        systemConfig = new SystemConfiguration(config);
        MonitorService mockedMonitor = mock(MonitorService.class);
        mockStatic(ElasticSearchUtils.class);
        ElasticSearchUtils mockedElasticSearchUtils = mock(ElasticSearchUtils.class);
        esAnnotationService = new ElasticSearchAnnotationService(systemConfig, mockedMonitor, mockedElasticSearchUtils);
    }

    @Before
    public void setUp() {
        TestUtils.setStaticField(ElasticSearchAnnotationService.class, "ANNOTATION_INDEX_MAX_RESULT_WINDOW", 10000);
    }
    
    @Test
    public void testConvertTimestampToMillis(){
        AnnotationQuery annotationQuery = new AnnotationQuery("scope1", "metric1", null, "unittest", 1557809359073L, 1557809599073L);
        esAnnotationService.convertTimestampToMillis(annotationQuery);
        assertEquals(1557809359073L, annotationQuery.getStartTimestamp().longValue());
        assertEquals(1557809599073L, annotationQuery.getEndTimestamp().longValue());
        
        annotationQuery = new AnnotationQuery("scope1", "metric1", null, "unittest", 1557809359L, 1557809599L);
        esAnnotationService.convertTimestampToMillis(annotationQuery);
        assertEquals(1557809359000L, annotationQuery.getStartTimestamp().longValue());
        assertEquals(1557809599000L, annotationQuery.getEndTimestamp().longValue());

        annotationQuery = new AnnotationQuery("scope1", "metric1", null, "unittest", 1557809359123L, 1557809599L);
        esAnnotationService.convertTimestampToMillis(annotationQuery);
        assertEquals(1557809359123L, annotationQuery.getStartTimestamp().longValue());
        assertEquals(1557809599000L, annotationQuery.getEndTimestamp().longValue());

        annotationQuery = new AnnotationQuery("scope1", "metric1", null, "unittest", 1557809359L, 1557809599456L);
        esAnnotationService.convertTimestampToMillis(annotationQuery);
        assertEquals(1557809359000L, annotationQuery.getStartTimestamp().longValue());
        assertEquals(1557809599456L, annotationQuery.getEndTimestamp().longValue());
    }
    
    @Test
    public void testAnnotationRecordListMapper() throws IOException {
        mapper = ElasticSearchAnnotationService.getAnnotationObjectMapper(new AnnotationRecordList.IndexSerializer());

        Annotation record1 = new Annotation("unittest", "id123", "unittest", "scope1", "metric1", 1557801635504L);
        AnnotationRecordList recordList = new AnnotationRecordList(Arrays.asList(record1), AnnotationRecordList.HashAlgorithm.fromString("MD5"));

        String serialized = mapper.writeValueAsString(recordList);
        String[] lines = serialized.split("\\r?\\n");

        String expectedIndexName = "argus-annotation-2019-05";
        JsonNode root = mapper.readTree(lines[0]);
        String actualIndexName = root.get("index").get("_index").asText();
        assertEquals(expectedIndexName, actualIndexName);

        String expectedSerializedAnnotation = 
                "{\"metric\":\"metric1\",\"scope\":\"scope1\",\"source\":\"unittest\",\"id\":\"id123\",\"type\":\"unittest\",\"fields\":\"{}\",\"tags\":\"{}\",\"sid\":\"f9c22bcbd813474ec99f7011ae50b080\",\"ts\":\"1557801635504\"}";
        assertEquals(expectedSerializedAnnotation, lines[1]);
    }

    @Test
    public void testPutAnnotationsUsingAnnotationIndex() throws IOException {
        ElasticSearchAnnotationService spyService = _initializeSpyService(esAnnotationService, createSucessReply, true);
        List<Annotation> annotations = new ArrayList<>();

        Annotation record1 = new Annotation("unittest", "id456", "unittest", "scope1", "metric1", 1557800720441L);
        annotations.add(record1);

        spyService.putAnnotations(annotations);

        ArgumentCaptor<String> requestCaptorUrl = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> requestCaptorBody = ArgumentCaptor.forClass(String.class);

        verifyStatic(ElasticSearchUtils.class, times(1));
        ElasticSearchUtils.performESRequest(eq(restClient), requestCaptorUrl.capture(), requestCaptorBody.capture());


        String expectedURL = "_bulk";
        assertEquals(expectedURL, requestCaptorUrl.getValue());

        String[] lines = requestCaptorBody.getValue().split("\\r?\\n");

        String expectedIndexName = "argus-annotation-2019-05";
        JsonNode root = mapper.readTree(lines[0]);
        String actualIndexName = root.get("index").get("_index").asText();
        assertEquals(expectedIndexName, actualIndexName);

        String expectedSerializedAnnotation =
                "{\"metric\":\"metric1\",\"scope\":\"scope1\",\"source\":\"unittest\",\"id\":\"id456\",\"type\":\"unittest\",\"fields\":\"{}\",\"tags\":\"{}\",\"sid\":\"f9c22bcbd813474ec99f7011ae50b080\",\"ts\":\"1557800720441\"}";
        assertEquals(expectedSerializedAnnotation, lines[1]);
    }

    @Test
    public void testGetAnnotations() throws IOException {
        AnnotationQuery annotationQuery = new AnnotationQuery("scope1", "metric1", null, "unittest", 1557809359073L, 1557809599073L);
        List<AnnotationQuery> queries = new ArrayList<>();
        queries.add(annotationQuery);
        ElasticSearchAnnotationService spyService = _initializeSpyService(esAnnotationService, getReply, false);

        List<Annotation> annotations = spyService.getAnnotations(queries);
        Annotation expectedAnnotation = new Annotation("unittest", "16ab4b56311", "unittest", "scope1", "metric1", 1557809559073L);
        assertEquals(expectedAnnotation, annotations.get(0));
        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(restClient, times(1)).performRequest(requestCaptor.capture());
        Request capturedRequest = requestCaptor.getValue();

        String queryJson = convertToPrettyJson(EntityUtils.toString(capturedRequest.getEntity()));
        assertEquals(annotationQueryMustTermRange, queryJson);
        String expectedURL = "/argus-annotation-*/_search";
        assertEquals(expectedURL, capturedRequest.getEndpoint());
    }

    @Test (expected = RuntimeException.class)
    public void testGetAnnotationsExceedingLimit(){
        AnnotationQuery annotationQuery = new AnnotationQuery("scope1", "metric1", null, "unittest", 1557809359073L, 1557809599073L);
        List<AnnotationQuery> queries = new ArrayList<>();
        queries.add(annotationQuery);

        ElasticSearchAnnotationService spyService = null;
        try {
            spyService = _initializeSpyService(esAnnotationService, getReply, false);
        } catch (IOException e) {
            fail();
        }
        Whitebox.setInternalState(spyService, "ANNOTATION_INDEX_MAX_RESULT_WINDOW", 1);
        spyService.getAnnotations(queries);
    }

    @Test
    public void testHashedSearchIdentifierAnnotationAndAnnotationQueryMatch(){
        Map<String, String> annotationQueryTags = new HashMap<>();
        annotationQueryTags.put("device","device1");
        annotationQueryTags.put("podName","pod1");
        AnnotationQuery annotationQuery = new AnnotationQuery("scope1", "metric1", annotationQueryTags, "transactionId", 1557809359073L, 1557809599073L);

        Annotation annotation = new Annotation("source", "16ab4b56311", "transactionId", "scope1", "metric1", 1557809559073L);
        annotation.setTags(annotationQueryTags);
        assertEquals(AnnotationRecordList.getHashedSearchIdentifier(annotation), ElasticSearchAnnotationService.getHashedSearchIdentifier(annotationQuery));

        // change order of tags in annotation query
        Map<String, String> annotationQueryTags2 = new HashMap<>();
        annotationQueryTags2.put("podName","pod1");
        annotationQueryTags2.put("device","device1");
        AnnotationQuery annotationQuery2 = new AnnotationQuery("scope1", "metric1", annotationQueryTags2, "transactionId", 1557809359073L, 1557809599073L);
        assertEquals(AnnotationRecordList.getHashedSearchIdentifier(annotation), ElasticSearchAnnotationService.getHashedSearchIdentifier(annotationQuery2));
    }

    private ElasticSearchAnnotationService _initializeSpyService(ElasticSearchAnnotationService service,
            String firstReply, boolean isPut) throws IOException {

        restClient = mock(RestClient.class);
        service.setESRestClient(restClient);
        mockStatic(ElasticSearchUtils.class);
        if (isPut) {
            when(ElasticSearchUtils.performESRequest(eq(restClient), any(), any())).thenReturn(mapper.readValue(firstReply, ElasticSearchUtils.PutResponse.class));
        } else {
            when(ElasticSearchUtils.extractResponse(any())).thenReturn(firstReply);
            mapper = ElasticSearchAnnotationService.getAnnotationObjectMapper(new AnnotationRecordList.IndexSerializer());
            AnnotationRecordList ret = mapper.readValue(firstReply, new TypeReference<AnnotationRecordList>() {});
            when(ElasticSearchUtils.toEntity(any(), any(),any())).thenReturn(ret);
        }

        ElasticSearchAnnotationService spyService = spy(service);

        return spyService;
    }
}
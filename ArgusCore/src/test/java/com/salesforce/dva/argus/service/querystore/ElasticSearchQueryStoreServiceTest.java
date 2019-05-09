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


package com.salesforce.dva.argus.service.querystore;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.QueryStoreRecord;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ElasticSearchQueryStoreServiceTest {

    private RestClient restClient;
    private String createSucessReply = String.join("\n",
            "{" +
                    "    \"took\": 55," +
                    "    \"errors\": false," +
                    "    \"items\": [" +
                    "        {" +
                    "            \"create\": {" +
                    "                \"_index\": \"argusqs-v1\"," +
                    "                \"_type\": \"argus-query_type\"," +
                    "                \"_id\": \"26efe188e63fb16c94bd6ec9bbd98d0f\"," +
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

    static private SystemConfiguration systemConfig;
    static private ElasticSearchQueryStoreService _esQueryStoreService;
    static private String queryStoreIndexName;
    static private String queryStoreTypeName;


    @BeforeClass
    public static void setUpClass() {
        Properties config = new Properties();
        systemConfig = new SystemConfiguration(config);
        MonitorService mockedMonitor = mock(MonitorService.class);
        _esQueryStoreService = new ElasticSearchQueryStoreService(systemConfig, mockedMonitor);
        queryStoreIndexName=systemConfig.getValue(ElasticSearchQueryStoreService.Property.QUERY_STORE_ES_INDEX_NAME.getName(),
                ElasticSearchQueryStoreService.Property.QUERY_STORE_ES_INDEX_NAME.getDefaultValue());
        queryStoreTypeName=systemConfig.getValue(ElasticSearchQueryStoreService.Property.QUERY_STORE_ES_INDEX_TYPE.getName(),
                ElasticSearchQueryStoreService.Property.QUERY_STORE_ES_INDEX_TYPE.getDefaultValue());
    }

    @Test
    public void testPutIndexUsingQueryStoreIndex() throws IOException {

        List<Metric> metrics = new ArrayList<>();
        Metric myMetric = new Metric("scope1", "metric1");
        metrics.add(myMetric);
        ElasticSearchQueryStoreService spyService = _initializeSpyService(_esQueryStoreService, createSucessReply, createSucessReply);

        Set<QueryStoreRecord> records = new HashSet<>();

        for(Metric m : metrics) {
            QueryStoreRecord qsr = new QueryStoreRecord(m.getScope(),m.getMetric());
            records.add(qsr);
        }

        spyService.upsertQueryStoreRecords(records);

        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);

        verify(restClient, times(1)).performRequest(requestCaptor.capture());

        Request capturedRequest = requestCaptor.getValue();
        String createJson = EntityUtils.toString(capturedRequest.getEntity());
        assertTrue(createJson.contains("index"));
        assertTrue(createJson.contains("mts"));
        assertTrue(createJson.contains("sourcehost"));
        assertTrue(createJson.contains("scope"));
        assertTrue(createJson.contains("metric"));
        String expectedURL = String.format("/%s/%s/_bulk", queryStoreIndexName, queryStoreTypeName);
        assertEquals(expectedURL, capturedRequest.getEndpoint());
    }

    @Test
    public void testDoExtractResponse() throws Exception {
        final String message = "this is a test";
        BasicHttpEntity entity = new BasicHttpEntity();
        try(ByteArrayInputStream bis = new ByteArrayInputStream(message.getBytes())) {
            entity.setContent(bis);
        }
        catch (IOException e) {
            throw e;
        }

        String responseMessage = ElasticSearchQueryStoreService.doExtractResponse(200, entity);
        assertEquals("expect the entity to be equal after extraction", message, responseMessage);
    }

    @Test
    public void pushExistingRecordToElasticSearch()
    {
        QueryStoreRecord record1= new QueryStoreRecord("scope1","metric1");

        Set<QueryStoreRecord> records = new HashSet<>();
        records.add(record1);
        ElasticSearchQueryStoreService spyService = _initializeSpyService(_esQueryStoreService, createSucessReply, createSucessReply);
        spyService.addQueryRecordsToCreatedBloom(records);

        List<Metric> metrics = new ArrayList<>();
        Metric myMetric = new Metric("scope1", "metric1");
        metrics.add(myMetric);
        Set<Metric> recordsToAdd = new HashSet<>(metrics.size());
        recordsToAdd.add(myMetric);

        spyService.putArgusWsQueries(metrics);
        verify(spyService,times(1)).insertRecordsToES(new HashSet<>(metrics.size()));
        verify(spyService,never()).upsertQueryStoreRecords(new HashSet<>(metrics.size()));

    }

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testDoExtractResponse400() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Status code: 400");
        ElasticSearchQueryStoreService.doExtractResponse(400, null);
    }

    @Test
    public void testDoExtractResponse500() {
        expectedException.expect(SystemException.class);
        expectedException.expectMessage("Status code: 500");
        ElasticSearchQueryStoreService.doExtractResponse(500, null);
    }

    @Test
    public void testQueryStoreRecordListMapper() throws Exception {
        ObjectMapper mapper = ElasticSearchQueryStoreService.getQueryStoreObjectMapper(new QueryStoreRecordList.IndexSerializer());

        QueryStoreRecord record1 = new QueryStoreRecord("scope1", "metric1");
        QueryStoreRecordList recordList = new QueryStoreRecordList(new HashSet<>(Arrays.asList(record1)), QueryStoreRecordList.HashAlgorithm.fromString("MD5"));

        String serialized = mapper.writeValueAsString(recordList);

        assertTrue("expect the serialized record to have scope", serialized.contains("scope1"));
        assertTrue("expect the serialized record to have metric", serialized.contains("metric1"));
    }

    private ElasticSearchQueryStoreService _initializeSpyService(ElasticSearchQueryStoreService service,
                                                             String firstReply, String secondReply) {

        restClient = mock(RestClient.class);

        service.setESRestClient(restClient);

        ElasticSearchQueryStoreService spyService = spy(service);

        doAnswer(new Answer() {
            private int count = 0;

            public Object answer(InvocationOnMock invocation) {
                count++;
                if (count == 1) {
                    return firstReply;
                }

                return secondReply;
            }
        }).when(spyService).extractResponse(any());

        return spyService;
    }
}

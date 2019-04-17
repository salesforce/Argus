package com.salesforce.dva.argus.service.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.salesforce.dva.argus.entity.KeywordQuery;
import com.salesforce.dva.argus.entity.MetatagsRecord;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.MetricSchemaRecord;
import com.salesforce.dva.argus.entity.MetricSchemaRecordQuery;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpEntity;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.junit.BeforeClass;
import java.util.Properties;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

public class ElasticSearchSchemaServiceTest {

    private RestClient restClient;
    private String getReply = String.join("\n",
            "{",
            "    \"took\": 1,",
            "    \"timed_out\": false,",
            "    \"_shards\": {",
            "        \"total\": 6,",
            "        \"successful\": 6,",
            "        \"failed\": 0",
            "    },",
            "    \"hits\": {",
            "        \"total\": 426,",
            "        \"max_score\": 0.0,",
            "        \"hits\": []",
            "    },",
            "    \"aggregations\": {",
            "        \"distinct_values\": {",
            "            \"doc_count_error_upper_bound\": 0,",
            "            \"sum_other_doc_count\": 424,",
            "            \"buckets\": [",
            "                {",
            "                    \"key\": \"system.name1\\n\",",
            "                    \"doc_count\": 1",
            "                },",
            "                {",
            "                    \"key\": \"system.name2\\n\",",
            "                    \"doc_count\": 1",
            "                }",
            "            ]",
            "        }",
            "    }",
            "}");

    private String scopeQuery = String.join("\n",
            "{",
            "  \"query\": {",
            "    \"bool\": {",
            "      \"filter\": [",
            "        {",
            "          \"regexp\": {",
            "            \"scope.raw\": \"system.*\"",
            "          }",
            "        }",
            "      ]",
            "    }",
            "  },",
            "  \"size\": 0,",
            "  \"aggs\": {",
            "    \"distinct_values\": {",
            "      \"terms\": {",
            "        \"field\": \"scope.raw\",",
            "        \"order\": {",
            "          \"_term\": \"asc\"",
            "        },",
            "        \"size\": 10000,",
            "        \"execution_hint\": \"map\"",
            "      }",
            "    }",
            "  }",
            "}");

    private String metricQueryTagvRegex = String.join("\n",
            "{",
            "  \"query\": {",
            "    \"bool\": {",
            "      \"filter\": [",
            "        {",
            "          \"regexp\": {",
            "            \"metric.raw\": \"argus\"",
            "          }",
            "        },",
            "        {",
            "          \"regexp\": {",
            "            \"scope.raw\": \"system\"",
            "          }",
            "        },",
            "        {",
            "          \"regexp\": {",
            "            \"tagk.raw\": \"device\"",
            "          }",
            "        },",
            "        {",
            "          \"regexp\": {",
            "            \"tagv.raw\": \"abc.*\"",
            "          }",
            "        }",
            "      ]",
            "    }",
            "  },",
            "  \"size\": 0,",
            "  \"aggs\": {",
            "    \"distinct_values\": {",
            "      \"terms\": {",
            "        \"field\": \"tagv.raw\",",
            "        \"order\": {",
            "          \"_term\": \"asc\"",
            "        },",
            "        \"size\": 10000,",
            "        \"execution_hint\": \"map\"",
            "      }",
            "    }",
            "  }",
            "}");

    private String metricQueryNamespaceRegex = String.join("\n",
            "{",
            "  \"query\": {",
            "    \"bool\": {",
            "      \"filter\": [",
            "        {",
            "          \"regexp\": {",
            "            \"metric.raw\": \"argus\"",
            "          }",
            "        },",
            "        {",
            "          \"regexp\": {",
            "            \"scope.raw\": \"system\"",
            "          }",
            "        },",
            "        {",
            "          \"regexp\": {",
            "            \"namespace.raw\": \"common.*\"",
            "          }",
            "        }",
            "      ]",
            "    }",
            "  },",
            "  \"size\": 0,",
            "  \"aggs\": {",
            "    \"distinct_values\": {",
            "      \"terms\": {",
            "        \"field\": \"namespace.raw\",",
            "        \"order\": {",
            "          \"_term\": \"asc\"",
            "        },",
            "        \"size\": 10000,",
            "        \"execution_hint\": \"map\"",
            "      }",
            "    }",
            "  }",
            "}");

    static private ElasticSearchSchemaService _esSchemaService;
    static private SystemConfiguration systemConfig;

    @BeforeClass
    public static void setUpClass() {
        Properties config = new Properties();
        systemConfig = new SystemConfiguration(config);
        MonitorService mockedMonitor = mock(MonitorService.class);
        ElasticSearchUtils mockedElasticSearchUtils = mock(ElasticSearchUtils.class);
        _esSchemaService = new ElasticSearchSchemaService(systemConfig, mockedMonitor, mockedElasticSearchUtils);
    }

    @After
    public void tearDown() {
        _esSchemaService.clearBlooms();
    }


    @Test
    public void testPutCreateUsingMetatagsIndex() throws IOException {

        List<Metric> metrics = new ArrayList<>();

        Map<String, String> mtags = new HashMap<>();
        for(int i=1;i<4;i++) {
            mtags.put("dc"+i, "metav"+i);
        }
        MetatagsRecord metatags = new MetatagsRecord(mtags, "mymtagsid");
        Metric myMetric = new Metric("myscope", "mymetricname");
        myMetric.setMetatagsRecord(metatags);
        metrics.add(myMetric);

        Set<MetatagsRecord> records = new HashSet<>();

        for(Metric m : metrics) {
            MetatagsRecord msr = new MetatagsRecord(m.getMetatagsRecord().getMetatags(), m.getMetatagsRecord().getKey());
            records.add(msr);
        }

        Pair<MetatagsSchemaRecordList, String> retPair = _esSchemaService.getListAndBodyForUpsertMetatags(records);
        String createJson = retPair.getValue();

        assertTrue(createJson.contains("create"));
        assertFalse(createJson.contains("update"));
        assertTrue(createJson.contains("cts"));
        assertTrue(createJson.contains("mts"));
    }

    @Test
    public void testGetWithScroll() throws IOException {
        String reply = "{\"_scroll_id\":\"DnF1ZXJ5VGhlbkZldGNoMgAAAAENlX7HFjdTNVBGTGFmUUNLT3JyZEhKZWlyNXcAAAABDYdg9xZQUUpjNUN5eFRuS0tEYWNIQ1Vwdkp3AAAAAAN8R34WTHJZWWU5cDVTd2VvelRRM3BsTkQ3UQAAAAAGraWTFnRpSFI4eTBLUl9DTGhhaWZURmlnWWcAAAABCFeY2RY5ZTdWUkpKTVNxLXdScFZ2d0l4UGVRAAAAAQ2HYPgWUFFKYzVDeXhUbktLRGFjSENVcHZKdwAAAAAGraWUFnRpSFI4eTBLUl9DTGhhaWZURmlnWWcAAAABCefkTxZ4RDRtTXZhTlNncVUxX0VGYm14YU1RAAAAAAJYwTcWUmRTbE5fWEtUMXktMDVMWG1NU0lnZwAAAAADfUxpFjBwTWJSVEJRVGE2ZDRlaEhKN0VEVEEAAAABDYNu5RZFYUY0dVpST1NPU2JIRWlzMEhxM29RAAAAAAHPMhIWbDhLbkRGMGVRMktlNmVic3hmTTZQdwAAAAEIC84XFjNnYkNhamRMUWdpRDhJRlpTR3l5c2cAAAAA9Bb5phZSV1k3T3ZuT1FZV055ODZLandweUhnAAAAAAatpZUWdGlIUjh5MEtSX0NMaGFpZlRGaWdZZwAAAAENbfO-FlhfN3R5NkM3UWd1c0tKd25XXzh3MVEAAAAA9Bb5pxZSV1k3T3ZuT1FZV055ODZLandweUhnAAAAAQ2DbuYWRWFGNHVaUk9TT1NiSEVpczBIcTNvUQAAAAAGraWWFnRpSFI4eTBLUl9DTGhhaWZURmlnWWcAAAAArdRfQhZaWkpkNWFEX1J3dXl6THMzVk1uU0pRAAAAAAG3nz8WaFZsQi1hVGVRakNsWXUtc3V3dGZJdwAAAAEINqojFmt1T2VDZ0c2UVRxLXY1TXlKRnd0ckEAAAABCAvOGBYzZ2JDYWpkTFFnaUQ4SUZaU0d5eXNnAAAAAQg2qiQWa3VPZUNnRzZRVHEtdjVNeUpGd3RyQQAAAAEOFh5PFlM0MTF4a3hZU0dxVnhuSEZ1Z0p4aVEAAAAAAc8yExZsOEtuREYwZVEyS2U2ZWJzeGZNNlB3AAAAAQ3CTCoWckRCLXNaX2NUb3k5VVgtamRyMnlLZwAAAAADfUxqFjBwTWJSVEJRVGE2ZDRlaEhKN0VEVEEAAAABCGR0zhZUb0Z1eWpaLVJOcW1McUZsLVF0TnZ3AAAAAQ1t878WWF83dHk2QzdRZ3VzS0p3bldfOHcxUQAAAAENwkwrFnJEQi1zWl9jVG95OVVYLWpkcjJ5S2cAAAABDbrdJRZQdmFPTXJ0RFJEbUs1UkhjNmozZ0tnAAAAAQnn5FAWeEQ0bU12YU5TZ3FVMV9FRmJteGFNUQAAAAENwkwsFnJEQi1zWl9jVG95OVVYLWpkcjJ5S2cAAAABDZEgehZRck5Ddnd1RFFDS3J0cGtTaE43cUR3AAAAAK3UX1UWWlpKZDVhRF9Sd3V5ekxzM1ZNblNKUQAAAAENjv2hFm9RdWllMnJsVEJhWlA2alRJR3d6TFEAAAABDbrdJhZQdmFPTXJ0RFJEbUs1UkhjNmozZ0tnAAAAAQhkdM8WVG9GdXlqWi1STnFtTHFGbC1RdE52dwAAAAACtVXxFkx3cE1IQmoyVGZPUk1iMjdET1ZFREEAAAABDZV-yBY3UzVQRkxhZlFDS09ycmRISmVpcjV3AAAAAAIO8osWbm5KMlRwNklTZENsY3p5NWVmZ3JYdwAAAAENg27nFkVhRjR1WlJPU09TYkhFaXMwSHEzb1EAAAAA87q5RBZicGgwSHR3OFRwdTlGeGcwYm51MWNRAAAAAAIO8owWbm5KMlRwNklTZENsY3p5NWVmZ3JYdwAAAADzurlFFmJwaDBIdHc4VHB1OUZ4ZzBibnUxY1EAAAAAAg7yjRZubkoyVHA2SVNkQ2xjenk1ZWZnclh3AAAAAQhkdNAWVG9GdXlqWi1STnFtTHFGbC1RdE52dwAAAAENh2D5FlBRSmM1Q3l4VG5LS0RhY0hDVXB2SncAAAABDauQZxZDYVMxdEdiQVMzS2liS0FCSEkxcElB\",\"took\":937,\"timed_out\":false,\"_shards\":{\"total\":50,\"successful\":50,\"failed\":0},\"hits\":{\"total\":1,\"max_score\":0,\"hits\":[{\"_index\":\"metadata_index\",\"_type\":\"metadata_type\",\"_id\":\"e199fa2a0f00da90fec8c1eb543442b0\",\"_score\":0,\"_source\":{\"scope\":\"ajna.consumer\",\"metric\":\"datapoints.posted\",\"tagk\":\"uuid\",\"tagv\":\"shared1-argusmetrics1-5-prd.eng.sfdc.net9047\",\"mts\":1555112561350,\"ets\":1559000561350}}]}}";
        ObjectMapper mapper = new ObjectMapper();
        ElasticSearchSchemaService service = spy(_esSchemaService);
        restClient =  mock(RestClient.class);
        service.setRestClient(restClient);
        doAnswer(new Answer() {
            int callCount = 0;
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                if (callCount == 1) {
                    StringEntity requestBody = invocationOnMock.getArgument(3, StringEntity.class);
                    String jsonStr = EntityUtils.toString(requestBody);
                    JsonNode tree = mapper.readTree(jsonStr);
                    assertNotNull(tree.get("scroll"));
                    assertNotNull(tree.get("scroll_id"));
                }
                callCount++;
                return null;
            }
        }).when(restClient).performRequest(any(), any(), any(), any(HttpEntity.class));
        doAnswer(invoction -> reply).when(service).extractResponse(any());
        MetricSchemaRecordQuery query = new MetricSchemaRecordQuery.MetricSchemaRecordQueryBuilder().scope("system*")
                .metric("*")
                .tagKey("*")
                .tagValue("*")
                .namespace("*")
                .limit(10001)
                .build();
        service.get(query);
        verify(restClient, times(2)).performRequest(any(), any(), any(), any(HttpEntity.class));
    }


    @Test
    public void testGetUniqueUsingScopeSchemaIndex() throws IOException {

        MetricSchemaRecordQuery queryForScope = new MetricSchemaRecordQuery.MetricSchemaRecordQueryBuilder().scope("system*")
                .metric("*")
                .tagKey("*")
                .tagValue("*")
                .namespace("*")
                .limit(2)
                .build();

        SchemaService.RecordType scopeType = SchemaService.RecordType.SCOPE;

        MonitorService mockedMonitor = mock(MonitorService.class);

        ElasticSearchSchemaService spyService = _initializeSpyService(_esSchemaService, getReply, getReply);

        spyService.getUnique(queryForScope, scopeType);

        ArgumentCaptor<String> requestUrlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<StringEntity> queryJsonCaptor = ArgumentCaptor.forClass(StringEntity.class);

        verify(restClient, times(1)).performRequest(any(), requestUrlCaptor.capture(), any(), queryJsonCaptor.capture());

        String requestUrl = requestUrlCaptor.getValue();
        String queryJson = convertToPrettyJson(EntityUtils.toString(queryJsonCaptor.getValue()));

        assertEquals(scopeQuery, queryJson);
        assertEquals("/scopenames/scope_type/_search", requestUrl);

        assertTrue(queryForScope.isQueryOnlyOnScope());
        assertTrue(queryForScope.isQueryOnlyOnScopeAndMetric());
    }

    @Test
    public void testGetUniqueUsingMetricTagvRegexSchemaIndex() throws IOException {

        MetricSchemaRecordQuery queryForMetric = new MetricSchemaRecordQuery.MetricSchemaRecordQueryBuilder().scope("system")
                .metric("argus")
                .tagKey("device")
                .tagValue("abc*")
                .namespace("*")
                .limit(2)
                .build();

        SchemaService.RecordType scopeType = SchemaService.RecordType.TAGV;


        ElasticSearchSchemaService spyService = _initializeSpyService(_esSchemaService, getReply, getReply);

        spyService.getUnique(queryForMetric, scopeType);

        ArgumentCaptor<String> requestUrlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<StringEntity> queryJsonCaptor = ArgumentCaptor.forClass(StringEntity.class);

        verify(restClient, times(1)).performRequest(any(), requestUrlCaptor.capture(), any(), queryJsonCaptor.capture());

        String requestUrl = requestUrlCaptor.getValue();
        String queryJson = convertToPrettyJson(EntityUtils.toString(queryJsonCaptor.getValue()));

        assertEquals(metricQueryTagvRegex, queryJson);
        assertEquals("/metadata_index/metadata_type/_search", requestUrl);

        assertFalse(queryForMetric.isQueryOnlyOnScope());
        assertFalse(queryForMetric.isQueryOnlyOnScopeAndMetric());
    }

    @Test
    public void testGetUniqueUsingMetricNamespaceRegexSchemaIndex() throws IOException {

        MetricSchemaRecordQuery queryForMetric = new MetricSchemaRecordQuery.MetricSchemaRecordQueryBuilder().scope("system")
                .metric("argus")
                .tagKey("*")
                .tagValue("*")
                .namespace("common*")
                .limit(2)
                .build();

        SchemaService.RecordType scopeType = SchemaService.RecordType.NAMESPACE;

        ElasticSearchSchemaService spyService = _initializeSpyService(_esSchemaService, getReply, getReply);

        spyService.getUnique(queryForMetric, scopeType);

        ArgumentCaptor<String> requestUrlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<StringEntity> queryJsonCaptor = ArgumentCaptor.forClass(StringEntity.class);

        verify(restClient, times(1)).performRequest(any(), requestUrlCaptor.capture(), any(), queryJsonCaptor.capture());

        String requestUrl = requestUrlCaptor.getValue();
        String queryJson = convertToPrettyJson(EntityUtils.toString(queryJsonCaptor.getValue()));

        assertEquals(metricQueryNamespaceRegex, queryJson);
        assertEquals("/metadata_index/metadata_type/_search", requestUrl);

        assertFalse(queryForMetric.isQueryOnlyOnScope());
        assertFalse(queryForMetric.isQueryOnlyOnScopeAndMetric());
    }

    @Test
    public void testKeywordSearchWithQueryStringWithScroll() throws IOException {
        ElasticSearchSchemaService service = spy(_esSchemaService);
        restClient =  mock(RestClient.class);
        service.setRestClient(restClient);
        doAnswer(new Answer() {
            int callCount = 0;
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                callCount++;
                if (callCount <= 1) {
                    return "{\"tokens\":[{\"token\":\"text\",\"start_offset\":0,\"end_offset\":4,\"type\":\"word\",\"position\":0}]}";
                } else {
                    return "{\"took\":711,\"timed_out\":false,\"_shards\":{\"total\":50,\"successful\":50,\"failed\":0},\"hits\":{\"total\":0,\"max_score\":null,\"hits\":[]}}";
                }
            }
        }).when(service).extractResponse(any());
        KeywordQuery query = new KeywordQuery.KeywordQueryBuilder()
                .query("text*")
                .limit(10001)
                .build();
        service.keywordSearch(query);
        // 1 time for token analysis, 1 for first scroll call, 1 more for /_search/scroll
        verify(restClient, times(3)).performRequest(any(), any(), any(), any(HttpEntity.class));
    }

    @Test
    public void testKeywordSearchWithType() throws IOException {
        ElasticSearchSchemaService service = spy(_esSchemaService);
        restClient =  mock(RestClient.class);
        service.setRestClient(restClient);
        doAnswer(new Answer() {
            int callCount = 0;
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                // First 5 calls are analyzeToken REST calls
                callCount++;
                if (callCount <= 5) {
                    return "{\"tokens\":[{\"token\":\"text\",\"start_offset\":0,\"end_offset\":4,\"type\":\"word\",\"position\":0}]}";
                } else {
                    return "{\"took\":3939,\"timed_out\":false,\"_shards\":{\"total\":50,\"successful\":50,\"failed\":0},\"hits\":{\"total\":0,\"max_score\":0,\"hits\":[]},\"aggregations\":{\"distinct_values\":{\"doc_count_error_upper_bound\":0,\"sum_other_doc_count\":0,\"buckets\":[]}}}";
                }
            }
        }).when(service).extractResponse(any());
        KeywordQuery query = new KeywordQuery.KeywordQueryBuilder().scope("text*")
                .metric("text*")
                .tagKey("text*")
                .tagValue("text*")
                .namespace("text*")
                .type(SchemaService.RecordType.METRIC)
                .limit(10001)
                .build();
        service.keywordSearch(query);
        verify(restClient, times(6)).performRequest(any(), any(), any(), any(HttpEntity.class));
    }

	@Test
	public void testUpsertWhenAllNewDocsShouldNotUpdateMTSField() throws IOException {
		String esCreateResponse=String.join("\n", "{" +
				"  \"took\": 127," +
				"  \"errors\": false," +
				"  \"items\": [" +
				"    {" +
				"      \"create\": {" +
				"        \"_index\": \"metadata_index\"," +
				"        \"_type\": \"metadata_type\"," +
				"        \"_id\": \"1\"," +
				"        \"_version\": 1," +
				"        \"result\": \"created\"," +
				"        \"_shards\": {" +
				"          \"total\": 2," +
				"          \"successful\": 1," +
				"          \"failed\": 0" +
				"        }," +
				"        \"created\": true," +
				"        \"status\": 201" +
				"      }" +
				"    }," +
				"    {" +
				"      \"create\": {" +
				"        \"_index\": \"metadata_index\"," +
				"        \"_type\": \"metadata_type\"," +
				"        \"_id\": \"2\"," +
				"        \"_version\": 1," +
				"        \"result\": \"created\"," +
				"        \"_shards\": {" +
				"          \"total\": 2," +
				"          \"successful\": 1," +
				"          \"failed\": 0" +
				"        }," +
				"        \"created\": true," +
				"        \"status\": 201" +
				"      }" +
				"    }" +
				"  ]" +
				"}");
		ElasticSearchSchemaService spySchemaService = spy(_esSchemaService);
		RestClient _restClient = mock(RestClient.class);
		doReturn(null).when(_restClient).performRequest(any(), any(), any(),any());
		spySchemaService.setRestClient(_restClient);
		doReturn(esCreateResponse).when(spySchemaService).extractResponse(any());

		List<Metric> metrics = new ArrayList<>();
		Metric m1= new Metric("scope1", "metric1");
		Metric m2= new Metric("scope2", "metric2");
		metrics.add(m1);
		metrics.add(m2);
		spySchemaService.put(metrics);
		verify(spySchemaService, never()).updateMetadataRecordMts(any());
	}

	@Test
	public void testRealUpsertWithOne409() throws IOException {
		String esCreateResponse = String.join("\n", "{" +
				"  \"took\": 5," +
				"  \"errors\": true," +
				"  \"items\": [" +
				"    {" +
				"      \"create\": {" +
				"        \"_index\": \"metadata_index\"," +
				"        \"_type\": \"metadata_type\"," +
				"        \"_id\": \"dd123151c817644189a2d28757b5be8a\"," +
				"        \"status\": 409," +
				"        \"error\": {" +
				"          \"type\": \"version_conflict_engine_exception\"," +
				"          \"reason\": \"[metadata_type][dd123151c817644189a2d28757b5be8a]: version conflict, document already exists (current version [1])\"," +
				"          \"index_uuid\": \"lFrI7n47Sp-rpmuyqvhWvw\"," +
				"          \"shard\": \"2\"," +
				"          \"index\": \"metadata_index\"" +
				"        }" +
				"      }" +
				"    }," +
				"    {" +
				"      \"create\": {" +
				"        \"_index\": \"metadata_index\"," +
				"        \"_type\": \"metadata_type\"," +
				"        \"_id\": \"2\"," +
				"        \"_version\": 1," +
				"        \"result\": \"created\"," +
				"        \"_shards\": {" +
				"          \"total\": 2," +
				"          \"successful\": 1," +
				"          \"failed\": 0" +
				"        }," +
				"        \"created\": true," +
				"        \"status\": 201" +
				"      }" +
				"    }" +
				"  ]" +
				"}");
		String updateResponse = "{\"took\":1,\"errors\":false,\"items\":[{\"update\":{\"_index\":\"metadata_index\",\"_type\":\"metadata_type\",\"_id\":\"dd123151c817644189a2d28757b5be8a\",\"_version\":2,\"result\":\"updated\",\"_shards\":{\"total\":2,\"successful\":2,\"failed\":0},\"status\":200}}]}";
		String scopeCreateResponse = "{\"took\":1,\"errors\":true,\"items\":[{\"create\":{\"_index\":\"scopenames\",\"_type\":\"scope_type\",\"_id\":\"fbeb48e92a72df5f9844d2ecd4d1e825\",\"status\":409,\"error\":{\"type\":\"version_conflict_engine_exception\",\"reason\":\"[scope_type][fbeb48e92a72df5f9844d2ecd4d1e825]: version conflict, document already exists (current version [1])\",\"index_uuid\":\"fbq7sEAmQm6aMSH7z0Ij5A\",\"shard\":\"1\",\"index\":\"scopenames\"}}},{\"create\":{\"_index\":\"scopenames\",\"_type\":\"scope_type\",\"_id\":\"e7ccd95462e7696b26349360b709a1d7\",\"_version\":1,\"result\":\"created\",\"_shards\":{\"total\":2,\"successful\":1,\"failed\":0},\"created\":true,\"status\":201}}]}";
		String scopeUpdateResopnse = "{\"took\":2,\"errors\":false,\"items\":[{\"update\":{\"_index\":\"scopenames\",\"_type\":\"scope_type\",\"_id\":\"fbeb48e92a72df5f9844d2ecd4d1e825\",\"_version\":2,\"result\":\"updated\",\"_shards\":{\"total\":2,\"successful\":2,\"failed\":0},\"status\":200}}]}";
		ElasticSearchSchemaService spySchemaService = spy(_esSchemaService);
		RestClient _restClient = mock(RestClient.class);
		doAnswer(new Answer() {
		    int count = 0;
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                count++;
                // 2nd call: the updateMetadata, and 4th call: the updateScope, should only update one object AKA request body should be 2 lines
                if (count == 2 || count == 4) {
                    StringEntity entity = invocationOnMock.getArgument(1, StringEntity.class);
                    assertTrue(EntityUtils.toString(entity).split("\r\n|\r|\n").length == 2);
                }
                return null;
            }
        }).when(_restClient).performRequest(any(), any(), any(),any());
		spySchemaService.setRestClient(_restClient);
		doAnswer(new Answer() {
		    int count = 0;
            @Override
            public Object answer(InvocationOnMock invocationOnMock) throws Throwable {
                count++;
                if (count == 1) {
                    return esCreateResponse;
                } else if (count == 2) {
                    return updateResponse;
                } else if (count == 3) {
                    return scopeCreateResponse;
                } else {
                    return scopeUpdateResopnse;
                }
            }
        }).when(spySchemaService).extractResponse(any());

		List<Metric> metrics = new ArrayList<>();
		Metric m1= new Metric("scope1", "metric1");
		Metric m2= new Metric("scope2", "metric2");
		metrics.add(m1);
		metrics.add(m2);
		spySchemaService.put(metrics);
		verify(spySchemaService, times(1)).updateMetadataRecordMts(any());
	}

	@Test
	public void testUpsertWhenAllDocsExistShouldUpdateMTSFieldForAllDocs() throws IOException {
		String esCreateResponse=String.join("", "{" +
				"  \"took\": 0," +
				"  \"errors\": true," +
				"  \"items\": [" +
				"    {" +
				"      \"create\": {" +
				"        \"_index\": \"metadata_index\"," +
				"        \"_type\": \"metadata_type\"," +
				"        \"_id\": \"dd123151c817644189a2d28757b5be8a\"," +
				"        \"status\": 409," +
				"        \"error\": {" +
				"          \"type\": \"version_conflict_engine_exception\"," +
				"          \"reason\": \"[metadata_type][dd123151c817644189a2d28757b5be8a]: version conflict, document already exists (current version [1])\"," +
				"          \"index_uuid\": \"lFrI7n47Sp-rpmuyqvhWvw\"," +
				"          \"shard\": \"2\"," +
				"          \"index\": \"metadata_index\"" +
				"        }" +
				"      }" +
				"    }," +
				"    {" +
				"      \"create\": {" +
				"        \"_index\": \"metadata_index\"," +
				"        \"_type\": \"metadata_type\"," +
				"        \"_id\": \"4f86f5e6dc6d4672830d97de21e75a20\"," +
				"        \"status\": 409," +
				"        \"error\": {" +
				"          \"type\": \"version_conflict_engine_exception\"," +
				"          \"reason\": \"[metadata_type][4f86f5e6dc6d4672830d97de21e75a20]: version conflict, document already exists (current version [1])\"," +
				"          \"index_uuid\": \"lFrI7n47Sp-rpmuyqvhWvw\"," +
				"          \"shard\": \"4\"," +
				"          \"index\": \"metadata_index\"" +
				"        }" +
				"      }" +
				"    }" +
				"  ]" +
				"}");
		ElasticSearchSchemaService spySchemaService = spy(_esSchemaService);
		RestClient _restClient = mock(RestClient.class);
		doReturn(null).when(_restClient).performRequest(any(), any(), any(),any());
		spySchemaService.setRestClient(_restClient);
		doReturn(esCreateResponse).when(spySchemaService).extractResponse(any());
		doReturn(new HashSet<>()).when(spySchemaService).upsertScopeRecords(any());
		doAnswer((Answer<Set<MetricSchemaRecord>>) invocation -> {
            @SuppressWarnings("unchecked")
            Set<MetricSchemaRecord> recordsToUpdate = Set.class.cast(invocation.getArguments()[0]);
            assertTrue(recordsToUpdate.stream().anyMatch(r -> r.getScope().equals("scope1")));
            assertTrue(recordsToUpdate.stream().anyMatch(r -> r.getScope().equals("scope2")));
            assertEquals(2, recordsToUpdate.size());
            return new HashSet<>();
        }).when(spySchemaService).updateMetadataRecordMts(any());

		List<Metric> metrics = new ArrayList<>();
		Metric m1= new Metric("scope1", "metric1");
		Metric m2= new Metric("scope2", "metric2");
		metrics.add(m1);
		metrics.add(m2);
		spySchemaService.put(metrics);
		verify(spySchemaService, times(1)).updateMetadataRecordMts(any());
	}

	@Test
	public void testConstructTagNotEqualsQuery() throws IOException {
        String tagValue = "notTagValue";
        AtomicInteger invocationCount = new AtomicInteger(0);
        ObjectMapper mapper = new ObjectMapper();
        RestClient customClient = mock(RestClient.class);
        Answer<Response> requestAnswer = invocation -> {
            String requestUrl = invocation.getArgument(1, String.class);
            assertTrue(requestUrl.endsWith("_search"));
            StringEntity requestBodyEntity = invocation.getArgument(3, StringEntity.class);
            JsonNode root = mapper.readTree(EntityUtils.toString(requestBodyEntity));
            JsonNode nots = root.get("query").get("bool").get("must_not");
            JsonNode filters = root.get("query").get("bool").get("filter");
            assertEquals(3, filters.size());
            String actualTagValue = nots.get(0).get("regexp").get("tagv.raw").asText();
            assertEquals(tagValue, actualTagValue);
            invocationCount.incrementAndGet();
            return null;
        };
        doAnswer(requestAnswer).when(customClient).performRequest(anyString(), anyString(), anyMap(), any(HttpEntity.class));

        ElasticSearchUtils mockedElasticSearchUtils = mock(ElasticSearchUtils.class);
        ElasticSearchSchemaService schemaService = spy(new ElasticSearchSchemaService(systemConfig,
                                                                                      mock(MonitorService.class),
                                                                                      mockedElasticSearchUtils));
        schemaService.setRestClient(customClient);

        doReturn("{\"hits\":{\"total\": 0, \"max_score\": null, \"hits\": []}}").when(schemaService).extractResponse(any());
        schemaService.setRestClient(customClient);
        schemaService.get(new MetricSchemaRecordQuery.MetricSchemaRecordQueryBuilder()
                .scope("scope")
                .metric("metric")
                .tagKey("tagKey")
                .tagValue("~" + tagValue)
                .build()
        );
        assertEquals(1, invocationCount.get());
    }

    private String convertToPrettyJson(String jsonString) {
        JsonParser parser = new JsonParser();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        JsonElement el = parser.parse(jsonString);
        return gson.toJson(el);
    }

    private ElasticSearchSchemaService _initializeSpyService(ElasticSearchSchemaService service,
                                                             String firstReply, String secondReply) {

        restClient =  mock(RestClient.class);

        service.setRestClient(restClient);

        ElasticSearchSchemaService spyService = spy(service);

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

    @Test
    public void testMetriccSchemaRecordListMapper() throws Exception {
        ObjectMapper mapper = ElasticSearchSchemaService._getMetadataObjectMapper(new MetricSchemaRecordList.CreateSerializer());

        MetricSchemaRecord record1 = new MetricSchemaRecord("namespace1", "scope1", "metric1", "tagK1", "tagV1", 10);
        //MetricSchemaRecord record2 = new MetricSchemaRecord("namespace2", "scope2", "metric2", "tagK2", "tagV2", 10);
        MetricSchemaRecordList recordList = new MetricSchemaRecordList(new HashSet<>(Arrays.asList(record1)), MetricSchemaRecordList.HashAlgorithm.fromString("MD5"));

        String serialized = mapper.writeValueAsString(recordList);

        assertTrue("expect the serialized record to have EXPIRATION_TS", serialized.contains(MetricSchemaRecord.EXPIRATION_TS));
        assertTrue("expect the serialized record to have RETENTION_DISCOVERY", serialized.contains(MetricSchemaRecord.RETENTION_DISCOVERY));
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

        String responseMessage = ElasticSearchSchemaService.doExtractResponse(200, entity);
        assertEquals("expect the entity to be equal after extraction", message, responseMessage);
    }

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Test
    public void testDoExtractResponse400() {
        expectedException.expect(IllegalArgumentException.class);
        expectedException.expectMessage("Status code: 400");
        ElasticSearchSchemaService.doExtractResponse(400, null);
    }

    @Test
    public void testDoExtractResponse500() {
        expectedException.expect(SystemException.class);
        expectedException.expectMessage("Status code: 500");
        ElasticSearchSchemaService.doExtractResponse(500, null);
    }

    @Test
    public void testGetRequestBodyForMtsFieldUpdate() throws IOException {
        ObjectMapper updateMapper = ElasticSearchSchemaService._getMetadataObjectMapper(new MetricSchemaRecordList.UpdateSerializer());
        String expectedRegex = "\\{\"update\":\\{\"_id\":\"a303abc25d534dd8ff97121668e952e6\"\\}\\}\n" +
                "\\{\"doc\":\\{\"mts\":[0-9]+,\"ets\":[0-9]+\\}\\}\n" +
                "\\{\"update\":\\{\"_id\":\"8b7f219c5131eeff5b02a6e798c9ec2d\"\\}\\}\n" +
                "\\{\"doc\":\\{\"mts\":[0-9]+,\"_retention_discovery_\":10,\"ets\":[0-9]+\\}\\}\n";

        MetricSchemaRecord record1 = new MetricSchemaRecord("namespace1", "scope1", "metric1", "tagK1", "tagV1", 10);
        MetricSchemaRecord record2 = new MetricSchemaRecord("namespace2", "scope2", "metric2", "tagK2", "tagV2");   //retention will be the default 45 days


        MetricSchemaRecordList recordList = new MetricSchemaRecordList(new HashSet<>(Arrays.asList(record1, record2)), MetricSchemaRecordList.HashAlgorithm.fromString("MD5"));
        String requestBody = updateMapper.writeValueAsString(recordList);

        assertTrue(requestBody.matches(expectedRegex));
    }
}

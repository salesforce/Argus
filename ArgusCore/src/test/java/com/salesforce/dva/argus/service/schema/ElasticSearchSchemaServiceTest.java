package com.salesforce.dva.argus.service.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.MetatagsRecord;
import com.salesforce.dva.argus.entity.MetricSchemaRecord;
import com.salesforce.dva.argus.entity.MetricSchemaRecordQuery;
import com.salesforce.dva.argus.entity.ScopeAndMetricOnlySchemaRecord;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.system.SystemException;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.RestClient;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.*;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.HashMap;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.*;

import static org.junit.Assert.assertTrue;

public class ElasticSearchSchemaServiceTest extends AbstractTest {

    private RestClient restClient;

    private String createSucessReply = String.join("\n",
    "{" +
            "    \"took\": 178," +
            "    \"errors\": false," +
            "    \"items\": [" +
            "        {" +
            "            \"create\": {" +
            "                \"_index\": \"scopemetricnames\"," +
            "                \"_type\": \"scopemetric_type\"," +
            "                \"_id\": \"0f56139fa1c2a1834405bffd8e4570f1\"," +
            "                \"_version\": 1," +
            "                \"result\": \"created\"," +
            "                \"_shards\": {" +
            "                    \"total\": 2," +
            "                    \"successful\": 2," +
            "                    \"failed\": 0" +
            "                }," +
            "                \"created\": true," +
            "                \"status\": 201" +
            "            }" +
            "        }," +
            "        {" +
            "            \"create\": {" +
            "                \"_index\": \"scopemetricnames\"," +
            "                \"_type\": \"scopemetric_type\"," +
            "                \"_id\": \"5b5d61f40ff3df194cc0e5b2afe0c5b4\"," +
            "                \"_version\": 1," +
            "                \"result\": \"created\"," +
            "                \"_shards\": {" +
            "                    \"total\": 2," +
            "                    \"successful\": 2," +
            "                    \"failed\": 0" +
            "                }," +
            "                \"created\": true," +
            "                \"status\": 201" +
            "            }" +
            "        }" +
            "    ]" +
            "}");


    private String createFailReply = String.join("\n",
            "{",
            "    \"took\": 2," +
            "    \"errors\": true," +
            "    \"items\": [" +
            "        {" +
            "            \"create\": {" +
            "                \"_index\": \"scopemetricnames\"," +
            "                \"_type\": \"scopemetric_type\"," +
            "                \"_id\": \"0f56139fa1c2a1834405bffd8e4570f1\"," +
            "                \"status\": 409," +
            "                \"error\": {" +
            "                    \"type\": \"version_conflict_engine_exception\"," +
            "                    \"reason\": \"[scopemetric_type][9602e82b184a4930c2cf5de4651e0b3b]: version conflict, document already exists (current version [110])\"," +
            "                    \"index_uuid\": \"zxhVd68hTPmEfCWYKtkjSQ\"," +
            "                    \"shard\": \"0\"," +
            "                    \"index\": \"scopemetricnames\"" +
            "                }" +
            "            }" +
            "        }," +
            "        {" +
            "            \"create\": {" +
            "                \"_index\": \"scopemetricnames\"," +
            "                \"_type\": \"scopemetric_type\"," +
            "                \"_id\": \"5b5d61f40ff3df194cc0e5b2afe0c5b4\"," +
            "                \"status\": 409," +
            "                \"error\": {" +
            "                    \"type\": \"version_conflict_engine_exception\"," +
            "                    \"reason\": \"[scopemetric_type][398b3cee85ea47fa673a2fc3ac9970c3]: version conflict, document already exists (current version [110])\"," +
            "                    \"index_uuid\": \"zxhVd68hTPmEfCWYKtkjSQ\"," +
            "                    \"shard\": \"0\"," +
            "                    \"index\": \"scopemetricnames\"" +
            "                }" +
            "            }" +
            "        }" +
            "    ]" +
            "}");

    private String updateSucessReply = String.join("\n",
            "{",
            "    \"took\": 2," +
            "    \"errors\": false," +
            "    \"items\": [" +
            "        {" +
            "            \"update\": {" +
            "                \"_index\": \"scopemetricnames\"," +
            "                \"_type\": \"scopemetric_type\"," +
            "                \"_id\": \"0f56139fa1c2a1834405bffd8e4570f1\"," +
            "                \"_version\": 87," +
            "                \"result\": \"noop\"," +
            "                \"_shards\": {" +
            "                    \"total\": 2," +
            "                    \"successful\": 2," +
            "                    \"failed\": 0" +
            "                }," +
            "                \"status\": 200" +
            "            }" +
            "        }," +
            "        {" +
            "            \"update\": {" +
            "                \"_index\": \"scopemetricnames\"," +
            "                \"_type\": \"scopemetric_type\"," +
            "                \"_id\": \"5b5d61f40ff3df194cc0e5b2afe0c5b4\"," +
            "                \"_version\": 87," +
            "                \"result\": \"noop\"," +
            "                \"_shards\": {" +
            "                    \"total\": 2," +
            "                    \"successful\": 2," +
            "                    \"failed\": 0" +
            "                }," +
            "                \"status\": 200" +
            "            }" +
            "        }" +
            "    ]" +
            "}");

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

    private String scopeAndMetricQuery = String.join("\n",
            "{",
            "  \"query\": {",
            "    \"bool\": {",
            "      \"filter\": [",
            "        {",
            "          \"regexp\": {",
            "            \"metric.raw\": \"argus.*\"",
            "          }",
            "        },",
            "        {",
            "          \"regexp\": {",
            "            \"scope.raw\": \"system\"",
            "          }",
            "        }",
            "      ]",
            "    }",
            "  },",
            "  \"size\": 0,",
            "  \"aggs\": {",
            "    \"distinct_values\": {",
            "      \"terms\": {",
            "        \"field\": \"metric.raw\",",
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

    @Test
    public void testPutCreateUsingScopeAndMetricSchemaIndex() throws IOException {

        List<Metric> metrics = new ArrayList<>();

        for(char ch = 'a'; ch < 'l'; ch++) {
            metrics.add(new Metric("scope" + ch, "metric" + ch));
        }
        MonitorService mockedMonitor = mock(MonitorService.class);
        ElasticSearchSchemaService service = new ElasticSearchSchemaService(system.getConfiguration(), mockedMonitor);

        ElasticSearchSchemaService spyService = _initializeSpyService(service, createSucessReply, createSucessReply);

        List<ScopeAndMetricOnlySchemaRecord> records = new ArrayList<>();

        for(Metric m : metrics) {
            ScopeAndMetricOnlySchemaRecord msr = new ScopeAndMetricOnlySchemaRecord(m.getScope(), m.getMetric());
            records.add(msr);
        }

        spyService.upsertScopeAndMetrics(records);

        ArgumentCaptor<String> requestUrlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<StringEntity> createJsonCaptor = ArgumentCaptor.forClass(StringEntity.class);

        verify(restClient, times(1)).performRequest(any(), requestUrlCaptor.capture(), any(), createJsonCaptor.capture());

        String requestUrl = requestUrlCaptor.getValue();
        String createJson = EntityUtils.toString(createJsonCaptor.getValue());

        assertTrue(createJson.contains("create"));
        assertFalse(createJson.contains("update"));
        assertEquals(StringUtils.countMatches(createJson, "cts"), 11);
        assertEquals(StringUtils.countMatches(createJson, "mts"), 11);
        assertEquals("/scopemetricnames/scopemetric_type/_bulk", requestUrl);
    }

    @Test
    public void testPutUpdateUsingScopeAndMetricSchemaIndex() throws IOException {

        List<Metric> metrics = new ArrayList<>();

        for(char ch = 'a'; ch < 'l'; ch++) {
            metrics.add(new Metric("scope" + ch, "metric" + ch));
        }

        MonitorService mockedMonitor = mock(MonitorService.class);
        ElasticSearchSchemaService service = new ElasticSearchSchemaService(system.getConfiguration(), mockedMonitor);

        ElasticSearchSchemaService spyService = _initializeSpyService(service, createFailReply, updateSucessReply);

        List<ScopeAndMetricOnlySchemaRecord> records = new ArrayList<>();

        for(Metric m : metrics) {
            ScopeAndMetricOnlySchemaRecord msr = new ScopeAndMetricOnlySchemaRecord(m.getScope(), m.getMetric());
            records.add(msr);
        }

        spyService.upsertScopeAndMetrics(records);

        ArgumentCaptor<String> requestUrlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<StringEntity> createJsonCaptor = ArgumentCaptor.forClass(StringEntity.class);

        verify(restClient, times(2)).performRequest(any(), requestUrlCaptor.capture(), any(), createJsonCaptor.capture());

        List<String> requestUrls = requestUrlCaptor.getAllValues();
        List<StringEntity> createJsonEntities = createJsonCaptor.getAllValues();

        List<String> createJsons = new ArrayList<>();

        for(StringEntity createJsonEntity : createJsonEntities) {
            createJsons.add(EntityUtils.toString(createJsonEntity));
        }

        assertTrue(createJsons.get(0).contains("create"));
        assertTrue(createJsons.get(1).contains("update"));

        assertEquals(StringUtils.countMatches(createJsons.get(0), "cts"), 11);
        assertEquals(StringUtils.countMatches(createJsons.get(0), "mts"), 11);

        assertEquals(StringUtils.countMatches(createJsons.get(1), "cts"), 0);
        assertEquals(StringUtils.countMatches(createJsons.get(1), "mts"), 2);

        assertEquals("/scopemetricnames/scopemetric_type/_bulk", requestUrls.get(0));
        assertEquals("/scopemetricnames/scopemetric_type/_bulk", requestUrls.get(1));
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

        MonitorService mockedMonitor = mock(MonitorService.class);
        ElasticSearchSchemaService service = new ElasticSearchSchemaService(system.getConfiguration(), mockedMonitor);

        ElasticSearchSchemaService spyService = _initializeSpyService(service, createSucessReply, createSucessReply);

        List<MetatagsRecord> records = new ArrayList<>();

        for(Metric m : metrics) {
            MetatagsRecord msr = new MetatagsRecord(m.getMetatagsRecord().getMetatags(), m.getMetatagsRecord().getKey());
            records.add(msr);
        }

        spyService.upsertMetatags(records);

        ArgumentCaptor<String> requestUrlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<StringEntity> createJsonCaptor = ArgumentCaptor.forClass(StringEntity.class);

        verify(restClient, times(1)).performRequest(any(), requestUrlCaptor.capture(), any(), createJsonCaptor.capture());

        String requestUrl = requestUrlCaptor.getValue();
        String createJson = EntityUtils.toString(createJsonCaptor.getValue());

        assertTrue(createJson.contains("create"));
        assertFalse(createJson.contains("update"));
        assertTrue(createJson.contains("cts"));
        assertTrue(createJson.contains("mts"));
        assertEquals("/metatags/metatags_type/_bulk", requestUrl);
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
        ElasticSearchSchemaService service = new ElasticSearchSchemaService(system.getConfiguration(), mockedMonitor);

        ElasticSearchSchemaService spyService = _initializeSpyService(service, getReply, getReply);

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
    public void testGetUniqueUsingScopeAndMetricSchemaIndex() throws IOException {

        MetricSchemaRecordQuery queryForMetric = new MetricSchemaRecordQuery.MetricSchemaRecordQueryBuilder().scope("system")
                .metric("argus*")
                .tagKey("*")
                .tagValue("*")
                .namespace("*")
                .limit(2)
                .build();

        SchemaService.RecordType scopeType = SchemaService.RecordType.METRIC;

        MonitorService mockedMonitor = mock(MonitorService.class);
        ElasticSearchSchemaService service = new ElasticSearchSchemaService(system.getConfiguration(), mockedMonitor);

        ElasticSearchSchemaService spyService = _initializeSpyService(service, getReply, getReply);

        spyService.getUnique(queryForMetric, scopeType);

        ArgumentCaptor<String> requestUrlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<StringEntity> queryJsonCaptor = ArgumentCaptor.forClass(StringEntity.class);

        verify(restClient, times(1)).performRequest(any(), requestUrlCaptor.capture(), any(), queryJsonCaptor.capture());

        String requestUrl = requestUrlCaptor.getValue();
        String queryJson = convertToPrettyJson(EntityUtils.toString(queryJsonCaptor.getValue()));

        assertEquals(scopeAndMetricQuery, queryJson);
        assertEquals("/scopemetricnames/scopemetric_type/_search", requestUrl);

        assertFalse(queryForMetric.isQueryOnlyOnScope());
        assertTrue(queryForMetric.isQueryOnlyOnScopeAndMetric());
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

        MonitorService mockedMonitor = mock(MonitorService.class);
        ElasticSearchSchemaService service = new ElasticSearchSchemaService(system.getConfiguration(), mockedMonitor);

        ElasticSearchSchemaService spyService = _initializeSpyService(service, getReply, getReply);

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

        MonitorService mockedMonitor = mock(MonitorService.class);
        ElasticSearchSchemaService service = new ElasticSearchSchemaService(system.getConfiguration(), mockedMonitor);
        ElasticSearchSchemaService spyService = _initializeSpyService(service, getReply, getReply);

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
        MonitorService mockedMonitor = mock(MonitorService.class);
		ElasticSearchSchemaService schemaService = new ElasticSearchSchemaService(system.getConfiguration(), mockedMonitor);
		ElasticSearchSchemaService spySchemaService = spy(schemaService);
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
		verify(spySchemaService, never()).updateMtsField(any(), any(), any(), any());
	}

	@Test
	public void testUpsertWhenSomeDocsExistShouldUpdateMTSFieldForExistingDocs() throws IOException {
		String esCreateResponse=String.join("\n", "{" +
				"  \"took\": 5," +
				"  \"errors\": true," +
				"  \"items\": [" +
				"    {" +
				"      \"create\": {" +
				"        \"_index\": \"metadata_index\"," +
				"        \"_type\": \"metadata_type\"," +
				"        \"_id\": \"1\"," +
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
        MonitorService mockedMonitor = mock(MonitorService.class);
		ElasticSearchSchemaService schemaService = new ElasticSearchSchemaService(system.getConfiguration(), mockedMonitor);
		ElasticSearchSchemaService spySchemaService = spy(schemaService);
		RestClient _restClient = mock(RestClient.class);
		doReturn(null).when(_restClient).performRequest(any(), any(), any(),any());
		spySchemaService.setRestClient(_restClient);
		doReturn(esCreateResponse).when(spySchemaService).extractResponse(any());
		doNothing().when(spySchemaService).upsertScopeAndMetrics(any());
		doNothing().when(spySchemaService).upsertScopes(any());
		doAnswer(new Answer<Void>() {
			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				@SuppressWarnings("unchecked")
				List<String> updateDocIds = List.class.cast(invocation.getArguments()[0]);
				assertEquals("1", updateDocIds.get(0));
				assertEquals(1, updateDocIds.size());
				return null;
			}
		}).when(spySchemaService).updateMtsField(any(), any(), any(), any());

		List<Metric> metrics = new ArrayList<>();
		Metric m1= new Metric("scope1", "metric1");
		Metric m2= new Metric("scope2", "metric2");
		metrics.add(m1);
		metrics.add(m2);
		spySchemaService.put(metrics);
		verify(spySchemaService, times(1)).updateMtsField(any(), any(), any(), any());
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
				"        \"_id\": \"1\"," +
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
        MonitorService mockedMonitor = mock(MonitorService.class);
		ElasticSearchSchemaService schemaService = new ElasticSearchSchemaService(system.getConfiguration(), mockedMonitor);
		ElasticSearchSchemaService spySchemaService = spy(schemaService);
		RestClient _restClient = mock(RestClient.class);
		doReturn(null).when(_restClient).performRequest(any(), any(), any(),any());
		spySchemaService.setRestClient(_restClient);
		doReturn(esCreateResponse).when(spySchemaService).extractResponse(any());
		doNothing().when(spySchemaService).upsertScopeAndMetrics(any());
		doNothing().when(spySchemaService).upsertScopes(any());
		doAnswer(new Answer<Void>() {
			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				@SuppressWarnings("unchecked")
				List<String> updateDocIds = List.class.cast(invocation.getArguments()[0]);
				assertEquals("1", updateDocIds.get(0));
				assertEquals("2", updateDocIds.get(1));
				assertEquals(2, updateDocIds.size());
				return null;
			}
		}).when(spySchemaService).updateMtsField(any(), any(), any(), any());

		List<Metric> metrics = new ArrayList<>();
		Metric m1= new Metric("scope1", "metric1");
		Metric m2= new Metric("scope2", "metric2");
		metrics.add(m1);
		metrics.add(m2);
		spySchemaService.put(metrics);
		verify(spySchemaService, times(1)).updateMtsField(any(), any(), any(), any());
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

        service.enableScopeMetricNamesIndex();

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
        ObjectMapper mapper = ElasticSearchSchemaService.createObjectMapper();

        MetricSchemaRecord record1 = new MetricSchemaRecord("namespace1", "scope1", "metric1", "tagK1", "tagV1", 10);
        //MetricSchemaRecord record2 = new MetricSchemaRecord("namespace2", "scope2", "metric2", "tagK2", "tagV2", 10);
        MetricSchemaRecordList recordList = new MetricSchemaRecordList(Arrays.asList(record1), MetricSchemaRecordList.HashAlgorithm.fromString("MD5"));

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
    public void testGetRequestBodyForMtsFieldUpdate() {
        String expected = "{\"update\" : {\"_id\" : \"a303abc25d534dd8ff97121668e952e6\" } }\n" +
                "{\"doc\" : {\"mts\": 0,\"ets\":3888000000}}\n" +
                "{\"update\" : {\"_id\" : \"8b7f219c5131eeff5b02a6e798c9ec2d\" } }\n" +
                "{\"doc\" : {\"mts\": 0,\"ets\":864000000,\"_retention_discovery_\":10}}\n";

        MetricSchemaRecord record1 = new MetricSchemaRecord("namespace1", "scope1", "metric1", "tagK1", "tagV1", 10);
        MetricSchemaRecord record2 = new MetricSchemaRecord("namespace2", "scope2", "metric2", "tagK2", "tagV2");   //retention will be the default 45 days


        MetricSchemaRecordList recordList = new MetricSchemaRecordList(Arrays.asList(record1, record2), MetricSchemaRecordList.HashAlgorithm.fromString("MD5"));

        String requestBody = ElasticSearchSchemaService._getRequestBodyForMtsFieldUpdate(Arrays.asList("a303abc25d534dd8ff97121668e952e6", "8b7f219c5131eeff5b02a6e798c9ec2d"), recordList, 0);

        assertEquals("the update request body is different", expected, requestBody);
    }
}

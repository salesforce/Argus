package com.salesforce.dva.argus.service.schema;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.RestClient;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.MetricSchemaRecordQuery;
import com.salesforce.dva.argus.service.SchemaService;

public class ElasticSearchSchemaServiceTest extends AbstractTest {

	private RestClient restClient;

	private String reply = String.join("\n",
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
	public void testGetUniqueUsingScopeSchemaIndex() throws IOException {

		MetricSchemaRecordQuery queryForScope = new MetricSchemaRecordQuery.MetricSchemaRecordQueryBuilder().scope("system*")
				.metric("*")
				.tagKey("*")
				.tagValue("*")
				.namespace("*")
				.limit(2)
				.build();

		SchemaService.RecordType scopeType = SchemaService.RecordType.SCOPE;

		ElasticSearchSchemaService service = new ElasticSearchSchemaService(system.getConfiguration(), system.getServiceFactory().getMonitorService());

		ElasticSearchSchemaService spyService = _initializeSpyService(service, reply);

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

		ElasticSearchSchemaService service = new ElasticSearchSchemaService(system.getConfiguration(), system.getServiceFactory().getMonitorService());

		ElasticSearchSchemaService spyService = _initializeSpyService(service, reply);

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

		ElasticSearchSchemaService service = new ElasticSearchSchemaService(system.getConfiguration(), system.getServiceFactory().getMonitorService());

		ElasticSearchSchemaService spyService = _initializeSpyService(service, reply);

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

		ElasticSearchSchemaService service = new ElasticSearchSchemaService(system.getConfiguration(), system.getServiceFactory().getMonitorService());

		ElasticSearchSchemaService spyService = _initializeSpyService(service, reply);

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
		ElasticSearchSchemaService schemaService = new ElasticSearchSchemaService(system.getConfiguration(), system.getServiceFactory().getMonitorService());
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
		verify(spySchemaService, never()).updateMtsField(any(), any(), any());
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
		ElasticSearchSchemaService schemaService = new ElasticSearchSchemaService(system.getConfiguration(), system.getServiceFactory().getMonitorService());
		ElasticSearchSchemaService spySchemaService = spy(schemaService);
		RestClient _restClient = mock(RestClient.class);
		doReturn(null).when(_restClient).performRequest(any(), any(), any(),any());
		spySchemaService.setRestClient(_restClient);
		doReturn(esCreateResponse).when(spySchemaService).extractResponse(any());
		
		doAnswer(new Answer<Void>() {
			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				@SuppressWarnings("unchecked")
				List<String> updateDocIds = List.class.cast(invocation.getArguments()[0]);
				assertEquals("1", updateDocIds.get(0));
				assertEquals(1, updateDocIds.size()); 
				return null;
			}
		}).when(spySchemaService).updateMtsField(any(), any(), any());
	
		List<Metric> metrics = new ArrayList<>();
		Metric m1= new Metric("scope1", "metric1");
		Metric m2= new Metric("scope2", "metric2");
		metrics.add(m1);
		metrics.add(m2);
		spySchemaService.put(metrics);
		verify(spySchemaService, times(1)).updateMtsField(any(), any(), any());
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
		ElasticSearchSchemaService schemaService = new ElasticSearchSchemaService(system.getConfiguration(), system.getServiceFactory().getMonitorService());
		ElasticSearchSchemaService spySchemaService = spy(schemaService);
		RestClient _restClient = mock(RestClient.class);
		doReturn(null).when(_restClient).performRequest(any(), any(), any(),any());
		spySchemaService.setRestClient(_restClient);
		doReturn(esCreateResponse).when(spySchemaService).extractResponse(any());
		
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
		}).when(spySchemaService).updateMtsField(any(), any(), any());
	
		List<Metric> metrics = new ArrayList<>();
		Metric m1= new Metric("scope1", "metric1");
		Metric m2= new Metric("scope2", "metric2");
		metrics.add(m1);
		metrics.add(m2);
		spySchemaService.put(metrics);
		verify(spySchemaService, times(1)).updateMtsField(any(), any(), any());
	}
	
	private String convertToPrettyJson(String jsonString) {
		JsonParser parser = new JsonParser();
		Gson gson = new GsonBuilder().setPrettyPrinting().create();

		JsonElement el = parser.parse(jsonString);
		return gson.toJson(el);
	}

	private ElasticSearchSchemaService _initializeSpyService(ElasticSearchSchemaService service, String reply) {

		restClient =  mock(RestClient.class);

		service.setRestClient(restClient);

		service.enableScopeMetricNamesIndex();

		ElasticSearchSchemaService spyService = spy(service);

		doReturn(reply).when(spyService).extractResponse(any());

		return spyService;
	}
}

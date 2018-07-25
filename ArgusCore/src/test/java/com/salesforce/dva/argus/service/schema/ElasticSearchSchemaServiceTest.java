package com.salesforce.dva.argus.service.schema;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.MetricSchemaRecordQuery;
import com.salesforce.dva.argus.service.SchemaService;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.RestClient;
import org.junit.Test;
import org.mockito.*;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.Mockito.*;

import static org.junit.Assert.assertTrue;

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

    private String convertToPrettyJson(String jsonString) {
        JsonParser parser = new JsonParser();
        Gson gson = new GsonBuilder().setPrettyPrinting().create();

        JsonElement el = parser.parse(jsonString);
        return gson.toJson(el);
    }

    private ElasticSearchSchemaService _initializeSpyService(ElasticSearchSchemaService service, String reply) {

        restClient =  mock(RestClient.class);

        service.setRestClient(restClient);

        ElasticSearchSchemaService spyService = spy(service);

        doReturn(reply).when(spyService).extractResponse(any());

        return spyService;
    }
}

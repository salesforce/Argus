package com.salesforce.dva.argus.service.schema;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.MetricSchemaRecordQuery;
import com.salesforce.dva.argus.service.SchemaService;
import org.apache.http.entity.StringEntity;
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

    private String reply = "{\n" +
            "\t\"took\": 1,\n" +
            "\t\"timed_out\": false,\n" +
            "\t\"_shards\": {\n" +
            "\t\t\"total\": 6,\n" +
            "\t\t\"successful\": 6,\n" +
            "\t\t\"failed\": 0\n" +
            "\t},\n" +
            "\t\"hits\": {\n" +
            "\t\t\"total\": 426,\n" +
            "\t\t\"max_score\": 0.0,\n" +
            "\t\t\"hits\": []\n" +
            "\t},\n" +
            "\t\"aggregations\": {\n" +
            "\t\t\"distinct_values\": {\n" +
            "\t\t\t\"doc_count_error_upper_bound\": 0,\n" +
            "\t\t\t\"sum_other_doc_count\": 424,\n" +
            "\t\t\t\"buckets\": [\n" +
            "\t\t\t\t{\n" +
            "\t\t\t\t\t\"key\": \"system.name1\\n\",\n" +
            "\t\t\t\t\t\"doc_count\": 1\n" +
            "\t\t\t\t},\n" +
            "\t\t\t\t{\n" +
            "\t\t\t\t\t\"key\": \"system.name2\\n\",\n" +
            "\t\t\t\t\t\"doc_count\": 1\n" +
            "\t\t\t\t}\n" +
            "\t\t\t]\n" +
            "\t\t}\n" +
            "\t}\n" +
            "}";

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

        assertEquals("/scopenames/scope_type/_search", requestUrl);

        assertTrue(queryForScope.isQueryOnlyOnScope());
    }

    @Test
    public void testGetUniqueUsingMetricSchemaIndex() throws IOException {

        MetricSchemaRecordQuery queryForMetric = new MetricSchemaRecordQuery.MetricSchemaRecordQueryBuilder().scope("system")
                .metric("argus*")
                .tagKey("*")
                .tagValue("*")
                .namespace("*")
                .limit(2)
                .build();

        SchemaService.RecordType scopeType = SchemaService.RecordType.SCOPE;

        ElasticSearchSchemaService service = new ElasticSearchSchemaService(system.getConfiguration(), system.getServiceFactory().getMonitorService());

        ElasticSearchSchemaService spyService = _initializeSpyService(service, reply);

        spyService.getUnique(queryForMetric, scopeType);

        ArgumentCaptor<String> requestUrlCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<StringEntity> queryJsonCaptor = ArgumentCaptor.forClass(StringEntity.class);

        verify(restClient, times(1)).performRequest(any(), requestUrlCaptor.capture(), any(), queryJsonCaptor.capture());

        String requestUrl = requestUrlCaptor.getValue();

        assertEquals("/metadata_index/metadata_type/_search", requestUrl);

        assertFalse(queryForMetric.isQueryOnlyOnScope());
    }

    private ElasticSearchSchemaService _initializeSpyService(ElasticSearchSchemaService service, String reply) {

        restClient =  mock(RestClient.class);

        service.setRestClient(restClient);

        ElasticSearchSchemaService spyService = spy(service);

        doReturn(reply).when(spyService).extractResponse(any());

        return spyService;
    }
}

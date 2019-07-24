package com.salesforce.dva.argus.service.metric.metadata;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesforce.dva.argus.system.SystemConfiguration;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

public class DefaultIDBClientTest {
    private static final String ERROR_RESPONSE = "{\"success\":false,\"message\":\"cluster resource doesn't exist.\"}";
    private static final String EMPTY_DATA = "{\"success\":true,\"data\":[],\"total\":0}";
    private static final String HOST_DATA = "{\"success\":true,\"data\":[{\"@hostJacksonId\":\"91c841fb\",\"versionNumber\":14,\"createdDate\":1445499520410,\"modifiedDate\":1553890165927,\"name\":\"shared1-argushost\",\"assetTag\":\"MXQ533072Z\",\"serialNumber\":\"MXQ533072Z\",\"operationalStatus\":\"ACTIVE\"}],\"total\":1}";
    private static final String CLUSTER_DATA = "{\"success\":true,\"data\":[{\"@clusterJacksonId\":\"fdc75d7b\",\"versionNumber\":12,\"id\":\"d38ca572\",\"createdDate\":1530915730584,\"modifiedDate\":1531437610530,\"name\":\"DFW-SFSTORE\",\"dr\":false,\"operationalStatus\":\"ACTIVE\",\"environment\":\"PRODUCTION\",\"buildType\":\"NEW\",\"clusterType\":\"SFSTORE\"}],\"total\":1}";
    ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testGetHostSuccess() {
        DefaultIDBClient client = spy(new DefaultIDBClient(new SystemConfiguration(new Properties())));
        doAnswer(invocationOnMock -> {
            String requestUrl = invocationOnMock.getArgument(0, String.class);
            assertTrue("Hostname query should not contain any subdomains or TLD in the hostname", !requestUrl.contains(".domain2") && !requestUrl.contains(".domain") && !requestUrl.contains(".tld"));
            return mapper.readTree(HOST_DATA);
        }).when(client).httpGetJson(any());

        IDBFieldQuery query = new IDBFieldQuery(IDBFieldQuery.ResourceType.HOST, "dc1", "hostname.domain2.domain.tld", "operationalStatus");
        String status = client.get(Arrays.asList(query)).get(query).get();
        assertEquals("ACTIVE", status);
    }

    @Test
    public void testGetClusterSuccess() throws IOException {
        DefaultIDBClient client = spy(new DefaultIDBClient(new SystemConfiguration(new Properties())));
        doReturn(mapper.readTree(CLUSTER_DATA)).when(client).httpGetJson(any());

        IDBFieldQuery query = new IDBFieldQuery(IDBFieldQuery.ResourceType.CLUSTER, "dc1", "clusterName", "operationalStatus");
        String fieldValue = client.get(Arrays.asList(query)).get(query).get();
        assertEquals("ACTIVE", fieldValue);

        query = new IDBFieldQuery(IDBFieldQuery.ResourceType.CLUSTER, "dc1", "clusterName", "dr");
        fieldValue = client.get(Arrays.asList(query)).get(query).get();
        assertEquals("false", fieldValue);

        query = new IDBFieldQuery(IDBFieldQuery.ResourceType.CLUSTER, "dc1", "clusterName", "environment");
        fieldValue = client.get(Arrays.asList(query)).get(query).get();
        assertEquals("PRODUCTION", fieldValue);
    }

    @Test
    public void testReturnNullOptionalOnError() throws IOException {
        DefaultIDBClient client = spy(new DefaultIDBClient(new SystemConfiguration(new Properties())));
        doReturn(mapper.readTree(ERROR_RESPONSE)).when(client).httpGetJson(any());
        IDBFieldQuery query = new IDBFieldQuery(IDBFieldQuery.ResourceType.HOST, "dc1", "hostname", "operationalStatus");
        assertFalse(client.get(Arrays.asList(query)).get(query).isPresent());
    }

    @Test
    public void testReturnNullOptionalOnEmptyData() throws IOException {
        DefaultIDBClient client = spy(new DefaultIDBClient(new SystemConfiguration(new Properties())));
        doReturn(mapper.readTree(EMPTY_DATA)).when(client).httpGetJson(any());
        IDBFieldQuery query = new IDBFieldQuery(IDBFieldQuery.ResourceType.HOST, "dc1", "hostname", "operationalStatus");
        assertFalse(client.get(Arrays.asList(query)).get(query).isPresent());
    }
}

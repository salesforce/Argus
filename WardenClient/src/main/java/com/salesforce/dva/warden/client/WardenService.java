package com.salesforce.dva.warden.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.annotations.VisibleForTesting;
import com.salesforce.dva.argus.system.SystemException;
import com.salesforce.dva.warden.client.WardenHttpClient.RequestType;


import com.salesforce.dva.warden.dto.Policy;
import com.salesforce.dva.warden.client.WardenHttpClient.WardenResponse;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

class WardenService
    implements AutoCloseable
{

	private WardenHttpClient httpClient;

    //~ Constructors *********************************************************************************************************************************
    WardenService(WardenHttpClient client) {
        httpClient = client;
    }

    //~ Methods **************************************************************************************************************************************
    /**
     * Returns a new instance of the Argus service configured with 10 second timeouts.
     *
     * @param endpoint The HTTP endpoint for Argus.
     * @param maxConn The number of maximum connections. Must be greater than 0.
     *
     * @return A new instance of the Argus service.
     * @throws java.io.IOException
     */
    public static WardenService getInstance(String endpoint, int maxConn) throws IOException {
        return getInstance(endpoint, maxConn, 10000, 10000);
    }

    /**
     * Returns a new instance of the Argus service.
     *
     * @param endpoint The HTTP endpoint for Argus.
     * @param maxConn The number of maximum connections. Must be greater than 0.
     * @param connTimeout The connection timeout in milliseconds. Must be greater than 0.
     * @param connRequestTimeout The connection request timeout in milliseconds. Must be greater than 0.
     *
     * @return A new instance of the Argus service.
     * @throws java.io.IOException
     */
    public static WardenService getInstance(String endpoint, int maxConn, int connTimeout, int connRequestTimeout) throws IOException {
        WardenHttpClient client = new WardenHttpClient(endpoint, maxConn, connTimeout, connRequestTimeout);
        return new WardenService(client);
    }


    public AuthService getAuthService(){
        return new AuthService (httpClient);
    }

    public PolicyService getPolicyService(){
        return new PolicyService (httpClient);
    }


    public void close() throws IOException {
        httpClient.dispose();
    }
}

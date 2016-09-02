package com.salesforce.dva.warden.client;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.PropertyAccessor;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class WardenService implements AutoCloseable
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

    /**
     * Represents the result of a write operation for annotations and metrics.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public static class PutResult {

        @JsonProperty(value = "Success")
        private final String _successCount;
        @JsonProperty(value = "Error")
        private final String _failCount;
        @JsonProperty(value = "Error Messages")
        private final List<String> _errorMessages;

        /**
         * Creates a new PutResult object.
         *
         * @param successCount  The number of successful writes.
         * @param failCount     The number of failed writes.
         * @param errorMessages The associated descriptive error messages for the failed writes.
         */
        public PutResult(String successCount, String failCount, List<String> errorMessages) {
            _successCount = successCount;
            _failCount = failCount;
            _errorMessages = new ArrayList<>();
            if (errorMessages != null) {
                _errorMessages.addAll(errorMessages);
            }
        }
    }

    /**
     * Base class used for endpoint services.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    static class EndpointService {

        private static final ObjectMapper MAPPER = new ObjectMapper();

        static {
            MAPPER.setVisibility(PropertyAccessor.GETTER, JsonAutoDetect.Visibility.ANY);
            MAPPER.setVisibility(PropertyAccessor.SETTER, JsonAutoDetect.Visibility.ANY);
        }

        private WardenHttpClient _client;

        /**
         * Creates a new EndpointService object.
         *
         * @param   client  The HTTP client for use by the endpoint service.
         *
         * @throws  IllegalArgumentException  If the specified client is null.
         */
        EndpointService(WardenHttpClient client) {
            if (client == null) {
                throw new IllegalArgumentException("The HTTP client cannot be null.");
            }
            _client = client;
        }

        /**
         * De-serializes JSON into the corresponding Java object.
         *
         * @param   <T>   The type of the Java object.
         * @param   json  The JSON to de-serialize.
         * @param   type  The type of the Java object.
         *
         * @return  The resulting Java object.
         *
         * @throws  IOException  If the Java object cannot be constructed from the provided JSON.
         */
        protected <T> T fromJson(String json, Class<T> type) throws IOException {
            return MAPPER.readValue(json, type);
        }

        /**
         * De-serializes JSON into the corresponding Java object.
         *
         * @param   <T>      The type of the Java object.
         * @param   json     The JSON to de-serialize.
         * @param   typeRef  The type of the Java object.
         *
         * @return  The resulting Java object.
         *
         * @throws  IOException  If the Java object cannot be constructed from the provided JSON.
         */
        protected <T> T fromJson(String json, TypeReference typeRef) throws IOException {
            return (T) MAPPER.readValue(json, typeRef);
        }

        /**
         * Returns the HTTP client for use by the endpoint service.
         *
         * @return  The HTTP client.
         */
        protected WardenHttpClient getClient() {
            return _client;
        }

        /**
         * Throws an exception if a request results in an error.
         *
         * @param   response    The response to evaluate.
         * @param   requestUrl  The URL to which the request was dispatched.
         *
         * @throws  WardenException  If the request resulted in an error.
         */
        protected void assertValidResponse(WardenHttpClient.WardenResponse response, String requestUrl) throws WardenException {
            if (response.getErrorMessage() != null) {
                throw new WardenException(response.getStatus(), response.getErrorMessage(), requestUrl, response.getResult());
            }
        }
    }


    public void close() throws IOException {
        httpClient.dispose();
    }
}

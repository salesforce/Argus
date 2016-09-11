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
package com.salesforce.dva.argus.sdk;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Provides read and write access to Argus.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class ArgusService implements AutoCloseable {

    //~ Instance fields ******************************************************************************************************************************

    private ArgusHttpClient _client;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new ArgusService object. This method should only be used for mocking unit tests.
     *
     * @param   client  The HTTP client to use.
     *
     * @throws  IllegalArgumentException  If the specified client is null.
     */
    ArgusService(ArgusHttpClient client) {
        if (client == null) {
            throw new IllegalArgumentException("The HTTP client cannot be null.");
        }
        _client = client;
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns a new instance of the Argus service configured with 10 second timeouts.
     *
     * @param   endpoint  The HTTP endpoint for Argus.
     * @param   maxConn   The number of maximum connections. Must be greater than 0.
     *
     * @return  A new instance of the Argus service.
     *
     * @throws  IOException  If the service cannot be initialized due a configuration error such as a malformed URL for example.
     */
    public static ArgusService getInstance(String endpoint, int maxConn) throws IOException {
        return getInstance(endpoint, maxConn, 10000, 10000);
    }

    /**
     * Returns a new instance of the Argus service.
     *
     * @param   endpoint            The HTTP endpoint for Argus.
     * @param   maxConn             The number of maximum connections. Must be greater than 0.
     * @param   connTimeout         The connection timeout in milliseconds. Must be greater than 0.
     * @param   connRequestTimeout  The connection request timeout in milliseconds. Must be greater than 0.
     *
     * @return  A new instance of the Argus service.
     *
     * @throws  IOException  If the service cannot be initialized due a configuration error such as a malformed URL for example.
     */
    public static ArgusService getInstance(String endpoint, int maxConn, int connTimeout, int connRequestTimeout) throws IOException {
        ArgusHttpClient client = new ArgusHttpClient(endpoint, maxConn, connTimeout, connRequestTimeout);

        return new ArgusService(client);
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public void close() throws IOException {
        _client.dispose();
    }

    /**
     * Returns an instance of the authorization service.
     *
     * @return  The authorization service.
     */
    public AuthService getAuthService() {
        return new AuthService(_client);
    }

    /**
     * Returns an instance of the alert service.
     *
     * @return  The alert service.
     */
    public AlertService getAlertService() {
        return new AlertService(_client);
    }

    /**
     * Returns an instance of the annotations service.
     *
     * @return  The annotations service
     */
    public AnnotationService getAnnotationService() {
        return new AnnotationService(_client);
    }

    /**
     * Returns an instance of the audit service.
     *
     * @return  The audit service.
     */
    public AuditService getAuditService() {
        return new AuditService(_client);
    }

    /**
     * Returns an instance of the batch service.
     *
     * @return  The batch service.
     */
    public BatchService getBatchService() {
        return new BatchService(_client);
    }

    /**
     * Returns an instance of the batch service.
     *
     * @return  The batch service.
     */
    public DashboardService getDashboardService() {
        return new DashboardService(_client);
    }

    /**
     * Returns an instance of the discovery service.
     *
     * @return  The discovery service.
     */
    public DiscoveryService getDiscoveryService() {
        return new DiscoveryService(_client);
    }

    /**
     * Returns an instance of the history service.
     *
     * @return  The history service.
     */
    public HistoryService getHistoryService() {
        return new HistoryService(_client);
    }

    /**
     * Returns an instance of the metric service.
     *
     * @return  The metric service.
     */
    public MetricService getMetricService() {
        return new MetricService(_client);
    }

    /**
     * Returns an instance of the namespace service.
     *
     * @return  The namespace service.
     */
    public NamespaceService getNamespaceService() {
        return new NamespaceService(_client);
    }

    /**
     * Returns an instance of the user service.
     *
     * @return  The user service.
     */
    public UserService getUserService() {
        return new UserService(_client);
    }

    //~ Inner Classes ********************************************************************************************************************************

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
         * @param  successCount   The number of successful writes.
         * @param  failCount      The number of failed writes.
         * @param  errorMessages  The associated descriptive error messages for the failed writes.
         */
        public PutResult(String successCount, String failCount, List<String> errorMessages) {
            _successCount = successCount;
            _failCount = failCount;
            _errorMessages = new ArrayList<>();
            if (errorMessages != null) {
                _errorMessages.addAll(errorMessages);
            }
        }

        /**
         * Returns the error messages associated with the failed writes.
         *
         * @return  The error messages. Will never be null, but may be empty.
         */
        public List<String> getErrorMessages() {
            return _errorMessages;
        }

        /**
         * Returns the number of failed writes.
         *
         * @return  The number of failed writes.
         */
        public String getFailCount() {
            return _failCount;
        }

        /**
         * Returns the number of successful writes.
         *
         * @return  The number of successful writes.
         */
        public String getSuccessCount() {
            return _successCount;
        }

        @Override
        public int hashCode() {
            int hash = 3;

            hash = 59 * hash + Objects.hashCode(this._successCount);
            hash = 59 * hash + Objects.hashCode(this._failCount);
            hash = 59 * hash + Objects.hashCode(this._errorMessages);
            return hash;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }

            final PutResult other = (PutResult) obj;

            if (!Objects.equals(this._successCount, other._successCount)) {
                return false;
            }
            if (!Objects.equals(this._failCount, other._failCount)) {
                return false;
            }
            if (!Objects.equals(this._errorMessages, other._errorMessages)) {
                return false;
            }
            return true;
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

        private ArgusHttpClient _client;

        /**
         * Creates a new EndpointService object.
         *
         * @param   client  The HTTP client for use by the endpoint service.
         *
         * @throws  IllegalArgumentException  If the specified client is null.
         */
        EndpointService(ArgusHttpClient client) {
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
        protected ArgusHttpClient getClient() {
            return _client;
        }

        /**
         * Throws an exception if a request results in an error.
         *
         * @param   response    The response to evaluate.
         * @param   requestUrl  The URL to which the request was dispatched.
         *
         * @throws  ArgusServiceException  If the request resulted in an error.
         */
        protected void assertValidResponse(ArgusHttpClient.ArgusResponse response, String requestUrl) throws ArgusServiceException {
            if (response.getErrorMessage() != null) {
                throw new ArgusServiceException(response.getStatus(), response.getErrorMessage(), requestUrl, response.getResult());
            }
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

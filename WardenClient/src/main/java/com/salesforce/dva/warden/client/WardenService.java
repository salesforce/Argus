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
package com.salesforce.dva.warden.client;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;

/**
 * DOCUMENT ME!
 *
 * @author  Jigna Bhatt (jbhatt@salesforce.com)
 */
public class WardenService implements AutoCloseable {

    //~ Instance fields ******************************************************************************************************************************

    private final WardenHttpClient httpClient;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new WardenService object.
     *
     * @param  client  DOCUMENT ME!
     */
    WardenService(WardenHttpClient client) {
        httpClient = client;
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
     * @throws  IOException  DOCUMENT ME!
     */
    public static WardenService getInstance(String endpoint, int maxConn) throws IOException {
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
     * @throws  IOException  DOCUMENT ME!
     */
    public static WardenService getInstance(String endpoint, int maxConn, int connTimeout, int connRequestTimeout) throws IOException {
        WardenHttpClient client = new WardenHttpClient(endpoint, maxConn, connTimeout, connRequestTimeout);

        return new WardenService(client);
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public AuthService getAuthService() {
        return new AuthService(httpClient);
    }

    /**
     * DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     */
    public PolicyService getPolicyService() {
        return new PolicyService(httpClient);
    }

    /**
     * DOCUMENT ME!
     *
     * @throws  IOException  DOCUMENT ME!
     */
    public void close() throws IOException {
        httpClient.dispose();
    }

    //~ Inner Classes ********************************************************************************************************************************

    /**
     * Base class used for endpoint services.
     *
     * @author  Jigna Bhatt (jbhatt@salesforce.com)
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
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

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

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.util.EntityUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;

/**
 * HTTP based API client for Argus.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
class ArgusHttpClient {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        MAPPER.setVisibility(PropertyAccessor.GETTER, Visibility.ANY);
        MAPPER.setVisibility(PropertyAccessor.SETTER, Visibility.ANY);
    }

    //~ Instance fields ******************************************************************************************************************************

    private final String _endpoint;
    private final CloseableHttpClient _httpClient;
    private final BasicHttpContext _httpContext;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new Argus HTTP client.
     *
     * @param   endpoint    The URL of the read endpoint including the port number. Must not be null.
     * @param   maxConn     The maximum number of concurrent connections. Must be greater than 0.
     * @param   timeout     The connection timeout in milliseconds. Must be greater than 0.
     * @param   reqTimeout  The connection request timeout in milliseconds. Must be greater than 0.
     *
     * @throws  IOException  If the client cannot be initialized due a configuration error such as a malformed URL for example.
     */
    ArgusHttpClient(String endpoint, int maxConn, int timeout, int reqTimeout) throws IOException {
        URL url = new URL(endpoint);
        PoolingHttpClientConnectionManager connMgr = new PoolingHttpClientConnectionManager();

        connMgr.setMaxTotal(maxConn);
        connMgr.setDefaultMaxPerRoute(maxConn);

        RequestConfig defaultRequestConfig = RequestConfig.custom().setConnectionRequestTimeout(reqTimeout).setConnectTimeout(timeout).build();

        _httpClient = HttpClients.custom().setConnectionManager(connMgr).setDefaultRequestConfig(defaultRequestConfig).build();
        _httpContext = new BasicHttpContext();
        _httpContext.setAttribute(HttpClientContext.COOKIE_STORE, new BasicCookieStore());
        _endpoint = endpoint;
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Converts a Java object to JSON.
     *
     * @param   <I>   The type of Java object to convert.
     * @param   type  The Java object to convert. Cannot be null.
     *
     * @return  The JSON representation of the object.
     *
     * @throws  IOException  If a serialization error occurs.
     */
    <I> String toJson(I type) throws IOException {
        return MAPPER.writeValueAsString(type);
    }

    /**
     * Closes the client connections and prepares the client for garbage collection.
     *
     * @throws  IOException  If an error occurs while disposing of the client.
     */
    void dispose() throws IOException {
        _httpClient.close();
        _httpContext.clear();
    }

    /* Execute a request given by type requestType. */
    ArgusResponse executeHttpRequest(RequestType requestType, String url, Object payload) throws IOException {
        url = _endpoint + url;

        String json = payload == null ? null : toJson(payload);

        return ArgusResponse.generateResponse(_doHttpRequest(requestType, url, json));
    }

    /* The actual request call.  Factored for test mocking. */
    HttpResponse _doHttpRequest(RequestType requestType, String url, String json) throws IOException {
        StringEntity entity = null;

        if (json != null) {
            entity = new StringEntity(json);
            entity.setContentType("application/json");
        }
        switch (requestType) {
            case POST:

                HttpPost post = new HttpPost(url);

                post.setEntity(entity);
                return _httpClient.execute(post, _httpContext);
            case GET:

                HttpGet httpGet = new HttpGet(url);

                return _httpClient.execute(httpGet, _httpContext);
            case DELETE:

                HttpDelete httpDelete = new HttpDelete(url);

                return _httpClient.execute(httpDelete, _httpContext);
            case PUT:

                HttpPut httpput = new HttpPut(url);

                httpput.setEntity(entity);
                return _httpClient.execute(httpput, _httpContext);
            default:
                throw new IllegalArgumentException(" Request Type " + requestType + " not a valid request type. ");
        }
    }

    //~ Enums ****************************************************************************************************************************************

    /**
     * The HTTP request type to use.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    static enum RequestType {

        POST,
        GET,
        DELETE,
        PUT;
    }

    //~ Inner Classes ********************************************************************************************************************************

    /**
     * The Argus request response object which encapsulates information about a completed request.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public static class ArgusResponse {

        private final int _status;
        private final String _message;
        private final String _errorMessage;
        private final String _result;

        private ArgusResponse(int status, String message, String errorMessage, String result) {
            _status = status;
            _message = message;
            _errorMessage = errorMessage;
            _result = result;
        }

        static ArgusResponse generateResponse(HttpResponse response) throws IOException {
            int status = response.getStatusLine().getStatusCode();
            String message = null, errorMessage = null;

            if (status >= 200 && status < 300) {
                message = response.getStatusLine().getReasonPhrase();
            } else {
                errorMessage = response.getStatusLine().getReasonPhrase();
            }

            HttpEntity entity = response.getEntity();
            String result = null;

            if (entity != null) {
                result = EntityUtils.toString(entity);
            }
            return new ArgusResponse(status, message, errorMessage, result);
        }

        /**
         * Returns the HTTP status code for the response.
         *
         * @return  The HTTP status code for the response.
         */
        public int getStatus() {
            return _status;
        }

        /**
         * The message associated with the response.
         *
         * @return  The message associated with the response.
         */
        public String getMessage() {
            return _message;
        }

        /**
         * The error message associated with the response.
         *
         * @return  THe error message associated with the response.
         */
        public String getErrorMessage() {
            return _errorMessage;
        }

        /**
         * Returns the response body as a text string.
         *
         * @return  The response body as a text string.
         */
        public String getResult() {
            return _result;
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

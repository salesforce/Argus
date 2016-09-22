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

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.BasicHttpContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.net.URL;

/**
 * DOCUMENT ME!
 *
 * @author  Jigna Bhatt (jbhatt@salesforce.com)
 */
public class WardenHttpClient {

    //~ ssssatic fields/initializers *******************************************************************************************************************

    // ~ Static fields/initializers
    // *******************************************************************************************************************
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger LOGGER = LoggerFactory.getLogger(WardenHttpClient.class.getName());

    static {
        MAPPER.setVisibility(PropertyAccessor.GETTER, Visibility.ANY);
        MAPPER.setVisibility(PropertyAccessor.SETTER, Visibility.ANY);
    }

    //~ Instance fields ******************************************************************************************************************************

    // ~ Instance fields
    // ******************************************************************************************************************************
    int maxConn = 100;
    int connTimeout = 10000;
    int connRequestTimeout = 10000;
    String endpoint;
    CloseableHttpClient httpClient;
    PoolingHttpClientConnectionManager connMgr;
    private BasicCookieStore cookieStore;
    private BasicHttpContext httpContext;

    //~ Constructors *********************************************************************************************************************************

    // ~ Constructors
    // *********************************************************************************************************************************
    /**
     * Creates a new Argus HTTP client.
     *
     * @param   endpoint    The URL of the read endpoint including the port number. Must not be null.
     * @param   maxConn     The maximum number of concurrent connections. Must be greater than 0.
     * @param   timeout     The connection timeout in milliseconds. Must be greater than 0.
     * @param   reqTimeout  The connection request timeout in milliseconds. Must be greater than 0.
     *
     * @throws  IOException  DOCUMENT ME!
     */
    public WardenHttpClient(String endpoint, int maxConn, int timeout, int reqTimeout) throws IOException {
        URL url = new URL(endpoint);
        int port = url.getPort();

        connMgr = new PoolingHttpClientConnectionManager();
        connMgr.setMaxTotal(maxConn);
        connMgr.setDefaultMaxPerRoute(maxConn);

        String routePath = endpoint.substring(0, endpoint.lastIndexOf(":"));
        HttpHost host = new HttpHost(routePath, port);
        RequestConfig defaultRequestConfig = RequestConfig.custom().setConnectionRequestTimeout(reqTimeout).setConnectTimeout(timeout).build();

        connMgr.setMaxPerRoute(new HttpRoute(host), maxConn / 2);
        httpClient = HttpClients.custom().setConnectionManager(connMgr).setDefaultRequestConfig(defaultRequestConfig).build();
        cookieStore = new BasicCookieStore();
        httpContext = new BasicHttpContext();
        httpContext.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);
        LOGGER.info("Argus HTTP Client initialized using " + endpoint);
        this.endpoint = endpoint;
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * DOCUMENT ME!
     *
     * @param   <T>   DOCUMENT ME!
     * @param   type  DOCUMENT ME!
     *
     * @return  DOCUMENT ME!
     *
     * @throws  IOException  DOCUMENT ME!
     */
    protected <T> String toJson(T type) throws IOException {
        return MAPPER.writeValueAsString(type);
    }

    // ~ Methods
    // **************************************************************************************************************************************
    /**
     * Closes the client connections and prepares the client for garbage collection. This method may be invoked on a client which has already been
     * disposed.
     *
     * @throws  IOException  DOCUMENT ME!
     */
    public void dispose() throws IOException {
        httpClient.close();
        cookieStore.clear();
        httpContext.clear();
    }

    /* Execute a request given by type requestType. */
    <T> WardenResponse<T> executeHttpRequest(RequestType requestType, String url, Object payload) throws IOException {
        url = endpoint + url;

        String json = payload == null ? null : toJson(payload);
        HttpResponse response = doHttpRequest(requestType, url, json);
        WardenResponse<T> wardenResponse = WardenResponse.generateResponse(response);

        return wardenResponse;
    }

    HttpResponse doHttpRequest(RequestType requestType, String url, String json) throws IOException {
        StringEntity entity = null;

        if (json != null) {
            entity = new StringEntity(json);
            entity.setContentType("application/json");
        }
        switch (requestType) {
            case POST:

                HttpPost post = new HttpPost(url);

                post.setEntity(entity);
                return httpClient.execute(post, httpContext);
            case GET:

                HttpGet httpGet = new HttpGet(url);

                return httpClient.execute(httpGet, httpContext);
            case DELETE:

                HttpDelete httpDelete = new HttpDelete(url);

                return httpClient.execute(httpDelete, httpContext);
            case PUT:

                HttpPut httpput = new HttpPut(url);

                httpput.setEntity(entity);
                return httpClient.execute(httpput, httpContext);
            default:
                throw new IllegalArgumentException(" Request Type " + requestType + " not a valid request type. ");
        }
    }

    //~ Enums ****************************************************************************************************************************************

    // ~ Enums
    // ****************************************************************************************************************************************
    /**
     * The request type to use.
     *
     * @author  Jigna Bhatt (jbhatt@salesforce.com)
     */
    static enum RequestType {

        POST,
        GET,
        DELETE,
        PUT;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

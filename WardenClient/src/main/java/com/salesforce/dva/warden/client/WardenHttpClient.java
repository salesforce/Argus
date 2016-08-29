package com.salesforce.dva.warden.client;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.http.HttpEntity;
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

import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

public class WardenHttpClient
{
    // ~ Static fields/initializers
    // *******************************************************************************************************************
    private static final ObjectMapper MAPPER = new ObjectMapper(  );
    private static final Logger LOGGER = LoggerFactory.getLogger( WardenHttpClient.class.getName(  ) );

    static
    {
        MAPPER.setVisibility( PropertyAccessor.GETTER, Visibility.ANY );
        MAPPER.setVisibility( PropertyAccessor.SETTER, Visibility.ANY );
    }

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

    protected <T> String toJson(T type) throws IOException {
        return MAPPER.writeValueAsString(type);
    }

    // ~ Constructors
    // *********************************************************************************************************************************

    /**
     * Creates a new Argus HTTP client.
     *
     * @param endpoint
     *            The URL of the read endpoint including the port number. Must
     *            not be null.
     * @param maxConn
     *            The maximum number of concurrent connections. Must be greater
     *            than 0.
     * @param timeout
     *            The connection timeout in milliseconds. Must be greater than
     *            0.
     * @param reqTimeout
     *            The connection request timeout in milliseconds. Must be
     *            greater than 0.
     *
     */
    public WardenHttpClient(String endpoint, int maxConn, int timeout, int reqTimeout ) throws IOException
    {
            URL url = new URL( endpoint );
            int port = url.getPort(  );

            connMgr = new PoolingHttpClientConnectionManager(  );
            connMgr.setMaxTotal( maxConn );
            connMgr.setDefaultMaxPerRoute( maxConn );

            String routePath = endpoint.substring( 0, endpoint.lastIndexOf( ":" ) );
            HttpHost host = new HttpHost( routePath, port );
            RequestConfig defaultRequestConfig = RequestConfig.custom(  ).setConnectionRequestTimeout( reqTimeout ).setConnectTimeout( timeout ).build(  );

            connMgr.setMaxPerRoute( new HttpRoute( host ), maxConn / 2 );
            httpClient = HttpClients.custom(  ).setConnectionManager( connMgr ) .setDefaultRequestConfig( defaultRequestConfig ).build(  );
            cookieStore = new BasicCookieStore(  );
            httpContext = new BasicHttpContext(  );
            httpContext.setAttribute( HttpClientContext.COOKIE_STORE, cookieStore );
        LOGGER.info( "Argus HTTP Client initialized using " + endpoint );
        this.endpoint = endpoint;
    }

    public static class WardenResponse {
        private final int status;
        private final String message;
        private final String errorMessage;
        private final String result;

        static WardenResponse generateResponse(HttpResponse response) throws IOException {
            EntityUtils.consume(response.getEntity());
            int status = response.getStatusLine().getStatusCode();
            String message = null, errorMessage = null;
            if(status >=200 && status < 300) {
                message = response.getStatusLine().getReasonPhrase();
            } else {
                errorMessage = response.getStatusLine().getReasonPhrase();
            }
            HttpEntity entity = response.getEntity();
            String result = null;
            if(entity != null) {

                try(ByteArrayOutputStream baos = new ByteArrayOutputStream()){
                    entity.writeTo(baos);
                    result = baos.toString();
                }
            }
            return new WardenResponse(status, message, errorMessage, result);
        }


        private WardenResponse(int status, String message, String errorMessage, String result) {
            this.status = status;
            this.message = message;
            this.errorMessage = errorMessage;
            this.result = result;
        }

        public int getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public String getResult() {
            return result;
        }

    }

    // ~ Methods
    // **************************************************************************************************************************************

    /**
     * Closes the client connections and prepares the client for garbage
     * collection. This method may be invoked on a client which has already been
     * disposed.
     */
    public void dispose(  ) throws IOException
    {
        httpClient.close(  );
        cookieStore.clear();
        httpContext.clear();
    }

    /* Execute a request given by type requestType. */
    WardenResponse executeHttpRequest(RequestType requestType, String url, Object payload) throws IOException{
        url = endpoint + url;
        String json = payload == null ? null : toJson(payload);
            HttpResponse response = doHttpRequest(requestType, url, json);
            WardenResponse wardenResponse = WardenResponse.generateResponse(response);
            return wardenResponse;
    }

    HttpResponse doHttpRequest(RequestType requestType, String url, String json) throws IOException{
            StringEntity entity = null;
            if(json != null) {
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



    // ~ Enums
    // ****************************************************************************************************************************************

    /**
     * The request type to use.
     *
     * @author Tom Valine (tvaline@salesforce.com)
     */
    static enum RequestType
    {
        POST,
        GET,
        DELETE,
        PUT;

    }
}
/*
 * Copyright Salesforce 2002,2014 All Rights Reserved.
 * **************************
 * *******************************************************************
 */

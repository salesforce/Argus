package com.salesforce.dva.warden.client;

import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesforce.dva.warden.client.util.WardenException;

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
import java.net.MalformedURLException;
import java.net.URL;
import static com.salesforce.dva.warden.client.util.Assert.requireArgument;

public class WardenHttpClient {

		// ~ Static fields/initializers
		// *******************************************************************************************************************

		private static final ObjectMapper MAPPER = new ObjectMapper();
		private static final Logger LOGGER = LoggerFactory.getLogger(WardenHttpClient.class.getName());
		static {
			MAPPER.setVisibility(PropertyAccessor.GETTER, Visibility.ANY);
			MAPPER.setVisibility(PropertyAccessor.SETTER, Visibility.ANY);
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
		 * @throws AKCException
		 *             If an error occurs.
		 */
		public WardenHttpClient(String endpoint, int maxConn, int timeout, int reqTimeout) {
			requireArgument((endpoint != null) && (!endpoint.isEmpty()), "Illegal endpoint URL.");
			requireArgument(maxConn >= 2, "At least two connections are required.");
			requireArgument(timeout >= 1, "Timeout must be greater than 0.");
			requireArgument(reqTimeout >= 1, "Request timeout must be greater than 0.");
			
			try {
				URL url = new URL(endpoint);
				int port = url.getPort();

				requireArgument(port != -1, "Endpoint must include explicit port.");
				connMgr = new PoolingHttpClientConnectionManager();
				connMgr.setMaxTotal(maxConn);
				connMgr.setDefaultMaxPerRoute(maxConn);

				String routePath = endpoint.substring(0, endpoint.lastIndexOf(":"));
				HttpHost host = new HttpHost(routePath, port);
				RequestConfig defaultRequestConfig = RequestConfig.custom()
						.setConnectionRequestTimeout(reqTimeout)
						.setConnectTimeout(timeout).build();

				connMgr.setMaxPerRoute(new HttpRoute(host), maxConn / 2);
				httpClient = HttpClients.custom().setConnectionManager(connMgr).setDefaultRequestConfig(defaultRequestConfig).build();
				cookieStore = new BasicCookieStore();
				httpContext = new BasicHttpContext();
				httpContext.setAttribute(HttpClientContext.COOKIE_STORE, cookieStore);
			} catch (MalformedURLException ex) {
				throw new WardenException("Error initializing the Argus HTTP Client.", ex);
			}
			LOGGER.info("Argus HTTP Client initialized using " + endpoint);
			this.endpoint = endpoint;
		}

		// ~ Methods
		// **************************************************************************************************************************************

		/**
		 * Closes the client connections and prepares the client for garbage
		 * collection. This method may be invoked on a client which has already been
		 * disposed.
		 */
		public void dispose() {
			try {
				httpClient.close();
			} catch (IOException ex) {
				LOGGER.warn("The HTTP client failed to shutdown properly.", ex);
			}
		}
		

		/* Execute a request given by type requestType. */
	HttpResponse executeHttpRequest(RequestType requestType,
				String url, StringEntity entity) throws IOException {
			HttpResponse httpResponse = null;

			if (entity != null) {
				entity.setContentType("application/json");
			}
			switch (requestType) {
			case POST:
				HttpPost post = new HttpPost(url);
				post.setEntity(entity);
				httpResponse = httpClient.execute(post, httpContext);
				break;
			case GET:
				HttpGet httpGet = new HttpGet(url);
				httpResponse = httpClient.execute(httpGet, httpContext);
				break;
			case DELETE:
				HttpDelete httpDelete = new HttpDelete(url);
				httpResponse = httpClient.execute(httpDelete, httpContext);
				break;
			case PUT:
				HttpPut httpput = new HttpPut(url);
				httpput.setEntity(entity);
				httpResponse = httpClient.execute(httpput, httpContext);
				break;
			default:
				throw new IllegalArgumentException(" Request Type " + requestType
						+ " not a valid request type. ");
			}
			return httpResponse;
		}

		static <T> String toJson(T type) {
			try {
				return MAPPER.writeValueAsString(type);
			} catch (IOException ex) {
				throw new WardenException(ex);
			}
		}

		// ~ Enums
		// ****************************************************************************************************************************************

		/**
		 * The request type to use.
		 * 
		 * @author Tom Valine (tvaline@salesforce.com)
		 */
		 static enum RequestType {

			POST("post"), GET("get"), DELETE("delete"), PUT("put");

			private final String requestType;

			private RequestType(String requestType) {
				this.requestType = requestType;
			}

			/**
			 * Returns the request type as a string.
			 * 
			 * @return The request type.
			 */
			@SuppressWarnings("unused")
			public String getRequestType() {
				return requestType;
			}
		}

	 void clearCookies() {
			cookieStore.clear();
		}
	}
	/*
	 * Copyright Salesforce 2002,2014 All Rights Reserved.
	 * **************************
	 * *******************************************************************
	 */


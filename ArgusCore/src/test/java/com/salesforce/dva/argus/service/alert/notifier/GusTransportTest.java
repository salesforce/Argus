package com.salesforce.dva.argus.service.alert.notifier;

import com.salesforce.dva.argus.service.alert.notifier.GusTransport.EndpointInfo;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.slf4j.LoggerFactory;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * Ignore the NoSuchAlgorithmException that gets logged for all test cases in this class. This failure is occurring
 * because SSLContext is loading from PowerMock's classloader.
 * http://mathieuhicauber-java.blogspot.com/2013/07/powermock-and-ssl-context.html
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({HttpClients.class, EntityUtils.class, HttpClientBuilder.class})
public class GusTransportTest {
    private static final String PROXY_HOST = "test_proxy_host";
    private static final int PROXY_PORT = 9090;
    private static final String AUTH_ENDPOINT = "https://test_auth_ep.com";
    private static final String AUTH_CLIENT_ID = "test_auth_client_id";
    private static final String AUTH_CLIENT_SECRET = "test_auth_client_secret";
    private static final String AUTH_USERNAME = "test_auth_username";
    private static final String AUTH_PASSWORD = "test_auth_password";
    private static final String DEFAULT_ENDPOINT = "https://test_default_ep.com";
    private static final String TEST_INSTANCE_URL = "https://test_instance_url.com";
    private static final String TEST_TOKEN = "test_token";
    private static final int CONNECTION_POOL_SIZE = 5;
    private static final int CONNECTION_POOL_MAX_PER_ROUTE = 5;

    // mocks
    private HttpClientBuilder httpClientBuilder;
    private CloseableHttpClient httpClient;
    private CloseableHttpResponse httpResponse;
    private StatusLine httpResponseStatusLine;
    private HttpEntity httpResponseEntity;

    private GusTransport gusTransport;

    private static ch.qos.logback.classic.Logger apacheLogger;
    private static ch.qos.logback.classic.Logger myClassLogger;

    @BeforeClass
    static public void setUpClass() {
        myClassLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("com.salesforce.dva.argus.service.alert.GusTransportTest");
        myClassLogger.setLevel(ch.qos.logback.classic.Level.OFF);
        apacheLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.apache");
        apacheLogger.setLevel(ch.qos.logback.classic.Level.OFF);
    }

    @Before
    public void setUp() {
        httpClientBuilder = mock(HttpClientBuilder.class);
        httpClient = mock(CloseableHttpClient.class);
        httpResponse = mock(CloseableHttpResponse.class);
        httpResponseStatusLine = mock(StatusLine.class);
        httpResponseEntity = mock(HttpEntity.class);

        mockStatic(HttpClients.class);
        mockStatic(EntityUtils.class);
    }

    @Test
    public void constructor_test() throws Exception {
        gusTransport = createGusTransportHappyCase();

        verify(httpClientBuilder).setRoutePlanner(any());
    }

    @Test
    public void constructor_testProxyHostStringAndProxyPortString() throws Exception {
        gusTransport = createGusTransportHappyCase(PROXY_HOST, new Integer(PROXY_PORT).toString());

        verify(httpClientBuilder).setRoutePlanner(any());
    }

    @Test
    public void constructor_testEmptyProxyHostString() throws Exception {
        gusTransport = createGusTransportHappyCase("   ", new Integer(PROXY_PORT).toString());

        verify(httpClientBuilder, never()).setRoutePlanner(any());
    }

    @Test
    public void constructor_testEmptyProxyHost() throws Exception {
        gusTransport = createGusTransportHappyCase(Optional.empty(), Optional.of(PROXY_PORT));

        verify(httpClientBuilder, never()).setRoutePlanner(any());
    }

    @Test
    public void constructor_testEmptyProxyPortString() throws Exception {
        gusTransport = createGusTransportHappyCase(PROXY_HOST, " ");

        verify(httpClientBuilder, never()).setRoutePlanner(any());
    }

    @Test
    public void constructor_testEmptyProxyPort() throws Exception {
        gusTransport = createGusTransportHappyCase(Optional.of(PROXY_HOST), Optional.empty());

        verify(httpClientBuilder, never()).setRoutePlanner(any());
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_testProxyPortStringNonNumeric() throws Exception {
        gusTransport = new GusTransport(PROXY_HOST,
                "ABC",
                AUTH_ENDPOINT,
                AUTH_CLIENT_ID,
                AUTH_CLIENT_SECRET,
                AUTH_USERNAME,
                AUTH_PASSWORD,
                new EndpointInfo(DEFAULT_ENDPOINT, GusTransport.NO_TOKEN),
                CONNECTION_POOL_SIZE,
                CONNECTION_POOL_MAX_PER_ROUTE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_testProxyHostBlank() throws Exception {
        gusTransport = createGusTransport(Optional.of("  "), Optional.of(PROXY_PORT));
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_testProxyPortLessThan0() throws Exception {
        gusTransport = createGusTransport(Optional.of(PROXY_HOST), Optional.of(-1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_testAuthEndpointBlank() throws Exception {
        gusTransport = new GusTransport(Optional.of(PROXY_HOST),
                Optional.of(PROXY_PORT),
                "",
                AUTH_CLIENT_ID,
                AUTH_CLIENT_SECRET,
                AUTH_USERNAME,
                AUTH_PASSWORD,
                new EndpointInfo(DEFAULT_ENDPOINT, GusTransport.NO_TOKEN),
                CONNECTION_POOL_SIZE,
                CONNECTION_POOL_MAX_PER_ROUTE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_testAuthEndpointNull() throws Exception {
        gusTransport = new GusTransport(Optional.of(PROXY_HOST),
                Optional.of(PROXY_PORT),
                null,
                AUTH_CLIENT_ID,
                AUTH_CLIENT_SECRET,
                AUTH_USERNAME,
                AUTH_PASSWORD,
                new EndpointInfo(DEFAULT_ENDPOINT, GusTransport.NO_TOKEN),
                CONNECTION_POOL_SIZE,
                CONNECTION_POOL_MAX_PER_ROUTE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_testAuthClientIdBlank() throws Exception {
        gusTransport = new GusTransport(Optional.of(PROXY_HOST),
                Optional.of(PROXY_PORT),
                AUTH_ENDPOINT,
                " ",
                AUTH_CLIENT_SECRET,
                AUTH_USERNAME,
                AUTH_PASSWORD,
                new EndpointInfo(DEFAULT_ENDPOINT, GusTransport.NO_TOKEN),
                CONNECTION_POOL_SIZE,
                CONNECTION_POOL_MAX_PER_ROUTE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_testAuthClientIdNull() throws Exception {
        gusTransport = new GusTransport(Optional.of(PROXY_HOST),
                Optional.of(PROXY_PORT),
                AUTH_ENDPOINT,
                null,
                AUTH_CLIENT_SECRET,
                AUTH_USERNAME,
                AUTH_PASSWORD,
                new EndpointInfo(DEFAULT_ENDPOINT, GusTransport.NO_TOKEN),
                CONNECTION_POOL_SIZE,
                CONNECTION_POOL_MAX_PER_ROUTE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_testAuthClientSecretBlank() throws Exception {
        gusTransport = new GusTransport(Optional.of(PROXY_HOST),
                Optional.of(PROXY_PORT),
                AUTH_ENDPOINT,
                AUTH_CLIENT_ID,
                " ",
                AUTH_USERNAME,
                AUTH_PASSWORD,
                new EndpointInfo(DEFAULT_ENDPOINT, GusTransport.NO_TOKEN),
                CONNECTION_POOL_SIZE,
                CONNECTION_POOL_MAX_PER_ROUTE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_testAuthClientSecretNull() throws Exception {
        gusTransport = new GusTransport(Optional.of(PROXY_HOST),
                Optional.of(PROXY_PORT),
                AUTH_ENDPOINT,
                AUTH_CLIENT_ID,
                null,
                AUTH_USERNAME,
                AUTH_PASSWORD,
                new EndpointInfo(DEFAULT_ENDPOINT, GusTransport.NO_TOKEN),
                CONNECTION_POOL_SIZE,
                CONNECTION_POOL_MAX_PER_ROUTE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_testAuthUsernameBlank() throws Exception {
        gusTransport = new GusTransport(Optional.of(PROXY_HOST),
                Optional.of(PROXY_PORT),
                AUTH_ENDPOINT,
                AUTH_CLIENT_ID,
                AUTH_CLIENT_SECRET,
                "",
                AUTH_PASSWORD,
                new EndpointInfo(DEFAULT_ENDPOINT, GusTransport.NO_TOKEN),
                CONNECTION_POOL_SIZE,
                CONNECTION_POOL_MAX_PER_ROUTE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_testAuthUsernameNull() throws Exception {
        gusTransport = new GusTransport(Optional.of(PROXY_HOST),
                Optional.of(PROXY_PORT),
                AUTH_ENDPOINT,
                AUTH_CLIENT_ID,
                AUTH_CLIENT_SECRET,
                null,
                AUTH_PASSWORD,
                new EndpointInfo(DEFAULT_ENDPOINT, GusTransport.NO_TOKEN),
                CONNECTION_POOL_SIZE,
                CONNECTION_POOL_MAX_PER_ROUTE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_testAuthPasswordBlank() throws Exception {
        gusTransport = new GusTransport(Optional.of(PROXY_HOST),
                Optional.of(PROXY_PORT),
                AUTH_ENDPOINT,
                AUTH_CLIENT_ID,
                AUTH_CLIENT_SECRET,
                AUTH_USERNAME,
                "",
                new EndpointInfo(DEFAULT_ENDPOINT, GusTransport.NO_TOKEN),
                CONNECTION_POOL_SIZE,
                CONNECTION_POOL_MAX_PER_ROUTE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_testAuthPasswordNull() throws Exception {
        gusTransport = new GusTransport(Optional.of(PROXY_HOST),
                Optional.of(PROXY_PORT),
                AUTH_ENDPOINT,
                AUTH_CLIENT_ID,
                AUTH_CLIENT_SECRET,
                AUTH_USERNAME,
                null,
                new EndpointInfo(DEFAULT_ENDPOINT, GusTransport.NO_TOKEN),
                CONNECTION_POOL_SIZE,
                CONNECTION_POOL_MAX_PER_ROUTE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_testDefaultEndpointNull() throws Exception {
        gusTransport = new GusTransport(Optional.of(PROXY_HOST),
                Optional.of(PROXY_PORT),
                AUTH_ENDPOINT,
                AUTH_CLIENT_ID,
                AUTH_CLIENT_SECRET,
                AUTH_USERNAME,
                AUTH_PASSWORD,
                null,
                CONNECTION_POOL_SIZE,
                CONNECTION_POOL_MAX_PER_ROUTE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_testDefaultEndpointEndpointBlank() throws Exception {
        gusTransport = new GusTransport(Optional.of(PROXY_HOST),
                Optional.of(PROXY_PORT),
                AUTH_ENDPOINT,
                AUTH_CLIENT_ID,
                AUTH_CLIENT_SECRET,
                AUTH_USERNAME,
                AUTH_PASSWORD,
                new EndpointInfo("", GusTransport.NO_TOKEN),
                CONNECTION_POOL_SIZE,
                CONNECTION_POOL_MAX_PER_ROUTE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_testDefaultEndpointEndpointNull() throws Exception {
        gusTransport = new GusTransport(Optional.of(PROXY_HOST),
                Optional.of(PROXY_PORT),
                AUTH_ENDPOINT,
                AUTH_CLIENT_ID,
                AUTH_CLIENT_SECRET,
                AUTH_USERNAME,
                AUTH_PASSWORD,
                new EndpointInfo(null, GusTransport.NO_TOKEN),
                CONNECTION_POOL_SIZE,
                CONNECTION_POOL_MAX_PER_ROUTE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_testDefaultEndpointTokenBlank() throws Exception {
        gusTransport = new GusTransport(Optional.of(PROXY_HOST),
                Optional.of(PROXY_PORT),
                AUTH_ENDPOINT,
                AUTH_CLIENT_ID,
                AUTH_CLIENT_SECRET,
                AUTH_USERNAME,
                AUTH_PASSWORD,
                new EndpointInfo(DEFAULT_ENDPOINT, " "),
                CONNECTION_POOL_SIZE,
                CONNECTION_POOL_MAX_PER_ROUTE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_testDefaultEndpointTokenNull() throws Exception {
        gusTransport = new GusTransport(Optional.of(PROXY_HOST),
                Optional.of(PROXY_PORT),
                AUTH_ENDPOINT,
                AUTH_CLIENT_ID,
                AUTH_CLIENT_SECRET,
                AUTH_USERNAME,
                AUTH_PASSWORD,
                new EndpointInfo(DEFAULT_ENDPOINT, null),
                CONNECTION_POOL_SIZE,
                CONNECTION_POOL_MAX_PER_ROUTE);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_testInvalidConnectionPoolSize() throws Exception {
        gusTransport = new GusTransport(Optional.of(PROXY_HOST),
                Optional.of(PROXY_PORT),
                AUTH_ENDPOINT,
                AUTH_CLIENT_ID,
                AUTH_CLIENT_SECRET,
                AUTH_USERNAME,
                AUTH_PASSWORD,
                new EndpointInfo(DEFAULT_ENDPOINT, GusTransport.NO_TOKEN),
                0,
                1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructor_testInvalidConnectionPoolMaxPerRoute() throws Exception {
        gusTransport = new GusTransport(Optional.of(PROXY_HOST),
                Optional.of(PROXY_PORT),
                AUTH_ENDPOINT,
                AUTH_CLIENT_ID,
                AUTH_CLIENT_SECRET,
                AUTH_USERNAME,
                AUTH_PASSWORD,
                new EndpointInfo(DEFAULT_ENDPOINT, GusTransport.NO_TOKEN),
                1,
                -1);
    }

    @Test
    public void getEndpointInfo_testFalseRefresh() throws Exception {
        int times = 10;
        boolean refresh = false;

        gusTransport = createGusTransportHappyCase();
        EndpointInfo ei = gusTransport.getEndpointInfo();

        assertEquals(TEST_INSTANCE_URL, ei.getEndPoint());
        assertEquals(TEST_TOKEN, ei.getToken());

        // call again and should not refresh
        for (int i = 0; i < times; i++) {
            EndpointInfo ei2 = gusTransport.getEndpointInfo(refresh);
            assertSame(ei, ei2);
        }

        verify(httpClient, times(1)).execute(any());
        verify(httpResponse, times(1)).getStatusLine();
        verify(httpResponseStatusLine, times(1)).getStatusCode();
        verify(httpResponse, times(1)).getEntity();
    }

    @Test
    public void getEndpointInfo_testTrueRefresh() throws Exception {
        int times = 10;
        boolean refresh = true;

        gusTransport = createGusTransportHappyCase();
        EndpointInfo ei = gusTransport.getEndpointInfo();

        assertEquals(TEST_INSTANCE_URL, ei.getEndPoint());
        assertEquals(TEST_TOKEN, ei.getToken());

        // call again and should refresh each time
        for (int i = 1; i < times; i++) {
            EndpointInfo ei2 = gusTransport.getEndpointInfo(refresh);
            assertEquals(ei, ei2);
        }

        verify(httpClient, times(times)).execute(any());
        verify(httpResponse, times(times)).getStatusLine();
        verify(httpResponseStatusLine, times(times)).getStatusCode();
        verify(httpResponse, times(times)).getEntity();
    }

    @Test
    public void getEndpointInfo_testFailToInitEndpointOnConstructionWithBadResponse() throws Exception {
        mockCacheInitExpectations();
        when(httpResponseStatusLine.getStatusCode()).thenReturn(401); // get token response code

        gusTransport = createGusTransport();
        EndpointInfo ei = gusTransport.getEndpointInfo();

        assertEquals(DEFAULT_ENDPOINT, ei.getEndPoint());
        assertEquals(GusTransport.NO_TOKEN, ei.getToken());

        verify(httpClient, times(1)).execute(any());
        verify(httpResponse, times(1)).getStatusLine();
        verify(httpResponseStatusLine, times(1)).getStatusCode();
        verify(httpResponse, times(1)).getEntity();
    }

    @Test
    public void getEndpointInfo_testFailToRefreshEndpointWithBadResponse() throws Exception {
        boolean refresh = true;
        mockCacheInitExpectations();
        when(httpResponseStatusLine.getStatusCode()).thenReturn(200) // first time, return OK
                .thenReturn(401); // 2nd time, return bad response
        when(EntityUtils.toString(httpResponseEntity))
                .thenReturn("{\"instance_url\": \"" + TEST_INSTANCE_URL +"\",  \"access_token\": \"" + TEST_TOKEN + "\"}")
                .thenReturn("bad response");

        gusTransport = createGusTransport();
        EndpointInfo ei = gusTransport.getEndpointInfo();

        assertEquals(TEST_INSTANCE_URL, ei.getEndPoint());
        assertEquals(TEST_TOKEN, ei.getToken());

        EndpointInfo ei2 = gusTransport.getEndpointInfo(refresh);
        assertSame(ei, ei2);

        verify(httpClient, times(2)).execute(any());
        verify(httpResponse, times(2)).getStatusLine();
        verify(httpResponseStatusLine, times(2)).getStatusCode();
        verify(httpResponse, times(2)).getEntity();
    }

    private GusTransport createGusTransportHappyCase() throws Exception {
        return createGusTransportHappyCase(Optional.of(PROXY_HOST), Optional.of(PROXY_PORT));
    }

    private GusTransport createGusTransport() throws Exception {
        return createGusTransport(Optional.of(PROXY_HOST), Optional.of(PROXY_PORT));
    }

    private void mockCacheInitExpectations() throws Exception {
        // define mock behavior where cache init is successful
        when(httpClient.execute(any())).thenReturn(httpResponse);
        when(httpResponse.getStatusLine()).thenReturn(httpResponseStatusLine);
        when(httpResponse.getEntity()).thenReturn(httpResponseEntity);
        when(EntityUtils.toString(httpResponseEntity)).thenReturn(
                "{\"instance_url\": \"" + TEST_INSTANCE_URL +"\",  \"access_token\": \"" + TEST_TOKEN + "\"}");
    }

    private void mockCacheInitExpectationsHappyCase() throws Exception {
        mockCacheInitExpectations();
        when(httpResponseStatusLine.getStatusCode()).thenReturn(200);
    }

    private GusTransport createGusTransportHappyCase(String proxyHostString, String proxyPortString) throws Exception {
        mockCacheInitExpectationsHappyCase();
        mockHttpClientExpectations(Optional.ofNullable(proxyHostString),
                StringUtils.isNumeric(proxyPortString) ? Optional.of(Integer.parseInt(proxyPortString)) : Optional.empty());
        return new GusTransport(proxyHostString,
                proxyPortString,
                AUTH_ENDPOINT,
                AUTH_CLIENT_ID,
                AUTH_CLIENT_SECRET,
                AUTH_USERNAME,
                AUTH_PASSWORD,
                new EndpointInfo(DEFAULT_ENDPOINT, GusTransport.NO_TOKEN),
                CONNECTION_POOL_SIZE,
                CONNECTION_POOL_MAX_PER_ROUTE);
    }

    private GusTransport createGusTransportHappyCase(Optional<String> proxyHost, Optional<Integer> proxyPort) throws Exception {
        mockCacheInitExpectationsHappyCase();
        return createGusTransport(proxyHost, proxyPort);
    }

    private void mockHttpClientExpectations(Optional<String> proxyHost, Optional<Integer> proxyPort) throws Exception {
        // define mock behavior
        when(HttpClients.custom()).thenReturn(httpClientBuilder);
        when(httpClientBuilder.setDefaultRequestConfig(any())).thenReturn(httpClientBuilder);
        when(httpClientBuilder.setConnectionManager(any())).thenReturn(httpClientBuilder);
        when(httpClientBuilder.setSSLContext(any())).thenReturn(httpClientBuilder);
        when(httpClientBuilder.setSSLHostnameVerifier(any())).thenReturn(httpClientBuilder);
        if (proxyHost.isPresent() && proxyPort.isPresent()) {
            when(httpClientBuilder.setRoutePlanner(any())).thenReturn(httpClientBuilder);
        }
        when(httpClientBuilder.build()).thenReturn(httpClient);
    }

    private GusTransport createGusTransport(Optional<String> proxyHost, Optional<Integer> proxyPort) throws Exception {
        mockHttpClientExpectations(proxyHost, proxyPort);

        // create new GusTransport
        return new GusTransport(proxyHost,
                proxyPort,
                AUTH_ENDPOINT,
                AUTH_CLIENT_ID,
                AUTH_CLIENT_SECRET,
                AUTH_USERNAME,
                AUTH_PASSWORD,
                new EndpointInfo(DEFAULT_ENDPOINT, GusTransport.NO_TOKEN),
                CONNECTION_POOL_SIZE,
                CONNECTION_POOL_MAX_PER_ROUTE);
    }

}

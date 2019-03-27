package com.salesforce.dva.argus.service.alert.notifier;

import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.apache.commons.lang.StringUtils;
import org.junit.Before;
import com.salesforce.dva.argus.service.alert.notifier.GusTransport.EndpointInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.verifyNew;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.whenNew;

@RunWith(PowerMockRunner.class)
@PrepareForTest(GusTransport.class)
public class GusTransportTest {
    private static final String HTTP_CONNECTION_MANAGER_TIMEOUT_PARAMETER = "http.connection-manager.timeout";
    private static final long HTTP_CONNECTION_MANAGER_TIMEOUT_MILLIS = 2000L;
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

    // mocks
    private HttpClient httpClient;
    private HttpClientParams httpClientParams;
    private HostConfiguration hostConfiguration;
    private PostMethod oauthPostMethod;

    private GusTransport gusTransport;

    @Before
    public void setUp() {
        httpClient = mock(HttpClient.class);
        httpClientParams = mock(HttpClientParams.class);
        hostConfiguration = mock(HostConfiguration.class);
        oauthPostMethod = mock(PostMethod.class);
    }

    @Test
    public void constructor_test() throws Exception {
        gusTransport = createGusTransportHappyCase();

        verify(httpClientParams).setParameter(HTTP_CONNECTION_MANAGER_TIMEOUT_PARAMETER, HTTP_CONNECTION_MANAGER_TIMEOUT_MILLIS);
        verify(httpClient).getHostConfiguration();
        verify(hostConfiguration).setProxy(PROXY_HOST, PROXY_PORT);
    }

    @Test
    public void constructor_testProxyHostStringAndProxyPortString() throws Exception {
        gusTransport = createGusTransportHappyCase(PROXY_HOST, new Integer(PROXY_PORT).toString());

        verify(httpClientParams).setParameter(HTTP_CONNECTION_MANAGER_TIMEOUT_PARAMETER, HTTP_CONNECTION_MANAGER_TIMEOUT_MILLIS);
        verify(httpClient).getHostConfiguration();
        verify(hostConfiguration).setProxy(PROXY_HOST, PROXY_PORT);
    }

    @Test
    public void constructor_testEmptyProxyHostString() throws Exception {
        gusTransport = createGusTransportHappyCase("   ", new Integer(PROXY_PORT).toString());

        verify(httpClientParams).setParameter(HTTP_CONNECTION_MANAGER_TIMEOUT_PARAMETER, HTTP_CONNECTION_MANAGER_TIMEOUT_MILLIS);
        verify(httpClient, never()).getHostConfiguration();
        verify(hostConfiguration, never()).setProxy(anyString(), anyInt());
    }

    @Test
    public void constructor_testEmptyProxyHost() throws Exception {
        gusTransport = createGusTransportHappyCase(Optional.empty(), Optional.of(PROXY_PORT));

        verify(httpClientParams).setParameter(HTTP_CONNECTION_MANAGER_TIMEOUT_PARAMETER, HTTP_CONNECTION_MANAGER_TIMEOUT_MILLIS);
        verify(httpClient, never()).getHostConfiguration();
        verify(hostConfiguration, never()).setProxy(anyString(), anyInt());
    }

    @Test
    public void constructor_testEmptyProxyPortString() throws Exception {
        gusTransport = createGusTransportHappyCase(PROXY_HOST, " ");

        verify(httpClientParams).setParameter(HTTP_CONNECTION_MANAGER_TIMEOUT_PARAMETER, HTTP_CONNECTION_MANAGER_TIMEOUT_MILLIS);
        verify(httpClient, never()).getHostConfiguration();
        verify(hostConfiguration, never()).setProxy(anyString(), anyInt());
    }

    @Test
    public void constructor_testEmptyProxyPort() throws Exception {
        gusTransport = createGusTransportHappyCase(Optional.of(PROXY_HOST), Optional.empty());

        verify(httpClientParams).setParameter(HTTP_CONNECTION_MANAGER_TIMEOUT_PARAMETER, HTTP_CONNECTION_MANAGER_TIMEOUT_MILLIS);
        verify(httpClient, never()).getHostConfiguration();
        verify(hostConfiguration, never()).setProxy(anyString(), anyInt());
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
                new EndpointInfo(DEFAULT_ENDPOINT, GusTransport.NO_TOKEN));
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
                new EndpointInfo(DEFAULT_ENDPOINT, GusTransport.NO_TOKEN));
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
                new EndpointInfo(DEFAULT_ENDPOINT, GusTransport.NO_TOKEN));
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
                new EndpointInfo(DEFAULT_ENDPOINT, GusTransport.NO_TOKEN));
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
                new EndpointInfo(DEFAULT_ENDPOINT, GusTransport.NO_TOKEN));
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
                new EndpointInfo(DEFAULT_ENDPOINT, GusTransport.NO_TOKEN));
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
                new EndpointInfo(DEFAULT_ENDPOINT, GusTransport.NO_TOKEN));
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
                new EndpointInfo(DEFAULT_ENDPOINT, GusTransport.NO_TOKEN));
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
                new EndpointInfo(DEFAULT_ENDPOINT, GusTransport.NO_TOKEN));
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
                new EndpointInfo(DEFAULT_ENDPOINT, GusTransport.NO_TOKEN));
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
                new EndpointInfo(DEFAULT_ENDPOINT, GusTransport.NO_TOKEN));
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
                null);
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
                new EndpointInfo("", GusTransport.NO_TOKEN));
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
                new EndpointInfo(null, GusTransport.NO_TOKEN));
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
                new EndpointInfo(DEFAULT_ENDPOINT, " "));
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
                new EndpointInfo(DEFAULT_ENDPOINT, null));
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

        verifyNew(PostMethod.class, times(1)).withArguments(AUTH_ENDPOINT);
        verify(httpClient, times(1)).executeMethod(oauthPostMethod);
        verify(oauthPostMethod, times(1)).getResponseBodyAsString();
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

        verifyNew(PostMethod.class, times(times)).withArguments(AUTH_ENDPOINT);
        verify(httpClient, times(times)).executeMethod(oauthPostMethod);
        verify(oauthPostMethod, times(times)).getResponseBodyAsString();
    }

    @Test
    public void getEndpointInfo_testFailToInitEndpointOnConstructionWithBadResponse() throws Exception {
        whenNew(PostMethod.class).withArguments(AUTH_ENDPOINT).thenReturn(oauthPostMethod);
        when(httpClient.executeMethod(oauthPostMethod)).thenReturn(401); // get token response code

        gusTransport = createGusTransport();
        EndpointInfo ei = gusTransport.getEndpointInfo();

        assertEquals(DEFAULT_ENDPOINT, ei.getEndPoint());
        assertEquals(GusTransport.NO_TOKEN, ei.getToken());

        verifyNew(PostMethod.class, times(1)).withArguments(AUTH_ENDPOINT);
        verify(httpClient, times(1)).executeMethod(oauthPostMethod);
        verify(oauthPostMethod, times(1)).getResponseBodyAsString();
    }

    @Test
    public void getEndpointInfo_testFailToRefreshEndpointWithBadResponse() throws Exception {
        boolean refresh = true;
        whenNew(PostMethod.class).withArguments(AUTH_ENDPOINT).thenReturn(oauthPostMethod);
        when(httpClient.executeMethod(oauthPostMethod)).thenReturn(200) // first time, return OK
                .thenReturn(401); // 2nd time, return bad response
        when(oauthPostMethod.getResponseBodyAsString())
                .thenReturn("{\"instance_url\": \"" + TEST_INSTANCE_URL +"\",  \"access_token\": \"" + TEST_TOKEN + "\"}")
                .thenReturn("bad response");

        gusTransport = createGusTransport();
        EndpointInfo ei = gusTransport.getEndpointInfo();

        assertEquals(TEST_INSTANCE_URL, ei.getEndPoint());
        assertEquals(TEST_TOKEN, ei.getToken());

        EndpointInfo ei2 = gusTransport.getEndpointInfo(refresh);
        assertSame(ei, ei2);

        verifyNew(PostMethod.class, times(2)).withArguments(AUTH_ENDPOINT);
        verify(httpClient, times(2)).executeMethod(oauthPostMethod);
        verify(oauthPostMethod, times(2)).getResponseBodyAsString();
    }

    private GusTransport createGusTransportHappyCase() throws Exception {
        return createGusTransportHappyCase(Optional.of(PROXY_HOST), Optional.of(PROXY_PORT));
    }

    private GusTransport createGusTransport() throws Exception {
        return createGusTransport(Optional.of(PROXY_HOST), Optional.of(PROXY_PORT));
    }

    private void mockCacheInitExpectations() throws Exception {
        // define mock behavior where cache init is successful
        whenNew(PostMethod.class).withArguments(AUTH_ENDPOINT).thenReturn(oauthPostMethod);
        when(httpClient.executeMethod(oauthPostMethod)).thenReturn(200); // get token response code
        when(oauthPostMethod.getResponseBodyAsString()).thenReturn(
                "{\"instance_url\": \"" + TEST_INSTANCE_URL +"\",  \"access_token\": \"" + TEST_TOKEN + "\"}");
    }

    private GusTransport createGusTransportHappyCase(String proxyHostString, String proxyPortString) throws Exception {
        mockCacheInitExpectations();
        mockHttpClientExpectations(Optional.ofNullable(proxyHostString),
                StringUtils.isNumeric(proxyPortString) ? Optional.of(Integer.parseInt(proxyPortString)) : Optional.empty());
        return new GusTransport(proxyHostString,
                proxyPortString,
                AUTH_ENDPOINT,
                AUTH_CLIENT_ID,
                AUTH_CLIENT_SECRET,
                AUTH_USERNAME,
                AUTH_PASSWORD,
                new EndpointInfo(DEFAULT_ENDPOINT, GusTransport.NO_TOKEN));
    }

    private GusTransport createGusTransportHappyCase(Optional<String> proxyHost, Optional<Integer> proxyPort) throws Exception {
        mockCacheInitExpectations();
        return createGusTransport(proxyHost, proxyPort);
    }

    private void mockHttpClientExpectations(Optional<String> proxyHost, Optional<Integer> proxyPort) throws Exception {
        // define mock behavior
        whenNew(HttpClient.class).withAnyArguments().thenReturn(httpClient);
        when(httpClient.getParams()).thenReturn(httpClientParams);
        if (proxyHost.isPresent() && proxyPort.isPresent()) {
            when(httpClient.getHostConfiguration()).thenReturn(hostConfiguration);
        }
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
                new EndpointInfo(DEFAULT_ENDPOINT, GusTransport.NO_TOKEN));
    }

}

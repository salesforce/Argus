package com.salesforce.dva.argus.service;

import com.salesforce.dva.argus.service.alert.notifier.GusTransport;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.DefaultProxyRoutePlanner;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

public class ArgusTransport {
    private static final Logger LOGGER = LoggerFactory.getLogger(ArgusTransport.class);
    private static final int CONNECTION_TIMEOUT_MILLIS = 10000;
    private static final int READ_TIMEOUT_MILLIS = 10000;

    protected final CloseableHttpClient httpClient;

    public ArgusTransport(Optional<String> proxyHost,
                          Optional<Integer> proxyPort,
                          int connectionPoolMaxSize,
                          int connectionPoolMaxPerRoute) {
        this.httpClient = buildHttpClient(proxyHost, proxyPort, connectionPoolMaxSize, connectionPoolMaxPerRoute);
    }

    public ArgusTransport(String proxyHost, String proxyPort, int connectionPoolMaxSize, int connectionPoolMaxPerRoute) {
        this(validateProxyHostAndPortStrings(proxyHost, proxyPort) ? Optional.of(proxyHost) : Optional.empty(),
                validateProxyHostAndPortStrings(proxyHost, proxyPort) ? Optional.of(Integer.parseInt(proxyPort)) : Optional.empty(),
                connectionPoolMaxSize, connectionPoolMaxPerRoute);
    }

    public static boolean validateProxyHostAndPortStrings(String proxyHost, String proxyPort) {
        requireArgument(StringUtils.isBlank(proxyPort) || StringUtils.isNumeric(proxyPort),
                "proxyPort must be numeric if present");
        return StringUtils.isNotBlank(proxyHost) && StringUtils.isNotBlank(proxyPort) && StringUtils.isNumeric(proxyPort);
    }

    /**
     * Get HttpClient.
     *
     * @return  HttpClient
     */
    public CloseableHttpClient getHttpClient() {
        return httpClient;
    }

    protected static SSLContext getSSLContext() {
        SSLContext sslContext = null;
        try {
            sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, null, null);
        } catch (NoSuchAlgorithmException | KeyManagementException e) {
            LOGGER.error("Failed to init SSLContext", e);
        }
        return sslContext;
    }

    protected static PoolingHttpClientConnectionManager buildConnectionManager(int connectionPoolMaxSize,
                                                                               int connectionPoolMaxPerRoute,
                                                                               SSLContext sslContext) {
        requireArgument(connectionPoolMaxSize > 0,
                String.format("connectionPoolMaxSize(%d) must be > 0", connectionPoolMaxSize));
        requireArgument(connectionPoolMaxPerRoute > 0,
                String.format("connectionPoolMaxPerRoute(%d) must be > 0", connectionPoolMaxPerRoute));

        RegistryBuilder<ConnectionSocketFactory> rb = RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory());
        if (sslContext != null) {
            rb.register("https", new SSLConnectionSocketFactory(sslContext));
        }
        Registry<ConnectionSocketFactory> r = rb.build();
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager(r);
        cm.setMaxTotal(connectionPoolMaxSize);
        cm.setDefaultMaxPerRoute(connectionPoolMaxPerRoute);
        LOGGER.info(String.format("Creating connection manager with maxPoolSize=%d, maxPerRoute=%d",
                connectionPoolMaxSize,
                connectionPoolMaxPerRoute));
        return cm;
    }

    protected static CloseableHttpClient buildHttpClient(Optional<String> proxyHost,
                                                  Optional<Integer> proxyPort,
                                                  int connectionPoolMaxSize,
                                                  int connectionPoolMaxPerRoute) {
        requireArgument(!proxyHost.isPresent() || StringUtils.isNotBlank(proxyHost.get()),
                String.format("proxyHost must not be blank if present", proxyHost.isPresent() ? proxyHost.get() : "null"));
        requireArgument(!proxyPort.isPresent() || proxyPort.get() > 0,
                String.format("proxyPort(%s) must > 0 if present", proxyPort.isPresent() ? proxyPort.get().toString() : "null"));

        SSLContext sslContext = getSSLContext();
        PoolingHttpClientConnectionManager cm = buildConnectionManager(connectionPoolMaxSize, connectionPoolMaxPerRoute, sslContext);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(CONNECTION_TIMEOUT_MILLIS)
                .setConnectionRequestTimeout(CONNECTION_TIMEOUT_MILLIS)
                .setSocketTimeout(READ_TIMEOUT_MILLIS)
                .build();

        HttpClientBuilder builder = HttpClients.custom()
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(cm);
        if (sslContext != null) {
            builder = builder
                    .setSSLContext(sslContext)
                    .setSSLHostnameVerifier(new NoopHostnameVerifier());
        }
        if (proxyHost.isPresent() && proxyHost.get().length() > 0 && proxyPort.isPresent()) {
            HttpHost proxy = new HttpHost(proxyHost.get(), proxyPort.get().intValue());
            DefaultProxyRoutePlanner routePlanner = new DefaultProxyRoutePlanner(proxy);
            builder = builder.setRoutePlanner(routePlanner);
        }
        return builder.build();
    }

}

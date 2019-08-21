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

package com.salesforce.dva.argus.service.alert.notifier;

import com.salesforce.dva.argus.system.SystemConfiguration;
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
import org.apache.http.ssl.SSLContexts;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.text.MessageFormat;

/**
 * Manage Refocus connection, proxy and timeouts.
 *
 * @author  Ian Keck (ikeck@salesforce.com)
 */
public class RefocusTransport
{

    private static final Logger _logger = LoggerFactory.getLogger(RefocusTransport.class);

    private static final int CONNECTION_TIMEOUT_MILLIS = 10000;
    private static final int READ_TIMEOUT_MILLIS = 10000;

    private SSLContext theSslContext = null;
    private boolean loadedSslContext = false;
    private PoolingHttpClientConnectionManager theConnectionManager = null;

    // make the class singleton
    private RefocusTransport()
    {
    }

    public static RefocusTransport getInstance()
    {
        return RefocusTransportHolder.INSTANCE;
    }

    private static class RefocusTransportHolder
    {
        private final static RefocusTransport INSTANCE = new RefocusTransport();
    }


    private SSLContext getSslContext(SystemConfiguration config)
    {
        if (!loadedSslContext )
        {
            loadedSslContext = true;
            try
            {
                KeyStore ks = readStore(config);
                if (ks != null)
                {
                    theSslContext = getCustomSSLContext(config, ks);
                }
                else // Default to TrustAll manager
                {
                    SSLContext sslContext = SSLContext.getInstance("TLS");
                    TrustManager[] temp = new TrustManager[] { new TrustAllManager() };
                    sslContext.init(null, temp, null);
                    theSslContext = sslContext;
                }
            } catch (Exception e)  // TODO - more correct exception capturing
            {
                // TODO - throw or log
            }
        }
        return theSslContext;
    }

    /**
     * Construct the connection manager from the system config params.
     *
     * @param config
     * @return the connection manager
     */
    private PoolingHttpClientConnectionManager getTheConnectionManager(SystemConfiguration config)
    {
        if (theConnectionManager == null)
        {
            SSLContext sslContext = getSslContext(config);

            RegistryBuilder rb = RegistryBuilder.<ConnectionSocketFactory>create();
            rb.register("http", PlainConnectionSocketFactory.getSocketFactory()); // register 2x?
            if (sslContext != null)
            {
                rb.register("https", new SSLConnectionSocketFactory(sslContext));
            }
            rb.register("http", new PlainConnectionSocketFactory()); // IMPORTANT - DEBUG - added - register http socket factory.
            Registry<ConnectionSocketFactory> rsf = rb.build();

            theConnectionManager = new PoolingHttpClientConnectionManager(rsf);
            theConnectionManager.setMaxTotal(200);
        }
        return theConnectionManager;
    }

    /**
     * Get HttpClient with proper proxy and timeout settings.
     *
     * @param config The system configuration.  Cannot be null.
     * @return HttpClient
     */
    public CloseableHttpClient getHttpClient(SystemConfiguration config)
    {

        CloseableHttpClient httpClient = null;
        try
        {
            SSLContext sslContext = getSslContext(config);
            PoolingHttpClientConnectionManager cm = getTheConnectionManager(config);

            RequestConfig requestConfig = RequestConfig.custom()
                    .setConnectTimeout(CONNECTION_TIMEOUT_MILLIS)
                    .setConnectionRequestTimeout(CONNECTION_TIMEOUT_MILLIS)
                    .setSocketTimeout(READ_TIMEOUT_MILLIS).build();

            DefaultProxyRoutePlanner routePlanner = getRoutePlanner(config);

            HttpClientBuilder builder = HttpClients.custom()
                                            .setDefaultRequestConfig(requestConfig)
                                            .setConnectionManager(cm);

            if (sslContext != null)
            {
                // TODO - add support for verification, read from config, which cert should be used?
                builder = builder
                        .setSSLContext(sslContext)
                        .setSSLHostnameVerifier(new NoopHostnameVerifier());
            }
            if (routePlanner != null)
            {
                builder = builder.setRoutePlanner(routePlanner);
            }
            httpClient = builder.build();

        } catch (Exception e)
        {
            // TODO - how to handle
        }
        return httpClient;
    }


    private KeyStore readStore(SystemConfiguration config) throws Exception
    {
        // TODO - replace with read key and cert file names and construct keystore (in memory) using fixed passwords.
        // See example in infra-security

        String keystorePath = config.getValue(RefocusProperty.REFOCUS_CUSTOM_KEYSTORE_PATH.getName(), RefocusProperty.REFOCUS_CUSTOM_KEYSTORE_PATH.getDefaultValue());
        String keystorePassword =  config.getValue(RefocusProperty.REFOCUS_CUSTOM_KEYSTORE_PASSWORD.getName(), RefocusProperty.REFOCUS_CUSTOM_KEYSTORE_PASSWORD.getDefaultValue());

        if (keystorePath.isEmpty() || keystorePassword.isEmpty())
        {
            return null;
        }

        _logger.info(MessageFormat.format("Refocus: Keystore={0} KeystorePW={1}", keystorePath, keystorePassword)); // DEBUG - IMPORTANT _ REMOVE

        try (InputStream keyStoreStream = this.getClass().getResourceAsStream(keystorePath))
        {
            KeyStore keyStore = KeyStore.getInstance("JKS"); // or "PKCS12" // TODO - what?
            keyStore.load(keyStoreStream, keystorePassword.toCharArray());
            return keyStore;
        }
    }

    private SSLContext getCustomSSLContext(SystemConfiguration config, KeyStore ks) throws Exception
    {
        String keyPassword = config.getValue(RefocusProperty.REFOCUS_CUSTOM_KEYSTORE_KEY_PASSWORD.getName(), RefocusProperty.REFOCUS_CUSTOM_KEYSTORE_KEY_PASSWORD.getDefaultValue());

        if (ks == null || keyPassword == null || keyPassword.isEmpty())
        {
            return null;
        }
        _logger.info(MessageFormat.format("Refocus: KeyPassword={0}", keyPassword)); // DEBUG - IMPORTANT _ REMOVE


        theSslContext = SSLContexts.custom()
                .loadKeyMaterial(ks, keyPassword.toCharArray()) // use null as second param if you don't have a separate key password
                .build();
        theSslContext.init(null, null, null);
        return theSslContext;
    }

    private DefaultProxyRoutePlanner getRoutePlanner(SystemConfiguration config) throws Exception
    {
        DefaultProxyRoutePlanner routePlanner = null;
        try
        {
            String proxyHost = config.getValue(RefocusProperty.REFOCUS_PROXY_HOST.getName(), RefocusProperty.REFOCUS_PROXY_HOST.getDefaultValue());
            String proxyPort = config.getValue(RefocusProperty.REFOCUS_PROXY_PORT.getName(), RefocusProperty.REFOCUS_PROXY_PORT.getDefaultValue());
            int port = proxyPort.isEmpty() ? -1 : Integer.parseInt(proxyPort);

            _logger.info(MessageFormat.format("Refocus: Proxy={0} Port={1}", proxyHost, port)); // DEBUG - IMPORTANT _ REMOVE

            if (proxyHost != null && !proxyHost.isEmpty() && port != -1)
            {
                HttpHost proxy = new HttpHost(proxyHost, port);
                routePlanner = new DefaultProxyRoutePlanner(proxy);
            }
        }
        catch(Exception e)
        {}

        return routePlanner;
    }



    // IMPORTANT - replace with a better trust manager! Necessary in PRD to Proxy?

    public class TrustAllManager implements X509TrustManager {
        public  void checkClientTrusted(X509Certificate[] cert, String authType) throws CertificateException {
        }

        public  void checkServerTrusted(X509Certificate[] cert, String authType) throws CertificateException {
        }

        public  X509Certificate[] getAcceptedIssuers() {
            return null;
        }
    }

}


/* Copyright (c) 2019, Salesforce.com, Inc.  All rights reserved. */

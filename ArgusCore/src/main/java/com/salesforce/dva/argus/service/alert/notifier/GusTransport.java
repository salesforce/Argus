package com.salesforce.dva.argus.service.alert.notifier;

import com.google.common.base.Supplier;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ExecutionError;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.salesforce.dva.argus.service.ArgusTransport;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Manage Gus connections, oAuth and timeouts.
 *
 */
public class GusTransport {
    //~ Static fields/initializers *******************************************************************************************************************
    public static final String NO_TOKEN = "NO_TOKEN";
    private static final Logger LOGGER = LoggerFactory.getLogger(GusTransport.class);
    private static final String HTTP_CONNECTION_MANAGER_TIMEOUT_PARAMETER = "http.connection-manager.timeout";
    private static final long HTTP_CONNECTION_MANAGER_TIMEOUT_MILLIS = 2000L;
    private static final String UTF_8 = "UTF-8";
    private static final long MIN_SESSION_REFRESH_THRESHOLD_MILLIS = 5 * 60 * 1000; // Wait 5min b/w refresh attempts
    private static final String DUMMY_CACHE_KEY = "endpoint"; // dummy key since we are only caching 1 value

    /*
     * Using a single entry cache to hold the current EndpointInfo and manage refreshes. Since the CacheLoader ignores
     * the key, there will only ever be 1 value. Therefore, a dummy key is used. If a dummy key is not used, every new
     * key will require a get() call from the Supplier instead of accessing the cached value under a diff key if it
     * already exists.
     *
     * LoadingCache was chosen over Supplier with expiry since refresh is supported in LoadingCache.
     * If LoadingCache.refresh() fails, the old value will continue to get used.
     */
    private final LoadingCache<String, EndpointInfo> endpointInfoCache;
    private final ArgusTransport transport;

    public GusTransport(Optional<String> proxyHost, Optional<Integer> proxyPort, Optional<String> proxyUsername,
                        Optional<String> proxyPassword, String authEndpoint, String authClientId, String authClientSecret,
                        String authUsername, String authPassword, EndpointInfo defaultEndpointInfo,
                        long tokenCacheRefreshPeriodMillis, int connectionPoolMaxSize, int connectionPoolMaxPerRoute) {
        requireArgument(StringUtils.isNotBlank(authEndpoint),
                String.format("authEndpoint(%s) must not be blank", authEndpoint));
        requireArgument(StringUtils.isNotBlank(authClientId),
                String.format("authClientId(%s) must not be blank", authClientId));
        requireArgument(StringUtils.isNotBlank(authClientSecret),
                String.format("authClientSecret(%s) must not be blank", authClientSecret));
        requireArgument(StringUtils.isNotBlank(authUsername),
                String.format("authUsername(%s) must not be blank", authUsername));
        requireArgument(StringUtils.isNotBlank(authPassword),
                String.format("authPassword(%s) must not be blank", authPassword));
        requireArgument(defaultEndpointInfo != null, "defaultEndpointInfo must not be null");
        requireArgument(StringUtils.isNotBlank(defaultEndpointInfo.getEndPoint()),
                String.format("defaultEndpointInfo.endpoint(%s) must not be blank", defaultEndpointInfo.getEndPoint()));
        requireArgument(StringUtils.isNotBlank(defaultEndpointInfo.getToken()),
                String.format("defaultEndpointInfo.token(%s) must not be blank", defaultEndpointInfo.getToken()));
        requireArgument(tokenCacheRefreshPeriodMillis > 0,
                String.format("cacheRefreshPeriodMillis(%d) must be > 0", tokenCacheRefreshPeriodMillis));

        this.transport = new ArgusTransport(proxyHost, proxyPort, proxyUsername, proxyPassword, connectionPoolMaxSize, connectionPoolMaxPerRoute);

        EndpointInfoSupplier supplier = new EndpointInfoSupplier(authEndpoint, authClientId,
                authClientSecret, authUsername, authPassword);
        this.endpointInfoCache = CacheBuilder.newBuilder()
                .refreshAfterWrite(tokenCacheRefreshPeriodMillis, TimeUnit.MILLISECONDS)
                .initialCapacity(1)
                .build(CacheLoader.from(supplier));
        // init cache
        try {
            this.endpointInfoCache.get(DUMMY_CACHE_KEY);
        } catch (ExecutionException | UncheckedExecutionException | ExecutionError e) {
            LOGGER.error("Failed to get auth token.  Exception: {}", e.getMessage());
            // put default if there is a failure on init
            this.endpointInfoCache.put(DUMMY_CACHE_KEY, defaultEndpointInfo);
        }
    }

    public GusTransport(Optional<String> proxyHost, Optional<Integer> proxyPort, Optional<String> proxyUsername, Optional<String> proxyPassword, String authEndpoint,
                        String authClientId, String authClientSecret, String authUsername, String authPassword,
                        EndpointInfo defaultEndpointInfo, int connectionPoolMaxSize, int connectionPoolMaxPerRoute) {
        this(proxyHost, proxyPort, proxyUsername, proxyPassword, authEndpoint, authClientId, authClientSecret, authUsername, authPassword,
                defaultEndpointInfo, MIN_SESSION_REFRESH_THRESHOLD_MILLIS, connectionPoolMaxSize, connectionPoolMaxPerRoute);
    }

    public GusTransport(String proxyHost, String proxyPort, String proxyUsername, String proxyPassword, String authEndpoint,
                        String authClientId, String authClientSecret, String authUsername, String authPassword,
                        EndpointInfo defaultEndpointInfo, int connectionPoolMaxSize, int connectionPoolMaxPerRoute) {
        this(ArgusTransport.validateProxyHostAndPortStrings(proxyHost, proxyPort) ? Optional.of(proxyHost) : Optional.empty(),
                ArgusTransport.validateProxyHostAndPortStrings(proxyHost, proxyPort) ? Optional.of(Integer.parseInt(proxyPort)) : Optional.empty(),
                ArgusTransport.validateProxyUsernameAndPassword(proxyUsername, proxyPassword) ? Optional.of(proxyUsername) : Optional.empty(),
                ArgusTransport.validateProxyUsernameAndPassword(proxyUsername, proxyPassword) ? Optional.of(proxyPassword) : Optional.empty(),
                authEndpoint, authClientId, authClientSecret, authUsername, authPassword, defaultEndpointInfo,
                connectionPoolMaxSize, connectionPoolMaxPerRoute);
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Get HttpClient.
     *
     * @return  HttpClient
     */
    public CloseableHttpClient getHttpClient() {
        return transport.getHttpClient();
    }

    public EndpointInfo getEndpointInfo() throws GetAuthenticationTokenFailureException {
        try {
            return endpointInfoCache.get(DUMMY_CACHE_KEY);
        } catch (ExecutionException | UncheckedExecutionException | ExecutionError e) {
            Throwable cause = e.getCause();
            if (cause instanceof GetAuthenticationTokenFailureRuntimeException) {
                throw new GetAuthenticationTokenFailureException(cause.getMessage(), cause.getCause());
            } else {
                throw new GetAuthenticationTokenFailureException("Getting auth token failed", e);
            }
        }
    }

    /**
     * Get authenticated endpoint and token.
     *
     * @param   refresh  - If true get a new token even if one exists.
     *
     * @return  EndpointInfo - with valid endpoint and token. The token can be a dummy or expired.
     */
    public EndpointInfo getEndpointInfo(boolean refresh) throws GetAuthenticationTokenFailureException  {
        if (refresh) {
            endpointInfoCache.refresh(DUMMY_CACHE_KEY);
        }
        return getEndpointInfo();
    }

    /**
     * Supplier for loading EndpointInfo (endpoint and token).
     */
    public class EndpointInfoSupplier implements Supplier<EndpointInfo> {
        private final String authEndpoint;
        private final String authClientId;
        private final String authClientSecret;
        private final String authUsername;
        private final String authPassword;

        public EndpointInfoSupplier(String authEndpoint, String authClientId, String authClientSecret, String authUsername, String authPassword) {
            this.authEndpoint = authEndpoint;
            this.authClientId = authClientId;
            this.authClientSecret = authClientSecret;
            this.authUsername = authUsername;
            this.authPassword = authPassword;
        }

        @Override
        public EndpointInfo get() {
            CloseableHttpResponse response = null;
            try {
                RequestBuilder rb = RequestBuilder.post()
                        .setUri(authEndpoint)
                        .addParameter("grant_type", "password")
                        .addParameter("client_id", URLEncoder.encode(authClientId, UTF_8))
                        .addParameter("client_secret", URLEncoder.encode(authClientSecret, UTF_8))
                        .addParameter("username", authUsername)
                        .addParameter("password", authPassword);

                response = transport.getHttpClient().execute(rb.build());
                int respCode = response.getStatusLine().getStatusCode();
                String responseBodyAsString = EntityUtils.toString(response.getEntity());

                // Check for success
                if (respCode == 200) {
                    JsonObject authResponse = new Gson().fromJson(responseBodyAsString, JsonObject.class);
                    String endpoint = authResponse.get("instance_url").getAsString();
                    String token = authResponse.get("access_token").getAsString();

                    LOGGER.info("Success - getting access_token for endpoint '{}'", endpoint);
                    LOGGER.debug("access_token '{}'", token);
                    return new EndpointInfo(endpoint, token);
                } else {
                    String errorMessage = String.format("Failure - getting oauth2 token (responseCode=%d), check username/password: '%s'",
                            respCode,
                            responseBodyAsString);
                    LOGGER.error(errorMessage);
                    throw new GetAuthenticationTokenFailureRuntimeException(errorMessage);
                }
            } catch (GetAuthenticationTokenFailureRuntimeException e) {
                throw e;
            } catch (Exception e) {
                LOGGER.error("Failure - exception getting access_token '{}'", e);
                throw new GetAuthenticationTokenFailureRuntimeException("Failure - exception getting access_token", e);
            } finally {
                if (response != null) {
                    try {
                        response.close();
                    } catch (IOException e) {
                        LOGGER.error("Exception while attempting to close response", e);
                    }
                }
            }
        }
    }

    /**
     * Utility class for endpoint information.
     *
     * @author  fiaz.hossain
     */
    public static class EndpointInfo {

        private final String endPoint;
        private final String token;

        protected EndpointInfo(final String endPoint, final String token) {
            this.endPoint = endPoint;
            this.token = token;
        }

        /**
         * Valid endpoint. Either from config or endpont after authentication
         *
         * @return  endpoint
         */
        public String getEndPoint() {
            return endPoint;
        }

        /**
         * Token can be either active, expired or a dummy value.
         *
         * @return  token
         */
        public String getToken() {
            return token;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            EndpointInfo that = (EndpointInfo) o;
            return Objects.equals(endPoint, that.endPoint) &&
                    Objects.equals(token, that.token);
        }

        @Override
        public int hashCode() {
            return Objects.hash(endPoint, token);
        }
    }

    /**
     * Exception (checked) for failures when attempting to get a new auth token.
     */
    public static class GetAuthenticationTokenFailureException extends Exception {
        public GetAuthenticationTokenFailureException(String message) {
            super(message);
        }
        public GetAuthenticationTokenFailureException(String message, Throwable cause) { super(message, cause); }
        public GetAuthenticationTokenFailureException(Throwable cause) {
            super(cause);
        }
    }

    /**
     * RuntimeException for failures when attempting to get a new auth token (meant to be used internally).
     */
    private static class GetAuthenticationTokenFailureRuntimeException extends RuntimeException {
        public GetAuthenticationTokenFailureRuntimeException(String message) {
            super(message);
        }
        public GetAuthenticationTokenFailureRuntimeException(String message, Throwable cause) {
            super(message, cause);
        }
        public GetAuthenticationTokenFailureRuntimeException(Throwable cause) {
            super(cause);
        }
    }
}

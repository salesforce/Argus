package com.salesforce.dva.argus.service.alert.notifier;

import com.google.common.base.Supplier;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.util.concurrent.ExecutionError;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpConnectionManager;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.lang.StringUtils;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final int CONNECTION_TIMEOUT_MILLIS = 10000;
    private static final int READ_TIMEOUT_MILLIS = 10000;
    private static final String DUMMY_CACHE_KEY = "endpoint"; // dummy key since we are only caching 1 value

    private final HttpConnectionManager connectionManager;
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
    private final Optional<String> proxyHost;
    private final Optional<Integer> proxyPort;

    public GusTransport(Optional<String> proxyHost, Optional<Integer> proxyPort, String authEndpoint,
                        String authClientId, String authClientSecret, String authUsername, String authPassword,
                        EndpointInfo defaultEndpointInfo, long tokenCacheRefreshPeriodMillis) {
        requireArgument(!proxyHost.isPresent() || StringUtils.isNotBlank(proxyHost.get()),
                String.format("proxyHost must not be blank if present", proxyHost.isPresent() ? proxyHost.get() : "null"));
        requireArgument(!proxyPort.isPresent() || proxyPort.get() > 0,
                String.format("proxyPort(%s) must > 0 if present", proxyPort.isPresent() ? proxyPort.get().toString() : "null"));
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

        this.proxyHost = proxyHost;
        this.proxyPort = proxyPort;
        this.connectionManager = new MultiThreadedHttpConnectionManager();
        HttpConnectionManagerParams params = connectionManager.getParams();
        params.setConnectionTimeout(CONNECTION_TIMEOUT_MILLIS);
        params.setSoTimeout(READ_TIMEOUT_MILLIS);

        EndpointInfoSupplier supplier = new EndpointInfoSupplier(authEndpoint, authClientId,
                authClientSecret, authUsername, authPassword);
        this.endpointInfoCache = CacheBuilder.<String, EndpointInfo>newBuilder()
                .refreshAfterWrite(tokenCacheRefreshPeriodMillis, TimeUnit.MILLISECONDS)
                .initialCapacity(1)
                .build(CacheLoader.from(supplier));
        // init cache
        try {
            this.endpointInfoCache.get(DUMMY_CACHE_KEY);
        } catch (ExecutionException | UncheckedExecutionException | ExecutionError e) {
            LOGGER.error("Failed to get auth token.", e);
            // put default if there is a failure on init
            this.endpointInfoCache.put(DUMMY_CACHE_KEY, defaultEndpointInfo);
        }
    }

    public GusTransport(Optional<String> proxyHost, Optional<Integer> proxyPort, String authEndpoint,
                        String authClientId, String authClientSecret, String authUsername, String authPassword,
                        EndpointInfo defaultEndpointInfo) {
        this(proxyHost, proxyPort, authEndpoint, authClientId, authClientSecret, authUsername, authPassword,
                defaultEndpointInfo, MIN_SESSION_REFRESH_THRESHOLD_MILLIS);
    }

    public GusTransport(String proxyHost, String proxyPort, String authEndpoint,
                        String authClientId, String authClientSecret, String authUsername, String authPassword,
                        EndpointInfo defaultEndpointInfo) {
        this(validateProxyHostAndPortStrings(proxyHost, proxyPort) ? Optional.of(proxyHost) : Optional.empty(),
                validateProxyHostAndPortStrings(proxyHost, proxyPort) ? Optional.of(Integer.parseInt(proxyPort)) : Optional.empty(),
                authEndpoint, authClientId, authClientSecret, authUsername, authPassword, defaultEndpointInfo);
    }

    private static boolean validateProxyHostAndPortStrings(String proxyHost, String proxyPort) {
        requireArgument(StringUtils.isBlank(proxyPort) || StringUtils.isNumeric(proxyPort),
                "proxyPort must be numeric if present");
        return StringUtils.isNotBlank(proxyHost) && StringUtils.isNotBlank(proxyPort) && StringUtils.isNumeric(proxyPort);
    }

    //~ Methods **************************************************************************************************************************************

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
     * Get HttpClient.
     *
     * @return  HttpClient
     */
    public HttpClient getHttpClient() {
        HttpClient httpclient = new HttpClient(connectionManager);
        // Wait for 2 seconds to get a connection from pool
        httpclient.getParams().setParameter(HTTP_CONNECTION_MANAGER_TIMEOUT_PARAMETER, HTTP_CONNECTION_MANAGER_TIMEOUT_MILLIS);
        if (proxyHost.isPresent() && proxyHost.get().length() > 0 && proxyPort.isPresent()) {
            httpclient.getHostConfiguration().setProxy(proxyHost.get(), proxyPort.get());
        }
        return httpclient;
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
            PostMethod post = new PostMethod(authEndpoint);

            try {
                post.addParameter("grant_type", "password");
                post.addParameter("client_id", URLEncoder.encode(authClientId, UTF_8));
                post.addParameter("client_secret", URLEncoder.encode(authClientSecret, UTF_8));
                post.addParameter("username", authUsername);
                post.addParameter("password", authPassword);
                int respCode = getHttpClient().executeMethod(post);

                // Check for success
                if (respCode == 200) {
                    JsonObject authResponse = new Gson().fromJson(post.getResponseBodyAsString(), JsonObject.class);
                    String endpoint = authResponse.get("instance_url").getAsString();
                    String token = authResponse.get("access_token").getAsString();

                    LOGGER.info("Success - getting access_token for endpoint '{}'", endpoint);
                    LOGGER.debug("access_token '{}'", token);
                    return new EndpointInfo(endpoint, token);
                } else {
                    String errorMessage = String.format("Failure - getting oauth2 token (responseCode=%d), check username/password: '%s'",
                            respCode,
                            post.getResponseBodyAsString());
                    LOGGER.error(errorMessage);
                    throw new GetAuthenticationTokenFailureRuntimeException(errorMessage);
                }
            } catch (GetAuthenticationTokenFailureRuntimeException e) {
                throw e;
            } catch (Exception e) {
                LOGGER.error("Failure - exception getting access_token '{}'", e);
                throw new GetAuthenticationTokenFailureRuntimeException("Failure - exception getting access_token", e);
            } finally {
                post.releaseConnection();
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

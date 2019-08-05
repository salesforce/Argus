package com.salesforce.dva.argus.service.metric.metadata;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import com.salesforce.sds.keystore.DynamicKeyStore;
import com.salesforce.sds.keystore.DynamicKeyStoreBuilder;
import com.salesforce.sds.pki.utils.BouncyIntegration;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.annotation.meta.field;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DefaultIDBClient implements IDBClient {
    static final String BASE_URL_FORMAT = "%s/cidb-api/%s/1.04/%s?name=%s";
    static final String HOST_RESOURCE = "hosts";
    static final String CLUSTER_RESOURCE = "clusters";
    final Logger logger = LoggerFactory.getLogger(IDBMetadataService.class);
    ExecutorService executorService;
    String idbEndpoint;
    CloseableHttpClient client;
    ObjectMapper mapper = new ObjectMapper();
    DynamicKeyStore keystore;

    static {
        BouncyIntegration.init();
    }

    @Inject
    public DefaultIDBClient(SystemConfiguration config) {
        idbEndpoint = config.getValue(Property.IDB_ENDPOINT.getName(),
                Property.IDB_ENDPOINT.getDefaultValue());
        int connectionCount = Integer.valueOf(config.getValue(Property.IDB_CONN_COUNT.getName(),
                Property.IDB_CONN_COUNT.getDefaultValue()));
        executorService = Executors.newFixedThreadPool(connectionCount);
        client = createClient(config, connectionCount);
    }

    CloseableHttpClient createClient(SystemConfiguration config, int connectionCount) {
        HttpClientBuilder clientBuilder = HttpClients.custom();
        PoolingHttpClientConnectionManager connManager;
        try {
            keystore = new DynamicKeyStoreBuilder()
                    .withMonitoredDirectory(config.getValue(Property.IDB_KEYSTORE_MONITORED_DIRECTORY.getName(),
                            Property.IDB_KEYSTORE_MONITORED_DIRECTORY.getDefaultValue()))
                    .withCADirectory(config.getValue(Property.IDB_CA_DIRECTORY.getName(),
                            Property.IDB_CA_DIRECTORY.getDefaultValue()))
                    .withStartThread(true).build();
            SSLContext sslContext = keystore.getSSLContext();
            SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext);
            clientBuilder.setSSLContext(sslContext);
            clientBuilder.setSSLSocketFactory(sslsf);
            Registry<ConnectionSocketFactory> socketFactoryRegistry = RegistryBuilder.<ConnectionSocketFactory> create().register("https", sslsf).build();
            connManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
            logger.info("IDBClient DynamicKeyStore initialized successfully");
        } catch (Exception ex) {
            connManager = new PoolingHttpClientConnectionManager();
            logger.warn("DefaultIDBClient failed to init HttpClient with DynamicKeyStore. HTTPS will not work ", ex);
        }
        connManager.setMaxTotal(connectionCount);
        int connectTimeout = Integer.valueOf(config.getValue(Property.IDB_CONN_TIMEOUT.getName(),
                Property.IDB_CONN_TIMEOUT.getDefaultValue()));
        int socketTimeout = Integer.valueOf(config.getValue(Property.IDB_SOCKET_TIMEOUT.getName(),
                Property.IDB_SOCKET_TIMEOUT.getDefaultValue()));
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(connectTimeout)
                .setSocketTimeout(socketTimeout).build();
        return clientBuilder
                .setDefaultRequestConfig(requestConfig)
                .setConnectionManager(connManager)
                .build();
    }

    @Override
    public Map<IDBFieldQuery, Optional<String>> get(Collection<IDBFieldQuery> queries) {
        String requestUrl;
        Map<IDBFieldQuery, Future<String>> futures = new HashMap<>();
        for (IDBFieldQuery query: queries) {
            if (query.getType().equals(IDBFieldQuery.ResourceType.HOST)) {
                String hostnameWithoutDomains = query.getResourceName().split("\\.")[0];
                requestUrl = String.format(BASE_URL_FORMAT, idbEndpoint, query.getDatacenter(), HOST_RESOURCE, hostnameWithoutDomains);
            } else {
                requestUrl = String.format(BASE_URL_FORMAT, idbEndpoint, query.getDatacenter(), CLUSTER_RESOURCE, query.getResourceName());
            }
            futures.put(query, executorService.submit(new IDBClientWorker(requestUrl, query.getResourceName(), query.getField())));
        }
        Map<IDBFieldQuery, Optional<String>> fieldValues = new HashMap<>();
        futures.forEach((query, future) -> {
            try {
                fieldValues.put(query, Optional.ofNullable(future.get()));
            } catch (InterruptedException | ExecutionException ex) {
                logger.warn("Exception during IDB API future.get: ", ex);
            }
        });
        return fieldValues;
    }

    // Separated for unit tests
    JsonNode httpGetJson(String requestUrl) {
        CloseableHttpResponse response;
        try {
            response = client.execute(new HttpGet(requestUrl));
        } catch (IOException ex) {
            logger.warn("IOException while calling IDB API: ", ex);
            return null;
        }
        JsonNode tree;
        int status = response.getStatusLine().getStatusCode();
        if (status >= 200 && status < 300) {
            try {
                tree = mapper.readTree(response.getEntity().getContent());
            } catch (IOException ex) {
                logger.warn("IOException while getting IDB response content", ex);
                tree = null;
            }
        } else {
            tree = null;
        }
        EntityUtils.consumeQuietly(response.getEntity());
        return tree;
    }

    @Override
    public Properties getIDBClientProperties() {
        Properties serviceProps= new Properties();

        for (Property property: Property.values()){
            serviceProps.put(property.getName(), property.getDefaultValue());
        }
        return serviceProps;
    }

    class IDBClientWorker implements Callable<String> {
        String requestUrl, resourceName, field;

        public IDBClientWorker(String requestUrl, String resourceName, String field) {
            this.requestUrl = requestUrl;
            this.resourceName = resourceName;
            this.field = field;
        }

        @Override
        public String call() {
            JsonNode tree = httpGetJson(requestUrl);
            if (tree == null) {
                return null;
            }
            if (tree.get("success").equals("false")) {
                logger.warn("Error from IDB API: " + tree.get("message"));
            } else if (tree.get("success") == null) {
                logger.warn("Unexpected IDB API response: missing key 'success'");
            } else if (tree.get("data") == null || !tree.get("data").isArray()) {
                logger.warn("Unexpected IDB API response: expecting an array for key 'data': " + tree.toString());
            } else if (tree.get("data").size() == 0) {
                logger.warn("IDB " + field + " resource " + resourceName + " not found");
            } else {
                JsonNode firstResult = tree.get("data").iterator().next();
                if (firstResult.get(field) == null) {
                    logger.warn("IDB " + field + " not found for resource " + resourceName);
                    return null;
                }
                return firstResult.get(field).asText();
            }
            return null;
        }
    }

    public enum Property {

        IDB_ENDPOINT("service.property.idbclient.endpoint", "https://cfg0-cidbapima1-0-prd.data.sfdc.net:443"),
        IDB_CONN_COUNT("service.property.idbclient.conn.count", "30"),
        IDB_KEYSTORE_MONITORED_DIRECTORY("service.property.idbclient.keystore.monitored.directory", "/etc/pki_service/sfdc/argus-ajnaconsumer"),
        IDB_CA_DIRECTORY("service.property.idbclient.ca.directory", "/etc/pki_service/ca"),
        IDB_CACHE_TTL_SECS("service.property.idbclient.cache.ttl.secs", "1800"),
        IDB_CONN_TIMEOUT("service.property.idbclient.conn.timeout", "30000"),
        IDB_SOCKET_TIMEOUT("service.property.idbclient.socket.timeout", "30000");

        private final String _name;
        private final String _defaultValue;

        Property(String name, String defaultValue) {
            _name = name;
            _defaultValue = defaultValue;
        }

        /**
         * Returns the property name.
         *
         * @return  The property name.
         */
        public String getName() {
            return _name;
        }

        /**
         * Returns the default value for the property.
         *
         * @return  The default value.
         */
        public String getDefaultValue() {
            return _defaultValue;
        }
    }
}

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

package com.salesforce.dva.argus.service.image;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.ImageStorageService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.schema.ElasticSearchUtils;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseListener;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;


/**
 * ElasticSearch implementation of Image Service to store and query image.
 *
 * @author Chandravyas Annakula (cannakula@salesforce.com)
 */

@Singleton
public class ElasticSearchImageService extends DefaultService implements ImageStorageService {

    private static Logger logger = LoggerFactory.getLogger(ElasticSearchImageService.class);
    private static ObjectMapper genericObjectMapper = new ObjectMapper();

    protected final MonitorService monitorService;
    /** Global ES properties */
    private static final int IMAGE_MAX_RETRY_TIMEOUT = 300 * 1000;
    private static final int  IMAGE_INDEX_MAX_RESULT_WINDOW = 10000;
    private static final String FIELD_TYPE_BINARY = "binary";
    private static final String FIELD_TYPE_DATE ="date";
    private static final String PUT_REQUEST_URL= "_bulk";

    private RestClient esRestClient;

    /** Image index properties */
    private static String imageIndexTemplateName;
    public static String imageIndexTemplatePatternStart;
    public static String imageIndexType;
    private static String imageSearchRequestURL;
    private final int replicationFactorForImageIndex;
    private final int numShardsForImageIndex;
    private final ObjectMapper imageMapper;

    @Inject
    protected ElasticSearchImageService(SystemConfiguration config,MonitorService monitorService, ElasticSearchUtils esUtils) {
        super(config);
        this.monitorService = monitorService;
        /** Setup Global ES stuff */
        String[] nodes = config.getValue(ElasticSearchImageService.Property.IMAGE_ES_ENDPOINT.getName(), ElasticSearchImageService.Property.IMAGE_ES_ENDPOINT.getDefaultValue()).split(",");
        HttpHost[] httpHosts = new HttpHost[nodes.length];
        for(int i=0; i<nodes.length; i++) {
            try {
                URL url = new URL(nodes[i]);
                httpHosts[i] = new HttpHost(url.getHost(), url.getPort());
            } catch (MalformedURLException e) {
                logger.error("One or more ElasticSearch endpoints are malformed. "
                        + "If you have configured only a single endpoint, then ESImageService will not function.", e);
            }
        }
        RestClientBuilder.HttpClientConfigCallback clientConfigCallback = httpClientBuilder -> {
            try {
                int connCount = Integer.parseInt(config.getValue(ElasticSearchImageService.Property.IMAGE_ES_CONNECTION_COUNT.getName(),
                        ElasticSearchImageService.Property.IMAGE_ES_CONNECTION_COUNT.getDefaultValue()));
                PoolingNHttpClientConnectionManager connMgr =
                        new PoolingNHttpClientConnectionManager(new DefaultConnectingIOReactor());
                connMgr.setMaxTotal(connCount);
                connMgr.setDefaultMaxPerRoute(connCount/httpHosts.length < 1 ? 1:connCount/httpHosts.length);
                httpClientBuilder.setConnectionManager(connMgr);
                return httpClientBuilder;
            } catch(Exception e) {
                throw new SystemException(e);
            }
        };
        RestClientBuilder.RequestConfigCallback requestConfigCallback = requestConfigBuilder -> {
            int connTimeout = Integer.parseInt(config.getValue(ElasticSearchImageService.Property.IMAGE_ES_ENDPOINT_CONNECTION_TIMEOUT_MILLIS.getName(),
                    ElasticSearchImageService.Property.IMAGE_ES_ENDPOINT_CONNECTION_TIMEOUT_MILLIS.getDefaultValue()));
            int socketTimeout = Integer.parseInt(config.getValue(ElasticSearchImageService.Property.IMAGE_ES_ENDPOINT_SOCKET_TIMEOUT.getName(),
                    ElasticSearchImageService.Property.IMAGE_ES_ENDPOINT_SOCKET_TIMEOUT.getDefaultValue()));
            requestConfigBuilder.setConnectTimeout(connTimeout).setSocketTimeout(socketTimeout);

            logger.info("esRestClient set connectionTimeoutMillis {} socketTimeoutMillis {}",
                    connTimeout, socketTimeout);

            return requestConfigBuilder;
        };
        esRestClient = RestClient.builder(httpHosts)
                .setHttpClientConfigCallback(clientConfigCallback)
                .setRequestConfigCallback(requestConfigCallback)
                .setMaxRetryTimeoutMillis(IMAGE_MAX_RETRY_TIMEOUT)
                .build();
        logger.info("esRestClient set MaxRetryTimeoutsMillis {}", IMAGE_MAX_RETRY_TIMEOUT);

        /** Set up image index stuff */
        imageMapper = getImageObjectMapper(new ImageRecord.IndexSerializer());
        imageIndexType = config.getValue(Property.IMAGE_ES_INDEX_TYPE.getName(),
                ElasticSearchImageService.Property.IMAGE_ES_INDEX_TYPE.getDefaultValue());
        imageIndexTemplateName = config.getValue(ElasticSearchImageService.Property.IMAGE_ES_INDEX_TEMPLATE_NAME.getName(),
                ElasticSearchImageService.Property.IMAGE_ES_INDEX_TEMPLATE_NAME.getDefaultValue());
        imageIndexTemplatePatternStart = config.getValue(ElasticSearchImageService.Property.IMAGE_ES_INDEX_TEMPLATE_PATTERN_START.getName(),
                ElasticSearchImageService.Property.IMAGE_ES_INDEX_TEMPLATE_PATTERN_START.getDefaultValue());
        imageSearchRequestURL = String.format("/%s-*/_search", imageIndexTemplatePatternStart);
        replicationFactorForImageIndex = Integer.parseInt(
                config.getValue(ElasticSearchImageService.Property.IMAGE_ES_NUM_REPLICAS.getName(), ElasticSearchImageService.Property.IMAGE_ES_NUM_REPLICAS.getDefaultValue()));
        numShardsForImageIndex = Integer.parseInt(
                config.getValue(ElasticSearchImageService.Property.IMAGE_ES_SHARDS_COUNT.getName(), ElasticSearchImageService.Property.IMAGE_ES_SHARDS_COUNT.getDefaultValue()));
        esUtils.createIndexTemplate(esRestClient,
                imageIndexTemplateName,
                imageIndexTemplatePatternStart,
                this::createImageIndexTemplateSettingsNode,
                this::createImageIndexTemplateMappingNode);
    }

    @Override
    public void putImage(String imageId,byte[] imageBytes, boolean sync) {

        try {
            ImageRecord indexImageRecord = new ImageRecord(imageId,imageBytes);
            String requestBody = imageMapper.writeValueAsString(indexImageRecord);
            Request request = new Request(ElasticSearchUtils.HttpMethod.POST.getName(), PUT_REQUEST_URL);
            request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
            if (sync) {
                performESRequest(request);
            }
            else{
                performAsyncESRequest(request);
            }

        } catch (IOException e) {
            logger.error("Failed to index image record to ES.",e);
            throw new SystemException("Failed to index image record to ES.", e);
        }

    }

    @Override
    public byte[] getImage(String imageId) {
        requireNotDisposed();
        requireArgument(imageId != null, "imageId cannot be null");
        ImageRecord record=null;
        try{
                String queryJson = constructSimpleIdSearchQuery(imageId).toString();
                final long start = System.currentTimeMillis();
                Request request = new Request(ElasticSearchUtils.HttpMethod.POST.getName(), imageSearchRequestURL);
                request.setEntity(new StringEntity(queryJson, ContentType.APPLICATION_JSON));
                Response response = esRestClient.performRequest(request);
                final long time = System.currentTimeMillis() - start;
                logger.info("ES get request completed in {} ms", time);
                String str = extractResponse(response);
                record = ElasticSearchUtils.toEntity(str, new TypeReference<ImageRecord>() {},imageMapper);
        } catch(IOException ex) {
            logger.error("Exception while retrieving the image",ex);
            throw new SystemException("Exception while retrieving the image",ex);
        }
        return record.getImageBytes();
    }


    private void performESRequest(Request request) throws IOException {
        Response response = esRestClient.performRequest(request);
        String strResponse = extractResponse(response);
        ElasticSearchUtils.PutResponse putResponse = genericObjectMapper.readValue(strResponse, ElasticSearchUtils.PutResponse.class);
        parseResponseErrors(putResponse);
    }

    private void parseResponseErrors(ElasticSearchUtils.PutResponse putResponse) throws JsonProcessingException {

        if (putResponse.isErrors()) {
            for (ElasticSearchUtils.PutResponse.Item item : putResponse.getItems()) {
                if (item.getIndex() != null && item.getIndex().getStatus() != HttpStatus.SC_CREATED) {
                    logger.warn("Failed to add image record with id {} to index. Reason: {}",
                            item.getIndex().get_id(),
                            imageMapper.writeValueAsString(item.getIndex().getError()));
                    monitorService.modifyCounter(MonitorService.Counter.IMAGE_RECORDS_WRITE_FAILURES, 1, null);

                }
            }
        }
    }

    private void performAsyncESRequest(Request request) {
        esRestClient.performRequestAsync(request,new ResponseListener() {
            @Override
            public void onSuccess(Response response) {
                String strResponse = extractResponse(response);
                try {
                    ElasticSearchUtils.PutResponse putResponse = genericObjectMapper.readValue(strResponse, ElasticSearchUtils.PutResponse.class);
                    parseResponseErrors(putResponse);
                } catch (IOException exception) {
                    logger.error("Failed to parse the Elastic Search response in Async call.", exception);
                }

            }

            @Override
            public void onFailure(Exception exception) {
                logger.error("Failed to index image record to ES in Async.", exception);
                throw new SystemException("Failed to index image record to ES in Async.", exception);
            }
        });
    }

    private ObjectNode constructSimpleIdSearchQuery(String imageId) {

        ArrayNode valuesNode = genericObjectMapper.createArrayNode();
        valuesNode.add(imageId);

        ObjectNode idsNode = genericObjectMapper.createObjectNode();
        idsNode.set("values", valuesNode);

        ObjectNode queryNode = genericObjectMapper.createObjectNode();
        queryNode.set("ids", idsNode);

        ObjectNode  searchQuery= genericObjectMapper.createObjectNode();
        searchQuery.set("query",queryNode);

        return searchQuery;
    }

    private ObjectNode createFieldNodeNoAnalyzer(String type) {
        ObjectNode fieldNode = genericObjectMapper.createObjectNode();
        fieldNode.put("type", type);
        return fieldNode;
    }

    private ObjectNode createImageIndexTemplateSettingsNode() {
        ObjectNode indexNode = genericObjectMapper.createObjectNode();
        indexNode.put("max_result_window", IMAGE_INDEX_MAX_RESULT_WINDOW);
        indexNode.put("number_of_replicas", replicationFactorForImageIndex);
        indexNode.put("number_of_shards", numShardsForImageIndex);

        ObjectNode settingsNode = genericObjectMapper.createObjectNode();
        settingsNode.set("index", indexNode);

        return settingsNode;
    }

    private ObjectNode createImageIndexTemplateMappingNode() {
        ObjectNode propertiesNode = genericObjectMapper.createObjectNode();
        propertiesNode.set(ImageRecord.ImageRecordType.IMAGEBLOB.getName(), createFieldNodeNoAnalyzer(FIELD_TYPE_BINARY));
        propertiesNode.set(ImageRecord.ImageRecordType.MTS.getName(), createFieldNodeNoAnalyzer(FIELD_TYPE_DATE));

        ObjectNode docNode = genericObjectMapper.createObjectNode();
        docNode.set("properties", propertiesNode);

        ObjectNode mappingsNode = genericObjectMapper.createObjectNode();
        mappingsNode.set(imageIndexType, docNode);

        return mappingsNode;
    }

    /** Converting static call to instance method call to make this unit testable
     * Helper to process the response. <br><br>
     * @param   response ES response
     * @return  Stringified response
     */
    public String extractResponse(Response response) {
        return ElasticSearchUtils.extractResponse(response);
    }


    @Override
    public void dispose() {
        requireNotDisposed();
        shutdownElasticSearchService();
    }

    private void shutdownElasticSearchService(){
        try {
            esRestClient.close();
            logger.info("Shutdown of ElasticSearch RESTClient complete");
        } catch (IOException e) {
            logger.warn("ElasticSearch RestClient failed to shutdown properly.", e);
        }
    }

    /* Method to change the rest client. Used for testing. */
    protected void setESRestClient(RestClient restClient){
        this.esRestClient = restClient;
    }


    @VisibleForTesting
    static ObjectMapper getImageObjectMapper(JsonSerializer<ImageRecord> serializer) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        SimpleModule module = new SimpleModule();
        module.addSerializer(ImageRecord.class, serializer);
        module.addDeserializer(ImageRecord.class, new ImageRecord.Deserializer());
        mapper.registerModule(module);

        return mapper;
    }


    /**
     * The set of implementation specific configuration properties.
     *
     */
    public enum Property {

        IMAGE_ES_ENDPOINT("service.property.image.elasticsearch.endpoint", "http://localhost:9200,http://localhost:9201"),
        /** Connection timeout for ES REST client. */
        IMAGE_ES_ENDPOINT_CONNECTION_TIMEOUT_MILLIS("service.property.image.elasticsearch.endpoint.connection.timeout.millis", "10000"),
        /** Socket connection timeout for ES REST client. */
        IMAGE_ES_ENDPOINT_SOCKET_TIMEOUT("service.property.image.elasticsearch.endpoint.socket.timeout", "10000"),
        /** Connection count for ES REST client. */
        IMAGE_ES_CONNECTION_COUNT("service.property.image.elasticsearch.connection.count", "10"),
        /** Replication factor */
        IMAGE_ES_NUM_REPLICAS("service.property.image.elasticsearch.num.replicas", "1"),
        /** Shard count */
        IMAGE_ES_SHARDS_COUNT("service.property.image.elasticsearch.shards.count", "6"),
        /** Index type */
        IMAGE_ES_INDEX_TYPE("service.property.image.elasticsearch.index.type", "_doc"),
        /** Index template name */
        IMAGE_ES_INDEX_TEMPLATE_NAME("service.property.image.elasticsearch.indextemplate.name", "argus-image-template"),
        /** Index template pattern match */
        IMAGE_ES_INDEX_TEMPLATE_PATTERN_START("service.property.image.elasticsearch.indextemplate.patternstart", "argus-image");

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

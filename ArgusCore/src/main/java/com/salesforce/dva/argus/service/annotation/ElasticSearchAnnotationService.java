package com.salesforce.dva.argus.service.annotation;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.annotation.AnnotationRecordList.HashAlgorithm;
import com.salesforce.dva.argus.service.AnnotationStorageService;
import com.salesforce.dva.argus.service.schema.ElasticSearchUtils;
import com.salesforce.dva.argus.service.tsdb.AnnotationQuery;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;

/**
 * The Elastic Search implementation of the annotation storage service.
 *
 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
 */
@Singleton
public class ElasticSearchAnnotationService extends DefaultService implements AnnotationStorageService {
    private static Logger logger = LoggerFactory.getLogger(ElasticSearchAnnotationService.class);
    private static ObjectMapper genericObjectMapper = new ObjectMapper();

    protected final MonitorService monitorService;
    
    /** Global ES properties */
    private static final int ANNOTATION_MAX_RETRY_TIMEOUT = 300 * 1000;
    private static final String FIELD_TYPE_TEXT = "text";
    private static final String FIELD_TYPE_DATE ="date";
    public static final int  ANNOTATION_INDEX_MAX_RESULT_WINDOW = 10000;
    private RestClient esRestClient;

    /** Annotation index properties */
    private HashAlgorithm idgenHashAlgo;
    private static String ANNOTATION_INDEX_TEMPLATE_NAME;
    private final int replicationFactorForAnnotationIndex;
    private final int numShardsForAnnotationIndex;
    private final ObjectMapper annotationMapper;
    public static String ANNOTATION_INDEX_TEMPLATE_PATTERN_START;
    
    private static final String EXCEPTION_MESSAGE = "Your query returns {0} or more annotations."
            + " Please modify your query by reducing the time window.";
    
    @Inject
    ElasticSearchAnnotationService(SystemConfiguration config, MonitorService monitorService, ElasticSearchUtils esUtils) {
        super(config);
        this.monitorService = monitorService;
        /** Setup Global ES stuff */
        String algorithm = config.getValue(Property.ANNOTATION_ES_IDGEN_HASH_ALGO.getName(), Property.ANNOTATION_ES_IDGEN_HASH_ALGO.getDefaultValue());
        try {
            idgenHashAlgo = HashAlgorithm.fromString(algorithm);
        } catch(IllegalArgumentException e) {
            logger.warn("{} is not supported by this service. Valid values are: {}.", algorithm, Arrays.asList(AnnotationRecordList.HashAlgorithm.values()));
            idgenHashAlgo = HashAlgorithm.MD5;
        }
        logger.info("Using {} for Elasticsearch document id generation.", idgenHashAlgo);

        String[] nodes = config.getValue(Property.ANNOTATION_ES_ENDPOINT.getName(), Property.ANNOTATION_ES_ENDPOINT.getDefaultValue()).split(",");
        HttpHost[] httpHosts = new HttpHost[nodes.length];
        for(int i=0; i<nodes.length; i++) {
            try {
                URL url = new URL(nodes[i]);
                httpHosts[i] = new HttpHost(url.getHost(), url.getPort());
            } catch (MalformedURLException e) {
                logger.error("One or more ElasticSearch endpoints are malformed. "
                        + "If you have configured only a single endpoint, then ESAnnotationService will not function.", e);
            }
        }
        RestClientBuilder.HttpClientConfigCallback clientConfigCallback = httpClientBuilder -> {
            try {
                int connCount = Integer.parseInt(config.getValue(Property.ANNOTATION_ES_CONNECTION_COUNT.getName(),
                        Property.ANNOTATION_ES_CONNECTION_COUNT.getDefaultValue()));
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
            int connTimeout = Integer.parseInt(config.getValue(Property.ANNOTATION_ES_ENDPOINT_CONNECTION_TIMEOUT_MILLIS.getName(),
                    Property.ANNOTATION_ES_ENDPOINT_CONNECTION_TIMEOUT_MILLIS.getDefaultValue()));
            int socketTimeout = Integer.parseInt(config.getValue(Property.ANNOTATION_ES_ENDPOINT_SOCKET_TIMEOUT.getName(),
                    Property.ANNOTATION_ES_ENDPOINT_SOCKET_TIMEOUT.getDefaultValue()));
            requestConfigBuilder.setConnectTimeout(connTimeout).setSocketTimeout(socketTimeout);

            logger.info("esRestClient set connectionTimeoutMillis {} socketTimeoutMillis {}",
                    connTimeout, socketTimeout);

            return requestConfigBuilder;
        };
        esRestClient = RestClient.builder(httpHosts)
                .setHttpClientConfigCallback(clientConfigCallback)
                .setRequestConfigCallback(requestConfigCallback)
                .setMaxRetryTimeoutMillis(ANNOTATION_MAX_RETRY_TIMEOUT)
                .build();
        logger.info("esRestClient set MaxRetryTimeoutsMillis {}", ANNOTATION_MAX_RETRY_TIMEOUT);

        /** Set up annotation index stuff */
        annotationMapper = getAnnotationObjectMapper(new AnnotationRecordList.IndexSerializer());
        ANNOTATION_INDEX_TEMPLATE_NAME = config.getValue(Property.ANNOTATION_ES_INDEX_TEMPLATE_NAME.getName(),
                Property.ANNOTATION_ES_INDEX_TEMPLATE_NAME.getDefaultValue());
        ANNOTATION_INDEX_TEMPLATE_PATTERN_START = config.getValue(Property.ANNOTATION_ES_INDEX_TEMPLATE_PATTERN_START.getName(),
                Property.ANNOTATION_ES_INDEX_TEMPLATE_PATTERN_START.getDefaultValue());
        replicationFactorForAnnotationIndex = Integer.parseInt(
                config.getValue(Property.ANNOTATION_ES_NUM_REPLICAS.getName(), Property.ANNOTATION_ES_NUM_REPLICAS.getDefaultValue()));
        numShardsForAnnotationIndex = Integer.parseInt(
                config.getValue(Property.ANNOTATION_ES_SHARDS_COUNT.getName(), Property.ANNOTATION_ES_SHARDS_COUNT.getDefaultValue()));
        esUtils.createIndexTemplate(esRestClient,
                ANNOTATION_INDEX_TEMPLATE_NAME,
                ANNOTATION_INDEX_TEMPLATE_PATTERN_START,
                this::createAnnotationIndexTemplateSettingsNode,
                this::createAnnotationIndexTemplateMappingsNode);
    }

    /**
     * The set of implementation specific configuration properties.
     *
     */
    public enum Property {

        ANNOTATION_ES_ENDPOINT("service.property.annotation.elasticsearch.endpoint", "http://localhost:9200,http://localhost:9201"),
        /** Connection timeout for ES REST client. */
        ANNOTATION_ES_ENDPOINT_CONNECTION_TIMEOUT_MILLIS("service.property.annotation.elasticsearch.endpoint.connection.timeout.millis", "10000"),
        /** Socket connection timeout for ES REST client. */
        ANNOTATION_ES_ENDPOINT_SOCKET_TIMEOUT("service.property.annotation.elasticsearch.endpoint.socket.timeout", "10000"),
        /** Connection count for ES REST client. */
        ANNOTATION_ES_CONNECTION_COUNT("service.property.annotation.elasticsearch.connection.count", "10"),
        /** The hashing algorithm to use for generating document id. */
        ANNOTATION_ES_IDGEN_HASH_ALGO("service.property.annotation.elasticsearch.idgen.hash.algo", "MD5"),
        /** Replication factor */
        ANNOTATION_ES_NUM_REPLICAS("service.property.annotation.elasticsearch.num.replicas", "1"),
        /** Shard count */
        ANNOTATION_ES_SHARDS_COUNT("service.property.annotation.elasticsearch.shards.count", "6"),
        /** Index template name */
        ANNOTATION_ES_INDEX_TEMPLATE_NAME("service.property.annotation.elasticsearch.indextemplate.name", "argus-annotation-template"),
        /** Index template pattern match */
        ANNOTATION_ES_INDEX_TEMPLATE_PATTERN_START("service.property.annotation.elasticsearch.indextemplate.patternstart", "argus-annotation");

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
    
    /* Method to change the rest client. Used for testing. */
    protected void setESRestClient(RestClient restClient){
        this.esRestClient = restClient;
    }

    private ObjectNode _createFieldNodeNoAnalyzer(String type) {
        ObjectNode fieldNode = genericObjectMapper.createObjectNode();
        fieldNode.put("type", type);
        return fieldNode;
    }

    private ObjectNode createAnnotationIndexTemplateMappingsNode() {
        ObjectNode propertiesNode = genericObjectMapper.createObjectNode();
        propertiesNode.set(AnnotationRecordList.AnnotationRecordType.SCOPE.getName(), createAnnotationFieldNodeAnalyzer(FIELD_TYPE_TEXT));
        propertiesNode.set(AnnotationRecordList.AnnotationRecordType.METRIC.getName(), createAnnotationFieldNodeAnalyzer(FIELD_TYPE_TEXT));
        propertiesNode.set(AnnotationRecordList.AnnotationRecordType.TAGS.getName(), createAnnotationFieldNodeAnalyzer(FIELD_TYPE_TEXT));
        propertiesNode.set(AnnotationRecordList.AnnotationRecordType.SOURCE.getName(), createAnnotationFieldNodeAnalyzer(FIELD_TYPE_TEXT));
        propertiesNode.set(AnnotationRecordList.AnnotationRecordType.ID.getName(), createAnnotationFieldNodeAnalyzer(FIELD_TYPE_TEXT));
        propertiesNode.set(AnnotationRecordList.AnnotationRecordType.TYPE.getName(), createAnnotationFieldNodeAnalyzer(FIELD_TYPE_TEXT));
        propertiesNode.set(AnnotationRecordList.AnnotationRecordType.FIELDS.getName(), createAnnotationFieldNodeAnalyzer(FIELD_TYPE_TEXT));
        propertiesNode.set(AnnotationRecordList.AnnotationRecordType.SEARCH_ID.getName(), createAnnotationFieldNodeAnalyzer(FIELD_TYPE_TEXT));
        propertiesNode.set(AnnotationRecordList.AnnotationRecordType.TIMESTAMP.getName(), _createFieldNodeNoAnalyzer(FIELD_TYPE_DATE));

        ObjectNode docNode = genericObjectMapper.createObjectNode();
        docNode.set("properties", propertiesNode);
        
        ObjectNode mappingsNode = genericObjectMapper.createObjectNode();
        mappingsNode.set("_doc", docNode);

        return mappingsNode;
    }

    private ObjectNode createAnnotationFieldNodeAnalyzer(String type) {
        ObjectNode fieldNode = genericObjectMapper.createObjectNode();
        fieldNode.put("type", type);
        fieldNode.put("analyzer", "annotation_analyzer");
        ObjectNode keywordNode = genericObjectMapper.createObjectNode();
        keywordNode.put("type", "keyword");
        ObjectNode fieldsNode = genericObjectMapper.createObjectNode();
        fieldsNode.set("raw", keywordNode);
        fieldNode.set("fields", fieldsNode);
        return fieldNode;
    }

    private ObjectNode createAnnotationIndexTemplateSettingsNode() {
        ObjectNode annotationAnalyzer = genericObjectMapper.createObjectNode();
        annotationAnalyzer.put("tokenizer", "annotation_tokenizer");
        annotationAnalyzer.set("filter", genericObjectMapper.createArrayNode().add("lowercase"));

        ObjectNode analyzerNode = genericObjectMapper.createObjectNode();
        analyzerNode.set("annotation_analyzer", annotationAnalyzer);

        ObjectNode tokenizerNode = genericObjectMapper.createObjectNode();
        tokenizerNode.set("annotation_tokenizer", genericObjectMapper.createObjectNode().put("type", "pattern").put("pattern", ElasticSearchUtils.TOKENIZER_PATTERN));

        ObjectNode analysisNode = genericObjectMapper.createObjectNode();
        analysisNode.set("analyzer", analyzerNode);
        analysisNode.set("tokenizer", tokenizerNode);

        ObjectNode indexNode = genericObjectMapper.createObjectNode();
        indexNode.put("max_result_window", ANNOTATION_INDEX_MAX_RESULT_WINDOW);
        indexNode.put("number_of_replicas", replicationFactorForAnnotationIndex);
        indexNode.put("number_of_shards", numShardsForAnnotationIndex);

        ObjectNode settingsNode = genericObjectMapper.createObjectNode();
        settingsNode.set("analysis", analysisNode);
        settingsNode.set("index", indexNode);

        return settingsNode;
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
    
    /**
     * Enumeration of supported HTTP methods.
     *
     * @author Dilip Devaraj (ddevaraj@salesforce.com)
     */
    private enum HttpMethod {

        /** POST operation. */
        POST("POST"),
        /** PUT operation. */
        PUT("PUT"),
        /** HEAD operation. */
        HEAD("HEAD");

        private String name;

        HttpMethod(String name) {
            this.setName(name);
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    } 

    @VisibleForTesting
    static ObjectMapper getAnnotationObjectMapper(JsonSerializer<AnnotationRecordList> serializer) {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        SimpleModule module = new SimpleModule();
        module.addSerializer(AnnotationRecordList.class, serializer);
        module.addDeserializer(AnnotationRecordList.class, new AnnotationRecordList.Deserializer());
        mapper.registerModule(module);

        return mapper;
    }

    @Override
    public void putAnnotations(List<Annotation> annotations) {
        String requestUrl = "_bulk";

        try {
            AnnotationRecordList indexAnnotationRecordList = new AnnotationRecordList(annotations, idgenHashAlgo);
            String requestBody = annotationMapper.writeValueAsString(indexAnnotationRecordList);
            Set<Annotation> failedRecords = new HashSet<>();
            ElasticSearchUtils.PutResponse putResponse = ElasticSearchUtils.performESRequest(esRestClient, requestUrl, requestBody);

            if(putResponse.isErrors()) {
                for(ElasticSearchUtils.PutResponse.Item item : putResponse.getItems()) {
                    if (item.getIndex() != null && item.getIndex().getStatus() != HttpStatus.SC_CREATED) {
                        logger.warn("Failed to add record {} to index. Reason: {}",
                                indexAnnotationRecordList.getRecord(item.getIndex().get_id()),
                                annotationMapper.writeValueAsString(item.getIndex().getError()));
                        failedRecords.add(indexAnnotationRecordList.getRecord(item.getIndex().get_id()));
                    }
                }
            }

            if (failedRecords.size() > 0) {
                logger.warn("{} records were not written to annotation ES", failedRecords.size());
                monitorService.modifyCounter(MonitorService.Counter.ANNOTATION_RECORDS_WRITE_FAILURES, failedRecords.size(), null);
            }

        } catch (IOException e) {
            throw new SystemException("Failed to index annotation to ES.", e);
        }
    }

    @Override
    public List<Annotation> getAnnotations(List<AnnotationQuery> queries) {
        requireNotDisposed();
        requireArgument(queries != null, "Annotation queries cannot be null.");
        List<Annotation> annotations = new ArrayList<>();
        int from = 0, scrollSize = ANNOTATION_INDEX_MAX_RESULT_WINDOW;

        String requestUrl = String.format("/%s-*/_search", ANNOTATION_INDEX_TEMPLATE_PATTERN_START);
        try{
            for (AnnotationQuery query : queries) {
                String queryJson = constructTermQuery(query, from, scrollSize);
                final long start = System.currentTimeMillis();
                Request request = new Request(HttpMethod.POST.getName(), requestUrl);
                request.setEntity(new StringEntity(queryJson, ContentType.APPLICATION_JSON));
                Response response = esRestClient.performRequest(request);
                final long time = System.currentTimeMillis() - start;
                logger.info("ES get request completed in {} ms", time);
                String str = ElasticSearchUtils.extractResponse(response);
                AnnotationRecordList list = ElasticSearchUtils.toEntity(str, new TypeReference<AnnotationRecordList>() {}, annotationMapper);
                annotations.addAll(list.getRecords());
                
                if(annotations.size() == scrollSize) {
                    logger.error("Maximum annotations limit execeeded for query- " + query.toString());
                    throw new RuntimeException(MessageFormat.format(EXCEPTION_MESSAGE, scrollSize));
                }
            }
        } catch(IOException ex) {
            throw new SystemException(ex);
        }
        return annotations;
    }

    private String constructTermQuery(AnnotationQuery query, int from, int size) throws JsonProcessingException {
        convertTimestampToMillis(query);

        ObjectNode queryNode = constructSearchQueryNode(query, genericObjectMapper);
        ObjectNode rootNode = annotationMapper.createObjectNode();
        rootNode.set("query", queryNode);
        rootNode.put("from", from);
        rootNode.put("size", size);

        return rootNode.toString();
    }

    protected void convertTimestampToMillis(AnnotationQuery query) {
        long queryStart = query.getStartTimestamp();
        long queryEnd = query.getEndTimestamp();
        if (queryStart < 100000000000L) query.setStartTimestamp(queryStart * 1000);
        if (queryEnd < 100000000000L) query.setEndTimestamp(queryEnd * 1000);
    }
    
    private ObjectNode constructSearchQueryNode(AnnotationQuery query, ObjectMapper mapper) throws JsonProcessingException {
        ArrayNode mustNodes = mapper.createArrayNode();
        ObjectNode node = mapper.createObjectNode();
        ObjectNode termNode = mapper.createObjectNode();
        termNode.put(AnnotationRecordList.AnnotationRecordType.SEARCH_ID.getName() + ".raw", getHashedSearchIdentifier(query));
        node.set("term", termNode);
        mustNodes.add(node);

        node = mapper.createObjectNode();
        ObjectNode rangeNode = mapper.createObjectNode();
        ObjectNode timestampNode = mapper.createObjectNode();
        timestampNode.put("gte", Long.toString(query.getStartTimestamp()));
        timestampNode.put("lte", Long.toString(query.getEndTimestamp()));
        rangeNode.set(AnnotationRecordList.AnnotationRecordType.TIMESTAMP.getName(), timestampNode);
        node.set("range", rangeNode);
        mustNodes.add(node);

        ObjectNode boolNode = mapper.createObjectNode();
        boolNode.set("must", mustNodes);

        ObjectNode queryNode = mapper.createObjectNode();
        queryNode.set("bool", boolNode);
        return queryNode;
    }

    @VisibleForTesting
    static String getHashedSearchIdentifier(AnnotationQuery annotationQuery) {
        HashFunction hf = Hashing.murmur3_128();
        String searchIdentifier = new StringBuilder().append(annotationQuery.getScope()).append(annotationQuery.getMetric())
                .append(annotationQuery.getTags().toString()).append(annotationQuery.getType()).toString();
        return hf.newHasher().putString(searchIdentifier, Charset.defaultCharset()).hash().toString();
    }
}
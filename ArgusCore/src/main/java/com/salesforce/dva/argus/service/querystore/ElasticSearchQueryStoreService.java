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

package com.salesforce.dva.argus.service.querystore;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.QueryStoreRecord;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.QueryStoreService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Implementation of User Query Store using ElasticSearch along with Bloom Filter.
 *
 * @author  ChandraVyas Annakula (cannakula@salesforce.com)
 */
@Singleton
public class ElasticSearchQueryStoreService extends DefaultService implements QueryStoreService {

    private static Logger logger = LoggerFactory.getLogger(ElasticSearchQueryStoreService.class);

    /** Bloom Filter properties */
    private static final long QS_BLOOM_POLL_INTERVAL_MS = 10 * 60 * 1000L;
    private static final int QS_BLOOM_DAY_IN_SECONDS = 24 * 60 * 60;

    /** Global ES properties */
    private static final int QUERY_STORE_INDEX_MAX_RESULT_WINDOW = 10000;
    private static final int QUERY_STORE_MAX_RETRY_TIMEOUT = 300 * 1000;
    private static final String FIELD_TYPE_TEXT = "text";
    private static final String FIELD_TYPE_DATE ="date";
    private RestClient esRestClient;
    private final int bulkIndexingSize;
    private QueryStoreRecordList.HashAlgorithm idgenHashAlgo;

    /** Query Store index properties */
    private static String QUERY_STORE_INDEX_TEMPLATE_NAME;
    private static String QUERY_STORE_INDEX_TEMPLATE_PATTERN_START;
    private static String QUERY_STORE_TYPE_NAME;
    private final ObjectMapper queryStoreMapper;
    private final int replicationFactorForQueryStoreIndex;
    private final int numShardsForQueryStoreIndex;

    protected static BloomFilter<CharSequence> createdBloom;

    protected final MonitorService monitorService;

    private int createdBloomExpectedNumberInsertions;
    private double createdBloomErrorRate;

    private final String qsRandomBloomAppend;
    private final Thread bloomFilterMonitorThread;
    private final Map<String, String> bloomFilterMonitorTags;
    private final SystemConfiguration config;
    private int bloomFilterFlushHourToStartAt;
    private ScheduledExecutorService scheduledExecutorService;
    private String createdBloomFileName;
    protected final boolean bloomFileWritingEnabled;


    @Inject
    public ElasticSearchQueryStoreService(SystemConfiguration config, MonitorService monitorService) {
        super(config);
        this.config = config;
        this.monitorService =monitorService;

        /** Setup Bloom Filter related items */

        String appendValue;
        try {
            appendValue = Integer.toString(Math.abs(InetAddress.getLocalHost().getHostName().hashCode()));
        } catch (IOException io) {
            appendValue = "123456";
            logger.error("Failed to create qsRandomBloomAppend for querystore bloom, using {}. {}", appendValue, io);
        }
        qsRandomBloomAppend = appendValue;
        bloomFileWritingEnabled = Boolean.parseBoolean(config.getValue(Property.QUERY_STORE_BLOOM_FILE_WRITING_ENABLED.getName(), Property.QUERY_STORE_BLOOM_FILE_WRITING_ENABLED.getDefaultValue()));
        String bfStateBaseDir = config.getValue(Property.QUERY_STORE_BF_STATE_BASE_DIR.getName(), Property.QUERY_STORE_BF_STATE_BASE_DIR.getDefaultValue());
        createdBloomFileName = bfStateBaseDir + "/querystore_created_bloom.state." +
                config.getValue(SystemConfiguration.ARGUS_INSTANCE_ID, "noid");
        bloomFilterMonitorTags = new ImmutableMap.Builder<String, String>()
                .put("instanceId", config.getValue(SystemConfiguration.ARGUS_INSTANCE_ID, "noid"))
                .build();
        createdBloomExpectedNumberInsertions = Integer.parseInt(config.getValue(Property.QUERY_STORE_CREATED_BLOOM_EXPECTED_NUMBER_INSERTIONS.getName(), Property.QUERY_STORE_CREATED_BLOOM_EXPECTED_NUMBER_INSERTIONS.getDefaultValue()));
        createdBloomErrorRate = Double.parseDouble(config.getValue(Property.QUERY_STORE_CREATED_BLOOM_ERROR_RATE.getName(), Property.QUERY_STORE_CREATED_BLOOM_ERROR_RATE.getDefaultValue()));
        createdBloom = createOrReadBloomFilter(createdBloomFileName, createdBloomExpectedNumberInsertions, createdBloomErrorRate);
        bloomFilterMonitorThread = new Thread(new ElasticSearchQueryStoreService.BloomFilterMonitorThread(), "bloom-filter-monitor");
        bloomFilterMonitorThread.start();
        bloomFilterFlushHourToStartAt = getBloomFilterFlushHourToStartAt();
        createScheduledExecutorService(bloomFilterFlushHourToStartAt);

        /** Setup Global ES stuff */
        String algorithm = config.getValue(Property.QUERY_STORE_ES_IDGEN_HASH_ALGO.getName(), Property.QUERY_STORE_ES_IDGEN_HASH_ALGO.getDefaultValue());
        try {
            idgenHashAlgo = QueryStoreRecordList.HashAlgorithm.fromString(algorithm);
        } catch(IllegalArgumentException e) {
            logger.warn("{} is not supported by this service. Valid values are: {}.", algorithm, Arrays.asList(QueryStoreRecordList.HashAlgorithm.values()));
            idgenHashAlgo = QueryStoreRecordList.HashAlgorithm.MD5;
        }
        logger.info("Using {} for Elasticsearch document id generation.", idgenHashAlgo);
        bulkIndexingSize = Integer.parseInt(
                config.getValue(Property.QUERY_STORE_ES_INDEXING_BATCH_SIZE.getName(), Property.QUERY_STORE_ES_INDEXING_BATCH_SIZE.getDefaultValue()));

        String[] nodes = config.getValue(Property.QUERY_STORE_ES_ENDPOINT.getName(), Property.QUERY_STORE_ES_ENDPOINT.getDefaultValue()).split(",");
        HttpHost[] httpHosts = new HttpHost[nodes.length];
        for(int i=0; i<nodes.length; i++) {
            try {
                URL url = new URL(nodes[i]);
                httpHosts[i] = new HttpHost(url.getHost(), url.getPort());
            } catch (MalformedURLException e) {
                logger.error("One or more ElasticSearch endpoints are malformed. "
                        + "If you have configured only a single endpoint, then ESQueryStoreService will not function.", e);
            }
        }
        RestClientBuilder.HttpClientConfigCallback clientConfigCallback = httpClientBuilder -> {
            try {
                int connCount = Integer.parseInt(config.getValue(Property.QUERY_STORE_ES_CONNECTION_COUNT.getName(),
                        Property.QUERY_STORE_ES_CONNECTION_COUNT.getDefaultValue()));
                PoolingNHttpClientConnectionManager connMgr =
                        new PoolingNHttpClientConnectionManager(new DefaultConnectingIOReactor());
                connMgr.setMaxTotal(connCount);
                connMgr.setDefaultMaxPerRoute(connCount / httpHosts.length);
                httpClientBuilder.setConnectionManager(connMgr);
                return httpClientBuilder;
            } catch(Exception e) {
                throw new SystemException(e);
            }
        };
        RestClientBuilder.RequestConfigCallback requestConfigCallback = requestConfigBuilder -> {
            int connTimeout = Integer.parseInt(config.getValue(Property.QUERY_STORE_ES_ENDPOINT_CONNECTION_TIMEOUT.getName(),
                    Property.QUERY_STORE_ES_ENDPOINT_CONNECTION_TIMEOUT.getDefaultValue()));
            int socketTimeout = Integer.parseInt(config.getValue(Property.QUERY_STORE_ES_ENDPOINT_SOCKET_TIMEOUT.getName(),
                    Property.QUERY_STORE_ES_ENDPOINT_SOCKET_TIMEOUT.getDefaultValue()));
            requestConfigBuilder.setConnectTimeout(connTimeout).setSocketTimeout(socketTimeout);

            logger.info("esRestClient set connTimeoutMillis {} socketTimeoutMillis {}",
                    connTimeout, socketTimeout);

            return requestConfigBuilder;
        };
        esRestClient = RestClient.builder(httpHosts)
                .setHttpClientConfigCallback(clientConfigCallback)
                .setRequestConfigCallback(requestConfigCallback)
                .setMaxRetryTimeoutMillis(QUERY_STORE_MAX_RETRY_TIMEOUT)
                .build();
        logger.info("esRestClient set MaxRetryTimeoutsMillis {}", QUERY_STORE_MAX_RETRY_TIMEOUT);

        /** Set up querystore index stuff */
        queryStoreMapper = getQueryStoreObjectMapper(new QueryStoreRecordList.CreateSerializer());
        QUERY_STORE_TYPE_NAME = config.getValue(Property.QUERY_STORE_ES_INDEX_TYPE.getName(),
                Property.QUERY_STORE_ES_INDEX_TYPE.getDefaultValue());
        QUERY_STORE_INDEX_TEMPLATE_NAME = config.getValue(Property.QUERY_STORE_ES_INDEX_TEMPLATE_NAME.getName(),
                Property.QUERY_STORE_ES_INDEX_TEMPLATE_NAME.getDefaultValue());
        QUERY_STORE_INDEX_TEMPLATE_PATTERN_START = config.getValue(Property.QUERY_STORE_ES_INDEX_TEMPLATE_PATTERN_START.getName(),
                Property.QUERY_STORE_ES_INDEX_TEMPLATE_PATTERN_START.getDefaultValue());
        replicationFactorForQueryStoreIndex = Integer.parseInt(
                config.getValue(Property.QUERY_STORE_ES_NUM_REPLICAS.getName(), Property.QUERY_STORE_ES_NUM_REPLICAS.getDefaultValue()));
        numShardsForQueryStoreIndex = Integer.parseInt(
                config.getValue(Property.QUERY_STORE_ES_SHARDS_COUNT.getName(), Property.QUERY_STORE_ES_SHARDS_COUNT.getDefaultValue()));
        createQueryStoreIndexTemplate(QUERY_STORE_INDEX_TEMPLATE_NAME, replicationFactorForQueryStoreIndex, numShardsForQueryStoreIndex,
                () -> createQueryStoreMappingsNode());

    }


    protected int getNumSecondsUntilTargetHour(int targetHour){
        logger.info("Initialized bloom filter flushing out, at {} hour of day", targetHour);
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int secondsPastHour = calendar.get(Calendar.MINUTE) * 60;
        int hoursUntil = hour < targetHour ? (targetHour - hour) : (targetHour + 24 - hour);
        return hoursUntil * 60 * 60 - secondsPastHour;
    }

    /*
     * Have a different flush start hour for schema committers based on hostname, to prevent thundering herd problem.
     */
    private int getBloomFilterFlushHourToStartAt() {
        int bloomFilterFlushHourToStartAt = 0;
        try {
            String toHash = InetAddress.getLocalHost().getHostName() + config.getValue(config.ARGUS_INSTANCE_ID, "noid");
            HashFunction hf = Hashing.murmur3_128();
            bloomFilterFlushHourToStartAt = Math.abs(hf.newHasher().putString(toHash, Charset.defaultCharset()).hash().asInt() % 24);
        } catch (UnknownHostException e) {
            logger.warn("BloomFilter UnknownHostException", e);
        }
        logger.info("BloomFilter flush hour to start at {}th hour of day", bloomFilterFlushHourToStartAt);
        return bloomFilterFlushHourToStartAt;
    }

    private BloomFilter<CharSequence> createOrReadBloomFilter(String filename, int expectedNumberInsertions, double errorRate) {
        File bfFile = new File(filename);
        if (bloomFileWritingEnabled && bfFile.exists()) {
            logger.info("Bloomfilter state file {} exists, using it to pre-populate bloom", filename);
            try (InputStream inputStream = new FileInputStream(bfFile)) {
                return BloomFilter.readFrom(inputStream, Funnels.stringFunnel(Charset.defaultCharset()));
            } catch (IOException io) {
                logger.error("Bloomfilter state file {} read error, not using prev state: {}", filename, io);
                return BloomFilter.create(Funnels.stringFunnel(Charset.defaultCharset()), expectedNumberInsertions, errorRate);
            }
        }
        logger.info("Bloomfilter state file {} NOT present or bloomFileWritingEnabled is false, starting fresh bloom", filename);
        return BloomFilter.create(Funnels.stringFunnel(Charset.defaultCharset()), expectedNumberInsertions, errorRate);
    }

    private void writeBloomsToFile() {
        if (!bloomFileWritingEnabled) {
            return;
        }

        File createdBloomFile = new File(this.createdBloomFileName);
        if (!createdBloomFile.getParentFile().exists()) {
            createdBloomFile.getParentFile().mkdirs();
        }
        try (OutputStream out = new FileOutputStream(createdBloomFile)) {
            createdBloom.writeTo(out);
            logger.info("Successfully wrote created-metrics bloomfilter to file {}", this.createdBloomFileName);
        } catch (IOException io) {
            logger.error("Failed to write to createdBloom file", io);
        }

    }


    private void createScheduledExecutorService(int targetHourToStartAt){
        scheduledExecutorService = Executors.newScheduledThreadPool(1);
        int initialDelayInSeconds = getNumSecondsUntilTargetHour(targetHourToStartAt);
        ElasticSearchQueryStoreService.BloomFilterFlushThread bloomFilterFlushThread = new ElasticSearchQueryStoreService.BloomFilterFlushThread();
        scheduledExecutorService.scheduleAtFixedRate(bloomFilterFlushThread, initialDelayInSeconds, QS_BLOOM_DAY_IN_SECONDS, TimeUnit.SECONDS);
    }

    private void shutdownScheduledExecutorService(){
        logger.info("Shutting down scheduled bloom filter flush executor service");
        scheduledExecutorService.shutdown();
        try {
            scheduledExecutorService.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            logger.warn("Shutdown of executor service was interrupted.");
            Thread.currentThread().interrupt();
        }
    }

    private void shutdownElasticSearchService(){
        try {
            esRestClient.close();
            logger.info("Shutdown of ElasticSearch RESTClient complete");
        } catch (IOException e) {
            logger.warn("ElasticSearch RestClient failed to shutdown properly.", e);
        }
    }


    @Override
    public void putArgusWsQueries(List<Metric> metrics) {
        requireNotDisposed();
        requireArgument(metrics != null, "Metric list cannot be null.");
        if (metrics.size()==0) {
            return;
        }
        Set<Metric> recordsToAdd = new HashSet<>(metrics.size());

        for(Metric metric : metrics) {
            String key = QueryStoreRecord.constructBloomKey(metric.getScope(),metric.getMetric());
            if (!createdBloom.mightContain(key)) {
                recordsToAdd.add(metric);
            }
        }
        insertRecordsToES(recordsToAdd);
    }

    /**
     * Inserts User Query Records to ES
     * @param  recordsToAdd  The metrics for which to store query records for.  Cannot be null, but may be empty.
     */

    protected void insertRecordsToES(Set<Metric> recordsToAdd) {
        requireArgument(recordsToAdd != null, "Metrics list cannot be null.");
        if (recordsToAdd.size()==0){
            return;
        }

        int totalCount = 0;
        long start = System.currentTimeMillis();
        List<Set<QueryStoreRecord>> fracturedToCreateList = fractureQueryStoreRecords(recordsToAdd);
        for(Set<QueryStoreRecord> records : fracturedToCreateList) {
            if(!records.isEmpty()) {
                Set<QueryStoreRecord> failedRecords = upsertQueryStoreRecords(records);
                records.removeAll(failedRecords);
                addQueryRecordsToCreatedBloom(records);
                totalCount += records.size();
            }
        }
        monitorService.modifyCounter(MonitorService.Counter.QUERYSTORE_RECORDS_WRITTEN, totalCount, null);
        monitorService.modifyCounter(MonitorService.Counter.QUERYSTORE_RECORDS_WRITE_LATENCY,
                (System.currentTimeMillis() - start),
                null);

        logger.info("{} new metrics sent to ES in {} ms.", totalCount, System.currentTimeMillis()-start);
    }

    /* Converts the given list of metrics to a list of QueryStore records. At the same time, fracture the records list
	 * if its size is greater than QUERY_STORE_ES_INDEXING_BATCH_SIZE.
	 */
    protected List<Set<QueryStoreRecord>> fractureQueryStoreRecords(Set<Metric> metrics) {
        List<Set<QueryStoreRecord>> fracturedList = new ArrayList<>();

        Set<QueryStoreRecord> records = new HashSet<>(bulkIndexingSize);
        for(Metric metric : metrics) {
            records.add(new QueryStoreRecord(metric.getScope(),metric.getMetric()));
            if(records.size() == bulkIndexingSize) {
                fracturedList.add(records);
                records = new HashSet<>(bulkIndexingSize);
            }
        }

        if(!records.isEmpty()) {
            fracturedList.add(records);
        }

        return fracturedList;
    }

    /**
     * @param records Set of records to insert
     * @return	List of records that failed
     */
    protected Set<QueryStoreRecord> upsertQueryStoreRecords(Set<QueryStoreRecord> records) {

        String indexNameToAppend = String.format("-m%s", LocalDateTime.now().getMonthValue());
        String requestUrl = String.format("/%s%s/%s/_bulk", QUERY_STORE_INDEX_TEMPLATE_PATTERN_START,indexNameToAppend, QUERY_STORE_TYPE_NAME);

        try {
            Set<QueryStoreRecord> failedRecords = new HashSet<>();
            QueryStoreRecordList createQueryStoreRecordList = new QueryStoreRecordList(records, idgenHashAlgo);
            String requestBody = queryStoreMapper.writeValueAsString(createQueryStoreRecordList);
            PutResponse putResponse = performESRequest(requestUrl, requestBody);

            Pair<List<String>, List<String>> failedOrExistingRecordsResponse = parseFailedResponses(putResponse);

            List<String> failedIds = failedOrExistingRecordsResponse.getLeft();
            if (failedIds.size() > 0) {
                logger.warn("{} records were not written to query store ES", failedIds.size());
            }

            for(String id : failedIds) {
                failedRecords.add(createQueryStoreRecordList.getRecord(id));
            }
            return failedRecords;
        } catch (IOException e) {
            throw new SystemException("Failed to upsert query store record to ES. ", e);
        }
    }

    protected void addQueryRecordsToCreatedBloom(Set<QueryStoreRecord> records) {
        for (QueryStoreRecord record : records) {
            createdBloom.put(record.toBloomFilterKey());
        }
    }

    private PutResponse performESRequest(String requestUrl, String requestBody) throws IOException {

        String strResponse = "";

        Response response = esRestClient.performRequest(HttpMethod.POST.getName(), requestUrl, Collections.emptyMap(), new StringEntity(requestBody));
        strResponse = extractResponse(response);
        PutResponse putResponse = new ObjectMapper().readValue(strResponse, PutResponse.class);
        return putResponse;
    }

    /* Method to change the rest client. Used for testing. */
    protected void setESRestClient(RestClient restClient)
    {
        this.esRestClient = restClient;
    }

    private Pair<List<String>, List<String>> parseFailedResponses(PutResponse putResponse) throws IOException {

        List<String> failedIds = new ArrayList<>();
        List<String> updateRequiredIds = new ArrayList<>();

        if (putResponse.errors) {
            for (PutResponse.Item item : putResponse.items) {

                if (item.create != null && item.create.status != HttpStatus.SC_CREATED) {

                    if (item.create.status == HttpStatus.SC_CONFLICT) {
                        updateRequiredIds.add(item.create._id);
                    } else {
                        logger.debug("Failed to create document. Reason: " + new ObjectMapper().writeValueAsString(item.create.error));
                        failedIds.add(item.create._id);
                    }
                }

                if (item.update != null && item.update.status != HttpStatus.SC_OK) {

                    if (item.update.status == HttpStatus.SC_CONFLICT) {
                        updateRequiredIds.add(item.update._id);
                    } else {
                        logger.warn("Failed to update document. Reason: " + new ObjectMapper().writeValueAsString(item.update.error));
                        failedIds.add(item.update._id);
                    }
                }
            }
        }
        return Pair.of(failedIds, updateRequiredIds);
    }

    /** Helper to process the response. <br><br>
     * Throws IllegalArgumentException when the http status code is in the 400 range <br>
     * Throws SystemException when the http status code is outsdie of the 200 and 400 range
     * @param response ES response
     * @return	Stringified response
     */
    protected String extractResponse(Response response) {
        requireArgument(response != null, "HttpResponse object cannot be null.");

        return doExtractResponse(response.getStatusLine().getStatusCode(), response.getEntity());
    }

    /**
     * testable version of {@link ElasticSearchQueryStoreService#extractResponse(Response)}
     * @param statusCode
     * @param entity
     * @return
     */
    @VisibleForTesting
    static String doExtractResponse(int statusCode, HttpEntity entity) {
        String message = null;

        if (entity != null) {
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                entity.writeTo(baos);
                message = baos.toString("UTF-8");
            }
            catch (IOException ex) {
                throw new SystemException(ex);
            }
        }

        //if the response is in the 400 range, use IllegalArgumentException, which currently translates to a 400 error
        if (statusCode>= HttpStatus.SC_BAD_REQUEST && statusCode < HttpStatus.SC_INTERNAL_SERVER_ERROR) {
            throw new IllegalArgumentException("Status code: " + statusCode + " .  Error occurred. " +  message);
        }
        //everything else that's not in the 200 range, use SystemException, which translates to a 500 error.
        if ((statusCode < HttpStatus.SC_OK) || (statusCode >= HttpStatus.SC_MULTIPLE_CHOICES)) {
            throw new SystemException("Status code: " + statusCode + " .  Error occurred. " +  message);
        } else {
            return message;
        }
    }

    @VisibleForTesting
    static ObjectMapper getQueryStoreObjectMapper(JsonSerializer<QueryStoreRecordList> serializer) {
        ObjectMapper mapper = new ObjectMapper();

        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        SimpleModule module = new SimpleModule();
        module.addSerializer(QueryStoreRecordList.class, serializer);
        module.addDeserializer(QueryStoreRecordList.class, new QueryStoreRecordList.Deserializer());
        mapper.registerModule(module);

        return mapper;
    }

    private void createQueryStoreIndexTemplate(String templateName, int replicationFactor, int numShards,
                                               Supplier<ObjectNode> createIndexTemplateMappingsNode) {
        try {
                ObjectMapper mapper = new ObjectMapper();
                ObjectNode rootNode = mapper.createObjectNode();
                String templatePattern = String.format("%s*", QUERY_STORE_INDEX_TEMPLATE_PATTERN_START);
                rootNode.put("template",templatePattern);
                rootNode.set("settings", createQueryStoreIndexTemplateSettingsNode(replicationFactor, numShards));
                rootNode.set("mappings", createIndexTemplateMappingsNode.get());

                String settingsAndMappingsJson = rootNode.toString();
                String requestUrl = new StringBuilder().append("/_template/").append(templateName).toString();

                Response response = esRestClient.performRequest(HttpMethod.PUT.getName(), requestUrl, Collections.emptyMap(), new StringEntity(settingsAndMappingsJson));
                extractResponse(response);
        } catch (Exception e) {
            logger.error("Failed to check/create {} index template. It is failed because of the error {}",
                    templateName, e);
        }
    }

    private ObjectNode createQueryStoreIndexTemplateSettingsNode(int replicationFactor, int numShards) {
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode indexNode = mapper.createObjectNode();
        indexNode.put("max_result_window", QUERY_STORE_INDEX_MAX_RESULT_WINDOW);
        indexNode.put("number_of_replicas", replicationFactor);
        indexNode.put("number_of_shards", numShards);

        ObjectNode settingsNode = mapper.createObjectNode();
        settingsNode.set("index", indexNode);

        return settingsNode;
    }

    private ObjectNode createQueryStoreMappingsNode() {
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode propertiesNode = mapper.createObjectNode();
        propertiesNode.set(QueryStoreRecordList.QueryStoreRecordType.SCOPE.getName(), createQueryStoreFieldNodeNoAnalyzer(FIELD_TYPE_TEXT));
        propertiesNode.set(QueryStoreRecordList.QueryStoreRecordType.METRIC.getName(), createQueryStoreFieldNodeNoAnalyzer(FIELD_TYPE_TEXT));

        propertiesNode.set("mts", createQueryStoreFieldNodeNoAnalyzer(FIELD_TYPE_DATE));
        propertiesNode.set("cts", createQueryStoreFieldNodeNoAnalyzer(FIELD_TYPE_DATE));

        ObjectNode typeNode = mapper.createObjectNode();
        typeNode.set("properties", propertiesNode);

        ObjectNode mappingsNode = mapper.createObjectNode();
        mappingsNode.set(QUERY_STORE_TYPE_NAME, typeNode);

        return mappingsNode;
    }

    private ObjectNode createQueryStoreFieldNodeNoAnalyzer(String type) {
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode fieldNode = mapper.createObjectNode();
        fieldNode.put("type", type);
        return fieldNode;
    }

    @Override
    public void dispose() {
        requireNotDisposed();
        if (bloomFilterMonitorThread != null && bloomFilterMonitorThread.isAlive()) {
            logger.info("Stopping bloom filter monitor thread.");
            bloomFilterMonitorThread.interrupt();
            logger.info("Bloom filter monitor thread interrupted.");
            try {
                logger.info("Waiting for bloom filter monitor thread to terminate.");
                bloomFilterMonitorThread.join();
            } catch (InterruptedException ex) {
                logger.warn("Bloom filter monitor thread was interrupted while shutting down.");
            }
            logger.info("System monitoring stopped.");
        } else {
            logger.info("Requested shutdown of bloom filter monitor thread aborted, as it is not yet running.");
        }
        shutdownScheduledExecutorService();
        shutdownElasticSearchService();
    }

    @Override
    public Properties getServiceProperties() {
        Properties serviceProps = new Properties();

        for (Property property : Property.values()) {
            serviceProps.put(property.getName(), property.getDefaultValue());
        }
        return serviceProps;
    }

    //~ Inner Classes ********************************************************************************************************************************

    /**
     * Bloom Filter monitoring thread.
     *
     */
    private class BloomFilterMonitorThread implements Runnable {
        @Override
        public void run() {
            logger.info("Initialized qsRandomBloomAppend for bloom filter key = {}", qsRandomBloomAppend);
            while (!Thread.currentThread().isInterrupted()) {
                _sleepForPollPeriod();
                if (!Thread.currentThread().isInterrupted()) {
                    try {
                        _checkBloomFilterUsage();
                    } catch (Exception ex) {
                        logger.warn("Exception occurred while checking bloom filter usage.", ex);
                    }
                }
            }
        }

        private void _checkBloomFilterUsage() {
            monitorService.modifyCounter(MonitorService.Counter.QUERY_STORE_BLOOM_CREATED_APPROXIMATE_ELEMENT_COUNT, createdBloom.approximateElementCount(), bloomFilterMonitorTags);
            logger.info("Bloom for created-timestamp expected error rate = {}", createdBloom.expectedFpp());
        }

        private void _sleepForPollPeriod() {
            try {
                logger.info("Sleeping for {}s before checking bloom filter statistics.", QS_BLOOM_POLL_INTERVAL_MS / 1000);
                Thread.sleep(QS_BLOOM_POLL_INTERVAL_MS);
            } catch (InterruptedException ex) {
                logger.warn("ElasticSearchQueryStoreService memory monitor thread was interrupted.");
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     *	Writes the createdBloom to disk.
     */
    private class BloomFilterFlushThread implements Runnable {
        @Override
        public void run() {
            try {
                _flushBloomFilter();
            } catch (Exception ex) {
                logger.warn("Exception occurred while flushing bloom filter.", ex);
            }
        }

        private void _flushBloomFilter() {
            logger.info("Flushing out bloom filter entries");
            writeBloomsToFile();
        }
    }


    /**
     * Enumeration of supported HTTP methods.
     *
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


    /**
     * The set of implementation specific configuration properties.
     *
     */
    public enum Property {

        QUERY_STORE_BLOOM_FILE_WRITING_ENABLED("service.property.querystore.bloom.file.writing.enabled", "false"),
        QUERY_STORE_BF_STATE_BASE_DIR("service.property.querystore.bf.state.base.dir", "bloomstate"),

        /*
         * (Comment from old bloom filter pattern)
         * Estimated Filter Size using bloomFilter 1 million entries
         * https://hur.st/bloomfilter/?n=1000000&p=1.0E-5&m=&k= 2.86MiB
         * Storing in a Set 100K entries with avg length of 15 chars would be 100K * 15 * 2 B = 30B * 100K = 3 MB
         * If # of entries is 1 million, then it would be 30 MB resulting in savings in space.
         */
        QUERY_STORE_CREATED_BLOOM_EXPECTED_NUMBER_INSERTIONS("service.property.querystore.bloomfilter.created.expected.number.insertions", "40"),
        QUERY_STORE_CREATED_BLOOM_ERROR_RATE("service.property.querystore.bloomfilter.created.error.rate", "0.00001"),

        QUERY_STORE_ES_ENDPOINT("service.property.querystore.elasticsearch.endpoint", "http://localhost:9200,http://localhost:9201"),
        /** Connection timeout for ES REST client. */
        QUERY_STORE_ES_ENDPOINT_CONNECTION_TIMEOUT("service.property.querystore.elasticsearch.endpoint.connection.timeout", "10000"),
        /** Socket connection timeout for ES REST client. */
        QUERY_STORE_ES_ENDPOINT_SOCKET_TIMEOUT("service.property.querystore.elasticsearch.endpoint.socket.timeout", "10000"),
        /** Connection count for ES REST client. */
        QUERY_STORE_ES_CONNECTION_COUNT("service.property.querystore.elasticsearch.connection.count", "10"),
        /** The no. of records to batch for bulk indexing requests.
         * https://www.elastic.co/guide/en/elasticsearch/guide/current/indexing-performance.html#_using_and_sizing_bulk_requests
         */
        QUERY_STORE_ES_INDEXING_BATCH_SIZE("service.property.querystore.elasticsearch.indexing.batch.size", "10000"),
        /** The hashing algorithm to use for generating document id. */
        QUERY_STORE_ES_IDGEN_HASH_ALGO("service.property.querystore.elasticsearch.idgen.hash.algo", "MD5"),


        /** Replication factor for query store */
        QUERY_STORE_ES_NUM_REPLICAS("service.property.querystore.elasticsearch.num.replicas", "1"),
        /** Shard count for query store */
        QUERY_STORE_ES_SHARDS_COUNT("service.property.querystore.elasticsearch.shards.count", "6"),
        /** Query store index type */
        QUERY_STORE_ES_INDEX_TYPE("service.property.querystore.elasticsearch.index.type", "argus-query_type"),
        /** Query store index template name */
        QUERY_STORE_ES_INDEX_TEMPLATE_NAME("service.property.querystore.elasticsearch.indextemplate.name", "argus-querystore-template"),
        /** Query store index template pattern match */
        QUERY_STORE_ES_INDEX_TEMPLATE_PATTERN_START("service.property.querystore.elasticsearch.indextemplate.patternstart", "argus-querystore");


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

    /**
     *	Used for constructing Elastic Search Response object
     */
    static class PutResponse {
        private int took;
        private boolean errors;
        private List<Item> items;

        public PutResponse() {}

        public int getTook() {
            return took;
        }

        public void setTook(int took) {
            this.took = took;
        }

        public boolean isErrors() {
            return errors;
        }

        public void setErrors(boolean errors) {
            this.errors = errors;
        }

        public List<Item> getItems() {
            return items;
        }

        public void setItems(List<Item> items) {
            this.items = items;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Item {
            private CreateItem create;
            private CreateItem index;
            private CreateItem update;

            public Item() {}

            public CreateItem getCreate() {
                return create;
            }

            public void setCreate(CreateItem create) {
                this.create = create;
            }

            public CreateItem getIndex() {
                return index;
            }

            public void setIndex(CreateItem index) {
                this.index = index;
            }

            public CreateItem getUpdate() {
                return update;
            }

            public void setUpdate(CreateItem update) {
                this.update = update;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class CreateItem {
            private String _index;
            private String _type;
            private String _id;
            private int status;
            private int _version;
            private Error error;

            public CreateItem() {}

            public String get_index() {
                return _index;
            }

            public void set_index(String _index) {
                this._index = _index;
            }

            public String get_type() {
                return _type;
            }

            public void set_type(String _type) {
                this._type = _type;
            }

            public String get_id() {
                return _id;
            }

            public void set_id(String _id) {
                this._id = _id;
            }

            public int get_version() {
                return _version;
            }

            public void set_version(int _version) {
                this._version = _version;
            }

            public int getStatus() {
                return status;
            }

            public void setStatus(int status) {
                this.status = status;
            }

            public Error getError() {
                return error;
            }

            public void setError(Error error) {
                this.error = error;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class Error {
            private String type;
            private String reason;

            public Error() {}

            public String getType() {
                return type;
            }

            public void setType(String type) {
                this.type = type;
            }

            public String getReason() {
                return reason;
            }

            public void setReason(String reason) {
                this.reason = reason;
            }
        }
    }

}

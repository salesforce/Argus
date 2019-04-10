package com.salesforce.dva.argus.service.schema;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.salesforce.dva.argus.entity.AbstractSchemaRecord;
import com.salesforce.dva.argus.entity.KeywordQuery;
import com.salesforce.dva.argus.entity.MetatagsRecord;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.MetricSchemaRecord;
import com.salesforce.dva.argus.entity.MetricSchemaRecordQuery;
import com.salesforce.dva.argus.entity.ScopeOnlySchemaRecord;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.MonitorService.Counter;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.service.schema.ElasticSearchSchemaService.PutResponse.Item;
import com.salesforce.dva.argus.service.schema.MetricSchemaRecordList.HashAlgorithm;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder.HttpClientConfigCallback;
import org.elasticsearch.client.RestClientBuilder.RequestConfigCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Supplier;

import static com.salesforce.dva.argus.entity.MetricSchemaRecord.RETENTION_DISCOVERY;
import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;


/**
 * ElasticSearch Initializer
 *
 * @author  Kunal Nawale (knawale@salesforce.com)
 */
@Singleton
public class ElasticSearchUtils {

    private static Logger _logger = LoggerFactory.getLogger(ElasticSearchUtils.class);

    private static final int INDEX_MAX_RESULT_WINDOW = 10000;


    public ElasticSearchUtils() {
    }

    public void createIndexIfNotExists(RestClient esRestClient,
                                       String indexName,
                                       int replicationFactor,
                                       int numShards,
                                       Supplier<ObjectNode> createMappingsNode) {
        try {
            Response response = esRestClient.performRequest(HttpMethod.HEAD.getName(), "/" + indexName);
            boolean indexExists = response.getStatusLine().getStatusCode() == HttpStatus.SC_OK ? true : false;

            if(!indexExists) {
                _logger.info("Index [" + indexName + "] does not exist. Will create one.");
                ObjectMapper mapper = new ObjectMapper();

                ObjectNode rootNode = mapper.createObjectNode();
                rootNode.put("settings", _createSettingsNode(replicationFactor, numShards));
                rootNode.put("mappings", createMappingsNode.get());

                String settingsAndMappingsJson = rootNode.toString();
                String requestUrl = new StringBuilder().append("/").append(indexName).toString();

                response = esRestClient.performRequest(HttpMethod.PUT.getName(),
                                                       requestUrl,
                                                       Collections.emptyMap(),
                                                       new StringEntity(settingsAndMappingsJson, ContentType.APPLICATION_JSON));
                extractResponse(response);
            }
        } catch (Exception e) {
            _logger.error("Failed to check/create {} index. ElasticSearchSchemaService may not function. {}",
                          indexName, e);
        }
    }

    private ObjectNode _createSettingsNode(int replicationFactor, int numShards) {
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode metadataAnalyzer = mapper.createObjectNode();
        metadataAnalyzer.put("tokenizer", "metadata_tokenizer");
        metadataAnalyzer.put("filter", mapper.createArrayNode().add("lowercase"));

        ObjectNode analyzerNode = mapper.createObjectNode();
        analyzerNode.put("metadata_analyzer", metadataAnalyzer);

        ObjectNode tokenizerNode = mapper.createObjectNode();
        tokenizerNode.put("metadata_tokenizer", mapper.createObjectNode().put("type", "pattern").put("pattern", "([^\\p{L}\\d]+)|(?<=[\\p{L}&&[^\\p{Lu}]])(?=\\p{Lu})|(?<=\\p{Lu})(?=\\p{Lu}[\\p{L}&&[^\\p{Lu}]])"));

        ObjectNode analysisNode = mapper.createObjectNode();
        analysisNode.put("analyzer", analyzerNode);
        analysisNode.put("tokenizer", tokenizerNode);

        ObjectNode indexNode = mapper.createObjectNode();
        indexNode.put("max_result_window", INDEX_MAX_RESULT_WINDOW);
        indexNode.put("number_of_replicas", replicationFactor);
        indexNode.put("number_of_shards", numShards);

        ObjectNode settingsNode = mapper.createObjectNode();
        settingsNode.put("analysis", analysisNode);
        settingsNode.put("index", indexNode);

        return settingsNode;
    }


    static String extractResponse(Response response) {
        requireArgument(response != null, "HttpResponse object cannot be null.");

        return doExtractResponse(response.getStatusLine().getStatusCode(), response.getEntity());
    }

    /**
     * testable version of {@link ElasticSearchSchemaService#extractResponse(Response)}
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

    /**
     * Enumeration of supported HTTP methods.
     *
     * @author  Bhinav Sura (bhinav.sura@salesforce.com)
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

}

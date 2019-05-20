package com.salesforce.dva.argus.service.schema;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.function.Supplier;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Singleton;
import com.salesforce.dva.argus.system.SystemException;


/**
 * ElasticSearch Initializer
 *
 * @author  Kunal Nawale (knawale@salesforce.com)
 */
@Singleton
public class ElasticSearchUtils {

    private static Logger _logger = LoggerFactory.getLogger(ElasticSearchUtils.class);
    private static ObjectMapper mapper = new ObjectMapper();

    private static final int INDEX_MAX_RESULT_WINDOW = 10000;
    private static final int  ANNOTATION_INDEX_MAX_RESULT_WINDOW = 10000;


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

                ObjectNode rootNode = mapper.createObjectNode();
                rootNode.set("settings", _createSettingsNode(replicationFactor, numShards));
                rootNode.set("mappings", createMappingsNode.get());

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
    
    public void createIndexTemplate(RestClient esRestClient,
                                    String templateName,
                                    String templatePattern,
                                    int replicationFactor,
                                    int numShards,
                                    Supplier<ObjectNode> createIndexTemplateMappingsNode) {
        try {
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.put("template",templatePattern + "*");
            rootNode.set("settings", createAnnotationIndexTemplateSettingsNode(replicationFactor, numShards));
            rootNode.set("mappings", createIndexTemplateMappingsNode.get());

            String settingsAndMappingsJson = rootNode.toString();
            String requestUrl = new StringBuilder().append("/_template/").append(templateName).toString();

            Request request = new Request(HttpMethod.PUT.getName(), requestUrl);
            request.setEntity(new StringEntity(settingsAndMappingsJson, ContentType.APPLICATION_JSON));
            Response response = esRestClient.performRequest(request);
            extractResponse(response);
        } catch (Exception e) {
            _logger.error("Failed to check/create {} index template. Failure due to {}", templateName, e);
        }
    }
    
    private ObjectNode createAnnotationIndexTemplateSettingsNode(int replicationFactor, int numShards) {
        ObjectNode annotationAnalyzer = mapper.createObjectNode();
        annotationAnalyzer.put("tokenizer", "annotation_tokenizer");
        annotationAnalyzer.set("filter", mapper.createArrayNode().add("lowercase"));

        ObjectNode analyzerNode = mapper.createObjectNode();
        analyzerNode.set("annotation_analyzer", annotationAnalyzer);

        ObjectNode tokenizerNode = mapper.createObjectNode();
        tokenizerNode.set("annotation_tokenizer", mapper.createObjectNode().put("type", "pattern").put("pattern", "([^\\p{L}\\d]+)|(?<=[\\p{L}&&[^\\p{Lu}]])(?=\\p{Lu})|(?<=\\p{Lu})(?=\\p{Lu}[\\p{L}&&[^\\p{Lu}]])"));

        ObjectNode analysisNode = mapper.createObjectNode();
        analysisNode.set("analyzer", analyzerNode);
        analysisNode.set("tokenizer", tokenizerNode);


        ObjectNode indexNode = mapper.createObjectNode();
        indexNode.put("max_result_window", ANNOTATION_INDEX_MAX_RESULT_WINDOW);
        indexNode.put("number_of_replicas", replicationFactor);
        indexNode.put("number_of_shards", numShards);

        ObjectNode settingsNode = mapper.createObjectNode();
        settingsNode.set("analysis", analysisNode);
        settingsNode.set("index", indexNode);

        return settingsNode;
    }

    private ObjectNode _createSettingsNode(int replicationFactor, int numShards) {
        ObjectNode metadataAnalyzer = mapper.createObjectNode();
        metadataAnalyzer.put("tokenizer", "metadata_tokenizer");
        metadataAnalyzer.set("filter", mapper.createArrayNode().add("lowercase"));

        ObjectNode analyzerNode = mapper.createObjectNode();
        analyzerNode.set("metadata_analyzer", metadataAnalyzer);

        ObjectNode tokenizerNode = mapper.createObjectNode();
        tokenizerNode.set("metadata_tokenizer", mapper.createObjectNode().put("type", "pattern").put("pattern", "([^\\p{L}\\d]+)|(?<=[\\p{L}&&[^\\p{Lu}]])(?=\\p{Lu})|(?<=\\p{Lu})(?=\\p{Lu}[\\p{L}&&[^\\p{Lu}]])"));

        ObjectNode analysisNode = mapper.createObjectNode();
        analysisNode.set("analyzer", analyzerNode);
        analysisNode.set("tokenizer", tokenizerNode);

        ObjectNode indexNode = mapper.createObjectNode();
        indexNode.put("max_result_window", INDEX_MAX_RESULT_WINDOW);
        indexNode.put("number_of_replicas", replicationFactor);
        indexNode.put("number_of_shards", numShards);

        ObjectNode settingsNode = mapper.createObjectNode();
        settingsNode.set("analysis", analysisNode);
        settingsNode.set("index", indexNode);

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

package com.salesforce.dva.argus.service.schema;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
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

    public static final int INDEX_MAX_RESULT_WINDOW = 10000;

    public static final String TOKENIZER_PATTERN = "([^\\p{L}\\d]+)|(?<=[\\p{L}&&[^\\p{Lu}]])(?=\\p{Lu})|(?<=\\p{Lu})(?=\\p{Lu}[\\p{L}&&[^\\p{Lu}]])";


    public ElasticSearchUtils() {
    }

    public static Long convertTimestampToMillis(Long timestamp) {
        if (timestamp < 1_00_000_000_000L) return (timestamp * 1000);
        return timestamp;
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
                                    Supplier<ObjectNode> createIndexTemplateSettingsNode,
                                    Supplier<ObjectNode> createIndexTemplateMappingsNode) {
        try {
            ObjectNode rootNode = mapper.createObjectNode();
            rootNode.put("template",templatePattern + "*");
            rootNode.set("settings", createIndexTemplateSettingsNode.get());
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

    private ObjectNode _createSettingsNode(int replicationFactor, int numShards) {
        ObjectNode metadataAnalyzer = mapper.createObjectNode();
        metadataAnalyzer.put("tokenizer", "metadata_tokenizer");
        metadataAnalyzer.set("filter", mapper.createArrayNode().add("lowercase"));

        ObjectNode analyzerNode = mapper.createObjectNode();
        analyzerNode.set("metadata_analyzer", metadataAnalyzer);

        ObjectNode tokenizerNode = mapper.createObjectNode();
        tokenizerNode.set("metadata_tokenizer", mapper.createObjectNode().put("type", "pattern").put("pattern", TOKENIZER_PATTERN));

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

    /* Helper method to convert JSON String representation to the corresponding Java entity. */
    public static  <T> T toEntity(String content, TypeReference<T> type, ObjectMapper mapper) {
        try {
            return mapper.readValue(content, type);
        } catch (IOException ex) {
            throw new SystemException(ex);
        }
    }

    /** Helper to process the response. <br><br>
     * Throws IllegalArgumentException when the http status code is in the 400 range <br>
     * Throws SystemException when the http status code is outside of the 200 and 400 range
     * @param   response ES response
     * @return  Stringified response
     */
    public static String extractResponse(Response response) {
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
    public static String doExtractResponse(int statusCode, HttpEntity entity) {
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

    public enum HashAlgorithm {
        MD5,
        XXHASH;

        public static ElasticSearchUtils.HashAlgorithm fromString(String str) throws IllegalArgumentException {
            for(ElasticSearchUtils.HashAlgorithm algo : ElasticSearchUtils.HashAlgorithm.values()) {
                if(algo.name().equalsIgnoreCase(str)) {
                    return algo;
                }
            }
            throw new IllegalArgumentException(str + " does not match any of the available algorithms.");
        }
    }

    /**
     * Enumeration of supported HTTP methods.
     *
     * @author  Bhinav Sura (bhinav.sura@salesforce.com)
     */
    public enum HttpMethod {

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

    public static PutResponse performESRequest(RestClient esRestClient, String requestUrl, String requestBody) throws IOException {
        ObjectMapper genericObjectMapper = new ObjectMapper();
        Request request = new Request(HttpMethod.POST.getName(), requestUrl);
        request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
        Response response = esRestClient.performRequest(request);
        String strResponse = extractResponse(response);
        PutResponse putResponse = genericObjectMapper.readValue(strResponse, PutResponse.class);
        return putResponse;
    }

    /**
     *  Used for constructing Elastic Search Response object
     */
    public static class PutResponse {
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

        public List<PutResponse.Item> getItems() {
            return items;
        }

        public void setItems(List<PutResponse.Item> items) {
            this.items = items;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class Item {
            private PutResponse.CreateItem create;
            private PutResponse.CreateItem index;
            private PutResponse.CreateItem update;

            public Item() {}

            public PutResponse.CreateItem getCreate() {
                return create;
            }

            public void setCreate(PutResponse.CreateItem create) {
                this.create = create;
            }

            public PutResponse.CreateItem getIndex() {
                return index;
            }

            public void setIndex(PutResponse.CreateItem index) {
                this.index = index;
            }

            public PutResponse.CreateItem getUpdate() {
                return update;
            }

            public void setUpdate(PutResponse.CreateItem update) {
                this.update = update;
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class CreateItem {
            private String _index;
            private String _id;
            private int status;
            private int _version;
            private PutResponse.Error error;

            public CreateItem() {}

            public String get_index() {
                return _index;
            }

            public void set_index(String _index) {
                this._index = _index;
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

            public PutResponse.Error getError() {
                return error;
            }

            public void setError(PutResponse.Error error) {
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

package com.salesforce.dva.argus.service.schema;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.salesforce.dva.argus.entity.KeywordQuery;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.MetricSchemaRecord;
import com.salesforce.dva.argus.entity.MetricSchemaRecordQuery;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.service.schema.ElasticSearchSchemaService.PutResponse.Item;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;

@Singleton
public class ElasticSearchSchemaService extends AbstractSchemaService {
	
	private static final String INDEX_NAME = "metadata_index";
	private static final String TYPE_NAME = "metadata_type";
	private static final String KEEP_SCROLL_CONTEXT_OPEN_FOR = "1m";
	private static final int INDEX_MAX_RESULT_WINDOW = 10000; 
	
	
	private final ObjectMapper _mapper;
    private Logger _logger = LoggerFactory.getLogger(getClass());
    private CloseableHttpClient _httpClient;
    private String _esEndpoint;
    private final MonitorService _monitorService;
    
    @Inject
	public ElasticSearchSchemaService(SystemConfiguration config, MonitorService monitorService) {
		super(config);
		
		_monitorService = monitorService;
		_esEndpoint = config.getValue(Property.ELASTICSEARCH_ENDPOINT.getName(), Property.ELASTICSEARCH_ENDPOINT.getDefaultValue());
		
		int connCount = Integer.parseInt(config.getValue(Property.ELASTICSEARCH_CONNECTION_COUNT.getName(),
                Property.ELASTICSEARCH_CONNECTION_COUNT.getDefaultValue()));
        int connTimeout = Integer.parseInt(config.getValue(Property.ELASTICSEARCH_ENDPOINT_CONNECTION_TIMEOUT.getName(),
                Property.ELASTICSEARCH_ENDPOINT_CONNECTION_TIMEOUT.getDefaultValue()));
        int socketTimeout = Integer.parseInt(config.getValue(Property.ELASTICSEARCH_ENDPOINT_SOCKET_TIMEOUT.getName(),
                Property.ELASTICSEARCH_ENDPOINT_SOCKET_TIMEOUT.getDefaultValue()));
        
		_mapper = _createObjectMapper();
		
		try {
			_httpClient = _getClient(_esEndpoint, connCount, connTimeout, socketTimeout);
		} catch (MalformedURLException e) {
			_logger.error("Error initializing ElasticSearch Http Client. SchemaService will not work.", e);
		}
		
		_createIndexIfNotExists();
	}
    

	@Override
	public void dispose() {
		super.dispose();
        for (CloseableHttpClient client : new CloseableHttpClient[] { _httpClient }) {
            try {
                client.close();
            } catch (Exception ex) {
                _logger.warn("ElasticSearch HTTP client failed to shutdown properly.", ex);
            }
        }
	}

	
	@Override
	public Properties getServiceProperties() {
		Properties serviceProps = new Properties();

		for (Property property : Property.values()) {
			serviceProps.put(property.getName(), property.getDefaultValue());
		}
		return serviceProps;
	}
	
	
	@Override
	protected void implementationSpecificPut(List<Metric> metrics) {
		SystemAssert.requireArgument(metrics != null, "Metrics list cannot be null.");
		
		List<MetricSchemaRecord> records = new ArrayList<>();
		for(Metric metric : metrics) {
			for(Map.Entry<String, String> entry : metric.getTags().entrySet()) {
				records.add(new MetricSchemaRecord(metric.getNamespace(), metric.getScope(), metric.getMetric(), 
													entry.getKey(), entry.getValue()));
			}
		}
		
		if(!records.isEmpty()) {
			_upsert(records);
		}
		_monitorService.modifyCounter(MonitorService.Counter.SCHEMARECORDS_WRITTEN, records.size(), null);
	}
	

	@Override
	public List<MetricSchemaRecord> get(MetricSchemaRecordQuery query) {
		requireNotDisposed();
        SystemAssert.requireArgument(query != null, "MetricSchemaRecordQuery cannot be null.");
        
        boolean scroll = false;
		StringBuilder sb = new StringBuilder(_esEndpoint).append("/")
														  .append(INDEX_NAME)
														  .append("/")
														  .append(TYPE_NAME)
														  .append("/")
														  .append("_search");
		
		int from = 0, size;
		if(query.getLimit() * query.getPage() > 10000) {
			sb.append("?scroll=").append(KEEP_SCROLL_CONTEXT_OPEN_FOR);
			scroll = true;
			long total = query.getLimit() * query.getPage();
			size = (int) (total / (total / 10000 + 1));
		} else {
			from = query.getLimit() * (query.getPage() - 1);
			size = query.getLimit();
		}
		
		String requestUrl = sb.toString();
		String queryJson = _constructTermQuery(query, from, size);
		
		try {
			HttpResponse response = executeHttpRequest(HttpMethod.POST, requestUrl, new StringEntity(queryJson));
			MetricSchemaRecordList list = toEntity(extractResponse(response), new TypeReference<MetricSchemaRecordList>() {});
			
			if(scroll) {
				requestUrl = new StringBuilder(_esEndpoint).append("/").append("_search").append("/").append("scroll").toString();
				List<MetricSchemaRecord> records = new LinkedList<>(list.getRecords());
				
				while(true) {
					String scrollID = list.getScrollID();
					
					Map<String, String> requestBody = new HashMap<>();
					requestBody.put("scroll_id", scrollID);
					requestBody.put("scroll", KEEP_SCROLL_CONTEXT_OPEN_FOR);
					response = executeHttpRequest(HttpMethod.POST, requestUrl, new StringEntity(new ObjectMapper().writeValueAsString(requestBody)));
					list = toEntity(extractResponse(response), new TypeReference<MetricSchemaRecordList>() {});
					records.addAll(list.getRecords());
					
					if(records.size() >= query.getLimit() * query.getPage() || list.getRecords().size() < size) {
						break;
					}
				}
				
				int fromIndex = query.getLimit() * (query.getPage() - 1);
				if(records.size() <= fromIndex) {
					return Collections.emptyList();
				}
				
				return records.subList(fromIndex, records.size());
				
			} else {
				return list.getRecords();
			}
			
		} catch (UnsupportedEncodingException | JsonProcessingException e) {
			throw new SystemException("Search failed.", e);
		}
	}

	
	@Override
	public List<MetricSchemaRecord> getUnique(MetricSchemaRecordQuery query, RecordType type) {
		requireNotDisposed();
		SystemAssert.requireArgument(query != null, "MetricSchemaRecordQuery cannot be null.");
		
		String requestUrl = new StringBuilder(_esEndpoint).append("/")
														  .append(INDEX_NAME)
														  .append("/")
														  .append(TYPE_NAME)
														  .append("/")
														  .append("_search")
														  .toString();
		
		String queryJson = _constructTermAggregationQuery(query, type);
		
		try {
			HttpResponse response = executeHttpRequest(HttpMethod.POST, requestUrl, new StringEntity(queryJson));
			String str = extractResponse(response);
			List<MetricSchemaRecord> records = SchemaService.constructMetricSchemaRecordsForType(
												toEntity(str, new TypeReference<List<String>>() {}), type);
			
			int fromIndex = query.getLimit() * (query.getPage() - 1);
			if(records.size() <= fromIndex) {
				return Collections.emptyList();
			}
			
			if(records.size() < query.getLimit() * query.getPage()) {
				return records.subList(fromIndex, records.size());
			} else {
				return records.subList(fromIndex, query.getLimit() * query.getPage());
			}
		} catch (UnsupportedEncodingException e) {
			throw new SystemException("Search failed.", e);
		}
	}
	
	@Override
	public List<MetricSchemaRecord> keywordSearch(KeywordQuery kq) {
		requireNotDisposed();
        SystemAssert.requireArgument(kq != null, "KeywordQuery cannot be null.");
        
		StringBuilder sb = new StringBuilder(_esEndpoint).append("/")
				  										  .append(INDEX_NAME)
														  .append("/")
														  .append(TYPE_NAME)
														  .append("/")
														  .append("_search");
		
		//This is a hack to support native multi_match elastic search queries via Argus interface. 
		if(kq.isNative()) {
			SystemAssert.requireArgument(kq.getQuery().contains("multi_match"), "Only multi_match native elasticsearch queries are allowed.");
			try {
				HttpResponse response = executeHttpRequest(HttpMethod.POST, sb.toString(), new StringEntity(kq.getQuery()));
				return toEntity(extractResponse(response), new TypeReference<MetricSchemaRecordList>() {}).getRecords();
			} catch (UnsupportedEncodingException e) {
				throw new SystemException("Search failed.", e);
			}
		}
		
		boolean scroll = false;
		int from = 0, size;
		if(kq.getLimit() * kq.getPage() > 10000) {
			sb.append("?scroll=").append(KEEP_SCROLL_CONTEXT_OPEN_FOR);
			scroll = true;
			long total = kq.getLimit() * kq.getPage();
			size = (int) (total / (total / 10000 + 1));
		} else {
			from = kq.getLimit() * (kq.getPage() - 1);
			size = kq.getLimit();
		}
		
		String requestUrl = sb.toString();
		String queryJson = _constructMultiMatchQuery(kq.getQuery(), from, size);
		try {
			HttpResponse response = executeHttpRequest(HttpMethod.POST, requestUrl, new StringEntity(queryJson));
			MetricSchemaRecordList list = toEntity(extractResponse(response), new TypeReference<MetricSchemaRecordList>() {});
			
			if(scroll) {
				requestUrl = new StringBuilder(_esEndpoint).append("/").append("_search").append("/").append("scroll").toString();
				List<MetricSchemaRecord> records = new LinkedList<>(list.getRecords());
				
				while(true) {
					String scrollID = list.getScrollID();
					
					Map<String, String> requestBody = new HashMap<>();
					requestBody.put("scroll_id", scrollID);
					requestBody.put("scroll", KEEP_SCROLL_CONTEXT_OPEN_FOR);
					response = executeHttpRequest(HttpMethod.POST, requestUrl, new StringEntity(new ObjectMapper().writeValueAsString(requestBody)));
					list = toEntity(extractResponse(response), new TypeReference<MetricSchemaRecordList>() {});
					records.addAll(list.getRecords());
					
					if(records.size() >= kq.getLimit() * kq.getPage() || list.getRecords().size() < size) {
						break;
					}
				}
				
				int fromIndex = kq.getLimit() * (kq.getPage() - 1);
				if(records.size() <= fromIndex) {
					return Collections.emptyList();
				}
				
				return records.subList(fromIndex, records.size());
				
			} else {
				return list.getRecords();
			}
		} catch (UnsupportedEncodingException | JsonProcessingException e) {
			throw new SystemException("Search failed.", e);
		}
	}
	
	
	private void _upsert(List<MetricSchemaRecord> records) {
		
		String requestUrl = new StringBuilder(_esEndpoint).append("/")
														  .append(INDEX_NAME)
														  .append("/")
														  .append(TYPE_NAME)
														  .append("/")
														  .append("_bulk")
														  .toString();
		
		String strResponse = "";
		try {
			String requestBody = _mapper.writeValueAsString(new MetricSchemaRecordList(records));
			
			HttpResponse response = executeHttpRequest(HttpMethod.POST, requestUrl, new StringEntity(requestBody));
			strResponse = extractResponse(response);
		} catch (JsonProcessingException | UnsupportedEncodingException e) {
			throw new SystemException("Failed to parse metrics when indexing.", e);
		}
		
		try {
			PutResponse putResponse = new ObjectMapper().readValue(strResponse, PutResponse.class);
			if(putResponse.errors) {
				for(Item item : putResponse.items) {
					if(item.create != null && item.create.status != HttpStatus.SC_CONFLICT && item.create.status != HttpStatus.SC_CREATED) {
						throw new SystemException("Failed to index metric. Reason: " + new ObjectMapper().writeValueAsString(item.create.errorMap));
					}
					
					if(item.index != null && item.index.status == HttpStatus.SC_NOT_FOUND) {
						throw new SystemException("Index does not exist. Error: " + new ObjectMapper().writeValueAsString(item.index.errorMap));
					}
				}
			}
		} catch(IOException e) {
			throw new SystemException("Failed to parse reponse of put metrics.", e);
		}
	}
	
	
	private String _constructTermAggregationQuery(MetricSchemaRecordQuery query, RecordType type) {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode queryNode = _constructQueryNode(query, mapper);
		
		int size = query.getLimit() * query.getPage();
		ObjectNode aggsNode = _constructAggsNode(type, Math.max(size, 10000), mapper);
		
		ObjectNode rootNode = mapper.createObjectNode();
		rootNode.put("query", queryNode);
		rootNode.put("size", 0);
		rootNode.put("aggs", aggsNode);
		
		return rootNode.toString();
	}
	
	
	private String _constructTermQuery(MetricSchemaRecordQuery query, int from, int size) {
		ObjectMapper mapper = new ObjectMapper();
		
		ObjectNode queryNode = _constructQueryNode(query, mapper);
		
		ObjectNode rootNode = _mapper.createObjectNode();
		rootNode.put("query", queryNode);
		rootNode.put("from", from);
		rootNode.put("size", size);
		
		return rootNode.toString();
	}

	
	private String _constructMultiMatchQuery(String query, int from, int size) {
		ObjectMapper mapper = new ObjectMapper();
		
		ObjectNode multiMatchNode = mapper.createObjectNode();
		multiMatchNode.put("query", query);
		multiMatchNode.put("type", "best_fields");
		multiMatchNode.put("fields", mapper.createArrayNode().add("scope").add("metric").add("tagKey").add("tagValue").add("namespace"));
		multiMatchNode.put("operator", "and");
		
		ObjectNode queryNode = mapper.createObjectNode();
		queryNode.put("multi_match", multiMatchNode);
		
		ObjectNode rootNode = mapper.createObjectNode();
		rootNode.put("query", queryNode);
		rootNode.put("from", from);
		rootNode.put("size", size);
		
		return rootNode.toString();
	}
	
	
	private ObjectNode _constructQueryNode(MetricSchemaRecordQuery query, ObjectMapper mapper) {
		ArrayNode shouldNodes = mapper.createArrayNode();
		if(SchemaService.containsFilter(query.getMetric())) {
			ObjectNode shouldNode = mapper.createObjectNode();
			ObjectNode regexpNode = mapper.createObjectNode();
			regexpNode.put("metric.raw", SchemaService.convertToRegex(query.getMetric()));
			shouldNode.put("regexp", regexpNode);
			shouldNodes.add(shouldNode);
		}
		
		if(SchemaService.containsFilter(query.getScope())) {
			ObjectNode shouldNode = mapper.createObjectNode();
			ObjectNode regexpNode = mapper.createObjectNode();
			regexpNode.put("scope.raw", SchemaService.convertToRegex(query.getScope()));
			shouldNode.put("regexp", regexpNode);
			shouldNodes.add(shouldNode);
		}
		
		if(SchemaService.containsFilter(query.getTagKey())) {
			ObjectNode shouldNode = mapper.createObjectNode();
			ObjectNode regexpNode = mapper.createObjectNode();
			regexpNode.put("tagKey.raw", SchemaService.convertToRegex(query.getTagKey()));
			shouldNode.put("regexp", regexpNode);
			shouldNodes.add(shouldNode);
		}
		
		if(SchemaService.containsFilter(query.getTagValue())) {
			ObjectNode shouldNode = mapper.createObjectNode();
			ObjectNode regexpNode = mapper.createObjectNode();
			regexpNode.put("tagValue.raw", SchemaService.convertToRegex(query.getTagValue()));
			shouldNode.put("regexp", regexpNode);
			shouldNodes.add(shouldNode);
		}
		
		if(SchemaService.containsFilter(query.getNamespace())) {
			ObjectNode shouldNode = mapper.createObjectNode();
			ObjectNode regexpNode = mapper.createObjectNode();
			regexpNode.put("namespace.raw", SchemaService.convertToRegex(query.getNamespace()));
			shouldNode.put("regexp", regexpNode);
			shouldNodes.add(shouldNode);
		}
		
		ObjectNode boolNode = mapper.createObjectNode();
		boolNode.put("should", shouldNodes);
		boolNode.put("minimum_should_match", shouldNodes.size());
		
		ObjectNode queryNode = mapper.createObjectNode();
		queryNode.put("bool", boolNode);
		return queryNode;
	}
	
	
	private ObjectNode _constructAggsNode(RecordType type, long limit, ObjectMapper mapper) {
		
		ObjectNode termsNode = mapper.createObjectNode();
		termsNode.put("field", type.getName() + ".raw");
		termsNode.put("size", limit);
		
		ObjectNode distinctValuesNode = mapper.createObjectNode();
		distinctValuesNode.put("terms", termsNode);
		
		ObjectNode aggsNode = mapper.createObjectNode();
		aggsNode.put("distinct_values", distinctValuesNode);
		return aggsNode;
	}
	
	
	/* Helper method to convert JSON String representation to the corresponding Java entity. */
    private <T> T toEntity(String content, TypeReference<T> type) {
        try {
            return _mapper.readValue(content, type);
        } catch (IOException ex) {
            throw new SystemException(ex);
        }
    }
        
	
	/** Helper to process the response. 
	 * Throws a SystemException when the http status code is outsdie of the range 200 - 300.
	 */
    private String extractResponse(HttpResponse response) {
    	requireArgument(response != null, "HttpResponse object cannot be null.");
    	
        int status = response.getStatusLine().getStatusCode();
        String strResponse = extractStringResponse(response);
        
        if ((status < HttpStatus.SC_OK) || (status >= HttpStatus.SC_MULTIPLE_CHOICES)) {
        	throw new SystemException("Status code: " + status + " .  Error occurred. " +  strResponse);
        } else {
            return strResponse;
        }
    }
    
    
    private String extractStringResponse(HttpResponse content) {
        requireArgument(content != null, "Response content is null.");

        String result;
        HttpEntity entity = null;

        try {
            entity = content.getEntity();
            if (entity == null) {
                result = "";
            } else {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();

                entity.writeTo(baos);
                result = baos.toString("UTF-8");
            }
            return result;
        } catch (IOException ex) {
            throw new SystemException(ex);
        } finally {
            if (entity != null) {
                try {
                    EntityUtils.consume(entity);
                } catch (IOException ex) {
                    _logger.warn("Failed to close entity stream.", ex);
                }
            }
        }
    }
    
    
	private ObjectMapper _createObjectMapper() {
		ObjectMapper mapper = new ObjectMapper();
		
		mapper.setSerializationInclusion(Include.NON_NULL);
		SimpleModule module = new SimpleModule();
		module.addSerializer(MetricSchemaRecordList.class, new MetricSchemaRecordList.Serializer());
		module.addDeserializer(MetricSchemaRecordList.class, new MetricSchemaRecordList.Deserializer());
		module.addDeserializer(List.class, new MetricSchemaRecordList.AggDeserializer());
		mapper.registerModule(module);
		
		return mapper;
	}
	
	
    private ObjectNode _createSettingsNode() {
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
    	
    	ObjectNode settingsNode = mapper.createObjectNode();
    	settingsNode.put("analysis", analysisNode);
    	settingsNode.put("index", indexNode);
    	
    	return settingsNode;
    	
    }
    
    
    private ObjectNode _createMappingsNode() {
    	ObjectMapper mapper = new ObjectMapper();
    	
    	ObjectNode propertiesNode = mapper.createObjectNode();
    	propertiesNode.put("scope", _createFieldNode());
    	propertiesNode.put("metric", _createFieldNode());
    	propertiesNode.put("tagKey", _createFieldNode());
    	propertiesNode.put("tagValue", _createFieldNode());
    	propertiesNode.put("namespace", _createFieldNode());
    	
    	ObjectNode typeNode = mapper.createObjectNode();
    	typeNode.put("properties", propertiesNode);
    	
    	ObjectNode mappingsNode = mapper.createObjectNode();
    	mappingsNode.put(TYPE_NAME, typeNode);
    	
    	return mappingsNode;
    }
    
    
    private ObjectNode _createFieldNode() {
    	ObjectMapper mapper = new ObjectMapper();
    	
    	ObjectNode fieldNode = mapper.createObjectNode();
    	fieldNode.put("type", "text");
    	fieldNode.put("analyzer", "metadata_analyzer");
    	ObjectNode keywordNode = mapper.createObjectNode();
    	keywordNode.put("type", "keyword");
    	ObjectNode fieldsNode = mapper.createObjectNode();
    	fieldsNode.put("raw", keywordNode);
    	fieldNode.put("fields", fieldsNode);
    	return fieldNode;
    }

    
	private void _createIndexIfNotExists() {
		try {
			HttpResponse response = executeHttpRequest(HttpMethod.HEAD, _esEndpoint + "/" + INDEX_NAME, null);
			boolean indexExists = response.getStatusLine().getStatusCode() == HttpStatus.SC_OK ? true : false;
			
			if(!indexExists) {
				ObjectMapper mapper = new ObjectMapper();
				
				ObjectNode rootNode = mapper.createObjectNode();
				rootNode.put("settings", _createSettingsNode());
				rootNode.put("mappings", _createMappingsNode());
				
				String settingsAndMappingsJson = rootNode.toString();
				String requestUrl = new StringBuilder(_esEndpoint).append("/").append(INDEX_NAME).toString();
				response = executeHttpRequest(HttpMethod.PUT, requestUrl, new StringEntity(settingsAndMappingsJson));
				extractResponse(response);
			}
		} catch (Exception e) {
			_logger.error("Failed to check/create elastic search index. ElasticSearchSchemaService may not function.", e);
		}
	}
	
	
	/* Execute a request given by type requestType. */
    private HttpResponse executeHttpRequest(HttpMethod requestType, String url, StringEntity entity) {
        HttpResponse httpResponse = null;

        if (entity != null) {
            entity.setContentType("application/json");
        }
        try {
            switch (requestType) {
                case POST:

                    HttpPost post = new HttpPost(url);

                    post.setEntity(entity);
                    httpResponse = _httpClient.execute(post);
                    break;
                case GET:

                    HttpGet httpGet = new HttpGet(url);

                    httpResponse = _httpClient.execute(httpGet);
                    break;
                case DELETE:

                    HttpDelete httpDelete = new HttpDelete(url);

                    httpResponse = _httpClient.execute(httpDelete);
                    break;
                case PUT:

                    HttpPut httpPut = new HttpPut(url);

                    httpPut.setEntity(entity);
                    httpResponse = _httpClient.execute(httpPut);
                    break;
                case HEAD:
                	HttpHead httpHead = new HttpHead(url);
                	
                	httpResponse = _httpClient.execute(httpHead);
                	break;
                default:
                    throw new MethodNotSupportedException(requestType.toString());
            }
        } catch (MethodNotSupportedException | IOException ex) {
            throw new SystemException(ex);
        }
        
        return httpResponse;
    }
	
    
	/* Helper to create http clients. */
    private CloseableHttpClient _getClient(String endpoint, int connCount, int connTimeout, int socketTimeout) throws MalformedURLException {
        URL url = new URL(endpoint);
        int port = url.getPort();

        requireArgument(port != -1, "Endpoint must include explicit port.");

        PoolingHttpClientConnectionManager connMgr = new PoolingHttpClientConnectionManager();

        connMgr.setMaxTotal(connCount);
        connMgr.setDefaultMaxPerRoute(connCount);

        String route = endpoint.substring(0, endpoint.lastIndexOf(":"));
        HttpHost host = new HttpHost(route, port);
        RequestConfig reqConfig = RequestConfig.custom().setConnectionRequestTimeout(connTimeout).setConnectTimeout(connTimeout).setSocketTimeout(
            socketTimeout).build();

        connMgr.setMaxPerRoute(new HttpRoute(host), connCount / 2);
        return HttpClients.custom().setConnectionManager(connMgr).setDefaultRequestConfig(reqConfig).build();
    }
	
    
    /**
     * Enumeration of supported HTTP methods.
     *
     * @author  Bhinav Sura (bhinav.sura@salesforce.com)
     */
    private enum HttpMethod {

        /** POST operation. */
        POST,
        /** GET operation. */
        GET,
        /** DELETE operation. */
        DELETE,
        /** PUT operation. */
        PUT,
        /** HEAD operation. */
        HEAD;
    }
    
	
	/**
     * The set of implementation specific configuration properties.
     *
     * @author  Bhinav Sura (bhinav.sura@salesforce.com)
     */
    public enum Property {
        
        ELASTICSEARCH_ENDPOINT("service.property.schema.elasticsearch.endpoint", "http://localhost:9200"),
    	/** The TSDB connection timeout. */
    	ELASTICSEARCH_ENDPOINT_CONNECTION_TIMEOUT("service.property.schema.elasticsearch.endpoint.connection.timeout", "10000"),
        /** The TSDB socket connection timeout. */
    	ELASTICSEARCH_ENDPOINT_SOCKET_TIMEOUT("service.property.schema.elasticsearch.endpoint.socket.timeout", "10000"),
        /** The TSDB connection count. */
    	ELASTICSEARCH_CONNECTION_COUNT("service.property.schema.elasticsearch.connection.count", "10");

        private final String _name;
        private final String _defaultValue;

        private Property(String name, String defaultValue) {
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
        }
    	
		@JsonIgnoreProperties(ignoreUnknown = true)
    	static class CreateItem {
    		private String _index;
    		private String _type;
    		private String _id;
    		private int status;
    		private Map<String, String> errorMap;
    		
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
			
			public int getStatus() {
				return status;
			}
			
			public void setStatus(int status) {
				this.status = status;
			}

			@JsonProperty("error")
			public Map<String, String> getErrorMap() {
				return errorMap;
			}

			@JsonProperty("error")
			public void setErrorMap(Map<String, String> errorMap) {
				this.errorMap = errorMap;
			}
    	}
    }

}

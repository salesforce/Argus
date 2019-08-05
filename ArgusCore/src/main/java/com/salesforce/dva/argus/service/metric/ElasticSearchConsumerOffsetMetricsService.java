package com.salesforce.dva.argus.service.metric;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.MetricStorageService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.service.schema.ConsumerOffsetRecordList;
import com.salesforce.dva.argus.service.schema.ElasticSearchUtils;
import com.salesforce.dva.argus.service.schema.ElasticSearchUtils.HashAlgorithm;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
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
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

@Singleton
public class ElasticSearchConsumerOffsetMetricsService extends DefaultService implements MetricStorageService {

	private static Logger logger = LoggerFactory.getLogger(ElasticSearchConsumerOffsetMetricsService.class);
	private static ObjectMapper genericObjectMapper = new ObjectMapper();

	protected final MonitorService monitorService;

	/** Global ES properties */
	private static final int MAX_RETRY_TIMEOUT = 300_000;
	public static final int  INDEX_MAX_RESULT_WINDOW = 10000;
	public static final String DATE_FORMAT = "yyyy-MM-dd";
	public static final String INDEX_FORMAT = "%s-%s";

	private static final String FIELD_TYPE_TEXT = "text";
	private static final String FIELD_TYPE_DATE = "date";
	private static final String FIELD_TYPE_LONG = "long";
	private static final String FIELD_TYPE_KEYWORD = "keyword";

	private RestClient esRestClient;

	/** Index properties */
	private HashAlgorithm idgenHashAlgo;
	private static String INDEX_TEMPLATE_NAME;
	private final int REPLICATION_FACTOR;
	private final int NUM_SHARDS;
	private final ObjectMapper metricMapper;
	public static String INDEX_TEMPLATE_PATTERN_START;
	public static final String SCOPE_NAME = "ajna.consumer";
	public static final String METRIC_NAME = "metric.consumer.lag";
	public static final String requestUrl = "_bulk";


	private static final String EXCEPTION_MESSAGE = "Your query returns {0} or more metrics."
			+ " Please modify your query by reducing the time window.";

	@Inject
	protected ElasticSearchConsumerOffsetMetricsService(SystemConfiguration config, MonitorService monitorService, ElasticSearchUtils esUtils) {
		super(config);
		this.monitorService = monitorService;
		/** Setup Global ES stuff */
		String algorithm = config.getValue(Property.CONSUMER_OFFSET_METRICS_ES_IDGEN_HASH_ALGO.getName(), Property.CONSUMER_OFFSET_METRICS_ES_IDGEN_HASH_ALGO.getDefaultValue());
		try {
			idgenHashAlgo = HashAlgorithm.fromString(algorithm);
		} catch(IllegalArgumentException e) {
			logger.warn("{} is not supported by this service. Valid values are: {}.", algorithm, Arrays.asList(HashAlgorithm.values()));
			idgenHashAlgo = HashAlgorithm.MD5;
		}
		logger.info("Using {} for Elasticsearch document id generation.", idgenHashAlgo);

		String[] nodes = config.getValue(Property.CONSUMER_OFFSET_METRICS_ES_ENDPOINT.getName(), Property.CONSUMER_OFFSET_METRICS_ES_ENDPOINT.getDefaultValue()).split(",");
		HttpHost[] httpHosts = new HttpHost[nodes.length];
		for(int i=0; i<nodes.length; i++) {
			try {
				URL url = new URL(nodes[i]);
				httpHosts[i] = new HttpHost(url.getHost(), url.getPort());
			} catch (MalformedURLException e) {
				logger.error("One or more ElasticSearch endpoints are malformed. "
						+ "If you have configured only a single endpoint, then ESConsumerOffsetService will not function.", e);
			}
		}
		RestClientBuilder.HttpClientConfigCallback clientConfigCallback = httpClientBuilder -> {
			try {
				int connCount = Integer.parseInt(config.getValue(Property.CONSUMER_OFFSET_METRICS_ES_CONNECTION_COUNT.getName(),
						Property.CONSUMER_OFFSET_METRICS_ES_CONNECTION_COUNT.getDefaultValue()));
				PoolingNHttpClientConnectionManager connMgr =
						new PoolingNHttpClientConnectionManager(new DefaultConnectingIOReactor());
				connMgr.setMaxTotal(connCount);
				int connCountPerRoute = connCount/httpHosts.length;
				connMgr.setDefaultMaxPerRoute(connCountPerRoute < 1 ? 1:connCountPerRoute);
				httpClientBuilder.setConnectionManager(connMgr);
				return httpClientBuilder;
			} catch(Exception e) {
				throw new SystemException(e);
			}
		};
		RestClientBuilder.RequestConfigCallback requestConfigCallback = requestConfigBuilder -> {
			int connTimeout = Integer.parseInt(config.getValue(Property.CONSUMER_OFFSET_METRICS_ES_ENDPOINT_CONNECTION_TIMEOUT_MILLIS.getName(),
					Property.CONSUMER_OFFSET_METRICS_ES_ENDPOINT_CONNECTION_TIMEOUT_MILLIS.getDefaultValue()));
			int socketTimeout = Integer.parseInt(config.getValue(Property.CONSUMER_OFFSET_METRICS_ES_ENDPOINT_SOCKET_TIMEOUT.getName(),
					Property.CONSUMER_OFFSET_METRICS_ES_ENDPOINT_SOCKET_TIMEOUT.getDefaultValue()));
			requestConfigBuilder.setConnectTimeout(connTimeout).setSocketTimeout(socketTimeout);

			logger.info("esRestClient set connectionTimeoutMillis {} socketTimeoutMillis {}",
					connTimeout, socketTimeout);

			return requestConfigBuilder;
		};
		esRestClient = RestClient.builder(httpHosts)
				.setHttpClientConfigCallback(clientConfigCallback)
				.setRequestConfigCallback(requestConfigCallback)
				.setMaxRetryTimeoutMillis(MAX_RETRY_TIMEOUT)
				.build();
		logger.info("esRestClient set MaxRetryTimeoutsMillis {}", MAX_RETRY_TIMEOUT);

		/** Set up akc consumer offset index stuff */
		metricMapper = getMetricObjectMapper(new ConsumerOffsetRecordList.IndexSerializer(), new ConsumerOffsetRecordList.Deserializer());
		INDEX_TEMPLATE_NAME = config.getValue(Property.CONSUMER_OFFSET_METRICS_ES_INDEX_TEMPLATE_NAME.getName(),
				Property.CONSUMER_OFFSET_METRICS_ES_INDEX_TEMPLATE_NAME.getDefaultValue());
		INDEX_TEMPLATE_PATTERN_START = config.getValue(Property.CONSUMER_OFFSET_METRICS_ES_INDEX_TEMPLATE_PATTERN_START.getName(),
				Property.CONSUMER_OFFSET_METRICS_ES_INDEX_TEMPLATE_PATTERN_START.getDefaultValue());
		REPLICATION_FACTOR = Integer.parseInt(
				config.getValue(Property.CONSUMER_OFFSET_METRICS_ES_NUM_REPLICAS.getName(), Property.CONSUMER_OFFSET_METRICS_ES_NUM_REPLICAS.getDefaultValue()));
		NUM_SHARDS = Integer.parseInt(
				config.getValue(Property.CONSUMER_OFFSET_METRICS_ES_SHARDS_COUNT.getName(), Property.CONSUMER_OFFSET_METRICS_ES_SHARDS_COUNT.getDefaultValue()));
		esUtils.createIndexTemplate(esRestClient,
				INDEX_TEMPLATE_NAME,
				INDEX_TEMPLATE_PATTERN_START,
				()-> createIndexTemplateSettingsNode(REPLICATION_FACTOR, NUM_SHARDS),
				() -> createMappingsNode());
	}

	/**
	 * The set of implementation specific configuration properties.
	 *
	 */
	public enum Property {

		CONSUMER_OFFSET_METRICS_ES_ENDPOINT("service.property.akc.consumer.offset.elasticsearch.endpoint", "http://localhost:9200,http://localhost:9201"),
		/** Connection timeout for ES REST client. */
		CONSUMER_OFFSET_METRICS_ES_ENDPOINT_CONNECTION_TIMEOUT_MILLIS("service.property.akc.consumer.offset.elasticsearch.endpoint.connection.timeout", "10000"),
		/** Socket connection timeout for ES REST client. */
		CONSUMER_OFFSET_METRICS_ES_ENDPOINT_SOCKET_TIMEOUT("service.property.akc.consumer.offset.elasticsearch.endpoint.socket.timeout", "10000"),
		/** Connection count for ES REST client. */
		CONSUMER_OFFSET_METRICS_ES_CONNECTION_COUNT("service.property.akc.consumer.offset.elasticsearch.connection.count", "10"),
		/** The hashing algorithm to use for generating document id. */
		CONSUMER_OFFSET_METRICS_ES_IDGEN_HASH_ALGO("service.property.akc.consumer.offset.elasticsearch.idgen.hash.algo", "MD5"),
		/** Replication factor */
		CONSUMER_OFFSET_METRICS_ES_NUM_REPLICAS("service.property.akc.consumer.offset.elasticsearch.num.replicas", "1"),
		/** Shard count */
		CONSUMER_OFFSET_METRICS_ES_SHARDS_COUNT("service.property.akc.consumer.offset.elasticsearch.shards.count", "3"),
		/** Index template name */
		CONSUMER_OFFSET_METRICS_ES_INDEX_TEMPLATE_NAME("service.property.akc.consumer.offset.elasticsearch.indextemplate.name", "argus-akc-consumer-offset-template"),
		/** Index template pattern match */
		CONSUMER_OFFSET_METRICS_ES_INDEX_TEMPLATE_PATTERN_START("service.property.akc.consumer.offset.elasticsearch.indextemplate.patternstart", "argus-akc-consumer-offset");

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

	private ObjectNode createMappingsNode() {
		ObjectNode propertiesNode = genericObjectMapper.createObjectNode();
		propertiesNode.set(ConsumerOffsetRecordList.ConsumerOffsetRecordType.METRIC.getName(), createFieldNodeAnalyzer(FIELD_TYPE_TEXT));
		propertiesNode.set(ConsumerOffsetRecordList.ConsumerOffsetRecordType.TOPIC.getName(), createFieldNodeAnalyzer(FIELD_TYPE_TEXT));
		propertiesNode.set(ConsumerOffsetRecordList.ConsumerOffsetRecordType.VALUE.getName(), _createFieldNodeNoAnalyzer(FIELD_TYPE_LONG));
		propertiesNode.set(ConsumerOffsetRecordList.ConsumerOffsetRecordType.TIMESERIES.getName(), _createFieldNodeNoAnalyzer(FIELD_TYPE_DATE));
		propertiesNode.set(ConsumerOffsetRecordList.ConsumerOffsetRecordType.TAGS.getName(), createFieldNodeAnalyzer(FIELD_TYPE_TEXT));

		ObjectNode docNode = genericObjectMapper.createObjectNode();
		docNode.set("properties", propertiesNode);

		ObjectNode mappingsNode = genericObjectMapper.createObjectNode();
		mappingsNode.set("_doc", docNode);

		logger.info("Setting up Mapping for Consumer Offset Metric Service - {}", mappingsNode.toString());
		return mappingsNode;
	}

	private ObjectNode _createFieldNodeNoAnalyzer(String type) {
		ObjectNode fieldNode = genericObjectMapper.createObjectNode();
		fieldNode.put("type", type);
		return fieldNode;
	}

	private ObjectNode createFieldNodeAnalyzer(String type) {
		ObjectNode fieldNode = genericObjectMapper.createObjectNode();
		fieldNode.put("type", type);
		fieldNode.put("analyzer", "akc-consumer-offset_analyzer");
		ObjectNode keywordNode = genericObjectMapper.createObjectNode();
		keywordNode.put("type", FIELD_TYPE_KEYWORD);
		ObjectNode fieldsNode = genericObjectMapper.createObjectNode();
		fieldsNode.set("raw", keywordNode);
		fieldNode.set("fields", fieldsNode);
		return fieldNode;
	}

	private ObjectNode createIndexTemplateSettingsNode(int replicationFactor, int numShards) {
		ObjectNode metricAnalyzer = genericObjectMapper.createObjectNode();
		metricAnalyzer.put("tokenizer", "akc-consumer-offset_tokenizer");
		metricAnalyzer.set("filter", genericObjectMapper.createArrayNode().add("lowercase"));

		ObjectNode analyzerNode = genericObjectMapper.createObjectNode();
		analyzerNode.set("akc-consumer-offset_analyzer", metricAnalyzer);

		ObjectNode tokenizerNode = genericObjectMapper.createObjectNode();
		tokenizerNode.set("akc-consumer-offset_tokenizer", genericObjectMapper.createObjectNode().put("type", "pattern").put("pattern", ElasticSearchUtils.TOKENIZER_PATTERN));

		ObjectNode analysisNode = genericObjectMapper.createObjectNode();
		analysisNode.set("analyzer", analyzerNode);
		analysisNode.set("tokenizer", tokenizerNode);

		ObjectNode indexNode = genericObjectMapper.createObjectNode();
		indexNode.put("max_result_window", INDEX_MAX_RESULT_WINDOW);
		indexNode.put("number_of_replicas", replicationFactor);
		indexNode.put("number_of_shards", numShards);

		ObjectNode settingsNode = genericObjectMapper.createObjectNode();
		settingsNode.set("analysis", analysisNode);
		settingsNode.set("index", indexNode);

		logger.info("Setting up Index Template for Consumer Offset Metric Service - {}", settingsNode.toString());
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

	@VisibleForTesting
	static ObjectMapper getMetricObjectMapper(JsonSerializer<ConsumerOffsetRecordList> serializer, JsonDeserializer<ConsumerOffsetRecordList> deserializer) {
		ObjectMapper mapper = new ObjectMapper();
		mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		SimpleModule module = new SimpleModule();
		module.addSerializer(ConsumerOffsetRecordList.class, serializer);
		module.addDeserializer(ConsumerOffsetRecordList.class, deserializer);
		mapper.registerModule(module);

		return mapper;
	}

	@Override
	public void putMetrics(List<Metric> metrics) {
		requireArgument(metrics.size() > 0, "Cannot push empty list of metrics to ES.");
		try {
			final long start = System.currentTimeMillis();
			ConsumerOffsetRecordList indexRecordList = new ConsumerOffsetRecordList(ConsumerOffsetMetric.convertToConsumerOffsetMetrics(metrics), idgenHashAlgo);
			String requestBody = metricMapper.writeValueAsString(indexRecordList);
			Set<ConsumerOffsetMetric> failedRecords = new HashSet<>();
			ElasticSearchUtils.PutResponse putResponse = ElasticSearchUtils.performESRequest(esRestClient, requestUrl, requestBody);
			final long latency = System.currentTimeMillis() - start;
			monitorService.modifyCounter(MonitorService.Counter.CONSUMER_OFFSET_RECORDS_WRITE_LATENCY, latency, null);

			if(putResponse.isErrors()) {
				for(ElasticSearchUtils.PutResponse.Item item : putResponse.getItems()) {
					if (item.getIndex() != null && item.getIndex().getStatus() != HttpStatus.SC_CREATED) {
						logger.warn("Failed to add record {} to index. Reason: {}",
								indexRecordList.getRecord(item.getIndex().get_id()),
								metricMapper.writeValueAsString(item.getIndex().getError()));
						failedRecords.add(indexRecordList.getRecord(item.getIndex().get_id()));
					}
				}
			}

			if (failedRecords.size() > 0) {
				logger.warn("{} records were not written to Consumer Offset ES", failedRecords.size());
				monitorService.modifyCounter(MonitorService.Counter.CONSUMER_OFFSET_RECORDS_WRITE_FAILURES, failedRecords.size(), null);
			}

		} catch (IOException e) {
			throw new SystemException("Failed to index consumer offset metric to ES.", e);
		}
	}

	@Override
	public Map<MetricQuery, List<Metric>> getMetrics(List<MetricQuery> queries) {
		requireNotDisposed();
		requireArgument(queries != null, "ConsumerOffset queries cannot be null.");
		int from = 0, scrollSize = INDEX_MAX_RESULT_WINDOW, numOfMetrics = 0;

		Map<MetricQuery, List<Metric> > result = new HashMap<>();
		String requestUrl = String.format("/%s-*/_search", INDEX_TEMPLATE_PATTERN_START);
		try {
			for (MetricQuery query : queries) {
				List<ConsumerOffsetMetric> consumerOffsetMetrics = new ArrayList<>();
				String queryJson = constructQuery(new MetricQuery(query), from, scrollSize);
				final long start = System.currentTimeMillis();
				Request request = new Request(ElasticSearchUtils.HttpMethod.POST.getName(), requestUrl);
				request.setEntity(new StringEntity(queryJson, ContentType.APPLICATION_JSON));
				Response response = esRestClient.performRequest(request);
				final long latency = System.currentTimeMillis() - start;
				monitorService.modifyCounter(MonitorService.Counter.CONSUMER_OFFSET_RECORDS_READ_LATENCY, latency, null);
				logger.debug("ES get request for consumer offset completed in {} ms", latency);
				String extractResponse = ElasticSearchUtils.extractResponse(response);
				ConsumerOffsetRecordList recordList = ElasticSearchUtils.toEntity(extractResponse, new TypeReference<ConsumerOffsetRecordList>() {}, metricMapper);
				consumerOffsetMetrics.addAll(recordList.getRecords());
				result.put(query, ConsumerOffsetMetric.convertToMetrics(consumerOffsetMetrics));

				numOfMetrics += consumerOffsetMetrics.size();
				if(numOfMetrics >= scrollSize) {
					logger.error("Maximum metrics limit execeeded for query- " + query.toString());
					throw new RuntimeException(MessageFormat.format(EXCEPTION_MESSAGE, scrollSize));
				}
			}
		} catch(IOException ex) {
			throw new SystemException(ex);
		}
		return result;
	}

	@VisibleForTesting
	protected String constructQuery(MetricQuery query, int scrollFrom, int size) {
		query.setStartTimestamp(ElasticSearchUtils.convertTimestampToMillis(query.getStartTimestamp()));
		query.setEndTimestamp(ElasticSearchUtils.convertTimestampToMillis(query.getEndTimestamp()));
		ObjectNode aggsNode = constructAggregationNode(query, genericObjectMapper);
		ObjectNode queryNode = constructQueryNode(query, genericObjectMapper);
		ObjectNode rootNode = metricMapper.createObjectNode();
		if (aggsNode != null && queryNode != null) {
			rootNode.set("aggs", aggsNode);
			rootNode.set("query", queryNode);
			rootNode.put("from", scrollFrom);
			rootNode.put("size", size);

			return rootNode.toString();
		} else {
			logger.error("Failed to construct query");
			return null;
		}
	}

	private ObjectNode addParentNodeWithNameInOrder(ObjectNode node, int index, String... nodeNameList) {
		if (nodeNameList.length == index) {
			return node;
		}
		ObjectNode parentNode = genericObjectMapper.createObjectNode();
		parentNode.set(nodeNameList[index], node);
		return  addParentNodeWithNameInOrder(parentNode, index + 1, nodeNameList);
	}

	private ObjectNode addParentNodeWithNameInOrder(String value, int index, String... nodeNameList) {
		ObjectNode parentNode = genericObjectMapper.createObjectNode();
		if (0 == index) {
			parentNode.put(nodeNameList[index], value);
			return parentNode;
		}
		ObjectNode node = addParentNodeWithNameInOrder(value, index - 1, nodeNameList);
		parentNode.set(nodeNameList[index], node);
		return parentNode;
	}

	private ObjectNode constructQueryNode(MetricQuery query, ObjectMapper mapper) {
		try {
			Map<String, String> tags = query.getTags();
			Long startTimestamp = query.getStartTimestamp(), endTimestamp = query.getEndTimestamp();
			requireArgument(startTimestamp != null && endTimestamp != null, "Start and end timestamps should be present while querying.");

			String topicFieldName = ConsumerOffsetRecordList.ConsumerOffsetRecordType.TOPIC.getName();
			String tagFieldName = ConsumerOffsetRecordList.ConsumerOffsetRecordType.TAGS.getName();
			String tsFieldName = ConsumerOffsetRecordList.ConsumerOffsetRecordType.TIMESERIES.getName();

			ArrayNode filterNode = mapper.createArrayNode();

			if (tags != null && tags.containsKey(topicFieldName)) {
				String topicFilters = tags.get(topicFieldName);
				tags.remove(topicFieldName);
				String regexPattern = SchemaService.convertToRegex(topicFilters);
				ObjectNode topicFilterNode = addParentNodeWithNameInOrder(regexPattern, 1, topicFieldName + ".raw", "regexp");
				filterNode.add(topicFilterNode);
			}

			if (tags != null && tags.size() > 0) {
				for (Map.Entry<String, String> tag: query.getTags().entrySet()) {
					String rawTagString = SchemaService.convertToRegex(mapper.writeValueAsString(tag));
					rawTagString = rawTagString.substring(1, rawTagString.length() - 1);
					String regexPattern = ".*(" + rawTagString.replaceAll("\"", "[\"]") + ").*";
					ObjectNode regexFilterNode = addParentNodeWithNameInOrder(regexPattern, 1, tagFieldName + ".raw", "regexp");
					filterNode.add(regexFilterNode);
				}
			}

			ObjectNode timeSeriesRangeNode = mapper.createObjectNode();
			timeSeriesRangeNode.put("gte", Long.toString(startTimestamp));
			timeSeriesRangeNode.put("lte", Long.toString(endTimestamp));
			ObjectNode timeSeriesNode = addParentNodeWithNameInOrder(timeSeriesRangeNode, 0, tsFieldName, "range");

			ArrayNode mustNode = mapper.createArrayNode();
			mustNode.add(timeSeriesNode);

			ObjectNode compoundNode = mapper.createObjectNode();
			compoundNode.set("must", mustNode);
			compoundNode.set("filter", filterNode);

			ObjectNode queryNode = addParentNodeWithNameInOrder(compoundNode, 0, "bool");
			return queryNode;
		} catch (Exception e) {
			logger.error("Failed to construct query node -{}", e);
			return null;
		}
	}

	private ObjectNode constructAggregationNode(MetricQuery query, ObjectMapper mapper) {
		try {
			Long downsamplingPeriod = query.getDownsamplingPeriod();
			MetricQuery.Aggregator downsamplerAgg = query.getDownsampler();
			requireArgument(downsamplerAgg != null, "Downsampler aggregation should be present while querying for consumer offset.");
			requireArgument(downsamplingPeriod != null, "Downsampling period should be present while querying for consumer offset.");
			Long convertMillistoMinute = Long.valueOf(60 * 1000);

			String topicFieldName = ConsumerOffsetRecordList.ConsumerOffsetRecordType.TOPIC.getName();
			String valueFieldName = ConsumerOffsetRecordList.ConsumerOffsetRecordType.VALUE.getName();
			String tsFieldName = ConsumerOffsetRecordList.ConsumerOffsetRecordType.TIMESERIES.getName();

			ObjectNode maxOffsetGreaterThanAggNode = addParentNodeWithNameInOrder(valueFieldName, 2, "field", downsamplerAgg.getDescription(), "max_offset_greater_than");

			ObjectNode dateHistogramNode = mapper.createObjectNode();
			dateHistogramNode.put("field", tsFieldName);
			dateHistogramNode.put("interval", String.format("%sm", downsamplingPeriod / convertMillistoMinute));

			ObjectNode maxOffsetPerUnitTimeGreaterThanNode = mapper.createObjectNode();
			maxOffsetPerUnitTimeGreaterThanNode.set("date_histogram", dateHistogramNode);
			maxOffsetPerUnitTimeGreaterThanNode.set("aggs", maxOffsetGreaterThanAggNode);
			ObjectNode maxOffsetPerUnitTimeGreaterThanAggNode = addParentNodeWithNameInOrder(maxOffsetPerUnitTimeGreaterThanNode, 0, "max_offset_per_unit_time_greater_than");

			ObjectNode topicBucketNode = addParentNodeWithNameInOrder(topicFieldName + ".raw", 0, "field");
			ObjectNode maxTopicOffsetPerUnitTimeGreaterThanAggNode = mapper.createObjectNode();
			maxTopicOffsetPerUnitTimeGreaterThanAggNode.set("terms", topicBucketNode);
			maxTopicOffsetPerUnitTimeGreaterThanAggNode.set("aggs", maxOffsetPerUnitTimeGreaterThanAggNode);

			ObjectNode parentNode = addParentNodeWithNameInOrder(maxTopicOffsetPerUnitTimeGreaterThanAggNode, 0, "max_topic_offset_per_unit_time_greater_than");

			return parentNode;
		} catch (Exception e) {
			logger.error("Failed to construct aggregate node - {}", e);
			return null;
		}
	}
}

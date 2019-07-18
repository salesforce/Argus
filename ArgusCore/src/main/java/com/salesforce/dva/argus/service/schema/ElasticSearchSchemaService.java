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
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.elasticsearch.client.Request;
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

import static com.salesforce.dva.argus.entity.MetricSchemaRecord.RETENTION_DISCOVERY;
import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Implementation of the schema service using ElasticSearch.
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
@Singleton
public class ElasticSearchSchemaService extends AbstractSchemaService {

	private static Logger _logger = LoggerFactory.getLogger(ElasticSearchSchemaService.class);

	/** Global ES properties */
	private static final String KEEP_SCROLL_CONTEXT_OPEN_FOR = "1m";
	static final String SCROLL_ENDPOINT = "/_search/scroll";
	private static final int MAX_RETRY_TIMEOUT = 300 * 1000;
	private static final String FIELD_TYPE_TEXT = "text";
	private static final String FIELD_TYPE_DATE ="date";
	private static final String FIELD_TYPE_INTEGER = "integer";
	private RestClient _esRestClient;
	private final int _bulkIndexingSize;
	private HashAlgorithm _idgenHashAlgo;

	/** Main index properties */
	private static String TAGS_INDEX_NAME;
	private static String TAGS_TYPE_NAME;
	private final ObjectMapper indexMetadataMapper;

	/** Scope-only index properties */
	private static String SCOPE_INDEX_NAME;
	private static String SCOPE_TYPE_NAME;
	private final ObjectMapper indexScopeOnlyMapper;

	/** Metatags index properties */
	private static String METATAGS_INDEX_NAME;
	private static String METATAGS_TYPE_NAME;
	private final ObjectMapper indexMetatagsMapper;

	private static ObjectMapper genericObjectMapper = new ObjectMapper();


    @Inject
	public ElasticSearchSchemaService(SystemConfiguration config, MonitorService monitorService, ElasticSearchUtils esUtils) {
                super(config, monitorService);

		/* Setup Global ES stuff */
		String algorithm = config.getValue(Property.ELASTICSEARCH_IDGEN_HASH_ALGO.getName(), Property.ELASTICSEARCH_IDGEN_HASH_ALGO.getDefaultValue());
		try {
			_idgenHashAlgo = HashAlgorithm.fromString(algorithm);
		} catch(IllegalArgumentException e) {
			_logger.warn("{} is not supported by this service. Valid values are: {}.", algorithm, Arrays.asList(HashAlgorithm.values()));
			_idgenHashAlgo = HashAlgorithm.MD5;
		}
		_logger.info("Using {} for Elasticsearch document id generation.", _idgenHashAlgo);
		_bulkIndexingSize = Integer.parseInt(
				config.getValue(Property.ELASTICSEARCH_INDEXING_BATCH_SIZE.getName(), Property.ELASTICSEARCH_INDEXING_BATCH_SIZE.getDefaultValue()));

		String[] nodes = config.getValue(Property.ELASTICSEARCH_ENDPOINT.getName(), Property.ELASTICSEARCH_ENDPOINT.getDefaultValue()).split(",");
		HttpHost[] httpHosts = new HttpHost[nodes.length];
		for(int i=0; i<nodes.length; i++) {
			try {
				URL url = new URL(nodes[i]);
				httpHosts[i] = new HttpHost(url.getHost(), url.getPort());
			} catch (MalformedURLException e) {
				_logger.error("One or more ElasticSearch endpoints are malformed. "
						+ "If you have configured only a single endpoint, then ESSchemaService will not function.", e);
			}
		}
		HttpClientConfigCallback clientConfigCallback = httpClientBuilder -> {
			try {
				int connCount = Integer.parseInt(config.getValue(Property.ELASTICSEARCH_CONNECTION_COUNT.getName(),
						Property.ELASTICSEARCH_CONNECTION_COUNT.getDefaultValue()));
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
		RequestConfigCallback requestConfigCallback = requestConfigBuilder -> {
			int connTimeout = Integer.parseInt(config.getValue(Property.ELASTICSEARCH_ENDPOINT_CONNECTION_TIMEOUT.getName(),
					Property.ELASTICSEARCH_ENDPOINT_CONNECTION_TIMEOUT.getDefaultValue()));
			int socketTimeout = Integer.parseInt(config.getValue(Property.ELASTICSEARCH_ENDPOINT_SOCKET_TIMEOUT.getName(),
					Property.ELASTICSEARCH_ENDPOINT_SOCKET_TIMEOUT.getDefaultValue()));
			requestConfigBuilder.setConnectTimeout(connTimeout).setSocketTimeout(socketTimeout);

			_logger.info("_esRestClient set connTimeoutMillis {} socketTimeoutMillis {}",
					connTimeout, socketTimeout);

			return requestConfigBuilder;
		};
		_esRestClient = RestClient.builder(httpHosts)
				.setHttpClientConfigCallback(clientConfigCallback)
				.setRequestConfigCallback(requestConfigCallback)
				.setMaxRetryTimeoutMillis(MAX_RETRY_TIMEOUT)
				.build();
		_logger.info("_esRestClient set MaxRetryTimeoutsMillis {}", MAX_RETRY_TIMEOUT);

		/* Set up main index stuff */
		indexMetadataMapper = _getMetadataObjectMapper(new MetricSchemaRecordList.IndexSerializer());
		TAGS_INDEX_NAME = config.getValue(Property.ELASTICSEARCH_TAGS_INDEX_NAME.getName(),
				Property.ELASTICSEARCH_TAGS_INDEX_NAME.getDefaultValue());
		TAGS_TYPE_NAME = config.getValue(Property.ELASTICSEARCH_TAGS_TYPE_NAME.getName(),
				Property.ELASTICSEARCH_TAGS_TYPE_NAME.getDefaultValue());
		int replicationFactor = Integer.parseInt(
				config.getValue(Property.ELASTICSEARCH_NUM_REPLICAS_FOR_TAGS_INDEX.getName(), Property.ELASTICSEARCH_NUM_REPLICAS_FOR_TAGS_INDEX.getDefaultValue()));
		int numShards = Integer.parseInt(
				config.getValue(Property.ELASTICSEARCH_SHARDS_COUNT_FOR_TAGS_INDEX.getName(), Property.ELASTICSEARCH_SHARDS_COUNT_FOR_TAGS_INDEX.getDefaultValue()));

		esUtils.createIndexIfNotExists(_esRestClient,
									   TAGS_INDEX_NAME,
									   replicationFactor,
									   numShards,
									   () -> _createMappingsNode());


		/** Set up scope-only index stuff */
		indexScopeOnlyMapper = _getScopeOnlyObjectMapper(new ScopeOnlySchemaRecordList.IndexSerializer());
		SCOPE_INDEX_NAME = config.getValue(Property.ELASTICSEARCH_SCOPE_INDEX_NAME.getName(),
				Property.ELASTICSEARCH_SCOPE_INDEX_NAME.getDefaultValue());
		SCOPE_TYPE_NAME = config.getValue(Property.ELASTICSEARCH_SCOPE_TYPE_NAME.getName(),
				Property.ELASTICSEARCH_SCOPE_TYPE_NAME.getDefaultValue());
		int replicationFactorForScopeIndex = Integer.parseInt(
				config.getValue(Property.ELASTICSEARCH_NUM_REPLICAS_FOR_SCOPE_INDEX.getName(), Property.ELASTICSEARCH_NUM_REPLICAS_FOR_SCOPE_INDEX.getDefaultValue()));
		int numShardsForScopeIndex = Integer.parseInt(
				config.getValue(Property.ELASTICSEARCH_SHARDS_COUNT_FOR_SCOPE_INDEX.getName(), Property.ELASTICSEARCH_SHARDS_COUNT_FOR_SCOPE_INDEX.getDefaultValue()));

		esUtils.createIndexIfNotExists(_esRestClient,
									   SCOPE_INDEX_NAME,
									   replicationFactorForScopeIndex,
									   numShardsForScopeIndex,
									   () -> _createScopeMappingsNode());

		/** Set up metatags index stuff */
		indexMetatagsMapper = _getMetatagsObjectMapper(new MetatagsSchemaRecordList.IndexSerializer());
		METATAGS_INDEX_NAME = config.getValue(Property.ELASTICSEARCH_METATAGS_INDEX_NAME.getName(),
				Property.ELASTICSEARCH_METATAGS_INDEX_NAME.getDefaultValue());
		METATAGS_TYPE_NAME = config.getValue(Property.ELASTICSEARCH_METATAGS_TYPE_NAME.getName(),
				Property.ELASTICSEARCH_METATAGS_TYPE_NAME.getDefaultValue());
		int replicationFactorForMetatagsIndex = Integer.parseInt(
				config.getValue(Property.ELASTICSEARCH_NUM_REPLICAS_FOR_METATAGS_INDEX.getName(),
						Property.ELASTICSEARCH_NUM_REPLICAS_FOR_METATAGS_INDEX.getDefaultValue()));
		int numShardsForMetatagsIndex = Integer.parseInt(
				config.getValue(Property.ELASTICSEARCH_SHARDS_COUNT_FOR_METATAGS_INDEX.getName(),
						Property.ELASTICSEARCH_SHARDS_COUNT_FOR_METATAGS_INDEX.getDefaultValue()));

		esUtils.createIndexIfNotExists(_esRestClient,
									   METATAGS_INDEX_NAME,
									   replicationFactorForMetatagsIndex,
									   numShardsForMetatagsIndex,
									   () -> _createMetatagsMappingsNode());

	}

	@Override
	public void dispose() {
		super.dispose();
		try {
			_esRestClient.close();
			_logger.info("Shutdown of ElasticSearch RESTClient complete");
		} catch (IOException e) {
			_logger.warn("ElasticSearch RestClient failed to shutdown properly.", e);
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
	protected void implementationSpecificPut(Set<Metric> metricsToIndex,
											 Set<String> scopesToIndex,
											 Set<MetatagsRecord> metatagsToIndex) {
		SystemAssert.requireArgument(metricsToIndex != null, "Metrics list cannot be null.");

		// Push to metadata index
		int successCount = 0;
		long start = System.currentTimeMillis();
		long timeTaken;
		List<Set<MetricSchemaRecord>> fracturedSchemas = _fracture(metricsToIndex);
		for(Set<MetricSchemaRecord> records : fracturedSchemas) {
			if(!records.isEmpty()) {
				Set<MetricSchemaRecord> failedRecords = doBulkIndex(TAGS_INDEX_NAME, TAGS_TYPE_NAME, new MetricSchemaRecordList(records, _idgenHashAlgo), indexMetadataMapper);
				records.removeAll(failedRecords);
				_addToModifiedBloom(records);
				successCount += records.size();
			}
		}

		timeTaken = System.currentTimeMillis() - start;
		_monitorService.modifyCounter(MonitorService.Counter.SCHEMARECORDS_WRITTEN, successCount, null);
		_monitorService.modifyCounter(MonitorService.Counter.SCHEMARECORDS_WRITE_LATENCY, timeTaken, null);
		_logger.info("{} schema records sent to ES and bloomFilter in {} ms.", successCount, timeTaken);

		// Push to scope-only index
		successCount = 0;
		start = System.currentTimeMillis();
		List<Set<ScopeOnlySchemaRecord>> fracturedScopes = _fractureScopes(scopesToIndex);
		for(Set<ScopeOnlySchemaRecord> records : fracturedScopes) {
			if(!records.isEmpty()) {
				Set<ScopeOnlySchemaRecord> failedRecords = doBulkIndex(SCOPE_INDEX_NAME, SCOPE_TYPE_NAME, new ScopeOnlySchemaRecordList(records, _idgenHashAlgo), indexScopeOnlyMapper);
				records.removeAll(failedRecords);
				_addToModifiedBloom(records);
				successCount += records.size();
			}
		}

		timeTaken = System.currentTimeMillis() - start;
		_monitorService.modifyCounter(MonitorService.Counter.SCOPENAMES_WRITTEN, successCount, null);
		_monitorService.modifyCounter(MonitorService.Counter.SCOPENAMES_WRITE_LATENCY, timeTaken, null);
		_logger.info("{} scopes sent to ES and bloomFilter in {} ms.", successCount, timeTaken);

		// Push to metatags index
		successCount = 0;
		start = System.currentTimeMillis();
		List<Set<MetatagsRecord>> fracturedMetatags = _fractureMetatags(metatagsToIndex);
		for(Set<MetatagsRecord> records : fracturedMetatags) {
			if(!records.isEmpty()) {
				Set<MetatagsRecord> failedRecords = doBulkIndex(METATAGS_INDEX_NAME, METATAGS_TYPE_NAME, new MetatagsSchemaRecordList(records, _idgenHashAlgo), indexMetatagsMapper);
				records.removeAll(failedRecords);
				_addToModifiedBloom(records);
				successCount += records.size();
			}
		}

		timeTaken = System.currentTimeMillis() - start;
		_monitorService.modifyCounter(MonitorService.Counter.METATAGS_WRITTEN, successCount, null);
		_monitorService.modifyCounter(Counter.METATAGS_WRITE_LATENCY, timeTaken, null);
		_logger.info("{} metatags sent to ES and bloomFilter in {} ms.", successCount, timeTaken);
	}

	/* Convert the given list of metrics to a list of metric schema records. At the same time, fracture the records list
	 * if its size is greater than ELASTICSEARCH_INDEXING_BATCH_SIZE.
	 */
	protected List<Set<MetricSchemaRecord>> _fracture(Set<Metric> metrics) {
		List<Set<MetricSchemaRecord>> fracturedList = new ArrayList<>();

		Set<MetricSchemaRecord> records = new HashSet<>(_bulkIndexingSize);
		for(Metric metric : metrics) {
			if(metric.getTags().isEmpty()) {
				MetricSchemaRecord msr = new MetricSchemaRecord(metric.getScope(), metric.getMetric());
				msr.setNamespace(metric.getNamespace());
				records.add(msr);
				if(records.size() == _bulkIndexingSize) {
					fracturedList.add(records);
					records = new HashSet<>(_bulkIndexingSize);
				}
				continue;
			}

			String retention  = metric.getMetatagsRecord()==null?null:metric.getMetatagsRecord().getMetatagValue(RETENTION_DISCOVERY);
			Integer retentionInt = null;
			if (retention != null) {
				try {
					retentionInt = Integer.parseInt(retention);
				}
				catch(NumberFormatException e) {
					_logger.debug("expect _retention_discovery_ to be a numeric value; {} is invalid", retention);
				}
			}
			for(Map.Entry<String, String> entry : metric.getTags().entrySet()) {
				records.add(new MetricSchemaRecord(metric.getNamespace(),
													metric.getScope(),
													metric.getMetric(),
													entry.getKey(),
													entry.getValue(),
													retentionInt));
				if(records.size() == _bulkIndexingSize) {
					fracturedList.add(records);
					records = new HashSet<>(_bulkIndexingSize);
				}
			}
		}

		if(!records.isEmpty()) {
			fracturedList.add(records);
		}

		return fracturedList;
	}

	/* Convert the given list of scopes to a list of scope only schema records. At the same time, fracture the records list
	 * if its size is greater than ELASTICSEARCH_INDEXING_BATCH_SIZE.
	 */
	protected List<Set<ScopeOnlySchemaRecord>> _fractureScopes(Set<String> scopeNames) {
		List<Set<ScopeOnlySchemaRecord>> fracturedList = new ArrayList<>();

		Set<ScopeOnlySchemaRecord> records = new HashSet<>(_bulkIndexingSize);
		for(String scope : scopeNames) {
			records.add(new ScopeOnlySchemaRecord(scope));

			if(records.size() == _bulkIndexingSize) {
				fracturedList.add(records);
				records = new HashSet<>(_bulkIndexingSize);
			}
		}

		if(!records.isEmpty()) {
			fracturedList.add(records);
		}

		return fracturedList;
	}

	protected List<Set<MetatagsRecord>> _fractureMetatags(Set<MetatagsRecord> metatagsToPut) {
		List<Set<MetatagsRecord>> fracturedList = new ArrayList<>();

		Set<MetatagsRecord> records = new HashSet<>(_bulkIndexingSize);
		for(MetatagsRecord record: metatagsToPut) {
			//remove this special metatag to prevent it from going to ES
			record.removeMetatag(RETENTION_DISCOVERY);
			MetatagsRecord mtag = new MetatagsRecord(record.getMetatags(), record.getKey());
			records.add(mtag);
			if(records.size() == _bulkIndexingSize) {
				fracturedList.add(records);
				records = new HashSet<>(_bulkIndexingSize);
			}
		}

		if(!records.isEmpty()) {
			fracturedList.add(records);
		}

		return fracturedList;
	}

	@Override
	public List<MetricSchemaRecord> get(MetricSchemaRecordQuery query) {
	    requireNotDisposed();
	    SystemAssert.requireArgument(query != null, "MetricSchemaRecordQuery cannot be null.");
	    SystemAssert.requireArgument(query.getLimit() >= 0, "Limit must be >= 0");
		SystemAssert.requireArgument(query.getPage() >= 1, "Page must be >= 1");
		try {
			Math.multiplyExact(query.getLimit(), query.getPage());
		} catch (ArithmeticException ex) {
			SystemAssert.requireArgument(true, "(limit * page) cannot result in int overflow");
		}

	    Map<String, String> tags = new HashMap<>();
	    tags.put("type", "REGEXP_WITHOUT_AGGREGATION");
	    long start = System.currentTimeMillis();
	    StringBuilder sb = new StringBuilder().append(String.format("/%s/%s/_search", TAGS_INDEX_NAME, TAGS_TYPE_NAME));

		List<MetricSchemaRecord> finalResult;
		try {
			/*
				If the limit is 0, this is an unbounded query from MetricQueryProcessor
				It is unknown whether the matched doc count will be <= ES window limit or greater at this point
				First, send a non-scroll request to get the doc count
					If the total doc count is > ES window limit, re-send the request with ?scroll and start scrolling
					   [ Need to re-ask for the entire first 10k since ordering / eliminating seen-documents is not guaranteed without scroll ]
					Else return
			 */
			if (query.getLimit() == 0) {
				MetricSchemaRecordList list = _getRecords(sb.toString(), _constructTermQuery(query, 0, ElasticSearchUtils.INDEX_MAX_RESULT_WINDOW));
				if (list.getTotalHits() > ElasticSearchUtils.INDEX_MAX_RESULT_WINDOW) {
					sb.append("?scroll=").append(KEEP_SCROLL_CONTEXT_OPEN_FOR);
					list = _getRecords(sb.toString(), _constructTermQuery(query, 0, ElasticSearchUtils.INDEX_MAX_RESULT_WINDOW));
					List<MetricSchemaRecord> records = new LinkedList<>(list.getRecords());
					_appendScrollRecordsUntilCountOrEnd(records, list.getScrollID(), query.getLimit() * query.getPage(), ElasticSearchUtils.INDEX_MAX_RESULT_WINDOW);
					finalResult = records;
				} else {
					finalResult = list.getRecords();
				}
				_monitorService.modifyCounter(Counter.SCHEMARECORDS_DOCS_PULLED, finalResult.size(), tags);
			}
			// If the user manually asks for a much later page and/or a high limit past the ES window limit, a scroll is mandatory
			else if (query.getLimit() * query.getPage() > ElasticSearchUtils.INDEX_MAX_RESULT_WINDOW) {
				sb.append("?scroll=").append(KEEP_SCROLL_CONTEXT_OPEN_FOR);
				MetricSchemaRecordList list = _getRecords(sb.toString(), _constructTermQuery(query, 0, ElasticSearchUtils.INDEX_MAX_RESULT_WINDOW));
				List<MetricSchemaRecord> records = new LinkedList<>(list.getRecords());
				_appendScrollRecordsUntilCountOrEnd(records, list.getScrollID(), query.getLimit() * query.getPage(), ElasticSearchUtils.INDEX_MAX_RESULT_WINDOW);

				int fromIndex = query.getLimit() * (query.getPage() - 1);
				if (records.size() <= fromIndex) {
					finalResult = Collections.emptyList();
				} else {
					finalResult = records.subList(fromIndex, records.size());
				}
				_monitorService.modifyCounter(Counter.SCHEMARECORDS_DOCS_PULLED, records.size(), tags);
			}
			// Otherwise no need to scroll
			else {
				int from = query.getLimit() * (query.getPage() - 1);
				MetricSchemaRecordList list = _getRecords(sb.toString(), _constructTermQuery(query, from, ElasticSearchUtils.INDEX_MAX_RESULT_WINDOW));
				finalResult = list.getRecords();
				_monitorService.modifyCounter(Counter.SCHEMARECORDS_DOCS_PULLED, finalResult.size(), tags);
			}
			_monitorService.modifyCounter(Counter.SCHEMARECORDS_QUERY_COUNT, 1, tags);
			_monitorService.modifyCounter(Counter.SCHEMARECORDS_QUERY_LATENCY, (System.currentTimeMillis() - start), tags);
		} catch (UnsupportedEncodingException | JsonProcessingException e) {
			throw new SystemException("Search failed: " + e);
		} catch (IOException e) {
			throw new SystemException("IOException when trying to perform ES request" + e);
		}
		return finalResult;
	}

	MetricSchemaRecordList _getRecords(String requestUrl, String queryJson) throws IOException {
		_logger.debug("get POST requestUrl {} queryJson {}", requestUrl, queryJson);
		Request request = new Request(HttpMethod.POST.getName(), requestUrl);
		request.setEntity(new StringEntity(queryJson, ContentType.APPLICATION_JSON));
		Response response = _esRestClient.performRequest(request);

		String esResponse = extractResponse(response);
		logAndMonitorESFailureResponses(esResponse);
		return toEntity(esResponse, new TypeReference<MetricSchemaRecordList>() {});
	}

	/**
	 * Appends documents to records argument by using ES Scroll API
	 * @param records			List to mutate and add scrolled records to
	 * @param startingScrollId	Starting scroll ID
	 * @param count				User-provied total count of docs to add to records (0 if unbounded)
	 * @param scrollSize		ES request "size" parameter
	 * @throws IOException
	 */
	void _appendScrollRecordsUntilCountOrEnd(List<MetricSchemaRecord> records, String startingScrollId, int count, int scrollSize) throws IOException {
		Map<String, String> requestBody = new HashMap<>();
		requestBody.put("scroll", KEEP_SCROLL_CONTEXT_OPEN_FOR);
		String scrollId = startingScrollId;

		while (true) {
			requestBody.put("scroll_id", scrollId);
			String requestJson = genericObjectMapper.writeValueAsString(requestBody);
			Request request = new Request(HttpMethod.POST.getName(), SCROLL_ENDPOINT);
			request.setEntity(new StringEntity(requestJson, ContentType.APPLICATION_JSON));
			Response response = _esRestClient.performRequest(request);

			MetricSchemaRecordList list = toEntity(extractResponse(response), new TypeReference<MetricSchemaRecordList>() {});
			records.addAll(list.getRecords());
			scrollId = list.getScrollID();

			// If total records retrieved is greater than what the user manually asked for
			// Or if we are on the last scroll page
			if(count != 0 && records.size() >= count || list.getRecords().size() < scrollSize) {
				break;
			}
		}
	}

	@Override
	public List<String> browseUnique(MetricSchemaRecordQuery query, RecordType type, int indexLevel) {

        List<MetricSchemaRecord> records = getUnique(query, type);

        SortedSet<String> tokens = MetricSchemaRecordTokenizer.GetUniqueTokens(records, type, indexLevel);

        return new ArrayList<>(tokens);
	}

	@Override
	public List<MetricSchemaRecord> getUnique(MetricSchemaRecordQuery query, RecordType type) {
		requireNotDisposed();
		SystemAssert.requireArgument(query != null, "MetricSchemaRecordQuery cannot be null.");
		long size = (long) query.getLimit() * query.getPage();
		SystemAssert.requireArgument(size > 0 && size <= Integer.MAX_VALUE,
				"(limit * page) must be greater than 0 and atmost Integer.MAX_VALUE");


		Map<String, String> tags = new HashMap<>();
		tags.put("type", "REGEXP_WITH_AGGREGATION");
		long start = System.currentTimeMillis();

		String indexName = TAGS_INDEX_NAME;
		String typeName = TAGS_TYPE_NAME;

		if (query.isQueryOnlyOnScope() && RecordType.SCOPE.equals(type))
		{
			indexName = SCOPE_INDEX_NAME;
			typeName = SCOPE_TYPE_NAME;
		}

		String requestUrl = new StringBuilder().append("/")
				.append(indexName)
				.append("/")
				.append(typeName)
				.append("/")
				.append("_search")
				.toString();

		try {

			String queryJson = _constructTermAggregationQuery(query, type);
			_logger.debug("getUnique POST requestUrl {} queryJson {}", requestUrl, queryJson);

			Request request = new Request(HttpMethod.POST.getName(), requestUrl);
			request.setEntity(new StringEntity(queryJson, ContentType.APPLICATION_JSON));
			Response response = _esRestClient.performRequest(request);
			String str = extractResponse(response);
			List<MetricSchemaRecord> records = SchemaService.constructMetricSchemaRecordsForType(toEntity(str, new TypeReference<List<String>>() {}), type);

			if (query.isQueryOnlyOnScope() && RecordType.SCOPE.equals(type)) {
				_monitorService.modifyCounter(Counter.SCOPENAMES_QUERY_COUNT, 1, tags);
				_monitorService.modifyCounter(Counter.SCOPENAMES_QUERY_LATENCY, (System.currentTimeMillis() - start), tags);

			} else {
				_monitorService.modifyCounter(Counter.SCHEMARECORDS_QUERY_COUNT, 1, tags);
				_monitorService.modifyCounter(Counter.SCHEMARECORDS_QUERY_LATENCY, (System.currentTimeMillis() - start), tags);
			}

			int fromIndex = query.getLimit() * (query.getPage() - 1);
			if(records.size() <= fromIndex) {
				return Collections.emptyList();
			}

			if(records.size() < query.getLimit() * query.getPage()) {
				return records.subList(fromIndex, records.size());
			} else {
				return records.subList(fromIndex, query.getLimit() * query.getPage());
			}

		} catch (IOException e) {
			throw new SystemException(e);
		}
	}

	@Override
	public List<MetricSchemaRecord> keywordSearch(KeywordQuery kq) {
		requireNotDisposed();
		SystemAssert.requireArgument(kq != null, "Query cannot be null.");
		SystemAssert.requireArgument(kq.getQuery() != null || kq.getType() != null, "Either the query string or the type must not be null.");

		long size = (long) kq.getLimit() * kq.getPage();
		SystemAssert.requireArgument(size > 0 && size <= Integer.MAX_VALUE,
				"(limit * page) must be greater than 0 and atmost Integer.MAX_VALUE");


		Map<String, String> tags = new HashMap<>();
		tags.put("type", "FTS_WITH_AGGREGATION");
		long start = System.currentTimeMillis();
		StringBuilder sb = new StringBuilder().append("/")
				.append(TAGS_INDEX_NAME)
				.append("/")
				.append(TAGS_TYPE_NAME)
				.append("/")
				.append("_search");
		try {

			if(kq.getQuery() != null) {

				int from = 0, scrollSize = 0;

				boolean scroll = false;;
				if(kq.getLimit() * kq.getPage() > ElasticSearchUtils.INDEX_MAX_RESULT_WINDOW) {
					sb.append("?scroll=").append(KEEP_SCROLL_CONTEXT_OPEN_FOR);
					scroll = true;
					scrollSize = ElasticSearchUtils.INDEX_MAX_RESULT_WINDOW;
				} else {
					from = kq.getLimit() * (kq.getPage() - 1);
					scrollSize = kq.getLimit();
				}

				List<String> tokens = _analyzedTokens(kq.getQuery());
				String queryJson = _constructQueryStringQuery(tokens, from, scrollSize);
				String requestUrl = sb.toString();

				Request request = new Request(HttpMethod.POST.getName(), requestUrl);
				request.setEntity(new StringEntity(queryJson, ContentType.APPLICATION_JSON));
				Response response = _esRestClient.performRequest(request);
				String strResponse = extractResponse(response);
				MetricSchemaRecordList list = toEntity(strResponse, new TypeReference<MetricSchemaRecordList>() {});

				if(scroll) {
					requestUrl = new StringBuilder().append("/").append("_search").append("/").append("scroll").toString();
					List<MetricSchemaRecord> records = new LinkedList<>(list.getRecords());

					while(true) {
						Map<String, String> requestBody = new HashMap<>();
						requestBody.put("scroll_id", list.getScrollID());
						requestBody.put("scroll", KEEP_SCROLL_CONTEXT_OPEN_FOR);

						request = new Request(HttpMethod.POST.getName(), requestUrl);
						request.setEntity(new StringEntity(genericObjectMapper.writeValueAsString(requestBody), ContentType.APPLICATION_JSON));
						response = _esRestClient.performRequest(request);
						list = toEntity(extractResponse(response), new TypeReference<MetricSchemaRecordList>() {});

						records.addAll(list.getRecords());

						if(records.size() >= kq.getLimit() * kq.getPage() || list.getRecords().size() < scrollSize) {
							break;
						}
					}

					int fromIndex = kq.getLimit() * (kq.getPage() - 1);
					if(records.size() <= fromIndex) {
						_monitorService.modifyCounter(Counter.SCHEMARECORDS_QUERY_COUNT, 1, tags);
						_monitorService.modifyCounter(Counter.SCHEMARECORDS_QUERY_LATENCY, (System.currentTimeMillis() - start), tags);
						return Collections.emptyList();
					}

					_monitorService.modifyCounter(Counter.SCHEMARECORDS_QUERY_COUNT, 1, tags);
					_monitorService.modifyCounter(Counter.SCHEMARECORDS_QUERY_LATENCY, (System.currentTimeMillis() - start), tags);
					return records.subList(fromIndex, records.size());

				} else {
					_monitorService.modifyCounter(Counter.SCHEMARECORDS_QUERY_COUNT, 1, tags);
					_monitorService.modifyCounter(Counter.SCHEMARECORDS_QUERY_LATENCY, (System.currentTimeMillis() - start), tags);
					return list.getRecords();
				}


			} else {
				Map<RecordType, List<String>> tokensMap = new HashMap<>();

				List<String> tokens = _analyzedTokens(kq.getScope());
				if(!tokens.isEmpty()) {
					tokensMap.put(RecordType.SCOPE, tokens);
				}

				tokens = _analyzedTokens(kq.getMetric());
				if(!tokens.isEmpty()) {
					tokensMap.put(RecordType.METRIC, tokens);
				}

				tokens = _analyzedTokens(kq.getTagKey());
				if(!tokens.isEmpty()) {
					tokensMap.put(RecordType.TAGK, tokens);
				}

				tokens = _analyzedTokens(kq.getTagValue());
				if(!tokens.isEmpty()) {
					tokensMap.put(RecordType.TAGV, tokens);
				}

				tokens = _analyzedTokens(kq.getNamespace());
				if(!tokens.isEmpty()) {
					tokensMap.put(RecordType.NAMESPACE, tokens);
				}

				String queryJson = _constructQueryStringQuery(kq, tokensMap);
				String requestUrl = sb.toString();

				Request request = new Request(HttpMethod.POST.getName(), requestUrl);
				request.setEntity(new StringEntity(queryJson, ContentType.APPLICATION_JSON));
				Response response = _esRestClient.performRequest(request);
				String strResponse = extractResponse(response);

				List<MetricSchemaRecord> records = SchemaService.constructMetricSchemaRecordsForType(
						toEntity(strResponse, new TypeReference<List<String>>() {}), kq.getType());

				int fromIndex = kq.getLimit() * (kq.getPage() - 1);
				if(records.size() <= fromIndex) {
					_monitorService.modifyCounter(Counter.SCHEMARECORDS_QUERY_COUNT, 1, tags);
					_monitorService.modifyCounter(Counter.SCHEMARECORDS_QUERY_LATENCY, (System.currentTimeMillis() - start), tags);
					return Collections.emptyList();
				}

				if(records.size() < kq.getLimit() * kq.getPage()) {
					_monitorService.modifyCounter(Counter.SCHEMARECORDS_QUERY_COUNT, 1, tags);
					_monitorService.modifyCounter(Counter.SCHEMARECORDS_QUERY_LATENCY, (System.currentTimeMillis() - start), tags);
					return records.subList(fromIndex, records.size());
				} else {
					_monitorService.modifyCounter(Counter.SCHEMARECORDS_QUERY_COUNT, 1, tags);
					_monitorService.modifyCounter(Counter.SCHEMARECORDS_QUERY_LATENCY, (System.currentTimeMillis() - start), tags);
					return records.subList(fromIndex, kq.getLimit() * kq.getPage());
				}

			}

		} catch (IOException e) {
			throw new SystemException(e);
		}
	}

	private List<String> _analyzedTokens(String query) {

		if(!SchemaService.containsFilter(query)) {
			return Collections.emptyList();
		}

		List<String> tokens = new ArrayList<>();

		String requestUrl = new StringBuilder("/").append(TAGS_INDEX_NAME).append("/_analyze").toString();

		String requestBody = "{\"analyzer\" : \"metadata_analyzer\", \"text\": \"" + query + "\" }";

		try {
			Request request = new Request(HttpMethod.POST.getName(), requestUrl);
			request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
			Response response = _esRestClient.performRequest(request);
			String strResponse = extractResponse(response);
			JsonNode tokensNode = indexMetadataMapper.readTree(strResponse).get("tokens");
			if(tokensNode.isArray()) {
				for(JsonNode tokenNode : tokensNode) {
					tokens.add(tokenNode.get("token").asText());
				}
			}

			return tokens;
		} catch (IOException e) {
			throw new SystemException(e);
		}
	}

	/**
	 * The generic bulk index method for any one of our existing indices
	 * @param indexName
	 * @param typeName
	 * @param recordFinder
	 * @param mapper
	 * @param <T>
	 * @return
	 */
	<T> Set<T> doBulkIndex(String indexName, String typeName, RecordFinder<T> recordFinder, ObjectMapper mapper) {
		String requestUrl = String.format("/%s/%s/_bulk", indexName, typeName);
		String strResponse;

		try {
			String requestBody = mapper.writeValueAsString(recordFinder);
			Request request = new Request(HttpMethod.POST.getName(), requestUrl);
			request.setEntity(new StringEntity(requestBody, ContentType.APPLICATION_JSON));
			Response response = _esRestClient.performRequest(request);
			strResponse = extractResponse(response);
		} catch (IOException e) {
			//TODO: Retry with exponential back-off for handling EsRejectedExecutionException/RemoteTransportException/TimeoutException??
			throw new SystemException(e);
		}

		try {
			Set<T> failedRecords = new HashSet<>();
			PutResponse putResponse = genericObjectMapper.readValue(strResponse, PutResponse.class);
			//TODO: If response contains HTTP 429 Too Many Requests (EsRejectedExecutionException), then retry with exponential back-off.
			if(putResponse.errors) {
				for(Item item : putResponse.items) {
					if (item.index !=null && item.index.status != HttpStatus.SC_CREATED) {
						_logger.warn("Failed to add record {} to index {}. Reason: {}", recordFinder.getRecord(item.index._id), indexName, genericObjectMapper.writeValueAsString(item.index.error));

						failedRecords.add(recordFinder.getRecord(item.index._id));
					}
				}

				if (failedRecords.size() != 0) {
					_logger.warn("{} records were not written to index {}", failedRecords.size(), indexName);
				}
			}
			return failedRecords;
		} catch(IOException e) {
			throw new SystemException("Failed to parse reponse of put metrics. The response was: " + strResponse, e);
		}
	}

	protected void _addToModifiedBloom(Set<? extends AbstractSchemaRecord> records) {
		for (AbstractSchemaRecord record : records) {
			bloomFilter.put(record.toBloomFilterKey());
		}
	}

	private String _constructTermAggregationQuery(MetricSchemaRecordQuery query, RecordType type) {
		ObjectNode queryNode = _constructQueryNode(query, genericObjectMapper);

		long size = query.getLimit() * query.getPage();
		SystemAssert.requireArgument(size > 0 && size <= Integer.MAX_VALUE,
				"(limit * page) must be greater than 0 and less than Integer.MAX_VALUE");

		ObjectNode aggsNode = _constructAggsNode(type, Math.max(size, ElasticSearchUtils.INDEX_MAX_RESULT_WINDOW), genericObjectMapper);

		ObjectNode rootNode = genericObjectMapper.createObjectNode();
		rootNode.set("query", queryNode);
		rootNode.put("size", 0);
		rootNode.set("aggs", aggsNode);

		return rootNode.toString();
	}

	private String _constructTermQuery(MetricSchemaRecordQuery query, int from, int size) {
		ObjectNode queryNode = _constructQueryNode(query, genericObjectMapper);

		ObjectNode rootNode = indexMetadataMapper.createObjectNode();
		rootNode.set("query", queryNode);
		rootNode.put("from", from);
		rootNode.put("size", size);
		return rootNode.toString();
	}

	private ObjectNode _constructSimpleQueryStringNode(List<String> tokens, RecordType... types) {

		if(tokens.isEmpty()) {
			return null;
		}

		StringBuilder queryString = new StringBuilder();
		for(String token : tokens) {
			queryString.append('+').append(token).append(' ');
		}
		queryString.replace(queryString.length() - 1, queryString.length(), "*");

		ObjectNode node = genericObjectMapper.createObjectNode();
		ArrayNode fieldsNode = genericObjectMapper.createArrayNode();
		for(RecordType type : types) {
			fieldsNode.add(type.getName());
		}
		node.set("fields", fieldsNode);
		node.put("query", queryString.toString());

		ObjectNode simpleQueryStringNode = genericObjectMapper.createObjectNode();
		simpleQueryStringNode.set("simple_query_string", node);

		return simpleQueryStringNode;
	}

	private String _constructQueryStringQuery(List<String> tokens, int from, int size) {
		ObjectNode simpleQueryStringNode = _constructSimpleQueryStringNode(tokens, RecordType.values());

		ObjectNode rootNode = genericObjectMapper.createObjectNode();
		rootNode.set("query", simpleQueryStringNode);
		rootNode.put("from", from);
		rootNode.put("size", size);

		return rootNode.toString();
	}

	private String _constructQueryStringQuery(KeywordQuery kq, Map<RecordType, List<String>> tokensMap) {
		ArrayNode filterNodes = genericObjectMapper.createArrayNode();
		for(Map.Entry<RecordType, List<String>> entry : tokensMap.entrySet()) {
			ObjectNode simpleQueryStringNode = _constructSimpleQueryStringNode(entry.getValue(), entry.getKey());
			filterNodes.add(simpleQueryStringNode);
		}

		ObjectNode boolNode = genericObjectMapper.createObjectNode();
		boolNode.set("filter", filterNodes);

		ObjectNode queryNode = genericObjectMapper.createObjectNode();
		queryNode.set("bool", boolNode);

		ObjectNode rootNode = genericObjectMapper.createObjectNode();
		rootNode.set("query", queryNode);
		rootNode.put("size", 0);

		long size = kq.getLimit() * kq.getPage();
		SystemAssert.requireArgument(size > 0 && size <= Integer.MAX_VALUE,
				"(limit * page) must be greater than 0 and less than Integer.MAX_VALUE");
		rootNode.set("aggs", _constructAggsNode(kq.getType(), Math.max(size, ElasticSearchUtils.INDEX_MAX_RESULT_WINDOW), genericObjectMapper));

		return rootNode.toString();

	}

	private ObjectNode _constructQueryNode(MetricSchemaRecordQuery query, ObjectMapper mapper) {
		ArrayNode filterNodes = mapper.createArrayNode();
		if(SchemaService.containsFilter(query.getMetric())) {
			ObjectNode node = mapper.createObjectNode();
			ObjectNode regexpNode = mapper.createObjectNode();
			regexpNode.put(RecordType.METRIC.getName() + ".raw", SchemaService.convertToRegex(query.getMetric()));
			node.set("regexp", regexpNode);
			filterNodes.add(node);
		}

		if(SchemaService.containsFilter(query.getScope())) {
			ObjectNode node = mapper.createObjectNode();
			ObjectNode regexpNode = mapper.createObjectNode();
			regexpNode.put(RecordType.SCOPE.getName() + ".raw", SchemaService.convertToRegex(query.getScope()));
			node.set("regexp", regexpNode);
			filterNodes.add(node);
		}

		if(SchemaService.containsFilter(query.getTagKey())) {
			ObjectNode node = mapper.createObjectNode();
			ObjectNode regexpNode = mapper.createObjectNode();
			regexpNode.put(RecordType.TAGK.getName() + ".raw", SchemaService.convertToRegex(query.getTagKey()));
			node.set("regexp", regexpNode);
			filterNodes.add(node);
		}

		ArrayNode mustNotNodes = mapper.createArrayNode();
		if(SchemaService.containsFilter(query.getTagValue())) {
			String trueTagValue = query.getTagValue();
			ArrayNode parentNode = filterNodes;
			if (query.getTagValue().charAt(0) == MetricQuery.TAG_NOT_EQUALS_INTERNAL_PREFIX.charAt(0)) {
				trueTagValue = trueTagValue.substring(1);
				parentNode = mustNotNodes;
			}
			ObjectNode node = mapper.createObjectNode();
			ObjectNode regexpNode = mapper.createObjectNode();
			regexpNode.put(RecordType.TAGV.getName() + ".raw", SchemaService.convertToRegex(trueTagValue));
			node.set("regexp", regexpNode);
			parentNode.add(node);
		}

		if(SchemaService.containsFilter(query.getNamespace())) {
			ObjectNode node = mapper.createObjectNode();
			ObjectNode regexpNode = mapper.createObjectNode();
			regexpNode.put(RecordType.NAMESPACE.getName() + ".raw", SchemaService.convertToRegex(query.getNamespace()));
			node.set("regexp", regexpNode);
			filterNodes.add(node);
		}

		ObjectNode boolNode = mapper.createObjectNode();
		boolNode.set("filter", filterNodes);
		if (mustNotNodes.size() > 0) {
			boolNode.set("must_not", mustNotNodes);
		}

		ObjectNode queryNode = mapper.createObjectNode();
		queryNode.set("bool", boolNode);
		return queryNode;
	}

	private ObjectNode _constructAggsNode(RecordType type, long limit, ObjectMapper mapper) {

		ObjectNode termsNode = mapper.createObjectNode();
		termsNode.put("field", type.getName() + ".raw");
		termsNode.set("order", mapper.createObjectNode().put("_term", "asc"));
		termsNode.put("size", limit);
		termsNode.put("execution_hint", "map");

		ObjectNode distinctValuesNode = mapper.createObjectNode();
		distinctValuesNode.set("terms", termsNode);

		ObjectNode aggsNode = mapper.createObjectNode();
		aggsNode.set("distinct_values", distinctValuesNode);
		return aggsNode;
	}


	/* Helper method to convert JSON String representation to the corresponding Java entity. */
	private <T> T toEntity(String content, TypeReference<T> type) {
		try {
			return indexMetadataMapper.readValue(content, type);
		} catch (IOException ex) {
			throw new SystemException(ex);
		}
	}

	/* Method to change the rest client. Used for testing. */
	protected void setRestClient(RestClient restClient)
	{
		this._esRestClient = restClient;
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

	@VisibleForTesting
	static ObjectMapper _getMetadataObjectMapper(JsonSerializer<MetricSchemaRecordList> serializer) {
		ObjectMapper mapper = new ObjectMapper();

		mapper.setSerializationInclusion(Include.NON_NULL);
		SimpleModule module = new SimpleModule();
		module.addSerializer(MetricSchemaRecordList.class, serializer);
		module.addDeserializer(MetricSchemaRecordList.class, new MetricSchemaRecordList.Deserializer());
		module.addDeserializer(List.class, new SchemaRecordList.AggDeserializer());
		mapper.registerModule(module);

		return mapper;
	}

	private ObjectMapper _getScopeOnlyObjectMapper(JsonSerializer<ScopeOnlySchemaRecordList> serializer) {
		ObjectMapper mapper = new ObjectMapper();

		mapper.setSerializationInclusion(Include.NON_NULL);
		SimpleModule module = new SimpleModule();
		module.addSerializer(ScopeOnlySchemaRecordList.class, serializer);
		module.addDeserializer(ScopeOnlySchemaRecordList.class, new ScopeOnlySchemaRecordList.Deserializer());
		module.addDeserializer(List.class, new SchemaRecordList.AggDeserializer());
		mapper.registerModule(module);

		return mapper;
	}

	private ObjectMapper _getMetatagsObjectMapper(JsonSerializer<MetatagsSchemaRecordList> serializer) {
		ObjectMapper mapper = new ObjectMapper();

		mapper.setSerializationInclusion(Include.NON_NULL);
		SimpleModule module = new SimpleModule();
		module.addSerializer(MetatagsSchemaRecordList.class, serializer);
		module.addDeserializer(MetatagsSchemaRecordList.class, new MetatagsSchemaRecordList.Deserializer());
		mapper.registerModule(module);

		return mapper;
	}

	private ObjectNode _createMappingsNode() {
		ObjectNode propertiesNode = genericObjectMapper.createObjectNode();
		propertiesNode.set(RecordType.SCOPE.getName(), _createFieldNode(FIELD_TYPE_TEXT));
		propertiesNode.set(RecordType.METRIC.getName(), _createFieldNode(FIELD_TYPE_TEXT));
		propertiesNode.set(RecordType.TAGK.getName(), _createFieldNode(FIELD_TYPE_TEXT));
		propertiesNode.set(RecordType.TAGV.getName(), _createFieldNode(FIELD_TYPE_TEXT));
		propertiesNode.set(RecordType.NAMESPACE.getName(), _createFieldNode(FIELD_TYPE_TEXT));
		propertiesNode.set(RecordType.RETENTION_DISCOVERY.getName(),
                                   _createFieldNodeNoAnalyzer(FIELD_TYPE_INTEGER));
		propertiesNode.set(MetricSchemaRecord.EXPIRATION_TS, _createFieldNodeNoAnalyzer(FIELD_TYPE_DATE));

		propertiesNode.set("mts", _createFieldNodeNoAnalyzer(FIELD_TYPE_DATE));

		ObjectNode typeNode = genericObjectMapper.createObjectNode();
		typeNode.set("properties", propertiesNode);

		ObjectNode mappingsNode = genericObjectMapper.createObjectNode();
		mappingsNode.set(TAGS_TYPE_NAME, typeNode);
		return mappingsNode;
	}

	private ObjectNode _createScopeMappingsNode() {
		ObjectNode propertiesNode = genericObjectMapper.createObjectNode();
		propertiesNode.set(RecordType.SCOPE.getName(), _createFieldNode(FIELD_TYPE_TEXT));

		propertiesNode.set("mts", _createFieldNodeNoAnalyzer(FIELD_TYPE_DATE));
		propertiesNode.set("cts", _createFieldNodeNoAnalyzer(FIELD_TYPE_DATE));

		ObjectNode typeNode = genericObjectMapper.createObjectNode();
		typeNode.set("properties", propertiesNode);

		ObjectNode mappingsNode = genericObjectMapper.createObjectNode();
		mappingsNode.set(SCOPE_TYPE_NAME, typeNode);

		return mappingsNode;
	}

	private ObjectNode _createMetatagsMappingsNode() {
		ObjectNode propertiesNode = genericObjectMapper.createObjectNode();
		propertiesNode.set(RecordType.METATAGS.getName(), _createFieldNode(FIELD_TYPE_TEXT));

		propertiesNode.set("mts", _createFieldNodeNoAnalyzer(FIELD_TYPE_DATE));
		propertiesNode.set("cts", _createFieldNodeNoAnalyzer(FIELD_TYPE_DATE));

		ObjectNode typeNode = genericObjectMapper.createObjectNode();
		typeNode.set("properties", propertiesNode);

		ObjectNode mappingsNode = genericObjectMapper.createObjectNode();
		mappingsNode.set(METATAGS_TYPE_NAME, typeNode);

		return mappingsNode;
	}

	private ObjectNode _createFieldNode(String type) {
		ObjectNode fieldNode = genericObjectMapper.createObjectNode();
		fieldNode.put("type", type);
		fieldNode.put("analyzer", "metadata_analyzer");
		ObjectNode keywordNode = genericObjectMapper.createObjectNode();
		keywordNode.put("type", "keyword");
		ObjectNode fieldsNode = genericObjectMapper.createObjectNode();
		fieldsNode.set("raw", keywordNode);
		fieldNode.set("fields", fieldsNode);
		return fieldNode;
	}

	private ObjectNode _createFieldNodeNoAnalyzer(String type) {
		ObjectNode fieldNode = genericObjectMapper.createObjectNode();
		fieldNode.put("type", type);
		return fieldNode;
	}
	
	void logAndMonitorESFailureResponses(String esResponse){
	    ObjectMapper mapper = new ObjectMapper();
	    JsonNode tree;
	    try {
	        tree = mapper.readTree(esResponse);
	        if(tree.get("failures") != null) {
	            _logger.warn("ES Response get failures- {}", esResponse);
	            _monitorService.modifyCounter(MonitorService.Counter.ELASTIC_SEARCH_GET_FAILURES, 1, null);
	        }
	    } catch (IOException e) {
	        _logger.warn("Failed to parse ES json response {}", e);
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


	/**
	 * The set of implementation specific configuration properties.
	 *
	 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
	 */
	public enum Property {

		ELASTICSEARCH_ENDPOINT("service.property.schema.elasticsearch.endpoint", "http://localhost:9200,http://localhost:9201"),
		/** Connection timeout for ES REST client. */
		ELASTICSEARCH_ENDPOINT_CONNECTION_TIMEOUT("service.property.schema.elasticsearch.endpoint.connection.timeout", "10000"),
		/** Socket connection timeout for ES REST client. */
		ELASTICSEARCH_ENDPOINT_SOCKET_TIMEOUT("service.property.schema.elasticsearch.endpoint.socket.timeout", "10000"),
		/** Connection count for ES REST client. */
		ELASTICSEARCH_CONNECTION_COUNT("service.property.schema.elasticsearch.connection.count", "10"),
		/** The no. of records to batch for bulk indexing requests.
		 * https://www.elastic.co/guide/en/elasticsearch/guide/current/indexing-performance.html#_using_and_sizing_bulk_requests
		 */
		ELASTICSEARCH_INDEXING_BATCH_SIZE("service.property.schema.elasticsearch.indexing.batch.size", "10000"),
		/** The hashing algorithm to use for generating document id. */
		ELASTICSEARCH_IDGEN_HASH_ALGO("service.property.schema.elasticsearch.idgen.hash.algo", "MD5"),

		/** Name of the main scope:metric:tagk:tagv index */
		ELASTICSEARCH_TAGS_INDEX_NAME("service.property.schema.elasticsearch.index.name", "metadata_index"),
		/** Type within the main index */
		ELASTICSEARCH_TAGS_TYPE_NAME("service.property.schema.elasticsearch.type.name", "metadata_type"),
		/** Replication factor for main index */
		ELASTICSEARCH_NUM_REPLICAS_FOR_TAGS_INDEX("service.property.schema.elasticsearch.num.replicas", "1"),
		/** Shard count for main index */
		ELASTICSEARCH_SHARDS_COUNT_FOR_TAGS_INDEX("service.property.schema.elasticsearch.shards.count", "10"),


		/** Name of scope only index */
		ELASTICSEARCH_SCOPE_INDEX_NAME("service.property.schema.elasticsearch.scope.index.name", "scopenames"),
		/** Type within scope only index */
		ELASTICSEARCH_SCOPE_TYPE_NAME("service.property.schema.elasticsearch.scope.type.name", "scope_type"),
		/** Replication factor for scopenames */
		ELASTICSEARCH_NUM_REPLICAS_FOR_SCOPE_INDEX("service.property.schema.elasticsearch.num.replicas.for.scope.index", "1"),
		/** Shard count for scopenames */
		ELASTICSEARCH_SHARDS_COUNT_FOR_SCOPE_INDEX("service.property.schema.elasticsearch.shards.count.for.scope.index", "6"),

		/** Name of metatags only index */
		ELASTICSEARCH_METATAGS_INDEX_NAME("service.property.schema.elasticsearch.metatags.index.name", "metatags"),
		/** Type within metatags only index */
		ELASTICSEARCH_METATAGS_TYPE_NAME("service.property.schema.elasticsearch.metatags.type.name", "metatags_type"),
		/** Replication factor for metatags */
		ELASTICSEARCH_NUM_REPLICAS_FOR_METATAGS_INDEX("service.property.schema.elasticsearch.num.replicas.for.metatags.index", "1"),
		/** Shard count for metatags */
		ELASTICSEARCH_SHARDS_COUNT_FOR_METATAGS_INDEX("service.property.schema.elasticsearch.shards.count.for.metatags.index", "6");

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

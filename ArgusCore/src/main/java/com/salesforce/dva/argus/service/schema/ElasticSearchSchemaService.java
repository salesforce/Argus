package com.salesforce.dva.argus.service.schema;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonSerializer;
import com.salesforce.dva.argus.entity.KeywordQuery;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.MetricSchemaRecord;
import com.salesforce.dva.argus.entity.MetricSchemaRecordQuery;
import com.salesforce.dva.argus.entity.ScopeAndMetricOnlySchemaRecord;
import com.salesforce.dva.argus.entity.ScopeOnlySchemaRecord;
import com.salesforce.dva.argus.entity.MetatagsRecord;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.client.config.RequestConfig.Builder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.apache.http.impl.nio.conn.PoolingNHttpClientConnectionManager;
import org.apache.http.impl.nio.reactor.DefaultConnectingIOReactor;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.elasticsearch.client.RestClientBuilder.HttpClientConfigCallback;
import org.elasticsearch.client.RestClientBuilder.RequestConfigCallback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.MonitorService.Counter;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.service.schema.ElasticSearchSchemaService.PutResponse.Item;
import com.salesforce.dva.argus.service.schema.MetricSchemaRecordList.HashAlgorithm;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;

/**
 * Implementation of the schema service using ElasticSearch.
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
@Singleton
public class ElasticSearchSchemaService extends AbstractSchemaService {

	private static String SCOPE_INDEX_NAME;
	private static String SCOPE_TYPE_NAME;

	private static String SCOPE_AND_METRIC_INDEX_NAME;
	private static String SCOPE_AND_METRIC_TYPE_NAME;

	private static String METATAGS_INDEX_NAME;
	private static String METATAGS_TYPE_NAME;

	private static final String INDEX_NAME = "metadata_index";
	private static final String TYPE_NAME = "metadata_type";
	private static final String KEEP_SCROLL_CONTEXT_OPEN_FOR = "1m";
	private static final int INDEX_MAX_RESULT_WINDOW = 10000;
	private static final int MAX_RETRY_TIMEOUT = 300 * 1000;
	private static final String FIELD_TYPE_TEXT = "text";
	private static final String FIELD_TYPE_DATE ="date";

	private final ObjectMapper _mapper;
	private final ObjectMapper _createScopeOnlyMapper;
	private final ObjectMapper _updateScopeOnlyMapper;
	private final ObjectMapper _createScopeAndMetricOnlyMapper;
	private final ObjectMapper _updateScopeAndMetricOnlyMapper;
	private final ObjectMapper _createMetatagsMapper;
	private final ObjectMapper _updateMetatagsMapper;

	private Logger _logger = LoggerFactory.getLogger(getClass());
	private RestClient _esRestClient;
	private final int _replicationFactor;
	private final int _numShards;
	private final int _replicationFactorForScopeIndex;
	private final int _numShardsForScopeIndex;
	private final int _replicationFactorForScopeAndMetricIndex;
	private final int _numShardsForScopeAndMetricIndex;
	private final int _replicationFactorForMetatagsIndex;
	private final int _numShardsForMetatagsIndex;
	private final int _bulkIndexingSize;
	private HashAlgorithm _idgenHashAlgo;

	private boolean _useScopeMetricNamesIndex;

	@Inject
	public ElasticSearchSchemaService(SystemConfiguration config, MonitorService monitorService) {
		super(config, monitorService);

		_mapper = _createObjectMapper();

		_createScopeOnlyMapper = _getScopeOnlyObjectMapper(new ScopeOnlySchemaRecordList.CreateSerializer());
		_updateScopeOnlyMapper = _getScopeOnlyObjectMapper(new ScopeOnlySchemaRecordList.UpdateSerializer());

		_createScopeAndMetricOnlyMapper = _getScopeAndMetricOnlyObjectMapper(new ScopeAndMetricOnlySchemaRecordList.CreateSerializer());
		_updateScopeAndMetricOnlyMapper = _getScopeAndMetricOnlyObjectMapper(new ScopeAndMetricOnlySchemaRecordList.UpdateSerializer());

		_createMetatagsMapper = _getMetatagsObjectMapper(new MetatagsSchemaRecordList.CreateSerializer());
		_updateMetatagsMapper = _getMetatagsObjectMapper(new MetatagsSchemaRecordList.UpdateSerializer());

		SCOPE_INDEX_NAME = config.getValue(Property.ELASTICSEARCH_SCOPE_INDEX_NAME.getName(),
				Property.ELASTICSEARCH_SCOPE_INDEX_NAME.getDefaultValue());
		SCOPE_TYPE_NAME = config.getValue(Property.ELASTICSEARCH_SCOPE_TYPE_NAME.getName(),
				Property.ELASTICSEARCH_SCOPE_TYPE_NAME.getDefaultValue());

		SCOPE_AND_METRIC_INDEX_NAME = config.getValue(Property.ELASTICSEARCH_SCOPE_AND_METRIC_INDEX_NAME.getName(),
				Property.ELASTICSEARCH_SCOPE_AND_METRIC_INDEX_NAME.getDefaultValue());
		SCOPE_AND_METRIC_TYPE_NAME = config.getValue(Property.ELASTICSEARCH_SCOPE_AND_METRIC_TYPE_NAME.getName(),
				Property.ELASTICSEARCH_SCOPE_AND_METRIC_TYPE_NAME.getDefaultValue());

		METATAGS_INDEX_NAME = config.getValue(Property.ELASTICSEARCH_METATAGS_INDEX_NAME.getName(),
                                                      Property.ELASTICSEARCH_METATAGS_INDEX_NAME.getDefaultValue());
		METATAGS_TYPE_NAME = config.getValue(Property.ELASTICSEARCH_METATAGS_TYPE_NAME.getName(),
                                                     Property.ELASTICSEARCH_METATAGS_TYPE_NAME.getDefaultValue());

		String algorithm = config.getValue(Property.ELASTICSEARCH_IDGEN_HASH_ALGO.getName(), Property.ELASTICSEARCH_IDGEN_HASH_ALGO.getDefaultValue());
		try {
			_idgenHashAlgo = HashAlgorithm.fromString(algorithm);
		} catch(IllegalArgumentException e) {
			_logger.warn("{} is not supported by this service. Valid values are: {}.", algorithm, Arrays.asList(HashAlgorithm.values()));
			_idgenHashAlgo = HashAlgorithm.MD5;
		}

		_logger.info("Using {} for Elasticsearch document id generation.", _idgenHashAlgo);

		_replicationFactor = Integer.parseInt(
				config.getValue(Property.ELASTICSEARCH_NUM_REPLICAS.getName(), Property.ELASTICSEARCH_NUM_REPLICAS.getDefaultValue()));

		_numShards = Integer.parseInt(
				config.getValue(Property.ELASTICSEARCH_SHARDS_COUNT.getName(), Property.ELASTICSEARCH_SHARDS_COUNT.getDefaultValue()));

		_replicationFactorForScopeIndex = Integer.parseInt(
				config.getValue(Property.ELASTICSEARCH_NUM_REPLICAS_FOR_SCOPE_INDEX.getName(), Property.ELASTICSEARCH_NUM_REPLICAS_FOR_SCOPE_INDEX.getDefaultValue()));

		_numShardsForScopeIndex = Integer.parseInt(
				config.getValue(Property.ELASTICSEARCH_SHARDS_COUNT_FOR_SCOPE_INDEX.getName(), Property.ELASTICSEARCH_SHARDS_COUNT_FOR_SCOPE_INDEX.getDefaultValue()));

		_replicationFactorForScopeAndMetricIndex = Integer.parseInt(
				config.getValue(Property.ELASTICSEARCH_NUM_REPLICAS_FOR_SCOPE_AND_METRIC_INDEX.getName(), Property.ELASTICSEARCH_NUM_REPLICAS_FOR_SCOPE_AND_METRIC_INDEX.getDefaultValue()));

		_numShardsForScopeAndMetricIndex = Integer.parseInt(
				config.getValue(Property.ELASTICSEARCH_SHARDS_COUNT_FOR_SCOPE_AND_METRIC_INDEX.getName(), Property.ELASTICSEARCH_SHARDS_COUNT_FOR_SCOPE_AND_METRIC_INDEX.getDefaultValue()));

		_replicationFactorForMetatagsIndex = Integer.parseInt(
				config.getValue(Property.ELASTICSEARCH_NUM_REPLICAS_FOR_METATAGS_INDEX.getName(),
                                                Property.ELASTICSEARCH_NUM_REPLICAS_FOR_METATAGS_INDEX.getDefaultValue()));

		_numShardsForMetatagsIndex = Integer.parseInt(
				config.getValue(Property.ELASTICSEARCH_SHARDS_COUNT_FOR_METATAGS_INDEX.getName(),
                                                Property.ELASTICSEARCH_SHARDS_COUNT_FOR_METATAGS_INDEX.getDefaultValue()));

		_bulkIndexingSize = Integer.parseInt(
				config.getValue(Property.ELASTICSEARCH_INDEXING_BATCH_SIZE.getName(), Property.ELASTICSEARCH_INDEXING_BATCH_SIZE.getDefaultValue()));

		_useScopeMetricNamesIndex = Boolean.parseBoolean(
				config.getValue(Property.ELASTICSEARCH_USE_SCOPE_AND_METRIC_INDEX.getName(), Property.ELASTICSEARCH_USE_SCOPE_AND_METRIC_INDEX.getDefaultValue()));

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

		HttpClientConfigCallback clientConfigCallback = new RestClientBuilder.HttpClientConfigCallback() {

			@Override
			public HttpAsyncClientBuilder customizeHttpClient(HttpAsyncClientBuilder httpClientBuilder) {
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
			}
		};

		RequestConfigCallback requestConfigCallback = new RestClientBuilder.RequestConfigCallback() {

			@Override
			public Builder customizeRequestConfig(Builder requestConfigBuilder) {
				int connTimeout = Integer.parseInt(config.getValue(Property.ELASTICSEARCH_ENDPOINT_CONNECTION_TIMEOUT.getName(),
						Property.ELASTICSEARCH_ENDPOINT_CONNECTION_TIMEOUT.getDefaultValue()));
				int socketTimeout = Integer.parseInt(config.getValue(Property.ELASTICSEARCH_ENDPOINT_SOCKET_TIMEOUT.getName(),
						Property.ELASTICSEARCH_ENDPOINT_SOCKET_TIMEOUT.getDefaultValue()));
				requestConfigBuilder.setConnectTimeout(connTimeout).setSocketTimeout(socketTimeout);

				_logger.info("_esRestClient set connTimeoutMillis {} socketTimeoutMillis {}",
						connTimeout, socketTimeout);

				return requestConfigBuilder;
			}
		};

		_esRestClient = RestClient.builder(httpHosts)
				.setHttpClientConfigCallback(clientConfigCallback)
				.setRequestConfigCallback(requestConfigCallback)
				.setMaxRetryTimeoutMillis(MAX_RETRY_TIMEOUT)
				.build();

		_logger.info("_esRestClient set MaxRetryTimeoutsMillis {}", MAX_RETRY_TIMEOUT);

		_createIndexIfNotExists(INDEX_NAME, _replicationFactor, _numShards, () -> _createMappingsNode());

		_createIndexIfNotExists(SCOPE_INDEX_NAME, _replicationFactorForScopeIndex, _numShardsForScopeIndex,
				() -> _createScopeMappingsNode());

		_createIndexIfNotExists(SCOPE_AND_METRIC_INDEX_NAME, _replicationFactorForScopeAndMetricIndex,
				_numShardsForScopeAndMetricIndex, () -> _createScopeAndMetricMappingsNode());

		_createIndexIfNotExists(METATAGS_INDEX_NAME, _replicationFactorForMetatagsIndex,
                                        _numShardsForMetatagsIndex, () -> _createMetatagsMappingsNode());
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
	protected void implementationSpecificPut(List<Metric> metrics,
                                                 Set<String> scopeNames,
                                                 Set<Pair<String, String>> scopesAndMetricNames,
                                                 Map<String, MetatagsRecord> metatagsToPut) {
		SystemAssert.requireArgument(metrics != null, "Metrics list cannot be null.");

                // Put metric tags
		long start = System.currentTimeMillis();
		List<List<MetricSchemaRecord>> fracturedList = _fracture(metrics);

                int count = 0;
		for(List<MetricSchemaRecord> records : fracturedList) {
                    if(!records.isEmpty()) {
                        upsert(records);
                        count += records.size();
                    }
		}

		_monitorService.modifyCounter(MonitorService.Counter.SCHEMARECORDS_WRITTEN, count, null);
		_monitorService.modifyCounter(MonitorService.Counter.SCHEMARECORDS_WRITE_LATENCY,
                                              (System.currentTimeMillis() - start),
                                              null);

		_logger.info("{} new metrics were indexed in {} ms.", count, (System.currentTimeMillis() - start));

                // Put scopes
		start = System.currentTimeMillis();
		List<List<ScopeOnlySchemaRecord>> fracturedScopesList = _fractureScopes(scopeNames);

                count = 0;
		for(List<ScopeOnlySchemaRecord> records : fracturedScopesList) {
                    if(!records.isEmpty()) {
                        upsertScopes(records);
                        count += records.size();
                    }
		}

		_monitorService.modifyCounter(MonitorService.Counter.SCOPENAMES_WRITTEN, count, null);
		_monitorService.modifyCounter(MonitorService.Counter.SCOPENAMES_WRITE_LATENCY,
                                              (System.currentTimeMillis() - start),
                                              null);

		_logger.info("{} new scopes were indexed in {} ms.", count, (System.currentTimeMillis() - start));

                // Put scopes+metrics
		start = System.currentTimeMillis();
		List<List<ScopeAndMetricOnlySchemaRecord>> fracturedScopesAndMetricsList =
                    _fractureScopeAndMetrics(scopesAndMetricNames);

                count = 0;
		for(List<ScopeAndMetricOnlySchemaRecord> records : fracturedScopesAndMetricsList) {
                    if(!records.isEmpty()) {
                        upsertScopeAndMetrics(records);
                        count += records.size();
                    }
		}

		_monitorService.modifyCounter(MonitorService.Counter.SCOPEANDMETRICNAMES_WRITTEN, count, null);
		_monitorService.modifyCounter(Counter.SCOPEANDMETRICNAMES_WRITE_LATENCY,
                                              (System.currentTimeMillis() - start),
                                              null);

		_logger.info("{} new scope and metric names were indexed in {} ms.",
                             count,
                             (System.currentTimeMillis() - start));

                // Put Metric MetatagsRecord
		start = System.currentTimeMillis();
		List<List<MetatagsRecord>> fracturedMetatagsList =
                    _fractureMetatags(metatagsToPut);

                count = 0;
		for(List<MetatagsRecord> records : fracturedMetatagsList) {
                    if(!records.isEmpty()) {
                        upsertMetatags(records);
                        count += records.size();
                    }
		}

		_monitorService.modifyCounter(MonitorService.Counter.METATAGS_WRITTEN, count, null);
		_monitorService.modifyCounter(Counter.METATAGS_WRITE_LATENCY,
                                              (System.currentTimeMillis() - start),
                                              null);

		_logger.info("{} new metatags were indexed in {} ms.",
                             count,
                             (System.currentTimeMillis() - start));

	}

	/* Convert the given list of metrics to a list of metric schema records. At the same time, fracture the records list
	 * if its size is greater than ELASTICSEARCH_INDEXING_BATCH_SIZE.
	 */
	protected List<List<MetricSchemaRecord>> _fracture(List<Metric> metrics) {
		List<List<MetricSchemaRecord>> fracturedList = new ArrayList<>();

		List<MetricSchemaRecord> records = new ArrayList<>(_bulkIndexingSize);
		for(Metric metric : metrics) {
			if(metric.getTags().isEmpty()) {
				MetricSchemaRecord msr = new MetricSchemaRecord(metric.getScope(), metric.getMetric());
				msr.setNamespace(metric.getNamespace());
				records.add(msr);
				if(records.size() == _bulkIndexingSize) {
					fracturedList.add(records);
					records = new ArrayList<>(_bulkIndexingSize);
				}
				continue;
			}

			String retention  = metric.getMetatagsRecord()==null?null:metric.getMetatagsRecord().getMetatagValue(MetricSchemaRecord.RETENTION_DISCOVERY);
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
					records = new ArrayList<>(_bulkIndexingSize);
				}
			}
		}

		if(!records.isEmpty()) {
			fracturedList.add(records);
		}

		return fracturedList;
	}

	/* Convert the given list of scope and metric names to a list of scope and metric only schema records.
	 * At the same time, fracture the records list if its size is greater than ELASTICSEARCH_INDEXING_BATCH_SIZE.
	 */
	protected List<List<ScopeAndMetricOnlySchemaRecord>> _fractureScopeAndMetrics(Set<Pair<String, String>> scopesAndMetricNames) {
		List<List<ScopeAndMetricOnlySchemaRecord>> fracturedList = new ArrayList<>();

		List<ScopeAndMetricOnlySchemaRecord> records = new ArrayList<>(_bulkIndexingSize);
		for(Pair<String, String> scopeAndMetric : scopesAndMetricNames) {
			records.add(new ScopeAndMetricOnlySchemaRecord(scopeAndMetric.getLeft(), scopeAndMetric.getRight()));

			if(records.size() == _bulkIndexingSize) {
				fracturedList.add(records);
				records = new ArrayList<>(_bulkIndexingSize);
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
	protected List<List<ScopeOnlySchemaRecord>> _fractureScopes(Set<String> scopeNames) {
		List<List<ScopeOnlySchemaRecord>> fracturedList = new ArrayList<>();

		List<ScopeOnlySchemaRecord> records = new ArrayList<>(_bulkIndexingSize);
		for(String scope : scopeNames) {
			records.add(new ScopeOnlySchemaRecord(scope));

			if(records.size() == _bulkIndexingSize) {
				fracturedList.add(records);
				records = new ArrayList<>(_bulkIndexingSize);
			}
		}

		if(!records.isEmpty()) {
			fracturedList.add(records);
		}

		return fracturedList;
	}

	protected List<List<MetatagsRecord>> _fractureMetatags(Map<String, MetatagsRecord> metatagsToPut) {
		List<List<MetatagsRecord>> fracturedList = new ArrayList<>();

		List<MetatagsRecord> records = new ArrayList<>(_bulkIndexingSize);
                for(Map.Entry<String, MetatagsRecord> entry : metatagsToPut.entrySet()) {
                	//remove this special metatag to prevent it from going to ES
                	entry.getValue().removeMetatag(MetricSchemaRecord.RETENTION_DISCOVERY);
                    MetatagsRecord mtag = new MetatagsRecord(entry.getValue().getMetatags(), entry.getValue().getKey());
                    records.add(mtag);
                    if(records.size() == _bulkIndexingSize) {
                        fracturedList.add(records);
                        records = new ArrayList<>(_bulkIndexingSize);
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
		long size = (long) query.getLimit() * query.getPage();
		SystemAssert.requireArgument(size > 0 && size <= Integer.MAX_VALUE,
				"(limit * page) must be greater than 0 and atmost Integer.MAX_VALUE");


		Map<String, String> tags = new HashMap<>();
		tags.put("type", "REGEXP_WITHOUT_AGGREGATION");
		long start = System.currentTimeMillis();
		boolean scroll = false;
		StringBuilder sb = new StringBuilder().append("/")
				.append(INDEX_NAME)
				.append("/")
				.append(TYPE_NAME)
				.append("/")
				.append("_search");

		int from = 0, scrollSize;
		if(query.getLimit() * query.getPage() > 10000) {
			sb.append("?scroll=").append(KEEP_SCROLL_CONTEXT_OPEN_FOR);
			scroll = true;
			int total = query.getLimit() * query.getPage();
			scrollSize = (int) (total / (total / 10000 + 1));
		} else {
			from = query.getLimit() * (query.getPage() - 1);
			scrollSize = query.getLimit();
		}

		String requestUrl = sb.toString();
		String queryJson = _constructTermQuery(query, from, scrollSize);

		try {
			_logger.debug("get POST requestUrl {} queryJson {}", requestUrl, queryJson);
			Response response = _esRestClient.performRequest(HttpMethod.POST.getName(), requestUrl, Collections.emptyMap(), new StringEntity(queryJson));

			MetricSchemaRecordList list = toEntity(extractResponse(response), new TypeReference<MetricSchemaRecordList>() {});

			if(scroll) {
				requestUrl = new StringBuilder().append("/").append("_search").append("/").append("scroll").toString();
				List<MetricSchemaRecord> records = new LinkedList<>(list.getRecords());

				while(true) {
					String scrollID = list.getScrollID();

					Map<String, String> requestBody = new HashMap<>();
					requestBody.put("scroll_id", scrollID);
					requestBody.put("scroll", KEEP_SCROLL_CONTEXT_OPEN_FOR);

					String requestJson = new ObjectMapper().writeValueAsString(requestBody);
					_logger.debug("get Scroll POST requestUrl {} queryJson {}", requestUrl, queryJson);
					response = _esRestClient.performRequest(HttpMethod.POST.getName(), requestUrl, Collections.emptyMap(), new StringEntity(requestJson));

					list = toEntity(extractResponse(response), new TypeReference<MetricSchemaRecordList>() {});
					records.addAll(list.getRecords());

					if(records.size() >= query.getLimit() * query.getPage() || list.getRecords().size() < scrollSize) {
						break;
					}
				}

				int fromIndex = query.getLimit() * (query.getPage() - 1);
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

		} catch (UnsupportedEncodingException | JsonProcessingException e) {
			throw new SystemException("Search failed.", e);
		} catch (IOException e) {
			throw new SystemException("IOException when trying to perform ES request.", e);
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

		String indexName = INDEX_NAME;
		String typeName = TYPE_NAME;

		if (query.isQueryOnlyOnScope() && RecordType.SCOPE.equals(type))
		{
			indexName = SCOPE_INDEX_NAME;
			typeName = SCOPE_TYPE_NAME;
		}
		else if (_useScopeMetricNamesIndex && query.isQueryOnlyOnScopeAndMetric() &&
				(RecordType.SCOPE.equals(type) || RecordType.METRIC.equals(type)))
		{
			indexName = SCOPE_AND_METRIC_INDEX_NAME;
			typeName = SCOPE_AND_METRIC_TYPE_NAME;
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
			Response response = _esRestClient.performRequest(HttpMethod.POST.getName(), requestUrl, Collections.emptyMap(), new StringEntity(queryJson));
			String str = extractResponse(response);
			List<MetricSchemaRecord> records = SchemaService.constructMetricSchemaRecordsForType(toEntity(str, new TypeReference<List<String>>() {}), type);

			if (query.isQueryOnlyOnScope() && RecordType.SCOPE.equals(type)) {
				_monitorService.modifyCounter(Counter.SCOPENAMES_QUERY_COUNT, 1, tags);
				_monitorService.modifyCounter(Counter.SCOPENAMES_QUERY_LATENCY, (System.currentTimeMillis() - start), tags);

			} else if (_useScopeMetricNamesIndex && query.isQueryOnlyOnScopeAndMetric() &&
					(RecordType.SCOPE.equals(type) || RecordType.METRIC.equals(type))) {
				_monitorService.modifyCounter(Counter.SCOPEANDMETRICNAMES_QUERY_COUNT, 1, tags);
				_monitorService.modifyCounter(Counter.SCOPEANDMETRICNAMES_QUERY_LATENCY, (System.currentTimeMillis() - start), tags);
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
				.append(INDEX_NAME)
				.append("/")
				.append(TYPE_NAME)
				.append("/")
				.append("_search");
		try {

			if(kq.getQuery() != null) {

				int from = 0, scrollSize = 0;
				boolean scroll = false;;
				if(kq.getLimit() * kq.getPage() > 10000) {
					sb.append("?scroll=").append(KEEP_SCROLL_CONTEXT_OPEN_FOR);
					scroll = true;
					int total = kq.getLimit() * kq.getPage();
					scrollSize = (int) (total / (total / 10000 + 1));
				} else {
					from = kq.getLimit() * (kq.getPage() - 1);
					scrollSize = kq.getLimit();
				}

				List<String> tokens = _analyzedTokens(kq.getQuery());
				String queryJson = _constructQueryStringQuery(tokens, from, scrollSize);
				String requestUrl = sb.toString();

				Response response = _esRestClient.performRequest(HttpMethod.POST.getName(), requestUrl, Collections.emptyMap(), new StringEntity(queryJson));
				String strResponse = extractResponse(response);
				MetricSchemaRecordList list = toEntity(strResponse, new TypeReference<MetricSchemaRecordList>() {});

				if(scroll) {
					requestUrl = new StringBuilder().append("/").append("_search").append("/").append("scroll").toString();
					List<MetricSchemaRecord> records = new LinkedList<>(list.getRecords());

					while(true) {
						Map<String, String> requestBody = new HashMap<>();
						requestBody.put("scroll_id", list.getScrollID());
						requestBody.put("scroll", KEEP_SCROLL_CONTEXT_OPEN_FOR);

						response = _esRestClient.performRequest(HttpMethod.POST.getName(), requestUrl, Collections.emptyMap(),
								new StringEntity(new ObjectMapper().writeValueAsString(requestBody)));

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
				Response response = _esRestClient.performRequest(HttpMethod.POST.getName(), requestUrl, Collections.emptyMap(), new StringEntity(queryJson));
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

		String requestUrl = new StringBuilder("/").append(INDEX_NAME).append("/_analyze").toString();

		String requestBody = "{\"analyzer\" : \"metadata_analyzer\", \"text\": \"" + query + "\" }";

		try {
			Response response = _esRestClient.performRequest(HttpMethod.POST.getName(), requestUrl, Collections.emptyMap(), new StringEntity(requestBody));
			String strResponse = extractResponse(response);
			JsonNode tokensNode = _mapper.readTree(strResponse).get("tokens");
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

	protected void upsert(List<MetricSchemaRecord> records) {
		String requestUrl = new StringBuilder().append("/")
				.append(INDEX_NAME)
				.append("/")
				.append(TYPE_NAME)
				.append("/")
				.append("_bulk")
				.toString();

		String strResponse = "";

		MetricSchemaRecordList msrList = new MetricSchemaRecordList(records, _idgenHashAlgo);
		try {
			String requestBody = _mapper.writeValueAsString(msrList);
			Response response = _esRestClient.performRequest(HttpMethod.POST.getName(), requestUrl,
					Collections.emptyMap(), new StringEntity(requestBody));
			strResponse = extractResponse(response);
		} catch (IOException e) {
			//TODO: Retry with exponential back-off for handling EsRejectedExecutionException/RemoteTransportException/TimeoutException??
			throw new SystemException(e);
		}

		try {
			PutResponse putResponse = new ObjectMapper().readValue(strResponse, PutResponse.class);
			//TODO: If response contains HTTP 429 Too Many Requests (EsRejectedExecutionException), then retry with exponential back-off.
			if(putResponse.errors) {
				List<MetricSchemaRecord> recordsToRemove = new ArrayList<>();
				List<String> updateMtsFieldList = new ArrayList<>();
				for(Item item : putResponse.items) {
					if(item.create != null) {
						if(item.create.status == HttpStatus.SC_CONFLICT) {
							updateMtsFieldList.add(item.create._id);
						}else if(item.create.status != HttpStatus.SC_CREATED) {
							_logger.warn("Failed to index metric {}. Reason: {}", msrList.getRecord(item.create._id),
									new ObjectMapper().writeValueAsString(item.create.error));
							recordsToRemove.add(msrList.getRecord(item.create._id));
						}
					}
				}
				if(updateMtsFieldList.size()>0) {
					_logger.debug("mts filed will be updated for docs with ids {}", updateMtsFieldList);
					Response response = updateMtsField(updateMtsFieldList,INDEX_NAME,TYPE_NAME);
					PutResponse updateResponse = new ObjectMapper().readValue(extractResponse(response), PutResponse.class);
					for(Item item: updateResponse.items) {
						if(item.update != null && item.update.status != HttpStatus.SC_OK) {
							_logger.debug("Failed to update mts field for metric {}. Reason: {}",msrList.getRecord(item.update._id),
									new ObjectMapper().writeValueAsString(item.update.error));
							recordsToRemove.add(msrList.getRecord(item.update._id));
						}
					}

				}

				if(recordsToRemove.size() != 0) {
					_logger.warn("{} records were not written to ES", recordsToRemove.size());
					records.removeAll(recordsToRemove);
				}
			}
			//add to bloom filter
			_addToBloomFilter(records);

		} catch(IOException e) {
			throw new SystemException("Failed to parse reponse of put metrics. The response was: " + strResponse, e);
		}
	}

	protected void upsertScopeAndMetrics(List<ScopeAndMetricOnlySchemaRecord> records) {
		String requestUrl = new StringBuilder().append("/")
				.append(SCOPE_AND_METRIC_INDEX_NAME)
				.append("/")
				.append(SCOPE_AND_METRIC_TYPE_NAME)
				.append("/")
				.append("_bulk")
				.toString();

		try {

			ScopeAndMetricOnlySchemaRecordList createSchemaRecordList = new ScopeAndMetricOnlySchemaRecordList(records, _idgenHashAlgo);
			String requestBody = _createScopeAndMetricOnlyMapper.writeValueAsString(createSchemaRecordList);
			PutResponse putResponse = _performRequest(requestUrl, requestBody);

			Pair<List<String>, List<String>> failedResponses = _parseFailedResponses(putResponse);

			List<String> failedIds = failedResponses.getLeft();
			List<String> updateRequiredIds = failedResponses.getRight();

			if (updateRequiredIds.size() > 0) {

				List<ScopeAndMetricOnlySchemaRecord> updateRequiredRecords = new ArrayList<>();

				for (String id : updateRequiredIds) {
					updateRequiredRecords.add(createSchemaRecordList.getRecord(id));
				}

				ScopeAndMetricOnlySchemaRecordList updateSchemaRecordList = new ScopeAndMetricOnlySchemaRecordList(updateRequiredRecords, _idgenHashAlgo);
				requestBody = _updateScopeAndMetricOnlyMapper.writeValueAsString(updateSchemaRecordList);
				putResponse = _performRequest(requestUrl, requestBody);

				failedResponses = _parseFailedResponses(putResponse);

				// We collect new failures.
				failedIds.addAll(failedResponses.getLeft());

				// We do not collect update failures if they fail with 409 (version_conflict_engine_exception).
				// This usually happens when there is another concurrent update happening to mts field"
			}

			if (failedIds.size() > 0) {
				_logger.warn("{} records were not written to scope and metric ES", failedIds.size());
			}

			for (String id : failedIds) {
				records.remove(createSchemaRecordList.getRecord(id));
			}

			//add to bloom filter
			_addToBloomFilterScopeAndMetricOnly(records);

		} catch (IOException e) {
			throw new SystemException("Failed to create/update scope and metric ES. ", e);
		}
	}

	protected void upsertScopes(List<ScopeOnlySchemaRecord> records) {

		String requestUrl = new StringBuilder().append("/")
				.append(SCOPE_INDEX_NAME)
				.append("/")
				.append(SCOPE_TYPE_NAME)
				.append("/")
				.append("_bulk")
				.toString();

		try {

			ScopeOnlySchemaRecordList createSchemaRecordList = new ScopeOnlySchemaRecordList(records, _idgenHashAlgo);
			String requestBody = _createScopeOnlyMapper.writeValueAsString(createSchemaRecordList);
			PutResponse putResponse = _performRequest(requestUrl, requestBody);

			Pair<List<String>, List<String>> failedResponses = _parseFailedResponses(putResponse);

			List<String> failedIds = failedResponses.getLeft();
			List<String> updateRequiredIds = failedResponses.getRight();

			if (updateRequiredIds.size() > 0) {

				List<ScopeOnlySchemaRecord> updateRequiredRecords = new ArrayList<>();

				for (String id : updateRequiredIds) {
					updateRequiredRecords.add(createSchemaRecordList.getRecord(id));
				}

				ScopeOnlySchemaRecordList updateSchemaRecordList = new ScopeOnlySchemaRecordList(updateRequiredRecords, _idgenHashAlgo);
				requestBody = _updateScopeOnlyMapper.writeValueAsString(updateSchemaRecordList);
				putResponse = _performRequest(requestUrl, requestBody);

				failedResponses = _parseFailedResponses(putResponse);

				// We collect new failures.
				failedIds.addAll(failedResponses.getLeft());

				// We do not collect update failures if they fail with 409 (version_conflict_engine_exception).
				// This usually happens when there is another concurrent update happening to mts field"
			}

			if (failedIds.size() > 0) {
				_logger.warn("{} records were not written to scope ES", failedIds.size());
			}

			for(String id : failedIds) {
				records.remove(createSchemaRecordList.getRecord(id));
			}

			//add to bloom filter
			_addToBloomFilterScopeOnly(records);

		} catch (IOException e) {
			throw new SystemException("Failed to create/update scope ES. ", e);
		}
	}

	protected void upsertMetatags(List<MetatagsRecord> records) {
            String requestUrl = new StringBuilder().append("/")
                .append(METATAGS_INDEX_NAME)
                .append("/")
                .append(METATAGS_TYPE_NAME)
                .append("/")
                .append("_bulk")
                .toString();

            try {
                MetatagsSchemaRecordList createMetatagsSchemaRecordList  =
                    new MetatagsSchemaRecordList(records,
                                                 _idgenHashAlgo);
                String requestBody = _createMetatagsMapper.writeValueAsString(createMetatagsSchemaRecordList);
                PutResponse putResponse = _performRequest(requestUrl, requestBody);

                Pair<List<String>, List<String>> failedResponses = _parseFailedResponses(putResponse);

                List<String> failedIds = failedResponses.getLeft();
                List<String> updateRequiredIds = failedResponses.getRight();

                if (updateRequiredIds.size() > 0) {
                    List<MetatagsRecord> updateRequiredRecords = new ArrayList<>();
                    for (String id : updateRequiredIds) {
                        updateRequiredRecords.add(createMetatagsSchemaRecordList.getRecord(id));
                    }
                    MetatagsSchemaRecordList updateMetatagsSchemaRecordList =
                        new MetatagsSchemaRecordList(updateRequiredRecords, _idgenHashAlgo);
                    requestBody = _updateMetatagsMapper.writeValueAsString(updateMetatagsSchemaRecordList);
                    putResponse = _performRequest(requestUrl, requestBody);

                    failedResponses = _parseFailedResponses(putResponse);

		    // We collect new failures.
                    failedIds.addAll(failedResponses.getLeft());

                    // We do not collect update failures if they fail with 409 (version_conflict_engine_exception).
                    // This usually happens when there is another concurrent update happening to mts field"
                }

                if (failedIds.size() > 0) {
                    _logger.warn("{} records were not written to metatags ES", failedIds.size());
                }

                for(String id : failedIds) {
                    records.remove(createMetatagsSchemaRecordList.getRecord(id));
                }

                //add to bloom filter
                _addToBloomFilterMetatags(records);

		} catch (IOException e) {
                throw new SystemException("Failed to create/update scope ES. ", e);
            }

        }

	private PutResponse _performRequest(String requestUrl, String requestBody) throws IOException {

		String strResponse = "";

		Response response = _esRestClient.performRequest(HttpMethod.POST.getName(), requestUrl, Collections.emptyMap(), new StringEntity(requestBody));

		//TODO: Retry with exponential back-off for handling EsRejectedExecutionException/RemoteTransportException/TimeoutException??

		strResponse = extractResponse(response);

		PutResponse putResponse = new ObjectMapper().readValue(strResponse, PutResponse.class);
		return putResponse;
	}

	private Pair<List<String>, List<String>> _parseFailedResponses(PutResponse putResponse) throws IOException {

		List<String> failedIds = new ArrayList<>();
		List<String> updateRequiredIds = new ArrayList<>();

		//TODO: If response contains HTTP 429 Too Many Requests (EsRejectedExecutionException), then retry with exponential back-off.
		if (putResponse.errors) {
			for (Item item : putResponse.items) {

				if (item.create != null && item.create.status != HttpStatus.SC_CREATED) {

					if (item.create.status == HttpStatus.SC_CONFLICT) {
						updateRequiredIds.add(item.create._id);
					} else {
						_logger.debug("Failed to create document. Reason: " + new ObjectMapper().writeValueAsString(item.create.error));
						failedIds.add(item.create._id);
					}
				}

				if (item.update != null && item.update.status != HttpStatus.SC_OK) {

					if (item.update.status == HttpStatus.SC_CONFLICT) {
						updateRequiredIds.add(item.update._id);
					} else {
						_logger.warn("Failed to update document. Reason: " + new ObjectMapper().writeValueAsString(item.update.error));
						failedIds.add(item.update._id);
					}
				}
			}
		}
		return Pair.of(failedIds, updateRequiredIds);
	}

	protected Response updateMtsField(List<String> docIds, String index, String type) {
		Response result= null;
		if(docIds != null && docIds.size()>0) {
			String requestUrl = new StringBuilder().append("/")
					.append(index)
					.append("/")
					.append(type)
					.append("/")
					.append("_bulk")
					.toString();
			try {
				String requestBody = _getRequestBodyForMtsFieldUpdate(docIds);
				result = _esRestClient.performRequest(HttpMethod.POST.getName(), requestUrl, Collections.emptyMap(),
						new StringEntity(requestBody));
			} catch (IOException e) {
				throw new SystemException(e);
			}
		}
		return result;
	}

	private String _getRequestBodyForMtsFieldUpdate(List<String> docIds) {
		StringBuilder result = new StringBuilder();
		for(String docId:docIds) {
			result.append("{\"update\" : {\"_id\" : \"" + docId + "\" } }");
			result.append(System.lineSeparator());
			result.append("{\"doc\" : {\"mts\": " + System.currentTimeMillis() + "}}");
			result.append(System.lineSeparator());
		}
		return result.toString();
	}

	protected void _addToBloomFilter(List<MetricSchemaRecord> records){
		_logger.info("Adding {} records into bloom filter.", records.size());
		for (MetricSchemaRecord record : records) {
			String key = constructKey(record.getScope(), record.getMetric(), record.getTagKey(), record.getTagValue(), record.getNamespace());
			bloomFilter.put(key);
		}
	}

	protected void _addToBloomFilterScopeAndMetricOnly(List<ScopeAndMetricOnlySchemaRecord> records) {
		_logger.info("Adding {} records into scope and metric only bloom filter.", records.size());
		for (ScopeAndMetricOnlySchemaRecord record : records) {
			String key = constructScopeAndMetricOnlyKey(record.getScope(), record.getMetric());
			bloomFilterScopeAndMetricOnly.put(key);
		}
	}

	protected void _addToBloomFilterScopeOnly(List<ScopeOnlySchemaRecord> records) {
		_logger.info("Adding {} records into scope only bloom filter.", records.size());
		for (ScopeOnlySchemaRecord record : records) {
			String key = constructScopeOnlyKey(record.getScope());
			bloomFilterScopeOnly.put(key);
		}
	}

	protected void _addToBloomFilterMetatags(List<MetatagsRecord> records) {
		_logger.info("Adding {} records into metatags bloom filter.", records.size());
		for (MetatagsRecord record : records) {
			String key = record.getKey();
			bloomFilterMetatags.put(key);
		}
	}

	private String _constructTermAggregationQuery(MetricSchemaRecordQuery query, RecordType type) {
		ObjectMapper mapper = new ObjectMapper();
		ObjectNode queryNode = _constructQueryNode(query, mapper);

		long size = query.getLimit() * query.getPage();
		SystemAssert.requireArgument(size > 0 && size <= Integer.MAX_VALUE,
				"(limit * page) must be greater than 0 and less than Integer.MAX_VALUE");

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

	private ObjectNode _constructSimpleQueryStringNode(List<String> tokens, RecordType... types) {

		if(tokens.isEmpty()) {
			return null;
		}

		ObjectMapper mapper = new ObjectMapper();

		StringBuilder queryString = new StringBuilder();
		for(String token : tokens) {
			queryString.append('+').append(token).append(' ');
		}
		queryString.replace(queryString.length() - 1, queryString.length(), "*");

		ObjectNode node = mapper.createObjectNode();
		ArrayNode fieldsNode = mapper.createArrayNode();
		for(RecordType type : types) {
			fieldsNode.add(type.getName());
		}
		node.put("fields", fieldsNode);
		node.put("query", queryString.toString());

		ObjectNode simpleQueryStringNode = mapper.createObjectNode();
		simpleQueryStringNode.put("simple_query_string", node);

		return simpleQueryStringNode;
	}

	private String _constructQueryStringQuery(List<String> tokens, int from, int size) {
		ObjectMapper mapper = new ObjectMapper();

		ObjectNode simpleQueryStringNode = _constructSimpleQueryStringNode(tokens, RecordType.values());

		ObjectNode rootNode = mapper.createObjectNode();
		rootNode.put("query", simpleQueryStringNode);
		rootNode.put("from", from);
		rootNode.put("size", size);

		return rootNode.toString();
	}

	private String _constructQueryStringQuery(KeywordQuery kq, Map<RecordType, List<String>> tokensMap) {
		ObjectMapper mapper = new ObjectMapper();

		ArrayNode filterNodes = mapper.createArrayNode();
		for(Map.Entry<RecordType, List<String>> entry : tokensMap.entrySet()) {
			ObjectNode simpleQueryStringNode = _constructSimpleQueryStringNode(entry.getValue(), entry.getKey());
			filterNodes.add(simpleQueryStringNode);
		}

		ObjectNode boolNode = mapper.createObjectNode();
		boolNode.put("filter", filterNodes);

		ObjectNode queryNode = mapper.createObjectNode();
		queryNode.put("bool", boolNode);

		ObjectNode rootNode = mapper.createObjectNode();
		rootNode.put("query", queryNode);
		rootNode.put("size", 0);

		long size = kq.getLimit() * kq.getPage();
		SystemAssert.requireArgument(size > 0 && size <= Integer.MAX_VALUE,
				"(limit * page) must be greater than 0 and less than Integer.MAX_VALUE");
		rootNode.put("aggs", _constructAggsNode(kq.getType(), Math.max(size, 10000), mapper));

		return rootNode.toString();

	}

	private ObjectNode _constructQueryNode(MetricSchemaRecordQuery query, ObjectMapper mapper) {
		ArrayNode filterNodes = mapper.createArrayNode();
		if(SchemaService.containsFilter(query.getMetric())) {
			ObjectNode node = mapper.createObjectNode();
			ObjectNode regexpNode = mapper.createObjectNode();
			regexpNode.put(RecordType.METRIC.getName() + ".raw", SchemaService.convertToRegex(query.getMetric()));
			node.put("regexp", regexpNode);
			filterNodes.add(node);
		}

		if(SchemaService.containsFilter(query.getScope())) {
			ObjectNode node = mapper.createObjectNode();
			ObjectNode regexpNode = mapper.createObjectNode();
			regexpNode.put(RecordType.SCOPE.getName() + ".raw", SchemaService.convertToRegex(query.getScope()));
			node.put("regexp", regexpNode);
			filterNodes.add(node);
		}

		if(SchemaService.containsFilter(query.getTagKey())) {
			ObjectNode node = mapper.createObjectNode();
			ObjectNode regexpNode = mapper.createObjectNode();
			regexpNode.put(RecordType.TAGK.getName() + ".raw", SchemaService.convertToRegex(query.getTagKey()));
			node.put("regexp", regexpNode);
			filterNodes.add(node);
		}

		if(SchemaService.containsFilter(query.getTagValue())) {
			ObjectNode node = mapper.createObjectNode();
			ObjectNode regexpNode = mapper.createObjectNode();
			regexpNode.put(RecordType.TAGV.getName() + ".raw", SchemaService.convertToRegex(query.getTagValue()));
			node.put("regexp", regexpNode);
			filterNodes.add(node);
		}

		if(SchemaService.containsFilter(query.getNamespace())) {
			ObjectNode node = mapper.createObjectNode();
			ObjectNode regexpNode = mapper.createObjectNode();
			regexpNode.put(RecordType.NAMESPACE.getName() + ".raw", SchemaService.convertToRegex(query.getNamespace()));
			node.put("regexp", regexpNode);
			filterNodes.add(node);
		}

		ObjectNode boolNode = mapper.createObjectNode();
		boolNode.put("filter", filterNodes);

		ObjectNode queryNode = mapper.createObjectNode();
		queryNode.put("bool", boolNode);
		return queryNode;
	}

	private ObjectNode _constructAggsNode(RecordType type, long limit, ObjectMapper mapper) {

		ObjectNode termsNode = mapper.createObjectNode();
		termsNode.put("field", type.getName() + ".raw");
		termsNode.put("order", mapper.createObjectNode().put("_term", "asc"));
		termsNode.put("size", limit);
		termsNode.put("execution_hint", "map");

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

	/* Method to change the rest client. Used for testing. */
	protected void setRestClient(RestClient restClient)
	{
		this._esRestClient = restClient;
	}

	/* Method to enable ScopeMetricNames Index. Used for testing. */
	protected void enableScopeMetricNamesIndex()
	{
		this._useScopeMetricNamesIndex = true;
	}

	/** Helper to process the response.
	 * Throws a SystemException when the http status code is outsdie of the range 200 - 300.
	 * @param response ES response
	 * @return	Stringified response
	 */
	protected String extractResponse(Response response) {
		requireArgument(response != null, "HttpResponse object cannot be null.");

		int status = response.getStatusLine().getStatusCode();
		String strResponse = extractStringResponse(response);

		if ((status < HttpStatus.SC_OK) || (status >= HttpStatus.SC_MULTIPLE_CHOICES)) {
			throw new SystemException("Status code: " + status + " .  Error occurred. " +  strResponse);
		} else {
			return strResponse;
		}
	}

	private String extractStringResponse(Response content) {
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
		module.addDeserializer(List.class, new SchemaRecordList.AggDeserializer());
		mapper.registerModule(module);

		return mapper;
	}

	private ObjectMapper _getScopeAndMetricOnlyObjectMapper(JsonSerializer<ScopeAndMetricOnlySchemaRecordList> serializer) {
		ObjectMapper mapper = new ObjectMapper();

		mapper.setSerializationInclusion(Include.NON_NULL);
		SimpleModule module = new SimpleModule();
		module.addSerializer(ScopeAndMetricOnlySchemaRecordList.class, serializer);
		module.addDeserializer(ScopeAndMetricOnlySchemaRecordList.class, new ScopeAndMetricOnlySchemaRecordList.Deserializer());
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

	private ObjectNode _createMappingsNode() {
		ObjectMapper mapper = new ObjectMapper();

		ObjectNode propertiesNode = mapper.createObjectNode();
		propertiesNode.put(RecordType.SCOPE.getName(), _createFieldNode(FIELD_TYPE_TEXT));
		propertiesNode.put(RecordType.METRIC.getName(), _createFieldNode(FIELD_TYPE_TEXT));
		propertiesNode.put(RecordType.TAGK.getName(), _createFieldNode(FIELD_TYPE_TEXT));
		propertiesNode.put(RecordType.TAGV.getName(), _createFieldNode(FIELD_TYPE_TEXT));
		propertiesNode.put(RecordType.NAMESPACE.getName(), _createFieldNode(FIELD_TYPE_TEXT));
		propertiesNode.put(RecordType.RETENTION_DISCOVERY.getName(), _createFieldNode(FIELD_TYPE_TEXT));

		propertiesNode.put("mts", _createFieldNodeNoAnalyzer(FIELD_TYPE_DATE));

		ObjectNode typeNode = mapper.createObjectNode();
		typeNode.put("properties", propertiesNode);

		ObjectNode mappingsNode = mapper.createObjectNode();
		mappingsNode.put(TYPE_NAME, typeNode);
		return mappingsNode;
	}

	private ObjectNode _createScopeAndMetricMappingsNode() {
		ObjectMapper mapper = new ObjectMapper();

		ObjectNode propertiesNode = mapper.createObjectNode();
		propertiesNode.put(RecordType.SCOPE.getName(), _createFieldNode(FIELD_TYPE_TEXT));
		propertiesNode.put(RecordType.METRIC.getName(), _createFieldNode(FIELD_TYPE_TEXT));

		propertiesNode.put("mts", _createFieldNodeNoAnalyzer(FIELD_TYPE_DATE));
		propertiesNode.put("cts", _createFieldNodeNoAnalyzer(FIELD_TYPE_DATE));

		ObjectNode typeNode = mapper.createObjectNode();
		typeNode.put("properties", propertiesNode);

		ObjectNode mappingsNode = mapper.createObjectNode();
		mappingsNode.put(SCOPE_AND_METRIC_TYPE_NAME, typeNode);

		return mappingsNode;
	}


	private ObjectNode _createScopeMappingsNode() {
		ObjectMapper mapper = new ObjectMapper();

		ObjectNode propertiesNode = mapper.createObjectNode();
		propertiesNode.put(RecordType.SCOPE.getName(), _createFieldNode(FIELD_TYPE_TEXT));

		propertiesNode.put("mts", _createFieldNodeNoAnalyzer(FIELD_TYPE_DATE));
		propertiesNode.put("cts", _createFieldNodeNoAnalyzer(FIELD_TYPE_DATE));

		ObjectNode typeNode = mapper.createObjectNode();
		typeNode.put("properties", propertiesNode);

		ObjectNode mappingsNode = mapper.createObjectNode();
		mappingsNode.put(SCOPE_TYPE_NAME, typeNode);

		return mappingsNode;
	}

	private ObjectNode _createMetatagsMappingsNode() {
		ObjectMapper mapper = new ObjectMapper();

		ObjectNode propertiesNode = mapper.createObjectNode();
		propertiesNode.put(RecordType.METATAGS.getName(), _createFieldNode(FIELD_TYPE_TEXT));

		propertiesNode.put("mts", _createFieldNodeNoAnalyzer(FIELD_TYPE_DATE));
		propertiesNode.put("cts", _createFieldNodeNoAnalyzer(FIELD_TYPE_DATE));

		ObjectNode typeNode = mapper.createObjectNode();
		typeNode.put("properties", propertiesNode);

		ObjectNode mappingsNode = mapper.createObjectNode();
		mappingsNode.put(METATAGS_TYPE_NAME, typeNode);

		return mappingsNode;
	}

	private ObjectNode _createFieldNode(String type) {
		ObjectMapper mapper = new ObjectMapper();

		ObjectNode fieldNode = mapper.createObjectNode();
		fieldNode.put("type", type);
		fieldNode.put("analyzer", "metadata_analyzer");
		ObjectNode keywordNode = mapper.createObjectNode();
		keywordNode.put("type", "keyword");
		ObjectNode fieldsNode = mapper.createObjectNode();
		fieldsNode.put("raw", keywordNode);
		fieldNode.put("fields", fieldsNode);
		return fieldNode;
	}

	private ObjectNode _createFieldNodeNoAnalyzer(String type) {
		ObjectMapper mapper = new ObjectMapper();

		ObjectNode fieldNode = mapper.createObjectNode();
		fieldNode.put("type", type);
		return fieldNode;
	}

	private void _createIndexIfNotExists(String indexName, int replicationFactor, int numShards,
										 Supplier<ObjectNode> createMappingsNode) {
		try {
			Response response = _esRestClient.performRequest(HttpMethod.HEAD.getName(), "/" + indexName);
			boolean indexExists = response.getStatusLine().getStatusCode() == HttpStatus.SC_OK ? true : false;

			if(!indexExists) {
				_logger.info("Index [" + indexName + "] does not exist. Will create one.");
				ObjectMapper mapper = new ObjectMapper();

				ObjectNode rootNode = mapper.createObjectNode();
				rootNode.put("settings", _createSettingsNode(replicationFactor, numShards));
				rootNode.put("mappings", createMappingsNode.get());

				String settingsAndMappingsJson = rootNode.toString();
				String requestUrl = new StringBuilder().append("/").append(indexName).toString();

				response = _esRestClient.performRequest(HttpMethod.PUT.getName(), requestUrl, Collections.emptyMap(), new StringEntity(settingsAndMappingsJson));
				extractResponse(response);
			}
		} catch (Exception e) {
			_logger.error("Failed to check/create {} index. ElasticSearchSchemaService may not function. {}",
				indexName, e);
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
		/** Replication factor for metadata_index. */
		ELASTICSEARCH_NUM_REPLICAS("service.property.schema.elasticsearch.num.replicas", "1"),
		/** Shard count for metadata_index. */
		ELASTICSEARCH_SHARDS_COUNT("service.property.schema.elasticsearch.shards.count", "10"),
		/** Replication factor for scopenames */
		ELASTICSEARCH_NUM_REPLICAS_FOR_SCOPE_INDEX("service.property.schema.elasticsearch.num.replicas.for.scope.index", "1"),
		/** Shard count for scopenames */
		ELASTICSEARCH_SHARDS_COUNT_FOR_SCOPE_INDEX("service.property.schema.elasticsearch.shards.count.for.scope.index", "6"),
		/** Replication factor for metatags */
		ELASTICSEARCH_NUM_REPLICAS_FOR_METATAGS_INDEX("service.property.schema.elasticsearch.num.replicas.for.metatags.index", "1"),
		/** Shard count for metatags */
		ELASTICSEARCH_SHARDS_COUNT_FOR_METATAGS_INDEX("service.property.schema.elasticsearch.shards.count.for.metatags.index", "6"),
		/** The no. of records to batch for bulk indexing requests.
		 * https://www.elastic.co/guide/en/elasticsearch/guide/current/indexing-performance.html#_using_and_sizing_bulk_requests
		 */
		ELASTICSEARCH_INDEXING_BATCH_SIZE("service.property.schema.elasticsearch.indexing.batch.size", "10000"),

		ELASTICSEARCH_USE_SCOPE_AND_METRIC_INDEX("service.property.schema.elasticsearch.use.scopeandmetric.index", "false"),

		/** The hashing algorithm to use for generating document id. */
		ELASTICSEARCH_IDGEN_HASH_ALGO("service.property.schema.elasticsearch.idgen.hash.algo", "MD5"),

		/** Name of scope only index */
		ELASTICSEARCH_SCOPE_INDEX_NAME("service.property.schema.elasticsearch.scope.index.name", "scopenames"),
		/** Type within scope only index */
		ELASTICSEARCH_SCOPE_TYPE_NAME("service.property.schema.elasticsearch.scope.type.name", "scope_type"),

		/** Name of metatags only index */
		ELASTICSEARCH_METATAGS_INDEX_NAME("service.property.schema.elasticsearch.metatags.index.name", "metatags"),
		/** Type within metatags only index */
		ELASTICSEARCH_METATAGS_TYPE_NAME("service.property.schema.elasticsearch.metatags.type.name", "metatags_type"),

		/** Replication factor for scope and metric names */
		ELASTICSEARCH_NUM_REPLICAS_FOR_SCOPE_AND_METRIC_INDEX("service.property.schema.elasticsearch.num.replicas.for.scopeandmetric.index", "1"),
		/** Shard count for scope and metric names */
		ELASTICSEARCH_SHARDS_COUNT_FOR_SCOPE_AND_METRIC_INDEX("service.property.schema.elasticsearch.shards.count.for.scopeandmetric.index", "6"),

		/** Name of scope and metric only index */
		ELASTICSEARCH_SCOPE_AND_METRIC_INDEX_NAME("service.property.schema.elasticsearch.scopeandmetric.index.name", "scopemetricnames"),
		/** Type within scope and metric only index */
		ELASTICSEARCH_SCOPE_AND_METRIC_TYPE_NAME("service.property.schema.elasticsearch.scopeandmetric.type.name", "scopemetric_type");

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

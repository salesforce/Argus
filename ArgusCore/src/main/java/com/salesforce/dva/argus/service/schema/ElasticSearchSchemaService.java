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
import com.salesforce.dva.argus.service.schema.ElasticSearchUtils;
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
	private static final int INDEX_MAX_RESULT_WINDOW = 10000;
	private static final int MAX_RETRY_TIMEOUT = 300 * 1000;
	private static final String FIELD_TYPE_TEXT = "text";
	private static final String FIELD_TYPE_DATE ="date";
	private static final String FIELD_TYPE_INTEGER = "integer";
	private static final long ONE_DAY_IN_MILLIS = 24L * 3600L * 1000L;
	private RestClient _esRestClient;
	private final int _bulkIndexingSize;
	private HashAlgorithm _idgenHashAlgo;

	/** Main index properties */
	private static String TAGS_INDEX_NAME;
	private static String TAGS_TYPE_NAME;
	private final ObjectMapper _createMetadataMapper;
	private final ObjectMapper _updateMetadataMapper;
	private final int _replicationFactor;
	private final int _numShards;

	/** Scope-only index properties */
	private static String SCOPE_INDEX_NAME;
	private static String SCOPE_TYPE_NAME;
	private final ObjectMapper _createScopeOnlyMapper;
	private final ObjectMapper _updateScopeOnlyMapper;
	private final int _replicationFactorForScopeIndex;
	private final int _numShardsForScopeIndex;

	/** Metatags index properties */
	private static String METATAGS_INDEX_NAME;
	private static String METATAGS_TYPE_NAME;
	private final ObjectMapper _createMetatagsMapper;
	private final ObjectMapper _updateMetatagsMapper;
	private final int _replicationFactorForMetatagsIndex;
	private final int _numShardsForMetatagsIndex;
        private final String _metatagsBulkIndexUrl;


        @Inject
	public ElasticSearchSchemaService(SystemConfiguration config, MonitorService monitorService, ElasticSearchUtils esUtils) {
                super(config, monitorService);

		/** Setup Global ES stuff */
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

		/** Set up main index stuff */
		_createMetadataMapper = _getMetadataObjectMapper(new MetricSchemaRecordList.CreateSerializer());
		_updateMetadataMapper = _getMetadataObjectMapper(new MetricSchemaRecordList.UpdateSerializer());
		TAGS_INDEX_NAME = config.getValue(Property.ELASTICSEARCH_TAGS_INDEX_NAME.getName(),
				Property.ELASTICSEARCH_TAGS_INDEX_NAME.getDefaultValue());
		TAGS_TYPE_NAME = config.getValue(Property.ELASTICSEARCH_TAGS_TYPE_NAME.getName(),
				Property.ELASTICSEARCH_TAGS_TYPE_NAME.getDefaultValue());
		_replicationFactor = Integer.parseInt(
				config.getValue(Property.ELASTICSEARCH_NUM_REPLICAS_FOR_TAGS_INDEX.getName(), Property.ELASTICSEARCH_NUM_REPLICAS_FOR_TAGS_INDEX.getDefaultValue()));
		_numShards = Integer.parseInt(
				config.getValue(Property.ELASTICSEARCH_SHARDS_COUNT_FOR_TAGS_INDEX.getName(), Property.ELASTICSEARCH_SHARDS_COUNT_FOR_TAGS_INDEX.getDefaultValue()));

                esUtils.createIndexIfNotExists(_esRestClient,
                                               TAGS_INDEX_NAME,
                                               _replicationFactor,
                                               _numShards,
                                               () -> _createMappingsNode());


		/** Set up scope-only index stuff */
		_createScopeOnlyMapper = _getScopeOnlyObjectMapper(new ScopeOnlySchemaRecordList.CreateSerializer());
		_updateScopeOnlyMapper = _getScopeOnlyObjectMapper(new ScopeOnlySchemaRecordList.UpdateSerializer());
		SCOPE_INDEX_NAME = config.getValue(Property.ELASTICSEARCH_SCOPE_INDEX_NAME.getName(),
				Property.ELASTICSEARCH_SCOPE_INDEX_NAME.getDefaultValue());
		SCOPE_TYPE_NAME = config.getValue(Property.ELASTICSEARCH_SCOPE_TYPE_NAME.getName(),
				Property.ELASTICSEARCH_SCOPE_TYPE_NAME.getDefaultValue());
		_replicationFactorForScopeIndex = Integer.parseInt(
				config.getValue(Property.ELASTICSEARCH_NUM_REPLICAS_FOR_SCOPE_INDEX.getName(), Property.ELASTICSEARCH_NUM_REPLICAS_FOR_SCOPE_INDEX.getDefaultValue()));
		_numShardsForScopeIndex = Integer.parseInt(
				config.getValue(Property.ELASTICSEARCH_SHARDS_COUNT_FOR_SCOPE_INDEX.getName(), Property.ELASTICSEARCH_SHARDS_COUNT_FOR_SCOPE_INDEX.getDefaultValue()));

                esUtils.createIndexIfNotExists(_esRestClient,
                                               SCOPE_INDEX_NAME,
                                               _replicationFactorForScopeIndex,
                                               _numShardsForScopeIndex,
                                               () -> _createScopeMappingsNode());

		/** Set up metatags index stuff */
		_createMetatagsMapper = _getMetatagsObjectMapper(new MetatagsSchemaRecordList.CreateSerializer());
		_updateMetatagsMapper = _getMetatagsObjectMapper(new MetatagsSchemaRecordList.UpdateSerializer());
		METATAGS_INDEX_NAME = config.getValue(Property.ELASTICSEARCH_METATAGS_INDEX_NAME.getName(),
				Property.ELASTICSEARCH_METATAGS_INDEX_NAME.getDefaultValue());
		METATAGS_TYPE_NAME = config.getValue(Property.ELASTICSEARCH_METATAGS_TYPE_NAME.getName(),
				Property.ELASTICSEARCH_METATAGS_TYPE_NAME.getDefaultValue());
                _metatagsBulkIndexUrl = String.format("/%s/%s/_bulk", METATAGS_INDEX_NAME, METATAGS_TYPE_NAME);
		_replicationFactorForMetatagsIndex = Integer.parseInt(
				config.getValue(Property.ELASTICSEARCH_NUM_REPLICAS_FOR_METATAGS_INDEX.getName(),
						Property.ELASTICSEARCH_NUM_REPLICAS_FOR_METATAGS_INDEX.getDefaultValue()));
		_numShardsForMetatagsIndex = Integer.parseInt(
				config.getValue(Property.ELASTICSEARCH_SHARDS_COUNT_FOR_METATAGS_INDEX.getName(),
						Property.ELASTICSEARCH_SHARDS_COUNT_FOR_METATAGS_INDEX.getDefaultValue()));

                esUtils.createIndexIfNotExists(_esRestClient,
                                               METATAGS_INDEX_NAME,
                                               _replicationFactorForMetatagsIndex,
                                               _numShardsForMetatagsIndex,
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
	protected void implementationSpecificPut(Set<Metric> metricsToCreate,
											 Set<Metric> metricsToUpdate,
											 Set<String> scopesToCreate,
											 Set<String> scopesToUpdate,
											 Set<MetatagsRecord> metatagsToCreate,
											 Set<MetatagsRecord> metatagsToUpdate) {
		SystemAssert.requireArgument(metricsToCreate != null, "Metrics list cannot be null.");

		// Push to metadata index
		int totalCount = 0;
		long start = System.currentTimeMillis();
		List<Set<MetricSchemaRecord>> fracturedToCreateList = _fracture(metricsToCreate);
		for(Set<MetricSchemaRecord> records : fracturedToCreateList) {
			if(!records.isEmpty()) {
				Set<MetricSchemaRecord> failedRecords = upsertMetadataRecords(records);
				records.removeAll(failedRecords);
				_addToCreatedBloom(records);
				_addToModifiedBloom(records);
				totalCount += records.size();
			}
		}
		int createdCount = totalCount;
		List<Set<MetricSchemaRecord>> fracturedToUpdateList = _fracture(metricsToUpdate);
		for(Set<MetricSchemaRecord> records : fracturedToUpdateList) {
			if(!records.isEmpty()) {
				Set<MetricSchemaRecord> failedRecords = updateMetadataRecordMts(records);
				records.removeAll(failedRecords);
				_addToModifiedBloom(records);
				totalCount += records.size();
			}
		}
		_monitorService.modifyCounter(MonitorService.Counter.SCHEMARECORDS_WRITTEN, totalCount, null);
		_monitorService.modifyCounter(MonitorService.Counter.SCHEMARECORDS_WRITE_LATENCY,
				(System.currentTimeMillis() - start),
				null);
		_logger.info("{} new metrics sent to ES in {} ms. {} added to both bloomfilters. {} more added to modifiedBloom", totalCount, System.currentTimeMillis()-start, createdCount, totalCount-createdCount);

		// Push to scope-only index
		totalCount = 0;
		start = System.currentTimeMillis();
		List<Set<ScopeOnlySchemaRecord>> fracturedScopesToCreate = _fractureScopes(scopesToCreate);
		for(Set<ScopeOnlySchemaRecord> records : fracturedScopesToCreate) {
			if(!records.isEmpty()) {
				Set<ScopeOnlySchemaRecord> failedRecords = upsertScopeRecords(records);
				records.removeAll(failedRecords);
				_addToCreatedBloom(records);
				_addToModifiedBloom(records);
				totalCount += records.size();
			}
		}
		createdCount = totalCount;
		List<Set<ScopeOnlySchemaRecord>> fracturedScopesToUpdate = _fractureScopes(scopesToUpdate);
		for(Set<ScopeOnlySchemaRecord> records : fracturedScopesToUpdate) {
			if(!records.isEmpty()) {
				Set<ScopeOnlySchemaRecord> failedRecords = updateScopeRecordMts(records);
				records.removeAll(failedRecords);
				_addToModifiedBloom(records);
				totalCount += records.size();
			}
		}
		_monitorService.modifyCounter(MonitorService.Counter.SCOPENAMES_WRITTEN, totalCount, null);
		_monitorService.modifyCounter(MonitorService.Counter.SCOPENAMES_WRITE_LATENCY,
				(System.currentTimeMillis() - start),
				null);
		_logger.info("{} new scopes sent to ES in {} ms. {} added to both bloomfilters. {} more added to modifiedBloom", totalCount, System.currentTimeMillis()-start, createdCount, totalCount-createdCount);

		// Push to metatags index
		totalCount = 0;
		start = System.currentTimeMillis();
		List<Set<MetatagsRecord>> fracturedMetatagsToCreate = _fractureMetatags(metatagsToCreate);
		for(Set<MetatagsRecord> records : fracturedMetatagsToCreate) {
			if(!records.isEmpty()) {
				Set<MetatagsRecord> failedRecords = upsertMetatags(records);
				records.removeAll(failedRecords);
				_addToCreatedBloom(records);
				_addToModifiedBloom(records);
				totalCount += records.size();
			}
		}
		createdCount = totalCount;
		List<Set<MetatagsRecord>> fracturedMetatagsToUpdate = _fractureMetatags(metatagsToUpdate);
		for(Set<MetatagsRecord> records : fracturedMetatagsToUpdate) {
			if(!records.isEmpty()) {
				Set<MetatagsRecord> failedRecords = upsertMetatags(records);
				records.removeAll(failedRecords);
				_addToModifiedBloom(records);
				totalCount += records.size();
			}
		}
		_monitorService.modifyCounter(MonitorService.Counter.METATAGS_WRITTEN, totalCount, null);
		_monitorService.modifyCounter(Counter.METATAGS_WRITE_LATENCY,
				(System.currentTimeMillis() - start),
				null);

		_logger.info("{} new metatags sent to ES in {} ms. {} added to both bloomfilters. {} more added to modifiedBloom", totalCount, System.currentTimeMillis()-start, createdCount, totalCount-createdCount);
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
		long size = (long) query.getLimit() * query.getPage();
		SystemAssert.requireArgument(size > 0 && size <= Integer.MAX_VALUE,
				"(limit * page) must be greater than 0 and atmost Integer.MAX_VALUE");


		Map<String, String> tags = new HashMap<>();
		tags.put("type", "REGEXP_WITHOUT_AGGREGATION");
		long start = System.currentTimeMillis();
		boolean scroll = false;
		StringBuilder sb = new StringBuilder().append("/")
				.append(TAGS_INDEX_NAME)
				.append("/")
				.append(TAGS_TYPE_NAME)
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
			Response response = _esRestClient.performRequest(HttpMethod.POST.getName(), requestUrl, Collections.emptyMap(), new StringEntity(queryJson, ContentType.APPLICATION_JSON));

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
					response = _esRestClient.performRequest(HttpMethod.POST.getName(), requestUrl, Collections.emptyMap(), new StringEntity(requestJson, ContentType.APPLICATION_JSON));

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
			throw new SystemException("Search failed: " + e);
		} catch (IOException e) {
			throw new SystemException("IOException when trying to perform ES request" + e);
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
			Response response = _esRestClient.performRequest(HttpMethod.POST.getName(), requestUrl, Collections.emptyMap(), new StringEntity(queryJson, ContentType.APPLICATION_JSON));
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

				Response response = _esRestClient.performRequest(HttpMethod.POST.getName(), requestUrl, Collections.emptyMap(), new StringEntity(queryJson, ContentType.APPLICATION_JSON));
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
						        new StringEntity(new ObjectMapper().writeValueAsString(requestBody), ContentType.APPLICATION_JSON));

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
				Response response = _esRestClient.performRequest(HttpMethod.POST.getName(), requestUrl, Collections.emptyMap(), new StringEntity(queryJson, ContentType.APPLICATION_JSON));
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
			Response response = _esRestClient.performRequest(HttpMethod.POST.getName(), requestUrl, Collections.emptyMap(), new StringEntity(requestBody, ContentType.APPLICATION_JSON));
			String strResponse = extractResponse(response);
			JsonNode tokensNode = _createMetadataMapper.readTree(strResponse).get("tokens");
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
	 * @param records
	 * @return	List of records that FAILED to upsert
	 */
	protected Set<MetricSchemaRecord> upsertMetadataRecords(Set<MetricSchemaRecord> records) {
		String requestUrl = String.format("/%s/%s/_bulk", TAGS_INDEX_NAME, TAGS_TYPE_NAME);
		String strResponse;

		MetricSchemaRecordList msrList = new MetricSchemaRecordList(records, _idgenHashAlgo);
		try {
			String requestBody = _createMetadataMapper.writeValueAsString(msrList);
			Response response = _esRestClient.performRequest(HttpMethod.POST.getName(), requestUrl,
					Collections.emptyMap(), new StringEntity(requestBody, ContentType.APPLICATION_JSON));
			strResponse = extractResponse(response);
		} catch (IOException e) {
			//TODO: Retry with exponential back-off for handling EsRejectedExecutionException/RemoteTransportException/TimeoutException??
			throw new SystemException(e);
		}

		try {
			Set<MetricSchemaRecord> failedRecords = new HashSet<>();
			PutResponse putResponse = new ObjectMapper().readValue(strResponse, PutResponse.class);
			//TODO: If response contains HTTP 429 Too Many Requests (EsRejectedExecutionException), then retry with exponential back-off.
			if(putResponse.errors) {
				List<String> idsToUpdate = new ArrayList<>();
				for(Item item : putResponse.items) {
					if(item.create != null) {
						if (item.create.status == HttpStatus.SC_CONFLICT) {
							idsToUpdate.add(item.create._id);
						} else if (item.create.status != HttpStatus.SC_CREATED) {
							_logger.warn("Failed to index metric {}. Reason: {}", msrList.getRecord(item.create._id),
									new ObjectMapper().writeValueAsString(item.create.error));
							failedRecords.add(msrList.getRecord(item.create._id));
						}
					}
				}
				if (idsToUpdate.size() > 0) {
					_logger.debug("mts filed will be updated for docs with ids {}", idsToUpdate);
					Set<MetricSchemaRecord> recordsToUpdate = new HashSet<>();
					for (String id: idsToUpdate) {
						recordsToUpdate.add(msrList.getRecord(id));
					}
					Set<MetricSchemaRecord> failedUpdates = updateMetadataRecordMts(recordsToUpdate);
					failedRecords.addAll(failedUpdates);
				}

				if (failedRecords.size() != 0) {
					_logger.warn("{} records were not written to ES", failedRecords.size());
				}
			}
			return failedRecords;
		} catch(IOException e) {
			throw new SystemException("Failed to parse reponse of put metrics. The response was: " + strResponse, e);
		}
	}

	/**
	 * @param records
	 * @return	List of records that FAILED to update
	 */
	protected Set<MetricSchemaRecord> updateMetadataRecordMts(Set<MetricSchemaRecord> records) {
		String requestUrl = String.format("/%s/%s/_bulk", TAGS_INDEX_NAME, TAGS_TYPE_NAME);
		Set<MetricSchemaRecord> failedRecords = new HashSet<>();
		try {
			MetricSchemaRecordList updateSchemaRecordList = new MetricSchemaRecordList(records, _idgenHashAlgo);
			String requestBody = _updateMetadataMapper.writeValueAsString(updateSchemaRecordList);
			PutResponse updateResponse = _performRequest(requestUrl, requestBody);

			for(Item item: updateResponse.items) {
				if(item.update != null && item.update.status != HttpStatus.SC_OK) {
					_logger.debug("Failed to update mts field for metric {}. Reason: {}",updateSchemaRecordList.getRecord(item.update._id),
							new ObjectMapper().writeValueAsString(item.update.error));
					failedRecords.add(updateSchemaRecordList.getRecord(item.update._id));
				}
			}
		} catch (IOException e) {
			throw new SystemException(e);
		}
		return failedRecords;
	}

	/**
	 * @param records
	 * @return	List of records that FAILED to upsert
	 */
	protected Set<ScopeOnlySchemaRecord> upsertScopeRecords(Set<ScopeOnlySchemaRecord> records) {
		String requestUrl = String.format("/%s/%s/_bulk", SCOPE_INDEX_NAME, SCOPE_TYPE_NAME);

		try {
			Set<ScopeOnlySchemaRecord> failedRecords = new HashSet<>();
			ScopeOnlySchemaRecordList createSchemaRecordList = new ScopeOnlySchemaRecordList(records, _idgenHashAlgo);
			String requestBody = _createScopeOnlyMapper.writeValueAsString(createSchemaRecordList);
			PutResponse putResponse = _performRequest(requestUrl, requestBody);

			Pair<List<String>, List<String>> failedResponses = _parseFailedResponses(putResponse);

			List<String> failedIds = failedResponses.getLeft();
			List<String> updateRequiredIds = failedResponses.getRight();

			if (updateRequiredIds.size() > 0) {

				Set<ScopeOnlySchemaRecord> updateRequiredRecords = new HashSet<>();

				for (String id : updateRequiredIds) {
					updateRequiredRecords.add(createSchemaRecordList.getRecord(id));
				}

				Set<ScopeOnlySchemaRecord> failedScopeUpdates = updateScopeRecordMts(updateRequiredRecords);
				failedRecords.addAll(failedScopeUpdates);
			}

			if (failedIds.size() > 0) {
				_logger.warn("{} records were not written to scope ES", failedIds.size());
			}

			for(String id : failedIds) {
				failedRecords.add(createSchemaRecordList.getRecord(id));
			}
			return failedRecords;
		} catch (IOException e) {
			throw new SystemException("Failed to upsert scope-only record to ES. ", e);
		}
	}

	/**
	 * @param records
	 * @return	List of records that FAILED to update
	 */
	protected Set<ScopeOnlySchemaRecord> updateScopeRecordMts(Set<ScopeOnlySchemaRecord> records) {
		String requestUrl = String.format("/%s/%s/_bulk", SCOPE_INDEX_NAME, SCOPE_TYPE_NAME);
		Set<ScopeOnlySchemaRecord> failedRecords = new HashSet<>();
		try {
			ScopeOnlySchemaRecordList updateSchemaRecordList = new ScopeOnlySchemaRecordList(records, _idgenHashAlgo);
			String requestBody = _updateScopeOnlyMapper.writeValueAsString(updateSchemaRecordList);
			PutResponse putResponse = _performRequest(requestUrl, requestBody);

			Pair<List<String>, List<String>> failedResponses = _parseFailedResponses(putResponse);
			List<String> failedIds = failedResponses.getLeft();

			for (String id : failedIds) {
				failedRecords.add(updateSchemaRecordList.getRecord(id));
			}
		} catch (IOException ex) {
			throw new SystemException("Failed to update scope-only record to ES", ex);
		}
		return failedRecords;
	}

    protected Pair<MetatagsSchemaRecordList, String> getListAndBodyForUpsertMetatags(Set<MetatagsRecord> records) throws IOException {
        MetatagsSchemaRecordList createMetatagsSchemaRecordList = new MetatagsSchemaRecordList(records, _idgenHashAlgo);
        String requestBody = _createMetatagsMapper.writeValueAsString(createMetatagsSchemaRecordList);
        return Pair.of(createMetatagsSchemaRecordList, requestBody);
    }
	/**
	 * @param records
	 * @return	List of records that FAILED to upsert
	 */
	protected Set<MetatagsRecord> upsertMetatags(Set<MetatagsRecord> records) {
		try {
			Set<MetatagsRecord> failedRecords = new HashSet<>();
                        Pair<MetatagsSchemaRecordList, String> retPair = getListAndBodyForUpsertMetatags(records);
                        MetatagsSchemaRecordList createMetatagsSchemaRecordList = retPair.getKey();
                        String requestBody = retPair.getValue();
			PutResponse putResponse = _performRequest(_metatagsBulkIndexUrl, requestBody);
			Pair<List<String>, List<String>> failedResponses = _parseFailedResponses(putResponse);

			List<String> failedIds = failedResponses.getLeft();
			List<String> updateRequiredIds = failedResponses.getRight();

			if (updateRequiredIds.size() > 0) {
				Set<MetatagsRecord> updateRequiredRecords = new HashSet<>();
				for (String id : updateRequiredIds) {
					updateRequiredRecords.add(createMetatagsSchemaRecordList.getRecord(id));
				}

				Set<MetatagsRecord> failedMetatagsUpdates = updateMetatagsMts(updateRequiredRecords);
				failedRecords.addAll(failedMetatagsUpdates);
			}

			if (failedIds.size() > 0) {
				_logger.warn("{} records were not written to metatags ES", failedIds.size());
			}

			for(String id : failedIds) {
				failedRecords.add(createMetatagsSchemaRecordList.getRecord(id));
			}
			return failedRecords;
		} catch (IOException e) {
			throw new SystemException("Failed to upsert metatags record to ES. ", e);
		}
	}

	/**
	 * @param records
	 * @return	List of records that FAILED to update
	 */
	protected Set<MetatagsRecord> updateMetatagsMts(Set<MetatagsRecord> records) {
		String requestUrl = String.format("/%s/%s/_bulk", METATAGS_INDEX_NAME, METATAGS_TYPE_NAME);
		Set<MetatagsRecord> failedRecords = new HashSet<>();

		try {
			MetatagsSchemaRecordList updateMetatagsSchemaRecordList = new MetatagsSchemaRecordList(records, _idgenHashAlgo);
			String requestBody = _updateMetatagsMapper.writeValueAsString(updateMetatagsSchemaRecordList);
			PutResponse putResponse = _performRequest(requestUrl, requestBody);

			Pair<List<String>, List<String>> failedResponses = _parseFailedResponses(putResponse);

			List<String> failedIds = failedResponses.getLeft();
			for (String id : failedIds) {
				failedRecords.add(updateMetatagsSchemaRecordList.getRecord(id));
			}
		} catch (IOException ex) {
			throw new SystemException("Failed to update metatags record to ES. ", ex);
		}
		return failedRecords;
	}

	private PutResponse _performRequest(String requestUrl, String requestBody) throws IOException {

		String strResponse = "";

		Response response = _esRestClient.performRequest(HttpMethod.POST.getName(), requestUrl, Collections.emptyMap(), new StringEntity(requestBody, ContentType.APPLICATION_JSON));

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

	protected void _addToCreatedBloom(Set<? extends AbstractSchemaRecord> records) {
		for (AbstractSchemaRecord record : records) {
			createdBloom.put(record.toBloomFilterKey());
		}
	}

	protected void _addToModifiedBloom(Set<? extends AbstractSchemaRecord> records) {
		for (AbstractSchemaRecord record : records) {
			modifiedBloom.put(record.toBloomFilterKey());
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

		ObjectNode rootNode = _createMetadataMapper.createObjectNode();
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
			node.put("regexp", regexpNode);
			parentNode.add(node);
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
		if (mustNotNodes.size() > 0) {
			boolNode.put("must_not", mustNotNodes);
		}

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
			return _createMetadataMapper.readValue(content, type);
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
		propertiesNode.set(RecordType.SCOPE.getName(), _createFieldNode(FIELD_TYPE_TEXT));
		propertiesNode.set(RecordType.METRIC.getName(), _createFieldNode(FIELD_TYPE_TEXT));
		propertiesNode.set(RecordType.TAGK.getName(), _createFieldNode(FIELD_TYPE_TEXT));
		propertiesNode.set(RecordType.TAGV.getName(), _createFieldNode(FIELD_TYPE_TEXT));
		propertiesNode.set(RecordType.NAMESPACE.getName(), _createFieldNode(FIELD_TYPE_TEXT));
		propertiesNode.set(RecordType.RETENTION_DISCOVERY.getName(),
                                   _createFieldNodeNoAnalyzer(FIELD_TYPE_INTEGER));
		propertiesNode.set(MetricSchemaRecord.EXPIRATION_TS, _createFieldNodeNoAnalyzer(FIELD_TYPE_DATE));

		propertiesNode.set("mts", _createFieldNodeNoAnalyzer(FIELD_TYPE_DATE));

		ObjectNode typeNode = mapper.createObjectNode();
		typeNode.put("properties", propertiesNode);

		ObjectNode mappingsNode = mapper.createObjectNode();
		mappingsNode.put(TAGS_TYPE_NAME, typeNode);
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

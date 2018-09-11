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

package com.salesforce.dva.argus.service.tsdb;

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
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.commons.lang.StringUtils;
import org.apache.http.ConnectionReuseStrategy;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.MethodNotSupportedException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.inject.Inject;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;

/**
 * TSDB abstract class, where the put methods are implemented, and the get methods are overridden by specific implementation.
 *
 * @author  Tom Valine (tvaline@salesforce.com), Bhinav Sura (bhinav.sura@salesforce.com)
 */
public class AbstractTSDBService extends DefaultService implements TSDBService {

	//~ Static fields/initializers *******************************************************************************************************************

	private static final int CHUNK_SIZE = 50;
	private static final int TSDB_DATAPOINTS_WRITE_MAX_SIZE = 100;
	private static final String QUERY_LATENCY_COUNTER = "query.latency";
	private static final String QUERY_COUNT_COUNTER = "query.count";
	static final String DELIMITER = "-__-";

	//~ Instance fields ******************************************************************************************************************************
	private final ObjectMapper _mapper;
	protected final Logger _logger = LoggerFactory.getLogger(getClass());

	private final String[] _writeEndpoints;
	protected CloseableHttpClient _writeHttpClient;

	protected final List<String> _readEndPoints;
	protected final List<String> _readBackupEndPoints;
	protected Map<String, CloseableHttpClient> _readPortMap = new HashMap<>();
	protected final Map<String, String> _readBackupEndPointsMap = new HashMap<>();

	/** Round robin iterator for write endpoints. 
	 * We will cycle through this iterator to select an endpoint from the set of available endpoints  */
	private final Iterator<String> _roundRobinIterator;

	protected final ExecutorService _executorService;
	protected final MonitorService _monitorService;
	private final int RETRY_COUNT;

	/*
		Given a key for an annotation, we cache its tuid obtained from TSDB
		when storing the metric portion of annotation.
		Annotations cannot directly be stored as metric_name and tags.
		You need to store the metric_name with tags, get the generated tuid and store the annotation using the tuid.

		Feature request to fix this is mentioned below.
		https://github.com/OpenTSDB/opentsdb/issues/913

	*/
	private Cache<String, String> _keyUidCache;

	//~ Constructors *********************************************************************************************************************************

	/**
	 * Creates a new Default TSDB Service having an equal number of read and write routes.
	 *
	 * @param   config               The system _configuration used to configure the service.
	 * @param   monitorService       The monitor service used to collect query time window counters. Cannot be null.
	 * @throws  SystemException  If an error occurs configuring the service.
	 */
	@Inject
	public AbstractTSDBService(SystemConfiguration config, MonitorService monitorService) {
		super(config);
		requireArgument(config != null, "System configuration cannot be null.");
		requireArgument(monitorService != null, "Monitor service cannot be null.");
		_monitorService = monitorService;

		_mapper = getMapper();
		int connCount = Integer.parseInt(config.getValue(Property.TSD_CONNECTION_COUNT.getName(),
				Property.TSD_CONNECTION_COUNT.getDefaultValue()));
		int connTimeout = Integer.parseInt(config.getValue(Property.TSD_ENDPOINT_CONNECTION_TIMEOUT.getName(),
				Property.TSD_ENDPOINT_CONNECTION_TIMEOUT.getDefaultValue()));
		int socketTimeout = Integer.parseInt(config.getValue(Property.TSD_ENDPOINT_SOCKET_TIMEOUT.getName(),
				Property.TSD_ENDPOINT_SOCKET_TIMEOUT.getDefaultValue()));
		int tsdbConnectionReuseCount=Integer.parseInt(config.getValue(Property.TSDB_READ_CONNECTION_REUSE_COUNT.getName(),
				Property.TSDB_READ_CONNECTION_REUSE_COUNT.getDefaultValue()));

		_readEndPoints = Arrays.asList(config.getValue(Property.TSD_ENDPOINT_READ.getName(), Property.TSD_ENDPOINT_READ.getDefaultValue()).split(","));
		requireArgument(_readEndPoints.size() > 0, "At least one TSD read endpoint required");

		for(String readEndPoint : _readEndPoints) {
			requireArgument((readEndPoint != null) && (!readEndPoint.isEmpty()), "Illegal read endpoint URL.");
		}

		_readBackupEndPoints = Arrays.asList(config.getValue(Property.TSD_ENDPOINT_BACKUP_READ.getName(), Property.TSD_ENDPOINT_BACKUP_READ.getDefaultValue()).split(","));

		if(_readBackupEndPoints.size() < _readEndPoints.size()){
			for(int i=0; i< _readEndPoints.size() - _readBackupEndPoints.size();i++)
				_readBackupEndPoints.add("");
		}

		_writeEndpoints = config.getValue(Property.TSD_ENDPOINT_WRITE.getName(), Property.TSD_ENDPOINT_WRITE.getDefaultValue()).split(",");
		requireArgument(_writeEndpoints.length > 0, "At least one TSD write endpoint required");
		RETRY_COUNT = Integer.parseInt(config.getValue(Property.TSD_RETRY_COUNT.getName(),
				Property.TSD_RETRY_COUNT.getDefaultValue()));

		for(String writeEndpoint : _writeEndpoints) {
			requireArgument((writeEndpoint != null) && (!writeEndpoint.isEmpty()), "Illegal write endpoint URL.");
		}

		requireArgument(connCount >= 2, "At least two connections are required.");
		requireArgument(connTimeout >= 1, "Timeout must be greater than 0.");

		_keyUidCache = CacheBuilder.newBuilder()
				.maximumSize(100000)
				.recordStats()
				.expireAfterAccess(1, TimeUnit.HOURS)
				.build();

		try {
			int index = 0;
			for (String readEndpoint : _readEndPoints) {
				_readPortMap.put(readEndpoint, getClient(connCount / 2, connTimeout, socketTimeout, tsdbConnectionReuseCount ,readEndpoint));
				_readBackupEndPointsMap.put(readEndpoint, _readBackupEndPoints.get(index));
				index ++;
			}
			for (String readBackupEndpoint : _readBackupEndPoints) {
				if (!readBackupEndpoint.isEmpty())
					_readPortMap.put(readBackupEndpoint, getClient(connCount / 2, connTimeout, socketTimeout, tsdbConnectionReuseCount, readBackupEndpoint));
			}

			_writeHttpClient = getClient(connCount / 2, connTimeout, socketTimeout,tsdbConnectionReuseCount, _writeEndpoints);

			_roundRobinIterator = constructCyclingIterator(_writeEndpoints);
			_executorService = Executors.newFixedThreadPool(connCount);
		} catch (MalformedURLException ex) {
			throw new SystemException("Error initializing the TSDB HTTP Client.", ex);
		}

	}

	//~ Methods **************************************************************************************************************************************

	/* Used in tests to mock Tsdb clients. */
	void SetTsdbClients(CloseableHttpClient writeHttpClient, CloseableHttpClient readHttpClient) {
		_writeHttpClient = writeHttpClient;
		for(String key : _readPortMap.keySet()) {
			_readPortMap.put(key, readHttpClient);
		}
	}

	Iterator<String> constructCyclingIterator(String[] endpoints) {
		// Return repeating, non-blocking iterator if single element
		if (endpoints.length == 1) {
			return new Iterator<String>() {
				String item = endpoints[0];

				@Override
				public boolean hasNext() {
					return true;
				}

				@Override
				public String next() {
					return item;
				}
			};
		}
		return new Iterator<String>() {
			AtomicInteger index = new AtomicInteger(0);
			List<String> items = Arrays.asList(endpoints);
			IntUnaryOperator updater = (operand) -> {
				if (operand == items.size() - 1) {
					return 0;
				} else {
					return operand + 1;
				}
			};

			@Override
			public boolean hasNext() {
				return true;
			}

			@Override
			public String next() {
				return items.get(index.getAndUpdate(updater));
			}
		};
	}

	/* Generates the metric names for metrics used for annotations. */
	static String toAnnotationKey(String scope, String metric, String type, Map<String, String> tags) {
		int hash = 7;

		hash = 71 * hash + (scope != null ? scope.hashCode() : 0);
		hash = 71 * hash + (metric != null ? metric.hashCode() : 0);
		hash = 71 * hash + (type != null ? type.hashCode() : 0);
		hash = 71 * hash + (tags != null ? tags.hashCode() : 0);

		StringBuilder sb = new StringBuilder();

		sb.append(scope).append(".").append(Integer.toHexString(hash));
		return sb.toString();
	}

	private static String toAnnotationKey(Annotation annotation) {
		String scope = annotation.getScope();
		String metric = annotation.getMetric();
		String type = annotation.getType();
		Map<String, String> tags = annotation.getTags();

		return toAnnotationKey(scope, metric, type, tags);
	}

	/**
	 * We construct OpenTSDB metric name as a combination of Argus metric, scope and namespace as follows:
	 * 			
	 * 			metric(otsdb) = metric(argus)&lt;DELIMITER&gt;scope(argus)&lt;DELIMITER&gt;namespace(argus)
	 * 
	 * @param metric 	The metric
	 * @return OpenTSDB metric name constructed from scope, metric and namespace.
	 */
	public static String constructTSDBMetricName(Metric metric) {
		StringBuilder sb = new StringBuilder();

		sb.append(metric.getMetric()).append(DELIMITER).append(metric.getScope());

		if (metric.getNamespace() != null && !metric.getNamespace().isEmpty()) {
			sb.append(DELIMITER).append(metric.getNamespace());
		}
		return sb.toString();
	}

	/**
	 * Given otsdb metric name, return argus metric.
	 * We construct OpenTSDB metric name as a combination of Argus metric, scope and namespace as follows:
	 * 			
	 * 			metric(otsdb) = metric(argus)&lt;DELIMITER&gt;scope(argus)&lt;DELIMITER&gt;namespace(argus)
	 * 
	 * 
	 * @param tsdbMetricName 	The TSDB metric name
	 * @return Argus metric name.
	 */
	public static String getMetricFromTSDBMetric(String tsdbMetricName) {
		return tsdbMetricName.split(DELIMITER)[0];
	}

	/**
	 * Given otsdb metric name, return argus scope.
	 * We construct OpenTSDB metric name as a combination of Argus metric, scope and namespace as follows:
	 * 			
	 * 			metric(otsdb) = metric(argus)&lt;DELIMITER&gt;scope(argus)&lt;DELIMITER&gt;namespace(argus)
	 * 
	 * 
	 * @param tsdbMetricName	The TSDB metric name
	 * @return Argus scope.
	 */
	public static String getScopeFromTSDBMetric(String tsdbMetricName) {
		return tsdbMetricName.split(DELIMITER)[1];
	}

	/**
	 * Given otsdb metric name, return argus namespace.
	 * We construct OpenTSDB metric name as a combination of Argus metric, scope and namespace as follows:
	 * 			
	 * 			metric(otsdb) = metric(argus)&lt;DELIMITER&gt;scope(argus)&lt;DELIMITER&gt;namespace(argus)
	 * 
	 * 
	 * @param tsdbMetricName	The TSDB metric name
	 * @return Argus namespace. 
	 */
	public static String getNamespaceFromTSDBMetric(String tsdbMetricName) {
		String[] splits = tsdbMetricName.split(DELIMITER);
		return (splits.length == 3) ? splits[2] : null;
	}

	//~ Methods **************************************************************************************************************************************

	/** @see  TSDBService#dispose() */
	@Override
	public void dispose() {
	}

	/** @see  TSDBService#putMetrics(java.util.List) */
	@Override
	public void putMetrics(List<Metric> metrics) {
		requireNotDisposed();
		requireArgument(TSDB_DATAPOINTS_WRITE_MAX_SIZE > 0, "Max Chunk size can not be less than 1");
		requireArgument(metrics != null, "Metrics can not be null");

		String endpoint = _roundRobinIterator.next();
		_logger.debug("Pushing {} metrics to TSDB using endpoint {}.", metrics.size(), endpoint);

		List<Metric> fracturedList = new ArrayList<>();

		for (Metric metric : metrics) {
			if (metric.getDatapoints().size() <= TSDB_DATAPOINTS_WRITE_MAX_SIZE) {
				fracturedList.add(metric);
			} else {
				fracturedList.addAll(fractureMetric(metric));
			}
		}

		try {
			put(fracturedList, endpoint + "/api/put", HttpMethod.POST);
		} catch(IOException ex) {
			_logger.warn("IOException while trying to push metrics", ex);
			_retry(fracturedList, _roundRobinIterator);
		}
	}

	public <T> void _retry(List<T> objects, Iterator<String> endPointIterator) {
		for(int i=0;i<RETRY_COUNT;i++) {
			try {
				String endpoint = endPointIterator.next();
				_logger.info("Retrying using endpoint {}.", endpoint);
				put(objects, endpoint + "/api/put", HttpMethod.POST);
				return;
			} catch(IOException ex) {
				_logger.warn("IOException while trying to push data. We will retry for {} more times",RETRY_COUNT-i);
			}
		}

		_logger.error("Retried for {} times and we still failed. Dropping this chunk of data.", RETRY_COUNT);

	}

	/** @see  TSDBService#putAnnotations(java.util.List) */
	@Override
	public void putAnnotations(List<Annotation> annotations) {
		requireNotDisposed();
		if (annotations != null) {

			// Dictionary of annotation key and the annotation
			Map<String, Annotation> keyAnnotationMap = new HashMap<>();

			List<AnnotationWrapper> wrappers = new ArrayList<>();

			for(Annotation annotation : annotations) {
				String key = toAnnotationKey(annotation);
				String uid = _keyUidCache.getIfPresent(key);

				if(StringUtils.isEmpty(uid)) {
					// Not in cache, populate keyAnnotationMap so that we can query TSDB.
					keyAnnotationMap.put(key, annotation);
				} else {
					// If we find uid in the cache, we construct the AnnotationWrapper object.
					AnnotationWrapper wrapper = new AnnotationWrapper(uid, annotation);
					wrappers.add(wrapper);
				}
			}

			// query TSDB to get uids for annotations.
			Map<String, String> keyUidMap = getUidMapFromTsdb(keyAnnotationMap);

			for(Map.Entry<String, String> keyUidEntry : keyUidMap.entrySet()) {

				// We add new uids to the cache and create AnnotationWrapper objects.
				_keyUidCache.put(keyUidEntry.getKey(), keyUidEntry.getValue());
				AnnotationWrapper wrapper = new AnnotationWrapper(keyUidEntry.getValue(),
						keyAnnotationMap.get(keyUidEntry.getKey()));

				wrappers.add(wrapper);
			}

			_logger.info("putAnnotations CacheStats hitCount {} requestCount {} " +
							"evictionCount {} annotationsCount {}",
					_keyUidCache.stats().hitCount(), _keyUidCache.stats().requestCount(),
					_keyUidCache.stats().evictionCount(), annotations.size());

			String endpoint = _roundRobinIterator.next();

			try {
				put(wrappers, endpoint + "/api/annotation/bulk", HttpMethod.POST);
			} catch(IOException ex) {
				_logger.warn("IOException while trying to push annotations", ex);
				_retry(wrappers, _roundRobinIterator);
			}
		}
	}

	private Map<String, String> getUidMapFromTsdb(Map<String, Annotation> keyAnnotationMap) {

		List<MetricQuery> queries = new ArrayList<>();
		List<Metric> metrics = new ArrayList<>();

		Map<Long, Double> datapoints = new HashMap<>();
		datapoints.put(1L, 0.0);

		for(Map.Entry<String, Annotation> annotationEntry : keyAnnotationMap.entrySet()) {
			String annotationKey = annotationEntry.getKey();
			Annotation annotation = annotationEntry.getValue();
			String type = annotation.getType();
			Map<String, String> tags = annotation.getTags();

			Metric metric = new Metric(annotationKey, type);
			metric.setDatapoints(datapoints);
			metric.setTags(tags);
			metrics.add(metric);

			MetricQuery query = new MetricQuery(annotationKey, type, tags, 0L, 2L);
			queries.add(query);
		}

		long backOff = 1000L;

		for (int attempts = 0; attempts < 3; attempts++) {

			putMetrics(metrics);
			try {
				Thread.sleep(backOff);
			} catch (InterruptedException ex) {
				// We continue if interrupted.
			}

			try {

				Map<String, String> keyUidMap = new HashMap<>();
				Map<MetricQuery, List<Metric>> metricMap = getMetrics(queries);
				for(List<Metric> getMetrics : metricMap.values()) {
					Metric firstMetric = getMetrics.get(0);
					keyUidMap.put(firstMetric.getScope(), firstMetric.getUid());
				}

				return keyUidMap;

			} catch (Exception e) {
				backOff += 1000L;
			}
		}

		throw new SystemException("Failed to create new annotation metric.");
	}

	private ObjectMapper getMapper() {
		ObjectMapper mapper = new ObjectMapper();
		SimpleModule module = new SimpleModule();

		module.addSerializer(Metric.class, new MetricTransform.Serializer());
		module.addDeserializer(ResultSet.class, new MetricTransform.MetricListDeserializer());
		module.addSerializer(AnnotationWrapper.class, new AnnotationTransform.Serializer());
		module.addDeserializer(AnnotationWrappers.class, new AnnotationTransform.Deserializer());
		module.addSerializer(MetricQuery.class, new MetricQueryTransform.Serializer());
		mapper.registerModule(module);
		return mapper;
	}

	/* gets objects in chunks.
	public Map<MetricQuery, List<Metric>> get(List<MetricQuery> queries) {
		requireNotDisposed();
		requireArgument(queries != null, "Metric Queries cannot be null.");

		Map<MetricQuery, List<Metric>> metricsMap = new HashMap<>();
		Map<MetricQuery, Future<List<Metric>>> futures = new HashMap<>();
		Map<MetricQuery, Long> queryStartExecutionTime = new HashMap<>();
		// Only one endpoint for AbstractTSDBService
		String requestUrl = _readEndPoints.get(0) + "/api/query";

		int chunkEnd = 0;

		while (chunkEnd < queries.size()) {
			int chunkStart = chunkEnd;

			long start = System.currentTimeMillis();

			chunkEnd = Math.min(queries.size(), chunkStart + CHUNK_SIZE);

			String requestBody = fromEntity(queries.subList(chunkStart, chunkEnd));

			_logger.info("requestUrl {} requestBody {}", requestUrl, requestBody);

			HttpResponse response = executeHttpRequest(HttpMethod.POST, requestUrl, _readPortMap.get(requestUrl), new StringEntity(requestBody));
			List<Metric> metrics = toEntity(extractResponse(response), new TypeReference<ResultSet>() { }).getMetrics();
		}

		for (Map.Entry<MetricQuery, Future<List<Metric>>> entry : futures.entrySet()) {
			try {
				List<Metric> m = entry.getValue().get();
				List<Metric> metrics = new ArrayList<>();

				if (m != null) {
					for (Metric metric : m) {
						if (metric != null) {
							metric.setQuery(entry.getKey());
							metrics.add(metric);
						}
					}
				}

				instrumentQueryLatency(_monitorService, entry.getKey(), queryStartExecutionTime.get(entry.getKey()), "metrics");
				metricsMap.put(entry.getKey(), metrics);
			} catch (InterruptedException | ExecutionException e) {
				throw new SystemException("Failed to get metrics. The query was: " + entry.getKey() + "\\n", e);
			}
		}
		_logger.info("Time to get Metrics = " + (System.currentTimeMillis() - start));
		return metricsMap;
	}
	*/

	/* Writes objects in chunks. */
	private <T> void put(List<T> objects, String endpoint, HttpMethod method) throws IOException {
		if (objects != null) {
			int chunkEnd = 0;

			while (chunkEnd < objects.size()) {
				int chunkStart = chunkEnd;

				long start = System.currentTimeMillis();

				chunkEnd = Math.min(objects.size(), chunkStart + CHUNK_SIZE);
				try {

					String createBody = fromEntity(objects.subList(chunkStart, chunkEnd));

					StringEntity entity = new StringEntity(createBody);

					if (endpoint.contains("put")) {
						_logger.info("createUrl {} createBody {}", endpoint, createBody);
					}

					HttpResponse response = executeHttpRequest(method, endpoint, _writeHttpClient, entity);

					extractResponse(response);

					_logger.info("Time to put Metrics = " + (System.currentTimeMillis() - start));
				} catch (UnsupportedEncodingException ex) {
					throw new SystemException("Error posting data", ex);
				}
			}
		}
	}

	/* Helper to create the read and write clients. */
	protected CloseableHttpClient getClient(int connCount, int connTimeout, int socketTimeout, int connectionReuseCount, String...endpoints) throws MalformedURLException {
		PoolingHttpClientConnectionManager connMgr = new PoolingHttpClientConnectionManager();
		connMgr.setMaxTotal(connCount);

		for(String endpoint : endpoints) {
			URL url = new URL(endpoint);
			int port = url.getPort();
			requireArgument(port != -1, "TSDB endpoint must include explicit port.");
			HttpHost host = new HttpHost(url.getHost(),	url.getPort());
			connMgr.setMaxPerRoute(new HttpRoute(host), connCount / endpoints.length);
		}

		RequestConfig reqConfig = RequestConfig.custom().setConnectionRequestTimeout(connTimeout).setConnectTimeout(connTimeout).setSocketTimeout(
				socketTimeout).build();
		if(connectionReuseCount>0) {
			return HttpClients.custom().setConnectionManager(connMgr).setConnectionReuseStrategy(new TSDBReadConnectionReuseStrategy(connectionReuseCount)).setDefaultRequestConfig(reqConfig).build();
		}else {
			return HttpClients.custom().setConnectionManager(connMgr).setDefaultRequestConfig(reqConfig).build();
		}
	}

	/*
	 * Helper method to extract the HTTP response and close the client connection. Please note that <tt>ByteArrayOutputStreams</tt> do not require to
	 * be closed.
	 */
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

	/* Helper method to convert JSON String representation to the corresponding Java entity. */
	protected <T> T toEntity(String content, TypeReference<T> type) {
		try {
			return _mapper.readValue(content, type);
		} catch (IOException ex) {
			throw new SystemException(ex);
		}
	}

	/* Helper method to convert a Java entity to a JSON string. */
	protected <T> String fromEntity(T type) {
		try {
			return _mapper.writeValueAsString(type);
		} catch (JsonProcessingException ex) {
			throw new SystemException(ex);
		}
	}	

	/* Helper to process the response. */
	protected String extractResponse(HttpResponse response) {
		if (response != null) {
			int status = response.getStatusLine().getStatusCode();

			if ((status < HttpStatus.SC_OK) || (status >= HttpStatus.SC_MULTIPLE_CHOICES)) {
				Map<String, Map<String, String>> errorMap = toEntity(extractStringResponse(response),
						new TypeReference<Map<String, Map<String, String>>>() { });
				if (errorMap != null) {
					throw new SystemException("Error : " + errorMap.toString());
				} else {
					throw new SystemException("Status code: " + status + " .  Unknown error occurred. ");
				}
			} else {
				return extractStringResponse(response);
			}
		}
		return null;
	}

	/* Execute a request given by type requestType. */
	protected HttpResponse executeHttpRequest(HttpMethod requestType, String url,  CloseableHttpClient client, StringEntity entity) throws IOException {

		HttpResponse httpResponse = null;

		if (entity != null) {
			entity.setContentType("application/json");
		}
		try {
			switch (requestType) {
			case POST:

				HttpPost post = new HttpPost(url);

				post.setEntity(entity);
				httpResponse = client.execute(post);
				break;
			case GET:

				HttpGet httpGet = new HttpGet(url);

				httpResponse = client.execute(httpGet);
				break;
			case DELETE:

				HttpDelete httpDelete = new HttpDelete(url);

				httpResponse = client.execute(httpDelete);
				break;
			case PUT:

				HttpPut httpput = new HttpPut(url);

				httpput.setEntity(entity);
				httpResponse = client.execute(httpput);
				break;
			default:
				throw new MethodNotSupportedException(requestType.toString());
			}
		} catch (MethodNotSupportedException ex) {
			throw new SystemException(ex);
		}
		return httpResponse;
	}

	/**
	 * This method partitions data points of a given metric.
	 *
	 * @param   metric  - metric whose data points to be fractured
	 *
	 * @return  - list of chunks in descending order so that the last chunk will be of size of "firstChunkSize"
	 */
	private List<Metric> fractureMetric(Metric metric) {
		List<Metric> result = new ArrayList<>();

		if (metric.getDatapoints().size() <= TSDB_DATAPOINTS_WRITE_MAX_SIZE) {
			result.add(metric);
			return result;
		}

		Metric tempMetric = new Metric(metric);
		Map<Long, Double> dataPoints = new LinkedHashMap<>();
		int tempChunkSize = TSDB_DATAPOINTS_WRITE_MAX_SIZE;

		for (Map.Entry<Long, Double> dataPoint : metric.getDatapoints().entrySet()) {
			dataPoints.put(dataPoint.getKey(), dataPoint.getValue());
			if (--tempChunkSize == 0) {
				tempMetric.setDatapoints(dataPoints);
				result.add(tempMetric);
				tempMetric = new Metric(metric);
				tempChunkSize = TSDB_DATAPOINTS_WRITE_MAX_SIZE;
				dataPoints = new LinkedHashMap<>();
			}
		}
		if (!dataPoints.isEmpty()) {
			tempMetric.setDatapoints(dataPoints);
			result.add(tempMetric);
		}
		return result;
	}

	//~ Enums ****************************************************************************************************************************************

	/**
	 * Enumerates the implementation specific configuration properties.
	 *
	 * @author  Tom Valine (tvaline@salesforce.com)
	 */
	public enum Property {

		/** The TSDB read endpoint. */
		TSD_ENDPOINT_READ("service.property.tsdb.endpoint.read", "http://localhost:4466,http://localhost:4467"),
		/** The TSDB write endpoint. */
		TSD_ENDPOINT_WRITE("service.property.tsdb.endpoint.write", "http://localhost:4477,http://localhost:4488"),
		/** The TSDB connection timeout. */
		TSD_ENDPOINT_CONNECTION_TIMEOUT("service.property.tsdb.endpoint.connection.timeout", "10000"),
		/** The TSDB socket connection timeout. */
		TSD_ENDPOINT_SOCKET_TIMEOUT("service.property.tsdb.endpoint.socket.timeout", "10000"),
		/** The TSDB connection count. */
		TSD_CONNECTION_COUNT("service.property.tsdb.connection.count", "2"),
		TSD_RETRY_COUNT("service.property.tsdb.retry.count", "3"),
		/** The TSDB backup read endpoint. */
		TSD_ENDPOINT_BACKUP_READ("service.property.tsdb.endpoint.backup.read", "http://localhost:4466,http://localhost:4467"),	
		TSDB_READ_CONNECTION_REUSE_COUNT("service.property.tsdb.read.connection.reuse.count", "2000");

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

	protected void instrumentQueryLatency(final MonitorService monitorService, final AnnotationQuery query, final long start,
			final String measurementType) {
		String timeWindow = QueryTimeWindow.getWindow(query.getEndTimestamp() - query.getStartTimestamp());
		Map<String, String> tags = new HashMap<String, String>();
		tags.put("type", measurementType);
		tags.put("timeWindow", timeWindow);
		tags.put("cached", "false");
		monitorService.modifyCustomCounter(QUERY_LATENCY_COUNTER, (System.currentTimeMillis() - start), tags);
		monitorService.modifyCustomCounter(QUERY_COUNT_COUNTER, 1, tags);
	}

	/**
	 * Enumeration of supported HTTP methods.
	 *
	 * @author  Tom Valine (tvaline@salesforce.com), Bhinav Sura (bhinav.sura@salesforce.com)
	 */
	protected enum HttpMethod {

		/** POST operation. */
		POST,
		/** GET operation. */
		GET,
		/** DELETE operation. */
		DELETE,
		/** PUT operation. */
		PUT;
	}

	//~ Inner Classes ********************************************************************************************************************************

	/**
	 * Helper entity to wrap multiple Annotation entities into a form closer to the TSDB metric form.
	 *
	 * @author  Tom Valine (tvaline@salesforce.com), Bhinav Sura (bhinav.sura@salesforce.com)
	 */
	static class AnnotationWrapper {

		String _uid;
		Annotation _annotation;

		AnnotationWrapper(String uid, Annotation annotation) {
			_uid = uid;
			_annotation = annotation;
		}

		Annotation getAnnotation() {
			return _annotation;
		}

		String getUid() { return _uid; }

		String getAnnotationKey() {
			return _annotation.getSource() + "." + _annotation.getId();
		}

		public void setUid(String uid) {
			_uid = uid;
		}

		public void setAnnotation(Annotation annotation) {
			_annotation = annotation;
		}
	}

	/**
	 * Helper entity to facilitate de-serialization.
	 *
	 * @author  Tom Valine (tvaline@salesforce.com), Bhinav Sura (bhinav.sura@salesforce.com)
	 */
	static class AnnotationWrappers extends ArrayList<AnnotationWrapper> {

		/** Comment for <code>serialVersionUID.</code> */
		private static final long serialVersionUID = 1L;
	}

	/**
	 * Helper class used to parallelize query execution.
	 *
	 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
	 */
	class QueryWorker implements Callable<List<Metric>> {

		private final String _requestUrl;
		private final String _requestEndPoint;
		private final String _requestBody;

		/**
		 * Creates a new QueryWorker object.
		 *
		 * @param  requestUrl  The URL to which the request will be issued.
		 * @param requestEndPoint The endpoint to which the request will be issued.
		 * @param  requestBody The request body. 
		 */
		public QueryWorker(String requestUrl,  String requestEndPoint, String requestBody) {
			this._requestUrl = requestUrl;
			this._requestEndPoint = requestEndPoint;
			this._requestBody = requestBody;
		}

		@Override
		public List<Metric> call() {
			_logger.info("TSDB requestUrl {} requestBody {}", _requestUrl, _requestBody);

			try {
				HttpResponse response = executeHttpRequest(HttpMethod.POST, _requestUrl, _readPortMap.get(_requestEndPoint), new StringEntity(_requestBody));
				List<Metric> metrics = toEntity(extractResponse(response), new TypeReference<ResultSet>() { }).getMetrics();
				return metrics;
			} catch (IOException e) {
				throw new SystemException("Failed to retrieve metrics.", e);
			}
		}
	}

	@Override
	public Properties getServiceProperties() {
		throw new UnsupportedOperationException("This method should be overriden by a specific implementation.");
	}

	@Override
	public Map<MetricQuery, List<Metric>> getMetrics(List<MetricQuery> queries) {
		throw new UnsupportedOperationException("This method should be overriden by a specific implementation.");
	}

	@Override
	public List<Annotation> getAnnotations(List<AnnotationQuery> queries) {
		throw new UnsupportedOperationException("This method should be overriden by a specific implementation.");
	}
	/**
	 * Used to close http connections after reusing the same connection for certain number of times 
	 * @author rsarkapally
	 *
	 */
	class TSDBReadConnectionReuseStrategy implements ConnectionReuseStrategy{
		int connectionReuseCount;
		AtomicInteger numOfTimesReused = new AtomicInteger(1);
		public TSDBReadConnectionReuseStrategy(int connectionReuseCount) {
			this.connectionReuseCount=connectionReuseCount;
		}

		@Override
		public boolean keepAlive(HttpResponse response, HttpContext context) {
			HttpClientContext httpContext = (HttpClientContext) context;
			_logger.debug("http connection {} reused for {} times", httpContext.getConnection(), httpContext.getConnection().getMetrics().getRequestCount()); 
			if (numOfTimesReused.getAndIncrement() % connectionReuseCount == 0) {
				numOfTimesReused.set(1);
				return false;
			}
			return true;
		}
	}
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

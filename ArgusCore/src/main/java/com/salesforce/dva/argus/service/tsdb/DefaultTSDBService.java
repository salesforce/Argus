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
import java.text.MessageFormat;
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
import java.util.Map.Entry;
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
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.collect.Iterables;
import com.google.inject.Inject;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;

/**
 * The default implementation of the TSDBService.
 *
 * @author  Tom Valine (tvaline@salesforce.com), Bhinav Sura (bhinav.sura@salesforce.com)
 */
public class DefaultTSDBService extends DefaultService implements TSDBService {

	//~ Static fields/initializers *******************************************************************************************************************

	private static final int CHUNK_SIZE = 50;
	private static final int TSDB_DATAPOINTS_WRITE_MAX_SIZE = 100;
	private static final String QUERY_LATENCY_COUNTER = "query.latency";
	private static final String QUERY_COUNT_COUNTER = "query.count";
	static final String DELIMITER = "-__-";
	private static final long TIME_FEDERATE_LIMIT_MILLIS = 86400000L;

	//~ Instance fields ******************************************************************************************************************************

	private final ObjectMapper _mapper;
	protected final Logger _logger = LoggerFactory.getLogger(getClass());
	private final Set<String> _writeEndpoints;
	private final Map<String, CloseableHttpClient> _writeHttpClients = new HashMap<>();
	private final Map<String, CloseableHttpClient> _readPortMap = new HashMap<>();
	private final List<String> _readEndPoints;
	private final List<String> _readBackupEndPoints;

	/** Round robin iterator for write endpoints. 
	 * We will cycle through this iterator to select an endpoint from the set of available endpoints  */
	private final Iterator<String> _roundRobinIterator;

	private final ExecutorService _executorService;
	private final MonitorService _monitorService;

	//~ Constructors *********************************************************************************************************************************

	/**
	 * Creates a new Default TSDB Service having an equal number of read and write routes.
	 *
	 * @param   config               The system _configuration used to configure the service.
	 * @param   monitorService       The monitor service used to collect query time window counters. Cannot be null.
	 *
	 * @throws  SystemException  If an error occurs configuring the service.
	 */
	@Inject
	public DefaultTSDBService(SystemConfiguration config, MonitorService monitorService) {
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

		_readEndPoints = Arrays.asList(config.getValue(Property.TSD_ENDPOINT_READ.getName(), Property.TSD_ENDPOINT_READ.getDefaultValue()).split(","));
		for(String readEndPoint : _readEndPoints) {
			requireArgument((readEndPoint != null) && (!readEndPoint.isEmpty()), "Illegal read endpoint URL.");
		}

		_readBackupEndPoints = Arrays.asList(config.getValue(Property.TSD_ENDPOINT_BACKUP_READ.getName(), Property.TSD_ENDPOINT_BACKUP_READ.getDefaultValue()).split(","));

		_writeEndpoints = new HashSet<>(Arrays.asList(config.getValue(Property.TSD_ENDPOINT_WRITE.getName(), Property.TSD_ENDPOINT_WRITE.getDefaultValue()).split(",")));

		for(String writeEndpoint : _writeEndpoints) {
			requireArgument((writeEndpoint != null) && (!writeEndpoint.isEmpty()), "Illegal write endpoint URL.");
		}

		requireArgument(connCount >= 2, "At least two connections are required.");
		requireArgument(connTimeout >= 1, "Timeout must be greater than 0.");

		try {
			for (String readEndpoint : _readEndPoints) {
				_readPortMap.put(readEndpoint, getClient(readEndpoint, connCount / 2, connTimeout, socketTimeout));
			}
			for (String readBackupEndpoint : _readBackupEndPoints) {
				if (!readBackupEndpoint.isEmpty())
					_readPortMap.put(readBackupEndpoint, getClient(readBackupEndpoint, connCount / 2, connTimeout, socketTimeout));
			}

			for(String writeEndpoint : _writeEndpoints) {
				CloseableHttpClient writeHttpClient = getClient(writeEndpoint, connCount / 2, connTimeout, socketTimeout);
				_writeHttpClients.put(writeEndpoint, writeHttpClient);
			}

			_roundRobinIterator = Iterables.cycle(_writeEndpoints).iterator();
			_executorService = Executors.newFixedThreadPool(connCount);
		} catch (MalformedURLException ex) {
			throw new SystemException("Error initializing the TSDB HTTP Client.", ex);
		}

	}

	//~ Methods **************************************************************************************************************************************

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
	 * 			metric(otsdb) = metric(argus)<DELIMITER>scope(argus)<DELIMITER>namespace(argus)
	 * 
	 * @param metric
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
	 * 			metric(otsdb) = metric(argus)<DELIMITER>scope(argus)<DELIMITER>namespace(argus)
	 * 
	 * 
	 * @param tsdbMetricName
	 * @return Argus metric name.
	 */
	public static String getMetricFromTSDBMetric(String tsdbMetricName) {
		return tsdbMetricName.split(DELIMITER)[0];
	}

	/**
	 * Given otsdb metric name, return argus scope.
	 * We construct OpenTSDB metric name as a combination of Argus metric, scope and namespace as follows:
	 * 			
	 * 			metric(otsdb) = metric(argus)<DELIMITER>scope(argus)<DELIMITER>namespace(argus)
	 * 
	 * 
	 * @param tsdbMetricName
	 * @return Argus scope.
	 */
	public static String getScopeFromTSDBMetric(String tsdbMetricName) {
		return tsdbMetricName.split(DELIMITER)[1];
	}

	/**
	 * Given otsdb metric name, return argus namespace.
	 * We construct OpenTSDB metric name as a combination of Argus metric, scope and namespace as follows:
	 * 			
	 * 			metric(otsdb) = metric(argus)<DELIMITER>scope(argus)<DELIMITER>namespace(argus)
	 * 
	 * 
	 * @param tsdbMetricName
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
		super.dispose();
		List<CloseableHttpClient> clients = new ArrayList<>();
		clients.addAll(_readPortMap.values());
		clients.addAll(_writeHttpClients.values());
		for (CloseableHttpClient client : clients) {
			try {
				client.close();
			} catch (Exception ex) {
				_logger.warn("A TSDB HTTP client failed to shutdown properly.", ex);
			}
		}
		_executorService.shutdownNow();
		try {
			_executorService.awaitTermination(10, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			_logger.debug("Await Termination Interrupted", e);
		}
	}

	/** @see  TSDBService#putMetrics(java.util.List) */
	@Override
	public void putMetrics(List<Metric> metrics) {
		requireNotDisposed();
		requireArgument(TSDB_DATAPOINTS_WRITE_MAX_SIZE > 0, "Max Chunk size can not be less than 1");
		requireArgument(metrics != null, "Metrics can not be null");

		String endpoint = _roundRobinIterator.next();
		_logger.info("Pushing {} metrics to TSDB using endpoint {}.", metrics.size(), endpoint);

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
			List<String> copy = new ArrayList<>(_writeEndpoints);
			copy.remove(endpoint);
			_retry(fracturedList, copy);
		}
	}

	public <T> void _retry(List<T> objects, List<String> endpointsToRetryWith) {

		for(String endpoint : endpointsToRetryWith) {
			try {
				_logger.info("Retrying using endpoint {}.", endpoint);
				put(objects, endpoint + "/api/put", HttpMethod.POST);
				return;
			} catch(IOException ex) {
				_logger.warn("IOException while trying to push data", ex);
			}
		}

		_logger.error("Tried all available endpoints to push data and we still failed. Dropping this chunk of data.");

	}

	/** @see  TSDBService#getMetrics(java.util.List) 
	 * - Federation occurs for multiple endpoints (it uses backup if primary endpoint is down)
	 * - Federation occurs for large range queries into smaller range sub queries
	 * */
	@Override
	public Map<MetricQuery, List<Metric>> getMetrics(List<MetricQuery> queries) {
		requireNotDisposed();
		requireArgument(queries != null, "Metric Queries cannot be null.");
		_logger.trace("Active Threads in the pool = " + ((ThreadPoolExecutor) _executorService).getActiveCount());

		Map<MetricQuery, Long> queryStartExecutionTime = new HashMap<>();
		Map<MetricQuery, List<MetricQuery>> mapQuerySubQueries = new HashMap<>();

		List<MetricQuery> queriesSplit = timeFederateQueries(queries, queryStartExecutionTime, mapQuerySubQueries);
		Map<MetricQuery, List<Future<List<Metric>>>> queryFuturesMap = endPointFederateQueries(queriesSplit);
		Map<MetricQuery, List<Metric>> subQueryMetricsMap = endPointMergeMetrics(queryFuturesMap);
		return timeMergeMetrics(queries, mapQuerySubQueries, subQueryMetricsMap, queryStartExecutionTime);
	}

	/** @see  TSDBService#putAnnotations(java.util.List) */
	@Override
	public void putAnnotations(List<Annotation> annotations) {
		requireNotDisposed();
		if (annotations != null) {
			List<AnnotationWrapper> wrappers = reconcileWrappers(toAnnotationWrappers(annotations));
			String endpoint = _roundRobinIterator.next();

			try {
				put(wrappers, endpoint + "/api/annotation/bulk", HttpMethod.POST);
			} catch(IOException ex) {
				_logger.warn("IOException while trying to push annotations", ex);
				List<String> copy = new ArrayList<>(_writeEndpoints);
				copy.remove(endpoint);
				_retry(wrappers, copy);
			}
		}
	}

	/** @see  TSDBService#getAnnotations(java.util.List)
	 * - Federation occurs for multiple endpoints (it uses backup if primary endpoint is down)
	 * - Federation occurs for large range queries into smaller range sub queries 
	 * */
	@Override
	public List<Annotation> getAnnotations(List<AnnotationQuery> queries) {
		requireNotDisposed();
		requireArgument(queries != null, "Annotation queries cannot be null.");

		List<Annotation> annotations = new ArrayList<>();

		for (AnnotationQuery query : queries) {
			long start = System.currentTimeMillis();
			int index = 0;
			for (String readEndPoint : _readEndPoints) {
				String pattern = readEndPoint + "/api/query?{0}";
				String requestUrl = MessageFormat.format(pattern, query.toString());
				List<AnnotationWrapper> wrappers = null;
				try {
					HttpResponse response = executeHttpRequest(HttpMethod.GET, requestUrl,  _readPortMap.get(readEndPoint), null);
					wrappers = toEntity(extractResponse(response), new TypeReference<AnnotationWrappers>() {
					});
				} catch (Exception ex) {
					_logger.warn("Failed to get annotations from TSDB. Reason: " + ex.getMessage());
					try {
						if (!_readBackupEndPoints.get(index).isEmpty()) {
							_logger.warn("Trying to read from Backup endpoint");
							pattern = _readBackupEndPoints.get(index) + "/api/query?{0}";
							requestUrl = MessageFormat.format(pattern, query.toString());							
							HttpResponse response = executeHttpRequest(HttpMethod.GET, requestUrl, _readPortMap.get( _readBackupEndPoints.get(index)), null);
							wrappers = toEntity(extractResponse(response), new TypeReference<AnnotationWrappers>() {
							});
						}
					} catch (Exception e) {
						_logger.warn("Failed to get annotations from Backup TSDB. Reason: " + e.getMessage());
						index++;
						continue;
					}					
				} 

				index++;

				if (wrappers != null) {
					for (AnnotationWrapper wrapper : wrappers) {
						for (Annotation existing : wrapper.getAnnotations()) {
							String source = existing.getSource();
							String id = existing.getId();
							String type = query.getType();
							String scope = query.getScope();
							String metric = query.getMetric();
							Long timestamp = existing.getTimestamp();
							Annotation updated = new Annotation(source, id, type, scope, metric, timestamp);

							updated.setFields(existing.getFields());
							updated.setTags(query.getTags());
							annotations.add(updated);
						}
					}
				}
			}
			instrumentQueryLatency(_monitorService, query, start, "annotations");
		}
		return annotations;
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

	/* Writes objects in chunks. */
	private <T> void put(List<T> objects, String endpoint, HttpMethod method) throws IOException {
		if (objects != null) {
			int chunkEnd = 0;

			while (chunkEnd < objects.size()) {
				int chunkStart = chunkEnd;

				chunkEnd = Math.min(objects.size(), chunkStart + CHUNK_SIZE);
				try {
					StringEntity entity = new StringEntity(fromEntity(objects.subList(chunkStart, chunkEnd)));
					HttpResponse response = executeHttpRequest(method, endpoint, _chooseWriteEndpoint(endpoint),entity);

					extractResponse(response);
				} catch (UnsupportedEncodingException ex) {
					throw new SystemException("Error posting data", ex);
				}
			}
		}
	}

	/* Helper to create the read and write clients. */
	private CloseableHttpClient getClient(String endpoint, int connCount, int connTimeout, int socketTimeout) throws MalformedURLException {
		URL url = new URL(endpoint);
		int port = url.getPort();

		requireArgument(port != -1, "Read endpoint must include explicit port.");

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

	/* Converts a list of annotations into a list of annotation wrappers for use in serialization.  Resulting list is sorted by target annotation
	 * scope and timestamp.
	 */
	List<AnnotationWrapper> toAnnotationWrappers(List<Annotation> annotations) {
		Map<String, Set<Annotation>> sortedByUid = new TreeMap<>();

		for (Annotation annotation : annotations) {
			String key = toAnnotationKey(annotation);
			Set<Annotation> items = sortedByUid.get(key);

			if (items == null) {
				items = new HashSet<>();
				sortedByUid.put(key, items);
			}
			items.add(annotation);
		}

		List<AnnotationWrapper> result = new LinkedList<>();

		for (Set<Annotation> items : sortedByUid.values()) {
			Map<Long, Set<Annotation>> sortedByUidAndTimestamp = new HashMap<>();

			for (Annotation item : items) {
				Long timestamp = item.getTimestamp();
				Set<Annotation> itemsByTimestamp = sortedByUidAndTimestamp.get(timestamp);

				if (itemsByTimestamp == null) {
					itemsByTimestamp = new HashSet<>();
					sortedByUidAndTimestamp.put(timestamp, itemsByTimestamp);
				}
				itemsByTimestamp.add(item);
			}
			for (Set<Annotation> itemsByTimestamp : sortedByUidAndTimestamp.values()) {
				result.add(new AnnotationWrapper(itemsByTimestamp, this));
			}
		}
		return result;
	}

	/* This method should merge and update existing annotations rather than adding a duplicate annotation. */
	private List<AnnotationWrapper> reconcileWrappers(List<AnnotationWrapper> wrappers) {
		_logger.debug("Reconciling and merging of duplicate annotations is not yet implemented.");
		return wrappers;
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
	private <T> T toEntity(String content, TypeReference<T> type) {
		try {
			return _mapper.readValue(content, type);
		} catch (IOException ex) {
			throw new SystemException(ex);
		}
	}

	/* Helper method to convert a Java entity to a JSON string. */
	private <T> String fromEntity(T type) {
		try {
			return _mapper.writeValueAsString(type);
		} catch (JsonProcessingException ex) {
			throw new SystemException(ex);
		}
	}

	/* Helper to process the response. */
	String extractResponse(HttpResponse response) {
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

	private CloseableHttpClient _chooseWriteEndpoint(String url) {
		String defaultWriteEndpoint = "";
		for(String writeEndpoint : _writeEndpoints) {
			defaultWriteEndpoint = writeEndpoint;
			if(url.startsWith(writeEndpoint)) {
				return _writeHttpClients.get(writeEndpoint);
			}
		}

		return _writeHttpClients.get(defaultWriteEndpoint);
	}

	/* Execute a request given by type requestType. */
	private HttpResponse executeHttpRequest(HttpMethod requestType, String url, CloseableHttpClient client, StringEntity entity) throws IOException {
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

	/* Federate query to list of Read TSDB endpoints by using an executor service future */
	private Map<MetricQuery, List<Future<List<Metric>>>> endPointFederateQueries(List<MetricQuery> queries) {
		Map<MetricQuery, List<Future<List<Metric>>>> queryFuturesMap = new HashMap<>();
		for (MetricQuery query : queries) {
			String requestBody = fromEntity(query);
			List<Future<List<Metric>>> futures = new ArrayList<>();
			for (String readEndpoint : _readEndPoints) {
				String requestUrl = readEndpoint + "/api/query";
				futures.add(_executorService.submit(new QueryWorker(requestUrl, readEndpoint, requestBody)));
			}
			queryFuturesMap.put(query, futures);
		}
		return queryFuturesMap;
	}

	/* Merge metrics from different endpoints for each query, by reading from future */
	private Map<MetricQuery, List<Metric>> endPointMergeMetrics(Map<MetricQuery, List<Future<List<Metric>>>> queryFuturesMap) {
		Map<MetricQuery, List<Metric>> subQueryMetricsMap = new HashMap<>();
		for (Entry<MetricQuery, List<Future<List<Metric>>>> entry : queryFuturesMap.entrySet()) {
			Map<String, Metric> metricMergeMap = new HashMap<>();
			List<Future<List<Metric>>> futures = entry.getValue();
			List<Metric> metrics = new ArrayList<>();
			String metricIdentifier;
			int index = 0;

			for (Future<List<Metric>> future : futures) {
				List<Metric> m = null;
				try {
					m = future.get();
				} catch (InterruptedException | ExecutionException e) {
					_logger.warn("Failed to get metrics from TSDB. Reason: " + e.getMessage());
					try {
						if (!_readBackupEndPoints.get(index).isEmpty()) {
							_logger.warn("Trying to read from Backup endpoint");
							m = new QueryWorker(_readBackupEndPoints.get(index) + "/api/query",
									_readBackupEndPoints.get(index), fromEntity(entry.getKey())).call();
						}
					} catch (Exception ex) {
						_logger.warn("Failed to get metrics from Backup TSDB. Reason: " + ex.getMessage());
						index++;
						continue;
					}
				}

				index++;

				if (m != null) {
					for (Metric metric : m) {
						if (metric != null) {
							metricIdentifier = metric.getIdentifier();
							Metric finalMetric = metricMergeMap.get(metricIdentifier);
							if (finalMetric == null) {
								metric.setQuery(entry.getKey());
								metricMergeMap.put(metricIdentifier, metric);
							} else {
								finalMetric.addDatapoints(metric.getDatapoints());
							}
						}
					}
				}
			}

			for (Metric finalMetric : metricMergeMap.values()) {
				metrics.add(finalMetric);
			}

			subQueryMetricsMap.put(entry.getKey(), metrics);
		}
		return subQueryMetricsMap;
	}

	/* Time based federation. Split large range queries into smaller queries */
	private List<MetricQuery> timeFederateQueries(List<MetricQuery> queries,
			Map<MetricQuery, Long> queryStartExecutionTime, Map<MetricQuery, List<MetricQuery>> mapQuerySubQuery) {
		List<MetricQuery> queriesSplit = new ArrayList<>();
		for (MetricQuery query : queries) {
			queryStartExecutionTime.put(query, System.currentTimeMillis());
			List<MetricQuery> metricSubQueries = new ArrayList<>();
			if (query.getEndTimestamp() - query.getStartTimestamp() > TIME_FEDERATE_LIMIT_MILLIS) {
				for (long time = query.getStartTimestamp(); time <= query.getEndTimestamp(); time = time + TIME_FEDERATE_LIMIT_MILLIS) {
					MetricQuery mq = new MetricQuery(query);
					mq.setStartTimestamp(time);
					if (time + TIME_FEDERATE_LIMIT_MILLIS > query.getEndTimestamp()) {
						mq.setEndTimestamp(query.getEndTimestamp());
					} else {
						mq.setEndTimestamp(time + TIME_FEDERATE_LIMIT_MILLIS);
					}
					queriesSplit.add(mq);
					metricSubQueries.add(mq);
				}
				mapQuerySubQuery.put(query, metricSubQueries);
			} else {
				metricSubQueries.add(query);
				mapQuerySubQuery.put(query, metricSubQueries);
				queriesSplit.add(query);
			}
		}

		return queriesSplit;
	}

	/* Merge metrics from smaller time queries */
	private Map<MetricQuery, List<Metric>> timeMergeMetrics(List<MetricQuery> queries,
			Map<MetricQuery, List<MetricQuery>> mapQuerySubQueries, Map<MetricQuery, List<Metric>> subQueryMetricsMap,
			Map<MetricQuery, Long> queryStartExecutionTime) {
		Map<MetricQuery, List<Metric>> queryMetricsMap = new HashMap<>();
		Map<String, Metric> metricMergeMap = new HashMap<>();
		String metricIdentifier = null;
		for (MetricQuery query : queries) {
			List<Metric> metrics = new ArrayList<>();
			List<MetricQuery> subQueries = mapQuerySubQueries.get(query);
			for (MetricQuery subQuery : subQueries) {
				List<Metric> metricsFromSubQuery = subQueryMetricsMap.get(subQuery);
				if (metricsFromSubQuery != null) {
					for (Metric metric : metricsFromSubQuery) {
						if (metric != null) {
							metricIdentifier = metric.getIdentifier();
							Metric finalMetric = metricMergeMap.get(metricIdentifier);
							if (finalMetric == null) {
								metric.setQuery(query);
								metricMergeMap.put(metricIdentifier, metric);
							} else {
								finalMetric.addDatapoints(metric.getDatapoints());
							}
						}
					}
				}
			}
			for (Metric finalMetric : metricMergeMap.values()) {
				metrics.add(finalMetric);
			}
			instrumentQueryLatency(_monitorService, query, queryStartExecutionTime.get(query), "metrics");
			queryMetricsMap.put(query, metrics);
		}
		return queryMetricsMap;
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
		/** The TSDB backup read endpoint. */
		TSD_ENDPOINT_BACKUP_READ("service.property.tsdb.endpoint.backup.read", "http://localhost:4466,http://localhost:4467");    	

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

	void instrumentQueryLatency(final MonitorService monitorService, final AnnotationQuery query, final long start,
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
	enum HttpMethod {

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
		Long _timestamp;
		Map<String, Annotation> _custom;
		private TSDBService _service;

		/** Creates a new AnnotationWrapper object. */
		AnnotationWrapper() {
			_custom = new HashMap<>();
		}

		/* Annotations should have the same scope, metric, type and tags and timestamp. */
		AnnotationWrapper(Set<Annotation> annotations, TSDBService service) {
			this();
			_service = service;
			for (Annotation annotation : annotations) {
				if (_uid == null) {
					_uid = getUid(annotation);
					_timestamp = annotation.getTimestamp();
				}
				_custom.put(annotation.getSource() + "." + annotation.getId(), annotation);
			}
		}

		List<Annotation> getAnnotations() {
			return new ArrayList<>(_custom.values());
		}

		private String getUid(Annotation annotation) {
			String scope = toAnnotationKey(annotation);
			String type = annotation.getType();
			Map<String, String> tags = annotation.getTags();
			MetricQuery query = new MetricQuery(scope, type, tags, 0L, 2L);
			long backOff = 1000L;

			for (int attempts = 0; attempts < 3; attempts++) {
				try {
					return _service.getMetrics(Arrays.asList(query)).get(query).get(0).getUid();
				} catch (Exception e) {
					Metric metric = new Metric(scope, type);
					Map<Long, Double> datapoints = new HashMap<>();

					datapoints.put(1L, 0.0);
					metric.setDatapoints(datapoints);
					metric.setTags(annotation.getTags());
					_service.putMetrics(Arrays.asList(new Metric[] { metric }));
					try {
						Thread.sleep(backOff);
					} catch (InterruptedException ex) {
						break;
					}
					backOff += 1000L;
				}
			}
			throw new SystemException("Failed to create new annotation metric.");
		}

		String getUid() {
			return _uid;
		}

		Long getTimestamp() {
			return _timestamp;
		}

		Map<String, Annotation> getCustom() {
			return Collections.unmodifiableMap(_custom);
		}

		void setUid(String uid) {
			_uid = uid;
		}

		void setTimestamp(Long timestamp) {
			_timestamp = timestamp;
		}

		void setCustom(Map<String, Annotation> custom) {
			_custom = custom;
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
	private class QueryWorker implements Callable<List<Metric>> {

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
		public QueryWorker(String requestUrl, String requestEndPoint, String requestBody) {
			this._requestUrl = requestUrl;
			this._requestEndPoint = requestEndPoint;
			this._requestBody = requestBody;
		}

		@Override
		public List<Metric> call() {
			_logger.debug("TSDB Query = " + _requestBody);

			try {
				HttpResponse response = executeHttpRequest(HttpMethod.POST, _requestUrl,  _readPortMap.get(_requestEndPoint), new StringEntity(_requestBody));
				List<Metric> metrics = toEntity(extractResponse(response), new TypeReference<ResultSet>() { }).getMetrics();
				return metrics;
			} catch (IOException e) {
				throw new SystemException("Failed to retrieve metrics.", e);
			}
		}
	}

	@Override
	public Properties getServiceProperties() {
		Properties serviceProps= new Properties();

		for(Property property:Property.values()){
			serviceProps.put(property.getName(), property.getDefaultValue());
		}
		return serviceProps;
	}
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
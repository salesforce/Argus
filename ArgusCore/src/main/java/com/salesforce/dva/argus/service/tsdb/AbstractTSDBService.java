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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;

import com.salesforce.dva.argus.entity.MetatagsRecord;
import com.salesforce.dva.argus.entity.MetricSchemaRecord;
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
	protected final CloseableHttpClient _writeHttpClient;

	protected final List<String> _readEndPoints;
	protected final List<String> _readBackupEndPoints;
	protected final Map<String, CloseableHttpClient> _readPortMap = new HashMap<>();
	protected final Map<String, String> _readBackupEndPointsMap = new HashMap<>();

	/** Round robin iterator for write endpoints. 
	 * We will cycle through this iterator to select an endpoint from the set of available endpoints  */
	private final Iterator<String> _roundRobinIterator;

	protected final ExecutorService _executorService;
	protected final MonitorService _monitorService;
	private final int RETRY_COUNT;

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

		try {
			int index = 0;
			for (String readEndpoint : _readEndPoints) {
				_readPortMap.put(readEndpoint, getClient(connCount / 2, connTimeout, socketTimeout,tsdbConnectionReuseCount ,readEndpoint));
				_readBackupEndPointsMap.put(readEndpoint, _readBackupEndPoints.get(index));
				index ++;
			}
			for (String readBackupEndpoint : _readBackupEndPoints) {
				if (!readBackupEndpoint.isEmpty())
					_readPortMap.put(readBackupEndpoint, getClient(connCount / 2, connTimeout, socketTimeout,tsdbConnectionReuseCount, readBackupEndpoint));
			}

			_writeHttpClient = getClient(connCount / 2, connTimeout, socketTimeout,tsdbConnectionReuseCount, _writeEndpoints);

			_roundRobinIterator = constructCyclingIterator(_writeEndpoints);
			_executorService = Executors.newFixedThreadPool(connCount);
		} catch (MalformedURLException ex) {
			throw new SystemException("Error initializing the TSDB HTTP Client.", ex);
		}

	}

	//~ Methods **************************************************************************************************************************************

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
			MetatagsRecord metatagsRecord = metric.getMetatagsRecord();
			if (metatagsRecord != null) {
				//remove this special metatag to prevent it from going to TSDB
				metatagsRecord.removeMetatag(MetricSchemaRecord.RETENTION_DISCOVERY);
			}

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
			List<AnnotationWrapper> wrappers = reconcileWrappers(toAnnotationWrappers(annotations));
			String endpoint = _roundRobinIterator.next();

			try {
				put(wrappers, endpoint + "/api/annotation/bulk", HttpMethod.POST);
			} catch(IOException ex) {
				_logger.warn("IOException while trying to push annotations", ex);
				_retry(wrappers, _roundRobinIterator);
			}
		}
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
					HttpResponse response = executeHttpRequest(method, endpoint, _writeHttpClient, entity);

					extractResponse(response);
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

	/* Converts a list of annotations into a list of annotation wrappers for use in serialization.  Resulting list is sorted by target annotation
	 * scope and timestamp.
	 */
	private List<AnnotationWrapper> toAnnotationWrappers(List<Annotation> annotations) {
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
		Long _timestamp;
		Map<String, Annotation> _custom;
		private TSDBService _service;

		/** Creates a new AnnotationWrapper object. */
		AnnotationWrapper() {
			_custom = new HashMap<>();
		}

		/* Annotations should have the same scope, metric, type and tags and timestamp. */
		private AnnotationWrapper(Set<Annotation> annotations, TSDBService service) {
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
			_logger.debug("TSDB Query = " + _requestBody);

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

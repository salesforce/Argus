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

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.http.HttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.metric.transform.Transform;
import com.salesforce.dva.argus.service.metric.transform.TransformFactory;
import com.salesforce.dva.argus.service.tsdb.MetricQuery.Aggregator;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;

/**
 * The sharded implementation of the TSDBService.
 * Data queried for a given time series in different shards is disjoint wrt. time 
 *  - ie. data for a given time range is present completely in some shard.
 *
 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
 */
@Singleton
public class ShardedTSDBService extends AbstractTSDBService{

	//~ Instance fields ******************************************************************************************************************************
	private final TransformFactory _transformFactory;


	//~ Constructors *********************************************************************************************************************************
	/**
	 * Creates a new sharded TSDB Service having an equal number of read and write routes.
	 *
	 * @param   config               The system _configuration used to configure the service.
	 * @param   monitorService       The monitor service used to collect query time window counters. Cannot be null.
	 * @param   transformFactory     Transform Factory
	 *
	 * @throws  SystemException  If an error occurs configuring the service.
	 */
	@Inject
	public ShardedTSDBService(SystemConfiguration config, MonitorService monitorService, TransformFactory transformFactory) {
		super(config, monitorService);
		_transformFactory = transformFactory;
	}

	//~ Methods **************************************************************************************************************************************
	/** @see  TSDBService#dispose() */
	@Override
	public void dispose() {
		super.dispose();
		List<CloseableHttpClient> clients = new ArrayList<>();
		clients.addAll(_readPortMap.values());
		clients.add(_writeHttpClient);
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

	/** @see  TSDBService#getMetrics(java.util.List) */
	@Override
	public Map<MetricQuery, List<Metric>> getMetrics(List<MetricQuery> queries) {

		// For a given time series all the tags corresponding to that time series is present in that shard at any given time.
		// Hence we can directly query with the user's specified aggregator

		Map<MetricQuery, Long> queryStartExecutionTime = new HashMap<>();
		for (MetricQuery query : queries) {
			queryStartExecutionTime.put(query, System.currentTimeMillis());
		}

		QueryFederation queryFederation = new EndPointQueryFederation(_readEndPoints);
		Map<MetricQuery, List<MetricQuery>> mapQueryEndPointSubQueries = queryFederation.federateQueries(queries);

		List<MetricQuery> queriesSplit = new ArrayList<>();
		for(List<MetricQuery> subQueries  :  mapQueryEndPointSubQueries.values()){
			queriesSplit.addAll(subQueries);
		}

		long beforeTime = System.currentTimeMillis();		
		Map<MetricQuery, List<Metric>>  subQueryMetricsMap = getSubQueryMetrics(queriesSplit);
		long afterTime = System.currentTimeMillis();
		_logger.info("Time spent in waiting for all sub query results: {}", afterTime - beforeTime);

		beforeTime = System.currentTimeMillis();
		Map<MetricQuery, List<Metric>> queryMetricsMap = queryFederation.join(mapQueryEndPointSubQueries, subQueryMetricsMap);
		afterTime = System.currentTimeMillis();
		_logger.info("Time spent in joining results: {}", afterTime - beforeTime);

		for (MetricQuery query : queries) {
			instrumentQueryLatency(_monitorService, query, queryStartExecutionTime.get(query), "metrics");
		}

		return queryMetricsMap;
	}

	/** @see  TSDBService#getAnnotations(java.util.List) */
	@Override
	public List<Annotation> getAnnotations(List<AnnotationQuery> queries) {
		requireNotDisposed();
		requireArgument(queries != null, "Annotation queries cannot be null.");

		List<Annotation> annotations = new ArrayList<>();

		for (AnnotationQuery query : queries) {
			long start = System.currentTimeMillis();
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
						if (!_readBackupEndPointsMap.get(readEndPoint).isEmpty()) {
							_logger.warn("Trying to read from Backup endpoint");
							pattern = _readBackupEndPointsMap.get(readEndPoint) + "/api/query?{0}";
							requestUrl = MessageFormat.format(pattern, query.toString());							
							HttpResponse response = executeHttpRequest(HttpMethod.GET, requestUrl, _readPortMap.get( _readBackupEndPointsMap.get(readEndPoint)), null);
							wrappers = toEntity(extractResponse(response), new TypeReference<AnnotationWrappers>() {
							});
						}
					} catch (Exception e) {
						_logger.warn("Failed to get annotations from Backup TSDB. Reason: " + e.getMessage());
						continue;
					}					
				} 

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

	/* Gets metrics for a list of queries */
	private Map<MetricQuery, List<Metric>> getSubQueryMetrics(List<MetricQuery> queries) {
		Map<MetricQuery, Future<List<Metric>>> queryFutureMap = new HashMap<>();

		for (MetricQuery query : queries) {
			String requestBody = fromEntity(query);
			String requestUrl = query.getMetricQueryContext().getReadEndPoint() + "/api/query";
			queryFutureMap.put(query, _executorService.submit(new QueryWorker(requestUrl, query.getMetricQueryContext().getReadEndPoint(), requestBody)));
		}

		Map<MetricQuery, List<Metric>> subQueryMetricsMap = new HashMap<>();

		for (Entry<MetricQuery, Future<List<Metric>>> entry : queryFutureMap.entrySet()) {
			List<Metric> metrics = new ArrayList<>();
			List<Metric> m = null;
			try {
				m = entry.getValue().get();
			} catch (InterruptedException | ExecutionException e) {
				_logger.warn("Failed to get metrics from TSDB. Reason: " + e.getMessage());
				try {
					String readBackupEndPoint = _readBackupEndPointsMap.get(entry.getKey().getMetricQueryContext().getReadEndPoint()); 
					if (!readBackupEndPoint.isEmpty()) {
						_logger.warn("Trying to read from Backup endpoint");
						m = new QueryWorker(readBackupEndPoint + "/api/query", readBackupEndPoint, fromEntity(entry.getKey())).call();
					}
				} catch (Exception ex) {
					_logger.warn("Failed to get metrics from Backup TSDB. Reason: " + ex.getMessage());
					continue;
				}
			}

			if (m != null) {
				for (Metric metric : m) {
					if (metric != null) {
						metric.setQuery(entry.getKey());
						metrics.add(metric);
					}
				}
			}

			subQueryMetricsMap.put(entry.getKey(), metrics);
		}
		return subQueryMetricsMap;
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
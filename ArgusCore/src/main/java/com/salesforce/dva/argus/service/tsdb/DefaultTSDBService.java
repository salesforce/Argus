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

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
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
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;

/**
 * The default implementation of the TSDBService.
 *
 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
 */
@Singleton
public class DefaultTSDBService extends AbstractTSDBService{

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
		super(config, monitorService);
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
        requireNotDisposed();
        requireArgument(queries != null, "Metric Queries cannot be null.");
        _logger.trace("Active Threads in the pool = " + ((ThreadPoolExecutor) _executorService).getActiveCount());

        long start = System.currentTimeMillis();
        Map<MetricQuery, List<Metric>> metricsMap = new HashMap<>();
        Map<MetricQuery, Future<List<Metric>>> futures = new HashMap<>();
        Map<MetricQuery, Long> queryStartExecutionTime = new HashMap<>();
        // Only one endpoint for DefaultTSDBService
        String requestUrl = _readEndPoints.get(0) + "/api/query";

        for (MetricQuery query : queries) {
        	String requestBody = fromEntity(query);
            futures.put(query, _executorService.submit(new QueryWorker(requestUrl, _readEndPoints.get(0), requestBody)));
            queryStartExecutionTime.put(query, System.currentTimeMillis());
        }
        for (Entry<MetricQuery, Future<List<Metric>>> entry : futures.entrySet()) {
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
        _logger.debug("Time to get Metrics = " + (System.currentTimeMillis() - start));
        return metricsMap;
    }

	/** @see  TSDBService#getAnnotations(java.util.List) */
	@Override
    public List<Annotation> getAnnotations(List<AnnotationQuery> queries) {
        requireNotDisposed();
        requireArgument(queries != null, "Annotation queries cannot be null.");

        List<Annotation> annotations = new ArrayList<>();
        String pattern = _readEndPoints.get(0) + "/api/query?{0}";

        try {
        	for (AnnotationQuery query : queries) {
            	long start = System.currentTimeMillis();
                String requestUrl = MessageFormat.format(pattern, query.toString());
                HttpResponse response = executeHttpRequest(HttpMethod.GET, requestUrl, _readPortMap.get(_readEndPoints.get(0)), null);
                List<AnnotationWrapper> wrappers = toEntity(extractResponse(response), new TypeReference<AnnotationWrappers>() { });
                if (wrappers != null) {
                    for (AnnotationWrapper wrapper : wrappers) {
                        for (Annotation existing : wrapper.getAnnotations()) {
                            String source = existing.getSource();
                            String id = existing.getId();
                            String type = query.getType();
                            String scope = query.getScope();
                            String metric = query.getMetric();
			    
                            //Convert all timestamps to millis, so that we can compare them
                            long timestamp = existing.getTimestamp();
                            if(String.valueOf(timestamp).length() < 12) {
                            	timestamp = timestamp * 1000;
                            }
                            
                            long queryStart = query.getStartTimestamp();
                            long queryEnd = query.getEndTimestamp();
                            if(String.valueOf(queryStart).length() < 12) {
                            	queryStart = queryStart * 1000;
                            }
                            
                            if(String.valueOf(queryEnd).length() < 12) {
                            	queryEnd = queryEnd * 1000;
                            }
                            
                            if(timestamp > queryStart && timestamp <= queryEnd) {
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
        } catch(IOException ex) {
        	throw new SystemException(ex);
        }
        
        return annotations;
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
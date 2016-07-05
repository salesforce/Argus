package com.salesforce.dva.argus.service.metric.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.salesforce.dva.argus.entity.AsyncBatchedMetricQuery;
import com.salesforce.dva.argus.entity.BatchMetricQuery;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.BatchService;
import com.salesforce.dva.argus.service.CacheService;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.MetricQueueService;
import com.salesforce.dva.argus.service.batch.CachedBasedBatchService;
import com.salesforce.dva.argus.service.metric.MetricReader;
import com.salesforce.dva.argus.service.metric.ParseException;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Default implementation of the metric queue service.
 *
 * @author  Colby Guan (cguan@salesforce.com)
 */
public class CacheBasedMetricQueueService extends DefaultService implements MetricQueueService {

    //~ Static fields/initializers *******************************************************************************************************************
    @SLF4JTypeListener.InjectLogger
    private Logger LOGGER;
    private static final Object cacheLock = new Object();
    private static final ObjectMapper MAPPER = new ObjectMapper();
    // Keys to store async queries at
    private static final String ROOT_LOW = "async/low/";
    private static final String ROOT_HIGH = "async/high/";
    private static final String LOW_HEAD = ROOT_LOW + "front";
    private static final String LOW_TAIL = ROOT_LOW + "back";
    private static final String HIGH_HEAD = ROOT_HIGH + "front";
    private static final String HIGH_TAIL = ROOT_HIGH + "back";

    //~ Instance fields ******************************************************************************************************************************

    private final SystemConfiguration _config;
    private final CacheService _cacheService;
    private final BatchService _batchService;
    private final Provider<MetricReader<Metric>> _metricReaderProviderForMetrics;

    //~ Constructors *********************************************************************************************************************************

    @Inject
    public CacheBasedMetricQueueService(SystemConfiguration config, CacheService cacheService,
                                        BatchService batchService, Provider<MetricReader<Metric>> metricsprovider) {
        super(config);
        requireArgument(cacheService != null, "Cache service cannot be null.");
        _config = config;
        _cacheService = cacheService;
        _batchService = batchService;
        _metricReaderProviderForMetrics = metricsprovider;
        if (_cacheService.get(LOW_HEAD) == null) {
            _cacheService.put(LOW_HEAD, String.valueOf(1), Integer.MAX_VALUE);
            _cacheService.put(LOW_TAIL, String.valueOf(0), Integer.MAX_VALUE);
        }
        if (_cacheService.get(HIGH_HEAD) == null) {
            _cacheService.put(HIGH_HEAD, String.valueOf(1), Integer.MAX_VALUE);
            _cacheService.put(HIGH_TAIL, String.valueOf(0), Integer.MAX_VALUE);
        }
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public synchronized void enqueue(AsyncBatchedMetricQuery query, int priority) {
        String tailPath;
        if (BatchMetricQuery.Priority.fromInt(priority) == BatchMetricQuery.Priority.HIGH) {
            tailPath = HIGH_TAIL;
        } else {
            tailPath = LOW_TAIL;
        }
        long tailId = Long.valueOf(_cacheService.get(tailPath)) + 1;
        _cacheService.put(tailPath, String.valueOf(tailId), Integer.MAX_VALUE);
        LOGGER.info("MetricQueueService.enqueue/" + tailId + "/" + query.getExpression());

        query.setQueueId(tailId);
        query.setStatus(BatchMetricQuery.Status.QUEUED);
        updateQuery(query);
    }

    @Override
    public synchronized AsyncBatchedMetricQuery dequeueAndProcess(int priority) {
        String headPath;
        if (BatchMetricQuery.Priority.fromInt(priority) == BatchMetricQuery.Priority.HIGH) {
            headPath = HIGH_HEAD;
        } else {
            headPath = LOW_HEAD;
        }

        // Shift pointer
        AsyncBatchedMetricQuery query;
        synchronized(CachedBasedBatchService.class) {
            long headId = Long.valueOf(_cacheService.get(headPath));
            query = findQueryById(priority, headId);
            if (query != null) {
                _cacheService.put(headPath, String.valueOf(headId + 1), Integer.MAX_VALUE);
                LOGGER.info("MetricQueueService.dequeue/" + headId + "/" + query.getExpression());
            } else {
                return null;
            }
        }

        // Calculate query
        query.setStatus(BatchMetricQuery.Status.PROCESSING);
        updateQuery(query);
        MetricReader<Metric> reader = _metricReaderProviderForMetrics.get();
        try {
            LOGGER.info("MetricQueueService/parsing " + query.getExpression());
            List<Metric> results = reader.parse(query.getExpression(), query.getOffset(), Metric.class);
            query.setStatus(BatchMetricQuery.Status.DONE);
            if (results.size() == 0) {
                updateQuery(query);
                return null;
            }
            query.setResult(results.get(0));
            updateQuery(query);

            // Update corresponding batch
            BatchMetricQuery batch = _batchService.findBatchById(query.getBatchId());
            _batchService.updateBatch(batch);
            return query;
        } catch (ParseException ex) {
            throw new SystemException("Failed to parse the given expression", ex);
        }
    }

    public AsyncBatchedMetricQuery findQueryById(int priority, long id) {
        BatchMetricQuery.Priority priorityEnum = BatchMetricQuery.Priority.fromInt(priority);
        String rootPath = _getRootPathFromPriority(priorityEnum);
        String json = _cacheService.get(rootPath + id);
        if (json == null) {
            return null;
        }
        try {
            Map<String, Object> queryData = MAPPER.readValue(json, Map.class);
            String expression = (String) queryData.get("expression");
            long offset = Long.valueOf(queryData.get("offset").toString());
            long queueId = Long.valueOf(queryData.get("queueId").toString());
            String batchId = (String) queryData.get("batchId");
            BatchMetricQuery.Status status = BatchMetricQuery.Status.fromInt((Integer) queryData.get("status"));
            String metricJson = (String) queryData.get("metric");
            Metric result = MAPPER.readValue(metricJson, Metric.class);
            return new AsyncBatchedMetricQuery(expression, offset, queueId, batchId, status, priorityEnum, result);
        } catch (IOException ex) {
            throw new SystemException(ex);
        }
    }

    public void updateQuery(AsyncBatchedMetricQuery query) {
        String rootPath = _getRootPathFromPriority(query.getPriority());
        String json = _serializeQueryToJson(query);
        _cacheService.put(rootPath + String.valueOf(query.getQueueId()), json, Integer.MAX_VALUE);
    }

    public void updateQueryWithTtl(AsyncBatchedMetricQuery query, int ttl) {
        String rootPath = _getRootPathFromPriority(query.getPriority());
        String json = _serializeQueryToJson(query);
        _cacheService.put(rootPath + String.valueOf(query.getQueueId()), json, ttl);
    }

    private String _serializeQueryToJson(AsyncBatchedMetricQuery query) {
        Map<String,Object> queryData = new HashMap<>();
        try {
            queryData.put("expression", query.getExpression());
            queryData.put("offset", query.getOffset());
            queryData.put("queueId", query.getQueueId());
            queryData.put("batchId", query.getBatchId());
            queryData.put("status", query.getStatus().toInt());
            queryData.put("priority", query.getPriority().toInt());
            queryData.put("metric", MAPPER.writeValueAsString(query.getResult()));
            return MAPPER.writeValueAsString(queryData);
        } catch (IOException ex) {
            throw new SystemException(ex);
        }
    }

    private String _getRootPathFromPriority(BatchMetricQuery.Priority priority) {
        if (BatchMetricQuery.Priority.fromInt(priority.toInt()) == BatchMetricQuery.Priority.HIGH) {
            return ROOT_HIGH;
        } else {
            return ROOT_LOW;
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

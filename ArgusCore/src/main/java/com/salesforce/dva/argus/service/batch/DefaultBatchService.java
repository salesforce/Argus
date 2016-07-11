package com.salesforce.dva.argus.service.batch;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.salesforce.dva.argus.entity.AsyncBatchedMetricQuery;
import com.salesforce.dva.argus.entity.BatchMetricQuery;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.*;
import com.salesforce.dva.argus.service.metric.MetricReader;
import com.salesforce.dva.argus.service.metric.ParseException;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;

import java.io.IOException;
import java.util.*;

import static com.salesforce.dva.argus.service.MQService.MQQueue.BATCH;
import static com.salesforce.dva.argus.entity.BatchMetricQuery.Status;
import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Default implementation of the batch service.
 *
 * @author Colby Guan (cguan@salesforce.com)
 */
public class DefaultBatchService extends DefaultService implements BatchService {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final String ROOT = "batch/";
    private static final String QUERIES = "/queries/";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int DEFAULT_TTL = 2592000;

    //~ Instance fields ******************************************************************************************************************************

    private final SystemConfiguration _config;
    private final CacheService _cacheService;
    private final MQService _mqService;
    private final Provider<MetricReader<Metric>> _metricReaderProviderForMetrics;

    //~ Constructors *********************************************************************************************************************************

    @Inject
    public DefaultBatchService(SystemConfiguration config, CacheService cacheService, MQService mqService, Provider<MetricReader<Metric>> metricsprovider) {
        super(config);
        requireArgument(cacheService != null, "Cache service cannot be null.");
        _config = config;
        _cacheService = cacheService;
        _mqService = mqService;
        _metricReaderProviderForMetrics = metricsprovider;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public BatchMetricQuery findBatchById(String id) {
        String json = _cacheService.get(ROOT + id);
        if (json == null) {
            return null;
        }
        try {
            Map<String, Object> batchData = MAPPER.readValue(json, Map.class);
            Status status = BatchMetricQuery.Status.fromInt((Integer) batchData.get("status"));
            int ttl = Integer.valueOf(batchData.get("ttl").toString());
            long createdDate = Long.valueOf(batchData.get("createdDate").toString());
            String ownerName = (String) batchData.get("ownerName");
            String batchId = (String) batchData.get("batchId");
            List<AsyncBatchedMetricQuery> queries = new ArrayList<>();

            String[] queryIds = MAPPER.readValue((String) batchData.get("queueIds"), String[].class);
            for (String queryId: queryIds) {
                queries.add(_findQueryById(batchId, queryId));
            }
            BatchMetricQuery batch = new BatchMetricQuery(status, ttl, createdDate, batchId, ownerName, queries);
            return batch;
        } catch (IOException ex) {
            throw new SystemException(ex);
        }
    }

    @Override
    public Map<String, String> findBatchesByOwnerName(String ownerName) {
        String userBatchesJson = _cacheService.get(ROOT + ownerName);
        if (userBatchesJson == null) {
        	return null;
        }
        try {
            Map<String, String> userBatches = MAPPER.readValue(userBatchesJson, Map.class);
            List<String> toRemove = new LinkedList<>();
            for (String id: userBatches.keySet()) {
                if (_cacheService.get(ROOT + id) == null) {
                    toRemove.add(id);
                }
            }
            for (String id: toRemove) {
                userBatches.remove(id);
            }
            userBatchesJson = MAPPER.writeValueAsString(userBatches);
            _cacheService.put(ROOT + ownerName, userBatchesJson, DEFAULT_TTL);
            return userBatches;
        } catch (IOException ex) {
            throw new SystemException(ex);
        }
    }

    @Override
    public void updateBatch(BatchMetricQuery batch) {
        Map<String,Object> batchData = new HashMap<>();
        try {
            // Put batch JSON to cache
            int ttl = batch.getTtl();
            String batchId = batch.getBatchId();
            batchData.put("ttl", ttl);
            batchData.put("createdDate", batch.getCreatedDate());
            batchData.put("ownerName", batch.getOwnerName());
            batchData.put("batchId", batchId);
            List<String> queueIds = new ArrayList<>(batch.getQueries().size());
            for (AsyncBatchedMetricQuery query : batch.getQueries()) {
                queueIds.add(query.getQueryId());
            }
            String queueIdsJson = MAPPER.writeValueAsString(queueIds);
            batchData.put("queueIds", queueIdsJson);

            BatchMetricQuery.Status oldStatus = batch.getStatus();
            batch.updateStatus();
            BatchMetricQuery.Status newStatus = batch.getStatus();
            batchData.put("status", newStatus.toInt());
            String json = MAPPER.writeValueAsString(batchData);
            // Enforce cache TTL if batch status now changes to done
            if (oldStatus != newStatus && newStatus == BatchMetricQuery.Status.DONE) {
                _cacheService.put(ROOT + batchId, json, ttl);
                for (AsyncBatchedMetricQuery query : batch.getQueries()) {
                    _updateQuery(query, ttl);
                }
            } else {
                _cacheService.put(ROOT + batchId, json, DEFAULT_TTL);
                for (AsyncBatchedMetricQuery query : batch.getQueries()) {
                    _updateQuery(query, DEFAULT_TTL);
                }
            }

            // Update user JSON in cache
            String userBatchesJson = _cacheService.get(ROOT + batch.getOwnerName());
            Map<String, Object> userBatches;
            if (userBatchesJson == null) {
                userBatches = new HashMap<>();
            } else {
                userBatches = MAPPER.readValue(userBatchesJson, Map.class);
            }
            userBatches.put(batch.getBatchId(), newStatus.toInt());
            String updatedBatchesJson = MAPPER.writeValueAsString(userBatches);
            _cacheService.put(ROOT + batch.getOwnerName(), updatedBatchesJson, DEFAULT_TTL);
        } catch (IOException ex) {
            throw new SystemException(ex);
        }
    }

    @Override
    public void enqueueBatch(BatchMetricQuery batch) {
        _mqService.enqueue(BATCH.getQueueName(), batch.getQueries());
    }

    @Override
    public AsyncBatchedMetricQuery executeNextQuery(int timeout) {
        AsyncBatchedMetricQuery query = _mqService.dequeue(BATCH.getQueueName(), AsyncBatchedMetricQuery.class, timeout);
        if (query == null) {
            return null;
        }
        query.setStatus(Status.PROCESSING);
        _updateQuery(query, DEFAULT_TTL);
        MetricReader<Metric> reader = _metricReaderProviderForMetrics.get();
        try {
            List<Metric> results = reader.parse(query.getExpression(), query.getOffset(), Metric.class);
            query.setStatus(BatchMetricQuery.Status.DONE);
            if (results.size() == 0) {
                _updateQuery(query, DEFAULT_TTL);
                return null;
            }
            query.setResult(results.get(0));
            _updateQuery(query, DEFAULT_TTL);

            // Update corresponding batch
            BatchMetricQuery batch = findBatchById(query.getBatchId());
            updateBatch(batch);
            return query;
        } catch (ParseException ex) {
            throw new SystemException("Failed to parse the given expression", ex);
        }
    }

    private AsyncBatchedMetricQuery _findQueryById(String batchId, String queryId) {
        String json = _cacheService.get(ROOT + batchId + QUERIES + queryId);
        if (json == null) {
            return null;
        }
        try {
            Map<String, Object> queryData = MAPPER.readValue(json, Map.class);
            String expression = (String) queryData.get("expression");
            long offset = Long.valueOf(queryData.get("offset").toString());
            BatchMetricQuery.Status status = BatchMetricQuery.Status.fromInt((Integer) queryData.get("status"));
            String metricJson = (String) queryData.get("metric");
            Metric result = MAPPER.readValue(metricJson, Metric.class);
            return new AsyncBatchedMetricQuery(expression, offset, batchId, queryId, status, result);
        } catch (IOException ex) {
            throw new SystemException(ex);
        }
    }

    private void _updateQuery(AsyncBatchedMetricQuery query, int ttl) {
        Map<String,Object> queryData = new HashMap<>();
        try {
            queryData.put("expression", query.getExpression());
            queryData.put("offset", query.getOffset());
            queryData.put("queueId", query.getQueryId());
            queryData.put("batchId", query.getBatchId());
            queryData.put("status", query.getStatus().toInt());
            queryData.put("metric", MAPPER.writeValueAsString(query.getResult()));
            _cacheService.put(ROOT + query.getBatchId() + QUERIES + query.getQueryId(), MAPPER.writeValueAsString(queryData), ttl);
        } catch (IOException ex) {
            throw new SystemException(ex);
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

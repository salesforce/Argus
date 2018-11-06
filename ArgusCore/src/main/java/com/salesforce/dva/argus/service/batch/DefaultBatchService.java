package com.salesforce.dva.argus.service.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.salesforce.dva.argus.entity.AsyncBatchedMetricQuery;
import com.salesforce.dva.argus.entity.BatchMetricQuery;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.BatchService;
import com.salesforce.dva.argus.service.CacheService;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.MQService;
import com.salesforce.dva.argus.service.metric.MetricReader;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import com.salesforce.dva.argus.util.QueryContextHolder;

import java.io.IOException;
import java.util.*;

import static com.salesforce.dva.argus.entity.BatchMetricQuery.Status;
import static com.salesforce.dva.argus.service.MQService.MQQueue.BATCH;
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

    private final CacheService _cacheService;
    private final MQService _mqService;
    private final Provider<MetricReader<Metric>> _metricReaderProviderForMetrics;

    //~ Constructors *********************************************************************************************************************************

    @Inject
    public DefaultBatchService(SystemConfiguration config, CacheService cacheService, MQService mqService, Provider<MetricReader<Metric>> metricsprovider) {
        super(config);
        requireArgument(cacheService != null, "Cache service cannot be null.");
        requireArgument(mqService != null, "MQ service cannot be null.");
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
            Map<String, Object> batchData = MAPPER.readValue(json, new TypeReference<HashMap<String, Object>>() {});
            Status status = BatchMetricQuery.Status.fromInt((Integer) batchData.get("status"));
            int ttl = Integer.valueOf(batchData.get("ttl").toString());
            long createdDate = Long.valueOf(batchData.get("createdDate").toString());
            String ownerName = (String) batchData.get("ownerName");
            String batchId = (String) batchData.get("batchId");
            List<AsyncBatchedMetricQuery> queries = new ArrayList<>();

            int[] indices = MAPPER.readValue((String) batchData.get("indices"), int[].class);
            for (int index: indices) {
                queries.add(_findQueryById(batchId, index));
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
            Map<String, String> userBatches = MAPPER.readValue(userBatchesJson, new TypeReference<HashMap<String, String>>() {});
            List<String> toRemove = new LinkedList<>();
            for (String id: userBatches.keySet()) {
                BatchMetricQuery userBatch = findBatchById(id);
                if (userBatch == null) {
                    toRemove.add(id);
                } else {
                    userBatches.put(id, String.valueOf(userBatch.getStatus().toInt()));
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
    public void enqueueBatch(BatchMetricQuery batch) {
        _createBatch(batch);
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
            List<Metric> results = reader.parse(query.getExpression(), query.getRelativeTo(), Metric.class, new QueryContextHolder(), false);
            query.setStatus(Status.DONE);
            if (results.size() != 0) {
                query.setResult(results.get(0));
            }
            _updateQuery(query, DEFAULT_TTL);

            BatchMetricQuery batch = findBatchById(query.getBatchId());
            if (batch.getStatus() == Status.DONE || batch.getStatus() == Status.ERROR) {
                _enforceTtl(batch);
            }
            return query;
        } catch (Exception ex) {
            query.setStatus(Status.ERROR);
            query.setMessage(ex.toString());
            _updateQuery(query, DEFAULT_TTL);
            BatchMetricQuery batch = findBatchById(query.getBatchId());
            if (batch.getStatus() == Status.ERROR) {
                _enforceTtl(batch);
            }
            throw new SystemException("Failed to parse the given expression", ex);
        }
    }

    @Override
    public void deleteBatch(String id) {
        String json = _cacheService.get(ROOT + id);
        requireArgument(json != null, "No such batch exists");
        _cacheService.delete(ROOT + id);
        try {
            Map<String, Object> batchData = MAPPER.readValue(json, new TypeReference<HashMap<String, Object>>() {});

            int[] indices = MAPPER.readValue((String) batchData.get("indices"), int[].class);
            for (int index: indices) {
                _cacheService.delete(ROOT + id + QUERIES + index);
            }
        } catch (IOException ex) {
            throw new SystemException(ex);
        }
    }

    private void _createBatch(BatchMetricQuery batch) {
        String json = _serializeBatchToJson(batch);
        _cacheService.put(ROOT + batch.getBatchId(), json, DEFAULT_TTL);
        for (AsyncBatchedMetricQuery query : batch.getQueries()) {
            _updateQuery(query, DEFAULT_TTL);
        }

        _updateUserBatches(batch);
    }

    private void _enforceTtl(BatchMetricQuery batch) {
        String json = _serializeBatchToJson(batch);
        _cacheService.put(ROOT + batch.getBatchId(), json, batch.getTtl());
        for (AsyncBatchedMetricQuery query : batch.getQueries()) {
            _updateQuery(query, batch.getTtl());
        }
    }

    private String _serializeBatchToJson(BatchMetricQuery batch) {
        Map<String,Object> batchData = new HashMap<>();
        try {
            batchData.put("status", batch.getStatus().toInt());
            batchData.put("ttl", batch.getTtl());
            batchData.put("createdDate", batch.getCreatedDate());
            batchData.put("ownerName", batch.getOwnerName());
            batchData.put("batchId", batch.getBatchId());
            List<Integer> indices = new ArrayList<>(batch.getQueries().size());
            for (AsyncBatchedMetricQuery query : batch.getQueries()) {
                indices.add(query.getIndex());
            }
            String indicesJson = MAPPER.writeValueAsString(indices);
            batchData.put("indices", indicesJson);
            return MAPPER.writeValueAsString(batchData);
        } catch (IOException ex) {
            throw new SystemException(ex);
        }
    }

    private void _updateUserBatches(BatchMetricQuery batch) {
        try {
            String userBatchesJson = _cacheService.get(ROOT + batch.getOwnerName());
            Map<String, Object> userBatches;
            if (userBatchesJson == null) {
                userBatches = new HashMap<>();
            } else {
                userBatches = MAPPER.readValue(userBatchesJson, new TypeReference<HashMap<String, Object>>() {});
            }
            userBatches.put(batch.getBatchId(), null);
            String updatedBatchesJson = MAPPER.writeValueAsString(userBatches);
            _cacheService.put(ROOT + batch.getOwnerName(), updatedBatchesJson, DEFAULT_TTL);
        } catch (IOException ex) {
            throw new SystemException(ex);
        }
    }

    private AsyncBatchedMetricQuery _findQueryById(String batchId, int index) {
        String json = _cacheService.get(ROOT + batchId + QUERIES + index);
        if (json == null) {
            return null;
        }
        try {
            Map<String, Object> queryData = MAPPER.readValue(json, new TypeReference<HashMap<String, Object>>() {});
            String expression = (String) queryData.get("expression");
            long relativeTo = Long.valueOf(queryData.get("relativeTo").toString());
            BatchMetricQuery.Status status = BatchMetricQuery.Status.fromInt((Integer) queryData.get("status"));
            String message = (String) queryData.get("message");
            String metricJson = (String) queryData.get("metric");
            Metric result = MAPPER.readValue(metricJson, Metric.class);
            return new AsyncBatchedMetricQuery(expression, relativeTo, batchId, index, status, result, message);
        } catch (IOException ex) {
            throw new SystemException(ex);
        }
    }

    private void _updateQuery(AsyncBatchedMetricQuery query, int ttl) {
        Map<String,Object> queryData = new HashMap<>();
        try {
            queryData.put("expression", query.getExpression());
            queryData.put("relativeTo", query.getRelativeTo());
            queryData.put("index", query.getIndex());
            queryData.put("batchId", query.getBatchId());
            queryData.put("status", query.getStatus().toInt());
            queryData.put("message", query.getMessage());
            queryData.put("metric", MAPPER.writeValueAsString(query.getResult()));
            _cacheService.put(ROOT + query.getBatchId() + QUERIES + query.getIndex(), MAPPER.writeValueAsString(queryData), ttl);
        } catch (IOException ex) {
            throw new SystemException(ex);
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

package com.salesforce.dva.argus.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesforce.dva.argus.service.CacheService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by cguan on 6/1/16.
 */
public class AsyncBatchedMetricQuery {
    String _expression;
    long _offset;
    long _queueId;
    String _batchId;
    BatchMetricQuery.Status _status;
    Metric _result;
    private static final String ROOT = "async/";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger LOGGER = LoggerFactory.getLogger(AsyncBatchedMetricQuery.class);

    public AsyncBatchedMetricQuery(String expression, long offset, String batchId) {
        _expression = expression;
        _offset = offset;
        _batchId = batchId;
        _status = BatchMetricQuery.Status.QUEUED;
    }

    public AsyncBatchedMetricQuery(String json) {
        try {
            Map<String, Object> queryData = MAPPER.readValue(json, Map.class);
            _expression = (String) queryData.get("expression");
            _offset = Long.valueOf((Integer) queryData.get("offset"));
            _queueId = Long.valueOf((Integer) queryData.get("queueId"));
            _batchId = (String) queryData.get("batchId");
            _status = BatchMetricQuery.Status.fromInt((Integer) queryData.get("status"));
            String metricJson = (String) queryData.get("metric");
            _result = MAPPER.readValue(metricJson, Metric.class);
        } catch (Exception ex) {
            LOGGER.error("Exception in AsyncBatchedMetricQuery construction from JSON: {}", ex.toString());
            ex.printStackTrace();
        }
    }

    public String getExpression() {
        return _expression;
    }

    public long getOffset() {
        return _offset;
    }

    public void setQueueId(long queueId) {
        _queueId = queueId;
    }

    public long getQueueId() {
        return _queueId;
    }

    public BatchMetricQuery.Status getStatus() {
        return _status;
    }

    public void setStatus(BatchMetricQuery.Status status) {
        _status = status;
    }

    public Metric getResult() {
        return _result;
    }

    public void setResult(Metric result) {
        _result = result;
    }

    public synchronized void save(CacheService cacheService) {
        // TODO: account for TTL
        Map<String,Object> queryData = new HashMap<>();
        try {
            queryData.put("expression", _expression);
            queryData.put("offset", _offset);
            queryData.put("queueId", _queueId);
            queryData.put("batchId", _batchId);
            queryData.put("status", _status.toInt());
            queryData.put("metric", MAPPER.writeValueAsString(_result));
            String json = MAPPER.writeValueAsString(queryData);
            cacheService.put(ROOT + String.valueOf(_queueId), json, Integer.MAX_VALUE);
        } catch (JsonProcessingException ex) {
            LOGGER.error("Exception in AsyncBatchedMetricQuery.save: {}", ex.toString());
        }
    }
}

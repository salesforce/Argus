package com.salesforce.dva.argus.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesforce.dva.argus.service.CacheService;
import com.salesforce.dva.argus.service.MetricQueueService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.*;

/**
 * Created by cguan on 6/1/16.
 */
public class BatchMetricQuery implements Serializable {
    private Status _status;
    private Priority _priority;
    private int _ttl;
    private String _batchId;
    private String _ownerName;
    private List<AsyncBatchedMetricQuery> _queries;
    private static final String ROOT = "batch/";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger LOGGER = LoggerFactory.getLogger(BatchMetricQuery.class);

    public BatchMetricQuery(List<String> expressions, long offset, int priority, int ttl, String ownerName) {
        _status = Status.QUEUED;
        _priority = Priority.fromInt(priority);
        _ttl = ttl;
        _ownerName = ownerName;
        _batchId = UUID.randomUUID().toString();
        _queries = new ArrayList<>(expressions.size());
        for (String expression: expressions) {
            _queries.add(new AsyncBatchedMetricQuery(expression, offset, _batchId));
        }
    }

    // TODO: restructure to service
    private BatchMetricQuery(MetricQueueService metricQueueService, String json) {
        try {
            Map<String, Object> batchData = MAPPER.readValue(json, Map.class);
            _status = Status.fromInt((Integer) batchData.get("status"));
            _priority = Priority.fromInt((Integer) batchData.get("priority"));
            _ttl = (Integer) batchData.get("ttl");
            _ownerName = (String) batchData.get("ownerName");
            _batchId = (String) batchData.get("batchId");
            _queries = new ArrayList<>();

            Long[] queueIds = MAPPER.readValue((String) batchData.get("queueIds"), Long[].class);
            for (Long queueId: queueIds) {
                _queries.add(metricQueueService.findById(queueId));
            }
            updateStatus();
        } catch (Exception ex) {
            LOGGER.error("Exception in BatchMetricQuery construction from JSON: {}", ex.toString());
        }
    }

    private void updateStatus() {
        boolean allDone = true;
        if (_status == Status.DONE) {
            return;
        }
        for (AsyncBatchedMetricQuery query: _queries) {
            Status status = query.getStatus();
            allDone &= (query.getStatus() == Status.DONE);
            if (status == Status.PROCESSING) {
                _status = Status.PROCESSING;
                break;
            }
        }
        if (allDone) {
            _status = Status.DONE;
        }
    }

    public Status getStatus() {
        return _status;
    }

    public Priority getPriority() {
        return _priority;
    }

    public int getTtl() {
        return _ttl;
    }

    public String getBatchId()
    {
        return _batchId;
    }

    public String getOwnerName() {
        return _ownerName;
    }

    public List<AsyncBatchedMetricQuery> getQueries() {
        return _queries;
    }

    // Invariant: never called again after all queries finish
    public synchronized void save(CacheService cacheService) {
        // TODO: account for TTL. This method also "requires" that queueIds are already in place?
        Map<String,Object> batchData = new HashMap<>();
        try {
            batchData.put("priority", _priority.toInt());
            batchData.put("ttl", _ttl);
            batchData.put("ownerName", _ownerName);
            batchData.put("batchId", _batchId);
            List<Long> queueIds = new ArrayList<>(_queries.size());
            for (AsyncBatchedMetricQuery query: _queries) {
                queueIds.add(query.getQueueId());
            }
            String queueIdsJson = MAPPER.writeValueAsString(queueIds);
            batchData.put("queueIds", queueIdsJson);

            Status oldStatus = _status;
            updateStatus();
            batchData.put("status", _status.toInt());
            String json = MAPPER.writeValueAsString(batchData);
            if (oldStatus != _status && _status == Status.DONE) {
                cacheService.put(ROOT + _batchId, json , _ttl);
            } else {
                cacheService.put(ROOT + _batchId, json , Integer.MAX_VALUE);
            }
        } catch (JsonProcessingException ex) {
            LOGGER.error("Exception in AsyncBatchedMetricQuery.save: {}", ex.toString());
        }
    }

    public static BatchMetricQuery findById(CacheService cacheService, MetricQueueService metricQueueService, String id) {
        // TODO: move getting batch entity to CacheBasedBatchService that implements generic getter?
        String json = cacheService.get(ROOT + id);
        if (json != null) {
            return new BatchMetricQuery(metricQueueService, json);
        } else {
            return null;
        }
    }

    public enum Priority {
        HIGH("high"),
        LOW("low");

        private final String _key;

        Priority(String key) {
            _key = key;
        }

        public static Priority fromInt(int priority) {
            if (priority >= 1) {
                return HIGH;
            } else {
                return LOW;
            }
        }
        
        public int toInt() {
        	if (this == HIGH) {
        		return 1;
        	} else {
        		return 0;
        	}
        }

        @Override
        public String toString() {
            return _key;
        }
    }

    public enum Status {
        QUEUED("queued"),
        PROCESSING("processing"),
        DONE("done");

        private final String _key;

        Status(String key) {
            _key = key;
        }

        public static Status fromInt(int status) {
            if (status >= 2) {
                return DONE;
            } else if (status == 1) {
                return PROCESSING;
            } else {
                return QUEUED;
            }
        }
        
        public int toInt() {
        	if (this == DONE) {
        		return 2;
        	} else if (this == PROCESSING) {
        		return 1;
        	} else {
        		return 0;
        	}
        }

        @Override
        public String toString() {
            return _key;
        }
    }
}

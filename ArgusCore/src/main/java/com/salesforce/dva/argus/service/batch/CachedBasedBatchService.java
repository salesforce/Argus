package com.salesforce.dva.argus.service.batch;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.salesforce.dva.argus.entity.AsyncBatchedMetricQuery;
import com.salesforce.dva.argus.entity.BatchMetricQuery;
import com.salesforce.dva.argus.service.BatchService;
import com.salesforce.dva.argus.service.CacheService;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.MetricQueueService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Created by cguan on 6/15/16.
 */
public class CachedBasedBatchService extends DefaultService implements BatchService {
    private static final String ROOT = "batch/";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger LOGGER = LoggerFactory.getLogger(BatchMetricQuery.class);

    private final SystemConfiguration _config;
    private final CacheService _cacheService;
    private final MetricQueueService _metricQueueService;

    @Inject
    public CachedBasedBatchService (SystemConfiguration config, CacheService cacheService, MetricQueueService metricQueueService) {
        super(config);
        requireArgument(cacheService != null, "Cache service cannot be null.");
        _config = config;
        _cacheService = cacheService;
        _metricQueueService = metricQueueService;
    }

    @Override
    public BatchMetricQuery findBatchById(String id) {
        String json = _cacheService.get(ROOT + id);
        if (json == null) {
            return null;
        }
        try {
            Map<String, Object> batchData = MAPPER.readValue(json, Map.class);
            BatchMetricQuery.Status status = BatchMetricQuery.Status.fromInt((Integer) batchData.get("status"));
            BatchMetricQuery.Priority priority = BatchMetricQuery.Priority.fromInt((Integer) batchData.get("priority"));
            int ttl = (Integer) batchData.get("ttl");
            String ownerName = (String) batchData.get("ownerName");
            String batchId = (String) batchData.get("batchId");
            List<AsyncBatchedMetricQuery> queries = new ArrayList<>();

            Long[] queueIds = MAPPER.readValue((String) batchData.get("queueIds"), Long[].class);
            for (Long queueId: queueIds) {
                queries.add(_metricQueueService.findQueryById(priority.toInt(), queueId));
            }
            BatchMetricQuery batch = new BatchMetricQuery(status, priority, ttl, batchId, ownerName, queries);
            return batch;
        } catch (Exception ex) {
            LOGGER.error("Exception in BatchMetricQuery construction from JSON: {}", ex.toString());
            return null;
        }
    }

    @Override
    public void updateBatch(BatchMetricQuery batch) {
        Map<String,Object> batchData = new HashMap<>();
        try {
            int ttl = batch.getTtl();
            String batchId = batch.getBatchId();
            batchData.put("priority", batch.getPriority().toInt());
            batchData.put("ttl", ttl);
            batchData.put("ownerName", batch.getOwnerName());
            batchData.put("batchId", batchId);
            List<Long> queueIds = new ArrayList<>(batch.getQueries().size());
            for (AsyncBatchedMetricQuery query: batch.getQueries()) {
                queueIds.add(query.getQueueId());
            }
            String queueIdsJson = MAPPER.writeValueAsString(queueIds);
            batchData.put("queueIds", queueIdsJson);

            BatchMetricQuery.Status oldStatus = batch.getStatus();
            batch.updateStatus();
            BatchMetricQuery.Status newStatus = batch.getStatus();
            batchData.put("status", newStatus.toInt());
            String json = MAPPER.writeValueAsString(batchData);
            LOGGER.info("CachedBasedBatchService.updateBatch/status changed from " + oldStatus + " to " + newStatus);
            if (oldStatus != newStatus && newStatus == BatchMetricQuery.Status.DONE) {
                _cacheService.put(ROOT + batchId, json , ttl);
            } else {
                _cacheService.put(ROOT + batchId, json , Integer.MAX_VALUE);
            }
        } catch (JsonProcessingException ex) {
            LOGGER.error("Exception in AsyncBatchedMetricQuery.saveToCache: {}", ex.toString());
        }
    }
}

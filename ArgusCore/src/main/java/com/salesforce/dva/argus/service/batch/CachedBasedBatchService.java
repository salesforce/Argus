package com.salesforce.dva.argus.service.batch;

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

import java.util.*;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Default implementation of the batch service.
 *
 * @author Colby Guan (cguan@salesforce.com)
 */
public class CachedBasedBatchService extends DefaultService implements BatchService {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final String ROOT = "batch/";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Logger LOGGER = LoggerFactory.getLogger(BatchMetricQuery.class);

    //~ Instance fields ******************************************************************************************************************************

    private final SystemConfiguration _config;
    private final CacheService _cacheService;
    private final MetricQueueService _metricQueueService;

    //~ Constructors *********************************************************************************************************************************

    @Inject
    public CachedBasedBatchService (SystemConfiguration config, CacheService cacheService, MetricQueueService metricQueueService) {
        super(config);
        requireArgument(cacheService != null, "Cache service cannot be null.");
        requireArgument(metricQueueService != null, "Metric queue service cannot be null.");
        _config = config;
        _cacheService = cacheService;
        _metricQueueService = metricQueueService;
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
            BatchMetricQuery.Status status = BatchMetricQuery.Status.fromInt((Integer) batchData.get("status"));
            BatchMetricQuery.Priority priority = BatchMetricQuery.Priority.fromInt((Integer) batchData.get("priority"));
            int ttl = Integer.valueOf(batchData.get("ttl").toString());
            long createdDate = Long.valueOf(batchData.get("createdDate").toString());
            String ownerName = (String) batchData.get("ownerName");
            String batchId = (String) batchData.get("batchId");
            List<AsyncBatchedMetricQuery> queries = new ArrayList<>();

            Long[] queueIds = MAPPER.readValue((String) batchData.get("queueIds"), Long[].class);
            for (Long queueId: queueIds) {
                queries.add(_metricQueueService.findQueryById(priority.toInt(), queueId));
            }
            BatchMetricQuery batch = new BatchMetricQuery(status, priority, ttl, createdDate, batchId, ownerName, queries);
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
            // Put batch JSON to cache
            int ttl = batch.getTtl();
            String batchId = batch.getBatchId();
            batchData.put("priority", batch.getPriority().toInt());
            batchData.put("ttl", ttl);
            batchData.put("createdDate", batch.getCreatedDate());
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
            // Enforce cache TTL if batch status now changes to done
            if (oldStatus != newStatus && newStatus == BatchMetricQuery.Status.DONE) {
                _cacheService.put(ROOT + batchId, json , ttl);
                for (AsyncBatchedMetricQuery query: batch.getQueries()) {
                    _metricQueueService.updateQueryWithTtl(query, ttl);
                }
            } else {
                _cacheService.put(ROOT + batchId, json , Integer.MAX_VALUE);
            }

            // Update user JSON in cache
            String userBatchesJson = _cacheService.get(ROOT + batch.getOwnerName());
            Map<String,Object> userBatches;
            if (userBatchesJson == null) {
                userBatches = new HashMap<>();
                userBatches.put(batch.getBatchId(), newStatus.toInt());
            } else {
                userBatches = MAPPER.readValue(userBatchesJson, Map.class);
                userBatches.put(batch.getBatchId(), newStatus.toInt());
            }
            String updatedBatchesJson = MAPPER.writeValueAsString(userBatches);
            _cacheService.put(ROOT + batch.getOwnerName(), updatedBatchesJson, Integer.MAX_VALUE);
        } catch (Exception ex) {
            LOGGER.error("Exception in CacheBasedBatchService.saveToCache: {}", ex.toString());
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
            _cacheService.put(ROOT + ownerName, userBatchesJson, Integer.MAX_VALUE);
            return userBatches;
        } catch (Exception ex) {
            LOGGER.error("Exception in CachedBasedBatchServce.findBatchesByOwnerName: {}", ex.toString());
            return null;
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

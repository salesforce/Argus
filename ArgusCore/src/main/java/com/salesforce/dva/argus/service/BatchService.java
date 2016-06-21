package com.salesforce.dva.argus.service;

import com.salesforce.dva.argus.entity.BatchMetricQuery;

import java.util.Map;

/**
 * Created by cguan on 6/15/16.
 */
public interface BatchService {
    BatchMetricQuery findBatchById(String id);

    void updateBatch(BatchMetricQuery batch);

    Map<String, String> findBatchesByOwnerName(String ownerName);
}

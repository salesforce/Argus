package com.salesforce.dva.argus.service;

import com.salesforce.dva.argus.entity.AsyncBatchedMetricQuery;

/**
 * Created by cguan on 6/13/16.
 */
public interface MetricQueueService extends Service {

    void enqueue(AsyncBatchedMetricQuery query, int priority);

    AsyncBatchedMetricQuery dequeueAndProcess(int priority);

    AsyncBatchedMetricQuery findQueryById(int priority, long id);

    void updateQuery(AsyncBatchedMetricQuery query);

    void updateQueryWithTtl(AsyncBatchedMetricQuery query, int ttl);
}

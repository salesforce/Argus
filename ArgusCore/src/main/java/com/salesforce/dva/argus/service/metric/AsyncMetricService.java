package com.salesforce.dva.argus.service.metric;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.salesforce.dva.argus.entity.AsyncBatchedMetricQuery;
import com.salesforce.dva.argus.entity.BatchMetricQuery;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.BatchService;
import com.salesforce.dva.argus.service.CacheService;
import com.salesforce.dva.argus.service.MetricQueueService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.system.SystemConfiguration;

import java.util.List;

/**
 * Created by cguan on 6/1/16.
 */
public class AsyncMetricService extends DefaultMetricService {
    //~ Instance fields ******************************************************************************************************************************

    private final MetricQueueService _metricQueueService;
    private final CacheService _cacheService;
    private final BatchService _batchService;

    //~ Constructors *********************************************************************************************************************************

    @Inject
    protected AsyncMetricService(MonitorService monitorService, Provider<MetricReader<Metric>> metricsprovider,
                                 Provider<MetricReader<MetricQuery>> queryprovider, MetricQueueService metricQueueService,
                                 CacheService cacheService, BatchService batchService, SystemConfiguration config) {
        super(monitorService, metricsprovider, queryprovider, config);
        _metricQueueService = metricQueueService;
        _cacheService = cacheService;
        _batchService = batchService;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public String getAsyncMetrics(List<String> expressions, long offset, int ttl, String ownerName) {
        int priority = 1;
        BatchMetricQuery batchQuery = new BatchMetricQuery(expressions, offset, priority, ttl, ownerName);
        for (AsyncBatchedMetricQuery query: batchQuery.getQueries()) {
            _metricQueueService.enqueue(query, 1);
        }
        _batchService.updateBatch(batchQuery);
        return batchQuery.getBatchId();
    }
}

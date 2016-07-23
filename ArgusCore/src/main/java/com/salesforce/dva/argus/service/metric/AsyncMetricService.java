package com.salesforce.dva.argus.service.metric;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.salesforce.dva.argus.entity.BatchMetricQuery;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.BatchService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.system.SystemConfiguration;

import java.util.List;

/**
 * Asynchronous extension of the default metric service.
 *
 * @author  Colby Guan (cguan@salesforce.com)
 */
public class AsyncMetricService extends DefaultMetricService {

    //~ Instance fields ******************************************************************************************************************************

    private final BatchService _batchService;

    //~ Constructors *********************************************************************************************************************************

    @Inject
    protected AsyncMetricService(MonitorService monitorService, Provider<MetricReader<Metric>> metricsprovider,
                                 Provider<MetricReader<MetricQuery>> queryprovider,
                                 BatchService batchService, SystemConfiguration config) {
        super(monitorService, metricsprovider, queryprovider, config);
        _batchService = batchService;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public String getAsyncMetrics(List<String> expressions, long offset, int ttl, String ownerName) {
        BatchMetricQuery batch= new BatchMetricQuery(expressions, offset, ttl, ownerName);
        _batchService.enqueueBatch(batch);
        return batch.getBatchId();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

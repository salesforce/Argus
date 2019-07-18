package com.salesforce.dva.argus.service.metric;

import com.google.inject.Inject;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.MetricStorageService;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.system.SystemConfiguration;

import java.util.List;
import java.util.Map;
import java.util.Properties;

public class NoOperationMetricsStorageService extends DefaultService implements MetricStorageService {

    @Inject
    public NoOperationMetricsStorageService(SystemConfiguration config) {
        super(config);
    }

    @Override
    public void putMetrics(List<Metric> metrics) {}

    @Override
    public Map<MetricQuery, List<Metric>> getMetrics(List<MetricQuery> queries) {
        return null;
    }

}

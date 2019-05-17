package com.salesforce.dva.argus.querysnapshots;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.tsdb.DefaultTSDBService;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.system.SystemConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LoggingTSDBService extends DefaultTSDBService {
    Map<MetricQuery, List<Metric>> log;

    public LoggingTSDBService(SystemConfiguration config, MonitorService monitorService, Map<MetricQuery, List<Metric>> log) {
        super(config, monitorService);
        this.log = log;
    }

    @Override
    public Map<MetricQuery, List<Metric>> getMetrics(List<MetricQuery> queries) {
        Map<MetricQuery, List<Metric>> results = super.getMetrics(queries);
        // Deep clone everything
        results.forEach((query, metricList) -> {
            List<Metric> metricsListClone = new ArrayList<>();
            for (Metric metric: metricList) {
                metricsListClone.add(new Metric(metric));
            }
            log.put(new MetricQuery(query), metricsListClone);
        });
        return results;
    }
}

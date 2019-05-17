package com.salesforce.dva.argus.querysnapshots;

import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.service.schema.DefaultDiscoveryService;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.system.SystemConfiguration;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LoggingDiscoveryService extends DefaultDiscoveryService {
    Map<MetricQuery, List<MetricQuery>> log;

    public LoggingDiscoveryService(SchemaService schemaService, SystemConfiguration config, MonitorService monitorService, Map<MetricQuery, List<MetricQuery>> log) {
        super(schemaService, config, monitorService);
        this.log = log;
    }

    @Override
    public List<MetricQuery> getMatchingQueries(MetricQuery query) {
        List<MetricQuery> queries = super.getMatchingQueries(query);
        log.put(new MetricQuery(query), queries.stream().map(resultQuery -> new MetricQuery(resultQuery)).collect(Collectors.toList()));
        return queries;
    }
}

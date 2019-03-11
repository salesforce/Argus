package com.salesforce.dva.argus.querysnapshots;

import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.service.schema.DefaultDiscoveryService;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.system.SystemConfiguration;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class LoggingDiscoveryService extends DefaultDiscoveryService {
    Map<MetricQuery, List<MetricQuery>> log;

    public LoggingDiscoveryService(SchemaService schemaService, SystemConfiguration config, Map<MetricQuery, List<MetricQuery>> log) {
        super(schemaService, config);
        this.log = log;
    }

    @Override
    public List<MetricQuery> getMatchingQueries(MetricQuery query) {
        List<MetricQuery> queries = super.getMatchingQueries(query);
        log.put(query, queries);
        return queries;
    }
}

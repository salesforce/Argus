package com.salesforce.dva.argus.service.schema;

import com.google.inject.Inject;
import com.salesforce.dva.argus.entity.KeywordQuery;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.MetricSchemaRecord;
import com.salesforce.dva.argus.entity.MetricSchemaRecordQuery;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.system.SystemConfiguration;

import java.util.List;

/**
 * Schema service that does absolutely nothing. Meant as an available SchemaService binding for dependents
 * of ArgusCore that do not need a SchemaService.
 */
public class NoOperationSchemaService extends DefaultService implements SchemaService {

    @Inject
    public NoOperationSchemaService(SystemConfiguration config) {
        super(config);
    }

    @Override
    public void put(List<Metric> metrics) {
        throw new UnsupportedOperationException(NoOperationSchemaService.class.getName() + " does not support puts");
    }

    @Override
    public void put(Metric metric) {
        throw new UnsupportedOperationException(NoOperationSchemaService.class.getName() + " does not support puts");
    }

    @Override
    public List<MetricSchemaRecord> get(MetricSchemaRecordQuery query) {
        return null;
    }

    @Override
    public List<MetricSchemaRecord> getUnique(MetricSchemaRecordQuery query, RecordType type) {
        return null;
    }

    @Override
    public List<String> browseUnique(MetricSchemaRecordQuery query, RecordType type, int indexLevel) {
        return null;
    }

    @Override
    public List<MetricSchemaRecord> keywordSearch(KeywordQuery query) {
        return null;
    }
}

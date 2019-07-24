package com.salesforce.dva.argus.service.metric.transform;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.metric.metadata.MetadataService;
import com.salesforce.dva.argus.system.SystemException;
import com.salesforce.dva.argus.util.QueryContext;

import java.util.List;

/**
 * Remove metrics if the metadata from an external source (provided by MetadataService) matches
 * the user-provided lookup_value
 */
public class MetadataExclude implements Transform {
    private static final String TRANSFORM_NAME =  TransformFactory.Function.METADATA_EXCLUDE.name() + " transform";
    private static final String ERROR_MESSAGE_PREFIX = "METADATA_EXCLUDE is just a wrapper around the METADATA_INCLUDE transform. Error from METADATA_INCLUDE: \n";
    private MetadataService metadataService;

    public MetadataExclude(MetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @Override
    public List<Metric> transform(QueryContext queryContext, List<Metric> metrics, List<String> constants) {
        try {
            List<Metric> removedMetrics = new MetadataInclude(metadataService).processMetadataFilterTransform(metrics, constants, false);
            metrics.removeAll(removedMetrics);
        } catch (SystemException ex) {
            throw new SystemException(ERROR_MESSAGE_PREFIX + ex, ex);
        }
        return metrics;
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.EXCLUDE.name();
    }

    @Override
    public List<Metric> transform(QueryContext context, List<Metric> metrics) {
        throw new UnsupportedOperationException(String.format("%s expects 3 or 4 arguments %s", TRANSFORM_NAME, ERROR_MESSAGE_PREFIX + MetadataInclude.HELP_MESSAGE));
    }

    @Override
    public List<Metric> transform(QueryContext queryContext, List<Metric>... metrics) {
        throw new UnsupportedOperationException(String.format("%s expects 3 or 4 arguments %s", TRANSFORM_NAME, ERROR_MESSAGE_PREFIX + MetadataInclude.HELP_MESSAGE));
    }
}

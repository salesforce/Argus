package com.salesforce.dva.argus.service.metric.metadata;

import com.salesforce.dva.argus.entity.Metric;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public interface MetadataService {
    /**
     * Returns a mapping of each metric to whether its metadata lookupKey == lookupValue for the extracted resourceName
     * Returns defaultBoolean mapping for any downstream API calls that result in an error
     * eg. Returns true if the idb.host.operationalStatus of shared1-argushost == ACTIVE
     * eg. Returns true if the idb.cluster.environment of argus-dev == TESTING
     *
     * @param metrics               List of metrics to perform metadata equality check on
     * @param lookupKey             Key defining the field to lookup
     * @param lookupValue           The field value for the above key
     * @param defaultBoolean        The default value to return for downstream errors.
     *                              Should be "true" for INCLUDE transform, "false" for EXCLUDE transform
     * @param resourceNameExtractor Function that extracts the resource name (eg. hostname) from a Metric
     * @return
     */
    Map<Metric, Boolean> isMetadataEquals(List<Metric> metrics,
                                          String lookupKey,
                                          String lookupValue,
                                          boolean defaultBoolean,
                                          Function<Metric, String> resourceNameExtractor);
}

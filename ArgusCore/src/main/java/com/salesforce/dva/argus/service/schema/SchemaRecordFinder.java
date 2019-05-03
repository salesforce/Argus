package com.salesforce.dva.argus.service.schema;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The common interface for the existing schema record list classes when they are being used in
 * {@link ElasticSearchSchemaService#doBulkIndex(String, String, SchemaRecordFinder, ObjectMapper)} to retrieve individual
 * records by id.
 * @param <T>
 */
public interface SchemaRecordFinder<T> {
    T getRecord(String id);
}

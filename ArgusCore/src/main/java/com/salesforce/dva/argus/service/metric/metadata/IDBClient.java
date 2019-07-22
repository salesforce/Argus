package com.salesforce.dva.argus.service.metric.metadata;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * A bindable helper class for IDBMetadataService to connect to an IDB data source eg. REST API
 */
public interface IDBClient {
    /**
     * Pull IDB data and return a field value for each field query
     *
     * @param queries   Collection of individual queries to run
     * @return          A mapping of argument queries to an Optional String value of the IDBFieldQuery#field
     *                  The Optional will be empty/null to indicate an IDB I/O or response error for that query
     */
    Map<IDBFieldQuery, Optional<String>> get(Collection<IDBFieldQuery> queries);

    /**
     * IDBClient is meant to be a helper class, not a service, so this method is for any parent service to
     * pass properties back to SystemMain
     *
     * @return See DefaultIDBClient#Property
     */
    Properties getIDBClientProperties();
}

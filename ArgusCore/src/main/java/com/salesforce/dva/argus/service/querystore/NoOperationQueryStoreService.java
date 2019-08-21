package com.salesforce.dva.argus.service.querystore;

import com.google.inject.Inject;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.QueryStoreService;
import com.salesforce.dva.argus.system.SystemConfiguration;

import java.util.List;

/**
 * Query Store service that does absolutely nothing. Meant as an available QueryStoreService binding for dependents
 * of ArgusCore that do not need a QueryStoreService.
 */
public class NoOperationQueryStoreService extends DefaultService implements QueryStoreService {

    @Inject
    public NoOperationQueryStoreService(SystemConfiguration config) {
        super(config);
    }

    @Override
    public void putArgusWsQueries(List<Metric> metrics){ return;}

}

package com.salesforce.dva.argus.service.metric.metadata;

import com.google.inject.Inject;
import com.salesforce.dva.argus.service.CacheService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.system.SystemConfiguration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class CachedIDBClient extends DefaultIDBClient {
    static final String CACHE_KEY_PREFIX = "$idb.";
    static final String HOST_KEY_PREFIX = CACHE_KEY_PREFIX + "host";
    static final String CLUSTER_KEY_PREFIX = CACHE_KEY_PREFIX + "cluster";
    CacheService cacheService;
    MonitorService monitorService;
    final int ttlSeconds;

    @Inject
    public CachedIDBClient(SystemConfiguration config, CacheService cacheService, MonitorService monitorService) {
        super(config);
        ttlSeconds = Integer.valueOf(config.getValue(Property.IDB_CACHE_TTL_SECS.getName(),
                Property.IDB_CACHE_TTL_SECS.getDefaultValue()));
        this.cacheService = cacheService;
        this.monitorService = monitorService;
    }

    @Override
    public Map<IDBFieldQuery, Optional<String>> get(Collection<IDBFieldQuery> queries) {
        Map<IDBFieldQuery, Optional<String>> fieldValues = new HashMap<>();
        List<IDBFieldQuery> cacheMissedQueries = new ArrayList<>();
        queries.forEach(query -> {
            String fieldValue = cacheService.get(makeCacheKey(query));
            if (fieldValue == null) {
                cacheMissedQueries.add(query);
            } else {
                monitorService.modifyCounter(MonitorService.Counter.IDB_CLIENT_CACHE_HITS, 1, null);
                fieldValues.put(query, Optional.ofNullable(fieldValue));
            }
        });
        if (!cacheMissedQueries.isEmpty()) {
            Map<IDBFieldQuery, Optional<String>> cacheMissedFieldValues = onCacheMiss(cacheMissedQueries);
            cacheMissedFieldValues.forEach((query, fieldValue) -> {
                fieldValues.put(query, fieldValue);
                if (fieldValue.isPresent()) {
                    cacheService.put(makeCacheKey(query), fieldValue.get(), ttlSeconds);
                }
            });
        }
        return fieldValues;
    }

    String makeCacheKey(IDBFieldQuery query) {
        if (query.getType().equals(IDBFieldQuery.ResourceType.CLUSTER)) {
            return String.format("%s.%s.%s.%s", CLUSTER_KEY_PREFIX, query.getDatacenter(), query.getResourceName(), query.getField());
        } else {
            return String.format("%s.%s.%s.%s", HOST_KEY_PREFIX, query.getDatacenter(), query.getResourceName(), query.getField());
        }
    }

    // Separated for testability
    Map<IDBFieldQuery, Optional<String>> onCacheMiss(Collection<IDBFieldQuery> queries) {
        return super.get(queries);
    }
}

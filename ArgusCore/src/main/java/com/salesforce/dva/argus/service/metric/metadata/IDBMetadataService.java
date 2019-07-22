package com.salesforce.dva.argus.service.metric.metadata;

import com.google.inject.Inject;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.monitor.DefaultMonitorService;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class IDBMetadataService extends DefaultService implements MetadataService {
    public static final String IDB_HOST_FIELD_PREFIX = "idb.host.";
    public static final String IDB_CLUSTER_FIELD_PREFIX = "idb.cluster.";
    static final Set<String> VALID_IDB_HOST_FIELDS = new HashSet<>(Arrays.asList("operationalStatus"));
    static final Set<String> VALID_IDB_CLUSTER_FIELDS = new HashSet<>(Arrays.asList("operationalStatus", "environment", "dr"));
    Logger logger = LoggerFactory.getLogger(IDBMetadataService.class);
    MonitorService monitorService;
    IDBClient client;

    @Inject
    public IDBMetadataService(SystemConfiguration config, IDBClient client, MonitorService monitorService) {
        super(config);
        this.client = client;
        this.monitorService = monitorService;
    }

    @Override
    public Map<Metric, Boolean> isMetadataEquals(List<Metric> metrics, String lookupKey, String lookupValue, boolean defaultBool, Function<Metric, String> resourceNameExtractor) {
        String metadataField;
        Map<IDBFieldQuery, Metric> queries = new HashMap<>();
        if (lookupKey.startsWith(IDB_HOST_FIELD_PREFIX)) {
            metadataField = lookupKey.substring(IDB_HOST_FIELD_PREFIX.length());
            SystemAssert.requireArgument(VALID_IDB_HOST_FIELDS.contains(metadataField), "IDB field for host lookup should be one of " + VALID_IDB_HOST_FIELDS);
            metrics.forEach(metric -> {
                String resourceName = resourceNameExtractor.apply(metric);
                queries.put(new IDBFieldQuery(IDBFieldQuery.ResourceType.HOST, extractDatacenter(metric), resourceName, metadataField), metric);
            });
        } else if (lookupKey.startsWith(IDB_CLUSTER_FIELD_PREFIX)) {
            metadataField = lookupKey.substring(IDB_CLUSTER_FIELD_PREFIX.length());
            SystemAssert.requireArgument(VALID_IDB_CLUSTER_FIELDS.contains(metadataField), "IDB field for cluster lookup should be one of " + VALID_IDB_CLUSTER_FIELDS);
            metrics.forEach(metric -> {
                String resourceName = resourceNameExtractor.apply(metric);
                queries.put(new IDBFieldQuery(IDBFieldQuery.ResourceType.CLUSTER, extractDatacenter(metric), resourceName, metadataField), metric);
            });
        } else {
            throw new SystemException(String.format("lookup_key needs to start with '%s' or '%s'", IDB_HOST_FIELD_PREFIX, IDB_CLUSTER_FIELD_PREFIX));
        }
        long startTime = System.currentTimeMillis();
        Map<IDBFieldQuery, Optional<String>> fieldValues = client.get(queries.keySet());
        monitorService.modifyCounter(MonitorService.Counter.IDB_CLIENT_GET_LATENCY, System.currentTimeMillis() - startTime, null);
        monitorService.modifyCounter(MonitorService.Counter.IDB_CLIENT_QUERY_COUNT, queries.size(), null);
        monitorService.modifyCounter(MonitorService.Counter.IDB_CLIENT_GET_COUNT, 1, null);
        Map<Metric, Boolean> equalities = new HashMap<>();
        fieldValues.forEach((query, fieldValue) -> {
            if (fieldValue.isPresent()) {
                equalities.put(queries.get(query), fieldValue.get().equals(lookupValue));
            } else {
                equalities.put(queries.get(query), defaultBool);
            }
        });
        return equalities;
    }

    String extractDatacenter(Metric metric) {
        String scope = metric.getScope();

        String[] tokens = scope.split("\\.");
        String datacenter;
        if (tokens.length <= 4 && tokens.length >= 2) {
            datacenter = tokens[1];
            if (!isValidDatacenter(datacenter)) {
                throw new SystemException(String.format("For IDB lookup, expected the 2nd token in the scope to be a 3-char datacenter name for metric %s, but got token: %s", metric.getIdentifier(), datacenter));
            }
        } else if (tokens.length < 2) {
            throw new SystemException(String.format("For IDB lookup, cannot have less than 2 period-separated tokens in the scope; expecting a 3-char datacenter name as the 2nd token for metric %s", metric.getIdentifier()));
        } else {
            datacenter = tokens[1];
            if (!isValidDatacenter(datacenter)) {
                datacenter = tokens[tokens.length - 3];
                if (!isValidDatacenter(datacenter)) {
                    throw new SystemException(String.format("For IDB lookup, expected a 3-char datacenter name as either the 2nd token OR as the 2-before-the-last token in the scope, for metric %s", metric.getIdentifier()));
                }
            }
        }
        return datacenter;
    }

    boolean isValidDatacenter(String datacenter) {
        return datacenter.length() == 3;
    }

    @Override
    public Properties getServiceProperties() {
        return client.getIDBClientProperties();
    }
}

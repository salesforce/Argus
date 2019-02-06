package com.salesforce.dva.argus.util;

import java.util.HashMap;
import java.util.Map;

import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.metric.MetricQueryResult;

public class MonitoringUtils {

    public static final String ALERT_DATA_READ_NUM_TIME_SERIES = "perf.alert.read.num.time.series";

    public static final String ALERT_DATA_READ_NUM_DISCOVERY_RESULTS = "perf.alert.read.num.discovery.results";

    public static final String ALERT_DATA_READ_NUM_DISCOVERY_QUERIES = "perf.alert.read.num.discovery.queries";
    
    public static final String ALERT_QUERY_COUNT = "perf.alert.read.count";

    public static final String TAGS_USER_KEY = "user";

    public static final String TAGS_TIME_WINDOW_KEY = "timeWindow";

    public static final String TAGS_EXPANDED_TIME_SERIES_RANGE_KEY = "expandedTimeSeriesRange";

    public static final String TAGS_START_TIME_WINDOW_KEY = "startTimeWindow";

    public static void updateAlertMetricQueryPerfCounters(MonitorService monitorService, MetricQueryResult queryResult, String alertOwner) {
        Map<String, String> tags = new HashMap<String, String>();
        tags.put(TAGS_USER_KEY, alertOwner);
        tags.put(TAGS_TIME_WINDOW_KEY, queryResult.getQueryTimeWindow());
        tags.put(TAGS_EXPANDED_TIME_SERIES_RANGE_KEY, queryResult.getExpandedTimeSeriesRange());
        tags.put(TAGS_START_TIME_WINDOW_KEY, queryResult.getQueryStartTimeWindow());

        monitorService.modifyCustomCounter(ALERT_DATA_READ_NUM_TIME_SERIES, queryResult.getNumTSDBResults(), tags);
        monitorService.modifyCustomCounter(ALERT_DATA_READ_NUM_DISCOVERY_RESULTS, queryResult.getNumDiscoveryResults(), tags);
        monitorService.modifyCustomCounter(ALERT_DATA_READ_NUM_DISCOVERY_QUERIES, queryResult.getNumDiscoveryQueries(), tags);
        monitorService.modifyCustomCounter(ALERT_QUERY_COUNT, 1, tags);
    }
}

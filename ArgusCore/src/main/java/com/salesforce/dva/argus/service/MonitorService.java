/*
 * Copyright (c) 2016, Salesforce.com, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.salesforce.dva.argus.service;

import com.salesforce.dva.argus.entity.Dashboard;
import com.salesforce.dva.argus.entity.Metric;

import java.util.Map;

/**
 * Provides methods to update and record system counters to be used in monitoring and alerting. All counters are reset after their values have been
 * persisted to TSDB. All counters must be recorded for the JVM in which the service is running facilitating system wide aggregation at the alert
 * level.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
public interface MonitorService extends Service {

    //~ Methods **************************************************************************************************************************************

    /** Enables monitoring across all servers. */
    void enableMonitoring();

    /** Disables monitoring across all servers. */
    void disableMonitoring();

    /** Starts recording counters to TSDB in the background. */
    void startRecordingCounters();

    /** Stops recording counters. */
    void stopRecordingCounters();

    /**
     * Replaces the value of a counter.
     *
     * @param  counter  The counter to update. Cannot be null.
     * @param  value    The new value.
     * @param  tags     The tags representing the TSDB metric for this counter.
     */
    void updateCounter(Counter counter, double value, Map<String, String> tags);

    /**
     * Replaces the value of a custom counter.
     *
     * @param  name   The name of the counter to update. Cannot be null.
     * @param  value  The new value.
     * @param  tags   The tags representing the TSDB metric for this counter.
     */
    void updateCustomCounter(String name, double value, Map<String, String> tags);

    /**
     * Modifies the value of a counter.
     *
     * @param   counter  The counter to update. Cannot be null.
     * @param   delta    The amount of change to apply to the counter.
     * @param   tags     The tags representing the TSDB metric for this counter.
     *
     * @return  The updated counter value.
     */
    double modifyCounter(Counter counter, double delta, Map<String, String> tags);

    /**
     * Modifies the value of a custom counter.
     *
     * @param   name   The name of the counter to update. Cannot be null.
     * @param   delta  The amount of change to apply to the counter.
     * @param   tags   The tags representing the TSDB metric for this counter.
     *
     * @return  The updated counter value.
     */
    double modifyCustomCounter(String name, double delta, Map<String, String> tags);

    /**
     * Returns the current value of a counter.
     *
     * @param   counter  The counter for which to retrieve the value. Cannot be null or empty.
     * @param   tags     The tags representing the TSDB metric for this counter.
     *
     * @return  The current value of the counter. Will not be null.
     */
    double getCounter(Counter counter, Map<String, String> tags);

    /**
     * Returns the current value of a custom counter.
     *
     * @param   name  The name of the counter for which to retrieve the value. Cannot be null or empty.
     * @param   tags  The tags representing the TSDB metric for this counter.
     *
     * @return  The current value of the counter. Will not be null.
     */
    double getCustomCounter(String name, Map<String, String> tags);

    /** Resets all custom counter values. */
    void resetCustomCounters();

    /** Resets all system counter values. */
    void resetSystemCounters();

    /** Resets all runtime counter values. */
    void resetRuntimeCounters();

    /**
     * Returns the system dashboard. All metrics are aggregated across hosts.
     *
     * @return  The system dashboard. Will not be null.
     */
    Dashboard getSystemDashboard();

    /**
     * Returns the host specific runtime dashboard.
     *
     * @return  The runtime dashboard. Will not be null.
     */
    Dashboard getRuntimeDashboard();

    /**
     * Returns boolean to indicate whether the data is lagging currently or not
     *
     * @return isDataLagging boolean flag
     */
    boolean isDataLagging(String dataCenter);

	/**
	 * This is helper function so that we can export metrics to JMX metric exporter everywhere in the
	 * system
	 *
	 * @param metric the metric to export
	 * @param value  the value datapoint of the metric to be export
	 */
	void exportMetric(Metric metric, Double value);

    //~ Enums ****************************************************************************************************************************************

    /**
     * The supported counter types. Metric name for the counter must be unique even across scopes.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public static enum Counter {

        ACTIVE_CORES("argus.jvm", "cores.active"),
        LOADED_CLASSES("argus.jvm", "classes.loaded"),
        UNLOAED_CLASSES("argus.jvm", "classes.unloaded"),
        MARKSWEEP_COUNT("argus.jvm", "gc.count.marksweep"),
        SCAVENGE_COUNT("argus.jvm", "gc.count.scavenge"),
        HEAP_USED("argus.jvm", "mem.heap.used"),
        NONHEAP_USED("argus.jvm", "mem.nonheap.used"),
        CODECACHE_USED("argus.jvm", "mem.ccache.used"),
        EDEN_USED("argus.jvm", "mem.eden.used"),
        OLDGEN_USED("argus.jvm", "mem.oldgen.used"),
        PERMGEN_USED("argus.jvm", "mem.permgen.used"),
        SURVIVOR_USED("argus.jvm", "mem.survivor.used"),
        FREE_PHYSICAL_MEM("argus.jvm", "mem.physical.free"),
        FREE_SWAP_SPACE("argus.jvm", "mem.swap.free"),
        MAX_PHYSICAL_MEM("argus.jvm", "mem.physical.max"),
        MAX_SWAP_SPACE("argus.jvm", "mem.swap.max"),
        OPEN_DESCRIPTORS("argus.jvm", "file.descriptor.open"),
        MAX_DESCRIPTORS("argus.jvm", "file.descriptor.max"),
        THREADS("argus.jvm", "thread.used"),
        PEAK_THREADS("argus.jvm", "thread.peak"),
        DAEMON_THREADS("argus.jvm", "thread.daemon"),
        MBEANSERVER_MBEAN_TOTAL("argus.jvm", "mbeanserver.mbean.total", MetricType.COUNTER),
        METRIC_WRITES("argus.core", "metric.writes", MetricType.COUNTER),
        ANNOTATION_WRITES("argus.core", "annotation.writes", MetricType.COUNTER),
        ANNOTATION_DROPS_MAXSIZEEXCEEDED("argus.core", "annotation.drops.maxSizeExceeded", MetricType.COUNTER),
        HISTOGRAM_WRITES("argus.core", "histogram.writes", MetricType.COUNTER),
        HISTOGRAM_DROPPED("argus.core", "histogram.dropped", MetricType.COUNTER),
        METRIC_READS("argus.core", "metric.reads", MetricType.COUNTER),
        ANNOTATION_READS("argus.core", "annotation.reads", MetricType.COUNTER),
        WARDEN_TRIGGERS("argus.core", "triggers.warden", MetricType.COUNTER),
        SYSTEM_TRIGGERS("argus.core", "triggers.system", MetricType.COUNTER),
        USER_TRIGGERS("argus.core", "triggers.user", MetricType.COUNTER),
        JOBS_SCHEDULED("argus.core", "jobs.scheduled", MetricType.COUNTER),
        JOBS_MAX("argus.core", "jobs.max"),
        ALERTS_KPI("argus.core", "alerts.kpi"),
        ALERTS_ENABLED("argus.core", "alerts.enabled"),
        ALERTS_SCHEDULED("argus.core", "alerts.scheduled", MetricType.COUNTER),
        ALERTS_SCHEDULED_TOTAL("argus.core", "alerts.scheduled.total", MetricType.COUNTER),
        ALERTS_SCHEDULING_QUEUE_SIZE("argus.core", "alerts.scheduleQueue.size"),
        ALERTS_EVALUATED("argus.core", "alerts.evaluated", MetricType.COUNTER),
        ALERTS_EVALUATED_RAWTOTAL("argus.core", "alerts.evaluated.rawtotal", MetricType.COUNTER),
        ALERTS_EVALUATED_TOTAL("argus.core", "alerts.evaluated.total", MetricType.COUNTER),
        ALERTS_EVALUATION_STARTED("argus.alerts", "evaluation.started", MetricType.COUNTER),
        ALERTS_EVALUATION_DELAYED("argus.alerts", "evaluation.delayed", MetricType.COUNTER),
        ALERTS_FAILED("argus.core", "alerts.failed", MetricType.COUNTER),
        ALERTS_EVALUATION_LATENCY("argus.core", "alerts.evaluation.latency", MetricType.COUNTER),
        ALERTS_UPDATE_LATENCY("argus.core","alerts.update.latency"),
        ALERTS_NEW_LATENCY("argus.core","alerts.new.latency"),
        ALERTS_UPDATED_COUNT("argus.core","alerts.updated", MetricType.COUNTER),
        ALERTS_CREATED_COUNT("argus.core","alerts.created", MetricType.COUNTER),
        ALERTS_SKIPPED("argus.core", "alerts.skipped", MetricType.COUNTER),
        TRANSFORMS_EVALUATED("argus.core", "transforms.evaluated", MetricType.COUNTER),
        NOTIFICATIONS_SENT("argus.core", "notifications.sent", MetricType.COUNTER),
        GOC_NOTIFICATIONS_FAILED("argus.core", "notifications.failed.goc", MetricType.COUNTER),
        GUS_NOTIFICATIONS_FAILED("argus.core", "notifications.failed.gus", MetricType.COUNTER),
        PAGERDUTY_NOTIFICATIONS_FAILED("argus.core", "notifications.failed.pagerduty", MetricType.COUNTER),
        GOC_NOTIFICATIONS_RETRIES("argus.core", "notifications.retries.goc", MetricType.COUNTER),
        GUS_NOTIFICATIONS_RETRIES("argus.core", "notifications.retries.gus", MetricType.COUNTER),
        PAGERDUTY_NOTIFICATIONS_RETRIES("argus.core", "notifications.retries.pagerduty", MetricType.COUNTER),
        TRIGGERS_VIOLATED("argus.core", "triggers.violated", MetricType.COUNTER),
        ALERTS_MAX("argus.core", "alerts.max",MetricType.COUNTER),
        ALERT_EVALUATION_KPI("argus.core", "alert.evaluation.kpi", MetricType.COUNTER),
        DATAPOINT_READS("argus.core", "datapoint.reads", MetricType.COUNTER),
        DATAPOINT_WRITES("argus.core", "datapoint.writes", MetricType.COUNTER),
        UNIQUE_USERS("argus.core", "users.unique", MetricType.COUNTER),
        DAILY_USERS("argus.core", "users.daily", MetricType.COUNTER),
        MONTHLY_USERS("argus.core", "users.monthly", MetricType.COUNTER),
        COMMIT_CLIENT_DATAPOINT_WRITES("argus.core", "commit.client.datapoint.writes", MetricType.COUNTER),
        COMMIT_CLIENT_METRIC_WRITES("argus.core", "commit.client.metric.writes", MetricType.COUNTER),
        SCHEMACOMMIT_CLIENT_METRIC_WRITES("argus.core", "schemacommit.client.metric.writes", MetricType.COUNTER),

        // MORE FINE GRAIN ALERT EVALUATION TIMERS
        METRICQUERYPROCESSOR_EVALUATETSDBQUERY_LATENCY("argus.core", "metricqueryprocessor.evaluatetsdbquery.latency", MetricType.COUNTER),
        METRICQUERYPROCESSOR_EVALUATETSDBQUERY_COUNT("argus.core", "metricqueryprocessor.evaluatetsdbquery.count", MetricType.COUNTER),
        METRICS_GETMETRICS_LATENCY("argus.core", "metrics.getmetrics.latency", MetricType.COUNTER),
        METRICS_GETMETRICS_COUNT("argus.core", "metrics.getmetrics.count", MetricType.COUNTER),
        REDISCACHE_GET_LATENCY("argus.core", "rediscache.get.latency", MetricType.COUNTER),
        REDISCACHE_GET_COUNT("argus.core", "rediscache.get.count", MetricType.COUNTER),
        ALERTS_EVALUATION_ONLY_LATENCY("argus.core", "alerts.evaluation.nonotification.latency", MetricType.COUNTER),
        ALERTS_EVALUATION_LATENCY_COUNT("argus.core", "alerts.evaluation.timer.count", MetricType.COUNTER),

        SCOPEANDMETRICNAMES_WRITTEN("argus.core", "scopeandmetricnames.written", MetricType.COUNTER),
        SCOPEANDMETRICNAMES_WRITE_LATENCY("argus.core", "scopeandmetricnames.write.latency"),
        SCOPEANDMETRICNAMES_QUERY_COUNT("argus.core", "scopeandmetricnames.query.count", MetricType.COUNTER),
        SCOPEANDMETRICNAMES_QUERY_LATENCY("argus.core", "scopeandmetricnames.query.latency"),

        QUERYSTORE_RECORDS_WRITTEN("argus.core", "querystore.records.written", MetricType.COUNTER),
        QUERYSTORE_RECORDS_WRITE_LATENCY("argus.core", "querystore.records.write.latency"),

        SCOPENAMES_WRITTEN("argus.core", "scopenames.written", MetricType.COUNTER),
        SCOPENAMES_WRITE_LATENCY("argus.core", "scopenames.write.latency"),
        SCOPENAMES_QUERY_COUNT("argus.core", "scopenames.query.count", MetricType.COUNTER),
        SCOPENAMES_QUERY_LATENCY("argus.core", "scopenames.query.latency"),

        SCHEMARECORDS_WRITTEN("argus.core", "schemarecords.written", MetricType.COUNTER),
        SCHEMARECORDS_WRITE_LATENCY("argus.core", "schemarecords.write.latency"),
        SCHEMARECORDS_QUERY_COUNT("argus.core", "schemarecords.query.count", MetricType.COUNTER),
        SCHEMARECORDS_DOCS_PULLED("argus.core", "schemarecords.docs.pulled.count", MetricType.COUNTER),
        SCHEMARECORDS_QUERY_LATENCY("argus.core", "schemarecords.query.latency"),

        METATAGS_WRITTEN("argus.core", "metatags.written", MetricType.COUNTER),
        METATAGS_WRITE_LATENCY("argus.core", "metatags.write.latency"),

        BLOOM_CREATED_APPROXIMATE_ELEMENT_COUNT("argus.core", "bloomfilter.created.approximate.element.count", MetricType.COUNTER),
        BLOOM_MODIFIED_APPROXIMATE_ELEMENT_COUNT("argus.core", "bloomfilter.modified.approximate.element.count", MetricType.COUNTER),

        QUERY_STORE_BLOOM_CREATED_APPROXIMATE_ELEMENT_COUNT("argus.core", "querystore.bloomfilter.created.approximate.element.count", MetricType.COUNTER),

        DATALAG_PER_DC_TIME_LAG("argus.core", "datalag.seconds"),
        DATALAG_PER_DC_OFFSET_LAG("argus.core", "datalag.offset"),
        QUERY_DATAPOINTS_LIMIT_EXCEEDED("argus.core", "query.datapoints.limit.exceeded"),

        ELASTIC_SEARCH_GET_FAILURES("argus.core", "elastic.search.get.failures", MetricType.COUNTER),

        CONSUMER_OFFSET_RECORDS_WRITE_FAILURES("argus.core", "consumer.offset.records.write.failures", MetricType.COUNTER),
        CONSUMER_OFFSET_RECORDS_WRITE_LATENCY("argus.core", "consumer.offset.records.write.latency", MetricType.COUNTER),
        CONSUMER_OFFSET_RECORDS_READ_LATENCY("argus.core", "consumer.offset.records.read.latency", MetricType.COUNTER),


        ANNOTATION_RECORDS_WRITE_FAILURES("argus.core", "annotation.records.write.failures", MetricType.COUNTER),
        IMAGE_RECORDS_WRITE_FAILURES("argus.core", "image.records.write.failures", MetricType.COUNTER);

        private final String _scope;
        private final String _metric;
        private final MetricType _type;
        private final String _jmxMetricNameSuffix;

        /**
         * Creates a new Counter object.
         *
         * @param  scope   The counter scope name.
         * @param  metric  The corresponding metric name.
         * @param  type    The corresponding metric type.
         * @param  jmxMetricNameSuffix This will be appended to the JMX metric name used by Prometheus.
         */
        Counter(String scope, String metric, MetricType type, String jmxMetricNameSuffix) {
            _scope = scope;
            _metric = metric;
            _type = type;
            _jmxMetricNameSuffix = jmxMetricNameSuffix;
        }

        /**
         * Creates a new Counter object.
         *
         * @param  scope   The counter scope name.
         * @param  metric  The corresponding metric name.
         * @param  type    The corresponding metric type.
         */
        Counter(String scope, String metric, MetricType type) {
            this(scope, metric, type, MetricType.COUNTER == type ? ".count" : "");
        }

        /**
         * Creates a new Counter object.
         *
         * @param  scope   The counter scope name.
         * @param  metric  The corresponding metric name.
         */
        Counter(String scope, String metric) {
            this(scope, metric, MetricType.GAUGE, "");
        }

        /**
         * Retrieves a counter given its metric name.
         *
         * @param   metricName  The metric name.
         *
         * @return  The corresponding counter or null if no counter exists for the metric name.
         */
        public static Counter fromMetricName(String metricName) {
            for (Counter counter : Counter.values()) {
                if (counter.getMetric().equals(metricName)) {
                    return counter;
                }
            }
            return null;
        }

        /**
         * Retrieves the scope for the counter.
         *
         * @return  The scope for the counter. Will not be null.
         */
        public String getScope() {
            return _scope;
        }

        /**
         * Retrieves the metric name for the counter.
         *
         * @return  The metric name for the counter. Will not be null.
         */
        public String getMetric() {
            return _metric;
        }

        /**
         * Retrieves the metric type for the counter.
         *
         * @return  The metric type for the counter. Will not be null.
         */
        public MetricType getMetricType() {
            return _type;
        }

        public String getJMXMetricNameSuffix() {return _jmxMetricNameSuffix; }

        public static enum MetricType {
            COUNTER,
            GAUGE,
            TIMER
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

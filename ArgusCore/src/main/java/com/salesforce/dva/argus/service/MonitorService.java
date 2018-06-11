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
    boolean isDataLagging();

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
        METRIC_WRITES("argus.core", "metric.writes"),
        ANNOTATION_WRITES("argus.core", "annotation.writes"),
        METRIC_READS("argus.core", "metric.reads"),
        ANNOTATION_READS("argus.core", "annotation.reads"),
        WARDEN_TRIGGERS("argus.core", "triggers.warden"),
        SYSTEM_TRIGGERS("argus.core", "triggers.system"),
        USER_TRIGGERS("argus.core", "triggers.user"),
        JOBS_SCHEDULED("argus.core", "jobs.scheduled"),
        JOBS_MAX("argus.core", "jobs.max"),
        ALERTS_ENABLED("argus.core", "alerts.enabled"),
        ALERTS_SCHEDULED("argus.core", "alerts.scheduled"),
        ALERTS_SCHEDULING_QUEUE_SIZE("argus.core", "alerts.scheduleQueue.size"),
        ALERTS_EVALUATED("argus.core", "alerts.evaluated"),
        ALERTS_FAILED("argus.core", "alerts.failed"),
        ALERTS_EVALUATION_LATENCY("argus.core", "alerts.evaluation.latency"),
        ALERTS_SKIPPED("argus.core", "alerts.skipped"),
        NOTIFICATIONS_SENT("argus.core", "notifications.sent"),
        TRIGGERS_VIOLATED("argus.core", "triggers.violated"),
        ALERTS_MAX("argus.core", "alerts.max"),
        DATAPOINT_READS("argus.core", "datapoint.reads"),
        DATAPOINT_WRITES("argus.core", "datapoint.writes"),
        UNIQUE_USERS("argus.core", "users.unique"),
        DAILY_USERS("argus.core", "users.daily"),
        MONTHLY_USERS("argus.core", "users.monthly"),
        COMMIT_CLIENT_DATAPOINT_WRITES("argus.core", "commit.client.datapoint.writes"),
    	COMMIT_CLIENT_METRIC_WRITES("argus.core", "commit.client.metric.writes"),
        SCHEMACOMMIT_CLIENT_METRIC_WRITES("argus.core", "schemacommit.client.metric.writes"),
    	SCHEMARECORDS_WRITTEN("argus.core", "schemarecords.written"),
    	SCHEMARECORDS_WRITE_LATENCY("argus.core", "schemarecords.write.latency"),
    	SCHEMARECORDS_QUERY_COUNT("argus.core", "schemarecords.query.count"),
    	SCHEMARECORDS_QUERY_LATENCY("argus.core", "schemarecords.query.latency");

        private final String _scope;
        private final String _metric;

        /**
         * Creates a new Counter object.
         *
         * @param  scope   The counter scope name.
         * @param  metric  The corresponding metric name.
         */
        Counter(String scope, String metric) {
            _scope = scope;
            _metric = metric;
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
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

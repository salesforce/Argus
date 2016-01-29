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
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.entity.Trigger.TriggerType;
import java.util.Map;

import static com.salesforce.dva.argus.service.WardenService.SubSystem.POSTING;

/**
 * Provides methods to updated counters associated with usage policies. This service will suspend users permissions to use a specific sub-system if
 * they violate that sub-systems policies. Suspensions are temporary and are lifted after the suspension expires. Repeat offenders can be suspended
 * indefinitely requiring an explicit operation to remove the suspension.
 *
 * <p>This service shall be used as a singleton within the Argus system. All methods that modify or update a policy counter must perform the following
 * actions:</p>
 *
 * <ul>
 *   <li>Create a set of warden specific alerts for the user indicated in the method, if none already exist, that use the warden notifier.</li>
 *   <li>Enable the warden specific alerts for any users that have had a counter updated in the trailing one hour period and whose alerts are
 *     disabled.</li>
 *   <li>Disable the warden specific alerts for any users that have not had a counter updated in the trailing one hour period and whose alerts are
 *     enabled.</li>
 * </ul>
 *
 * <p>All warden specific alerts must be scheduled at 5 minute intervals, but may be scheduled in an offset manner so as to distribute system load.
 * </p>
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public interface WardenService extends Service {

    //~ Methods **************************************************************************************************************************************

    /** Enables warden across all instances. */
    void enableWarden();

    /** Disables warden across all instances. */
    void disableWarden();

    /**
     * Checks if warden is enabled.
     *
     * @return  True if warden is enabled.
     */
    boolean isWardenServiceEnabled();

    /**
     * Updates the policy limits for a user.
     *
     * @param  user     The user for which the policy will be updated. Cannot be null.
     * @param  counter  The policy counter for which the limit will be set. Cannot be null.
     * @param  value    The new value for the policy limit for the user.
     */
    void updatePolicyLimitForUser(PrincipalUser user, PolicyCounter counter, double value);

    /**
     * Sets the suspension level for a given subsystem.
     *
     * @param  subSystem        The subsystem. Cannot be null.
     * @param  infractionCount  The infraction count threshold. Must be greater than zero.
     * @param  durationMillis   The suspension duration in milliseconds. Must be greater than zero.
     */
    void updateSuspensionLevel(SubSystem subSystem, int infractionCount, long durationMillis);

    /**
     * Sets the suspension levels for a given subsystem.
     *
     * @param  subSystem         The subsystem. Cannot be null.
     * @param  infractionCounts  The infraction count thresholds and durations.
     */
    void updateSuspensionLevels(SubSystem subSystem, Map<Integer, Long> infractionCounts);

    /**
     * Replaces the value of a counter.
     *
     * @param  user     The user for which the counter will be updated. Cannot be null.
     * @param  counter  The counter to update. Cannot be null.
     * @param  value    The new value.
     */
    void updatePolicyCounter(PrincipalUser user, PolicyCounter counter, double value);

    /**
     * Modifies the value of a counter.
     *
     * @param   user     The user for which the counter will be updated. Cannot be null.
     * @param   counter  The counter to update. Cannot be null.
     * @param   delta    The amount of change to apply to the counter.
     *
     * @return  The updated counter value.
     */
    double modifyPolicyCounter(PrincipalUser user, PolicyCounter counter, double delta);

    /**
     * Determines if a user is permitted to use a sub-system based on prior policy violations. This method shall throw a runtime exception if the user
     * does not have permission to use the specified sub-system. This method shall never throw an exception for administrative users.
     *
     * @param  user       The user for which to evaluate sub-system permission. Cannot be null.
     * @param  subSystem  The sub-system for which to evaluate permission. Cannot be null.
     */
    void assertSubSystemUsePermitted(PrincipalUser user, SubSystem subSystem);

    /**
     * Suspends a user for the particular sub-system.
     *
     * @param   user       The user to suspend. Cannot be null.
     * @param   subSystem  The sub-system for which to suspend the users permissions. Cannot be null.
     *
     * @return  True if the suspension resulted in a permanent suspension.
     */
    boolean suspendUser(PrincipalUser user, SubSystem subSystem);

    /**
     * Reinstates the user permissions for the particular sub-system.
     *
     * @param  user       The user to reinstate. Cannot be null.
     * @param  subSystem  The sub-system for which the user permissions will be reinstated. Cannot be null.
     */
    void reinstateUser(PrincipalUser user, SubSystem subSystem);

    /**
     * Returns the Warden dashboard for a given user.
     *
     * @param   user  The user for which to retrieve the warden dashboard for.
     *
     * @return  The warden dashboard for the user. Will never be null.
     */
    Dashboard getWardenDashboard(PrincipalUser user);

    //~ Enums ****************************************************************************************************************************************

    /**
     * The supported policy types.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public static enum PolicyCounter {

        METRICS_PER_HOUR("warden.metrics_per_hour", 1100, POSTING, "sum", TriggerType.GREATER_THAN),
        DATAPOINTS_PER_HOUR("warden.datapoints_per_hour", 11000, POSTING, "sum", TriggerType.GREATER_THAN),
        MINIMUM_RESOLUTION_MS("warden.min_resolution_in_millis", 60000, POSTING, "min", TriggerType.LESS_THAN);

        private final String _metricName;
        private final double _defaultValue;
        private final SubSystem _subSystem;
        private final String _aggregator;
        private final TriggerType _triggerType;

        /**
         * Creates a new policy object.
         *
         * @param  metricName    The corresponding metric name.
         * @param  defaultValue  The default value for the policy.
         * @param  subSystem     The sub-system to which the policy applies.
         * @param  aggregator    The aggregator used to aggregate data points across different time series for this metric.
         * @param  triggerType   The trigger type that is associated for this policy counter
         */
        PolicyCounter(String metricName, double defaultValue, SubSystem subSystem, String aggregator, TriggerType triggerType) {
            _metricName = metricName;
            _defaultValue = defaultValue;
            _subSystem = subSystem;
            _aggregator = aggregator;
            _triggerType = triggerType;
        }

        /**
         * Retrieves a policy given its metric name.
         *
         * @param   metricName  The metric name.
         *
         * @return  The corresponding policy or null if no policy exists for the metric name.
         */
        public static PolicyCounter fromMetricName(String metricName) {
            for (PolicyCounter policy : PolicyCounter.values()) {
                if (policy.getMetricName().equals(metricName)) {
                    return policy;
                }
            }
            return null;
        }

        /**
         * Returns the sub-system to which the policy applies.
         *
         * @return  The sub-system to which the policy applies.
         */
        public SubSystem getSubSystem() {
            return _subSystem;
        }

        /**
         * Retrieves the metric name for the policy.
         *
         * @return  The metric name for the policy. Will not be null.
         */
        public String getMetricName() {
            return _metricName;
        }

        /**
         * Returns the default value for the policy.
         *
         * @return  The default value for the policy.
         */
        public double getDefaultValue() {
            return _defaultValue;
        }

        /**
         * Returns the aggregator for the policy.
         *
         * @return  The aggregator for the policy.
         */
        public String getAggregator() {
            return _aggregator;
        }

        /**
         * Returns the type of trigger that is associated for this policy.
         *
         * @return  The type of trigger that is associated for this policy.
         */
        public TriggerType getTriggerType() {
            return _triggerType;
        }
    }

    /**
     * The sub-system categories with which policies may be associated.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public static enum SubSystem {

        POSTING,
        API
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

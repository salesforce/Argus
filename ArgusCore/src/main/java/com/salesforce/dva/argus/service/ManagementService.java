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
import com.salesforce.dva.argus.service.WardenService.PolicyCounter;
import com.salesforce.dva.argus.service.WardenService.SubSystem;
import java.util.Map;

/**
 * Provides methods used to manage the Argus system.
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public interface ManagementService extends Service {

    //~ Methods **************************************************************************************************************************************

    /**
     * Grants or revokes administrator privileges to a user.
     *
     * @param  user          The user to modify.
     * @param  isPrivileged  Indicates whether privileges should be granted or revoked.
     */
    void setAdministratorPrivilege(PrincipalUser user, boolean isPrivileged);

    /**
     * @param  user     The user for which the policy will be updated. Cannot be null.
     * @param  counter  The policy counter for which the limit will be set. Cannot be null.
     * @param  value    The new value for the policy limit for the user.
     * @see  WardenService#updatePolicyLimitForUser(PrincipalUser, PolicyCounter, double)  */
    void updateWardenPolicyForUser(PrincipalUser user, PolicyCounter counter, double value);

    /**
     * @param  user       The user to reinstate. Cannot be null.
     * @param  subSystem  The sub-system for which the user permissions will be reinstated. Cannot be null.
     * @see  WardenService#reinstateUser(PrincipalUser, SubSystem) */
    void reinstateUser(PrincipalUser user, SubSystem subSystem);

    /**
     * @param  subSystem         The subsystem. Cannot be null.
     * @param  infractionCounts  The infraction count thresholds and durations.
     * @see  WardenService#updateSuspensionLevels(SubSystem, Map) */
    void updateWardenSuspensionLevelsAndDurations(SubSystem subSystem, Map<Integer, Long> infractionCounts);

    /**
     * Updates suspension levels for all subsystems.
     *
     * @param  levelToDurationMap  Maps the infraction count to suspension duration in milliseconds. Cannot be null.
     *
     * @see    WardenService#updateSuspensionLevels(SubSystem, Map)
     */
    void updateWardenSuspensionLevelsAndDurations(Map<Integer, Long> levelToDurationMap);

    /** @see  WardenService#disableWarden() */
    void disableWarden();

    /** @see  WardenService#enableWarden() */
    void enableWarden();

    /** @see  MonitorService#enableMonitoring() */
    void enableMonitorCounterCollection();

    /** @see  MonitorService#disableMonitoring() */
    void disableMonitorCounterCollection();

    /** Resets all runtime counters defined in the monitor service. */
    void resetRuntimeCounters();

    /** Resets all system counters defined in the monitor service. */
    void resetSystemCounters();

    /** Resets all custom counters defined in the monitor service. */
    void resetCustomCounters();

    /**
     * @param   user  The user for which to retrieve the warden dashboard for.
     *
     * @return  The warden dashboard for the user. Will never be null.
     * @see  WardenService#getWardenDashboard(PrincipalUser) */
    Dashboard getWardenDashboard(PrincipalUser user);

    /**
     * Returns a dashboard of the system counters annotated with system alert information.
     *
     * @return  The system dashboard.
     */
    Dashboard getSystemDashboard();

    /**
     * Returns a dashboard of the runtime counters annotated with runtime alert information.
     *
     * @return  The runtime dashboard.
     */
    Dashboard getRuntimeDashboard();

    /** @see  SchedulingService#enableScheduling() */
    void enableScheduling();

    /** @see  SchedulingService#disableScheduling() */
    void disableScheduling();

    /** Perform clean up of the database. Delete expired Audit and History records etc. */
    void cleanupRecords();

    /**
     * Cleans up alerts that have been marked for deletion.
     *  
     * @param limit	 The maximun number of alerts to delete. 
     */
	void cleanupDeletedAlerts(int limit);
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

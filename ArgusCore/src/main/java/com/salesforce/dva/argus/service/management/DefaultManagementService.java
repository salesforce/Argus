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
	 
package com.salesforce.dva.argus.service.management;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.Dashboard;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.AlertService;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.HistoryService;
import com.salesforce.dva.argus.service.ManagementService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.SchedulingService;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.service.WardenService;
import com.salesforce.dva.argus.service.WardenService.PolicyCounter;
import com.salesforce.dva.argus.service.WardenService.SubSystem;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import org.slf4j.Logger;
import java.lang.reflect.Method;
import java.util.Map;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Default implementation of the management service.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class DefaultManagementService extends DefaultService implements ManagementService {

    //~ Instance fields ******************************************************************************************************************************

    @SLF4JTypeListener.InjectLogger
    private Logger _logger;
    private final WardenService _wardenService;
    private final MonitorService _monitorService;
    private final SchedulingService _schedulingService;
    private final UserService _userService;
    private final AuditService _auditService;
    private final HistoryService _historyService;
    private final AlertService _alertService;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new DefaultManagementService object.
     *
     * @param  wardenService      The warden service. Cannot be null.
     * @param  monitorService     The monitor service. Cannot be null.
     * @param  schedulingService  The scheduling service. Cannot be null.
     * @param  userService        The user service. Cannot be null.
     * @param  auditService       The audit service.  Cannot be null.
     * @param  historyService     The history service.  Cannot be null.
     * @param  alertService       The alert service.  Cannot be null.
     */
    @Inject
    DefaultManagementService(WardenService wardenService, MonitorService monitorService, SchedulingService schedulingService, UserService userService,
        AuditService auditService, HistoryService historyService, AlertService alertService, SystemConfiguration config) {
    	super(config);
        requireArgument(wardenService != null, "Warden service cannot be null.");
        requireArgument(monitorService != null, "Monitor service cannot be null.");
        requireArgument(schedulingService != null, "Scheduling service cannot be null.");
        requireArgument(userService != null, "User service cannot be null.");
        requireArgument(auditService != null, "Audit service cannot be null.");
        requireArgument(historyService != null, "History service cannot be null.");
        requireArgument(alertService != null, "Alert service cannot be null.");
        _userService = userService;
        _wardenService = wardenService;
        _monitorService = monitorService;
        _schedulingService = schedulingService;
        _auditService = auditService;
        _historyService = historyService;
        _alertService = alertService;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    @Transactional
    public void updateWardenPolicyForUser(PrincipalUser user, PolicyCounter counter, double value) {
        requireNotDisposed();
        requireArgument(user != null, "User cannot be null.");
        requireArgument(counter != null, "Policy counter cannot be null.");
        _logger.info("Updating warden policy {} for {} to {} via the management service.", counter, user.getUserName(), value);
        _wardenService.updatePolicyLimitForUser(user, counter, value);
    }

    @Override
    @Transactional
    public void reinstateUser(PrincipalUser user, SubSystem subSystem) {
        requireNotDisposed();
        requireArgument(user != null, "User cannot be null.");
        requireArgument(subSystem != null, "Subsystem cannot be null.");
        _logger.info("Reinstating {} for {} via the management service.", subSystem, user.getUserName());
        _wardenService.reinstateUser(user, subSystem);
    }

    @Override
    @Transactional
    public void updateWardenSuspensionLevelsAndDurations(SubSystem subSystem, Map<Integer, Long> levelsAndDurations) {
        requireNotDisposed();
        requireArgument(subSystem != null, "Subsystem cannot be null");
        requireArgument(levelsAndDurations != null && !levelsAndDurations.isEmpty(), "Threshold and durations cannot be null or empty.");
        _logger.info("Updating warden suspension levels and durations via the management service.");
        _wardenService.updateSuspensionLevels(subSystem, levelsAndDurations);
    }

    @Override
    @Transactional
    public void updateWardenSuspensionLevelsAndDurations(Map<Integer, Long> levelsAndDurations) {
        requireNotDisposed();
        requireArgument(levelsAndDurations != null && !levelsAndDurations.isEmpty(), "Threshold and durations cannot be null or empty.");
        _logger.info("Updating warden suspension levels and durations for all subsystems via the management service.");
        for (SubSystem subSystem : SubSystem.values()) {
            updateWardenSuspensionLevelsAndDurations(subSystem, levelsAndDurations);
        }
    }

    @Override
    @Transactional
    public void disableWarden() {
        requireNotDisposed();
        _logger.info("Disabling warden service via the management service.");
        _wardenService.disableWarden();
    }

    @Override
    @Transactional
    public void enableWarden() {
        requireNotDisposed();
        _logger.info("Enabling warden service via the management service.");
        _wardenService.enableWarden();
    }

    @Override
    @Transactional
    public void enableMonitorCounterCollection() {
        requireNotDisposed();
        _logger.info("Enabling monitoring service via the management service.");
        _monitorService.enableMonitoring();
    }

    @Override
    @Transactional
    public void disableMonitorCounterCollection() {
        requireNotDisposed();
        _logger.info("Disabling monitoring service via the management service.");
        _monitorService.disableMonitoring();
    }

    @Override
    @Transactional
    public void resetRuntimeCounters() {
        requireNotDisposed();
        _logger.info("Resetting runtime counters service via the management service.");
        _monitorService.resetRuntimeCounters();
    }

    @Override
    @Transactional
    public void resetSystemCounters() {
        requireNotDisposed();
        _logger.info("Resetting system counters service via the management service.");
        _monitorService.resetSystemCounters();
    }

    @Override
    @Transactional
    public void resetCustomCounters() {
        requireNotDisposed();
        _logger.info("Resetting custom counters service via the management service.");
        _monitorService.resetCustomCounters();
    }

    @Override
    @Transactional
    public Dashboard getWardenDashboard(PrincipalUser user) {
        requireNotDisposed();
        requireArgument(user != null, "The user cannot be null.");
        _logger.info("Obtaining warden dashboard for {} via the management service.", user.getUserName());
        return _wardenService.getWardenDashboard(user);
    }

    @Override
    @Transactional
    public Dashboard getSystemDashboard() {
        requireNotDisposed();
        _logger.info("Obtaining system dashboard via the management service.");
        return _monitorService.getSystemDashboard();
    }

    @Override
    @Transactional
    public Dashboard getRuntimeDashboard() {
        requireNotDisposed();
        _logger.info("Obtaining runtime dashboard via the management service.");
        return _monitorService.getRuntimeDashboard();
    }

    @Override
    @Transactional
    public void enableScheduling() {
        requireNotDisposed();
        _logger.info("Enabling scheduling via the management service.");
        _schedulingService.enableScheduling();
    }

    @Override
    @Transactional
    public void disableScheduling() {
        requireNotDisposed();
        _logger.info("Disabling scheduling via the management service.");
        _schedulingService.disableScheduling();
    }

    @Override
    @Transactional
    public void setAdministratorPrivilege(PrincipalUser user, boolean isPrivileged) {
        requireNotDisposed();
        requireArgument(user != null, "The user cannot be null.");
        _logger.info("Disabling scheduling via the management service.");
        try {
            Method method = PrincipalUser.class.getDeclaredMethod("setPrivileged", boolean.class);

            method.setAccessible(true);
            method.invoke(user, isPrivileged);
            _userService.updateUser(user);
        } catch (Exception ex) {
            throw new SystemException(ex);
        }
    }

    @Override
    @Transactional
    public void cleanupRecords() {
        requireNotDisposed();
        _logger.info("Performing database clean up.");
        _historyService.deleteExpiredHistory();
        _auditService.deleteExpiredAudits();
        for (Alert alert : _alertService.findAlertsMarkedForDeletion()) {
            _alertService.deleteAlert(alert);
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        _userService.dispose();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

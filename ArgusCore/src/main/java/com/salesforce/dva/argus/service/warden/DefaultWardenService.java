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
	 
package com.salesforce.dva.argus.service.warden;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.entity.Dashboard;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.PolicyLimit;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.entity.ServiceManagementRecord;
import com.salesforce.dva.argus.entity.ServiceManagementRecord.Service;
import com.salesforce.dva.argus.entity.SubsystemSuspensionLevels;
import com.salesforce.dva.argus.entity.SuspensionRecord;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.AlertService;
import com.salesforce.dva.argus.service.AnnotationService;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.DashboardService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.MonitorService.Counter;
import com.salesforce.dva.argus.service.ServiceManagementService;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.service.WardenService;
import com.salesforce.dva.argus.service.jpa.DefaultJPAService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import org.slf4j.Logger;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.persistence.EntityManager;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Default implementation of the warden service.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
@Singleton
public class DefaultWardenService extends DefaultJPAService implements WardenService {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final long TIME_BETWEEN_WARDEN_ALERT_DISABLEMENT_MILLIS = 1 * 24 * 60 * 60 * 1000L;
    private static final long MILLIS_IN_SEVEN_DAYS = 7 * 24 * 60 * 60 * 1000L;
    private static final long MILLIS_IN_THIRTY_DAYS = 30 * 24 * 60 * 60 * 1000L;
    private static final String USERNAME_KEY = "user";
    private static final String WARDEN_ALERT_NAME_PREFIX = "warden-";
    private static final String NOTIFICATION_NAME = "Warden Notification";
    private static final String ANNOTATION_SOURCE = "ARGUS-WARDEN";
    private static final String ANNOTATION_TYPE = "WARDEN";

    //~ Instance fields ******************************************************************************************************************************

    private ScheduledExecutorService _scheduledExecutorService;
    @SLF4JTypeListener.InjectLogger
    private Logger _logger;
    @Inject
    private Provider<EntityManager> emf;
    private final AlertService _alertService;
    private final MonitorService _monitorService;
    private final UserService _userService;
    private final MetricService _metricService;
    private final ServiceManagementService _serviceManagementRecordService;
    private final DashboardService _dashboardService;
    private final AnnotationService _annotationService;
    private final PrincipalUser _adminUser;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new DefaultWardenService object.
     *
     * @param  alertService              The alert service user to create warden alerts when necessary. Cannot be null.
     * @param  monitorService            The monitor service used to collect warden policy metric counters. Cannot be null.
     * @param  userService               The user service. Cannot be null.
     * @param  metricService             The metric service. Cannot be null.
     * @param  serviceManagementService  The service management service. Cannot be null.
     * @param  dashboardService          The dashboard service. Cannot be null.
     * @param  auditService              The audit service. Cannot be null.
     * @param  annotationService         The annotation service. Cannot be null.
     * @param _sysConfig Service properties
     */
    @Inject
    protected DefaultWardenService(AlertService alertService, MonitorService monitorService, UserService userService, MetricService metricService,
        ServiceManagementService serviceManagementService, DashboardService dashboardService, AuditService auditService,
        AnnotationService annotationService, SystemConfiguration _sysConfig) {
        super(auditService, _sysConfig);
        requireArgument(alertService != null, "Alert service cannot be null.");
        requireArgument(monitorService != null, "Monitor service cannot be null.");
        requireArgument(userService != null, "User service cannot be null.");
        requireArgument(metricService != null, "Metric service cannot be null.");
        requireArgument(serviceManagementService != null, "Service management service cannot be null.");
        requireArgument(dashboardService != null, "Dashboard service cannot be null.");
        requireArgument(annotationService != null, "Annotation service cannot be null.");
        _alertService = alertService;
        _monitorService = monitorService;
        _userService = userService;
        _metricService = metricService;
        _serviceManagementRecordService = serviceManagementService;
        _dashboardService = dashboardService;
        _annotationService = annotationService;
        _adminUser = _userService.findAdminUser();
        _scheduledExecutorService = _createScheduledExecutorService();
        _startScheduledExecutorService();
    }

    //~ Methods **************************************************************************************************************************************

    private static String _constructWardenMetricExpression(String relativeStart, PrincipalUser user, PolicyCounter counter) {
        assert (user != null) : "User cannot be null.";
        assert (counter != null) : "Counter cannot be null.";
        assert (relativeStart != null) : "Relative start cannot be null.";

        String metricName = counter.getMetricName();
        String userName = user.getUserName();

        if ("sum".equals(counter.getAggregator())) {
            return MessageFormat.format("INTEGRAL(ZEROIFMISSINGSUM({0}:argus.custom:{1}'{'user={2},host=*'}':{3}))", relativeStart, metricName,
                userName, counter.getAggregator());
        } else {
            return MessageFormat.format("{0}:argus.custom:{1}'{'user={2},host=*'}':{3}", relativeStart, metricName, userName,
                counter.getAggregator());
        }
    }

    private static String _constructWardenAlertName(PrincipalUser user, PolicyCounter counter) {
        assert (user != null) : "User cannot be null.";
        assert (counter != null) : "Counter cannot be null.";
        return WARDEN_ALERT_NAME_PREFIX + user.getUserName() + "-" + counter.name();
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public void dispose() {
        super.dispose();
        _alertService.dispose();
        _userService.dispose();
        _metricService.dispose();
        _serviceManagementRecordService.dispose();
        _dashboardService.dispose();
        _shutdownScheduledExecutorService();
    }

    @Override
    @Transactional
    public void updatePolicyCounter(PrincipalUser user, PolicyCounter counter, double value) {
        requireNotDisposed();
        requireArgument(user != null, "Cannot update a policy counter with null user.");
        requireArgument(counter != null, "Cannot update a null policy counter.");
        _logger.debug("Updating {} policy for {} to {}.", counter.name(), user.getUserName(), value);

        Map<String, String> metaProps = new HashMap<>();

        metaProps.put(USERNAME_KEY, user.getUserName());
        _monitorService.updateCustomCounter(counter.getMetricName(), value, metaProps);
        _updateWardenAlertsForUser(user, counter);
    }

    @Override
    @Transactional
    public double modifyPolicyCounter(PrincipalUser user, PolicyCounter counter, double delta) {
        requireNotDisposed();
        requireArgument(user != null, "Cannot modify a policy counter with null user.");
        requireArgument(counter != null, "Cannot modify a null policy counter.");
        _logger.debug("Modifying {} policy for {} using delta of {}.", counter.name(), user.getUserName(), delta);

        Map<String, String> tags = new HashMap<>();

        tags.put(USERNAME_KEY, user.getUserName());

        double value = _monitorService.modifyCustomCounter(counter.getMetricName(), delta, tags);

        _updateWardenAlertsForUser(user, counter);
        return value;
    }

    @Override
    @Transactional
    public void assertSubSystemUsePermitted(PrincipalUser user, SubSystem subSystem) {
        requireNotDisposed();
        requireArgument(user != null, "User cannot be null while checking for subsystem use.");
        requireArgument(subSystem != null, "Subsystem cannot be null while checking for its use.");
        _logger.info(MessageFormat.format("Checking if {0} can access {1} sub-system.", user.getUserName(), subSystem.toString()));
        if (user.isPrivileged()) {
            return;
        }

        SuspensionRecord record = SuspensionRecord.findByUserAndSubsystem(emf.get(), user, subSystem);

        if (record == null) {
            return;
        }
        if (record.isSuspendedIndefinitely()) {
            _logger.warn(MessageFormat.format("{0} is suspended indefinitely from using the system.", user.getUserName()));
            throw new SystemException(MessageFormat.format("{0} is suspended indefinitely from using the system.", user.getUserName()));
        }
        if (record.isSuspended()) {
            _logger.warn(MessageFormat.format("{0} is suspended from using the {1} sub-system.", user.getUserName(), subSystem));
            throw new SystemException(MessageFormat.format("{0} is suspended from using the {1} sub-system.", user.getUserName(), subSystem));
        }
    }

    @Override
    @Transactional
    public boolean suspendUser(PrincipalUser user, SubSystem subSystem) {
        requireNotDisposed();
        requireArgument(user != null, "User cannot be null while checking for sub-system use.");
        requireArgument(subSystem != null, "Subsystem cannot be null while checking for its use.");
        if (user.isPrivileged()) {
            return false;
        }

        EntityManager em = emf.get();
        SuspensionRecord record = SuspensionRecord.findByUserAndSubsystem(em, user, subSystem);

        if (record == null) {
            record = new SuspensionRecord(_adminUser, user, subSystem, System.currentTimeMillis());
        } else {
            record.addInfraction(System.currentTimeMillis());
        }
        record = mergeEntity(em, record);
        em.flush();
        if (record.isSuspendedIndefinitely()) {
            return true;
        }

        int sevenDayInfractionCount = SuspensionRecord.findInfractionCount(em, user, subSystem, System.currentTimeMillis() - MILLIS_IN_SEVEN_DAYS);
        int thirtyDayInfractionCount = SuspensionRecord.findInfractionCount(em, user, null, System.currentTimeMillis() - MILLIS_IN_THIRTY_DAYS);

        if (thirtyDayInfractionCount >= 15) {
            _suspendUserIndefinitely(user);
            return true;
        }
        if (sevenDayInfractionCount > 0) {
            long suspensionTime = _getSuspensionTimeBasedOnInfractionCount(sevenDayInfractionCount, subSystem);

            _suspendUserUntil(user, subSystem, System.currentTimeMillis() + suspensionTime);
            return false;
        }
        return false;
    }

    @Override
    @Transactional
    public void reinstateUser(PrincipalUser user, SubSystem subSystem) {
        requireNotDisposed();
        requireArgument(user != null, "Cannot reinstate a null user.");
        requireArgument(subSystem != null, "Cannot reinstate a user for a null sub-system.");

        EntityManager em = emf.get();
        SuspensionRecord record = SuspensionRecord.findByUserAndSubsystem(em, user, subSystem);

        if (record != null) {
            deleteEntity(em, record);
            em.flush();
            _auditService.createAudit("Reinstated user : {0}", user, user);
        }

        // Create an annotation for reinstated user, if he is indefinitely suspended
        if (record != null && record.isSuspendedIndefinitely()) {
            Annotation annotation = new Annotation(ANNOTATION_SOURCE, user.getUserName(), ANNOTATION_TYPE, Counter.WARDEN_TRIGGERS.getScope(),
                Counter.WARDEN_TRIGGERS.getMetric(), System.currentTimeMillis());
            Map<String, String> fields = new TreeMap<>();

            fields.put("Reinstated subsystem", subSystem.toString());
            annotation.setFields(fields);
            _annotationService.updateAnnotation(user, annotation);
        }
    }

    @Override
    @Transactional
    public void updatePolicyLimitForUser(PrincipalUser user, PolicyCounter counter, double value) {
        requireNotDisposed();
        requireArgument(user != null, "Cannot update policy limit for a null user.");
        requireArgument(counter != null, "Cannot update policy limit for a null counter.");

        EntityManager em = emf.get();
        PolicyLimit pLimit = PolicyLimit.findPolicyLimitByUserAndCounter(em, user, counter);

        if (pLimit == null) {
            pLimit = new PolicyLimit(_adminUser, user, counter, value);
        }
        pLimit.setLimit(value);
        mergeEntity(em, pLimit);
        em.flush();
        _auditService.createAudit("Updated policy limit for user : {0} to {1}", user, counter, value);
    }

    @Override
    @Transactional
    public void updateSuspensionLevel(SubSystem subSystem, int level, long durationInMillis) {
        requireNotDisposed();
        requireArgument(subSystem != null, "Cannot update level for a null sub-system.");

        EntityManager em = emf.get();
        SubsystemSuspensionLevels suspensionLevels = SubsystemSuspensionLevels.findBySubsystem(em, subSystem);

        suspensionLevels.getLevels().put(level, durationInMillis);
        mergeEntity(em, suspensionLevels);
        em.flush();
        _auditService.createAudit("Updated suspension level for {0} level {1} to {2}", suspensionLevels, subSystem, level, durationInMillis);
    }

    @Override
    @Transactional
    public void updateSuspensionLevels(SubSystem subSystem, Map<Integer, Long> levels) {
        requireNotDisposed();
        requireArgument(subSystem != null, "Cannot update levels for a null sub-system.");
        requireArgument(levels != null && !levels.isEmpty(), "Cannot update a sub-system with null or empty suspension levels.");

        EntityManager em = emf.get();
        SubsystemSuspensionLevels suspensionLevels = SubsystemSuspensionLevels.findBySubsystem(em, subSystem);

        suspensionLevels.setLevels(levels);
        mergeEntity(em, suspensionLevels);
        em.flush();
        _auditService.createAudit("Updated suspension levels for {0} to {1}", suspensionLevels, subSystem, levels);
    }

    @Override
    @Transactional
    public void enableWarden() {
        requireNotDisposed();
        _enableWarden(true);
    }

    @Override
    @Transactional
    public void disableWarden() {
        requireNotDisposed();
        _enableWarden(false);
    }

    @Override
    @Transactional
    public Dashboard getWardenDashboard(PrincipalUser user) {
        requireNotDisposed();
        requireArgument(user != null, "User cannot be null.");

        String userName = user.getUserName();
        String dashboardName = "Warden Dashboard - " + userName;
        Dashboard wardenDashboard = _dashboardService.findDashboardByNameAndOwner(dashboardName, _adminUser);

        if (wardenDashboard == null) {
            wardenDashboard = new Dashboard(_adminUser, dashboardName, _adminUser);

            /* @todo: create dashboard content. */
            wardenDashboard = _dashboardService.updateDashboard(wardenDashboard);
        }
        return wardenDashboard;
    }

    /**
     * Indicates if the warden service is enabled.
     *
     * @return  True if the service is enabled.
     */
    public boolean isWardenServiceEnabled() {
        return _serviceManagementRecordService.isServiceEnabled(Service.WARDEN);
    }

    private void _updateWardenAlertsForUser(PrincipalUser user, PolicyCounter counter) {
        /* Enable alert for this counter or create one if it doesn't exist. */
        Alert wardenAlert = _alertService.findAlertByNameAndOwner(_constructWardenAlertName(user, counter), _adminUser);

        if (wardenAlert == null) {
            wardenAlert = _constructWardenAlertForUser(user, counter);
        }
        wardenAlert.setEnabled(true);
        _alertService.updateAlert(wardenAlert);
    }

    private void _enableWarden(boolean enabled) {
        ServiceManagementRecord wardenServiceRecord = _serviceManagementRecordService.findServiceManagementRecord(Service.WARDEN);

        if (wardenServiceRecord == null) {
            wardenServiceRecord = new ServiceManagementRecord(_adminUser, Service.WARDEN, enabled);
        } else {
            wardenServiceRecord.setEnabled(enabled);
        }
        _serviceManagementRecordService.updateServiceManagementRecord(wardenServiceRecord);
    }

    /**
     * Create a warden alert which will annotate the corresponding warden metric with suspension events.
     *
     * @param   user     The user for which the notification should be created.  Cannot be null.
     * @param   counter  The policy counter for which the notification should be created.  Cannot be null.
     *
     * @return  The warden alert.
     */
    private Alert _constructWardenAlertForUser(PrincipalUser user, PolicyCounter counter) {
        String metricExp = _constructWardenMetricExpression("-1h", user, counter);
        Alert alert = new Alert(_adminUser, _adminUser, _constructWardenAlertName(user, counter), metricExp, "*/5 * * * *");
        List<Trigger> triggers = new ArrayList<>();
        EntityManager em = emf.get();
        double limit = PolicyLimit.getLimitByUserAndCounter(em, user, counter);
        Trigger trigger = new Trigger(alert, counter.getTriggerType(), "counter-value-" + counter.getTriggerType().toString() + "-policy-limit",
            limit, 0.0, 0L);

        triggers.add(trigger);

        List<Notification> notifications = new ArrayList<>();
        Notification notification = new Notification(NOTIFICATION_NAME, alert, _getWardenNotifierClass(counter), new ArrayList<String>(), 3600000);
        List<String> metricAnnotationList = new ArrayList<String>();

        String wardenMetricAnnotation = MessageFormat.format("{0}:{1}'{'user={2}'}':sum", Counter.WARDEN_TRIGGERS.getScope(),
            Counter.WARDEN_TRIGGERS.getMetric(), user.getUserName());

        metricAnnotationList.add(wardenMetricAnnotation);
        notification.setMetricsToAnnotate(metricAnnotationList);
        notification.setTriggers(triggers);
        notifications.add(notification);
        alert.setTriggers(triggers);
        alert.setNotifications(notifications);
        return alert;
    }

    private long _getSuspensionTimeBasedOnInfractionCount(int count, SubSystem subSystem) {
        EntityManager em = emf.get();
        SubsystemSuspensionLevels suspensionLevels = SubsystemSuspensionLevels.findBySubsystem(em, subSystem);
        Long timeToSuspendInMillis = suspensionLevels.getLevels().get(count);

        if (timeToSuspendInMillis != null) {
            return timeToSuspendInMillis;
        }

        // Find the biggest level smaller than this level and return suspension period for that level.
        int biggestLevelSmallerThan = -1;

        // The levels is backed internally by a TreeMap which stores the entry in natural ordering of keys,
        // The entry thus read has keys in descending order
        for (Entry<Integer, Long> entry : suspensionLevels.getLevels().entrySet()) {
            biggestLevelSmallerThan = entry.getKey();
            if (entry.getKey() <= count) {
                break;
            }
        }
        return biggestLevelSmallerThan == -1 ? 0L : suspensionLevels.getLevels().get(biggestLevelSmallerThan);
    }

    private void _suspendUserIndefinitely(PrincipalUser user) {
        for (SubSystem subSystem : SubSystem.values()) {
            _suspendUserUntil(user, subSystem, -1);
        }
        _auditService.createAudit("User suspended indefinitely", user, new Object[] {});
    }

    private void _suspendUserUntil(PrincipalUser user, SubSystem subSystem, long expiration) {
        EntityManager em = emf.get();
        SuspensionRecord record = SuspensionRecord.findByUserAndSubsystem(em, user, subSystem);

        if (record == null) {
            record = new SuspensionRecord(_adminUser, user, subSystem, expiration);
        } else {
            record.setSuspendedUntil(expiration);
        }
        mergeEntity(em, record);
        em.flush();
        _auditService.createAudit("User suspended from {0} until {1,date}", user, subSystem, expiration);
    }

    private String _getWardenNotifierClass(PolicyCounter counter) {
        if (counter.getSubSystem().equals(SubSystem.API)) {
            return WardenApiNotifier.class.getName();
        } else {
            return WardenPostingNotifier.class.getName();
        }
    }

    private void _disableWardenAlertsThatAreNotUpdated() {
        /* Disable alerts for counters that haven't been updated in the past one hour. */
        List<Alert> alertsForAdmin = _alertService.findAlertsByOwner(_adminUser);

        for (Alert alert : alertsForAdmin) {
            if (alert.getName().contains(WARDEN_ALERT_NAME_PREFIX) && alert.isEnabled()) {
                try {
                    _logger.info("Disabling warden alert:{}", alert.getName());

                    List<Metric> metrics = _metricService.getMetrics(alert.getExpression());
                    Metric metric = metrics.isEmpty() ? null : metrics.get(0);

                    if (metric == null || metric.getDatapoints().isEmpty()) {
                        alert.setEnabled(false);
                        _alertService.updateAlert(alert);
                    }
                } catch (Exception ex) {
                    _logger.warn("Failed to get metrics for alert: {}. Reason: {}", alert, ex.getMessage());
                }
            }
        }
    }

    private ScheduledExecutorService _createScheduledExecutorService() {
        return Executors.newScheduledThreadPool(1);
    }

    /** Starts the scheduled executor service. */
    private void _startScheduledExecutorService() {
        DisableWardenAlertsThread disableWardenAlertThread = new DisableWardenAlertsThread();

        _scheduledExecutorService.scheduleAtFixedRate(disableWardenAlertThread, 0L, TIME_BETWEEN_WARDEN_ALERT_DISABLEMENT_MILLIS,
            TimeUnit.MILLISECONDS);
    }

    /** Shuts down the scheduled executor service. */
    private void _shutdownScheduledExecutorService() {
        _logger.info("Shutting down scheduled disable warden alerts executor service");
        _scheduledExecutorService.shutdown();
        try {
            if (!_scheduledExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
                _logger.warn("Shutdown of scheduled disable warden alerts executor service timed out after 5 seconds.");
                _scheduledExecutorService.shutdownNow();
            }
        } catch (InterruptedException ex) {
            _logger.warn("Shutdown of executor service was interrupted.");
            Thread.currentThread().interrupt();
        }
    }

    //~ Inner Classes ********************************************************************************************************************************

    /**
     * Thread that disables all enabled warden alerts that are not being updated due to user activity.
     *
     * @author  Dilip Devaraj (ddevaraj@salesforce.com)
     */
    private class DisableWardenAlertsThread implements Runnable {

        @Override
        public void run() {
            try {
                _disableWardenAlertsThatAreNotUpdated();
            } catch (Exception ex) {
                _logger.warn("Error occured Reason: {}", ex.getMessage());
            }
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

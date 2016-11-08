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
	 
package com.salesforce.dva.argus.service.schedule;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.CronJob;
import com.salesforce.dva.argus.entity.JPAEntity;
import com.salesforce.dva.argus.entity.ServiceManagementRecord;
import com.salesforce.dva.argus.entity.ServiceManagementRecord.Service;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.AlertService;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.GlobalInterlockService;
import com.salesforce.dva.argus.service.GlobalInterlockService.LockType;
import com.salesforce.dva.argus.service.SchedulingService;
import com.salesforce.dva.argus.service.ServiceManagementService;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import it.sauronsoftware.cron4j.Scheduler;
import org.slf4j.Logger;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Default implementation of the SchedulingService interface.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 * @author  Raj Sarkapally (rsarkapally@salesforce.com)
 */
@Singleton
public class DefaultSchedulingService extends DefaultService implements SchedulingService {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final long GLOBAL_LOCK_EXPIRATION_PERIOD_MS = 1000L * 60L * 15L;
    private static final long GLOBAL_LOCK_REFRESH_PERIOD_MS = 1000L * 60L * 14L;
    private static final long GLOBAL_LOCK_ACQUISITION_PERIOD_MS = 1000L * 60L * 1L;
    private static final String GLOBAL_LOCK_NOTE_TEMPLATE = "Last refresh of {0} lock {1}, expires {2}.";

    //~ Instance fields ******************************************************************************************************************************

    @SLF4JTypeListener.InjectLogger
    private Logger _logger;
    private final AlertService _alertService;
    private final GlobalInterlockService _globalInterlockService;
    private final UserService _userService;
    private final ServiceManagementService _serviceManagementRecordService;
    private final AuditService _auditService;
    private Thread _alertSchedulingThread;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new DefaultSchedulingService object.
     *
     * @param  alertService                    The alert service instance to use. Cannot be null.
     * @param  globalInterlockService          Global lock service instance to obtain the lock. Cannot be null.
     * @param  userService                     The user service instance to use. Cannot be null.
     * @param  serviceManagementRecordService  The serviceManagementRecordService instance to use. Cannot be null.
     * @param  auditService                    The audit service. Cannot be null.
     * @param config 
     */
    @Inject
    DefaultSchedulingService(AlertService alertService, GlobalInterlockService globalInterlockService, UserService userService,
        ServiceManagementService serviceManagementRecordService, AuditService auditService, SystemConfiguration config) {
    	super(config);
        requireArgument(alertService != null, "Alert service cannot be null.");
        requireArgument(globalInterlockService != null, "Global interlock service cannot be null.");
        requireArgument(userService != null, "User service cannot be null.");
        requireArgument(serviceManagementRecordService != null, "Service management record service cannot be null.");
        requireArgument(auditService != null, "Audit service cannot be null.");
        _alertService = alertService;
        _globalInterlockService = globalInterlockService;
        _userService = userService;
        _serviceManagementRecordService = serviceManagementRecordService;
        _auditService = auditService;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    @Transactional
    public synchronized void startAlertScheduling() {
        requireNotDisposed();
        if (_alertSchedulingThread != null && _alertSchedulingThread.isAlive()) {
            _logger.info("Request to start alert scheduling aborted as it is already running.");
        } else {
            _logger.info("Starting alert scheduling thread.");
            _alertSchedulingThread = new SchedulingThread("schedule-alerts", LockType.ALERT_SCHEDULING);
            _alertSchedulingThread.start();
            _logger.info("Alert scheduling thread started.");
        }
    }

    @Override
    public synchronized void dispose() {
        stopAlertScheduling();
        super.dispose();
        _serviceManagementRecordService.dispose();
        _alertService.dispose();
        _globalInterlockService.dispose();
        _userService.dispose();
    }

    @Override
    public synchronized void stopAlertScheduling() {
        requireNotDisposed();
        if (_alertSchedulingThread != null && _alertSchedulingThread.isAlive()) {
            _logger.info("Stopping alert scheduling");
            _alertSchedulingThread.interrupt();
            _logger.info("Alert scheduling thread interrupted.");
            try {
                _logger.info("Waiting for alert scheduling thread to terminate.");
                _alertSchedulingThread.join();
            } catch (InterruptedException ex) {
                _logger.warn("Alert job scheduler was interrupted while shutting down.");
            }
            _logger.info("Alert job scheduling stopped.");
        } else {
            _logger.info("Requested shutdown of alert scheduling aborted as it is not yet running.");
        }
    }

    @Override
    @Transactional
    public synchronized void enableScheduling() {
        requireNotDisposed();
        _logger.info("Globally enabling all scheduling.");
        _setServiceEnabled(true);
        _logger.info("All scheduling globally enabled.");
    }

    @Override
    @Transactional
    public synchronized void disableScheduling() {
        requireNotDisposed();
        _logger.info("Globally disabling all scheduling.");
        _setServiceEnabled(false);
        _logger.info("All scheduling globally disabled.");
    }

    @Transactional
    private boolean _isSchedulingServiceEnabled() {
        synchronized (_serviceManagementRecordService) {
            return _serviceManagementRecordService.isServiceEnabled(Service.SCHEDULING);
        }
    }

    /**
     * Enables the scheduling service.
     *
     * @param  enabled  True to enable, false to disable.
     */
    @Transactional
    protected void _setServiceEnabled(boolean enabled) {
        synchronized (_serviceManagementRecordService) {
            ServiceManagementRecord record = _serviceManagementRecordService.findServiceManagementRecord(Service.SCHEDULING);

            if (record == null) {
                record = new ServiceManagementRecord(_userService.findAdminUser(), Service.SCHEDULING, enabled);
            }
            record.setEnabled(enabled);
            _serviceManagementRecordService.updateServiceManagementRecord(record);
        }
    }

    //~ Inner Classes ********************************************************************************************************************************

    /**
     * Submits the Job to Queue.
     *
     * @author  Raj Sarkapally (rsarkapally@salesforce.com)
     */
    private class RunnableJob implements Runnable {

        private final CronJob job;
        private final LockType lockType;

        private RunnableJob(LockType lockType, CronJob job) {
            this.job = job;
            this.lockType = lockType;
        }

        @Override
        public void run() {
            try {
                if (!isDisposed() && _isSchedulingServiceEnabled()) {
                    _logger.info("Scheduling job {}", job);
                    if (LockType.ALERT_SCHEDULING.equals(lockType)) {
                        synchronized (_alertService) {
                            _alertService.enqueueAlerts(Arrays.asList(new Alert[] { Alert.class.cast(job) }));
                        }
                    } else {
                        throw new SystemException("Unsupported lock type " + lockType);
                    }
                    _logger.info("Successfully scheduled job.");
                }
            } catch (Exception ex) {
                _logger.warn("Could not enqueue scheduled job. " + ex.getMessage());
                _auditService.createAudit("Could not enqueue scheduled job. " + ex.getMessage(), JPAEntity.class.cast(job));
            }
        }
    }

    /**
     * Job scheduler.
     *
     * @author  Raj Sarkapally (rsarkapally@salesforce.com)
     */
    private class SchedulingThread extends Thread {

        private final LockType lockType;

        /**
         * Creates a new SchedulingThread object.
         *
         * @param  name      The name of the thread.
         * @param  lockType  Type of the lock. Cannot be null.
         */
        public SchedulingThread(String name, LockType lockType) {
            super(name);
            this.lockType = lockType;
        }

        /**
         * It fetches all enabled CRON jobs from database.
         *
         * @return  returns all enabled jobs for the given job type.
         */
        protected List<CronJob> getEnabledJobs() {
            List<CronJob> result = new ArrayList<>();

            if (!isDisposed()) {
                if (LockType.ALERT_SCHEDULING.equals(lockType)) {
                    _logger.info("Retreiving all enabled alerts to schedule.");
                    synchronized (_alertService) {
                        result.addAll(_alertService.findAlertsByStatus(true));
                    }
                    _logger.info("Retrieved {} alerts.", result.size());
                }
            }
            return result;
        }

        @Override
        public void run() {
            Scheduler scheduler = null;
            String key = null;

            while (!isInterrupted()) {
                if (_isSchedulingServiceEnabled()) {
                    if (key == null) {
                        key = _becomeMaster();
                    }
                    while (!isInterrupted() && key != null && _isSchedulingServiceEnabled()) {
                        scheduler = _refreshJobSchedule(scheduler);
                        key = _refreshMaster(key);
                    }
                }

                boolean interrupted = interrupted();

                _releaseLock(key);
                if (!interrupted) {
                    _sleepForMasterPollPeriod();
                } else {
                    interrupt();
                }
            }
            _disposeScheduler(scheduler);
        }

        /**
         * Attempts to become the scheduling master.
         *
         * @return  The global interlock key or null if attempt failed.
         */
        protected String _becomeMaster() {
            _logger.info("Attempting to become " + lockType + " master.");

            Date now = new Date();
            Date expiration = new Date(System.currentTimeMillis() + GLOBAL_LOCK_EXPIRATION_PERIOD_MS);
            String lockNote = MessageFormat.format(GLOBAL_LOCK_NOTE_TEMPLATE, lockType, now, expiration);

            synchronized (_globalInterlockService) {
                String key = _globalInterlockService.obtainLock(GLOBAL_LOCK_EXPIRATION_PERIOD_MS, lockType, lockNote);

                _logger.info("Attempt to become {} master {}.", lockType, (key == null ? "did not succeed" : "succeeded"));
                return key;
            }
        }

        private String _refreshMaster(String oldKey) {
            assert oldKey != null : "Can only refresh a key that already exists.";
            try {
                _logger.info("Sleeping for {}s before next attempt refreshing {} schedule.", GLOBAL_LOCK_REFRESH_PERIOD_MS / 1000, lockType);
                sleep(GLOBAL_LOCK_REFRESH_PERIOD_MS);
                _logger.info("Attempting to refresh {} expiration.", lockType);

                Date now = new Date();
                Date expiration = new Date(System.currentTimeMillis() + GLOBAL_LOCK_EXPIRATION_PERIOD_MS);
                String lockNote = MessageFormat.format(GLOBAL_LOCK_NOTE_TEMPLATE, lockType, now, expiration);

                synchronized (_globalInterlockService) {
                    String key = _globalInterlockService.refreshLock(lockType, oldKey, lockNote);

                    _logger.info("Attempt to refresh {} expiration {}.", lockType, (key == null ? "did not succeed" : "succeeded"));
                    return key;
                }
            } catch (InterruptedException ex) {
                _logger.warn("Scheduling was interrupted.");
                interrupt();
                return oldKey;
            }
        }

        /**
         * Refreshes the job schedule with the current list of enabled jobs of the type to be scheduled.
         *
         * @param   scheduler  The scheduler to update.
         *
         * @return  The updated scheduler.
         */
        protected Scheduler _refreshJobSchedule(Scheduler scheduler) {
            _disposeScheduler(scheduler);

            Scheduler result = new Scheduler();

            _logger.info("Refreshing job schedule.");
            for (CronJob job : getEnabledJobs()) {
                _logger.debug("Adding job to scheduler: {}", job);
                try {
                    result.schedule(job.getCronEntry(), new RunnableJob(lockType, job));
                } catch (Exception ex) {
                    String msg = "Failed to schedule job {0} : {1}";
                    JPAEntity entity = JPAEntity.class.cast(job);

                    _auditService.createAudit(msg, entity, entity, ex.getMessage());
                    _logger.error("Failed to schedule job {} : {}", job, ex.getMessage());
                }
            }
            result.start();
            _logger.info("Job schedule refreshed.");
            return result;
        }

        private void _disposeScheduler(Scheduler scheduler) {
            if (scheduler != null) {
                scheduler.stop();
            }
        }

        private void _sleepForMasterPollPeriod() {
            try {
                _logger.info("Sleeping for {}s before next attempt at becoming {} master.", GLOBAL_LOCK_ACQUISITION_PERIOD_MS / 1000, lockType);
                sleep(GLOBAL_LOCK_ACQUISITION_PERIOD_MS);
            } catch (InterruptedException ex) {
                _logger.warn("Scheduling was interrupted.");
                interrupt();
            }
        }

        private void _releaseLock(String key) {
            if (key != null) {
                _logger.info("Releasing {} lock {}.", lockType, key);
                synchronized (_globalInterlockService) {
                    _globalInterlockService.releaseLock(lockType, key);
                }
            }
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

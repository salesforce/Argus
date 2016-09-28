package com.salesforce.dva.argus.service.schedule;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;

import com.google.inject.Inject;
import com.google.inject.persist.Transactional;
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
import com.salesforce.dva.argus.service.MQService;
import com.salesforce.dva.argus.service.MQService.MQQueue;
import com.salesforce.dva.argus.service.SchedulingService;
import com.salesforce.dva.argus.service.ServiceManagementService;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.system.SystemConfiguration;

/**
 * A distributed version of the QuartzSchedulingService. It consists of a single master (which also acts as a slave) and 
 * multiple slaves. The master periodically gets all enabled job ids from the RDBMS and enqueues it onto the TaskQueue. 
 * The slaves periodically poll the TaskQueue for job ids. Once all the jobs have been dequeued by the slaves, they go 
 * ahead and schedule them using the QuartzScheduler. On each poll, if no jobs have been dequeued, the slaves go back to 
 * sleep and wait for the next cycle.
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
public class QueueBasedDistributedSchedulingService extends DefaultService implements SchedulingService {

	//~ Static fields/initializers *******************************************************************************************************************

    private static final long GLOBAL_LOCK_EXPIRATION_PERIOD_MS = 1000L * 60L * 15L;
    private static final long GLOBAL_LOCK_REFRESH_PERIOD_MS = 1000L * 60L * 14L;
    private static final long GLOBAL_LOCK_ACQUISITION_PERIOD_MS = 1000L * 60L * 1L;
    private static final String QUARTZ_THREADPOOL_COUNT = "org.quartz.threadPool.threadCount";
    private static final String QUARTZ_THREAD_PRIORITY = "org.quartz.threadPool.threadPriority";
    private static final String QUARTZ_THREAD_PRIORITY_VALUE = "3";
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
    private SystemConfiguration _configuration;
    private final MQService _mqService;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new DefaultSchedulingService object.
     *
     * @param  alertService                    The alert service instance to use. Cannot be null.
     * @param  globalInterlockService          Global lock service instance to obtain the lock. Cannot be null.
     * @param  userService                     The user service instance to use. Cannot be null.
     * @param  serviceManagementRecordService  The serviceManagementRecordService instance to use. Cannot be null.
     * @param  auditService                    The audit service. Cannot be null.
     * @param  config                          The system configuration used to configure the service.
     */
    @Inject
    QueueBasedDistributedSchedulingService(AlertService alertService, GlobalInterlockService globalInterlockService, UserService userService,
        ServiceManagementService serviceManagementRecordService, AuditService auditService, SystemConfiguration config, MQService mqService) {
    	super(config);
        requireArgument(alertService != null, "Alert service cannot be null.");
        requireArgument(globalInterlockService != null, "Global interlock service cannot be null.");
        requireArgument(userService != null, "User service cannot be null.");
        requireArgument(serviceManagementRecordService != null, "Service management record service cannot be null.");
        requireArgument(auditService != null, "Audit service cannot be null.");
        requireArgument(config != null, "System configuration cannot be null.");
        _alertService = alertService;
        _globalInterlockService = globalInterlockService;
        _userService = userService;
        _serviceManagementRecordService = serviceManagementRecordService;
        _auditService = auditService;
        _configuration = config;
        _mqService = mqService;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    @Transactional
    public synchronized void startAlertScheduling() {
        requireNotDisposed();
        if (_alertSchedulingThread != null && _alertSchedulingThread.isAlive()) {
            _logger.info("Request to start alert scheduler aborted as it is already running.");
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
            _logger.info("Stopping alert scheduling thread");
            _alertSchedulingThread.interrupt();
            _logger.info("Alert scheduling thread interrupted.");
            try {
                _logger.info("Waiting for alert scheduling thread to terminate.");
                _alertSchedulingThread.join();
            } catch (InterruptedException ex) {
                _logger.warn("Alert job scheduler was interrupted while shutting down.");
            }
            _logger.info("Alert job scheduler stopped.");
        } else {
            _logger.info("Requested shutdown of alert scheduling thread aborted as it is not yet running.");
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

    //~ Enums ****************************************************************************************************************************************

    /**
     * The implementation specific configuration properties.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public enum Property {

        /** Specifies the number of threads used for scheduling.  Defaults to 1. */
        QUARTZ_THREADPOOL_COUNT("service.property.scheduling.quartz.threadPool.threadCount", "1");

        private final String _name;
        private final String _defaultValue;

        private Property(String name, String defaultValue) {
            _name = name;
            _defaultValue = defaultValue;
        }

        /**
         * Returns the name of the property.
         *
         * @return  The name of the property.
         */
        public String getName() {
            return _name;
        }

        /**
         * Returns the default property value.
         *
         * @return The default property value.
         */
        public String getDefaultValue() {
            return _defaultValue;
        }
    }

    //~ Inner Classes ********************************************************************************************************************************
    
    /**
     * Job scheduler.
     *
     * @author  Dilip Devaraj (ddevaraj@salesforce.com)
     */
    private class SchedulingThread extends Thread {

    	private static final long SLAVE_SLEEP_IN_MILLIS = 60000;
    	private static final int TASK_DEQUE_LIMIT = 1000;
    	private static final int TASK_DEQUE_TIMEOUT = 500;
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
        protected List<BigInteger> getEnabledJobs() {
            List<BigInteger> result = new ArrayList<>();

            if (!isDisposed()) {
                if (LockType.ALERT_SCHEDULING.equals(lockType)) {
                    _logger.info("Retreiving all enabled alerts to schedule.");
                    synchronized (_alertService) {
                        result.addAll(_alertService.findAlertIdsByStatus(true));
                    }
                    _logger.info("Retrieved {} alerts.", result.size());
                }
            }
            return result;
        }
        
        private void _sleep(long millis) {
        	try {
				Thread.sleep(millis);
			} catch (InterruptedException e) {
				_logger.warn("Thread interrupted while sleeping");
				Thread.currentThread().interrupt();
			}
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
                    
                    //Initialize argusTaskQueue streams and drain any tasks previously added to the queue.
                    while(_mqService.dequeue(MQQueue.TASKQUEUE.getQueueName(), BigInteger.class, 
                    		TASK_DEQUE_TIMEOUT, TASK_DEQUE_LIMIT).size() == TASK_DEQUE_LIMIT) {
                    	continue;
                    }
                    
                    while (!isInterrupted() && _isSchedulingServiceEnabled()) {
                    	if(key != null) {
                    		submitTasksToQueue();
                    		scheduler = _refreshJobSchedule(scheduler);
                            key = _refreshMaster(key);
                    	} else {
                    		key = _becomeMaster();
                    		scheduler = _refreshJobSchedule(scheduler);
                    		_logger.debug("Sleeping for {}s before attempting to become master and refresh job schedule.", SLAVE_SLEEP_IN_MILLIS / 1000);
                    		_sleep(SLAVE_SLEEP_IN_MILLIS);
                    	}
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
            _disposeScheduler(scheduler, true);
        }
        
        protected Scheduler _refreshJobSchedule(Scheduler scheduler) {
        	
        	List<BigInteger> alertIds = new ArrayList<>();
            while(true) {
            	List<BigInteger> ids = _mqService.dequeue(MQQueue.TASKQUEUE.getQueueName(), BigInteger.class, 
            			TASK_DEQUE_TIMEOUT, TASK_DEQUE_LIMIT);
            	if(ids.size() < TASK_DEQUE_LIMIT) {
            		alertIds.addAll(ids);
            		break;
            	}
            	alertIds.addAll(ids);
            }
            
            //There is nothing new to be scheduled. No need to dispose, re-initialize scheduler or re-schedule jobs.
            if(alertIds.isEmpty()) {
            	return scheduler;
            }
        	
            _disposeScheduler(scheduler, false);
            Scheduler result = null;
            try {
            	result = new StdSchedulerFactory(_getSchedulerProperties()).getScheduler();
            } catch (SchedulerException e) {
                _logger.error("Exception in setting up scheduler: {}", e);
                return result;
            }
            
            List<CronJob> jobs = new ArrayList<>();
            _logger.debug("Getting {} jobs from DB.", alertIds.size());
            jobs.addAll(_alertService.findAlertsByPrimaryKeys(alertIds));
            for (CronJob job : jobs) {
                _logger.debug("Adding job to scheduler: {}", job);
                try {
                    // Convert from linux cron to quartz cron expression
                    String quartzCronEntry = "0 " + job.getCronEntry().substring(0, job.getCronEntry().length() - 1) + "?";
                    JobDetail jobDetail = JobBuilder.newJob(RunnableJob.class).build();
                    CronTrigger cronTrigger = TriggerBuilder.newTrigger().withSchedule(CronScheduleBuilder.cronSchedule(quartzCronEntry)).build();

                    // Pass parameter to quartz worker threads
                    jobDetail.getJobDataMap().put(RunnableJob.CRON_JOB, job);
                    jobDetail.getJobDataMap().put(RunnableJob.LOCK_TYPE, lockType);
                    jobDetail.getJobDataMap().put("AlertService", _alertService);
                    jobDetail.getJobDataMap().put("AuditService", _auditService);
                    result.scheduleJob(jobDetail, cronTrigger);
                } catch (Exception ex) {
                    String msg = "Failed to schedule job {0} : {1}";
                    JPAEntity entity = JPAEntity.class.cast(job);

                    _auditService.createAudit(msg, entity, entity, ex.getMessage());
                    _logger.error("Failed to schedule job {} : {}", job, ex.getMessage());
                }
            }
            try {
                result.start();
            } catch (SchedulerException e) {
                _logger.error("Exception in starting scheduler: {}", e);
            }
            _logger.info("Job schedule refreshed.");
            return result;
        }

		private Properties _getSchedulerProperties() {
			String schedulerName = null;
			Properties props = new Properties();

            // Set quartz worker thread properties
            props.put(QUARTZ_THREADPOOL_COUNT,
                _configuration.getValue(Property.QUARTZ_THREADPOOL_COUNT.getName(), Property.QUARTZ_THREADPOOL_COUNT.getDefaultValue()));
            props.put(QUARTZ_THREAD_PRIORITY, QUARTZ_THREAD_PRIORITY_VALUE);
            props.put(StdSchedulerFactory.PROP_SCHED_SCHEDULER_THREADS_INHERIT_CONTEXT_CLASS_LOADER_OF_INITIALIZING_THREAD, true);

            /* Have multiple scheduler instances for different job types, so that when
             * we stop the previous instance of a scheduler during the refresh cycle it does not affect another scheduler.
             */
            switch (Thread.currentThread().getName()) {
                case "schedule-alerts":
                default:
                    schedulerName = "AlertScheduler";
            }
            props.put(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, schedulerName);
			return props;
		}
        
        private void submitTasksToQueue() {
    		switch(lockType) {
    		case ALERT_SCHEDULING:
    			List<BigInteger> alertIds = getEnabledJobs();
    			_logger.info("Adding alerts to task queue: {}", alertIds);
    			_mqService.enqueue(MQQueue.TASKQUEUE.getQueueName(), alertIds);
    			break;
    		default:
    			throw new IllegalArgumentException("This lockType is not supported: " + lockType);
    		}
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
        
        private void _disposeScheduler(Scheduler scheduler, boolean sleep) {
            if (scheduler != null) {
                try {
                    scheduler.shutdown();

                    /* Add a small sleep so Tomcat does not complain - the web application has started a thread,
                     * but has failed to stop it.This is very likely to create a memory leak.
                     */
                    if(sleep) {
                    	Thread.sleep(2000);
                    }
                } catch (SchedulerException e) {
                    _logger.error("Quartz failed to shutdown {}", e);
                } catch (InterruptedException e) {
                    _logger.warn("Shutdown of quartz scheduler was interrupted.");
                    Thread.currentThread().interrupt();
                }
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

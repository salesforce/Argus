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
import com.salesforce.dva.argus.entity.CronJob;
import com.salesforce.dva.argus.entity.DistributedSchedulingLock;
import com.salesforce.dva.argus.entity.JPAEntity;
import com.salesforce.dva.argus.entity.ServiceManagementRecord;
import com.salesforce.dva.argus.entity.ServiceManagementRecord.Service;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.AlertService;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.DistributedSchedulingLockService;
import com.salesforce.dva.argus.service.GlobalInterlockService.LockType;
import com.salesforce.dva.argus.service.SchedulingService;
import com.salesforce.dva.argus.service.ServiceManagementService;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.system.SystemConfiguration;

import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.slf4j.Logger;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Implementation of Distributed scheduling using database
 * 
 * @author Raj Sarkapally rsarkapally@salesforce.com
 *
 */
@Singleton
public class DistributedDatabaseSchedulingService extends DefaultService implements SchedulingService {

	//~ Static fields/initializers *******************************************************************************************************************

	private static final String QUARTZ_THREADPOOL_COUNT = "org.quartz.threadPool.threadCount";
	private static final String QUARTZ_THREAD_PRIORITY = "org.quartz.threadPool.threadPriority";
	private static final String QUARTZ_THREAD_PRIORITY_VALUE = "3";

	//~ Instance fields ******************************************************************************************************************************

	@SLF4JTypeListener.InjectLogger
	private Logger _logger;
	private final AlertService _alertService;
	private final UserService _userService;
	private final ServiceManagementService _serviceManagementRecordService;
	private final AuditService _auditService;
	private Thread _alertSchedulingThread;
	private SystemConfiguration _configuration;
	private final DistributedSchedulingLockService _distributedSchedulingService;

	//~ Constructors *********************************************************************************************************************************

	/**
	 * Creates a new DefaultSchedulingService object.
	 *
	 * @param  alertService                    The alert service instance to use. Cannot be null.
	 * @param  userService                     The user service instance to use. Cannot be null.
	 * @param  serviceManagementRecordService  The serviceManagementRecordService instance to use. Cannot be null.
	 * @param  auditService                    The audit service. Cannot be null.
	 * @param  config                          The system configuration used to configure the service.
	 */
	@Inject
	DistributedDatabaseSchedulingService(AlertService alertService, UserService userService,
			ServiceManagementService serviceManagementRecordService, AuditService auditService, SystemConfiguration config, DistributedSchedulingLockService distributedSchedulingLockService) {
		super(config);
		requireArgument(alertService != null, "Alert service cannot be null.");
		requireArgument(userService != null, "User service cannot be null.");
		requireArgument(serviceManagementRecordService != null, "Service management record service cannot be null.");
		requireArgument(auditService != null, "Audit service cannot be null.");
		requireArgument(config != null, "System configuration cannot be null.");
		_alertService = alertService;
		_userService = userService;
		_serviceManagementRecordService = serviceManagementRecordService;
		_auditService = auditService;
		_configuration = config;
		_distributedSchedulingService=distributedSchedulingLockService;
	}

	//~ Methods **************************************************************************************************************************************

	@Override
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

	//~ Enums ****************************************************************************************************************************************

	/**
	 * The implementation specific configuration properties.
	 *
	 * @author   Raj Sarkapally (rsarkapally@salesforce.com)
	 */
	public enum Property {

		/** Specifies the number of threads used for scheduling.  Defaults to 1. */
		QUARTZ_THREADPOOL_COUNT("service.property.scheduling.quartz.threadPool.threadCount", "1"),
		JOBS_BLOCK_SIZE("service.property.scheduling.jobsBlockSize", "1000"),
		SCHEDULING_REFRESH_INTERVAL_IN_MILLS("service.property.scheduling.schedulingRefeshInterval", "300000"),
		SLEEP_TIME_BEFORE_GETTING_NEXT_JOB_BLOCK_IN_MILLS("service.property.scheduling.sleepTimeBeforeGettingNextJobBlock", "100"),
		MAX_JOBS_PER_SCHEDULER("service.property.scheduling.maxJobsPerScheduler", "10000");

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
	 * @author  Raj Sarkapally (rsarkapally@salesforce.com)
	 */
	private class SchedulingThread extends Thread {

		private final LockType lockType;

		/**
		 * Creates a new SchedulingThread object.
		 *
		 * @param  name      The name of the thread.
		 * @param  Schedulingtype  Type of the schedule. Cannot be null.
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
			long currentScheduleEndTime = -1;
			int jobsBlockSize=Integer.parseInt(_configuration.getValue(Property.JOBS_BLOCK_SIZE.getName(), Property.JOBS_BLOCK_SIZE.getDefaultValue()));
			long schedulingRefreshTime = Long.parseLong(_configuration.getValue(Property.SCHEDULING_REFRESH_INTERVAL_IN_MILLS.getName(), Property.SCHEDULING_REFRESH_INTERVAL_IN_MILLS.getDefaultValue()));
			long sleepTimeBeforeGettingNextJobBlock = Long.parseLong(_configuration.getValue(Property.SLEEP_TIME_BEFORE_GETTING_NEXT_JOB_BLOCK_IN_MILLS.getName(), Property.SLEEP_TIME_BEFORE_GETTING_NEXT_JOB_BLOCK_IN_MILLS.getDefaultValue()));
			long maxJobsPerScheduler = Long.parseLong(_configuration.getValue(Property.MAX_JOBS_PER_SCHEDULER.getName(), Property.MAX_JOBS_PER_SCHEDULER.getDefaultValue()));

			while (!isInterrupted()) {
				if (_isSchedulingServiceEnabled()) {
					List<CronJob> jobs = null;
					DistributedSchedulingLock distributedSchedulingLock = _distributedSchedulingService.updateNGetDistributedScheduleByType(LockType.ALERT_SCHEDULING,jobsBlockSize,schedulingRefreshTime);

					int jobsFromIndex = distributedSchedulingLock.getCurrentIndex() - jobsBlockSize; 
					int numberOfJobsScheduledByScheduler =0 ;

					while(jobsFromIndex < distributedSchedulingLock.getJobCount()){
						if(currentScheduleEndTime < distributedSchedulingLock.getNextScheduleStartTime()){
							_disposeScheduler(scheduler);
							scheduler = _createScheduler();
							_logger.info("Creating a new scheduler with name {}", _getSchedulerName(scheduler));
							currentScheduleEndTime=distributedSchedulingLock.getNextScheduleStartTime();
							jobs = getEnabledJobs();
						}
						int jobsToIndex = jobs.size()<(jobsFromIndex+jobsBlockSize)?jobs.size():jobsFromIndex+jobsBlockSize;
						numberOfJobsScheduledByScheduler+= jobsToIndex - jobsFromIndex;
						_logger.info("Adding jobs between {} and {} to scheduler {}",  jobsFromIndex, jobsToIndex,_getSchedulerName(scheduler)); 
						addJobsToScheduler(scheduler, _drainTo(jobs, jobsFromIndex, jobsToIndex));

						if(numberOfJobsScheduledByScheduler >= maxJobsPerScheduler) break;

						_sleep(sleepTimeBeforeGettingNextJobBlock); //This will distribute jobs evenly across schedulers.
						distributedSchedulingLock = _distributedSchedulingService.updateNGetDistributedScheduleByType(LockType.ALERT_SCHEDULING,jobsBlockSize,schedulingRefreshTime);
						jobsFromIndex = distributedSchedulingLock.getCurrentIndex() - jobsBlockSize; 
					}
					_logger.info("All jobs are scheduled. Scheduler {} is sleeping for {} millis", _getSchedulerName(scheduler), distributedSchedulingLock.getNextScheduleStartTime()-System.currentTimeMillis());
					_logger.info("Next schedule time is {}", distributedSchedulingLock.getNextScheduleStartTime()); 
					_sleep(distributedSchedulingLock.getNextScheduleStartTime()-System.currentTimeMillis());
				}
			}
			_disposeScheduler(scheduler);
		}

		private List<CronJob> _drainTo(List<CronJob> jobs,int fromIndex, int toIndex){
			if(fromIndex>=jobs.size())
				return new ArrayList<>();
			return jobs.subList(fromIndex, toIndex);
		}

		private Scheduler _createScheduler(){

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
				schedulerName = "AlertScheduler-" + this.hashCode();
			}
			props.put(StdSchedulerFactory.PROP_SCHED_INSTANCE_NAME, schedulerName);

			SchedulerFactory schedulerFactory;
			Scheduler scheduler = null;

			try {
				schedulerFactory = new StdSchedulerFactory(props);
				scheduler = schedulerFactory.getScheduler();
				scheduler.start();
			} catch (Exception e) {
				_logger.error("Exception in setting up scheduler: {}", e);

			}
			return scheduler;
		}

		private String _getSchedulerName(Scheduler scheduler){

			if(scheduler==null)
				return "no-scheduler-instance";

			try {
				return scheduler.getSchedulerName();
			} catch (SchedulerException e) {
				_logger.error(e.toString()); 
				return "no-scheduler";
			}

		}

		private void addJobsToScheduler(Scheduler scheduler, List<CronJob> jobs){ 
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
					scheduler.scheduleJob(jobDetail, cronTrigger);
				} catch (Exception ex) {
					String msg = "Failed to schedule job {0} : {1}";
					JPAEntity entity = JPAEntity.class.cast(job);

					_auditService.createAudit(msg, entity, entity, ex.getMessage());
					_logger.error("Failed to schedule job {} : {}", job, ex.getMessage());
				}
			}
		}

		private void _disposeScheduler(Scheduler scheduler) {
			if (scheduler != null) {
				try {
					scheduler.shutdown();

					/* Add a small sleep so Tomcat does not complain - the web application has started a thread,
					 * but has failed to stop it.This is very likely to create a memory leak.
					 */
					Thread.sleep(2000);
				} catch (SchedulerException e) {
					_logger.error("Quartz failed to shutdown {}", e);
				} catch (InterruptedException e) {
					_logger.warn("Shutdown of quartz scheduler was interrupted.");
					Thread.currentThread().interrupt();
				}
			}
		}

		private void _sleep(long millis) {
			try {
				sleep(millis);
			} catch (InterruptedException ex) {
				_logger.warn("Scheduling was interrupted.");
				interrupt();
			}
		}

	}
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

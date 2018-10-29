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
import com.salesforce.dva.argus.entity.DistributedSchedulingLock;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.ServiceManagementRecord;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.entity.ServiceManagementRecord.Service;
import com.salesforce.dva.argus.entity.Trigger.TriggerType;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.AlertService;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.DistributedSchedulingLockService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.GlobalInterlockService.LockType;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.SchedulingService;
import com.salesforce.dva.argus.service.ServiceManagementService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.service.alert.AlertDefinitionsCache;
import com.salesforce.dva.argus.service.monitor.GaugeExporter;
import com.salesforce.dva.argus.system.SystemConfiguration;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Implementation of Distributed scheduling using database
 * 
 * @author Raj Sarkapally rsarkapally@salesforce.com
 *
 */
@Singleton
public class DistributedDatabaseSchedulingService extends DefaultService implements SchedulingService {


	//~ Instance fields ******************************************************************************************************************************

	@SLF4JTypeListener.InjectLogger
	private Logger _logger;
	private final AlertService _alertService;
	private final UserService _userService;
	private final ServiceManagementService _serviceManagementRecordService;
	private final AuditService _auditService;
	private final MetricService _metricService;
	private final TSDBService _tsdbService;
	private final BlockingQueue<Alert> _alertsQueue = new LinkedBlockingQueue<Alert>();
	private ExecutorService _schedulerService;
	private Thread _alertSchedulingThread;
	private SystemConfiguration _configuration;
	private final DistributedSchedulingLockService _distributedSchedulingService;
	private AlertDefinitionsCache _alertDefinitionsCache;
	private AlertSchedulingKPIReporter _alertSchedulingKpiReporter;
	private AlertEvaluationKPIReporter _alertEvaluationKPIReporter;
	private static final Integer ALERT_SCHEDULING_BATCH_SIZE = 100;
	private static final Long SCHEDULING_REFRESH_INTERVAL_IN_MILLS = 60000L;
	private static final Random _randomNumGenerator = new Random(System.nanoTime());

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
	DistributedDatabaseSchedulingService(AlertService alertService, UserService userService, TSDBService tsdbService, MetricService metricService,
			ServiceManagementService serviceManagementRecordService, AuditService auditService,
			SystemConfiguration config, DistributedSchedulingLockService distributedSchedulingLockService) {
		super(config);
		requireArgument(alertService != null, "Alert service cannot be null.");
		requireArgument(userService != null, "User service cannot be null.");
		requireArgument(serviceManagementRecordService != null, "Service management record service cannot be null.");
		requireArgument(auditService != null, "Audit service cannot be null.");
		requireArgument(config != null, "System configuration cannot be null.");
		_alertService = alertService;
		_userService = userService;
		_metricService = metricService;
		_serviceManagementRecordService = serviceManagementRecordService;
		_auditService = auditService;
		_tsdbService = tsdbService;
		_configuration = config;
		_distributedSchedulingService=distributedSchedulingLockService;
		_alertDefinitionsCache = new AlertDefinitionsCache(_alertService);

		// initializing the alert scheduler tasks
		int numThreads = Integer.parseInt(_configuration.getValue(Property.SCHEDULER_THREADPOOL_COUNT.getName(), Property.SCHEDULER_THREADPOOL_COUNT.getDefaultValue()));
		_schedulerService = Executors.newFixedThreadPool(numThreads,
				new ThreadFactory() {
			public Thread newThread(Runnable r) {
				Thread t = Executors.defaultThreadFactory().newThread(r);
				t.setDaemon(true);
				return t;
			}
		});

		for(int i=0;i<numThreads;i++) {
			_schedulerService.submit(new AlertScheduler());
		}

		_alertSchedulingKpiReporter = new AlertSchedulingKPIReporter();
		_alertSchedulingKpiReporter.setDaemon(true);
		_alertSchedulingKpiReporter.start();
		
		_alertEvaluationKPIReporter = new AlertEvaluationKPIReporter();
		_alertEvaluationKPIReporter.setDaemon(true);
		_alertEvaluationKPIReporter.start();
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

		SCHEDULER_THREADPOOL_COUNT("service.property.scheduling.quartz.threadPool.threadCount", "10"),
		JOBS_BLOCK_SIZE("service.property.scheduling.jobsBlockSize", "100000");

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

		/**
		 * Creates a new SchedulingThread object.
		 *
		 * @param  name      The name of the thread.
		 * @param  Schedulingtype  Type of the schedule. Cannot be null.
		 */
		public SchedulingThread(String name, LockType lockType) {
			super(name);
		}

		@Override
		public void run() {

			int jobsBlockSize=Integer.parseInt(_configuration.getValue(Property.JOBS_BLOCK_SIZE.getName(), Property.JOBS_BLOCK_SIZE.getDefaultValue()));

			if (_isSchedulingServiceEnabled()) {
				// wait for the alert definitions cache to be loaded
				while(!_alertDefinitionsCache.isAlertsCacheInitialized()) {
					_logger.info("Waiting for alerts cache to be initialized. Sleeping for 2 seconds..");
					try {
						Thread.sleep(2*1000);
					} catch (InterruptedException e) {
						_logger.error("Thread interrupted when sleeping - " + ExceptionUtils.getFullStackTrace(e));
					}
				}

				while (!isInterrupted()) {
					DistributedSchedulingLock distributedSchedulingLock = _distributedSchedulingService.updateNGetDistributedScheduleByType(LockType.ALERT_SCHEDULING,
							jobsBlockSize,
							SCHEDULING_REFRESH_INTERVAL_IN_MILLS);

					long nextStartTime = distributedSchedulingLock.getNextScheduleStartTime();
					int jobsFromIndex = distributedSchedulingLock.getCurrentIndex() - jobsBlockSize; 

					if(jobsFromIndex >= distributedSchedulingLock.getJobCount() && System.currentTimeMillis() < nextStartTime) {
						_logger.info("All jobs for the current minute are scheduled already. Scheduler is sleeping for {} millis", (nextStartTime - System.currentTimeMillis()));
						_sleep((nextStartTime-System.currentTimeMillis()) + _randomNumGenerator.nextInt(1000));
					}else {
						long startTimeForCurrMinute = nextStartTime;
						if(startTimeForCurrMinute>System.currentTimeMillis()) {
							startTimeForCurrMinute = startTimeForCurrMinute - 60*1000;
						}
						List<Alert> enabledAlerts = AlertDefinitionsCache.getEnabledAlertsForMinute(startTimeForCurrMinute);
						_logger.info("Enabled alerts for start time {} are {}, and from index is {}", startTimeForCurrMinute, enabledAlerts.size(), jobsFromIndex);
						while(jobsFromIndex < enabledAlerts.size()){
							int jobsToIndex = enabledAlerts.size()<(jobsFromIndex+jobsBlockSize)?enabledAlerts.size():jobsFromIndex+jobsBlockSize;
							// schedule all the jobs by putting them in scheduling queue
							_logger.info("Scheduling enabled alerts for the minute starting at {}", startTimeForCurrMinute);
							_logger.info("Adding alerts between {} and {} to scheduler",  jobsFromIndex, jobsToIndex);
							_alertsQueue.addAll(enabledAlerts.subList(jobsFromIndex, jobsToIndex));

							distributedSchedulingLock = _distributedSchedulingService.updateNGetDistributedScheduleByType(LockType.ALERT_SCHEDULING,
									jobsBlockSize,
									SCHEDULING_REFRESH_INTERVAL_IN_MILLS);
							jobsFromIndex = distributedSchedulingLock.getCurrentIndex() - jobsBlockSize; 
						}
					}
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

	class AlertScheduler implements Runnable{
		@Override
		public void run() {
			List<Alert> alertsBatch = new ArrayList<Alert>();
			while(true) {
				try {
					Alert alert = _alertsQueue.poll(10, TimeUnit.MILLISECONDS);
					if(alert!=null) {
						alertsBatch.add(alert);
					}
					if((alert==null && alertsBatch.size()>0) || alertsBatch.size()==ALERT_SCHEDULING_BATCH_SIZE) {
						_alertService.enqueueAlerts(alertsBatch);
						alertsBatch = new ArrayList<Alert>();
						if(alert==null) {
							_logger.info("Alerts queue is empty");
						}
					}
				}catch(Exception e) {
					_logger.error("Exception occured when scheduling alerts - "+ ExceptionUtils.getFullStackTrace(e));
				}
			}
		}
	}

	class AlertSchedulingKPIReporter extends Thread{

		@Override
		public void run() {
			while (!isInterrupted()) {
				try {
					long nextMinuteStartTime = 60*1000*(System.currentTimeMillis()/(60*1000)) + 60*1000;
					sleep(nextMinuteStartTime - System.currentTimeMillis());
					if(_alertDefinitionsCache.isAlertsCacheInitialized()) {

						Metric schedulingQueueSizeMetric = new Metric(MonitorService.Counter.ALERTS_SCHEDULING_QUEUE_SIZE.getScope(), MonitorService.Counter.ALERTS_SCHEDULING_QUEUE_SIZE.getMetric());
						schedulingQueueSizeMetric.setTag("host",SystemConfiguration.getHostname());
						Map<Long, Double> datapoints = new HashMap<>();
						double queueSize = Double.valueOf(_alertsQueue.size());
						datapoints.put(nextMinuteStartTime, queueSize);
						schedulingQueueSizeMetric.addDatapoints(datapoints);
						_alertService.exportMetric(schedulingQueueSizeMetric, queueSize);

						Metric enabledAlertsMetric = new Metric(MonitorService.Counter.ALERTS_ENABLED.getScope(), MonitorService.Counter.ALERTS_ENABLED.getMetric());
						enabledAlertsMetric.setTag("host",SystemConfiguration.getHostname());
						datapoints = new HashMap<>();
						double enabledCount = Double.valueOf(AlertDefinitionsCache.getEnabledAlertsForMinute(nextMinuteStartTime).size());
						datapoints.put(nextMinuteStartTime, enabledCount);
						enabledAlertsMetric.addDatapoints(datapoints);
						_alertService.exportMetric(enabledAlertsMetric, enabledCount);

						try {
							_tsdbService.putMetrics(Arrays.asList(new Metric[] {schedulingQueueSizeMetric, enabledAlertsMetric}));
						} catch (Exception ex) {
							_logger.error("Error occurred while pushing alert audit scheduling time series. Reason: {}", ex.getMessage());
						}
					}else {
						sleep(30*1000);
					}
				} catch(Exception e) {
					_logger.error("Exception occured when scheduling alerts - "+ ExceptionUtils.getFullStackTrace(e));
				}
			}
		}
	}

	class AlertEvaluationKPIReporter extends Thread{
		@Override
		public void run() {
			while (!isInterrupted()) {
				Alert alert = new Alert(_userService.findAdminUser(), _userService.findAdminUser(), "kpi-alert-"+SystemConfiguration.getHostname()+"-"+System.currentTimeMillis(), "-5m:argus.core:alerts.kpi{host="+SystemConfiguration.getHostname()+"}:avg", "* * * * *") ;
				try {
					long fiveMinuteStartTime = 5*60*1000*(System.currentTimeMillis()/(5*60*1000)) + 5*60*1000;
					sleep(fiveMinuteStartTime - System.currentTimeMillis());
					Notification notification1 = new Notification("notification1", alert, "com.salesforce.dva.argus.service.alert.notifier.AuditNotifier", new ArrayList<String>(), 5000L);
					Trigger trigger1 = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "trigger1", 0.0, 0);

					alert.setNotifications(Arrays.asList(new Notification[] { notification1 }));
					alert.setTriggers(Arrays.asList(new Trigger[] { trigger1 }));
					alert.setEnabled(true);
					for (Notification notification : alert.getNotifications()) {
						notification.setTriggers(alert.getTriggers());
					}
					alert = _alertService.updateAlert(alert);
					Metric trackerMetric = new Metric("argus.core", "alerts.kpi");
					trackerMetric.setTag("host",SystemConfiguration.getHostname());
					Map<Long, Double> datapoints = new HashMap<>();
					datapoints.put(fiveMinuteStartTime, 1.0);
					trackerMetric.addDatapoints(datapoints);
					//sleeping for a minute to make sure the new alert is updated in cache
					sleep(60*1000);
					
					try {
						_tsdbService.putMetrics(Arrays.asList(new Metric[] {trackerMetric}));
					} catch (Exception ex) {
						_logger.error("Error occurred while pushing tracker metric . Reason: {}", ex.getMessage());
						continue;
					}	

					long metricPublishTime = System.currentTimeMillis();
					long currCycleEndTime = fiveMinuteStartTime + 5*60*1000 - 30*1000;
					boolean alertEvaluated = false;
					while(System.currentTimeMillis() < currCycleEndTime) {
						try {
							List<Metric> metrics = _metricService.getMetrics("-5m:notifications.sent:alert-"+alert.getId().intValue()+":zimsum:1m-sum");
							if(metrics!=null && !metrics.isEmpty()) {
								for(Metric metric : metrics) {
									if(metric.getDatapoints()!=null && metric.getDatapoints().keySet().size()>0) {
										List<Long> notificationTimestamps = new ArrayList<Long>(metric.getDatapoints().keySet());
										Collections.sort(notificationTimestamps);
										long notificationSentTime = notificationTimestamps.get(0);
										long alertEvaluationTime = notificationSentTime - metricPublishTime;
										alertEvaluated = true;
										publishKPIMetric(fiveMinuteStartTime, new Double(alertEvaluationTime));
									}
								}
							}else {
                                 sleep(10*1000);
							}
							if(alertEvaluated){
								break;
							}
						}catch(Exception ex) {
							_logger.info("Exception occured when getting notification related datapoints - "+ ex.getMessage());
							 sleep(10*1000);
						}
					}

					if(!alertEvaluated) {
						_logger.warn("Notification sent metric is not found in tsdb, so publishing 4 minutes as alert evaluation kpi");
						publishKPIMetric(fiveMinuteStartTime, new Double(4*60*1000));
					}
				} catch(Exception e) {
					_logger.error("Exception occured when computing alert evaluation kpi metric - "+ ExceptionUtils.getFullStackTrace(e));
				} finally {
					_logger.error("marking alert with name {} and id {} for deletion", alert.getName(), alert.getId() == null? null: alert.getId().intValue());
					_alertService.markAlertForDeletion(alert.getName(), _userService.findAdminUser());
				}
			}
		}
		
		private void publishKPIMetric(long timestamp, Double kpiValue) {
			Metric kpiMetric = new Metric(MonitorService.Counter.ALERT_EVALUATION_KPI.getScope(), MonitorService.Counter.ALERT_EVALUATION_KPI.getMetric());
			kpiMetric.setTag("host",SystemConfiguration.getHostname());
			Map<Long, Double> datapoints = new HashMap<>();
			datapoints.put(timestamp, kpiValue);
			kpiMetric.addDatapoints(datapoints);
			_alertService.exportMetric(kpiMetric, kpiValue);
			try {
				_tsdbService.putMetrics(Arrays.asList(new Metric[] {kpiMetric}));
			} catch (Exception ex) {
				_logger.error("Error occurred while pushing kpi metric . Reason: {}", ex.getMessage());
			}
		}
	}
}
/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */
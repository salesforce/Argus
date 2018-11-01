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

package com.salesforce.dva.argus.service.monitor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.Dashboard;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.entity.ServiceManagementRecord;
import com.salesforce.dva.argus.entity.ServiceManagementRecord.Service;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.entity.Trigger.TriggerType;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.AlertService;
import com.salesforce.dva.argus.service.DashboardService;
import com.salesforce.dva.argus.service.MailService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.ServiceManagementService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService;
import com.salesforce.dva.argus.service.alert.notifier.AuditNotifier;
import com.salesforce.dva.argus.service.jpa.DefaultJPAService;
import com.salesforce.dva.argus.service.metric.transform.TransformFactory.Function;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import com.sun.management.OperatingSystemMXBean;
import com.sun.management.UnixOperatingSystemMXBean;
import org.slf4j.Logger;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Default implementation of the monitor service.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
@Singleton
public class DefaultMonitorService extends DefaultJPAService implements MonitorService {

	//~ Static fields/initializers *******************************************************************************************************************

	private static final String NOTIFICATION_NAME = "monitor_notification";
	private static final String PHYSICAL_MEMORY_ALERT = "physical_memory";
	private static final String SWAP_SPACE_ALERT = "swap_space";
	private static final String FILE_DESCRIPTORS_ALERT = "file_descriptors";
	private static final String ALERT_NAME_PREFIX = "monitor-";
	private static final String HOSTNAME;
	private static long TIME_BETWEEN_RECORDINGS = 60 * 1000;

	static {
		HOSTNAME = SystemConfiguration.getHostname();
	}

	//~ Instance fields ******************************************************************************************************************************

	@SLF4JTypeListener.InjectLogger
	private Logger _logger;
	private final TSDBService _tsdbService;
	private final UserService _userService;
	private final AlertService _alertService;
	private final ServiceManagementService _serviceManagementService;
	private final DashboardService _dashboardService;
	private final MetricService _metricService;
	private final MailService _mailService;
	private final GaugeExporter _gaugeExporter;
	private final Map<Metric, Double> _metrics = new ConcurrentHashMap<>();
	private final PrincipalUser _adminUser;
	private final SystemConfiguration _sysConfig;
	private Thread _monitorThread;
	private DataLagMonitor _dataLagMonitorThread;


	//~ Constructors *********************************************************************************************************************************

	/**
	 * Creates a new DefaultMonitorService object.
	 *
	 * @param  tsdbService               The TSDB service. Cannot be null.
	 * @param  userService               The user service. Cannot be null.
	 * @param  alertService              The alert service. Cannot be null.
	 * @param  serviceManagementService  The service management service. Cannot be null.
	 * @param  dashboardService          The dashboard service. Cannot be null.
	 * @param metricService              The metric service
	 * @param mailService                The mail service
	 * @param sysConfig                  The system config
	 */
	@Inject
	public DefaultMonitorService(TSDBService tsdbService, UserService userService, AlertService alertService,
			ServiceManagementService serviceManagementService, DashboardService dashboardService, MetricService metricService, MailService mailService, 
			GaugeExporter gaugeExporter, SystemConfiguration sysConfig) {
		super(null, sysConfig);
		requireArgument(tsdbService != null, "TSDB service cannot be null.");
		requireArgument(userService != null, "User service cannot be null.");
		requireArgument(alertService != null, "Alert service cannot be null.");
		requireArgument(serviceManagementService != null, "Service management service cannot be null.");
		requireArgument(dashboardService != null, "Dashboard service cannot be null.");
		_tsdbService = tsdbService;
		_userService = userService;
		_alertService = alertService;
		_serviceManagementService = serviceManagementService;
		_dashboardService = dashboardService;
		_sysConfig = sysConfig;
		_metricService = metricService;
		_mailService = mailService;
		_adminUser = _userService.findAdminUser();
		_gaugeExporter = gaugeExporter;
	}

	//~ Methods **************************************************************************************************************************************

	private static String _constructAlertName(String type) {
		return ALERT_NAME_PREFIX + type + "-" + HOSTNAME;
	}

	private static Metric _constructCounterKey(String metricName, Map<String, String> tags) {
		SystemAssert.requireArgument(metricName != null, "Cannot create a Metric with null metric name");

		Counter counter = Counter.fromMetricName(metricName);
		String scope = counter == null ? "argus.custom" : counter.getScope();
		Metric metric = new Metric(scope, metricName);

		metric.setTags(tags);
		metric.setTag("host", HOSTNAME);
		return metric;
	}

	//~ Methods **************************************************************************************************************************************

	@Override
	@Transactional
	public synchronized void enableMonitoring() {
		requireNotDisposed();
		_logger.info("Globally enabling all system monitoring.");
		_setServiceEnabled(true);
		_checkAlertExistence(true);
		_logger.info("All system monitoring globally enabled.");
	}

	@Override
	@Transactional
	public synchronized void disableMonitoring() {
		requireNotDisposed();
		_logger.info("Globally disabling all system monitoring.");
		_setServiceEnabled(false);
		_checkAlertExistence(false);
		_logger.info("All system monitoring globally disabled.");
	}

	@Override
	@Transactional
	public synchronized void startRecordingCounters() {
		requireNotDisposed();
		if (_monitorThread != null && _monitorThread.isAlive()) {
			_logger.info("Request to start system monitoring aborted as it is already running.");
		} else {
			_logger.info("Starting system monitor thread.");
			_checkAlertExistence(true);
			_monitorThread = new MonitorThread("system-monitor");

			_monitorThread.setUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {

				@Override
				public void uncaughtException(Thread t, Throwable e) {
					_logger.error("Uncaught exception occurred while pushing monitor counters for {}. Reason: {}", HOSTNAME, e.getMessage());
					t.interrupt();
				}
			});

			_monitorThread.start();
			_logger.info("System monitor thread started.");

			if (Boolean.valueOf(_sysConfig.getValue(com.salesforce.dva.argus.system.SystemConfiguration.Property.DATA_LAG_MONITOR_ENABLED))) {
				_logger.info("Starting data lag monitor thread.");
				_dataLagMonitorThread = new DataLagMonitor(_sysConfig, _metricService, _mailService);
				_dataLagMonitorThread.start();
				_logger.info("Data lag monitor thread started.");
			}
		}
	}

	@Override
	@Transactional
	public synchronized void stopRecordingCounters() {
		requireNotDisposed();
		if (_monitorThread != null && _monitorThread.isAlive()) {
			_logger.info("Stopping system monitoring.");
			_monitorThread.interrupt();
			_logger.info("System monitor thread interrupted.");
			try {
				_logger.info("Waiting for system monitor thread to terminate.");
				_monitorThread.join();
			} catch (InterruptedException ex) {
				_logger.warn("System monitoring was interrupted while shutting down.");
			}
			_checkAlertExistence(false);
			_logger.info("System monitoring stopped.");
		} else {
			_logger.info("Requested shutdown of system monitoring aborted as it is not yet running.");
		}

		if (_dataLagMonitorThread != null && _dataLagMonitorThread.isAlive()) {
			_logger.info("Stopping data lag monitoring.");
			_dataLagMonitorThread.interrupt();
			_logger.info("System data lag monitoring thread interrupted.");
			try {
				_logger.info("Waiting for data lag monitor thread to terminate.");
				_dataLagMonitorThread.join();
			} catch (InterruptedException ex) {
				_logger.warn("Data lag monitoring thread was interrupted while shutting down.");
			}
			_logger.info("Data lag monitoring stopped.");
		} else {
			_logger.info("Requested shutdown of data lag monitoring thread aborted as it is not yet running.");
		}

	}

	@Override
	public void updateCustomCounter(String name, double value, Map<String, String> tags) {
		requireNotDisposed();
		requireArgument(name != null && !name.isEmpty(), "Cannot update a counter with null or empty name.");

		Metric metric = _constructCounterKey(name, tags);
		_gaugeExporter.exportGauge(metric, value);
		_logger.debug("Updating {} counter for {} to {}.", name, tags, value);
		_metrics.put(metric, value);
	}

	@Override
	public void updateCounter(Counter counter, double value, Map<String, String> tags) {
		requireNotDisposed();
		requireArgument(counter != null, "Cannot update a null counter.");
		requireArgument(!"argus.jvm".equalsIgnoreCase(counter.getScope()), "Cannot update JVM counters");
		updateCustomCounter(counter.getMetric(), value, tags);
	}

	@Override
	public double modifyCustomCounter(String name, double delta, Map<String, String> tags) {
		requireNotDisposed();
		SystemAssert.requireArgument(name != null && !name.isEmpty(), "Cannot modify a counter with null or empty name.");

		Metric key = _constructCounterKey(name, tags);

		synchronized (_metrics) {
			Double value = _metrics.get(key);
			double newValue = value == null ? delta : value + delta;

			_logger.debug("Modifying {} counter from {} to {}.", name, value, newValue);
			_metrics.put(key, newValue);
			_gaugeExporter.exportGauge(key, newValue);
			return newValue;
		}
	}

	@Override
	public double modifyCounter(Counter counter, double delta, Map<String, String> tags) {
		requireNotDisposed();
		requireArgument(counter != null, "Cannot modify a null counter.");
		requireArgument(!"argus.jvm".equalsIgnoreCase(counter.getScope()), "Cannot modify JVM counters");
		return modifyCustomCounter(counter.getMetric(), delta, tags);
	}

	@Override
	public double getCounter(Counter counter, Map<String, String> tags) {
		requireArgument(counter != null, "Cannot get value for a null counter.");
		return getCustomCounter(counter.getMetric(), tags);
	}

	@Override
	public double getCustomCounter(String name, Map<String, String> tags) {
		requireNotDisposed();
		requireArgument(name != null && !name.isEmpty(), "Cannot update a counter with null or empty name.");

		Metric metric = _constructCounterKey(name, tags);
		Double value;

		synchronized (_metrics) {
			value = _metrics.get(metric);
			if (value == null) {
				value = Double.NaN;
			}
		}
		_logger.debug("Value for {} counter having tags {} is {}.", name, tags, value);
		return value;
	}

	@Override
	public void resetCustomCounters() {
		requireNotDisposed();
		_resetCountersForScope("argus.custom");
	}

	@Override
	public void resetSystemCounters() {
		requireNotDisposed();
		_resetCountersForScope("argus.core");
	}

	@Override
	public void resetRuntimeCounters() {
		requireNotDisposed();
		_resetCountersForScope("argus.jvm");
	}

	@Override
	@Transactional
	public Dashboard getSystemDashboard() {
		requireNotDisposed();
		return _getDashboardForScope("System Dashboard", "argus.core");
	}

	@Override
	@Transactional
	public Dashboard getRuntimeDashboard() {
		requireNotDisposed();
		return _getDashboardForScope("Runtime Dashboard", "argus.jvm");
	}

	@Override
	public synchronized void dispose() {
		stopRecordingCounters();
		super.dispose();
		_userService.dispose();
		_dashboardService.dispose();
		_alertService.dispose();
		_serviceManagementService.dispose();
	}

	@Override
	public boolean isDataLagging() {
		if(_dataLagMonitorThread!=null) {
			return _dataLagMonitorThread.isDataLagging();
		}else {
			return false;
		}
	}

	private void _setServiceEnabled(boolean enabled) {
		synchronized (_serviceManagementService) {
			ServiceManagementRecord record = _serviceManagementService.findServiceManagementRecord(Service.MONITORING);

			if (record == null) {
				record = new ServiceManagementRecord(_userService.findAdminUser(), Service.MONITORING, enabled);
			}
			record.setEnabled(enabled);
			_serviceManagementService.updateServiceManagementRecord(record);
		}
	}

	private boolean _isMonitoringServiceEnabled() {
		//return _serviceManagementService.isServiceEnabled(Service.MONITORING);
		return true;
	}

	private void _resetCountersForScope(String scope) {
		assert (scope != null) : "Scope can not be null.";
		_logger.info("Resetting {} counters.", scope);

		List<Metric> toRemove = new LinkedList<>();

		synchronized (_metrics) {
			for (Metric metric : _metrics.keySet()) {
				if (scope.equalsIgnoreCase(metric.getScope())) {
					toRemove.add(metric);
				}
			}
			for (Metric metric : toRemove) {
				_logger.debug("Resetting counter {}.", metric);
				_metrics.remove(metric);
			}
		}
	}

	private void _updateJVMStatsCounters() {
		Counter[] counters = Counter.values();
		List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
		List<MemoryPoolMXBean> memoryPoolBeans = ManagementFactory.getMemoryPoolMXBeans();
		OperatingSystemMXBean osBean = ((OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean());

		for (Counter counter : counters) {
			if ("argus.jvm".equalsIgnoreCase(counter.getScope())) {
				Double value = null;
				String units = "count";

				switch (counter) {
				case ACTIVE_CORES:
					value = (double) Runtime.getRuntime().availableProcessors();
					break;
				case LOADED_CLASSES:
					value = (double) ManagementFactory.getClassLoadingMXBean().getLoadedClassCount();
					break;
				case UNLOAED_CLASSES:
					value = (double) ManagementFactory.getClassLoadingMXBean().getUnloadedClassCount();
					break;
				case MARKSWEEP_COUNT:
					for (GarbageCollectorMXBean bean : gcBeans) {
						if (bean.getName().toLowerCase().contains("mark")) {
							value = (double) bean.getCollectionCount();
							break;
						}
					}
					break;
				case SCAVENGE_COUNT:
					for (GarbageCollectorMXBean bean : gcBeans) {
						if (bean.getName().toLowerCase().contains("scavenge")) {
							value = (double) bean.getCollectionCount();
							break;
						}
					}
					break;
				case HEAP_USED:
					value = (double) ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();
					units = "bytes";
					break;
				case NONHEAP_USED:
					value = (double) ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage().getUsed();
					units = "bytes";
					break;
				case CODECACHE_USED:
					for (MemoryPoolMXBean bean : memoryPoolBeans) {
						if (bean.getName().toLowerCase().contains("code")) {
							value = (double) bean.getUsage().getUsed();
							units = "bytes";
							break;
						}
					}
					break;
				case EDEN_USED:
					for (MemoryPoolMXBean bean : memoryPoolBeans) {
						if (bean.getName().toLowerCase().contains("eden")) {
							value = (double) bean.getUsage().getUsed();
							units = "bytes";
							break;
						}
					}
					break;
				case OLDGEN_USED:
					for (MemoryPoolMXBean bean : memoryPoolBeans) {
						if (bean.getName().toLowerCase().contains("old")) {
							value = (double) bean.getUsage().getUsed();
							units = "bytes";
							break;
						}
					}
					break;
				case PERMGEN_USED:
					for (MemoryPoolMXBean bean : memoryPoolBeans) {
						if (bean.getName().toLowerCase().contains("perm")) {
							value = (double) bean.getUsage().getUsed();
							units = "bytes";
							break;
						}
					}
					break;
				case SURVIVOR_USED:
					for (MemoryPoolMXBean bean : memoryPoolBeans) {
						if (bean.getName().toLowerCase().contains("survivor")) {
							value = (double) bean.getUsage().getUsed();
							units = "bytes";
							break;
						}
					}
					break;
				case FREE_PHYSICAL_MEM:
					value = (double) osBean.getFreePhysicalMemorySize();
					units = "bytes";
					break;
				case FREE_SWAP_SPACE:
					value = (double) osBean.getFreeSwapSpaceSize();
					units = "bytes";
					break;
				case MAX_PHYSICAL_MEM:
					value = (double) osBean.getTotalPhysicalMemorySize();
					units = "bytes";
					break;
				case MAX_SWAP_SPACE:
					value = (double) osBean.getTotalSwapSpaceSize();
					units = "bytes";
					break;
				case OPEN_DESCRIPTORS:
					if (osBean instanceof UnixOperatingSystemMXBean) {
						value = (double) ((UnixOperatingSystemMXBean) osBean).getOpenFileDescriptorCount();
					}
					break;
				case MAX_DESCRIPTORS:
					value = (double) ((UnixOperatingSystemMXBean) osBean).getMaxFileDescriptorCount();
					break;
				case THREADS:
					value = (double) ManagementFactory.getThreadMXBean().getThreadCount();
					break;
				case PEAK_THREADS:
					value = (double) ManagementFactory.getThreadMXBean().getPeakThreadCount();
					break;
				case DAEMON_THREADS:
					value = (double) ManagementFactory.getThreadMXBean().getDaemonThreadCount();
					break;
				default:
					throw new IllegalArgumentException("Unexpected Counter: This should never happen");
				} // end switch
				if (value != null) {
					Metric metric = _constructCounterKey(counter.getMetric(), Collections.<String, String>emptyMap());

					metric.setUnits(units);
					_metrics.put(metric, value);
				}
			} // end if
		} // end for
	}

	private Dashboard _getDashboardForScope(String name, String scope) {
		String dashboardName = name + HOSTNAME;
		Dashboard dashboard;

		synchronized (_dashboardService) {
			dashboard = _dashboardService.findDashboardByNameAndOwner(dashboardName, _adminUser);
		}
		if (dashboard == null) {
			dashboard = new Dashboard(_adminUser, dashboardName, _adminUser);

			/* @todo: create dashboard content. */
			synchronized (_dashboardService) {
				dashboard = _dashboardService.updateDashboard(dashboard);
			}
		}
		return dashboard;
	}

	/**
	 * Determines if an alert exists, creates it if it doesn't and then sets it to be enabled or disabled, as required.
	 *
	 * @param   enabled  Enables or disables the alert.
	 *
	 * @throws  SystemException  If an error creating the alert occurs.
	 */
	@Transactional
	protected synchronized void _checkAlertExistence(boolean enabled) {
		for (String alertName : new String[] { FILE_DESCRIPTORS_ALERT, PHYSICAL_MEMORY_ALERT, SWAP_SPACE_ALERT }) {
			if (_alertService.findAlertByNameAndOwner(_constructAlertName(alertName), _adminUser) == null) {
				String metricExpression = null;
				TriggerType triggerType = null;
				String triggerName = null;
				double triggerThreshold = Double.NaN;

				switch (alertName) {
				case FILE_DESCRIPTORS_ALERT:

					String openFileDescMetricExp = MessageFormat.format("-1h:{0}:{1}'{'host={2}'}':avg", Counter.OPEN_DESCRIPTORS.getScope(),
							Counter.OPEN_DESCRIPTORS.getMetric(), HOSTNAME);
					String maxFileDescMetricExp = MessageFormat.format("-1h:{0}:{1}'{'host={2}'}':avg", Counter.MAX_DESCRIPTORS.getScope(),
							Counter.MAX_DESCRIPTORS.getMetric(), HOSTNAME);

					metricExpression = MessageFormat.format("{0}({1}, {2})", Function.DIVIDE.getName(), openFileDescMetricExp,
							maxFileDescMetricExp);
					triggerType = TriggerType.GREATER_THAN;
					triggerName = "Open FD > 95% of Max FD";
					triggerThreshold = 0.95;
					break;
				case PHYSICAL_MEMORY_ALERT:

					String freeMemMetricExp = MessageFormat.format("-1h:{0}:{1}'{'host={2}'}':avg", Counter.FREE_PHYSICAL_MEM.getScope(),
							Counter.FREE_PHYSICAL_MEM.getMetric(), HOSTNAME);
					String maxMemMetricExp = MessageFormat.format("-1h:{0}:{1}'{'host={2}'}':avg", Counter.MAX_PHYSICAL_MEM.getScope(),
							Counter.MAX_PHYSICAL_MEM.getMetric(), HOSTNAME);

					metricExpression = MessageFormat.format("{0}({1}, {2})", Function.DIVIDE.getName(), freeMemMetricExp, maxMemMetricExp);
					triggerType = TriggerType.LESS_THAN;
					triggerName = "Free Mem < 5% of Tot Mem";
					triggerThreshold = 0.05;
					break;
				case SWAP_SPACE_ALERT:

					String freeSSMetricExp = MessageFormat.format("-1h:{0}:{1}'{'host={2}'}':avg", Counter.FREE_SWAP_SPACE.getScope(),
							Counter.FREE_SWAP_SPACE.getMetric(), HOSTNAME);
					String maxSSMetricExp = MessageFormat.format("-1h:{0}:{1}'{'host={2}'}':avg", Counter.MAX_SWAP_SPACE.getScope(),
							Counter.MAX_SWAP_SPACE.getMetric(), HOSTNAME);

					metricExpression = MessageFormat.format("{0}({1}, {2})", Function.DIVIDE.getName(), freeSSMetricExp, maxSSMetricExp);
					triggerType = TriggerType.LESS_THAN;
					triggerName = "Free Swap Space < 5% of Tot Swap Space";
					triggerThreshold = 0.05;
					break;
				default:
					throw new SystemException("Attempting to create an unsupported monitoring alert" + alertName);
				}
				requireArgument(metricExpression != null && triggerType != null & triggerName != null, "Unsupported monitor alert " + alertName);

				Alert alert = new Alert(_adminUser, _adminUser, _constructAlertName(alertName), metricExpression, "0 * * * *");
				Notification notification = new Notification(NOTIFICATION_NAME, alert, AuditNotifier.class.getName(), new ArrayList<String>(),
						60000L);
				Trigger trigger = new Trigger(alert, triggerType, triggerName, triggerThreshold, 0);
				List<Trigger> triggers = Arrays.asList(new Trigger[] { trigger });

				notification.setTriggers(triggers);
				alert.setNotifications(Arrays.asList(new Notification[] { notification }));
				alert.setTriggers(triggers);
				alert.setEnabled(enabled);
				_alertService.updateAlert(alert);
			} else { // end if

				Alert alert = _alertService.findAlertByNameAndOwner(_constructAlertName(alertName), _adminUser);

				alert.setEnabled(enabled);
				_alertService.updateAlert(alert);
			} // end if-else
		} // end for
	}

	//~ Inner Classes ********************************************************************************************************************************

	/**
	 * Monitoring thread.
	 *
	 * @author  Tom Valine (tvaline@salesforce.com)
	 */
	private class MonitorThread extends Thread {

		/**
		 * Creates a new MonitorThread object.
		 *
		 * @param  name  The thread name.
		 */
		public MonitorThread(String name) {
			super(name);
		}

		@Override
		public void run() {
			while (!isInterrupted()) {
				_sleepForPollPeriod();
				if (!isInterrupted() && _isMonitoringServiceEnabled()) {
					try {
						_pushCounters();
					} catch (Exception ex) {
						_logger.error("Error occurred while pushing monitor counters for {}. Reason: {}", HOSTNAME, ex.getMessage());
					}
				}
			}
		}

		private void _pushCounters() {
			int sizeJVMMetrics = 0;
			_logger.debug("Pushing monitor service counters for {}.", HOSTNAME);

			Map<Metric, Double> counters = new HashMap<>();

			_updateJVMStatsCounters();

			synchronized (_metrics) {
				sizeJVMMetrics = _metrics.size();
				counters.putAll(_metrics);
				_metrics.clear();
			}

			if(counters.size() != sizeJVMMetrics){
				_logger.warn("Monitoring Service JVM Metrics and counters size are not equal");
				_logger.warn("JVM Metrics size = {}", sizeJVMMetrics);
				_logger.warn("counters size = {}", counters.size());
			}


			long timestamp = (System.currentTimeMillis() / 60000) * 60000L;

			for (Entry<Metric, Double> entry : counters.entrySet()) {
				Map<Long, Double> dataPoints = new HashMap<>(1);

				dataPoints.put(timestamp, entry.getValue());
				entry.getKey().setDatapoints(dataPoints);
				_gaugeExporter.exportGauge(entry.getKey(), entry.getValue());
			}
			if (!isDisposed()) {
				_logger.info("Pushing {} monitoring metrics to TSDB.", counters.size());
				_tsdbService.putMetrics(new ArrayList<>(counters.keySet()));
			}
		}

		private void _sleepForPollPeriod() {
			try {
				_logger.info("Sleeping for {}s before pushing counters.", TIME_BETWEEN_RECORDINGS / 1000);
				sleep(TIME_BETWEEN_RECORDINGS);
			} catch (InterruptedException ex) {
				_logger.warn("System monitoring was interrupted.");
				interrupt();
			}
		}
	}

	@Override
	public void exportMetric(Metric metric, Double value) {
		_gaugeExporter.exportGauge(metric, value);		
	}

}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
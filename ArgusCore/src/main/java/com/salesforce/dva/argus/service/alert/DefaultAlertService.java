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

package com.salesforce.dva.argus.service.alert;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.entity.History.JobStatus;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.service.AlertService;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.HistoryService;
import com.salesforce.dva.argus.service.MQService;
import com.salesforce.dva.argus.service.MailService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.MonitorService.Counter;
import com.salesforce.dva.argus.service.NotifierFactory;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.jpa.DefaultJPAService;
import com.salesforce.dva.argus.service.metric.transform.MissingDataException;
import com.salesforce.dva.argus.system.SystemConfiguration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.math.BigInteger;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TimeZone;

import javax.persistence.EntityManager;

import static com.salesforce.dva.argus.service.MQService.MQQueue.ALERT;
import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;
import static java.math.BigInteger.ZERO;

/**
 * Default implementation of the alert service.
 *
 * @author  Tom Valine (tvaline@salesforce.com), Raj sarkapally (rsarkapally@salesforce.com)
 */
public class DefaultAlertService extends DefaultJPAService implements AlertService {    

	//~ Static fields/initializers *******************************************************************************************************************

	private static final ThreadLocal<SimpleDateFormat> DATE_FORMATTER = new ThreadLocal<SimpleDateFormat>() {

		@Override
		protected SimpleDateFormat initialValue() {
			SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss z");

			sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
			return sdf;
		}
	};

	//~ Instance fields ******************************************************************************************************************************
	
	private final Logger _logger = LoggerFactory.getLogger(DefaultAlertService.class);
	private final Provider<EntityManager> _emProvider;
	private final MQService _mqService;
	private final TSDBService _tsdbService;
	private final MetricService _metricService;
	private final MailService _mailService;
	private final SystemConfiguration _configuration;
	private final HistoryService _historyService;
	private final MonitorService _monitorService;
	private final NotifierFactory _notifierFactory;

	//~ Constructors *********************************************************************************************************************************

	/**
	 * Creates a new DefaultAlertService object.
	 *
	 * @param  mqService         The MQ service instance to use. Cannot be null.
	 * @param  metricService      The Metric service instance to use. Cannot be null.
	 * @param  auditService       The audit service instance to use. Cannot be null.
	 * @param  mailService        The mail service instance to use. Cannot be null.
	 * @param  configuration      The system configuration instance to use. Cannot be null.
	 * @param  historyService     The job history service instance to use. Cannot be null.
	 * @param  monitorService     The monitor service instance to use. Cannot be null.
	 */
	@Inject
	public DefaultAlertService(SystemConfiguration configuration, MQService mqService, MetricService metricService, 
			AuditService auditService, TSDBService tsdbService, MailService mailService, HistoryService historyService, 
			MonitorService monitorService, NotifierFactory notifierFactory, Provider<EntityManager> emProvider) {
		super(auditService, configuration);
		requireArgument(mqService != null, "MQ service cannot be null.");
		requireArgument(metricService != null, "Metric service cannot be null.");
		requireArgument(tsdbService != null, "TSDB service cannot be null.");
		_tsdbService = tsdbService;
		_mqService = mqService;
		_metricService = metricService;
		_mailService = mailService;
		_configuration = configuration;
		_historyService = historyService;
		_monitorService = monitorService;
		_notifierFactory = notifierFactory;
		_emProvider = emProvider;
	}

	//~ Methods **************************************************************************************************************************************

	@Override
	@Transactional
	public Alert updateAlert(Alert alert) {
		requireNotDisposed();
		requireArgument(alert != null, "Cannot update a null alert");

		EntityManager em = _emProvider.get();
		Alert result = mergeEntity(em, alert);

		em.flush();
		_logger.debug("Updated alert to : {}", result);
		_auditService.createAudit("Updated alert to : {0}", result, result);
		return result;
	}

	@Override
	@Transactional
	public void deleteAlert(String name, PrincipalUser owner) {
		requireNotDisposed();
		requireArgument(name != null && !name.isEmpty(), "Name cannot be null or empty.");
		requireArgument(owner != null, "Owner cannot be null.");

		Alert alert = findAlertByNameAndOwner(name, owner);

		deleteAlert(alert);
	}

	@Override
	@Transactional
	public void deleteAlert(Alert alert) {
		requireNotDisposed();
		requireArgument(alert != null, "Alert cannot be null.");
		_logger.debug("Deleting an alert {}.", alert);

		EntityManager em = _emProvider.get();

		deleteEntity(em, alert);
		em.flush();
	}

	@Override
	@Transactional
	public void markAlertForDeletion(String name, PrincipalUser owner) {
		requireNotDisposed();
		requireArgument(name != null && !name.isEmpty(), "Name cannot be null or empty.");
		requireArgument(owner != null, "Owner cannot be null.");

		Alert alert = findAlertByNameAndOwner(name, owner);

		markAlertForDeletion(alert);
	}

	@Override
	@Transactional
	public void markAlertForDeletion(Alert alert) {
		requireNotDisposed();
		requireArgument(alert != null, "Alert cannot be null.");
		_logger.debug("Marking alert for deletion {}.", alert);

		EntityManager em = _emProvider.get();

		alert.setDeleted(true);
		alert.setEnabled(false);
		alert.setName(alert.getName() + System.currentTimeMillis());

		Alert result = mergeEntity(em, alert);

		em.flush();
		_logger.debug("Set delete marker for alert : {}", result);
		_auditService.createAudit("Set delete marker for alert : {0}", result, result);
	}

	@Override
	@Transactional
	public List<Alert> findAlertsMarkedForDeletion() {
		requireNotDisposed();
		return findEntitiesMarkedForDeletion(_emProvider.get(), Alert.class, -1);
	}

	@Override
	@Transactional
	public List<Alert> findAlertsMarkedForDeletion(final int limit) {
		requireNotDisposed();
		requireArgument(limit > 0, "Limit must be greater than 0.");
		return findEntitiesMarkedForDeletion(_emProvider.get(), Alert.class, limit);
	}

	@Override
	@Transactional
	public List<Alert> findAlertsByOwner(PrincipalUser owner) {
		requireNotDisposed();
		requireArgument(owner != null, "Owner cannot be null.");
		return Alert.findByOwner(_emProvider.get(), owner);
	}

	@Override
	@Transactional
	public Alert findAlertByPrimaryKey(BigInteger id) {
		requireNotDisposed();
		requireArgument(id != null && id.compareTo(ZERO) > 0, "ID must be a positive non-zero value.");

		EntityManager em = _emProvider.get();

		em.getEntityManagerFactory().getCache().evictAll();

		Alert result = Alert.findByPrimaryKey(em, id, Alert.class);

		_logger.debug("Query for alert having id {} resulted in : {}", id, result);
		return result;
	}

	@Override
	@Transactional
	public List<Alert> findAlertsByPrimaryKeys(List<BigInteger> ids) {
		requireNotDisposed();
		requireArgument(ids != null && !ids.isEmpty(), "IDs list cannot be null or empty.");

		EntityManager em = _emProvider.get();

		em.getEntityManagerFactory().getCache().evictAll();

		List<Alert> result = Alert.findByPrimaryKeys(em, ids, Alert.class);

		_logger.debug("Query for alerts having ids {} resulted in : {}", ids, result);
		return result;
	}
	
	@Override
	@Transactional
	public List<History> executeScheduledAlerts(int alertCount, int timeout) {
		requireNotDisposed();
		requireArgument(alertCount > 0, "Alert count must be greater than zero.");
		requireArgument(timeout > 0, "Timeout in milliseconds must be greater than zero.");

		List<History> historyList = new ArrayList<>();
		List<AlertIdWithTimestamp> alertIdWithTimestampList = _mqService.dequeue(ALERT.getQueueName(), AlertIdWithTimestamp.class, timeout,
				alertCount);

		List<BigInteger> alertIds = new ArrayList<>(alertIdWithTimestampList.size());
		for(AlertIdWithTimestamp alertIdWithTimestamp : alertIdWithTimestampList) {
			alertIds.add(alertIdWithTimestamp.alertId);
		}

		List<Alert> alerts = alertIds.isEmpty() ? Collections.emptyList() : findAlertsByPrimaryKeys(alertIds);
		Map<BigInteger, Alert> alertsByIds = new HashMap<>(alerts.size());
		for(Alert alert : alerts) {
			alertsByIds.put(alert.getId(), alert);
		}

		for (AlertIdWithTimestamp alertIdWithTimestamp : alertIdWithTimestampList) {
			long jobStartTime = System.currentTimeMillis();
			BigInteger alertId = alertIdWithTimestamp.alertId;

			Alert alert = alertsByIds.get(alertId);
			if(!_shouldEvaluateAlert(alert, alertId)) {
				continue;
			}

			long jobEndTime = 0;
			String logMessage = null;
			History history = new History(addDateToMessage(JobStatus.STARTED.getDescription()), SystemConfiguration.getHostname(), alert.getId(), JobStatus.STARTED);
			
			try {
				List<Metric> metrics = _metricService.getMetrics(alert.getExpression(), alertIdWithTimestamp.alertEnqueueTime);
				
				if(metrics.isEmpty()) {
					if (alert.isMissingDataNotificationEnabled()) {
						_sendNotificationForMissingData(alert);
						logMessage = MessageFormat.format("Metric data does not exit for alert expression: {0}. Sent notification for missing data.",
								alert.getExpression());
						_logger.info(logMessage);
						_appendMessageNUpdateHistory(history, logMessage, null, 0);
					} else {
						logMessage = MessageFormat.format("Metric data does not exit for alert expression: {0}. Missing data notification was not enabled.",
								alert.getExpression());
						_logger.info(logMessage);
						_appendMessageNUpdateHistory(history, logMessage, null, 0);
					}
				} else {
					Map<BigInteger, Map<Metric, Long>> triggerFiredTimesAndMetricsByTrigger = _evaluateTriggers(alert.getTriggers(), 
							metrics, history);
					for(Notification notification : alert.getNotifications()) {
						if (notification.getTriggers().isEmpty()) {
							logMessage = MessageFormat.format("The notification {0} has no triggers.", notification.getName());
							_logger.info(logMessage);
							_appendMessageNUpdateHistory(history, logMessage, null, 0);
						} else {
							_processNotification(alert, history, metrics, triggerFiredTimesAndMetricsByTrigger, notification);
						}
					}
				}
				
				jobEndTime = System.currentTimeMillis();
				_appendMessageNUpdateHistory(history, "Alert was evaluated successfully.", JobStatus.SUCCESS, jobEndTime - jobStartTime);
				
			} catch (MissingDataException mde) {
				jobEndTime = System.currentTimeMillis();
				logMessage = MessageFormat.format("Failed to evaluate alert : {0}. Reason: {1}", alert.getId(), mde.getMessage());
				_logger.warn(logMessage);
				_appendMessageNUpdateHistory(history, logMessage, JobStatus.FAILURE, jobEndTime - jobStartTime);
				if (alert.isMissingDataNotificationEnabled()) {
					_sendNotificationForMissingData(alert);
				}
			} catch (Exception ex) {
				jobEndTime = System.currentTimeMillis();
				logMessage = MessageFormat.format("Failed to evaluate alert : {0}. Reason: {1}", alert.getId(), ex.getMessage());
				_logger.warn(logMessage);
				_appendMessageNUpdateHistory(history, logMessage, JobStatus.FAILURE, jobEndTime - jobStartTime);
				
				if (Boolean.valueOf(_configuration.getValue(SystemConfiguration.Property.EMAIL_EXCEPTIONS))) {
					_sendEmailToAdmin(alert, alertId, ex);
				}
				
			} finally {
				_monitorService.modifyCounter(Counter.ALERTS_EVALUATED, 1, null);
				history = _historyService.createHistory(alert, history.getMessage(), history.getJobStatus(), history.getExecutionTime());
				historyList.add(history);
			}
		} // end for
		return historyList;
	}
	

	/**
	 * Evaluates all triggers associated with the notification and updates the job history.
	 */
	private void _processNotification(Alert alert, History history, List<Metric> metrics, 
			Map<BigInteger, Map<Metric, Long>> triggerFiredTimesAndMetricsByTrigger, Notification notification) {
		
		for(Trigger trigger : notification.getTriggers()) {
			Map<Metric, Long> triggerFiredTimesForMetrics = triggerFiredTimesAndMetricsByTrigger.get(trigger.getId());
			for(Metric m : metrics) {
				if(triggerFiredTimesForMetrics.containsKey(m)) {
					String logMessage = MessageFormat.format("The trigger {0} was evaluated against metric {1} and it is fired.", trigger.getName(), m.getIdentifier());
					_appendMessageNUpdateHistory(history, logMessage, null, 0);
					if(!notification.onCooldown(trigger, m)) {
						_updateNotificationSetActiveStatus(trigger, m, history, notification);
						_sendNotification(trigger, m, history, notification, alert, triggerFiredTimesForMetrics.get(m));
					} else {
						logMessage = MessageFormat.format("The notification {0} is on cooldown until {1}.", notification.getName(), getDateMMDDYYYY(notification.getCooldownExpirationByTriggerAndMetric(trigger, m)));
						_appendMessageNUpdateHistory(history, logMessage, null, 0);
					}
				} else {
					String logMessage = MessageFormat.format("The trigger {0} was evaluated against metric {1} and it is not fired.", trigger.getName(), m.getIdentifier());
					_appendMessageNUpdateHistory(history, logMessage, null, 0);
					if(notification.isActiveForTriggerAndMetric(trigger, m)) {
						// This is case when the notification was active for the given trigger, metric combination
						// and the metric did not violate triggering condition on current evaluation. Hence we must clear it.
						_updateNotificationClearActiveStatus(trigger, m, notification);
						_sendClearNotification(trigger, m, history, notification, alert);
					} else {
						// This is case when the notification is not active for the given trigger, metric combination
						// and the metric did not violate triggering condition on current evaluation.
						;
					}
				}
			}
		}
	}
	
	
	/**
	 * Determines if the alert should be evaluated or not.
	 */
	private boolean _shouldEvaluateAlert(Alert alert, BigInteger alertId) {
		if (alert == null) {
			_logger.warn(MessageFormat.format("Could not find alert ID {0}", alertId));
			return false;
		}
		if(!alert.isEnabled()) {
			_logger.warn(MessageFormat.format("Alert {0} has been disabled. Will not evaluate.", alert.getId()));
			return false;
		}
		
		return true;
	}
	
	
	/**
	 * Evaluates all triggers for the given set of metrics and returns a map of triggerIds to a map containing the triggered metric
	 * and the trigger fired time.
	 */
	private Map<BigInteger, Map<Metric, Long>> _evaluateTriggers(List<Trigger> triggers, List<Metric> metrics, History history) {
		Map<BigInteger, Map<Metric, Long>> triggerFiredTimesAndMetricsByTrigger = new HashMap<>();
		
		for(Trigger trigger : triggers) {
			Map<Metric, Long> triggerFiredTimesForMetrics = new HashMap<>(metrics.size());
			for(Metric metric : metrics) {
				Long triggerFiredTime = getTriggerFiredDatapointTime(trigger, metric);
				
				if (triggerFiredTime != null) {
					triggerFiredTimesForMetrics.put(metric, triggerFiredTime);
					_monitorService.modifyCounter(Counter.TRIGGERS_VIOLATED, 1, null);
				}
			}
			triggerFiredTimesAndMetricsByTrigger.put(trigger.getId(), triggerFiredTimesForMetrics);
		}
		return triggerFiredTimesAndMetricsByTrigger;
	}
	
		
	public void _sendNotification(Trigger trigger, Metric metric, History history, Notification notification, Alert alert,
			Long triggerFiredTime) {
		
		double value = metric.getDatapoints().get(triggerFiredTime);
		NotificationContext context = new NotificationContext(alert, trigger, notification, triggerFiredTime, value, metric);
		Notifier notifier = getNotifier(SupportedNotifier.fromClassName(notification.getNotifierName()));
		notifier.sendNotification(context);
		
		Map<String, String> tags = new HashMap<>();
		tags.put("status", "active");
		tags.put("type", SupportedNotifier.fromClassName(notification.getNotifierName()).name());
		_monitorService.modifyCounter(Counter.NOTIFICATIONS_SENT, 1, tags);
		
		String logMessage = MessageFormat.format("Sent alert notification and updated the cooldown: {0}",
				getDateMMDDYYYY(notification.getCooldownExpirationByTriggerAndMetric(trigger, metric)));
		_logger.info(logMessage);
		_appendMessageNUpdateHistory(history, logMessage, null, 0);
	}
	
	public void _sendClearNotification(Trigger trigger, Metric metric, History history, Notification notification, Alert alert) {
		NotificationContext context = new NotificationContext(alert, trigger, notification, System.currentTimeMillis(), 0.0, metric);
		Notifier notifier = getNotifier(SupportedNotifier.fromClassName(notification.getNotifierName()));

		notifier.clearNotification(context);
		
		Map<String, String> tags = new HashMap<>();
		tags.put("status", "clear");
		tags.put("type", SupportedNotifier.fromClassName(notification.getNotifierName()).name());
		_monitorService.modifyCounter(Counter.NOTIFICATIONS_SENT, 1, tags);
		
		String logMessage = MessageFormat.format("The notification {0} was cleared.", notification.getName());
		_logger.info(logMessage);
		_appendMessageNUpdateHistory(history, logMessage, null, 0);
	}

	private void _updateNotificationSetActiveStatus(Trigger trigger, Metric metric, History history, Notification notification) {
		notification.setCooldownExpirationByTriggerAndMetric(trigger, metric, System.currentTimeMillis() + notification.getCooldownPeriod());
		notification.setActiveForTriggerAndMetric(trigger, metric, true);
		notification = mergeEntity(_emProvider.get(), notification);
	}

	private void _updateNotificationClearActiveStatus(Trigger trigger, Metric metric, Notification notification) {
		notification.setActiveForTriggerAndMetric(trigger, metric, false);
		notification = mergeEntity(_emProvider.get(), notification);
	}
	
	private void _appendMessageNUpdateHistory(History history, String message, JobStatus jobStatus, long executionTime) {
		String oldMessage = history.getMessage();
		history.setMessage(oldMessage + addDateToMessage(message));
		if(jobStatus != null) {
			history.setJobStatus(jobStatus);
		}
		history.setExecutionTime(executionTime);
	}
	
	private void _sendEmailToAdmin(Alert alert, BigInteger alertId, Throwable ex) {
		Set<String> to = new HashSet<>();

		to.add(_configuration.getValue(SystemConfiguration.Property.ADMIN_EMAIL));

		String subject = "Alert evaluation failure notification.";
		StringBuilder message = new StringBuilder();

		message.append("<p>The evaluation for the following alert failed. </p>");
		message.append(MessageFormat.format("Alert Id: {0}", alertId));
		if (alert != null) {
			message.append(MessageFormat.format("<br> Alert name: {0} <br> Exception message: {1} ", alert.getName(), ex.toString()));
		} else {
			message.append(MessageFormat.format("<br> Exception message: The alert with id {0} does not exist.", alertId));
		}
		message.append(MessageFormat.format("<br> Time stamp: {0}", DATE_FORMATTER.get().format(new Date(System.currentTimeMillis()))));
		_mailService.sendMessage(to, subject, message.toString(), "text/html; charset=utf-8", MailService.Priority.HIGH);
		if (alert != null && alert.getOwner() != null && alert.getOwner().getEmail() != null && !alert.getOwner().getEmail().isEmpty()) {
			to.clear();
			to.add(alert.getOwner().getEmail());
			_mailService.sendMessage(to, subject, message.toString(), "text/html; charset=utf-8", MailService.Priority.HIGH);
		}
	}
	
	private void _sendNotificationForMissingData(Alert alert) {
		Set<String> to = new HashSet<>();
		to.add(alert.getOwner().getEmail());

		String subject = "Alert Missing Data Notification.";
		StringBuilder message = new StringBuilder();

		message.append("<p>This is a missing data notification. </p>");
		message.append(MessageFormat.format("Alert Id: {0}", alert.getId()));
		message.append(MessageFormat.format("<br> Alert name: {0}" , alert.getName()));
		message.append(MessageFormat.format("<br> No data found for the following metric expression: ", alert.getExpression()));
		message.append(MessageFormat.format("<br> Time stamp: {0}", DATE_FORMATTER.get().format(new Date(System.currentTimeMillis()))));
		_mailService.sendMessage(to, subject, message.toString(), "text/html; charset=utf-8", MailService.Priority.HIGH);
		
		Map<String, String> tags = new HashMap<>();
		tags.put("status", "missingdata");
		tags.put("type", SupportedNotifier.EMAIL.name());
		_monitorService.modifyCounter(Counter.NOTIFICATIONS_SENT, 1, tags);
	}
	

	@Override
	@Transactional
	public Alert findAlertByNameAndOwner(String name, PrincipalUser owner) {
		requireNotDisposed();
		requireArgument(name != null && !name.isEmpty(), "Name cannot be null or empty.");
		requireArgument(owner != null, "Owner cannot be null.");
		return Alert.findByNameAndOwner(_emProvider.get(), name, owner);
	}

	@Override
	public void enqueueAlerts(List<Alert> alerts) {
		requireNotDisposed();
		requireArgument(alerts != null, "The list of alerts cannot be null.");

		List<AlertIdWithTimestamp> idsWithTimestamp = new ArrayList<>(alerts.size());

		for (Alert alert : alerts) {
			AlertIdWithTimestamp obj = new AlertIdWithTimestamp(alert.getId(), System.currentTimeMillis());

			idsWithTimestamp.add(obj);
		}
		_monitorService.modifyCounter(Counter.ALERTS_SCHEDULED, alerts.size(), null);
		_mqService.enqueue(ALERT.getQueueName(), idsWithTimestamp);

		List<Metric> metricsAlertScheduled = new ArrayList<Metric>();

		// Write alerts scheduled for evaluation as time series to TSDB
		for (Alert alert : alerts) {
			Map<Long, Double> datapoints = new HashMap<>();
			// convert timestamp to nearest minute since cron is Least scale resolution of minute
			datapoints.put(1000*60 * (System.currentTimeMillis()/(1000 *60)), 1.0);
			Metric metric = new Metric("alerts.scheduled", "alert-" + alert.getId().toString());
			metric.setTag("host",SystemConfiguration.getHostname());
			metric.addDatapoints(datapoints);
			metricsAlertScheduled.add(metric);
		}

		try {
			_tsdbService.putMetrics(metricsAlertScheduled);
		} catch (Exception ex) {
			_logger.error("Error occured while pushing alert audit scheduling time series. Reason: {}", ex.getMessage());
		}		
	}


	@Override
	@Transactional
	public List<Alert> findAllAlerts() {
		requireNotDisposed();
		return Alert.findAll(_emProvider.get());
	}

	@Override
	@Transactional
	public List<Alert> findAlertsByStatus(boolean enabled) {
		requireNotDisposed();
		return Alert.findByStatus(_emProvider.get(), enabled);
	}

	@Override
	@Transactional
	public List<BigInteger> findAlertIdsByStatus(boolean enabled) {
		requireNotDisposed();
		return Alert.findIDsByStatus(_emProvider.get(), enabled);
	}

	@Override
	@Transactional
	public int alertCountByStatus(boolean enabled) {
		requireNotDisposed();
		return Alert.alertCountByStatus(_emProvider.get(), enabled);
	}

	@Override
	public List<Alert> findAlertsByLimitOffsetStatus(int limit, int offset,
			boolean enabled) {
		requireNotDisposed();
		return Alert.findByLimitOffsetStatus(_emProvider.get(), limit, offset, enabled);
	}

	@Override
	@Transactional
	public List<Alert> findAlertsByNameWithPrefix(String prefix) {
		requireNotDisposed();
		requireArgument(prefix != null && !prefix.isEmpty(), "Name prefix cannot be null or empty.");
		return Alert.findByPrefix(_emProvider.get(), prefix);
	}

	@Override
	public List<String> getSupportedNotifiers() {
		requireNotDisposed();

		List<String> result = new ArrayList<>(SupportedNotifier.values().length);

		for (SupportedNotifier notifier : SupportedNotifier.values()) {
			result.add(notifier.toString());
		}
		return result;
	}
	
	@Override
	@Transactional
	public List<Alert> findSharedAlerts() {
		requireNotDisposed();
		return Alert.findSharedAlerts(_emProvider.get());
	}

	/**
	 * Returns an instance of a supported notifier.
	 *
	 * @param   notifier  The supported notifier to obtain an instance for.
	 *
	 * @return  The notifier instance.
	 */
	@Override
	public Notifier getNotifier(SupportedNotifier notifier) {
		switch (notifier) {
		case EMAIL:
			return _notifierFactory.getEmailNotifier();
		case GOC:
			return _notifierFactory.getGOCNotifier();
		case DATABASE:
			return _notifierFactory.getDBNotifier();
		case WARDENAPI:
			return _notifierFactory.getWardenApiNotifier();
		case WARDENPOSTING:
			return _notifierFactory.getWardenPostingNotifier();
		case GUS:
			return _notifierFactory.getGusNotifier();
		default:
			return _notifierFactory.getDBNotifier();
		}
	}

	@Override
	public void dispose() {
		super.dispose();
		_metricService.dispose();
	}

	/**
	 * Evaluates the trigger against metric data.
	 *
	 * @param   trigger  Trigger to be evaluated.
	 * @param   metric   Metric data for the alert which the trigger belongs.
	 *
	 * @return  The time stamp of the last data point in metric at which the trigger was decided to be fired.
	 */
	public Long getTriggerFiredDatapointTime(Trigger trigger, Metric metric) {
		List<Map.Entry<Long, Double>> sortedDatapoints = new ArrayList<>(metric.getDatapoints().entrySet());

		if (metric.getDatapoints().isEmpty()) {
			return null;
		} else if (metric.getDatapoints().size() == 1) {
			if (trigger.getInertia().compareTo(0L) <= 0) {
				if (Trigger.evaluateTrigger(trigger, sortedDatapoints.get(0).getValue())) {
					return sortedDatapoints.get(0).getKey();
				} else {
					return null;
				}
			} else {
				return null;
			}
		}

		Collections.sort(sortedDatapoints, new Comparator<Map.Entry<Long, Double>>() {

			@Override
			public int compare(Entry<Long, Double> e1, Entry<Long, Double> e2) {
				return e1.getKey().compareTo(e2.getKey());
			}
		});

		int endIndex = sortedDatapoints.size();

		for(int startIndex=sortedDatapoints.size()-1; startIndex>=0; startIndex--){
			if(Trigger.evaluateTrigger(trigger, sortedDatapoints.get(startIndex).getValue())){
				Long interval = sortedDatapoints.get(endIndex-1).getKey() - sortedDatapoints.get(startIndex).getKey();
				if(interval>=trigger.getInertia())
					return sortedDatapoints.get(endIndex-1).getKey();
			}else{
				endIndex=startIndex;
			}
		}
		return null;
	}

	@Override
	@Transactional
	public void deleteTrigger(Trigger trigger) {
		requireNotDisposed();
		requireArgument(trigger != null, "Trigger cannot be null.");
		_logger.debug("Deleting trigger {}.", trigger);

		EntityManager em = _emProvider.get();

		deleteEntity(em, trigger);
		em.flush();
	}

	@Override
	@Transactional
	public void deleteNotification(Notification notification) {
		requireNotDisposed();
		requireArgument(notification != null, "Notification cannot be null.");
		_logger.debug("Deleting notification {}.", notification);

		EntityManager em = _emProvider.get();

		deleteEntity(em, notification);
		em.flush();
	}

	private String addDateToMessage(String message) {
		return MessageFormat.format("\n {0} : {1}", DATE_FORMATTER.get().format(new Date()), message);
	}

	private String getDateMMDDYYYY(long dateInSeconds) {
		String result;

		try {
			result = DATE_FORMATTER.get().format(new Date(dateInSeconds));
		} catch (Exception ex) {
			result = String.valueOf(dateInSeconds);
		}
		return result;
	}


	//~ Inner Classes ********************************************************************************************************************************

	/**
	 * Used to enqueue alerts to evaluate.  The timestamp is used to reconcile lag between enqueue time and evaluation time by adjusting relative
	 * times in the alert metric expression being evaluated.
	 *
	 * @author  Tom Valine (tvaline@salesforce.com)
	 * @todo Add data validation checks.
	 */
	public static class AlertIdWithTimestamp implements Serializable {

		/** The serial version UID. */
		private static final long serialVersionUID = 1L;
		protected BigInteger alertId;
		protected long alertEnqueueTime;

		/** Creates a new AlertIdWithTimestamp object. */
		public AlertIdWithTimestamp() { }

		/**
		 * Creates a new AlertIdWithTimestamp object.
		 *
		 * @param  id         The alert ID.  Cannot be null.
		 * @param  timestamp  The epoch timestamp the alert was enqueued for evaluation.
		 */
		public AlertIdWithTimestamp(BigInteger id, long timestamp) {
			this.alertId = id;
			this.alertEnqueueTime = timestamp;
		}

		/**
		 * Returns the alert ID.
		 *
		 * @return  The alert ID.
		 */
		public BigInteger getAlertId() {
			return alertId;
		}

		/**
		 * Sets the alert ID.
		 *
		 * @param  alertId  The alert ID.
		 */
		public void setAlertId(BigInteger alertId) {
			this.alertId = alertId;
		}

		/**
		 * Returns the epoch timestamp at which the alert was enqueued.
		 *
		 * @return  The enqueue timestamp.
		 */
		public long getAlertEnqueueTime() {
			return alertEnqueueTime;
		}

		/**
		 * Sets the epoch timestamp at which the alert was enqueued.
		 *
		 * @param  alertEnqueueTime  The enqueue timestamp.
		 */
		public void setAlertEnqueueTime(long alertEnqueueTime) {
			this.alertEnqueueTime = alertEnqueueTime;
		}
	}

	/**
	 * The context for the notification which contains relevant information for the notification occurrence.
	 *
	 * @author  Tom Valine (tvaline@salesforce.com)
	 */
	public static class NotificationContext {

		private Alert alert;
		private Trigger trigger;
		private long coolDownExpiration;
		private Notification notification;
		private long triggerFiredTime;
		private double triggerEventValue;
		private Metric triggeredMetric;

		/**
		 * Creates a new Notification Context object.
		 *
		 * @param  alert              The id of the alert for which the trigger is fired.
		 * @param  trigger            Name of the trigger fired.
		 * @param  notification       coolDownExpiration The cool down period of the notification.
		 * @param  triggerFiredTime   The time stamp of the last data point in metric at which the trigger was decided to be fired.
		 * @param  triggerEventValue  The value of the metric at the event trigger time.
		 */
		public NotificationContext(Alert alert, Trigger trigger, Notification notification, long triggerFiredTime, double triggerEventValue, Metric triggeredMetric) {
			this.alert = alert;
			this.trigger = trigger;
			this.coolDownExpiration = notification.getCooldownExpirationByTriggerAndMetric(trigger, triggeredMetric);
			this.notification = notification;
			this.triggerFiredTime = triggerFiredTime;
			this.triggerEventValue = triggerEventValue;
			this.triggeredMetric = triggeredMetric;
		}

		/** Creates a new NotificationContext object. */
		protected NotificationContext() { }

		/**
		 * returns the alert id.
		 *
		 * @return  Id of the alert
		 */
		public Alert getAlert() {
			return alert;
		}

		/**
		 * Sets the alert id.
		 *
		 * @param  alert  Id of the alert for which the trigger is fired.
		 */
		public void setAlert(Alert alert) {
			this.alert = alert;
		}

		/**
		 * returns the trigger.
		 *
		 * @return  trigger Trigger Object.
		 */
		public Trigger getTrigger() {
			return trigger;
		}

		/**
		 * sets the trigger.
		 *
		 * @param  trigger  Trigger Object.
		 */
		public void setTriggerName(Trigger trigger) {
			this.trigger = trigger;
		}

		/**
		 * Returns the cool down period.
		 *
		 * @return  The cool down period of the notification.
		 */
		public long getCoolDownExpiration() {
			return coolDownExpiration;
		}

		/**
		 * Sets the cool down period.
		 *
		 * @param  coolDownExpiration  cool down period of the notification.
		 */
		public void setCoolDownExpiration(long coolDownExpiration) {
			this.coolDownExpiration = coolDownExpiration;
		}

		/**
		 * returns the notification object.
		 *
		 * @return  the notification object for which the trigger is fired.
		 */
		public Notification getNotification() {
			return notification;
		}

		/**
		 * Sets the notification object.
		 *
		 * @param  notification  the notification object for which the trigger is fired.
		 */
		public void setNotificationName(Notification notification) {
			this.notification = notification;
		}

		/**
		 * returns the last time stamp in metric at which the trigger was decided to be fired.
		 *
		 * @return  The time stamp of the last data point in metric at which the trigger was decided to be fired.
		 */
		public long getTriggerFiredTime() {
			return triggerFiredTime;
		}

		/**
		 * Sets the trigger fired time.
		 *
		 * @param  triggerFiredTime  The time stamp of the last data point in metric at which the trigger was decided to be fired.
		 */
		public void setTriggerFiredTime(long triggerFiredTime) {
			this.triggerFiredTime = triggerFiredTime;
		}

		/**
		 * Returns the event trigger value.
		 *
		 * @return  The event trigger value.
		 */
		public double getTriggerEventValue() {
			return triggerEventValue;
		}

		/**
		 * Sets the event trigger value.
		 *
		 * @param  triggerEventValue  The event trigger value.
		 */
		public void setTriggerEventValue(double triggerEventValue) {
			this.triggerEventValue = triggerEventValue;
		}

		public Metric getTriggeredMetric() {
			return triggeredMetric;
		}

		public void setTriggeredMetric(Metric triggeredMetric) {
			this.triggeredMetric = triggeredMetric;
		}

	}

}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
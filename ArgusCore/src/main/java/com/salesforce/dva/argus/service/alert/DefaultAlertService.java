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
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.AlertService;
import com.salesforce.dva.argus.service.AnnotationService;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.HistoryService;
import com.salesforce.dva.argus.service.MQService;
import com.salesforce.dva.argus.service.MailService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.MonitorService.Counter;
import com.salesforce.dva.argus.service.NotifierFactory;
import com.salesforce.dva.argus.service.jpa.DefaultJPAService;
import com.salesforce.dva.argus.service.metric.transform.MissingDataException;
import com.salesforce.dva.argus.system.SystemConfiguration;

import org.slf4j.Logger;

import java.io.Serializable;
import java.math.BigInteger;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
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

	@SLF4JTypeListener.InjectLogger
	private Logger _logger;
	@Inject
	private Provider<EntityManager> emf;
	private final MQService _mqService;
	private final MetricService _metricService;
	private final AnnotationService _annotationService;
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
	 * @param  annotationService  The Annotation service instance to use. Cannot be null.
	 * @param  auditService       The audit service instance to use. Cannot be null.
	 * @param  mailService        The mail service instance to use. Cannot be null.
	 * @param  configuration      The system configuration instance to use. Cannot be null.
	 * @param  historyService     The job history service instance to use. Cannot be null.
	 * @param  monitorService     The monitor service instance to use. Cannot be null.
	 */
	@Inject
	public DefaultAlertService(MQService mqService, MetricService metricService, AnnotationService annotationService, AuditService auditService,
			MailService mailService, SystemConfiguration configuration, HistoryService historyService, MonitorService monitorService, NotifierFactory notifierFactory) {
		super(auditService, configuration);
		requireArgument(mqService != null, "MQ service cannot be null.");
		requireArgument(metricService != null, "Metric service cannot be null.");
		requireArgument(annotationService != null, "Annotation service cannot be null.");
		_mqService = mqService;
		_metricService = metricService;
		_annotationService = annotationService;
		_mailService = mailService;
		_configuration = configuration;
		_historyService = historyService;
		_monitorService = monitorService;
		_notifierFactory = notifierFactory;

	}

	//~ Methods **************************************************************************************************************************************

	@Override
	@Transactional
	public Alert updateAlert(Alert alert) {
		requireNotDisposed();
		requireArgument(alert != null, "Cannot update a null alert");

		EntityManager em = emf.get();
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

		EntityManager em = emf.get();

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

		EntityManager em = emf.get();

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
		return findEntitiesMarkedForDeletion(emf.get(), Alert.class);
	}

	@Override
	@Transactional
	public List<Alert> findAlertsByOwner(PrincipalUser owner) {
		requireNotDisposed();
		requireArgument(owner != null, "Owner cannot be null.");
		return Alert.findByOwner(emf.get(), owner);
	}

	@Override
	@Transactional
	public Alert findAlertByPrimaryKey(BigInteger id) {
		requireNotDisposed();
		requireArgument(id != null && id.compareTo(ZERO) > 0, "ID must be a positive non-zero value.");

		EntityManager em = emf.get();

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

        EntityManager em = emf.get();

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
		EntityManager em = emf.get();
		int failedNotificationsCount = 0;
		String logMessage = null;
		long jobEndTime = 0;

		for (AlertIdWithTimestamp alertIdWithTimestamp : alertIdWithTimestampList) {
			long jobStartTime = System.currentTimeMillis();
			BigInteger alertID = alertIdWithTimestamp.alertId;
			Long enqueuedTimestamp = alertIdWithTimestamp.alertEnqueueTime;

			failedNotificationsCount = 0;

			Alert alert = findAlertByPrimaryKey(alertID);

			if (alert == null) {
				logMessage = MessageFormat.format("Could not find alert ID {0}", alertID);
				_logger.warn(logMessage);
				continue;
			}

			History history = _historyService.createHistory(addDateToMessage(JobStatus.STARTED.getDescription()), alert, JobStatus.STARTED, 0, 0);

			try {
				List<Metric> metrics = _metricService.getMetrics(alert.getExpression(), enqueuedTimestamp - System.currentTimeMillis());

				if (metrics == null || metrics.isEmpty()) {
					logMessage = "The metric expression associated with the alert did not return any metric data.";
					_logger.info(logMessage);
					appendMessageNUpdateHistory(history.getId(), logMessage, null, 0, 0);
					continue;
				}
				for (Metric metric : metrics) {
					if (!shouldEvaluateMetric(metric, alert, history.getId())) {
						continue;
					}
					for (Notification notification : alert.getNotifications()) {
						boolean successfullyProcessed = processNotification(notification, history.getId(), metric, alert, em);

						if (!successfullyProcessed) {
							failedNotificationsCount++;
						}
					}
				}
				jobEndTime = System.currentTimeMillis();
				if (failedNotificationsCount > 0) {
					logMessage = MessageFormat.format("No.of notifications failed: {0}", failedNotificationsCount);
					_logger.info(logMessage);
					appendMessageNUpdateHistory(history.getId(), logMessage, null, 0, 0);
				}
				appendMessageNUpdateHistory(history.getId(), "Alert was evaluated successfully.", JobStatus.SUCCESS, 0, jobEndTime - jobStartTime);
			} catch (MissingDataException mde) {
				jobEndTime = System.currentTimeMillis();
				logMessage = MessageFormat.format("Failed to evaluate an alert : {0}. Reason: {1}", alert.getName(), mde.getMessage());
				_logger.warn(logMessage);
				appendMessageNUpdateHistory(history.getId(), mde.toString(), JobStatus.FAILURE, 0, jobEndTime - jobStartTime);
				if (alert.isMissingDataNotificationEnabled()) {
					_sendNotifocationForMissingData(alert);
				}
			} catch (Exception ex) {
				jobEndTime = System.currentTimeMillis();
				try {
					appendMessageNUpdateHistory(history.getId(), ex.toString(), JobStatus.FAILURE, 0, jobEndTime - jobStartTime);
					_logger.warn("Failed to evaluate alert : {}. Reason: {}", alert, ex.getMessage());
				} finally {
					sendEmailToAdmin(alert, alertID, ex);
				}
			} finally {
				_monitorService.modifyCounter(Counter.ALERTS_EVALUATED, 1, null);
				historyList.add(history);
			}
		} // end for
		return historyList;
	}

	/**
	 * Evaluates all triggers associated with the notification and updates the job history.
	 *
	 * @param   notification  The notification to be evaluated
	 * @param   historyId     Job history object
	 * @param   metric        metric associated with an alert
	 * @param   alert         The alert for which the notification belongs to
	 * @param   em            Entity manager.
	 *
	 * @return  Returns true if the notification is successfully evaluated.
	 */
	private boolean processNotification(Notification notification, BigInteger historyId, Metric metric, Alert alert, EntityManager em) {
		if (!shouldEvaluateNotification(notification, historyId)) {
			return true;
		}

		String logMessage = null;

		if (notification.isActive() && notification.getFiredTrigger() != null) {
			Long triggerFiredTime = getTriggerFiredDatapointTime(notification.getFiredTrigger(), metric);

			if (triggerFiredTime == null) {
				clearNotification(notification.getFiredTrigger(), metric, historyId, notification, em, alert);
			}
		}
		try {
			if (!notification.onCooldown()) {
				for (Trigger trigger : notification.getTriggers()) {
					boolean triggerFired = evaluateTrigger(trigger, metric, historyId, notification, em, alert);

					if (triggerFired) {
						break;
					}
				}
			} else {
				logMessage = MessageFormat.format("The notification {0} is on cooldown until {1}.", notification.getName(),
						getDateMMDDYYYY(notification.getCooldownExpiration()));
				_logger.info(logMessage);
				appendMessageNUpdateHistory(historyId, logMessage, null, 0, 0);
			}
		} catch (Exception ex) {
			logMessage = MessageFormat.format("Exception occured while processing the notification: {0}. Reason: {1}", notification.getName(),
					ex.toString());
			_logger.warn("Exception occured while processing the notification: {}. Reason: {}", notification, ex.toString());
			appendMessageNUpdateHistory(historyId, logMessage, null, 0, 0);
			return false;
		}
		return true;
	}

	/**
	 * Evaluates the triggering condition.
	 *
	 * @param   trigger       Trigger to be evaluated
	 * @param   metric        Metric associated with the alert
	 * @param   historyId     Job history for this alert evaluation
	 * @param   notification  Notification to which trigger belongs to
	 * @param   em            Entity manager
	 * @param   alert         The alert to which the trigger belongs to
	 *
	 * @return  Returns true if the trigger is fired and notification is sent otherwise false.
	 */
	private boolean evaluateTrigger(Trigger trigger, Metric metric, BigInteger historyId, Notification notification, EntityManager em, Alert alert) {
		Long triggerFiredTime = getTriggerFiredDatapointTime(trigger, metric);

		if (triggerFiredTime != null) {
			sendNotification(trigger, metric, historyId, notification, em, alert, triggerFiredTime);
			return true;
		} else {
			String logMessage = MessageFormat.format("The trigger {0} was evaluated against metric {1} and it is not fired for the notification {2}.",
					trigger.getName(), getMetricExpression(metric), notification.getName());

			_logger.info(logMessage);
			_historyService.appendMessageAndUpdate(historyId, logMessage, null, 0, 0);
		}
		return false;
	}

	private void sendNotification(Trigger trigger, Metric metric, BigInteger historyId, Notification notification, EntityManager em, Alert alert,
			Long triggerFiredTime) {
		String logMessage = MessageFormat.format("The trigger {0} was evaluated against metric {1} and it is fired for the notification {2}.",
				trigger.getName(), getMetricExpression(metric), notification.getName());

		_logger.info(logMessage);
		appendMessageNUpdateHistory(historyId, logMessage, null, 0, 0);

		String value = metric.getDatapoints().get(triggerFiredTime);

		notification.setCooldownExpiration(System.currentTimeMillis() + notification.getCooldownPeriod());
		notification.setActive(true);
		notification.setFiredTrigger(trigger);
		notification = mergeEntity(em, notification);

		NotificationContext context = new NotificationContext(alert, trigger, notification, triggerFiredTime, value);
		Notifier notifier = getNotifier(SupportedNotifier.fromClassName(notification.getNotifierName()));

		notifier.sendNotification(context);
		logMessage = MessageFormat.format("Sent alert notification and updated the cooldown: {0}",
				getDateMMDDYYYY(notification.getCooldownExpiration()));
		_logger.info(logMessage);
		appendMessageNUpdateHistory(historyId, logMessage, null, 0, 0);
	}

	private void clearNotification(Trigger trigger, Metric metric, BigInteger historyId, Notification notification, EntityManager em, Alert alert) {
		String logMessage = null;
		String value = "0";

		notification.setActive(false);
		notification.setFiredTrigger(null);
		notification = mergeEntity(em, notification);

		NotificationContext context = new NotificationContext(alert, trigger, notification, System.currentTimeMillis(), value);
		Notifier notifier = getNotifier(SupportedNotifier.fromClassName(notification.getNotifierName()));

		notifier.clearNotification(context);
		logMessage = MessageFormat.format("The notification {0} was cleared.", notification.getName());
		_logger.info(logMessage);
		appendMessageNUpdateHistory(historyId, logMessage, null, 0, 0);
	}

	private boolean shouldEvaluateMetric(Metric metric, Alert alert, BigInteger historyId) {
		String logMessage = null;

		if (metric.getDatapoints().isEmpty()) {
			if (alert.isMissingDataNotificationEnabled()) {
				_sendNotifocationForMissingData(alert);
				logMessage = MessageFormat.format("Metric data does not exit for metric: {0}. Sent notification for missing data.",
						getMetricExpression(metric));
				_logger.info(logMessage);
				appendMessageNUpdateHistory(historyId, logMessage, null, 0, 0);
			} else {
				logMessage = MessageFormat.format("Metric data does not exit for metric: {0}. Missing data notification was not enabled.",
						getMetricExpression(metric));
				_logger.info(logMessage);
				appendMessageNUpdateHistory(historyId, logMessage, JobStatus.SUCCESS, 0, 0);
			}
			return false;
		}
		return true;
	}

	private History appendMessageNUpdateHistory(BigInteger historyId, String message, JobStatus jobStatus, long waitTime, long executionTime) {
		return _historyService.appendMessageAndUpdate(historyId, addDateToMessage(message), jobStatus, waitTime, executionTime);
	}

	private boolean shouldEvaluateNotification(Notification notification, BigInteger historyId) {
		if (notification.getTriggers().isEmpty()) {
			String logMessage = MessageFormat.format("The notification {0} has no triggers.", notification.getName());

			_logger.info(logMessage);
			appendMessageNUpdateHistory(historyId, logMessage, null, 0, 0);
			return false;
		}
		return true;
	}

	private void sendEmailToAdmin(Alert alert, BigInteger alertId, Throwable ex) {
		Set<String> to = new HashSet<>();

		to.add(_configuration.getValue(com.salesforce.dva.argus.system.SystemConfiguration.Property.ADMIN_EMAIL));

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

	private void _sendNotifocationForMissingData(Alert alert) {
		Set<String> to = new HashSet<>();

		to.add(alert.getOwner().getEmail());
		for (Notification notification : alert.getNotifications()) {
			to.addAll(notification.getSubscriptions());
		}

		String subject = "Alert scheduling failure notification.";
		StringBuilder message = new StringBuilder();

		message.append("<p>The scheduling for the following alert was failed. </p>");
		message.append(MessageFormat.format("Alert Id: {0}", alert.getId()));
		message.append(MessageFormat.format("<br> Alert name: {0} <br> Exception message: {1} ", alert.getName(),
				"The data for the metric expression is not available."));
		message.append(MessageFormat.format("<br> Time stamp: {0}", DATE_FORMATTER.get().format(new Date(System.currentTimeMillis()))));
		_mailService.sendMessage(to, subject, message.toString(), "text/html; charset=utf-8", MailService.Priority.HIGH);
	}

	@Override
	@Transactional
	public Alert findAlertByNameAndOwner(String name, PrincipalUser owner) {
		requireNotDisposed();
		requireArgument(name != null && !name.isEmpty(), "Name cannot be null or empty.");
		requireArgument(owner != null, "Owner cannot be null.");
		return Alert.findByNameAndOwner(emf.get(), name, owner);
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
		for (Alert alert : alerts) {
			if (findAlertByPrimaryKey(alert.getId()) == null) {
				_logger.warn("Could not find alert ID {}", alert.getId());
				continue;
			}
			_historyService.createHistory(addDateToMessage("Alert queued for evaluation"), alert, JobStatus.QUEUED, 0, 0);
		}
	}

	@Override
	@Transactional
	public List<Alert> findAllAlerts() {
		requireNotDisposed();
		return Alert.findAll(emf.get());
	}

	@Override
	@Transactional
	public List<Alert> findAlertsByStatus(boolean enabled) {
		requireNotDisposed();
		return Alert.findByStatus(emf.get(), enabled);
	}
	
	@Override
	@Transactional
	public List<BigInteger> findAlertIdsByStatus(boolean enabled) {
		requireNotDisposed();
		return Alert.findIDsByStatus(emf.get(), enabled);
	}

	@Override
	@Transactional
	public int alertCountByStatus(boolean enabled) {
		requireNotDisposed();
		return Alert.alertCountByStatus(emf.get(), enabled);
	}

	@Override
	public List<Alert> findAlertsByLimitOffsetStatus(int limit, int offset,
			boolean enabled) {
		requireNotDisposed();
		return Alert.findByLimitOffsetStatus(emf.get(), limit, offset, enabled);
	}

	@Override
	@Transactional
	public List<Alert> findAlertsByNameWithPrefix(String prefix) {
		requireNotDisposed();
		requireArgument(prefix != null && !prefix.isEmpty(), "Name prefix cannot be null or empty.");
		return Alert.findByPrefix(emf.get(), prefix);
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
		_annotationService.dispose();
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
		List<Map.Entry<Long, String>> sortedDatapoints = new ArrayList<>(metric.getDatapoints().entrySet());

		if (metric.getDatapoints().isEmpty()) {
			return null;
		} else if (metric.getDatapoints().size() == 1) {
			if (trigger.getInertia().compareTo(0L) <= 0) {
				if (Trigger.evaluateTrigger(trigger, new Double(sortedDatapoints.get(0).getValue()))) {
					return sortedDatapoints.get(0).getKey();
				} else {
					return null;
				}
			} else {
				return null;
			}
		}

		long datapointsInterval, inertialLength;
		int inertiaDatapointsCount, count = 0, left, right, currWindowStart;

		Collections.sort(sortedDatapoints, new Comparator<Map.Entry<Long, String>>() {

			@Override
			public int compare(Entry<Long, String> e1, Entry<Long, String> e2) {
				return e1.getKey().compareTo(e2.getKey());
			}
		});
		datapointsInterval = sortedDatapoints.get(1).getKey() - sortedDatapoints.get(0).getKey();
		inertialLength = trigger.getInertia() / datapointsInterval;
		if (trigger.getInertia() < datapointsInterval) {
			inertiaDatapointsCount = 1;
		} else if (inertialLength * datapointsInterval < trigger.getInertia()) {
			inertiaDatapointsCount = (int) inertialLength + 2;
		} else {
			inertiaDatapointsCount = (int) inertialLength + 1;
		}
		if (inertiaDatapointsCount > sortedDatapoints.size()) {
			return null;
		}
		currWindowStart = left = sortedDatapoints.size() - inertiaDatapointsCount;
		right = sortedDatapoints.size() - 1; // -1 as index starts from 0
		while (left <= right) {
			if (sortedDatapoints.get(left).getValue() != null &&
					Trigger.evaluateTrigger(trigger, new Double(sortedDatapoints.get(left).getValue()))) {
				count++;
				left++;
			} else {
				right = currWindowStart - 1;
				left = right - inertiaDatapointsCount + count + 1; // +1 as index starts from 0
				count = 0;
				currWindowStart = left;
				if (left < 0) {
					return null;
				}
			}
		}
		return sortedDatapoints.get(currWindowStart + inertiaDatapointsCount - 1).getKey(); // -1 as index starts from 0
	}

	@Override
	@Transactional
	public void deleteTrigger(Trigger trigger) {
		requireNotDisposed();
		requireArgument(trigger != null, "Trigger cannot be null.");
		_logger.debug("Deleting trigger {}.", trigger);

		EntityManager em = emf.get();

		deleteEntity(em, trigger);
		em.flush();
	}

	@Override
	@Transactional
	public void deleteNotification(Notification notification) {
		requireNotDisposed();
		requireArgument(notification != null, "Notification cannot be null.");
		_logger.debug("Deleting notification {}.", notification);

		EntityManager em = emf.get();

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

	private String getMetricExpression(Metric metric) {
		StringBuilder result = new StringBuilder();

		result.append("{Scope=");
		result.append(metric.getScope());
		result.append(", Metric=");
		result.append(metric.getMetric());
		if (!metric.getTags().isEmpty()) {
			result.append(", Tags:");
			result.append(metric.getTags().entrySet());
		}
		result.append("}");
		return result.toString();
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
		protected Long alertEnqueueTime;

		/** Creates a new AlertIdWithTimestamp object. */
		public AlertIdWithTimestamp() { }

		/**
		 * Creates a new AlertIdWithTimestamp object.
		 *
		 * @param  id         The alert ID.  Cannot be null.
		 * @param  timestamp  The epoch timestamp the alert was enqueued for evaluation.
		 */
		public AlertIdWithTimestamp(BigInteger id, Long timestamp) {
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
		public Long getAlertEnqueueTime() {
			return alertEnqueueTime;
		}

		/**
		 * Sets the epoch timestamp at which the alert was enqueued.
		 *
		 * @param  alertEnqueueTime  The enqueue timestamp.
		 */
		public void setAlertEnqueueTime(Long alertEnqueueTime) {
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
		private String triggerEventValue;

		/**
		 * Creates a new Notification Context object.
		 *
		 * @param  alert              The id of the alert for which the trigger is fired.
		 * @param  trigger            Name of the trigger fired.
		 * @param  notification       coolDownExpiration The cool down period of the notification.
		 * @param  triggerFiredTime   The time stamp of the last data point in metric at which the trigger was decided to be fired.
		 * @param  triggerEventValue  The value of the metric at the event trigger time.
		 */
		public NotificationContext(Alert alert, Trigger trigger, Notification notification, long triggerFiredTime, String triggerEventValue) {
			this.alert = alert;
			this.trigger = trigger;
			this.coolDownExpiration = notification.getCooldownExpiration();
			this.notification = notification;
			this.triggerFiredTime = triggerFiredTime;
			this.triggerEventValue = triggerEventValue;
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
		public String getTriggerEventValue() {
			return triggerEventValue;
		}

		/**
		 * Sets the event trigger value.
		 *
		 * @param  triggerEventValue  The event trigger value.
		 */
		public void setTriggerEventValue(String triggerEventValue) {
			this.triggerEventValue = triggerEventValue;
		}
	}

}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

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

import static com.salesforce.dva.argus.service.MQService.MQQueue.ALERT;
import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;
import static java.math.BigInteger.ZERO;

import java.io.Serializable;
import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.Map.Entry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.persistence.EntityManager;

import com.google.common.annotations.VisibleForTesting;
import com.salesforce.dva.argus.entity.*;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import com.salesforce.dva.argus.entity.History.JobStatus;
import com.salesforce.dva.argus.entity.Trigger.TriggerType;
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
import com.salesforce.dva.argus.service.metric.MetricQueryResult;
import com.salesforce.dva.argus.service.metric.transform.MissingDataException;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.util.AlertUtils;
import com.salesforce.dva.argus.util.MonitoringUtils;
import com.salesforce.dva.argus.util.RequestContext;
import com.salesforce.dva.argus.util.RequestContextHolder;

/**
 * Default implementation of the alert service.
 *
 * @author  Tom Valine (tvaline@salesforce.com), Raj sarkapally (rsarkapally@salesforce.com), Dongpu Jin (djin@salesforce.com)
 */
public class DefaultAlertService extends DefaultJPAService implements AlertService {    

	//~ Static fields/initializers *******************************************************************************************************************

	private static final String ACTIONTAG = "action";
	private static final String ALERTIDTAG = "alertId";
	private static final String RETRIESTAG = "retries";
	private static final String HOSTTAG = "host";
	private static final String NOTIFYTARGETTAG = "notifyTarget";
	private static final String STATUSTAG = "status";
	private static final String USERTAG = "user";

	private static final String ACTION_CLEARED = "cleared";
	private static final String ACTION_MISSINGDATA = "missingdata";
	private static final String ACTION_TRIGGERED = "triggered";
	private static final String ALERTSCOPE = "argus.alerts";
	private static final BigInteger DEFAULTALERTID = new BigInteger("0");
	private static final String DEFAULTUSER = "none";
	private static final String STATUS_SUCCESS = "succeeded";
	private static final String STATUS_FAILURE = "failed";
	private static final Long EVALUATIONDELAY = 1000L * 60;
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
	private final ObjectMapper _mapper = new ObjectMapper();
	private static NotificationsCache _notificationsCache = null;
	private static List<Pattern> _whiteListedScopeRegexPatterns = null;
	private static List<Pattern> _whiteListedUserRegexPatterns = null;
	private static final String HOSTNAME;

	//~ Constructors *********************************************************************************************************************************

	static {
		// Can fail if DNS is broken.
		// ToDo Handle the failure.
		HOSTNAME = SystemConfiguration.getHostname();
	}

	/**
	 * Creates a new DefaultAlertService object.
	 *
	 * @param  configuration      The system configuration instance to use. Cannot be null.
	 * @param  mqService         The MQ service instance to use. Cannot be null.
	 * @param  metricService      The Metric service instance to use. Cannot be null.
	 * @param  auditService       The audit service instance to use. Cannot be null.
	 * @param  tsdbService        The TSDB service instance to use.
	 * @param  mailService        The mail service instance to use. Cannot be null.
	 * @param  historyService     The job history service instance to use. Cannot be null.
	 * @param  monitorService     The monitor service instance to use. Cannot be null.
	 * @param  notifierFactory		The notifier factory to use
	 * @param  emProvider			The entity manager provider to use
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

		_initializeObjectMapper();
	}

	//~ Methods **************************************************************************************************************************************

	private void _initializeObjectMapper() {

		SimpleModule module = new SimpleModule();
		module.addSerializer(Alert.class, new Alert.Serializer());
		module.addSerializer(Trigger.class, new Trigger.Serializer());
		module.addSerializer(Notification.class, new Notification.Serializer());
		module.addSerializer(PrincipalUser.class, new Alert.PrincipalUserSerializer());
		module.addDeserializer(Alert.class, new Alert.Deserializer());

		_mapper.registerModule(module);
	}

	@Override
	@Transactional
	public Alert updateAlert(Alert alert) throws RuntimeException {
		requireNotDisposed();
		requireArgument(alert != null, "Cannot update a null alert");

		alert.validateAlert(); // prevent any invalid alerts from being committed to the database.

		alert.setModifiedDate(new Date());

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
		alert.setName(alert.getName() + System.currentTimeMillis());
		alert.setModifiedDate(new Date());
		alert.setEnabled(false);
		Alert result = mergeEntity(em, alert);

		em.flush();
		_logger.debug("Set delete marker for alert : {}", result);
		_auditService.createAudit("Set delete marker for alert : {0}", result, result);
	}

	@Override
	public List<Alert> findAlertsMarkedForDeletion() {
		requireNotDisposed();
		return findEntitiesMarkedForDeletion(_emProvider.get(), Alert.class, -1);
	}

	@Override
	public List<Alert> findAlertsMarkedForDeletion(final int limit) {
		requireNotDisposed();
		requireArgument(limit > 0, "Limit must be greater than 0.");
		return findEntitiesMarkedForDeletion(_emProvider.get(), Alert.class, limit);
	}

	@Override
	public List<Alert> findAlertsByOwner(PrincipalUser owner, boolean metadataOnly) {
		requireNotDisposed();
		requireArgument(owner != null, "Owner cannot be null.");

		return metadataOnly ? Alert.findByOwnerMeta(_emProvider.get(), owner) : Alert.findByOwner(_emProvider.get(), owner);
	}

	@Override
	public List<Alert> findAlertsByOwnerPaged(PrincipalUser owner, Integer limit, Integer offset, String searchText, String sortField, String sortOrder) {
		requireNotDisposed();
		requireArgument(owner != null, "Owner cannot be null.");

		return Alert.findByOwnerMetaPaged(_emProvider.get(), owner, limit, offset, searchText, sortField, sortOrder);
	}

	@Override
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
	public void updateNotificationsActiveStatusAndCooldown(List<Notification> notifications) {
		List<BigInteger> ids = notifications.stream().map(x -> x.getId()).collect(Collectors.toList());
		_logger.debug("Updating notifications: {}", ids);
		if(_notificationsCache == null) {
			synchronized(DefaultAlertService.class) {
				if(_notificationsCache == null) {
					_notificationsCache = new NotificationsCache(_emProvider);
				}
			}
		}

		// if cache is refreshed, we read the cooldown and trigger info from cache, else we query the db directly
		if(_notificationsCache.isNotificationsCacheRefreshed()) {
			for(Notification notification : notifications) {
				if(_notificationsCache.getNotificationActiveStatusMap().get(notification.getId())!=null) {
					notification.setActiveStatusMap(_notificationsCache.getNotificationActiveStatusMap().get(notification.getId()));
				}else {
					notification.getActiveStatusMap().clear();
				}
				if(_notificationsCache.getNotificationCooldownExpirationMap().get(notification.getId())!=null) {
					notification.setCooldownExpirationMap(_notificationsCache.getNotificationCooldownExpirationMap().get(notification.getId()));
				}else {
					notification.getCooldownExpirationMap().clear();
				}
			}
		}else {
			Notification.updateActiveStatusAndCooldown(_emProvider.get(), notifications);
		}
	}

	@Override
	@Transactional
	public List<History> executeScheduledAlerts(int alertCount, int timeout) {
		requireNotDisposed();
		requireArgument(alertCount > 0, "Alert count must be greater than zero.");
		requireArgument(timeout > 0, "Timeout in milliseconds must be greater than zero.");

		List<History> historyList = new ArrayList<>();
		List<AlertWithTimestamp> alertsWithTimestamp = _mqService.dequeue(ALERT.getQueueName(), AlertWithTimestamp.class, timeout,
				alertCount);

		List<Notification> allNotifications = new ArrayList<>();
		Map<BigInteger, Alert> alertsByNotificationId = new HashMap<>();
		Map<BigInteger, Long> alertEnqueueTimestampsByAlertId = new HashMap<>();

		if (_whiteListedScopeRegexPatterns == null) {
			String whiteListedScopesProperty = _configuration.getValue(SystemConfiguration.Property.DATA_LAG_WHITE_LISTED_SCOPES);
			if (!StringUtils.isEmpty(whiteListedScopesProperty)) {
				_whiteListedScopeRegexPatterns = Stream.of(whiteListedScopesProperty.split(",")).map(elem -> Pattern.compile(elem.toLowerCase())).collect(Collectors.toList());
			} else {
				_whiteListedScopeRegexPatterns = new ArrayList<Pattern>();
			}
		}

		if (_whiteListedUserRegexPatterns == null) {
			String whiteListedUsersProperty = _configuration.getValue(SystemConfiguration.Property.DATA_LAG_WHITE_LISTED_USERS);
			if (!StringUtils.isEmpty(whiteListedUsersProperty)) {
				_whiteListedUserRegexPatterns = Stream.of(whiteListedUsersProperty.split(",")).map(elem -> Pattern.compile(elem.toLowerCase())).collect(Collectors.toList());
			} else {
				_whiteListedUserRegexPatterns = new ArrayList<Pattern>();
			}
		}

		_monitorService.modifyCounter(Counter.ALERTS_EVALUATED_RAWTOTAL, alertsWithTimestamp.size(), new HashMap<>());
		for(AlertWithTimestamp alertWithTimestamp : alertsWithTimestamp) {
			String serializedAlert = alertWithTimestamp.getSerializedAlert();

			_logger.debug(MessageFormat.format("serializedAlert {0}", serializedAlert));

			Alert alert;
			try {
				alert = _mapper.readValue(serializedAlert, Alert.class);
			} catch (Exception e) {
				String logMessage = MessageFormat.format("Failed to deserialize alert {0}. Full stack trace of exception {1}", serializedAlert, ExceptionUtils.getFullStackTrace(e));
				_logger.warn(logMessage);

				logAlertStatsOnFailure(DEFAULTALERTID, DEFAULTUSER);

				continue;
			}

			if(!_shouldEvaluateAlert(alert, alert.getId())) {

				logAlertStatsOnFailure(alert.getId(), alert.getOwner().getUserName());

				continue;
			}

			if(alertEnqueueTimestampsByAlertId.containsKey(alert.getId())) {
				String logMessage = MessageFormat.format("Found alert {0}:{1} with multiple timestamps. ExistingTime:{2} NewTime:{3}. Existing evaluation will be overwritten.",
						alert.getId(), alert.getName(), alertEnqueueTimestampsByAlertId.get(alert.getId()), alertWithTimestamp.getAlertEnqueueTime());
				_logger.warn(logMessage);

				// Treating this as a failure.
				logAlertStatsOnFailure(alert.getId(), alert.getOwner().getUserName());
			}

			alertEnqueueTimestampsByAlertId.put(alert.getId(), alertWithTimestamp.getAlertEnqueueTime());

			List<Notification> notifications = new ArrayList<>(alert.getNotifications());
			alert.setNotifications(null);

			if(notifications.size() == 0) {
				String logMessage = MessageFormat.format("Found alert {0}:{1} with no notification.", alert.getId(), alert.getName());
				_logger.warn(logMessage);

				// Treating this as a failure.
				logAlertStatsOnFailure(alert.getId(), alert.getOwner().getUserName());
				continue;
			}

			for(Notification n : notifications) {

				if(alertsByNotificationId.containsKey(n.getId())) {
					String logMessage = MessageFormat.format("Found alert {0}:{1} where notification {2} is present multiple times. ",
							alert.getId(), alert.getName(), n.getId());
					_logger.warn(logMessage);
				}

				alertsByNotificationId.put(n.getId(), alert);
			}
			allNotifications.addAll(notifications);
		}

		// Update the state of notification objects from the database since the notification contained 	
		// in the serialized alert might be stale. This is because the scheduler only refreshes the alerts	
		// after a specified REFRESH_INTERVAL. And within this interval, the notification state may have changed.	
		// For example, the notification may have been updated to be on cooldown by a previous alert evaluation.	
		// Or it's active/clear status may have changed. 
		updateNotificationsActiveStatusAndCooldown(allNotifications);
		for(Notification n : allNotifications) {
			alertsByNotificationId.get(n.getId()).addNotification(n);
		}

		Set<Alert> alerts = new HashSet<>(alertsByNotificationId.values());

		long jobStartTime, jobEndTime;
		Long alertEnqueueTimestamp;

		String logMessage;
		History history;

		_monitorService.modifyCounter(Counter.ALERTS_EVALUATED_TOTAL, alerts.size(), new HashMap<>());
		for (Alert alert : alerts) {

			jobStartTime = System.currentTimeMillis();
			alertEnqueueTimestamp = alertEnqueueTimestampsByAlertId.get(alert.getId());
			updateRequestContext(alert);

			updateAlertStartEvaluationStats(alertEnqueueTimestampsByAlertId, alert, jobStartTime);

			history = new History(History.addDateToMessage(JobStatus.STARTED.getDescription()), HOSTNAME, alert.getId(), JobStatus.STARTED);
			Set<Trigger> missingDataTriggers = new HashSet<Trigger>();

			for(Trigger trigger : alert.getTriggers()) {
				if(trigger.getType().equals(TriggerType.NO_DATA)) {
					missingDataTriggers.add(trigger);
				}
			}

			boolean alertSkipped = false;
			boolean alertFailure = false;
			boolean datalagMonitorEnabled = Boolean.valueOf(_configuration.getValue(SystemConfiguration.Property.DATA_LAG_MONITOR_ENABLED));
			boolean doesDatalagConditionSatisfy = datalagMonitorEnabled ? doesDatalagConditionSatisfy(alert, null) : false;
			try {
				alertEnqueueTimestamp = alertEnqueueTimestampsByAlertId.get(alert.getId());
				MetricQueryResult queryResult = _metricService.getMetrics(alert.getExpression(), alertEnqueueTimestamp);
				MonitoringUtils.updateAlertMetricQueryPerfCounters(_monitorService, queryResult, alert.getOwner().getUserName());
				List<Metric> metrics = queryResult.getMetricsList();
				int initialMetricSize = metrics.size();

				/* It works only for alerts with regex based expressions
				TODO: Fix for expressions that do not go through discovery service ( i.e, non regex based expressions )
				*/
//				if (initialMetricSize == 0 && alert.getModifiedDate() != null && ((System.currentTimeMillis() - alert.getModifiedDate().getTime()) / (24 * 60 * 60 * 1000)) > MetricSchemaRecord.DEFAULT_RETENTION_DISCOVERY_DAYS && // if Last Modified time was > DEFAULT_RETENTION_DISCOVERY_DAYS
//						(_whiteListedScopeRegexPatterns.isEmpty() || !AlertUtils.isScopePresentInWhiteList(alert.getExpression(), _whiteListedScopeRegexPatterns))) { // not disable whitelisted argus alerts.
				if(false) {
					_logger.info("Orphan Alert detected. Disabling it and notifying user. Alert Id: {}", alert.getId());
					Alert dbAlert = findAlertByPrimaryKey(alert.getId());
					dbAlert.setEnabled(false);
					_sendOrphanAlertNotification(alert);
				} else {
					if (datalagMonitorEnabled) {
						metrics.removeIf(m -> shouldMetricBeRemovedForDataLag(alert, m, historyList));

						if ((metrics.size() <= 0 && initialMetricSize > 0) || // Skip alert evaluation if all the expanded alert expression contains dc with data lag and initial size was non-zero.
								(initialMetricSize == 0 && doesDatalagConditionSatisfy)) { // or, if the initial size is 0 and data lag is present in atleast one dc.
							alertSkipped = true;
							continue;
						}
					}

					if (areDatapointsEmpty(metrics)) {
						if (alert.isMissingDataNotificationEnabled()) {
							_sendNotificationForMissingData(alert);
							logMessage = MessageFormat.format("Metric data does not exist for alert expression: {0}. Sent notification for missing data.",
									alert.getExpression());
							_logger.debug(logMessage);
							history.appendMessageNUpdateHistory(logMessage, null, 0);
						} else {
							logMessage = MessageFormat.format("Metric data does not exist for alert expression: {0}. Missing data notification was not enabled.",
									alert.getExpression());
							_logger.debug(logMessage);
							history.appendMessageNUpdateHistory(logMessage, null, 0);
						}

						if (missingDataTriggers.size() > 0) {
							for (Notification notification : alert.getNotifications()) {
								if (!notification.getTriggers().isEmpty()) {
									_processMissingDataNotification(alert, history, missingDataTriggers, notification, true, alertEnqueueTimestamp);
								}
							}
						}
					} else {
						// _logger.error("Alert: {}", alert.toString()); // DEBUG - REMOVE

						//Only evaluate those triggers which are associated with any notification.
						Set<Trigger> triggersToEvaluate = new HashSet<>();
						for (Notification notification : alert.getNotifications()) {
							triggersToEvaluate.addAll(notification.getTriggers());
						}

						Map<BigInteger, Map<Metric, Long>> triggerFiredTimesAndMetricsByTrigger = _evaluateTriggers(triggersToEvaluate,
								metrics, alert.getExpression(), alertEnqueueTimestamp);

						for (Notification notification : alert.getNotifications()) {
							if (notification.getTriggers().isEmpty()) {
							    _processTriggerlessNotification(alert, history, metrics, notification, alertEnqueueTimestamp);
							} else {
								_processNotification(alert, history, metrics, triggerFiredTimesAndMetricsByTrigger, notification, alertEnqueueTimestamp);
								if (missingDataTriggers.size() > 0) {
									// processing to possibly to clear missing data notification
									_processMissingDataNotification(alert, history, missingDataTriggers, notification, false, alertEnqueueTimestamp);
								}
							}
						}
					}
				}

				jobEndTime = System.currentTimeMillis();
				long evalLatency = jobEndTime - jobStartTime;
				history.appendMessageNUpdateHistory("Alert was evaluated successfully.", JobStatus.SUCCESS, evalLatency);

				Map<String, String> tagUser = new HashMap<>();
				tagUser.put(USERTAG, alert.getOwner().getUserName());
				_monitorService.modifyCounter(Counter.ALERTS_EVALUATION_LATENCY, evalLatency, tagUser);

			} catch (MissingDataException mde) {
				if (doesDatalagConditionSatisfy) {
					alertSkipped = true;
				}
				alertFailure = true;
				handleAlertEvaluationException(alert, jobStartTime, alertEnqueueTimestamp, history, missingDataTriggers, mde, true);
			} catch (Exception ex) {
				if (doesDatalagConditionSatisfy) {
					alertSkipped = true;
				}
				alertFailure = true;
				handleAlertEvaluationException(alert, jobStartTime, alertEnqueueTimestamp, history, missingDataTriggers, ex, false);
			} finally {

				history = _historyService.createHistory(alert, history.getMessage(), history.getJobStatus(), history.getExecutionTime());
				historyList.add(history);

				Map<String, String> tags = new HashMap<>();
				tags.put(HOSTTAG, HOSTNAME);
				tags.put(ALERTIDTAG, alert.getId().toString());
				publishAlertTrackingMetric(Counter.ALERTS_EVALUATED.getMetric(),
						alertSkipped ? -1.0 /*failure*/ : 1.0 /*success*/,
						tags);

				Map<String, String> tagUser = new HashMap<>();
				tagUser.put(USERTAG, alert.getOwner().getUserName());
				_monitorService.modifyCounter(alertSkipped ? Counter.ALERTS_SKIPPED : Counter.ALERTS_EVALUATED, 1, tagUser);

				if (alertFailure) {
					Map<String, String> tagUser2 = new HashMap<>();
					tagUser2.put(USERTAG, alert.getOwner().getUserName());
					_monitorService.modifyCounter(Counter.ALERTS_FAILED, 1, tagUser2);
				}
			}
		} // end for
		return historyList;
	}

	@VisibleForTesting
	protected void updateRequestContext(Alert alert) {
		RequestContextHolder.setRequestContext(new RequestContext(alert.getOwner().getUserName() + "-alert"));
	}

	private boolean doesDatalagConditionSatisfy(Alert alert, String currentDC) {
		return _monitorService.isDataLagging(currentDC) &&
				(_whiteListedScopeRegexPatterns.isEmpty() || !AlertUtils.isPatternPresentInWhiteList(alert.getExpression(), _whiteListedScopeRegexPatterns)) &&
				(_whiteListedUserRegexPatterns.isEmpty() || !AlertUtils.isPatternPresentInWhiteList(alert.getOwner().getUserName(), _whiteListedUserRegexPatterns));
	}

	private boolean shouldMetricBeRemovedForDataLag(Alert alert, Metric m, List<History> historyList) {
		try {
			String currentDC = _metricService.getDCFromScope(m.getScope());
			if (doesDatalagConditionSatisfy(alert, currentDC)) {
				String logMessage = MessageFormat.format("Skipping evaluation of the alert expression with scope: {0} in alert with id: {1} due metric data was lagging in DC: {2}", m.getScope(), alert.getId().intValue(), currentDC);
				_logger.info(logMessage);
				History history = new History(History.addDateToMessage(JobStatus.SKIPPED.getDescription()), HOSTNAME, alert.getId(), JobStatus.SKIPPED);
				history.appendMessageNUpdateHistory(logMessage, null, 0);
				history = _historyService.createHistory(alert, history.getMessage(), history.getJobStatus(), history.getExecutionTime());
				historyList.add(history);
				return true;
			}
			return false;
		} catch (Exception ex) {
			_logger.error("Error while identifying metric be removed from datalag: {}", ex);
			return false;
		}
	}

	private void updateAlertStartEvaluationStats(Map<BigInteger, Long> alertEnqueueTimestampsByAlertId, Alert alert, long jobStartTime) {
		Long alertEnqueueTimestamp = 0L;

		Map<String, String> tags = new HashMap<>();
		tags.put(USERTAG, alert.getOwner().getUserName());

		if(alertEnqueueTimestampsByAlertId.containsKey(alert.getId())) {

			alertEnqueueTimestamp = alertEnqueueTimestampsByAlertId.get(alert.getId());

			if(jobStartTime - alertEnqueueTimestamp > EVALUATIONDELAY) {
				_monitorService.modifyCounter(Counter.ALERTS_EVALUATION_DELAYED, 1, tags);
				_logger.warn("EVALUATION_DELAYED: Alert {}:{} enQueueTime {} evaluationTime {}",
						alert.getId(), alert.getName(), alertEnqueueTimestamp, jobStartTime);
			} else {
				_monitorService.modifyCounter(Counter.ALERTS_EVALUATION_STARTED, 1, tags);
			}
		}
	}

	private void logAlertStatsOnFailure(BigInteger alertid, String user) {
		Map<String, String> tags = new HashMap<>();
		tags.put(HOSTTAG, HOSTNAME);
		tags.put(ALERTIDTAG, alertid.toString());
		publishAlertTrackingMetric(Counter.ALERTS_EVALUATED.getMetric(), -1.0/*failure*/, tags);
		tags = new HashMap<>();
		tags.put(USERTAG, user);
		_monitorService.modifyCounter(Counter.ALERTS_FAILED, 1, tags);

		_monitorService.modifyCounter(Counter.ALERTS_EVALUATED, 1, tags);
	}

	private void handleAlertEvaluationException(Alert alert, long jobStartTime, Long alertEnqueueTimestamp, History history,
		Set<Trigger> missingDataTriggers, Exception ex, Boolean isDataMissing) {
		long jobEndTime;
		String logMessage;
		jobEndTime = System.currentTimeMillis();
		if(isDataMissing) {
			logMessage = MessageFormat.format("Failed to evaluate alert : {0} due to missing data exception. Full stack trace of exception - {1}",
					alert.getId().intValue(), ExceptionUtils.getFullStackTrace(ex));
		} else {
			logMessage = MessageFormat.format("Failed to evaluate alert : {0}. Full stack trace of exception - {1}",
					alert.getId().intValue(), ExceptionUtils.getFullStackTrace(ex));
		}
		_logger.warn(logMessage);

		try {
			if (Boolean.valueOf(_configuration.getValue(SystemConfiguration.Property.EMAIL_EXCEPTIONS))) {
				_sendEmailToAdmin(alert, alert.getId(), ex);
			}

			history.appendMessageNUpdateHistory(logMessage, JobStatus.FAILURE, jobEndTime - jobStartTime);

			if(logMessage.contains("net.opentsdb.tsd.BadRequestException") || isDataMissing) {

				if (alert.isMissingDataNotificationEnabled()) {
					_sendNotificationForMissingData(alert);
				}

				if (missingDataTriggers.size() > 0) {
					for (Notification notification : alert.getNotifications()) {
						if (!notification.getTriggers().isEmpty()) {
							_processMissingDataNotification(alert, history, missingDataTriggers, notification, true, alertEnqueueTimestamp);
						}
					}
				}
			}
		}
		catch (Exception e) {
			logMessage = MessageFormat.format("Unexpected exception evaluating alert : {0}. Full stack trace of exception - {1}", alert.getId().intValue(), ExceptionUtils.getFullStackTrace(e));
			_logger.warn(logMessage);
		}
	}

	private boolean areDatapointsEmpty(List<Metric> metrics) {
		if(metrics==null || metrics.size()==0) {
			return true;
		}else {
			for(Metric metric : metrics) {
				if(metric!=null && metric.getDatapoints()!=null && metric.getDatapoints().keySet().size()!=0) {
					return false;
				}
			}
		}
		return true;
	}

	/**
	 * Evaluates all triggers associated with the notification and updates the job history.
	 */
	private void _processNotification(Alert alert, History history, List<Metric> metrics, 
			Map<BigInteger, Map<Metric, Long>> triggerFiredTimesAndMetricsByTrigger, Notification notification, Long alertEnqueueTimestamp) {

		//refocus notifier does not need cool down logic, and every evaluation needs to send notification
        // Future - once refocus v1 notifiers are migrated to refocus_boolean notifiers, remove REFOCUS.
		boolean isBooleanRefocusNotifier = SupportedNotifier.REFOCUS.getName().equals(notification.getNotifierName()) ||
                                           SupportedNotifier.REFOCUS_BOOLEAN.getName().equals(notification.getNotifierName());
        boolean isValueRefocusNotifier   = SupportedNotifier.REFOCUS_VALUE.getName().equals(notification.getNotifierName());

        if (isValueRefocusNotifier)
        {
            // Future - For now just ignore RefocusValueNotifiers attached to Triggers.
            String logMessage = MessageFormat.format("RefocusValueNotifiers must not be associated with triggers. Name: {0}", notification.getName());
            _logger.info(logMessage);
            history.appendMessageNUpdateHistory(logMessage, null, 0);
            return;
        }

		for(Trigger trigger : notification.getTriggers()) {
			Map<Metric, Long> triggerFiredTimesForMetrics = triggerFiredTimesAndMetricsByTrigger.get(trigger.getId());

			for(Metric m : metrics) {
				if(triggerFiredTimesForMetrics!=null && triggerFiredTimesForMetrics.containsKey(m)) {
					String logMessage = MessageFormat.format("The trigger {0} was evaluated against metric {1} and it is fired.", trigger.getName(), m.getIdentifier());
					history.appendMessageNUpdateHistory(logMessage, null, 0);

					if (isBooleanRefocusNotifier) {
						sendNotification(trigger, m, history, notification, alert, triggerFiredTimesForMetrics.get(m), alertEnqueueTimestamp);
						continue;
					}

					if(!notification.onCooldown(trigger, m)) {
						_updateNotificationSetActiveStatus(trigger, m, history, notification);
						sendNotification(trigger, m, history, notification, alert, triggerFiredTimesForMetrics.get(m), alertEnqueueTimestamp);
					} else {
						logMessage = MessageFormat.format("The notification {0} is on cooldown until {1}.", notification.getName(), getDateMMDDYYYY(notification.getCooldownExpirationByTriggerAndMetric(trigger, m)));
						history.appendMessageNUpdateHistory(logMessage, null, 0);
					}
				} else {
					String logMessage = MessageFormat.format("The trigger {0} was evaluated against metric {1} and it is not fired.", trigger.getName(), m.getIdentifier());
					history.appendMessageNUpdateHistory(logMessage, null, 0);

					if (isBooleanRefocusNotifier) {
						sendClearNotification(trigger, m, history, notification, alert, alertEnqueueTimestamp);
						continue;
					}

					if(notification.isActiveForTriggerAndMetric(trigger, m)) {
						// This is case when the notification was active for the given trigger, metric combination
						// and the metric did not violate triggering condition on current evaluation. Hence we must clear it.
						_updateNotificationClearActiveStatus(trigger, m, notification);
						sendClearNotification(trigger, m, history, notification, alert, alertEnqueueTimestamp);
					}
				}
			}
		}
	}

    /**
     * Evaluates notifiers without triggers.  Only RefocusValueNotifiers can execute with out a trigger.
     * All other notifiers without triggers are logged.
     */
    private void _processTriggerlessNotification(Alert alert, History history, List<Metric> metrics, Notification notification, Long alertEnqueueTimestamp) {

        boolean isRefocusValueNotifier = SupportedNotifier.REFOCUS_VALUE.getName().equals(notification.getNotifierName());

        if (!isRefocusValueNotifier)
        {
            String logMessage = MessageFormat.format("The notification {0} has no triggers.", notification.getName());
            _logger.info(logMessage);
            history.appendMessageNUpdateHistory(logMessage, null, 0);
        }
        else
        {

            // Refocus Notifiers: every evaluation needs to send notification
            // Future - file work item for Refocus -> each metric (evaluated expression) will be directed to all of the S+A in the notifier.
            // future - Work item will request expansion of the S+A based on some part of the metric expression.
            // FOR NOW - Users should auther Alerts with RefocusValueNotifiers to have only a single expression.
            for (Metric m : metrics)
            {
                Long latestDataPoint = getLatestDatapointTime(m, alert.getExpression(), alertEnqueueTimestamp);

                if (latestDataPoint != null)
                {
                    sendNotification(null, m, history, notification, alert, latestDataPoint, alertEnqueueTimestamp);
                }
            }
        }
    }

	/**
	 * Evaluates all triggers associated with the missing data notification and updates the job history.
	 */
	private void _processMissingDataNotification(Alert alert, History history, Set<Trigger> triggers, Notification notification, boolean isDataMissing, Long alertEnqueueTimestamp) {

		//refocus notifier does not need cool down logic, and every evaluation needs to send notification
		boolean isRefocusNotifier = SupportedNotifier.REFOCUS.getName().equals(notification.getNotifierName()) ||
                                    SupportedNotifier.REFOCUS_BOOLEAN.getName().equals(notification.getNotifierName());
        boolean isValueRefocusNotifier   = SupportedNotifier.REFOCUS_VALUE.getName().equals(notification.getNotifierName());

        // IMPORTANT - Verify that missing data should result in no notification to Refocus for valueNotifier
        if (isValueRefocusNotifier)
        {
            // Future - For now just ignore RefocusValueNotifiers attached to NoData Scenarios.  Later we trigger, but require that the subscriptions for refocusValue have a value supplied too! S|A|Value
            String logMessage = MessageFormat.format("RefocusValueNotifiers must not be associated with no-data triggers. Name: {0}", notification.getName());
            _logger.info(logMessage);
            history.appendMessageNUpdateHistory(logMessage, null, 0);
            return;
        }

		for(Trigger trigger : notification.getTriggers()) {
			if(triggers.contains(trigger)) {
				Metric m = new Metric("unknown","unknown");
				if(isDataMissing) {
					String logMessage = MessageFormat.format("The trigger {0} was evaluated and it is fired as data for the metric expression {1} does not exist", trigger.getName(), alert.getExpression());
					history.appendMessageNUpdateHistory(logMessage, null, 0);

					if(isRefocusNotifier) {
						sendNotification(trigger, m, history, notification, alert, System.currentTimeMillis(), alertEnqueueTimestamp);
						continue;
					}

					if (!notification.onCooldown(trigger, m)) {
						_updateNotificationSetActiveStatus(trigger, m, history, notification);
						sendNotification(trigger, m, history, notification, alert, System.currentTimeMillis(), alertEnqueueTimestamp);
					} else {
						logMessage = MessageFormat.format("The notification {0} is on cooldown until {1}.", notification.getName(), getDateMMDDYYYY(notification.getCooldownExpirationByTriggerAndMetric(trigger, m)));
						history.appendMessageNUpdateHistory(logMessage, null, 0);
					}

				} else {  // Data is not missing
					String logMessage = MessageFormat.format("The trigger {0} was evaluated and it is not fired as data exists for the expression {1}", trigger.getName(), alert.getExpression());
					history.appendMessageNUpdateHistory(logMessage, null, 0);

					if(isRefocusNotifier) {
						sendClearNotification(trigger, m, history, notification, alert, alertEnqueueTimestamp);
						continue;
					}
					if (notification.isActiveForTriggerAndMetric(trigger, m)) {
						// This is case when the notification was active for the given trigger, metric combination
						// and the metric did not violate triggering condition on current evaluation. Hence we must clear it.
						_updateNotificationClearActiveStatus(trigger, m, notification);
						sendClearNotification(trigger, m, history, notification, alert, alertEnqueueTimestamp);
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
			_logger.warn(MessageFormat.format("Alert {0} has been disabled. Will not evaluate.", alert.getId().intValue()));
			return false;
		}

		return true;
	}


	/**
	 * Evaluates all triggers for the given set of metrics and returns a map of triggerIds to a map containing the triggered metric
	 * and the trigger fired time. 
	 */
	private Map<BigInteger, Map<Metric, Long>> _evaluateTriggers(Set<Trigger> triggers, List<Metric> metrics, String queryExpression, Long alertEnqueueTimestamp) {
		Map<BigInteger, Map<Metric, Long>> triggerFiredTimesAndMetricsByTrigger = new HashMap<>();

		for(Trigger trigger : triggers) {
			Map<Metric, Long> triggerFiredTimesForMetrics = new HashMap<>(metrics.size());
			for(Metric metric : metrics) {
				Long triggerFiredTime = getTriggerFiredDatapointTime(trigger, metric, queryExpression, alertEnqueueTimestamp);

				if (triggerFiredTime != null) {
					triggerFiredTimesForMetrics.put(metric, triggerFiredTime);
					Map<String, String> tags = new HashMap<>();
					tags.put(USERTAG, trigger.getAlert().getOwner().getUserName());
					_monitorService.modifyCounter(Counter.TRIGGERS_VIOLATED, 1, tags);
				}
			}
			triggerFiredTimesAndMetricsByTrigger.put(trigger.getId(), triggerFiredTimesForMetrics);
		}
		return triggerFiredTimesAndMetricsByTrigger;
	}


	public void sendNotification(Trigger trigger, Metric metric, History history, Notification notification, Alert alert,
			Long triggerFiredTime, Long alertEnqueueTime) {

	    /* NOTE - For triggerless Notifications (i.e. the RefocusValueNotifier), trigger is null, and the
	       passed in triggerFiredTime is the most recent value in the metric. */
		double triggerValue = 0.0;
		if(trigger == null || !trigger.getType().equals(TriggerType.NO_DATA)){
			triggerValue = metric.getDatapoints().get(triggerFiredTime);
		}
		NotificationContext context = new NotificationContext(alert, trigger, notification, triggerFiredTime, triggerValue, metric, history);
		context.setAlertEnqueueTimestamp(alertEnqueueTime);
		Notifier notifier = getNotifier(SupportedNotifier.fromClassName(notification.getNotifierName()));

        Map<String, String> tags = new HashMap<>();
		tags.put(USERTAG, alert.getOwner().getUserName());
		tags.put(ALERTIDTAG, (trigger != null) ? trigger.getAlert().getId().toString() : alert.getId().toString());
		tags.put(ACTIONTAG, ACTION_TRIGGERED);
		tags.put(RETRIESTAG, Integer.toString(context.getNotificationRetries()));
		tags.put(NOTIFYTARGETTAG, SupportedNotifier.fromClassName(notification.getNotifierName()).name());
		String logMessage = "";
		
		boolean rc = true;
		try {
			rc = notifier.sendNotification(context);
		} catch (Exception e) {
			_logger.error("sendNotification() hit exception", e);
			rc = false;
		}

		// TODO - log alertId, triggerId, notificationId?
		if (rc) {
			tags.put(STATUSTAG, STATUS_SUCCESS);
			if (trigger != null)
            {
                logMessage = MessageFormat.format("Sent alert notification and updated the cooldown: {0}",
                        getDateMMDDYYYY(notification.getCooldownExpirationByTriggerAndMetric(trigger, metric)));
            }
            else
            {
                logMessage = MessageFormat.format("Sent notification to {0}",
                        SupportedNotifier.fromClassName(notification.getNotifierName()).name());
            }
		} else {
			tags.put(STATUSTAG, STATUS_FAILURE);
			logMessage = MessageFormat.format("Failed to send notification to {0}",
                    SupportedNotifier.fromClassName(notification.getNotifierName()).name());
		}

		_monitorService.modifyCounter(Counter.NOTIFICATIONS_SENT, 1, tags);

		tags = new HashMap<>();
		tags.put(HOSTTAG, HOSTNAME);
		tags.put(STATUSTAG, rc ? STATUS_SUCCESS: STATUS_FAILURE);
		tags.put(USERTAG, alert.getOwner().getUserName());
		tags.put(ACTIONTAG, ACTION_TRIGGERED);
		tags.put(NOTIFYTARGETTAG, SupportedNotifier.fromClassName(notification.getNotifierName()).name());
		// TODO - QUESTION - can trigger.getAlert().getId() differ from alert.getId()?
		tags.put(ALERTIDTAG, (trigger != null) ? trigger.getAlert().getId().toString() : alert.getId().toString());
		publishAlertTrackingMetric(Counter.NOTIFICATIONS_SENT.getMetric(), 1.0/*notification sent*/, tags);

		_logger.debug(logMessage);
		history.appendMessageNUpdateHistory(logMessage, null, 0);
	}

	public void sendClearNotification(Trigger trigger, Metric metric, History history, Notification notification, Alert alert, Long alertEnqueueTime) {
		NotificationContext context = new NotificationContext(alert, trigger, notification, System.currentTimeMillis(), 0.0, metric, history);
		context.setAlertEnqueueTimestamp(alertEnqueueTime);
		Notifier notifier = getNotifier(SupportedNotifier.fromClassName(notification.getNotifierName()));

		String logMessage ="";
		Map<String, String> tags = new HashMap<>();
		tags.put(USERTAG, alert.getOwner().getUserName());
		tags.put(ALERTIDTAG, trigger.getAlert().getId().toString());
		tags.put(ACTIONTAG, ACTION_CLEARED);
		tags.put(NOTIFYTARGETTAG, SupportedNotifier.fromClassName(notification.getNotifierName()).name());

		boolean rc = true;
		try {
			rc = notifier.clearNotification(context);
		} catch (Exception e) {
			_logger.error("clearNotification() hit exception", e);
			rc = false;
		}
		if (rc) {
			tags.put(STATUSTAG, STATUS_SUCCESS);
			logMessage = MessageFormat.format("The notification {0} was cleared.", notification.getName());
		} else {
			tags.put(STATUSTAG, STATUS_FAILURE);
			logMessage = MessageFormat.format("Failed to send clear notifiction to {0}", SupportedNotifier.fromClassName(notification.getNotifierName()).name());
		}

		_monitorService.modifyCounter(Counter.NOTIFICATIONS_SENT, 1, tags);
		
		tags = new HashMap<>();
		tags.put(HOSTTAG, HOSTNAME);
		tags.put(NOTIFYTARGETTAG, SupportedNotifier.fromClassName(notification.getNotifierName()).name());
		tags.put(ALERTIDTAG, trigger.getAlert().getId().toString());
		tags.put(STATUSTAG, rc ? STATUS_SUCCESS: STATUS_FAILURE);
		tags.put(ACTIONTAG, ACTION_CLEARED);
		tags.put(USERTAG, alert.getOwner().getUserName());
		publishAlertTrackingMetric(Counter.NOTIFICATIONS_SENT.getMetric(), 1.0, tags);

		_logger.info(logMessage);
		history.appendMessageNUpdateHistory(logMessage, null, 0);
	}

	private void publishAlertTrackingMetric(String metric, double value, Map<String, String> tags) {
		publishAlertTrackingMetric(ALERTSCOPE, metric, value, tags);
	}
	
	private void publishAlertTrackingMetric(String scope, String metric, double value, Map<String, String> tags) {
		Map<Long, Double> datapoints = new HashMap<>();
		datapoints.put(System.currentTimeMillis(), value);
		Metric trackingMetric = new Metric(scope, metric);
		trackingMetric.addDatapoints(datapoints);
		if(tags!=null) {
			trackingMetric.setTags(tags);
		}

		// this.exportMetric(trackingMetric, value);
		try {
			_tsdbService.putMetrics(Arrays.asList(new Metric[] {trackingMetric}));
		} catch (Exception ex) {
			_logger.error("Exception occurred while adding alerts evaluated metric to tsdb - {}", ex.getMessage());
		}
	}

	private void _updateNotificationSetActiveStatus(Trigger trigger, Metric metric, History history, Notification notification) {
		notification.setCooldownExpirationByTriggerAndMetric(trigger, metric, System.currentTimeMillis() + notification.getCooldownPeriod());
		notification.setActiveForTriggerAndMetric(trigger, metric, true);
		notification = mergeEntity(_emProvider.get(), notification);
	}

	private void _updateNotificationClearActiveStatus(Trigger trigger, Metric metric, Notification notification) {
		notification.setCooldownExpirationByTriggerAndMetric(trigger, metric, System.currentTimeMillis());
		notification.setActiveForTriggerAndMetric(trigger, metric, false);
		notification = mergeEntity(_emProvider.get(), notification);
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
			message.append(MessageFormat.format("<br> Exception message: The alert with id {0} does not exist.", alertId.intValue()));
		}
		message.append(MessageFormat.format("<br> Time stamp: {0}", History.DATE_FORMATTER.get().format(new Date(System.currentTimeMillis()))));
		_mailService.sendMessage(to, subject, message.toString(), "text/html; charset=utf-8", MailService.Priority.HIGH);
		if (alert != null && alert.getOwner() != null && alert.getOwner().getEmail() != null && !alert.getOwner().getEmail().isEmpty()) {
			to.clear();
			to.add(alert.getOwner().getEmail());
			_mailService.sendMessage(to, subject, message.toString(), "text/html; charset=utf-8", MailService.Priority.HIGH);
		}
	}

	private void _sendOrphanAlertNotification(Alert alert) {
		if(alert != null) {
			String subject = MessageFormat.format("Argus alert {0} is mark disabled", alert.getId().intValue());
			StringBuilder message = new StringBuilder();
			message.append("<p>This is an alert disabling notification</p>");
			message.append(MessageFormat.format("Alert Id: {0}", alert.getId().intValue()));
			message.append(MessageFormat.format("<br> Alert name: {0}" , alert.getName()));
			message.append(MessageFormat.format("<br> No data found for the following metric expression: {0} for last {1} days.", alert.getExpression(), MetricSchemaRecord.DEFAULT_RETENTION_DISCOVERY_DAYS));
			message.append("<br> If you wish to re enable it, please modify the alert expression and then enable the alert.");
			if (alert.getOwner() != null && alert.getOwner().getEmail() != null && !alert.getOwner().getEmail().isEmpty()) {
				Set<String> to = new HashSet<>();
				to.add(alert.getOwner().getEmail());
				_mailService.sendMessage(to, subject, message.toString(), "text/html; charset=utf-8", MailService.Priority.NORMAL);
			}
		}
	}

	private void _sendNotificationForMissingData(Alert alert) {
		Set<String> to = new HashSet<>();
		to.add(alert.getOwner().getEmail());

		String subject = "Alert Missing Data Notification.";
		StringBuilder message = new StringBuilder();

		message.append("<p>This is a missing data notification. </p>");
		message.append(MessageFormat.format("Alert Id: {0}", alert.getId().intValue()));
		message.append(MessageFormat.format("<br> Alert name: {0}" , alert.getName()));
		message.append(MessageFormat.format("<br> No data found for the following metric expression: {0}", alert.getExpression()));
		message.append(MessageFormat.format("<br> Time stamp: {0}", History.DATE_FORMATTER.get().format(new Date(System.currentTimeMillis()))));
		boolean rc = _mailService.sendMessage(to, subject, message.toString(), "text/html; charset=utf-8", MailService.Priority.HIGH);

		Map<String, String> tags = new HashMap<>();
		tags.put(ALERTIDTAG, alert.getId().toString());
		tags.put(USERTAG, alert.getOwner().getUserName());
		tags.put(ACTIONTAG, ACTION_MISSINGDATA);
		tags.put(STATUSTAG, rc ? STATUS_SUCCESS: STATUS_FAILURE);
		tags.put(NOTIFYTARGETTAG, SupportedNotifier.EMAIL.name());
		_monitorService.modifyCounter(Counter.NOTIFICATIONS_SENT, 1, tags);
		publishAlertTrackingMetric(Counter.NOTIFICATIONS_SENT.getMetric(), 1.0, tags);
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

		List<AlertWithTimestamp> alertsWithTimestamp = new ArrayList<>(alerts.size());
		for (Alert alert : alerts) {
			AlertWithTimestamp obj;
			try {
				String serializedAlert = _mapper.writeValueAsString(alert);
				
				_logger.debug(MessageFormat.format("serializedAlert {0}", serializedAlert));

				obj = new AlertWithTimestamp(serializedAlert, System.currentTimeMillis());
			} catch (JsonProcessingException e) {
				_logger.warn("Failed to serialize alert: {}.", alert.getId().intValue());
				_logger.warn("", e);
				continue;
			}

			alertsWithTimestamp.add(obj);
		}

		try {
			_mqService.enqueue(ALERT.getQueueName(), alertsWithTimestamp);
		} catch (Exception ex) {
			_logger.error("Error occurred while enqueueing alerts to mq service. Reason {}", ex.getMessage());
		}


		List<Metric> metricsAlertScheduled = new ArrayList<Metric>();

		_monitorService.modifyCounter(Counter.ALERTS_SCHEDULED_TOTAL, alerts.size(), new HashMap<>());
		// Write alerts scheduled for evaluation as time series to TSDB
		for (Alert alert : alerts) {
			Map<Long, Double> datapoints = new HashMap<>();
			// convert timestamp to nearest minute since cron is Least scale resolution of minute
			datapoints.put(1000 * 60 * (System.currentTimeMillis()/(1000 *60)), 1.0);
			Metric metric = new Metric("argus.alerts", "scheduled");
			metric.setTag(HOSTTAG, HOSTNAME);
			metric.setTag(ALERTIDTAG, alert.getId().toString());
			metric.setTag(USERTAG, alert.getOwner().getUserName());
			metric.addDatapoints(datapoints);
			metricsAlertScheduled.add(metric);
			Map<String, String> tags = new HashMap<>();
			tags.put(USERTAG, alert.getOwner().getUserName());
			tags.put(ALERTIDTAG, alert.getId().toString());
			_monitorService.modifyCounter(Counter.ALERTS_SCHEDULED, 1, tags);
		}

		try {
			_tsdbService.putMetrics(metricsAlertScheduled);
		} catch (Exception ex) {
			_logger.error("Error occurred while pushing alert audit scheduling time series. Reason: {}", ex.getMessage());
		}		
	}


	@Override
	public List<Alert> findAllAlerts(boolean metadataOnly) {
		requireNotDisposed();

		return metadataOnly ? Alert.findAllMeta(_emProvider.get()) : Alert.findAll(_emProvider.get());
	}

	@Override
	public List<Alert> findAlertsByStatus(boolean enabled) {
		requireNotDisposed();
		return Alert.findByStatus(_emProvider.get(), enabled);
	}

	@Override
	public List<BigInteger> findAlertIdsByStatus(boolean enabled) {
		requireNotDisposed();
		return Alert.findIDsByStatus(_emProvider.get(), enabled);
	}

	@Override
	public List<Alert> findAlertsByRangeAndStatus(BigInteger fromId, BigInteger toId, boolean enabled) {
		requireNotDisposed();
		return Alert.findByRangeAndStatus(_emProvider.get(), fromId, toId, enabled);
	}

	@Override
	public List<Alert> findAlertsModifiedAfterDate(Date modifiedDate) {
		requireNotDisposed();
		return Alert.findAlertsModifiedAfterDate(_emProvider.get(), modifiedDate);
	}

	@Override
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
	public List<Alert> findSharedAlerts(boolean metadataOnly, PrincipalUser owner, Integer limit) {
		requireNotDisposed();
		return metadataOnly ? Alert.findSharedAlertsMeta(_emProvider.get(), owner, limit) : Alert.findSharedAlerts(_emProvider.get(), owner, limit);
	}

	@Override
	public List<Alert> findSharedAlertsPaged(Integer limit, Integer offset, String searchText, String sortField, String sortOrder) {
		requireNotDisposed();
		return Alert.findSharedAlertsMetaPaged(_emProvider.get(), limit, offset, searchText, sortField, sortOrder);
	}

	@Override
	public List<Alert> findPrivateAlertsForPrivilegedUserPaged(PrincipalUser owner, Integer limit, Integer offset, String searchText,
		String sortField, String sortOrder) {
		requireNotDisposed();

		// Invalid user nor non-privileged user shall not view other's non-shared alerts, thus immediately return empty list
		if (owner == null || !owner.isPrivileged()) {
			return new ArrayList<>(0);
		}

		return Alert.findPrivateAlertsForPrivilegedUserMetaPaged(_emProvider.get(), owner, limit, offset, searchText, sortField, sortOrder);
	}

	@Override
	public int countAlerts(AlertsCountContext context) {
		requireNotDisposed();

		if (context == null) {
			return 0;
		}

		// Count total number of shared alerts for the shared alerts tab
		if (context.isCountSharedAlerts()) {
			return Alert.countSharedAlerts(_emProvider.get(), context.getSearchText());
		}

		PrincipalUser owner = context.getPrincipalUser();

		// Count total number of private alerts (non-shared alerts) if user is
		// privileged user, otherwise return 0
		if (context.isCountPrivateAlerts()) {
			// Invalid user nor non-privileged user shall not view other's
			// non-shared alerts, thus immediately return 0
			if (owner == null || !owner.isPrivileged()) {
				return 0;
			}

			return Alert.countPrivateAlertsForPrivilegedUser(_emProvider.get(), owner, context.getSearchText());
		}

		// Count total number of user alerts
		if (owner != null) {
			return Alert.countByOwner(_emProvider.get(), owner, context.getSearchText());
		}

		return 0;
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
		case CALLBACK:
			return _notifierFactory.getCallbackNotifier();
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
		case REFOCUS:
			return _notifierFactory.getRefocusNotifier();
		case REFOCUS_BOOLEAN:
			return _notifierFactory.getRefocusBooleanNotifier();
		case REFOCUS_VALUE:
			return _notifierFactory.getRefocusValueNotifier();
		default:
			return _notifierFactory.getDBNotifier();
		}
	}

	@Override
	public void dispose() {
		super.dispose();
		_metricService.dispose();
		_notificationsCache.dispose();
	}

	/**
	 * Evaluates the trigger against metric data.
	 *
	 * @param   trigger  Trigger to be evaluated.
	 * @param   metric   Metric data for the alert which the trigger belongs.
	 *
	 * @return  The time stamp of the last data point in metric at which the trigger was decided to be fired.
	 */
	public Long getTriggerFiredDatapointTime(Trigger trigger, Metric metric, String queryExpression, Long alertEnqueueTimestamp) {
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
		// else NOTE - metric.getDataPoints().size() > 1

		Collections.sort(sortedDatapoints, new Comparator<Map.Entry<Long, Double>>() {

			@Override
			public int compare(Entry<Long, Double> e1, Entry<Long, Double> e2) {
				return e1.getKey().compareTo(e2.getKey());
			}
		});

		int endIndex = sortedDatapoints.size();

		if(trigger.getType().equals(TriggerType.NO_DATA)) {
			if(trigger.getInertia()>0) {
				Long[] queryTimes = AlertUtils.getStartAndEndTimes(queryExpression, alertEnqueueTimestamp);
				if(((sortedDatapoints.get(0).getKey()-queryTimes[0]) > trigger.getInertia())){ 
					return sortedDatapoints.get(0).getKey();
				}

				if((queryTimes[1] - sortedDatapoints.get(sortedDatapoints.size()-1).getKey()) > trigger.getInertia()) {
					return sortedDatapoints.get(sortedDatapoints.size()-1).getKey();
				}

				if(sortedDatapoints.size()>1) {
					for(int i=1; i<sortedDatapoints.size(); i++) {
						if((sortedDatapoints.get(i).getKey()-sortedDatapoints.get(i-1).getKey()) > trigger.getInertia()) {
							return sortedDatapoints.get(i-1).getKey();
						}
					}
				}
			}
		}else {
			for(int startIndex=sortedDatapoints.size()-1; startIndex>=0; startIndex--){
				if(Trigger.evaluateTrigger(trigger, sortedDatapoints.get(startIndex).getValue())){
					Long interval = sortedDatapoints.get(endIndex-1).getKey() - sortedDatapoints.get(startIndex).getKey();
					if(interval >= trigger.getInertia())
						return sortedDatapoints.get(endIndex-1).getKey();
				}else{
					endIndex=startIndex;
				}
			}
		}
		return null;
	}


    /**
     * Evaluates the trigger against metric data.
     *
     * @param   metric   Metric data for the alert which the trigger belongs.
     *
     * @return  The time stamp of the last data point in metric at which the trigger was decided to be fired.
     */
    public Long getLatestDatapointTime(Metric metric, String queryExpression, Long alertEnqueueTimestamp) {

        if (metric.getDatapoints().isEmpty()) {
            return null;
        }

        Long latestTime = Collections.max(metric.getDatapoints().keySet());
        return latestTime;
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

	private String getDateMMDDYYYY(long dateInSeconds) {
		String result;

		try {
			result = History.DATE_FORMATTER.get().format(new Date(dateInSeconds));
		} catch (Exception ex) {
			result = String.valueOf(dateInSeconds);
		}
		return result;
	}


	//~ Inner Classes ********************************************************************************************************************************

	/**
	 * Used to enqueue alerts to evaluate.  The timestamp is used to reconcile lag between enqueue time 
	 * and evaluation time by adjusting relative times in the alert metric expression being evaluated.
	 *
	 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
	 */
	public static class AlertWithTimestamp implements Serializable {

		/** The serial version UID. */
		private static final long serialVersionUID = 1L;
		protected String serializedAlert;
		protected long alertEnqueueTime;

		/** Creates a new AlertIdWithTimestamp object. */
		public AlertWithTimestamp() { }

		/**
		 * Creates a new AlertIdWithTimestamp object.
		 *
		 * @param serializedAlert The serialized alert
		 * @param  timestamp  The epoch timestamp the alert was enqueued for evaluation.
		 */
		public AlertWithTimestamp(String serializedAlert, long timestamp) {
			this.serializedAlert = serializedAlert;
			this.alertEnqueueTime = timestamp;
		}

		public String getSerializedAlert() {
			return serializedAlert;
		}

		public void setSerializedAlert(String serializedAlert) {
			this.serializedAlert = serializedAlert;
		}

		public long getAlertEnqueueTime() {
			return alertEnqueueTime;
		}

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
		private long alertEnqueueTimestamp;
		private History history;
		private int notificationRetries = 0;

		/**
		 * Creates a new Notification Context object.
		 *
		 * @param  alert              The id of the alert for which the trigger is fired.
		 * @param  trigger            Name of the trigger fired.
		 * @param  notification       coolDownExpiration The cool down period of the notification.
		 * @param  triggerFiredTime   The time stamp of the last data point in metric at which the trigger was decided to be fired.
		 * @param  triggerEventValue  The value of the metric at the event trigger time.
		 * @param  triggeredMetric    The corresponding metric
		 * @param history             History object
		 */
		public NotificationContext(Alert alert, Trigger trigger, Notification notification, long triggerFiredTime, double triggerEventValue, Metric triggeredMetric, History history) {
			this.alert = alert;
			this.trigger = trigger;
			this.coolDownExpiration = notification.getCooldownExpirationByTriggerAndMetric(trigger, triggeredMetric);
			this.notification = notification;
			this.triggerFiredTime = triggerFiredTime;
			this.triggerEventValue = triggerEventValue;
			this.triggeredMetric = triggeredMetric;
			this.alertEnqueueTimestamp = 0L;
			this.history = history;
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


		public History getHistory() {
			return history;
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

		public long getAlertEnqueueTimestamp() { return alertEnqueueTimestamp; }

		public void setAlertEnqueueTimestamp(Long alertEnqueueTimestamp) { this.alertEnqueueTimestamp = alertEnqueueTimestamp; }

		public void setNotificationRetries(int notificationRetries) { this.notificationRetries = notificationRetries; }

		/**
		 *
		 * @return number of retries to send the notification.
		 */
		public int getNotificationRetries() { return notificationRetries; }
	}


	@Override
	public void exportMetric(Metric metric, Double value) {
		this._monitorService.exportMetric(metric, value);
	}

	@Override
	public void updateCounter(Counter counter, Double value) {
		this._monitorService.updateCounter(counter, value, null);
	}
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.common.annotations.VisibleForTesting;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.entity.History.JobStatus;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.MetricSchemaRecord;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.entity.Trigger;
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
import com.salesforce.dva.argus.service.alert.retriever.ImageDataRetrievalContext;
import com.salesforce.dva.argus.service.alert.retriever.ImageDataRetriever;
import com.salesforce.dva.argus.service.jpa.DefaultJPAService;
import com.salesforce.dva.argus.service.mail.EmailContext;
import com.salesforce.dva.argus.service.metric.MetricQueryResult;
import com.salesforce.dva.argus.service.metric.transform.MissingDataException;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.service.alert.testing.AlertTestResults;
import com.salesforce.dva.argus.util.AlertUtils;
import com.salesforce.dva.argus.util.MonitoringUtils;
import com.salesforce.dva.argus.util.RequestContext;
import com.salesforce.dva.argus.util.RequestContextHolder;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import java.io.Serializable;
import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static com.salesforce.dva.argus.service.MQService.MQQueue.ALERT;
import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;
import static java.math.BigInteger.ZERO;

/**
 * Default implementation of the alert service.
 *
 * @author  Tom Valine (tvaline@salesforce.com), Raj sarkapally (rsarkapally@salesforce.com), Dongpu Jin (djin@salesforce.com), Ian Keck (ikeck@salesforce.com)
 */
public class DefaultAlertService extends DefaultJPAService implements AlertService
{

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
	private static final String ACTION_NOTIFIED = "notified";
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
	private final ImageDataRetriever _imageDataRetriever;
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

	static
	{
		// Can fail if DNS is broken.
		// ToDo Handle the failure.
		HOSTNAME = SystemConfiguration.getHostname();
	}

	/**
	 * Creates a new DefaultAlertService object.
	 *
	 * @param configuration   The system configuration instance to use. Cannot be null.
	 * @param mqService       The MQ service instance to use. Cannot be null.
	 * @param metricService   The Metric service instance to use. Cannot be null.
	 * @param auditService    The audit service instance to use. Cannot be null.
	 * @param tsdbService     The TSDB service instance to use.
	 * @param mailService     The mail service instance to use. Cannot be null.
	 * @param historyService  The job history service instance to use. Cannot be null.
	 * @param monitorService  The monitor service instance to use. Cannot be null.
	 * @param notifierFactory The notifier factory to use
	 * @param emProvider      The entity manager provider to use
	 */
	@Inject
	public DefaultAlertService(SystemConfiguration configuration, MQService mqService, MetricService metricService,
							   AuditService auditService, TSDBService tsdbService, MailService mailService, HistoryService historyService,
							   MonitorService monitorService, ImageDataRetriever imageDataRetriever, NotifierFactory notifierFactory, Provider<EntityManager> emProvider)
	{
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
		_imageDataRetriever = imageDataRetriever;
		_notifierFactory = notifierFactory;
		_emProvider = emProvider;

		_initializeObjectMapper();
	}

	//~ Methods **************************************************************************************************************************************

	private void _initializeObjectMapper()
	{

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
	public Alert updateAlert(Alert alert) throws RuntimeException
	{
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
	public void deleteAlert(String name, PrincipalUser owner)
	{
		requireNotDisposed();
		requireArgument(name != null && !name.isEmpty(), "Name cannot be null or empty.");
		requireArgument(owner != null, "Owner cannot be null.");

		Alert alert = findAlertByNameAndOwner(name, owner);

		deleteAlert(alert);
	}

	@Override
	@Transactional
	public void deleteAlert(Alert alert)
	{
		requireNotDisposed();
		requireArgument(alert != null, "Alert cannot be null.");
		_logger.debug("Deleting an alert {}.", alert);

		EntityManager em = _emProvider.get();

		deleteEntity(em, alert);
		em.flush();
	}

	@Override
	@Transactional
	public void markAlertForDeletion(String name, PrincipalUser owner)
	{
		requireNotDisposed();
		requireArgument(name != null && !name.isEmpty(), "Name cannot be null or empty.");
		requireArgument(owner != null, "Owner cannot be null.");

		Alert alert = findAlertByNameAndOwner(name, owner);

		markAlertForDeletion(alert);
	}

	@Override
	@Transactional
	public void markAlertForDeletion(Alert alert)
	{
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
	public List<Alert> findAlertsMarkedForDeletion()
	{
		requireNotDisposed();
		return findEntitiesMarkedForDeletion(_emProvider.get(), Alert.class, -1);
	}

	@Override
	public List<Alert> findAlertsMarkedForDeletion(final int limit)
	{
		requireNotDisposed();
		requireArgument(limit > 0, "Limit must be greater than 0.");
		return findEntitiesMarkedForDeletion(_emProvider.get(), Alert.class, limit);
	}

	@Override
	public List<Alert> findAlertsByOwner(PrincipalUser owner, boolean metadataOnly)
	{
		requireNotDisposed();
		requireArgument(owner != null, "Owner cannot be null.");

		return metadataOnly ? Alert.findByOwnerMeta(_emProvider.get(), owner) : Alert.findByOwner(_emProvider.get(), owner);
	}

	@Override
	public List<Alert> findAlertsByOwnerPaged(PrincipalUser owner, Integer limit, Integer offset, String searchText, String sortField, String sortOrder)
	{
		requireNotDisposed();
		requireArgument(owner != null, "Owner cannot be null.");

		return Alert.findByOwnerMetaPaged(_emProvider.get(), owner, limit, offset, searchText, sortField, sortOrder);
	}

	@Override
	public Alert findAlertByPrimaryKey(BigInteger id)
	{
		requireNotDisposed();
		requireArgument(id != null && id.compareTo(ZERO) > 0, "ID must be a positive non-zero value.");

		EntityManager em = _emProvider.get();

		em.getEntityManagerFactory().getCache().evictAll();

		Alert result = Alert.findByPrimaryKey(em, id, Alert.class);

		_logger.debug("Query for alert having id {} resulted in : {}", id, result);
		return result;
	}

	@Override
	public List<Alert> findAlertsByPrimaryKeys(List<BigInteger> ids)
	{
		requireNotDisposed();
		requireArgument(ids != null && !ids.isEmpty(), "IDs list cannot be null or empty.");

		EntityManager em = _emProvider.get();

		em.getEntityManagerFactory().getCache().evictAll();

		List<Alert> result = Alert.findByPrimaryKeys(em, ids, Alert.class);

		_logger.debug("Query for alerts having ids {} resulted in : {}", ids, result);
		return result;
	}


	// ******************************************************************************
	// Alert Evaluation
	// ******************************************************************************

	@Override
	public void updateNotificationsActiveStatusAndCooldown(List<Notification> notifications)
	{
		List<BigInteger> ids = notifications.stream().map(x -> x.getId()).collect(Collectors.toList());
		_logger.debug("Updating notifications: {}", ids);
		if (_notificationsCache == null)
		{
			synchronized (DefaultAlertService.class)
			{
				if (_notificationsCache == null)
				{
					_notificationsCache = new NotificationsCache(_emProvider);
				}
			}
		}

		// if cache is refreshed, we read the cooldown and trigger info from cache, else we query the db directly
		if (_notificationsCache.isNotificationsCacheRefreshed())
		{
			for (Notification notification : notifications)
			{
				if (_notificationsCache.getNotificationActiveStatusMap().get(notification.getId()) != null)
				{
					notification.setActiveStatusMap(_notificationsCache.getNotificationActiveStatusMap().get(notification.getId()));
				} else
				{
					notification.getActiveStatusMap().clear();
				}
				if (_notificationsCache.getNotificationCooldownExpirationMap().get(notification.getId()) != null)
				{
					notification.setCooldownExpirationMap(_notificationsCache.getNotificationCooldownExpirationMap().get(notification.getId()));
				} else
				{
					notification.getCooldownExpirationMap().clear();
				}
			}
		} else
		{
			Notification.updateActiveStatusAndCooldown(_emProvider.get(), notifications);
		}
	}

	private void loadWhiteListRegexPatterns()
	{
		if (_whiteListedScopeRegexPatterns == null)
		{
			String whiteListedScopesProperty = _configuration.getValue(SystemConfiguration.Property.DATA_LAG_WHITE_LISTED_SCOPES);
			if (!StringUtils.isEmpty(whiteListedScopesProperty))
			{
				_whiteListedScopeRegexPatterns = Stream.of(whiteListedScopesProperty.split(",")).map(elem -> Pattern.compile(elem.toLowerCase())).collect(Collectors.toList());
			} else
			{
				_whiteListedScopeRegexPatterns = new ArrayList<Pattern>();
			}
		}

		if (_whiteListedUserRegexPatterns == null)
		{
			String whiteListedUsersProperty = _configuration.getValue(SystemConfiguration.Property.DATA_LAG_WHITE_LISTED_USERS);
			if (!StringUtils.isEmpty(whiteListedUsersProperty))
			{
				_whiteListedUserRegexPatterns = Stream.of(whiteListedUsersProperty.split(",")).map(elem -> Pattern.compile(elem.toLowerCase())).collect(Collectors.toList());
			} else
			{
				_whiteListedUserRegexPatterns = new ArrayList<Pattern>();
			}
		}
	}


	@Override
	@Transactional
	public Integer executeScheduledAlerts(int alertCount, int timeout) {
		requireNotDisposed();
		requireArgument(alertCount > 0, "Alert count must be greater than zero.");
		requireArgument(timeout > 0, "Timeout in milliseconds must be greater than zero.");

		List<History> historyList = new ArrayList<>();
		List<AlertWithTimestamp> alertsWithTimestamp = _mqService.dequeue(ALERT.getQueueName(), AlertWithTimestamp.class, timeout,
				alertCount);

		List<Notification> allNotifications = new ArrayList<>();
		Map<BigInteger, Alert> alertsByNotificationId = new HashMap<>();
		Map<BigInteger, Long> alertEnqueueTimestampsByAlertId = new HashMap<>();

		loadWhiteListRegexPatterns();

		_monitorService.modifyCounter(Counter.ALERTS_EVALUATED_RAWTOTAL, alertsWithTimestamp.size(), new HashMap<>());
		for (AlertWithTimestamp alertWithTimestamp : alertsWithTimestamp)
		{
			String serializedAlert = alertWithTimestamp.getSerializedAlert();

			_logger.debug(MessageFormat.format("serializedAlert {0}", serializedAlert));

			Alert alert;
			try
			{
				alert = _mapper.readValue(serializedAlert, Alert.class);
			} catch (Exception e)
			{
				String logMessage = MessageFormat.format("Failed to deserialize alert {0}. Full stack trace of exception {1}", serializedAlert, ExceptionUtils.getFullStackTrace(e));
				_logger.warn(logMessage);

				logAlertStatsOnFailure(DEFAULTALERTID, DEFAULTUSER);
				continue;
			}

			if (!_shouldEvaluateAlert(alert, alert.getId()))
			{
				logAlertStatsOnFailure(alert.getId(), alert.getOwner().getUserName());
				continue;
			}

			if (alertEnqueueTimestampsByAlertId.containsKey(alert.getId()))
			{
				String logMessage = MessageFormat.format("Found alert {0}:{1} with multiple timestamps. ExistingTime:{2} NewTime:{3}. Existing evaluation will be overwritten.",
						alert.getId(), alert.getName(), alertEnqueueTimestampsByAlertId.get(alert.getId()), alertWithTimestamp.getAlertEnqueueTime());
				_logger.warn(logMessage);

				// Treating this as a failure.
				logAlertStatsOnFailure(alert.getId(), alert.getOwner().getUserName());
			}

			alertEnqueueTimestampsByAlertId.put(alert.getId(), alertWithTimestamp.getAlertEnqueueTime());

			List<Notification> notifications = new ArrayList<>(alert.getNotifications());
			alert.setNotifications(null);

			if (notifications.size() == 0)
			{
				String logMessage = MessageFormat.format("Found alert {0}:{1} with no notification.", alert.getId(), alert.getName());
				_logger.warn(logMessage);

				// Treating this as a failure.
				logAlertStatsOnFailure(alert.getId(), alert.getOwner().getUserName());
				continue;
			}

			for (Notification n : notifications)
			{

				if (alertsByNotificationId.containsKey(n.getId()))
				{
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

		// Adds updated notifications back to each alert.
		updateNotificationsActiveStatusAndCooldown(allNotifications);
		for (Notification n : allNotifications)
		{
			alertsByNotificationId.get(n.getId()).addNotification(n);
		}

		Set<Alert> alerts = new HashSet<>(alertsByNotificationId.values());

		long jobStartTime, evaluateEndTime;
		Long alertEnqueueTimestamp;

		String logMessage;
		History history;

		NotificationProcessor np = new NotificationProcessor(this, _logger);
		_monitorService.modifyCounter(Counter.ALERTS_EVALUATED_TOTAL, alerts.size(), new HashMap<>());
		boolean datalagMonitorEnabled = Boolean.valueOf(_configuration.getValue(SystemConfiguration.Property.DATA_LAG_MONITOR_ENABLED));
		AtomicInteger numberOfAlertsEvaluated = new AtomicInteger(alerts.size());
		for (Alert alert : alerts) {

			jobStartTime = System.currentTimeMillis();
			evaluateEndTime = 0;
			alertEnqueueTimestamp = alertEnqueueTimestampsByAlertId.get(alert.getId());
			updateRequestContext(alert);

			updateAlertStartEvaluationStats(alertEnqueueTimestampsByAlertId, alert, jobStartTime);

			history = new History(History.addDateToMessage(JobStatus.STARTED.getDescription()), HOSTNAME, alert.getId(), JobStatus.STARTED);
			Set<Trigger> missingDataTriggers = new HashSet<Trigger>();

			for (Trigger trigger : alert.getTriggers())
			{
				if (trigger.getType().equals(TriggerType.NO_DATA))
				{
					missingDataTriggers.add(trigger);
				}
			}

			boolean alertSkipped = false;
			boolean alertFailure = false;
			boolean alertEvaluationStarted = false;
			boolean doesDatalagExistInAnyDC = false;

			try
			{
				alertEnqueueTimestamp = alertEnqueueTimestampsByAlertId.get(alert.getId());
				MetricQueryResult queryResult = _metricService.getMetrics(alert.getExpression(), alertEnqueueTimestamp);
				MonitoringUtils.updateAlertMetricQueryPerfCounters(_monitorService, queryResult, alert.getOwner().getUserName()); // NOTE - ian - TODO - call this for historical testing?
				List<Metric> metrics = new ArrayList<>(queryResult.getMetricsList());
				int initialMetricSize = metrics.size();
				doesDatalagExistInAnyDC = datalagMonitorEnabled && doesDatalagExistsInAtLeastOneDC(queryResult, alert);

				/* It works only for alerts with regex based expressions
				TODO: Fix for expressions that do not go through discovery service ( i.e, non regex based expressions )
				*/
//				if (initialMetricSize == 0 && alert.getModifiedDate() != null && ((System.currentTimeMillis() - alert.getModifiedDate().getTime()) / (24 * 60 * 60 * 1000)) > MetricSchemaRecord.DEFAULT_RETENTION_DISCOVERY_DAYS && // if Last Modified time was > DEFAULT_RETENTION_DISCOVERY_DAYS
//						(_whiteListedScopeRegexPatterns.isEmpty() || !AlertUtils.isScopePresentInWhiteList(alert.getExpression(), _whiteListedScopeRegexPatterns))) { // not disable whitelisted argus alerts.
				if (false)
				{
					_logger.info("Orphan Alert detected. Disabling it and notifying user. Alert Id: {}", alert.getId());
					Alert dbAlert = findAlertByPrimaryKey(alert.getId());
					dbAlert.setEnabled(false);
					_sendOrphanAlertNotification(alert);
				} else
				{

					if (datalagMonitorEnabled)
					{
						/* Two Cases: 1. Contains transform, 2. Doesn't contain transform.
						 * If contain transform, disable if at least 1 dc is lagging.
						 * else disable per expanded expression specific lag.
						 * TODO: If transforms are independent, should we disable entirely or expression specific.
						 */
						if (queryResult.containsTransform() || initialMetricSize == 0)
						{ // Skip alert evaluation if the initial time series returned by metric service is null or if expression contains transforms and data lag exists in at least one dc.
							if (doesDatalagExistInAnyDC)
							{
								logMessage = MessageFormat.format("Skipping Alert {0} Evaluation as data was lagging in at least one dc for expression: {1}", alert.getId().intValue(), alert.getExpression());
								updateDatalagHistory(alert, historyList, logMessage);
								alertSkipped = true;
								continue;
							}
						} else
						{ // expanded alert expression doesn't contain any transforms.
							metrics.removeIf(m -> shouldMetricBeRemovedForDataLag(alert, m, historyList));
						}

						if (initialMetricSize > 0 && metrics.size() == 0)
						{ // Skip alert evaluation if all the expanded alert expression contains dc with data lag and initial size was non-zero.
							alertSkipped = true;
							_logger.info(MessageFormat.format("Skipping Alert {0} Evaluation as the metrics expressions evaluation were skipped due to data lag. {1}", alert.getId().intValue(), alert.getExpression()));
							continue;
						}
					}

					alertEvaluationStarted = true;

					evaluateEndTime = System.currentTimeMillis(); // set evaluateEndTime to evaluate start time to override init value (0)
					if (areDatapointsEmpty(metrics))
					{
						_processMissingDataNotifications(np, alert, history, alertEnqueueTimestamp, missingDataTriggers);

					} else
					{
						// _logger.error("Alert: {}", alert.toString()); // DEBUG - REMOVE or add conditional here. maybe a log whitelist? - feature for debugging

						//Only evaluate those triggers which are associated with any notification.
						Set<Trigger> triggersToEvaluate = new HashSet<>();
						for (Notification notification : alert.getNotifications())
						{
							triggersToEvaluate.addAll(notification.getTriggers());
						}

						Map<BigInteger, Map<Metric, Long>> triggerFiredTimesAndMetricsByTrigger = _evaluateTriggers(triggersToEvaluate,
								metrics, alert.getExpression(), alertEnqueueTimestamp, this::noopTags);

						evaluateEndTime = System.currentTimeMillis();

						for (Notification notification : alert.getNotifications())
						{
							if (notification.getTriggers().isEmpty())
							{
								_processTriggerlessNotification(np, alert, history, metrics, notification, alertEnqueueTimestamp);
							} else
							{
								_processNotification(np, alert, history, metrics, triggerFiredTimesAndMetricsByTrigger, notification, alertEnqueueTimestamp);
								if (missingDataTriggers.size() > 0)
								{
									// processing to possibly to clear missing data notification
									_processMissingDataNotification(np, alert, history, missingDataTriggers, notification, false, alertEnqueueTimestamp);
								}
							}
						}
					}
				}

				history.appendMessageNUpdateHistory("Alert was evaluated successfully.", JobStatus.SUCCESS, System.currentTimeMillis() - jobStartTime);

			} catch (MissingDataException mde)
			{
				if (doesDatalagExistInAnyDC && !alertEvaluationStarted)
				{
					alertSkipped = true;
				}
				alertFailure = true;
				_handleAlertEvaluationException(np, alert, jobStartTime, alertEnqueueTimestamp, history, missingDataTriggers, mde, true);
			} catch (Exception ex)
			{
				if (doesDatalagExistInAnyDC && !alertEvaluationStarted)
				{
					alertSkipped = true;
				}
				alertFailure = true;
				_handleAlertEvaluationException(np, alert, jobStartTime, alertEnqueueTimestamp, history, missingDataTriggers, ex, false);
			} finally
			{

				history = _historyService.createHistory(alert, history.getMessage(), history.getJobStatus(), history.getExecutionTime());
				historyList.add(history);

				Map<String, String> tags = new HashMap<>();
				tags.put(HOSTTAG, HOSTNAME);
				tags.put(USERTAG, alert.getOwner().getUserName());

				if (!alertSkipped)
				{
					_monitorService.modifyCounter(Counter.ALERTS_EVALUATION_LATENCY, System.currentTimeMillis() - jobStartTime, tags);
					if (evaluateEndTime == 0)
					{
						evaluateEndTime = System.currentTimeMillis();
					}
					_monitorService.modifyCounter(Counter.ALERTS_EVALUATION_ONLY_LATENCY, evaluateEndTime - jobStartTime, tags);
					_monitorService.modifyCounter(Counter.ALERTS_EVALUATION_LATENCY_COUNT, 1, tags);
				}

				_monitorService.modifyCounter(alertSkipped ? Counter.ALERTS_SKIPPED : Counter.ALERTS_EVALUATED, 1, tags);

				if (alertFailure)
				{
					_monitorService.modifyCounter(Counter.ALERTS_FAILED, 1, tags);
				}

				tags.put(ALERTIDTAG, alert.getId().toString());

				if(alertSkipped) {
					numberOfAlertsEvaluated.decrementAndGet();
					publishAlertTrackingMetric(Counter.ALERTS_SKIPPED.getMetric(), 1.0, tags);
				} else
				{
					publishAlertTrackingMetric(Counter.ALERTS_EVALUATED.getMetric(), 1.0, tags);
				}

				if (alertFailure)
				{
					publishAlertTrackingMetric(Counter.ALERTS_FAILED.getMetric(), 1.0, tags);
				}
			}
		} // end for
		return numberOfAlertsEvaluated.get();
	}

	@VisibleForTesting
	protected void updateRequestContext(Alert alert)
	{
		RequestContextHolder.setRequestContext(new RequestContext(alert.getOwner().getUserName() + "-alert"));
	}

	private boolean doesDatalagExistsInAtLeastOneDC(MetricQueryResult queryResult, Alert alert)
	{

		boolean isLagPresentInAtLeastOneDC = false;

		List<MetricQuery> mQInboundList = queryResult.getInboundMetricQueries();
		List<String> dcList = _metricService.extractDCFromMetricQuery(mQInboundList);

		if (dcList == null || dcList.size() == 0)
		{
			isLagPresentInAtLeastOneDC = doesDatalagConditionSatisfy(alert, null);
		}

		for (String currentDC : dcList)
		{
			isLagPresentInAtLeastOneDC |= doesDatalagConditionSatisfy(alert, currentDC);
		}

		_logger.debug(MessageFormat.format("AlertId: {0}, Expression:{1}, DC detected: {2}, lagPresent:{3}", alert.getId(), alert.getExpression(), dcList, isLagPresentInAtLeastOneDC));
		return isLagPresentInAtLeastOneDC;
	}

	private boolean doesDatalagConditionSatisfy(Alert alert, String currentDC)
	{
		return _monitorService.isDataLagging(currentDC) &&
				(_whiteListedScopeRegexPatterns.isEmpty() || !AlertUtils.isPatternPresentInWhiteList(alert.getExpression(), _whiteListedScopeRegexPatterns)) &&
				(_whiteListedUserRegexPatterns.isEmpty() || !AlertUtils.isPatternPresentInWhiteList(alert.getOwner().getUserName(), _whiteListedUserRegexPatterns));
	}

	private void updateDatalagHistory(Alert alert, List<History> historyList, String historyMessage)
	{
		_logger.info(historyMessage);
		if (historyList != null)
		{
			History history = new History(History.addDateToMessage(JobStatus.SKIPPED.getDescription()), HOSTNAME, alert.getId(), JobStatus.SKIPPED);
			history.appendMessageNUpdateHistory(historyMessage, null, 0);
			history = _historyService.createHistory(alert, history.getMessage(), history.getJobStatus(), history.getExecutionTime());
			historyList.add(history);
		}
	}

	// TODO - handle case when testing. should return a
	private String _shouldMetricBeRemovedForDataLag(Alert alert, Metric m)
	{
		try
		{
			String currentDC = _metricService.extractDCFromMetric(m);
			if (doesDatalagConditionSatisfy(alert, currentDC))
			{
				String logMessage = String.format("Skipping evaluation of the alert expression with scope: %s in alert with id: %d due metric data was lagging in DC: %s", m.getScope(), alert.getId().intValue(), currentDC);
				return logMessage;
			}
			return null;
		} catch (Exception ex)
		{
			_logger.error("Error while identifying metric be removed from datalag: {}", ex);
			return null;
		}
	}

	private boolean shouldMetricBeRemovedForDataLag(Alert alert, Metric m, List<History> historyList)
	{
		String msg = _shouldMetricBeRemovedForDataLag(alert, m);
		if (msg != null)
		{
			String logMessage = MessageFormat.format("{0}", msg);
			updateDatalagHistory(alert, historyList, logMessage);
			return true;
		}
		return false;
	}

	private void updateAlertStartEvaluationStats(Map<BigInteger, Long> alertEnqueueTimestampsByAlertId, Alert alert, long jobStartTime)
	{
		Long alertEnqueueTimestamp = 0L;

		Map<String, String> tags = new HashMap<>();
		tags.put(USERTAG, alert.getOwner().getUserName());

		if (alertEnqueueTimestampsByAlertId.containsKey(alert.getId()))
		{

			alertEnqueueTimestamp = alertEnqueueTimestampsByAlertId.get(alert.getId());

			if (jobStartTime - alertEnqueueTimestamp > EVALUATIONDELAY)
			{
				_monitorService.modifyCounter(Counter.ALERTS_EVALUATION_DELAYED, 1, tags);
				_logger.warn("EVALUATION_DELAYED: Alert {}:{} enQueueTime {} evaluationTime {}",
						alert.getId(), alert.getName(), alertEnqueueTimestamp, jobStartTime);
			} else
			{
				_monitorService.modifyCounter(Counter.ALERTS_EVALUATION_STARTED, 1, tags);
			}
		}
	}

	private void logAlertStatsOnFailure(BigInteger alertId, String user)
	{
		Map<String, String> tags = new HashMap<>();
		tags.put(USERTAG, user);

		_monitorService.modifyCounter(Counter.ALERTS_FAILED, 1, tags);
		_monitorService.modifyCounter(Counter.ALERTS_EVALUATED, 1, tags);

		tags.put(HOSTTAG, HOSTNAME);
		tags.put(ALERTIDTAG, alertId.toString());

		publishAlertTrackingMetric(Counter.ALERTS_EVALUATED.getMetric(), -1.0/*failure*/, tags);
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


	/* ----------------------------------------------------------------------------------------------
	 * Generic Notification Processing
	 *   Specific processing for each situation is implemented by classes implementing INotificationHandler.
	 *   The higher level notification processing is handled by the following functions:
	 *     1. _processMissingDataNotifications()
	 *     2. _handleAlertEvaluationException()
	 *     3. _processNotification()
	 *     4. _processTriggerlessNotification()
	 *     5. _processMissingDataNotification()
	 *   These functions call INotificationHandler to perform the notification handling.
	 *   Normal alert processing uses NotificationProcessor.
	 *   Alert testing uses TestNotificationProcessor.
	 * ---------------------------------------------------------------------------------------------*/


	/*
	 * INotificationHandler defines the methods called to handle notification in various situations.
	 * The default implementation delivers notifications the usual way.
	 * THe test implementation drops notifications on the floor and will later record that they notified.
	 */
	interface INotificationHandler {

		// Notification Handling
		void _onNotificationRefocusValueNotifier(Notification n, History h);

		void _onNotificationFired(Alert alert, Trigger trigger, Notification notification, Metric m, History history,
								  Map<Metric, Long> triggerFiredTimesForMetrics, Boolean isBooleanRefocusNotifier, Long alertEnqueueTimestamp);

		void _onNotificationCleared(Alert alert, Trigger trigger, Notification notification, Metric m, History history,
									boolean isBooleanRefocusNotifier, Long alertEnqueueTimestamp);

		// Triggerless Notification Handling
		void _onTriggerlessIsNotRefocusValueNotifier(Notification n, History h);

		void _onTriggerlessIsRefocusValueNotifier(Alert alert, Notification notification, Metric m, History history,
												  Long dataPoint, Long alertEnqueueTimestamp);

		// Missing Data Notification Handling
		void _onMissingDataRefocusValueNotification(Notification n, History h);

		void _onMissingDataNotification(Alert alert, Trigger trigger, Notification notification, Metric m, History history,
										boolean isRefocusNotifier, Long alertEnqueueTimestamp);

		void _onMissingClearedDataNotification(Alert alert, Trigger trigger, Notification notification, Metric m, History history,
											   boolean isRefocusNotifier, Long alertEnqueueTimestamp);

		void _onMissingDataNotificationEnabled(Alert alert, History history);

		void _onMissingDataNotificationDisabled(Alert alert, History history);

		// Alert Evaluation Exception Handling
		void _onAlertEvaluationException(Alert alert, History history,
										 long jobStartTime, Exception ex, boolean isDataMissing);

	}


	private void _processMissingDataNotifications(INotificationHandler np, Alert alert, History history, Long alertEvaluationTime, Set<Trigger> missingDataTriggers)
	{
		if(alert.isMissingDataNotificationEnabled()) {
			np._onMissingDataNotificationEnabled(alert, history);
		}
		else {
			np._onMissingDataNotificationDisabled(alert, history);
		}

		if(missingDataTriggers.size()>0) {
			for (Notification notification : alert.getNotifications()) {
				if (!notification.getTriggers().isEmpty()) {
					_processMissingDataNotification(np, alert, history, missingDataTriggers, notification, true, alertEvaluationTime);
				}
			}
		}
	}

	private void _handleAlertEvaluationException(INotificationHandler np, Alert alert, long jobStartTime, Long alertEnqueueTimestamp, History history,
												 Set<Trigger> missingDataTriggers, Exception ex, Boolean isDataMissing) {
		try
		{
			np._onAlertEvaluationException(alert, history, jobStartTime, ex, isDataMissing);

			String exMessage = ExceptionUtils.getMessage(ex);
			if (exMessage.contains("net.opentsdb.tsd.BadRequestException") || isDataMissing)
			{
				_processMissingDataNotifications(np, alert, history, alertEnqueueTimestamp, missingDataTriggers);
			}
		}
		catch(Exception e)
		{
			String logMessage = MessageFormat.format("Unexpected exception evaluating alert : `{0}`. Full stack trace of exception - {1}", alert.getId().intValue(), ExceptionUtils.getFullStackTrace(e));
			_logger.warn(logMessage);
		}
	}


	/**
	 * Evaluates all triggers associated with the notification and updates the job history.
	 */
	public void _processNotification(INotificationHandler notificationHandler,
									 Alert alert,
									 History history,
									 List<Metric> metrics,
									 Map<BigInteger, Map<Metric, Long>> triggerFiredTimesAndMetricsByTrigger,
									 Notification notification,
									 Long alertEnqueueTimestamp) {

		//refocus notifier does not need cool down logic, and every evaluation needs to send notification
		// Future - once refocus v1 notifiers are migrated to refocus_boolean notifiers, remove REFOCUS.
		boolean isBooleanRefocusNotifier = SupportedNotifier.REFOCUS.getName().equals(notification.getNotifierName()) ||
				SupportedNotifier.REFOCUS_BOOLEAN.getName().equals(notification.getNotifierName());
		boolean isValueRefocusNotifier = SupportedNotifier.REFOCUS_VALUE.getName().equals(notification.getNotifierName());

		if (isValueRefocusNotifier) {
			// Future - For now just ignore RefocusValueNotifiers attached to Triggers.
			notificationHandler._onNotificationRefocusValueNotifier(notification, history);
			return;
		}

		for (Trigger trigger : notification.getTriggers()) {
			Map<Metric, Long> triggerFiredTimesForMetrics = triggerFiredTimesAndMetricsByTrigger.get(trigger.getId());

			for (Metric m : metrics) {
				if (triggerFiredTimesForMetrics != null && triggerFiredTimesForMetrics.containsKey(m)) {
					notificationHandler._onNotificationFired( alert, trigger, notification, m, history,
							triggerFiredTimesForMetrics,
							isBooleanRefocusNotifier, alertEnqueueTimestamp);

				} else {
					notificationHandler._onNotificationCleared( alert, trigger, notification, m, history,
							isBooleanRefocusNotifier, alertEnqueueTimestamp);
				}
			}
		}
	}

	/**
	 * Evaluates notifiers without triggers.  Only RefocusValueNotifiers can execute with out a trigger.
	 * All other notifiers without triggers are logged.
	 */
	public void _processTriggerlessNotification(INotificationHandler notificationHandler,
												Alert alert,
												History history,
												List<Metric> metrics,
												Notification notification,
												Long alertEnqueueTimestamp) {

		boolean isRefocusValueNotifier = SupportedNotifier.REFOCUS_VALUE.getName().equals(notification.getNotifierName());

		if (!isRefocusValueNotifier) {
			notificationHandler._onTriggerlessIsNotRefocusValueNotifier(notification, history);
		} else {

			// Refocus Notifiers: every evaluation needs to send notification
			// Future - file work item for Refocus -> each metric (evaluated expression) will be directed to all of the S+A in the notifier.
			// future - Work item will request expansion of the S+A based on some part of the metric expression.
			// FOR NOW - Users should auther Alerts with RefocusValueNotifiers to have only a single expression.
			for (Metric m : metrics) {
				Long latestDataPoint = getLatestDatapointTime(m, alert.getExpression(), alertEnqueueTimestamp);

				if (latestDataPoint != null) {
					notificationHandler._onTriggerlessIsRefocusValueNotifier( alert, notification, m, history, latestDataPoint, alertEnqueueTimestamp);
				}
			}
		}
	}

	/**
	 * Evaluates all triggers associated with the missing data notification and updates the job history.
	 */
	public void _processMissingDataNotification(INotificationHandler notificationHandler,
												Alert alert,
												History history,
												Set<Trigger> triggers,
												Notification notification,
												boolean isDataMissing,
												Long alertEnqueueTimestamp){

		//refocus notifier does not need cool down logic, and every evaluation needs to send notification
		boolean isRefocusNotifier = SupportedNotifier.REFOCUS.getName().equals(notification.getNotifierName()) ||
				SupportedNotifier.REFOCUS_BOOLEAN.getName().equals(notification.getNotifierName());
		boolean isValueRefocusNotifier = SupportedNotifier.REFOCUS_VALUE.getName().equals(notification.getNotifierName());

		if (isValueRefocusNotifier) {
			notificationHandler._onMissingDataRefocusValueNotification(notification, history);
			return;
		}

		for (Trigger trigger : notification.getTriggers()) {
			if (triggers.contains(trigger)) {
				Metric m = new Metric("unknown", "unknown");
				if (isDataMissing) {
					notificationHandler._onMissingDataNotification( alert, trigger, notification, m, history,
							isRefocusNotifier, alertEnqueueTimestamp);

				} else {
					notificationHandler._onMissingClearedDataNotification( alert, trigger, notification, m, history,
							isRefocusNotifier, alertEnqueueTimestamp);
				}
			}
		}
	}

	/*
	 *  Default Notification Processor - Normal handling of Notification during alert execution.
	 */
	class NotificationProcessor implements INotificationHandler {

		private DefaultAlertService alertService;
		private Logger _logger;

		public NotificationProcessor(DefaultAlertService alertService, Logger logger)
		{
			this.alertService = alertService;
			this._logger = logger;
		}

		// Notification -------------------------------------------------------------------

		@Override
		public void _onNotificationRefocusValueNotifier(Notification notification, History history)
		{
			// Future - For now just ignore RefocusValueNotifiers attached to Triggers.
			String logMessage = MessageFormat.format("RefocusValueNotifiers must not be associated with triggers. Name: `{0}`", notification.getName());
			_logger.info(logMessage);
			history.appendMessageNUpdateHistory(logMessage, null, 0);
		}

		@Override
		public void _onNotificationFired(Alert alert, Trigger trigger, Notification notification, Metric m, History history,
										 Map<Metric, Long> triggerFiredTimesForMetrics,
										 Boolean isBooleanRefocusNotifier,
										 Long alertEnqueueTimestamp)
		{
			String logMessage = MessageFormat.format("The trigger `{0}` was evaluated against metric `{1}` and it is fired.", trigger.getName(), m.getIdentifier());
			history.appendMessageNUpdateHistory(logMessage, null, 0);

			if (isBooleanRefocusNotifier) {
				sendNotification(trigger, m, history, notification, alert, triggerFiredTimesForMetrics.get(m), alertEnqueueTimestamp, ACTION_NOTIFIED);
				return;
			}

			if (!notification.onCooldown(trigger, m)) {
				_updateNotificationSetActiveStatus(trigger, m, history, notification);
				sendNotification(trigger, m, history, notification, alert, triggerFiredTimesForMetrics.get(m), alertEnqueueTimestamp, ACTION_TRIGGERED);
			} else {
				logMessage = MessageFormat.format("The notification `{0}` is on cooldown until {1}.", notification.getName(), getDateMMDDYYYY(notification.getCooldownExpirationByTriggerAndMetric(trigger, m)));
				history.appendMessageNUpdateHistory(logMessage, null, 0);
			}
		}

		@Override
		public void _onNotificationCleared(Alert alert, Trigger trigger, Notification notification, Metric m, History history,
										   boolean isBooleanRefocusNotifier, Long alertEnqueueTimestamp)
		{
			String logMessage = MessageFormat.format("The trigger `{0}` was evaluated against metric `{1}` and it is not fired.", trigger.getName(), m.getIdentifier());
			history.appendMessageNUpdateHistory(logMessage, null, 0);

			if (isBooleanRefocusNotifier) {
				sendClearNotification(trigger, m, history, notification, alert, alertEnqueueTimestamp, ACTION_NOTIFIED);
				return;
			}

			if (notification.isActiveForTriggerAndMetric(trigger, m)) {
				// This is case when the notification was active for the given trigger, metric combination
				// and the metric did not violate triggering condition on current evaluation. Hence we must clear it.
				_updateNotificationClearActiveStatus(trigger, m, notification);
				sendClearNotification(trigger, m, history, notification, alert, alertEnqueueTimestamp, ACTION_CLEARED);
			}
		}

		// Triggerless Notification -------------------------------------------------------

		@Override
		public void _onTriggerlessIsNotRefocusValueNotifier(Notification notification, History history)
		{
			String logMessage = MessageFormat.format("The notification `{0}` has no triggers.", notification.getName());
			_logger.debug(logMessage);
			history.appendMessageNUpdateHistory(logMessage, null, 0);
		}

		@Override
		public void _onTriggerlessIsRefocusValueNotifier(Alert alert, Notification notification, Metric m, History history,
														 Long dataPoint, Long alertEnqueueTimestamp)
		{
			sendNotification(null, m, history, notification, alert, dataPoint, alertEnqueueTimestamp, ACTION_NOTIFIED);
		}

		// Missing Data Notification -------------------------------------------------------

		@Override
		public void _onMissingDataRefocusValueNotification(Notification notification, History history)
		{
			// Future - For now just ignore RefocusValueNotifiers attached to NoData Scenarios.  Later we trigger, but require that the subscriptions for refocusValue have a value supplied too! S|A|Value
			String logMessage = MessageFormat.format("RefocusValueNotifiers must not be associated with no-data triggers. Name: `{0}`", notification.getName());
			_logger.info(logMessage);
			history.appendMessageNUpdateHistory(logMessage, null, 0);
			return;
		}

		@Override
		public void _onMissingDataNotification(Alert alert, Trigger trigger, Notification notification, Metric m, History history,
											   boolean isRefocusNotifier, Long alertEnqueueTimestamp)
		{
			String logMessage = MessageFormat.format("The trigger `{0}` was evaluated and it is fired as data for the metric expression `{1}` does not exist", trigger.getName(), alert.getExpression());
			history.appendMessageNUpdateHistory(logMessage, null, 0);

			if (isRefocusNotifier) {
				sendNotification(trigger, m, history, notification, alert, System.currentTimeMillis(), alertEnqueueTimestamp, ACTION_NOTIFIED);
				return;
			}

			if (!notification.onCooldown(trigger, m)) {
				_updateNotificationSetActiveStatus(trigger, m, history, notification);
				sendNotification(trigger, m, history, notification, alert, System.currentTimeMillis(), alertEnqueueTimestamp, ACTION_TRIGGERED);
			} else {
				logMessage = MessageFormat.format("The notification `{0}` is on cooldown until `{1}`.", notification.getName(), getDateMMDDYYYY(notification.getCooldownExpirationByTriggerAndMetric(trigger, m)));
				history.appendMessageNUpdateHistory(logMessage, null, 0);
			}
		}

		@Override
		public void _onMissingClearedDataNotification(Alert alert, Trigger trigger, Notification notification, Metric m, History history,
													  boolean isRefocusNotifier, Long alertEnqueueTimestamp)
		{
			String logMessage = MessageFormat.format("The trigger `{0}` was evaluated and it is not fired as data exists for the expression `{1}`", trigger.getName(), alert.getExpression());
			history.appendMessageNUpdateHistory(logMessage, null, 0);

			if (isRefocusNotifier) {
				sendClearNotification(trigger, m, history, notification, alert, alertEnqueueTimestamp, ACTION_NOTIFIED);
				return;
			}
			if (notification.isActiveForTriggerAndMetric(trigger, m)) {
				// This is case when the notification was active for the given trigger, metric combination
				// and the metric did not violate triggering condition on current evaluation. Hence we must clear it.
				_updateNotificationClearActiveStatus(trigger, m, notification);
				sendClearNotification(trigger, m, history, notification, alert, alertEnqueueTimestamp, ACTION_CLEARED);
			}
		}

		@Override
		public void _onMissingDataNotificationEnabled(Alert alert, History history) {
			_sendNotificationForMissingData(alert);

			String logMessage = MessageFormat.format("Metric data does not exist for alert expression: {0}. Sent notification for missing data.",
					alert.getExpression());
			_logger.debug(logMessage);
			history.appendMessageNUpdateHistory(logMessage, null, 0);
		}

		@Override
		public void _onMissingDataNotificationDisabled(Alert alert, History history) {

			String logMessage = MessageFormat.format("Metric data does not exist for alert expression: {0}. Missing data notification was not enabled.",
					alert.getExpression());
			_logger.debug(logMessage);
			history.appendMessageNUpdateHistory(logMessage, null, 0);
		}

		// Exception Handling ---------------------------------------------------------------

		@Override
		public void _onAlertEvaluationException(Alert alert, History history,
												long jobStartTime, Exception ex, boolean isDataMissing)
		{
			String logMessage;
			long jobEndTime = System.currentTimeMillis();
			if (isDataMissing)
			{
				logMessage = MessageFormat.format("Failed to evaluate alert : `{0}` due to missing data exception. Exception message - {1}",
						alert.getId().intValue(), ExceptionUtils.getMessage(ex));
			} else
			{
				logMessage = MessageFormat.format("Failed to evaluate alert : `{0}`. Exception message - {1}",
						alert.getId().intValue(), ExceptionUtils.getMessage(ex));
			}
			_logger.warn(logMessage);

			if (Boolean.valueOf(_configuration.getValue(SystemConfiguration.Property.EMAIL_EXCEPTIONS)))
			{
				_sendEmailToAdmin(alert, alert.getId(), ex);
			}

			history.appendMessageNUpdateHistory(logMessage, JobStatus.FAILURE, jobEndTime - jobStartTime);
		}

	}


	/**
	 * Determines if the alert should be evaluated or not.
	 */
	private boolean _shouldEvaluateAlert(Alert alert, BigInteger alertId) {
		if (alert == null) {
			_logger.warn(MessageFormat.format("Could not find alert ID `{0}`", alertId));
			return false;
		}
		if(!alert.isEnabled()) {
			_logger.warn(MessageFormat.format("Alert `{0}` has been disabled. Will not evaluate.", alert.getId().intValue()));
			return false;
		}
		return true;
	}


	private void incrementTriggersViolated(Map<String,String> tags) {
		_monitorService.modifyCounter(Counter.TRIGGERS_VIOLATED, 1, tags);
	}

	private void noopTags(Map<String,String> tags) {
		// Don't delete. This method is used by alert testing.
	}




	/**
	 * Evaluates all triggers for the given set of metrics and returns a map of triggerIds to a map containing the triggered metric
	 * and the trigger fired time.
	 * Note: this::incrementTriggersViolated() is passed to increment the triggedCounter.
	 * Returns map: trigger_id -> ( map: metric -> time )
	 */
	private Map<BigInteger, Map<Metric, Long>> _evaluateTriggers(Set<Trigger> triggers,
																 List<Metric> metrics,
																 String queryExpression,
																 Long alertEnqueueTimestamp,
																 Consumer<Map<String,String>> incrTriggeredCounter) {
		Map<BigInteger, Map<Metric, Long>> triggerFiredTimesAndMetricsByTrigger = new HashMap<>();

		for(Trigger trigger : triggers) {
			Map<Metric, Long> triggerFiredTimesForMetrics = new HashMap<>(metrics.size());
			for(Metric metric : metrics) {
				Long triggerFiredTime = getTriggerFiredDatapointTime(trigger, metric, queryExpression, alertEnqueueTimestamp);

				if (triggerFiredTime != null) {
					triggerFiredTimesForMetrics.put(metric, triggerFiredTime);
					Map<String, String> tags = new HashMap<>();
					tags.put(USERTAG, trigger.getAlert().getOwner().getUserName());
					incrTriggeredCounter.accept(tags); // In normal alert evaluation, this increments the Triggers_Violated counter.
				}
			}
			triggerFiredTimesAndMetricsByTrigger.put(trigger.getId(), triggerFiredTimesForMetrics);
		}
		return triggerFiredTimesAndMetricsByTrigger;
	}


	public void sendNotification(Trigger trigger, Metric metric, History history, Notification notification, Alert alert,
			Long triggerFiredTime, Long alertEnqueueTime, String action) {

	    /* NOTE - For trigger-less Notifications (i.e. the RefocusValueNotifier), trigger is null, and the
	       passed in triggerFiredTime is the most recent value in the metric. */
		double triggerValue = 0.0;
		if(trigger == null || !trigger.getType().equals(TriggerType.NO_DATA)){
			triggerValue = metric.getDatapoints().get(triggerFiredTime);
		}

		Pair<String, byte[]> evaluatedMetricSnapshotDetails = null;
		String evaluatedMetricSnapshotURL = null;
		if (isImagesInNotificationsEnabled(action)) {
			ImageDataRetrievalContext imageDataRetrievalContext = new ImageDataRetrievalContext(alert, trigger,
					triggerFiredTime, metric, Notifier.NotificationStatus.TRIGGERED);
			evaluatedMetricSnapshotDetails = getEvaluatedMetricSnapshotDetails(imageDataRetrievalContext);
			if (evaluatedMetricSnapshotDetails != null) {
				evaluatedMetricSnapshotURL = _imageDataRetriever.getImageURL(evaluatedMetricSnapshotDetails);
			}
		}

		Long timestamp = (triggerFiredTime != null) ? triggerFiredTime : System.currentTimeMillis();
		String alertEvaluationTrackingID = getAlertEvaluationTrackingID(alert, timestamp);

		NotificationContext context = new NotificationContext(alert, trigger, notification, triggerFiredTime,
				triggerValue, metric, history, evaluatedMetricSnapshotDetails, evaluatedMetricSnapshotURL, alertEvaluationTrackingID );
		context.setAlertEnqueueTimestamp(alertEnqueueTime);
		Notifier notifier = getNotifier(SupportedNotifier.fromClassName(notification.getNotifierName()));

		String alertId = (trigger != null) ? trigger.getAlert().getId().toString() : alert.getId().toString();
		String notificationTarget = SupportedNotifier.fromClassName(notification.getNotifierName()).name();

        Map<String, String> tags = new HashMap<>();
		String logMessage;
		boolean rc;

		try {
			rc = notifier.sendNotification(context);
		} catch (Exception e) {
			_logger.error("sendNotification() hit exception", e);
			rc = false;
		}

		// TODO - log alertId, triggerId, notificationId?
		if (rc) {
			tags.put(STATUSTAG, STATUS_SUCCESS);
			if (trigger != null) {
                logMessage = MessageFormat.format("Sent alert notification and updated the cooldown: {0}",
                        getDateMMDDYYYY(notification.getCooldownExpirationByTriggerAndMetric(trigger, metric)));
            }
            else {
                logMessage = MessageFormat.format("Sent notification to {0}",
						notificationTarget);
            }
		} else {
			tags.put(STATUSTAG, STATUS_FAILURE);
			logMessage = MessageFormat.format("Failed to send notification to {0}",
					notificationTarget);
		}

		tags.put(USERTAG, alert.getOwner().getUserName());
		tags.put(ACTIONTAG, action);
		tags.put(RETRIESTAG, Integer.toString(context.getNotificationRetries()));
		tags.put(NOTIFYTARGETTAG, notificationTarget);
		// metric published every minute by monitor service. Fewer tags, faster for aggregated debugging
		_monitorService.modifyCounter(Counter.NOTIFICATIONS_SENT, 1, tags);

		tags.put(HOSTTAG, HOSTNAME);
		tags.put(ALERTIDTAG, alertId);
		publishAlertTrackingMetric(Counter.NOTIFICATIONS_SENT.getMetric(), 1.0, tags);

		_logger.debug(logMessage);
		history.appendMessageNUpdateHistory(logMessage, null, 0);
	}

	private boolean isImagesInNotificationsEnabled(String action) {
		return Boolean.valueOf(_configuration.getValue(SystemConfiguration.Property.IMAGES_IN_NOTIFICATIONS_ENABLED)) &&
				(ACTION_TRIGGERED.equals(action) || ACTION_CLEARED.equals(action));
	}

	private Pair<String, byte[]> getEvaluatedMetricSnapshotDetails(ImageDataRetrievalContext imageDataRetrievalContext) {
		Pair<String, byte[]> evaluatedMetricSnapshotDetails;
		try {
			evaluatedMetricSnapshotDetails = _imageDataRetriever.getAnnotatedImage(imageDataRetrievalContext);
		} catch (Exception e) {
			_logger.error("Exception encountered while trying to fetch the evaluated metric snapshot details. The snapshot" +
					" or the URL will not be displayed for notification associated with alert ID "
					+ imageDataRetrievalContext.getAlert().getId(), e);
			return null;
		}
		return evaluatedMetricSnapshotDetails;
	}


	public void sendClearNotification(Trigger trigger, Metric metric, History history, Notification notification, Alert alert, Long alertEnqueueTime, String action) {
		Pair<String, byte[]> evaluatedMetricSnapshotDetails = null;
		String evaluatedMetricSnapshotURL = null;

		if (isImagesInNotificationsEnabled(action)) {
			ImageDataRetrievalContext imageDataRetrievalContext = new ImageDataRetrievalContext(alert, trigger, metric, Notifier.NotificationStatus.CLEARED);
			evaluatedMetricSnapshotDetails = getEvaluatedMetricSnapshotDetails(imageDataRetrievalContext);
			if (evaluatedMetricSnapshotDetails != null) {
				evaluatedMetricSnapshotURL = _imageDataRetriever.getImageURL(evaluatedMetricSnapshotDetails);
			}
		}

		Long timestamp = (alertEnqueueTime != null) ? alertEnqueueTime : System.currentTimeMillis();
		String alertEvaluationTrackingID = getAlertEvaluationTrackingID(alert, timestamp);

		NotificationContext context = new NotificationContext(alert, trigger, notification, System.currentTimeMillis(),
				0.0, metric, history, evaluatedMetricSnapshotDetails, evaluatedMetricSnapshotURL,
				alertEvaluationTrackingID);
		context.setAlertEnqueueTimestamp(alertEnqueueTime);
		Notifier notifier = getNotifier(SupportedNotifier.fromClassName(notification.getNotifierName()));

		String alertId = (trigger != null) ? trigger.getAlert().getId().toString() : alert.getId().toString();
		String notificationTarget = SupportedNotifier.fromClassName(notification.getNotifierName()).name();

		Map<String, String> tags = new HashMap<>();
		String logMessage;
		boolean rc;

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
			logMessage = MessageFormat.format("Failed to send clear notification to {0}", notificationTarget);
		}

		tags.put(USERTAG, alert.getOwner().getUserName());
		tags.put(ACTIONTAG, action);
		tags.put(NOTIFYTARGETTAG, notificationTarget);
		// metric published every minute by monitor service. Fewer tags, faster for aggregated debugging
		_monitorService.modifyCounter(Counter.NOTIFICATIONS_SENT, 1, tags);

		tags.put(HOSTTAG, HOSTNAME);
		tags.put(ALERTIDTAG, alertId);
		publishAlertTrackingMetric(Counter.NOTIFICATIONS_SENT.getMetric(), 1.0, tags);

		_logger.info(logMessage);
		history.appendMessageNUpdateHistory(logMessage, null, 0);
	}

	private String getAlertEvaluationTrackingID(Alert alert, Long timestamp) {
		BigInteger alertId = alert.getId();
		if(timestamp == null) {
			_logger.error("The timestamp is null. Unable to construct a tracking ID for evaluation on alert ID "+ alertId);
		}
		return alertId + "_" + timestamp;
	}


	/**
	 * Publishing tracking metric per alert/notification. For ad-hoc tracking metrics
	 */
	private void publishAlertTrackingMetric(String metric, double value, Map<String, String> tags) {
		Map<Long, Double> datapoints = new HashMap<>();
		datapoints.put(System.currentTimeMillis(), value);
		Metric trackingMetric = new Metric(ALERTSCOPE, metric);
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

		EmailContext.Builder emailContextBuilder = new EmailContext.Builder()
				.withRecipients(to)
				.withSubject(subject)
				.withEmailBody(message.toString())
				.withContentType("text/html; charset=utf-8")
				.withEmailPriority(MailService.Priority.HIGH);

		_mailService.sendMessage(emailContextBuilder.build());
		if (alert != null && alert.getOwner() != null && alert.getOwner().getEmail() != null && !alert.getOwner().getEmail().isEmpty()) {
			to.clear();
			to.add(alert.getOwner().getEmail());

			emailContextBuilder = emailContextBuilder.withRecipients(to);
			_mailService.sendMessage(emailContextBuilder.build());
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
				EmailContext emailContext = new EmailContext.Builder()
						.withRecipients(to)
						.withSubject(subject)
						.withEmailBody(message.toString())
						.withContentType("text/html; charset=utf-8")
						.withEmailPriority(MailService.Priority.NORMAL)
						.build();

				_mailService.sendMessage(emailContext);
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

		EmailContext emailContext = new EmailContext.Builder()
				.withRecipients(to)
				.withSubject(subject)
				.withEmailBody(message.toString())
				.withContentType("text/html; charset=utf-8")
				.withEmailPriority(MailService.Priority.HIGH)
				.build();

		boolean rc = _mailService.sendMessage(emailContext);

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

	// ******************************************************************************
	// Historical Testing - Alert Evaluation
	// ******************************************************************************


	/**
	 * Evaluates the serialized alert and delivers results to the result cache.
	 * Used by historical testing.
	 *
	 * @param   serializedAlert  The serializedAlert
	 * @param   when             The time at which to evaluate the alert.
	 * @param   testUuid         The test UUID.
	 *
	 * @return  returns Job history of alerts executed.
	 */
	// TODO - improve architecture - test spec, and callback class for delivering results.
	@Override
	// 	IMPORTANT - @Transactional ?? - should not be as it shouldn't talk to the DB
	public void testEvaluateAlert(String serializedAlert, Long when, String testUuid)
	{
		requireNotDisposed();

		assert(serializedAlert != null && serializedAlert.length() > 0);
		assert(testUuid != null && testUuid.length() > 0);
		assert(when != 0L);

		AlertTestResults results = new AlertTestResults(testUuid);
		Alert alert;

		_logger.debug(MessageFormat.format("historicalTest {2} evaluationTime {1} serializedAlert {0}", serializedAlert, when, testUuid));

		// NOTE - ian - Use of .getId() - reserve range of 250 IDs for use by testing.  (They can be shared during alert testing.)
		//		 note - ian - JPA reserves IDs from 100001 onward.  Could we take 2k below that?
		// NOTE - TODO - can we filter history for a specific range of IDs?
		//       NOTE - how to specify the unusable range of ids.
		// NOTE - TODO - serialized alert - on JPA load, fixup all ids to be within the range of test alerts.

		try {
			alert = _mapper.readValue(serializedAlert, Alert.class);  // IMPORTANT - verify that this is independent of ids (i.e. that internal ids work fine!)
		} catch (Exception e)
		{
			String logMessage = MessageFormat.format("Failed to deserialize alert {0}. Full stack trace of exception {1}", serializedAlert, ExceptionUtils.getFullStackTrace(e));
			_logger.warn(logMessage);
			return;
		}

		try {
			if (alert != null)
			{
				testEvaluateAlert(alert, when, results);
			}
		}
		finally
		{
			// TODO - deliver results
		}
	}

	// ------------------------------------------------------------------------------------------
	// FUTURE - Handle notification state changes (cooldown)
	// NOTE - not important for V1 of historical testing.  In any case, it requires new code
	//            to handle because the status and state must be maintained separately from the state of the cached
	//            state for live alerts.
	//        Also (IMPORTANT) this state can only be generated oldest-newest by ensuring execution order.
	// NOTE - ian - historical testing & notification - 2 problems:  1) stubs/cooldown handling,  2) cooldown should be evaluated oldest - newest
	//        note - ian - State is preserved in notifiers - bad!  COnsider post process to handle cool-down.  Note that start of window cooldown will not be correctly evaluated.
	// -----------------------------------------------------------------------------------------

	private boolean verifyTriggerNamesAreUnique(Alert alert)
	{
		List<Trigger> triggers = alert.getTriggers();
		Set<String> triggerNames = triggers.stream().map( t -> t.getName()).collect(Collectors.toSet());
		return triggers.size() == triggerNames.size();
	}

	@VisibleForTesting
	public boolean testEvaluateAlert(Alert alert, Long alertEvaluationTime, AlertTestResults testResults)
	{
		MessageList messages = new MessageList();
		String msg;
		String logMessage;

		assert(alert != null);
		assert(alertEvaluationTime != 0L);
		assert(testResults != null);

		// Isolate result recording into API
		//                  - results.recordRequest(alert,time)
		//                  - results.recordMetrics(metrics)
		//                  - results.recordTriggersFired(triggers)
		//                  - results.recordSummary(failed,nodata,latency,tags,...)
		// TODO - do in caller?
		// TODO - replace with result recorder?
		testResults.setAlert(alert);
		testResults.setEvaluationTime(alertEvaluationTime);
		testResults.setExpression(alert.getExpression());
		testResults.setCronEntry(alert.getCronEntry());

		requireNotDisposed();
		loadWhiteListRegexPatterns(); // NOTE - probably not needed for historical testing (used by datalag experiments)

		List<Notification> notifications = new ArrayList<>(alert.getNotifications());

		// Note - It's OK to test alerts without notifications, but only metrics will be returned.
		messages.info(notifications.size() == 0, () -> String.format("Info: Testing alert %s with no notifications. NO triggers will be evaluated", alert.getName()));
		// TODO - IMPORTANT - verify unique trigger names (assumes unique names and IDs)
		// TODO - IMPORTANT - calling code should validate expression
		// TODO - calling code should validate the cron.



		// Evaluate Alert, Triggers, Notifications -----------------------------------------------------------------
		// TODO - enable datalag monitor in alert testing?
		boolean datalagMonitorEnabled = Boolean.valueOf(_configuration.getValue(SystemConfiguration.Property.DATA_LAG_MONITOR_ENABLED)); // TODO - get default value

		long jobStartTime = System.currentTimeMillis();
		long evaluateEndTime = 0;

		updateRequestContext(alert); // NOTE - stores owner name of current alert in thread local storage  // UGH!

		// Collect missing data triggers
		Set<Trigger> missingDataTriggers = new HashSet<Trigger>();
		for(Trigger trigger : alert.getTriggers()) {
			if(trigger.getType().equals(TriggerType.NO_DATA)) {
				missingDataTriggers.add(trigger);
			}
		}

		boolean alertSkipped = false;
		boolean alertIsValid = true;
		boolean alertFailure = false;
		boolean alertEvaluationStarted = false;
		boolean doesDatalagExistInAnyDC = false;
		boolean noDataIsAvailable = false;
		INotificationHandler np = new TestNotificationProcessor(this, _logger, messages);

		try {

			// Verify alert semantics ---------------------------------------------------
			if (!verifyTriggerNamesAreUnique(alert))
			{
				messages.warn("Some triggers of this alert do not have unique names.");
				alertIsValid = false;  // TODO - bail instead and throw a distinct exception
			}


			// Evaluate the Expression --------------------------------------------------

			MetricQueryResult queryResult = _metricService.getMetrics(alert.getExpression(), alertEvaluationTime);
			// Updates metric query perf counters
			// QUESTION - MonitoringUtils.updateAlertMetricQueryPerfCounters(_monitorService, queryResult, alert.getOwner().getUserName()); // QUESTION - call in historical testing?
			List<Metric> metrics = new ArrayList<>(queryResult.getMetricsList());
			int initialMetricSize = metrics.size();


			// Check for Data-Lag -------------------------------------------------------
			doesDatalagExistInAnyDC = datalagMonitorEnabled && doesDatalagExistsInAtLeastOneDC(queryResult, alert);

			// TODO - keep or remove datalag testing?
			if (datalagMonitorEnabled) {
				/* Two Cases: 1. Contains transform, 2. Doesn't contain transform.
				 * If contain transform, disable if at least 1 dc is lagging.
				 * else disable per expanded expression specific lag.
				 * TODO: If transforms are independent, should we disable entirely or expression specific.
				 */
				if( queryResult.containsTransform() || initialMetricSize == 0) { // Skip alert evaluation if the initial time series returned by metric service is null or if expression contains transforms and data lag exists in at least one dc.
					if ( doesDatalagExistInAnyDC ) {
						messages.warn(String.format("Skipping Alert %s Evaluation as data was lagging in at least one dc for expression", alert.getName()));
						alertSkipped = true;
						return false;
					}
				} else { // expanded alert expression doesn't contain any transforms.

					// TODO - do we need these messages in historical testing?
					List<String> perDcDataLagMsgs = metrics.stream().map( m -> _shouldMetricBeRemovedForDataLag(alert,m)).collect(Collectors.toList());
					perDcDataLagMsgs.removeIf( s -> s == null );
					for (String m: perDcDataLagMsgs)
					{
						messages.warn(m);
					}

					// Can we do something neater so we don't have to iterate through metrics twice?
					metrics.removeIf( m -> _shouldMetricBeRemovedForDataLag(alert,m) != null ? true : false);
				}

				if (initialMetricSize > 0 && metrics.size() == 0) { // Skip alert evaluation if all the expanded alert expression contains dc with data lag and initial size was non-zero.
					alertSkipped = true;
					return false;
				}
			}


			// Return metrics computed.
			testResults.setMetrics(metrics);

			alertEvaluationStarted = true;

			evaluateEndTime = System.currentTimeMillis(); // set evaluateEndTime to evaluate start time to override init value (0)

			// If all metrics are empty
			if (areDatapointsEmpty(metrics)) {

				noDataIsAvailable = true;

				// TODO - record missing data triggers as fired. IMPORTANT
				_processMissingDataNotifications(np, alert, (History) null, alertEvaluationTime, missingDataTriggers);

			} else {

				// Determine which triggers to evaluate.
				// Only evaluate those triggers which are associated with some notification.
				int numTriggers = alert.getTriggers().size();

				Set<Trigger> triggersToEvaluate = new HashSet<>();
				for (Notification notification : alert.getNotifications()) {
					triggersToEvaluate.addAll(notification.getTriggers());
				}
				int numTriggersToEvaluate = triggersToEvaluate.size();

				Set<Trigger> nonEvaluatedTriggers = new HashSet<Trigger>(alert.getTriggers());
				nonEvaluatedTriggers.removeAll(triggersToEvaluate);

				     if (messages.warn(numTriggers == 0,                               () -> "Warning: Alert has no triggers. NO triggers will be evaluated.")) {}
				else if (messages.error(numTriggers > 0 && numTriggersToEvaluate == 0, () -> "Error: NO Notifier has a Trigger.  NO triggers will be evaluated.")) {}
				else if (messages.warn(numTriggers != numTriggersToEvaluate,           () -> "Warning: Some triggers are not linked to a Notifier and will NOT be evaluated." )) {}
				// TODO - get Alert.triggers and warn if orphan triggers (no notifier referring to them).


				// Evaluate Triggers
				Map<BigInteger, Map<Metric, Long>> triggerFiredTimesAndMetricsByTrigger =
						_evaluateTriggers(triggersToEvaluate, metrics, alert.getExpression(), alertEvaluationTime, this::incrementTriggersViolated);

				evaluateEndTime = System.currentTimeMillis();

				// Save Trigger map Here -> TODO - convert to understandable format first?
				testResults.setTriggerFirings(triggerFiredTimesAndMetricsByTrigger);
				testResults.setEvaluatedTriggers(triggersToEvaluate.stream().map( t -> t.getId()).collect(Collectors.toSet()));
				testResults.setNonEvaluatedTriggers(nonEvaluatedTriggers.stream().map( t -> t.getId()).collect(Collectors.toSet()));

				// TODO - Historical Testing - V2 - Record Notification Firing by passing a reference to the testResults object in np.

				// Execute Notifications
				for (Notification notification : alert.getNotifications()) {
					if (notification.getTriggers().isEmpty()) {
						_processTriggerlessNotification(np, alert, (History) null, metrics, notification, alertEvaluationTime);
					} else {
						_processNotification(np, alert, (History) null, metrics, triggerFiredTimesAndMetricsByTrigger, notification, alertEvaluationTime);
						if (missingDataTriggers.size() > 0) {
							// processing to possibly to clear missing data notification
							_processMissingDataNotification(np, alert, (History) null, missingDataTriggers, notification, false, alertEvaluationTime);
						}
					}
				}
			}

		} catch (MissingDataException mde) {
			if (doesDatalagExistInAnyDC && !alertEvaluationStarted) {
				alertSkipped = true;
			}
			alertFailure = true;
			_handleAlertEvaluationException(np, alert, jobStartTime, alertEvaluationTime, null,
											missingDataTriggers, mde, true);
		} catch (Exception ex) {
			if (doesDatalagExistInAnyDC && !alertEvaluationStarted) {
				alertSkipped = true;
			}
			alertFailure = true;
			_handleAlertEvaluationException(np, alert, jobStartTime, alertEvaluationTime, null,
											missingDataTriggers, ex, false);
		} finally {

			// Return variables
			Long latency = 0L;
			Long evalOnlyLatency = 0L;
			boolean bValid = alertIsValid;
			boolean bSkipped = alertSkipped;
			boolean bFailed = alertFailure; // exception caught
			boolean bNoData = noDataIsAvailable;

			Map<String, String> tags = new HashMap<>();
			tags.put(HOSTTAG, HOSTNAME);
			tags.put(USERTAG, alert.getOwner().getUserName());

			// TODO - compute latency even when skipped
			if (!alertSkipped) {
				latency = System.currentTimeMillis() - jobStartTime;
				if (evaluateEndTime == 0) {
					evaluateEndTime = System.currentTimeMillis();
				}
				evalOnlyLatency = evaluateEndTime - jobStartTime;
			}

			testResults.setTags(tags);
			testResults.setLatency(latency);
			testResults.setEvaluateOnlyLatency(evalOnlyLatency);
			testResults.setIsValid(bValid);
			testResults.setIsFailed(bFailed);
			testResults.setIsSkipped(bSkipped);
			testResults.setIsNoData(bNoData);
			testResults.setMessages(messages.messages);

		}
		return true; // TODO - return results!
	}

	/*
	 * MessageList - this class collects messages into a list of strings.
	 * For the methods that take a test, the String argument is replaced by a supplier so that the string can
	 * be lazily evaluated.   The user is expected to pass a lambda that looks like the following to construct the string
	 * when the test evaluates to true:   () -> String.format(format,...)
	 */
	// TODO - move to generic utility class
	class MessageList
	{
		public List<String> messages;

		public MessageList()
		{
			messages = new ArrayList<String>();
		}

		private boolean condition(boolean test, String type, Supplier<String> s)
		{
			if (test)
			{
				messages.add(type + ": " + s.get());
			}
			return test;
		}

		private boolean unconditional(String type, String str)
		{
			messages.add(type + ": " + str);
			return true;
		}

		public boolean warn(boolean test, Supplier<String> s)  { return condition(test, "Warning", s); }
		public boolean info(boolean test, Supplier<String> s)  { return condition(test, "Info",    s); }
		public boolean error(boolean test, Supplier<String> s) { return condition(test, "Error",   s); }

		public boolean warn(String s)  { return unconditional("Warning", s); }
		public boolean info(String s)  { return unconditional("Info", s); }
		public boolean error(String s) { return unconditional("Error", s); }
	}


	/*
	 * TestNotificationProcessor - NotificationProcessor used in Alert testing.
	 */
	// IMPORTANT - Historical Testing - V2 - reporting notification behavior,
	// TODO - encapsulate all of the result recording in this object?  Implement a result interface?
	class TestNotificationProcessor implements INotificationHandler {

		private DefaultAlertService alertService;
		private Logger _logger;
		private MessageList messages;

		public TestNotificationProcessor(DefaultAlertService alertService, Logger logger, MessageList messages )
		{
			this.alertService = alertService;
			this._logger = logger;
			this.messages = messages;
		}

		public MessageList getMessages() { return messages ; }

		// Notification -------------------------------------------------------------------

		@Override
		public void _onNotificationRefocusValueNotifier(Notification notification, History history)
		{
			// TODO - messages += MessageFormat.format("RefocusValueNotifiers must not be associated with triggers. Name: `{0}`", notification.getName());
		}

		@Override
		public void _onNotificationFired(Alert alert, Trigger trigger, Notification notification, Metric m, History history,
										 Map<Metric, Long> triggerFiredTimesForMetrics,
										 Boolean isBooleanRefocusNotifier, Long alertEnqueueTimestamp) {}

		@Override
		public void _onNotificationCleared(Alert alert, Trigger trigger, Notification notification, Metric m, History history,
										   boolean isBooleanRefocusNotifier, Long alertEnqueueTimestamp) {}

		// Triggerless Notification -------------------------------------------------------

		@Override
		public void _onTriggerlessIsNotRefocusValueNotifier(Notification notification, History history) {}

		@Override
		public void _onTriggerlessIsRefocusValueNotifier(Alert a, Notification n, Metric m, History h,
				Long dataPoint, Long alertEnqueueTimestamp) {}

		// Missing Data Notification -------------------------------------------------------

		@Override
		public void _onMissingDataRefocusValueNotification(Notification notification,  History history)
		{
			// TODO - messages += MessageFormat.format("RefocusValueNotifiers must not be associated with no-data triggers. Name: `{0}`", notification.getName());
		}

		@Override
		public void _onMissingDataNotification(Alert alert, Trigger trigger, Notification notification, Metric m, History history,
											   boolean isRefocusNotifier, Long alertEnqueueTimestamp) {}

		@Override
		public void _onMissingClearedDataNotification(Alert alert, Trigger trigger, Notification notification, Metric m, History history,
													  boolean isRefocusNotifier, Long alertEnqueueTimestamp) {}

		@Override
		public void _onMissingDataNotificationEnabled(Alert alert, History history) {}

		@Override
		public void _onMissingDataNotificationDisabled(Alert alert, History history) {}

		// Exception Handling ------------------------------------------------------------
		@Override
		public void _onAlertEvaluationException(Alert alert, History history, long jobStartTime, Exception ex, boolean isDataMissing)
		{
			long jobEndTime;
			String logMessage;
			jobEndTime = System.currentTimeMillis();
			if (isDataMissing)
			{
				logMessage = String.format("Failed to evaluate alert : `%s` due to missing data exception. Exception message - %s",
						alert.getName(), ExceptionUtils.getMessage(ex));
			} else
			{
				logMessage = String.format("Failed to evaluate alert : `%s`. Exception message - %s",
						alert.getName(), ExceptionUtils.getMessage(ex));
			}
			messages.warn(logMessage);
		}


	}


	// ******************************************************************************
	// Query API
	// ******************************************************************************

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


	// ******************************************************************************
	// Trigger support, Notification support and implementation methods.
	// Should be re-organized.
	// ******************************************************************************

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
		case PAGERDUTY:
			return _notifierFactory.getPagerDutyNotifier();
		case REFOCUS:
			return _notifierFactory.getRefocusNotifier();
		case REFOCUS_BOOLEAN:
			return _notifierFactory.getRefocusBooleanNotifier();
		case REFOCUS_VALUE:
			return _notifierFactory.getRefocusValueNotifier();
			// TODO - ian NoOpNotifier!
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
		private Pair<String, byte[]> evaluatedMetricSnapshotDetails;
		private String evaluatedMetricSnapshotURL;
		private String alertEvaluationTrackingID;

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
		public NotificationContext(Alert alert, Trigger trigger, Notification notification, long triggerFiredTime,
								   double triggerEventValue, Metric triggeredMetric, History history,
								   Pair<String, byte[]> evaluatedMetricSnapshotDetails, String evaluatedMetricSnapshotURL,
								   String alertEvaluationTrackingID) {
			this.alert = alert;
			this.trigger = trigger;
			this.coolDownExpiration = notification.getCooldownExpirationByTriggerAndMetric(trigger, triggeredMetric);
			this.notification = notification;
			this.triggerFiredTime = triggerFiredTime;
			this.triggerEventValue = triggerEventValue;
			this.triggeredMetric = triggeredMetric;
			this.alertEnqueueTimestamp = 0L;
			this.history = history;
			this.evaluatedMetricSnapshotDetails = evaluatedMetricSnapshotDetails;
			this.evaluatedMetricSnapshotURL = evaluatedMetricSnapshotURL;
			this.alertEvaluationTrackingID = alertEvaluationTrackingID;
		}

		public NotificationContext(Alert alert, Trigger trigger, Notification notification, long triggerFiredTime,
								   double triggerEventValue, Metric triggeredMetric, History history) {
			this.alert = alert;
			this.trigger = trigger;
			this.coolDownExpiration = notification.getCooldownExpirationByTriggerAndMetric(trigger, triggeredMetric);
			this.notification = notification;
			this.triggerFiredTime = triggerFiredTime;
			this.triggerEventValue = triggerEventValue;
			this.triggeredMetric = triggeredMetric;
			this.alertEnqueueTimestamp = 0L;
			this.history = history;
			this.evaluatedMetricSnapshotDetails = null;
			this.evaluatedMetricSnapshotURL = null;
			this.alertEvaluationTrackingID = null;
		}

		/** Creates a new NotificationContext object. */
		protected NotificationContext() { }

		public Notification getAlertNotification() {
			final String notificationName = notification.getName();
			for (Notification alertNotification : alert.getNotifications()) {
				if (alertNotification.getName().equalsIgnoreCase(notificationName)) {
					return alertNotification;
				}
			}
			return null;
		}

		public Trigger getAlertTrigger() {
			final String triggerName = trigger.getName();
			for (Trigger alertTrigger : alert.getTriggers()) {
				if (alertTrigger.getName().equalsIgnoreCase(triggerName)) {
					return alertTrigger;
				}
			}
			return null;
		}

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

		public Optional<Pair<String, byte[]>> getEvaluatedMetricSnapshotDetails() {
			return Optional.ofNullable(evaluatedMetricSnapshotDetails);
		}

		public Optional<String> getEvaluatedMetricSnapshotURL() {
			return Optional.ofNullable(evaluatedMetricSnapshotURL);
		}

		public Optional<String> getAlertEvaluationTrackingID() {
			return Optional.ofNullable(alertEvaluationTrackingID);
		}

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

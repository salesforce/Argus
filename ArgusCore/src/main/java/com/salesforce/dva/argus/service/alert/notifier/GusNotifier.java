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
package com.salesforce.dva.argus.service.alert.notifier;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import java.net.URLEncoder;
import java.sql.Date;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import javax.persistence.EntityManager;

import com.google.gson.Gson;
import com.google.inject.Singleton;
import com.salesforce.dva.argus.service.MonitorService;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.entity.Trigger.TriggerType;
import com.salesforce.dva.argus.service.AnnotationService;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.NotificationContext;
import com.salesforce.dva.argus.service.alert.notifier.GusTransport.EndpointInfo;
import com.salesforce.dva.argus.service.alert.notifier.GusTransport.GetAuthenticationTokenFailureException;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.util.AlertUtils;
import com.salesforce.dva.argus.util.TemplateReplacer;


/**
 * Chatter Notifier: api user can only post alert to PUBLIC group
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
@Singleton
public class GusNotifier extends AuditNotifier {

	private static final Logger _logger = LoggerFactory.getLogger(GusNotifier.class);
	private static final int MAX_ATTEMPTS_GUS_POST = 3;
	private final MonitorService monitorService;
	private volatile GusTransport gusTransport = null;

	//~ Constructors *********************************************************************************************************************************

	/**
	 * Creates a new GusNotifier object.
	 *
	 * @param  metricService      The metric service to use.  Cannot be null.
	 * @param  annotationService  The annotation service to use.  Cannot be null.
	 * @param  auditService       The audit service to use.  Cannot be null.
	 * @param  config             The system configuration.  Cannot be null.
	 * @param  emf                The entity manager factory to use.  Cannot be null.
	 */
	@Inject
	public GusNotifier(MetricService metricService, AnnotationService annotationService, AuditService auditService,
			SystemConfiguration config, Provider<EntityManager> emf, MonitorService monitorService) {
		super(metricService, annotationService, auditService, config, emf);
		requireArgument(config != null, "The configuration cannot be null.");
		this.monitorService = monitorService;
	}

	//~ Methods **************************************************************************************************************************************

	@Override
	public String getName() {
		return GusNotifier.class.getName();
	}

	@Override
	protected boolean sendAdditionalNotification(NotificationContext context) {
		requireArgument(context != null, "Notification context cannot be null.");
		if (!super.sendAdditionalNotification(context)){
			return false;
		}
		return sendGusNotification(context, NotificationStatus.TRIGGERED);
	}
	
    @Override
    protected boolean clearAdditionalNotification(NotificationContext context) {
        requireArgument(context != null, "Notification context cannot be null.");
        super.clearAdditionalNotification(context);
        return sendGusNotification(context, NotificationStatus.CLEARED);
    }
    
    protected boolean sendGusNotification(NotificationContext context, NotificationStatus status) {
		Notification notification = null;
		Trigger trigger = null;

		for (Notification tempNotification : context.getAlert().getNotifications()) {
			if (tempNotification.getName().equalsIgnoreCase(context.getNotification().getName())) {
				notification = tempNotification;
				break;
			}
		}
		requireArgument(notification != null, "Notification in notification context cannot be null.");
		for (Trigger tempTrigger : context.getAlert().getTriggers()) {
			if (tempTrigger.getName().equalsIgnoreCase(context.getTrigger().getName())) {
				trigger = tempTrigger;
				break;
			}
		}
		requireArgument(trigger != null, "Trigger in notification context cannot be null.");

		Set<String> to = new HashSet<String>(notification.getSubscriptions());
		String feed = generateGusFeed(notification, trigger, context, status);

		return postToGus(context.getHistory(),to, feed, _config);
    }

	private String generateGusFeed(Notification notification, Trigger trigger, NotificationContext context, NotificationStatus status) {
		StringBuilder sb = new StringBuilder();
		Alert currentAlert = notification.getAlert();
		String expression = AlertUtils.getExpressionWithAbsoluteStartAndEndTimeStamps(context);
		String notificationMessage = status == NotificationStatus.TRIGGERED ? "Triggered" : "Cleared";
		sb.append(MessageFormat.format("Alert {0} was {1} at {2}\n", TemplateReplacer.applyTemplateChanges(context, context.getAlert().getName()), notificationMessage,
				DATE_FORMATTER.get().format(new Date(context.getTriggerFiredTime()))));
		String customText = context.getNotification().getCustomText();
		if( customText != null && customText.length()>0  && status == NotificationStatus.TRIGGERED){
			sb.append(TemplateReplacer.applyTemplateChanges(context, customText)).append("\n");
		}
		if(currentAlert.getNotifications().size() > 1)
			sb.append(MessageFormat.format("Notification:  {0}\n", TemplateReplacer.applyTemplateChanges(context, notification.getName())));
		if(currentAlert.getTriggers().size() > 1)
			sb.append(MessageFormat.format("Triggered by:  {0}\n", TemplateReplacer.applyTemplateChanges(context, trigger.getName())));
		if(status == NotificationStatus.TRIGGERED) {
		    sb.append(MessageFormat.format("Notification is on cooldown until:  {0}\n",
				DATE_FORMATTER.get().format(new Date(context.getCoolDownExpiration()))));
		}
		if(!expression.equals("")) {
			sb.append(MessageFormat.format("URL for evaluated metric expression:  {0}\n", getExpressionUrl(expression)));
		}	else {
			sb.append(MessageFormat.format("Evaluated metric expression:  {0}\n", getExpressionUrl(context.getAlert().getExpression())));
		}
		sb.append(MessageFormat.format("Current view of the metric expression:  {0}\n", getExpressionUrl(context.getAlert().getExpression())));

		if(context.getTriggeredMetric()!=null) {
			if(status == NotificationStatus.TRIGGERED){
				sb.append(MessageFormat.format("Triggered on Metric: {0}", context.getTriggeredMetric().getIdentifier()));
			}else {
				sb.append(MessageFormat.format("Cleared on Metric: {0}", context.getTriggeredMetric().getIdentifier()));
			}
		}
		
		sb.append(MessageFormat.format("Trigger details: {0}\n", getTriggerDetails(trigger, context)));
		if(!trigger.getType().equals(TriggerType.NO_DATA) && status == NotificationStatus.TRIGGERED){
			sb.append(MessageFormat.format("Triggering event value:  {0}\n", context.getTriggerEventValue()));
		}
		sb.append("\n");
		for (String metricToAnnotate : notification.getMetricsToAnnotate()) {
			sb.append(MessageFormat.format("Annotated series for {0}: {1}\n", metricToAnnotate,
					getMetricUrl(metricToAnnotate, context.getTriggerFiredTime())));
		}
		sb.append("\n");
		sb.append(MessageFormat.format("Alert definition:  {0}\n", getAlertUrl(notification.getAlert().getId())));
		return sb.toString();
	}

	public boolean postToGus(History history, Set<String> to, String feed, SystemConfiguration _config) {
		boolean result = false;
		String failureMsg = null;
		int retries = 0;

		if (Boolean.valueOf(_config.getValue(com.salesforce.dva.argus.system.SystemConfiguration.Property.GUS_ENABLED))) {
			// So far works for only one group, will accept a set of string in future.
			String groupId = to.toArray(new String[to.size()])[0];

			boolean refresh = false; // get cached EndpointInfo by default
			for (int i = 0; i < MAX_ATTEMPTS_GUS_POST; i++) {
				retries = i;
				PostMethod gusPost = new PostMethod(_config.getValue(Property.POST_ENDPOINT.getName(), Property.POST_ENDPOINT.getDefaultValue()));

				try {
					EndpointInfo endpointInfo = getGusTransportInstance().getEndpointInfo(refresh);
					HttpClient httpclient = getGusTransportInstance().getHttpClient();
					gusPost.setRequestHeader("Authorization", "Bearer " + endpointInfo.getToken());
					String gusMessage = MessageFormat.format("{0}&subjectId={1}&text={2}",
							_config.getValue(Property.POST_ENDPOINT.getName(), Property.POST_ENDPOINT.getDefaultValue()), groupId,
							URLEncoder.encode(feed.toString(), "UTF-8"));

					gusPost.setRequestEntity(new StringRequestEntity(gusMessage, "application/x-www-form-urlencoded", null));
					int respCode = httpclient.executeMethod(gusPost);
					_logger.info("Gus message response code '{}'", respCode);
					if (respCode == 201 || respCode == 204) {
						String infoMsg = MessageFormat.format("Success - send to GUS group {0}", groupId);
						_logger.info(infoMsg);
						history.appendMessageNUpdateHistory(infoMsg, null, 0);
						result = true;
						break;
					} else {
						final String gusPostResponseBody = gusPost.getResponseBodyAsString();
						failureMsg = MessageFormat.format("Failure - send to GUS group {0}. Cause {1}", groupId, gusPostResponseBody);
						_logger.error(failureMsg);
						history.appendMessageNUpdateHistory(failureMsg, null, 0);

						List<Map<String, Object>> jsonResponseBody = new Gson().fromJson(gusPostResponseBody, List.class);
						if (jsonResponseBody != null && jsonResponseBody.size() > 0) {
							Map<String, Object> responseBodyMap = jsonResponseBody.get(0);
							if (responseBodyMap != null &&
									("INVALID_HEADER_TYPE".equals(responseBodyMap.get("message")) ||
											"INVALID_AUTH_HEADER".equals(responseBodyMap.get("errorCode")))) {
								_logger.warn("Failed with invalid auth header, attempting to refresh token if possible");
								refresh = true;
								continue;
							}
						}
						refresh = false;
					}
				} catch (GetAuthenticationTokenFailureException e) {
					failureMsg = MessageFormat.format("Caught GetAuthenticationTokenFailureException {0} when posting to gus group {1}, attempting to refresh token if possible",
							e, groupId);
					_logger.error(failureMsg);
					history.appendMessageNUpdateHistory(failureMsg, null, 0);
					refresh = true; // try forced refresh of token
				} catch (Exception e) {
					failureMsg = MessageFormat.format("Throws Exception {0} when posting to gus group {1}", e, groupId);
					_logger.error(failureMsg);
					history.appendMessageNUpdateHistory(failureMsg, null, 0);
					refresh = false;
				} finally {
					gusPost.releaseConnection();
				}
			}
			monitorService.modifyCounter(MonitorService.Counter.GUS_NOTIFICATIONS_RETRIES, retries, null);
			monitorService.modifyCounter(MonitorService.Counter.GUS_NOTIFICATIONS_FAILED, result ? 0 : 1, null);
		} else {
			failureMsg = MessageFormat.format("Sending GUS notification is disabled.  Not sending message to groups {0}.", to);
			_logger.warn(failureMsg);
			history.appendMessageNUpdateHistory(failureMsg, null, 0);
		}

		return result;
	}

	@Override
	public Properties getNotifierProperties() {
		Properties result = super.getNotifierProperties();

		for( Property property : Property.values()) {
			result.put(property.getName(), property.getDefaultValue());
		}
		return result;
	}

	protected GusTransport getGusTransportInstance() {
		if (gusTransport == null) {
			synchronized (this) {
				if (gusTransport == null) {
					gusTransport = new GusTransport(_config.getValue(Property.GUS_PROXY_HOST.getName(), null), // no default since this is optional
							_config.getValue(Property.GUS_PROXY_PORT.getName(), null), // no default since this is optional
							_config.getValue(Property.GUS_ENDPOINT.getName(), Property.GUS_ENDPOINT.getDefaultValue()),
							_config.getValue(Property.GUS_CLIENT_ID.getName(), Property.GUS_CLIENT_ID.getDefaultValue()),
							_config.getValue(Property.GUS_CLIENT_SECRET.getName(), Property.GUS_CLIENT_SECRET.getDefaultValue()),
							_config.getValue(Property.ARGUS_GUS_USER.getName(), Property.ARGUS_GUS_USER.getDefaultValue()),
							_config.getValue(Property.ARGUS_GUS_PWD.getName(), Property.ARGUS_GUS_PWD.getDefaultValue()),
							new GusTransport.EndpointInfo(_config.getValue(Property.GUS_ENDPOINT.getName(), Property.GUS_ENDPOINT.getDefaultValue()), GusTransport.NO_TOKEN));
				}
			}
		}
		return gusTransport;
	}

	public enum Property {
		/** The GUS user name. */
		ARGUS_GUS_USER("notifier.property.alert.gus_user", "test@test.com"),
		/** The GUS password. */
		ARGUS_GUS_PWD("notifier.property.alert.gus_pwd", "password"),
		/** The GUS endpoint. */
		GUS_ENDPOINT("notifier.property.alert.gus_endpoint", "https://gus.test.com"),
		/** The GUS client ID. */
		GUS_CLIENT_ID("notifier.property.alert.gus_client_id", "test123"),
		/** The GUS client secret. */
		GUS_CLIENT_SECRET("notifier.property.alert.gus_client_secret", "password"),
		/** The GUS post endpoint. */
		POST_ENDPOINT("notifier.property.alert.gus_post_endpoint", "https://gus.test.com"),
		/** The GUS proxy host. */
		GUS_PROXY_HOST("notifier.property.proxy.host", ""),
		/** The GUS port. */
		GUS_PROXY_PORT("notifier.property.proxy.port", "");

		private final String _name;
		private final String _defaultValue;

		private Property(String name, String defaultValue) {
			_name = name;
			_defaultValue = defaultValue;
		}

		/**
		 * Returns the property name.
		 *
		 * @return  The property name.
		 */
		public String getName() {
			return _name;
		}

		/**
		 * Returns the default value.
		 *
		 * @return  The default value.
		 */
		public String getDefaultValue() {
			return _defaultValue;
		}
	}
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
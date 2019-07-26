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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.entity.Trigger.TriggerType;
import com.salesforce.dva.argus.service.AnnotationService;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.NotificationContext;
import com.salesforce.dva.argus.service.alert.notifier.GusTransport.GetAuthenticationTokenFailureException;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import com.salesforce.dva.argus.util.AlertUtils;
import com.salesforce.dva.argus.util.TemplateReplacer;

import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.SocketTimeoutException;
import java.net.URLEncoder;
import java.sql.Date;
import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Implementation of notifier interface for notifying GOC++.
 *
 * @author  Fiaz Hossain (fiaz.hossain@salesforce.com)
 */
@Singleton
public class GOCNotifier extends AuditNotifier {

	private static final Logger _logger = LoggerFactory.getLogger(GOCNotifier.class);
	private static final int MAX_ATTEMPTS_GOC_POST = 3;
	private final MonitorService monitorService;
	private volatile GusTransport gusTransport = null;

	//~ Constructors *********************************************************************************************************************************

	/**
	 * Creates a new GOC notifier.
	 *
	 * @param  metricService      The metric service. Cannot be null.
	 * @param  annotationService  The annotation service. Cannot be null.
	 * @param  auditService       The audit service. Cannot be null.
	 * @param  config             The system configuration. Cannot be null.
	 * @param  emf                The entity manager factory. Cannot be null.
	 */
	@Inject
	public GOCNotifier(MetricService metricService, AnnotationService annotationService, AuditService auditService,
					   SystemConfiguration config, Provider<EntityManager> emf,
					   MonitorService monitorService) {
		super(metricService, annotationService, auditService, config, emf);
		requireArgument(config != null, "The configuration cannot be null.");
		this.monitorService = monitorService;
	}

	//~ Methods **************************************************************************************************************************************

	/**
	 * Sends an GOC++ message.
	 *  @param  severity      The message severity
	 * @param  className     The alert class name
	 * @param  elementName   The element/instance name
	 * @param  eventName     The event name
	 * @param  message       The message body.
	 * @param  severityLevel The severity level
	 * @param  srActionable  Is the GOC notification SR actionable
	 * @param  lastNotified  The last message time. (typically current time)
	 * @param triggeredOnMetric The corresponding metric
	 * @param productTag
	 * @param articleNumber
     * @return true if succeed, false if fail
	 */
	private boolean sendMessage(History history,
								Severity severity,
								String className,
								String elementName,
								String eventName,
								String message,
								int severityLevel,
								boolean srActionable,
								long lastNotified,
								Metric triggeredOnMetric,
								String productTag,
								String articleNumber,
								NotificationContext context) {
    	requireArgument(elementName != null && !elementName.isEmpty(), "ElementName cannot be null or empty.");
		requireArgument(eventName != null && !eventName.isEmpty(), "EventName cannot be null or empty.");

		boolean result = false;
		String failureMsg = null;
		int retries = 0;

		if (Boolean.valueOf(_config.getValue(com.salesforce.dva.argus.system.SystemConfiguration.Property.GOC_ENABLED))) {
			try {
				GOCDataBuilder builder = new GOCDataBuilder();

				className = _truncateIfSizeGreaterThan(className, 50);
				elementName = _truncateIfSizeGreaterThan(elementName, 100);
				eventName = _truncateIfSizeGreaterThan(eventName, 100);

				builder.withClassName(className).withElementName(elementName).withEventName(eventName).
						withSeverity(severityLevel).withSRActionable(srActionable).withEventText(message)
						.withArticleNumber(articleNumber);
				if (severity == Severity.OK) {
					builder.withActive(false).withClearedAt(lastNotified);
				} else {
					builder.withActive(true).withCreatedAt(lastNotified);
				}
				builder.withLastNotifiedAt(lastNotified);
				if (srActionable == true) {
					builder.withUserdefined2(_config.getValue(AuditNotifier.Property.AUDIT_PRODOUTAGE_EMAIL_TEMPLATE.getName(), AuditNotifier.Property.AUDIT_PRODOUTAGE_EMAIL_TEMPLATE.getDefaultValue()));
				}
				if (productTag != null) {
					builder.withProductTag(productTag);
				}

				GOCData gocData = builder.build();
				boolean refresh = false;
				CloseableHttpClient httpClient = getGusTransportInstance().getHttpClient();

				for (int i = 0; i < MAX_ATTEMPTS_GOC_POST; i++) {
					retries = i;

					CloseableHttpResponse response = null;

					try {
						GusTransport.EndpointInfo endpointInfo = getGusTransportInstance().getEndpointInfo(refresh);
						// Create upsert URI with PATCH method
						RequestBuilder rb = RequestBuilder.patch()
								.setUri(String.format("%s/services/data/v25.0/sobjects/SM_Alert__c/%s/%s",
										endpointInfo.getEndPoint(),
										urlEncode(GOCData.SM_ALERT_ID__C_FIELD),
										urlEncode(triggeredOnMetric.hashCode() + " " + gocData.getsm_Alert_Id__c())))
								.setHeader("Authorization", "Bearer " + endpointInfo.getToken())
								.setEntity(new StringEntity(gocData.toJSON(), ContentType.create("application/json")));

						response = httpClient.execute(rb.build());
						int respCode = response.getStatusLine().getStatusCode();

						// Check for success
						if (respCode == 201 || respCode == 204) {
							String infoMsg = MessageFormat.format("Success - send GOC++ having element {0} event {1} severity {2}.",
									elementName, eventName, severity.name());
							_logger.debug(infoMsg);
							history.appendMessageNUpdateHistory(infoMsg, null, 0);

							result = true;
							break;
						} else {
							final String gusPostResponseBody = EntityUtils.toString(response.getEntity());
							failureMsg = MessageFormat.format("Failure - send GOC++ having element {0} event {1} severity {2}. Response code {3} response {4}",
									elementName, eventName, severity.name(), respCode, gusPostResponseBody);

							if (respCode == 401) { // Indication that the session timed out, try refreshing token and retrying post
								_logger.warn(failureMsg);
								refresh = true;
								continue; // retry
							} else if (respCode == 400) {
								List<Map<String, Object>> jsonResponseBody = null;
								try {
									jsonResponseBody = new Gson().fromJson(gusPostResponseBody, List.class);
								} catch (RuntimeException e) {
									_logger.warn("Failed to parse response", e);
								}
								if (jsonResponseBody != null && jsonResponseBody.size() > 0) {
									Map<String, Object> responseBodyMap = jsonResponseBody.get(0);
									if (responseBodyMap != null &&
											("INVALID_HEADER_TYPE".equals(responseBodyMap.get("message")) ||
													"INVALID_AUTH_HEADER".equals(responseBodyMap.get("errorCode")))) {
										_logger.warn("Failed with invalid auth header, attempting to refresh token if possible");
										refresh = true;
										continue; // retry
									}
								}
								_logger.error(failureMsg);
								break; // do not retry
							} else if (respCode >= 500 && respCode < 600) { // Server errors
								_logger.warn(failureMsg);
								continue; // retry
							} else {
								_logger.error(failureMsg);
								break; // unknown error, do not retry
							}
						}
					} catch (SocketTimeoutException e) {
						failureMsg = MessageFormat.format("Failure - send GOC++ having element {0} event {1} severity {2}. Exception {3}",
								elementName, eventName, severity.name(), e.getMessage());
						_logger.error(failureMsg, e);

						refresh = false; // do not refresh token
						continue; // retry
					} catch (GetAuthenticationTokenFailureException e) {
						failureMsg = MessageFormat.format("Failure - send GOC++ having element {0} event {1} severity {2}. Exception {3}",
								elementName, eventName, severity.name(), e.getMessage());
						_logger.error(failureMsg, e);

						refresh = true; // try forced refresh of token
						continue; // retry
					} catch (Exception e) {
						failureMsg = MessageFormat.format("Failure - send GOC++ having element {0} event {1} severity {2}. Exception {3}",
								elementName, eventName, severity.name(), e.getMessage());
						_logger.error(failureMsg, e);
						break; // unknown error, do not retry
					} finally {
						try {
							if (response != null) {
								response.close();
							}
						} catch (IOException e) {
							_logger.error("Exception while attempting to close post to GUS response", e);
						}
					}
				}
			} catch (RuntimeException ex) {
				failureMsg = MessageFormat.format("Failure - send GOC++. Exception {0}",ex.getMessage());
				history.appendMessageNUpdateHistory(failureMsg, null, 0);
				throw new SystemException("Failed to send an GOC++ notification.", ex);
			} finally {
				monitorService.modifyCounter(MonitorService.Counter.GOC_NOTIFICATIONS_RETRIES, retries, null);
				monitorService.modifyCounter(MonitorService.Counter.GOC_NOTIFICATIONS_FAILED, result ? 0 : 1, null);
			}
		} else {
			failureMsg = MessageFormat.format("Sending GOC++ notification is disabled.  Not sending message for element {0} event {1} severity {2}.",
					elementName, eventName, severity.name());
			_logger.warn(failureMsg);
		}

		context.setNotificationRetries(retries);
		if (StringUtils.isNotBlank(failureMsg)) {
			history.appendMessageNUpdateHistory(failureMsg, null, 0);
		}
		return result;

	}

	private static String _truncateIfSizeGreaterThan(String str, int maxAllowed) {
		if(str != null && str.length() > maxAllowed) {
			str = str.substring(0, maxAllowed - 3);
			str += "...";
		}

		return str;
	}

	@Override
	public String getName() {
		return GOCNotifier.class.getName();
	}

	@Override
	protected boolean sendAdditionalNotification(NotificationContext context) {
		requireArgument(context != null, "Notification context cannot be null.");
		super.sendAdditionalNotification(context);
		return _sendAdditionalNotification(context, NotificationStatus.TRIGGERED);
	}

	@Override
	protected boolean clearAdditionalNotification(NotificationContext context) {
		requireArgument(context != null, "Notification context cannot be null.");
		super.clearAdditionalNotification(context);
		return _sendAdditionalNotification(context, NotificationStatus.CLEARED);
	}

	/**
	 * Update the state of the notification to indicate whether the triggering condition exists or has been cleared.
	 *
	 * @param  context  The notification context.  Cannot be null.
	 * @param  status   The notification status.  If null, will set the notification severity to <tt>ERROR</tt>
	 */
	protected boolean _sendAdditionalNotification(NotificationContext context, NotificationStatus status) {
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

		String body = getGOCMessageBody(notification, trigger, context, status);
		Severity sev = status == NotificationStatus.CLEARED ? Severity.OK : Severity.ERROR;

		String elementName = notification.getElementName();
		String eventName = notification.getEventName();

		if (elementName == null || elementName.isEmpty()) {
			elementName = context.getAlert().getName();
		}

		if (eventName == null || eventName.isEmpty()) {
			eventName = trigger.getName();
		}

		elementName = TemplateReplacer.applyTemplateChanges(context, elementName);
		eventName = TemplateReplacer.applyTemplateChanges(context, eventName);

		return sendMessage(context.getHistory(),
				sev,
				TemplateReplacer.applyTemplateChanges(context, notification.getName()),
				elementName,
				eventName,
				body,
				context.getNotification().getSeverityLevel(),
				context.getNotification().getSRActionable(),
				context.getTriggerFiredTime(),
				context.getTriggeredMetric(),
				notification.getProductTag(),
				notification.getArticleNumber(),
				context);
	}

	/**
	 * Returns the goc message body content.
	 *
	 * @param   notification  The source notification.
	 * @param   trigger       The source trigger.
	 * @param   context       The notification context.
	 *
	 * @return  The goc++ message body.
	 */
	protected String getGOCMessageBody(Notification notification, Trigger trigger, NotificationContext context, NotificationStatus notificationStatus) {
		StringBuilder sb = new StringBuilder();
		Alert currentAlert = notification.getAlert();
		String expression = AlertUtils.getExpressionWithAbsoluteStartAndEndTimeStamps(context);
		String notificationMessage = notificationStatus == NotificationStatus.TRIGGERED ? "Triggered" : "Cleared";
		
		sb.append(MessageFormat.format("Alert {0} was {1} at {2}\n", TemplateReplacer.applyTemplateChanges(context, context.getAlert().getName()), notificationMessage,
				DATE_FORMATTER.get().format(new Date(context.getTriggerFiredTime()))));
		String customText = context.getNotification().getCustomText();
		if( customText != null && customText.length()>0 && notificationStatus == NotificationStatus.TRIGGERED){
			sb.append(TemplateReplacer.applyTemplateChanges(context, customText)).append("\n");
		}
		if(currentAlert.getNotifications().size() > 1)
			sb.append(MessageFormat.format("Notification:  {0}\n", TemplateReplacer.applyTemplateChanges(context, notification.getName())));
		if(currentAlert.getTriggers().size() > 1)
			sb.append(MessageFormat.format("Triggered by:  {0}\n", TemplateReplacer.applyTemplateChanges(context, trigger.getName())));
		if(notificationStatus == NotificationStatus.TRIGGERED) {
		    sb.append(MessageFormat.format("Notification is on cooldown until:  {0}\n",
				DATE_FORMATTER.get().format(new Date(context.getCoolDownExpiration()))));
		}

		if (context.getEvaluatedMetricSnapshotURL().isPresent() && !context.getEvaluatedMetricSnapshotURL().get().equals("")) {
			sb.append(MessageFormat.format("Snapshot of the evaluated metric data: {0}\n", context.getEvaluatedMetricSnapshotURL().get()));
		} else {
			if(!expression.equals("")) {
				sb.append(MessageFormat.format("URL for evaluated metric expression:  {0}\n", getExpressionUrl(expression)));
			}
		}

		sb.append(MessageFormat.format("Current view of the metric expression:  {0}\n",
				getExpressionUrl(context.getAlert().getExpression())));

		if(context.getTriggeredMetric()!=null) {
			if(notificationStatus == NotificationStatus.TRIGGERED){
				sb.append(MessageFormat.format("Triggered on Metric: {0}", context.getTriggeredMetric().getIdentifier()));
			}else {
				sb.append(MessageFormat.format("Cleared on Metric: {0}", context.getTriggeredMetric().getIdentifier()));
			}
		}
		
		sb.append(MessageFormat.format("Trigger details: {0}\n", getTriggerDetails(trigger, context)));
		if(!trigger.getType().equals(TriggerType.NO_DATA) && notificationStatus == NotificationStatus.TRIGGERED){
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

	@Override
	public Properties getNotifierProperties() {
		Properties notifierProps= super.getNotifierProperties();

		for(Property property:Property.values()){
			notifierProps.put(property.getName(), property.getDefaultValue());
		}
		return notifierProps;
	}

	protected GusTransport getGusTransportInstance() {
		if (gusTransport == null) {
			synchronized (this) {
				if (gusTransport == null) {
					gusTransport = new GusTransport(_config.getValue(Property.GOC_PROXY_HOST.getName(), null), // no default since this is optional
							_config.getValue(Property.GOC_PROXY_PORT.getName(), null), // no default since this is optional
							_config.getValue(Property.GOC_PROXY_USERNAME.getName(), null), // no default since this is optional
							_config.getValue(Property.GOC_PROXY_PASSWORD.getName(), null), // no default since this is optional
							_config.getValue(Property.GOC_ENDPOINT.getName(), Property.GOC_ENDPOINT.getDefaultValue()) + "/services/oauth2/token",
							_config.getValue(Property.GOC_CLIENT_ID.getName(), Property.GOC_CLIENT_ID.getDefaultValue()),
							_config.getValue(Property.GOC_CLIENT_SECRET.getName(), Property.GOC_CLIENT_SECRET.getDefaultValue()),
							_config.getValue(Property.GOC_USER.getName(), Property.GOC_USER.getDefaultValue()),
							_config.getValue(Property.GOC_PWD.getName(), Property.GOC_PWD.getDefaultValue()),
							new GusTransport.EndpointInfo(_config.getValue(Property.GOC_ENDPOINT.getName(), Property.GOC_ENDPOINT.getDefaultValue()), GusTransport.NO_TOKEN),
							Integer.parseInt(_config.getValue(Property.GOC_CONNECTION_POOL_MAX_SIZE.getName(), Property.GOC_CONNECTION_POOL_MAX_SIZE.getDefaultValue())),
							Integer.parseInt(_config.getValue(Property.GOC_CONNECTION_POOL_MAX_PER_ROUTE.getName(), Property.GOC_CONNECTION_POOL_MAX_PER_ROUTE.getDefaultValue())));
				}
			}
		}
		return gusTransport;
	}

	private String urlEncode(String s) throws UnsupportedEncodingException{
		return URLEncoder.encode(s,org.apache.commons.lang3.CharEncoding.UTF_8).replace("+", "%20");
	}

	//~ Enums ****************************************************************************************************************************************

	/**
	 * Sets the severity of the message.
	 *
	 * @author  Fiaz Hossain (fiaz.hossain@salesforce.com)
	 */
	public enum Severity {

		OK,
		WARN,
		ERROR
	}

	/**
	 * Enumerates implementation specific configuration properties.
	 *
	 * @author  Tom Valine (tvaline@salesforce.com)
	 */
	public enum Property {

		/** The GOC endpoint. */
		GOC_ENDPOINT("notifier.property.goc.endpoint", "https://test.com"),
		/** The GOC user with which to authenticate. */
		GOC_USER("notifier.property.goc.username", "test_user"),
		/** The GOC password with which to authenticate. */
		GOC_PWD("notifier.property.goc.password", "test_password"),
		/** The GOC proxy host. */
		GOC_PROXY_HOST("notifier.property.proxy.host", ""),
		/** The GOC port. */
		GOC_PROXY_PORT("notifier.property.proxy.port", ""),
		/** The GOC proxy username. */
		GOC_PROXY_USERNAME("notifier.property.proxy.username", ""),
		/** The GOC proxy password. */
		GOC_PROXY_PASSWORD("notifier.property.proxy.password", ""),
		/** The GOC client ID. */
		GOC_CLIENT_ID("notifier.property.goc.client.id", "default_client_id"),
		/** The GOC client secret. */
		GOC_CLIENT_SECRET("notifier.property.goc.client.secret", "default_pass"),
		/** The alert URL template to be included with GOC notifications. */
		EMAIL_ALERT_URL_TEMPLATE("notifier.property.goc.alerturl.template", "http://localhost:8080/argus/alertId"),
		/** The metric URL template to be included with GOC notifications. */
		EMAIL_METRIC_URL_TEMPLATE("notifier.property.goc.metricurl.template", "http://localhost:8080/argus/metrics"),
		/** The connection pool size for connecting to GOC */
		GOC_CONNECTION_POOL_MAX_SIZE("notifier.property.goc.connectionpool.maxsize", "55"),
		/** The connection pool max per route for connecting to GOC */
		GOC_CONNECTION_POOL_MAX_PER_ROUTE("notifier.property.goc.connectionpool.maxperroute", "20");

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
		 * Returns the default property value.
		 *
		 * @return  The default property value.
		 */
		public String getDefaultValue() {
			return _defaultValue;
		}
	}

	//~ Inner classes ********************************************************************************************************************************

	public class PatchMethod extends PostMethod {
		public PatchMethod(String uri) { super(uri); }

		@Override
		public String getName() {
			return "PATCH";
		}
	}

	/**
	 * GOCData object to generate JSON.
	 *
	 * @author  Fiaz Hossain (fiaz.hossain@salesforce.com)
	 */
	public class GOCData {

		//~ Static fields/initializers *******************************************************************************************************************
		private static final String SM_ACTIVE__C_FIELD = "SM_Active__c";

		/** 
		 * The name of the GOC alert ID field.  
		 * TODO: Move this to DefaultGOCService.
		 */
		public static final String SM_ALERT_ID__C_FIELD = "SM_Alert_Id__c";
		private static final String SM_CLASSNAME__C_FIELD = "SM_ClassName__c";
		private static final String SM_CLEAREDAT__C_FIELD = "SM_ClearedAt__c";
		private static final String SM_CREATEDAT__C_FIELD = "SM_CreatedAt__c";
		private static final String SM_ELEMENTNAME__C_FIELD = "SM_ElementName__c";
		private static final String SM_EVENTNAME__C_FIELD = "SM_EventName__c";
		private static final String SM_EVENTTEXT__C_FIELD = "SM_EventText__c";
		private static final String SM_LASTNOTIFIEDAT__C_FIELD = "SM_LastNotifiedAt__c";
		private static final String SM_SEVERITY__C_FIELD = "SM_Severity__c";
		private static final String SM_SOURCEDOMAIN__C_FIELD = "SM_SourceDomain__c";
		private static final String SR_ACTIONABLE__C_FIELD = "SR_Actionable__c";
		private static final String SM_USERDEFINED2__C_FIELD = "SM_Userdefined2__c";
		private static final String SM_USERDEFINED3__C_FIELD = "SM_Userdefined3__c";
		private static final String SM_USERDEFINED10__C_FIELD = "SM_Userdefined10__c";
		private static final String SM_USERDEFINED12__C_FIELD = "SM_Userdefined12__c";
		private static final String SM_ARTICLE_NUMBER__C_FIELD = "SM_Article_Number__c";
		private static final String SM_PRODUCTTAG__C_FIELD = "SM_Product_Tag__c";

		//~ Instance fields ******************************************************************************************************************************

		private final boolean smActivec; // true when alert is active, false when alert is cleared
		private final String smAlertIdc; // Text(200) (External ID) --> sm_ElementName__c + ALERT_ID_SEPARATOR + sm_EventName__c
		private final String smClassNamec; // Text(50)
		private final long smClearedAtc; // Date/Time --> timestamp when the alert cleared, null while alert is still active
		private final long smCreatedAtc; // Date/Time --> timestamp when the alert last became active
		private final String smElementNamec; // Text(100) --> hostname
		private final String smEventNamec; // Text(50)
		private final String smEventTextc; // Long Text Area(32768)
		private final long smLastNotifiedAtc; // Date/Time --> timestamp
		private final int smSeverityc; // Number(1, 0) (External ID) --> 0 through 5
		private final String smSourceDomainc;
		private final boolean srActionablec; // Checkbox --> true if SR needs to respond to this alert
		// Userdefined fields
		private final String smUserdefined2c;
		private final String smUserdefined3c;
		private final String smUserdefined10c;
		private final String smUserdefined12c;
		private final String smArticleNumber;
		private final String smProductTag; // Product Tag associated with the alert object.



		//~ Constructors *********************************************************************************************************************************

		private GOCData(final boolean smActivec, final String smAlertIdc, final String smClassNamec, final long smClearedAtc, final long smCreatedAtc,
						final String smElementNamec, final String smEventNamec, final String smEventTextc, final long smLastNotifiedAtc, final int smSeverityc,
						final String smSourceDomainc, final boolean srActionablec, final String smUserdefined2c, final String smUserdefined3c, final String smUserdefined10c, final String smUserdefined12c,
						final String smArticleNumber, final String smProductTag) {
			this.smActivec = smActivec;
			this.smAlertIdc = smAlertIdc;
			this.smClassNamec = smClassNamec;
			this.smClearedAtc = smClearedAtc;
			this.smCreatedAtc = smCreatedAtc;
			this.smElementNamec = smElementNamec;
			this.smEventNamec = smEventNamec;
			this.smEventTextc = smEventTextc;
			this.smLastNotifiedAtc = smLastNotifiedAtc;
			this.smSeverityc = smSeverityc;
			this.smSourceDomainc = smSourceDomainc;
			this.srActionablec = srActionablec;
			this.smUserdefined2c = smUserdefined2c;
			this.smUserdefined3c = smUserdefined3c;
			this.smUserdefined10c = smUserdefined10c;
			this.smUserdefined12c = smUserdefined12c;
			this.smArticleNumber = smArticleNumber;
			this.smProductTag = smProductTag;

		}

		//~ Methods **************************************************************************************************************************************

		/**
		 * Returns the GOC alert ID field name.
		 *
		 * @return  The GOC alert ID field name.
		 */
		public String getsm_Alert_Id__c() {
			return smAlertIdc;
		}

		/**
		 * Convert data to a JSON string.
		 *
		 * @return  JSON string
		 */
		public String toJSON() {
			JsonObject gocData = new JsonObject();

			gocData.addProperty(SM_ACTIVE__C_FIELD, smActivec);

			/**
			 * SM_ALERT_ID__C_FIELD will be in the URI and should not in sObject data
			 */
			gocData.addProperty(SM_CLASSNAME__C_FIELD, smClassNamec);
			if (smClearedAtc > 0) {
				gocData.addProperty(SM_CLEAREDAT__C_FIELD, smClearedAtc);
			}
			if (smCreatedAtc > 0) {
				gocData.addProperty(SM_CREATEDAT__C_FIELD, smCreatedAtc);
			}
			gocData.addProperty(SM_ELEMENTNAME__C_FIELD, smElementNamec);
			gocData.addProperty(SM_EVENTNAME__C_FIELD, smEventNamec);
			gocData.addProperty(SM_EVENTTEXT__C_FIELD, smEventTextc);
			gocData.addProperty(SM_LASTNOTIFIEDAT__C_FIELD, smLastNotifiedAtc);
			gocData.addProperty(SM_SEVERITY__C_FIELD, smSeverityc);
			gocData.addProperty(SM_SOURCEDOMAIN__C_FIELD, smSourceDomainc);
			gocData.addProperty(SR_ACTIONABLE__C_FIELD, srActionablec);
			if(smUserdefined2c != null) {
				gocData.addProperty(SM_USERDEFINED2__C_FIELD, smUserdefined2c);
			}
			if(smUserdefined3c != null) {
				gocData.addProperty(SM_USERDEFINED3__C_FIELD, smUserdefined3c);
			}
			if(smUserdefined10c != null) {
				gocData.addProperty(SM_USERDEFINED10__C_FIELD, smUserdefined10c);
			}
			if(smUserdefined12c != null) {
				gocData.addProperty(SM_USERDEFINED12__C_FIELD, smUserdefined12c);
			}
			if(smArticleNumber != null) {
				gocData.addProperty(SM_ARTICLE_NUMBER__C_FIELD, smArticleNumber);
			}
			if(smProductTag != null) {
				gocData.addProperty(SM_PRODUCTTAG__C_FIELD, smProductTag);
			}
			return gocData.toString();
		}

		//~ Inner Classes ********************************************************************************************************************************

	}

	/**
	 * Utility builder.
	 *
	 * @author  Fiaz Hossain (fiaz.hossain@salesforce.com)
	 */
	public class GOCDataBuilder {

		private static final String SM_SOURCE_DOMAIN__C = "Argus";
		private static final String ALERT_ID_SEPARATOR = ".";

		private boolean smActivec; // true when alert is active, false when alert is cleared
		private String smClassNamec; // Text(50)
		private long smClearedAtc; // Date/Time --> timestamp when the alert cleared, null while alert is still active
		private long smCreatedAtc; // Date/Time --> timestamp when the alert last became active
		private String smElementNamec; // Text(100) --> hostname
		private String smEventNamec; // Text(50)
		private String smEventTextc; // Long Text Area(32768)
		private long smLastNotifiedAtc; // Date/Time --> timestamp
		private int smSeverityc = 5; // Number(1, 0) (External ID) --> 0 through 5
		private boolean srActionablec = false;
		private String smUserdefined2c = null;
		private String smUserdefined3c = null;
		private String smUserdefined10c = null;
		private String smUserdefined12c = null;
		private String smArticleNumber;
		private String smProductTag;

		/** Creates a new GOCDataBuilder object. */
		public GOCDataBuilder() { }

		/**
		 * Specifies the active status.
		 *
		 * @param   smActivec  True if active.
		 *
		 * @return  The updated builder object.
		 */
		public GOCDataBuilder withActive(final boolean smActivec) {
			this.smActivec = smActivec;
			return this;
		}

		/**
		 * Indicates the class name.
		 *
		 * @param   smClassNamec  The class name.
		 *
		 * @return  The updated builder object.
		 */
		public GOCDataBuilder withClassName(final String smClassNamec) {
			this.smClassNamec = smClassNamec;
			return this;
		}

		/**
		 * Specifies the cleared at time.
		 *
		 * @param   smClearedAtc  The cleared time.
		 *
		 * @return  The updated builder object.
		 */
		public GOCDataBuilder withClearedAt(final long smClearedAtc) {
			this.smClearedAtc = smClearedAtc;
			return this;
		}

		/**
		 * Specifies the created at time.
		 *
		 * @param   smCreatedAtc  The created at time.
		 *
		 * @return  The updated builder object.
		 */
		public GOCDataBuilder withCreatedAt(final long smCreatedAtc) {
			this.smCreatedAtc = smCreatedAtc;
			return this;
		}

		/**
		 * Specifies the element name.
		 *
		 * @param   smElementNamec  The element name.
		 *
		 * @return  The updated builder object.
		 */
		public GOCDataBuilder withElementName(final String smElementNamec) {
			this.smElementNamec = smElementNamec;
			return this;
		}

		/**
		 * Specifies the event name.
		 *
		 * @param   smEventNamec  The event name.
		 *
		 * @return  The updated builder object.
		 */
		public GOCDataBuilder withEventName(final String smEventNamec) {
			this.smEventNamec = smEventNamec;
			return this;
		}

		/**
		 * Specifies the event text.
		 *
		 * @param   smEventTextc  The event text.
		 *
		 * @return  The updated builder object.
		 */
		public GOCDataBuilder withEventText(final String smEventTextc) {
			this.smEventTextc = smEventTextc;
			return this;
		}

		/**
		 * Specifies the last notified date.
		 *
		 * @param   smLastNotifiedAtc  The last notified date.
		 *
		 * @return  The updated builder object.
		 */
		public GOCDataBuilder withLastNotifiedAt(final long smLastNotifiedAtc) {
			this.smLastNotifiedAtc = smLastNotifiedAtc;
			return this;
		}

		/**
		 * Specifies the severity.
		 *
		 * @param   smSeverityc  The severity.
		 *
		 * @return  The updated builder object.
		 */
		public GOCDataBuilder withSeverity(final int smSeverityc) {
			this.smSeverityc = smSeverityc;
			return this;
		}

		/**
		 * Specifies whether the alert is actionable.
		 *
		 * @param   sRActionablec  True if actionable.
		 *
		 * @return  The updated builder object.
		 */
		public GOCDataBuilder withSRActionable(final boolean sRActionablec) {
			this.srActionablec = sRActionablec;
			return this;
		}

		/**
		 * Specifies whether the userdefined2 field is defined.
		 *
		 * @param   smUserdefined2c  user defined field.
		 *
		 * @return  The updated builder object.
		 */
		public GOCDataBuilder withUserdefined2(final String smUserdefined2c) {
			this.smUserdefined2c = smUserdefined2c;
			return this;
		}

		/**
		 * Specifies whether the userdefined3 field is defined.
		 *
		 * @param   smUserdefined3c  user defined field.
		 *
		 * @return  The updated builder object.
		 */
		public GOCDataBuilder withUserdefined3(final String smUserdefined3c) {
			this.smUserdefined3c = smUserdefined3c;
			return this;
		}

		/**
		 * Specifies whether the userdefined10 field is defined.
		 *
		 * @param   smUserdefined10c  user defined field.
		 *
		 * @return  The updated builder object.
		 */
		public GOCDataBuilder withUserdefined10(final String smUserdefined10c) {
			this.smUserdefined10c = smUserdefined10c;
			return this;
		}

		/**
		 * Specifies whether the userdefined12 field is defined.
		 *
		 * @param   smUserdefined12c  user defined field.
		 *
		 * @return  The updated builder object.
		 */
		public GOCDataBuilder withUserdefined12(final String smUserdefined12c) {
			this.smUserdefined12c = smUserdefined12c;
			return this;
		}

		/**
		 * Specifies whether the userdefined2 field is defined.
		 *
		 * @param   smArticleNumber  user defined field.
		 *
		 * @return  The updated builder object.
		 */
		public GOCDataBuilder withArticleNumber(String smArticleNumber) {
			this.smArticleNumber = smArticleNumber;
			return this;
		}


		/**
		 * Specifies whether the userdefined2 field is defined.
		 *
		 * @param   smProductTag  user defined field.
		 *
		 * @return  The updated builder object.
		 */
		public GOCDataBuilder withProductTag(String smProductTag) {
			this.smProductTag = smProductTag;
			return this;
		}

		/**
		 * Create the GOCData object, use defaults where needed.
		 *
		 * @return  GOCData created based on builder data
		 */
		public GOCData build() {
			return new GOCData(smActivec, smElementNamec + ALERT_ID_SEPARATOR + smEventNamec, smClassNamec, smClearedAtc, smCreatedAtc,
					smElementNamec, smEventNamec, smEventTextc, smLastNotifiedAtc, smSeverityc, SM_SOURCE_DOMAIN__C, srActionablec, smUserdefined2c, smUserdefined3c, smUserdefined10c, smUserdefined12c, smArticleNumber, smProductTag);
		}
	}

}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

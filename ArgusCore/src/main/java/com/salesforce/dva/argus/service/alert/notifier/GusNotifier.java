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

import java.io.IOException;
import java.net.URLEncoder;
import java.sql.Date;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import javax.persistence.EntityManager;

import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.util.AlertUtils;
import com.salesforce.dva.argus.util.TemplateReplacer;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.slf4j.Logger;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.entity.Trigger.TriggerType;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.AnnotationService;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.MailService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.AlertService.Notifier.NotificationStatus;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.NotificationContext;
import com.salesforce.dva.argus.system.SystemConfiguration;

import joptsimple.internal.Strings;

/**
 * Chatter Notifier: api user can only post alert to PUBLIC group
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class GusNotifier extends AuditNotifier {

	//~ Static fields/initializers *******************************************************************************************************************
	private static final int CONNECTION_TIMEOUT_MILLIS = 10000;
	private static final int READ_TIMEOUT_MILLIS = 10000;
	private static final String UTF_8 = "UTF-8";

	//~ Instance fields ******************************************************************************************************************************
	@SLF4JTypeListener.InjectLogger
	private Logger _logger;
	private final MultiThreadedHttpConnectionManager theConnectionManager;
	{
		theConnectionManager = new MultiThreadedHttpConnectionManager();

		HttpConnectionManagerParams params = theConnectionManager.getParams();

		params.setConnectionTimeout(CONNECTION_TIMEOUT_MILLIS);
		params.setSoTimeout(READ_TIMEOUT_MILLIS);
	}    

	//~ Constructors *********************************************************************************************************************************

	/**
	 * Creates a new GusNotifier object.
	 *
	 * @param  metricService      The metric service to use.  Cannot be null.
	 * @param  annotationService  The annotation service to use.  Cannot be null.
	 * @param  auditService       The audit service to use.  Cannot be null.
	 * @param  mailService        The mail service to use.  Cannot be null.
	 * @param  config             The system configuration.  Cannot be null.
	 * @param  emf                The entity manager factory to use.  Cannot be null.
	 */
	@Inject
	public GusNotifier(MetricService metricService, AnnotationService annotationService, AuditService auditService, MailService mailService,
			SystemConfiguration config, Provider<EntityManager> emf) {
		super(metricService, annotationService, auditService, config, emf);
		requireArgument(mailService != null, "Mail service cannot be null.");
		requireArgument(config != null, "The configuration cannot be null.");
	}

	//~ Methods **************************************************************************************************************************************

	@Override
	public String getName() {
		return GusNotifier.class.getName();
	}

	@Override
	protected void sendAdditionalNotification(NotificationContext context) {
		requireArgument(context != null, "Notification context cannot be null.");
		super.sendAdditionalNotification(context);
		sendGusNotification(context, NotificationStatus.TRIGGERED);
	}
	
    @Override
    protected void clearAdditionalNotification(NotificationContext context) {
        requireArgument(context != null, "Notification context cannot be null.");
        super.clearAdditionalNotification(context);
        sendGusNotification(context, NotificationStatus.CLEARED);
    }
    
    private void sendGusNotification(NotificationContext context, NotificationStatus status) {
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

		postToGus(to, feed);
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
			sb.append(MessageFormat.format("Evaluated metric expression:  {0}\n", context.getAlert().getExpression()));
		}
		
		if(context.getTriggeredMetric()!=null) {
			if(status == NotificationStatus.TRIGGERED){
				sb.append(MessageFormat.format("<b>Triggered on Metric:  </b> {0}<br/>", context.getTriggeredMetric().getIdentifier()));
			}else {
				sb.append(MessageFormat.format("<b>Cleared on Metric:  </b> {0}<br/>", context.getTriggeredMetric().getIdentifier()));
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

	private void postToGus(Set<String> to, String feed) {

		if (Boolean.valueOf(_config.getValue(com.salesforce.dva.argus.system.SystemConfiguration.Property.GUS_ENABLED))) {
			// So far works for only one group, will accept a set of string in future.
			String groupId = to.toArray(new String[to.size()])[0];
			PostMethod gusPost = new PostMethod(_config.getValue(Property.POST_ENDPOINT.getName(), Property.POST_ENDPOINT.getDefaultValue()));

			try {
				gusPost.setRequestHeader("Authorization", "Bearer " + generateAccessToken());
				String gusMessage = MessageFormat.format("{0}&subjectId={1}&text={2}",
						_config.getValue(Property.POST_ENDPOINT.getName(), Property.POST_ENDPOINT.getDefaultValue()), groupId,
						URLEncoder.encode(feed.toString(), "UTF-8"));

				gusPost.setRequestEntity(new StringRequestEntity(gusMessage, "application/x-www-form-urlencoded", null));
				HttpClient httpclient = getHttpClient(_config);
				int respCode = httpclient.executeMethod(gusPost);
				_logger.info("Gus message response code '{}'", respCode);
				if (respCode == 201 || respCode == 204) {
					_logger.info("Success - send to GUS group {}", groupId);
				} else {
					_logger.error("Failure - send to GUS group {}. Cause {}", groupId, gusPost.getResponseBodyAsString());
				}
			} catch (Exception e) {
				_logger.error("Throws Exception {} when posting to gus group {}", e, groupId);
			} finally {
				gusPost.releaseConnection();
			}
		} else {
			_logger.info("Sending GUS notification is disabled.  Not sending message to groups '{}'.", to);
		}
	}

	private String generateAccessToken() {
		// Set up an HTTP client that makes a connection to REST API.
		HttpClient httpclient = getHttpClient(_config);

		// Send a post request to the OAuth URL.
		PostMethod oauthPost = new PostMethod(_config.getValue(Property.GUS_ENDPOINT.getName(), Property.GUS_ENDPOINT.getDefaultValue()));

		try {
			oauthPost.addParameter("grant_type", "password");
			oauthPost.addParameter("client_id",
					URLEncoder.encode(_config.getValue(Property.GUS_CLIENT_ID.getName(), Property.GUS_CLIENT_ID.getDefaultValue()), UTF_8));
			oauthPost.addParameter("client_secret",
					URLEncoder.encode(_config.getValue(Property.GUS_CLIENT_SECRET.getName(), Property.GUS_CLIENT_SECRET.getDefaultValue()), UTF_8));
			oauthPost.addParameter("username", _config.getValue(Property.ARGUS_GUS_USER.getName(), Property.ARGUS_GUS_USER.getDefaultValue()));
			oauthPost.addParameter("password", _config.getValue(Property.ARGUS_GUS_PWD.getName(), Property.ARGUS_GUS_PWD.getDefaultValue()));

			int respCode = httpclient.executeMethod(oauthPost);

			_logger.info("Response code '{}'", respCode);

			// Check for success
			if (respCode == 200) {
				JsonObject authResponse = new Gson().fromJson(oauthPost.getResponseBodyAsString(), JsonObject.class);
				String endpoint = authResponse.get("instance_url").getAsString();
				String token = authResponse.get("access_token").getAsString();

				_logger.info("Success - getting access_token for endpoint '{}'", endpoint);
				_logger.info("access_token '{}'", token);
				return token;
			}
			else {
				_logger.error("Failure - getting oauth2 token, check username/password: '{}'", oauthPost.getResponseBodyAsString());
			} 
		} catch (RuntimeException | IOException e) {
			_logger.error("Failure - exception getting gus access_token {}", e);
		} finally {
			oauthPost.releaseConnection();
		}
		return Strings.EMPTY;
	}

	/**
	 * Get HttpClient with proper proxy and timeout settings.
	 *
	 * @param   config  The system configuration.  Cannot be null.
	 *
	 * @return  HttpClient
	 */
	public  HttpClient getHttpClient(SystemConfiguration config) {
		HttpClient httpclient = new HttpClient(theConnectionManager);

		// Wait for 2 seconds to get a connection from pool
		httpclient.getParams().setParameter("http.connection-manager.timeout", 2000L); 

		String host = config.getValue(Property.GUS_PROXY_HOST.getName(), Property.GUS_PROXY_HOST.getDefaultValue());

		if (host != null && host.length() > 0) {
			httpclient.getHostConfiguration().setProxy(host,
					Integer.parseInt(config.getValue(Property.GUS_PROXY_PORT.getName(), Property.GUS_PROXY_PORT.getDefaultValue())));
		}
		return httpclient;
	}    

	@Override
	public Properties getNotifierProperties() {
		Properties result = super.getNotifierProperties();

		for( Property property : Property.values()) {
			result.put(property.getName(), property.getDefaultValue());
		}
		return result;
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
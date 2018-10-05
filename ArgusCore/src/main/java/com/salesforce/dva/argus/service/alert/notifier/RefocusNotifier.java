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

import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.AnnotationService;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.NotificationContext;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;

import javax.persistence.EntityManager;
import java.util.List;
import java.util.Properties;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Implementation of notifier interface for notifying Refocus.
 *
 * @author  Janine Zou (yzou@salesforce.com)
 */
public class RefocusNotifier extends AuditNotifier {

	@SLF4JTypeListener.InjectLogger
	private Logger _logger;
	private final String endpoint;


	/**
	 * Creates a new Refocus notifier.
	 *
	 * @param  metricService      The metric service. Cannot be null.
	 * @param  annotationService  The annotation service. Cannot be null.
	 * @param  auditService       The audit service. Cannot be null.
	 * @param  config             The system configuration. Cannot be null.
	 * @param  emf                The entity manager factory. Cannot be null.
	 */
	@Inject
	public RefocusNotifier(MetricService metricService, AnnotationService annotationService, AuditService auditService,
                           SystemConfiguration config, Provider<EntityManager> emf) {
		super(metricService, annotationService, auditService, config, emf);
		requireArgument(config != null, "The configuration cannot be null.");
		endpoint = _config.getValue(Property.REFOCUS_ENDPOINT.getName(), Property.REFOCUS_ENDPOINT.getDefaultValue());
	}

	@Override
	protected void sendAdditionalNotification(NotificationContext context) {
		_sendRefocusNotification(context, true);
	}

	@Override
	protected void clearAdditionalNotification(NotificationContext context) {
		_sendRefocusNotification(context, false);
	}

	private void _sendRefocusNotification(NotificationContext context, boolean isTriggerActive) {
		List<String> aspectPaths = context.getNotification().getSubscriptions();
		String token = context.getNotification().getCustomText();

		//TODO: get customer specified refocus sample values when UI is ready, currently use 1 for active trigger and 0 for non-active trigger

		requireArgument(StringUtils.isNoneBlank(token), "Token(custom text) cannot be blank.");
		requireArgument(aspectPaths!=null && !aspectPaths.isEmpty(), "aspect paths (subscriptions) cannot be empty.");

		for (String aspect : aspectPaths) {
			sendMessage(aspect, token, isTriggerActive);
		}

	}

	/**
	 * Sends an Refocus sample.
	 *
	 * @param  aspectPath    The Refocus aspect path.
	 * @param  token         The Customer token to access Refocus.
	 * @param  fired         If the trigger is fired or not.
	 */
	private void sendMessage(String aspectPath, String token, boolean fired) {
		if (Boolean.valueOf(_config.getValue(SystemConfiguration.Property.REFOCUS_ENABLED))) {
			int refreshMaxTimes = Integer.parseInt(_config.getValue(Property.REFOCUS_CONNECTION_MAX_TIMES.getName(), Property.REFOCUS_CONNECTION_MAX_TIMES.getDefaultValue()));
			try {

				//TODO: get customer specified refocus sample values when UI is ready, currently use 1 for active trigger and 0 for non-active trigger

				RefocusSample refocusSample = new RefocusSample(aspectPath, fired ? 1:0);
				RefocusTransport refocusTransport = RefocusTransport.getInstance();
				HttpClient httpclient = refocusTransport.getHttpClient(_config);


				for (int i = 0; i < 1+refreshMaxTimes; i++) {

					PostMethod post = null;

					try {
						post = new PostMethod(String.format("%s/v1/samples/upsert", endpoint));
						post.setRequestHeader("Authorization", token);
						post.setRequestEntity(new StringRequestEntity(refocusSample.toJSON(), "application/json", null));

						int respCode = httpclient.executeMethod(post);

						// Check for success
						if (respCode == 200 || respCode == 201 || respCode == 204) {
							_logger.info("Success - send Refocus sample '{}'.", refocusSample.toJSON());
							break;
						} else if (respCode == 401) {
							// Indication that the session timedout, Need to refresh and retry
							continue;
						} else {
							_logger.error("Failure - send Refocus sample '{}'. Response code '{}' response '{}'",
									refocusSample.toJSON(), respCode, post.getResponseBodyAsString());
							break;
						}
					} catch (Exception e) {
						_logger.error("Failure - send Refocus sample'{}'. Exception '{}'", refocusSample.toJSON(), e);
					} finally {
						if(post != null){
							post.releaseConnection();
						}
					}
				}
			} catch (RuntimeException ex) {
				throw new SystemException("Failed to send an GOC++ notification.", ex);
			}
		} else {
			_logger.info("Sending Refocus notification is disabled.  Not sending message for aspect '{}'.", aspectPath);
		}
	}

	@Override
	public String getName() {
		return RefocusNotifier.class.getName();
	}

	@Override
	public Properties getNotifierProperties() {
		Properties notifierProps= super.getNotifierProperties();

		for(Property property: Property.values()){
			notifierProps.put(property.getName(), property.getDefaultValue());
		}
		return notifierProps;
	}

	/**
	 * Enumerates implementation specific configuration properties.
	 *
	 * @author  Janine Zou (yzou@salesforce.com)
	 */
	public enum Property {

		/** The Refocus endpoint. */
		REFOCUS_ENDPOINT("notifier.property.refocus.endpoint", "https://test.refocus.com"),
		/** The Refocus access token. */
		// REFOCUS_TOKEN("notifier.property.refocus.token", "test-token"),
		/** The Refocus proxy host. */
		REFOCUS_PROXY_HOST("notifier.property.proxy.host", ""),
		/** The Refocus port. */
		REFOCUS_PROXY_PORT("notifier.property.proxy.port", ""),
		/** The Refocus connection refresh max times. */
		REFOCUS_CONNECTION_MAX_TIMES("notifier.property.refocus.refreshMaxTimes", "0");

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

	/**
	 * RefocusSample object to generate JSON.
	 *
	 * @author  Janine Zou (yzou@salesforce.com)
	 */
	public class RefocusSample {

		public static final String ASPECT_NAME_FIELD = "name";
		public static final String ASPECT_VALUE_FIELD = "value";
		private final String name;
		private final int value; // Number(1, 0) 1 means fired while 0 means not fired

		private RefocusSample(final String name, final int value) {
			this.name = name;
			this.value = value;
		}

		/**
		 * Convert data to a JSON string.
		 *
		 * @return  JSON string
		 */
		public String toJSON() {
			JsonObject sampleData = new JsonObject();

			sampleData.addProperty(ASPECT_NAME_FIELD, name);
			sampleData.addProperty(ASPECT_VALUE_FIELD, value);
			return sampleData.toString();
		}

	}

	/**
	 * Manage Refocus connection, proxy and timeouts.
	 *
	 * @author  Janine Zou (yzou@salesforce.com)
	 */
	public static class RefocusTransport {

		private static final int CONNECTION_TIMEOUT_MILLIS = 10000;
		private static final int READ_TIMEOUT_MILLIS = 10000;
		private final MultiThreadedHttpConnectionManager theConnectionManager;
		{
			theConnectionManager = new MultiThreadedHttpConnectionManager();

			HttpConnectionManagerParams params = theConnectionManager.getParams();

			params.setConnectionTimeout(CONNECTION_TIMEOUT_MILLIS);
			params.setSoTimeout(READ_TIMEOUT_MILLIS);
		}

		// make the class singleton
		private RefocusTransport() {

		}

		public static RefocusTransport getInstance() {
			return RefocusTransportHolder.INSTANCE;
		}

		private static class RefocusTransportHolder {
			private final static RefocusTransport INSTANCE = new RefocusTransport();
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

			httpclient.getParams().setParameter("http.connection-manager.timeout", 2000L); // Wait for 2 seconds to get a connection from pool

			String host = config.getValue(Property.REFOCUS_PROXY_HOST.getName(), Property.REFOCUS_PROXY_HOST.getDefaultValue());

			if (host != null && host.length() > 0) {
				httpclient.getHostConfiguration().setProxy(host,
						Integer.parseInt(config.getValue(Property.REFOCUS_PROXY_PORT.getName(), Property.REFOCUS_PROXY_PORT.getDefaultValue())));
			}
			return httpclient;
		}

	}


}

/*
 * Copyright (C) 2016 Kiwigrid GmbH (oss@kiwigrid.com)
 *
 * Licensed under the  Creative Commons - Attribution-NoDerivatives License, Version 4.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *         https://creativecommons.org/licenses/by-nd/4.0/legalcode
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package com.salesforce.dva.argus.service.callback;

import java.io.StringReader;
import java.sql.Date;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.inject.Inject;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.service.CallbackService;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService;
import com.salesforce.dva.argus.service.alert.notifier.AuditNotifier;
import com.salesforce.dva.argus.service.alert.notifier.CallbackNotifier;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.util.AlertUtils;
import com.salesforce.dva.argus.util.TemplateReplacer;
import com.salesforce.sds.keystore.DynamicKeyStore;
import com.salesforce.sds.keystore.DynamicKeyStoreBuilder;
import com.salesforce.sds.pki.utils.BouncyIntegration;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHttpResponse;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.json.Json;
import javax.json.JsonBuilderFactory;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.stream.JsonGenerator;

/**
 * Default {@link CallbackService} implementation sending the request via a shared apache HttpClient
 *
 * @author svenkrause
 */
public class DefaultCallbackService extends DefaultService implements CallbackService {

	static {
		BouncyIntegration.init();
	}

	//~ Instance fields ******************************************************************************************************************************

	private final Logger _logger = LoggerFactory.getLogger(DefaultCallbackService.class);
	private final HttpClientPool httpClientPool;
	private DynamicKeyStore ks;

	//~ Constructors *********************************************************************************************************************************

	/**
	 * Creates a new DefaultCallbackService object.
	 *
	 * @param config The system configuration. Cannot be null.
	 */
	@Inject
	public DefaultCallbackService(SystemConfiguration config) {
		super(config);

		int poolSize = Property.POOL_SIZE.getInt(config);
		TimeUnit timeUnit = Property.POOL_REFRESH_UNIT.getEnum(config, TimeUnit.class);
		int refresh = Property.POOL_REFRESH_TIME.getInt(config);

		httpClientPool = new HttpClientPool(poolSize, refresh, timeUnit);

		try {
			ks = new DynamicKeyStoreBuilder().
					withMonitoredDirectory(config.getValue(SystemConfiguration.Property.PKI_MONITORED_DIRECTORY)).
					withCADirectory(config.getValue(SystemConfiguration.Property.PKI_CA_DIRECTORY)).
					withStartThread(true).build();
			_logger.info("DynamicKeyStore initialized successfully in DefaultCallbackService");
		} catch (Exception e) {
			_logger.error("Exception initializing DynamicKeyStore. Callback notifier will NOT support HTTPS. Exception: {}", e.getMessage());
			ks = null;
		}
	}

	@Override
	public HttpResponse sendNotification(DefaultAlertService.NotificationContext context, CallbackNotifier notifier) {

		Notification notification = context.getNotification();
		String subscription = notification.getSubscriptions().stream().collect(Collectors.joining());

		String notificationMessage = null;
		CallbackRequest request = new CallbackRequest();

		request.setUri(subscription);
		request.setMethod(Method.POST);

		String payload = createPayload(context, notifier);
		request.setBody(payload);

		Map<String, String> header = new HashMap<>();
		header.put("Content-Type", "application/json");
		request.setHeader(header);

		notificationMessage = MessageFormat.format("Callback via Url {0} Method {1} Body {2}",
				request.getUri(), request.getMethod().name(), payload);

		try {
			HttpResponse response = sendNotification(buildRequest(request), notificationMessage);
			return response;
		} catch (Exception e) {
			return errorResponse(notificationMessage + " failed. ", e);
		}
	}

	protected String createPayload(DefaultAlertService.NotificationContext context,
								   CallbackNotifier notifier) {

		Notification notification = context.getNotification();
		Trigger trigger = context.getTrigger();
		Alert alert = context.getAlert();

		Map<String, Object> config = new HashMap<>();
		config.put(JsonGenerator.PRETTY_PRINTING, true);

		JsonBuilderFactory factory = Json.createBuilderFactory(config);

		JsonObjectBuilder alertBuilder = factory.createObjectBuilder();
		JsonObjectBuilder triggerBuilder = factory.createObjectBuilder();
		JsonObjectBuilder notificationBuilder = factory.createObjectBuilder();

		alertBuilder.add("name", TemplateReplacer.applyTemplateChanges(context, alert.getName()));
		alertBuilder.add("alertUrl", notifier.getAlertUrl(alert.getId()));
		alertBuilder.add("firedAt", context.getTriggerFiredTime());

		notificationBuilder.add("name", TemplateReplacer.applyTemplateChanges(context, notification.getName()));
		notificationBuilder.add("status", "Triggered");
		notificationBuilder.add("CoolDownUntil", AuditNotifier.DATE_FORMATTER.get().format(new Date(context.getCoolDownExpiration())));

		triggerBuilder.add("name", TemplateReplacer.applyTemplateChanges(context, trigger.getName()));

		Trigger.TriggerType triggerType = trigger.getType();

		triggerBuilder.add("type", triggerType.toString());

		if (triggerType == Trigger.TriggerType.BETWEEN || triggerType == Trigger.TriggerType.NOT_BETWEEN) {
			triggerBuilder.add("primaryThreshold", trigger.getThreshold());
			triggerBuilder.add("secondaryThreshold", trigger.getSecondaryThreshold());
		} else {
			triggerBuilder.add("threshold", trigger.getThreshold());
		}

		triggerBuilder.add("inertia", trigger.getInertia());

		if(!trigger.getType().equals(Trigger.TriggerType.NO_DATA)) {
			triggerBuilder.add("triggeredValue", context.getTriggerEventValue());
		}

		if(context.getTriggeredMetric() != null) {
			triggerBuilder.add("triggeredMetric", context.getTriggeredMetric().getIdentifier());
		}

		JsonObjectBuilder rootBuilder = factory.createObjectBuilder();

		String customText = context.getNotification().getCustomText();

		if( customText != null && customText.length() > 0){
			customText = TemplateReplacer.applyTemplateChanges(context, customText);

			JsonReader reader = null;

			try  {
				reader = Json.createReader(new StringReader(customText));
				JsonObject customJson = reader.readObject();
				rootBuilder.add("custom", customJson);
			} finally {
				if(reader != null) {
					reader.close();
				}
			}
		}

		if (context.getEvaluatedMetricSnapshotURL().isPresent()) {
			alertBuilder.add("evaluatedMetricSnapshotUrl", context.getEvaluatedMetricSnapshotURL().get());
		} else {
			String expression = AlertUtils.getExpressionWithAbsoluteStartAndEndTimeStamps(context);

			if(!expression.equals("")) {
				alertBuilder.add("evaluatedMetricUrl", notifier.getExpressionUrl(expression));
			}
		}

		alertBuilder.add("evaluatedMetric", alert.getExpression());

		rootBuilder.add("alert", alertBuilder)
				.add("notification", notificationBuilder)
				.add("trigger", triggerBuilder);

		return rootBuilder.build().toString();
	}

	@Override
	public void dispose() {
		httpClientPool.shutdown();
		super.dispose();
	}

	private HttpUriRequest buildRequest(CallbackService.CallbackRequest request) {
		RequestBuilder builder = RequestBuilder
				.create(request.getMethod().name())
				.setUri(request.getUri())
				.setEntity(getBody(request));
		request.getHeader().forEach((k, v) -> builder.addHeader(k, v));

		return builder.build();
	}

	private HttpEntity getBody(CallbackService.CallbackRequest request) {
		String body = request.getBody();

		if (body != null) {
			StringEntity entity;

			if (request.getHeader().containsKey(HttpHeaders.CONTENT_TYPE)) {
				entity = new StringEntity(body, ContentType.parse(request.getHeader().get(HttpHeaders.CONTENT_TYPE)));
			} else {
				entity = new StringEntity(body, ContentType.TEXT_PLAIN);
			}
			return entity;
		}
		return null;
	}

	private HttpResponse sendNotification(HttpUriRequest request, String notificationMessage) {

		try {
			HttpClient httpClient;
			if (request.getURI().getScheme().equals("https")) {
				httpClient = HttpClients.custom().setSSLContext(ks.getSSLContext()).build();
			} else {
				httpClient = httpClientPool.borrowObject();
			}
			HttpResponse response = httpClient.execute(request);
			EntityUtils.consume(response.getEntity());
			return response;
		} catch (Throwable t) {
			return errorResponse(notificationMessage + " failed. ", t);
		}
	}

	private static HttpResponse errorResponse(String reason, Throwable t) {
		return new BasicHttpResponse(
				new ProtocolVersion("HTTP", 1, 1),
				500,
				String.format("%s: %s", reason, t.getMessage()));
	}


	public enum Property {

		POOL_SIZE("service.callback.pool.size", "10"),
		POOL_REFRESH_TIME("service.callback.pool.refresh_time", "1"),
		POOL_REFRESH_UNIT("service.callback.pool.refresh_unit", TimeUnit.SECONDS.name());

		private final String _name;
		private final String _defaultValue;

		private Property(String name, String defaultValue) {
			_name = name;
			_defaultValue = defaultValue;
		}

		public String getName() {
			return _name;
		}

		public String getDefaultValue() {
			return _defaultValue;
		}

		public int getInt(SystemConfiguration configuration) {
			String value = configuration.getValue(getName(), getDefaultValue());
			return Integer.parseInt(value);
		}

		public <T extends Enum<T>> T getEnum(SystemConfiguration configuration, Class<T> enumClass) {
			return Enum.valueOf(enumClass, configuration.getValue(getName(), getDefaultValue()));
		}

		public String getString(SystemConfiguration configuration) {
			return configuration.getValue(getName(), getDefaultValue());
		}

		public char getChar(SystemConfiguration configuration) {
			String s = configuration.getValue(getName(), getDefaultValue());
			return s.charAt(0);
		}
	}
}

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

import java.io.IOException;
import java.text.MessageFormat;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.CallbackService;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.util.TemplateReplacer;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpResponse;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.message.BasicHttpResponse;
import org.slf4j.Logger;

/**
 * Default {@link CallbackService} implementation sending the request via a shared apache HttpClient
 *
 * @author svenkrause
 */
public class DefaultCallbackService extends DefaultService implements CallbackService {
	//~ Instance fields ******************************************************************************************************************************

	@SLF4JTypeListener.InjectLogger
	private Logger _logger;
	private final HttpClientPool httpClientPool;
	private final ObjectMapper _mapper;

	//~ Constructors *********************************************************************************************************************************

	/**
	 * Creates a new DefaultCallbackService object.
	 *
	 * @param config The system configuration. Cannot be null.
	 */
	@Inject
	public DefaultCallbackService(SystemConfiguration config) {
		super(config);
		_mapper = new ObjectMapper();

		int poolSize = Property.POOL_SIZE.getInt(config);
		TimeUnit timeUnit = Property.POOL_REFRESH_UNIT.getEnum(config, TimeUnit.class);
		int refresh = Property.POOL_REFRESH_TIME.getInt(config);

		httpClientPool = new HttpClientPool(poolSize, refresh, timeUnit);
	}

	@Override
	public HttpResponse sendNotification(DefaultAlertService.NotificationContext context) {
		String subscription = context.getNotification().getSubscriptions().stream().collect(Collectors.joining());

		String notificationMessage = null;
		Request request = null;

		try {
			request = _mapper.readValue(subscription, Request.class);
		} catch (Exception e) {
			return errorResponse(subscription + " cannot be parsed. ", e);
		}

		notificationMessage = MessageFormat.format("Callback via Url {0} Method {1} Body {2}",
				request.getUri(), request.getMethod().name(), getResolvedBody(context, request));

		try {
			HttpResponse response = sendNotification(buildRequest(context, request), notificationMessage);
			return response;
		} catch (Exception e) {
			return errorResponse(notificationMessage + " failed. ", e);
		}
	}

	@Override
	public void dispose() {
		httpClientPool.shutdown();
		super.dispose();
	}

	private HttpUriRequest buildRequest(DefaultAlertService.NotificationContext context,
			CallbackService.Request request) {
		RequestBuilder builder = RequestBuilder
				.create(request.getMethod().name())
				.setUri(request.getUri())
				.setEntity(getBody(context, request));
		request.getHeader().forEach((k, v) -> builder.addHeader(k, v));

		return builder.build();
	}

	private String getResolvedBody(DefaultAlertService.NotificationContext context,
							   CallbackService.Request request) {

		if (request.getBody() != null) {
			String body = request.getBody();
			body = TemplateReplacer.applyTemplateChanges(context, body);
			return body;
		}
		return null;
	}

		private HttpEntity getBody(DefaultAlertService.NotificationContext context,
			CallbackService.Request request)
	{
		String body = getResolvedBody(context, request);

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

		HttpClient httpClient = httpClientPool.borrowObject();
		try {
			return httpClient.execute(request);
		} catch (Throwable t) {
			return errorResponse(notificationMessage + " failed. ", t);
		} finally {
			httpClientPool.returnObject(httpClient);
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

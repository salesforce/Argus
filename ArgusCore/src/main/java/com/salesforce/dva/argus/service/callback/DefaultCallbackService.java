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
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.CallbackService;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService;
import com.salesforce.dva.argus.system.SystemConfiguration;
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
import org.stringtemplate.v4.ST;

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
	private final char delimiterStart;
	private final char delimiterEnd;

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

		delimiterStart = Property.ST4_DELIMITER_START.getChar(config);
		delimiterEnd = Property.ST4_DELIMITER_END.getChar(config);
	}

	@Override
	public HttpResponse sendNotification(DefaultAlertService.NotificationContext context) {
		String subscription = context.getNotification().getSubscriptions().stream().collect(Collectors.joining());
		try {
			Request request = _mapper.readValue(subscription, Request.class);
			return sendNotification(buildRequest(context, request));
		} catch (IOException e) {
			return errorResponse("illegal subscription format", e);
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
				.create(request.method().name())
				.setUri(request.uri())
				.setEntity(body(context, request));
		request.header().forEach((k, v) -> builder.addHeader(k, v));
		return builder.build();
	}

	private HttpEntity body(DefaultAlertService.NotificationContext context,
			CallbackService.Request request)
	{
		if (request.body() != null) {
			StringEntity entity;
			String body = request.body();
			if (request.template() == Template.ST4) {
				ST st = new ST(request.body(), delimiterStart, delimiterEnd);
				st.add("alert", context.getAlert());
				st.add("trigger", context.getTrigger());
				st.add("coolDownExpiration", context.getCoolDownExpiration());
				st.add("notification", context.getNotification());
				st.add("triggerFiredTime", context.getTriggerFiredTime());
				st.add("triggerEventValue", context.getTriggerEventValue());
				st.add("triggeredMetric", context.getTriggeredMetric());
				body = st.render();
			}
			if (request.header().containsKey(HttpHeaders.CONTENT_TYPE)) {
				entity = new StringEntity(body, ContentType.parse(request.header().get(HttpHeaders.CONTENT_TYPE)));
			} else {
				entity = new StringEntity(body, ContentType.TEXT_PLAIN);
			}
			return entity;
		}
		return null;
	}

	private HttpResponse sendNotification(HttpUriRequest request) {

		_logger.debug(request.toString());
		HttpClient httpClient = httpClientPool.borrowObject();
		try {
			return httpClient.execute(request);
		} catch (Throwable t) {
			return errorResponse("error executing request " + request.toString(), t);
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
		POOL_REFRESH_UNIT("service.callback.pool.refresh_unit", TimeUnit.SECONDS.name()),
		ST4_DELIMITER_START("service.callback.ST4.del_start", "«"),
		ST4_DELIMITER_END("service.callback.ST4.del_end", "»");

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

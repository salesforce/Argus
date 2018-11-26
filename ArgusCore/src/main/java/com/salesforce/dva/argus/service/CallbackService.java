package com.salesforce.dva.argus.service;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.salesforce.dva.argus.service.alert.DefaultAlertService;
import com.salesforce.dva.argus.service.alert.notifier.CallbackNotifier;
import org.apache.http.HttpResponse;

/**
 * Send notifications to a HTTP endpoint.
 *
 * @author svenkrause, Naveen Reddy Karri (nkarri@salesforce.com)
 */
public interface CallbackService {

	HttpResponse sendNotification(DefaultAlertService.NotificationContext context, CallbackNotifier notifier);

	class CallbackRequest {
		@JsonProperty(required = true)
		private String uri;

		@JsonProperty(required = true)
		private Method method;

		@JsonProperty
		private String body;

		@JsonProperty
		private Map<String, String> header = new LinkedHashMap<>();

		public Method getMethod() {
			return method;
		}

		public CallbackRequest setMethod(Method method) {
			this.method = method;
			return this;
		}

		public String getUri() {
			return uri;
		}

		public CallbackRequest setUri(String uri) {
			this.uri = uri;
			return this;
		}

		public String getBody() {
			return body;
		}

		public CallbackRequest setBody(String body) {
			this.body = body;
			return this;
		}

		public Map<String, String> getHeader() {
			return header;
		}

		public CallbackRequest setHeader(Map<String, String> header) {
			this.header = header;
			return this;
		}
	}

	enum Method {
		POST,
		PUT,
		GET,
		DELETE
	}
}

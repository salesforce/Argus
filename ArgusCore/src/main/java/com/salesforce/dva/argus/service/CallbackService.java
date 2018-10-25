package com.salesforce.dva.argus.service;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.salesforce.dva.argus.service.alert.DefaultAlertService;
import org.apache.http.HttpResponse;

/**
 * Send notifications to a HTTP endpoint.
 *
 * @author svenkrause, Naveen Reddy Karri (nkarri@salesforce.com)
 */
public interface CallbackService {

	HttpResponse sendNotification(DefaultAlertService.NotificationContext context);

	class Request {
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

		public Request setMethod(Method method) {
			this.method = method;
			return this;
		}

		public String getUri() {
			return uri;
		}

		public Request setUri(String uri) {
			this.uri = uri;
			return this;
		}

		public String getBody() {
			return body;
		}

		public Request setBody(String body) {
			this.body = body;
			return this;
		}

		public Map<String, String> getHeader() {
			return header;
		}

		public Request setHeader(Map<String, String> header) {
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

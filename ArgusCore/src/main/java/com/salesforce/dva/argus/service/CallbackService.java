package com.salesforce.dva.argus.service;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.salesforce.dva.argus.service.alert.DefaultAlertService;
import com.salesforce.dva.argus.service.alert.notifier.CallbackNotifier;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpUriRequest;

/**
 * ToDo: insert javadoc
 *
 * @author svenkrause
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
		private Template template;
		@JsonProperty
		private Map<String, String> header = new LinkedHashMap<>();

		public Method method() {
			return method;
		}

		public Request method(Method method) {
			this.method = method;
			return this;
		}

		public String uri() {
			return uri;
		}

		public Request uri(String uri) {
			this.uri = uri;
			return this;
		}

		public String body() {
			return body;
		}

		public Request body(String body) {
			this.body = body;
			return this;
		}

		public Map<String, String> header() {
			return header;
		}

		public Request header(Map<String, String> header) {
			this.header = header;
			return this;
		}

		public Template template() {
			return template;
		}

		public Request template(Template template) {
			this.template = template;
			return this;
		}
	}

	enum Method {
		POST,
		PUT,
		GET,
		DELETE
	}

	enum Template {
		ST4
	}
}

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
package com.salesforce.dva.argus.service.alert.notifier;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.AnnotationService;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.CallbackService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import javax.persistence.EntityManager;
import org.apache.http.HttpResponse;
import org.slf4j.Logger;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Callback notifier sending the event via REST client to an endpoint defined within the notification subscription.
 *
 * @author svenkrause
 */
public class CallbackNotifier extends AuditNotifier {

	@SLF4JTypeListener.InjectLogger
	private Logger _logger;

	private final CallbackService _callbackService;
	/**
	 * Creates a new CallbackNotifier object.
	 *
	 * @param metricService The metric service. Cannot be null.
	 * @param annotationService The annotation service. Cannot be null.
	 * @param auditService The audit service. Cannot be null.
	 * @param callbackService   The callback service. Cannot be null.
	 * @param config The system configuration. Cannot be null.
	 * @param emf The entity manager factory. Cannot be null.
	 */
	@Inject
	public CallbackNotifier(MetricService metricService, AnnotationService annotationService, AuditService
			auditService, CallbackService callbackService, SystemConfiguration config, Provider<EntityManager> emf)
	{
		super(metricService, annotationService, auditService, config, emf);
		requireArgument(callbackService != null, "callback service cannot be null.");
		requireArgument(config != null, "The configuration cannot be null.");
		_callbackService = callbackService;
	}

	//~ Methods **************************************************************************************************************************************

	@Override
	public String getName() {
		return CallbackNotifier.class.getName();
	}

	@Override
	protected void sendAdditionalNotification(DefaultAlertService.NotificationContext context) {
		requireArgument(context != null, "Notification context cannot be null.");
		super.sendAdditionalNotification(context);
		HttpResponse response = _callbackService.sendNotification(context);
		int code = response.getStatusLine().getStatusCode();
		if (!(code >= 200 && code <= 300)) {
			_logger.error("notification send response: {}", response.toString());
		}
	}
}

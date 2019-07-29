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

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.service.AnnotationService;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.RefocusService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.NotificationContext;
import com.salesforce.dva.argus.system.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import java.util.List;
import java.util.Properties;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Implementation of notifier interface for notifying Refocus.
 *
 * @author  Janine Zou (yzou@salesforce.com), Ian Keck (ikeck@salesforce.com)
 */
public class RefocusBooleanNotifier extends AuditNotifier {

	private Logger _logger = LoggerFactory.getLogger(RefocusBooleanNotifier.class);
	private RefocusService _refocusService;


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
	public RefocusBooleanNotifier(MetricService metricService, AnnotationService annotationService, AuditService auditService,
								  RefocusService refocusService, SystemConfiguration config, Provider<EntityManager> emf) {
		super(metricService, annotationService, auditService, config, emf);
		requireArgument(config != null, "The configuration cannot be null.");
		this._refocusService = refocusService;
	}

	@Override
	protected boolean sendAdditionalNotification(NotificationContext context) {
		return _sendRefocusNotification(context, true);
	}

	@Override
	protected boolean clearAdditionalNotification(NotificationContext context) {
		return _sendRefocusNotification(context, false);
	}

	// Future - TODO - userID from principal owner of alert, token_id from user preference.
	private boolean _sendRefocusNotification(NotificationContext context, boolean isTriggerActive) {
		requireArgument(context != null, "Notification context cannot be null.");
		List<String> aspectPaths = context.getNotification().getSubscriptions();

		requireArgument(aspectPaths!=null && !aspectPaths.isEmpty(), "aspect paths (subscriptions) cannot be empty.");

		if(isTriggerActive) {
			super.sendAdditionalNotification(context);
		} else {
			super.clearAdditionalNotification(context);
		}

		// IMPORTANT - should interrupted exception be handled?
		boolean result = true;
		History history = context.getHistory();
		for (String aspect : aspectPaths) {
			boolean tmp = _refocusService.sendRefocusNotification(aspect,
					isTriggerActive ? "1" : "0",
					"user_id",
					"token_id",
					history);
			result = result && tmp;
		}

		return result;
	}


	@Override
	public String getName() {
		return RefocusBooleanNotifier.class.getName();
	}

	@Override
	public Properties getNotifierProperties() {
		Properties notifierProps= super.getNotifierProperties();

		for(Property property: Property.values()){
			notifierProps.put(property.getName(), property.getDefaultValue());
		}

		return notifierProps;
	}

}

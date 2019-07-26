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
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.service.AnnotationService;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.RefocusService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.NotificationContext;
import com.salesforce.dva.argus.system.SystemConfiguration;

import com.salesforce.dva.argus.util.AlertUtils;
import com.salesforce.dva.argus.util.TemplateReplacer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import java.sql.Date;
import java.text.MessageFormat;
import java.util.List;
import java.util.Properties;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Implementation of notifier interface for notifying Refocus.
 *
 * @author  Ian Keck (ikeck@salesforce.com)
 */
public class RefocusValueNotifier extends AuditNotifier {

	private static final Logger _logger = LoggerFactory.getLogger(RefocusValueNotifier.class);

	private RefocusService _refocusService;

	/**
	 * Creates a new Refocus notifier.
	 *
	 * @param  metricService      The metric service. Cannot be null.
	 * @param  annotationService  The annotation service. Cannot be null.
	 * @param  auditService       The audit service. Cannot be null.
	 * @param  refocusService     The refocus service. Cannot be null.
	 * @param  config             The system configuration. Cannot be null.
	 * @param  emf                The entity manager factory. Cannot be null.
	 */
	@Inject
	public RefocusValueNotifier(MetricService metricService,
								AnnotationService annotationService,
								AuditService auditService,
                                RefocusService refocusService,
                           		SystemConfiguration config,
								Provider<EntityManager> emf) {
		super(metricService, annotationService, auditService, config, emf);
		requireArgument(config != null, "The configuration cannot be null.");
		this._refocusService = refocusService;
	}

	@Override
	protected boolean sendAdditionalNotification(NotificationContext context) {

		_logger.info("In RefocusValueNotifier::sendAdditionalNotification()"); // IMPORTANT - DEBUG
		double value = context.getTriggerEventValue();
		boolean rv = _sendRefocusNotification(context, _valueToString(context.getTriggerEventValue()));
		_logger.info("Returning from RefocusValueNotifier::sendAdditionalNotification()"); // IMPORTANT - DEBUG
		return rv;
	}

	/**
	 * Refocus Value Notifiers are not Stateful.  This method implementation is empty.
	 *
	 * @param notificationContext The notification context.
	 */
	@Override
	public boolean clearNotification(NotificationContext notificationContext) {  return true; }

	@Override
	protected boolean clearAdditionalNotification(NotificationContext context) { return true; }

	private String _valueToString(double value)
	{
		// IMPORTANT - TODO - verify whether Refocus takes double precision values or just ints
		return String.format("%f", value); // alternatively for int String.format("%ld", Math.round(value));
	}

	// TODO - userID from principal owner of alert, token_id from user preference.
	private boolean _sendRefocusNotification(NotificationContext context, String value) {
		requireArgument(context != null, "Notification context cannot be null.");
		List<String> aspectPaths = context.getNotification().getSubscriptions();

		requireArgument(aspectPaths!=null && !aspectPaths.isEmpty(), "aspect paths (subscriptions) cannot be empty.");
		super.sendAdditionalNotification(context);

		boolean result = true;
		History history = context.getHistory();
		for (String aspect : aspectPaths) {
			// IMPORTANT - TODO - should interrupted exception be handled?
            _logger.info(MessageFormat.format("In RefocusValueNotifier::sendAdditionalNotification() sending {0} to aspect {1}", value, aspect)); // IMPORTANT - DEBUG
            boolean tmp = _refocusService.sendRefocusNotification(aspect,
													value,
													"user_id",
													"token_id",
													history);
			result = result && tmp;
		}

        return result;
	}

    /**
     * Returns the audit entry body containing the alert information.
     * The audit entry is different for RefocusValueNotifications.
     *
     * @param   context             The notification context.
     * @param   notificationStatus  The source notification.
     *
     * @return  The audit entry body to persist.
     */
    @Override
    protected String getAuditBody(NotificationContext context, NotificationStatus notificationStatus) {
        String notificationMessage = MessageFormat.format("<b>Alert {0} was sent to Refocus at {1}</b><br/>", TemplateReplacer.applyTemplateChanges(context, context.getAlert().getName()),
                DATE_FORMATTER.get().format(new Date(context.getTriggerFiredTime())));
        Notification notification = null;
        String expression = AlertUtils.getExpressionWithAbsoluteStartAndEndTimeStamps(context);

        for (Notification tempNotification : context.getAlert().getNotifications()) {
            if (tempNotification.getName().equalsIgnoreCase(context.getNotification().getName())) {
                notification = tempNotification;
                break;
            }
        }

        // IMPORTANT - review this with Sudhanshu
        requireArgument(notification != null, "Notification in notification context cannot be null.");

        StringBuilder sb = new StringBuilder();

        sb.append(notificationMessage);
        String customText = context.getNotification().getCustomText();
        if( customText != null && customText.length()>0){
            sb.append(TemplateReplacer.applyTemplateChanges(context, customText)).append("<br/>");
        }
        sb.append(MessageFormat.format("<b>Notification:  </b> {0}<br/>", TemplateReplacer.applyTemplateChanges(context,notification.getName())));

        if (!expression.equals("")) sb.append(MessageFormat.format("<b>Evaluated metric expression:  </b> {0}<br/>", expression));
        else sb.append(MessageFormat.format("<b>Evaluated metric expression:  </b> {0}<br/>", context.getAlert().getExpression()));

        if (context.getEvaluatedMetricSnapshotURL().isPresent() && !context.getEvaluatedMetricSnapshotURL().get().equals("")) {
	        sb.append("<p><a href='").append(context.getEvaluatedMetricSnapshotURL().get()).append("'>Snapshot of the current view the evaluated metric data.</a><br/>");
        } else {
	        if(!expression.equals("")) {
		        sb.append("<p><a href='").append(getExpressionUrl(expression)).append("'>Click here to view the evaluated metric data.</a><br/>");
	        }
        }

        sb.append("<p><a href='").append(getExpressionUrl(context.getAlert().getExpression())).append("'>Click here for the current view of the metric data.</a><br/><br/>");
        sb.append("<p><small>Disclaimer:  This alert was evaluated using the time series data as it existed at the time of evaluation.  ");
        sb.append("If the data source has inherent lag or a large aggregation window is used during data collection, it is possible ");
        sb.append("for the time series data to be updated such that the alert condition is no longer met.  This may be avoided by ");
        sb.append("ensuring the time window used in alert expression is outside the range of the datasource lag.</small>");
        return sb.toString();
    }

    @Override
	public String getName() {
		return RefocusValueNotifier.class.getName();
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

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
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.service.AnnotationService;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.MailService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.NotificationContext;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import java.sql.Date;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Set;
import javax.persistence.EntityManager;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Implementation of notifier interface for notifying via email.
 *
 * @author  Raj Sarkapally (rsarkapally@salesforce.com)
 */
public class EmailNotifier extends AuditNotifier {

    //~ Instance fields ******************************************************************************************************************************

    private final MailService _mailService;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new email notifier.
     *
     * @param  metricService      The metric service. Cannot be null.
     * @param  annotationService  The annotation service. Cannot be null.
     * @param  auditService       The audit service. Cannot be null.
     * @param  mailService        The mail service. Cannot be null.
     * @param  config             The system configuration. Cannot be null.
     * @param  emf                The entity manager factory. Cannot be null.
     */
    @Inject
    public EmailNotifier(MetricService metricService, AnnotationService annotationService, AuditService auditService, MailService mailService,
        SystemConfiguration config, Provider<EntityManager> emf) {
        super(metricService, annotationService, auditService, config, emf);
        requireArgument(mailService != null, "Mail service cannot be null.");
        requireArgument(config != null, "The configuration cannot be null.");
        _mailService = mailService;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public String getName() {
        return EmailNotifier.class.getName();
    }

    @Override
    protected void sendAdditionalNotification(NotificationContext context) {
        requireArgument(context != null, "Notification context cannot be null.");
        super.sendAdditionalNotification(context);

        String subject = getEmailSubject(context);
        String body = getEmailBody(context, NotificationStatus.TRIGGERED);
        Set<String> to = _getNotificationSubscriptions(context);

        _mailService.sendMessage(to, subject, body, "text/html; charset=utf-8", MailService.Priority.NORMAL);
    }

    private Set<String> _getNotificationSubscriptions(NotificationContext context) {
        Notification notification = null;

        for (Notification tempNotification : context.getAlert().getNotifications()) {
            if (tempNotification.getName().equalsIgnoreCase(context.getNotification().getName())) {
                notification = tempNotification;
                break;
            }
        }
        if (notification == null) {
            throw new SystemException("Notification in notification context cannot be null.");
        }
        return new HashSet<>(notification.getSubscriptions());
    }

    private String getEmailSubject(NotificationContext context) {
        return "[Argus] Alert Notification: " + context.getNotification().getName();
    }

    /**
     * Returns the email body content.
     *
     * @param   context             The notification context.
     * @param   notificationStatus  The source notification.
     *
     * @return  The email body.
     */
    protected String getEmailBody(NotificationContext context, NotificationStatus notificationStatus) {
        String notificationMessage = notificationStatus == NotificationStatus.TRIGGERED ? "Triggered" : "Cleared";
        Notification notification = null;
        Trigger trigger = null;

        for (Notification tempNotification : context.getAlert().getNotifications()) {
            if (tempNotification.getName().equalsIgnoreCase(context.getNotification().getName())) {
                notification = tempNotification;
                break;
            }
        }
        requireArgument(notification != null, "Notification in notification context cannot be null.");
        for (Trigger tempTrigger : context.getAlert().getTriggers()) {
            if (tempTrigger.getName().equalsIgnoreCase(context.getTrigger().getName())) {
                trigger = tempTrigger;
                break;
            }
        }
        requireArgument(trigger != null, "Trigger in notification context cannot be null.");

        StringBuilder sb = new StringBuilder();

        sb.append(MessageFormat.format("<h3>Alert {0}  was {1} at {2}</h3>", context.getAlert().getName(), notificationMessage,
                DATE_FORMATTER.get().format(new Date(context.getTriggerFiredTime()))));
        sb.append(MessageFormat.format("<b>Notification:  </b> {0}<br/>", notification.getName()));
        sb.append(MessageFormat.format("<b>Triggered by:  </b> {0}<br/>", trigger.getName()));
        sb.append(MessageFormat.format("<b>Notification is on cooldown until:  </b> {0}<br/>",
                DATE_FORMATTER.get().format(new Date(context.getCoolDownExpiration()))));
        sb.append(MessageFormat.format("<b>Evaluated metric expression:  </b> {0}<br/>", context.getAlert().getExpression()));
        sb.append(MessageFormat.format("<b>Trigger details: </b> {0}<br/>", getTriggerDetails(trigger)));
        sb.append(MessageFormat.format("<b>Triggering event value:  </b> {0}<br/>", context.getTriggerEventValue()));
        sb.append("<p>");
        for (String metricToAnnotate : notification.getMetricsToAnnotate()) {
            sb.append("<a href='");
            sb.append(getMetricUrl(metricToAnnotate, context.getTriggerFiredTime()));
            sb.append("'>Click here to view the annotated series for ").append(metricToAnnotate).append(".</a><br/>");
        }
        sb.append("<p><a href='").append(getAlertUrl(notification.getAlert().getId())).append("'>Click here to view alert definition.</a><br/>");
        sb.append("<p><small>Disclaimer:  This alert was evaluated using the time series data as it existed at the time of evaluation.  ");
        sb.append("If the data source has inherent lag or a large aggregation window is used during data collection, it is possible ");
        sb.append("for the time series data to be updated such that the alert condition is no longer met.  This may be avoided by ");
        sb.append("ensuring the time window used in alert expression is outside the range of the datasource lag.</small>");
        sb.append("<p><small>You received this notification because you, or a distribution list you belong to is listed as a ");
        sb.append("subscriber of the alert.</small>");
        return sb.toString();
    }

    @Override
    protected void clearAdditionalNotification(NotificationContext context) {
        requireArgument(context != null, "Notification context cannot be null.");
        super.clearAdditionalNotification(context);

        String subject = getEmailSubject(context);
        String body = getEmailBody(context, NotificationStatus.CLEARED);
        Set<String> to = _getNotificationSubscriptions(context);

        _mailService.sendMessage(to, subject, body, "text/html; charset=utf-8", MailService.Priority.NORMAL);
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

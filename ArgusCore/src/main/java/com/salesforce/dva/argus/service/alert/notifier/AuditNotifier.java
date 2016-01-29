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
import com.salesforce.dva.argus.entity.Audit;
import com.salesforce.dva.argus.entity.JPAEntity;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.service.AnnotationService;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.NotificationContext;
import com.salesforce.dva.argus.system.SystemConfiguration;
import org.joda.time.DateTimeConstants;
import java.math.BigInteger;
import java.net.URLEncoder;
import java.sql.Date;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;
import javax.persistence.EntityManager;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * A notifier that sends notification to a database.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class AuditNotifier extends DefaultNotifier {

    //~ Static fields/initializers *******************************************************************************************************************

    protected static final ThreadLocal<SimpleDateFormat> DATE_FORMATTER = new ThreadLocal<SimpleDateFormat>() {

            @Override
            protected SimpleDateFormat initialValue() {
                SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss z");

                sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
                return sdf;
            }
        };

    //~ Instance fields ******************************************************************************************************************************

    protected final SystemConfiguration _config;
    protected final AuditService _auditService;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new DBNotifier object.
     *
     * @param  metricService      The metric service. Cannot be null.
     * @param  annotationService  The annotation service. Cannot be null.
     * @param  auditService       The audit service. Cannot be null.
     * @param  config             The system configuration. Cannot be null.
     * @param  emf                The entity manager factory. Cannot be null.
     */
    @Inject
    public AuditNotifier(MetricService metricService, AnnotationService annotationService, AuditService auditService, SystemConfiguration config,
        Provider<EntityManager> emf) {
        super(metricService, annotationService, config);
        _auditService = auditService;
        _config = config;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public String getName() {
        return AuditNotifier.class.getName();
    }

    @Override
    protected void sendAdditionalNotification(NotificationContext context) {
        requireArgument(context != null, "Notification context cannot be null.");

        Audit audit = new Audit(getAuditBody(context, NotificationStatus.TRIGGERED), SystemConfiguration.getHostname(), context.getAlert());

        _auditService.createAudit(audit);
    }

    /**
     * Returns the audit entry body containing the alert information.
     *
     * @param   context             The notification context.
     * @param   notificationStatus  The source notification.
     *
     * @return  The audit entry body to persist.
     */
    protected String getAuditBody(NotificationContext context, NotificationStatus notificationStatus) {
        String notificationMessage = MessageFormat.format("<b>Alert {0} was {1} at {2}</b><br/>", context.getAlert().getName(),
            notificationStatus == NotificationStatus.TRIGGERED ? "Triggered" : "Cleared",
            DATE_FORMATTER.get().format(new Date(context.getTriggerFiredTime())));
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

        sb.append(notificationMessage);
        sb.append(MessageFormat.format("<b>Notification:  </b> {0}<br/>", notification.getName()));
        sb.append(MessageFormat.format("<b>Triggered by:  </b> {0}<br/>", trigger.getName()));
        sb.append(MessageFormat.format("<b>Notification is on cooldown until:  </b> {0}<br/>",
                DATE_FORMATTER.get().format(new Date(context.getCoolDownExpiration()))));
        sb.append(MessageFormat.format("<b>Evaluated metric expression:  </b> {0}<br/>", context.getAlert().getExpression()));
        sb.append(MessageFormat.format("<b>Trigger details: </b> {0}<br/>", getTriggerDetails(trigger)));
        sb.append(MessageFormat.format("<b>Triggering event value:  </b> {0}<br/>", context.getTriggerEventValue()));
        sb.append("<p><small>Disclaimer:  This alert was evaluated using the time series data as it existed at the time of evaluation.  ");
        sb.append("If the data source has inherent lag or a large aggregation window is used during data collection, it is possible ");
        sb.append("for the time series data to be updated such that the alert condition is no longer met.  This may be avoided by ");
        sb.append("ensuring the time window used in alert expression is outside the range of the datasource lag.</small>");
        return sb.toString();
    }

    /**
     * Returns the trigger detail information.
     *
     * @param   trigger  The source trigger.
     *
     * @return  The trigger detail information.
     */
    protected String getTriggerDetails(Trigger trigger) {
        if (trigger != null) {
            String triggerString = trigger.toString();

            return triggerString.substring(triggerString.indexOf("{") + 1, triggerString.indexOf("}"));
        } else {
            return "";
        }
    }

    /**
     * Returns the URL linking back to the metric for use in alert notification.
     *
     * @param   metricToAnnotate  The metric to annotate.
     * @param   triggerFiredTime  The epoch timestamp when the corresponding trigger fired.
     *
     * @return  The fully constructed URL for the metric.
     */
    @SuppressWarnings("deprecation")
    protected String getMetricUrl(String metricToAnnotate, long triggerFiredTime) {
        long start = triggerFiredTime - (6L * DateTimeConstants.MILLIS_PER_HOUR);
        long end = Math.min(System.currentTimeMillis(), triggerFiredTime + (6L * DateTimeConstants.MILLIS_PER_HOUR));
        String expression = MessageFormat.format("{0,number,#}:{1,number,#}:{2}", start, end, metricToAnnotate);
        String template = _config.getValue(Property.AUDIT_METRIC_URL_TEMPLATE.getName(), Property.AUDIT_METRIC_URL_TEMPLATE.getDefaultValue());

        try {
            expression = URLEncoder.encode(expression, "UTF-8");
        } catch (Exception ex) {
            expression = URLEncoder.encode(expression);
        }
        return template.replaceAll("\\$expression\\$", expression);
    }

    /**
     * Returns the URL linking back to the alert for which notification is being sent.
     *
     * @param   id  The ID of the alert.
     *
     * @return  The fully constructed URL for the alert.
     */
    protected String getAlertUrl(BigInteger id) {
        String template = _config.getValue(Property.AUDIT_ALERT_URL_TEMPLATE.getName(), Property.AUDIT_ALERT_URL_TEMPLATE.getDefaultValue());

        return template.replaceAll("\\$alertid\\$", String.valueOf(id));
    }

    /**
     * Returns all audit entries for a notification.
     *
     * @param   entity  The object to retrieve alert audits for.
     *
     * @return  A list of notification audits for the object.
     */
    public List<Audit> getAllNotifications(JPAEntity entity) {
        return _auditService.findByEntityHostnameMessage(entity.getId(), null, "Triggering event value:");
    }

    @Override
    protected void clearAdditionalNotification(NotificationContext context) {
        requireArgument(context != null, "Notification context cannot be null.");

        Audit audit = new Audit(getAuditBody(context, NotificationStatus.CLEARED), SystemConfiguration.getHostname(), context.getAlert());

        _auditService.createAudit(audit);
    }
    
    @Override
    public Properties getNotifierProperties() {
            Properties notifierProps= super.getNotifierProperties();

            for(Property property:Property.values()){
                    notifierProps.put(property.getName(), property.getDefaultValue());
            }
            return notifierProps;
    }
    
  //~ Enums ****************************************************************************************************************************************

    /**
     * The enumeration of implementation specific configuration properties.
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public enum Property {

        /** The alert URL template to use in notifications. */
        AUDIT_ALERT_URL_TEMPLATE("notifier.property.alert.alerturl.template", "http://localhost:8080/argus/alertId"),
        /** The metric URL template to use in notifications. */
        AUDIT_METRIC_URL_TEMPLATE("notifier.property.alert.metricurl.template", "http://localhost:8080/argus/metrics");
        
        private final String _name;
        private final String _defaultValue;

        private Property(String name, String defaultValue) {
            _name = name;
            _defaultValue = defaultValue;
        }

        /**
         * Returns the property name.
         *
         * @return  The property name.
         */
        public String getName() {
            return _name;
        }

        /**
         * Returns the default value.
         *
         * @return  The default value.
         */
        public String getDefaultValue() {
            return _defaultValue;
        }
    }
    
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

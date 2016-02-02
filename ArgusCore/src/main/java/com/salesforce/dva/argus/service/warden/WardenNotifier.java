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
	 
package com.salesforce.dva.argus.service.warden;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.entity.SuspensionRecord;
import com.salesforce.dva.argus.service.AnnotationService;
import com.salesforce.dva.argus.service.MailService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.MonitorService.Counter;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.WardenService;
import com.salesforce.dva.argus.service.WardenService.SubSystem;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.NotificationContext;
import com.salesforce.dva.argus.service.alert.notifier.DefaultNotifier;
import com.salesforce.dva.argus.system.SystemConfiguration;
import java.math.BigInteger;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;
import javax.persistence.EntityManager;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Default implementation of the warden alert notifier. This warden specific implementation must suspend the user associated with the warden alert for
 * the right subsystem, if the policy is violated as determined by the trigger events. Additionally, the notifier must create an annotation on the
 * specific time series specified in the alert expression which indicates the time the policy was violated.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public abstract class WardenNotifier extends DefaultNotifier {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final String ANNOTATION_SOURCE = "ARGUS-WARDEN";
    private static final String ANNOTATION_TYPE = "WARDEN";
    private static final String WARDEN_ALERT_NAME_PREFIX = "warden-";
    private static final ThreadLocal<SimpleDateFormat> DATE_FORMATTER = new ThreadLocal<SimpleDateFormat>() {

            @Override
            protected SimpleDateFormat initialValue() {
                SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss z");

                sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
                return sdf;
            }
        };

    //~ Instance fields ******************************************************************************************************************************

    @Inject
    private Provider<EntityManager> emf;
    protected final WardenService _wardenService;
    private final TSDBService _tsdbService;
    private final AnnotationService _annotationService;
    private final MailService _mailService;
    private final SystemConfiguration _config;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new WardenNotifier object.
     *
     * @param  metricService      The metric service. Cannot be null.
     * @param  annotationService  The annotation service. Cannot be null.
     * @param  wardenService      The warden service. Cannot be null.
     * @param  tsdbService        The tsdb service instance to use. Cannot be null.
     * @param  mailService        The mail service. Cannot be null.
     * @param  config             The system configuration. Cannot be null.
     */
    @Inject
    protected WardenNotifier(MetricService metricService, AnnotationService annotationService, WardenService wardenService, TSDBService tsdbService,
        MailService mailService, SystemConfiguration config) {
        super(metricService, annotationService, config);
        requireArgument(wardenService != null, "Warden service cannot be null.");
        requireArgument(tsdbService != null, "TSDB service cannot be null.");
        requireArgument(annotationService != null, "Annotation service cannot be null.");
        requireArgument(mailService != null, "Mail service cannot be null.");
        requireArgument(config != null, "The configuration cannot be null.");
        _wardenService = wardenService;
        _tsdbService = tsdbService;
        _annotationService = annotationService;
        _mailService = mailService;
        _config = config;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public String getName() {
        return WardenNotifier.class.getName();
    }

    @Override
    protected abstract void sendAdditionalNotification(NotificationContext context);

    /**
     * Add annotation for user suspension to the <tt>triggers.warden</tt> metric..
     *
     * @param  context    The notification context.  Cannot be null.
     * @param  subSystem  The subsystem for which the user is being suspended.  Cannot be null.
     */
    protected void addAnnotationSuspendedUser(NotificationContext context, SubSystem subSystem) {
        Alert alert = context.getAlert();
        PrincipalUser wardenUser = getWardenUser(alert.getName());
        Metric metric = null;

        Map<Long, String> datapoints = new HashMap<>();

        metric = new Metric(Counter.WARDEN_TRIGGERS.getScope(), Counter.WARDEN_TRIGGERS.getMetric());
        metric.setTag("user", wardenUser.getUserName());
        datapoints.put(context.getTriggerFiredTime(), "1");
        metric.setDatapoints(datapoints);
        _tsdbService.putMetrics(Arrays.asList(new Metric[] { metric }));

        Annotation annotation = new Annotation(ANNOTATION_SOURCE, wardenUser.getUserName(), ANNOTATION_TYPE, Counter.WARDEN_TRIGGERS.getScope(),
            Counter.WARDEN_TRIGGERS.getMetric(), context.getTriggerFiredTime());
        Map<String, String> fields = new TreeMap<>();

        fields.put("Suspended from subsystem", subSystem.toString());
        fields.put("Alert Name", alert.getName());
        fields.put("Notification Name", context.getNotification().getName());
        fields.put("Trigger Name", context.getTrigger().getName());
        annotation.setFields(fields);
        _annotationService.updateAnnotation(alert.getOwner(), annotation);
    }

    /**
     * Sends an email to user and admin with information on suspension and when user will be reinstated.
     *
     * @param  context    Notification context of the warden notifier
     * @param  subSystem  The sub system user has been suspended from
     */
    protected void sendWardenEmailToUser(NotificationContext context, SubSystem subSystem) {
        EntityManager em = emf.get();
        PrincipalUser user = getWardenUser(context.getAlert().getName());
        SuspensionRecord record = SuspensionRecord.findByUserAndSubsystem(em, user, subSystem);
        Set<String> to = new HashSet<>();

        to.add(user.getEmail());

        String subject = "Warden Email Notification";
        StringBuilder message = new StringBuilder();

        message.append(MessageFormat.format("<p>{0} has been suspended from the Argus system for violating the following policy</p>",
                user.getUserName()));
        message.append(MessageFormat.format("Subsystem: {0}", subSystem.toString()));
        message.append(MessageFormat.format("<br>Policy: {0}",
                context.getAlert().getName().replace(WARDEN_ALERT_NAME_PREFIX + user.getUserName() + "-", "")));
        message.append(MessageFormat.format("<br>Threshold: {0}", context.getAlert().getTriggers().get(0).getThreshold()));
        message.append(MessageFormat.format("<br>Triggering Value: {0}", context.getTriggerEventValue()));
        if (record.getSuspendedUntil() == -1) {
            message.append("<br> You have been suspended indefinitely");
        } else {
            message.append(MessageFormat.format("<br>Reinstatement Time: {0}", DATE_FORMATTER.get().format(new Date(record.getSuspendedUntil()))));
        }
        _mailService.sendMessage(to, subject, message.toString(), "text/html; charset=utf-8", MailService.Priority.HIGH);
        to.clear();
        to.add("argus-admin@salesforce.com");
        message.append("<p><a href='").append(getAlertUrl(context.getAlert().getId())).append("'>Click here to view alert definition.</a><br/>");
        _mailService.sendMessage(to, subject, message.toString(), "text/html; charset=utf-8", MailService.Priority.HIGH);
    }

    private String getAlertUrl(BigInteger id) {
        String template = _config.getValue(Property.WARDEN_ALERT_URL_TEMPLATE.getName(), Property.WARDEN_ALERT_URL_TEMPLATE.getDefaultValue());

        return template.replaceAll("\\$alertid\\$", String.valueOf(id));
    }

    /**
     * From warden alert name, de-constructs the user for whom this warden alert is associated with.
     *
     * @param   wardenAlertName  Name of warden alert
     *
     * @return  User associated with the warden alert
     */
    protected PrincipalUser getWardenUser(String wardenAlertName) {
        assert (wardenAlertName != null) : "Warden alert name cannot be null.";

        int beginIndex = wardenAlertName.indexOf("-") + 1;
        int endIndex = wardenAlertName.lastIndexOf("-");

        return PrincipalUser.findByUserName(emf.get(), wardenAlertName.substring(beginIndex, endIndex));
    }

    /** 
     * Warden triggers are not stateful.  This method implementation is empty.
     * 
     * @param notificationContext The notification context. 
     */
    @Override
    public void clearNotification(NotificationContext notificationContext) { }

    /** No additional action needs to be taken for clearing warden notifications as they are not stateful.  This implementation is empty. */
    @Override
    protected void clearAdditionalNotification(NotificationContext context) { }
    
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
        WARDEN_ALERT_URL_TEMPLATE("notifier.property.alert.alerturl.template", "http://localhost:8080/argus/alertId"),
        /** The metric URL template to use in notifications. */
        WARDEN_METRIC_URL_TEMPLATE("notifier.property.alert.metricurl.template", "http://localhost:8080/argus/metrics");
        
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

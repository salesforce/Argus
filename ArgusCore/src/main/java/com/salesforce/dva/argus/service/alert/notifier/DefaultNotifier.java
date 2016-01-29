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
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.service.AlertService.Notifier;
import com.salesforce.dva.argus.service.AnnotationService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.NotificationContext;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemConfiguration;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Default implementation of the Notifier interface. It creates an annotation on the specific time series specified in the alert expression which
 * indicates the time the policy was violated.
 *
 * @author  Raj Sarkapally (rsarkapally@salesforce.com)
 */
public abstract class DefaultNotifier implements Notifier {

    //~ Static fields/initializers *******************************************************************************************************************

    // ~ Static fields/initializers
    // *******************************************************************************************************************
    private static final String ANNOTATION_SOURCE = "ARGUS-ALERTS";
    private static final String ANNOTATION_TYPE = "ALERT";
    private static final String ANNOTATION_ID = "ID";

    //~ Instance fields ******************************************************************************************************************************

    private final MetricService _metricService;
    private final AnnotationService _annotationService;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new DefaultNotifier object.
     *
     * @param  metricService      The metric service. Cannot be null.
     * @param  annotationService  The annotation service. Cannot be null.
     */
    @Inject
    protected DefaultNotifier(MetricService metricService, AnnotationService annotationService, SystemConfiguration systemConfiguration) {
        _metricService = metricService;
        _annotationService = annotationService;
        requireArgument(systemConfiguration != null, "The system configuration cannot be null.");
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public void sendNotification(NotificationContext notificationContext) {
        SystemAssert.requireArgument(notificationContext != null, "Notification context cannot be null.");

        Map<String, String> additionalFields = new HashMap<>();

        additionalFields.put("Notification status", "Notification created.");
        _createAnnotation(notificationContext, additionalFields);
        sendAdditionalNotification(notificationContext);
        _dispose();
    }

    private void _createAnnotation(NotificationContext notificationContext, Map<String, String> additionalFields) {
        Alert alert = notificationContext.getAlert();
        Notification notification = null;
        List<Notification> notifications = alert.getNotifications();

        for (Notification notif : notifications) {
            if (notif.getName().equalsIgnoreCase(notificationContext.getNotification().getName())) {
                notification = notif;
                break;
            }
        }
        if (notification != null) {
            List<String> metricsToAnnotate = notification.getMetricsToAnnotate();

            for (String metricToAnnotate : metricsToAnnotate) {
                Map<String, String> resolvedTags = getTags(metricToAnnotate);
                Annotation annotation = new Annotation(ANNOTATION_SOURCE, ANNOTATION_ID, ANNOTATION_TYPE, getScopeName(metricToAnnotate),
                    getMetricName(metricToAnnotate), notificationContext.getTriggerFiredTime());
                Map<String, String> fields = new TreeMap<>();

                fields.putAll(additionalFields);
                fields.put("Alert Name", notificationContext.getAlert().getName());
                fields.put("Notification Name", notificationContext.getNotification().getName());
                fields.put("Trigger Name", notificationContext.getTrigger().getName());
                fields.put("Tags", resolvedTags.toString());
                annotation.setFields(fields);
                annotation.setTags(resolvedTags);
                _annotationService.updateAnnotation(alert.getOwner(), annotation);
            }
        }
    }

    private void _dispose() {
        _metricService.dispose();
        _annotationService.dispose();
    }

    /**
     * A post send hook for sub-class implementations to perform additional functionality.
     *
     * @param  context  The notification context.
     */
    protected abstract void sendAdditionalNotification(NotificationContext context);

    @Override
    public String getName() {
        return DefaultNotifier.class.getName();
    }

    private Map<String, String> getTags(String metricToAnnotate) {
        Map<String, String> result = new HashMap<>();

        if (metricToAnnotate.contains("{")) {
            String tagsToAnnotate = metricToAnnotate.replaceFirst(".*\\{", "").replaceFirst("\\}.*", "");
            String[] entries = tagsToAnnotate.split(",");

            for (String entry : entries) {
                String[] tag = entry.split("=");

                if (tag.length == 2) {
                    result.put(tag[0], tag[1]);
                }
            }
        }
        return result;
    }

    private String getScopeName(String metricToAnnotate) {
        String[] entries = metricToAnnotate.split(":");

        return entries[0];
    }

    private String getMetricName(String metricToAnnotate) {
        String expression = metricToAnnotate.replaceFirst("\\{.*", "");
        String[] entries = expression.split(":");

        return entries[1];
    }

    @Override
    public void clearNotification(NotificationContext notificationContext) {
        SystemAssert.requireArgument(notificationContext != null, "Notification context cannot be null.");

        Map<String, String> additionalFields = new HashMap<>();

        additionalFields.put("Notification status", "Notification cleared.");
        _createAnnotation(notificationContext, additionalFields);
        clearAdditionalNotification(notificationContext);
        _dispose();
    }

    /**
     * Defines additional implementation specific actions to take when a notification is cleared.
     *
     * @param  context  The notification context.  Cannot be null.
     */
    protected abstract void clearAdditionalNotification(NotificationContext context);
    
    @Override
    public Properties getNotifierProperties(){
        return new Properties();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

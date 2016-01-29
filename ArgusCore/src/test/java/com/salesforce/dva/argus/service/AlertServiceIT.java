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
     
package com.salesforce.dva.argus.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.IntegrationTest;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.entity.History.JobStatus;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.entity.Trigger.TriggerType;
import com.salesforce.dva.argus.service.AlertService.SupportedNotifier;
import com.salesforce.dva.argus.service.alert.notifier.AuditNotifier;

@Category(IntegrationTest.class)
public class AlertServiceIT extends AbstractTest {

    @Test
    public void testExecuteScheduledAlerts_ForOneTimeSeries() throws InterruptedException {
        UserService userService = system.getServiceFactory().getUserService();
        AlertService alertService = system.getServiceFactory().getAlertService();
        TSDBService tsdbService = system.getServiceFactory().getTSDBService();
        String scope = createRandomName(), metricName = createRandomName();
        int triggerMinValue = 50, inertiaPeriod = 1000 * 60 * 5;
        long startTime = (1000 * 60 * (System.currentTimeMillis() / (1000 * 60))) - 1000L * 60 * 60;
        long endTime = startTime + 1000L * 60 * 60;
        String tagName = "recordType", tagValue = "A";
        StringBuilder expression = new StringBuilder();
        int cooldownPeriod = 1000 * 5;
        int expectedNotifications = 1 + random.nextInt(3);

        try {
            expression.append("IDENTITY(").append(startTime).append(':').append(endTime).append(':');
            expression.append(scope).append(':').append(metricName).append('{').append(tagName).append('=').append(tagValue).append("}:").append(
                "avg:1m-avg)");

            Metric metric = createMetric(scope, metricName, triggerMinValue, inertiaPeriod, startTime, tagName, tagValue);

            tsdbService.putMetrics(Arrays.asList(new Metric[] { metric }));
            Thread.sleep(2000);

            Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "testAlert", expression.toString(), "* * * * *");
            Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "testTrigger", triggerMinValue, inertiaPeriod);
            Notification notification = new Notification("testNotification", alert, AuditNotifier.class.getName(), new ArrayList<String>(),
                cooldownPeriod);

            alert.setTriggers(Arrays.asList(new Trigger[] { trigger }));
            alert.setNotifications(Arrays.asList(new Notification[] { notification }));
            notification.setTriggers(alert.getTriggers());
            alert = alertService.updateAlert(alert);
            for (int i = 0; i < expectedNotifications; i++) {
                for (int j = 0; j < 1 + random.nextInt(5); j++) {
                    alertService.enqueueAlerts(Arrays.asList(new Alert[] { alert }));
                }
                alertService.executeScheduledAlerts(10, 100);
                try {
                    Thread.sleep(2 * cooldownPeriod);
                } catch (InterruptedException e) {
                    fail(e.getMessage());
                }
            }

            AuditNotifier dbNotifier = AuditNotifier.class.cast(alertService.getNotifier(SupportedNotifier.DATABASE));

            assertEquals(expectedNotifications, dbNotifier.getAllNotifications(alert).size());
        } finally {
            tsdbService.dispose();
        }
    }

    @Test
    public void testExecuteScheduledAlerts_ForMoreThanOneTimeSeries() throws InterruptedException {
        UserService userService = system.getServiceFactory().getUserService();
        AlertService alertService = system.getServiceFactory().getAlertService();
        TSDBService tsdbService = system.getServiceFactory().getTSDBService();
        String scope = createRandomName(), metricName = createRandomName();
        String tagName = "recordType", tagValue1 = "A", tagValue2 = "U";
        StringBuilder expression = new StringBuilder();
        int expectedNotifications = 1;

        try {
            expression.append(1).append(':');
            expression.append(scope).append(':').append(metricName).append('{').append(tagName).append('=').append("*").append("}:sum");

            Metric metric1 = new Metric(scope, metricName);
            Map<Long, String> dps1 = new HashMap<Long, String>();

            dps1.put(1000L, "1");
            dps1.put(2000L, "2");
            dps1.put(3000L, "3");
            metric1.setDatapoints(dps1);
            metric1.setTag(tagName, tagValue1);

            Metric metric2 = new Metric(scope, metricName);
            Map<Long, String> dps2 = new HashMap<Long, String>();

            dps2.put(4000L, "11");
            dps2.put(5000L, "20");
            dps2.put(6000L, "30");
            metric2.setDatapoints(dps2);
            metric2.setTag(tagName, tagValue2);
            tsdbService.putMetrics(Arrays.asList(new Metric[] { metric1, metric2 }));
            Thread.sleep(2000);

            Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "testAlert", expression.toString(), "* * * * *");
            Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "testTrigger", 10, 0);
            Notification notification = new Notification("testNotification", alert, AuditNotifier.class.getName(), new ArrayList<String>(), 0);

            alert.setTriggers(Arrays.asList(new Trigger[] { trigger }));
            alert.setNotifications(Arrays.asList(new Notification[] { notification }));
            notification.setTriggers(alert.getTriggers());
            alert = alertService.updateAlert(alert);
            for (int i = 0; i < expectedNotifications; i++) {
                alertService.enqueueAlerts(Arrays.asList(new Alert[] { alert }));
                alertService.executeScheduledAlerts(10, 100);
            }

            AuditNotifier dbNotifier = AuditNotifier.class.cast(alertService.getNotifier(SupportedNotifier.DATABASE));

            assertEquals(expectedNotifications, dbNotifier.getAllNotifications(alert).size());
        } finally {
            tsdbService.dispose();
        }
    }

    @Test
    @Ignore
    public void testExecuteScheduledAlerts_ShouldReturnJobStatusFailed_WhenMetricNotExist() throws InterruptedException {
        UserService userService = system.getServiceFactory().getUserService();
        AlertService alertService = system.getServiceFactory().getAlertService();
        TSDBService tsdbService = system.getServiceFactory().getTSDBService();
        String scope = createRandomName(), metricName = createRandomName();
        StringBuilder expression = new StringBuilder();
        int expectedNotifications = 1;

        try {
            expression.append(1).append(':');
            expression.append(scope).append(':').append(metricName).append(":sum");

            Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "testAlert", expression.toString(), "* * * * *");
            Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "testTrigger", 10, 0);
            Notification notification = new Notification("testNotification", alert, AuditNotifier.class.getName(), new ArrayList<String>(), 0);

            alert.setTriggers(Arrays.asList(new Trigger[] { trigger }));
            alert.setNotifications(Arrays.asList(new Notification[] { notification }));
            notification.setTriggers(alert.getTriggers());
            alert = alertService.updateAlert(alert);
            for (int i = 0; i < expectedNotifications; i++) {
                alertService.enqueueAlerts(Arrays.asList(new Alert[] { alert }));

                List<History> result = alertService.executeScheduledAlerts(1, 100);

                assertEquals(JobStatus.FAILURE, result.get(0).getJobStatus());
            }
        } finally {
            tsdbService.dispose();
        }
    }

    @Ignore
    @Test
    public void testClearNotificationForAlertEvaluation() {
        AlertService alertService = system.getServiceFactory().getAlertService();
        TSDBService tsdbService = system.getServiceFactory().getTSDBService();
        UserService userService = system.getServiceFactory().getUserService();
        PrincipalUser user = userService.findAdminUser();
        String scopeName = "alert.scope.test", metricName = "alert.metric.test";
        String metricExpression = MessageFormat.format("1:{0}:{1}:sum", scopeName, metricName);
        long currentTime = 1445000;
        Map<Long, String> datapoints = new HashMap<>();

        datapoints.put(currentTime, "100");

        Metric metric = new Metric(scopeName, metricName);

        metric.addDatapoints(datapoints);

        Alert alert = new Alert(user, user, "alert_test_clear", metricExpression, "* * * * *");
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN, "test_trigger", 50, 0);
        Notification notification = new Notification("test_notification", alert, AuditNotifier.class.getName(),
            Arrays.asList("rsarkapally@salesforce.com"), 0);

        notification.setMetricsToAnnotate(Arrays.asList(MessageFormat.format("{0}:{1}:sum", scopeName, metricName)));
        notification.setTriggers(Arrays.asList(trigger));
        alert.setNotifications(Arrays.asList(notification));
        alert.setTriggers(Arrays.asList(trigger));
        alert = alertService.updateAlert(alert);
        tsdbService.putMetrics(Arrays.asList(metric));
        alertService.enqueueAlerts(Arrays.asList(new Alert[] { alert }));
        alertService.executeScheduledAlerts(1, 100);
        alert = alertService.findAlertByPrimaryKey(alert.getId());
        assertTrue(alert.getNotifications().get(0).isActive());
        assertEquals(alert.getTriggers().get(0), alert.getNotifications().get(0).getFiredTrigger());
        datapoints.put(currentTime, "0");
        metric.setDatapoints(datapoints);
        tsdbService.putMetrics(Arrays.asList(metric));
        alertService.enqueueAlerts(Arrays.asList(new Alert[] { alert }));
        alertService.executeScheduledAlerts(10, 100);
        alert = alertService.findAlertByPrimaryKey(alert.getId());
        assertTrue(alert.getNotifications().get(0).isActive() == false);
        assertNull(alert.getNotifications().get(0).getFiredTrigger());
    }

    private Metric createMetric(String scope, String metricName, int triggerMinValue, int inertiaPeriod, long startTime, String tagName,
        String tagValue) {
        inertiaPeriod = inertiaPeriod / (1000 * 60);

        Metric result = new Metric(scope, metricName);
        Map<Long, String> datapoints = new HashMap<>();
        int index = 0;

        for (int j = 0; j <= random.nextInt(10); j++) {
            datapoints.put(startTime + (++index * 60000L), String.valueOf(random.nextInt(triggerMinValue)));
        }
        for (int j = 0; j <= inertiaPeriod; j++) {
            datapoints.put(startTime + (++index * 60000L), String.valueOf(triggerMinValue + random.nextInt(10)));
        }
        for (int j = 0; j <= random.nextInt(10); j++) {
            datapoints.put(startTime + (++index * 60000L), String.valueOf(random.nextInt(triggerMinValue)));
        }
        result.setDatapoints(datapoints);
        result.setDisplayName(createRandomName());
        result.setUnits(createRandomName());
        result.setTag(tagName, tagValue);
        return result;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

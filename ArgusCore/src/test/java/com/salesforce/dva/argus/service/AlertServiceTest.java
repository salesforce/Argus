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

import static com.salesforce.dva.argus.service.MQService.MQQueue.ALERT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.slf4j.LoggerFactory;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.Audit;
import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.entity.History.JobStatus;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.entity.Trigger.TriggerType;
import com.salesforce.dva.argus.service.alert.DefaultAlertService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.AlertIdWithTimestamp;

public class AlertServiceTest extends AbstractTest {

    private static final String expression =
        "DIVIDE(-1h:argus.jvm:file.descriptor.open{host=unknown-host}:avg, -1h:argus.jvm:file.descriptor.max{host=unknown-host}:avg)";

    @Test
    public void testUpdateAlert() {
        UserService userService = system.getServiceFactory().getUserService();
        AlertService alertService = system.getServiceFactory().getAlertService();
        Alert expected = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name", expression, "* * * * *");
        Notification notification = new Notification("notification", expected, "notifier-name", new ArrayList<String>(), 5000L);
        Trigger trigger = new Trigger(expected, TriggerType.GREATER_THAN, "trigger-name", 0.95, 60000);

        notification.setAlert(expected);
        expected.setNotifications(Arrays.asList(new Notification[] { notification }));
        expected.setTriggers(Arrays.asList(new Trigger[] { trigger }));
        assertTrue(!expected.isEnabled());
        expected = alertService.updateAlert(expected);

        Alert actual = alertService.findAlertByNameAndOwner(expected.getName(), userService.findAdminUser());

        assertEquals(expected.getId(), actual.getId());
        assertEquals(expected.getTriggers(), actual.getTriggers());
        actual.setEnabled(true);
        actual = alertService.updateAlert(actual);
        assertTrue(actual.isEnabled());
        alertService.deleteAlert(actual.getName(), userService.findAdminUser());
        assertTrue(alertService.findAlertByNameAndOwner(expected.getName(), expected.getOwner()) == null);
    }

    @Test
    public void testDeleteAlert() {
        UserService userService = system.getServiceFactory().getUserService();
        AlertService alertService = system.getServiceFactory().getAlertService();
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name", expression, "* * * * *");
        Notification notification1 = new Notification("notification1", alert, "notifier-name1", new ArrayList<String>(), 5000L);
        Notification notification2 = new Notification("notification2", alert, "notifier-name2", new ArrayList<String>(), 5000L);
        Trigger trigger1 = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "trigger-name1", 0.95, 60000);
        Trigger trigger2 = new Trigger(alert, TriggerType.GREATER_THAN, "trigger-name2", 0.95, 60000);

        alert.setNotifications(Arrays.asList(new Notification[] { notification1, notification2 }));
        alert.setTriggers(Arrays.asList(new Trigger[] { trigger1, trigger2 }));
        for (Notification notification : alert.getNotifications()) {
            notification.setTriggers(alert.getTriggers());
        }
        alert = alertService.updateAlert(alert);
        for (Notification notification : alert.getNotifications()) {
            alertService.deleteNotification(notification);
        }
        for (Trigger trigger : alert.getTriggers()) {
            alertService.deleteTrigger(trigger);
        }
        alert.setTriggers(null);
        alert.setNotifications(null);
        alert = alertService.updateAlert(alert);

        Trigger trigger3 = new Trigger(alert, TriggerType.GREATER_THAN, "trigger-name3", 0.95, 60000);
        Notification notification3 = new Notification("notification3", alert, "notifier-name3", new ArrayList<String>(), 5000L);

        notification3.setTriggers(Arrays.asList(new Trigger[] { trigger3 }));
        alert.setTriggers(Arrays.asList(new Trigger[] { trigger3 }));
        alert.setNotifications(Arrays.asList(new Notification[] { notification3 }));
        alert = alertService.updateAlert(alert);
        alertService.markAlertForDeletion(alert);
        assertNull("Failed to mark alert as deleted", alertService.findAlertByPrimaryKey(alert.getId()));

        Alert result = null;
        List<Alert> results = alertService.findAlertsMarkedForDeletion();

        for (Alert a : results) {
            if (alert.getId().equals(a.getId())) {
                result = a;
                break;
            }
        }
        assertNotNull("Could not find the alert that was marked for deletion", result);
        assertTrue(result.isDeleted());
        alertService.deleteAlert(alert);
        assertNull("Failed to delete alert", alertService.findAlertByPrimaryKey(alert.getId()));
    }

    @Test
    public void testFindAlertByNameAndOwner() {
        AlertService alertService = system.getServiceFactory().getAlertService();
        String alertName = "testAlert";
        PrincipalUser expectedUser = new PrincipalUser("testUser", "testuser@testcompany.com");
        Alert expectedAlert = new Alert(expectedUser, expectedUser, alertName, expression, "* * * * *");

        expectedAlert = alertService.updateAlert(expectedAlert);

        Alert actualAlert = alertService.findAlertByNameAndOwner(alertName, expectedAlert.getOwner());

        assertNotNull(actualAlert);
        assertEquals(expectedAlert.getId(), actualAlert.getId());
    }

    @Test
    public void testfindAlertsByOwner() {
        UserService userService = system.getServiceFactory().getUserService();
        AlertService alertService = system.getServiceFactory().getAlertService();
        String userName = createRandomName();
        int alertsCount = random.nextInt(20) + 1;
        PrincipalUser user = new PrincipalUser(userName, userName + "@testcompany.com");

        user = userService.updateUser(user);

        List<Alert> expectedAlerts = new ArrayList<>();

        for (int i = 0; i < alertsCount; i++) {
            expectedAlerts.add(alertService.updateAlert(new Alert(user, user, "alert_" + i, expression, "* * * * *")));
        }

        List<Alert> actualAlerts = alertService.findAlertsByOwner(user);

        assertEquals(actualAlerts.size(), expectedAlerts.size());

        Set<Alert> actualSet = new HashSet<>();

        actualSet.addAll(actualAlerts);
        for (Alert alert : expectedAlerts) {
            assertTrue(actualSet.contains(alert));
        }
    }

    @Test
    public void findAllAlerts() {
        UserService userService = system.getServiceFactory().getUserService();
        AlertService alertService = system.getServiceFactory().getAlertService();
        String userName = createRandomName();
        int alertsCount = random.nextInt(100) + 1;
        PrincipalUser user = new PrincipalUser(userName, userName + "@testcompany.com");

        user = userService.updateUser(user);

        List<Alert> expectedAlerts = new ArrayList<>();

        for (int i = 0; i < alertsCount; i++) {
            expectedAlerts.add(alertService.updateAlert(new Alert(user, user, "alert_" + i, expression, "* * * * *")));
        }

        List<Alert> actualAlerts = alertService.findAllAlerts();

        assertEquals(actualAlerts.size(), expectedAlerts.size());

        Set<Alert> actualSet = new HashSet<>();

        actualSet.addAll(actualAlerts);
        for (Alert alert : expectedAlerts) {
            assertTrue(actualSet.contains(alert));
        }
    }

    @Test
    public void testAlertDelete() {
        UserService userService = system.getServiceFactory().getUserService();
        AlertService alertService = system.getServiceFactory().getAlertService();
        PrincipalUser user = userService.findAdminUser();
        String alertName = createRandomName();
        Alert expectedAlert = new Alert(user, user, alertName, expression, "* * * * *");

        expectedAlert = alertService.updateAlert(expectedAlert);
        alertService.deleteAlert(expectedAlert);
        assertNull(alertService.findAlertByNameAndOwner(alertName, user));
    }

    @Test
    public void testAlertDeletePerformance() {
        UserService userService = system.getServiceFactory().getUserService();
        AlertService alertService = system.getServiceFactory().getAlertService();
        AuditService auditService = system.getServiceFactory().getAuditService();
        HistoryService historyService = system.getServiceFactory().getHistoryService();
        PrincipalUser user = userService.findAdminUser();
        int alertCount = 10;
        int auditCount = 5000;
        int historyCount = 5000;
        Alert[] alerts = new Alert[alertCount];

        for (int i = 0; i < alerts.length; i++) {
            Alert alert = new Alert(user, user, createRandomName() + i, "-1d:scope:metric:avg", "1 1 1 1 1");

            alerts[i] = alertService.updateAlert(alert);
            for (int j = 0; j < auditCount; j++) {
                Audit audit = new Audit("message", "localhost", alerts[i]);

                auditService.createAudit(audit);
            }
            for (int j = 0; j < historyCount; j++) {
                History history = new History("message", "localhost", alerts[i], JobStatus.DEQUEUED);

                historyService.updateHistory(history);
            }
            LoggerFactory.getLogger(getClass()).info("Created alert " + i);
        }

        double total = 0;

        for (Alert alert : alerts) {
            long start = System.currentTimeMillis();

            alertService.deleteAlert(alert);

            long delta = System.currentTimeMillis() - start;

            total += delta;
            LoggerFactory.getLogger(getClass()).info("Alert delete latency = " + delta);
        }
        LoggerFactory.getLogger(getClass()).info("Average delete latency = " + (total / alerts.length));
    }

    @Test
    public void testDeletedTriggersInNotifications1() {
        UserService userService = system.getServiceFactory().getUserService();
        AlertService alertService = system.getServiceFactory().getAlertService();
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name", expression, "* * * * *");
        Notification notification1 = new Notification("notification1", alert, "notifier-name1", new ArrayList<String>(), 5000L);
        Notification notification2 = new Notification("notification2", alert, "notifier-name2", new ArrayList<String>(), 5000L);
        Notification notification3 = new Notification("notification3", alert, "notifier-name3", new ArrayList<String>(), 5000L);
        Trigger trigger1 = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "trigger-name1", 0.95, 60000);

        alert.setNotifications(Arrays.asList(new Notification[] { notification1, notification2, notification3 }));
        alert.setTriggers(Arrays.asList(new Trigger[] { trigger1 }));
        notification1.setTriggers(alert.getTriggers());
        notification2.setTriggers(alert.getTriggers());
        notification3.setTriggers(alert.getTriggers());
        alert = alertService.updateAlert(alert);
        for (Notification notification : alert.getNotifications()) {
            notification.setTriggers(null);
        }
        alert.setTriggers(new ArrayList<Trigger>());
        alert = alertService.updateAlert(alert);
        for (Notification notification : alert.getNotifications()) {
            assertTrue(notification.getTriggers().isEmpty());
        }
    }

    @Test
    public void testGetTriggerFiredDatapointTime() {
        ServiceFactory factory = system.getServiceFactory();
        NotifierFactory notifierFactory = system.getNotifierFactory();
        DefaultAlertService service = new DefaultAlertService(factory.getMQService(), factory.getMetricService(), factory.getAnnotationService(),
            factory.getAuditService(), factory.getTSDBService(), factory.getMailService(), system.getConfiguration(), factory.getHistoryService(), factory.getMonitorService(), notifierFactory);
        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name_test", expression, "* * * * *");
        Metric metric = new Metric("scope", "metric");
        double thresholdValue = 90;
        long inertia = 10;
        long startTime = 1;
        long expectedTriggerTime;
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "name_test", thresholdValue, inertia);
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.putAll(createDatapoints(inertia + 1, thresholdValue, startTime, true));
        metric.setDatapoints(datapoints);
        expectedTriggerTime = datapoints.size();

        long actualValue = service.getTriggerFiredDatapointTime(trigger, metric);

        assertEquals(expectedTriggerTime, actualValue);
        startTime = datapoints.size() + 1;
        datapoints.putAll(createDatapoints(201, thresholdValue, startTime, false));
        metric.setDatapoints(datapoints);
        actualValue = service.getTriggerFiredDatapointTime(trigger, metric);
        assertEquals(expectedTriggerTime, actualValue);
        startTime = datapoints.size() + 1;
        datapoints.putAll(createDatapoints(inertia - 1, thresholdValue, startTime, true));
        metric.setDatapoints(datapoints);
        actualValue = service.getTriggerFiredDatapointTime(trigger, metric);
        assertEquals(expectedTriggerTime, actualValue);
        startTime = datapoints.size() + 1;
        datapoints.putAll(createDatapoints(inertia + 1, thresholdValue, startTime, true));
        metric.setDatapoints(datapoints);
        actualValue = service.getTriggerFiredDatapointTime(trigger, metric);
        expectedTriggerTime = datapoints.size();
        assertEquals(expectedTriggerTime, actualValue);
        startTime = datapoints.size() + 1;
        datapoints.putAll(createDatapoints(201, thresholdValue, startTime, false));
        metric.setDatapoints(datapoints);
        actualValue = service.getTriggerFiredDatapointTime(trigger, metric);
        assertEquals(expectedTriggerTime, actualValue);
    }

    @Test
    public void testGetTriggerFiredDatapointTimeWhenOneDatapointAndZeroInertia() {
        ServiceFactory factory = system.getServiceFactory();
        NotifierFactory notifierFactory = system.getNotifierFactory();
        DefaultAlertService service = new DefaultAlertService(factory.getMQService(), factory.getMetricService(), factory.getAnnotationService(),
            factory.getAuditService(), factory.getTSDBService(), factory.getMailService(), system.getConfiguration(), factory.getHistoryService(), factory.getMonitorService(), notifierFactory);
        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name_test", expression, "* * * * *");
        Metric metric = new Metric("scope", "metric");
        double thresholdValue = 90;
        long inertia = 0;
        long startTime = 1000;
        long expectedTriggerTime;
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "name_test", thresholdValue, inertia);
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.putAll(createDatapoints(1, thresholdValue, startTime, true));
        metric.setDatapoints(datapoints);
        expectedTriggerTime = startTime;

        long actualValue = service.getTriggerFiredDatapointTime(trigger, metric);

        assertEquals(expectedTriggerTime, actualValue);
    }

    @Test
    public void testGetTriggerFiredDatapointTimeWhenOneDatapointAndInertiaOne() {
        ServiceFactory factory = system.getServiceFactory();
        NotifierFactory notifierFactory = system.getNotifierFactory();
        DefaultAlertService service = new DefaultAlertService(factory.getMQService(), factory.getMetricService(), factory.getAnnotationService(),
            factory.getAuditService(), factory.getTSDBService(), factory.getMailService(), system.getConfiguration(), factory.getHistoryService(), factory.getMonitorService(), notifierFactory);
        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name_test", expression, "* * * * *");
        Metric metric = new Metric("scope", "metric");
        double thresholdValue = 90;
        long inertia = 1;
        long startTime = 1000;
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "name_test", thresholdValue, inertia);
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.putAll(createDatapoints(1, thresholdValue, startTime, true));
        metric.setDatapoints(datapoints);

        Long actualValue = service.getTriggerFiredDatapointTime(trigger, metric);

        assertNull(actualValue);
    }

    @Test
    public void testGetTriggerFiredDatapointTimeWehnNoDatapoints() {
        ServiceFactory factory = system.getServiceFactory();
        NotifierFactory notifierFactory = system.getNotifierFactory();
        DefaultAlertService service = new DefaultAlertService(factory.getMQService(), factory.getMetricService(), factory.getAnnotationService(),
            factory.getAuditService(), factory.getTSDBService(), factory.getMailService(), system.getConfiguration(), factory.getHistoryService(), factory.getMonitorService(), notifierFactory);
        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name_test", expression, "* * * * *");
        Metric metric = new Metric("scope", "metric");
        double thresholdValue = 90;
        long inertia = 0;
        long startTime = 1000;
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "name_test", thresholdValue, inertia);
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.putAll(createDatapoints(0, thresholdValue, startTime, true));
        metric.setDatapoints(datapoints);

        Long actualValue = service.getTriggerFiredDatapointTime(trigger, metric);

        assertNull(actualValue);
    }


    @Test
    public void testAlertDeleteCreateAnotherAlertWithSameName() {
        UserService userService = system.getServiceFactory().getUserService();
        AlertService alertService = system.getServiceFactory().getAlertService();
        PrincipalUser user = userService.findAdminUser();
        String alertName = createRandomName();
        Alert alert = new Alert(user, user, alertName, expression, "* * * * *");

        alert = alertService.updateAlert(alert);
        alertService.markAlertForDeletion(alert);
        assertNull(alertService.findAlertByNameAndOwner(alertName, user));
        alert = new Alert(user, user, alertName, expression, "* * * * *");
        alert = alertService.updateAlert(alert);
        assertNotNull((alertService.findAlertByNameAndOwner(alertName, user)));
    }

    private Map<Long, String> createDatapoints(long size, Double value, long startTime, boolean greaterThan) {
        Map<Long, String> result = new HashMap<Long, String>();

        for (int i = 0; i < size; i++) {
            double dataPointValue = random.nextInt(value.intValue()) + (greaterThan ? (value + 2) : -1);

            result.put(startTime++, String.valueOf(dataPointValue));
        }
        return result;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

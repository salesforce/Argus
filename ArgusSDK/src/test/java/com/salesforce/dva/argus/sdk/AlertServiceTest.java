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
package com.salesforce.dva.argus.sdk;

import com.salesforce.dva.argus.sdk.entity.Alert;
import com.salesforce.dva.argus.sdk.entity.Notification;
import com.salesforce.dva.argus.sdk.entity.Trigger;
import org.junit.Test;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;

public class AlertServiceTest extends AbstractTest {

    @Test
    public void testCreateAlert() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AlertServiceTest.json"))) {
            AlertService alertService = argusService.getAlertService();
            Alert result = alertService.createAlert(_constructUnpersistedAlert());
            Alert expected = _constructPersistedAlert();

            assertEquals(expected, result);
        }
    }

    @Test
    public void testGetAlerts() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AlertServiceTest.json"))) {
            AlertService alertService = argusService.getAlertService();
            List<Alert> result = alertService.getAlerts();
            List<Alert> expected = Arrays.asList(new Alert[] { _constructPersistedAlert() });

            assertEquals(expected, result);
        }
    }

    @Test
    public void testGetAlert() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AlertServiceTest.json"))) {
            AlertService alertService = argusService.getAlertService();
            Alert result = alertService.getAlert(BigInteger.ONE);
            Alert expected = _constructPersistedAlert();

            assertEquals(expected, result);
        }
    }

    @Test
    public void testGetNotifications() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AlertServiceTest.json"))) {
            AlertService alertService = argusService.getAlertService();
            List<Notification> result = alertService.getNotifications(BigInteger.ONE);
            List<Notification> expected = Arrays.asList(new Notification[] { _constructPersistedNotification() });

            assertEquals(expected, result);
        }
    }

    @Test
    public void testGetTriggers() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AlertServiceTest.json"))) {
            AlertService alertService = argusService.getAlertService();
            List<Trigger> result = alertService.getTriggers(BigInteger.ONE);
            List<Trigger> expected = Arrays.asList(new Trigger[] { _constructPersistedTrigger() });

            assertEquals(expected, result);
        }
    }

    @Test
    public void testGetTrigger() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AlertServiceTest.json"))) {
            AlertService alertService = argusService.getAlertService();
            Trigger result = alertService.getTrigger(BigInteger.ONE, BigInteger.ONE);
            Trigger expected = _constructPersistedTrigger();

            assertEquals(expected, result);
        }
    }

    @Test
    public void testGetNotification() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AlertServiceTest.json"))) {
            AlertService alertService = argusService.getAlertService();
            Notification result = alertService.getNotification(BigInteger.ONE, BigInteger.ONE);
            Notification expected = _constructPersistedNotification();

            assertEquals(expected, result);
        }
    }

    @Test
    public void testGetTriggersForNotification() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AlertServiceTest.json"))) {
            AlertService alertService = argusService.getAlertService();
            List<Trigger> result = alertService.getTriggersForNotification(BigInteger.ONE, BigInteger.ONE);
            List<Trigger> expected = Arrays.asList(new Trigger[] { _constructPersistedTrigger() });

            assertEquals(expected, result);
        }
    }

    @Test
    public void testGetTriggerIfAssigned() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AlertServiceTest.json"))) {
            AlertService alertService = argusService.getAlertService();
            Trigger result = alertService.getTriggerIfAssigned(BigInteger.ONE, BigInteger.ONE, BigInteger.ONE);
            Trigger expected = _constructPersistedTrigger();

            assertEquals(expected, result);
        }
    }

    @Test
    public void testUpdateAlert() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AlertServiceTest.json"))) {
            AlertService alertService = argusService.getAlertService();
            Alert alert = alertService.getAlert(BigInteger.ONE);

            alert.setName("UpdatedAlert");

            Alert result = alertService.updateAlert(BigInteger.ONE, alert);
            Alert expected = _constructUpdatedAlert();

            assertEquals(expected, result);
        }
    }

    @Test
    public void testUpdateNotification() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AlertServiceTest.json"))) {
            AlertService alertService = argusService.getAlertService();
            Notification notification = alertService.getNotification(BigInteger.ONE, BigInteger.ONE);

            notification.setName("UpdatedNotification");

            Notification result = alertService.updateNotification(BigInteger.ONE, BigInteger.ONE, notification);
            Notification expected = _constructUpdatedNotification();

            assertEquals(expected, result);
        }
    }

    @Test
    public void testCreateNotification() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AlertServiceTest.json"))) {
            AlertService alertService = argusService.getAlertService();
            Notification notification = _constructUnpersistedNotification();
            List<Notification> result = alertService.createNotification(BigInteger.ONE, notification);
            List<Notification> expected = Arrays.asList(new Notification[] { _constructPersistedNotification() });

            assertEquals(expected, result);
        }
    }

    @Test
    public void testUpdateTrigger() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AlertServiceTest.json"))) {
            AlertService alertService = argusService.getAlertService();
            Trigger trigger = alertService.getTrigger(BigInteger.ONE, BigInteger.ONE);

            trigger.setName("UpdatedTrigger");

            Trigger result = alertService.updateTrigger(BigInteger.ONE, BigInteger.ONE, trigger);
            Trigger expected = _constructUpdatedTrigger();

            assertEquals(expected, result);
        }
    }

    @Test
    public void testCreateTrigger() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AlertServiceTest.json"))) {
            AlertService alertService = argusService.getAlertService();
            Trigger trigger = _constructUnpersistedTrigger();
            List<Trigger> result = alertService.createTrigger(BigInteger.ONE, trigger);
            List<Trigger> expected = Arrays.asList(new Trigger[] { _constructPersistedTrigger() });

            assertEquals(expected, result);
        }
    }

    @Test
    public void testDeleteAlert() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AlertServiceTest.json"))) {
            AlertService alertService = argusService.getAlertService();

            alertService.deleteAlert(BigInteger.ONE);
        }
    }

    @Test
    public void testDeleteNotifications() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AlertServiceTest.json"))) {
            AlertService alertService = argusService.getAlertService();

            alertService.deleteNotifications(BigInteger.ONE);
        }
    }

    @Test
    public void testDeleteNotification() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AlertServiceTest.json"))) {
            AlertService alertService = argusService.getAlertService();

            alertService.deleteNotification(BigInteger.ONE, BigInteger.ONE);
        }
    }

    @Test
    public void testDeleteTriggers() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AlertServiceTest.json"))) {
            AlertService alertService = argusService.getAlertService();

            alertService.deleteTriggers(BigInteger.ONE);
        }
    }

    @Test
    public void testDeleteTrigger() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AlertServiceTest.json"))) {
            AlertService alertService = argusService.getAlertService();

            alertService.deleteTrigger(BigInteger.ONE, BigInteger.ONE);
        }
    }

    @Test
    public void testUnlinkTriggers() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AlertServiceTest.json"))) {
            AlertService alertService = argusService.getAlertService();

            alertService.unlinkTriggers(BigInteger.ONE, BigInteger.ONE);
        }
    }

    @Test
    public void testUnlinkTrigger() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AlertServiceTest.json"))) {
            AlertService alertService = argusService.getAlertService();

            alertService.unlinkTrigger(BigInteger.ONE, BigInteger.ONE, BigInteger.ONE);
        }
    }

    @Test
    public void testLinkTrigger() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AlertServiceTest.json"))) {
            AlertService alertService = argusService.getAlertService();
            Trigger result = alertService.linkTrigger(BigInteger.ONE, BigInteger.ONE, BigInteger.ONE);
            Trigger expected = _constructPersistedTrigger();

            assertEquals(expected, result);
        }
    }

    @Test
    public void testDuplicateAlert() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AlertServiceTest.json"))) {
            AlertService alertService = argusService.getAlertService();

            alertService.createAlert(_constructPersistedAlert());
        } catch (ArgusServiceException ex) {
            assertEquals(500, ex.getStatus());
            return;
        }
        fail("Expected an ArgusServiceException for creating a duplicate alert.");
    }

    private Alert _constructUpdatedAlert() {
        Alert alert = _constructPersistedAlert();

        alert.setName("UpdatedAlert");
        return alert;
    }

    private Alert _constructPersistedAlert() {
        Alert alert = _constructUnpersistedAlert();

        alert.setCreatedDate(new Date(1472282830936L));
        alert.setCreatedById(BigInteger.ONE);
        alert.setId(BigInteger.ONE);
        alert.setModifiedById(BigInteger.ONE);
        alert.setModifiedDate(alert.getCreatedDate());
        return alert;
    }

    private Alert _constructUnpersistedAlert() {
        Alert alert = new Alert();

        alert.setCronEntry("* * * * *");
        alert.setEnabled(false);
        alert.setExpression("-1d:argus.jvm:open.file.descriptors:sum");
        alert.setMissingDataNotificationEnabled(false);
        alert.setName("TestAlert");
        alert.setOwnerName("admin");
        return alert;
    }

    private Notification _constructUpdatedNotification() {
        Notification notification = _constructPersistedNotification();

        notification.setName("UpdatedNotification");
        return notification;
    }

    private Notification _constructPersistedNotification() {
        Notification notification = _constructUnpersistedNotification();

        notification.setCreatedDate(new Date(1472282830936L));
        notification.setCreatedById(BigInteger.ONE);
        notification.setId(BigInteger.ONE);
        notification.setModifiedById(BigInteger.ONE);
        notification.setModifiedDate(notification.getCreatedDate());
        return notification;
    }

    private Notification _constructUnpersistedNotification() {
        Notification notification = new Notification();

        notification.setAlertId(BigInteger.ONE);
        notification.setCooldownExpiration(-1L);
        notification.setCooldownPeriod(60000L);
        notification.setMetricsToAnnotate(Arrays.asList(new String[] { "argus.jvm:open.file.descriptors:sum" }));
        notification.setName("TestNotification");
        notification.setNotifierName("EmailNotifier");
        notification.setSubscriptions(Arrays.asList(new String[] { "you@yourcompany.com" }));
        return notification;
    }

    private Trigger _constructUpdatedTrigger() {
        Trigger trigger = _constructPersistedTrigger();

        trigger.setName("UpdatedTrigger");
        return trigger;
    }

    private Trigger _constructPersistedTrigger() {
        Trigger trigger = _constructUnpersistedTrigger();

        trigger.setCreatedDate(new Date(1472282830936L));
        trigger.setCreatedById(BigInteger.ONE);
        trigger.setId(BigInteger.ONE);
        trigger.setModifiedById(BigInteger.ONE);
        trigger.setModifiedDate(trigger.getCreatedDate());
        return trigger;
    }

    private Trigger _constructUnpersistedTrigger() {
        Trigger trigger = new Trigger();

        trigger.setAlertId(BigInteger.ONE);
        trigger.setInertia(60000L);
        trigger.setThreshold(100.0);
        trigger.setSecondaryThreshold(200.0);
        trigger.setType("BETWEEN");
        trigger.setName("TestTrigger");
        return trigger;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

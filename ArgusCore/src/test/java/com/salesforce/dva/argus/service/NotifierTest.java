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

import java.util.ArrayList;
import java.util.Arrays;

import org.junit.Test;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.entity.Trigger.TriggerType;
import com.salesforce.dva.argus.service.AlertService.Notifier;
import com.salesforce.dva.argus.service.AlertService.SupportedNotifier;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.NotificationContext;
import com.salesforce.dva.argus.service.alert.notifier.AuditNotifier;

public class NotifierTest extends AbstractTest {

    private static final String expression =
        "DIVIDE(-1h:argus.jvm:file.descriptor.open{host=unknown-host}:avg, -1h:argus.jvm:file.descriptor.max{host=unknown-host}:avg)";

    @Test
    public void testDBNotifier() {
        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert_name", expression, "* * * * *");
        Notification notification = new Notification("notification_name", alert, "notifier_ame", new ArrayList<String>(), 23);
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "trigger_name", 2D, 5);

        alert.setNotifications(Arrays.asList(new Notification[] { notification }));
        alert.setTriggers(Arrays.asList(new Trigger[] { trigger }));
        alert = system.getServiceFactory().getAlertService().updateAlert(alert);

        NotificationContext context = new NotificationContext(alert, trigger, notification, 1418319600000L, "foo");
        int count = 1 + random.nextInt(5);

        for (int i = 0; i < count; i++) {
            Notifier notifier = system.getServiceFactory().getAlertService().getNotifier(SupportedNotifier.DATABASE);

            notifier.sendNotification(context);
        }

        Notifier notifier = system.getServiceFactory().getAlertService().getNotifier(SupportedNotifier.DATABASE);

        assertEquals(count, AuditNotifier.class.cast(notifier).getAllNotifications(alert).size());
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

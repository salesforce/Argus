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

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.service.monitor.DefaultMonitorService;
import org.junit.Test;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class MonitorServiceTest extends AbstractTest {

    @Test
    public void testServiceIsSingleton() {
        assertTrue(system.getServiceFactory().getMonitorService() == system.getServiceFactory().getMonitorService());
    }

    @Test(timeout = 10000L)
    public void testConcurrentUpdates() throws NoSuchFieldException, IllegalAccessException, InterruptedException {
        final MonitorService _monitorService = system.getServiceFactory().getMonitorService();
        Field field = DefaultMonitorService.class.getDeclaredField("TIME_BETWEEN_RECORDINGS");

        field.setAccessible(true);
        field.setLong(null, 10 * 1000);
        _monitorService.startRecordingCounters();

        final CountDownLatch gate = new CountDownLatch(1);
        int workerCount = 3;
        final int iterations = 100;
        Thread[] workers = new Thread[workerCount];

        for (int i = 0; i < workers.length; i++) {
            Thread thread = new Thread(new Runnable() {

                    @Override
                    public void run() {
                        try {
                            gate.await();
                            for (int j = 0; j < iterations; j++) {
                                _monitorService.modifyCustomCounter("test.custom.metric", 1, Collections.<String, String>emptyMap());
                            }
                        } catch (InterruptedException ex) {
                            org.junit.Assert.fail("This should never happen.");
                        }
                    }
                });

            thread.setDaemon(true);
            thread.start();
            workers[i] = thread;
        }
        gate.countDown();
        for (Thread worker : workers) {
            worker.join(5000);
        }

        int customCounter = (int) _monitorService.getCustomCounter("test.custom.metric", Collections.<String, String>emptyMap());

        assertEquals(iterations * workerCount, customCounter);
        _monitorService.resetCustomCounters();
        customCounter = (int) _monitorService.getCustomCounter("test.custom.metric", Collections.<String, String>emptyMap());
        assertEquals(0, customCounter);
    }

    @Test
    public void testAlertsCreatedOnStartMonitoring() throws Exception {
        MonitorService monitorService = system.getServiceFactory().getMonitorService();
        AlertService alertService = system.getServiceFactory().getAlertService();
        UserService userService = system.getServiceFactory().getUserService();

        monitorService.startRecordingCounters();
        assertNotNull(alertService.findAlertByNameAndOwner(constructAlertName(monitorService, "PHYSICAL_MEMORY_ALERT"), userService.findAdminUser()));
        assertNotNull(alertService.findAlertByNameAndOwner(constructAlertName(monitorService, "SWAP_SPACE_ALERT"), userService.findAdminUser()));
        assertNotNull(alertService.findAlertByNameAndOwner(constructAlertName(monitorService, "FILE_DESCRIPTORS_ALERT"),
                userService.findAdminUser()));
        monitorService.stopRecordingCounters();
        Thread.sleep(1000);
        assertFalse(alertService.findAlertByNameAndOwner(constructAlertName(monitorService, "PHYSICAL_MEMORY_ALERT"), userService.findAdminUser())
            .isEnabled());
        assertFalse(alertService.findAlertByNameAndOwner(constructAlertName(monitorService, "SWAP_SPACE_ALERT"), userService.findAdminUser())
            .isEnabled());
        assertFalse(alertService.findAlertByNameAndOwner(constructAlertName(monitorService, "FILE_DESCRIPTORS_ALERT"), userService.findAdminUser())
            .isEnabled());
    }

    private String constructAlertName(MonitorService service, String type) throws Exception {
        Method method = DefaultMonitorService.class.getDeclaredMethod("_constructAlertName", String.class);

        method.setAccessible(true);
        return (String) method.invoke(service, getFieldValue(service, type));
    }

    private String getFieldValue(MonitorService service, String fieldName) throws Exception {
        Field field = DefaultMonitorService.class.getDeclaredField(fieldName);

        field.setAccessible(true);
        return field.get(service).toString();
    }

    @Test
    public void testEnableDisableMonitoring() throws Exception {
        MonitorService monitorService = system.getServiceFactory().getMonitorService();

        monitorService.startRecordingCounters();
        monitorService.disableMonitoring();
        Thread.sleep(1000);

        AlertService alertService = system.getServiceFactory().getAlertService();
        List<Alert> alerts = alertService.findAlertsByNameWithPrefix(getFieldValue(monitorService, "ALERT_NAME_PREFIX"));
        boolean areAllAlertsDisabled = true;

        for (Alert alert : alerts) {
            if (alert.isEnabled()) {
                areAllAlertsDisabled = false;
            }
        }
        assertTrue(areAllAlertsDisabled);
        monitorService.enableMonitoring();
        alerts = alertService.findAlertsByNameWithPrefix(getFieldValue(monitorService, "ALERT_NAME_PREFIX"));

        boolean areAllAlertsEnabled = true;

        for (Alert alert : alerts) {
            if (!alert.isEnabled()) {
                areAllAlertsEnabled = false;
            }
        }
        assertTrue(areAllAlertsEnabled);
    }

    @Test
    public void test_getCounterFromMetricName() {
        assertThat(MonitorService.Counter.fromMetricName("cores.active"), is(MonitorService.Counter.ACTIVE_CORES));
        assertThat(MonitorService.Counter.fromMetricName("cores.active123"), is((MonitorService.Counter) null));
    }

    @Test
    public void test_MonitoringDashboardNotNull() {
        MonitorService monitorService = system.getServiceFactory().getMonitorService();

        assertNotNull(monitorService.getRuntimeDashboard());
        assertNotNull(monitorService.getSystemDashboard());
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

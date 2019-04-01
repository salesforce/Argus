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

import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.service.monitor.DataLagMonitor;
import com.salesforce.dva.argus.service.monitor.DefaultMonitorService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.powermock.reflect.Whitebox;
import org.mockito.runners.MockitoJUnitRunner;

import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.CountDownLatch;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import com.salesforce.dva.argus.system.SystemMain;
import com.salesforce.dva.argus.TestUtils;


@RunWith(MockitoJUnitRunner.class)
public class MonitorServiceTest {
    private static final double DOUBLE_COMPARISON_MAX_DELTA = 0.001;
    private static final String HOSTNAME = SystemConfiguration.getHostname();
    private MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();

    private SystemMain system;

    @Before
    public void setUp() {
        system = TestUtils.getInstance();
        system.start();
    }

    @After
    public void tearDown() {
        if (system != null) {
            system.getServiceFactory().getManagementService().cleanupRecords();
            system.stop();
        }
    }

    @Mock
    private TSDBService tsdbMock;

    @Mock
    private MetricService metricServiceMock;

    @Test
    public void testServiceIsSingleton() {
        assertTrue(system.getServiceFactory().getMonitorService() == system.getServiceFactory().getMonitorService());
    }

    @Test(timeout = 5000L)
    public void testGaugeConcurrentUpdates() throws Exception {
        final String metricName = "test.custom.metric";
        final DefaultMonitorService _monitorService = (DefaultMonitorService) system.getServiceFactory().getMonitorService();
        _monitorService.setTSDBService(tsdbMock);
        Field field = DefaultMonitorService.class.getDeclaredField("TIME_BETWEEN_RECORDINGS");

        final int TIME_BETWEEN_RECORDINGS_MS = 2000;
        final int RESET_TIME_AFTER_TEST_MS = 3000; // enough time for reset to have occurred.
        field.setAccessible(true);
        field.setLong(null, TIME_BETWEEN_RECORDINGS_MS);
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
                                _monitorService.modifyCustomCounter(metricName, 1, Collections.<String, String>emptyMap());
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
            worker.join(1500);
        }

        // gauge value should be iterations * workerCount
        double customCounter = _monitorService.getCustomCounter(metricName, Collections.<String, String>emptyMap());
        double expectedCounterValue = iterations * workerCount;
        assertEquals(expectedCounterValue, customCounter, DOUBLE_COMPARISON_MAX_DELTA);

        // jmx gauge should be 0 since gauge value should be init to 0
        ObjectName jmxName = new ObjectName("ArgusMetrics:type=Counter,scope=argus.custom,metric=" + metricName + ",host=" + HOSTNAME);
        double jmxValue = (Double)mbeanServer.getAttribute(jmxName, "Value");
        assertEquals(0, jmxValue, DOUBLE_COMPARISON_MAX_DELTA);

        // wait for MonitorThread to run
        Thread.sleep(RESET_TIME_AFTER_TEST_MS);

        // gauge value should have reset
        customCounter = _monitorService.getCustomCounter(metricName, Collections.<String, String>emptyMap());
        assertEquals(Double.NaN, customCounter, DOUBLE_COMPARISON_MAX_DELTA);

        // jmx gauge value should now reflect iterations * workerCount
        jmxValue = (Double)mbeanServer.getAttribute(jmxName, "Value");
        assertEquals(expectedCounterValue, jmxValue, DOUBLE_COMPARISON_MAX_DELTA);
    }

    @Test
    public void testDatalagIncrement() {
        DataLagMonitor dataLagMonitor = new DataLagMonitor(system.getConfiguration(), metricServiceMock, tsdbMock);
        Field testField = null;  // Checks superclasses.
        try {
            testField = dataLagMonitor.getClass().getDeclaredField("_lagPerDC");
            testField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            fail();
        }
        Map<String, Double> _lagPerDCTest = new TreeMap<>();
        Map<String, Double> expectedOutput = new TreeMap<>();
        Double minute = 1.0 * 60 * 1000;
        _lagPerDCTest.put("DC1", 0.0);
        expectedOutput.put("DC1", minute);
        _lagPerDCTest.put("DC2", 1.0 * 60 * 60 * 1000);
        expectedOutput.put("DC2", 1.0 * 60 * 60 * 1000 + minute);
        _lagPerDCTest.put("DC3", 2.0 * 60 * 60 * 1000);
        expectedOutput.put("DC3", 2.0 * 60 * 60 * 1000 + minute);
        _lagPerDCTest.put("DC4", 4.0 * 60 * 60 * 1000);
        expectedOutput.put("DC4", 4.0 * 60 * 60 * 1000);
        _lagPerDCTest.put("DC5", 7.0 * 60 * 60 * 1000);
        expectedOutput.put("DC5", 4.0 * 60 * 60 * 1000);
        try {
            testField.set(dataLagMonitor, _lagPerDCTest);
        } catch (IllegalAccessException e) {
            fail();
        }

        for(String dc: _lagPerDCTest.keySet()) {
            Double lagTime = null;
            try {
                lagTime = Whitebox.invokeMethod(dataLagMonitor, "getLagTimeInMillis", dc, System.currentTimeMillis(), null);
            } catch (Exception e) {
                fail();
            }
            assertEquals(expectedOutput.get(dc), lagTime, 0.01);
        }

    }

    @Test(timeout = 5000L)
    public void testMonotonicCounterConcurrentUpdates() throws Exception {
        final MonitorService.Counter counter = MonitorService.Counter.ALERTS_SCHEDULED;
        final DefaultMonitorService _monitorService = (DefaultMonitorService) system.getServiceFactory().getMonitorService();
        _monitorService.setTSDBService(tsdbMock);
        Field field = DefaultMonitorService.class.getDeclaredField("TIME_BETWEEN_RECORDINGS");

        final int TIME_BETWEEN_RECORDINGS_MS = 2000;
        final int RESET_TIME_AFTER_TEST_MS = 3000; // enough time for reset to have occurred.
        field.setAccessible(true);
        field.setLong(null, TIME_BETWEEN_RECORDINGS_MS);
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
                            _monitorService.modifyCounter(counter, 1, Collections.<String, String>emptyMap());
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
            worker.join(1500);
        }

        // gauge value should be iterations * workerCount
        double expectedCounterValue = iterations * workerCount;
        double customCounter = _monitorService.getCounter(counter, Collections.<String, String>emptyMap());
        assertEquals(expectedCounterValue, customCounter, DOUBLE_COMPARISON_MAX_DELTA);

        // jmx counter value should be iterations * workerCount as well
        ObjectName jmxName = new ObjectName("ArgusMetrics:type=Counter,scope=argus.core,metric=" + counter.getMetric() + counter.getJMXMetricNameSuffix() + ",host=" + HOSTNAME);
        double jmxValue = (Double)mbeanServer.getAttribute(jmxName, "Value");
        assertEquals(expectedCounterValue, jmxValue, DOUBLE_COMPARISON_MAX_DELTA);

        // wait for MonitorThread to run, which clears gauge metrics
        Thread.sleep(RESET_TIME_AFTER_TEST_MS);

        // counter value should NOT have reset
        customCounter = _monitorService.getCounter(counter, Collections.<String, String>emptyMap());
        assertEquals(Double.NaN, customCounter, DOUBLE_COMPARISON_MAX_DELTA);

        // jmx counter value should NOT reset either
        jmxValue = (Double)mbeanServer.getAttribute(jmxName, "Value");
        assertEquals(expectedCounterValue, jmxValue, DOUBLE_COMPARISON_MAX_DELTA);
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

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

package com.salesforce.dva.argus.service.schedule;

import com.google.inject.Provider;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.entity.Trigger.TriggerType;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.GlobalInterlockService;
import com.salesforce.dva.argus.service.HistoryService;
import com.salesforce.dva.argus.service.MQService;
import com.salesforce.dva.argus.service.MailService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService;
import com.salesforce.dva.argus.service.alert.notifier.AuditNotifier;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import javax.persistence.EntityManager;
import javax.persistence.Persistence;
import java.util.ArrayList;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.BeforeClass;
import org.junit.AfterClass;
import com.salesforce.dva.argus.system.SystemMain;
import com.salesforce.dva.argus.TestUtils;


@RunWith(org.mockito.junit.MockitoJUnitRunner.class)
public class SchedulingServiceTest {
    @Mock Provider<EntityManager> _emProviderMock;
    @Mock
    TSDBService _tsdbServiceMock;
    @Mock private MetricService _metricServiceMock;
    @Mock private MailService _mailServiceMock;
    @Mock private HistoryService _historyServiceMock;
    @Mock private MonitorService _monitorServiceMock;
    @Mock private AuditService _auditServiceMock;

    private EntityManager em;

    static private UserService userService;
    static private SystemMain system;
    static private MQService mqService;



    @BeforeClass
    static public void setUpClass() {
        system = TestUtils.getInstance();
        system.start();
        userService = system.getServiceFactory().getUserService();
        mqService = system.getServiceFactory().getMQService();
    }

    @AfterClass
    static public void tearDownClass() {
        if (system != null) {
            system.getServiceFactory().getManagementService().cleanupRecords();
            system.stop();
        }
    }

    @Before
    public void setup() {
        // set up EM
        em = Persistence.createEntityManagerFactory("argus-pu").createEntityManager();
        when(_emProviderMock.get()).thenReturn(em);
        em.getTransaction().begin();
    }

    @After
    public void teardown() {
        // rolling back transactions and forcing the gc to clean up. Otherwise the EM created above gets injected by guice in ut's that run afterwards. So weird
        em.getTransaction().rollback();
        em.close();
        em = null;
        System.gc();
    }

    @Test
    public void testAlertSchedulingWithGlobalInterlock() {

        // Alert service with mocked tsdb service
        DefaultAlertService alertServiceOriginal = new DefaultAlertService(system.getConfiguration(), mqService, _metricServiceMock, _auditServiceMock,
                _tsdbServiceMock, _mailServiceMock, _historyServiceMock, _monitorServiceMock, system.getNotifierFactory(),
                _emProviderMock);

        DefaultAlertService alertService = spy(alertServiceOriginal);
        DefaultSchedulingService schedulingService = new DefaultSchedulingService(alertService,
                                                                                  system.getServiceFactory().getGlobalInterlockService(),
                                                                                  userService,
                                                                                  system.getServiceFactory().getServiceManagementService(),
                                                                                  system.getServiceFactory().getAuditService(),
                                                                                  system.getConfiguration());

        schedulingService.enableScheduling();

        int noOfAlerts = TestUtils.random.nextInt(2) + 9;
        PrincipalUser user = userService.findAdminUser();

        for (int i = 0; i < noOfAlerts; i++) {
            String expression = "DIVIDE(-1h:argus.jvm:file.descriptor.open{host=unknown-host}:avg, " +
                "-1h:argus.jvm:file.descriptor.max{host=unknown-host}:avg)";

            Alert alert = alertService.updateAlert(new Alert(user, user, TestUtils.createRandomName(), expression, "* * * * *"));
            alert.setEnabled(true);

            Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "testTrigger", 0, 0);
            alert.setTriggers(Arrays.asList(trigger));
    		Notification notification = new Notification("testNotification", alert, AuditNotifier.class.getName(), new ArrayList<String>(),
    				0);
    		alert.setNotifications(Arrays.asList(notification));

            alertService.updateAlert(alert);
        }
        DefaultSchedulingService.SchedulingThread schedulingThread = spy(schedulingService.new SchedulingThread("schedule-alerts", GlobalInterlockService.LockType.ALERT_SCHEDULING));
        // Return null so the inner doSchedule loop stops
        doReturn(null).when(schedulingThread).refreshMaster(any());
        schedulingThread.doSchedule();
        schedulingService.stopAlertScheduling();

        verify(schedulingThread, times(noOfAlerts)).doScheduleJob(any(), any());
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

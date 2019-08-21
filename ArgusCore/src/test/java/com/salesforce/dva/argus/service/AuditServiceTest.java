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
import com.salesforce.dva.argus.entity.Audit;
import com.salesforce.dva.argus.system.SystemException;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.AfterClass;
import com.salesforce.dva.argus.system.SystemMain;
import com.salesforce.dva.argus.TestUtils;

public class AuditServiceTest {

    static private SystemMain system;
    static AuditService auditService;
    static AlertService alertService;
    static UserService userService;

    @BeforeClass
    static public void setUpClass() {
        system = TestUtils.getInstance();
        system.start();
        auditService = system.getServiceFactory().getAuditService();
        alertService = system.getServiceFactory().getAlertService();
        userService = system.getServiceFactory().getUserService();
    }

    @AfterClass
    static public void tearDownClass() {
        if (system != null) {
            system.getServiceFactory().getManagementService().cleanupRecords();
            system.stop();
        }
    }

    private static final String expression =
        "DIVIDE(-1h:argus.jvm:file.descriptor.open{host=unknown-host}:avg, -1h:argus.jvm:file.descriptor.max{host=unknown-host}:avg)";

    @Test
    public void testCreateAudit() {
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "test_alert-name1", expression, "* * * * *");

        alert = alertService.updateAlert(alert);

        Audit expected = auditService.createAudit(new Audit("Test case execution", "test.salesforce.com", alert));
        Audit actual = auditService.findAuditByPrimaryKey(expected.getId());

        assertEquals(expected.getId(), actual.getId());
    }

    @Test(expected = SystemException.class)
    public void testUpdateAudit() {
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "test_alert-name2", expression, "* * * * *");

        alert = alertService.updateAlert(alert);

        Audit audit = auditService.createAudit(new Audit("Test case execution", "test.salesforce.com", alert));

        audit.setHostName("test2@salesforce.com");
        auditService.createAudit(audit);
    }

    @Test
    public void testFindByJPAEntity() {
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "test_alert-name3", expression, "* * * * *");

        alert = alertService.updateAlert(alert);

        int expectedAuditcount = TestUtils.random.nextInt(10) + 1;
        List<Audit> expectedResult = new ArrayList<>(auditService.findByEntity(alert.getId()));

        for (int i = 0; i < expectedAuditcount; i++) {
            expectedResult.add(auditService.createAudit(new Audit("Test case execution" + i, "test.salesforce.com", alert)));
        }

        List<Audit> actualRusits = auditService.findByEntity(alert.getId());

        assertTrue(actualRusits.containsAll(expectedResult));
    }

    @Test
    public void testFindByHostName() {
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "test_alert-name4", expression, "* * * * *");

        alert = alertService.updateAlert(alert);

        String hostName = "test@salesforce.com";
        int expectedAuditcount = TestUtils.random.nextInt(10) + 1;
        List<Audit> expectedResult = new ArrayList<Audit>();

        for (int i = 0; i < expectedAuditcount; i++) {
            expectedResult.add(auditService.createAudit(new Audit("Test case execution" + i, hostName, alert)));
        }

        List<Audit> actualRusits = auditService.findByHostName(hostName);

        assertTrue(actualRusits.containsAll(expectedResult));
    }

    @Test
    public void testFindAll() {
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "test_alert-name5", expression, "* * * * *");

        alert = alertService.updateAlert(alert);

        int expectedAuditcount = TestUtils.random.nextInt(10) + 1;
        List<Audit> expectedResult = new ArrayList<Audit>(auditService.findAll());

        for (int i = 0; i < expectedAuditcount; i++) {
            expectedResult.add(auditService.createAudit(new Audit("Test case execution" + i, "test@salesforce.com", alert)));
        }

        List<Audit> actualRusits = auditService.findAll();

        assertTrue(actualRusits.containsAll(expectedResult));
    }

    @Test
    public void testFindByMessage() {
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "test_alert-name6", expression, "* * * * *");

        alert = alertService.updateAlert(alert);

        List<Audit> expectedResult = new ArrayList<Audit>();
        int expectedAuditcount = TestUtils.random.nextInt(10) + 1;
        String message = "test_message";

        for (int i = 0; i < expectedAuditcount; i++) {
            expectedResult.add(auditService.createAudit(new Audit(TestUtils.createRandomName() + message + TestUtils.createRandomName(), "test@salesforce.com", alert)));
        }

        List<Audit> actualRusits = auditService.findByMessage(message);

        assertTrue(actualRusits.containsAll(expectedResult));
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

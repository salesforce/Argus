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
import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.entity.History.JobStatus;
import org.junit.Test;

import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

public class HistoryServiceTest extends AbstractTest {

    private static final String expression = "-1h:argus.jvm:file.descriptor.max{host=unknown-host}:avg";

    @Test
    public void testCreateHistory() {
        HistoryService historyService = system.getServiceFactory().getHistoryService();
        UserService userService = system.getServiceFactory().getUserService();
        AlertService alertService = system.getServiceFactory().getAlertService();
        Alert job = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name", expression, "* * * * *");

        job = alertService.updateAlert(job);

        History expectedHistory = new History("test", "hostname_test", job, JobStatus.SUCCESS, 0, 0);

        expectedHistory = historyService.updateHistory(expectedHistory);

        History actual = historyService.findHistoryByPrimaryKey(expectedHistory.getId());

        assertTrue(actual.equals(expectedHistory));
        historyService.deleteExpiredHistory();
        assertNotNull(historyService.findHistoryByPrimaryKey(expectedHistory.getId()));
    }

    @Test
    public void testFindByJob() {
        HistoryService historyService = system.getServiceFactory().getHistoryService();
        UserService userService = system.getServiceFactory().getUserService();
        AlertService alertService = system.getServiceFactory().getAlertService();
        Alert job = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name", expression, "* * * * *");

        job = alertService.updateAlert(job);

        History expectedHistory = new History("test", "hostname_test", job, JobStatus.SUCCESS, 0, 0);

        expectedHistory = historyService.updateHistory(expectedHistory);

        History actual = historyService.findByJob(job.getId()).get(0);

        assertTrue(actual.equals(expectedHistory));
    }

    @Test
    public void testFindByJobAndStatus() {
        HistoryService historyService = system.getServiceFactory().getHistoryService();
        UserService userService = system.getServiceFactory().getUserService();
        AlertService alertService = system.getServiceFactory().getAlertService();
        Alert job = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name", expression, "* * * * *");

        job = alertService.updateAlert(job);

        History expectedHistory = new History("test", "hostname_test", job, JobStatus.SUCCESS, 0, 0);

        expectedHistory = historyService.updateHistory(expectedHistory);

        History actual = historyService.findByJobAndStatus(job.getId(), null, JobStatus.SUCCESS).get(0);

        assertTrue(actual.equals(expectedHistory));
        assertEquals(0, historyService.findByJobAndStatus(job.getId(), null, JobStatus.FAILURE).size());
    }

    @Test
    public void testAppendMessage() {
        HistoryService historyService = system.getServiceFactory().getHistoryService();
        UserService userService = system.getServiceFactory().getUserService();
        AlertService alertService = system.getServiceFactory().getAlertService();
        Alert job = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name", expression, "* * * * *");

        job = alertService.updateAlert(job);

        History expectedHistory = new History("test1", "hostname_test", job, JobStatus.SUCCESS, 0, 0);

        expectedHistory = historyService.updateHistory(expectedHistory);

        History actual = historyService.appendMessageAndUpdate(expectedHistory.getId(), "test2", null, 0, 0);

        assertEquals("test1test2", actual.getMessage());
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

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

import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.IntegrationTest;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.entity.History.JobStatus;

@Category(IntegrationTest.class)
public class HistoryServiceIT extends AbstractTest {

    private static final String expression = "-1h:argus.jvm:file.descriptor.max{host=unknown-host}:avg";

    @Test
    public void testCreateHistory() {
        HistoryService historyService = system.getServiceFactory().getHistoryService();
        UserService userService = system.getServiceFactory().getUserService();
        AlertService alertService = system.getServiceFactory().getAlertService();
        Alert job = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name", expression, "* * * * *");

        job = alertService.updateAlert(job);

        History expected = new History("test", "hostname_test", job.getId(), JobStatus.SUCCESS, 0);

        History actual = historyService.createHistory(job, "test", JobStatus.SUCCESS, 0);
        expected.setCreationTime(actual.getCreationTime());
        
        assertEquals(expected, actual);
    }

    @Test
    public void testFindByJob() {
        HistoryService historyService = system.getServiceFactory().getHistoryService();
        UserService userService = system.getServiceFactory().getUserService();
        AlertService alertService = system.getServiceFactory().getAlertService();
        Alert job = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name", expression, "* * * * *");

        job = alertService.updateAlert(job);

        History expected = historyService.createHistory(job, "test", JobStatus.SUCCESS, 0);

        History actual = historyService.findByJob(job.getId(), 1).get(0);
        
        assertEquals(expected, actual);
    }

    @Test
    public void testFindByJobAndStatus() {
        HistoryService historyService = system.getServiceFactory().getHistoryService();
        UserService userService = system.getServiceFactory().getUserService();
        AlertService alertService = system.getServiceFactory().getAlertService();
        Alert job = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name", expression, "* * * * *");

        job = alertService.updateAlert(job);

        History expected = historyService.createHistory(job, "test", JobStatus.SUCCESS, 0);
        
        History actual = historyService.findByJobAndStatus(job.getId(), 1, JobStatus.SUCCESS).get(0);

        assertEquals(expected, actual);
        assertEquals(0, historyService.findByJobAndStatus(job.getId(), 1, JobStatus.FAILURE).size());
    }
    
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

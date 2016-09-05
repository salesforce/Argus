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

import com.salesforce.dva.argus.sdk.entity.Dashboard;
import org.junit.Test;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class DashboardServiceTest extends AbstractTest {

    @Test
    public void testCreateDashboard() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/DashboardServiceTest.json"))) {
            DashboardService dashboardService = argusService.getDashboardService();
            Dashboard result = dashboardService.createDashboard(_constructUnpersistedDashboard());
            Dashboard expected = _constructPersistedDashboard();

            assertEquals(expected, result);
        }
    }

    @Test
    public void testDeleteDashboard() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/DashboardServiceTest.json"))) {
            DashboardService dashboardService = argusService.getDashboardService();

            dashboardService.deleteDashboard(BigInteger.ONE);
        }
    }

    @Test
    public void testGetDashboard() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/DashboardServiceTest.json"))) {
            DashboardService dashboardService = argusService.getDashboardService();
            Dashboard result = dashboardService.getDashboard(BigInteger.ONE);
            Dashboard expected = _constructPersistedDashboard();

            assertEquals(expected, result);
        }
    }

    @Test
    public void testGetDashboards() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/DashboardServiceTest.json"))) {
            DashboardService batchService = argusService.getDashboardService();
            List<Dashboard> result = batchService.getDashboards();
            List<Dashboard> expected = Arrays.asList(new Dashboard[] { _constructPersistedDashboard() });

            assertEquals(expected, result);
        }
    }

    @Test
    public void testUpdateDashboard() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/DashboardServiceTest.json"))) {
            DashboardService dashboardService = argusService.getDashboardService();
            Dashboard updated = _constructUpdatedDashboard();
            Dashboard result = dashboardService.updateDashboard(BigInteger.ONE, updated);
            Dashboard expected = _constructUpdatedDashboard();

            assertEquals(expected, result);
        }
    }

    private Dashboard _constructPersistedDashboard() throws IOException {
        Dashboard result = _constructUnpersistedDashboard();

        result.setId(BigInteger.ONE);
        result.setCreatedById(BigInteger.ONE);
        result.setCreatedDate(new Date(1472282830936L));
        result.setModifiedById(BigInteger.ONE);
        result.setModifiedDate(new Date(1472282830936L));
        return result;
    }

    private Dashboard _constructUnpersistedDashboard() throws IOException {
        Dashboard result = new Dashboard();

        result.setContent("TestContent");
        result.setDescription("TestDescription");
        result.setName("TestName");
        result.setOwnerName("TestOwnerName");
        result.setShared(true);
        return result;
    }

    private Dashboard _constructUpdatedDashboard() throws IOException {
        Dashboard result = _constructPersistedDashboard();

        result.setContent("UpdatedContent");
        return result;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

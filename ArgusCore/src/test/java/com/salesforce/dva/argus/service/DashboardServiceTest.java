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
import com.salesforce.dva.argus.entity.Dashboard;
import com.salesforce.dva.argus.entity.PrincipalUser;
import org.junit.Before;
import org.junit.Test;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class DashboardServiceTest extends AbstractTest {

    DashboardService dService;
    UserService uService;

    @Before
    @Override
    public void setUp() {
        super.setUp();
        dService = system.getServiceFactory().getDashboardService();
        uService = system.getServiceFactory().getUserService();
    }

    @Test
    public void testDashboardCrud() {
        PrincipalUser owner = new PrincipalUser("owner", "owner@mycompany.abc");
        Dashboard dashboard = new Dashboard(uService.findAdminUser(), "Test Dashboard", owner);

        dashboard = dService.updateDashboard(dashboard);
        assertNotNull(dashboard.getId());
        owner = uService.findUserByUsername("owner");

        Dashboard dashboardRetrieved = dService.findDashboardByNameAndOwner("Test Dashboard", owner);

        assertEquals(dashboard.getId(), dashboardRetrieved.getId());
        dashboardRetrieved = dService.findDashboardByPrimaryKey(dashboard.getId());
        assertEquals(dashboard.getId(), dashboardRetrieved.getId());
        testReadDashboard(owner, "Test Dashboard");
        testDeleteDashboard(owner, "Test Dashboard");
    }

    @Test
    public void testDashboard_FindShareDelete() {
        PrincipalUser owner = new PrincipalUser("owner1", "owner1@mycompany.abc");
        Dashboard dashboard = new Dashboard(uService.findAdminUser(), "Test Dashboard", owner);

        dashboard.setShared(true);
        dashboard = dService.updateDashboard(dashboard);
        assertNotNull(dashboard.getId());
        owner = uService.findUserByUsername("owner1");

        List<Dashboard> dashboardRetrieved = dService.findDashboardsByOwner(owner, false);

        assertEquals(1, dashboardRetrieved.size());
        assertEquals(dashboard.getId(), dashboardRetrieved.get(0).getId());
        dashboardRetrieved = dService.findSharedDashboards(false);
        assertEquals(1, dashboardRetrieved.size());
        assertEquals(dashboard.getId(), dashboardRetrieved.get(0).getId());
        dService.deleteDashboard(dashboard.getId());
        assertNull(dService.findDashboardByPrimaryKey(dashboard.getId()));
    }
    
    @Test
    public void testFindDashboardsMeta() {
    	
    	PrincipalUser owner = new PrincipalUser("owner1", "owner1@mycompany.abc");
        Dashboard dashboard = new Dashboard(uService.findAdminUser(), "Test Dashboard", owner);

        dashboard.setShared(true);
        dashboard = dService.updateDashboard(dashboard);
        assertNotNull(dashboard.getId());
        
        List<Dashboard> retrieved = dService.findDashboards(1, true);
        assertEquals(1, retrieved.size());
        assertEquals(dashboard.getId(), retrieved.get(0).getId());
    }
    
    @Test
    public void testFindDashboardsByOwnerMeta() {
        PrincipalUser owner1 = new PrincipalUser("owner1", "owner1@mycompany.abc");
        PrincipalUser owner2 = new PrincipalUser("owner2", "owner2@mycompany.abc");
        
        Dashboard dashboard1 = new Dashboard(uService.findAdminUser(), "Test Dashboard", owner1);
        dashboard1 = dService.updateDashboard(dashboard1);
        assertNotNull(dashboard1.getId());
        
        Dashboard dashboard2 = new Dashboard(uService.findAdminUser(), "Test Dashboard", owner2);
        dashboard2 = dService.updateDashboard(dashboard2);
        assertNotNull(dashboard2.getId());
        
        owner1 = uService.findUserByUsername("owner1");

        List<Dashboard> dashboardsRetrieved = dService.findDashboardsByOwner(owner1, true);
        assertEquals(1, dashboardsRetrieved.size());
        assertEquals(dashboard1.getId(), dashboardsRetrieved.get(0).getId());
    }
    
    @Test
    public void testFindSharedDashboardsMeta() {
        PrincipalUser owner = new PrincipalUser("owner1", "owner1@mycompany.abc");
        
        Dashboard dashboard1 = new Dashboard(uService.findAdminUser(), "Test Dashboard1", owner);
        dashboard1.setShared(true);
        dashboard1 = dService.updateDashboard(dashboard1);
        
        owner = uService.findUserByUsername("owner1");
        
        Dashboard dashboard2 = new Dashboard(uService.findAdminUser(), "Test Dashboard2", owner);
        dashboard2 = dService.updateDashboard(dashboard2);
        
        assertNotNull(dashboard1.getId());
        assertNotNull(dashboard2.getId());
        
        List<Dashboard> dashboardsRetrieved = dService.findSharedDashboards(true);
        assertEquals(1, dashboardsRetrieved.size());
        assertEquals(dashboard1.getId(), dashboardsRetrieved.get(0).getId());
    }
    

    private void testReadDashboard(PrincipalUser owner, String dashboardName) {
        Dashboard dashboard = dService.findDashboardByNameAndOwner("Test Dashboard", owner);

        assertNotNull(dashboard);
    }

    private void testDeleteDashboard(PrincipalUser owner, String testboads) {
        Dashboard dashboard = dService.findDashboardByNameAndOwner("Test Dashboard", owner);

        dService.deleteDashboard(dashboard);
        assertNull(dService.findDashboardByPrimaryKey(dashboard.getId()));
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

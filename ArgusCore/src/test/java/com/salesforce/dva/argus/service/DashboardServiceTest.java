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

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public class DashboardServiceTest extends AbstractTest {

	private PrincipalUser admin;
    DashboardService dService;
    UserService uService;

    @Before
    @Override
    public void setUp() {
        super.setUp();
        dService = system.getServiceFactory().getDashboardService();
        uService = system.getServiceFactory().getUserService();
        admin = system.getServiceFactory().getUserService().findAdminUser();
    }

    @Test
    public void testDashboardCrud() {
        PrincipalUser owner = new PrincipalUser(admin, "owner", "owner@mycompany.abc");
        Dashboard dashboard = new Dashboard(uService.findAdminUser(), "Test Dashboard", owner);
        dashboard.setTemplateVars(Arrays.asList(new Dashboard.TemplateVar("scope", "argus.jvm")));

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
        PrincipalUser owner = new PrincipalUser(admin, "owner1", "owner1@mycompany.abc");
        Dashboard dashboard = new Dashboard(uService.findAdminUser(), "Test Dashboard", owner);

        dashboard.setShared(true);
        dashboard = dService.updateDashboard(dashboard);
        assertNotNull(dashboard.getId());
        owner = uService.findUserByUsername("owner1");

        List<Dashboard> dashboardRetrieved = dService.findDashboardsByOwner(owner, false, null);

        assertEquals(1, dashboardRetrieved.size());
        assertEquals(dashboard.getId(), dashboardRetrieved.get(0).getId());
        dashboardRetrieved = dService.findSharedDashboards(false, null, null, null);
        assertEquals(1, dashboardRetrieved.size());
        assertEquals(dashboard.getId(), dashboardRetrieved.get(0).getId());
        dService.deleteDashboard(dashboard.getId());
        assertNull(dService.findDashboardByPrimaryKey(dashboard.getId()));
    }

    @Test
    public void testDashboard_FindShareDeleteByVersion() {
        PrincipalUser owner = new PrincipalUser(admin, "owner1", "owner1@mycompany.abc");
        Dashboard dashboard = new Dashboard(uService.findAdminUser(), "Test Dashboard", owner);

        dashboard.setShared(true);
        dashboard.setVersion("v1");
        dashboard = dService.updateDashboard(dashboard);
        assertNotNull(dashboard.getId());
        owner = uService.findUserByUsername("owner1");

        List<Dashboard> dashboardRetrieved = dService.findDashboardsByOwner(owner, false, "v1");

        assertEquals(1, dashboardRetrieved.size());
        assertEquals(dashboard.getId(), dashboardRetrieved.get(0).getId());
        dashboardRetrieved = dService.findSharedDashboards(false, null, null, "v1");
        assertEquals(1, dashboardRetrieved.size());
        assertEquals(dashboard.getId(), dashboardRetrieved.get(0).getId());
        dService.deleteDashboard(dashboard.getId());
        assertNull(dService.findDashboardByPrimaryKey(dashboard.getId()));
    }
    
    @Test
    public void testFindDashboardsMeta() {
    	
    	PrincipalUser owner = new PrincipalUser(admin, "owner1", "owner1@mycompany.abc");
        Dashboard dashboard = new Dashboard(uService.findAdminUser(), "Test Dashboard", owner);

        dashboard.setShared(true);
        dashboard = dService.updateDashboard(dashboard);
        assertNotNull(dashboard.getId());
        
        List<Dashboard> retrieved = dService.findDashboards(1, true, null);
        assertEquals(1, retrieved.size());
        assertEquals(dashboard.getId(), retrieved.get(0).getId());
    }

    @Test
    public void testFindDashboardsMetaByVersion() {

        PrincipalUser owner = new PrincipalUser(admin, "owner1", "owner1@mycompany.abc");
        Dashboard dashboard = new Dashboard(uService.findAdminUser(), "Test Dashboard", owner);

        dashboard.setShared(true);
        dashboard.setVersion("v1");
        dashboard = dService.updateDashboard(dashboard);
        assertNotNull(dashboard.getId());

        List<Dashboard> retrieved = dService.findDashboards(1, true, "v1");
        assertEquals(1, retrieved.size());
        assertEquals(dashboard.getId(), retrieved.get(0).getId());
    }
    
    @Test
    public void testFindDashboardsByOwnerMeta() {
        PrincipalUser owner1 = new PrincipalUser(admin, "owner1", "owner1@mycompany.abc");
        PrincipalUser owner2 = new PrincipalUser(admin, "owner2", "owner2@mycompany.abc");
        
        Dashboard dashboard1 = new Dashboard(uService.findAdminUser(), "Test Dashboard", owner1);
        dashboard1 = dService.updateDashboard(dashboard1);
        assertNotNull(dashboard1.getId());
        
        Dashboard dashboard2 = new Dashboard(uService.findAdminUser(), "Test Dashboard", owner2);
        dashboard2 = dService.updateDashboard(dashboard2);
        assertNotNull(dashboard2.getId());
        
        owner1 = uService.findUserByUsername("owner1");

        List<Dashboard> dashboardsRetrieved = dService.findDashboardsByOwner(owner1, true, null);
        assertEquals(1, dashboardsRetrieved.size());
        assertEquals(dashboard1.getId(), dashboardsRetrieved.get(0).getId());
    }

    @Test
    public void testFindDashboardsByOwnerMetaAndByVersion() {
        PrincipalUser owner1 = new PrincipalUser(admin, "owner1", "owner1@mycompany.abc");
        PrincipalUser owner2 = new PrincipalUser(admin, "owner2", "owner2@mycompany.abc");

        Dashboard dashboard1 = new Dashboard(uService.findAdminUser(), "Test Dashboard", owner1);
        dashboard1.setVersion("v1");
        dashboard1 = dService.updateDashboard(dashboard1);
        assertNotNull(dashboard1.getId());

        Dashboard dashboard2 = new Dashboard(uService.findAdminUser(), "Test Dashboard", owner2);
        dashboard2.setVersion("v2");
        dashboard2 = dService.updateDashboard(dashboard2);
        assertNotNull(dashboard2.getId());

        owner1 = uService.findUserByUsername("owner1");

        List<Dashboard> dashboardsRetrieved = dService.findDashboardsByOwner(owner1, true, "v1");
        assertEquals(1, dashboardsRetrieved.size());
        assertEquals(dashboard1.getId(), dashboardsRetrieved.get(0).getId());
    }
    
    @Test
    public void testFindSharedDashboardsMeta() {
        PrincipalUser owner = new PrincipalUser(admin, "owner1", "owner1@mycompany.abc");
        
        Dashboard dashboard1 = new Dashboard(uService.findAdminUser(), "Test Dashboard1", owner);
        dashboard1.setShared(true);
        dashboard1 = dService.updateDashboard(dashboard1);
        
        owner = uService.findUserByUsername("owner1");
        
        Dashboard dashboard2 = new Dashboard(uService.findAdminUser(), "Test Dashboard2", owner);
        dashboard2 = dService.updateDashboard(dashboard2);
        
        assertNotNull(dashboard1.getId());
        assertNotNull(dashboard2.getId());
        
        List<Dashboard> dashboardsRetrieved = dService.findSharedDashboards(true, null, null, null);
        assertEquals(1, dashboardsRetrieved.size());
        assertEquals(dashboard1.getId(), dashboardsRetrieved.get(0).getId());
    }

    @Test
    public void testFindSharedDashboardsMetaByVersion() {
        PrincipalUser owner = new PrincipalUser(admin, "owner1", "owner1@mycompany.abc");

        Dashboard dashboard1 = new Dashboard(uService.findAdminUser(), "Test Dashboard1", owner);
        dashboard1.setShared(true);
        dashboard1.setVersion("v1");
        dashboard1 = dService.updateDashboard(dashboard1);

        owner = uService.findUserByUsername("owner1");

        Dashboard dashboard2 = new Dashboard(uService.findAdminUser(), "Test Dashboard2", owner);
        dashboard2.setVersion("v1");
        dashboard2 = dService.updateDashboard(dashboard2);

        assertNotNull(dashboard1.getId());
        assertNotNull(dashboard2.getId());

        List<Dashboard> dashboardsRetrieved = dService.findSharedDashboards(true, null, null, "v1");
        assertEquals(1, dashboardsRetrieved.size());
        assertEquals(dashboard1.getId(), dashboardsRetrieved.get(0).getId());
    }
    
    @Test
    public void testFindSharedDashboardsByOwner() {
        PrincipalUser owner1 = new PrincipalUser(admin, "owner1", "owner1@mycompany.abc");
        
        Dashboard dashboard1 = new Dashboard(uService.findAdminUser(), "Test Dashboard1", owner1);
        dashboard1.setShared(true);
        dashboard1 = dService.updateDashboard(dashboard1);
        
        owner1 = uService.findUserByUsername("owner1");
        
        Dashboard dashboard2 = new Dashboard(uService.findAdminUser(), "Test Dashboard2", owner1);
        dashboard2 = dService.updateDashboard(dashboard2);
        
        assertNotNull(dashboard1.getId());
        assertNotNull(dashboard2.getId());
        
        Dashboard dashboard3 = new Dashboard(uService.findAdminUser(), "Test Dashboard3", uService.findAdminUser());
        dashboard3.setShared(true);
        dashboard3 = dService.updateDashboard(dashboard3);
        
        Dashboard dashboard4 = new Dashboard(uService.findAdminUser(), "Test Dashboard4", uService.findAdminUser());
        dashboard4 = dService.updateDashboard(dashboard4);

        assertNotNull(dashboard3.getId());
        assertNotNull(dashboard4.getId());

        PrincipalUser owner2 = new PrincipalUser(admin, "owner2", "owner2@mycompany.abc");
        
        Dashboard dashboard5 = new Dashboard(uService.findAdminUser(), "Test Dashboard5", owner2);
        dashboard5.setShared(true);
        dashboard5 = dService.updateDashboard(dashboard5);
        
        owner2 = uService.findUserByUsername("owner2");
        Dashboard dashboard6 = new Dashboard(uService.findAdminUser(), "Test Dashboard6", owner2);
        dashboard6 = dService.updateDashboard(dashboard6);

        assertNotNull(dashboard5.getId());
        assertNotNull(dashboard6.getId());
        
        List<Dashboard> allSharedDashboardsRetrieved = dService.findSharedDashboards(false, null, null, null);
        assertEquals(3, allSharedDashboardsRetrieved.size());
        
        List<Dashboard> allSharedOwner1DashboardsRetrieved = dService.findSharedDashboards(false, owner1, null, null);
        assertEquals(1, allSharedOwner1DashboardsRetrieved.size());
        assertEquals(dashboard1.getId(), allSharedOwner1DashboardsRetrieved.get(0).getId());

        List<Dashboard> allSharedAdminDashboardsRetrieved = dService.findSharedDashboards(false, admin, null, null);
        assertEquals(1, allSharedAdminDashboardsRetrieved.size());
        assertEquals(dashboard3.getId(), allSharedAdminDashboardsRetrieved.get(0).getId());
        
        List<Dashboard> allSharedOwner2DashboardsRetrieved = dService.findSharedDashboards(false, owner2, null, null);
        assertEquals(1, allSharedOwner2DashboardsRetrieved.size());
        assertEquals(dashboard5.getId(), allSharedOwner2DashboardsRetrieved.get(0).getId());
    }


    @Test
    public void testFindSharedDashboardsByOwnerAndByVersion() {
        PrincipalUser owner1 = new PrincipalUser(admin, "owner1", "owner1@mycompany.abc");

        Dashboard dashboard1 = new Dashboard(uService.findAdminUser(), "Test Dashboard1", owner1);
        dashboard1.setVersion("v1");
        dashboard1.setShared(true);
        dashboard1 = dService.updateDashboard(dashboard1);

        owner1 = uService.findUserByUsername("owner1");

        Dashboard dashboard2 = new Dashboard(uService.findAdminUser(), "Test Dashboard2", owner1);
        dashboard2.setVersion("v1");
        dashboard2 = dService.updateDashboard(dashboard2);

        assertNotNull(dashboard1.getId());
        assertNotNull(dashboard2.getId());

        Dashboard dashboard3 = new Dashboard(uService.findAdminUser(), "Test Dashboard3", uService.findAdminUser());
        dashboard3.setVersion("v1");
        dashboard3.setShared(true);
        dashboard3 = dService.updateDashboard(dashboard3);

        Dashboard dashboard4 = new Dashboard(uService.findAdminUser(), "Test Dashboard4", uService.findAdminUser());
        dashboard4.setVersion("v1");
        dashboard4 = dService.updateDashboard(dashboard4);

        assertNotNull(dashboard3.getId());
        assertNotNull(dashboard4.getId());

        PrincipalUser owner2 = new PrincipalUser(admin, "owner2", "owner2@mycompany.abc");

        Dashboard dashboard5 = new Dashboard(uService.findAdminUser(), "Test Dashboard5", owner2);
        dashboard5.setVersion("v1");
        dashboard5.setShared(true);
        dashboard5 = dService.updateDashboard(dashboard5);

        owner2 = uService.findUserByUsername("owner2");
        Dashboard dashboard6 = new Dashboard(uService.findAdminUser(), "Test Dashboard6", owner2);
        dashboard6.setVersion("v1");
        dashboard6 = dService.updateDashboard(dashboard6);

        assertNotNull(dashboard5.getId());
        assertNotNull(dashboard6.getId());

        List<Dashboard> allSharedDashboardsRetrieved = dService.findSharedDashboards(false, null, null, "v1");
        assertEquals(3, allSharedDashboardsRetrieved.size());

        List<Dashboard> allSharedOwner1DashboardsRetrieved = dService.findSharedDashboards(false, owner1, null, "v1");
        assertEquals(1, allSharedOwner1DashboardsRetrieved.size());
        assertEquals(dashboard1.getId(), allSharedOwner1DashboardsRetrieved.get(0).getId());

        List<Dashboard> allSharedAdminDashboardsRetrieved = dService.findSharedDashboards(false, admin, null, "v1");
        assertEquals(1, allSharedAdminDashboardsRetrieved.size());
        assertEquals(dashboard3.getId(), allSharedAdminDashboardsRetrieved.get(0).getId());

        List<Dashboard> allSharedOwner2DashboardsRetrieved = dService.findSharedDashboards(false, owner2, null, "v1");
        assertEquals(1, allSharedOwner2DashboardsRetrieved.size());
        assertEquals(dashboard5.getId(), allSharedOwner2DashboardsRetrieved.get(0).getId());
    }

    @Test
    public void testFindSharedDashboardsMetaByOwner() {
        PrincipalUser owner1 = new PrincipalUser(admin, "owner1", "owner1@mycompany.abc");

        Dashboard dashboard1 = new Dashboard(uService.findAdminUser(), "Test Dashboard1", owner1);
        dashboard1.setShared(true);
        dashboard1 = dService.updateDashboard(dashboard1);

        owner1 = uService.findUserByUsername("owner1");

        Dashboard dashboard2 = new Dashboard(uService.findAdminUser(), "Test Dashboard2", owner1);
        dashboard2 = dService.updateDashboard(dashboard2);

        assertNotNull(dashboard1.getId());
        assertNotNull(dashboard2.getId());

        Dashboard dashboard3 = new Dashboard(uService.findAdminUser(), "Test Dashboard3", uService.findAdminUser());
        dashboard3.setShared(true);
        dashboard3 = dService.updateDashboard(dashboard3);

        Dashboard dashboard4 = new Dashboard(uService.findAdminUser(), "Test Dashboard4", uService.findAdminUser());
        dashboard4 = dService.updateDashboard(dashboard4);

        assertNotNull(dashboard3.getId());
        assertNotNull(dashboard4.getId());

        PrincipalUser owner2 = new PrincipalUser(admin, "owner2", "owner2@mycompany.abc");

        Dashboard dashboard5 = new Dashboard(uService.findAdminUser(), "Test Dashboard5", owner2);
        dashboard5.setShared(true);
        dashboard5 = dService.updateDashboard(dashboard5);

        owner2 = uService.findUserByUsername("owner2");
        Dashboard dashboard6 = new Dashboard(uService.findAdminUser(), "Test Dashboard6", owner2);
        dashboard6 = dService.updateDashboard(dashboard6);

        assertNotNull(dashboard5.getId());
        assertNotNull(dashboard6.getId());

        List<Dashboard> allSharedDashboardsRetrieved = dService.findSharedDashboards(true, null, null, null);
        assertEquals(3, allSharedDashboardsRetrieved.size());

        List<Dashboard> allSharedOwner1DashboardsRetrieved = dService.findSharedDashboards(true, owner1, null, null);
        assertEquals(1, allSharedOwner1DashboardsRetrieved.size());
        assertEquals(dashboard1.getId(), allSharedOwner1DashboardsRetrieved.get(0).getId());

        List<Dashboard> allSharedAdminDashboardsRetrieved = dService.findSharedDashboards(true, admin, null, null);
        assertEquals(1, allSharedAdminDashboardsRetrieved.size());
        assertEquals(dashboard3.getId(), allSharedAdminDashboardsRetrieved.get(0).getId());

        List<Dashboard> allSharedOwner2DashboardsRetrieved = dService.findSharedDashboards(true, owner2, null, null);
        assertEquals(1, allSharedOwner2DashboardsRetrieved.size());
        assertEquals(dashboard5.getId(), allSharedOwner2DashboardsRetrieved.get(0).getId());
    }

    @Test
    public void testFindSharedDashboardsMetaByOwnerAndByVersion() {
        PrincipalUser owner1 = new PrincipalUser(admin, "owner1", "owner1@mycompany.abc");

        Dashboard dashboard1 = new Dashboard(uService.findAdminUser(), "Test Dashboard1", owner1);
        dashboard1.setVersion("v1");
        dashboard1.setShared(true);
        dashboard1 = dService.updateDashboard(dashboard1);

        owner1 = uService.findUserByUsername("owner1");

        Dashboard dashboard2 = new Dashboard(uService.findAdminUser(), "Test Dashboard2", owner1);
        dashboard2.setVersion("v1");
        dashboard2 = dService.updateDashboard(dashboard2);

        assertNotNull(dashboard1.getId());
        assertNotNull(dashboard2.getId());

        Dashboard dashboard3 = new Dashboard(uService.findAdminUser(), "Test Dashboard3", uService.findAdminUser());
        dashboard3.setVersion("v1");
        dashboard3.setShared(true);
        dashboard3 = dService.updateDashboard(dashboard3);

        Dashboard dashboard4 = new Dashboard(uService.findAdminUser(), "Test Dashboard4", uService.findAdminUser());
        dashboard4.setVersion("v1");
        dashboard4 = dService.updateDashboard(dashboard4);

        assertNotNull(dashboard3.getId());
        assertNotNull(dashboard4.getId());

        PrincipalUser owner2 = new PrincipalUser(admin, "owner2", "owner2@mycompany.abc");

        Dashboard dashboard5 = new Dashboard(uService.findAdminUser(), "Test Dashboard5", owner2);
        dashboard5.setVersion("v1");
        dashboard5.setShared(true);
        dashboard5 = dService.updateDashboard(dashboard5);

        owner2 = uService.findUserByUsername("owner2");
        Dashboard dashboard6 = new Dashboard(uService.findAdminUser(), "Test Dashboard6", owner2);
        dashboard6.setVersion("v1");
        dashboard6 = dService.updateDashboard(dashboard6);

        assertNotNull(dashboard5.getId());
        assertNotNull(dashboard6.getId());

        List<Dashboard> allSharedDashboardsRetrieved = dService.findSharedDashboards(true, null, null, "v1");
        assertEquals(3, allSharedDashboardsRetrieved.size());

        List<Dashboard> allSharedOwner1DashboardsRetrieved = dService.findSharedDashboards(true, owner1, null, "v1");
        assertEquals(1, allSharedOwner1DashboardsRetrieved.size());
        assertEquals(dashboard1.getId(), allSharedOwner1DashboardsRetrieved.get(0).getId());

        List<Dashboard> allSharedAdminDashboardsRetrieved = dService.findSharedDashboards(true, admin, null, "v1");
        assertEquals(1, allSharedAdminDashboardsRetrieved.size());
        assertEquals(dashboard3.getId(), allSharedAdminDashboardsRetrieved.get(0).getId());

        List<Dashboard> allSharedOwner2DashboardsRetrieved = dService.findSharedDashboards(true, owner2, null, "v1");
        assertEquals(1, allSharedOwner2DashboardsRetrieved.size());
        assertEquals(dashboard5.getId(), allSharedOwner2DashboardsRetrieved.get(0).getId());
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

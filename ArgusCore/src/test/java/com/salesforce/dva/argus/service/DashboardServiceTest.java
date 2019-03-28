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

import com.salesforce.dva.argus.entity.Dashboard;
import com.salesforce.dva.argus.entity.PrincipalUser;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Before;
import org.junit.After;
import org.junit.BeforeClass;
import org.junit.AfterClass;
import com.salesforce.dva.argus.system.SystemMain;
import com.salesforce.dva.argus.TestUtils;
import java.sql.DriverManager;
import java.sql.SQLNonTransientConnectionException;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.fail;


public class DashboardServiceTest {

    static private PrincipalUser admin;
    static DashboardService dService;
    static UserService uService;

    static private SystemMain system;

    static {
        ch.qos.logback.classic.Logger apacheLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.apache");
        apacheLogger.setLevel(ch.qos.logback.classic.Level.OFF);
    }

    @BeforeClass
    static public void setUpClass() {
    }

    @AfterClass
    static public void tearDownClass() {
    }

    @Before
    public void setUp() {
        try {
            Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
            DriverManager.getConnection("jdbc:derby:memory:argus;create=true").close();
        } catch (Exception ex) {
            LoggerFactory.getLogger(getClass()).error("Exception in setUp:{}", ex.getMessage());
            fail("Exception during database startup.");
        }
        system = TestUtils.getInstance();
        system.start();
        dService = system.getServiceFactory().getDashboardService();
        uService = system.getServiceFactory().getUserService();
        admin = system.getServiceFactory().getUserService().findAdminUser();
    }

    @After
    public void tearDown() {
        if (system != null) {
            system.getServiceFactory().getManagementService().cleanupRecords();
            system.stop();
        }
        try {
            DriverManager.getConnection("jdbc:derby:memory:argus;shutdown=true").close();
        } catch (SQLNonTransientConnectionException ex) {
            if (ex.getErrorCode() >= 50000 || ex.getErrorCode() < 40000) {
                throw new RuntimeException(ex);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }


    @Test
    public void testDashboardCrud() {
        String ownerName = "owner-" + TestUtils.createRandomName();
        PrincipalUser owner = new PrincipalUser(admin, ownerName, "owner1@mycompany.abc");
        String dashName = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard = new Dashboard(uService.findAdminUser(), dashName, owner);
        dashboard.setTemplateVars(Arrays.asList(new Dashboard.TemplateVar("scope", "argus.jvm")));

        dashboard = dService.updateDashboard(dashboard);
        assertNotNull(dashboard.getId());
        owner = uService.findUserByUsername(ownerName);

        Dashboard dashboardRetrieved = dService.findDashboardByNameAndOwner(dashName, owner);

        assertEquals(dashboard.getId(), dashboardRetrieved.getId());
        dashboardRetrieved = dService.findDashboardByPrimaryKey(dashboard.getId());
        assertEquals(dashboard.getId(), dashboardRetrieved.getId());
        testReadDashboard(owner, dashName);
        testDeleteDashboard(owner, dashName);
    }

    @Test
    public void testDashboard_FindShareDelete() {
        String ownerName = "owner-" + TestUtils.createRandomName();
        PrincipalUser owner = new PrincipalUser(admin, ownerName, "ownerb@mycompany.abc");
        String dashName = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard = new Dashboard(uService.findAdminUser(), dashName, owner);

        dashboard.setShared(true);
        dashboard = dService.updateDashboard(dashboard);
        assertNotNull(dashboard.getId());
        owner = uService.findUserByUsername(ownerName);

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
        String ownerName = "owner-" + TestUtils.createRandomName();
        PrincipalUser owner = new PrincipalUser(admin, ownerName, "ownerc@mycompany.abc");
        String dashName = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard = new Dashboard(uService.findAdminUser(), dashName, owner);

        dashboard.setShared(true);
        dashboard.setVersion("v1");
        dashboard = dService.updateDashboard(dashboard);
        assertNotNull(dashboard.getId());
        owner = uService.findUserByUsername(ownerName);

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

        String ownerName = "owner-" + TestUtils.createRandomName();
        PrincipalUser owner = new PrincipalUser(admin, ownerName, "ownerd@mycompany.abc");
        String dashName = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard = new Dashboard(uService.findAdminUser(), dashName, owner);

        dashboard.setShared(true);
        dashboard = dService.updateDashboard(dashboard);
        assertNotNull(dashboard.getId());

        List<Dashboard> retrieved = dService.findDashboards(1, true, null);
        assertEquals(1, retrieved.size());
        assertEquals(dashboard.getId(), retrieved.get(0).getId());
    }

    @Test
    public void testFindDashboardsMetaByVersion() {

        String ownerName = "owner-" + TestUtils.createRandomName();
        PrincipalUser owner = new PrincipalUser(admin, ownerName, "ownere@mycompany.abc");
        String dashName = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard = new Dashboard(uService.findAdminUser(), dashName, owner);

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
        String ownerName1 = "owner-" + TestUtils.createRandomName();
        PrincipalUser owner1 = new PrincipalUser(admin, ownerName1, "ownerf@mycompany.abc");
        String ownerName2 = "owner-" + TestUtils.createRandomName();
        PrincipalUser owner2 = new PrincipalUser(admin, ownerName2, "ownerg@mycompany.abc");

        String dashName1 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard1 = new Dashboard(uService.findAdminUser(), dashName1, owner1);
        dashboard1 = dService.updateDashboard(dashboard1);
        assertNotNull(dashboard1.getId());

        String dashName2 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard2 = new Dashboard(uService.findAdminUser(), dashName2, owner2);
        dashboard2 = dService.updateDashboard(dashboard2);
        assertNotNull(dashboard2.getId());

        owner1 = uService.findUserByUsername(ownerName1);

        List<Dashboard> dashboardsRetrieved = dService.findDashboardsByOwner(owner1, true, null);
        assertEquals(1, dashboardsRetrieved.size());
        assertEquals(dashboard1.getId(), dashboardsRetrieved.get(0).getId());
    }

    @Test
    public void testFindDashboardsByOwnerMetaAndByVersion() {
        String ownerName1 = "owner-" + TestUtils.createRandomName();
        PrincipalUser owner1 = new PrincipalUser(admin, ownerName1, "ownerh@mycompany.abc");
        String ownerName2 = "owner-" + TestUtils.createRandomName();
        PrincipalUser owner2 = new PrincipalUser(admin, ownerName2, "owneri@mycompany.abc");

        String dashName1 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard1 = new Dashboard(uService.findAdminUser(), dashName1, owner1);
        dashboard1.setVersion("v1");
        dashboard1 = dService.updateDashboard(dashboard1);
        assertNotNull(dashboard1.getId());

        String dashName2 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard2 = new Dashboard(uService.findAdminUser(), dashName2, owner2);
        dashboard2.setVersion("v2");
        dashboard2 = dService.updateDashboard(dashboard2);
        assertNotNull(dashboard2.getId());

        owner1 = uService.findUserByUsername(ownerName1);

        List<Dashboard> dashboardsRetrieved = dService.findDashboardsByOwner(owner1, true, "v1");
        assertEquals(1, dashboardsRetrieved.size());
        assertEquals(dashboard1.getId(), dashboardsRetrieved.get(0).getId());
    }

    @Test
    public void testFindSharedDashboardsMeta() {
        String ownerName = "owner-" + TestUtils.createRandomName();
        PrincipalUser owner = new PrincipalUser(admin, ownerName, "ownerj@mycompany.abc");

        String dashName1 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard1 = new Dashboard(uService.findAdminUser(), dashName1, owner);
        dashboard1.setShared(true);
        dashboard1 = dService.updateDashboard(dashboard1);

        owner = uService.findUserByUsername(ownerName);

        String dashName2 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard2 = new Dashboard(uService.findAdminUser(), dashName2, owner);
        dashboard2 = dService.updateDashboard(dashboard2);

        assertNotNull(dashboard1.getId());
        assertNotNull(dashboard2.getId());

        List<Dashboard> dashboardsRetrieved = dService.findSharedDashboards(true, null, null, null);
        assertEquals(1, dashboardsRetrieved.size());
        assertEquals(dashboard1.getId(), dashboardsRetrieved.get(0).getId());
    }

    @Test
    public void testFindSharedDashboardsMetaByVersion() {
        String ownerName = "owner-" + TestUtils.createRandomName();
        PrincipalUser owner = new PrincipalUser(admin, ownerName, "owners@mycompany.abc");

        String dashName1 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard1 = new Dashboard(uService.findAdminUser(), dashName1, owner);
        dashboard1.setShared(true);
        dashboard1.setVersion("v1");
        dashboard1 = dService.updateDashboard(dashboard1);

        owner = uService.findUserByUsername(ownerName);

        String dashName2 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard2 = new Dashboard(uService.findAdminUser(), dashName2, owner);
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
        String ownerName1 = "owner-" + TestUtils.createRandomName();
        PrincipalUser owner1 = new PrincipalUser(admin, ownerName1, "ownerk@mycompany.abc");

        String dashName1 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard1 = new Dashboard(uService.findAdminUser(), dashName1, owner1);
        dashboard1.setShared(true);
        dashboard1 = dService.updateDashboard(dashboard1);

        owner1 = uService.findUserByUsername(ownerName1);

        String dashName2 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard2 = new Dashboard(uService.findAdminUser(), dashName2, owner1);
        dashboard2 = dService.updateDashboard(dashboard2);

        assertNotNull(dashboard1.getId());
        assertNotNull(dashboard2.getId());

        String dashName3 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard3 = new Dashboard(uService.findAdminUser(), dashName3, uService.findAdminUser());
        dashboard3.setShared(true);
        dashboard3 = dService.updateDashboard(dashboard3);

        String dashName4 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard4 = new Dashboard(uService.findAdminUser(), dashName4, uService.findAdminUser());
        dashboard4 = dService.updateDashboard(dashboard4);

        assertNotNull(dashboard3.getId());
        assertNotNull(dashboard4.getId());

        String ownerName2 = "owner-" + TestUtils.createRandomName();
        PrincipalUser owner2 = new PrincipalUser(admin, ownerName2, "ownerl@mycompany.abc");

        String dashName5 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard5 = new Dashboard(uService.findAdminUser(), dashName5, owner2);
        dashboard5.setShared(true);
        dashboard5 = dService.updateDashboard(dashboard5);

        owner2 = uService.findUserByUsername(ownerName2);
        String dashName6 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard6 = new Dashboard(uService.findAdminUser(), dashName6, owner2);
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
        String ownerName1 = "owner-" + TestUtils.createRandomName();
        PrincipalUser owner1 = new PrincipalUser(admin, ownerName1, "ownerm@mycompany.abc");

        String dashName1 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard1 = new Dashboard(uService.findAdminUser(), dashName1, owner1);
        dashboard1.setVersion("v1");
        dashboard1.setShared(true);
        dashboard1 = dService.updateDashboard(dashboard1);

        owner1 = uService.findUserByUsername(ownerName1);

        String dashName2 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard2 = new Dashboard(uService.findAdminUser(), dashName2, owner1);
        dashboard2.setVersion("v1");
        dashboard2 = dService.updateDashboard(dashboard2);

        assertNotNull(dashboard1.getId());
        assertNotNull(dashboard2.getId());

        String dashName3 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard3 = new Dashboard(uService.findAdminUser(), dashName3, uService.findAdminUser());
        dashboard3.setVersion("v1");
        dashboard3.setShared(true);
        dashboard3 = dService.updateDashboard(dashboard3);

        String dashName4 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard4 = new Dashboard(uService.findAdminUser(), dashName4, uService.findAdminUser());
        dashboard4.setVersion("v1");
        dashboard4 = dService.updateDashboard(dashboard4);

        assertNotNull(dashboard3.getId());
        assertNotNull(dashboard4.getId());

        String ownerName2 = "owner-" + TestUtils.createRandomName();
        PrincipalUser owner2 = new PrincipalUser(admin, ownerName2, "ownern@mycompany.abc");

        String dashName5 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard5 = new Dashboard(uService.findAdminUser(), dashName5, owner2);
        dashboard5.setVersion("v1");
        dashboard5.setShared(true);
        dashboard5 = dService.updateDashboard(dashboard5);

        owner2 = uService.findUserByUsername(ownerName2);
        String dashName6 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard6 = new Dashboard(uService.findAdminUser(), dashName6, owner2);
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
        String ownerName1 = "owner-" + TestUtils.createRandomName();
        PrincipalUser owner1 = new PrincipalUser(admin, ownerName1, "ownero@mycompany.abc");

        String dashName1 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard1 = new Dashboard(uService.findAdminUser(), dashName1, owner1);
        dashboard1.setShared(true);
        dashboard1 = dService.updateDashboard(dashboard1);

        owner1 = uService.findUserByUsername(ownerName1);

        String dashName2 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard2 = new Dashboard(uService.findAdminUser(), dashName2, owner1);
        dashboard2 = dService.updateDashboard(dashboard2);

        assertNotNull(dashboard1.getId());
        assertNotNull(dashboard2.getId());

        String dashName3 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard3 = new Dashboard(uService.findAdminUser(), dashName3, uService.findAdminUser());
        dashboard3.setShared(true);
        dashboard3 = dService.updateDashboard(dashboard3);

        String dashName4 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard4 = new Dashboard(uService.findAdminUser(), dashName4, uService.findAdminUser());
        dashboard4 = dService.updateDashboard(dashboard4);

        assertNotNull(dashboard3.getId());
        assertNotNull(dashboard4.getId());

        String ownerName2 = "owner-" + TestUtils.createRandomName();
        PrincipalUser owner2 = new PrincipalUser(admin, ownerName2, "ownerp@mycompany.abc");

        String dashName5 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard5 = new Dashboard(uService.findAdminUser(), dashName5, owner2);
        dashboard5.setShared(true);
        dashboard5 = dService.updateDashboard(dashboard5);

        owner2 = uService.findUserByUsername(ownerName2);
        String dashName6 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard6 = new Dashboard(uService.findAdminUser(), dashName6, owner2);
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
        String ownerName1 = "owner-" + TestUtils.createRandomName();
        PrincipalUser owner1 = new PrincipalUser(admin, ownerName1, "ownerq@mycompany.abc");

        String dashName1 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard1 = new Dashboard(uService.findAdminUser(), dashName1, owner1);
        dashboard1.setVersion("v1");
        dashboard1.setShared(true);
        dashboard1 = dService.updateDashboard(dashboard1);

        owner1 = uService.findUserByUsername(ownerName1);

        String dashName2 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard2 = new Dashboard(uService.findAdminUser(), dashName2, owner1);
        dashboard2.setVersion("v1");
        dashboard2 = dService.updateDashboard(dashboard2);

        assertNotNull(dashboard1.getId());
        assertNotNull(dashboard2.getId());

        String dashName3 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard3 = new Dashboard(uService.findAdminUser(), dashName3, uService.findAdminUser());
        dashboard3.setVersion("v1");
        dashboard3.setShared(true);
        dashboard3 = dService.updateDashboard(dashboard3);

        String dashName4 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard4 = new Dashboard(uService.findAdminUser(), dashName4, uService.findAdminUser());
        dashboard4.setVersion("v1");
        dashboard4 = dService.updateDashboard(dashboard4);

        assertNotNull(dashboard3.getId());
        assertNotNull(dashboard4.getId());

        String ownerName2 = "owner-" + TestUtils.createRandomName();
        PrincipalUser owner2 = new PrincipalUser(admin, ownerName2, "ownerr@mycompany.abc");

        String dashName5 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard5 = new Dashboard(uService.findAdminUser(), dashName5, owner2);
        dashboard5.setVersion("v1");
        dashboard5.setShared(true);
        dashboard5 = dService.updateDashboard(dashboard5);

        owner2 = uService.findUserByUsername(ownerName2);
        String dashName6 = "dash-" + TestUtils.createRandomName();
        Dashboard dashboard6 = new Dashboard(uService.findAdminUser(), dashName6, owner2);
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
        Dashboard dashboard = dService.findDashboardByNameAndOwner(dashboardName, owner);

        assertNotNull(dashboard);
    }

    private void testDeleteDashboard(PrincipalUser owner, String testboads) {
        Dashboard dashboard = dService.findDashboardByNameAndOwner(testboads, owner);

        dService.deleteDashboard(dashboard);
        assertNull(dService.findDashboardByPrimaryKey(dashboard.getId()));
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

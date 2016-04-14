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
	 
package com.salesforce.dva.argus.service.warden;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Dashboard;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.service.WardenService;
import com.salesforce.dva.argus.service.WardenService.SubSystem;

/**
 * Test for dummy warden service.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */

public class DummyWardenServiceTest extends AbstractTest{
	@Test
    public void testDispose() {
		WardenService dummyWardenService = (WardenService) new DummyWardenService();
		dummyWardenService.dispose();
		assertTrue(dummyWardenService.isDisposed());
    }
	@Test
    public void testEnable() {
		WardenService dummyWardenService = (WardenService) new DummyWardenService();
		dummyWardenService.enableWarden();
		assertTrue(dummyWardenService.isWardenServiceEnabled());
    }
	@Test
    public void testDisable() {
		WardenService dummyWardenService = (WardenService) new DummyWardenService();
		dummyWardenService.disableWarden();
		assertFalse(dummyWardenService.isWardenServiceEnabled());
    }
    @Test
    public void testSuspendAdminUser() {
    	WardenService dummyWardenService = new DummyWardenService();
    	UserService userService = system.getServiceFactory().getUserService();
    	PrincipalUser admin = userService.findAdminUser();
    	dummyWardenService.suspendUser(admin, SubSystem.API);
    	dummyWardenService.assertSubSystemUsePermitted(admin, SubSystem.API);
    }

    @Test
    public void testReinstateAdminUser() {
    	testSuspendAdminUser();
    	WardenService dummyWardenService = new DummyWardenService();
    	UserService userService = system.getServiceFactory().getUserService();
    	PrincipalUser admin = userService.findAdminUser();
    	dummyWardenService.reinstateUser(admin, SubSystem.API);
    	dummyWardenService.assertSubSystemUsePermitted(admin, SubSystem.API);
    }

    @Test
    public void testGetWardenDashboard(){
    	WardenService dummyWardenService = (WardenService) new DummyWardenService();
    	Dashboard dashboard = dummyWardenService.getWardenDashboard(null);
    	assertEquals(dashboard, null);
    }   
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

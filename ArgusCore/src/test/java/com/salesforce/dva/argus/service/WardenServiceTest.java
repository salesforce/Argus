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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.junit.Before;
import org.junit.Test;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.service.WardenService.PolicyCounter;
import com.salesforce.dva.argus.service.WardenService.SubSystem;
import com.salesforce.dva.argus.service.warden.DefaultWardenService;
import com.salesforce.dva.argus.system.SystemException;

public class WardenServiceTest extends AbstractTest {

    private UserService _userService;
    private WardenService _wardenService;
    private AlertService _alertService;

    @Before
    @Override
    public void setUp() {
        super.setUp();
        _userService = system.getServiceFactory().getUserService();
        _wardenService = system.getServiceFactory().getWardenService();
        _alertService = system.getServiceFactory().getAlertService();

        PrincipalUser user = _userService.findUserByUsername("bhinav.sura");

        if (user == null) {
            user = new PrincipalUser("bhinav.sura", "bhinav.sura@salesforce.com");
            user = _userService.updateUser(user);
        }
        _wardenService.reinstateUser(user, SubSystem.API);
        _wardenService.reinstateUser(user, SubSystem.POSTING);

        SubSystem[] subSystems = SubSystem.values();

        for (SubSystem ss : subSystems) {
            Map<Integer, Long> levels = new HashMap<>();

            levels.put(1, 10 * 60 * 1000L);
            levels.put(2, 30 * 60 * 1000L);
            levels.put(3, 60 * 60 * 1000L);
            levels.put(4, 10 * 60 * 60 * 1000L);
            levels.put(5, 24 * 60 * 60 * 1000L);
            _wardenService.updateSuspensionLevels(ss, levels);
        }
    }

    @Test
    public void testServiceIsSingleton() {
        assertTrue(_wardenService == system.getServiceFactory().getWardenService());
    }

    @Test
    public void testSuspendAdminUser() {
        assertFalse(_wardenService.suspendUser(_userService.findAdminUser(), SubSystem.POSTING));
    }

    @Test
    public void testSuspendUser() {
        PrincipalUser user = _userService.findUserByUsername("bhinav.sura");
        boolean isIndefinitelySuspended = _wardenService.suspendUser(user, SubSystem.API);

        assertFalse(isIndefinitelySuspended);
    }

    @Test
    public void testSuspendUserIndefinitely() {
        PrincipalUser user = _userService.findUserByUsername("bhinav.sura");
        boolean isIndefinitelySuspended = true;

        for (int i = 0; i < 14; i++) {
            int index = random.nextInt(SubSystem.values().length);
            SubSystem subSystem = SubSystem.values()[index];

            isIndefinitelySuspended = _wardenService.suspendUser(user, subSystem);
        }
        assertFalse(isIndefinitelySuspended);
        isIndefinitelySuspended = _wardenService.suspendUser(user, SubSystem.API);
        assertTrue(isIndefinitelySuspended);
        isIndefinitelySuspended = _wardenService.suspendUser(user, SubSystem.API);
        assertTrue(isIndefinitelySuspended);
    }

    @Test
    public void testAssertSubsystemUsePermitted_AdminUser() {
        _wardenService.assertSubSystemUsePermitted(_userService.findAdminUser(), SubSystem.API);
        assertTrue(true);
    }

    @Test
    public void testAssertSubsystemUsePermitted_NoSuspension() {
        PrincipalUser user = _userService.findUserByUsername("bhinav.sura");

        _wardenService.assertSubSystemUsePermitted(user, SubSystem.API);
        assertTrue(true);
    }

    @Test(expected = SystemException.class)
    public void testAssertSubsystemUsePermitted_IndefiniteSuspension() {
        PrincipalUser user = _userService.findUserByUsername("bhinav.sura");

        for (int i = 0; i < 15; i++) {
            int index = random.nextInt(SubSystem.values().length);
            SubSystem subSystem = SubSystem.values()[index];

            _wardenService.suspendUser(user, subSystem);
        }
        _wardenService.assertSubSystemUsePermitted(user, SubSystem.API);
    }

    @Test(expected = SystemException.class)
    public void testAssertSubsystemUsePermitted_NonIndefiniteSuspension() {
        PrincipalUser user = _userService.findUserByUsername("bhinav.sura");

        _wardenService.suspendUser(user, SubSystem.API);
        _wardenService.assertSubSystemUsePermitted(user, SubSystem.API);
    }

    @Test
    public void testAssertSubsystemUsePermitted_ExpiredSuspension() {
        PrincipalUser user = _userService.findUserByUsername("bhinav.sura");

        _wardenService.updateSuspensionLevel(SubSystem.API, 1, 5 * 1000L);
        _wardenService.suspendUser(user, SubSystem.API);
        try {
            Thread.sleep(6 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        _wardenService.assertSubSystemUsePermitted(user, SubSystem.API);
        assertTrue(true);
    }

    @Test
    public void testUpdatePolicyCounterEnablesAlert() {
        _wardenService.updatePolicyCounter(_userService.findAdminUser(), PolicyCounter.METRICS_PER_HOUR, new Random().nextInt(50));

        String alertName = "";

        try {
            Method method = DefaultWardenService.class.getDeclaredMethod("_constructWardenAlertName", PrincipalUser.class, PolicyCounter.class);

            method.setAccessible(true);
            alertName = (String) method.invoke(_wardenService, _userService.findAdminUser(), PolicyCounter.METRICS_PER_HOUR);
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new SystemException("Failed to construct alert name using reflection");
        }

        Alert alert = _alertService.findAlertByNameAndOwner(alertName, _userService.findAdminUser());

        assertTrue(alert.isEnabled());
        _alertService.deleteAlert(alert);
    }

    @Test
    public void testModifyPolicyCounterEnablesAlert() {
        _wardenService.modifyPolicyCounter(_userService.findAdminUser(), PolicyCounter.METRICS_PER_HOUR, new Random().nextInt(50));

        String alertName = "";

        try {
            Method method = DefaultWardenService.class.getDeclaredMethod("_constructWardenAlertName", PrincipalUser.class, PolicyCounter.class);

            method.setAccessible(true);
            alertName = (String) method.invoke(_wardenService, _userService.findAdminUser(), PolicyCounter.METRICS_PER_HOUR);
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new SystemException("Failed to construct alert name using reflection");
        }

        Alert alert = _alertService.findAlertByNameAndOwner(alertName, _userService.findAdminUser());

        assertTrue(alert.isEnabled());
        _alertService.deleteAlert(alert);
    }

    @Test
    public void testSubSystemSuspensionLevels() {
        PrincipalUser user = _userService.findUserByUsername("bhinav.sura");

        _wardenService.updateSuspensionLevel(SubSystem.POSTING, 5, 5 * 1000L);
        for (int i = 0; i < 6; i++) {
            _wardenService.suspendUser(user, SubSystem.POSTING);
        }
        try {
            Thread.sleep(6 * 1000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        _wardenService.assertSubSystemUsePermitted(user, SubSystem.POSTING);
        assertTrue(true);
    }

    @Test
    public void testWardenAlertUsesUpdatedPolicyLimitForUser() {
        PrincipalUser user = _userService.findUserByUsername("bhinav.sura");

        _wardenService.updatePolicyLimitForUser(user, PolicyCounter.METRICS_PER_HOUR, 200);
        _wardenService.updatePolicyCounter(user, PolicyCounter.METRICS_PER_HOUR, 50);

        String alertName = "";

        try {
            Method method = DefaultWardenService.class.getDeclaredMethod("_constructWardenAlertName", PrincipalUser.class, PolicyCounter.class);

            method.setAccessible(true);
            alertName = (String) method.invoke(_wardenService, user, PolicyCounter.METRICS_PER_HOUR);
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new SystemException("Failed to construct alert name using reflection");
        }

        Alert alert = _alertService.findAlertByNameAndOwner(alertName, _userService.findAdminUser());
        Trigger trigger = alert.getTriggers().get(0);

        assertEquals(Double.valueOf(200), trigger.getThreshold());
    }

    @Test
    public void testEnableWarden() {
        _wardenService.enableWarden();
        assertTrue(_wardenService.isWardenServiceEnabled());
    }

    @Test
    public void testDisableWarden() {
        _wardenService.disableWarden();
        assertFalse(_wardenService.isWardenServiceEnabled());
    }

    @Test
    public void testWardenDashboard() {
        PrincipalUser user = _userService.findUserByUsername("bhinav.sura");

        assertNotNull(_wardenService.getWardenDashboard(user));
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

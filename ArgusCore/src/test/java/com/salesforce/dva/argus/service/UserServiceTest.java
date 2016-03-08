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
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.entity.PrincipalUser.Preference;
import org.junit.Test;
import java.math.BigInteger;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class UserServiceTest extends AbstractTest {

    @Test
    public void testUserCrud() {
        UserService userService = system.getServiceFactory().getUserService();
        PrincipalUser user = new PrincipalUser("aUser", "aUser@mycompany.abc");

        user.setCreatedBy(user);
        user = userService.updateUser(user);
        assertNotNull(user.getId());
        user.setEmail("someotheruser@mycompany.abc");
        user = userService.updateUser(user);

        Map<Preference, String> preferences = user.getPreferences();

        assertTrue(preferences.isEmpty());
        preferences.put(Preference.DISPLAY_NAME, "some.value");
        user.setPreferences(preferences);
        userService.updateUser(user);
        user = userService.findUserByPrimaryKey(user.getId());
        assertEquals(preferences, user.getPreferences());
        userService.deleteUser(user);
        assertNull(userService.findUserByPrimaryKey(user.getId()));
    }

    @Test
    public void testAdminUserExistence() {
        UserService userService = system.getServiceFactory().getUserService();
        PrincipalUser admin = userService.findUserByUsername("admin");

        assertNotNull(admin);
        assertEquals(BigInteger.ONE, admin.getId());
    }
    
    @Test
    public void testDefaultUserExistence() {
        UserService userService = system.getServiceFactory().getUserService();
        PrincipalUser defaultUser = userService.findUserByUsername("default");

        assertNotNull(defaultUser);
        assertEquals(BigInteger.valueOf(2), defaultUser.getId());
    }

    @Test
    public void testUniqueUserCount() {
        UserService userService = system.getServiceFactory().getUserService();
        long uniqueUserCount = userService.getUniqueUserCount();

        assertTrue("There should always be at least one user at system startup.", uniqueUserCount >= 1);
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

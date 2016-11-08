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

import com.salesforce.dva.argus.sdk.entity.PrincipalUser;
import org.junit.Test;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Date;

import static org.junit.Assert.*;

public class UserServiceTest extends AbstractTest {

    @Test
    public void testGetUserById() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/UserServiceTest.json"))) {
            UserService userService = argusService.getUserService();
            PrincipalUser result = userService.getUser(BigInteger.ONE);
            PrincipalUser expected = _constructAdminUser();

            assertEquals(expected, result);
        }
    }

    @Test
    public void testGetUserByUsername() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/UserServiceTest.json"))) {
            UserService userService = argusService.getUserService();
            PrincipalUser result = userService.getUser("admin");
            PrincipalUser expected = _constructAdminUser();

            assertEquals(expected, result);
        }
    }

    PrincipalUser _constructAdminUser() throws IOException {
        PrincipalUser result = new PrincipalUser();

        result.setCreatedById(BigInteger.ONE);
        result.setCreatedDate(new Date(1472282830936L));
        result.setEmail("admin@mycompany.com");
        result.setId(BigInteger.ONE);
        result.setModifiedById(BigInteger.ONE);
        result.setModifiedDate(new Date(1472282830936L));
        result.setPrivileged(true);
        result.setUserName("admin");
        return result;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

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

import org.junit.Test;

import com.salesforce.dva.argus.sdk.exceptions.ArgusServiceException;
import com.salesforce.dva.argus.sdk.exceptions.TokenExpiredException;

import java.io.IOException;

import static org.junit.Assert.*;

public class AuthServiceTest extends AbstractTest {

    @Test
    public void testLoginLogout() throws IOException, TokenExpiredException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AuthServiceTest2.json"))) {
            AuthService authService = argusService.getAuthService();

            authService.login("aUsername", "aPassword");
            assertEquals("anAccessToken", authService.getClient().accessToken);
            assertEquals("aRefreshToken", authService.getClient().refreshToken);
            authService.logout();
        }
    }

    @Test
    public void testBadLogin() throws IOException, TokenExpiredException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AuthServiceTest2.json"))) {
            AuthService authService = argusService.getAuthService();

            authService.login("aBadUsername", "aBadPassword");
        } catch (ArgusServiceException ex) {
            assertEquals(401, ex.getStatus());
            return;
        }
        fail("Expected an ArgusServiceException for bad login.");
    }
    
    @Test
    public void testObtainNewAccessToken() throws IOException, TokenExpiredException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AuthServiceTest2.json"))) {
            AuthService authService = argusService.getAuthService();

            authService.login("aUsername", "aPassword");
            assertEquals("anAccessToken", authService.getClient().accessToken);
            authService.obtainNewAccessToken();
            assertEquals("aNewAccessToken", authService.getClient().accessToken);
        }
    }
    
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

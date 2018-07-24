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
import com.salesforce.dva.argus.entity.OAuthAuthorizationCode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import java.sql.Timestamp;

/**
 * @author Chandravyas Annakula (cannakula@salesforce.com)
 */
public class OAuthServiceTest extends AbstractTest {
    OAuthAuthorizationCodeService authService;
    private UserService userService;

    @Before
    @Override
    public void setUp() {
        super.setUp();
        authService = system.getServiceFactory().getOAuthAuthorizationCodeService();
        userService = system.getServiceFactory().getUserService();
    }

    @Test
    public void testCreateAuthorizationCode() {
        String authCode = "test_code";
        String client_id = "test_client_id";
        String redirect_uri = "http://testredirect_uri";
        String scope = "test_scope";
        String state = "test_state";
        OAuthAuthorizationCode oauthAuthorizationCode =
                new OAuthAuthorizationCode(
                        authCode,
                        client_id,
                        "",
                        redirect_uri,
                        new Timestamp(System.currentTimeMillis()),
                        scope,
                        state

        );
        oauthAuthorizationCode = authService.create(oauthAuthorizationCode);
        Assert.assertNotNull(oauthAuthorizationCode);
    }

    @Test(expected = IllegalArgumentException.class)
    public void CreateAuthorizationCodeWithMissingArguments() {

        String authCode = "test_code";
        String client_id = "test_client_id";
        String redirect_uri = "http://testredirect_uri";
        String scope = "test_scope";
        String state = "test_state";
        OAuthAuthorizationCode oauthAuthorizationCode =
                new OAuthAuthorizationCode(
                        authCode,
                        client_id,
                        "",
                        redirect_uri,
                        new Timestamp(System.currentTimeMillis()),
                        scope,
                        state

                );
        oauthAuthorizationCode.setAuthorizationCode("");
        authService.create(oauthAuthorizationCode);

        oauthAuthorizationCode.setAuthorizationCode(authCode);
        oauthAuthorizationCode.setState("");
        authService.create(oauthAuthorizationCode);

        oauthAuthorizationCode.setState(state);
        oauthAuthorizationCode.setRedirectUri("");
        authService.create(oauthAuthorizationCode);

    }

    @Test
    public void testAcceptOauth() {
        String authCode = "test_code";
        String client_id = "test_client_id";
        String redirect_uri = "http://testredirect_uri";
        String scope = "test_scope";
        String state = "test_state";
        OAuthAuthorizationCode oauthAuthorizationCode =
                new OAuthAuthorizationCode(
                        authCode,
                        client_id,
                        "",
                        redirect_uri,
                        new Timestamp(System.currentTimeMillis()),
                        scope,
                        state

                );
        authService.create(oauthAuthorizationCode);
        OAuthAuthorizationCode foundOauthAuthorizationCode = authService.findByCodeAndState(authCode, state);
        Assert.assertNotNull(foundOauthAuthorizationCode);
        int updateResult = authService.updateUserId(authCode, state, "test_user");
        Assert.assertEquals(1,updateResult);

    }

    @Test
    public void testAcceptOauthModifiedParams() {
        String authCode = "test_code";
        String client_id = "test_client_id";
        String redirect_uri = "http://testredirect_uri";
        String scope = "test_scope";
        String state = "test_state";
        OAuthAuthorizationCode oauthAuthorizationCode =
                new OAuthAuthorizationCode(
                        authCode,
                        client_id,
                        "",
                        redirect_uri,
                        new Timestamp(System.currentTimeMillis()),
                        scope,
                        state

                );
        authService.create(oauthAuthorizationCode);
        OAuthAuthorizationCode foundOauthAuthorizationCode = authService.findByCodeAndState("test_code_modified", state);
        Assert.assertNull(foundOauthAuthorizationCode);
        foundOauthAuthorizationCode = authService.findByCodeAndState(authCode,"test_state_modified");
        Assert.assertNull(foundOauthAuthorizationCode);

        int updateResult = authService.updateUserId("test_code_modified", state, "test_user");
        Assert.assertEquals(0,updateResult);
        updateResult = authService.updateUserId(authCode, "test_state_modified", "test_user");
        Assert.assertEquals(0,updateResult);

    }

    @Test(expected = IllegalArgumentException.class)
    public void testAcceptOauthInvalidParams() {
        String authCode = "test_code";
        String client_id = "test_client_id";
        String redirect_uri = "http://testredirect_uri";
        String scope = "test_scope";
        String state = "test_state";
        OAuthAuthorizationCode oauthAuthorizationCode =
                new OAuthAuthorizationCode(
                        authCode,
                        client_id,
                        "",
                        redirect_uri,
                        new Timestamp(System.currentTimeMillis()),
                        scope,
                        state

                );
        authService.create(oauthAuthorizationCode);
        authService.findByCodeAndState("", state);
        authService.findByCodeAndState(authCode,"");
        authService.updateUserId("", state, "test_user");
        authService.updateUserId(authCode, "", "test_user");
        authService.updateUserId(authCode, state, "");
    }
    

}

/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */
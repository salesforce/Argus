package com.salesforce.dva.argus.service;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.OAuthAuthorizationCode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import java.sql.Timestamp;

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
package com.salesforce.dva.argus.service;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.OAuthAuthorizationCode;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import java.sql.Timestamp;

public class OAuthAuthorizationCodeServiceTest extends AbstractTest {
    OAuthAuthorizationCodeService authService;

    @Before
    @Override
    public void setUp() {
        super.setUp();
        authService = system.getServiceFactory().getOAuthAuthorizationCodeService();
    }

    @Test
    public void testAuthorizationCodeCrud() {
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
}

/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */
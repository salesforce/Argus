package com.salesforce.dva.argus.ws.resources;

import com.salesforce.dva.argus.ws.exception.OAuthException;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author Gaurav Kumar (gaurav.kumar@salesforce.com)
 */

public class JWTUtilsTest {
    @Test
    public void testJWTExpiry() throws OAuthException {
        String accessToken = "eyJhbGciOiJIUzI1NiJf.eyJpc3MiOiJhcmd1cy1hcGkiLCJpYXQiOjE1MzA3NTQzNzcsImV4cCI6MTUzMDc1Nzk3Nywic3ViIjoiZ2F1cmF2Lmt1bWFyIiwidHlwZSI6IkFDQ0VTUyJ9.Vo0DaXePFZ3rbHVyXoNy_oOJ31bNeDpkzC";
        Assert.assertEquals(1530757977, JWTUtils.getTokenExpiry(accessToken));
    }

    @Test
    public void testJWTUsername() throws OAuthException {
        String accessToken = "eyJhbGciOiJIUzI1NiJf.eyJpc3MiOiJhcmd1cy1hcGkiLCJpYXQiOjE1MzA3NTQzNzcsImV4cCI6MTUzMDc1Nzk3Nywic3ViIjoiZ2F1cmF2Lmt1bWFyIiwidHlwZSI6IkFDQ0VTUyJ9.Vo0DaXePFZ3rbHVyXoNy_oOJ31bNeDpkzC";
        Assert.assertEquals("gaurav.kumar", JWTUtils.getUsername(accessToken));
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

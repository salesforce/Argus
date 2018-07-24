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
/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */

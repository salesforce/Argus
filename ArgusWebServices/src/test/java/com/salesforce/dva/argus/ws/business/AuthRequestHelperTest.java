/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

package com.salesforce.dva.argus.ws.business;

import com.salesforce.dva.argus.ws.business.oauth.AuthRequestHelper;
import com.salesforce.dva.argus.ws.dto.AuthRequestDto;
import com.salesforce.dva.argus.ws.dto.OAuthApplicationDto;
import com.salesforce.dva.argus.ws.exception.OAuthException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;


/**
 * @author Gaurav Kumar (gaurav.kumar@salesforce.com)
 */
public class AuthRequestHelperTest {
    AuthRequestDto authRequestDto;
    AuthRequestDto authRequestDtoEncoded;
    OAuthApplicationDto oAuthApplicationDto;

    @Before
    public void setup() throws Exception {
        authRequestDto = new AuthRequestDto("test_client_id",  "test_response_type", "http://www.redirect.uri/test?param1=abc&param2=123", "test_scope", "test_state");
        authRequestDtoEncoded = new AuthRequestDto("test_client_id",  "test_response_type", "http%3A%2F%2Fwww.redirect.uri%2Ftest%3Fparam1%3Dabc%26param2%3D123", "test_scope", "test_state");
        oAuthApplicationDto = new OAuthApplicationDto("test_application", "test_client_id", "test_client_secret", "http://www.redirect.uri/test?param1=abc&param2=123");
    }

    @Test
    public void testValidateAuthRequest() throws OAuthException {
        Assert.assertTrue(AuthRequestHelper.validateAuthorizationRequest(authRequestDto, oAuthApplicationDto));
        Assert.assertTrue(AuthRequestHelper.validateAuthorizationRequest(authRequestDtoEncoded, oAuthApplicationDto));
    }

}

/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */
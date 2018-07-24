/*
 * Copyright (c) 2018, Salesforce.com, Inc.
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

package com.salesforce.dva.argus.ws.business.oauth;

/**
 * OAuth Response Codes
 * @author gaurav.kumar (gaurav.kumar@salesforce.com), Chandravyas Annakula(cannakula@salesforce.com)
 */
public final class ResponseCodes {
    public static final String SCOPE_NOT_EXIST = "{\"error\": \"scope does not exist\"}";
    public static final String INVALID_OR_MISSING_CLIENT_ID = "{\"error\": \"invalid or missing client_id/client_secret\"}";
    public static final String INVALID_OR_MISSING_REDIRECT_URI = "{\"error\": \"invalid or missing redirect_uri\"}";
    public static final String INVALID_OR_MISSING_GRANT_TYPE = "{\"error\": \"invalid or missing grant_type\"}";
    public static final String ERR_ISSUING_AUTH_CODE = "{\"error\": \"error issuing authorization code, internal error\"}";
    public static final String ERR_ISSUING_ACCESS_TOKEN = "{\"error\": \"error issuing access token, internal error\"}";
    public static final String ERR_FINDING_USERNAME = "{\"error\": \"error finding username\"}";
    public static final String ERR_DELETING_APP = "{\"error\": \"error deleting app\"}";
    public static final String INVALID_AUTH_CODE = "{\"error\": \"invalid authorization code\"}";
    public static final String INVALID_STATE = "{\"error\": \"invalid state code\"}";
    public static final String INVALID_AUTH_CODE_OR_STATE = "{\"error\": \"invalid authorization code or state\"}";
    public static final String GRANT_TYPE_NOT_SUPPORTED = "{\"error\": \"unsupported grant type\"}";
    public static final String INVALID_ACCESS_TOKEN = "{\"error\":\"invalid access token\"}";
    public static final String INVALID_REFRESH_TOKEN = "{\"error\":\"invalid refresh token\"}";
    public static final String APPLICATION_JSON = "application/json";
}

/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */
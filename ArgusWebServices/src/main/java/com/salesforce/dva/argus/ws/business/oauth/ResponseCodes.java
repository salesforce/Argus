package com.salesforce.dva.argus.ws.business.oauth;

/**
 * @author gaurav.kumar (gaurav.kumar@salesforce.com)
 */
public final class ResponseCodes {
    public static final String SCOPE_NOT_EXIST = "{\"error\": \"scope does not exist\"}";
    public static final String INVALID_OR_MISSING_CLIENT_ID = "{\"error\": \"invalid or missing client_id/client_secret\"}";
    public static final String INVALID_OR_MISSING_REDIRECT_URI = "{\"error\": \"invalid or missing redirect_uri\"}";
    public static final String INVALID_OR_MISSING_GRANT_TYPE = "{\"error\": \"invalid or missing grant_type\"}";
    public static final String ERR_ISSUING_AUTH_CODE = "{\"error\": \"error issuing authorization code, internal error\"}";
    public static final String ERR_ISSUING_ACCESS_TOKEN = "{\"error\": \"error issuing access token, internal error\"}";
    public static final String INVALID_AUTH_CODE = "{\"error\": \"invalid authorization code\"}";
    public static final String INVALID_STATE = "{\"error\": \"invalid state code\"}";
    public static final String INVALID_AUTH_CODE_OR_STATE = "{\"error\": \"invalid authorization code or state\"}";
    public static final String GRANT_TYPE_NOT_SUPPORTED = "{\"error\": \"unsupported grant type\"}";
    public static final String INVALID_ACCESS_TOKEN = "{\"error\":\"invalid access token\"}";
    public static final String INVALID_REFRESH_TOKEN = "{\"error\":\"invalid refresh token\"}";

    public static final String APPLICATION_JSON = "application/json";
}

/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */
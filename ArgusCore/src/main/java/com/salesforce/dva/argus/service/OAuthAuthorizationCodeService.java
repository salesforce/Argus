package com.salesforce.dva.argus.service;

import com.salesforce.dva.argus.entity.OAuthAuthorizationCode;

import java.sql.Timestamp;

public interface OAuthAuthorizationCodeService extends Service {
    OAuthAuthorizationCode create(OAuthAuthorizationCode authCodeEntity);
    OAuthAuthorizationCode findByCodeAndRedirectURI(String code, String uri);
    int updateExpiry(String code, Timestamp expires);
    int updateUserId(String code, String state, String userId);
}

/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */

package com.salesforce.dva.argus.service;

import com.salesforce.dva.argus.entity.OAuthAuthorizationCode;

import java.sql.Timestamp;
import java.util.List;

public interface OAuthAuthorizationCodeService extends Service {
    OAuthAuthorizationCode create(OAuthAuthorizationCode authCodeEntity);
    OAuthAuthorizationCode findByCodeAndRedirectURI(String code, String uri);
    OAuthAuthorizationCode findByCodeAndState(String code, String state);
    List<OAuthAuthorizationCode> findByUserId(String userName);
    int deleteExpiredAuthCodes(Timestamp currentTime);
    int deleteByUserId(String userName);
    int updateExpiry(String code, Timestamp expires);
    int updateUserId(String code, String state, String userId);
}

/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */

package com.salesforce.dva.argus.service;

import com.salesforce.dva.argus.entity.OAuthAccessToken;

import java.sql.Timestamp;

public interface OAuthAccessTokenService extends Service {
    OAuthAccessToken create(OAuthAccessToken oauthAccessToken);
    OAuthAccessToken findByAccessToken(String accessToken);
    OAuthAccessToken findLatestAccessTokensByClientIdUserId(String clientId, String userId);
    int updateAccessToken(String oldToken, String newToken, Timestamp expires);
}

/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */

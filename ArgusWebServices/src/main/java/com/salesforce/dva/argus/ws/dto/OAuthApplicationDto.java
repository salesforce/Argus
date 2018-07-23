package com.salesforce.dva.argus.ws.dto;

import java.io.Serializable;

/**
 * OAuth Application DTO
 *
 * @author gaurav.kumar (gaurav.kumar@salesforce.com)
 */
public final class OAuthApplicationDto implements Serializable {
    String name;
    String clientId;
    String clientSecret;
    String redirectUri;

    public OAuthApplicationDto(String name, String clientId, String clientSecret, String redirectUri) {
        this.name = name;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    /**
     * Gets the OAuth Application Name
     * @return Returns OAuth Application Name
     */
    public String getName() {
        return name;
    }


    /**
     * Gets the OAuth client Id
     * @return  Returns OAuth Client Id
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Gets the OAuth client secret
     * @return  Returns OAuth client secret
     */
    public String getClientSecret() {
        return clientSecret;
    }


    /**
     * Gets the RedirectURI
     * @return  Returns Redirect URI
     */
    public String getRedirectUri() {
        return redirectUri;
    }
}

/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */

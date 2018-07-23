package com.salesforce.dva.argus.ws.dto;

import java.io.Serializable;

/**
 * OAuth Token Request DTO
 * @author gaurav.kumar (gaurav.kumar@salesforce.com)
 */
public class TokenRequestDto implements Serializable  {
    private String client_id;
    private String client_secret;
    private String grant_type;
    private String code;
    private String redirect_uri;

    /**
     * Gets the Client Id
     * @return  Returns ID of the client sending this request
     */
    public String getClient_id() {
        return client_id;
    }

    /**
     * Sets the Client Id
     * @param client_id ClientId sending this request
     */
    public void setClient_id(String client_id) {
        this.client_id = client_id;
    }

    /**
     * Gets the client secret
     * @return  Returns client secret of the client sending this request
     */
    public String getClient_secret() {
        return client_secret;
    }

    /**
     * Sets the client secret
     * @param client_secret client secret of the client sending this request
     */
    public void setClient_secret(String client_secret) {
        this.client_secret = client_secret;
    }

    /**
     * Gets the Grant Type
     * @return  Returns the grant type
     */
    public String getGrant_type() {
        return grant_type;
    }

    /**
     * Sets the grant type
     * @param grant_type
     */
    public void setGrant_type(String grant_type) {
        this.grant_type = grant_type;
    }

    /**
     *Gets the Authorization Code
     * @return  Returns Authorization Code
     */
    public String getCode() {
        return code;
    }

    /**
     * Sets the Authorization Code
     * @param code Authorization Code
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     *Get Redirect URI
     * @return  Returns Redirect URI
     */
    public String getRedirect_uri() {
        return redirect_uri;
    }

    /**
     *Sets the Redirect URI
     * @param redirect_uri  Redirect URI
     */
    public void setRedirect_uri(String redirect_uri) {
        this.redirect_uri = redirect_uri;
    }
}

/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */

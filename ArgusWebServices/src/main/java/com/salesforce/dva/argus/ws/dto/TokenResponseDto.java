package com.salesforce.dva.argus.ws.dto;

import java.io.Serializable;

/**
 * This DTO is used as response while generating Token
 * @author gaurav.kumar (gaurav.kumar@salesforce.com)
 */
public class TokenResponseDto implements Serializable {
    private String access_token;
    private String  token_type;
    private Integer expires_in;
    private String refresh_token;

    /**
     * Gets the Access Token
     * @return  Returns JWT Access Token
     */
    public String getAccess_token() {
        return access_token;
    }

    /**
     * Sets the Access Token
     * @param access_token  JWT Access Token
     */
    public void setAccess_token(String access_token) {
        this.access_token = access_token;
    }

    /**
     * Gets the type of Access Token
     * @return  Type of Access Token is returned
     */
    public String getToken_type() {
        return token_type;
    }

    /**
     * Access Token Type is set
     * @param token_type    Token Type
     */
    public void setToken_type(String token_type) {
        this.token_type = token_type;
    }

    /**
     * Gets the expiry of access token
     * @return  Expiry time of access token
     */
    public Integer getExpires_in() {
        return expires_in;
    }

    /**
     * Sets the expiry time of access token
     * @param expires_in    Expiry time of access token
     */
    public void setExpires_in(Integer expires_in) {
        this.expires_in = expires_in;
    }

    /**
     * Gets the Refresh Token
     * @return  JWT Refresh Token
     */
    public String getRefresh_token() {
        return refresh_token;
    }

    /**
     * Sets the Refresh Token
     * @param refresh_token JWT Token
     */
    public void setRefresh_token(String refresh_token) {
        this.refresh_token = refresh_token;
    }
}
/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */

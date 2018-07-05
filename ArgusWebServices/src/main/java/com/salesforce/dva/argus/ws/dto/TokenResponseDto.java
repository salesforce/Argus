package com.salesforce.dva.argus.ws.dto;

import java.io.Serializable;

/**
 * @author gaurav.kumar (gaurav.kumar@salesforce.com)
 */
public class TokenResponseDto implements Serializable {
    private String access_token;
    private String  token_type;
    private Integer expires_in;
    private String refresh_token;

    public String getAccess_token() {
        return access_token;
    }

    public void setAccess_token(String access_token) {
        this.access_token = access_token;
    }

    public String getToken_type() {
        return token_type;
    }

    public void setToken_type(String token_type) {
        this.token_type = token_type;
    }

    public Integer getExpires_in() {
        return expires_in;
    }

    public void setExpires_in(Integer expires_in) {
        this.expires_in = expires_in;
    }

    public String getRefresh_token() {
        return refresh_token;
    }

    public void setRefresh_token(String refresh_token) {
        this.refresh_token = refresh_token;
    }
}
/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */

package com.salesforce.dva.argus.ws.dto;

import java.io.Serializable;

/**
 * @author gaurav.kumar (gaurav.kumar@salesforce.com)
 */
public class OAuthAcceptDto implements Serializable {
    private String code;
    private String state;
    private String message;
    private String access_token;
    private String refresh_token;
    private String owner;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getAccess_token() {
        return access_token;
    }

    public void setAccess_token(String access_token) {
        this.access_token = access_token;
    }

    public String getRefresh_token() {
        return refresh_token;
    }

    public void setRefresh_token(String refresh_token) {
        this.refresh_token = refresh_token;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }
}

/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */
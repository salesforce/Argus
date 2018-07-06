package com.salesforce.dva.argus.ws.dto;

import java.io.Serializable;

/**
 * @author gaurav.kumar (gaurav.kumar@salesforce.com)
 */
public class OAuthAcceptDto implements Serializable {
    private String code;
    private String state;

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

}

/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */
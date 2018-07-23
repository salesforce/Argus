package com.salesforce.dva.argus.ws.dto;

import java.io.Serializable;

/**
 * This DTO is used while displaying information to user for granting an application to access argus oauth resources
 *
 * @author gaurav.kumar (gaurav.kumar@salesforce.com)
 */
public class OAuthAcceptDto implements Serializable {
    private String code;
    private String state;

    /**
     * Gets the Authorization Code
     * @return  Authorization Code is returned
     */
    public String getCode() {
        return code;
    }

    /**
     * Sets the Authorization Code
     * @param code  Authorization Code is set
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     * To get the Application Code
     * @return  Application Code
     */
    public String getState() {
        return state;
    }

    /**
     * Sets the Application Code
     * @param state Application Code
     */
    public void setState(String state) {
        this.state = state;
    }

}

/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */
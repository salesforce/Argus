package com.salesforce.dva.argus.ws.dto;

import java.io.Serializable;

/**
 * This DTO is used to encapsulate the information sent by 3rd party application to argus oauth /authorize webservice
 *
 * @author gaurav.kumar (gaurav.kumar@salesforce.com)
 */
public final class AuthRequestDto implements Serializable {
    String client_id;
    String response_type;
    String scope;
    String redirect_uri;
    String state;

    public AuthRequestDto(String client_id,
                   String response_type,
                   String redirect_uri,
                   String scope,
                   String state) {
        this.client_id = client_id;
        this.response_type = response_type;
        this.redirect_uri = redirect_uri;
        this.scope = scope;
        this.state = state;
    }

    /**
     * Gets the client id
     * @return client id
     */
    public String getClient_id() {
        return client_id;
    }


    /**
     * Gets the response type
     * @return  Response Type
     */
    public String getResponse_type() {
        return response_type;
    }

    /**
     * Gets the scope
     * @return  Scope is returned
     */
    public String getScope() {
        return scope;
    }


    /**
     * Gets Redirect URI
     * @return Redirect URI is returned
     */
    public String getRedirect_uri() {
        return redirect_uri;
    }


    /**
     * Gets Application random code
     * @return Application random code
     */
    public String getState() {
        return state;
    }

}
/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */
package com.salesforce.dva.argus.ws.dto;

import java.io.Serializable;

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

    public String getClient_id() {
        return client_id;
    }


    public String getResponse_type() {
        return response_type;
    }


    public String getScope() {
        return scope;
    }


    public String getRedirect_uri() {
        return redirect_uri;
    }


    public String getState() {
        return state;
    }

}

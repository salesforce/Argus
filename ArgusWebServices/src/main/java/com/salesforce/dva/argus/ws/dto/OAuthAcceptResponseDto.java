package com.salesforce.dva.argus.ws.dto;

public class OAuthAcceptResponseDto {
    private String redirect_uri;

    public String getRedirect_uri() {
        return redirect_uri;
    }

    public void setRedirect_uri(String redirect_uri) {
        this.redirect_uri = redirect_uri;
    }
}

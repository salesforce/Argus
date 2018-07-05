package com.salesforce.dva.argus.ws.dto;

import java.io.Serializable;

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

    public String getName() {
        return name;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getRedirectUri() {
        return redirectUri;
    }
}

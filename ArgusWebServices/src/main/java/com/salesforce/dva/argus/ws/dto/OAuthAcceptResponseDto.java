package com.salesforce.dva.argus.ws.dto;

/**
 * This DTO is used as response after user grants an application to access argus oauth resources
 *
 * @author gaurav.kumar (gaurav.kumar@salesforce.com)
 */
public class OAuthAcceptResponseDto {
    private String redirect_uri;

    /**
     * Gets the Redirect URI to where the application has to be redirected
     * @return URI to where the application has to be redirected
     */
    public String getRedirect_uri() {
        return redirect_uri;
    }

    /**
     * Sets the URI to where the application has to be redirected
     * @param redirect_uri  URI to where the application has to be redirected
     */
    public void setRedirect_uri(String redirect_uri) {
        this.redirect_uri = redirect_uri;
    }
}

package com.salesforce.dva.argus.util;

/**
 * This class encapsulates some of the parameters that are sent through the web service request
 */
public class RequestContext {
    
    private String userName = "NULLUSER";

    public RequestContext(String userName) {
        this.userName = userName;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}

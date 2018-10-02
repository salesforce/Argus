package com.salesforce.dva.argus.service.alert.notifier.utils;

/**
 * Utility class for endpoint information.
 *
 * @author  fiaz.hossain
 */
public class EndpointInfo {

    private final String endPoint;
    private final String token;

    public EndpointInfo(final String endPoint, final String token) {
        this.endPoint = endPoint;
        this.token = token;
    }

    /**
     * Valid endpoint. Either from config or endpont after authentication
     *
     * @return  endpoint
     */
    public String getEndPoint() {
        return endPoint;
    }

    /**
     * Token can be either active, expired or a dummy value.
     *
     * @return  token
     */
    public String getToken() {
        return token;
    }
}
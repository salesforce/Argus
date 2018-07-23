package com.salesforce.dva.argus.ws.exception;

import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.Provider;

/**
 * Exception class for OAuth Related Exceptions
 *
 * @author gaurav.kumar (gaurav.kumar@salesforce.com)
 */
@Provider
public class OAuthException extends WebApplicationException {

    /**
     * Creates OAuth Exception
     * @param message       The exception message.
     * @param httpStatus    The corresponding HTTP status code.
     */
    public OAuthException(String message, HttpResponseStatus httpStatus) {
        super(message, Response.status(httpStatus.getCode())
                .entity(message).type(MediaType.APPLICATION_JSON_TYPE).build());
    }
}
/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */

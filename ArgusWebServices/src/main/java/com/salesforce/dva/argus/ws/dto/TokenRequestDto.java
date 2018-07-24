/*
 * Copyright (c) 2018, Salesforce.com, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.dva.argus.ws.dto;

import java.io.Serializable;

/**
 * OAuth Token Request DTO
 * @author gaurav.kumar (gaurav.kumar@salesforce.com)
 */
public class TokenRequestDto implements Serializable  {

    //~ Instance fields ******************************************************************************************************************************

    private String client_id;
    private String client_secret;
    private String grant_type;
    private String code;
    private String redirect_uri;

    //~ Methods **************************************************************************************************************************************

    /**
     * Gets the Client Id
     * @return  Returns ID of the client sending this request
     */
    public String getClient_id() {
        return client_id;
    }

    /**
     * Sets the Client Id
     * @param client_id ClientId sending this request
     */
    public void setClient_id(String client_id) {
        this.client_id = client_id;
    }

    /**
     * Gets the client secret
     * @return  Returns client secret of the client sending this request
     */
    public String getClient_secret() {
        return client_secret;
    }

    /**
     * Sets the client secret
     * @param client_secret client secret of the client sending this request
     */
    public void setClient_secret(String client_secret) {
        this.client_secret = client_secret;
    }

    /**
     * Gets the Grant Type
     * @return  Returns the grant type
     */
    public String getGrant_type() {
        return grant_type;
    }

    /**
     * Sets the grant type
     * @param grant_type Type of Grant
     */
    public void setGrant_type(String grant_type) {
        this.grant_type = grant_type;
    }

    /**
     *Gets the Authorization Code
     * @return  Returns Authorization Code
     */
    public String getCode() {
        return code;
    }

    /**
     * Sets the Authorization Code
     * @param code Authorization Code
     */
    public void setCode(String code) {
        this.code = code;
    }

    /**
     *Get Redirect URI
     * @return  Returns Redirect URI
     */
    public String getRedirect_uri() {
        return redirect_uri;
    }

    /**
     *Sets the Redirect URI
     * @param redirect_uri  Redirect URI
     */
    public void setRedirect_uri(String redirect_uri) {
        this.redirect_uri = redirect_uri;
    }
}

/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */

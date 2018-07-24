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

    private String clientId;
    private String clientSecret;
    private String grantType;
    private String code;
    private String redirectUri;

    //~ Methods **************************************************************************************************************************************

    /**
     * Gets the Client Id
     * @return  Returns ID of the client sending this request
     */
    public String getClientId() {
        return clientId;
    }

    /**
     * Sets the Client Id
     * @param clientId ClientId sending this request
     */
    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    /**
     * Gets the client secret
     * @return  Returns client secret of the client sending this request
     */
    public String getClientSecret() {
        return clientSecret;
    }

    /**
     * Sets the client secret
     * @param clientSecret client secret of the client sending this request
     */
    public void setClientSecret(String clientSecret) {
        this.clientSecret = clientSecret;
    }

    /**
     * Gets the Grant Type
     * @return  Returns the grant type
     */
    public String getGrantType() {
        return grantType;
    }

    /**
     * Sets the grant type
     * @param grantType Type of Grant
     */
    public void setGrantType(String grantType) {
        this.grantType = grantType;
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
    public String getRedirectUri() {
        return redirectUri;
    }

    /**
     *Sets the Redirect URI
     * @param redirectUri  Redirect URI
     */
    public void setRedirectUri(String redirectUri) {
        this.redirectUri = redirectUri;
    }
}

/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */

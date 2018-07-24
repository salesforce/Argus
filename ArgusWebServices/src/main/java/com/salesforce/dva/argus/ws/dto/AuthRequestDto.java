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
 * This DTO is used to encapsulate the information sent by 3rd party application to argus oauth /authorize webservice
 *
 * @author gaurav.kumar (gaurav.kumar@salesforce.com)
 */
public final class AuthRequestDto implements Serializable {

    //~ Instance fields ******************************************************************************************************************************

    String clientId;
    String responseType;
    String scope;
    String redirectUri;
    String state;

    //~ Methods **************************************************************************************************************************************

    public AuthRequestDto(String clientId,
                   String responseType,
                   String redirectUri,
                   String scope,
                   String state) {
        this.clientId = clientId;
        this.responseType = responseType;
        this.redirectUri = redirectUri;
        this.scope = scope;
        this.state = state;
    }

    /**
     * Gets the client id
     * @return client id
     */
    public String getClientId() {
        return clientId;
    }


    /**
     * Gets the response type
     * @return  Response Type
     */
    public String getResponseType() {
        return responseType;
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
    public String getRedirectUri() {
        return redirectUri;
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
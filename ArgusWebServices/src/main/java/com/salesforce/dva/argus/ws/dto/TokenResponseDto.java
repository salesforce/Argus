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
 * This DTO is used as response while generating Token
 * @author gaurav.kumar (gaurav.kumar@salesforce.com)
 */
public class TokenResponseDto implements Serializable {

    //~ Instance fields ******************************************************************************************************************************

    private String access_token;
    private String  token_type;
    private Integer expires_in;
    private String refresh_token;

    //~ Methods **************************************************************************************************************************************

    /**
     * Gets the Access Token
     * @return  Returns JWT Access Token
     */
    public String getAccess_token() {
        return access_token;
    }

    /**
     * Sets the Access Token
     * @param access_token  JWT Access Token
     */
    public void setAccess_token(String access_token) {
        this.access_token = access_token;
    }

    /**
     * Gets the type of Access Token
     * @return  Type of Access Token is returned
     */
    public String getToken_type() {
        return token_type;
    }

    /**
     * Access Token Type is set
     * @param token_type    Token Type
     */
    public void setToken_type(String token_type) {
        this.token_type = token_type;
    }

    /**
     * Gets the expiry of access token
     * @return  Expiry time of access token
     */
    public Integer getExpires_in() {
        return expires_in;
    }

    /**
     * Sets the expiry time of access token
     * @param expires_in    Expiry time of access token
     */
    public void setExpires_in(Integer expires_in) {
        this.expires_in = expires_in;
    }

    /**
     * Gets the Refresh Token
     * @return  JWT Refresh Token
     */
    public String getRefresh_token() {
        return refresh_token;
    }

    /**
     * Sets the Refresh Token
     * @param refresh_token JWT Token
     */
    public void setRefresh_token(String refresh_token) {
        this.refresh_token = refresh_token;
    }
}
/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */

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
 *
 * @author gaurav.kumar (gaurav.kumar@salesforce.com)
 */
public class TokenResponseDto implements Serializable {

    //~ Instance fields ******************************************************************************************************************************

    private String accessToken;
    private String tokenType;
    private Integer expiresIn;
    private String refreshToken;

    //~ Methods **************************************************************************************************************************************

    /**
     * Gets the Access Token
     * @return  Returns JWT Access Token
     */
    public String getAccessToken() {
        return accessToken;
    }

    /**
     * Sets the Access Token
     * @param accessToken  JWT Access Token
     */
    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    /**
     * Gets the type of Access Token
     * @return  Type of Access Token is returned
     */
    public String getTokenType() {
        return tokenType;
    }

    /**
     * Access Token Type is set
     * @param tokenType    Token Type
     */
    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    /**
     * Gets the expiry of access token
     * @return  Expiry time of access token
     */
    public Integer getExpiresIn() {
        return expiresIn;
    }

    /**
     * Sets the expiry time of access token
     * @param expiresIn    Expiry time of access token
     */
    public void setExpiresIn(Integer expiresIn) {
        this.expiresIn = expiresIn;
    }

    /**
     * Gets the Refresh Token
     * @return  JWT Refresh Token
     */
    public String getRefreshToken() {
        return refreshToken;
    }

    /**
     * Sets the Refresh Token
     * @param refreshToken JWT Token
     */
    public void setRefreshToken(String refreshToken) {
        this.refreshToken = refreshToken;
    }
}
/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */

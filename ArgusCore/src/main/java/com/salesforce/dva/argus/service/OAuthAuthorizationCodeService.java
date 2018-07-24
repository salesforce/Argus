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

package com.salesforce.dva.argus.service;

import com.salesforce.dva.argus.entity.OAuthAuthorizationCode;

import java.sql.Timestamp;
import java.util.List;

/**
 * Service to facilitate CRUD and search Operations for OAuth Authorization Codes
 *
 * @author gaurav.kumar (gaurav.kumar@salesforce.com), Chandravyas Annakula (cannakula@salesforce.com)
 *
 */
public interface OAuthAuthorizationCodeService extends Service {

    //~ Methods **************************************************************************************************************************************

    /**
     * Create OAuth Authorization Record
     * @param authCodeEntity  The authCodeEntity cannot be null
     * @return Created OauthAuthorizationCode
     */
    OAuthAuthorizationCode create(OAuthAuthorizationCode authCodeEntity);

    /**
     * Find OAuth Authorization Record using Code and RedirectURI
     * @param code  Authorization Code, This cannot be null
     * @param uri   Redirect URI, This cannot be null
     * @return Oauth Authorization Record by Code and RedirectURI
     */
    OAuthAuthorizationCode findByCodeAndRedirectURI(String code, String uri);

    /**
     * Find OAuth Authorization Record using Code and State
     * @param code  Authorization Code, This cannot be null
     * @param state state, This cannot be null
     * @return  Oauth Authorization Record by Code and State
     */
    OAuthAuthorizationCode findByCodeAndState(String code, String state);

    /**
     * Count of OAuth Authorization Record associated with userName
     * @param userName userName of the user created this Record.This cannot be null
     * @return  Count of OAuth Authorization Record associated with userName
     */
    int countByUserId(String userName);

    /**
     * Delete Expired Oauth Authorization Records by UserName
     * @param currentTime   Present time. This cannot be null
     * @param userName      userName we are interested.This cannot be null.
     * @return  Returns either 0 or non zero number based upon number of records that are deleted
     */
    int deleteExpiredAuthCodesByUserName(Timestamp currentTime,String userName);

    /**
     * Delete Oauth Authorization Records by UserName
     * @param userName  userName we are interested.This cannot be null.
     * @return Returns either 0 or non zero number based upon number of records that are deleted
     */
    int deleteByUserId(String userName);

    /**
     * Updates Expiry time of a record given unique Oauth Authorization Code
     * @param code      This is Unique Oauth Authorization Code. This cannot be null
     * @param expires   Expiry Time of the Authorization Code. This cannot be null
     * @return Returns either 0 or non zero number based upon number of records that are updated
     */
    int updateExpiry(String code, Timestamp expires);

    /**
     * Updates userId of a record given code and state
     * @param code  The Authorization Code. This cannot be null
     * @param state The state associated with request.This cannot be null
     * @param userId The user associated with this code and state.This cannot be null
     * @return Returns either 0 or non zero number based upon number of records that are updated
     */
    int updateUserId(String code, String state, String userId);
}

/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */

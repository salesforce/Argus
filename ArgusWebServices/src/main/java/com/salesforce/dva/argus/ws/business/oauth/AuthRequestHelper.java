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

package com.salesforce.dva.argus.ws.business.oauth;

import com.salesforce.dva.argus.ws.dto.AuthRequestDto;
import com.salesforce.dva.argus.ws.dto.OAuthApplicationDto;
import com.salesforce.dva.argus.ws.dto.TokenRequestDto;
import com.salesforce.dva.argus.ws.exception.OAuthException;
import org.apache.commons.lang.StringUtils;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.security.SecureRandom;


/**
 * Helper class used for validation of objects related to OAuth
 *
 * @author Gaurav Kumar (gaurav.kumar@salesforce.com)
 */
public class AuthRequestHelper {

    //~ Instance fields ******************************************************************************************************************************

    private static final Logger _logger = LoggerFactory.getLogger(AuthRequestHelper.class);
    private static final int AUTHORIZATION_CODE_LENGTH = 40;
    private static final char[] allowedCharacters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();

    //~ Methods **************************************************************************************************************************************

    /**
     * Validates Authorization Request
     * @param authRequestDto        Given Application DTO properties
     * @param oAuthApplicationDto   Expected Application DTO properties
     * @return Boolean or Exception based on Validation
     * @throws OAuthException   Exception related to OAuth
     */
    public static boolean validateAuthorizationRequest(AuthRequestDto authRequestDto, OAuthApplicationDto oAuthApplicationDto) throws OAuthException {
        if (StringUtils.isNotBlank(oAuthApplicationDto.getClientId()) && oAuthApplicationDto.getClientId().equals(authRequestDto.getClientId())) {
            try {
                String decodedRedirectUri = java.net.URLDecoder.decode(authRequestDto.getRedirectUri(), "UTF-8");
                if (StringUtils.isNotBlank(oAuthApplicationDto.getRedirectUri()) && oAuthApplicationDto.getRedirectUri().equals(decodedRedirectUri)) {
                    return true;
                } else {
                    _logger.info("Request Redirect URI '" + authRequestDto.getRedirectUri() + "' mismatch");
                    throw new OAuthException(ResponseCodes.INVALID_OR_MISSING_REDIRECT_URI, HttpResponseStatus.BAD_REQUEST);
                }
            } catch (UnsupportedEncodingException e) {
                _logger.info("Request Redirect URI '" + authRequestDto.getRedirectUri() + "' mismatch");
                throw new OAuthException(ResponseCodes.INVALID_OR_MISSING_REDIRECT_URI, HttpResponseStatus.BAD_REQUEST);
            }
        } else {
            _logger.info("Request Client ID '" + authRequestDto.getClientId() + "' mismatch");
            throw new OAuthException(ResponseCodes.INVALID_OR_MISSING_CLIENT_ID, HttpResponseStatus.BAD_REQUEST);
        }
    }

    /**
     * Generated Unique Authorization Code
     * @return Generated Authorization Code
     */
    public static String generateAuthorizationCode() {
        StringBuilder buf = new StringBuilder(AUTHORIZATION_CODE_LENGTH);
        SecureRandom rand = new SecureRandom();
        for (int i = 0; i < AUTHORIZATION_CODE_LENGTH; i++) {
            buf.append(allowedCharacters[rand.nextInt(allowedCharacters.length)]);
        }
        return buf.toString();
    }


    /**
     * Validates Token Request
     * @param tokenRequestDto       Given Application DTO properties
     * @param oAuthApplicationDto   Expected Application DTO properties
     * @return  Returns boolean or OAuth Exception
     */
    public static boolean validateTokenRequest(TokenRequestDto tokenRequestDto, OAuthApplicationDto oAuthApplicationDto) {
        // basic check
        try {
            String decodedRedirectUri = java.net.URLDecoder.decode(tokenRequestDto.getRedirectUri(), "UTF-8");
            if (StringUtils.isNotBlank(oAuthApplicationDto.getRedirectUri()) && oAuthApplicationDto.getRedirectUri().equals(decodedRedirectUri)) {
                if (StringUtils.isNotBlank(tokenRequestDto.getGrantType())) {
                    if (OAuthFields.AUTHORIZATION_CODE.equals(tokenRequestDto.getGrantType())) {
                        return true;
                    } else {
                        _logger.info("Grant Type '" + tokenRequestDto.getGrantType() + "' is not supported");
                        throw new OAuthException(ResponseCodes.GRANT_TYPE_NOT_SUPPORTED, HttpResponseStatus.BAD_REQUEST);
                    }
                } else {
                    _logger.info("Grant Type '" + tokenRequestDto.getGrantType() + "' mismatch");
                    throw new OAuthException(ResponseCodes.INVALID_OR_MISSING_GRANT_TYPE, HttpResponseStatus.BAD_REQUEST);
                }
            } else {
                _logger.info("Request Redirect URI '" + tokenRequestDto.getRedirectUri() + "' mismatch");
                throw new OAuthException(ResponseCodes.INVALID_OR_MISSING_REDIRECT_URI, HttpResponseStatus.BAD_REQUEST);
            }
        } catch (UnsupportedEncodingException e) {
            _logger.info("Request Redirect URI '" + tokenRequestDto.getRedirectUri() + "' mismatch");
            throw new OAuthException(ResponseCodes.INVALID_OR_MISSING_REDIRECT_URI, HttpResponseStatus.BAD_REQUEST);
        }

    }

}


/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */

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
 * @author Gaurav Kumar (gaurav.kumar@salesforce.com)
 */
public class AuthRequestHelper {
    private static final Logger _logger = LoggerFactory.getLogger(AuthRequestHelper.class);
    private static final int AUTHORIZATION_CODE_LENGTH = 40;
    private static final char[] allowedCharacters = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();


    public static boolean validateAuthorizationRequest(AuthRequestDto authRequestDto, OAuthApplicationDto oAuthApplicationDto) throws OAuthException {
        if (StringUtils.isNotBlank(oAuthApplicationDto.getClientId()) && oAuthApplicationDto.getClientId().equals(authRequestDto.getClient_id())) {
            try {
                String decodedRedirectUri = java.net.URLDecoder.decode(authRequestDto.getRedirect_uri(), "UTF-8");
                if (StringUtils.isNotBlank(oAuthApplicationDto.getRedirectUri()) && oAuthApplicationDto.getRedirectUri().equals(decodedRedirectUri)) {
                    return true;
                } else {
                    _logger.info("Request Redirect URI '" + authRequestDto.getRedirect_uri() + "' mismatch");
                    throw new OAuthException(ResponseCodes.INVALID_OR_MISSING_REDIRECT_URI, HttpResponseStatus.BAD_REQUEST);
                }
            } catch (UnsupportedEncodingException e) {
                _logger.info("Request Redirect URI '" + authRequestDto.getRedirect_uri() + "' mismatch");
                throw new OAuthException(ResponseCodes.INVALID_OR_MISSING_REDIRECT_URI, HttpResponseStatus.BAD_REQUEST);
            }
        } else {
            _logger.info("Request Client ID '" + authRequestDto.getClient_id() + "' mismatch");
            throw new OAuthException(ResponseCodes.INVALID_OR_MISSING_CLIENT_ID, HttpResponseStatus.BAD_REQUEST);
        }
    }

    public static String generateAuthorizationCode() {
        StringBuilder buf = new StringBuilder(AUTHORIZATION_CODE_LENGTH);
        SecureRandom rand = new SecureRandom();
        for (int i = 0; i < AUTHORIZATION_CODE_LENGTH; i++) {
            buf.append(allowedCharacters[rand.nextInt(allowedCharacters.length)]);
        }
        return buf.toString();
    }

    public static boolean validateTokenRequest(TokenRequestDto tokenRequestDto, OAuthApplicationDto oAuthApplicationDto) {
        // basic check
        try {
            String decodedRedirectUri = java.net.URLDecoder.decode(tokenRequestDto.getRedirect_uri(), "UTF-8");
            if (StringUtils.isNotBlank(oAuthApplicationDto.getRedirectUri()) && oAuthApplicationDto.getRedirectUri().equals(decodedRedirectUri)) {
                if (StringUtils.isNotBlank(tokenRequestDto.getGrant_type())) {
                    if (OAuthFields.AUTHORIZATION_CODE.equals(tokenRequestDto.getGrant_type())) {
                        return true;
                    } else {
                        _logger.info("Grant Type '" + tokenRequestDto.getGrant_type() + "' is not supported");
                        throw new OAuthException(ResponseCodes.GRANT_TYPE_NOT_SUPPORTED, HttpResponseStatus.BAD_REQUEST);
                    }
                } else {
                    _logger.info("Grant Type '" + tokenRequestDto.getGrant_type() + "' mismatch");
                    throw new OAuthException(ResponseCodes.INVALID_OR_MISSING_GRANT_TYPE, HttpResponseStatus.BAD_REQUEST);
                }
            } else {
                _logger.info("Request Redirect URI '" + tokenRequestDto.getRedirect_uri() + "' mismatch");
                throw new OAuthException(ResponseCodes.INVALID_OR_MISSING_REDIRECT_URI, HttpResponseStatus.BAD_REQUEST);
            }
        } catch (UnsupportedEncodingException e) {
            _logger.info("Request Redirect URI '" + tokenRequestDto.getRedirect_uri() + "' mismatch");
            throw new OAuthException(ResponseCodes.INVALID_OR_MISSING_REDIRECT_URI, HttpResponseStatus.BAD_REQUEST);
        }

    }

}


/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */

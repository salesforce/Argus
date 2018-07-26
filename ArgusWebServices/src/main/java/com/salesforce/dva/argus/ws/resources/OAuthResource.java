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

package com.salesforce.dva.argus.ws.resources;

import com.salesforce.dva.argus.entity.OAuthAuthorizationCode;
import com.salesforce.dva.argus.service.OAuthAuthorizationCodeService;
import com.salesforce.dva.argus.ws.annotation.Description;
import com.salesforce.dva.argus.ws.business.oauth.AuthRequestHelper;
import com.salesforce.dva.argus.ws.business.oauth.OAuthFields;
import com.salesforce.dva.argus.ws.dto.AuthRequestDto;
import com.salesforce.dva.argus.ws.dto.OAuthApplicationDto;
import com.salesforce.dva.argus.ws.dto.TokenRequestDto;
import com.salesforce.dva.argus.ws.dto.TokenResponseDto;
import com.salesforce.dva.argus.ws.exception.OAuthException;
import org.apache.commons.lang.StringUtils;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.QueryStringEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.sql.Timestamp;
import static com.salesforce.dva.argus.ws.business.oauth.OAuthFields.*;
import static com.salesforce.dva.argus.ws.business.oauth.ResponseCodes.*;

/**
 * Provides oauth authorize and token methods
 * @author Gaurav Kumar (gaurav.kumar@salesforce.com) , Chandravyas Annakula(cannakula@salesforce.com)
 */
@Path("/v1.0/oauth")
@Description("Provides methods to authenticate users.")
public class OAuthResource extends AbstractResource {

    private static final Logger _logger = LoggerFactory.getLogger(OAuthResource.class);

    //~ Instance fields ******************************************************************************************************************************

    private OAuthAuthorizationCodeService authService = system.getServiceFactory().getOAuthAuthorizationCodeService();
    private String applicationName = system.getConfiguration().getValue(Property.OAUTH_APP_NAME.getName(), Property.OAUTH_APP_NAME.getDefaultValue());
    private String applicationClientId = system.getConfiguration().getValue(Property.OAUTH_APP_CLIENT_ID.getName(), Property.OAUTH_APP_CLIENT_ID.getDefaultValue());
    private String applicationClientSecret = system.getConfiguration().getValue(Property.OAUTH_APP_CLIENT_SECRET.getName(), Property.OAUTH_APP_CLIENT_SECRET.getDefaultValue());
    private String applicationRedirectURI = system.getConfiguration().getValue(Property.OAUTH_APP_REDIRECT_URI.getName(), Property.OAUTH_APP_REDIRECT_URI.getDefaultValue());
    private String oauthAuthorizeUrl = system.getConfiguration().getValue(Property.OAUTH_AUTHORIZE_URL.getName(), Property.OAUTH_AUTHORIZE_URL.getDefaultValue());
    private String oauthAuthCodeExpiry = system.getConfiguration().getValue(Property.OAUTH_AUTHORIZATION_CODE_EXPIRY_MILLIS.getName(), Property.OAUTH_AUTHORIZATION_CODE_EXPIRY_MILLIS.getDefaultValue());
    private String invalidateAuthCodeAfterUse = system.getConfiguration().getValue(Property.OAUTH_AUTHORIZATION_CODE_INVALIDATE.getName(), Property.OAUTH_AUTHORIZATION_CODE_INVALIDATE.getDefaultValue());

    //~ Methods **************************************************************************************************************************************

    /**
     * OAuth2.0 authorize method implementation to generate an authorization_code and redirect after validating the required fields
     * Reference: https://tools.ietf.org/html/rfc6749
     *
     * @param client_id     The client id of the application sending this request
     * @param redirect_uri  The client secret of the application sending this request
     * @param response_type optional
     * @param scope         optional
     * @param state         The Random String generated by requesting application for identification and for preventing CSRF attacks.
     * @param req           The Http Request
     * @param res           The Http Response
     * @throws OAuthException The Oauth Authentication Exception
     */
    @GET
    @Description("OAuth2.0 authorize method implementation to generate an authorization_code and redirect after validating the required fields")
    @Path("/authorize")
    public void authorize(
            @QueryParam(CLIENT_ID) String client_id,
            @QueryParam(REDIRECT_URI) String redirect_uri,
            @QueryParam(RESPONSE_TYPE) String response_type,
            @QueryParam(SCOPE) String scope,
            @QueryParam(STATE) String state,
            @Context HttpServletRequest req,
            @Context HttpServletResponse res) throws OAuthException {

            AuthRequestDto authRequestDto = new AuthRequestDto(client_id, response_type, redirect_uri, scope, state);
            OAuthApplicationDto oAuthApplicationDto = new OAuthApplicationDto(applicationName, applicationClientId, applicationClientSecret, applicationRedirectURI);
            OAuthAuthorizationCode authCodeEntity = null;

            if (AuthRequestHelper.validateAuthorizationRequest(authRequestDto, oAuthApplicationDto)) {
                String authorizationCode = AuthRequestHelper.generateAuthorizationCode();
                int authCodeExpiryMillis = Integer.valueOf(oauthAuthCodeExpiry);
                Timestamp expiryTimestamp = new Timestamp(System.currentTimeMillis() + authCodeExpiryMillis);
                try {
                    authCodeEntity = new OAuthAuthorizationCode(
                            authorizationCode,
                            authRequestDto.getClientId(),
                            "",
                            authRequestDto.getRedirectUri(),
                            expiryTimestamp,
                            authRequestDto.getScope(),
                            authRequestDto.getState());
                    authCodeEntity = authService.create(authCodeEntity);
                } catch (Exception e) {
                    _logger.info("Error while saving Authorization code to database: " + authorizationCode);
                    throw new OAuthException(ERR_ISSUING_AUTH_CODE, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                }
            }

            _logger.debug("Oauth request for application: " + applicationName);
            _logger.debug("Client ID: " + client_id);
            _logger.debug("Redirect URI: " + redirect_uri);
            _logger.debug("Response Type: " + response_type);
            _logger.debug("Scope: " + scope);
            _logger.debug("State: " + state);

            QueryStringEncoder enc = new QueryStringEncoder(oauthAuthorizeUrl);
            if (authCodeEntity != null && StringUtils.isNotBlank(authCodeEntity.getAuthorizationCode())) {
                enc.addParam(CODE, authCodeEntity.getAuthorizationCode());
                if (StringUtils.isNotBlank(authCodeEntity.getState())) {
                    enc.addParam(STATE, authCodeEntity.getState());
                }
                try {
                    res.sendRedirect(enc.toString());
                } catch (IOException e) {
                    _logger.info("Error while redirecting to " + authCodeEntity.getRedirectUri());
                    throw new OAuthException(ERR_ISSUING_AUTH_CODE, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                }
            } else {
                _logger.info("Error while redirecting to " + authCodeEntity.getRedirectUri());
                throw new OAuthException(ERR_ISSUING_AUTH_CODE, HttpResponseStatus.INTERNAL_SERVER_ERROR);
            }
    }

    /**
     * OAuth2.0 token method implementation to generate an access_token and refresh_token after verifying the Required fields
     * Reference: https://tools.ietf.org/html/rfc6749
     *
     * @param clientId      The client id of the application sending this request
     * @param clientSecret  The client secret of the application sending this request
     * @param grantType     Optional
     * @param code          This is required to identify the user sending this request
     * @param redirectUri   This is required to identify the user sending this request
     * @param request       HTTP Request
     * @param res           HTTP Response
     * @return TokenResponseDto Contains information related to token
     */
    @POST
    @Path("/token")
    @Produces(MediaType.APPLICATION_JSON)
    @Description("OAuth2.0 token method implementation to generate an access_token and refresh_token after verifying the Required fields")
    public TokenResponseDto token(
            @FormParam(CLIENT_ID) String clientId , // Grafana is not sending this
            @FormParam(CLIENT_SECRET) String clientSecret , // Grafana is not sending this
            @FormParam(GRANT_TYPE) String grantType ,
            @FormParam(CODE) String code ,
            @FormParam(REDIRECT_URI) String redirectUri ,
            @Context HttpServletRequest request,
            @Context HttpServletResponse res) {

        _logger.debug("Client ID: " + clientId);
        _logger.debug("Client Secure: " + clientSecret);
        _logger.debug("Grant Type: " + grantType);
        _logger.debug("Code: " + code);
        _logger.debug("Redirect URI: " + redirectUri);

        OAuthApplicationDto oAuthApplicationDto = new OAuthApplicationDto(applicationName, applicationClientId, applicationClientSecret, applicationRedirectURI);

        TokenResponseDto response = new TokenResponseDto();
        TokenRequestDto tokenRequest = new TokenRequestDto();
        tokenRequest.setClientId(clientId);
        tokenRequest.setClientSecret(clientSecret);
        tokenRequest.setCode(code);
        tokenRequest.setGrantType(grantType);
        tokenRequest.setRedirectUri(redirectUri);

        if(!AuthRequestHelper.validateTokenRequest(tokenRequest, oAuthApplicationDto)) {
            throw new OAuthException(ERR_ISSUING_AUTH_CODE, HttpResponseStatus.BAD_REQUEST);
        }
        OAuthAuthorizationCode oauthAuthorizationCode = authService.findByCodeAndRedirectURI(tokenRequest.getCode(), tokenRequest.getRedirectUri());
        if (oauthAuthorizationCode != null) {
            _logger.debug("Found row with authorization_code " + oauthAuthorizationCode.getAuthorizationCode()
                    + " , rediect_uri: " + oauthAuthorizationCode.getRedirectUri()
                    + " , expiry: " + oauthAuthorizationCode.getExpires());
            if (oauthAuthorizationCode.getExpires().before(new Timestamp(System.currentTimeMillis()))) {
                throw new OAuthException(INVALID_AUTH_CODE, HttpResponseStatus.BAD_REQUEST);
            } {
                _logger.debug("All validations successful for " + tokenRequest);
                if(Boolean.valueOf(invalidateAuthCodeAfterUse)) {
                    authService.updateExpiry(tokenRequest.getCode(), new Timestamp(0));
                }
                // get the token using the associated authorization_code and user_id
                if(StringUtils.isNotBlank(oauthAuthorizationCode.getUserId())) {
                    // generate access token and refresh token
                    JWTUtils.Tokens tokens = JWTUtils.generateTokens(oauthAuthorizationCode.getUserId());
                    if(StringUtils.isNotBlank(tokens.accessToken) && StringUtils.isNotBlank(tokens.refreshToken)) {
                        _logger.info("Generated access/refresh tokens for user: " + oauthAuthorizationCode.getUserId());
                        response.setAccess_token(tokens.accessToken);
                        response.setRefresh_token(tokens.refreshToken);
                        response.setExpires_in(JWTUtils.getTokenExpiry(tokens.accessToken));
                        response.setToken_type(OAuthFields.TOKEN_TYPE_BEARER);
                    } else {
                        throw new OAuthException(ERR_ISSUING_ACCESS_TOKEN, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                    }
                } else {
                    throw new OAuthException(ERR_ISSUING_ACCESS_TOKEN, HttpResponseStatus.INTERNAL_SERVER_ERROR);
                }
            }
        } else {
            throw new OAuthException(INVALID_AUTH_CODE, HttpResponseStatus.BAD_REQUEST);
        }

        return response;
    }



}
/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */
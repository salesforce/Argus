package com.salesforce.dva.argus.ws.resources;

import com.salesforce.dva.argus.entity.OAuthAccessToken;
import com.salesforce.dva.argus.entity.OAuthAuthorizationCode;
import com.salesforce.dva.argus.service.OAuthAccessTokenService;
import com.salesforce.dva.argus.service.OAuthAuthorizationCodeService;
import com.salesforce.dva.argus.ws.annotation.Description;
import com.salesforce.dva.argus.ws.business.oauth.AuthRequestHelper;
import com.salesforce.dva.argus.ws.business.oauth.OAuthFields;
import com.salesforce.dva.argus.ws.business.oauth.ResponseCodes;
import com.salesforce.dva.argus.ws.dto.*;
import com.salesforce.dva.argus.ws.exception.OAuthException;
import org.apache.commons.lang.StringUtils;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;
import org.jboss.netty.handler.codec.http.QueryStringEncoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.sql.Timestamp;
import static com.salesforce.dva.argus.ws.business.oauth.OAuthFields.*;
import static com.salesforce.dva.argus.ws.business.oauth.ResponseCodes.*;

/**
 * Provides oauth authorize and token methods
 * @author Gaurav Kumar (gaurav.kumar@salesforce.com)
 */
@Path("/v1.0/oauth")
@Description("Provides methods to authenticate users.")
public class OAuthResource extends AbstractResource {
    private static final Logger _logger = LoggerFactory.getLogger(OAuthResource.class);

    private OAuthAuthorizationCodeService authService = system.getServiceFactory().getOAuthAuthorizationCodeService();
    private OAuthAccessTokenService tokenService = system.getServiceFactory().getOAuthAccessTokenService();
    private String applicationName = system.getConfiguration().getValue(Property.OAUTH_APP_NAME.getName(), Property.OAUTH_APP_NAME.getDefaultValue());
    private String applicationClientId = system.getConfiguration().getValue(Property.OAUTH_APP_CLIENT_ID.getName(), Property.OAUTH_APP_CLIENT_ID.getDefaultValue());
    private String applicationClientSecret = system.getConfiguration().getValue(Property.OAUTH_APP_CLIENT_SECRET.getName(), Property.OAUTH_APP_CLIENT_SECRET.getDefaultValue());
    private String applicationRedirectURI = system.getConfiguration().getValue(Property.OAUTH_APP_REDIRECT_URI.getName(), Property.OAUTH_APP_REDIRECT_URI.getDefaultValue());
    private String oauthAuthorizeUrl = system.getConfiguration().getValue(Property.OAUTH_AUTHORIZE_URL.getName(), Property.OAUTH_AUTHORIZE_URL.getDefaultValue());
    private String oauthAuthCodeExpiry = system.getConfiguration().getValue(Property.OAUTH_AUTHORIZATION_CODE_EXPIRY_MILLIS.getName(), Property.OAUTH_AUTHORIZATION_CODE_EXPIRY_MILLIS.getDefaultValue());
    private String invalidateAuthCodeAfterUse = system.getConfiguration().getValue(Property.OAUTH_AUTHORIZATION_CODE_INVALIDATE.getName(), Property.OAUTH_AUTHORIZATION_CODE_INVALIDATE.getDefaultValue());

    /**
     * OAuth2.0 authorize method implementation to generate an authorization_code and redirect after validating the required fields
     * Reference: https://tools.ietf.org/html/rfc6749
     *
     * @param client_id     Required
     * @param redirect_uri  Required
     * @param response_type Required
     * @param scope         Required
     * @param state
     * @param req
     * @param res
     * @throws OAuthException
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
                            authRequestDto.getClient_id(),
                            "",
                            authRequestDto.getRedirect_uri(),
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
     * @param clientId
     * @param clientSecret
     * @param grantType     Required
     * @param code          Required
     * @param redirectUri   Required
     * @param request
     * @param res
     * @return TokenResponseDto
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
        tokenRequest.setClient_id(clientId);
        tokenRequest.setClient_secret(clientSecret);
        tokenRequest.setCode(code);
        tokenRequest.setGrant_type(grantType);
        tokenRequest.setRedirect_uri(redirectUri);

        if(!AuthRequestHelper.validateTokenRequest(tokenRequest, oAuthApplicationDto)) {
            throw new OAuthException(ERR_ISSUING_AUTH_CODE, HttpResponseStatus.BAD_REQUEST);
        }
        OAuthAuthorizationCode oauthAuthorizationCode = authService.findByCodeAndRedirectURI(tokenRequest.getCode(), tokenRequest.getRedirect_uri());
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
                    OAuthAccessToken oauthAccessToken = new OAuthAccessToken(
                            tokens.accessToken,
                            oauthAuthorizationCode.getClientId(),
                            oauthAuthorizationCode.getUserId(),
                            new Timestamp(JWTUtils.getTokenExpiry(tokens.accessToken)),
                            oauthAuthorizationCode.getScope(),
                            tokens.refreshToken,
                            new Timestamp(JWTUtils.getTokenExpiry(tokens.refreshToken))
                    );
                    oauthAccessToken = tokenService.create(oauthAccessToken);
                    if(oauthAccessToken != null) {
                        _logger.info("Generated access/refresh tokens for user: " + oauthAuthorizationCode.getUserId());
                        response.setAccess_token(oauthAccessToken.getAccessToken());
                        response.setRefresh_token(oauthAccessToken.getRefreshToken());
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
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
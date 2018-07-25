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
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.entity.PrincipalUser.Preference;
import com.salesforce.dva.argus.service.OAuthAuthorizationCodeService;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.system.SystemException;
import com.salesforce.dva.argus.ws.annotation.Description;
import com.salesforce.dva.argus.ws.business.oauth.OAuthFields;
import com.salesforce.dva.argus.ws.business.oauth.ResponseCodes;
import com.salesforce.dva.argus.ws.dto.OAuthAcceptDto;
import com.salesforce.dva.argus.ws.dto.OAuthAcceptResponseDto;
import com.salesforce.dva.argus.ws.dto.AuthenticatedOAuthAppDto;
import com.salesforce.dva.argus.ws.dto.PrincipalUserDto;
import com.salesforce.dva.argus.ws.exception.OAuthException;
import org.apache.commons.lang.StringUtils;
import org.jboss.netty.handler.codec.http.HttpResponseStatus;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

/**
 * Provides methods to manipulate users.
 *
 * @author  Bhinav Sura (bsura@salesforce.com), Gaurav Kumar (gaurav.kumar@salesforce.com), Chandravyas Annakula(cannakula@salesforce.com)
 */
@Path("/users")
@Description("Provides methods to manipulate users.")
public class UserResources extends AbstractResource {

    //~ Instance fields ******************************************************************************************************************************

    private UserService _uService = system.getServiceFactory().getUserService();
    private String applicationName = system.getConfiguration().getValue(Property.OAUTH_APP_NAME.getName(), Property.OAUTH_APP_NAME.getDefaultValue());
    private OAuthAuthorizationCodeService authService = system.getServiceFactory().getOAuthAuthorizationCodeService();
    private String applicationRedirectURI = system.getConfiguration().getValue(Property.OAUTH_APP_REDIRECT_URI.getName(), Property.OAUTH_APP_REDIRECT_URI.getDefaultValue());
    private String invalidateAuthCodeAfterUse = system.getConfiguration().getValue(Property.OAUTH_AUTHORIZATION_CODE_INVALIDATE.getName(),
            Property.OAUTH_AUTHORIZATION_CODE_INVALIDATE.getDefaultValue());


    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the user having the given ID.
     *
     * @param   req     The HTTP request.
     * @param   userId  The user ID to retrieve
     *
     * @return  The corresponding user DTO.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/id/{userId}")
    @Description("Returns the user having the given ID.")
    public PrincipalUserDto getUserById(@Context HttpServletRequest req,
        @PathParam("userId") final BigInteger userId) {
        if (userId == null || userId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("User Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }

        PrincipalUser remoteUser = validateAndGetOwner(req, null);
        PrincipalUser user = _uService.findUserByPrimaryKey(userId);

        if (user != null) {
            super.validateResourceAuthorization(req, user, remoteUser);
            return PrincipalUserDto.transformToDto(user);
        }
        throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
    }

    /**
     * Returns the user having the given username.
     *
     * @param   req       The HTTP request.
     * @param   userName  The username to retrieve.
     *
     * @return  The user DTO.
     *
     * @throws  WebApplicationException  If an error occurs.
     * 
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/username/{username}")
    @Description("Returns the user having the given username.")
    public PrincipalUserDto getUserByUsername(@Context HttpServletRequest req,
        @PathParam("username") final String userName) {

        Enumeration<String> headerNames = req.getHeaderNames();
        while(headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            String headerValue = req.getHeader(headerName);
            System.out.println(headerName + ": " + headerValue);
        }

        if (userName == null || userName.isEmpty()) {
            throw new WebApplicationException("Username cannot be null or empty.", Status.BAD_REQUEST);
        }

        PrincipalUser remoteUser = validateAndGetOwner(req, null);
        PrincipalUser user = _uService.findUserByUsername(userName);

        if (user != null) {
            super.validateResourceAuthorization(req, user, remoteUser);
            return PrincipalUserDto.transformToDto(user);
        } else if (!remoteUser.isPrivileged()) {
            throw new WebApplicationException(Response.Status.FORBIDDEN.getReasonPhrase(), Response.Status.FORBIDDEN);
        } else {
            throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
        }
    }

    /**
     * Creates a user.
     *
     * @param   req      The HTTP request.
     * @param   userDto  The user to create.
     *
     * @return  The updated DTO for the created user.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Description("Creates a user.")
    public PrincipalUserDto createPrincipalUser(@Context HttpServletRequest req, final PrincipalUserDto userDto) {
        PrincipalUser remoteUser = validateAndGetOwner(req, null);

        validateResourceAuthorization(req, remoteUser, remoteUser);
        if (userDto == null) {
            throw new WebApplicationException("Cannot create a null user.", Status.BAD_REQUEST);
        }

        PrincipalUser user = new PrincipalUser(remoteUser, userDto.getUserName(), userDto.getEmail());

        copyProperties(user, userDto);
        user = _uService.updateUser(user);
        return PrincipalUserDto.transformToDto(user);
    }

    /**
     * Updates the user email.
     *
     * @param   req     The HTTP request.
     * @param   userId  The ID of the user to update.
     * @param   email   The new email address.
     *
     * @return  The updated user DTO.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Path("/{userId}/email")
    @Description("Update user email")
    public PrincipalUserDto updateUserEmail(@Context HttpServletRequest req,
        @PathParam("userId") final BigInteger userId,
        @FormParam("email") final String email) {
        if (userId == null || userId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("User Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        if (email == null || email.isEmpty()) {
            throw new WebApplicationException("Cannot update with null or empty email.", Status.BAD_REQUEST);
        }

        PrincipalUser remoteUser = getRemoteUser(req);
        PrincipalUser user = _uService.findUserByPrimaryKey(userId);

        validateResourceAuthorization(req, user, remoteUser);
        if (user == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
        }
        user.setEmail(email);
        user = _uService.updateUser(user);
        return PrincipalUserDto.transformToDto(user);
    }

    /**
     * Grants or revokes privileged permissions.
     *
     * @param   req         The HTTP request.
     * @param   userId      The ID of the user to update.
     * @param   privileged  True if the user has privileged access.
     *
     * @return  The updated user DTO.
     *
     * @throws  WebApplicationException  If an error occurs.
     * @throws  SystemException          If the privileged status is unable to be changed.
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Path("/{userId}/privileged")
    @Description("Grants or revokes privileged permissions")
    public PrincipalUserDto updateUserPrivilege(@Context HttpServletRequest req,
        @PathParam("userId") final BigInteger userId,
        @FormParam("privileged") final boolean privileged) {
        validatePrivilegedUser(req);
        if (userId == null || userId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("User Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }

        PrincipalUser user = _uService.findUserByPrimaryKey(userId);

        if (user == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
        }
        try {
            Method method = PrincipalUser.class.getDeclaredMethod("setPrivileged", boolean.class);

            method.setAccessible(true);
            method.invoke(user, privileged);
        } catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
            throw new SystemException("Failed to change privileged status.", e);
        }
        user = _uService.updateUser(user);
        return PrincipalUserDto.transformToDto(user);
    }

    /**
     * Updates user preferences.
     *
     * @param   req     The HTTP request.
     * 
     * @param   userId  The ID of the user to update.
     * @param   prefs   The updated preferences.
     *
     * @return  The updated user DTO.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("{userId}/preferences")
    @Description("Update user preferences.")
    public PrincipalUserDto updateUserPreferences(@Context HttpServletRequest req,
        @PathParam("userId") final BigInteger userId, final Map<Preference, String> prefs) {
        if (userId == null || userId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("User Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        if (prefs == null) {
            throw new WebApplicationException("Cannot update with null prefs.", Status.BAD_REQUEST);
        }

        PrincipalUser remoteUser = getRemoteUser(req);
        PrincipalUser user = _uService.findUserByPrimaryKey(userId);

        validateResourceAuthorization(req, user, remoteUser);
        if (user == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
        }
        user.getPreferences().putAll(prefs);
        user = _uService.updateUser(user);
        return PrincipalUserDto.transformToDto(user);
    }

    /**
     * Method to accept oauth access by third party applications. This method associates authorization_code with the logged in username.
     * @param acceptDto Json Object which contains information for granting permission for an app to use argus OAuth resources
     * @param request   The Http Request with authorization header
     * @return OAuthAcceptResponseDto  Json Object which is returned after user grants an application to access argus oauth resources
     */
    @POST
    @Path("/accept_oauth")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Description("Approves user for oauth based access.")
    public OAuthAcceptResponseDto accept(OAuthAcceptDto acceptDto, @Context HttpServletRequest request) {
        if(StringUtils.isBlank(acceptDto.getCode())) {
            throw new OAuthException(ResponseCodes.INVALID_AUTH_CODE, HttpResponseStatus.BAD_REQUEST);
        }
        if(StringUtils.isBlank(acceptDto.getState())) {
            throw new OAuthException(ResponseCodes.INVALID_STATE, HttpResponseStatus.BAD_REQUEST);
        }

        // Check if authorization code and state is valid, no need to check expiry as it might have been invalidated by token query
        OAuthAuthorizationCode oauthAuthorizationCode = authService.findByCodeAndState(acceptDto.getCode(), acceptDto.getState());
        if (oauthAuthorizationCode == null) {
            throw new OAuthException(ResponseCodes.INVALID_AUTH_CODE_OR_STATE, HttpResponseStatus.BAD_REQUEST);
        }

        String userName=findUserByToken(request);
        if(Boolean.valueOf(invalidateAuthCodeAfterUse)) {
            authService.deleteExpiredAuthCodesByUserName(new Timestamp(System.currentTimeMillis()),userName);
        }

        // updates userid in oauth_authorization_codes table
        int updateResult = authService.updateUserId(acceptDto.getCode(), acceptDto.getState(), userName);
        if(updateResult == 0) {
            throw new OAuthException(ResponseCodes.INVALID_AUTH_CODE, HttpResponseStatus.BAD_REQUEST);
        }

        OAuthAcceptResponseDto responseDto = new OAuthAcceptResponseDto();
        responseDto.setRedirectURI(oauthAuthorizationCode.getRedirectUri());

        return responseDto ;
    }

    /**
     * OAuth2.0 UserInfo reference implementation to get logged in user information using the access token
     * @param req   The Http Request with authorization header
     * @return      User Information Json Object
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/userinfo")
    @Description("Returns the user info of the user who is logged in.")
    public PrincipalUserDto userInfo(@Context HttpServletRequest req) {
        String userName=findUserByToken(req);
        PrincipalUser user = _uService.findUserByUsername(userName);
        return PrincipalUserDto.transformToDto(user);
    }

    private String findUserByToken(HttpServletRequest req)
    {
        String token = req.getHeader(OAuthFields.AUTHORIZATION);
        if(StringUtils.isBlank(token)) {
            throw new OAuthException(ResponseCodes.INVALID_ACCESS_TOKEN, HttpResponseStatus.BAD_REQUEST);
        }
        return  JWTUtils.getUsername(token);

    }

    /**
     * Returns redirectURI based on whether this user has previously accepted the authorization page
     *
     * @param req  The Http Request with authorization header
     * @return     Returns either redirectURI or OauthException
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/check_oauth_access")
    @Description("Returns redirectURI based on whether this user has previously accepted the authorization page")
    public OAuthAcceptResponseDto checkOauthAccess(@Context HttpServletRequest req) {
        String userName=findUserByToken(req);
        int result = authService.countByUserId(userName);
        OAuthAcceptResponseDto responseDto = new OAuthAcceptResponseDto();
        if (result==0) {
            throw new OAuthException(ResponseCodes.ERR_FINDING_USERNAME, HttpResponseStatus.BAD_REQUEST);
        }
        else {
            responseDto.setRedirectURI(applicationRedirectURI);
            return responseDto;
        }
    }

    /**
     * Returns list of Oauth approved apps of argus for this user
     *
     * @param req   The Http Request with authorization header
     * @return      Returns list of Oauth approved apps
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/oauth_approved_apps")
    @Description("Returns list of Oauth approved apps of argus for this user")
    public List<AuthenticatedOAuthAppDto> authenticatedApps(@Context HttpServletRequest req) {
        String userName=findUserByToken(req);
        int result = authService.countByUserId(userName);
        AuthenticatedOAuthAppDto app = new AuthenticatedOAuthAppDto();
        List<AuthenticatedOAuthAppDto> listApps = new ArrayList<>();
        if (result==0) {
            return listApps;
        }
        else {
            app.setApplicationName(applicationName);
            listApps.add(app);
            return listApps;
        }
    }

    /**
     * Revokes Oauth access of app from argus for a particular user
     *
     * @param req       The Http Request with authorization header
     * @param appName   Application Name which is recognized by Argus Oauth
     * @return          Returns either Success or OAuthException
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/revoke_oauth_access/{appName}")
    @Description("Revokes Oauth access of app from argus for a particular user")
    public Response removeAccess(@Context HttpServletRequest req,@PathParam("appName") String appName) {
        String userName=findUserByToken(req);

        if(appName.equalsIgnoreCase(applicationName))
        {
            authService.deleteByUserId(userName);
            return Response.status(Status.OK).build();
        }
        else
        {
            throw new OAuthException(ResponseCodes.ERR_DELETING_APP, HttpResponseStatus.BAD_REQUEST);
        }

    }

}
/* Copyright (c) 2018, Salesforce.com, Inc.  All rights reserved. */

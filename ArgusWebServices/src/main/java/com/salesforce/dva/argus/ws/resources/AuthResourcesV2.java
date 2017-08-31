/*
 * Copyright (c) 2016, Salesforce.com, Inc.
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

import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.AuthService;
import com.salesforce.dva.argus.ws.annotation.Description;
import com.salesforce.dva.argus.ws.dto.CredentialsDto;
import com.salesforce.dva.argus.ws.filter.AuthFilter;

import io.jsonwebtoken.ExpiredJwtException;

import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Provides methods to authenticate users.
 *
 * @author  Bhinav Sura (bsura@salesforce.com)
 */
@Path("/v2/auth")
@Description("Provides methods to authenticate users.")
public class AuthResourcesV2 extends AbstractResource {
	
    //~ Instance fields ******************************************************************************************************************************

    private AuthService authService = system.getServiceFactory().getAuthService();

    //~ Methods **************************************************************************************************************************************

    /**
     * Authenticates a user and return JsonWebTokens (AccessToken and RefreshToken).
     *
     * @param   req    The HTTP request.
     * @param   creds  The credentials with which to authenticate.
     *
     * @return  The tokens (access and refresh) or Exception if authentication fails.
     *
     * @throws  WebApplicationException  If the user is not authenticated.
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Description("Authenticates a user and returns access and refresh tokens.")
    @Path("/login")
    public Response login(@Context HttpServletRequest req, final CredentialsDto creds) {
        try {
            PrincipalUser user = authService.getUser(creds.getUsername(), creds.getPassword());

            if (user != null) {
                JWTUtils.Tokens tokens = JWTUtils.generateTokens(user.getUserName());
                req.setAttribute(AuthFilter.USER_ATTRIBUTE_NAME, user.getUserName());
                return Response.ok(tokens).build();
            } else {
                throw new WebApplicationException("User does not exist. Please provide valid credentials.", Response.Status.UNAUTHORIZED);
            }
        } catch (Exception ex) {
            throw new WebApplicationException("Exception: " + ex.getMessage(), Response.Status.UNAUTHORIZED);
        }
    }
    
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Description("Authenticates a user and returns access and refresh tokens.")
    @Path("/token/refresh")
    public Response refreshAccessToken(@Context HttpServletRequest req, final Map<String, String> map) {
    	
    	String username = null;
    	String refreshToken = map.get("refreshToken");
    	try {
    		username = JWTUtils.validateTokenAndGetSubj(refreshToken, JWTUtils.TokenType.REFRESH);
    		JWTUtils.Tokens tokens = new JWTUtils.Tokens(JWTUtils.generateAccessToken(username), refreshToken);
		req.setAttribute(AuthFilter.USER_ATTRIBUTE_NAME, username);
        	return Response.ok(tokens).build();
    	} catch(ExpiredJwtException ex) {
    		throw new WebApplicationException("Your Refresh token has expired. You can no longer use it to obtain a new Access token. "
    				+ "Please authenticate with the /login endpoint to obtain a new pair of tokens.", Response.Status.UNAUTHORIZED);
    	} catch(Exception ex) {
    		throw new WebApplicationException("Exception: " + ex.getMessage(), Response.Status.BAD_REQUEST);
    	}
    	
    }
    
    /**
     * Does not do anything. Is only supported for backwards compatibility. 
     *
     * @param   req  The HTTP request.
     *
     * @return  A message stating that the logout was successful.
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Description("Does not do anything. Is only supported for backwards compatibility.")
    @Path("/logout")
    public Response logout(@Context HttpServletRequest req) {
        return Response.ok("You have logged out.").build();
    }
    
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

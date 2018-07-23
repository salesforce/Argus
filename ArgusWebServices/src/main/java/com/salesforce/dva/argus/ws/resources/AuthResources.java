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

import com.salesforce.dva.argus.ws.annotation.Description;
import com.salesforce.dva.argus.ws.dto.CredentialsDto;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.core.UriInfo;

/**
 * Provides methods to authenticate users.
 *
 * @author  Bhinav Sura (bsura@salesforce.com)
 */
@Path("/auth")
@Description("Provides methods to authenticate users.")
public class AuthResources extends AbstractResource {

    //~ Instance fields ******************************************************************************************************************************

    //~ Methods **************************************************************************************************************************************

    /**
     * Authenticates a user principal.
     *
     * @param uriInfo  URI info
     * @param   creds  The credentials with which to authenticate.
     *
     * @return  Response containing the required information.
     *
     * @throws  WebApplicationException  If the user is not authenticated.
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Description("Authenticates a user principal.")
    @Path("/login")
    public Response login(@Context UriInfo uriInfo, final CredentialsDto creds) {
    	Response response = Response.temporaryRedirect(UriBuilder.fromUri(uriInfo.getBaseUri() + "v2/auth/login").build()).build();
    	return response;
    }

    /**
     * Terminates a user session. Not needed for existing auth api. This is just a placeholder method. 
     *
     * @param uriInfo URI info
     * @return  A message stating that the logout was successful.
     */
    @GET
    @Produces(MediaType.TEXT_PLAIN)
    @Description("Terminates a user session. Not needed for existing auth api. This is just a placeholder method.")
    @Path("/logout")
    public Response logout(@Context UriInfo uriInfo) {
    	 return Response.ok("You have logged out.").build();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

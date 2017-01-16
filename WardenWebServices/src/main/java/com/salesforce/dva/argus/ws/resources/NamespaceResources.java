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

import com.salesforce.dva.argus.entity.Namespace;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.NamespaceService;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.ws.annotation.Description;
import com.salesforce.dva.argus.ws.dto.NamespaceDto;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
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
 * Provides methods to manipulate namespaces.
 *
 * @author  Bhinav Sura (bsura@salesforce.com), Raj Sarkapally (rsarkapally@salesforce.com)
 */
@Path("/namespace")
@Description("Provides methods to manipulate namespaces.")
public class NamespaceResources extends AbstractResource {

    //~ Instance fields ******************************************************************************************************************************

    private NamespaceService _namespaceService = system.getServiceFactory().getNamespaceService();
    private UserService _userService = system.getServiceFactory().getUserService();

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns namespaces owned by the user.
     *
     * @param   req  The HTTP request.
     *
     * @return  The namespaces owned by the user.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Description("Returns namespaces owned by user.")
    public List<NamespaceDto> getNamespacesByOwner(@Context HttpServletRequest req) {
        PrincipalUser remoteUser = validateAndGetOwner(req, null);
        List<Namespace> namespaces = _namespaceService.findNamespacesByOwner(remoteUser);

        return NamespaceDto.transformToDto(namespaces);
    }

    /**
     * Creates a new namespace.
     * 
     *
     * @param   req           The HTTP request.
     * @param   namespaceDto  The namespace to create.
     *
     * @return  The updated namespace DTO for the created namespace.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Description("Creates a new namepsace.")
    public NamespaceDto createNamespace(@Context HttpServletRequest req, NamespaceDto namespaceDto) {
        if (namespaceDto == null) {
            throw new WebApplicationException("Null namespace object cannot be created.", Status.BAD_REQUEST);
        }

        PrincipalUser remoteUser = validateAndGetOwner(req, null);
        Set<PrincipalUser> users = _getPrincipalUserByUserName(namespaceDto.getUsernames());
        Namespace namespace = new Namespace(remoteUser, namespaceDto.getQualifier(), remoteUser, users);

        return NamespaceDto.transformToDto(_namespaceService.createNamespace(namespace));
    }

    /**
     * Update users allowed to use this namespace.
     *
     * @param   req          The HTTP request.
     * @param   namespaceId  The namespace ID to update.
     * @param   usernames    The list of authorized users.
     *
     * @return  The updated namespace.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{namespaceId}/users")
    @Description("Update users allowed to use this namespace.")
    public NamespaceDto updateUsersForNamespace(@Context HttpServletRequest req,
        @PathParam("namespaceId") final BigInteger namespaceId, final Set<String> usernames) {
        PrincipalUser remoteUser = getRemoteUser(req);

        if (namespaceId == null || namespaceId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Namespace Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        if (usernames == null) {
            throw new WebApplicationException("Cannot update with null users.", Status.BAD_REQUEST);
        }

        Namespace namespace = _namespaceService.findNamespaceByPrimaryKey(namespaceId);

        if (namespace == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
        }
        validateResourceAuthorization(req, namespace.getCreatedBy(), remoteUser);
        namespace.setUsers(_getPrincipalUserByUserName(usernames));
        namespace = _namespaceService.updateNamespace(namespace);
        return NamespaceDto.transformToDto(namespace);
    }

    /**
     * Updates a namespace.
     *
     * @param   req           The HTTP request.
     * @param   namespaceId   The ID of the namespace to update.
     * @param   newNamespace  The updated namespace data.
     *
     * @return  The updated namespace.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{namespaceId}")
    @Description("Update the namespace.")
    public NamespaceDto updateNamespace(@Context HttpServletRequest req,
        @PathParam("namespaceId") final BigInteger namespaceId, NamespaceDto newNamespace) {
        PrincipalUser remoteUser = getRemoteUser(req);

        if (namespaceId == null || namespaceId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Namespace Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        if (newNamespace == null) {
            throw new WebApplicationException("Cannot update null namespace.", Status.BAD_REQUEST);
        }

        Namespace oldNamespace = _namespaceService.findNamespaceByPrimaryKey(namespaceId);

        if (oldNamespace == null) {
            throw new WebApplicationException("The namespace with id " + namespaceId + " does not exist", Response.Status.NOT_FOUND);
        }
        if (!oldNamespace.getQualifier().equals(newNamespace.getQualifier())) {
            throw new WebApplicationException("The qualifier can not be updated.", Response.Status.NOT_FOUND);
        }
        validateResourceAuthorization(req, oldNamespace.getCreatedBy(), remoteUser);

        Set<PrincipalUser> users = _getPrincipalUserByUserName(newNamespace.getUsernames());

        if (!users.contains(oldNamespace.getOwner())) {
            users.add(oldNamespace.getOwner());
        }
        oldNamespace.setUsers(users);
        return NamespaceDto.transformToDto(_namespaceService.updateNamespace(oldNamespace));
    }

    private Set<PrincipalUser> _getPrincipalUserByUserName(Set<String> useNames) {
        Set<PrincipalUser> result = new HashSet<>();

        // Test when usernames is null
        for (String username : useNames) {
            PrincipalUser user = _userService.findUserByUsername(username);

            if (user != null) {
                result.add(user);
            } else {
                throw new WebApplicationException("At least one username from the list of usernames does not exist.", Status.BAD_REQUEST);
            }
        }
        return result;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

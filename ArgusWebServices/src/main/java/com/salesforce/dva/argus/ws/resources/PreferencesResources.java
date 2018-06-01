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

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import com.salesforce.dva.argus.entity.*;
import com.salesforce.dva.argus.service.ChartService;
import com.salesforce.dva.argus.service.PreferencesService;
import com.salesforce.dva.argus.ws.annotation.Description;
import com.salesforce.dva.argus.ws.dto.ChartDto;
import com.salesforce.dva.argus.ws.dto.PreferencesDto;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides methods to perform CRUD on Preferences objects.
 *
 * @author Chandravyas Annakula (cannakula@salesforce.com)
 */
@Path("v1/preferences")
@Description("Provides methods to perform CRUD on Preferences objects.")
public class PreferencesResources extends AbstractResource {

    //~ Instance fields ******************************************************************************************************************************

    private PreferencesService _preferencesService = system.getServiceFactory().getPreferencesService();

    //~ Methods **************************************************************************************************************************************

    /**
     *
     * @param req               The Http Request
     * @param preferencesDto    The preferences object to create
     * @return  The corresponding updated DTO for the created preferences
     *
     * @throws  WebApplicationException  If an error occurs while creating the preferences object.
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Description("Create a preferences object.")
    public PreferencesDto createPreferences(@Context HttpServletRequest req, PreferencesDto preferencesDto) {

        if (preferencesDto == null) {
            throw new WebApplicationException("Cannot create a null preferences object.", Response.Status.BAD_REQUEST);
        }

        PrincipalUser remoteUser = getRemoteUser(req);
        PrincipalUser owner;

        String userName = preferencesDto.getUserName();
        if (userName == null || userName.isEmpty() || userName.equalsIgnoreCase(remoteUser.getUserName())) {
            //If ownerName is not present or if it is present and equal to remote username, then return remoteUser.
            owner = remoteUser;
        } else if (remoteUser.isPrivileged()) {
            owner = userService.findUserByUsername(userName);
            if (owner == null) {
                throw new WebApplicationException(userName + ": User does not exist.", Response.Status.NOT_FOUND);
            }
        } else {
            //Remote user is not privileged and ownerName is not equal to remoteUser.username
            throw new WebApplicationException("You are not authorized to access Preferences owned by user: " + userName, Response.Status.FORBIDDEN);
        }

        JPAEntity entity = null;
        if (preferencesDto.getEntityId() != null) {
            //For now, we are only allowing preferences Charts and Dashboards. Hence entityId must be a dashboardId or a chartId
            PrincipalUser associatedEntityOwner;
            entity = _preferencesService.getAssociatedEntity(preferencesDto.getEntityId());
            if (entity == null) {
                throw new WebApplicationException("Entity id: " + preferencesDto.getEntityId() + " does not exist.", Response.Status.BAD_REQUEST);
            } else if (entity instanceof Dashboard) {
                associatedEntityOwner = Dashboard.class.cast(entity).getOwner();
            } else if (entity instanceof Chart) {
                associatedEntityOwner = Chart.class.cast(entity).getOwner();
            } else {
                throw new WebApplicationException("Unknown entity type associated with this chart. Please use either a dashboard id or a chart id.", Response.Status.BAD_REQUEST);
            }
            _validateResourceAuthorization(remoteUser, associatedEntityOwner);
        } else {
            throw new WebApplicationException("entityId cannot be null", Response.Status.BAD_REQUEST);
        }
        Preferences preferences = new Preferences(remoteUser, owner, entity, preferencesDto.getPreferences());
        preferences = _preferencesService.updatePreferences(preferences);
        preferencesDto = PreferencesDto.transformToDto(preferences);
        return preferencesDto;
    }

    private void _validateResourceAuthorization(PrincipalUser remoteUser, PrincipalUser resourceOwner) {
        if (!remoteUser.isPrivileged() && !remoteUser.equals(resourceOwner)) {
            throw new WebApplicationException("You are not authorized to access this resource.", Response.Status.FORBIDDEN);
        }
    }

    /**
     *
     * @param req       The Http Request
     * @param entityId  Preferences of the entityId to get
     * @return  The corresponding preferences
     *
     * @throws  WebApplicationException  If no corresponding preferences exists.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Description("Gets a preferences object.")
    public PreferencesDto getPreferencesByID(@Context HttpServletRequest req, @QueryParam("entityId") BigInteger entityId) {


        if (entityId == null || entityId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("entityId cannot be null and must be a positive non-zero number.", Response.Status.BAD_REQUEST);
        }

        PrincipalUser remoteUser = getRemoteUser(req);

        JPAEntity entity = null;
        if (entityId != null) {
            //TODO: For now, we are only allowing preferences Charts and Dashboards. Hence entityId must be a dashboardId or a chartId
            PrincipalUser associatedEntityOwner;
            entity = _preferencesService.getAssociatedEntity(entityId);
            if (entity == null) {
                throw new WebApplicationException("Entity id: " + entityId + " does not exist.", Response.Status.BAD_REQUEST);
            } else if (entity instanceof Dashboard) {
                associatedEntityOwner = Dashboard.class.cast(entity).getOwner();
            } else if (entity instanceof Chart) {
                associatedEntityOwner = Chart.class.cast(entity).getOwner();
            } else {
                throw new WebApplicationException("Unknown entity type associated with this chart. Please use either a dashboard id or a chart id.", Response.Status.BAD_REQUEST);
            }
            _validateResourceAuthorization(remoteUser, associatedEntityOwner);
        }
        Preferences preferences = _preferencesService.getPreferencesByEntity(entityId);
        if (preferences==null) {
            throw new WebApplicationException("Preferences for Entity id: " + entityId + " does not exist.", Response.Status.NOT_FOUND);
        }
        PreferencesDto preferencesDto = PreferencesDto.transformToDto(preferences);
        return preferencesDto;
    }


    /**
     *
     * @param req               The Http Request
     * @param preferencesDto    New Preferences object to update
     * @return  Updated preferences object
     *
     * @throws  WebApplicationException  If an error occurs while updating preferences.
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Description("Updates preferences object.")
    public PreferencesDto updatePreferences(@Context HttpServletRequest req, PreferencesDto preferencesDto) {

        if (preferencesDto == null) {
            throw new WebApplicationException("Preferences object cannot be null.", Response.Status.BAD_REQUEST);
        }

        PrincipalUser remoteUser = getRemoteUser(req);


        JPAEntity entity = null;
        if (preferencesDto.getEntityId() != null) {
            // For now, we are only allowing preferences Charts and Dashboards. Hence entityId must be a dashboardId or a chartId
            PrincipalUser associatedEntityOwner;
            entity = _preferencesService.getAssociatedEntity(preferencesDto.getEntityId());
            if (entity == null) {
                throw new WebApplicationException("Entity id: " + preferencesDto.getEntityId() + " does not exist.", Response.Status.BAD_REQUEST);
            } else if (entity instanceof Dashboard) {
                associatedEntityOwner = Dashboard.class.cast(entity).getOwner();
            } else if (entity instanceof Chart) {
                associatedEntityOwner = Chart.class.cast(entity).getOwner();
            } else {
                throw new WebApplicationException("Unknown entity type associated with this chart. Please use either a dashboard id or a chart id.", Response.Status.BAD_REQUEST);
            }
            _validateResourceAuthorization(remoteUser, associatedEntityOwner);
        } else {
            throw new WebApplicationException("entityId cannot be null", Response.Status.BAD_REQUEST);
        }

        Preferences existingPreferences = _preferencesService.getPreferencesByEntity(preferencesDto.getEntityId());
        if (existingPreferences == null) {
            throw new WebApplicationException("Preferences for entityID: " + preferencesDto.getEntityId() + " does not exist.",
                    Response.Status.NOT_FOUND);
        }

        copyProperties(existingPreferences, preferencesDto);
        existingPreferences.setModifiedBy(remoteUser);
        preferencesDto = PreferencesDto.transformToDto(_preferencesService.updatePreferences(existingPreferences));
        return preferencesDto;
    }

}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
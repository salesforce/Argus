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

import com.salesforce.dva.argus.entity.Dashboard;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.DashboardService;
import com.salesforce.dva.argus.ws.annotation.Description;
import com.salesforce.dva.argus.ws.dto.DashboardDto;
import com.salesforce.dva.argus.ws.listeners.ArgusWebServletListener;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

/**
 * Provides methods to manipulate dashboards.
 *
 * @author  Bhinav Sura (bsura@salesforce.com)
 */
@Path("/dashboards")
@Description("Provides methods to manipulate dashboards.")
public class DashboardResources extends AbstractResource {

    //~ Instance fields ******************************************************************************************************************************

    private DashboardService dService = ArgusWebServletListener.getSystem().getServiceFactory().getDashboardService();

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns all dashboards filtered by owner.
     *
     * @param   req            The HTTP request.
     * @param   dashboardName  The dashboard name filter.
     * @param   ownerName      The owner name filter.
     *
     * @return  The list of filtered dashboards.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Description("Returns all dashboards.")
    public List<DashboardDto> getDashboards(@Context HttpServletRequest req,
        @QueryParam("dashboardName") String dashboardName,
        @QueryParam("owner") String ownerName) {
        Set<Dashboard> result = new HashSet<>();
        PrincipalUser owner = validateAndGetOwner(req, ownerName);

        if (dashboardName != null && !dashboardName.isEmpty()) {
            Dashboard dashboard = dService.findDashboardByNameAndOwner(dashboardName, owner);

            if (dashboard != null) {
                result.add(dashboard);
            }
        } else {
            if (owner.isPrivileged()) {
                result.addAll(dService.findDashboards(null));
            } else {
                result.addAll(dService.findDashboardsByOwner(owner));
                result.addAll(dService.findSharedDashboards());
            }
        }
        return DashboardDto.transformToDto(new LinkedList<>(result));
    }

    /**
     * Returns a dashboard by its ID.
     *
     * @param   req          The HTTP request.
     * @param   dashboardId  The dashboard ID to retrieve.
     *
     * @return  The corresponding dashboard.
     *
     * @throws  WebApplicationException  If no corresponding dashboard exists.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{dashboardId}")
    @Description("Returns a dashboard by its ID.")
    public DashboardDto getDashboardByID(@Context HttpServletRequest req,
        @PathParam("dashboardId") BigInteger dashboardId) {
        if (dashboardId == null || dashboardId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Dashboard Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }

        Dashboard dashboard = dService.findDashboardByPrimaryKey(dashboardId);

        if (dashboard != null && !dashboard.isShared()) {
            validateAndGetOwner(req, null);
            validateResourceAuthorization(req, dashboard.getOwner(), getRemoteUser(req));
        }
        if (dashboard != null) {
            return DashboardDto.transformToDto(dashboard);
        }
        throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
    }

    /**
     * Creates a dashboard.
     *
     * @param   req           The HTTP request.
     * @param   dashboardDto  The dashboard to create.
     *
     * @return  The corresponding updated DTO for the created dashboard.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Description("Creates a dashboard.")
    public DashboardDto createDashboard(@Context HttpServletRequest req, DashboardDto dashboardDto) {
        if (dashboardDto == null) {
            throw new WebApplicationException("Null dashboard object cannot be created.", Status.BAD_REQUEST);
        }

        PrincipalUser owner = validateAndGetOwner(req, dashboardDto.getOwnerName());
        Dashboard dashboard = new Dashboard(getRemoteUser(req), dashboardDto.getName(), owner);

        copyProperties(dashboard, dashboardDto);
        return DashboardDto.transformToDto(dService.updateDashboard(dashboard));
    }

    /**
     * Updates a dashboard having the given ID.
     *
     * @param   req           The HTTP request.
     * @param   dashboardId   The dashboard ID to update.
     * @param   dashboardDto  The updated date.
     *
     * @return  The updated dashboard DTO.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{dashboardId}")
    @Description("Updates a dashboard having the given ID.")
    public DashboardDto updateDashboard(@Context HttpServletRequest req,
        @PathParam("dashboardId") BigInteger dashboardId, DashboardDto dashboardDto) {
        if (dashboardId == null || dashboardId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Dashboard Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        if (dashboardDto == null) {
            throw new WebApplicationException("Null object cannot be updated.", Status.BAD_REQUEST);
        }

        PrincipalUser owner = validateAndGetOwner(req, dashboardDto.getOwnerName());
        Dashboard oldDashboard = dService.findDashboardByPrimaryKey(dashboardId);

        if (oldDashboard == null) {
            throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
        }
        validateResourceAuthorization(req, oldDashboard.getOwner(), owner);
        copyProperties(oldDashboard, dashboardDto);
        oldDashboard.setModifiedBy(getRemoteUser(req));
        return DashboardDto.transformToDto(dService.updateDashboard(oldDashboard));
    }

    /**
     * Deletes the dashboard having the given ID.
     *
     * @param   req          The HTTP request.
     * @param   dashboardId  The dashboard ID to delete.
     *
     * @return  An empty body if the delete was successful.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{dashboardId}")
    @Description("Deletes the dashboard having the given ID.")
    public Response deleteDashboard(@Context HttpServletRequest req,
        @PathParam("dashboardId") BigInteger dashboardId) {
        if (dashboardId == null || dashboardId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Dashboard Id cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }

        Dashboard dashboard = dService.findDashboardByPrimaryKey(dashboardId);

        if (dashboard != null) {
            validateResourceAuthorization(req, dashboard.getOwner(), getRemoteUser(req));
            dService.deleteDashboard(dashboard);
            return Response.status(Status.OK).build();
        }
        throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

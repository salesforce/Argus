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

import com.salesforce.dva.argus.entity.Chart;
import com.salesforce.dva.argus.entity.Dashboard;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.ChartService;
import com.salesforce.dva.argus.service.DashboardService;
import com.salesforce.dva.argus.ws.annotation.Description;
import com.salesforce.dva.argus.ws.dto.DashboardDto;
import com.salesforce.dva.argus.ws.listeners.ArgusWebServletListener;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
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
	private ChartService _chartService = system.getServiceFactory().getChartService();

	//~ Methods **************************************************************************************************************************************

	/**
	 * Return all dashboard objects filtered by owner.
	 * @param   dashboardName  				The dashboard name filter.
	 * @param   owner          				The principlaUser owner for owner name filter.
	 * @param	populateMetaFieldsOnly		The flag to determine if only meta fields should be populated.
     * @param   version                     The version of the dashboard to return. It is either null or not empty
	 *
	 * @return  The list of filtered alerts in alert object.
	 */
	private List<Dashboard> _getDashboardsByOwner(String dashboardName, PrincipalUser owner, boolean populateMetaFieldsOnly, String version) {

		List<Dashboard> result = new ArrayList<>();

		if (dashboardName != null && !dashboardName.isEmpty()) {
			Dashboard dashboard = dService.findDashboardByNameAndOwner(dashboardName, owner);
			if (dashboard == null) {
				throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
			}

			result.add(dashboard);
		} else {
			if(owner.isPrivileged()) {
				result = populateMetaFieldsOnly ? dService.findDashboards(null, true, version) : dService.findDashboards(null, false, version);
			} else {
				result = populateMetaFieldsOnly ? dService.findDashboardsByOwner(owner, true, version) : dService.findDashboardsByOwner(owner, false, version);
			}
		}

		return result;
	}

	private List<Dashboard> getDashboardsObj(String dashboardName, PrincipalUser owner, boolean shared, boolean populateMetaFieldsOnly, Integer limit,String version) {

		Set<Dashboard> result = new HashSet<>();

		result.addAll(_getDashboardsByOwner(dashboardName, owner, populateMetaFieldsOnly,version));
		if(shared) {
			result.addAll(populateMetaFieldsOnly ? dService.findSharedDashboards(true, null, limit, version) : dService.findSharedDashboards(false, null, limit, version));
		}

		return new ArrayList<>(result);
	}
	
	private List<Dashboard> getSharedDashboardsObj(boolean populateMetaFieldsOnly, PrincipalUser owner, Integer limit, String version) {

		Set<Dashboard> result = new HashSet<>();
		result.addAll(populateMetaFieldsOnly ? dService.findSharedDashboards(true, owner, limit, version) : dService.findSharedDashboards(false, owner, limit,version));
		return new ArrayList<>(result);
	}
	

	/**
	 * Returns all dashboards' metadata filtered by owner.
	 *
	 * @param   req            The HTTP request.
	 * @param   dashboardName  The dashboard name filter.
	 * @param   ownerName      The owner name filter.
	 * @param shared           Filter shared dashboard
	 * @param   limit          The maximum number of rows to return.
     * @param   version        The version of the dashboard to return. It is either null or not empty
	 *
	 * @return  The list of filtered dashboards' metadata.
	 */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/meta")
	@Description("Returns all dashboards' metadata.")
	public List<DashboardDto> getDashboardsMeta(@Context HttpServletRequest req, @QueryParam("dashboardName") String dashboardName,
			@QueryParam("owner") String ownerName,
			@QueryParam("shared") @DefaultValue("true") boolean shared,
			@QueryParam("limit")  Integer limit,
            @QueryParam("version")  String version) {


		PrincipalUser owner = validateAndGetOwner(req, ownerName);
		List<Dashboard> result = getDashboardsObj(dashboardName, owner, shared, true, limit,version);
		return DashboardDto.transformToDto(result);
	}

	/**
	 * Returns all dashboards filtered by owner.
	 *
	 * @param   req            The HTTP request.
	 * @param   dashboardName  The dashboard name filter.
	 * @param   ownerName      The owner name filter.
	 * @param shared           Filter shared dashboards
	 * @param   limit          The maximum number of rows to return.
     * @param   version        The version of the dashboard to return. It is either null or not empty
	 * 
	 * @return  The list of filtered dashboards.
	 */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Description("Returns all dashboards.")
	public List<DashboardDto> getDashboards(@Context HttpServletRequest req, @QueryParam("dashboardName") String dashboardName,
			@QueryParam("owner") String ownerName,
			@QueryParam("shared") @DefaultValue("true") boolean shared,
			@QueryParam("limit")  Integer limit,
            @QueryParam("version") String version) {

		PrincipalUser owner = validateAndGetOwner(req, ownerName);
		List<Dashboard> result = getDashboardsObj(dashboardName, owner, shared, false, limit, version);
		return DashboardDto.transformToDto(result);
	}

	/**
	 * Returns all shared dashboards with filtering.
	 *
	 * @param   req            The HTTP request.
	 * @param   ownerName      The owner of shared dashboards to filter on. It is optional.
	 * @param   limit          The maximum number of results to return. It is optional.
     * @param   version        The version of the dashboard to return. It is either null or not empty.
	 * 
	 * @return  The list of all shared dashboards. Will never be null but may be empty.
	 */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/shared")
	@Description("Returns all shared dashboards.")
	public List<DashboardDto> getSharedDashboards(@Context HttpServletRequest req,
									@QueryParam("ownername") String ownerName,
									@QueryParam("limit")  Integer limit,
                                    @QueryParam("version") String version){
		PrincipalUser owner = null;
		if(ownerName != null){
			owner = userService.findUserByUsername(ownerName);
			if(owner == null){
				throw new WebApplicationException("Owner not found", Response.Status.NOT_FOUND);
			}
		}
		List<Dashboard> result = getSharedDashboardsObj(false, owner, limit,version);
		return DashboardDto.transformToDto(result);
	}
	
	/**
	 * Returns the list of all shared dashboards with only metadata information.
	 *
	 * @param   req        The HttpServlet request object. Cannot be null.
	 * @param   ownerName  Name of the owner. It is optional.
	 * @param   limit      The maximum number of results to return. It is optional.
     * @param   version    The version of the dashboard to return. It is either null or not empty
	 *
	 * @return  The list of all shared dashboards with meta information only. Will never be null but may be empty.
	 */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/shared/meta")
	@Description("Returns all shared dashboards' metadata.")
	public List<DashboardDto> getSharedDashboardsMeta(@Context HttpServletRequest req,
									@QueryParam("ownername") String ownerName,
									@QueryParam("limit")  Integer limit,
                                    @QueryParam("version") String version){
		PrincipalUser owner = null;
		if(ownerName != null){
			owner = userService.findUserByUsername(ownerName);
			if(owner == null){
				throw new WebApplicationException("Owner not found", Response.Status.NOT_FOUND);
			}
		}
		List<Dashboard> result = getSharedDashboardsObj(true, owner, limit,version);
		return DashboardDto.transformToDto(result);
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
			for(Chart c:_chartService.getChartsByOwnerForEntity(getRemoteUser(req),dashboard.getId()))
			{
				_chartService.deleteChart(c);
			}
			dService.deleteDashboard(dashboard);
			return Response.status(Status.OK).build();
		}
		throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
	}

}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

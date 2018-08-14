package com.salesforce.dva.argus.ws.resources;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

import com.salesforce.dva.argus.entity.*;
import com.salesforce.dva.argus.service.ChartService;
import com.salesforce.dva.argus.service.DashboardService;
import com.salesforce.dva.argus.ws.annotation.Description;
import com.salesforce.dva.argus.ws.dto.ChartDto;

/**
 * Provides methods to perform CRUD on Chart objects.
 *
 * @author  Bhinav Sura (bsura@salesforce.com), Chandravyas Annakula(cannakula@salesforce.com)
 */
@Path("v1/charts")
@Description("Provides methods to perform CRUD on Chart objects.")
public class ChartResources extends AbstractResource {
	
	//~ Instance fields ******************************************************************************************************************************

	private ChartService _chartService = system.getServiceFactory().getChartService();
    private DashboardService _dService = system.getServiceFactory().getDashboardService();

	//~ Methods **************************************************************************************************************************************
	
	@POST
	@Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Description("Create a chart object.")
	public ChartDto createChart(@Context HttpServletRequest req, ChartDto chartDto) {
		
		if (chartDto == null) {
			throw new WebApplicationException("Cannot create a null chart object.", Status.BAD_REQUEST);
		}

		PrincipalUser remoteUser = getRemoteUser(req);
		PrincipalUser owner;
		
		String ownerName = chartDto.getOwnerName();
        if (ownerName == null || ownerName.isEmpty() || ownerName.equalsIgnoreCase(remoteUser.getUserName())) {
        	//If ownerName is not present or if it is present and equal to remote username, then return remoteUser.
            owner = remoteUser;
        } else if (remoteUser.isPrivileged()) {
            owner = userService.findUserByUsername(ownerName);
            if (owner == null) {
                throw new WebApplicationException(ownerName + ": User does not exist.", Status.NOT_FOUND);
            }
        } else {
        	//Remote user is not privileged and ownerName is not equal to remoteUser.username
        	throw new WebApplicationException("You are not authorized to access charts owned by user: " + ownerName, Status.FORBIDDEN);
        }
		
		Chart chart = new Chart(remoteUser, owner, chartDto.getType(), chartDto.getQueries());
		if(chartDto.getEntityId() != null) {
			//TODO: For now, we are only allowing entityId to be a dashboardId or an alertId
			PrincipalUser associatedEntityOwner;
			JPAEntity entity = _chartService.getAssociatedEntity(chartDto.getEntityId());
			if(entity == null) {
				throw new WebApplicationException("Entity id: " + chartDto.getEntityId() + " does not exist.", Status.BAD_REQUEST);
			} else if(entity instanceof Dashboard) {
				associatedEntityOwner = Dashboard.class.cast(entity).getOwner(); 
			} else if(entity instanceof Alert) {
				associatedEntityOwner = Alert.class.cast(entity).getOwner();
			} else {
				throw new WebApplicationException("Unknown entity type associated with this chart. Please use either a dashboard id or an alert id.", Status.BAD_REQUEST);
			}
			
			_validateResourceAuthorization(remoteUser, associatedEntityOwner);
			chart.setEntity(entity);
		}
		
		copyProperties(chart, chartDto);
		chart = _chartService.updateChart(chart);
		chartDto = ChartDto.transformToDto(chart);
		chartDto.setHref(req.getRequestURL().append('/').append(chartDto.getId()).toString());
		return chartDto;
	}
	
	private void _validateResourceAuthorization(PrincipalUser remoteUser, PrincipalUser resourceOwner) {
		if(!remoteUser.isPrivileged() && !remoteUser.equals(resourceOwner)) {
			throw new WebApplicationException("You are not authorized to access this resource.", Status.FORBIDDEN);
		}
	}
	
	/**
	 * Updates an existing chart.
	 *
	 * @param   req       The HttpServlet request object. Cannot be null.
	 * @param   chartId   The id of a chart. Cannot be null.
	 * @param   chartDto  The new chart object. Cannot be null.
	 *
	 * @return  Updated chart object.
	 *
	 * @throws  WebApplicationException  An exception with 404 NOT_FOUND will be thrown if the chart does not exist.
	 */
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/{chartId}")
	@Description("Updates a chart having the given ID.")
	public ChartDto updateChart(@Context HttpServletRequest req, @PathParam("chartId") BigInteger chartId, ChartDto chartDto) {
		
		if (chartId == null || chartId.compareTo(BigInteger.ZERO) < 1) {
			throw new WebApplicationException("chartId cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
		}
		
		if (chartDto == null) {
			throw new WebApplicationException("Cannot update a null object.", Status.BAD_REQUEST);
		}
		
		PrincipalUser remoteUser = getRemoteUser(req);
		PrincipalUser owner;

		String ownerName = chartDto.getOwnerName();
        if (ownerName == null || ownerName.isEmpty() || ownerName.equalsIgnoreCase(remoteUser.getUserName())) {
        	//If ownerName is not present or if it is present and equal to remote username, then return remoteUser.
            owner = remoteUser;
        } else if (remoteUser.isPrivileged()) {
            owner = userService.findUserByUsername(ownerName);
            if (owner == null) {
                throw new WebApplicationException(ownerName + ": User does not exist.", Status.NOT_FOUND);
            }
        } else {
        	//Remote user is not privileged and ownerName is not equal to remoteUser.username
        	throw new WebApplicationException("You are not authorized to access charts owned by user: " + ownerName, Status.FORBIDDEN);
        }

		Chart existingChart = _chartService.getChartByPrimaryKey(chartId);
		if (existingChart == null) {
			throw new WebApplicationException("Chart with ID: " + chartId + " does not exist. Please use a valid chartId.", 
					Response.Status.NOT_FOUND);
		}
		_validateResourceAuthorization(remoteUser, existingChart.getOwner());
		
		if(chartDto.getEntityId() != null) {
			//TODO: For now, we are only allowing entityId to be a dashboardId or an alertId
			PrincipalUser associatedEntityOwner;
			JPAEntity entity = _chartService.getAssociatedEntity(chartDto.getEntityId());
			if(entity == null) {
				throw new WebApplicationException("Entity id: " + chartDto.getEntityId() + " does not exist.", Status.BAD_REQUEST);
			} else if(entity instanceof Dashboard) {
				associatedEntityOwner = Dashboard.class.cast(entity).getOwner(); 
			} else if(entity instanceof Alert) {
				associatedEntityOwner = Alert.class.cast(entity).getOwner();
			} else {
				throw new WebApplicationException("Unknown entity type associated with this chart. Please use either a dashboard id or an alert id.", Status.BAD_REQUEST);
			}
			
			_validateResourceAuthorization(remoteUser, associatedEntityOwner);
			existingChart.setEntity(entity);
		}
		
		copyProperties(existingChart, chartDto);
		existingChart.setModifiedBy(remoteUser);
		chartDto = ChartDto.transformToDto(_chartService.updateChart(existingChart));
		chartDto.setHref(req.getRequestURL().toString());
		return chartDto;
	}
	
	/**
	 * Delete a chart, given its id.
	 *
	 * @param   req      The HttpServlet request object. Cannot be null.
	 * @param   chartId  The chart Id. Cannot be null and must be a positive non-zero number.
	 *
	 * @return  A Response object indicating whether the chart deletion was successful or not.
	 *
	 * @throws  WebApplicationException  An exception with 404 NOT_FOUND will be thrown if the chart does not exist.
	 */
	@DELETE
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/{chartId}")
	@Description("Deletes a chart, given its id.")
	public Response deleteChart(@Context HttpServletRequest req, @PathParam("chartId") BigInteger chartId) {
		if (chartId == null || chartId.compareTo(BigInteger.ZERO) < 1) {
			throw new WebApplicationException("chartId cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
		}

		Chart chart = _chartService.getChartByPrimaryKey(chartId);
		if (chart == null) {
			throw new WebApplicationException("Chart with ID: " + chartId + " does not exist. Please use a valid chartId.", 
					Response.Status.NOT_FOUND);
		}
		
		PrincipalUser remoteUser = getRemoteUser(req);
		_validateResourceAuthorization(remoteUser, chart.getOwner());
		_chartService.deleteChart(chart);
		return Response.status(Status.OK).build();
	}
	
	/**
	 * Find a chart, given its id.
	 *
	 * @param   req      The HttpServlet request object. Cannot be null.
	 * @param   chartId  The chart Id. Cannot be null and must be a positive non-zero number.
	 * @param fields     The fields (unused parameter)
	 * @return  The chart object.
	 *
	 * @throws  WebApplicationException  An exception with 404 NOT_FOUND will be thrown if the chart does not exist.
	 */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Path("/{chartId}")
	@Description("Finds a chart, given its id.")
	public ChartDto getChartByID(@Context HttpServletRequest req, @PathParam("chartId") BigInteger chartId,
			@QueryParam("fields") List<String> fields) {
		
		if (chartId == null || chartId.compareTo(BigInteger.ZERO) < 1) {
			throw new WebApplicationException("chartId cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
		}

		Chart chart;
		if(fields == null || fields.isEmpty()) {
			chart = _chartService.getChartByPrimaryKey(chartId);
		} else {
			chart = _chartService.getChartByPrimaryKey(chartId);
		}
		
		if (chart == null) {
			throw new WebApplicationException("Chart with ID: " + chartId + " does not exist. Please use a valid chartId.", 
					Response.Status.NOT_FOUND);
		}
		
		PrincipalUser remoteUser = getRemoteUser(req);
		_validateResourceAuthorization(remoteUser, chart.getOwner());
		ChartDto chartDto = ChartDto.transformToDto(chart);
		chartDto.setHref(req.getRequestURL().toString());
		return chartDto;
	}
	
	/**
	 * Return a list of charts owned by a user. Optionally, provide an entityId to filter charts associated
	 * with a given entity. 
	 *
	 * @param   req      	The HttpServlet request object. Cannot be null.
	 * @param   ownerName  	Optional. The username for which to retrieve charts. For non-privileged this must be null
	 * 						or equal to the logged in user.
	 * @param   entityId  	Optional. The entity id associated with these charts. 
	 *
	 * @return  A list of charts filtered using the provided parameters. 
	 *
	 * @throws  WebApplicationException  An exception with 404 NOT_FOUND will be thrown if the user does not exist.
	 */
	@GET
	@Produces(MediaType.APPLICATION_JSON)
	@Description("Return a list of charts owned by a user. Optionally, provide an entityId to filter charts associated "
			+ "with a given entity. ")
	public List<ChartDto> getCharts(@Context HttpServletRequest req, 
			@QueryParam("ownerName") String ownerName,
			@QueryParam("entityId") BigInteger entityId) {
		
		PrincipalUser remoteUser = getRemoteUser(req);
		PrincipalUser owner;
		owner = validateAndGetOwner(req,ownerName);
		List<Chart> result = new ArrayList<>();
		if(entityId == null) {
			result.addAll(_chartService.getChartsByOwner(owner));
		} else {
		    Dashboard dashboard = _dService.findDashboardByPrimaryKey(entityId);
		    if(dashboard==null)
            {
                throw new WebApplicationException(entityId + ": Dashboard does not exist.", Status.NOT_FOUND);
            }
		    else if(dashboard.isShared() || remoteUser.isPrivileged())
            {
                result.addAll(_chartService.getChartsForEntity(entityId));
            }
            else {
                result.addAll(_chartService.getChartsByOwnerForEntity(owner, entityId));
            }
		}

		return ChartDto.transformToDto(result);
	}

    /**
     * Updates an existing chart preferences.
     *
     * @param   req             The HttpServlet request object. Cannot be null.
     * @param   chartId         The id of a chart. Cannot be null.
     * @param   preferences     Preferences for chart object. Cannot be null.
     *
     * @return  Updated chart object with preferences.
     *
     * @throws  WebApplicationException  An exception with 404 NOT_FOUND will be thrown if the chart does not exist.
     */
    @PUT
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{chartId}/preferences")
    @Description("Updates a chart preferences given the ID.")
    public ChartDto updateChartPreferences(@Context HttpServletRequest req, @PathParam("chartId") BigInteger chartId, final Map<String, String> preferences) {

        if (chartId == null || chartId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("chartId cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }

        if (preferences == null) {
            throw new WebApplicationException("Cannot update with null preferences.", Status.BAD_REQUEST);
        }

        PrincipalUser remoteUser = getRemoteUser(req);
        Chart existingChart = _chartService.getChartByPrimaryKey(chartId);
        if (existingChart == null) {
            throw new WebApplicationException("Chart with ID: " + chartId + " does not exist. Please use a valid chartId.",
                    Response.Status.NOT_FOUND);
        }
        Dashboard sharedDashboard= _dService.findDashboardByPrimaryKey(existingChart.getEntity().getId());
        if (!sharedDashboard.isShared()) {
            _validateResourceAuthorization(remoteUser, existingChart.getOwner());
        }

        existingChart.getPreferences().putAll(preferences);
        existingChart.setModifiedBy(remoteUser);
        existingChart=_chartService.updateChart(existingChart);
        return ChartDto.transformToDto(existingChart);
    }

}

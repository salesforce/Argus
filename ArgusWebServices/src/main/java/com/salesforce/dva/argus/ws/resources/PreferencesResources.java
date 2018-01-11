package com.salesforce.dva.argus.ws.resources;

import javax.ws.rs.Path;
import com.salesforce.dva.argus.ws.annotation.Description;

/**
 * Provides methods to perform CRUD on Preferences objects.
 *
 * @author  Bhinav Sura (bsura@salesforce.com)
 */
@Path("v1/preferences")
@Description("Provides methods to perform CRUD on Preferences objects.")
public class PreferencesResources extends AbstractResource {
	
	//~ Instance fields ******************************************************************************************************************************

	//private PreferencesService _preferencesService = system.getServiceFactory().getPreferencesService();
	
	//~ Methods **************************************************************************************************************************************
	
//	@POST
//	@Produces(MediaType.APPLICATION_JSON)
//    @Consumes(MediaType.APPLICATION_JSON)
//    @Description("Create a preferences object.")
//	public PreferencesDto createPreferences(@Context HttpServletRequest req, PreferencesDto preferencesDto) {
//		
//		if (preferencesDto == null) {
//			throw new WebApplicationException("Cannot create a null preferences object.", Status.BAD_REQUEST);
//		}
//
//		PrincipalUser remoteUser = getRemoteUser(req);
//		PrincipalUser owner;
//		
//		String userName = preferencesDto.getUserName();
//        if (userName == null || userName.isEmpty() || userName.equalsIgnoreCase(remoteUser.getUserName())) {
//        	//If ownerName is not present or if it is present and equal to remote username, then return remoteUser.
//            owner = remoteUser;
//        } else if (remoteUser.isPrivileged()) {
//            owner = userService.findUserByUsername(userName);
//            if (owner == null) {
//                throw new WebApplicationException(userName + ": User does not exist.", Status.NOT_FOUND);
//            }
//        } else {
//        	//Remote user is not privileged and ownerName is not equal to remoteUser.username
//        	throw new WebApplicationException("You are not authorized to access charts owned by user: " + userName, Status.FORBIDDEN);
//        }
//		
//        JPAEntity entity = null;
//		if(preferencesDto.getEntityId() != null) {
//			//TODO: For now, we are only allowing preferences Charts and Dashboards. Hence entityId must be a dashboardId or a chartId
//			PrincipalUser associatedEntityOwner;
//			entity = chartService.getAssociatedEntity(preferencesDto.getEntityId());
//			if(entity == null) {
//				throw new WebApplicationException("Entity id: " + preferencesDto.getEntityId() + " does not exist.", Status.BAD_REQUEST);
//			} else if(entity instanceof Dashboard) {
//				associatedEntityOwner = Dashboard.class.cast(entity).getOwner(); 
//			} else if(entity instanceof Chart) {
//				associatedEntityOwner = Chart.class.cast(entity).getOwner();
//			} else {
//				throw new WebApplicationException("Unknown entity type associated with this chart. Please use either a dashboard id or a chart id.", Status.BAD_REQUEST);
//			}
//			
//			_validateResourceAuthorization(remoteUser, associatedEntityOwner);
//			chart.setEntity(entity);
//		}
//		Preferences preferences = new Preferences(remoteUser, owner, entity, preferencesDto.getPreferences());
//		
//		copyProperties(chart, chartDto);
//		chart = chartService.updateChart(chart);
//		chartDto = ChartDto.transformToDto(chart);
//		chartDto.setHref(req.getRequestURL().append('/').append(chartDto.getId()).toString());
//		return chartDto;
//	}
//	
//	private void _validateResourceAuthorization(PrincipalUser remoteUser, PrincipalUser resourceOwner) {
//		if(!remoteUser.isPrivileged() && !remoteUser.equals(resourceOwner)) {
//			throw new WebApplicationException("You are not authorized to access this resource.", Status.FORBIDDEN);
//		}
//	}
//	
//	/**
//	 * Updates an existing chart.
//	 *
//	 * @param   req       The HttpServlet request object. Cannot be null.
//	 * @param   chartId   The id of a chart. Cannot be null.
//	 * @param   chartDto  The new chart object. Cannot be null.
//	 *
//	 * @return  Updated chart object.
//	 *
//	 * @throws  WebApplicationException  An exception with 404 NOT_FOUND will be thrown if the chart does not exist.
//	 */
//	@PUT
//	@Produces(MediaType.APPLICATION_JSON)
//	@Consumes(MediaType.APPLICATION_JSON)
//	@Path("/{chartId}")
//	@Description("Updates a chart having the given ID.")
//	public ChartDto updateChart(@Context HttpServletRequest req, @PathParam("chartId") BigInteger chartId, ChartDto chartDto) {
//		
//		if (chartId == null || chartId.compareTo(BigInteger.ZERO) < 1) {
//			throw new WebApplicationException("chartId cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
//		}
//		
//		if (chartDto == null) {
//			throw new WebApplicationException("Cannot update a null object.", Status.BAD_REQUEST);
//		}
//		
//		PrincipalUser remoteUser = getRemoteUser(req);
//		PrincipalUser owner;
//		
//		String ownerName = chartDto.getOwnerName();
//        if (ownerName == null || ownerName.isEmpty() || ownerName.equalsIgnoreCase(remoteUser.getUserName())) {
//        	//If ownerName is not present or if it is present and equal to remote username, then return remoteUser.
//            owner = remoteUser;
//        } else if (remoteUser.isPrivileged()) {
//            owner = userService.findUserByUsername(ownerName);
//            if (owner == null) {
//                throw new WebApplicationException(ownerName + ": User does not exist.", Status.NOT_FOUND);
//            }
//        } else {
//        	//Remote user is not privileged and ownerName is not equal to remoteUser.username
//        	throw new WebApplicationException("You are not authorized to access charts owned by user: " + ownerName, Status.FORBIDDEN);
//        }
//		
//		Chart existingChart = chartService.getChartByPrimaryKey(chartId);
//		if (existingChart == null) {
//			throw new WebApplicationException("Chart with ID: " + chartId + " does not exist. Please use a valid chartId.", 
//					Response.Status.NOT_FOUND);
//		}
//		_validateResourceAuthorization(remoteUser, existingChart.getOwner());
//		
//		if(chartDto.getEntityId() != null) {
//			//TODO: For now, we are only allowing entityId to be a dashboardId or an alertId
//			PrincipalUser associatedEntityOwner;
//			JPAEntity entity = chartService.getAssociatedEntity(chartDto.getEntityId());
//			if(entity == null) {
//				throw new WebApplicationException("Entity id: " + chartDto.getEntityId() + " does not exist.", Status.BAD_REQUEST);
//			} else if(entity instanceof Dashboard) {
//				associatedEntityOwner = Dashboard.class.cast(entity).getOwner(); 
//			} else if(entity instanceof Alert) {
//				associatedEntityOwner = Alert.class.cast(entity).getOwner();
//			} else {
//				throw new WebApplicationException("Unknown entity type associated with this chart. Please use either a dashboard id or an alert id.", Status.BAD_REQUEST);
//			}
//			
//			_validateResourceAuthorization(remoteUser, associatedEntityOwner);
//			existingChart.setEntity(entity);
//		}
//		
//		copyProperties(existingChart, chartDto);
//		existingChart.setModifiedBy(remoteUser);
//		chartDto = ChartDto.transformToDto(chartService.updateChart(existingChart));
//		chartDto.setHref(req.getRequestURL().toString());
//		return chartDto;
//	}
//	
//	/**
//	 * Delete a chart, given its id.
//	 *
//	 * @param   req      The HttpServlet request object. Cannot be null.
//	 * @param   chartId  The chart Id. Cannot be null and must be a positive non-zero number.
//	 *
//	 * @return  A Response object indicating whether the chart deletion was successful or not.
//	 *
//	 * @throws  WebApplicationException  An exception with 404 NOT_FOUND will be thrown if the chart does not exist.
//	 */
//	@DELETE
//	@Produces(MediaType.APPLICATION_JSON)
//	@Path("/{chartId}")
//	@Description("Deletes a chart, given its id.")
//	public Response deleteChart(@Context HttpServletRequest req, @PathParam("chartId") BigInteger chartId) {
//		if (chartId == null || chartId.compareTo(BigInteger.ZERO) < 1) {
//			throw new WebApplicationException("chartId cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
//		}
//
//		Chart chart = chartService.getChartByPrimaryKey(chartId);
//		if (chart == null) {
//			throw new WebApplicationException("Chart with ID: " + chartId + " does not exist. Please use a valid chartId.", 
//					Response.Status.NOT_FOUND);
//		}
//		
//		PrincipalUser remoteUser = getRemoteUser(req);
//		_validateResourceAuthorization(remoteUser, chart.getOwner());
//		chartService.markChartForDeletion(chart);
//		return Response.status(Status.OK).build();
//	}
//	
//	/**
//	 * Find a chart, given its id.
//	 *
//	 * @param   req      The HttpServlet request object. Cannot be null.
//	 * @param   chartId  The chart Id. Cannot be null and must be a positive non-zero number.
//	 *
//	 * @return  The chart object. 
//	 *
//	 * @throws  WebApplicationException  An exception with 404 NOT_FOUND will be thrown if the chart does not exist.
//	 */
//	@GET
//	@Produces(MediaType.APPLICATION_JSON)
//	@Path("/{chartId}")
//	@Description("Finds a chart, given its id.")
//	public ChartDto getChartByID(@Context HttpServletRequest req, @PathParam("chartId") BigInteger chartId,
//			@QueryParam("fields") List<String> fields) {
//		
//		if (chartId == null || chartId.compareTo(BigInteger.ZERO) < 1) {
//			throw new WebApplicationException("chartId cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
//		}
//
//		Chart chart;
//		if(fields == null || fields.isEmpty()) {
//			chart = chartService.getChartByPrimaryKey(chartId);
//		} else {
//			chart = chartService.getChartByPrimaryKey(chartId);
//		}
//		
//		if (chart == null) {
//			throw new WebApplicationException("Chart with ID: " + chartId + " does not exist. Please use a valid chartId.", 
//					Response.Status.NOT_FOUND);
//		}
//		
//		PrincipalUser remoteUser = getRemoteUser(req);
//		_validateResourceAuthorization(remoteUser, chart.getOwner());
//		ChartDto chartDto = ChartDto.transformToDto(chart);
//		chartDto.setHref(req.getRequestURL().toString());
//		return chartDto;
//	}
//	
//	/**
//	 * Return a list of charts owned by a user. Optionally, provide an entityId to filter charts associated
//	 * with a given entity. 
//	 *
//	 * @param   req      	The HttpServlet request object. Cannot be null.
//	 * @param   ownerName  	Optional. The username for which to retrieve charts. For non-privileged this must be null
//	 * 						or equal to the logged in user.
//	 * @param   entityId  	Optional. The entity id associated with these charts. 
//	 *
//	 * @return  A list of charts filtered using the provided parameters. 
//	 *
//	 * @throws  WebApplicationException  An exception with 404 NOT_FOUND will be thrown if the user does not exist.
//	 */
//	@GET
//	@Produces(MediaType.APPLICATION_JSON)
//	@Description("Return a list of charts owned by a user. Optionally, provide an entityId to filter charts associated "
//			+ "with a given entity. ")
//	public List<ChartDto> getCharts(@Context HttpServletRequest req, 
//			@QueryParam("ownerName") String ownerName,
//			@QueryParam("entityId") BigInteger entityId) {
//		
//		PrincipalUser remoteUser = getRemoteUser(req);
//		PrincipalUser owner;
//		
//		if (ownerName == null || ownerName.isEmpty() || ownerName.equalsIgnoreCase(remoteUser.getUserName())) {
//        	//If ownerName is not present or if it is present and equal to remote username, then return remoteUser.
//            owner = remoteUser;
//        } else if (remoteUser.isPrivileged()) {
//            owner = userService.findUserByUsername(ownerName);
//            if (owner == null) {
//                throw new WebApplicationException(ownerName + ": User does not exist.", Status.NOT_FOUND);
//            }
//        } else {
//        	//Remote user is not privileged and ownerName is not equal to remoteUser.username
//        	throw new WebApplicationException("You are not authorized to access charts owned by user: " + ownerName, Status.FORBIDDEN);
//        }
//		
//		List<Chart> result = new ArrayList<>();
//		if(entityId == null) {
//			result.addAll(chartService.getChartsByOwner(owner));
//		} else {
//			result.addAll(chartService.getChartsByOwnerForEntity(owner, entityId));
//		}
//
//		return ChartDto.transformToDto(result);
//	}
//	
//	public static void main(String[] args) throws IOException, TokenExpiredException {
//		
//		int maxConnections = 10;
//		ArgusService service = null;
//		Alert alert = null;
//		try {
//			service = ArgusService.getInstance("https://argus-ws.data.sfdc.net/argusws",maxConnections);
//		    service.getAuthService().login("bhinav.sura", "Bs&*2882616330506325");
//		    
//		    alert = new Alert();
//		    alert.setName("bhinav-test1");
//		    alert.setCronEntry("*/15 * * * *");
//		    alert.setExpression("-1d:TestScope:TestMetric{TestTag=TagValue}:sum");
//		    alert = service.getAlertService().createAlert(alert);
//		    
//		    Trigger trigger = new Trigger();
//		    trigger.setName("TestName");
//		    trigger.setInertia(300000L);
//		    trigger.setThreshold(100.0);
//		    trigger.setType("GREATER_THAN");
//		    trigger = service.getAlertService().createTrigger(alert.getId(), trigger).get(0);
//		    
//		    Notification notifier = new Notification();
//		    notifier.setCooldownExpiration(3600000L);
//		    notifier.setNotifierName("com.salesforce.dva.argus.service.alert.notifier.GOCNotifier");
//		    notifier.setName("TestName");
//		    notifier.setSubscriptions(Collections.emptyList());
//		    notifier.setMetricsToAnnotate(Collections.emptyList());
//		    notifier.setSeverityLevel(2);
//		    notifier.setSRActionable(true);
//		    notifier = service.getAlertService().createNotification(alert.getId(), notifier).get(0);
//		    
//		    service.getAlertService().linkTrigger(alert.getId(), notifier.getId(), trigger.getId());
//		    
//		    service.getAuthService().logout();
//		} catch (IOException | TokenExpiredException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		} finally {
//			service.getAlertService().deleteAlert(alert.getId());
//		}
//		
//	}

}

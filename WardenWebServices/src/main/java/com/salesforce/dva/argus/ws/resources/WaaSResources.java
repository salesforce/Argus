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

//import com.salesforce.dva.argus.entity.Policy;
import com.salesforce.dva.argus.entity.PrincipalUser;
//import com.salesforce.dva.argus.entity.SuspensionLevel;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.service.WaaSService;
import com.salesforce.dva.argus.ws.annotation.Description;
import com.salesforce.dva.argus.ws.dto.MetricDto;
import com.salesforce.dva.warden.dto.Policy;
import com.salesforce.dva.warden.dto.SuspensionLevel;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
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
 * Web services for WaaS.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
@Path("/warden")
@Description("Provides methods to manipulate warden entities.")
public class WaaSResources extends AbstractResource {

    //~ Instance fields ******************************************************************************************************************************

    private WaaSService waaSService = system.getServiceFactory().getWaaSService();
    private UserService userService = system.getServiceFactory().getUserService();

    //~ Methods **************************************************************************************************************************************

    /**
     * Updates existing policy.
     *
     * @param   req       	The HttpServlet request object. Cannot be null.
     * @param   serviceName The service associated who owns the policy. Cannot be null.
     * @param   policyName  The policy Name. Cannot be null.
     *
     * @return  Updated policy object.
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if the alert does not exist.
     */
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/{serviceName}/{policyName}")
	@Description("Updates an policy having the policy name for specific service.")
	public Policy updatePolicy(@Context HttpServletRequest req,
			@PathParam("serviceName") String serviceName, @PathParam("policyName") String policyName,
			Policy policyDto) {
		if (serviceName == null || serviceName.isEmpty()) {
			throw new WebApplicationException("Service name cannot be null or an empty string.", Status.BAD_REQUEST);
		}
		if (policyName == null || policyName.isEmpty()) {
			throw new WebApplicationException("Policy name cannot be null or an empty string.", Status.BAD_REQUEST);
		}
		if (policyDto == null) {
			throw new WebApplicationException("Null object cannot be updated.", Status.BAD_REQUEST);
		}

		PrincipalUser owner = validateAndGetOwner(req, getRemoteUser(req).getUserName());

		com.salesforce.dva.argus.entity.Policy oldPolicy = waaSService.getPolicy(policyName, serviceName);

		if (oldPolicy == null) {
			throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
		}

		PrincipalUser policyOwner = userService.findUserByUsername(oldPolicy.getOwners().get(0));
		validateResourceAuthorization(req, policyOwner, owner);
		copyProperties(oldPolicy, policyDto);
		oldPolicy.setModifiedBy(getRemoteUser(req));
		String user = policyDto.getUser().get(0);
		double value = policyDto.getThreshold().get(0);

		// waiting for transformToDto impl, return null so far
		// return
		// com.salesforce.dva.warden.dto.Policy.transformToDto(waaSService.upsertPolicy(user,
		// oldPolicy, value));
		return null;
	}

	/**
	 * Updates the suspension level.
	 *
	 * @param req					The HttpServlet request object. Cannot be null.
	 * @param policyName			The policy name. Cannot be null.
	 * @param suspensionLevel		The suspension level number. Cannot be null.
	 * @param suspensionLevelDto	New suspensionLevel object. Cannot be null.
	 *
	 * @return Updated suspensionLevel object.
	 *
	 * @throws WebApplicationException	The exception with 404 status will be thrown if either an policy or suspensionLevel do not exist.
	 */
	@PUT
	@Produces(MediaType.APPLICATION_JSON)
	@Consumes(MediaType.APPLICATION_JSON)
	@Path("/{serviceName}/{policyName}/suspensionLevels/{suspensionLevelNumber}")
	@Description("Updates a suspension level having the given suspensionLevelNumber if associated with the given policy name.")
	public SuspensionLevel updateSuspensionLevelByLevel(@Context HttpServletRequest req,
			@PathParam("serviceName") String serviceName, @PathParam("policyName") String policyName,
			@PathParam("suspensionLevelNumber") int suspensionLevelNumber,
			SuspensionLevel suspensionLevelDto) {
		if (serviceName == null || serviceName.isEmpty()) {
			throw new WebApplicationException("Service name cannot be null or an empty string.", Status.BAD_REQUEST);
		}
		if (policyName == null || policyName.length() == 0) {
			throw new WebApplicationException("Alert Id cannot be null and must be a positive non-zero number.",
					Status.BAD_REQUEST);
		}
		if (suspensionLevelNumber <= 0) {
			throw new WebApplicationException("Notification Id cannot be null and must be a positive non-zero number.",
					Status.BAD_REQUEST);
		}
		if (suspensionLevelDto == null) {
			throw new WebApplicationException("Null object cannot be updated.", Status.BAD_REQUEST);
		}

		PrincipalUser owner = validateAndGetOwner(req, getRemoteUser(req).getUserName());
		com.salesforce.dva.argus.entity.Policy oldPolicy = waaSService.getPolicy(policyName, serviceName);

		if (oldPolicy == null) {
			throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
		}
		
		PrincipalUser policyOwner = userService.findUserByUsername(oldPolicy.getOwners().get(0));
		validateResourceAuthorization(req, policyOwner, owner);
		for (com.salesforce.dva.argus.entity.SuspensionLevel suspensionLevel : oldPolicy.getSuspensionLevels()) {
			if (suspensionLevelNumber == suspensionLevel.getLevelNumber()) {
				copyProperties(suspensionLevel, suspensionLevelDto);
				suspensionLevel.setModifiedBy(getRemoteUser(req));
				oldPolicy.setModifiedBy(getRemoteUser(req));

				com.salesforce.dva.argus.entity.Policy policy = waaSService.upsertPolicy(policyName, oldPolicy, oldPolicy.getDefaultValue());
				int index = policy.getSuspensionLevels().indexOf(suspensionLevel);
				// waiting for transformToDto impl, return null so far
				// return com.salesforce.dva.warden.dto.SuspensionLevel.transformToDto(policy.getSuspensionLevels().get(index));
				return null;
			}
		}
		throw new WebApplicationException("The notification does not exist.", Response.Status.NOT_FOUND);
	}

    /**
     * push metrics for a given policy.
     *
     * @param   req              The HttpServlet request object. Cannot be null.
     * @param   serviceName      The service name. Cannot be null.
     * @param   policyName       The policy object. Cannot be null.
     *
     * @return  The updated metrics
     *
     * @throws  WebApplicationException  The exception with 404 status will be thrown if an alert does not exist.
     */
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{serviceName}/{policyName}/metric")
    @Description("Push metric data for the given policy.")
    public MetricDto pushMetrics(@Context HttpServletRequest req,
        @PathParam("serviceName") String serviceName, 
        @PathParam("policyName") String policyName, 
        MetricDto metricDto) {
    	if (serviceName == null || serviceName.isEmpty()) {
            throw new WebApplicationException("Service name cannot be null or an empty string.", Status.BAD_REQUEST);
        }
        if (policyName == null || policyName.isEmpty()) {
            throw new WebApplicationException("Policy name cannot be null or an empty string.", Status.BAD_REQUEST);
        }
        if (metricDto == null) {
            throw new WebApplicationException("Null object cannot be updated.", Status.BAD_REQUEST);
        }

        com.salesforce.dva.argus.entity.Policy policy = waaSService.getPolicy(policyName, serviceName);

        if (policy != null) {
        	PrincipalUser policyOwner = userService.findUserByUsername(policy.getOwners().get(0));
            validateResourceAuthorization(req, policyOwner, getRemoteUser(req));

            //TODO: return null so far
            //will create WaaSMonitorService on core side
            //which maintain a hashMap<MetricKey, Metric>
            //MetricKey will be metricName+userName combination
            //Metric will be the metric with latest datapoints
            //waaSMonitorService also push metric to tsdb
            //or creating method in waaSService to wrap it up
           // return MetricDto.transformToDto(waaSMonitorService.updateMetrics(policy).get(policy.getMetricName()+policy.getUser()));
            return null;
        }
        throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
    }

}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

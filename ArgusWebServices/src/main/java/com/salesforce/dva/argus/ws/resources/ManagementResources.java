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
import com.salesforce.dva.argus.service.ManagementService;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.service.WardenService.PolicyCounter;
import com.salesforce.dva.argus.service.WardenService.SubSystem;
import com.salesforce.dva.argus.ws.annotation.Description;
import com.salesforce.dva.argus.ws.dto.DashboardDto;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
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
 * Web services for management service.
 *
 * @author  Raj Sarkapally (rsarkapally@salesforce.com)
 */
@Path("/management")
@Description("Provides methods to manage the services.")
public class ManagementResources extends AbstractResource {

    //~ Instance fields ******************************************************************************************************************************

    private final UserService userService = system.getServiceFactory().getUserService();
    private final ManagementService managementService = system.getServiceFactory().getManagementService();

    //~ Methods **************************************************************************************************************************************

    /**
     * Updates the admin privileges for the user.
     *
     * @param   req         The HTTP request.
     * @param   userName    Name of the user whom the admin privileges will be updated. Cannot be null or empty.
     * @param   privileged  boolean variable indicating admin privileges.
     *
     * @return  Response object indicating whether the operation was successful or not.
     *
     * @throws  IllegalArgumentException  Throws IllegalArgument exception when the input is not valid.
     * @throws  WebApplicationException   Throws this exception if the user does not exist or the user is not authorized to carry out this operation.
     */
    @PUT
    @Path("/administratorprivilege")
    @Produces(MediaType.APPLICATION_JSON)
    @Description("Grants administrative privileges.")
    public Response setAdministratorPrivilege(@Context HttpServletRequest req,
        @FormParam("username") String userName,
        @FormParam("privileged") boolean privileged) {
        if (userName == null || userName.isEmpty()) {
            throw new IllegalArgumentException("User name cannot be null or empty.");
        }
        validatePrivilegedUser(req);

        PrincipalUser user = userService.findUserByUsername(userName);

        if (user == null) {
            throw new WebApplicationException("User does not exist.", Status.NOT_FOUND);
        }
        managementService.setAdministratorPrivilege(user, privileged);
        return Response.status(Status.OK).build();
    }

    /**
     * Updates the warden policy for the user.
     *
     * @param   req            The HTTP request.
     * @param   userName       The user name whom the warden policy will be updated. Cannot be null or empty.
     * @param   policycounter  The policy counter. Cannot be null.
     * @param   value          The value.
     *
     * @return  Response object indicating whether the operation was successful or not.
     *
     * @throws  IllegalArgumentException  Throws IllegalArgument exception when the input is not valid.
     * @throws  WebApplicationException   Throws this exception if the user does not exist or the user is not authorized to carry out this operation.
     */
    @PUT
    @Path("/wardenpolicyforuser")
    @Produces(MediaType.APPLICATION_JSON)
    @Description("Updates a warden policy for a user.")
    public Response updateWardenPolicyForUser(@Context HttpServletRequest req,
        @FormParam("username") String userName,
        @FormParam("policycounter") PolicyCounter policycounter,
        @FormParam("value") double value) {
        if (userName == null || userName.isEmpty()) {
            throw new IllegalArgumentException("User name cannot be null or empty.");
        }
        if (policycounter == null) {
            throw new IllegalArgumentException("Policy counter cannot be null.");
        }
        validatePrivilegedUser(req);

        PrincipalUser user = userService.findUserByUsername(userName);

        if (user == null) {
            throw new WebApplicationException("User does not exist.", Status.NOT_FOUND);
        }
        managementService.updateWardenPolicyForUser(user, policycounter, value);
        return Response.status(Status.OK).build();
    }

    /**
     * Reinstates the specified sub system to the user.
     *
     * @param   req        The HTTP request.
     * @param   userName   The user whom the sub system to be reinstated. Cannot be null or empty.
     * @param   subSystem  The subsystem to be reinstated. Cannot be null.
     *
     * @return  Response object indicating whether the operation was successful or not.
     *
     * @throws  IllegalArgumentException  Throws IllegalArgument exception when the input is not valid.
     * @throws  WebApplicationException   Throws this exception if the user does not exist or the user is not authorized to carry out this operation.
     */
    @PUT
    @Path("/reinstateuser")
    @Produces(MediaType.APPLICATION_JSON)
    @Description("Reinstates a suspended user.")
    public Response reinstateUser(@Context HttpServletRequest req,
        @FormParam("username") String userName,
        @FormParam("subsystem") SubSystem subSystem) {
        if (userName == null || userName.isEmpty()) {
            throw new IllegalArgumentException("User name cannot be null or empty.");
        }
        if (subSystem == null) {
            throw new IllegalArgumentException("Subsystem cannot be null.");
        }
        validatePrivilegedUser(req);

        PrincipalUser user = userService.findUserByUsername(userName);

        if (user == null) {
            throw new WebApplicationException("User does not exist.", Status.BAD_REQUEST);
        }
        managementService.reinstateUser(user, subSystem);
        return Response.status(Status.OK).build();
    }

    /**
     * Updates warden suspension levels.
     *
     * @param   req               The HTTP request.
     * @param   infractionCounts  Warden suspension levels.
     *
     * @return  Response object indicating whether the operation was successful or not.
     *
     * @throws  IllegalArgumentException  WebApplicationException.
     */
    @PUT
    @Path("/wardensuspensionlevelsanddurations")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    @Description("Updates warden infraction level counts and suspension durations.")
    public Response updateWardenSuspensionLevelsAndDurations(@Context HttpServletRequest req, Map<Integer, Long> infractionCounts) {
        if (infractionCounts == null || infractionCounts.isEmpty()) {
            throw new IllegalArgumentException("Infraction counts cannot be null or empty.");
        }
        validatePrivilegedUser(req);
        managementService.updateWardenSuspensionLevelsAndDurations(infractionCounts);
        return Response.status(Status.OK).build();
    }

    /**
     * Disables the warden.
     *
     * @param   req  The HTTP request.
     *
     * @return  Response object indicating whether the operation was successful or not.
     */
    @PUT
    @Path("/disablewarden")
    @Produces(MediaType.APPLICATION_JSON)
    @Description("Disables warden.")
    public Response disableWarden(@Context HttpServletRequest req) {
        validatePrivilegedUser(req);
        managementService.disableWarden();
        return Response.status(Status.OK).build();
    }

    /**
     * Enables the warden.
     *
     * @param   req  The HTTP request.
     *
     * @return  Response object indicating whether the operation was successful or not.
     */
    @PUT
    @Path("/enablewarden")
    @Produces(MediaType.APPLICATION_JSON)
    @Description("Enables warden.")
    public Response enableWarden(@Context HttpServletRequest req) {
        validatePrivilegedUser(req);
        managementService.enableWarden();
        return Response.status(Status.OK).build();
    }

    /**
     * Enables monitor counter collection.
     *
     * @param   req  The HTTP request.
     *
     * @return  Response object indicating whether the operation was successful or not.
     */
    @PUT
    @Path("/enablemonitorcountercollection")
    @Produces(MediaType.APPLICATION_JSON)
    @Description("Enables Argus system monitoring.")
    public Response enableMonitorCounterCollection(@Context HttpServletRequest req) {
        validatePrivilegedUser(req);
        managementService.enableMonitorCounterCollection();
        return Response.status(Status.OK).build();
    }

    /**
     * Disables monitor counter collection.
     *
     * @param   req  The HTTP request.
     *
     * @return  Response object indicating whether the operation was successful or not.
     */
    @PUT
    @Path("/disablemonitorcountercollection")
    @Produces(MediaType.APPLICATION_JSON)
    @Description("Disables Argus system monitoring.")
    public Response disableMonitorCounterCollection(@Context HttpServletRequest req) {
        validatePrivilegedUser(req);
        managementService.disableMonitorCounterCollection();
        return Response.status(Status.OK).build();
    }

    /**
     * Resets the runtime counters.
     *
     * @param   req  The HTTP request.
     *
     * @return  Response object indicating whether the operation was successful or not.
     */
    @PUT
    @Path("/resetruntimecounters")
    @Produces(MediaType.APPLICATION_JSON)
    @Description("Resets Argus system monitoring runtime counters.")
    public Response resetRuntimeCounters(@Context HttpServletRequest req) {
        validatePrivilegedUser(req);
        managementService.resetRuntimeCounters();
        return Response.status(Status.OK).build();
    }

    /**
     * Resets the system counters.
     *
     * @param   req  The HTTP request.
     *
     * @return  Response object indicating whether the operation was successful or not.
     */
    @PUT
    @Path("/resetsystemcounters")
    @Produces(MediaType.APPLICATION_JSON)
    @Description("Resets Argus system monitoring warden counters.")
    public Response resetSystemCounters(@Context HttpServletRequest req) {
        validatePrivilegedUser(req);
        managementService.resetSystemCounters();
        return Response.status(Status.OK).build();
    }

    /**
     * Resets the custom counters.
     *
     * @param   req  The HTTP request.
     *
     * @return  Response object indicating whether the operation was successful or not.
     */
    @PUT
    @Path("/resetcustomcounters")
    @Produces(MediaType.APPLICATION_JSON)
    @Description("Resets Argus system monitoring custom counters.")
    public Response resetCustomCounters(@Context HttpServletRequest req) {
        validatePrivilegedUser(req);
        managementService.resetCustomCounters();
        return Response.status(Status.OK).build();
    }

    /**
     * Returns the warden dashboard for the user.
     *
     * @param   req       The HTTP request.
     * @param   userName  The user name. Cannot be null or empty.
     *
     * @return  The dashboard object.
     *
     * @throws  IllegalArgumentException  Throws IllegalArgument exception when the input is not valid.
     * @throws  WebApplicationException   Throws this exception if the user does not exist or the user is not authorized to carry out this operation.
     */
    @GET
    @Path("/wardendashboard/{username}")
    @Produces(MediaType.APPLICATION_JSON)
    @Description("Returns the warden dashboard for a user.")
    public DashboardDto getWardenDashboard(@Context HttpServletRequest req,
        @PathParam("username") String userName) {
        if (userName == null || userName.isEmpty()) {
            throw new IllegalArgumentException("User name cannot be null or empty.");
        }
        validatePrivilegedUser(req);

        PrincipalUser user = userService.findUserByUsername(userName);

        if (user == null) {
            throw new WebApplicationException("User does not exist.", Status.NOT_FOUND);
        }
        return DashboardDto.transformToDto(managementService.getWardenDashboard(user));
    }

    /**
     * Returns the system dashboard.
     *
     * @param   req  The HTTP request.
     *
     * @return  The dashboard object.
     */
    @GET
    @Path("/systemdashboard")
    @Produces(MediaType.APPLICATION_JSON)
    @Description("Returns the system dashboard.")
    public DashboardDto getSystemDashboard(@Context HttpServletRequest req) {
        validatePrivilegedUser(req);
        return DashboardDto.transformToDto(managementService.getSystemDashboard());
    }

    /**
     * Returns the run time dashboard.
     *
     * @param   req  The HTTP request.
     *
     * @return  The dashboard object.
     */
    @GET
    @Path("/runtimedashboard")
    @Produces(MediaType.APPLICATION_JSON)
    @Description("Returns the runtime dashboard.")
    public DashboardDto getRuntimeDashboard(@Context HttpServletRequest req) {
        validatePrivilegedUser(req);
        return DashboardDto.transformToDto(managementService.getRuntimeDashboard());
    }

    /**
     * Enables scheduling.
     *
     * @param   req  The HTTP request.
     *
     * @return  Response object indicating whether the operation was successful or not.
     */
    @PUT
    @Path("/enablescheduling")
    @Produces(MediaType.APPLICATION_JSON)
    @Description("Enables collection and alert evaluation scheduling.")
    public Response enableScheduling(@Context HttpServletRequest req) {
        validatePrivilegedUser(req);
        managementService.enableScheduling();
        return Response.status(Status.OK).build();
    }

    /**
     * Disables scheduling.
     *
     * @param   req  The HTTP request.
     *
     * @return  Response object indicating whether the operation was successful or not.
     */
    @PUT
    @Path("/disablescheduling")
    @Produces(MediaType.APPLICATION_JSON)
    @Description("Disables collection and alert evaluation scheduling.")
    public Response disableScheduling(@Context HttpServletRequest req) {
        validatePrivilegedUser(req);
        managementService.disableScheduling();
        return Response.status(Status.OK).build();
    }

    /**
     * Cleans up records marked for deletion and orphan records.
     *
     * @param   req  The HTTP request.
     *
     * @return  Response object indicating whether the operation was successful or not.
     */
    @PUT
    @Path("/cleanuprecords")
    @Produces(MediaType.APPLICATION_JSON)
    @Description("Cleans up records marked for deletion and orphan records.")
    public Response cleanupRecords(@Context HttpServletRequest req) {
        validatePrivilegedUser(req);
        managementService.cleanupRecords();
        return Response.status(Status.OK).build();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

package com.salesforce.dva.argus.ws.resources;

import com.salesforce.dva.argus.entity.BatchMetricQuery;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.BatchService;
import com.salesforce.dva.argus.ws.annotation.Description;
import com.salesforce.dva.argus.ws.dto.BatchDto;
import com.salesforce.dva.argus.ws.listeners.ArgusWebServletListener;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Map;

/**
 * Provides methods to list and poll batches
 *
 * @author Colby Guan (cguan@salesforce.com)
 */
@Path("/batches")
@Description("Provides methods to check status and results of batch queries")
public class BatchResources extends AbstractResource {

    //~ Instance fields ******************************************************************************************************************************

    private BatchService batchService = ArgusWebServletListener.getSystem().getServiceFactory().getBatchService();

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns all batches filtered by owner
     *
     * @param   req          HTTPServlet request. Cannot be null.
     *
     * @return  List of filtered batches
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getBatches(@Context HttpServletRequest req) {
        PrincipalUser owner = validateAndGetOwner(req, null);
        Map<String,String> batches = batchService.findBatchesByOwnerName(owner.getUserName());
        if (batches == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        } else {
            return Response.ok(batches, MediaType.APPLICATION_JSON).build();
        }
    }

    /**
     * Returns a batch by its ID
     *
     * @param   req          HTTPServlet request. Cannot be null.
     * @param   batchId      The batch ID to retrieve.
     *
     * @return  The corresponding batch
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{batchId}")
    @Description("Return a batch query's status and results if done")
    public Response getBatchById(@Context HttpServletRequest req,
                                 @PathParam("batchId") String batchId) {
        BatchMetricQuery batch = batchService.findBatchById(batchId);
        if (batch == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        batch.updateStatus();
        return Response.ok(BatchDto.transformToDto(batch), MediaType.APPLICATION_JSON).build();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

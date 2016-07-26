package com.salesforce.dva.argus.ws.resources;

import com.salesforce.dva.argus.entity.BatchMetricQuery;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.BatchService;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.ws.annotation.Description;
import com.salesforce.dva.argus.ws.dto.BatchDto;
import com.salesforce.dva.argus.ws.listeners.ArgusWebServletListener;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.Map;
import java.util.UUID;

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
     * @return  Filtered batches in the form of a map from UUID to status
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response getBatches(@Context HttpServletRequest req) {
        PrincipalUser owner = validateAndGetOwner(req, null);
        SystemAssert.requireArgument(owner != null, "Owner cannot be null");

        Map<String,String> batches = batchService.findBatchesByOwnerName(owner.getUserName());
        return Response.ok(batches, MediaType.APPLICATION_JSON).build();
    }

    /**
     * Returns a batch by its ID
     *
     * @param   req          HTTPServlet request. Cannot be null.
     * @param   batchId      The batch ID to retrieve.
     *
     * @return  The corresponding batch
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{batchId}")
    @Description("Return a batch query's status and results if done")
    public Response getBatchById(@Context HttpServletRequest req,
                                 @PathParam("batchId") String batchId) {
        _validateBatchId(batchId);
        PrincipalUser currentOwner = validateAndGetOwner(req, null);

        BatchMetricQuery batch = batchService.findBatchById(batchId);
        if (batch != null) {
            PrincipalUser actualOwner = userService.findUserByUsername(batch.getOwnerName());
            validateResourceAuthorization(req, actualOwner, currentOwner);
            return Response.ok(BatchDto.transformToDto(batch), MediaType.APPLICATION_JSON).build();
        }
        throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
    }

    /**
     * Deletes the batch having the given ID.
     *
     * @param   req          HTTPServlet request. Cannot be null.
     * @param   batchId      The batch ID to retrieve.
     *
     * @return  An empty body if the delete was successful.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    @DELETE
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{batchId}")
    @Description("Deletes the batch having the given ID.")
    public Response deleteBatch(@Context HttpServletRequest req,
                                    @PathParam("batchId") String batchId) {
        _validateBatchId(batchId);
        PrincipalUser currentOwner = validateAndGetOwner(req, null);

        BatchMetricQuery batch = batchService.findBatchById(batchId);
        if (batch != null) {
            PrincipalUser actualOwner = userService.findUserByUsername(batch.getOwnerName());
            validateResourceAuthorization(req, actualOwner, currentOwner);
            batchService.deleteBatch(batchId);
            return Response.status(Response.Status.OK).build();
        }
        throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
    }

    private void _validateBatchId(String batchId) {
        if (batchId == null) {
            throw new WebApplicationException("Batch ID cannot be null", Response.Status.BAD_REQUEST);
        }
        try {
            UUID.fromString(batchId);
        } catch (Exception ex) {
            throw new WebApplicationException(ex, Response.Status.BAD_REQUEST);
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

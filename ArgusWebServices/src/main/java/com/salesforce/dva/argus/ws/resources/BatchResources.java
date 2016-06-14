package com.salesforce.dva.argus.ws.resources;

import com.salesforce.dva.argus.entity.BatchMetricQuery;
import com.salesforce.dva.argus.service.CacheService;
import com.salesforce.dva.argus.service.MetricQueueService;
import com.salesforce.dva.argus.ws.annotation.Description;
import com.salesforce.dva.argus.ws.dto.BatchDto;
import com.salesforce.dva.argus.ws.listeners.ArgusWebServletListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * Created by cguan on 6/7/16.
 */
@Path("/batches")
@Description("Provides methods to check status and results of batch queries")
public class BatchResources {
    private static final Logger LOGGER = LoggerFactory.getLogger(BatchResources.class);
    //~ Instance fields ******************************************************************************************************************************

    private CacheService cacheService = ArgusWebServletListener.getSystem().getServiceFactory().getCacheService();
    private MetricQueueService metricQueueService = ArgusWebServletListener.getSystem().getServiceFactory().getMetricQueueService();

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/{batchId}")
    @Description("Return a batch query's status and results if done")
    public Response getBatchById(@Context HttpServletRequest req,
        @PathParam("batchId") String batchId) {
        BatchMetricQuery batch = BatchMetricQuery.findById(cacheService, metricQueueService, batchId);
        if (batch == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        try {
            return Response.ok(BatchDto.transformToDto(batch), MediaType.APPLICATION_JSON).build();
        } catch (Exception ex) {
            LOGGER.error("Exception in BatchResources: {}", ex.toString());
            throw ex;
        }
    }
}

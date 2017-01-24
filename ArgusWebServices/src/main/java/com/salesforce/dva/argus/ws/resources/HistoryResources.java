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

import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.entity.History.JobStatus;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.AlertService;
import com.salesforce.dva.argus.service.HistoryService;
import com.salesforce.dva.argus.ws.annotation.Description;
import com.salesforce.dva.argus.ws.dto.HistoryDTO;
import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
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
 * Provides end points for job execution details.
 *
 * @author  Raj Sarkapally (rsarkapally@salesforce.com)
 */
@Path("/history")
@Description("Provides methods to examine object history details.")
public class HistoryResources extends AbstractResource {

    //~ Instance fields ******************************************************************************************************************************

    private final HistoryService _historyService = system.getServiceFactory().getHistoryService();
    private final AlertService _alertService = system.getServiceFactory().getAlertService();

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the list of job history record for the given job id.
     *
     * @param   req     The HTTP request.
     * @param   jobId   The job id. Cannot be null.
     * @param   limit   max no of records for a given job
     * @param   status  The job status filter.
     *
     * @return  The list of job history records.
     *
     * @throws  WebApplicationException  Throws the exception for invalid input data.
     */
    @GET
    @Path("/job/{jobId}")
    @Description("Returns the job history for the given job Id")
    @Produces(MediaType.APPLICATION_JSON)
    public List<HistoryDTO> findByEntityId(@Context HttpServletRequest req,
        @PathParam("jobId") BigInteger jobId,
        @QueryParam("limit") int limit,
        @QueryParam("status") JobStatus status) {
        if (jobId == null || jobId.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Job ID cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }

        Alert alert= _alertService.findAlertByPrimaryKey(jobId);

        if (alert == null) {
            throw new WebApplicationException(MessageFormat.format("The job with id {0} does not exist.", jobId), Response.Status.NOT_FOUND);
        }
        
        if(!alert.isShared()){
        	validateResourceAuthorization(req, alert.getOwner(), getRemoteUser(req));
        }
        List<History> historyList = status != null ? _historyService.findByJobAndStatus(jobId, limit, status)
                                                   : _historyService.findByJob(jobId, limit);
        return HistoryDTO.transformToDto(historyList);
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

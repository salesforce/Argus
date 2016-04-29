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

import com.salesforce.dva.argus.entity.Audit;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.ws.annotation.Description;
import com.salesforce.dva.argus.ws.dto.AuditDto;
import java.math.BigInteger;
import java.util.List;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

/**
 * Provides methods to examine audit history information for data objects.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
@Path("/audit")
@Description("Provides methods to examine audit history information for data objects.")
public class AuditResources extends AbstractResource {

    //~ Instance fields ******************************************************************************************************************************

    private final AuditService _auditService = system.getServiceFactory().getAuditService();

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the audit record for the given id.
     *
     * @param   entityid  The entity Id. Cannot be null.
     * @param   limit     max no of records
     *
     * @return  The audit object.
     *
     * @throws  WebApplicationException  Throws the exception for invalid input data.
     */
    @GET
    @Path("/entity/{entityid}")
    @Description("Returns the audit trail for an entity.")
    @Produces(MediaType.APPLICATION_JSON)
    public List<AuditDto> findByEntityId(@PathParam("entityid") BigInteger entityid,
        @QueryParam("limit") BigInteger limit) {
        if (entityid == null || entityid.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Entity ID cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }
        if (limit != null && limit.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("Limit must be a positive non-zero number.", Status.BAD_REQUEST);
        }

        List<Audit> auditList = limit == null ? _auditService.findByEntity(entityid) : _auditService.findByEntity(entityid, limit);

        if (auditList != null && auditList.size() > 0) {
            return AuditDto.transformToDto(auditList);
        } else {
            throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
        }
    }

    /**
     * Returns the audit record for the given id.
     *
     * @param   id  entityId The entity Id. Cannot be null.
     *
     * @return  The audit object.
     *
     * @throws  WebApplicationException  Throws the exception for invalid input data.
     */
    @GET
    @Path("/{id}")
    @Description("Returns the audit trail for a given Id.")
    @Produces(MediaType.APPLICATION_JSON)
    public AuditDto findById(@PathParam("id") BigInteger id) {
        if (id == null || id.compareTo(BigInteger.ZERO) < 1) {
            throw new WebApplicationException("ID cannot be null and must be a positive non-zero number.", Status.BAD_REQUEST);
        }

        Audit audit = _auditService.findAuditByPrimaryKey(id);

        if (audit != null) {
            return AuditDto.transformToDto(_auditService.findAuditByPrimaryKey(id));
        } else {
            throw new WebApplicationException(Response.Status.NOT_FOUND.getReasonPhrase(), Response.Status.NOT_FOUND);
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

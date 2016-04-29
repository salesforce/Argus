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
	 
package com.salesforce.dva.argus.ws.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.salesforce.dva.argus.entity.Audit;
import com.salesforce.dva.argus.entity.JPAEntity;
import org.apache.commons.beanutils.BeanUtils;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

/**
 * Audit DTO.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
@SuppressWarnings("serial")
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuditDto extends BaseDto implements Serializable {

    //~ Instance fields ******************************************************************************************************************************

    private BigInteger id;
    private Date createdDate;
    private String message;
    private String hostName;
    private BigInteger entityId;

    //~ Methods **************************************************************************************************************************************

    /**
     * Converts the audit entity to DTO.
     *
     * @param   audit  The audit entity.
     *
     * @return  The audit DTO.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    public static AuditDto transformToDto(Audit audit) {
        if (audit == null) {
            throw new WebApplicationException("Null entity object cannot be converted to Dto object.", Status.INTERNAL_SERVER_ERROR);
        }

        AuditDto auditDto = new AuditDto();

        try {
            BeanUtils.copyProperties(auditDto, audit);
            auditDto.setEntityId(audit.getEntity());
        } catch (Exception ex) {
            throw new WebApplicationException("DTO transformation failed.", Status.INTERNAL_SERVER_ERROR);
        }
        return auditDto;
    }

    /**
     * Converts a list of audit entities to DTOs.
     *
     * @param   audits  The list of audit entities to convert.
     *
     * @return  The list of DTO objects.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    public static List<AuditDto> transformToDto(List<Audit> audits) {
        if (audits == null) {
            throw new WebApplicationException("Null entity object cannot be converted to Dto object.", Status.INTERNAL_SERVER_ERROR);
        }

        List<AuditDto> result = new ArrayList<AuditDto>();

        for (Audit audit : audits) {
            result.add(transformToDto(audit));
        }
        return result;
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the audit ID.
     *
     * @return  The audit ID.
     */
    public BigInteger getId() {
        return id;
    }

    /**
     * Specifies the audit ID.
     *
     * @param  id  The audit ID.
     */
    public void setId(BigInteger id) {
        this.id = id;
    }

    /**
     * Returns the created date.
     *
     * @return  The created date.
     */
    public Date getCreatedDate() {
        return createdDate;
    }

    /**
     * Specifies the created date.
     *
     * @param  createdDate  The created date.
     */
    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }

    /**
     * Returns the audit message.
     *
     * @return  The message.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Specifies the audit message.
     *
     * @param  message  The message.
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * Returns the host name.
     *
     * @return  The host name.
     */
    public String getHostName() {
        return hostName;
    }

    /**
     * Specifies the host name.
     *
     * @param  hostName  The host name.
     */
    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    /**
     * Returns the entity ID.
     *
     * @return  The entity ID.
     */
    public BigInteger getEntityId() {
        return entityId;
    }

    /**
     * Specifies the entity ID.
     *
     * @param  jpaEntity  The entity ID.
     */
    public void setEntityId(JPAEntity jpaEntity) {
        this.entityId = jpaEntity.getId();
    }

    @Override
    public Object createExample() {
        AuditDto result = new AuditDto();

        result.setCreatedDate(new Date());
        result.setHostName("localhost");
        result.setId(BigInteger.ONE);
        result.setEntityId(null);
        result.setMessage("A description of the change or operation.");
        return result;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

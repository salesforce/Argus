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
import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.entity.History.JobStatus;
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
 * The history DTO.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
@SuppressWarnings("serial")
@JsonIgnoreProperties(ignoreUnknown = true)
public class HistoryDTO extends BaseDto implements Serializable {

    //~ Instance fields ******************************************************************************************************************************

    private BigInteger id;
    private Date createdDate;
    private String message;
    private String hostName;
    private BigInteger entityId;
    private JobStatus jobStatus;
    private long waitTime;
    private long executionTime;

    //~ Methods **************************************************************************************************************************************

    /**
     * Converts a history object to DTO.
     *
     * @param   history  The history object.
     *
     * @return  The history DTO.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    public static HistoryDTO transformToDto(History history) {
        if (history == null) {
            throw new WebApplicationException("Null entity object cannot be converted to Dto object.", Status.INTERNAL_SERVER_ERROR);
        }

        HistoryDTO historyDto = new HistoryDTO();

        try {
            BeanUtils.copyProperties(historyDto, history);
            historyDto.setEntityId(history.getEntity());
        } catch (Exception ex) {
            throw new WebApplicationException("DTO transformation failed.", Status.INTERNAL_SERVER_ERROR);
        }
        return historyDto;
    }

    /**
     * Converts a list of history entities to DTOs.
     *
     * @param   list  The list of entities.
     *
     * @return  The list of DTOs.
     *
     * @throws  WebApplicationException  If an error occurs.
     */
    public static List<HistoryDTO> transformToDto(List<History> list) {
        if (list == null) {
            throw new WebApplicationException("Null entity object cannot be converted to Dto object.", Status.INTERNAL_SERVER_ERROR);
        }

        List<HistoryDTO> result = new ArrayList<HistoryDTO>();

        for (History history : list) {
            result.add(transformToDto(history));
        }
        return result;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public Object createExample() {
        HistoryDTO history = new HistoryDTO();

        history.setCreatedDate(new Date());
        history.setHostName("localhost");
        history.setId(BigInteger.ONE);
        history.setEntityId(null);
        history.setMessage("A description of the change or operation.");
        history.setJobStatus(JobStatus.SUCCESS);
        return history;
    }

    /**
     * Returns the entity ID.
     *
     * @return  The entity ID.
     */
    public BigInteger getId() {
        return id;
    }

    /**
     * Specifies the entity ID.
     *
     * @param  id  The entity ID.
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
     * Returns the message.
     *
     * @return  The message.
     */
    public String getMessage() {
        return message;
    }

    /**
     * Specifies the message.
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
     * @param  jpaEntity  The entity object.
     */
    public void setEntityId(JPAEntity jpaEntity) {
        this.entityId = jpaEntity.getId();
    }

    /**
     * Returns the job status.
     *
     * @return  The job status.
     */
    public JobStatus getJobStatus() {
        return jobStatus;
    }

    /**
     * Specifies the job status.
     *
     * @param  jobStatus  The job status.
     */
    public void setJobStatus(JobStatus jobStatus) {
        this.jobStatus = jobStatus;
    }

    /**
     * Returns the wait time in milliseconds.
     *
     * @return  The wait time in milliseconds.
     */
    public long getWaitTime() {
        return waitTime;
    }

    /**
     * Specifies the wait time in milliseconds.
     *
     * @param  waitTime  The wait time in milliseconds.
     */
    public void setWaitTime(long waitTime) {
        this.waitTime = waitTime;
    }

    /**
     * Returns the execution time in milliseconds.
     *
     * @return  The execution time in milliseconds.
     */
    public long getExecutionTime() {
        return executionTime;
    }

    /**
     * Specifies the execution time in milliseconds.
     *
     * @param  executionTime  The execution time in milliseconds.
     */
    public void setExecutionTime(long executionTime) {
        this.executionTime = executionTime;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

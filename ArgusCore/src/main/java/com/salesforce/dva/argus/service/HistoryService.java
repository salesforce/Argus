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
	 
package com.salesforce.dva.argus.service;

import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.entity.History.JobStatus;
import com.salesforce.dva.argus.entity.JPAEntity;
import java.math.BigInteger;
import java.util.List;

/**
 * Provides methods to create, update and read job history records.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public interface HistoryService extends Service {

    //~ Methods **************************************************************************************************************************************

    /** Deletes expired history. */
    void deleteExpiredHistory();

    /**
     * Creates or updates job history.
     *
     * @param   history  The job history record to create or update. Cannot be null.
     *
     * @return  Updated job history object.
     */
    History updateHistory(History history);

    /**
     * Creates a new job history record.
     *
     * @param   message        The message pattern.
     * @param   entity         The entity to which the job history will be attached.
     * @param   jobStatus      The status of the job.
     * @param   waitTime       Job waiting time in MS.
     * @param   executionTime  Total job execution time in MS.
     * @param   params         The message parameters.
     *
     * @return  Created audit object.
     */
    History createHistory(String message, JPAEntity entity, JobStatus jobStatus, long waitTime, long executionTime, Object... params);

    /**
     * Finds the history object by Id.
     *
     * @param   id  Id of the job. Cannot be null and must be a positive non-zero number.
     *
     * @return  The job history record.
     */
    History findHistoryByPrimaryKey(BigInteger id);

    /**
     * Finds Job history for a given job.
     *
     * @param   entityId  jobId The job Id for which the history requested. Cannot be null.
     *
     * @return  History for a given entity.
     */
    List<History> findByJob(BigInteger entityId);

    /**
     * Finds Job history for a given job.
     *
     * @param   entityId  jobId The job Id for which the history requested. Cannot be null.
     * @param   limit     result set size
     *
     * @return  History for a given entity.
     */
    List<History> findByJob(BigInteger entityId, BigInteger limit);

    /**
     * Finds Job history for a given job.
     *
     * @param   entityId   jobId The job Id for which the history requested. Cannot be null.
     * @param   limit      result set size.
     * @param   jobStatus  The status of the job.
     *
     * @return  History for a given entity.
     */
    List<History> findByJobAndStatus(BigInteger entityId, BigInteger limit, JobStatus jobStatus);

    /**
     * Appends the message to the existing job history. Properties with default values are ignored.
     *
     * @param   id             Job history Id. Cannot be null.
     * @param   message        Message to be appended.
     * @param   jobStatus      The status of the job.
     * @param   waitTime       Job waiting time in MS.
     * @param   executionTime  Total job execution time in MS.
     *
     * @return  updated job history object.
     */
    History appendMessageAndUpdate(BigInteger id, String message, JobStatus jobStatus, long waitTime, long executionTime);
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

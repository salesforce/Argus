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
	 
package com.salesforce.dva.argus.service.history;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.entity.History.JobStatus;
import com.salesforce.dva.argus.entity.JPAEntity;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.HistoryService;
import com.salesforce.dva.argus.service.jpa.DefaultJPAService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import org.slf4j.Logger;
import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.List;
import javax.persistence.EntityManager;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;
import static java.math.BigInteger.ZERO;

/**
 * Provides methods to create and update audit history.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class DefaultHistoryService extends DefaultJPAService implements HistoryService {

    //~ Instance fields ******************************************************************************************************************************

    @SLF4JTypeListener.InjectLogger
    private Logger _logger;
    @Inject
    private Provider<EntityManager> emf;

    //~ Constructors *********************************************************************************************************************************

    /** 
     * Creates a new DefaultHistoryService object. 
     * @param sysConfig The system configuration.  Cannot be null.
     */
    @Inject
    public DefaultHistoryService(SystemConfiguration sysConfig) {
        super(null, sysConfig);
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    @Transactional
    public void deleteExpiredHistory() {
        requireNotDisposed();
        try {
            int deletedCount = History.deleteExpiredHistory(emf.get());

            _logger.info("Deleted {} expired job history records.", deletedCount);
        } catch (Exception ex) {
            _logger.warn("Couldn't delete expired job history : {}", ex.getMessage());
        }
    }

    @Override
    @Transactional
    public History updateHistory(History history) {
        requireNotDisposed();
        requireArgument(history != null, "Null history cannot be Updated.");

        History result = mergeEntity(emf.get(), history);

        _logger.debug("Updated job history object {}", result);
        return result;
    }

    @Override
    @Transactional
    public History createHistory(String message, JPAEntity entity, JobStatus jobStatus, long waitTime, long executionTime, Object... params) {
        History history = new History(MessageFormat.format(message, params), SystemConfiguration.getHostname(), entity, jobStatus, waitTime,
            executionTime);

        return updateHistory(history);
    }

    @Override
    @Transactional
    public History findHistoryByPrimaryKey(BigInteger id) {
        requireNotDisposed();
        requireArgument(id != null && id.compareTo(ZERO) > 0, "Id must be a positive non-zero value.");

        History result = findEntity(emf.get(), id, History.class);

        _logger.debug("Query for job history having id {} resulted in : {}", id, result);
        return result;
    }

    @Override
    public List<History> findByJob(BigInteger entityId) {
        return findByJob(entityId, null);
    }

    @Override
    @Transactional
    public List<History> findByJob(BigInteger entityId, BigInteger limit) {
        requireNotDisposed();
        requireArgument(entityId != null && entityId.compareTo(ZERO) > 0, "Id must be a positive non-zero value.");

        EntityManager em = emf.get();
        JPAEntity entity = findEntity(em, entityId, JPAEntity.class);

        if (entity == null) {
            throw new IllegalArgumentException(MessageFormat.format("The job with Id {0} does not exist.", entityId));
        }

        List<History> result = History.findHistoryByJob(em, entity, limit);

        _logger.debug("Query for job history with job Id {} returned {} records", entity.getId(), result.size());
        return result;
    }

    @Override
    @Transactional
    public List<History> findByJobAndStatus(BigInteger entityId, BigInteger limit, JobStatus jobStatus) {
        requireNotDisposed();
        requireArgument(entityId != null && entityId.compareTo(ZERO) > 0, "Id must be a positive non-zero value.");
        requireArgument(jobStatus != null, "Job status cannot be null.");

        EntityManager em = emf.get();
        JPAEntity entity = findEntity(em, entityId, JPAEntity.class);

        if (entity == null) {
            throw new IllegalArgumentException(MessageFormat.format("The job with Id {0} does not exist.", entityId));
        }

        List<History> result = History.findHistoryByJobAndStatus(em, entity, limit, jobStatus);

        _logger.debug("Query for job history with job Id {} and status {} returned {} records", entity.getId(), jobStatus, result.size());
        return result;
    }

    @Override
    @Transactional
    public History appendMessageAndUpdate(BigInteger id, String message, JobStatus jobStatus, long waitTime, long executionTime) {
        requireNotDisposed();

        History history = findHistoryByPrimaryKey(id);

        if (history == null) {
            throw new SystemException("Job History object does not exist so can not be updated.");
        }
        if (message != null && message.length() > 0) {
            history.setMessage(history.getMessage() + message);
        }
        if (jobStatus != null) {
            history.setJobStatus(jobStatus);
        }
        if (waitTime > 0) {
            history.setWaitTime(waitTime);
        }
        if (executionTime > 0) {
            history.setExecutionTime(executionTime);
        }
        return updateHistory(history);
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

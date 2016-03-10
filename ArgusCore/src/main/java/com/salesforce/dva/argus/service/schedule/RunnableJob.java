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
	 
package com.salesforce.dva.argus.service.schedule;

import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.CronJob;
import com.salesforce.dva.argus.entity.JPAEntity;
import com.salesforce.dva.argus.service.AlertService;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.GlobalInterlockService.LockType;
import com.salesforce.dva.argus.system.SystemException;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import java.util.Arrays;

/**
 * Represents a scheduled job to be run periodically.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class RunnableJob implements Job {

    //~ Static fields/initializers *******************************************************************************************************************
	
    public RunnableJob() {}

	/** The lock type key name. */
    public static final String LOCK_TYPE = "LockType";

    /** The CRON job type key name. */
    public static final String CRON_JOB = "CronJob";

    //~ Instance fields ******************************************************************************************************************************

    private CronJob job = null;
    private LockType lockType = null;

    //~ Methods **************************************************************************************************************************************

    /**
     * Passing the service instance from quartz main thread, to worker thread as a parameter. Although the alert service used here is not thread safe,
     * we are using the enqueueAlerts in a thread safe manner, since there is no shared mutable state in this method.
     *
     * <p>Not using Guice for injecton of services, since the JPAService in Guice when called as part of worker thread context has emfFactory as null.
     * Hence no database operation can be performed through JPA from he worker thread.</p>
     *
     * @param   context  - Job context passed from quartz main thread, to worker thread. Parameters are extracted from this context.
     *
     * @throws  JobExecutionException  If an error occurs.
     */
    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        JobDataMap map = context.getJobDetail().getJobDataMap();
        AlertService alertService = (AlertService) map.get("AlertService");
        AuditService auditService = (AuditService) map.get("AuditService");

        if (map.containsKey(LOCK_TYPE)) {
            lockType = (LockType) map.get(LOCK_TYPE);
        }
        if (map.containsKey(CRON_JOB)) {
            job = (CronJob) map.get(CRON_JOB);
        }
        try {
            if (!alertService.isDisposed()) {
                if (LockType.ALERT_SCHEDULING.equals(lockType)) {
                    alertService.enqueueAlerts(Arrays.asList(new Alert[] { Alert.class.cast(job) }));
                } else {
                    throw new SystemException("Unsupported lock type " + lockType);
                }
            }
        } catch (Exception ex) {
            auditService.createAudit("Could not enqueue scheduled job. " + ex.getMessage(), JPAEntity.class.cast(job));
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

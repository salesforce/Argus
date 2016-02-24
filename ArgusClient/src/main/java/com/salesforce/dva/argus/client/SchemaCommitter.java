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
	 
package com.salesforce.dva.argus.client;

import com.salesforce.dva.argus.service.CollectionService;
import com.salesforce.dva.argus.service.MonitorService;

import java.text.MessageFormat;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Drains metric schema from the respective queue and commits them to persistent storage to facilitate application level wildcarding and aggregation.
 * This is an alpha feature and is not currently supported.
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
class SchemaCommitter extends AbstractCommitter {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final int METRIC_MESSAGES_CHUNK_SIZE = 100;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new Committer object.
     *
     * @param  collectionService     The collection service to use. Cannot be null.
     * @param  monitorService 	The monitoring service to use. Cannot be null.
     * @param  jobCounter  The job counter. Cannot be null.
     */
    SchemaCommitter(CollectionService collectionService, MonitorService monitorService, AtomicInteger jobCounter) {
        super(collectionService, monitorService, jobCounter);
    }

    //~ Methods **************************************************************************************************************************************

    /** @see  Runnable#run() */
    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                int count = collectionService.commitMetricSchema(METRIC_MESSAGES_CHUNK_SIZE, TIMEOUT);

                if (count > 0) {
                    LOGGER.info(MessageFormat.format("Committed {0} metrics for schema records creation.", count));
                    jobCounter.incrementAndGet();
                }
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException ie) {
                LOGGER.info("Execution was interrupted.");
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable ex) {
                LOGGER.info("Error occured while committing metrics for schema records creation.", ex);
            }
        }
        LOGGER.warn("Ending Schema Committer.");
        collectionService.dispose();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

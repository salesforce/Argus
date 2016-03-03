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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The thread that is used to drain metrics and annotations from their respective queues and commit them to TSDB.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
abstract class AbstractCommitter implements Runnable {

    //~ Static fields/initializers *******************************************************************************************************************

    protected static final int TIMEOUT = 500;
    protected static final int POLL_INTERVAL_MS = 10; 
    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractCommitter.class);

    //~ Instance fields ******************************************************************************************************************************

    protected final CollectionService collectionService;
    protected final AtomicInteger jobCounter;
    protected final MonitorService monitorService;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new Committer object.
     *
     * @param  collectionService     The collection service to use. Cannot be null.
     * @param  monitorService 	The monitoring service to use. Cannot be null.
     * @param  jobCounter  The job counter. Cannot be null.
     */
    AbstractCommitter(CollectionService collectionService, MonitorService monitorService, AtomicInteger jobCounter) {
        this.collectionService = collectionService;
        this.jobCounter = jobCounter;
        this.monitorService=monitorService;
    }

    //~ Methods **************************************************************************************************************************************

    /** @see  Runnable#run() */
    @Override
    public abstract void run();
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

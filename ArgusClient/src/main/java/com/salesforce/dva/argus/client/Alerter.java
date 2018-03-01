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

import com.salesforce.dva.argus.service.AlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.text.MessageFormat;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executes alert evaluations for a client configured for alerting.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
class Alerter implements Runnable {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final long POLL_INTERVAL_MS = 500;
    private static final Logger LOGGER = LoggerFactory.getLogger(Alerter.class);

    //~ Instance fields ******************************************************************************************************************************

    private final AlertService service;
    private final int timeout;
    private final AtomicInteger jobCounter;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new Alerter object.
     *
     * @param  service     The alert service to use.
     * @param  timeout     The timeout in milliseconds for a single alert evaluation. Must be a positive number.
     * @param  jobCounter  The job counter. Cannot be null.
     */
    Alerter(AlertService service, int timeout, AtomicInteger jobCounter) {
        this.service = service;
        this.timeout = timeout;
        this.jobCounter = jobCounter;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                jobCounter.addAndGet(service.executeScheduledAlerts(50, timeout).size());
                LOGGER.info("alerts evaluated so far: {}", jobCounter.get()); 
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException ex) {
                LOGGER.info("Execution was interrupted.");
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable ex) {
                LOGGER.warn("Exception in alerter: {}", ex);
            }
        }
        LOGGER.warn(MessageFormat.format("Alerter thread interrupted. {} alerts evaluated by this thread.", jobCounter.get()));
        service.dispose();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

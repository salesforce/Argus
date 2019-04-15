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

import com.salesforce.dva.argus.service.RefocusService;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.text.MessageFormat;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Forwards batches of refocus notifications.
 *
 * @author  Ian Keck (ikeck@salesforce.com)
 */
class Refocuser implements Runnable {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final long POLL_INTERVAL_MS = 10;
    private static final Logger LOGGER = LoggerFactory.getLogger(Refocuser.class);

    //~ Instance fields ******************************************************************************************************************************

    private final RefocusService service;
    private final int timeout;
    private final AtomicInteger jobCounter;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new Alerter object.
     *
     * @param  service     The Refocus service to use.
     * @param  timeout     The timeout in milliseconds for a single alert evaluation. Must be a positive number.
     * @param  jobCounter  The job counter. Cannot be null.
     */
    Refocuser(RefocusService service, int timeout, AtomicInteger jobCounter) {
        this.service = service;
        this.timeout = timeout;
        this.jobCounter = jobCounter; // TODO - needed?
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public void run() {

        while (!Thread.currentThread().isInterrupted()) {
            try {
                int forwarded = service.forwardNotifications();

                if (forwarded > 0)
                {
                    jobCounter.set( service.getNotificationsDelivered() + service.getNotificationsDiscarded());
                }

                Thread.sleep(POLL_INTERVAL_MS); // TODO - needed?
            } catch (InterruptedException ex) {
                LOGGER.info("Execution was interrupted.");
                Thread.currentThread().interrupt();
                break;
            } catch (Throwable ex) {
                LOGGER.error("Exception in RefocusForwarder: {}", ExceptionUtils.getFullStackTrace(ex));
            }
        }
        service.dispose();
    }
}
/* Copyright (c) 2019, Salesforce.com, Inc.  All rights reserved. */

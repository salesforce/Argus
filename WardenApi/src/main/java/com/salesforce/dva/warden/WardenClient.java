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
package com.salesforce.dva.warden;

import com.salesforce.dva.warden.dto.Policy;
import java.util.List;

/**
 * DOCUMENT ME!
 *
 * @author  jbhatt
 */
public interface WardenClient {

    //~ Methods **************************************************************************************************************************************

    /**
     * This method is responsible for establishing communication with the warden server. It performs the following operations: Establish communication
     * via the specified port by which the server can publish relevant events to. Compare policies provided as parameters and reconcile with whats on
     * the server upserting as needed. Start the usage data push scheduling, so that usage data gets pushed to server on regular intervals.
     *
     * @param  policy  DOCUMENT ME!
     * @param  port    DOCUMENT ME!
     */
    void register(List<Policy> policy, int port);

    /** DOCUMENT ME! */
    void unregister();

    /**
     * DOCUMENT ME!
     *
     * @param  policy    DOCUMENT ME!
     * @param  username  DOCUMENT ME!
     * @param  value     DOCUMENT ME!
     */
    void updateMetric(Policy policy, String username, double value) throws SuspendedException;

    /**
     * DOCUMENT ME!
     *
     * @param  policy    DOCUMENT ME!
     * @param  username  DOCUMENT ME!
     * @param  delta     DOCUMENT ME!
     */
    void modifyMetric(Policy policy, String username, double delta) throws SuspendedException;
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

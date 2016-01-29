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

import java.util.Set;

/**
 * Sends an email message.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public interface MailService extends Service {

    //~ Methods **************************************************************************************************************************************

    /**
     * Sends an email message.
     *
     * @param  to           The set of email recipients.
     * @param  subject      The email subject.
     * @param  message      The message body.
     * @param  contentType  The content type.
     * @param  priority     The message priority.
     */
    void sendMessage(Set<String> to, String subject, String message, String contentType, Priority priority);

    //~ Enums ****************************************************************************************************************************************

    /**
     * Sets the priority of the message.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public enum Priority {

        /** High or urgent priority. */
        HIGH(1),
        /** Normal priority. */
        NORMAL(3),
        /** Low priority. */
        LOW(5);

        private final int xPriority;

        /**
         * Creates a new Priority object.
         *
         * @param  prio  The message x-priority as an integer.
         */
        Priority(int prio) {
            this.xPriority = prio;
        }

        /**
         * Returns the corresponding X-Priority value.
         *
         * @return  1 for High, 3 for Normal and 5 for Low priority.
         */
        public int getXPriority() {
            return this.xPriority;
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

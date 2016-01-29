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

/**
 * Provides methods to obtain and release a global lock exclusive across multiple JVM's.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public interface GlobalInterlockService extends Service {

    //~ Methods **************************************************************************************************************************************

    /**
     * Attempts to obtain an exclusive lock.
     *
     * @param   expiration  The amount of time in milliseconds which when applied to an existing locks creation time (should another process control
     *                      the lock) will indicate the existing lock is expired and can be overwritten. Must be a positive non-zero number.
     * @param   type        The type of lock. Cannot be null.
     * @param   note        A note to associate with the lock. Cannot be null or empty.
     *
     * @return  The key to be used for releasing the obtained lock or null if a lock could not be obtained.
     */
    String obtainLock(long expiration, LockType type, String note);

    /**
     * Attempts to refresh an lock. This method is provided to ensure a lock does not accidentally expire.
     *
     * @param   type  The type of lock. Cannot be null.
     * @param   key   The lock key obtained when acquiring the lock. Cannot be null or empty.
     * @param   note  A note to associate with the lock. Cannot be null or empty.
     *
     * @return  The new key if the lock exists and is successfully refreshed or null if the lock doesn't exist or wasn't refreshed.
     */
    String refreshLock(LockType type, String key, String note);

    /**
     * Attempts to release an exclusive lock.
     *
     * @param   type  The type of lock to release. Cannot be null.
     * @param   key   The lock key obtained when acquiring the lock. Cannot be null or empty.
     *
     * @return  True if the lock was released or false if the key doesn't match the currently held lock.
     */
    boolean releaseLock(LockType type, String key);

    //~ Enums ****************************************************************************************************************************************

    /**
     * Represents the supported lock types.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public static enum LockType {

        ALERT_SCHEDULING,
        COLLECTION_SCHEDULING;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

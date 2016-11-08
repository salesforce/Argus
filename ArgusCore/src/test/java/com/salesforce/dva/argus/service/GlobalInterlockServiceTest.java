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

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.service.GlobalInterlockService.LockType;
import org.junit.Test;

import static org.junit.Assert.*;

public class GlobalInterlockServiceTest extends AbstractTest {

    private static final long EXPIRATION_MS = 750;

    @Test
    public void testReleaseNonexistentScheduleLock() {
        GlobalInterlockService service = system.getServiceFactory().getGlobalInterlockService();

        assertFalse(service.releaseLock(LockType.ALERT_SCHEDULING, String.valueOf(System.currentTimeMillis())));
    }

    @Test
    public void testObtainReleaseLock() {
        GlobalInterlockService service = system.getServiceFactory().getGlobalInterlockService();
        String key = service.obtainLock(EXPIRATION_MS, LockType.ALERT_SCHEDULING, "Note");

        assertNull(service.obtainLock(EXPIRATION_MS, LockType.ALERT_SCHEDULING, "Note"));
        assertTrue(service.releaseLock(LockType.ALERT_SCHEDULING, key));
        assertFalse(service.releaseLock(LockType.ALERT_SCHEDULING, key));
    }

    @Test
    public void testObtainRefreshLock() throws InterruptedException {
        GlobalInterlockService service = system.getServiceFactory().getGlobalInterlockService();
        String key = service.obtainLock(EXPIRATION_MS, LockType.ALERT_SCHEDULING, "Note");

        assertNotNull(key);
        Thread.sleep(2000L);

        String refreshed = service.refreshLock(LockType.ALERT_SCHEDULING, key, "note");

        assertNotNull(refreshed);
        assertFalse(key.equals(refreshed));
        assertTrue(service.releaseLock(LockType.ALERT_SCHEDULING, refreshed));
        assertNull(service.refreshLock(LockType.ALERT_SCHEDULING, key, "note"));
    }

    @Test
    public void testObtainOnExpiredScheduleLock() throws InterruptedException {
        GlobalInterlockService service = system.getServiceFactory().getGlobalInterlockService();

        assertNotNull(service.obtainLock(EXPIRATION_MS, LockType.ALERT_SCHEDULING, "Note"));
        assertNull(service.obtainLock(EXPIRATION_MS, LockType.ALERT_SCHEDULING, "Note"));
        Thread.sleep(2000L);

        String key = service.obtainLock(EXPIRATION_MS, LockType.ALERT_SCHEDULING, "Note");

        assertNotNull(key);
        service.releaseLock(LockType.ALERT_SCHEDULING, key);
    }

    @Test
    public void testLockExclusivity() {
        GlobalInterlockService service = system.getServiceFactory().getGlobalInterlockService();
        String keyA = service.obtainLock(EXPIRATION_MS, LockType.ALERT_SCHEDULING, "Note");
        String keyB = service.obtainLock(EXPIRATION_MS, LockType.COLLECTION_SCHEDULING, "Note");

        assertTrue(service.releaseLock(LockType.ALERT_SCHEDULING, keyA));
        assertTrue(service.releaseLock(LockType.COLLECTION_SCHEDULING, keyB));
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

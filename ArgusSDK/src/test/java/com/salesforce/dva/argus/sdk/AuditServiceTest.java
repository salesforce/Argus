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
package com.salesforce.dva.argus.sdk;

import com.salesforce.dva.argus.sdk.entity.Audit;
import org.junit.Test;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class AuditServiceTest extends AbstractTest {

    @Test
    public void testGetAuditsForEntity() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AuditServiceTest.json"))) {
            AuditService auditService = argusService.getAuditService();
            List<Audit> result = auditService.getAuditsForEntity(BigInteger.ONE);
            List<Audit> expected = Arrays.asList(new Audit[] { _constructPersistedAudit() });

            assertEquals(expected, result);
        }
    }

    @Test
    public void testGetAudit() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AuditServiceTest.json"))) {
            AuditService auditService = argusService.getAuditService();
            Audit result = auditService.getAudit(BigInteger.ONE);
            Audit expected = _constructPersistedAudit();

            assertEquals(expected, result);
        }
    }

    private Audit _constructPersistedAudit() {
        Audit result = new Audit();

        result.setCreatedDate(new Date(1472282830936L));
        result.setHostName("localhost");
        result.setId(BigInteger.ONE);
        result.setMessage("TestMessage");
        result.setEntityId(BigInteger.ONE);
        return result;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

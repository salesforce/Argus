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

import com.salesforce.dva.argus.sdk.entity.History;
import org.junit.Test;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class HistoryServiceTest extends AbstractTest {

    @Test
    public void testGetHistoryForEntity() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/HistoryServiceTest.json"))) {
            HistoryService historyService = argusService.getHistoryService();
            List<History> result = historyService.getHistoryForEntity(BigInteger.ONE, 1);
            List<History> expected = Arrays.asList(new History[] { _constructPersistedHistory() });

            assertEquals(expected, result);
        }
    }

    @Test
    public void testGetHistory() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/HistoryServiceTest.json"))) {
            HistoryService historyService = argusService.getHistoryService();
            History result = historyService.getHistory(BigInteger.ONE);
            History expected = _constructPersistedHistory();

            assertEquals(expected, result);
        }
    }

    private History _constructPersistedHistory() {
        History result = new History();

        result.setCreatedDate(new Date(1472282830936L));
        result.setEntityId(BigInteger.ONE);
        result.setExecutionTime(100);
        result.setHostName("TestHost");
        result.setId(BigInteger.ONE);
        result.setJobStatus("TestStatus");
        result.setMessage("TestMessage");
        result.setWaitTime(100);
        return result;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

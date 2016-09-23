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

import com.salesforce.dva.argus.sdk.entity.Batch;
import com.salesforce.dva.argus.sdk.entity.Batch.Query;
import com.salesforce.dva.argus.sdk.entity.Metric;
import org.junit.Test;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class BatchServiceTest extends AbstractTest {

    @Test
    public void testCreateBatch() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/BatchServiceTest.json"))) {
            BatchService batchService = argusService.getBatchService();
            BigInteger id = batchService.createBatch(Arrays.asList(new String[] { "-1d:TestScope:TestMetric{TestTag=TagValue}:avg" }), 3600);

            assertEquals(BigInteger.ONE, id);
        }
    }

    @Test
    public void testDeleteBatch() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/BatchServiceTest.json"))) {
            BatchService batchService = argusService.getBatchService();

            batchService.deleteBatch(BigInteger.ONE);
        }
    }

    @Test
    public void testGetBatch() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/BatchServiceTest.json"))) {
            BatchService batchService = argusService.getBatchService();
            Batch result = batchService.getBatch(BigInteger.ONE);
            Batch expected = _constructPersistedBatch();

            assertEquals(expected, result);
        }
    }

    @Test
    public void testGetBatches() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/BatchServiceTest.json"))) {
            BatchService batchService = argusService.getBatchService();
            Map<String, String> result = batchService.getBatches();
            Map<String, String> expected = new HashMap<>();

            expected.put("1", "QUEUED");
            assertEquals(expected, result);
        }
    }

    private Batch _constructPersistedBatch() {
        Batch result = new Batch();

        result.setCreatedDate(1472282830936L);
        result.setOwnerName("TestOwner");
        result.setStatus("QUEUED");
        result.setTtl(3600);
        result.setQueries(Arrays.asList(new Query[] { _constructPersistedQuery() }));
        return result;
    }

    private Query _constructPersistedQuery() {
        return new Query("-1d:TestScope:TestMetric{TestTag=TagValue}:avg", _constructPersistedMetric(), "TestMessage");
    }

    private Metric _constructPersistedMetric() {
        Metric result = new Metric();

        result.setDisplayName("TestDisplayName");
        result.setMetric("TestMetric");
        result.setNamespace("TestNamespace");
        result.setScope("TestScope");
        result.setDatapoints(new HashMap<>());
        result.setTags(new HashMap<>());
        result.setUnits("TestUnits");
        return result;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

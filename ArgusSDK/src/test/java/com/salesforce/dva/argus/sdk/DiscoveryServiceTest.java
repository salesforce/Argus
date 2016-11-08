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

import com.salesforce.dva.argus.sdk.entity.MetricSchemaRecord;
import org.junit.Test;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static com.salesforce.dva.argus.sdk.DiscoveryService.FieldSelector.NAMESPACE;
import static org.junit.Assert.assertEquals;

public class DiscoveryServiceTest extends AbstractTest {

    @Test
    public void testGetMatchingRecords() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/DiscoveryServiceTest.json"))) {
            DiscoveryService discoveryService = argusService.getDiscoveryService();
            List<MetricSchemaRecord> result = discoveryService.getMatchingRecords("nsReg", "scpReg", "metReg", "tkReg", "tvReg", 1);
            List<MetricSchemaRecord> expected = Arrays.asList(new MetricSchemaRecord[] { _constructPersistedMetricSchemaRecord() });

            assertEquals(expected, result);
        }
    }

    @Test
    public void testGetMatchingRecordFields() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/DiscoveryServiceTest.json"))) {
            DiscoveryService discoveryService = argusService.getDiscoveryService();
            List<String> result = discoveryService.getMatchingRecordFields("nsReg", "scpReg", "metReg", "tkReg", "tvReg", NAMESPACE, 2);
            List<String> expected = Arrays.asList(new String[] { "namespace1", "namespace2" });

            assertEquals(expected, result);
        }
    }

    private MetricSchemaRecord _constructPersistedMetricSchemaRecord() {
        MetricSchemaRecord result = new MetricSchemaRecord();

        result.setMetric("metReg");
        result.setNamespace("nsReg");
        result.setScope("scpReg");
        result.setTagKey("tkReg");
        result.setTagValue("tvReg");
        return result;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

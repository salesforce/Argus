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
package com.salesforce.dva.warden.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.salesforce.dva.warden.dto.Policy;
import com.salesforce.dva.warden.dto.WardenResource;
import com.salesforce.dva.warden.dto.WardenResource.MetaKey;
import org.junit.Test;
import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class PolicyServiceTest extends AbstractTest {

    @Test
    public void testGetPolicies() throws IOException {
        try(WardenService wardenService = new WardenService(getMockedClient("/PolicyServiceTest.testGetPolicies.json"))) {
            PolicyService policyService = wardenService.getPolicyService();
            WardenResponse<Policy> expectedResponse = _constructPersistedResponse("GET");
            WardenResponse<Policy> actualResponse = policyService.getPolicies();

            assertEquals(expectedResponse, actualResponse);
        }
    }

    @Test
    public void testCreatePolicies() throws IOException {
        try(WardenService wardenService = new WardenService(getMockedClient("/PolicyServiceTest.testGetPolicies.json"))) {
            PolicyService policyService = wardenService.getPolicyService();
            List<Policy> policies = Arrays.asList(new Policy[] { _constructUnpersistedPolicy() });
            WardenResponse<Policy> actualResponse = policyService.createPolicies(policies);
            WardenResponse<Policy> expectedResponse = _constructPersistedResponse("POST");

            assertEquals(expectedResponse, actualResponse);
        }
    }

    @Test
    public void testDeletePolicies() throws IOException {

        try(WardenService wardenService = new WardenService(getMockedClient("/PolicyServiceTest.testGetPolicies.json"))) {
            PolicyService policyService = wardenService.getPolicyService();
            BigInteger[] policyIds = { BigInteger.ONE };
            WardenResponse<Policy> actualResponse = policyService.deletePolicies(policyIds);
            WardenResponse<Policy> expectedResponse = _constructPersistedResponse("DELETE");

            assertEquals(expectedResponse, actualResponse);
        }
    }

    @Test
    public void testUpdatePolicies() throws IOException {
        try(WardenService wardenService = new WardenService(getMockedClient("/PolicyServiceTest.testGetPolicies.json"))) {
            PolicyService policyService = wardenService.getPolicyService();
            List<Policy> policies = Arrays.asList(new Policy[] { _constructUnpersistedPolicy() });
            WardenResponse<Policy> actualResponse = policyService.updatePolicies(policies);
            WardenResponse<Policy> expectedResponse = _constructPersistedResponse("PUT");

            assertEquals(expectedResponse, actualResponse);
        }
    }

    private WardenResponse<Policy> _constructPersistedResponse(String httpVerb) throws JsonProcessingException {
        Policy persistedPolicy = _constructUnpersistedPolicy();
        WardenResponse<Policy> result = new WardenResponse<>();
        WardenResource<Policy> resource = new WardenResource<>();
        List<WardenResource<Policy>> resources = new ArrayList<>(1);
        EnumMap<MetaKey, String> meta = new EnumMap<>(MetaKey.class);

        meta.put(MetaKey.HREF, "TestHref");
        meta.put(MetaKey.DEV_MESSAGE, "TestDevMessage");
        meta.put(MetaKey.MESSAGE, "TestMessage");
        meta.put(MetaKey.STATUS, "200");
        meta.put(MetaKey.UI_MESSAGE, "TestUIMessage");
        meta.put(MetaKey.VERB, httpVerb);
        persistedPolicy.setId(BigInteger.ONE);
        resource.setEntity(persistedPolicy);
        resource.setMeta(meta);
        resources.add(resource);
        result.setMessage("success");
        result.setStatus(200);
        result.setResources(resources);
        System.out.println(MAPPER.writeValueAsString(result));
        return result;
    }

    private Policy _constructUnpersistedPolicy() {
        Policy result = new Policy();

        result.setCreatedById(BigInteger.ONE);
        result.setCreatedDate(new Date(1472847819167L));
        result.setModifiedById(BigInteger.TEN);
        result.setModifiedDate(new Date(1472847819167L));
        result.setService("TestService");
        result.setName("TestName");
        result.setOwners(Arrays.asList("TestOwner"));
        result.setUsers(Arrays.asList("TestUser"));
        result.setSubSystem("TestSubSystem");
        result.setMetricName("TestMetricName");
        result.setTriggerType(Policy.TriggerType.BETWEEN);
        result.setAggregator(Policy.Aggregator.AVG);
        result.setThresholds(Arrays.asList(0.0));
        result.setTimeUnit("5min");
        result.setDefaultValue(0.0);
        result.setCronEntry("0 */4 * * *");
        return result;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

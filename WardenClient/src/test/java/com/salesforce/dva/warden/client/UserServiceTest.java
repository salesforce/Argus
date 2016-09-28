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
import com.salesforce.dva.warden.dto.*;
import com.salesforce.dva.warden.dto.WardenResource.MetaKey;
import org.junit.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.*;

import static org.junit.Assert.assertEquals;

public class UserServiceTest extends AbstractTest {

    @Test
    public void testGetUsers() throws IOException {
        try(WardenService wardenService = new WardenService(getMockedClient("/UserServiceTests.json"))) {
            UserService userService = wardenService.getUserService();
            WardenResponse<WardenUser> expectedResponse = _constructPersistedResponse("GET");
            WardenResponse<WardenUser> actualResponse = userService.getUsers();

            assertEquals(expectedResponse, actualResponse);
        }
    }

    @Test
    public void testGetUserById() throws IOException {
        try(WardenService wardenService = new WardenService(getMockedClient("/UserServiceTests.json"))) {
            UserService userService = wardenService.getUserService();
            WardenResponse<WardenUser> expectedResponse = _constructPersistedResponse("GET");
            WardenResponse<WardenUser> actualResponse = userService.getUserById(BigInteger.ONE);

            assertEquals(expectedResponse, actualResponse);
        }
    }

    @Test
    public void testGetPoliciesByUser() throws IOException {
        try(WardenService wardenService = new WardenService(getMockedClient("/UserServiceTests.json"))) {
            UserService userService = wardenService.getUserService();
            WardenResponse<Policy> expectedResponse = _constructPersistedResponsePolicy("GET");
            WardenResponse<Policy> actualResponse = userService.getPoliciesForUser(BigInteger.ONE);

            assertEquals(expectedResponse, actualResponse);
        }
    }

    @Test
    public void testGetInfractionsForUserAndPolicy() throws IOException {
        try(WardenService wardenService = new WardenService(getMockedClient("/UserServiceTests.json"))) {
            UserService userService = wardenService.getUserService();
            WardenResponse<Infraction> expectedResponse = _constructPersistedResponseInfraction("GET");
            WardenResponse<Infraction> actualResponse = userService.getInfractionsForUserAndPolicy(BigInteger.ONE, BigInteger.ONE);

            assertEquals(expectedResponse, actualResponse);
        }
    }

    @Test
    public void testGetInfractionsForUser() throws IOException {
        try(WardenService wardenService = new WardenService(getMockedClient("/UserServiceTests.json"))) {
            UserService userService = wardenService.getUserService();
            WardenResponse<Infraction> expectedResponse = _constructPersistedResponseInfraction("GET");
            WardenResponse<Infraction> actualResponse = userService.getInfractionsForUser(BigInteger.ONE);

            assertEquals(expectedResponse, actualResponse);
        }
    }

    @Test
    public void testGetSuspensionsionsForUser() throws IOException {
        try(WardenService wardenService = new WardenService(getMockedClient("/UserServiceTests.json"))) {
            UserService userService = wardenService.getUserService();
            WardenResponse<Infraction> expectedResponse = _constructPersistedResponseInfraction("GET");
            WardenResponse<Infraction> actualResponse = userService.getSuspensionsForUser(BigInteger.ONE);

            assertEquals(expectedResponse, actualResponse);
        }
    }

    @Test
    public void testGetSuspensionsionForUser() throws IOException {
        try(WardenService wardenService = new WardenService(getMockedClient("/UserServiceTests.json"))) {
            UserService userService = wardenService.getUserService();
            WardenResponse<Infraction> expectedResponse = _constructPersistedResponseInfraction("GET");
            WardenResponse<Infraction> actualResponse = userService.getSuspensionForUser(BigInteger.ONE, BigInteger.ONE);

            assertEquals(expectedResponse, actualResponse);
        }
    }

    private Infraction _constructPersistedInfraction() throws JsonProcessingException {
        Infraction result = new Infraction();

        result.setPolicyId(BigInteger.ONE);
        result.setUserId(BigInteger.ONE);
        result.setInfractionTimestamp(100000L);
        result.setExpirationTimestamp(-1L);
        result.setCreatedById(BigInteger.ONE);
        result.setCreatedDate(new Date(1472847819167L));
        result.setModifiedById(BigInteger.TEN);
        result.setModifiedDate(new Date(1472847819167L));
        return result;
    }

    private WardenResponse<Infraction> _constructPersistedResponseInfraction(String httpVerb) throws JsonProcessingException {
        WardenResponse<Infraction> result = new WardenResponse<>();
        EnumMap<MetaKey, String> meta = new EnumMap<>(MetaKey.class);
        meta.put(MetaKey.HREF, "TestHref");
        meta.put(MetaKey.DEV_MESSAGE, "TestDevMessage");
        meta.put(MetaKey.MESSAGE, "TestMessage");
        meta.put(MetaKey.STATUS, "200");
        meta.put(MetaKey.UI_MESSAGE, "TestUIMessage");
        meta.put(MetaKey.VERB, httpVerb);

        WardenResource<Infraction> resource = new WardenResource<>();
        Infraction infraction = _constructPersistedInfraction();
        resource.setEntity(infraction);
        resource.setMeta(meta);

        List<WardenResource<Infraction>> resources = new ArrayList<>(1);
        infraction.setId(BigInteger.ONE);
        resources.add(resource);
        result.setMessage("success");
        result.setStatus(200);
        result.setResources(resources);
        return result;
    }


    private Policy _constructUnPersistedPolicy() throws JsonProcessingException {
        Policy result = new Policy();

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
        System.out.println("inside construct unpersisted policy");
        return result;

    }

    private Policy _constructPersistedPolicy() throws JsonProcessingException {
        Policy result = _constructUnPersistedPolicy();

        result.setId(BigInteger.ONE);
        result.setCreatedById(BigInteger.ONE);
        result.setCreatedDate(new Date(1472847819167L));
        result.setModifiedById(BigInteger.TEN);
        result.setModifiedDate(new Date(1472847819167L));
        return result;
    }

    private WardenResponse<Policy> _constructPersistedResponsePolicy(String httpVerb) throws JsonProcessingException {
        Policy persistedPolicy = _constructPersistedPolicy();
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
        return result;
    }

    private WardenUser _constructPersistedUser() throws JsonProcessingException {
        WardenUser result = new WardenUser();

        result.setEmail("user@user.com");
        result.setUserName("exampleuser");
        result.setCreatedById(BigInteger.ONE);
        result.setCreatedDate(new Date(1472847819167L));
        result.setModifiedById(BigInteger.TEN);
        result.setModifiedDate(new Date(1472847819167L));
        result.setId(BigInteger.ONE);
        
        return result;

    }


    private WardenResponse<WardenUser> _constructPersistedResponse(String httpVerb) throws JsonProcessingException {
        WardenUser persistedUser = _constructPersistedUser();
        WardenResponse<WardenUser> result = new WardenResponse<>();
        WardenResource<WardenUser> resource = new WardenResource<>();
        List<WardenResource<WardenUser>> resources = new ArrayList<>(1);
        EnumMap<MetaKey, String> meta = new EnumMap<>(MetaKey.class);

        meta.put(MetaKey.HREF, "TestHref");
        meta.put(MetaKey.DEV_MESSAGE, "TestDevMessage");
        meta.put(MetaKey.MESSAGE, "TestMessage");
        meta.put(MetaKey.STATUS, "200");
        meta.put(MetaKey.UI_MESSAGE, "TestUIMessage");
        meta.put(MetaKey.VERB, httpVerb);
        persistedUser.setId(BigInteger.ONE);
        resource.setEntity(persistedUser);
        resource.setMeta(meta);
        resources.add(resource);
        result.setMessage("success");
        result.setStatus(200);
        result.setResources(resources);
        System.out.println(MAPPER.writeValueAsString(result));
        return result;
    }

}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

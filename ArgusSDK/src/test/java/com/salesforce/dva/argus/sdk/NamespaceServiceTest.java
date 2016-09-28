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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.salesforce.dva.argus.sdk.entity.Namespace;
import org.junit.Test;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

public class NamespaceServiceTest extends AbstractTest {

    @Test
    public void testCreateNamespace() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/NamespaceServiceTest.json"))) {
            NamespaceService namespaceService = argusService.getNamespaceService();
            Namespace result = namespaceService.createNamespace(_constructUnpersistedNamespace());
            Namespace expected = _constructPersistedNamespace();

            assertEquals(expected, result);
        }
    }

    @Test
    public void testGetNamespace() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/NamespaceServiceTest.json"))) {
            NamespaceService namespaceService = argusService.getNamespaceService();
            List<Namespace> result = namespaceService.getNamespaces();
            List<Namespace> expected = Arrays.asList(new Namespace[] { _constructPersistedNamespace() });

            assertEquals(expected, result);
        }
    }

    @Test
    public void testUpdateNamespace() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/NamespaceServiceTest.json"))) {
            NamespaceService namespaceService = argusService.getNamespaceService();
            Namespace result = namespaceService.updateNamespace(BigInteger.ONE, _constructUpdatedNamespace());
            Namespace expected = _constructUpdatedNamespace();

            assertEquals(expected, result);
        }
    }

    @Test
    public void testUpdateNamespaceMembers() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/NamespaceServiceTest.json"))) {
            NamespaceService namespaceService = argusService.getNamespaceService();
            Set<String> members = new HashSet<>(Arrays.asList(new String[] { "TestUser", "UpdatedUser" }));
            Namespace result = namespaceService.updateNamespaceMembers(BigInteger.ONE, members);
            Namespace expected = _constructUpdatedNamespace();

            assertEquals(expected, result);
        }
    }

    private Namespace _constructUpdatedNamespace() throws JsonProcessingException {
        Namespace result = _constructPersistedNamespace();
        Set<String> members = result.getUsernames();

        members.add("UpdatedUser");
        return result;
    }

    private Namespace _constructPersistedNamespace() throws JsonProcessingException {
        Namespace result = _constructUnpersistedNamespace();

        result.setId(BigInteger.ONE);
        result.setCreatedById(BigInteger.ONE);
        result.setModifiedById(BigInteger.ONE);
        result.setModifiedDate(new Date(1472282830936L));
        result.setCreatedDate(new Date(1472282830936L));
        return result;
    }

    private Namespace _constructUnpersistedNamespace() throws JsonProcessingException {
        Namespace result = new Namespace();

        result.setQualifier("TestQualifier");
        result.setUsernames(new HashSet<>(Arrays.asList(new String[] { "TestUser" })));
        return result;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

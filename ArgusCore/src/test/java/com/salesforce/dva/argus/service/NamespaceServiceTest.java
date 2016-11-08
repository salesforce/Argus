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
import com.salesforce.dva.argus.entity.Namespace;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.system.SystemException;

import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NamespaceServiceTest extends AbstractTest {
	
    private NamespaceService _namespaceService;
    private UserService _userService;

    @Before
    public void setupNamespaceServiceTest() {
        _namespaceService = system.getServiceFactory().getNamespaceService();
        _userService = system.getServiceFactory().getUserService();
    }

    @Test
    public void testCreateNamespace() {
        PrincipalUser user = _userService.findAdminUser();
        Namespace namespace = new Namespace("namespace", user);

        namespace = _namespaceService.createNamespace(namespace);
        assertTrue(namespace.getId() != null && namespace.getId().compareTo(BigInteger.ZERO) > 0);
    }

    @Test(expected = SystemException.class)
    public void testNamespaceUnique() {
        PrincipalUser user = _userService.findAdminUser();
        Namespace namespace = new Namespace("namespace", user);

        _namespaceService.createNamespace(namespace);
        _namespaceService.createNamespace(namespace);
    }

    @Test
    public void testFindNamespaceByPrimaryKey() {
        PrincipalUser user = _userService.findAdminUser();
        Namespace namespace = new Namespace("namespace", user);

        namespace = _namespaceService.createNamespace(namespace);

        Namespace retrievedNamespace = _namespaceService.findNamespaceByPrimaryKey(namespace.getId());

        assertTrue(namespace.equals(retrievedNamespace));
    }

    @Test
    public void testAddAdditionalUsersToNamespace() {
        PrincipalUser user = _userService.findAdminUser();
        PrincipalUser user1 = new PrincipalUser("abc", "abc@xyz.com");
        Namespace namespace = new Namespace("namespace", user);

        namespace = _namespaceService.createNamespace(namespace);
        namespace.getUsers().add(user1);
        namespace = _namespaceService.updateNamespace(namespace);
        assertTrue(namespace.getUsers().size() == 2);
    }

    @Test
    public void testUserIsPermitted() {
        PrincipalUser user = _userService.findAdminUser();
        PrincipalUser user1 = new PrincipalUser("abc", "abc@xyz.com");
        Namespace namespace = new Namespace("namespace", user);

        namespace = _namespaceService.createNamespace(namespace);
        assertTrue(_namespaceService.isPermitted(namespace.getQualifier(), user));
        assertFalse(_namespaceService.isPermitted(namespace.getQualifier(), user1));
    }

    @Test
    public void testAdditionalUserIsPermitted() {
        PrincipalUser user = _userService.findAdminUser();
        PrincipalUser user1 = new PrincipalUser("abc", "abc@xyz.com");
        Namespace namespace = new Namespace("namespace", user);

        namespace = _namespaceService.createNamespace(namespace);
        namespace.getUsers().add(user1);
        namespace = _namespaceService.updateNamespace(namespace);
        assertTrue(_namespaceService.isPermitted(namespace.getQualifier(), user));
        assertTrue(_namespaceService.isPermitted(namespace.getQualifier(), user1));
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

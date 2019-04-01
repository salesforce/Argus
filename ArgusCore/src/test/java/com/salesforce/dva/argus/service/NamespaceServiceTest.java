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

import com.salesforce.dva.argus.entity.Namespace;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.system.SystemException;

import org.junit.Before;
import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.BeforeClass;
import org.junit.AfterClass;
import com.salesforce.dva.argus.system.SystemMain;
import com.salesforce.dva.argus.TestUtils;


public class NamespaceServiceTest {

    static private SystemMain system;
    static private UserService userService;
    static private PrincipalUser admin;
    static private NamespaceService namespaceService;

    @BeforeClass
    static public void setUpClass() {
        system = TestUtils.getInstance();
        system.start();
        namespaceService = system.getServiceFactory().getNamespaceService();
        userService = system.getServiceFactory().getUserService();
        admin = system.getServiceFactory().getUserService().findAdminUser();
    }

    @AfterClass
    static public void tearDownClass() {
        if (system != null) {
            system.getServiceFactory().getManagementService().cleanupRecords();
            system.stop();
        }
    }

    @Test
    public void testCreateNamespace() {
        PrincipalUser user = userService.findAdminUser();
        Namespace namespace = new Namespace("namespace1", user);

        namespace = namespaceService.createNamespace(namespace);
        assertTrue(namespace.getId() != null && namespace.getId().compareTo(BigInteger.ZERO) > 0);
    }

    @Test(expected = SystemException.class)
    public void testNamespaceUnique() {
        PrincipalUser user = userService.findAdminUser();
        Namespace namespace = new Namespace("namespace2", user);

        namespaceService.createNamespace(namespace);
        namespaceService.createNamespace(namespace);
    }

    @Test
    public void testFindNamespaceByPrimaryKey() {
        PrincipalUser user = userService.findAdminUser();
        Namespace namespace = new Namespace("namespace3", user);

        namespace = namespaceService.createNamespace(namespace);

        Namespace retrievedNamespace = namespaceService.findNamespaceByPrimaryKey(namespace.getId());

        assertTrue(namespace.equals(retrievedNamespace));
    }

    @Test
    public void testAddAdditionalUsersToNamespace() {
        PrincipalUser user = userService.findAdminUser();
        PrincipalUser user1 = new PrincipalUser(admin, "abc1", "abc1@xyz.com");
        Namespace namespace = new Namespace("namespace4", user);

        namespace = namespaceService.createNamespace(namespace);
        namespace.getUsers().add(user1);
        namespace = namespaceService.updateNamespace(namespace);
        assertTrue(namespace.getUsers().size() == 2);
    }

    @Test
    public void testUserIsPermitted() {
        PrincipalUser user = userService.findAdminUser();
        PrincipalUser user1 = new PrincipalUser(admin, "abc2", "abc2@xyz.com");
        Namespace namespace = new Namespace("namespace5", user);

        namespace = namespaceService.createNamespace(namespace);
        assertTrue(namespaceService.isPermitted(namespace.getQualifier(), user));
        assertFalse(namespaceService.isPermitted(namespace.getQualifier(), user1));
    }

    @Test
    public void testAdditionalUserIsPermitted() {
        PrincipalUser user = userService.findAdminUser();
        PrincipalUser user1 = new PrincipalUser(admin, "abc3", "abc3@xyz.com");
        Namespace namespace = new Namespace("namespace6", user);

        namespace = namespaceService.createNamespace(namespace);
        namespace.getUsers().add(user1);
        namespace = namespaceService.updateNamespace(namespace);
        assertTrue(namespaceService.isPermitted(namespace.getQualifier(), user));
        assertTrue(namespaceService.isPermitted(namespace.getQualifier(), user1));
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

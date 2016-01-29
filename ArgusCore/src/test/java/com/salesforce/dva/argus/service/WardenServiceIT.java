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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.IntegrationTest;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.WardenService.SubSystem;

@Category(IntegrationTest.class)
public class WardenServiceIT extends AbstractTest {

    private UserService _userService;
    private WardenService _wardenService;
    private AnnotationService _annotationService;

    @Before
    @Override
    public void setUp() {
        super.setUp();
        _userService = system.getServiceFactory().getUserService();
        _wardenService = system.getServiceFactory().getWardenService();
        _annotationService = system.getServiceFactory().getAnnotationService();

        PrincipalUser user = _userService.findUserByUsername("bhinav.sura");

        if (user == null) {
            user = new PrincipalUser("bhinav.sura", "bhinav.sura@salesforce.com");
            user = _userService.updateUser(user);
        }
        _wardenService.reinstateUser(user, SubSystem.API);
        _wardenService.reinstateUser(user, SubSystem.POSTING);

        SubSystem[] subSystems = SubSystem.values();

        for (SubSystem ss : subSystems) {
            Map<Integer, Long> levels = new HashMap<>();

            levels.put(1, 10 * 60 * 1000L);
            levels.put(2, 30 * 60 * 1000L);
            levels.put(3, 60 * 60 * 1000L);
            levels.put(4, 10 * 60 * 60 * 1000L);
            levels.put(5, 24 * 60 * 60 * 1000L);
            _wardenService.updateSuspensionLevels(ss, levels);
        }
    }

    @Test
    public void testReinstateUserFromPermanentSuspension() throws InterruptedException {
        PrincipalUser user = _userService.findUserByUsername("bhinav.sura");
        long marker = System.currentTimeMillis();

        Thread.sleep(2000);
        for (int i = 0; i < 15; i++) {
            int index = random.nextInt(SubSystem.values().length);
            SubSystem subSystem = SubSystem.values()[index];

            _wardenService.suspendUser(user, subSystem);
        }
        _wardenService.reinstateUser(user, SubSystem.POSTING);
        _wardenService.assertSubSystemUsePermitted(user, SubSystem.POSTING);
        Thread.sleep(2000);

        List<Annotation> annotations = _annotationService.getAnnotations("-3s:argus.core:triggers.warden:WARDEN:bhinav.sura");

        assertFalse(annotations.isEmpty());

        Annotation annotation = annotations.get(annotations.size() - 1);

        assertTrue(annotation.getTimestamp() > marker);
    }

    @Test
    public void testReinstatedUserFromTemporarySuspension() throws InterruptedException {
        PrincipalUser user = _userService.findUserByUsername("bhinav.sura");

        Thread.sleep(2000);
        _wardenService.suspendUser(user, SubSystem.API);
        _wardenService.reinstateUser(user, SubSystem.API);
        _wardenService.assertSubSystemUsePermitted(user, SubSystem.API);
        Thread.sleep(2000);

        List<Annotation> annotations = _annotationService.getAnnotations("-3s:argus.core:triggers.warden:WARDEN:bhinav.sura");

        if (annotations.size() == 0) {
            assertTrue(true);
        } else {
            Annotation annotation = annotations.get(annotations.size() - 1);

            if (System.currentTimeMillis() - annotation.getTimestamp() < 3000) {
                assertTrue(false);
            } else {
                assertTrue(true);
            }
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

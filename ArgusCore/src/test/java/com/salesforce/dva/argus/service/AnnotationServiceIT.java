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
import com.salesforce.dva.argus.IntegrationTest;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.PrincipalUser;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
public class AnnotationServiceIT extends AbstractTest {

    private UserService uService;
    private AnnotationService aService;

    @Before
    @Override
    public void setUp() {
        super.setUp();
        aService = system.getServiceFactory().getAnnotationService();
        uService = system.getServiceFactory().getUserService();
    }

    @After
    @Override
    public void tearDown() {
        super.tearDown();
        if (aService != null) {
            aService.dispose();
        }
    }

    @Test
    public void testUserFiltering() throws InterruptedException {
        String source = "test";
        String type = "test";
        String scope = createRandomName();
        String metric = createRandomName();
        Map<Annotation, PrincipalUser> map = new HashMap<>();

        for (int i = 0; i < 3; i++) {
            Long timestamp = System.currentTimeMillis() - (10000L * i);
            String id = createRandomName();
            Annotation annotation = new Annotation(source, id, type, scope, metric, timestamp);
            PrincipalUser user = uService.updateUser(new PrincipalUser(uService.findAdminUser(), "User" + i, "email" + i));

            map.put(annotation, user);
        }
        aService.updateAnnotations(map);
        Thread.sleep(2500);
        for (int i = 0; i < 3; i++) {
            PrincipalUser user = uService.findUserByUsername("User" + i);
            List<Annotation> annotations = aService.getAnnotations(System.currentTimeMillis() - 30000 + ":" + scope + ":" + metric + ":test:" +
                user.getUserName());

            assertEquals(1, annotations.size());
            assertTrue(map.keySet().containsAll(annotations));
            assertEquals("User" + i, annotations.get(0).getFields().get("user"));
        }
    }

    @Test
    public void testAlertAnnotation() throws InterruptedException {
        String source = "ARGUS-ALERTS";
        String type = "ALERT";
        String id = "ID";
        String scope = "s1.record_home.na1";
        String metric = "ept.min";
        Annotation annotation = new Annotation(source, id, type, scope, metric, System.currentTimeMillis());

        aService.updateAnnotation(uService.findAdminUser(), annotation);
        Thread.sleep(2500);

        List<Annotation> annotations = aService.getAnnotations("-100h:s1.record_home.na1:ept.min:ALERT:admin");

        assertNotNull(annotations);
    }

    @Test
    public void testGetMetricToAnnotate() {
        assertNotNull(Notification.getMetricToAnnotate("s1.record_home.na1:ept.min:avg"));
        assertNotNull(Notification.getMetricToAnnotate("s1.record_home.na1 :    ept.min :   avg"));
        assertNotNull(Notification.getMetricToAnnotate("s1.record_home.na1:ept.min{foo=bar}: avg"));
        assertNotNull(Notification.getMetricToAnnotate("s1.record_home.na1 :    ept.min {foo =  bar, baz= bah}: avg"));
        assertNotNull(Notification.getMetricToAnnotate("argus.jvm:file.descriptor.open{host=localhost}: avg"));
        assertNotNull(Notification.getMetricToAnnotate("argus.jvm:file.descriptor.open{ host    = localhost }: avg"));
        assertNull(Notification.getMetricToAnnotate("-100h:s1.record_home.na1:ept.min:ALERT:admin:avg"));
        assertNull(Notification.getMetricToAnnotate("s1.record_home.na1: ept.min: ALERT:admin:avg"));
        assertNull(Notification.getMetricToAnnotate("s1.record_home.na1 :    ept.min {foo = }:avg"));
        assertNull(Notification.getMetricToAnnotate("s1.record_home.na1 :    ept.min {foo = bar, = baz}:avg"));
        assertNull(Notification.getMetricToAnnotate("s1.record_home.na1 :    ept.min {foo = bar, = baz}"));
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

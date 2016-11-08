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

import com.salesforce.dva.argus.sdk.ArgusService.PutResult;
import com.salesforce.dva.argus.sdk.entity.Annotation;
import org.junit.Test;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.*;

public class AnnotationServiceTest extends AbstractTest {

    @Test
    public void testPutAnnotations() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AnnotationServiceTest.json"))) {
            AnnotationService annotationService = argusService.getAnnotationService();
            List<Annotation> annotations = Arrays.asList(new Annotation[] { _constructAnnotation() });
            PutResult result = annotationService.putAnnotations(annotations);

            assertEquals(_constructSuccessfulResult(annotations, 0), result);
        }
    }

    @Test
    public void testGetAnnotations() throws IOException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/AnnotationServiceTest.json"))) {
            AnnotationService annotationService = argusService.getAnnotationService();
            List<String> expressions = Arrays.asList(new String[] { "-1d:TestScope:TestMetric{TestTag=TagValue}:TestType" });
            List<Annotation> result = annotationService.getAnnotations(expressions);
            List<Annotation> expected = Arrays.asList(new Annotation[] { _constructAnnotation() });

            assertEquals(expected, result);
        }
    }

    private Annotation _constructAnnotation() {
        Annotation result = new Annotation();
        Map<String, String> fields = new TreeMap<>();
        Map<String, String> tags = new TreeMap<>();

        fields.put("TestField", "FieldValue");
        tags.put("TestTag", "TagValue");
        result.setId("TestID");
        result.setFields(fields);
        result.setMetric("TestMetric");
        result.setScope("TestScope");
        result.setSource("TestSource");
        result.setTags(tags);
        result.setTimestamp(1472282830936L);
        result.setType("TestType");
        return result;
    }

    private PutResult _constructSuccessfulResult(List<Annotation> annotations, int errorCount) {
        String failCount = Integer.toString(errorCount);
        String successCount = Integer.toString(annotations.size() - errorCount);
        List<String> errorMessages = new LinkedList<>();

        if (errorCount > 0) {
            errorMessages.add(failCount);
        }
        return new PutResult(successCount, failCount, errorMessages);
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

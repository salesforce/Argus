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
     
package com.salesforce.dva.argus.service.metric.transform;

import com.salesforce.dva.argus.entity.Metric;
import org.junit.Test;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;

public class IncludeTransformTest {

    private static final String TEST_SCOPE = "test-scope";
    private static final String TEST_METRIC = "test-metric";
    private static final String TEST_NAMESPACE = "test-namespace";
    
    private static final String TEST_TAG_1 = "tag1";
    private static final String TEST_TAG_2 = "tag2";
    private static final Map<String, String> TEST_TAGS = new HashMap<String, String>() {

            {
                put(TEST_TAG_1, "value1");
                put(TEST_TAG_2, "value2");
            }
        };

    private static final String TEST_BAD_STRING = "It is Mon again:(";
    private static final String TEST_INCLUDE_LITERAL = "Everyday is a holiday.";
    private static final String TEST_INCLUDE_REGEX = ".*test-metric[1-2].*";

    @Test(expected = UnsupportedOperationException.class)
    public void testIncludeTransformWithoutMetrics() {
        Transform includeTransform = new IncludeTransform();
        List<Metric> metrics = new ArrayList<Metric>();

        includeTransform.transform(null, metrics);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testIncludeTransformWithoutConstants() {
        Transform includeTransform = new IncludeTransform();
        List<Metric> metrics = new ArrayList<Metric>();

        includeTransform.transform(null, metrics);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIncludeTransformWithMoreThanOneConstant() {
        Transform includeTransform = new IncludeTransform();
        List<Metric> metrics = new ArrayList<Metric>();
        List<String> constants = new ArrayList<String>();

        constants.add(TEST_INCLUDE_LITERAL);
        constants.add(TEST_BAD_STRING);
        includeTransform.transform(null, metrics, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIncludeTransformWithEmptyRegex() {
        Transform includeTransform = new IncludeTransform();
        List<Metric> metrics = new ArrayList<Metric>();
        List<String> constants = new ArrayList<String>();

        constants.add(TEST_INCLUDE_LITERAL);
        constants.add(TEST_BAD_STRING);
        includeTransform.transform(null, metrics, constants);
    }

    @Test
    public void testIncludeTransformRegex() {
        Transform includeTransform = new IncludeTransform();
        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC + "1");

        //metric_1.setDisplayName(TEST_DISPLAYNAME_1);
        metric_1.setNamespace(TEST_NAMESPACE);
        metric_1.setTags(TEST_TAGS);

        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC + "2");

        //metric_2.setDisplayName(TEST_DISPLAYNAME_2);
        metric_2.setNamespace(TEST_NAMESPACE);
        metric_2.setTags(TEST_TAGS);

        Metric metric_3 = new Metric(TEST_SCOPE, TEST_METRIC + "3");

        //metric_3.setDisplayName(TEST_DISPLAYNAME_3);
        metric_3.setNamespace(TEST_NAMESPACE);
        metric_3.setTags(TEST_TAGS);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);
        metrics.add(metric_3);

        List<String> constants = new ArrayList<String>();

        constants.add(TEST_INCLUDE_REGEX);

        List<Metric> result = includeTransform.transform(null, metrics, constants);

        assertEquals(result.size(), 2);
        assertEquals("test-metric1", result.get(0).getMetric());
        assertEquals("test-metric2", result.get(1).getMetric());
    }

    @Test
    public void testIncludeTransformLiteral() {
        Transform includeTransform = new IncludeTransform();
        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);

        //metric_1.setDisplayName(TEST_DISPLAYNAME_1);
        metric_1.setNamespace(TEST_NAMESPACE);
        metric_1.setTags(TEST_TAGS);

        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);

        //metric_2.setDisplayName(TEST_DISPLAYNAME_2);
        metric_2.setNamespace(TEST_NAMESPACE);
        metric_2.setTags(TEST_TAGS);

        Metric metric_3 = new Metric(TEST_SCOPE, TEST_METRIC);

        //metric_3.setDisplayName(TEST_DISPLAYNAME_3);
        metric_3.setNamespace(TEST_NAMESPACE);
        metric_3.setTags(TEST_TAGS);

        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);
        metrics.add(metric_3);

        List<String> constants = new ArrayList<String>();

        constants.add(TEST_METRIC);

        List<Metric> result = includeTransform.transform(null, metrics, constants);

        assertEquals(result.size(), 0);
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

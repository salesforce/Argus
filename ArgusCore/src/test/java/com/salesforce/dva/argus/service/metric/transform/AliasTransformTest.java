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
import java.util.List;

import static org.junit.Assert.assertEquals;

public class AliasTransformTest {

    private static final String TEST_SCOPE = "test-scope";
    private static final String TEST_METRIC = "test-metric";
    private static final String TEST_METRIC_NAME_1 = "TGIF, Yeah!!!!!!";
    private static final String TEST_METRIC_NAME_2 = "Sat is awesome:)";
    private static final String TEST_METRIC_NAME_3 = "Sun is my big appetite day:(";
    private static final String TEST_BAD_STRING = "It is Mon again:(";
    private static final String TEST_TYPE_LITERAL = "literal";
    private static final String TEST_TYPE_REGEX = "regex";
    private static final String TEST_ALIAS_LITERAL = "Everyday is a holiday.";
    private static final String TEST_ALIAS_REGEX = "/^S(.*):[()]$/" + TEST_ALIAS_LITERAL + "/";

    @Test(expected = UnsupportedOperationException.class)
    public void testAliasTransformWithoutMetrics() {
        Transform aliasTransform = new AliasTransform();
        List<Metric> metrics = new ArrayList<Metric>();

        aliasTransform.transform(null, metrics);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAliasTransformWithoutConstants() {
        Transform aliasTransform = new AliasTransform();
        List<Metric> metrics = new ArrayList<Metric>();

        aliasTransform.transform(null, metrics);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAliasTransformWithOnlyOneConstant() {
        Transform aliasTransform = new AliasTransform();
        List<Metric> metrics = null;
        List<String> constants = new ArrayList<String>();

        constants.add(TEST_ALIAS_LITERAL);
        aliasTransform.transform(null, metrics, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAliasTransformWithInvalidType() {
        Transform aliasTransform = new AliasTransform();
        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(new Metric(TEST_SCOPE, TEST_METRIC));

        List<String> constants = new ArrayList<String>();

        constants.add(TEST_ALIAS_LITERAL);
        constants.add(TEST_BAD_STRING);
        aliasTransform.transform(null, metrics, constants);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAliasTransformWithInvalidRegex() {
        Transform aliasTransform = new AliasTransform();
        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(new Metric(TEST_SCOPE, TEST_METRIC));

        List<String> constants = new ArrayList<String>();

        constants.add(TEST_BAD_STRING);
        constants.add(TEST_ALIAS_REGEX);
        aliasTransform.transform(null, metrics, constants);
    }

    @Test
    public void testAliasTransformRegex() {
        Transform aliasTransform = new AliasTransform();
        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC_NAME_1);
        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC_NAME_2);
        Metric metric_3 = new Metric(TEST_SCOPE, TEST_METRIC_NAME_3);
        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);
        metrics.add(metric_3);

        List<String> constants = new ArrayList<String>();

        constants.add(TEST_ALIAS_REGEX);
        constants.add(TEST_TYPE_REGEX);

        List<Metric> result = aliasTransform.transform(null, metrics, constants);

        assertEquals(result.size(), 3);
        assertEquals(TEST_METRIC_NAME_1, result.get(0).getMetric());
        assertEquals(TEST_ALIAS_LITERAL, result.get(1).getMetric());
        assertEquals(TEST_ALIAS_LITERAL, result.get(2).getMetric());
    }

    @Test
    public void testAliasTransformLiteral() {
        Transform aliasTransform = new AliasTransform();
        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC_NAME_1);
        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC_NAME_2);
        Metric metric_3 = new Metric(TEST_SCOPE, TEST_METRIC_NAME_3);
        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);
        metrics.add(metric_3);

        List<String> constants = new ArrayList<String>();

        constants.add(TEST_ALIAS_LITERAL);
        constants.add(TEST_TYPE_LITERAL);

        List<Metric> result = aliasTransform.transform(null, metrics, constants);

        assertEquals(result.size(), 3);
        assertEquals(TEST_ALIAS_LITERAL, result.get(0).getMetric());
        assertEquals(TEST_ALIAS_LITERAL, result.get(1).getMetric());
        assertEquals(TEST_ALIAS_LITERAL, result.get(2).getMetric());
    }
    
    @Test
    public void testAliasTransformLiteralScope() {
        Transform aliasTransform = new AliasTransform();
        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC_NAME_1);
        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC_NAME_2);
        Metric metric_3 = new Metric(TEST_SCOPE, TEST_METRIC_NAME_3);
        List<Metric> metrics = new ArrayList<Metric>();

        metrics.add(metric_1);
        metrics.add(metric_2);
        metrics.add(metric_3);

        List<String> constants = new ArrayList<String>();

        constants.add("m");
        constants.add(TEST_TYPE_LITERAL);
        constants.add("s");
        constants.add(TEST_TYPE_LITERAL);

        List<Metric> result = aliasTransform.transform(null, metrics, constants);

        assertEquals(result.size(), 3);
        assertEquals("m", result.get(0).getMetric());
        assertEquals("m", result.get(1).getMetric());
        assertEquals("m", result.get(2).getMetric());
        
        assertEquals("s", result.get(0).getScope());
        assertEquals("s", result.get(1).getScope());
        assertEquals("s", result.get(2).getScope());
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

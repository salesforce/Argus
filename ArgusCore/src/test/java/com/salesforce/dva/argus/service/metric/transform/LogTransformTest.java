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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.*;

public class LogTransformTest {

    @Test(expected = IllegalArgumentException.class)
    public void testLogTransformWithoutMetrics() {
        List<Metric> metrics = null;

        (new MetricMappingTransform(new LogValueMapping())).transform(null, metrics);
    }

    @Test
    public void testLogTransformWithoutBaseForOneMetric() {
        Metric m1 = new Metric("test_scope", "test_metric");
        Map<Long, Double> dp = new HashMap<Long, Double>();

        dp.put(2L, 10.0);
        dp.put(4L, 100.0);
        dp.put(5L, 10000.0);
        m1.setDatapoints(dp);

        List<Metric> metrics = Arrays.asList(m1);
        List<Metric> result = (new MetricMappingTransform(new LogValueMapping())).transform(null, metrics);

        assertThat(result, equalTo(metrics));

        Map<Long, Double> expectedDps = new HashMap<Long, Double>();

        expectedDps.put(2L, 1.0);
        expectedDps.put(4L, 2.0);
        expectedDps.put(5L, 4.0);
        assertEquals(result.get(0).getDatapoints(), expectedDps);
    }

    @Test
    public void testLogTransformWithBaseForOneMetric() {
        Metric m1 = new Metric("test_scope", "test_metric");
        Map<Long, Double> dp = new HashMap<Long, Double>();

        dp.put(2L, 4.0);
        dp.put(4L, 8.0);
        dp.put(5L, 32.0);
        m1.setDatapoints(dp);

        List<Metric> metrics = Arrays.asList(m1);
        List<String> constants = new ArrayList<String>();

        constants.add("2");

        List<Metric> result = (new MetricMappingTransform(new LogValueMapping())).transform(null, metrics, constants);

        assertThat(result, equalTo(metrics));

        Map<Long, Double> expectedDps = new HashMap<Long, Double>();

        expectedDps.put(2L, 2.0);
        expectedDps.put(4L, 3.0);
        expectedDps.put(5L, 5.0);
        assertEquals(result.get(0).getDatapoints(), expectedDps);
    }

    @Test
    public void testLogWithoutBaseTransformForMultipleMetric() {
        Metric m1 = new Metric("test_scope", "test_metric");
        Map<Long, Double> dp_1 = new HashMap<Long, Double>();

        dp_1.put(2L, 10.0);
        dp_1.put(4L, 100.0);
        dp_1.put(5L, 10000.0);
        m1.setDatapoints(dp_1);

        Metric m2 = new Metric("test_scope", "test_metric");
        HashMap<Long, Double> dp_2 = new HashMap<Long, Double>();

        dp_2.put(2L, 10000.0);
        dp_2.put(4L, 10.0);
        dp_2.put(5L, 1.0);
        m2.setDatapoints(dp_2);

        List<Metric> metrics = Arrays.asList(m1, m2);
        List<Metric> result = (new MetricMappingTransform(new LogValueMapping())).transform(null, metrics);

        assertThat(result, equalTo(metrics));

        Map<Long, Double> expectedDps_1 = new HashMap<Long, Double>();

        expectedDps_1.put(2L, 1.0);
        expectedDps_1.put(4L, 2.0);
        expectedDps_1.put(5L, 4.0);

        Map<Long, Double> expectedDps_2 = new HashMap<Long, Double>();

        expectedDps_2.put(2L, 4.0);
        expectedDps_2.put(4L, 1.0);
        expectedDps_2.put(5L, 0.0);
        assertEquals(result.get(0).getDatapoints(), expectedDps_1);
        assertEquals(result.get(1).getDatapoints(), expectedDps_2);
    }

    @Test
    public void testLogWithBaseTransformForMultipleMetric() {
        Metric m1 = new Metric("test_scope", "test_metric");
        Map<Long, Double> dp_1 = new HashMap<Long, Double>();

        dp_1.put(2L, 4.0);
        dp_1.put(4L, 8.0);
        dp_1.put(5L, 32.0);
        m1.setDatapoints(dp_1);

        Metric m2 = new Metric("test_scope", "test_metric");
        HashMap<Long, Double> dp_2 = new HashMap<Long, Double>();

        dp_2.put(2L, 1024.0);
        dp_2.put(4L, 64.0);
        dp_2.put(5L, 16.0);
        m2.setDatapoints(dp_2);

        List<Metric> metrics = Arrays.asList(m1, m2);
        List<String> constants = new ArrayList<String>();

        constants.add("2");

        List<Metric> result = (new MetricMappingTransform(new LogValueMapping())).transform(null, metrics, constants);

        assertThat(result, equalTo(metrics));

        Map<Long, Double> expectedDps_1 = new HashMap<Long, Double>();

        expectedDps_1.put(2L, 2.0);
        expectedDps_1.put(4L, 3.0);
        expectedDps_1.put(5L, 5.0);

        Map<Long, Double> expectedDps_2 = new HashMap<Long, Double>();

        expectedDps_2.put(2L, 10.0);
        expectedDps_2.put(4L, 6.0);
        expectedDps_2.put(5L, 4.0);
        assertEquals(result.get(0).getDatapoints(), expectedDps_1);
        assertEquals(result.get(1).getDatapoints(), expectedDps_2);
    }

    @Test
    public void testLogTransformWithoutBaseForOneMetricHavingNull() {
        Metric m1 = new Metric("test_scope", "test_metric");
        Map<Long, Double> dp = new HashMap<Long, Double>();

        dp.put(2L, null);
        dp.put(4L, 100.0);
        dp.put(5L, 10000.0);
        m1.setDatapoints(dp);

        List<Metric> metrics = Arrays.asList(m1);
        List<Metric> result = (new MetricMappingTransform(new LogValueMapping())).transform(null, metrics);

        assertThat(result, equalTo(metrics));

        Map<Long, Double> expectedDps = new HashMap<Long, Double>();

        expectedDps.put(2L, Double.NEGATIVE_INFINITY);
        expectedDps.put(4L, 2.0);
        expectedDps.put(5L, 4.0);
        assertEquals(result.get(0).getDatapoints(), expectedDps);
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

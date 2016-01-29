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

public class DerivativeTransformTest {

    public static final String DEFAULT_METRIC_NAME = "result";

    @Test(expected = IllegalArgumentException.class)
    public void transform_shouldThrowIllegalArgumentExceptionWhenListIsNull() {
        Transform derivativeTransform = new MetricMappingTransform(new DerivativeValueMapping());

        derivativeTransform.transform((List<Metric>) null);
    }

    public void transform_shouldReturnEmptyListWhenListIsEmpty() {
        Transform derivativeTransform = new MetricMappingTransform(new DerivativeValueMapping());
        List<Metric> metrics = new ArrayList<Metric>();
        List<Metric> result = derivativeTransform.transform(metrics);

        assertThat(result, equalTo(metrics));
    }

    @Test
    public void transform_shouldReturnMetricWhenNoDataPoints() {
        Transform derivativeTransform = new MetricMappingTransform(new DerivativeValueMapping());
        Metric m1 = new Metric("test", "m1");
        List<Metric> metrics = Arrays.asList(m1);
        List<Metric> results = derivativeTransform.transform(metrics);

        assertEquals(results, metrics);
    }

    @Test
    public void transform_shouldReturnNullWhenOneDataPoint() {
        Transform derivativeTransform = new MetricMappingTransform(new DerivativeValueMapping());
        Metric m1 = new Metric("test", "m1");
        HashMap<Long, String> dps = new HashMap<Long, String>();

        dps.put(1L, "5");
        m1.setDatapoints(dps);

        List<Metric> metrics = Arrays.asList(m1);
        List<Metric> results = derivativeTransform.transform(metrics);

        assertThat(results, equalTo(metrics));

        Map<Long, String> expectedDps = new HashMap<Long, String>();

        assertEquals(metrics.get(0).getDatapoints(), expectedDps);
    }

    @Test
    public void transform_shouldReturnDerivativeNullThenNumberWhenTwoDataPoints() {
        Transform derivativeTransform = new MetricMappingTransform(new DerivativeValueMapping());
        Metric m1 = new Metric("test", "m1");
        HashMap<Long, String> dp = new HashMap<Long, String>();

        dp.put(1L, "5");
        dp.put(2L, "6");
        m1.setDatapoints(dp);

        List<Metric> metrics = Arrays.asList(m1);
        List<Metric> results = derivativeTransform.transform(metrics);

        assertThat(results, equalTo(metrics));

        Map<Long, String> expectedDps = new HashMap<Long, String>();

        expectedDps.put(2L, "1.0");
        assertEquals(metrics.get(0).getDatapoints(), expectedDps);
    }

    @Test
    public void transform_shouldReturnDerivativeNullThenTwoNumbersWhenThreeDataPoints() {
        Transform derivativeTransform = new MetricMappingTransform(new DerivativeValueMapping());
        Metric m1 = new Metric("test", "m1");
        HashMap<Long, String> dp = new HashMap<Long, String>();

        dp.put(1L, "5");
        dp.put(2L, "6");
        dp.put(3L, "8");
        m1.setDatapoints(dp);

        List<Metric> metrics = Arrays.asList(m1);
        List<Metric> results = derivativeTransform.transform(metrics);

        assertThat(results, equalTo(metrics));

        Map<Long, String> expectedDps = new HashMap<Long, String>();

        expectedDps.put(2L, "1.0");
        expectedDps.put(3L, "2.0");
        assertEquals(metrics.get(0).getDatapoints(), expectedDps);
    }

    @Test
    public void transform_shouldReturnDerivativeForTwoMetrics() {
        Transform derivativeTransform = new MetricMappingTransform(new DerivativeValueMapping());
        Metric m1 = new Metric("test", "m1");
        HashMap<Long, String> dp1 = new HashMap<Long, String>();

        dp1.put(Long.valueOf(1), "5");
        dp1.put(Long.valueOf(2), "6");
        m1.setDatapoints(dp1);

        Metric m2 = new Metric("test", "m1");
        HashMap<Long, String> dp2 = new HashMap<Long, String>();

        dp2.put(Long.valueOf(3), "5");
        dp2.put(Long.valueOf(4), "8");
        m2.setDatapoints(dp2);

        List<Metric> metrics = Arrays.asList(m1, m2);
        List<Metric> results = derivativeTransform.transform(metrics);

        assertEquals(results, metrics);

        Map<Long, String> expectedDps1 = new HashMap<Long, String>();

        expectedDps1.put(2L, "1.0");
        assertEquals(metrics.get(0).getDatapoints(), expectedDps1);
        assertEquals(results, metrics);

        Map<Long, String> expectedDps2 = new HashMap<Long, String>();

        expectedDps2.put(4L, "3.0");
        assertThat(metrics.get(1).getDatapoints(), equalTo(expectedDps2));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void transform_withConstants() {
        Transform derivativeTransform = new MetricMappingTransform(new DerivativeValueMapping());
        Metric m1 = new Metric("test", "m1");
        HashMap<Long, String> dp = new HashMap<Long, String>();

        dp.put(1L, "5");
        dp.put(2L, "6");
        dp.put(3L, "8");
        m1.setDatapoints(dp);

        List<Metric> metrics = Arrays.asList(m1);
        List<String> constants = new ArrayList<String>();

        derivativeTransform.transform(metrics, constants);
    }

    @Test
    public void transform_shouldReturnDerivativeNullThenTwoNumbersWhenThreeDataPointsHavingNull() {
        Transform derivativeTransform = new MetricMappingTransform(new DerivativeValueMapping());
        Metric m1 = new Metric("test", "m1");
        HashMap<Long, String> dp = new HashMap<Long, String>();

        dp.put(1L, null);
        dp.put(2L, "6");
        dp.put(3L, "8");
        m1.setDatapoints(dp);

        List<Metric> metrics = Arrays.asList(m1);
        List<Metric> results = derivativeTransform.transform(metrics);

        assertThat(results, equalTo(metrics));

        Map<Long, String> expectedDps = new HashMap<Long, String>();

        expectedDps.put(2L, "6.0");
        expectedDps.put(3L, "2.0");
        assertEquals(metrics.get(0).getDatapoints(), expectedDps);
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

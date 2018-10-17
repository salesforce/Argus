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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

public class IntegralTransformTest {

    public static final String DEFAULT_METRIC_NAME = "result";

    @Test
    public void test_shouldReturnIntegralForOneMetric() {
        Transform integralTransform = new MetricMappingTransform(new IntegralValueMapping());
        Metric m1 = new Metric("test_scope", "test_metric");
        Map<Long, Double> dp = new HashMap<Long, Double>();

        dp.put(2L, 3.0);
        dp.put(4L, 5.0);
        dp.put(5L, 2.2);
        m1.setDatapoints(dp);

        List<Metric> metrics = Arrays.asList(m1);
        List<Metric> result = integralTransform.transform(null, metrics);

        assertThat(result, equalTo(metrics));

        Map<Long, Double> expectedDps = new HashMap<Long, Double>();

        expectedDps.put(2L, 3.0);
        expectedDps.put(4L, 8.0);
        expectedDps.put(5L, 10.2);
        assertThat(result.get(0).getDatapoints(), equalTo(expectedDps));
    }

    @Test
    public void transform_shouldReturnIntegralForTwoMetrics() {
        Transform integralTransform = new MetricMappingTransform(new IntegralValueMapping());
        Metric m1 = new Metric("test_scope", "test_metric");
        HashMap<Long, Double> dp1 = new HashMap<Long, Double>();

        dp1.put(2L, 4.0);
        dp1.put(3L, 6.0);
        m1.setDatapoints(dp1);

        Metric m2 = new Metric("test_scope", "test_metric");
        HashMap<Long, Double> dp2 = new HashMap<Long, Double>();

        dp2.put(3L, 5.0);
        dp2.put(9L, 2.0);
        dp2.put(2L, 6.0);
        m2.setDatapoints(dp2);

        List<Metric> metrics = Arrays.asList(m1, m2);
        List<Metric> results = integralTransform.transform(null, metrics);

        assertThat(results, equalTo(metrics));

        Map<Long, Double> expectedDps1 = new HashMap<Long, Double>();

        expectedDps1.put(2L, 4.0);
        expectedDps1.put(3L, 10.0);
        assertThat(results.get(0).getDatapoints(), equalTo(expectedDps1));

        Map<Long, Double> expectedDps2 = new HashMap<Long, Double>();

        expectedDps2.put(2L, 6.0);
        expectedDps2.put(3L, 11.0);
        expectedDps2.put(9L, 13.0);
        assertThat(results.get(1).getDatapoints(), equalTo(expectedDps2));
    }

    @Test
    public void test_shouldReturnIntegralForOneMetricHavingNull() {
        Transform integralTransform = new MetricMappingTransform(new IntegralValueMapping());
        Metric m1 = new Metric("test_scope", "test_metric");
        Map<Long, Double> dp = new HashMap<Long, Double>();

        dp.put(2L, 3.0);
        dp.put(4L, 5.0);
        dp.put(5L, null);
        m1.setDatapoints(dp);

        List<Metric> metrics = Arrays.asList(m1);
        List<Metric> result = integralTransform.transform(null, metrics);

        assertThat(result, equalTo(metrics));

        Map<Long, Double> expectedDps = new HashMap<Long, Double>();

        expectedDps.put(2L, 3.0);
        expectedDps.put(4L, 8.0);
        expectedDps.put(5L, 8.0);
        assertThat(result.get(0).getDatapoints(), equalTo(expectedDps));
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

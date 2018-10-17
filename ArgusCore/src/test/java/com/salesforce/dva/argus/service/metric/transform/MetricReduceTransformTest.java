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
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class MetricReduceTransformTest {

    @Mock
    private ValueReducer mockValueReducer;

    @Test(expected = IllegalArgumentException.class)
    public void transform_shouldThrowExceptionWhenNullMetrics() {
        Mockito.when(mockValueReducer.name()).thenReturn("s");
        new MetricReducerTransform(mockValueReducer).transform(null, (List<Metric>) null);
    }

    @Test
    public void reduce_shouldUseDefaultMetricNameWhenCommonIsNull() {
        Mockito.when(mockValueReducer.name()).thenReturn("s");

        Metric m = new Metric("s", "m1");
        Metric m2 = new Metric("s", "m2");
        List<Metric> results = new MetricReducerTransform(mockValueReducer).transform(null, Arrays.asList(m, m2));
        List<Metric> expected = Arrays.asList(new Metric("s", TransformFactory.DEFAULT_METRIC_NAME));

        assertThat(results, equalTo(expected));
    }

    @Test
    public void reduce_shouldUseDistilledMetricNameWhenCommon() {
        Mockito.when(mockValueReducer.name()).thenReturn("s");

        Metric m = new Metric("s", "m1");
        Metric m2 = new Metric("s", "m1");
        List<Metric> results = new MetricReducerTransform(mockValueReducer).transform(null, Arrays.asList(m, m2));
        List<Metric> expected = Arrays.asList(new Metric("s", "m1"));

        assertThat(results, equalTo(expected));
    }

    @Test
    public void reduce_shouldUseNewScope() {
        Mockito.when(mockValueReducer.name()).thenReturn("new scope");

        Metric m = new Metric("s", "m");
        Metric m2 = new Metric("s1", "m");
        List<Metric> results = new MetricReducerTransform(mockValueReducer).transform(null, Arrays.asList(m, m2));
        List<Metric> expected = Arrays.asList(new Metric("new scope", "m"));

        assertThat(results, equalTo(expected));
    }
    
    @Test
    public void reduce_shouldUseDistilledScopeNameWhenCommon() {
        Metric m = new Metric("s", "m1");
        Metric m2 = new Metric("s", "m2");
        List<Metric> results = new MetricReducerTransform(mockValueReducer).transform(null, Arrays.asList(m, m2));
        List<Metric> expected = Arrays.asList(new Metric("s", TransformFactory.DEFAULT_METRIC_NAME));

        assertThat(results, equalTo(expected));
    }

    @Test
    public void reduce_shouldUseMetaDataFromSingleMetric() {
        Mockito.when(mockValueReducer.name()).thenReturn("s");

        Metric m = new Metric("s", "m");

        m.setDisplayName("dn");

        Map<String, String> tags = new HashMap<String, String>();

        tags.put("t1", "v1");
        m.setTags(tags);
        m.setUnits("u");

        List<Metric> expected = Arrays.asList(m);
        List<Metric> results = new MetricReducerTransform(mockValueReducer).transform(null, expected);

        assertThat(results, equalTo(expected));

        Metric result = results.get(0);

        assertThat(result.getDisplayName(), equalTo("dn"));
        assertThat(result.getMetric(), equalTo("m"));
        assertThat(result.getScope(), equalTo("s"));
        assertThat(result.getTags(), equalTo(tags));
        assertThat(result.getUnits(), equalTo("u"));
    }

    @Test
    public void reduce_shouldUseDatapointsFromOneMetric() {
        Mockito.when(mockValueReducer.name()).thenReturn("s");

        Metric m = new Metric("s", "m");
        Map<Long, Double> dps = new HashMap<Long, Double>();

        dps.put(1L, 1.0);
        dps.put(2L, 2.0);
        m.setDatapoints(dps);

        List<Metric> expected = Arrays.asList(m);
        List<Metric> results = new MetricReducerTransform(mockValueReducer).transform(null, expected);

        assertThat(results, equalTo(expected));
        verify(mockValueReducer).reduce(Arrays.asList(1.0));
        verify(mockValueReducer).reduce(Arrays.asList(2.0));

        Map<Long, Double> expectedDps = new HashMap<Long, Double>();

        expectedDps.put(1L, 0.0);
        expectedDps.put(2L, 0.0);
        assertThat(results.get(0).getDatapoints(), equalTo(expectedDps));
    }

    @Test
    public void reduce_shouldUseDatapointsFromOneMetricWhenValueNull() {
        Mockito.when(mockValueReducer.name()).thenReturn("s");

        Metric m = new Metric("s", "m");
        Map<Long, Double> dps = new HashMap<Long, Double>();

        dps.put(1L, null);
        dps.put(2L, 2.0);
        m.setDatapoints(dps);

        List<Metric> expected = Arrays.asList(m);
        List<Metric> results = new MetricReducerTransform(mockValueReducer).transform(null, expected);

        assertThat(results, equalTo(expected));

        List<Double> nullList = new ArrayList<>();

        nullList.add(null);
        verify(mockValueReducer).reduce(nullList);
        verify(mockValueReducer).reduce(Arrays.asList(2.0));

        Map<Long, Double> expectedDps = new HashMap<Long, Double>();

        expectedDps.put(1L, 0.0);
        expectedDps.put(2L, 0.0);
        assertThat(results.get(0).getDatapoints(), equalTo(expectedDps));
    }

    @Test
    public void reduce_shouldUseDatapointsFromTwoMetrics() {
        Mockito.when(mockValueReducer.name()).thenReturn("s");

        Metric m = new Metric("s", "m");
        Map<Long, Double> dps = new HashMap<Long, Double>();

        dps.put(1L, 1.0);
        dps.put(2L, 2.0);
        m.setDatapoints(dps);

        Metric m2 = new Metric("s", "m");

        dps = new HashMap<>();
        dps.put(1L, 3.0);
        dps.put(2L, 4.0);
        m2.setDatapoints(dps);

        List<Metric> results = new MetricReducerTransform(mockValueReducer).transform(null, Arrays.asList(m, m2));

        assertThat(results, equalTo(Arrays.asList(m)));
        verify(mockValueReducer).reduce(Arrays.asList(1.0, 3.0));
        verify(mockValueReducer).reduce(Arrays.asList(2.0, 4.0));

        Map<Long, Double> expectedDps = new HashMap<Long, Double>();

        expectedDps.put(1L, 0.0);
        expectedDps.put(2L, 0.0);
        assertThat(results.get(0).getDatapoints(), equalTo(expectedDps));
    }

    @Test
    public void transform_shouldFillGapsFromTwoMetrics() {
        Mockito.when(mockValueReducer.name()).thenReturn("s");

        Metric m = new Metric("s", "m");
        Map<Long, Double> dps = new HashMap<Long, Double>();

        dps.put(1L, 1.0);
        dps.put(3L, 2.0);
        m.setDatapoints(dps);

        Metric m2 = new Metric("s", "m");

        dps = new HashMap<>();
        dps.put(1L, 3.0);
        dps.put(2L, 4.0);
        dps.put(4L, 5.0);
        m2.setDatapoints(dps);

        List<Metric> results = new MetricReducerTransform(mockValueReducer).transform(null, Arrays.asList(m, m2));

        assertThat(results, equalTo(Arrays.asList(m)));
        verify(mockValueReducer).reduce(Arrays.asList(1.0, 3.0));
        verify(mockValueReducer).reduce(Arrays.asList(4.0));
        verify(mockValueReducer).reduce(Arrays.asList(2.0));
        verify(mockValueReducer).reduce(Arrays.asList(5.0));

        Map<Long, Double> expected = new HashMap<Long, Double>();

        expected.put(1L, 0.0);
        expected.put(2L, 0.0);
        expected.put(3L, 0.0);
        expected.put(4L, 0.0);
        assertThat(results.get(0).getDatapoints(), equalTo(expected));
    }

    @Test
    public void transform_checkScopeName() {
        Mockito.when(mockValueReducer.name()).thenReturn("s");
        assertEquals("s", new MetricReducerTransform(mockValueReducer).getResultScopeName());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void transform_ShouldThrowExceptionWhenConstantSpecified() {
        List<Metric> metrics = new ArrayList<Metric>();
        List<String> constants = new ArrayList<String>();

        new MetricReducerTransform(mockValueReducer).transform(null, metrics, constants);
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

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
import java.util.Map;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class MetricDistillerTest {

    @Test
    public void shouldHaveDefaultValuesWhenNoMetrics() {
        MetricDistiller d = new MetricDistiller();

        d.distill(new ArrayList<Metric>());
        assertThat(d.getTags().isEmpty(), is(true));
        assertThat(d.getDisplayName(), is(nullValue()));
        assertThat(d.getMetric(), is(nullValue()));
        assertThat(d.getScope(), is(nullValue()));
        assertThat(d.getUnits(), is(nullValue()));
    }

    @Test
    public void shouldReturnScopeWhenCommon() {
        MetricDistiller d = new MetricDistiller();
        Metric m = new Metric("s", "m");
        Metric m2 = new Metric("s", "m");

        d.distill(Arrays.asList(m, m2));
        assertThat(d.getScope(), equalTo("s"));
    }

    @Test
    public void shouldReturnNullScopeWhenNotCommon() {
        MetricDistiller d = new MetricDistiller();
        Metric m = new Metric("s", "m");
        Metric m2 = new Metric("s2", "m");

        d.distill(Arrays.asList(m, m2));
        assertThat(d.getScope(), is(nullValue()));
    }

    @Test
    public void shouldReturnMetricWhenCommon() {
        MetricDistiller d = new MetricDistiller();
        Metric m = new Metric("s", "m");
        Metric m2 = new Metric("s", "m");

        d.distill(Arrays.asList(m, m2));
        assertThat(d.getMetric(), equalTo("m"));
    }

    @Test
    public void shouldReturnNullMetricWhenNotCommon() {
        MetricDistiller d = new MetricDistiller();
        Metric m = new Metric("s", "m");
        Metric m2 = new Metric("s", "m2");

        d.distill(Arrays.asList(m, m2));
        assertThat(d.getMetric(), is(nullValue()));
    }

    @Test
    public void shouldReturnDisplayNameWhenCommon() {
        MetricDistiller d = new MetricDistiller();
        Metric m = new Metric("s", "m");

        m.setDisplayName("d1");

        Metric m2 = new Metric("s", "m");

        m2.setDisplayName("d1");
        d.distill(Arrays.asList(m, m2));
        assertThat(d.getDisplayName(), equalTo("d1"));
    }

    @Test
    public void shouldReturnNullDisplayNameWhenNotCommon() {
        MetricDistiller d = new MetricDistiller();
        Metric m = new Metric("s", "m");

        m.setDisplayName("d1");

        Metric m2 = new Metric("s", "m");

        m.setDisplayName("d2");
        d.distill(Arrays.asList(m, m2));
        assertThat(d.getDisplayName(), is(nullValue()));
    }

    @Test
    public void shouldReturnUnitsWhenCommon() {
        MetricDistiller d = new MetricDistiller();
        Metric m = new Metric("s", "m");

        m.setUnits("u");

        Metric m2 = new Metric("s", "m");

        m2.setUnits("u");
        d.distill(Arrays.asList(m, m2));
        assertThat(d.getUnits(), equalTo("u"));
    }

    @Test
    public void shouldReturnNullUnitsWhenNotCommon() {
        MetricDistiller d = new MetricDistiller();
        Metric m = new Metric("s", "m");

        m.setUnits("u");

        Metric m2 = new Metric("s", "m");

        m2.setUnits("u2");
        d.distill(Arrays.asList(m, m2));
        assertThat(d.getUnits(), is(nullValue()));
    }

    @Test
    public void shouldReturnNoTagsWhenMetricHasNoTags() {
        MetricDistiller d = new MetricDistiller();
        Metric m = new Metric("s", "m");

        d.distill(Arrays.asList(m));
        assertThat(d.getTags().isEmpty(), is(true));
    }

    @Test
    public void shouldReturnOneTagWhenMetricHasOneTag() {
        MetricDistiller d = new MetricDistiller();
        Metric m = new Metric("s", "m");
        Map<String, String> tags = new HashMap<String, String>();

        tags.put("a", "1");
        m.setTags(tags);
        d.distill(Arrays.asList(m));
        assertThat(tags, equalTo(d.getTags()));
    }

    @Test
    public void shouldReturnTwoTagsWhenTwoMetricsHaveTwoTags() {
        MetricDistiller d = new MetricDistiller();
        Map<String, String> tags = new HashMap<String, String>();

        tags.put("a", "1");
        tags.put("b", "2");

        Metric m1 = new Metric("s", "m");

        m1.setTags(tags);

        Metric m2 = new Metric("s", "m");

        m2.setTags(tags);
        d.distill(Arrays.asList(m1, m2));
        assertThat(tags, equalTo(d.getTags()));
    }

    @Test
    public void shouldReturnOneTagWhenOneCommonAccrossTwoMetrics() {
        MetricDistiller d = new MetricDistiller();
        Map<String, String> tags = new HashMap<String, String>();

        tags.put("a", "1");
        tags.put("b", "2");

        Metric m1 = new Metric("s", "m");

        m1.setTags(tags);
        tags = new HashMap<String, String>();
        tags.put("a", "1");

        Metric m2 = new Metric("s", "m");

        m2.setTags(tags);
        d.distill(Arrays.asList(m1, m2));
        assertThat(tags, equalTo(d.getTags()));
    }

    @Test
    public void shouldReturnNoTagsWhenNoTagsCommonAccrossAllMetrics() {
        MetricDistiller d = new MetricDistiller();
        Map<String, String> tags = new HashMap<String, String>();

        tags.put("a", "1");
        tags.put("b", "2");

        Metric m1 = new Metric("s", "m");

        m1.setTags(tags);
        tags = new HashMap<String, String>();
        tags.put("a", "2");
        tags.put("b", "1");

        Metric m2 = new Metric("s", "m");

        m2.setTags(tags);
        d.distill(Arrays.asList(m1, m2));
        assertThat(d.getTags().isEmpty(), is(true));
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

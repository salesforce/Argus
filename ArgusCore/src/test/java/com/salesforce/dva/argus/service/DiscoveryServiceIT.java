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
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

@Category(IntegrationTest.class)
@Ignore
public class DiscoveryServiceIT extends AbstractTest {

    DiscoveryService _discoveryService;

    @Before
    @Override
    public void setUp() {
        super.setUp();
        _discoveryService = system.getServiceFactory().getDiscoveryService();
    }

    @Test
    public void testWildcardQueries() {
        List<Metric> metrics = new ArrayList<Metric>();

        for (int i = 0; i < 10; i++) {
            Metric m = _createNonRandomMetric(i);

            metrics.add(m);
        }
        system.getServiceFactory().getSchemaService().put(metrics);

        Map<String, String> tags = new HashMap<String, String>();

        tags.put("source", "unittest");

        MetricQuery query = new MetricQuery("scope[0|1]", "metric[0|1]", tags, 1L, 2L);
        List<MetricQuery> queries = _discoveryService.getMatchingQueries(query);

        assertTrue(_checkIfEqual(new MetricQuery("scope0", "metric0", tags, 1L, 2L), queries.get(0)));
        assertTrue(_checkIfEqual(new MetricQuery("scope1", "metric1", tags, 1L, 2L), queries.get(1)));
    }

    @Test
    public void testWildcardQueriesNoMatch() {
        List<Metric> metrics = new ArrayList<Metric>();

        for (int i = 0; i < 10; i++) {
            Metric m = _createNonRandomMetric(i);

            metrics.add(m);
        }
        system.getServiceFactory().getSchemaService().put(metrics);

        Map<String, String> tags = new HashMap<String, String>();

        tags.put("source", "unittest");

        MetricQuery query = new MetricQuery("sdfg*", "ymdasdf*", tags, 1L, 2L);
        List<MetricQuery> queries = _discoveryService.getMatchingQueries(query);

        assertTrue(queries.size() == 0);
    }

    @Test
    public void testNoWildcardQuery() {
        Map<String, String> tags = new HashMap<String, String>();

        tags.put("recordType", "A");

        MetricQuery query = new MetricQuery("system", "runtime", null, 1L, 2L);
        List<MetricQuery> queries = _discoveryService.getMatchingQueries(query);

        assertTrue(queries.size() == 1);
        assertTrue(_checkIfEqual(query, queries.get(0)));
    }

    private boolean _checkIfEqual(MetricQuery metricQuery1, MetricQuery metricQuery2) {
        if (metricQuery1 == null) {
            return metricQuery2 == null;
        }
        if (!metricQuery1.getScope().equals(metricQuery2.getScope())) {
            return false;
        }
        if (!metricQuery1.getMetric().equals(metricQuery2.getMetric())) {
            return false;
        }
        if (metricQuery1.getNamespace() == null) {
            if (metricQuery2.getNamespace() != null) {
                return false;
            }
        } else if (!metricQuery1.getNamespace().equals(metricQuery2.getNamespace())) {
            return false;
        }
        return metricQuery1.getTags().equals(metricQuery2.getTags());
    }

    private Metric _createNonRandomMetric(int i) {
        Metric m = new Metric("scope" + i, "metric" + i);

        m.setTags(tags);
        return m;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

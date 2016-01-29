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
     
package com.salesforce.dva.argus.service.metric;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.IntegrationTest;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.service.tsdb.MetricQuery.Aggregator;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.*;

@Category(IntegrationTest.class)
public class MetricServiceIT extends AbstractTest {

    @Test
    public void testGetMetricsWithOffset() throws InterruptedException {
        MetricService metricService = system.getServiceFactory().getMetricService();
        TSDBService tsdbService = system.getServiceFactory().getTSDBService();

        try {
            Long currentTime = System.currentTimeMillis();
            Map<Long, String> datapoints = new TreeMap<Long, String>();

            datapoints.put(currentTime - 15000000, "1");
            datapoints.put(currentTime - 14000000, "2");
            datapoints.put(currentTime - 13000000, "3");
            datapoints.put(currentTime - 12000000, "4");
            datapoints.put(currentTime - 11000000, "5");

            Metric m = new Metric("scope-test-offset", "metric-test-offset");

            m.setDatapoints(datapoints);
            tsdbService.putMetrics(Arrays.asList(new Metric[] { m }));
            Thread.sleep(5 * 1000);

            List<Metric> metrics = metricService.getMetrics((currentTime - 10000000) +
                MessageFormat.format(":{0}:{1}:avg", m.getScope(), m.getMetric()), 0);

            assertTrue(metrics.size() == 0 || metrics.get(0).getDatapoints().size() == 0);
            metrics = metricService.getMetrics((currentTime - 10000000) + MessageFormat.format(":{0}:{1}:avg", m.getScope(), m.getMetric()),
                -10000000);
            assertTrue(_datapointsBetween(metrics.get(0).getDatapoints(), currentTime - 20000000, System.currentTimeMillis() - 10000000));
        } finally {
            metricService.dispose();
            tsdbService.dispose();
        }
    }

    @Test
    public void testGetQueries() {
        MetricService metricService = system.getServiceFactory().getMetricService();
        MetricQuery query = metricService.getQueries("1000:2000:scope:metric{tagk=tagv}:avg:15m-avg").get(0);

        assertEquals(Long.valueOf(1000), query.getStartTimestamp());
        assertEquals(Long.valueOf(2000), query.getEndTimestamp());
        assertEquals("scope", query.getScope());
        assertEquals("metric", query.getMetric());
        assertEquals(Aggregator.AVG, query.getAggregator());
        assertEquals(Aggregator.AVG, query.getDownsampler());
    }

    @Test
    public void testGetQueriesWithFunction() {
        MetricService metricService = system.getServiceFactory().getMetricService();
        List<MetricQuery> queries = metricService.getQueries(
            "SUM(1000:2000:scope:metric{tagk=tagv}:avg:15m-avg, 1000:2000:scope:metric{tagk=tagv}:avg:15m-avg)");

        assertEquals(2, queries.size());
    }

    private boolean _datapointsBetween(Map<Long, String> datapoints, long low, long high) {
        for (Long timestamp : datapoints.keySet()) {
            if (timestamp < low || timestamp > high) {
                return false;
            }
        }
        return true;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

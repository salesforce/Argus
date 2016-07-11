package com.salesforce.dva.argus.service.metric;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.IntegrationTest;
import com.salesforce.dva.argus.entity.AsyncBatchedMetricQuery;
import com.salesforce.dva.argus.entity.BatchMetricQuery;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.BatchService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.TSDBService;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.text.MessageFormat;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
public class AsyncMetricServiceIT extends AbstractTest {

    @Test
    public void testQueueAndProcessAsyncMetric() throws InterruptedException {
        MetricService metricService = system.getServiceFactory().getMetricService();
        BatchService batchService = system.getServiceFactory().getBatchService();
        TSDBService tsdbService = system.getServiceFactory().getTSDBService();

        try {
            // Setup
            Long currentTime = System.currentTimeMillis();
            Map<Long, String> datapoints = new TreeMap<Long, String>();

            datapoints.put(currentTime - 15000000, "1");
            datapoints.put(currentTime - 14000000, "2");
            datapoints.put(currentTime - 13000000, "3");
            datapoints.put(currentTime - 12000000, "4");
            datapoints.put(currentTime - 11000000, "5");

            Metric m = new Metric("scope-test-async", "metric-test-async");

            m.setDatapoints(datapoints);
            tsdbService.putMetrics(Arrays.asList(new Metric[] { m }));

            String expectedExpression = (currentTime - 10000000) + MessageFormat.format(":{0}:{1}:avg", m.getScope(), m.getMetric());
            int expectedTtl = 20;
            String expectedOwnerName = "ownerName-test-async";
            Thread.sleep(3 * 1000);

            List<String> expressions = new ArrayList<>(1);
            expressions.add(expectedExpression);

            // Start async metric pipeline
            String batchId = metricService.getAsyncMetrics(expressions, -10000000, expectedTtl, expectedOwnerName);

            batchService.executeNextQuery(3000);

            BatchMetricQuery result = batchService.findBatchById(batchId);
            assertEquals(result.getStatus().toString(), BatchMetricQuery.Status.DONE.toString());
            assertEquals(result.getTtl(), expectedTtl);
            assertEquals(result.getOwnerName(), expectedOwnerName);

            List<AsyncBatchedMetricQuery> queries = result.getQueries();
            assertEquals(queries.size(), 1);
            AsyncBatchedMetricQuery actualQuery = queries.get(0);

            Map<Long, String> resultDatapoints = actualQuery.getResult().getDatapoints();

            assertTrue(_datapointsBetween(resultDatapoints, currentTime - 20000000, System.currentTimeMillis() - 10000000));
        } finally {
            metricService.dispose();
            batchService.dispose();
            tsdbService.dispose();
        }
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

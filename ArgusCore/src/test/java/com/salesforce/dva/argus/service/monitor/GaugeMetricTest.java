package com.salesforce.dva.argus.service.monitor;

import com.google.common.collect.ImmutableMap;
import com.salesforce.dva.argus.entity.Metric;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;

public class GaugeMetricTest {
    private static final double DOUBLE_COMPARISON_MAX_DELTA = 0.001;
    private static final String SCOPE = "test.scope";
    private static final String METRIC_NAME = "test.name";
    private static final Map<String, String> TAGS = ImmutableMap.of("host", "localhost");
    private GaugeMetric gm;

    @Before
    public void setUp() {
        final Metric m = new Metric(SCOPE, METRIC_NAME);
        m.setTags(TAGS);
        gm = new GaugeMetric(m);
    }

    @Test
    public void getObjectName_test() {
        assertEquals("ArgusMetrics:type=Gauge,scope=test.scope,metric=test.name,host=localhost", gm.getObjectName());
    }

    @Test
    public void addValue_sumThenResetValue_testParallelAdds() throws Exception {
        final CountDownLatch gate = new CountDownLatch(1);
        final int workerCount = 3;
        final int iterations = 100;
        final Thread[] workers = new Thread[workerCount];

        for (int i = 0; i < workers.length; i++) {
            Thread thread = new Thread(new Runnable() {

                @Override
                public void run() {
                    try {
                        gate.await();
                        for (int j = 0; j < iterations; j++) {
                            gm.addValue(1.0);
                        }
                    } catch (InterruptedException ex) {
                        org.junit.Assert.fail("This should never happen.");
                    }
                }
            });

            thread.setDaemon(true);
            thread.start();
            workers[i] = thread;
        }
        gate.countDown();
        for (Thread worker : workers) {
            worker.join(1500);
        }

        final double expectedGaugeValue = workerCount * iterations;
        assertEquals(0.0, gm.getValue(), DOUBLE_COMPARISON_MAX_DELTA);
        assertEquals(expectedGaugeValue, gm.getCurrentGaugeAdderValue(), DOUBLE_COMPARISON_MAX_DELTA);

        assertEquals(expectedGaugeValue, gm.computeNewGaugeValueAndResetGaugeAdder(), DOUBLE_COMPARISON_MAX_DELTA);

        assertEquals(expectedGaugeValue, gm.getValue(), DOUBLE_COMPARISON_MAX_DELTA);
        assertEquals(0.0, gm.getCurrentGaugeAdderValue(), DOUBLE_COMPARISON_MAX_DELTA);

        final int moreIterations = 10;
        final double delta = 5.0;
        for (int i = 0; i < moreIterations; i++) {
            gm.addValue(delta);
        }
        final double expectedNewGaugeValue = delta * moreIterations;
        assertEquals(expectedGaugeValue, gm.getValue(), DOUBLE_COMPARISON_MAX_DELTA);
        assertEquals(expectedNewGaugeValue, gm.getCurrentGaugeAdderValue(), DOUBLE_COMPARISON_MAX_DELTA);

        assertEquals(expectedNewGaugeValue, gm.computeNewGaugeValueAndResetGaugeAdder(), DOUBLE_COMPARISON_MAX_DELTA);

        assertEquals(expectedNewGaugeValue, gm.getValue(), DOUBLE_COMPARISON_MAX_DELTA);
        assertEquals(0.0, gm.getCurrentGaugeAdderValue(), DOUBLE_COMPARISON_MAX_DELTA);
    }

    @Test
    public void setValue_test() {
        final int iterations = 10;
        final double delta = 5.0;
        for (int i = 0; i < iterations; i++) {
            gm.addValue(delta);
        }
        final double expectedGaugeValue = delta * iterations;
        assertEquals(0.0, gm.getValue(), DOUBLE_COMPARISON_MAX_DELTA);
        assertEquals(expectedGaugeValue, gm.getCurrentGaugeAdderValue(), DOUBLE_COMPARISON_MAX_DELTA);

        final double newValue = 78.6;
        gm.setValue(newValue);

        assertEquals(0.0, gm.getValue(), DOUBLE_COMPARISON_MAX_DELTA);
        assertEquals(newValue, gm.getCurrentGaugeAdderValue(), DOUBLE_COMPARISON_MAX_DELTA);
    }
}

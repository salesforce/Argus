package com.salesforce.dva.argus.service.monitor;

import com.google.common.collect.ImmutableMap;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.MonitorService;
import org.junit.Before;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;

public class CounterMetricTest {
    private static final double DOUBLE_COMPARISON_MAX_DELTA = 0.001;
    private static final MonitorService.Counter COUNTER = MonitorService.Counter.ALERTS_EVALUATED;
    private static final String SCOPE = COUNTER.getScope();
    private static final String METRIC_NAME = COUNTER.getMetric();
    private static final Map<String, String> TAGS = ImmutableMap.of("host", "localhost");
    private CounterMetric cm;

    @Before
    public void setUp() {
        final Metric m = new Metric(SCOPE, METRIC_NAME);
        m.setTags(TAGS);
        cm = new CounterMetric(m, COUNTER);
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
                            cm.addValue(1.0);
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

        final double expectedCounterValue = workerCount * iterations;
        assertEquals(expectedCounterValue, cm.getValue(), DOUBLE_COMPARISON_MAX_DELTA);
        assertEquals(expectedCounterValue, cm.getCurrentGaugeAdderValue(), DOUBLE_COMPARISON_MAX_DELTA);

        assertEquals(expectedCounterValue, cm.computeNewGaugeValueAndResetGaugeAdder(), DOUBLE_COMPARISON_MAX_DELTA);

        assertEquals(expectedCounterValue, cm.getValue(), DOUBLE_COMPARISON_MAX_DELTA);
        assertEquals(0.0, cm.getCurrentGaugeAdderValue(), DOUBLE_COMPARISON_MAX_DELTA);

        final int moreIterations = 10;
        final double delta = 5.0;
        for (int i = 0; i < moreIterations; i++) {
            cm.addValue(delta);
        }
        final double expectedNewGaugeValue = delta * moreIterations;
        final double expectedNewCounterValue = expectedNewGaugeValue + expectedCounterValue;
        assertEquals(expectedNewCounterValue, cm.getValue(), DOUBLE_COMPARISON_MAX_DELTA);
        assertEquals(expectedNewGaugeValue, cm.getCurrentGaugeAdderValue(), DOUBLE_COMPARISON_MAX_DELTA);

        assertEquals(expectedNewGaugeValue, cm.computeNewGaugeValueAndResetGaugeAdder(), DOUBLE_COMPARISON_MAX_DELTA);

        assertEquals(expectedNewCounterValue, cm.getValue(), DOUBLE_COMPARISON_MAX_DELTA);
        assertEquals(0.0, cm.getCurrentGaugeAdderValue(), DOUBLE_COMPARISON_MAX_DELTA);
    }

    @Test
    public void setValue_test() {
        final int iterations = 10;
        final double delta = 5.0;
        for (int i = 0; i < iterations; i++) {
            cm.addValue(delta);
        }
        final double expectedCounterValue = delta * iterations;
        assertEquals(expectedCounterValue, cm.getValue(), DOUBLE_COMPARISON_MAX_DELTA);
        assertEquals(expectedCounterValue, cm.getCurrentGaugeAdderValue(), DOUBLE_COMPARISON_MAX_DELTA);

        final double newValue = 78.6;
        cm.setValue(newValue);

        assertEquals(newValue, cm.getValue(), DOUBLE_COMPARISON_MAX_DELTA);
        assertEquals(newValue, cm.getCurrentGaugeAdderValue(), DOUBLE_COMPARISON_MAX_DELTA);
    }
}

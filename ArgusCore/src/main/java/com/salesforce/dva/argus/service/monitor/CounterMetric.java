package com.salesforce.dva.argus.service.monitor;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.MonitorService;

import java.util.concurrent.atomic.DoubleAdder;

public class CounterMetric extends GaugeMetric implements MetricMXBean {
    private static final double DOUBLE_COMPARISON_MAX_DELTA = 0.001;
    /**
     * The adder inherited from GaugeMetric will be re-purposed to act as a monotonic adder, instead of being
     * a gauge adder which gets reset on a periodic basis to update the gauge. The previousResetCounterValue
     * field will keep track of the value of the adder when the previous gauge value was calculated.
     */
    protected volatile Double previousResetAdderValue;

    public CounterMetric(Metric metric, MonitorService.Counter counter) {
        this(metric, counter.getJMXMetricNameSuffix());
    }

    public CounterMetric(Metric metric, String jmxMetricNameSuffix) {
        super(metric, _createJMXObjectNameForMetric(metric, jmxMetricNameSuffix));
        previousResetAdderValue = 0.0;
    }

    /**
     * Here the resetting of the "gauge" adder is simulated. The new gauge value is calculated based on the
     * last value that the adder was "reset" and the current value of the monotonic adder.
     *
     * @return the new value of the gauge
     */
    @Override
    public Double computeNewGaugeValueAndResetGaugeAdder() {
        // do not reset adder, because it should be monotonically increasing
        double currentResetAdderValue = adder.doubleValue();
        value = getCurrentGaugeAdderValue(currentResetAdderValue);
        previousResetAdderValue = currentResetAdderValue;
        return value;
    }

    /**
     * @return value of the monotonic counter
     */
    @Override
    public Double getValue() {
        return adder.doubleValue();
    }

    /**
     * The current gauge value is calculated based on the last value that the adder was "reset" and the
     * current value of the monotonic counter.
     *
     * @return the current value of the gauge adder
     */
    @Override
    public Double getCurrentGaugeAdderValue() {
        return getCurrentGaugeAdderValue(getValue());
    }

    private Double getCurrentGaugeAdderValue(Double currentAdderValue) {
        if (compareGreaterThanOrEqual(currentAdderValue, previousResetAdderValue, DOUBLE_COMPARISON_MAX_DELTA)) {
            // new gauge value
            return currentAdderValue - previousResetAdderValue;
        } else {
            // overflow case if previous value is greater than current value
            return currentAdderValue + (Double.MAX_VALUE - previousResetAdderValue);
        }
    }

    private static boolean compareGreaterThanOrEqual(double x, double y, double delta) {
        return x > y || compareAlmostEqual(x, y, delta);
    }

    private static boolean compareAlmostEqual(double x, double y, double delta) {
        return x == y  || Math.abs(x - y) < delta;
    }
}

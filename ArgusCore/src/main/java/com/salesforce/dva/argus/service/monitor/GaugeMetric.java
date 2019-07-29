package com.salesforce.dva.argus.service.monitor;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.MonitorService;

import java.util.concurrent.atomic.DoubleAdder;

public class GaugeMetric implements MetricMXBean {

    /**
     * This is the adder used to keep track of the sum of deltas being added to the counter
     */
    protected final DoubleAdder adder;
    /**
     * This is the current value of the gauge
     */
    protected volatile Double value;
    /**
     * Metric associated with the counter value
     */
    private final Metric metric;
    /**
     * Name associated with the metric in JMX
     */
    private final String jmxName;

    public GaugeMetric(Metric metric) {
        this(metric, _createJMXObjectNameForMetric(metric, "", MetricType.GAUGE_METRIC));
    }

    protected GaugeMetric(Metric metric, String jmxName) {
        this.metric = metric;
        this.jmxName = jmxName;
        this.value = 0.0;
        this.adder = new DoubleAdder();
    }

    @Override
    public String getObjectName() {
        return jmxName;
    }

    /**
     * @return the current gauge value
     */
    @Override
    public Double getValue() {
        return value;
    }

    /**
     * @return the current gauge sum since the last time the gauge adder reset
     */
    public Double getCurrentGaugeAdderValue() {
        return adder.doubleValue();
    }

    /**
     * Add a new delta to the adder
     * @param delta
     * @return the current gauge sum since the last time the gauge adder reset
     */
    public Double addValue(Double delta) {
        adder.add(delta);
        return adder.doubleValue();
    }

    /**
     * The old value in the adder will be cleared and set to the new value.
     *
     * @param value the adder will be set to this value
     */
    public void setValue(Double value) {
        adder.reset();
        adder.add(value);
    }

    /**
     * Compute the new value of the gauge. Reset the gauge adder.
     *
     * @return the new value of the gauge
     */
    public Double computeNewGaugeValueAndResetGaugeAdder() {
        value = adder.sumThenReset();
        return value;
    }

    protected static String _createJMXObjectNameForMetric(Metric metric, String jmxMetricNameSuffix, MetricType metricType) {
        String objName = "ArgusMetrics:type=" + metricType.getName() + ",scope=" + metric.getScope() + ",metric=" + metric.getMetric() + jmxMetricNameSuffix;
        if (null != metric.getTags()) {
            for (String key : metric.getTags().keySet()) {
                objName = objName + "," + (key.equalsIgnoreCase("type") || key.equalsIgnoreCase("scope")
                        || key.equalsIgnoreCase("metric") ? "_" + key : key) + "=" + metric.getTags().get(key);
            }
        }
        return objName;
    }

    protected static String _createJMXObjectNameForMetric(Metric metric, MonitorService.Counter counter) {
        return _createJMXObjectNameForMetric(metric, counter.getJMXMetricNameSuffix(), MetricType.GAUGE_METRIC);
    }

}

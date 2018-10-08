package com.salesforce.dva.argus.service.monitor;

import java.lang.management.ManagementFactory;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.MutableGauge;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;

@Singleton
public class CounterMetricJMXExporter implements GaugeExporter {

	private final MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
	private final Logger _logger = LoggerFactory.getLogger(CounterMetricJMXExporter.class);
	
	private final Map<String, MutableGauge> _exportedMetrics = new ConcurrentHashMap<String, MutableGauge>();

	private String _createObjectNameForMetric(Metric metric) {
		String objName = "ArgusMetrics:type=Counter,scope=" + metric.getScope() + ",metric=" + metric.getMetric();
		if (null != metric.getTags()) {
			for (String key : metric.getTags().keySet()) {
				objName = objName + "," + key + "=" + metric.getTags().get(key);
			}
		}
		return objName;
	}
	
	@Inject
	public CounterMetricJMXExporter() {
		_logger.info("CounterMetricJMXExporter created.");
	}

	@Override
	public void exportGauge(Metric metric, Double value) {
		String objectName = this._createObjectNameForMetric(metric);

		// change code to grab lock on _exportedMetrics instead of _metrics
		// eventually, _exportedMetrics will take over.
		synchronized (_exportedMetrics) {
			if (!_exportedMetrics.containsKey(objectName)) {
				MutableGauge gauge = new MutableGauge(objectName);
				gauge.setValue(value);
				_exportedMetrics.put(objectName, gauge);
				try {
					_logger.warn("exportGauge(): !!!!!! come to register {} to JMX", objectName);
					mbeanServer.registerMBean(gauge, new ObjectName(objectName));
				} catch (InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException
						| MalformedObjectNameException e) {
					_logger.error("exportGauge(): failed to register internal counter {} to JMX {}", objectName, e);
				}
			} else {
				_exportedMetrics.get(objectName).setValue(value);
			}
		}
	}

}

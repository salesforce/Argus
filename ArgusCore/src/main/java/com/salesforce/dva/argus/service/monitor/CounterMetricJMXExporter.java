/*
 * Copyright (c) 2018, Salesforce.com, Inc.
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

import scala.collection.mutable.ArrayBuilder.ofBoolean;

/**
 * This is the implementation for @GaugeExporter to export metrics to JMX. It 
 * transform incoming metric object into @MutableGauge object, register it with
 * JMX if it is new, and set its value.  The MBeanServer will take care of
 * making it available through JMX port.
 * 
 * @author taozhang
 *
 */
@Singleton
public class CounterMetricJMXExporter implements GaugeExporter {

	private final MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();
	private final Logger _logger = LoggerFactory.getLogger(CounterMetricJMXExporter.class);
	
	private final Map<String, MutableGauge> _exportedMetrics = new ConcurrentHashMap<String, MutableGauge>();

	private String _createObjectNameForMetric(Metric metric) {
		String objName = "ArgusMetrics:type=Counter,scope=" + metric.getScope() + ",metric=" + metric.getMetric();
		if (null != metric.getTags()) {
			for (String key : metric.getTags().keySet()) {
				objName = objName + "," + (key.equalsIgnoreCase("type")? "_type":key) + "=" + metric.getTags().get(key);
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

		synchronized (_exportedMetrics) {
			_logger.debug("exportGauge(): +++ set {} to {}", objectName, value);
			if (!_exportedMetrics.containsKey(objectName)) {
				MutableGauge gauge = new MutableGauge(objectName);
				gauge.setValue(value);
				_exportedMetrics.put(objectName, gauge);
				try {
					_logger.debug("exportGauge(): !!!!!! come to register {} to JMX", objectName);
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

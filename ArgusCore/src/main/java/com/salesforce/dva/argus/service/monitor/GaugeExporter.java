package com.salesforce.dva.argus.service.monitor;

import com.salesforce.dva.argus.entity.Metric;

/**
 * This interface allow program to export internal counter metrics to 
 * external metric collecting / reporting system
 * 
 * @author taozhang
 *
 */
public interface GaugeExporter {
	/**
	 * export internal metric and its latest value through GaugeExporter.  System
	 * need the metric counter object to create corresponding object name
	 * for the exporter
	 * @param metric  The internal metric that will be exported
	 * @param value   The latest value of the metric.
	 */
	void exportGauge(Metric metric, Double value);

}

package com.salesforce.dva.argus.service.tsdb;

import java.util.ArrayList;
import java.util.List;

import com.salesforce.dva.argus.entity.Metric;

public class ResultSet {
	
	private List<Metric> _metrics = new ArrayList<Metric>();
	
	ResultSet(List<Metric> metrics) {
		_metrics = metrics;
	}
	
	public List<Metric> getMetrics() {
		return _metrics;
	}

}

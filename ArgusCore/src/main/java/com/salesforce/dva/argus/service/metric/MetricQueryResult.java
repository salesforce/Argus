package com.salesforce.dva.argus.service.metric;

import java.util.ArrayList;
import java.util.List;

import com.salesforce.dva.argus.entity.Metric;

/*
 * This class encapsulates the results of the metric query evaluation that are returned to the caller
 */
public class MetricQueryResult {

	private List<Metric> metrics = new ArrayList<Metric>();
	
	// Returns the range of time series that current query has expanded to.
	private String expandedTimeSeriesRange;
	
	// Returns the query time window of current query
	private String queryTimeWindow;
	
	private Integer numDiscoveryResults = 0;
	
	private Integer numDiscoveryQueries = 0;

	private Long queryTimeRangeInMillis = 0L;
	
	private Integer numTSDBResults = 0;

	public List<Metric> getMetricsList() {
		return metrics;
	}

	public void setMetricsList(List<Metric> metrics) {
		this.metrics = metrics;
	}

	public String getExpandedTimeSeriesRange() {
		return expandedTimeSeriesRange;
	}

	public void setExpandedTimeSeriesRange(String expandedTimeSeriesRange) {
		this.expandedTimeSeriesRange = expandedTimeSeriesRange;
	}

	public String getQueryTimeWindow() {
		return queryTimeWindow;
	}

	public void setQueryTimeWindow(String queryTimeWindow) {
		this.queryTimeWindow = queryTimeWindow;
	}

	public Integer getNumDiscoveryResults() {
		return numDiscoveryResults;
	}

	public void setNumDiscoveryResults(Integer numDiscoveryResults) {
		this.numDiscoveryResults = numDiscoveryResults;
	}

	public Integer getNumDiscoveryQueries() {
		return numDiscoveryQueries;
	}

	public void setNumDiscoveryQueries(Integer numDiscoveryQueries) {
		this.numDiscoveryQueries = numDiscoveryQueries;
	}
	
	public Long getQueryTimeRangeInMillis() {
		return queryTimeRangeInMillis;
	}

	public void setQueryTimeRangeInMillis(Long queryTimeRangeInMillis) {
		this.queryTimeRangeInMillis = queryTimeRangeInMillis;
	}

	public Integer getNumTSDBResults() {
		return numTSDBResults;
	}

	public void setNumTSDBResults(Integer numTSDBResults) {
		this.numTSDBResults = numTSDBResults;
	}
}

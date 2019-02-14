package com.salesforce.dva.argus.service.metric;

import java.util.ArrayList;
import java.util.List;

import com.salesforce.dva.argus.entity.Metric;

/*
 * This class encapsulates the results of the metric query evaluation that are returned to the caller
 */
public class MetricQueryResult {

	private List<Metric> metrics = new ArrayList<Metric>();
	
	// Returns the number of time series that current query has expanded to.
	private String expandedTimeSeriesRange = "unknown";
	
	// Returns the time window of current query based on total query time range
	private String queryTimeWindow =  "unknown";
	
	// Returns the time window of the current query based on the start time of the query
	private String queryStartTimeWindow = "unknown";
	
	private Integer numDiscoveryResults = 0;
	
	private Integer numDiscoveryQueries = 0;

	private Long queryTimeRangeInMillis = 0L;
	
	private Integer numTSDBResults = 0;
	
	private Long queryStartTimeMillis = System.currentTimeMillis();

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
	
	public String getQueryStartTimeWindow() {
		return queryStartTimeWindow;
	}

	public void setQueryStartTimeWindow(String queryStartTimeWindow) {
		this.queryStartTimeWindow = queryStartTimeWindow;
	}

	public Long getQueryStartTimeMillis() {
		return queryStartTimeMillis;
	}

	public void setQueryStartTimeMillis(Long queryStartTimeMillis) {
		this.queryStartTimeMillis = queryStartTimeMillis;
	}
}

package com.salesforce.dva.argus.service.metric;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.metric.transform.TransformFactory;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import org.glassfish.grizzly.utils.ArraySet;

/*
 * This class encapsulates the results of the metric query evaluation that are returned to the caller
 */
public class MetricQueryResult {

	private List<Metric> metrics = new ArrayList<>();
	
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

	private Set<TransformFactory.Function> transforms = new HashSet<>();

	private Set<MetricQuery> inboundMetricQueries = new HashSet<>();

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

	public boolean containsTransform() { return (transforms.size() > 0); }

	public void addTransform(TransformFactory.Function transform) { this.transforms.add(transform); }

	public List<TransformFactory.Function> getTransforms() { return new ArrayList<>(this.transforms); }

	public void addTransforms(List<TransformFactory.Function> transforms) { this.transforms.addAll(transforms); }

	public void removeTransform(TransformFactory.Function transform) { this.transforms.remove(transform); }


	public List<MetricQuery> getInboundMetricQueries() { return new ArrayList<>(inboundMetricQueries); }

	public void removeMetricQueries(MetricQuery metricQuery) { this.inboundMetricQueries.remove(metricQuery); }

	public void addInboundMetricQuery(MetricQuery metricQuery) { this.inboundMetricQueries.add(metricQuery); }

	public void addInboundMetricQueries(List<MetricQuery> metricQuery) { this.inboundMetricQueries.addAll(metricQuery); }
}

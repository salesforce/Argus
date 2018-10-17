package com.salesforce.dva.argus.util;

import java.util.HashMap;
import java.util.Map;

import com.salesforce.dva.argus.service.tsdb.MetricQuery.Aggregator;

public class TSDBQueryExpression {

	private Long startTimestamp = null;
    
    private Long endTimestamp = null;
    
    private String namespace = null;
    
    private String scope = null;
    
    private String metric = null;
    
    private Map<String, String> tags = new HashMap<String, String>();
    
    private Aggregator aggregator = null;
    
    private Aggregator downsampler = null;
    
    private Long downsamplingPeriod = null;
    
	public Long getStartTimestamp() {
 		return startTimestamp;
 	}

 	public void setStartTimestamp(Long startTimestamp) {
 		this.startTimestamp = startTimestamp;
 	}

 	public Long getEndTimestamp() {
 		return endTimestamp;
 	}

 	public void setEndTimestamp(Long endTimestamp) {
 		this.endTimestamp = endTimestamp;
 	}

 	public String getNamespace() {
 		return namespace;
 	}

 	public void setNamespace(String namespace) {
 		this.namespace = namespace;
 	}

 	public String getScope() {
 		return scope;
 	}

 	public void setScope(String scope) {
 		this.scope = scope;
 	}

 	public String getMetric() {
 		return metric;
 	}

 	public void setMetric(String metric) {
 		this.metric = metric;
 	}

 	public Map<String, String> getTags() {
 		return tags;
 	}

 	public void setTags(Map<String, String> tags) {
 		this.tags = tags;
 	}

 	public Aggregator getAggregator() {
 		return aggregator;
 	}

 	public void setAggregator(Aggregator aggregator) {
 		this.aggregator = aggregator;
 	}

 	public Aggregator getDownsampler() {
 		return downsampler;
 	}

 	public void setDownsampler(Aggregator downsampler) {
 		this.downsampler = downsampler;
 	}

 	public Long getDownsamplingPeriod() {
 		return downsamplingPeriod;
 	}

 	public void setDownsamplingPeriod(Long downsamplingPeriod) {
 		this.downsamplingPeriod = downsamplingPeriod;
 	}
}

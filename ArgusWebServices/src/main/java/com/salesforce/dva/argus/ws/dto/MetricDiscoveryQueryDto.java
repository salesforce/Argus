package com.salesforce.dva.argus.ws.dto;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.salesforce.dva.argus.entity.MetricSchemaRecord;

public class MetricDiscoveryQueryDto extends BaseDto{

	    private String namespace;
	    private String scope;
	    private String metric;
	    private String tagKey;
	    private String tagValue;
	    private int limit = 50;
	    private String type;
	    private MetricSchemaRecord scanStartSchemaRecord;
	    private int page = 1;
	    
	    private String keywordQuery;
	    private boolean isKeywordQueryNative;
	    
		public String getNamespace() {
			return namespace == null || namespace.length() == 0 ? "*" : namespace;
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
		
		public String getTagKey() {
			return tagKey == null || tagKey.length() == 0 ? "*" : tagKey;
		}
		
		@JsonProperty("tagk")
		public void setTagKey(String tagKey) {
			this.tagKey = tagKey;
		}
		
		public String getTagValue() {
			return tagValue == null || tagValue.length() == 0 ? "*" : tagValue;
		}
		
		@JsonProperty("tagv")
		public void setTagValue(String tagValue) {
			this.tagValue = tagValue;
		}
		
		public int getLimit() {
			return limit;
		}
		
		public void setLimit(int limit) {
			this.limit = limit;
		}
		
		public String getType() {
			return type;
		}
		
		public void setType(String type) {
			this.type = type;
		}
		
		public MetricSchemaRecord getScanStartSchemaRecord() {
			return scanStartSchemaRecord;
		}
		
		public void setScanStartMetricSchemaRecord(MetricSchemaRecord scanStartSchemaRecord) {
			this.scanStartSchemaRecord = scanStartSchemaRecord;
		}
		
		public int getPage() {
			return page;
		}

		public void setPage(int page) {
			this.page = page;
		}

		public String getKeywordQuery() {
			return keywordQuery;
		}

		public void setKeywordQuery(String keywordQuery) {
			this.keywordQuery = keywordQuery;
		}

		public boolean isKeywordQueryNative() {
			return isKeywordQueryNative;
		}

		public void setKeywordQueryNative(boolean isKeywordQueryNative) {
			this.isKeywordQueryNative = isKeywordQueryNative;
		}

		@Override
		public Object createExample() {
			MetricDiscoveryQueryDto result = new MetricDiscoveryQueryDto();
			result.setNamespace("namespace");
			result.setScope("scope*");
			result.setMetric("metric");
			result.setTagKey("tagKey");
			result.setTagValue("tagValue");
			result.setLimit(10);
			result.setType("scope");
			result.setScanStartMetricSchemaRecord(new MetricSchemaRecord("scope1", "metric", "tagKey", "tagValue", "namespace")); 
			
			MetricDiscoveryQueryDto anotherResult = new MetricDiscoveryQueryDto();
			anotherResult.setNamespace("namespace");
			anotherResult.setScope("scope");
			anotherResult.setMetric("metric");
			anotherResult.setTagKey("tagKey");
			anotherResult.setTagValue("tagValue");
			anotherResult.setLimit(10);
			anotherResult.setType("scope");
			anotherResult.setPage(1);
			
			MetricDiscoveryQueryDto yetAnotherResult = new MetricDiscoveryQueryDto();
			yetAnotherResult.setKeywordQuery("cpu time");
			yetAnotherResult.setLimit(10);
			yetAnotherResult.setPage(1);
			yetAnotherResult.setKeywordQueryNative(false);
			
			return Arrays.asList(result, anotherResult, yetAnotherResult);
		}
	    
	    
	
}

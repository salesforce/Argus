package com.salesforce.dva.argus.ws.dto;

import com.salesforce.dva.argus.entity.MetricSchemaRecord;

public class MetricDiscoveryQueryDto extends BaseDto{
	
	private static int DEFAULT_LIMIT=50;

	    private String namespace;
	    private String scope;
	    private String metric;
	    private String tagKey;
	    private String tagValue;
	    private int limit;
	    private String type;
	    private MetricSchemaRecord scanStartSchemaRecord;
		public String getNamespace() {
			return namespace==null || namespace.length()==0?"*":namespace;
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
			return tagKey==null || tagKey.length()==0?"*":tagKey;
		}
		public void setTagKey(String tagKey) {
			this.tagKey = tagKey;
		}
		public String getTagValue() {
			return tagValue==null || tagValue.length()==0?"*":tagValue;
		}
		public void setTagValue(String tagValue) {
			this.tagValue = tagValue;
		}
		public int getLimit() {
			return limit==0?DEFAULT_LIMIT:limit;
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
		@Override
		public Object createExample() {
			MetricDiscoveryQueryDto result=new MetricDiscoveryQueryDto();
			result.setNamespace("namespace");
			result.setScope("scope");
			result.setMetric("metric");
			result.setTagKey("tagKey");
			result.setTagValue("tagValue");
			result.setLimit(10);
			result.setType("type");
			result.setScanStartMetricSchemaRecord(new MetricSchemaRecord()); 
			return result;
		}
	    
	    
	
}

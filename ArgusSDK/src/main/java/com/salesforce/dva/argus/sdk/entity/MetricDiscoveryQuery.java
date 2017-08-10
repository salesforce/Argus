package com.salesforce.dva.argus.sdk.entity;

import java.io.Serializable;

@SuppressWarnings("serial")
public class MetricDiscoveryQuery implements Serializable{
	
	private static int DEFAULT_LIMIT = 50;

	    private String namespace;
	    private String scope;
	    private String metric;
	    private String tagk;
	    private String tagv;
	    private int limit;
	    private String type;
	    private MetricSchemaRecord scanStartSchemaRecord;
	    
	    public MetricDiscoveryQuery(){};
	    public MetricDiscoveryQuery(String namespace, String scope, String metric, String tagk, String tagv, int limit, 
	    		String type, MetricSchemaRecord scanStartSchemarecord){
	    	setNamespace(namespace);
	    	setScope(scope);
	    	setMetric(metric);
	    	setTagk(tagk);
	    	setTagv(tagv);
	    	setLimit(limit);
	    	setType(type);
	    	setScanStartMetricSchemaRecord(scanStartSchemarecord);
	    }
	    
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
		
		public String getTagk() {
			return tagk == null || tagk.length() == 0 ? "*" : tagk;
		}
		
		public void setTagk(String tagk) {
			this.tagk = tagk;
		}
		
		public String getTagv() {
			return tagv == null || tagv.length() ==0 ? "*" : tagv;
		}
		
		public void setTagv(String tagv) {
			this.tagv = tagv;
		}
		
		public int getLimit() {
			return limit == 0 ? DEFAULT_LIMIT : limit;
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
}

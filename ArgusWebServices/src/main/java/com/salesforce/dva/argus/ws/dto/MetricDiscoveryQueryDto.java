package com.salesforce.dva.argus.ws.dto;

import java.util.Arrays;

import com.salesforce.dva.argus.entity.MetricSchemaRecord;
import com.salesforce.dva.argus.service.SchemaService.RecordType;

public class MetricDiscoveryQueryDto extends BaseDto{

	    private String namespace;
	    private String scope;
	    private String metric;
	    private String tagk;
	    private String tagv;
	    private RecordType type;
	    private MetricSchemaRecord scanStartSchemaRecord;
	    private int page = 1;
	    private int limit = 50;
	    
	    private String keywordQuery;
	    
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
			return tagv == null || tagv.length() == 0 ? "*" : tagv;
		}
		
		public void setTagv(String tagv) {
			this.tagv = tagv;
		}
		
		public int getLimit() {
			return limit;
		}
		
		public void setLimit(int limit) {
			this.limit = limit;
		}
		
		public RecordType getType() {
			return type;
		}
		
		public void setType(RecordType type) {
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

		@Override
		public Object createExample() {
			MetricDiscoveryQueryDto query = new MetricDiscoveryQueryDto();
			query.setNamespace("namespace");
			query.setScope("scope*");
			query.setMetric("metric");
			query.setTagk("tagKey");
			query.setTagv("tagValue");
			query.setLimit(10);
			query.setType(RecordType.SCOPE);
			query.setScanStartMetricSchemaRecord(new MetricSchemaRecord("scope1", "metric", "tagKey", "tagValue", "namespace")); 
			
			MetricDiscoveryQueryDto anotherQuery = new MetricDiscoveryQueryDto();
			anotherQuery.setNamespace("namespace");
			anotherQuery.setScope("scope");
			anotherQuery.setMetric("metric");
			anotherQuery.setTagk("tagKey");
			anotherQuery.setTagv("tagValue");
			anotherQuery.setLimit(10);
			anotherQuery.setType(RecordType.SCOPE);
			anotherQuery.setPage(1);
			
			MetricDiscoveryQueryDto yetAnotherResult = new MetricDiscoveryQueryDto();
			yetAnotherResult.setKeywordQuery("cpu time");
			yetAnotherResult.setLimit(10);
			yetAnotherResult.setPage(1);
			
			return Arrays.asList(query, anotherQuery, yetAnotherResult);
		}
	    
	    
	
}

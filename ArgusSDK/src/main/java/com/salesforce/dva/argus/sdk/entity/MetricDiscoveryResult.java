package com.salesforce.dva.argus.sdk.entity;

import java.io.Serializable;
import java.util.List;

@SuppressWarnings("serial")
public class MetricDiscoveryResult implements Serializable{
/*
 * When type is not null, the data is a list of strings otherwise list of MetricSchemaRecords
 */
	private List<?extends Object> data;
	private MetricSchemaRecord lastSchemaRecord;
	
	public MetricDiscoveryResult(){};
	public MetricDiscoveryResult(List<?extends Object> data, MetricSchemaRecord lastSchemaRecord){
		this.data=data;
		this.lastSchemaRecord=lastSchemaRecord;
	}
	
	public List<? extends Object> getData() {
		return data;
	}
	public void setData(List<? extends Object> data) {
		this.data = data;
	}
	public MetricSchemaRecord getLastSchemaRecord() {
		return lastSchemaRecord;
	}
	public void setLastSchemaRecord(MetricSchemaRecord lastSchemaRecord) {
		this.lastSchemaRecord = lastSchemaRecord;
	}
	
	

}

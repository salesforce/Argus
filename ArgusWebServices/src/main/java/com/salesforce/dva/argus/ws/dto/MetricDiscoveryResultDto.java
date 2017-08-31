package com.salesforce.dva.argus.ws.dto;

import java.util.ArrayList;
import java.util.List;

import com.salesforce.dva.argus.entity.MetricSchemaRecord;

public class MetricDiscoveryResultDto extends BaseDto{

	private List<?extends Object> data;
	private MetricSchemaRecord lastSchemaRecord;
	
	public MetricDiscoveryResultDto(List<?extends Object> data, MetricSchemaRecord lastSchemaRecord){
		this.data = data;
		this.lastSchemaRecord = lastSchemaRecord;
	}
	
	@Override
	public Object createExample() {
		List<String> data=new ArrayList<String>();
		data.add("scope1");
		data.add("scope2");
		data.add("scope3");
		
		return null;
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

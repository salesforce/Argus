package com.salesforce.dva.argus.sdk.entity;

import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SuppressWarnings("serial")
public class MetricDiscoveryResult implements Serializable{
/*
 * When type is not null, the data is a list of strings otherwise list of MetricSchemaRecords
 */
	private List<? extends Object> data;
	private MetricSchemaRecord lastSchemaRecord;
	
	public MetricDiscoveryResult(){};
	
	public MetricDiscoveryResult(List<?extends Object> data, MetricSchemaRecord lastSchemaRecord){
		this.data=data;
		this.lastSchemaRecord = lastSchemaRecord;
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

	public static class Deserializer extends JsonDeserializer<MetricDiscoveryResult> {

		@Override
		public MetricDiscoveryResult deserialize(JsonParser jp, DeserializationContext ctxt)
				throws IOException, JsonProcessingException {
			
			boolean isStrResult = false;
			List<MetricSchemaRecord> data = new ArrayList<>();
			List<String> strObjects = new ArrayList<>();
			
			JsonNode rootNode = jp.getCodec().readTree(jp);
			
			JsonNode dataNode = rootNode.get("data");
			if(dataNode.isArray()) {
				for(JsonNode datumNode : dataNode) {
					if(datumNode.isContainerNode()) {
						MetricSchemaRecord record = new ObjectMapper().treeToValue(datumNode, MetricSchemaRecord.class);
						data.add(record);
					} else {
						isStrResult = true;
						strObjects.add(datumNode.asText());
					}
				}
			}
			
			JsonNode lastSchemaRecordNode = rootNode.get("lastSchemaRecord");
			MetricSchemaRecord lastSchemaRecord = new ObjectMapper().treeToValue(lastSchemaRecordNode, MetricSchemaRecord.class);
			return new MetricDiscoveryResult(isStrResult ? strObjects : data, lastSchemaRecord);
		}
		
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((data == null) ? 0 : data.hashCode());
		result = prime * result + ((lastSchemaRecord == null) ? 0 : lastSchemaRecord.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		MetricDiscoveryResult other = (MetricDiscoveryResult) obj;
		if (data == null) {
			if (other.data != null)
				return false;
		} else if (!data.equals(other.data))
			return false;
		if (lastSchemaRecord == null) {
			if (other.lastSchemaRecord != null)
				return false;
		} else if (!lastSchemaRecord.equals(other.lastSchemaRecord))
			return false;
		return true;
	}
	
	

}

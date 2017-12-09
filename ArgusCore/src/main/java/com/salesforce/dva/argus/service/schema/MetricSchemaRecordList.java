package com.salesforce.dva.argus.service.schema;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.codec.digest.DigestUtils;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.salesforce.dva.argus.entity.MetricSchemaRecord;
import com.salesforce.dva.argus.service.SchemaService.RecordType;

public class MetricSchemaRecordList {
	
	private List<MetricSchemaRecord> _records;
	private String _scrollID;
	
	public MetricSchemaRecordList(List<MetricSchemaRecord> records) {
		setRecords(records);
	}
	
	private MetricSchemaRecordList(List<MetricSchemaRecord> records, String scrollID) {
		setRecords(records);
		setScrollID(scrollID);
	}

	public List<MetricSchemaRecord> getRecords() {
		return _records;
	}

	public void setRecords(List<MetricSchemaRecord> records) {
		this._records = records;
	}
	
	public String getScrollID() {
		return _scrollID;
	}

	public void setScrollID(String scrollID) {
		this._scrollID = scrollID;
	}
	
	
	static class Serializer extends JsonSerializer<MetricSchemaRecordList> {

		@Override
		public void serialize(MetricSchemaRecordList list, JsonGenerator jgen, SerializerProvider provider)
				throws IOException, JsonProcessingException {
			
			ObjectMapper mapper = new ObjectMapper();
			mapper.setSerializationInclusion(Include.NON_NULL);
			List<MetricSchemaRecord> records = list.getRecords();
			for(MetricSchemaRecord record : records) {
				jgen.writeRaw("{ \"create\" : {\"_id\" : \"" + DigestUtils.md5Hex(MetricSchemaRecord.print(record)) + "\"}}");
				jgen.writeRaw(System.lineSeparator());
				
				jgen.writeRaw(mapper.writeValueAsString(record));
				jgen.writeRaw(System.lineSeparator());
			}
		}
    	
    }
	
	static class Deserializer extends JsonDeserializer<MetricSchemaRecordList> {

		@Override
		public MetricSchemaRecordList deserialize(JsonParser jp, DeserializationContext context)
				throws IOException, JsonProcessingException {
			
			String scrollID = null;
			List<MetricSchemaRecord> records = Collections.emptyList();
			
			JsonNode rootNode = jp.getCodec().readTree(jp);
			if(rootNode.has("_scroll_id")) {
				scrollID = rootNode.get("_scroll_id").asText();
			}
			JsonNode hits = rootNode.get("hits").get("hits");
			
			if(JsonNodeType.ARRAY.equals(hits.getNodeType())) {
				records = new ArrayList<>(hits.size());
				Iterator<JsonNode> iter = hits.elements();
				while(iter.hasNext()) {
					JsonNode hit = iter.next();
					JsonNode source = hit.get("_source");
					
					JsonNode namespaceNode = source.get(RecordType.NAMESPACE.getName());
					JsonNode scopeNode = source.get(RecordType.SCOPE.getName());
					JsonNode metricNode = source.get(RecordType.METRIC.getName());
					JsonNode tagkNode = source.get(RecordType.TAGK.getName());
					JsonNode tagvNode = source.get(RecordType.TAGV.getName());
					
					records.add(new MetricSchemaRecord(namespaceNode == null ? null : namespaceNode.asText(), 
													   scopeNode.asText(), 
													   metricNode.asText(), 
													   tagkNode == null ? null : tagkNode.asText(), 
													   tagvNode == null ? null : tagvNode.asText()));
				}
			}
			
			return new MetricSchemaRecordList(records, scrollID);
		}
		
	}
	
	static class AggDeserializer extends JsonDeserializer<List<String>> {

		@Override
		public List<String> deserialize(JsonParser jp, DeserializationContext context)
				throws IOException, JsonProcessingException {
			
			List<String> values = Collections.emptyList();
			
			JsonNode rootNode = jp.getCodec().readTree(jp);
			JsonNode buckets = rootNode.get("aggregations").get("distinct_values").get("buckets");
			
			if(JsonNodeType.ARRAY.equals(buckets.getNodeType())) {
				values = new ArrayList<>(buckets.size());
				Iterator<JsonNode> iter = buckets.elements();
				while(iter.hasNext()) {
					JsonNode bucket = iter.next();
					String value  = bucket.get("key").asText();
					values.add(value);
				}
			}
			
			return values;
		}
		
	}

}

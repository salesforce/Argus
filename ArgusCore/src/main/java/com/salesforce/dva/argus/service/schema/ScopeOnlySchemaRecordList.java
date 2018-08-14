package com.salesforce.dva.argus.service.schema;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

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
import com.salesforce.dva.argus.entity.ScopeOnlySchemaRecord;
import com.salesforce.dva.argus.service.SchemaService.RecordType;
import com.salesforce.dva.argus.service.schema.MetricSchemaRecordList.HashAlgorithm;

import net.openhft.hashing.LongHashFunction;

/**
 * Represents a list of scope names from discovery queries.
 * Internally it has a mapping from hash id of scope names to the actual scope names.
 *
 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
 */
public class ScopeOnlySchemaRecordList {
	
	private Map<String, ScopeOnlySchemaRecord> _idToSchemaRecordMap = new HashMap<>();
	private String _scrollID;

	public ScopeOnlySchemaRecordList(List<ScopeOnlySchemaRecord> records, String scrollID) {
		int count = 0;
		for(ScopeOnlySchemaRecord record : records) {
			_idToSchemaRecordMap.put(String.valueOf(count++), record);
		}
		setScrollID(scrollID);
	}
	
	public ScopeOnlySchemaRecordList(List<ScopeOnlySchemaRecord> records, HashAlgorithm algorithm) {
		for(ScopeOnlySchemaRecord record : records) {
			String id = null;
			String scopeOnly = record.getScope();
			if(HashAlgorithm.MD5.equals(algorithm)) {
				id = DigestUtils.md5Hex(scopeOnly);
			} else {
				id = String.valueOf(LongHashFunction.xx().hashChars(scopeOnly));
			}
			_idToSchemaRecordMap.put(id, record);
		}
	}
	
	public List<ScopeOnlySchemaRecord> getRecords() {
		return new ArrayList<>(_idToSchemaRecordMap.values());
	}
	
	public String getScrollID() {
		return _scrollID;
	}

	public void setScrollID(String scrollID) {
		this._scrollID = scrollID;
	}
	
	ScopeOnlySchemaRecord getRecord(String id) {
		return _idToSchemaRecordMap.get(id);
	}
	
	static class CreateSerializer extends JsonSerializer<ScopeOnlySchemaRecordList> {

		@Override
		public void serialize(ScopeOnlySchemaRecordList list, JsonGenerator jgen, SerializerProvider provider)
				throws IOException {
			
			ObjectMapper mapper = new ObjectMapper();
			mapper.setSerializationInclusion(Include.NON_NULL);
			
			for(Map.Entry<String, ScopeOnlySchemaRecord> entry : list._idToSchemaRecordMap.entrySet()) {

				String fieldsData = mapper.writeValueAsString(entry.getValue());
				SchemaRecordList.addCreateJson(jgen, entry.getKey(), fieldsData);
			}
		}
	}

	static class UpdateSerializer extends JsonSerializer<ScopeOnlySchemaRecordList> {

		@Override
		public void serialize(ScopeOnlySchemaRecordList list, JsonGenerator jgen, SerializerProvider provider)
				throws IOException {

			ObjectMapper mapper = new ObjectMapper();
			mapper.setSerializationInclusion(Include.NON_NULL);

			for(Map.Entry<String, ScopeOnlySchemaRecord> entry : list._idToSchemaRecordMap.entrySet()) {
				SchemaRecordList.addUpdateJson(jgen, entry.getKey());
			}
		}
	}

	static class Deserializer extends JsonDeserializer<ScopeOnlySchemaRecordList> {

		@Override
		public ScopeOnlySchemaRecordList deserialize(JsonParser jp, DeserializationContext context)
				throws IOException {
			
			String scrollID = null;
			List<ScopeOnlySchemaRecord> records = Collections.emptyList();
			
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
					
					JsonNode scopeNode = source.get(RecordType.SCOPE.getName());
					
					records.add(new ScopeOnlySchemaRecord(scopeNode.asText()));
				}
			}
			
			return new ScopeOnlySchemaRecordList(records, scrollID);
		}
	}
}

package com.salesforce.dva.argus.service.schema;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.salesforce.dva.argus.entity.ScopeAndMetricOnlySchemaRecord;
import org.apache.commons.codec.digest.DigestUtils;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.salesforce.dva.argus.service.SchemaService.RecordType;
import com.salesforce.dva.argus.service.schema.MetricSchemaRecordList.HashAlgorithm;

import net.openhft.hashing.LongHashFunction;

/**
 * Represents a list of scope and metric names from discovery queries.
 * Internally it has a mapping from hash id of scope and metric names to the actual scope and metric names.
 *
 * @author  Naveen Reddy Karri (nkarri@salesforce.com)
 */
public class ScopeAndMetricOnlySchemaRecordList {

    private Map<String, ScopeAndMetricOnlySchemaRecord> _idToSchemaRecordMap = new HashMap<>();
    private String _scrollID;

    public ScopeAndMetricOnlySchemaRecordList(List<ScopeAndMetricOnlySchemaRecord> records, String scrollID) {
        int count = 0;
        for(ScopeAndMetricOnlySchemaRecord record : records) {
            _idToSchemaRecordMap.put(String.valueOf(count++), record);
        }
        setScrollID(scrollID);
    }

    public ScopeAndMetricOnlySchemaRecordList(List<ScopeAndMetricOnlySchemaRecord> records, HashAlgorithm algorithm) {
        for(ScopeAndMetricOnlySchemaRecord record : records) {
            String id = null;
            String scopeAndMetricName = ScopeAndMetricOnlySchemaRecord.getFieldsAsString(record);
            if(HashAlgorithm.MD5.equals(algorithm)) {
                id = DigestUtils.md5Hex(scopeAndMetricName);
            } else {
                id = String.valueOf(LongHashFunction.xx().hashChars(scopeAndMetricName));
            }
            _idToSchemaRecordMap.put(id, record);
        }
    }

    public List<ScopeAndMetricOnlySchemaRecord> getRecords() {
        return new ArrayList<>(_idToSchemaRecordMap.values());
    }

    public String getScrollID() {
        return _scrollID;
    }

    public void setScrollID(String scrollID) {
        this._scrollID = scrollID;
    }

    ScopeAndMetricOnlySchemaRecord getRecord(String id) {
        return _idToSchemaRecordMap.get(id);
    }

    static class CreateSerializer extends JsonSerializer<ScopeAndMetricOnlySchemaRecordList> {

        @Override
        public void serialize(ScopeAndMetricOnlySchemaRecordList list, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {

            ObjectMapper mapper = new ObjectMapper();
            mapper.setSerializationInclusion(Include.NON_NULL);

            for(Map.Entry<String, ScopeAndMetricOnlySchemaRecord> entry : list._idToSchemaRecordMap.entrySet()) {
                String fieldsData = mapper.writeValueAsString(entry.getValue());
                SchemaRecordList.addCreateJson(jgen, entry.getKey(), fieldsData);
            }
        }
    }

    static class UpdateSerializer extends JsonSerializer<ScopeAndMetricOnlySchemaRecordList> {

        @Override
        public void serialize(ScopeAndMetricOnlySchemaRecordList list, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {

            ObjectMapper mapper = new ObjectMapper();
            mapper.setSerializationInclusion(Include.NON_NULL);

            for(Map.Entry<String, ScopeAndMetricOnlySchemaRecord> entry : list._idToSchemaRecordMap.entrySet()) {
                SchemaRecordList.addUpdateJson(jgen, entry.getKey());
            }
        }
    }

    static class Deserializer extends JsonDeserializer<ScopeAndMetricOnlySchemaRecordList> {

        @Override
        public ScopeAndMetricOnlySchemaRecordList deserialize(JsonParser jp, DeserializationContext context)
                throws IOException {

            String scrollID = null;
            List<ScopeAndMetricOnlySchemaRecord> records = Collections.emptyList();

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
                    JsonNode metricNode = source.get(RecordType.METRIC.getName());

                    records.add(new ScopeAndMetricOnlySchemaRecord(scopeNode.asText(), metricNode.asText()));
                }
            }

            return new ScopeAndMetricOnlySchemaRecordList(records, scrollID);
        }
    }
}
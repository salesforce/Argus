package com.salesforce.dva.argus.service.schema;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public class SchemaRecordList {

    public static void addCreateJson(JsonGenerator jgen, String id, String fieldsData) throws IOException {

        jgen.writeRaw("{ \"create\" : {\"_id\" : \"" + id + "\"}}");
        jgen.writeRaw(System.lineSeparator());

        long currentTimeMillis = System.currentTimeMillis();

        String createTimeStampField = "\"cts\":" + currentTimeMillis;
        String updateTimeStampField = "\"mts\":" + currentTimeMillis;
        jgen.writeRaw(fieldsData.substring(0, fieldsData.length()-1) + "," + createTimeStampField + "," + updateTimeStampField + "}");
        jgen.writeRaw(System.lineSeparator());
    }

    public static void addUpdateJson(JsonGenerator jgen, String id) throws IOException {
        jgen.writeRaw("{ \"update\" : {\"_id\" : \"" + id + "\"}}");
        jgen.writeRaw(System.lineSeparator());
        String timeStampField = "\"mts\":" + System.currentTimeMillis();
        jgen.writeRaw("{ \"doc\" :{" + timeStampField + "} }");
        jgen.writeRaw(System.lineSeparator());
    }

    static class AggDeserializer extends JsonDeserializer<List<String>> {

        @Override
        public List<String> deserialize(JsonParser jp, DeserializationContext context)
                throws IOException {

            List<String> values = Collections.emptyList();

            JsonNode rootNode = jp.getCodec().readTree(jp);
            JsonNode buckets = rootNode.get("aggregations").get("distinct_values").get("buckets");

            if (JsonNodeType.ARRAY.equals(buckets.getNodeType())) {
                values = new ArrayList<>(buckets.size());
                Iterator<JsonNode> iter = buckets.elements();
                while (iter.hasNext()) {
                    JsonNode bucket = iter.next();
                    String value = bucket.get("key").asText();
                    values.add(value);
                }
            }

            return values;
        }
    }
}

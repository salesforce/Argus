/*
 * Copyright (c) 2016, Salesforce.com, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.salesforce.dva.argus.service.querystore;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.salesforce.dva.argus.entity.QueryStoreRecord;
import net.openhft.hashing.LongHashFunction;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stores map of QueryStoreRecords and also useful to serialize and deserialize QueryStoreRecord map
 *
 */
public class QueryStoreRecordList {

    private Map<String, QueryStoreRecord> _idToQueryStoreRecordMap = new HashMap<>();
    private String _scrollID;

    public QueryStoreRecordList(List<QueryStoreRecord> records, String scrollID) {
        int count = 0;
        for(QueryStoreRecord record : records) {
            _idToQueryStoreRecordMap.put(String.valueOf(count++), record);
        }
        setScrollID(scrollID);
    }

    public QueryStoreRecordList(Set<QueryStoreRecord> records, HashAlgorithm algorithm) {
        for(QueryStoreRecord record : records) {
            String id = null;
            String qsrKey = QueryStoreRecord.getFieldsAsString(record);
            if(HashAlgorithm.MD5.equals(algorithm)) {
                id = DigestUtils.md5Hex(qsrKey);
            } else {
                id = String.valueOf(LongHashFunction.xx().hashChars(qsrKey));
            }
            _idToQueryStoreRecordMap.put(id, record);
        }
    }

    public List<QueryStoreRecord> getRecords() {
        return new ArrayList<>(_idToQueryStoreRecordMap.values());
    }

    public String getScrollID() {
        return _scrollID;
    }

    public void setScrollID(String scrollID) {
        this._scrollID = scrollID;
    }

    public QueryStoreRecord getRecord(String id) {
        return _idToQueryStoreRecordMap.get(id);
    }

    public static class IndexSerializer extends JsonSerializer<QueryStoreRecordList> {

        @Override
        public void serialize(QueryStoreRecordList list, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {

            ObjectMapper mapper = new ObjectMapper();
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

            for(Map.Entry<String, QueryStoreRecord> entry : list._idToQueryStoreRecordMap.entrySet()) {
                String fieldsData = mapper.writeValueAsString(entry.getValue());
                jgen.writeRaw("{ \"index\" : {\"_id\" : \"" + entry.getKey() + "\"}}");
                jgen.writeRaw(System.lineSeparator());
                String hostName;
                try {
                    hostName = InetAddress.getLocalHost().getHostName();
                } catch (Exception e)
                {
                    hostName = "nohostname";
                }
                String sourceHost= "\"sourcehost\":" + "\""+hostName+"\"";
                long currentTimeMillis = System.currentTimeMillis();
                String updateTimeStampField = "\"mts\":" + currentTimeMillis;
                jgen.writeRaw(fieldsData.substring(0, fieldsData.length()-1) + "," +sourceHost+"," + updateTimeStampField + "}");
                jgen.writeRaw(System.lineSeparator());
            }
        }
    }


    public static class Deserializer extends JsonDeserializer<QueryStoreRecordList> {

        @Override
        public QueryStoreRecordList deserialize(JsonParser jp, DeserializationContext context)
                throws IOException {

            String scrollID = null;
            List<QueryStoreRecord> records = Collections.emptyList();

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

                    JsonNode scopeNode = source.get(QueryStoreRecordType.SCOPE.getName());
                    JsonNode metricNode = source.get(QueryStoreRecordType.METRIC.getName());

                    records.add(new QueryStoreRecord(scopeNode.asText(), metricNode.asText()));
                }
            }

            return new QueryStoreRecordList(records, scrollID);
        }
    }

    public enum HashAlgorithm {
        MD5,
        XXHASH;

        public static QueryStoreRecordList.HashAlgorithm fromString(String str) throws IllegalArgumentException {
            for(QueryStoreRecordList.HashAlgorithm algo : QueryStoreRecordList.HashAlgorithm.values()) {
                if(algo.name().equalsIgnoreCase(str)) {
                    return algo;
                }
            }

            throw new IllegalArgumentException(str + " does not match any of the available algorithms.");
        }
    }

    /**
     * Indicates the Query Store record field to be used for matching.
     *
     */
    public static enum QueryStoreRecordType {

        /** Match against the scope field. */
        SCOPE("scope"),
        /** Match against the metric field. */
        METRIC("metric");

        private String _name;

        private QueryStoreRecordType(String name) {
            _name = name;
        }

        /**
         * Returns a given record type corresponding to the given name.
         *
         * @param   name  The case sensitive name to match against.  Cannot be null.
         *
         * @return  The corresponding record type or null if no matching record type exists.
         */
        @JsonCreator
        public static QueryStoreRecordType fromName(String name) {
            for (QueryStoreRecordType type : QueryStoreRecordType.values()) {
                if (type.getName().equalsIgnoreCase(name)) {
                    return type;
                }
            }

            throw new IllegalArgumentException("Illegal record type: " + name);
        }

        /**
         * Returns the record type name.
         *
         * @return  The record type name.
         */
        public String getName() {
            return _name;
        }
    }

}


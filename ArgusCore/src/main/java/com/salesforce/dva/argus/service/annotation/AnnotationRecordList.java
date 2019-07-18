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

package com.salesforce.dva.argus.service.annotation;

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
import com.google.common.annotations.VisibleForTesting;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.salesforce.dva.argus.entity.Annotation;

import com.salesforce.dva.argus.service.schema.RecordFinder;
import net.openhft.hashing.LongHashFunction;

import java.io.IOException;
import java.nio.charset.Charset;

import org.apache.commons.codec.digest.DigestUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Stores map of annotation identifier to annotation. Used to serialize and deserialize Annotation map
 *
 */
public class AnnotationRecordList implements RecordFinder<Annotation> {

    private Map<String, Annotation> _idToAnnotationMap = new HashMap<>();
    private String _scrollID;
    private static ObjectMapper mapper = new ObjectMapper();

    public AnnotationRecordList(List<Annotation> annotations, String scrollID) {
        int count = 0;
        for(Annotation annotation : annotations) {
            _idToAnnotationMap.put(String.valueOf(count++), annotation);
        }
        setScrollID(scrollID);
    }

    public AnnotationRecordList(List<Annotation> annotations, HashAlgorithm algorithm) {
        for(Annotation annotation : annotations) {
            String id = null;

            // Convert all timestamps to millis
            long timestamp = annotation.getTimestamp();
            if (timestamp < 100000000000L) {
                annotation.setTimestamp(timestamp * 1000);
            }

            String annotationKey = Annotation.getIdentifierFieldsAsString(annotation);
            if(HashAlgorithm.MD5.equals(algorithm)) {
                id = DigestUtils.md5Hex(annotationKey);
            } else if(HashAlgorithm.XXHASH.equals(algorithm)) {
                id = String.valueOf(LongHashFunction.xx().hashChars(annotationKey));
            } else {
                // Defaulting to md5
                id = DigestUtils.md5Hex(annotationKey);
            }
            _idToAnnotationMap.put(id, annotation);
        }
    }

    @Override
    public List<Annotation> getRecords() {
        return new ArrayList<>(_idToAnnotationMap.values());
    }

    @Override
    public Set<String> getIdSet() {
        return _idToAnnotationMap.keySet();
    }

    @Override
    public String getScrollID() {
        return _scrollID;
    }

    @Override
    public void setScrollID(String scrollID) {
        this._scrollID = scrollID;
    }

    @Override
    public Annotation getRecord(String id) {
        return _idToAnnotationMap.get(id);
    }

    @VisibleForTesting
    static String getHashedSearchIdentifier(Annotation annotation) {
        HashFunction hf = Hashing.murmur3_128();
        String searchIdentifier = new StringBuilder().append(annotation.getScope()).append(annotation.getMetric())
                .append(annotation.getTags().toString()).append(annotation.getType()).toString();
        return hf.newHasher().putString(searchIdentifier, Charset.defaultCharset()).hash().toString();
    }

    public static class IndexSerializer extends JsonSerializer<AnnotationRecordList> {

        public static final long MILLIS_IN_A_DAY = 86400000L;
        public static final long MAX_ANNOTATION_AGE_MS = 400 * MILLIS_IN_A_DAY;;

        @Override
        public void serialize(AnnotationRecordList list, JsonGenerator jgen, SerializerProvider provider)
                throws IOException {

            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

            for(Map.Entry<String, Annotation> entry : list._idToAnnotationMap.entrySet()) {
                Annotation annotation = entry.getValue();

                if(isAnnotationTimestampOld(annotation.getTimestamp())) continue;
                
                jgen.writeRaw("{ \"index\" : {\"_index\" : \"" + getAnnotationIndex(annotation.getTimestamp()) + "\",\"_type\": \"_doc"
                        + "\",\"_id\" : \"" + entry.getKey() + "\"}}");
                jgen.writeRaw(System.lineSeparator());
                Map<String, String> fieldsData = new HashMap<>();
                fieldsData.put(AnnotationRecordType.SCOPE.getName(), annotation.getScope());
                fieldsData.put(AnnotationRecordType.METRIC.getName(), annotation.getMetric());
                fieldsData.put(AnnotationRecordType.TAGS.getName(), mapper.writeValueAsString(annotation.getTags()));
                fieldsData.put(AnnotationRecordType.SOURCE.getName(), annotation.getSource());
                fieldsData.put(AnnotationRecordType.TYPE.getName(), annotation.getType());
                fieldsData.put(AnnotationRecordType.ID.getName(), annotation.getId());
                fieldsData.put(AnnotationRecordType.FIELDS.getName(), mapper.writeValueAsString(annotation.getFields()));
                fieldsData.put(AnnotationRecordType.SEARCH_ID.getName(), getHashedSearchIdentifier(annotation));
                fieldsData.put(AnnotationRecordType.TIMESTAMP.getName(), Long.toString(annotation.getTimestamp()));
                jgen.writeRaw(mapper.writeValueAsString(fieldsData));
                jgen.writeRaw(System.lineSeparator());
            }
        }

        private boolean isAnnotationTimestampOld(Long timestampMillis) {
            return System.currentTimeMillis() - timestampMillis > MAX_ANNOTATION_AGE_MS ? true : false;
        }

        private String getAnnotationIndex(Long epochTimestamp) {
            Date annotationDate = new Date(epochTimestamp);
            SimpleDateFormat formatter= new SimpleDateFormat("yyyy-MM");
            String indexNameToAppend = String.format("%s-%s", ElasticSearchAnnotationService.ANNOTATION_INDEX_TEMPLATE_PATTERN_START, formatter.format(annotationDate));
            return indexNameToAppend;
        }
    }

    public static class Deserializer extends JsonDeserializer<AnnotationRecordList> {

        @Override
        public AnnotationRecordList deserialize(JsonParser jp, DeserializationContext context)
                throws IOException {

            String scrollID = null;
            Annotation annotation;
            List<Annotation> records = Collections.emptyList();

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

                    JsonNode scopeNode = source.get(AnnotationRecordType.SCOPE.getName());
                    JsonNode metricNode = source.get(AnnotationRecordType.METRIC.getName());
                    JsonNode sourceNode = source.get(AnnotationRecordType.SOURCE.getName());
                    JsonNode idNode = source.get(AnnotationRecordType.ID.getName());
                    JsonNode typeNode = source.get(AnnotationRecordType.TYPE.getName());
                    JsonNode timestampNode = source.get(AnnotationRecordType.TIMESTAMP.getName());
                    JsonNode tagsNode = source.get(AnnotationRecordType.TAGS.getName());
                    JsonNode fieldsNode = source.get(AnnotationRecordType.FIELDS.getName());
                    @SuppressWarnings("unchecked")
                    Map<String, String> fieldsMap = mapper.readValue(fieldsNode.asText(), HashMap.class);
                    annotation = new Annotation(sourceNode.asText(), idNode.asText(), typeNode.asText(), scopeNode.asText(), metricNode.asText(), timestampNode.asLong());
                    annotation.setFields(fieldsMap);
                    @SuppressWarnings("unchecked")
                    Map<String, String> tags = mapper.readValue(tagsNode.asText(), HashMap.class);
                    annotation.setTags(tags);
                    records.add(annotation);
                }
            }

            return new AnnotationRecordList(records, scrollID);
        }
    }

    public enum HashAlgorithm {
        MD5,
        XXHASH;

        public static AnnotationRecordList.HashAlgorithm fromString(String str) throws IllegalArgumentException {
            for(AnnotationRecordList.HashAlgorithm algo : AnnotationRecordList.HashAlgorithm.values()) {
                if(algo.name().equalsIgnoreCase(str)) {
                    return algo;
                }
            }

            throw new IllegalArgumentException(str + " does not match any of the available algorithms.");
        }
    }

    /**
     * Indicates the Annotation record field to be used for matching.
     *
     */
    public static enum AnnotationRecordType {

        /** Match against the scope field. */
        SCOPE("scope"),
        /** Match against the metric field. */
        METRIC("metric"),
        TAGS("tags"),
        TIMESTAMP("ts"),
        SOURCE("source"),
        ID("id"),
        TYPE("type"),
        FIELDS("fields"),
        SEARCH_ID("sid");

        private String _name;

        private AnnotationRecordType(String name) {
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
        public static AnnotationRecordType fromName(String name) {
            for (AnnotationRecordType type : AnnotationRecordType.values()) {
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

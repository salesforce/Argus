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
	 
package com.salesforce.dva.argus.service.tsdb;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.service.tsdb.DefaultTSDBService.AnnotationWrapper;
import com.salesforce.dva.argus.service.tsdb.DefaultTSDBService.AnnotationWrappers;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.xml.bind.DatatypeConverter;

/**
 * Transforms annotations from Java to JSON and vice versa.
 *
 * @author  Tom Valine (tvaline@salesforce.com), Bhinav Sura (bhinav.sura@salesforce.com)
 */
class AnnotationTransform {

    //~ Constructors *********************************************************************************************************************************

    private AnnotationTransform() { }

    //~ Inner Classes ********************************************************************************************************************************

    /**
     * The annotation deserializer.
     *
     * @author  Tom Valine (tvaline@salesforce.com), Bhinav Sura (bhinav.sura@salesforce.com)
     */
    static class Deserializer extends JsonDeserializer<AnnotationWrappers> {

        @Override
        public AnnotationWrappers deserialize(JsonParser jp, DeserializationContext dc) throws IOException {
            Iterator<JsonNode> nodes = jp.getCodec().readValue(jp, JsonNode.class).elements();
            AnnotationWrappers result = new AnnotationWrappers();

            while (nodes.hasNext()) {
                JsonNode metric = nodes.next();
                JsonNode annotationsNode = metric.get("annotations");

                if (annotationsNode != null && annotationsNode.isArray()) {
                    Iterator<JsonNode> annotationsIter = annotationsNode.elements();

                    while (annotationsIter.hasNext()) {
                        AnnotationWrapper wrapper = new AnnotationWrapper();
                        JsonNode annotationNode = annotationsIter.next();
                        Long timestamp = annotationNode.get("startTime").asLong();
                        String uid = annotationNode.get("tsuid").asText();
                        Map<String, Annotation> annotations = new HashMap<>();
                        Iterator<Map.Entry<String, JsonNode>> entries = annotationNode.get("custom").fields();

                        while (entries.hasNext()) {
                            Map.Entry<String, JsonNode> entry = entries.next();
                            String key = entry.getKey();
                            String[] parts = key.split("\\.");
                            String source = parts[0];
                            String id = parts[1];
                            Map<String, String> fields = fromMeta(entry.getValue().asText());
                            Annotation annotation = new Annotation(source, id, "null", "null", "null", timestamp);

                            annotation.setFields(fields);
                            annotations.put(key, annotation);
                        }
                        wrapper.setCustom(annotations);
                        wrapper.setTimestamp(timestamp);
                        wrapper.setUid(uid);
                        result.add(wrapper);
                    }
                }
            }
            return result;
        }

        private Map<String, String> fromMeta(String meta) throws IOException {
            try {
                String decoded = new String(DatatypeConverter.parseBase64Binary(meta.replace("_", "=")), "UTF-8");

                return new ObjectMapper().readValue(decoded, new TypeReference<Map<String, String>>() { });
            } catch (Exception ex) {
                throw new IOException(ex);
            }
        }
    }

    /**
     * The annotation serializer.
     *
     * @author  Tom Valine (tvaline@salesforce.com), Bhinav Sura (bhinav.sura@salesforce.com)
     */
    static class Serializer extends JsonSerializer<AnnotationWrapper> {

        @Override
        public void serialize(AnnotationWrapper wrapper, JsonGenerator jgen, SerializerProvider sp) throws IOException {
            Map<String, Annotation> annotations = wrapper.getCustom();

            jgen.writeStartObject();
            jgen.writeNumberField("startTime", wrapper.getTimestamp());
            jgen.writeStringField("tsuid", wrapper.getUid());
            jgen.writeObjectFieldStart("custom");
            for (Map.Entry<String, Annotation> entry : annotations.entrySet()) {
                jgen.writeStringField(entry.getKey(), toMeta(entry.getValue()));
            }
            jgen.writeEndObject();
            jgen.writeEndObject();
        }

        private String toMeta(Annotation annotation) throws IOException {
            Map<String, String> fields = annotation.getFields();

            try {
                return DatatypeConverter.printBase64Binary(new ObjectMapper().writeValueAsString(fields).getBytes("UTF-8")).replace("=", "_");
            } catch (JsonProcessingException | UnsupportedEncodingException ex) {
                throw new IOException(ex);
            }
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

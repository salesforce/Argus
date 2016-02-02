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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.TSDBEntity;
import com.salesforce.dva.argus.entity.TSDBEntity.ReservedField;
import com.salesforce.dva.argus.service.TSDBService;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.bind.DatatypeConverter;

/**
 * Transforms metrics from Java to JSON and vice versa.
 *
 * @author  Tom Valine (tvaline@salesforce.com), Bhinav Sura (bhinav.sura@salesforce.com)
 */
class MetricTransform {

    //~ Constructors *********************************************************************************************************************************

    private MetricTransform() { }

    //~ Inner Classes ********************************************************************************************************************************

    /**
     * The metric deserializer.
     *
     * @author  Tom Valine (tvaline@salesforce.com), Bhinav Sura (bhinav.sura@salesforce.com)
     */
    static class Deserializer extends JsonDeserializer<Metric> {
	TSDBService tsdbService;

        Deserializer(TSDBService tsdbService) {
	    this.tsdbService = tsdbService;
        }

        @Override
        public Metric deserialize(JsonParser jp, DeserializationContext dc) throws IOException {
            JsonNode node = jp.getCodec().readTree(jp);
            ObjectMapper mapper = new ObjectMapper();
            Map<Long, String> datapoints = mapper.readValue(node.get("dps").traverse(), new TypeReference<TreeMap<Long, String>>() { });

            Map<String, String> tags = mapper.readValue(node.get("tags").traverse(), new TypeReference<Map<String, String>>() { });

            Map<String, String> meta = fromMeta(tags.get(ReservedField.META.getKey()));
            String tsdbMetricName = node.get("metric").asText();
            String scope = tsdbService.getScopeFromTSDBMetric(tsdbMetricName);
            String namespace = tsdbService.getNamespaceFromTSDBMetric(tsdbMetricName);
		
            // Post filtering metric , since in some cases TSDB metric can be empty https://github.com/OpenTSDB/opentsdb/issues/540
            if (scope.isEmpty()) {
                return null;
            }

            String metric = tags.get(ReservedField.METRIC.getKey());
            Map<String, String> userTags = new HashMap<>();

            for (Map.Entry<String, String> entry : tags.entrySet()) {
                String key = entry.getKey();

                if (!ReservedField.isReservedField(key)) {
                    userTags.put(key, entry.getValue());
                }
            }

            Metric result = new Metric(scope, metric);

            if (meta != null) {
                String displayName = meta.get(ReservedField.DISPLAY_NAME.getKey());
                String units = meta.get(ReservedField.UNITS.getKey());

                result.setDisplayName(displayName);
                result.setUnits(units);
            }
            result.setTags(userTags);
            result.setDatapoints(datapoints);
            if (namespace != null) {
                result.setNamespace(namespace);
            }

            Iterator<JsonNode> tsuidsIter = node.get("tsuids").elements();
            String tsuid = tsuidsIter.next().asText();

            try {
                Field tsuidField = TSDBEntity.class.getDeclaredField("_uid");

                tsuidField.setAccessible(true);
                tsuidField.set(result, tsuid);
            } catch (Exception ex) {
                throw new IOException(ex);
            }
            return result;
        }

        private Map<String, String> fromMeta(String meta) throws IOException {
            if (meta != null) {
                try {
                    String decoded = new String(DatatypeConverter.parseBase64Binary(meta.replace("_", "=")), "UTF-8");

                    return new ObjectMapper().readValue(decoded, new TypeReference<Map<String, String>>() { });
                } catch (Exception ex) {
                    throw new IOException(ex);
                }
            } else {
                return new HashMap<>();
            }
        }
    }

    /**
     * The metric serializer.
     *
     * @author  Tom Valine (tvaline@salesforce.com), Bhinav Sura (bhinav.sura@salesforce.com)
     */
    static class Serializer extends JsonSerializer<Metric> {
       
	TSDBService tsdbService;

        Serializer(TSDBService tsdbService) {
	    this.tsdbService = tsdbService;
        }

        @Override
        public void serialize(Metric metric, JsonGenerator jgen, SerializerProvider sp) throws IOException {
            Map<Long, String> datapoints = metric.getDatapoints();

            for (Map.Entry<Long, String> entry : datapoints.entrySet()) {
                jgen.writeStartObject();
                jgen.writeStringField("metric", tsdbService.constructTSDBMetricName(metric.getScope(), metric.getNamespace()));
                jgen.writeNumberField("timestamp", entry.getKey());
                jgen.writeStringField("value", entry.getValue());
                serializeTags(metric, jgen);
                jgen.writeEndObject();
            }
        }

        private void serializeTags(Metric metric, JsonGenerator jgen) throws IOException {
            jgen.writeObjectFieldStart("tags");

            Map<String, String> tags = new HashMap<>(metric.getTags());

            tags.put(ReservedField.METRIC.getKey(), metric.getMetric());
            tags.put(ReservedField.META.getKey(), toMeta(metric));
            for (Map.Entry<String, String> tagEntry : tags.entrySet()) {
                jgen.writeStringField(tagEntry.getKey(), tagEntry.getValue());
            }
            jgen.writeEndObject();
        }

        private String toMeta(Metric metric) throws IOException {
            Map<String, String> meta = new HashMap<>();

            meta.put(ReservedField.DISPLAY_NAME.getKey(), metric.getDisplayName());
            meta.put(ReservedField.UNITS.getKey(), metric.getUnits());
            try {
                return DatatypeConverter.printBase64Binary(new ObjectMapper().writeValueAsString(meta).getBytes("UTF-8")).replace("=", "_");
            } catch (Exception ex) {
                throw new IOException(ex);
            }
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

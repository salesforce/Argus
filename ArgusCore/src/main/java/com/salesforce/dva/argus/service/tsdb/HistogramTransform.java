package com.salesforce.dva.argus.service.tsdb;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.xml.bind.DatatypeConverter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.salesforce.dva.argus.entity.Histogram;
import com.salesforce.dva.argus.entity.HistogramBucket;
import com.salesforce.dva.argus.entity.TSDBEntity.ReservedField;

/**
 * Transforms histograms from Java to JSON.
 *
 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
 */
public class HistogramTransform {

    /**
     * The histogram serializer.
     *
     * @author   Dilip Devaraj (ddevaraj@salesforce.com)
     */
    static class Serializer extends JsonSerializer<Histogram> {

        @Override
        public void serialize(Histogram histogram, JsonGenerator jgen, SerializerProvider sp) throws IOException {
            if(histogram != null) {
                jgen.writeStartObject();
                jgen.writeStringField("metric", DefaultTSDBService.constructTSDBMetricName(histogram));
                jgen.writeNumberField("timestamp", histogram.getTimestamp());
                jgen.writeNumberField("overflow", histogram.getOverflow());
                jgen.writeNumberField("underflow", histogram.getUnderflow());
                serializeTags(histogram, jgen);
                serializeBuckets(histogram, jgen);
                jgen.writeEndObject();
            }
        }

        private void serializeTags(Histogram histogram, JsonGenerator jgen) throws IOException {
            jgen.writeObjectFieldStart("tags");

            Map<String, String> tags = new HashMap<>(histogram.getTags());
            
            tags.put(ReservedField.META.getKey(), toMeta(histogram));
            for (Map.Entry<String, String> tagEntry : tags.entrySet()) {
                jgen.writeStringField(tagEntry.getKey(), tagEntry.getValue());
            }
            jgen.writeEndObject();
        }
        
        private void serializeBuckets(Histogram histogram, JsonGenerator jgen) throws IOException {
            jgen.writeObjectFieldStart("buckets");
            
            for (Map.Entry<HistogramBucket, Long> bucketEntry : histogram.getBuckets().entrySet()) {
                jgen.writeStringField(bucketEntry.getKey().toString(), bucketEntry.getValue().toString());
            }
            jgen.writeEndObject();
        }        

        private String toMeta(Histogram histogram) throws IOException {
            Map<String, String> meta = new HashMap<>();

            meta.put(ReservedField.DISPLAY_NAME.getKey(), histogram.getDisplayName());
            meta.put(ReservedField.UNITS.getKey(), histogram.getUnits());
            try {
                return DatatypeConverter.printBase64Binary(new ObjectMapper().writeValueAsString(meta).getBytes("UTF-8")).replace("=", "_");
            } catch (Exception ex) {
                throw new IOException(ex);
            }
        }
    }
}

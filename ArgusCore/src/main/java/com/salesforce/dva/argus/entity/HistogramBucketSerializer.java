package com.salesforce.dva.argus.entity;

import java.io.IOException;
import java.io.StringWriter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;

/**
 * HistogramBucket Serializer
 *
 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
 */
public class HistogramBucketSerializer extends JsonSerializer<HistogramBucket> {
    private static final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public void serialize(HistogramBucket value, JsonGenerator jgen, SerializerProvider serializers) throws IOException {
        StringWriter writer = new StringWriter();
        mapper.writeValue(writer, value);
        jgen.writeFieldName(writer.toString());
    }
}

package com.salesforce.dva.argus.entity;

import java.io.IOException;

import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.KeyDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * HistogramBucket Deserializer
 *
 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
 */
public class HistogramBucketDeserializer extends KeyDeserializer{
    private static final ObjectMapper mapper = new ObjectMapper();
    
    @Override
    public HistogramBucket deserializeKey(String key, DeserializationContext ctxt) throws IOException {
        return mapper.readValue(key, HistogramBucket.class);
    }
}
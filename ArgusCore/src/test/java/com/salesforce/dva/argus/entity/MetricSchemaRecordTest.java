package com.salesforce.dva.argus.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.junit.Assert.*;

public class MetricSchemaRecordTest {
    @Test
    public void testSerialization() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        //make sure existing serialized records without the new field can be reconstructed correctly
        MetricSchemaRecord original = new MetricSchemaRecord("namespace", "scope", "metric", "tagk", "tagv");
        assertNull("expect original object to have null retention value", original.getRetentionDiscovery());
        String s = objectMapper.writeValueAsString(original);
        assertFalse("expect the json to not contain the retention value", s.contains(MetricSchemaRecord.RETENTION_DISCOVERY));

        MetricSchemaRecord deserialized = objectMapper.readValue(s, MetricSchemaRecord.class);
        assertEquals("expect the deserialized object to be equal to the original", original, deserialized);
        assertNull("expect the deserialized object has null retention value", deserialized.getRetentionDiscovery());

        //update the original record with a retention value and test things out
        original.setRetentionDiscovery(50);
        String s2 = objectMapper.writeValueAsString(original);
        assertTrue("expect the new json to contain the retention value", s2.contains(MetricSchemaRecord.RETENTION_DISCOVERY));

        MetricSchemaRecord deserialized2 = objectMapper.readValue(s2, MetricSchemaRecord.class);
        assertEquals("expect the new deserialized object to be equal to the original", original, deserialized2);
        assertEquals("expect the new deserialized object to have retention value", deserialized2.getRetentionDiscovery().intValue(), 50);
    }

}
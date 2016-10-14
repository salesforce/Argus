package com.salesforce.dva.warden.dto;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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

public class WardenObjectTransform {
	/**
     * The infraction serializer.
     *
     * @author  Ruofan Zhang (rzhang@salesforce.com)
     */
    static class InfractionSerializer extends JsonSerializer<Infraction> {
   

        @Override
        public void serialize(Infraction infraction, JsonGenerator jgen, SerializerProvider sp) throws IOException {            

                jgen.writeStartObject();
                jgen.writeFieldName("id");
                jgen.writeNumber(infraction.getPolicyId());
                jgen.writeFieldName("id");
                jgen.writeNumber(infraction.getPolicyId());
                jgen.writeFieldName("userId");
                jgen.writeNumber(infraction.getUserId());
                
                jgen.writeNumberField("infractionTimestamp", infraction.getInfractionTimestamp());
                jgen.writeNumberField("expirationTimestamp", infraction.getInfractionTimestamp());
                jgen.writeEndObject();
            
        }
    }
    
     public static class InfractionDeserializer extends JsonDeserializer<Infraction> {
    
//    	@Override
//    	public Infraction deserialize(JsonParser jp, DeserializationContext dc) throws IOException {
//    		JsonNode node = jp.getCodec().readTree(jp);
//    		
//    		BigInteger policy_id = node.get("policy_id").bigIntegerValue();
//    		BigInteger user_id = node.get("user_id").bigIntegerValue();
//    		Long infraction_timestamp = node.get("infraction_timestamp").asLong();
//    		Long expiration_timestamp = node.get("expiration_timestamp").asLong();
//    		
//    		
//    		Infraction result = new Infraction();
//    		
//    		result.setPolicyId(policy_id);
//    		result.setUserId(user_id);
//    		result.setInfractionTimestamp(infraction_timestamp);
//    		result.setExpirationTimestamp(expiration_timestamp);
//    		
//    		return result;
//    	}
    	 
    	 @Override
     	public Infraction deserialize(JsonParser jp, DeserializationContext dc) throws IOException {
     		JsonNode node = jp.getCodec().readTree(jp);
     		
     		BigInteger id = node.get("id").bigIntegerValue();
     		BigInteger policy_id = node.get("policyId").bigIntegerValue();
     		BigInteger user_id = node.get("userId").bigIntegerValue();
     		//parse user list
//     		Iterator<JsonNode> userIterator = node.get("users").elements();
//     		List<JsonNode> userNodes = new ArrayList<>();
//     		userIterator.forEachRemaining(userNodes::add);
//     		List<String> users = userNodes.stream().map(uNode -> uNode.asText()).collect(Collectors.toList());

     		Long infraction_timestamp = node.get("infractionTimestamp").asLong();
     		Long expiration_timestamp = node.get("expirationTimestamp").asLong();
     		
     		
     		Infraction result = new Infraction();
     		
     		result.setId(id);
     		result.setPolicyId(policy_id);
     		result.setUserId(user_id);
     		result.setInfractionTimestamp(infraction_timestamp);
     		result.setExpirationTimestamp(expiration_timestamp);
     		
     		return result;
     	}
    	 
    	
    	/* Helper method to convert JSON String representation to the corresponding Java entity. */
        private static <T> T toEntity(ObjectMapper _mapper,String content, TypeReference<T> type) {
            try {
                return _mapper.readValue(content, type);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }

        /* Helper method to convert a Java entity to a JSON string. */
        private static <T> String fromEntity(ObjectMapper _mapper, T type) {
            try {
                return _mapper.writeValueAsString(type);
            } catch (JsonProcessingException ex) {
                throw new RuntimeException(ex);
            }
        }
    }
}

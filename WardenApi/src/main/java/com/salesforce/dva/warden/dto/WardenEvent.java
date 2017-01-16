package com.salesforce.dva.warden.dto;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.salesforce.dva.warden.dto.WardenEvent.WardenEventType;

/**
 * Warden object for warden event notification
 *
 * @author  Ruofan Zhang(rzhang@salesforce.com)
 */
public class WardenEvent {

	   //~ Instance fields ******************************************************************************************************************************
	
    private final ObjectMapper _mapper;
	
    public WardenEvent(){
    	_mapper = getMapper();
    }
	
    private ObjectMapper getMapper() {
        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();

        module.addSerializer(Infraction.class, new WardenObjectTransform.InfractionSerializer());
       
        mapper.registerModule(module);
        return mapper;
    }
    
    
    
    /* Writes new infractions.*/
    public  byte[] getWardenEventData(Infraction infraction, WardenEventType wardenEventType) {
    	if(infraction == null)
    		throw new IllegalArgumentException("No wardenObject found!");
    	
    	//byte[] eventCodeArr = ByteBuffer.allocate(4).putInt(wardenEventType.value()).array();
//    	byte[] eventCodeArr = WardenEvent.WardenEventType.NEW_INFRACTION.value().getBytes();
//    	byte[] dtoArr = fromEntity(infraction).getBytes();
//    	byte[] data = new byte[eventCodeArr.length + dtoArr.length];
//    	System.arraycopy(eventCodeArr, 0, data, 0, eventCodeArr.length);
//    	System.arraycopy(dtoArr, 0, data, eventCodeArr.length, data.length);
//    	
//    	return data;
    	
    	byte[] code = WardenEvent.WardenEventType.NEW_INFRACTION.value().getBytes();
	      byte[] delimiter = "-".getBytes();
	      byte[] entity =fromEntity(infraction).getBytes();
	      ByteBuffer message = ByteBuffer.allocate(code.length + delimiter.length + entity.length);
	      message.put(code);
	      message.put(delimiter);
	      message.put(entity);
	      
	      return message.array();
    }
    
    
//	/* Helper method to convert JSON String representation to the corresponding Java entity. */
//    private <T> T toEntity(String content, TypeReference<T> type) {
//        try {
//            return _mapper.readValue(content, type);
//        } catch (IOException ex) {
//            throw new RuntimeException(ex);
//        }
//    }

    /* Helper method to convert a Java entity to a JSON string. */
    private <T> String fromEntity(T type) {
        try {
            return _mapper.writeValueAsString(type);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    //~ Enums ****************************************************************************************************************************************

    /**
     * The type of warden event.
     *
     * @author  Ruofan Zhang (rzhang@salesforce.com)
     */
    public enum WardenEventType {

        /** New Infraction */
        NEW_INFRACTION("new");

    	private final String eventType;
    	private WardenEventType(String eventType) {
    		this.eventType = eventType;
    	}
        /**
         * Converts a string to a warden event type.
         *
         * @param   eventCode  The warden event type code.
         *
         * @return  The corresponding trigger type.
         *
         * @throws  IllegalArgumentException  If no corresponding trigger type is found.
         */
        @JsonCreator
        public static WardenEventType fromEventCode(String type) {
            for (WardenEventType t : WardenEventType.values()) {
                if (t.equals(type)) {
                    return t;
                }
            }
            throw new IllegalArgumentException("Warden Event Type does not exist.");
        }

        /**
         * Returns the event code of the warden event type.
         *
         * @return  The event code of the warden event type.
         */
        @JsonValue
        public  String value() {
            return this.eventType;
        }
    }
}

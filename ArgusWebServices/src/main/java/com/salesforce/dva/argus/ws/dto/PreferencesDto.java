package com.salesforce.dva.argus.ws.dto;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response.Status;

import com.salesforce.dva.argus.entity.Preferences;

@SuppressWarnings("serial")
public class PreferencesDto extends EntityDTO {
	
	private String userName;
	private BigInteger entityId;
	private String preferences;

	
	public static PreferencesDto transformToDto(Preferences preferences) {
		if (preferences == null) {
            throw new WebApplicationException("Null entity object cannot be converted to Dto object.", Status.INTERNAL_SERVER_ERROR);
        }

        PreferencesDto result = createDtoObject(PreferencesDto.class, preferences);

        result.setUserName(preferences.getUser().getUserName());
        if(preferences.getEntity() != null) {
        	result.setEntityId(preferences.getEntity().getId());
        }
        
        return result;
	}
	
	public static List<PreferencesDto> transformToDto(List<Preferences> preferencesList) {
		if (preferencesList == null) {
            throw new WebApplicationException("Null entity object cannot be converted to Dto object.", Status.INTERNAL_SERVER_ERROR);
        }

        List<PreferencesDto> result = new ArrayList<>(preferencesList.size());
        for (Preferences preferences : preferencesList) {
            result.add(transformToDto(preferences));
        }
        
        return result;
	}
	
	public String getUserName() {
		return userName;
	}

	public void setUserName(String userName) {
		this.userName = userName;
	}

	public BigInteger getEntityId() {
		return entityId;
	}

	public void setEntityId(BigInteger entityId) {
		this.entityId = entityId;
	}

	public String getPreferences() {
		return preferences;
	}

	public void setPreferences(String preferences) {
		this.preferences = preferences;
	}

	@Override
	public Object createExample() {
		// TODO Auto-generated method stub
		return null;
	}

}

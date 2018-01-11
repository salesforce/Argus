package com.salesforce.dva.argus.service;

import java.math.BigInteger;

import com.salesforce.dva.argus.entity.Preferences;

public interface PreferencesService {
	
	Preferences updatePreferences(Preferences preferences);
	
	void deletePreferences(BigInteger id);
	
	void deletePreferences(Preferences preferences);
	
	void deletePreferences(BigInteger userId, BigInteger entityId);
	
	Preferences getPreferencesByPrimaryKey(BigInteger id);
	
	Preferences getPreferencesByUserAndEntity(BigInteger userId, BigInteger entityId);
	
	Preferences getPreferencesForUser(BigInteger userId);

}

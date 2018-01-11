package com.salesforce.dva.argus.service.jpa;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;
import static java.math.BigInteger.ZERO;

import java.math.BigInteger;

import javax.persistence.EntityManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import com.salesforce.dva.argus.entity.Preferences;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.PreferencesService;
import com.salesforce.dva.argus.system.SystemConfiguration;

public class DefaultPreferencesService extends DefaultJPAService implements PreferencesService {
	
	private Logger _logger = LoggerFactory.getLogger(DefaultPreferencesService.class);
	@Inject
    Provider<EntityManager> emf;

	@Inject
	public DefaultPreferencesService(AuditService auditService, SystemConfiguration config) {
		super(auditService, config);
	}

	@Override
	@Transactional
	public Preferences updatePreferences(Preferences preferences) {
		requireNotDisposed();
        requireArgument(preferences != null, "Cannot update a null preferences entity.");

        EntityManager em = emf.get();
        Preferences result = mergeEntity(em, preferences);

        em.flush();
        _logger.debug("Updated preferences to : {}", result);
        _auditService.createAudit("Updated preferences : {0}", result, result);
        return result;
	}

	@Override
	@Transactional
	public void deletePreferences(BigInteger id) {
        Preferences preferences = getPreferencesByPrimaryKey(id);
        deletePreferences(preferences);
	}
	
	@Override
	@Transactional
	public void deletePreferences(Preferences preferences) {
		requireNotDisposed();
        requireArgument(preferences != null, "Cannot delete a null preferences entity.");
        _logger.debug("Deleting preferences {}.", preferences);

        EntityManager em = emf.get();

        deleteEntity(em, preferences);
        em.flush();
	}

	@Override
	@Transactional
	public void deletePreferences(BigInteger userId, BigInteger entityId) {
		Preferences preferences = getPreferencesByUserAndEntity(userId, entityId);
		deletePreferences(preferences);
	}

	@Override
	@Transactional
	public Preferences getPreferencesByPrimaryKey(BigInteger id) {
		requireNotDisposed();
        requireArgument(id != null && id.compareTo(ZERO) > 0, "Preferences ID must be greater than zero.");

        Preferences result = findEntity(emf.get(), id, Preferences.class);

        _logger.debug("Query for Preferences having id {} resulted in : {}", id, result);
        return result;
	}

	@Override
	@Transactional
	public Preferences getPreferencesByUserAndEntity(BigInteger userId, BigInteger entityId) {
		requireNotDisposed();
		requireArgument(userId != null && userId.compareTo(ZERO) > 0, "PrincipalUser ID cannot be null and must be greater than zero.");
		requireArgument(entityId != null && entityId.compareTo(ZERO) > 0, "Entity ID cannot be null and must be greater than zero.");
		
		return Preferences.getPreferencesByUserAndEntity(emf.get(), userId, entityId);
	}
	
	@Override
	@Transactional
	public Preferences getPreferencesForUser(BigInteger userId) {
		requireNotDisposed();
		requireArgument(userId != null && userId.compareTo(ZERO) > 0, "PrincipalUser ID cannot be null and must be greater than zero.");
		
		return Preferences.getPreferencesForUser(emf.get(), userId);
	}

}

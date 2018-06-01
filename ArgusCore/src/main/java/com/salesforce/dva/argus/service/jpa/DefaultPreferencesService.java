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

package com.salesforce.dva.argus.service.jpa;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;
import static java.math.BigInteger.ZERO;

import java.math.BigInteger;

import javax.persistence.EntityManager;

import com.salesforce.dva.argus.entity.JPAEntity;
import com.salesforce.dva.argus.entity.PrincipalUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import com.salesforce.dva.argus.entity.Preferences;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.PreferencesService;
import com.salesforce.dva.argus.system.SystemConfiguration;

/**
 * Default implementation of the <tt>PreferencesService</tt> interface.
 *
 * @author Chandravyas Annakula (cannakula@salesforce.com)
 */
public class DefaultPreferencesService extends DefaultJPAService implements PreferencesService {

    //~ Instance fields ******************************************************************************************************************************

    private Logger _logger = LoggerFactory.getLogger(DefaultPreferencesService.class);
	@Inject
    Provider<EntityManager> emf;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new DefaultDashboardService object.
     *
     * @param  auditService  The audit service. Cannot be null.
     * @param  config        Service properties
     */
    @Inject
	public DefaultPreferencesService(AuditService auditService, SystemConfiguration config) {
		super(auditService, config);
	}

    //~ Methods **************************************************************************************************************************************

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
	public Preferences getPreferencesByUserAndEntity(BigInteger userId, BigInteger entityId) {
		requireNotDisposed();
		requireArgument(userId != null && userId.compareTo(ZERO) > 0, "PrincipalUser ID cannot be null and must be greater than zero.");
		requireArgument(entityId != null && entityId.compareTo(ZERO) > 0, "Entity ID cannot be null and must be greater than zero.");
		
		return Preferences.getPreferencesByUserAndEntity(emf.get(), userId, entityId);
	}

    @Override
    @Transactional
    public Preferences getPreferencesByEntity(BigInteger entityId) {
        requireNotDisposed();
        requireArgument(entityId != null && entityId.compareTo(ZERO) > 0, "Entity ID cannot be null and must be greater than zero.");

        return Preferences.getPreferencesByEntity(emf.get(),entityId);
    }

    @Override
    @Transactional
    public JPAEntity getAssociatedEntity(BigInteger entityId) {
        requireNotDisposed();
        requireArgument(entityId != null && entityId.compareTo(ZERO) > 0, "Associated entity id for this chart cannot be null "
                + "and must be greater than zero.");

        return findEntity(emf.get(), entityId, JPAEntity.class);
    }


}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
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
	 
package com.salesforce.dva.argus.service.audit;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import com.salesforce.dva.argus.entity.Audit;
import com.salesforce.dva.argus.entity.JPAEntity;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.jpa.DefaultJPAService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import org.slf4j.Logger;
import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.List;
import javax.persistence.EntityManager;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;
import static java.math.BigInteger.ZERO;

/**
 * Default implementation of Audit service.
 *
 * @author  Raj Sarkapally (rsarkapally@salesforce.com)
 */
public class DefaultAuditService extends DefaultJPAService implements AuditService {

    //~ Instance fields ******************************************************************************************************************************

    @SLF4JTypeListener.InjectLogger
    private Logger _logger;
    @Inject
    private Provider<EntityManager> emf;

    //~ Constructors *********************************************************************************************************************************

    /** Creates a new DefaultAuditService object. */
    @Inject
    DefaultAuditService(SystemConfiguration config) {
        super(null, config);
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    @Transactional
    public Audit createAudit(String message, JPAEntity entity, Object... params) {
        Audit audit = new Audit(MessageFormat.format(message, params), SystemConfiguration.getHostname(), entity);

        return createAudit(audit);
    }

    @Override
    @Transactional
    public void deleteExpiredAudits() {
        requireNotDisposed();
        try {
            int deletedCount = Audit.deleteExpired(emf.get());

            _logger.info("Deleted {} expired audit entries.", deletedCount);
        } catch (Exception ex) {
            _logger.warn("Couldn't delete expired audits : {}", ex.getMessage());
        }
    }

    @Override
    @Transactional
    public Audit createAudit(Audit audit) {
        requireNotDisposed();
        requireArgument(audit != null, "Null audit cannot be created.");
        if (audit.getId() != null) {
            throw new SystemException("Audit object cannot be updated.");
        }

        Audit result = mergeEntity(emf.get(), audit);

        _logger.debug("Created audit object {}", result);
        return result;
    }

    @Override
    public Audit findAuditByPrimaryKey(BigInteger id) {
        requireNotDisposed();
        requireArgument(id != null && id.compareTo(ZERO) > 0, "Id must be a positive non-zero value.");

        Audit result = findEntity(emf.get(), id, Audit.class);

        _logger.debug("Query for audit having id {} resulted in : {}", id, result);
        return result;
    }

    @Override
    public List<Audit> findByEntity(BigInteger entityId, BigInteger limit) {
        requireNotDisposed();
        requireArgument(entityId != null && entityId.compareTo(ZERO) > 0, "Id must be a positive non-zero value.");

        EntityManager em = emf.get();
        JPAEntity entity = findEntity(em, entityId, JPAEntity.class);

        if (entity == null) {
            throw new IllegalArgumentException(MessageFormat.format("The entity with Id {} does not exist.", entityId));
        }

        List<Audit> result = limit == null ? Audit.findByEntity(em, entity) : Audit.findByEntity(em, entity, limit);

        _logger.debug("Query for Audits with JPA entity Id {} returned {} records", entity.getId(), result.size());
        return result;
    }

    @Override
    public List<Audit> findByEntity(BigInteger entityId) {
        return findByEntity(entityId, null);
    }

    @Override
    public List<Audit> findByHostName(String hostName) {
        requireNotDisposed();
        requireArgument(hostName != null && !hostName.isEmpty(), "Host name cannot be null or empty.");

        List<Audit> result = Audit.findByHostName(emf.get(), hostName);

        _logger.debug("Query for Audits with hostname {} returned {} records", hostName, result.size());
        return result;
    }

    @Override
    public List<Audit> findAll() {
        requireNotDisposed();

        List<Audit> result = Audit.findAll(emf.get());

        _logger.debug("Query for all Audits returned {} records", result.size());
        return result;
    }

    @Override
    public List<Audit> findByMessage(String message) {
        requireNotDisposed();

        List<Audit> result = Audit.findByMessage(emf.get(), message);

        _logger.debug("Query for Audits with message {} returned {} records", message, result.size());
        return result;
    }

    @Override
    public List<Audit> findByEntityHostnameMessage(BigInteger entityId, String hostName, String message) {
        if (entityId != null && entityId.compareTo(BigInteger.ZERO) < 1) {
            throw new IllegalArgumentException("JPA entity id cannot be zero or negative.");
        }

        JPAEntity jpaEntity = null;
        EntityManager em = emf.get();

        if (entityId != null) {
            jpaEntity = findEntity(em, entityId, JPAEntity.class);
            if (jpaEntity == null) {
                throw new IllegalArgumentException(MessageFormat.format("The entity with Id {0} does not exist.", entityId.toString()));
            }
        }

        List<Audit> result = Audit.findByEntityHostnameMessage(em, jpaEntity, hostName, message);

        _logger.debug("Query for audits with entity ID {}, host name {}, and message {} returned {} records.", entityId, hostName, message,
            result.size());
        return result;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

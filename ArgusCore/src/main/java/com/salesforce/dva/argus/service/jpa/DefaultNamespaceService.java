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

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.persist.Transactional;
import com.salesforce.dva.argus.entity.Namespace;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.NamespaceService;
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
 * The default implementation of the namespace service.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class DefaultNamespaceService extends DefaultJPAService implements NamespaceService {

    //~ Instance fields ******************************************************************************************************************************

    @SLF4JTypeListener.InjectLogger
    private Logger _logger;
    @Inject
    Provider<EntityManager> emf;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new DefaultNamespaceService object.
     *
     * @param  auditService  The audit service. Cannot be null.
     * @param _sysConfig Service properties
     */
    @Inject
    public DefaultNamespaceService(AuditService auditService, SystemConfiguration _sysConfig) {
        super(auditService, _sysConfig);
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    @Transactional
    public Namespace findNamespaceByPrimaryKey(BigInteger id) {
        requireNotDisposed();
        requireArgument(id != null && id.compareTo(ZERO) > 0, "ID must be a positive non-zero value.");

        Namespace result = findEntity(emf.get(), id, Namespace.class);

        _logger.debug("Query for namespace having id {} resulted in : {}", id, result);
        return result;
    }

    @Override
    @Transactional
    public List<Namespace> findNamespacesByOwner(PrincipalUser owner) {
        requireNotDisposed();
        requireArgument(owner != null, "Owner cannot be null.");

        List<Namespace> result = Namespace.findByOwner(emf.get(), owner);

        _logger.debug("Query for owner {} resulted in : {}", owner, result);
        return result;
    }

    /**
     * Creates a new namespace.
     *
     * @param   namespace  The namespace to create.  Cannot be null.
     *
     * @return  The updated namespace object having the ID field populated.
     *
     * @throws  SystemException  If a duplicate namespace exists.
     */
    @Override
    public Namespace createNamespace(Namespace namespace) {
        requireNotDisposed();
        requireArgument(namespace != null, "null namespace cannot be created.");
        
        if (!_validateQualifier(namespace.getQualifier())) {
            throw new SystemException(new IllegalArgumentException(
                    "Illegal characters found while generating namespace. Cannot generate a namespace with this qualifier."));
        }
        
        if (Namespace.findByQualifier(emf.get(), namespace.getQualifier()) != null) {
            throw new SystemException(new IllegalArgumentException("Namespace already exists. Please try a different namespace."));
        }
        
        namespace = updateNamespace(namespace);
        _logger.debug("Generated namespace {}.", namespace);
        return namespace;
    }

    private boolean _validateQualifier(String qualifierSuffix) {
        if (qualifierSuffix == null || qualifierSuffix.isEmpty()) {
            throw new SystemException(new IllegalArgumentException("Namespace qualifier cannot be null or empty."));
        }

        char[] charArray = qualifierSuffix.toCharArray();

        for (char c : charArray) {
            if (!_isAllowed(c)) {
                return false;
            }
        }
        return true;
    }

    private boolean _isAllowed(char c) {
        if (Character.isLetterOrDigit(c) || c == '.' || c == '-' || c == '_' || c == '/') {
            return true;
        }
        return false;
    }

    @Override
    @Transactional
    public Namespace updateNamespace(Namespace namespace) {
        requireNotDisposed();
        requireArgument(namespace != null, "Namespace cannot be null.");

        EntityManager em = emf.get();
        Namespace result = mergeEntity(em, namespace);

        _logger.debug("Updated namespace to : {}", result);
        _auditService.createAudit("Updated namespace : {0}", result, result);
        em.flush();
        return result;
    }

    @Override
    @Transactional
    public boolean isPermitted(String qualifier, PrincipalUser user) {
        requireNotDisposed();
        requireArgument(user != null, "Principal user cannot be null.");
        if (qualifier == null || qualifier.isEmpty()) {
            return true;
        }

        Namespace namespace = Namespace.findByQualifier(emf.get(), qualifier);

        if (namespace == null) {
            _logger.warn(MessageFormat.format("Namespace {0} does not exist", qualifier));
        } else {
            if (namespace.getUsers().contains(user)) {
                return true;
            } else {
                _logger.warn(MessageFormat.format("User {0} is not permitted to use namespace {1}", user.getUserName(), qualifier));
            }
        }
        return false;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

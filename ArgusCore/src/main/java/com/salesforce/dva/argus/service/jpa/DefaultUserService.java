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
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import org.slf4j.Logger;
import java.lang.reflect.Method;
import java.math.BigInteger;
import javax.persistence.EntityManager;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;
import static java.math.BigInteger.ZERO;

/**
 * Default implementation of the <tt>UserService</tt> interface.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class DefaultUserService extends DefaultJPAService implements UserService {

    //~ Instance fields ******************************************************************************************************************************

    @SLF4JTypeListener.InjectLogger
    private Logger _logger;
    @Inject
    Provider<EntityManager> emf;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new DefaultUserService object.
     *
     * @param  auditService  The audit service. Cannot be null.
     * @param _sysConfig Service properties
     */
    @Inject
    public DefaultUserService(AuditService auditService, SystemConfiguration _sysConfig) {
        super(auditService, _sysConfig);
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    @Transactional
    public PrincipalUser findUserByUsername(String userName) {
        requireNotDisposed();
        requireArgument(userName != null && !userName.trim().isEmpty(), "User name cannot be null or empty.");

        PrincipalUser result = PrincipalUser.findByUserName(emf.get(), userName);

        _logger.debug("Query for user having username {} resulted in : {}", userName, result);
        return result;
    }

    @Override
    @Transactional
    public PrincipalUser findUserByPrimaryKey(BigInteger id) {
        requireNotDisposed();
        requireArgument(id != null && id.compareTo(ZERO) > 0, "ID must be a positive non-zero value.");

        PrincipalUser result = findEntity(emf.get(), id, PrincipalUser.class);

        _logger.debug("Query for user having id {} resulted in : {}", id, result);
        return result;
    }

    @Override
    @Transactional
    public void deleteUser(PrincipalUser user) {
        requireNotDisposed();
        requireArgument(user != null && user.getId() != null && user.getId().compareTo(ZERO) > 0, "User cannot be null and must have a valid ID.");
        _logger.debug("Deleting user {}.", user);

        EntityManager em = emf.get();

        deleteEntity(em, user);
        em.flush();
    }

    @Override
    @Transactional
    public PrincipalUser updateUser(PrincipalUser user) {
        requireNotDisposed();
        requireArgument(user != null, "User cannot be null.");

        EntityManager em = emf.get();
        PrincipalUser result = mergeEntity(em, user);

        _logger.debug("Updated user to : {}", result);
        _auditService.createAudit("Updated user : {0}", result, result);
        em.flush();
        return result;
    }

    @Override
    @Transactional
    public synchronized PrincipalUser findAdminUser() {
        requireNotDisposed();

        PrincipalUser result;

        _logger.debug("Retrieving the administrative user.");
        if ((result = findUserByPrimaryKey(BigInteger.ONE)) == null) {
            try {
                Method method = PrincipalUser.class.getDeclaredMethod("createAdminUser", new Class<?>[0]);

                method.setAccessible(true);
                result = updateUser(PrincipalUser.class.cast(method.invoke(null, new Object[0])));
                method.setAccessible(false);
            } catch (Exception ex) {
                throw new SystemException(ex);
            }
        }
        return result;
    }
    
    @Override
    @Transactional
    public synchronized PrincipalUser findDefaultUser() {
        requireNotDisposed();

        PrincipalUser result;

        _logger.debug("Retrieving the default user.");
        if ((result = findUserByPrimaryKey(BigInteger.valueOf(2))) == null) {
            try {
                Method method = PrincipalUser.class.getDeclaredMethod("createDefaultUser", new Class<?>[0]);

                method.setAccessible(true);
                result = updateUser(PrincipalUser.class.cast(method.invoke(null, new Object[0])));
                method.setAccessible(false);
            } catch (Exception ex) {
                throw new SystemException(ex);
            }
        }
        return result;
    }

    @Override
    @Transactional
    public long getUniqueUserCount() {
        requireNotDisposed();
        return PrincipalUser.findUniqueUserCount(emf.get());
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

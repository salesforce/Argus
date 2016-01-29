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
import com.salesforce.dva.argus.entity.ServiceManagementRecord;
import com.salesforce.dva.argus.entity.ServiceManagementRecord.Service;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.ServiceManagementService;
import com.salesforce.dva.argus.system.SystemConfiguration;

import org.slf4j.Logger;
import javax.persistence.EntityManager;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Default implementation of the ServiceManagementService.
 *
 * @author  Raj sarkapally (rsarkapally@salesforce.com)
 */
public class DefaultServiceManagementService extends DefaultJPAService implements ServiceManagementService {

    //~ Instance fields ******************************************************************************************************************************

    @SLF4JTypeListener.InjectLogger
    private Logger _logger;
    @Inject
    Provider<EntityManager> emf;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new DefaultSubsystemMgmtRecordService object.
     *
     * @param  auditService  The audit service. Cannot be null.
     * @param _sysConfig Service properties
     */
    @Inject
    public DefaultServiceManagementService(AuditService auditService, SystemConfiguration _sysConfig) {
        super(auditService, _sysConfig);
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    @Transactional
    public ServiceManagementRecord updateServiceManagementRecord(ServiceManagementRecord serviceManagementRecord) {
        requireNotDisposed();
        requireArgument(serviceManagementRecord != null, "Cannot update a null sub system.");

        EntityManager em = emf.get();
        ServiceManagementRecord result = ServiceManagementRecord.updateServiceManagementRecord(em, serviceManagementRecord);

        em.flush();
        _logger.debug("Updated service management record {}.", result);
        _auditService.createAudit("Updated service management record : {0}", result, result);
        return result;
    }

    @Override
    @Transactional
    public boolean isServiceEnabled(Service service) {
        requireNotDisposed();
        requireArgument(service != null, "Service cannot be null.");

        boolean result = ServiceManagementRecord.isServiceEnabled(emf.get(), service);

        _logger.debug("The {} service enabled flag is {}", service, result);
        return result;
    }

    @Override
    @Transactional
    public ServiceManagementRecord findServiceManagementRecord(Service service) {
        requireNotDisposed();
        requireArgument(service != null, "Service cannot be null.");

        ServiceManagementRecord result = ServiceManagementRecord.findServiceManagementRecord(emf.get(), service);

        if (result != null) {
            _logger.debug("Located service management record: {}", result);
        } else {
            _logger.debug("Could not locate service management record for {}", service);
        }
        return result;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

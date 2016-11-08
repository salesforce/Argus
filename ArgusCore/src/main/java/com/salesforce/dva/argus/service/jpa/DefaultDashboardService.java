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
import com.salesforce.dva.argus.entity.Dashboard;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.DashboardService;
import com.salesforce.dva.argus.system.SystemConfiguration;

import org.slf4j.Logger;
import java.math.BigInteger;
import java.util.List;
import javax.persistence.EntityManager;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;
import static java.math.BigInteger.ZERO;

/**
 * Default implementation of the <tt>DashboardService</tt> interface.
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
public class DefaultDashboardService extends DefaultJPAService implements DashboardService {

    //~ Instance fields ******************************************************************************************************************************

    @SLF4JTypeListener.InjectLogger
    private Logger _logger;
    @Inject
    Provider<EntityManager> emf;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new DefaultDashboardService object.
     *
     * @param  auditService  The audit service. Cannot be null.
     * @param _sysConfig Service properties
     */
    @Inject
    public DefaultDashboardService(AuditService auditService, SystemConfiguration _sysConfig) {
        super(auditService, _sysConfig);
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    @Transactional
    public Dashboard findDashboardByNameAndOwner(String name, PrincipalUser owner) {
        requireNotDisposed();
        requireArgument(name != null && !name.isEmpty(), "Dashboard name cannot be null or empty");
        requireArgument(owner != null, "Owner of the dashboard cannot be null");
        _logger.debug("Querying Dashboard by name: {} and owned by: {}", name, owner);

        Dashboard dashboard = Dashboard.findByNameAndOwner(emf.get(), name, owner);

        _logger.debug("Found Dashboard: {}", dashboard);
        return dashboard;
    }

    @Override
    @Transactional
    public Dashboard findDashboardByPrimaryKey(BigInteger id) {
        requireNotDisposed();
        requireArgument(id != null && id.compareTo(ZERO) > 0, "ID must be a positive non-zero value.");

        Dashboard result = findEntity(emf.get(), id, Dashboard.class);

        _logger.debug("Query for dashboard having id {} resulted in : {}", id, result);
        return result;
    }

    @Override
    @Transactional
    public Dashboard updateDashboard(Dashboard dashboard) {
        requireNotDisposed();
        requireArgument(dashboard != null, "Cannot update a null dashboard");

        EntityManager em = emf.get();
        Dashboard result = mergeEntity(em, dashboard);

        em.flush();
        _logger.debug("Updated dashboard to : {}", result);
        _auditService.createAudit("Updated dashboard : {0}", result, result);
        return result;
    }

    @Override
    @Transactional
    public void deleteDashboard(Dashboard dashboard) {
        requireNotDisposed();
        requireArgument(dashboard != null, "Cannot delete a null dashboard. No such dashboard exists.");
        _logger.debug("Deleting dashboard {}.", dashboard);

        EntityManager em = emf.get();

        deleteEntity(em, dashboard);
        em.flush();
    }

    @Override
    @Transactional
    public void deleteDashboard(String dashboardName, PrincipalUser owner) {
        requireNotDisposed();
        requireArgument(dashboardName != null && !dashboardName.isEmpty(), "Dashboard name cannot be null or empty.");
        requireArgument(owner != null, "Owner cannot be null");

        Dashboard dashboard = findDashboardByNameAndOwner(dashboardName, owner);

        deleteDashboard(dashboard);
    }

    @Override
    @Transactional
    public void deleteDashboard(BigInteger id) {
        requireNotDisposed();
        requireArgument(id != null && BigInteger.ZERO.compareTo(id) == -1, "Dashboard ID must be greater than zero.");

        Dashboard dashboard = findDashboardByPrimaryKey(id);

        deleteDashboard(dashboard);
    }

    @Override
    @Transactional
    public List<Dashboard> findSharedDashboards() {
        requireNotDisposed();
        return Dashboard.findSharedDashboards(emf.get());
    }

    @Override
    @Transactional
    public List<Dashboard> findDashboardsByOwner(PrincipalUser user) {
        requireNotDisposed();
        requireArgument(user != null, "Owner cannot be null");
        return Dashboard.findDashboardsByOwner(emf.get(), user);
    }

    @Override
    @Transactional
    public List<Dashboard> findDashboards(Integer limit) {
        requireNotDisposed();
        requireArgument(limit == null || limit > 0, "Cannot get 0 or negative number of dashboards.");
        return Dashboard.findDashboards(emf.get(), limit);
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

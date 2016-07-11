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
	 
package com.salesforce.dva.argus.service;

import com.google.inject.Inject;
import com.google.inject.Provider;

/**
 * The system service factory module. All services should be obtained from this class via injection.
 *
 * @author  Tom Valine (tvaline@salesforce.com), Bhinav Sura (bhinav.sura@salesforce.com)
 */
public final class ServiceFactory {

    //~ Instance fields ******************************************************************************************************************************

    @Inject
    Provider<TSDBService> _tsdbServiceProvider;
    @Inject
    Provider<CollectionService> _collectionServiceProvider;
    @Inject
    Provider<MQService> _mqServiceProvider;
    @Inject
    Provider<UserService> _userServiceProvider;
    @Inject
    Provider<DashboardService> _dashboardServiceProvider;
    @Inject
    Provider<AlertService> _alertServiceProvider;
    @Inject
    Provider<MetricService> _metricServiceProvider;
    @Inject
    Provider<SchedulingService> _schedulingServiceProvider;
    @Inject
    Provider<GlobalInterlockService> _globalInterlockServiceProvider;
    @Inject
    Provider<MonitorService> _monitorServiceProvider;
    @Inject
    Provider<AnnotationService> _annotationServiceProvider;
    @Inject
    Provider<WardenService> _wardenServiceProvider;
    @Inject
    Provider<ManagementService> _managementServiceProvider;
    @Inject
    Provider<AuditService> _auditServiceProvider;
    @Inject
    Provider<MailService> _mailServiceProvider;
    @Inject
    Provider<AuthService> _authServiceProvider;
    @Inject
    Provider<HistoryService> _historyServiceProvider;
    @Inject
    Provider<SchemaService> _schemaServiceProvider;
    @Inject
    Provider<NamespaceService> _namespaceServiceProvider;
    @Inject
    Provider<CacheService> _cacheServiceProvider;
    @Inject
    Provider<DiscoveryService> _discoveryServiceProvider;
    @Inject
    Provider<BatchService> _batchServiceProvider;


    //~ Methods **************************************************************************************************************************************

    /**
     * Returns an instance of the TSDB service.
     *
     * @return  An instance of the TSDB service.
     */
    public synchronized TSDBService getTSDBService() {
        return _tsdbServiceProvider.get();
    }

    /**
     * Returns an instance of the Collection service.
     *
     * @return  An instance of the Collection service.
     */
    public synchronized CollectionService getCollectionService() {
        return _collectionServiceProvider.get();
    }

    /**
     * Returns an instance of the MQ service.
     *
     * @return  An instance of the MQ service.
     */
    public synchronized MQService getMQService() {
        return _mqServiceProvider.get();
    }

    /**
     * Returns an instance of the user service.
     *
     * @return  An instance of the user service.
     */
    public synchronized UserService getUserService() {
        return _userServiceProvider.get();
    }

    /**
     * Returns an instance of the dashboard service.
     *
     * @return  An instance of the dashboard service.
     */
    public synchronized DashboardService getDashboardService() {
        return _dashboardServiceProvider.get();
    }

    /**
     * Returns an instance of the alert service.
     *
     * @return  An instance of the alert service.
     */
    public synchronized AlertService getAlertService() {
        return _alertServiceProvider.get();
    }

    /**
     * Returns an instance of the metric service.
     *
     * @return  An instance of the metric service.
     */
    public synchronized MetricService getMetricService() {
        return _metricServiceProvider.get();
    }

    /**
     * Returns an instance of the scheduling service.
     *
     * @return  An instance of the scheduling service.
     */
    public synchronized SchedulingService getSchedulingService() {
        return _schedulingServiceProvider.get();
    }

    /**
     * Returns an instance of the global interlock service.
     *
     * @return  An instance of the global interlock service.
     */
    public synchronized GlobalInterlockService getGlobalInterlockService() {
        return _globalInterlockServiceProvider.get();
    }

    /**
     * Returns an instance of the monitor service.
     *
     * @return  An instance of the monitor service.
     */
    public synchronized MonitorService getMonitorService() {
        return _monitorServiceProvider.get();
    }

    /**
     * Returns an instance of the mail service.
     *
     * @return  An instance of the mail service.
     */
    public synchronized MailService getMailService() {
        return _mailServiceProvider.get();
    }

    /**
     * Returns an instance of the annotation service.
     *
     * @return  An instance of the annotation service.
     */
    public synchronized AnnotationService getAnnotationService() {
        return _annotationServiceProvider.get();
    }

    /**
     * Returns an instance of the warden service.
     *
     * @return  An instance of the warden service.
     */
    public synchronized WardenService getWardenService() {
        return _wardenServiceProvider.get();
    }

    /**
     * Returns an instance of the management service.
     *
     * @return  An instance of the management service.
     */
    public synchronized ManagementService getManagementService() {
        return _managementServiceProvider.get();
    }

    /**
     * Returns an instance of the audit service.
     *
     * @return  An instance of the audit service.
     */
    public synchronized AuditService getAuditService() {
        return _auditServiceProvider.get();
    }

    /**
     * Returns an instance of the authentication service.
     *
     * @return  An instance of the audit service.
     */
    public synchronized AuthService getAuthService() {
        return _authServiceProvider.get();
    }

    /**
     * Returns an instance of the job history service.
     *
     * @return  An instance of the job history service.
     */
    public synchronized HistoryService getHistoryService() {
        return _historyServiceProvider.get();
    }

    /**
     * Returns an instance of the schema service.
     *
     * @return  An instance of the schema service.
     */
    public synchronized SchemaService getSchemaService() {
        return _schemaServiceProvider.get();
    }

    /**
     * Returns an instance of the namespace service.
     *
     * @return  An instance of the namespace service.
     */
    public synchronized NamespaceService getNamespaceService() {
        return _namespaceServiceProvider.get();
    }

    /**
     * Returns an instance of the cache service.
     *
     * @return  An instance of the cache service.
     */
    public synchronized CacheService getCacheService() {
        return _cacheServiceProvider.get();
    }

    /**
     * Returns an instance of the Discovery service.
     *
     * @return  An instance of the Discovery service.
     */
    public synchronized DiscoveryService getDiscoveryService() {
        return _discoveryServiceProvider.get();
    }

    /**
     * Returns an instance of the batch service.
     *
     * @return  An instance of the batch service.
     */
    public synchronized BatchService getBatchService() {
        return _batchServiceProvider.get();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

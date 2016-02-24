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
	 
package com.salesforce.dva.argus.client;

import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemMain;
import java.text.MessageFormat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Creates and starts the appropriate client services based on the configured client type.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
class ClientServiceFactory {

    //~ Methods **************************************************************************************************************************************

    static ExecutorService startClientService(SystemMain system, ClientType clientType, AtomicInteger jobCounter) {
        switch (clientType) {
            case ALERT:
                return startAlertClientService(system, jobCounter);
            case COMMIT_SCHEMA:

                /* Alpha feature, not currently supported. */
                return startCommitSchemaClientService(system, jobCounter);
            case COMMIT_ANNOTATIONS:
                return startCommitAnnotationsClientService(system, jobCounter);
            default:
                return startCommitMetricsClientService(system, jobCounter);
        }
    }

    private static ExecutorService startAlertClientService(SystemMain system, AtomicInteger jobCounter) {
        int configuredCount = Integer.valueOf(system.getConfiguration().getValue(SystemConfiguration.Property.CLIENT_THREADS));
        int configuredTimeout = Integer.valueOf(system.getConfiguration().getValue(SystemConfiguration.Property.CLIENT_CONNECT_TIMEOUT));
        int threadPoolCount = Math.max(configuredCount, 2);
        int timeout = Math.max(10000, configuredTimeout);
        ExecutorService service = Executors.newFixedThreadPool(threadPoolCount, new ThreadFactory() {

                AtomicInteger id = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, MessageFormat.format("alertclient-{0}", id.getAndIncrement()));
                }
            });
        system.getServiceFactory().getMonitorService().startRecordingCounters();
        for (int i = 0; i < threadPoolCount; i++) {
            service.submit(new Alerter(system.getServiceFactory().getAlertService(), timeout, jobCounter));
        }
        return service;
    }

    private static ExecutorService startCommitAnnotationsClientService(SystemMain system, AtomicInteger jobCounter) {
        int configuredCount = Integer.valueOf(system.getConfiguration().getValue(SystemConfiguration.Property.CLIENT_THREADS));
        int threadPoolCount = Math.max(configuredCount, 2);
        ExecutorService service = Executors.newFixedThreadPool(threadPoolCount, new ThreadFactory() {

                AtomicInteger id = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, MessageFormat.format("annotationcommitclient-{0}", id.getAndIncrement()));
                }
            });
        
        for (int i = 0; i < threadPoolCount; i++) {
            service.submit(new AnnotationCommitter(system.getServiceFactory().getCollectionService(),system.getServiceFactory().getMonitorService(), jobCounter));
        }
        return service;
    }

    private static ExecutorService startCommitMetricsClientService(SystemMain system, AtomicInteger jobCounter) {
        int configuredCount = Integer.valueOf(system.getConfiguration().getValue(SystemConfiguration.Property.CLIENT_THREADS));
        int threadPoolCount = Math.max(configuredCount, 2);
        ExecutorService service = Executors.newFixedThreadPool(threadPoolCount, new ThreadFactory() {

                AtomicInteger id = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, MessageFormat.format("metriccommitclient-{0}", id.getAndIncrement()));
                }
            });
        system.getServiceFactory().getMonitorService().startRecordingCounters();
        for (int i = 0; i < threadPoolCount; i++) {
            service.submit(new MetricCommitter(system.getServiceFactory().getCollectionService(),system.getServiceFactory().getMonitorService(), jobCounter));
        }
        return service;
    }

    private static ExecutorService startCommitSchemaClientService(SystemMain system, AtomicInteger jobCounter) {
        int configuredCount = Integer.valueOf(system.getConfiguration().getValue(SystemConfiguration.Property.CLIENT_THREADS));
        int threadPoolCount = Math.max(configuredCount, 2);
        ExecutorService service = Executors.newFixedThreadPool(threadPoolCount, new ThreadFactory() {

                AtomicInteger id = new AtomicInteger(0);

                @Override
                public Thread newThread(Runnable r) {
                    return new Thread(r, MessageFormat.format("schemacommitclient-{0}", id.getAndIncrement()));
                }
            });
        for (int i = 0; i < threadPoolCount; i++) {
            service.submit(new SchemaCommitter(system.getServiceFactory().getCollectionService(),system.getServiceFactory().getMonitorService(), jobCounter));
        }
        return service;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

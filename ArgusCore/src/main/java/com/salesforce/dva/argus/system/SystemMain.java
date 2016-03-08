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
package com.salesforce.dva.argus.system;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.persist.PersistService;
import com.google.inject.persist.UnitOfWork;
import com.salesforce.dva.argus.service.AlertService.Notifier;
import com.salesforce.dva.argus.service.NotifierFactory;
import com.salesforce.dva.argus.service.Service;
import com.salesforce.dva.argus.service.ServiceFactory;
import static com.salesforce.dva.argus.system.SystemAssert.requireState;

import java.lang.reflect.Method;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import java.util.Map.Entry;

/**
 * Loads the system configuration from disk and provides start up and shut down methods. Calling the shut down method before the start up method has
 * been called and completed will block until start up is complete or the current thread of execution is interrupted. This class is thread safe.
 *
 * @author Tom Valine (tvaline@salesforce.com), Bhinav Sura (bhinav.sura@salesforce.com)
 */
public final class SystemMain extends SystemService {

    //~ Instance fields ******************************************************************************************************************************
    private final SystemConfiguration _configuration;
    private final ServiceFactory _serviceFactory;
    private final NotifierFactory _notifierFactory;
    private final PersistService _persistService;
    private final UnitOfWork _uof;

    //~ Constructors *********************************************************************************************************************************
    @Inject
    private SystemMain(PersistService persistService, ServiceFactory factory, NotifierFactory notifierFactory, SystemConfiguration config, UnitOfWork uof) {
        _persistService = persistService;
        _serviceFactory = factory;
        _notifierFactory = notifierFactory;
        _configuration = config;
        _uof = uof;
    }

    //~ Methods **************************************************************************************************************************************
    /**
     * Returns an instance of the system. Configuration is read from the default locations.
     *
     * @return An instance of the system.
     *
     * @see SystemConfiguration
     */
    public static SystemMain getInstance() {
        return getInstance(null);
    }

    /**
     * Returns an instance of the system. Configuration is read from the supplied properties file.
     *
     * @param config If null, configuration is read from the default location.
     *
     * @return An instance of the system.
     *
     * @see SystemConfiguration
     */
    public static SystemMain getInstance(Properties config) {
        return Guice.createInjector(new SystemInitializer(config)).getInstance(SystemMain.class);
    }

    //~ Methods **************************************************************************************************************************************
    /**
     * Starts the system.
     */
    @Override
    protected void doStart() {
        try {
            String build = _configuration.getValue(SystemConfiguration.Property.BUILD);
            String version = _configuration.getValue(SystemConfiguration.Property.VERSION);
            String year = new SimpleDateFormat("yyyy").format(new Date());

            _log.info("Argus version {} build {}.", version, build);
            _log.info("Copyright Salesforce.com, {}.", year);
            _log.info("{} started.", getName());
            _persistService.start();
            _serviceFactory.getUserService().findAdminUser();
            _serviceFactory.getUserService().findDefaultUser();
        } catch (Exception ex) {
            _log.error(getName() + " startup aborted.", ex);
        } finally {
            _mergeServiceConfiguration();
            _mergeNotifierConfiguration();
            _log.info(_configuration.toString());
        }
    }

    /**
     * Stops the system.
     */
    @Override
    protected void doStop() {
        try {
            _dispose(_serviceFactory.getWardenService());
            _dispose(_serviceFactory.getMonitorService());
            _dispose(_serviceFactory.getSchedulingService());
            _dispose(_serviceFactory.getGlobalInterlockService());
            _dispose(_serviceFactory.getMQService());
            _dispose(_serviceFactory.getSchemaService());
            _dispose(_serviceFactory.getTSDBService());
            _persistService.stop();
            _log.info("{} stopped.", getName());
        } catch (Exception ex) {
            _log.error(getName() + " shutdown aborted.", ex);
        }
    }

    /**
     * Returns the service factory from which service implementations instances can be obtained.
     *
     * @return The service factory.
     */
    public ServiceFactory getServiceFactory() {
        return _serviceFactory;
    }
    
    /**
     * Returns the notifier factory from which notifier implementations instances can be obtained.
     *
     * @return The notifier factory.
     */
    public NotifierFactory getNotifierFactory() {
        return _notifierFactory;
    }

    /**
     * Returns a copy of the configuration information for the system.
     *
     * @return The configuration information for the system. Will never return null.
     */
    public SystemConfiguration getConfiguration() {
        return new SystemConfiguration(_configuration);
    }

    /**
     * Returns the transactional unit of work.
     *
     * @return The unit of work.
     */
    public UnitOfWork getUnitOfWork() {
        return _uof;
    }

    private void _dispose(Service service) {
        if (!service.isDisposed()) {
            try {
                service.dispose();
            } catch (Exception ex) {
                _log.warn("Error disposing service: " + ex.getMessage());
            }
        }
    }

    /**
     * Merges the implementation specific service configuration properties with the system level configuration properties. This method examines the
     * implementation specific service configuration property keys as obtained by the <tt>getServiceProperties()</tt> method and determines if the
     * system configuration contains the property. If the system configuration does not contain the property, the method inserts the default value
     * specified in the result of <tt>getServiceProperties()</tt> method into the system configuration.
     *
     * @param implementationSpecificProperties The implementation specific service configuration property defaults to merge if they are absent from
     *                                         the system configuration.
     */
    protected final void _mergeProperties(Properties implementationSpecificProperties) {
        for (Entry<Object, Object> entry : implementationSpecificProperties.entrySet()) {
            _configuration.putIfAbsent(entry.getKey(), entry.getValue());
        }
    }

    private void _mergeServiceConfiguration() {
        Method[] methods = _serviceFactory.getClass().getDeclaredMethods();

        for (Method method : methods) {
            if (Service.class.isAssignableFrom(method.getReturnType())) {
                try {
                    boolean accessible = method.isAccessible();
                    method.setAccessible(true);
                    Service service = Service.class.cast(method.invoke(_serviceFactory, new Object[]{}));
                    method.setAccessible(accessible);
                    _mergeProperties(service.getServiceProperties());
                } catch (Exception e) {
                    requireState(false, "Failed to load service properties for service factory method " + method.getName());
                }
            }
        }

    }
    
    /**
     * Merges the implementation specific notifier configuration properties with the system level configuration properties. This method examines the
     * implementation specific notifier configuration property keys as obtained by the <tt>getNotifierProperties()</tt> method and determines if the
     * system configuration contains the property. If the system configuration does not contain the property, the method inserts the default value
     * specified in the result of <tt>getNotifierProperties()</tt> method into the system configuration.
     *
     * @param implementationSpecificNotifierProperties The implementation specific notifier configuration property defaults to merge if they are absent from
     *                                         the system configuration.
     */

    private void _mergeNotifierConfiguration() {
        Method[] methods = _notifierFactory.getClass().getDeclaredMethods();

        for (Method method : methods) {
            if (Notifier.class.isAssignableFrom(method.getReturnType())) {
                try {
                    boolean accessible = method.isAccessible();
                    method.setAccessible(true);
                    Notifier notifier = Notifier.class.cast(method.invoke(_notifierFactory, new Object[]{}));
                    method.setAccessible(accessible);
                    _mergeProperties(notifier.getNotifierProperties());
                } catch (Exception e) {
                    requireState(false, "Failed to load notifier properties for notifier factory method " + method.getName());
                }
            }
        }

    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

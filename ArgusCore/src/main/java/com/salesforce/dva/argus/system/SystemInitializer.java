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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;

import com.google.inject.AbstractModule;
import com.google.inject.matcher.Matchers;
import com.google.inject.persist.jpa.JpaPersistModule;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.*;
import com.salesforce.dva.argus.service.annotation.DefaultAnnotationService;
import com.salesforce.dva.argus.service.batch.DefaultBatchService;
import com.salesforce.dva.argus.service.collect.DefaultCollectionService;
import com.salesforce.dva.argus.service.jpa.DefaultChartService;
import com.salesforce.dva.argus.service.jpa.DefaultDashboardService;
import com.salesforce.dva.argus.service.jpa.DefaultDistributedSchedulingLockService;
import com.salesforce.dva.argus.service.jpa.DefaultGlobalInterlockService;
import com.salesforce.dva.argus.service.jpa.DefaultNamespaceService;
import com.salesforce.dva.argus.service.jpa.DefaultServiceManagementService;
import com.salesforce.dva.argus.service.management.DefaultManagementService;
import com.salesforce.dva.argus.service.metric.AsyncMetricService;
import com.salesforce.dva.argus.service.monitor.DefaultMonitorService;
import com.salesforce.dva.argus.service.oauth.DefaultOAuthAuthorizationCodeService;
import com.salesforce.dva.argus.service.schema.CachedDiscoveryService;
import com.salesforce.dva.argus.service.schema.DefaultDiscoveryService;
import com.salesforce.dva.argus.service.tsdb.CachedTSDBService;
import com.salesforce.dva.argus.service.users.CachedUserService;
import com.salesforce.dva.argus.service.users.DefaultUserService;
import com.salesforce.dva.argus.system.SystemConfiguration.Property;

import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

/**
 * Sets up the application based on information read from the configuration files.
 *
 * @author  Tom Valine (tvaline@salesforce.com), Bhinav Sura (bhinav.sura@salesforce.com)
 */
final class SystemInitializer extends AbstractModule {
    private static final String JPA_PROPERTY_PREFIX = "system.property.jpa.";

    //~ Instance fields ******************************************************************************************************************************

    private final Properties _config;
    private SystemConfiguration _systemConfiguration;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new SystemInitializer object.
     *
     * @param  config  The configuration used to initialize the system.
     */
    SystemInitializer(Properties config) {
        if (config == null) {
            config = readConfigInfo();
        }
        _config = config;
    }

    //~ Methods **************************************************************************************************************************************

    private static void readClasspath(Properties props, String path) {
        if ((path != null) && !path.isEmpty()) {
            InputStream is = null;
            Properties result = new Properties();

            try {
                is = SystemConfiguration.class.getResourceAsStream(path);
                result.load(is);
                props.putAll(result);
            } catch (IOException ex) {
                throw new SystemException(ex);
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException ex) {
                        assert false : "This should never occur.";
                    }
                }
            }
        }
    }

    private static Properties readConfigInfo() {
        Properties props = new Properties();

        readFile(props, System.getProperty(SystemConfiguration.GLOBAL_CONFIG_LOCATION));
        readFile(props, System.getProperty(SystemConfiguration.LOCAL_CONFIG_LOCATION));
        readClasspath(props, "/META-INF/build.properties");
        return props;
    }

    private static void readFile(Properties props, String filePath) {
        if ((filePath != null) && !filePath.isEmpty()) {
            InputStream is = null;
            Properties result = new Properties();

            try {
                is = new FileInputStream(filePath);
                result.load(is);
                props.putAll(result);
            } catch (IOException ex) {
                LoggerFactory.getLogger("com.salesforce.dva.argus").warn("Unable to load properties file \"{}\". Reason: {}", filePath,
                    ex.getMessage());
            } finally {
                if (is != null) {
                    try {
                        is.close();
                    } catch (IOException ex) {
                        assert false : "This should never occur.";
                    }
                }
            }
        }
    }

    //~ Methods **************************************************************************************************************************************

    /** @see  AbstractModule#configure() */
    @Override
    protected void configure() {
        configureLogging();
        configureSystem();
        configurePersistence();
        configureServices();
    }

    private void configureSystem() {
        _systemConfiguration = new SystemConfiguration(_config);

        Logger app = Logger.class.cast(LoggerFactory.getLogger("com.salesforce.dva.argus"));

        app.setLevel(Level.toLevel(_systemConfiguration.getValue(SystemConfiguration.Property.LOG_LEVEL)));
        bind(SystemConfiguration.class).toInstance(_systemConfiguration);
        bindListener(Matchers.any(), new SLF4JTypeListener());
        _systemConfiguration.putAll(getServiceSpecificProperties());      
    }

    private void configurePersistence() {
        JpaPersistModule jpaPersistModule = new JpaPersistModule("argus-pu");
        Properties jpaProperties = getJpaProperties(_systemConfiguration);
        jpaPersistModule.properties(jpaProperties);
        binder().install(jpaPersistModule);
    }

    private Properties getJpaProperties(Properties properties) {
        Properties jpaProperties = new Properties();
        for (Map.Entry<Object, Object> entry : properties.entrySet()) {
            String propertyNameWithPrefix = (String) entry.getKey();
            if (propertyNameWithPrefix.startsWith(JPA_PROPERTY_PREFIX)) {
                String propertyName = propertyNameWithPrefix.substring(JPA_PROPERTY_PREFIX.length());
                jpaProperties.put(propertyName, entry.getValue());
            }
        }
        return jpaProperties;
    }

    private void configureLogging() {
        InputStream is = null;

        try {
            String rootName = Logger.ROOT_LOGGER_NAME;
            Logger root = (Logger) LoggerFactory.getLogger(rootName);
            LoggerContext context = root.getLoggerContext();
            JoranConfigurator configurator = new JoranConfigurator();

            is = getClass().getResourceAsStream("/META-INF/logback.xml");
            context.reset();
            configurator.setContext(context);
            configurator.doConfigure(is);
            root.setLevel(Level.ERROR);
        } catch (JoranException ex) {
            throw new SystemException(ex);
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException ex) {
                    assert false : "This should never occur.";
                }
            }
        }
    }

    private void configureServices() {
        bindConcreteClass(Property.CACHE_SERVICE_IMPL_CLASS, CacheService.class);
        bindConcreteClass(Property.MQ_SERVICE_IMPL_CLASS, MQService.class);
        bindConcreteClass(Property.ALERT_SERVICE_IMPL_CLASS, AlertService.class);
        bindConcreteClass(Property.SCHEDULING_SERVICE_IMPL_CLASS, SchedulingService.class);
        bindConcreteClass(Property.MAIL_SERVICE_IMPL_CLASS, MailService.class);
        bindConcreteClass(Property.AUTH_SERVICE_IMPL_CLASS, AuthService.class);
        bindConcreteClass(Property.SCHEMA_SERVICE_IMPL_CLASS, SchemaService.class);
        bindConcreteClass(Property.HISTORY_SERVICE_IMPL_CLASS, HistoryService.class);
        bindConcreteClass(Property.AUDIT_SERVICE_IMPL_CLASS, AuditService.class);
        bindConcreteClass(Property.CALLBACK_SERVICE_IMPL_CLASS, CallbackService.class);
        bindConcreteClass(Property.WARDEN_SERVICE_IMPL_CLASS, WardenService.class);

        // Named annotation binding
        bindConcreteClassWithNamedAnnotation(getConcreteClassToBind(Property.TSDB_SERVICE_IMPL_CLASS, TSDBService.class), TSDBService.class);
        bindConcreteClassWithNamedAnnotation(DefaultDiscoveryService.class, DiscoveryService.class);
        bindConcreteClassWithNamedAnnotation(DefaultUserService.class, UserService.class);

        // static binding
        bindConcreteClass(CachedTSDBService.class, TSDBService.class);
        bindConcreteClass(CachedUserService.class, UserService.class);
        bindConcreteClass(DefaultDashboardService.class, DashboardService.class);
        bindConcreteClass(DefaultOAuthAuthorizationCodeService.class, OAuthAuthorizationCodeService.class);
        bindConcreteClass(DefaultCollectionService.class, CollectionService.class);
        bindConcreteClass(AsyncMetricService.class, MetricService.class);
        bindConcreteClass(DefaultBatchService.class, BatchService.class);
        bindConcreteClass(DefaultGlobalInterlockService.class, GlobalInterlockService.class);
        bindConcreteClass(DefaultMonitorService.class, MonitorService.class);
        bindConcreteClass(DefaultAnnotationService.class, AnnotationService.class);
        bindConcreteClass(DefaultManagementService.class, ManagementService.class);
        bindConcreteClass(DefaultServiceManagementService.class, ServiceManagementService.class);
        bindConcreteClass(DefaultNamespaceService.class, NamespaceService.class);
        bindConcreteClass(CachedDiscoveryService.class, DiscoveryService.class);
        bindConcreteClass(DefaultDistributedSchedulingLockService.class, DistributedSchedulingLockService.class);
        bindConcreteClass(DefaultChartService.class, ChartService.class);
    }

    private <T> void bindConcreteClass(Property property, Class<T> type) {
        bind(type).to(getConcreteClassToBind(property, type));
    }

    private <I, T extends I> void bindConcreteClassWithNamedAnnotation(Class<T> implClass, Class<I> interfaceType) {
        bind(interfaceType).annotatedWith(NamedBinding.class).to(implClass);
    }

    @SuppressWarnings("unchecked")
    private <T> Class<? extends T> getConcreteClassToBind(Property property, Class<T> type) {
        try {
            return (Class<? extends T>) Class.forName(_systemConfiguration.getValue(property));
        } catch (ClassNotFoundException e) {
            assert false : "This should never occur. Failed to bind the concrete class for " + property.name();
            return null;
        }
    }

    private <I, T extends I> void bindConcreteClass(Class<T> implClassType, Class<I> interfaceType) {
        bind(interfaceType).to(implClassType);
    }

    private Properties getServiceSpecificProperties() {
        Properties properties = new Properties();

        readFile(properties, _systemConfiguration.getValue(Property.CACHE_SERVICE_PROPERTY_FILE));
        readFile(properties, _systemConfiguration.getValue(Property.MQ_SERVICE_PROPERTY_FILE));
        readFile(properties, _systemConfiguration.getValue(Property.ALERT_SERVICE_PROPERTY_FILE));
        readFile(properties, _systemConfiguration.getValue(Property.SCHEDULING_SERVICE_PROPERTY_FILE));
        readFile(properties, _systemConfiguration.getValue(Property.MAIL_SERVICE_PROPERTY_FILE));
        readFile(properties, _systemConfiguration.getValue(Property.AUTH_SERVICE_PROPERTY_FILE));
        readFile(properties, _systemConfiguration.getValue(Property.SCHEMA_SERVICE_PROPERTY_FILE));
        readFile(properties, _systemConfiguration.getValue(Property.HISTORY_SERVICE_PROPERTY_FILE));
        readFile(properties, _systemConfiguration.getValue(Property.TSDB_SERVICE_PROPERTY_FILE));
        readFile(properties, _systemConfiguration.getValue(Property.NOTIFIER_PROPERTY_FILE)); 
        readFile(properties, _systemConfiguration.getValue(Property.ASYNCHBASE_PROPERTY_FILE));
        readFile(properties, _systemConfiguration.getValue(Property.WARDEN_SERVICE_PROPERTY_FILE));
        readFile(properties, _systemConfiguration.getValue(Property.OAUTH_SERVICE_PROPERTY_FILE));
        return properties;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

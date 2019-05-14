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

import com.google.inject.Singleton;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.text.MessageFormat;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Immutable system configuration information.
 *
 * @author  Tom Valine (tvaline@salesforce.com), Bhinav Sura (bhinav.sura@salesforce.com)
 */
@SuppressWarnings("serial")
@Singleton
public final class SystemConfiguration extends Properties {

    //~ Static fields/initializers *******************************************************************************************************************

    static final String LOCAL_CONFIG_LOCATION;
    static final String GLOBAL_CONFIG_LOCATION;
    static public final String ARGUS_INSTANCE_ID;

    static {
        LOCAL_CONFIG_LOCATION = "argus.config.public.location";
        GLOBAL_CONFIG_LOCATION = "argus.config.private.location";
        ARGUS_INSTANCE_ID = "argus.instance.id";
    }

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new SystemConfiguration object.
     *
     * @param  props  The properties used to configure the system. Cannot be null;
     */
    public SystemConfiguration(Properties props) {
        super();
        putAll(props);
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Interactively generates a local configuration file, prompting the user for input.
     *
     * @param   input        The input stream to read user responses from. Cannot be null.
     * @param   output       The output stream to prompt the user on. Cannot be null.
     * @param   destination  The destination file to write the configuration to. Cannot be null and cannot be an existing file.
     *
     * @throws  IOException  If an error writing the configuration occurs.
     */
    public static void generateConfiguration(InputStream input, OutputStream output, File destination) throws IOException {
        requireArgument(input != null, "Input stream cannot be null.");
        requireArgument(output != null, "Output stream cannot be null.");
        requireArgument(destination != null, "Destination cannot be null.");
        requireArgument(!destination.exists(), "The destination file already exists.");

        BufferedReader in = new BufferedReader(new InputStreamReader(input, Charset.forName("UTF-8")));
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(output, Charset.forName("UTF-8")));
        SystemConfiguration config = new SystemConfiguration(new Properties());

        for (Property property : Property.values()) {
            if (!Property.BUILD.equals(property) && !Property.VERSION.equals(property)) {
                String name = property.key();

                do {
                    String defaultValue = config.getValue(property);

                    out.write(MessageFormat.format("Enter value for {0} ", name));
                    if (defaultValue != null) {
                        out.write(MessageFormat.format("(default = {0}): ", config.getValue(property)));
                    }
                    out.flush();

                    String value = in.readLine();

                    if (value != null && !value.trim().isEmpty()) {
                        config.put(name, value);
                    } else if (defaultValue != null) {
                        config.put(name, defaultValue);
                    }
                } while (config.getValue(property) == null);
            }
        }
        try(OutputStreamWriter fileWriter = new OutputStreamWriter(new FileOutputStream(destination), Charset.forName("UTF-8"))) {
            config.store(fileWriter, "Argus local configuration");
            out.write(MessageFormat.format("Configuration saved to {0}.", destination.getAbsolutePath()));
        }
    }

    /**
     * Returns the host name of the system on which it is invoked.
     *
     * @return  The system host name.
     */
    public static String getHostname() {
        if (System.getProperty("os.name").startsWith("Windows")) {
            return System.getenv("COMPUTERNAME");
        } else {
            String hostname = System.getenv("HOSTNAME");

            if (hostname != null) {
                return hostname;
            }
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception ex) {
            return "unknown-host";
        }
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns the value of a configuration property.
     *
     * @param   property  The property to retrieve. Cannot be null.
     *
     * @return  The configured value of the property. May be null for properties having no default.
     */
    public String getValue(Property property) {
        return getProperty(property.key(), property.defaultValue());
    }

    /**
     * Returns the value of a configuration property.
     *
     * @param   key           The key to retrieve. Cannot be null.
     * @param   defaultValue  The default value.
     *
     * @return  The configured value of the property. May be null for properties having no default.
     */
    public String getValue(String key, String defaultValue) {
        return getProperty(key, defaultValue);
    }

    public String refreshAndGetValue(Property propertyFile, String key, String defaultValue) {
        SystemInitializer.readFile(this, getValue(propertyFile));
        return getValue(key, defaultValue);
    }

    /**
     * Returns the list of configured properties and their values.
     *
     * @return  The list of configured properties and their values.
     */
    @Override
    public synchronized String toString() {
        Properties allProperties = new Properties();
        allProperties.putAll(this);
        for (Property property : Property.values()) {
            allProperties.putIfAbsent(property.key(), property.defaultValue());
        }
        StringBuilder sb = new StringBuilder();

        sb.append("Using the following configured values:\n");
        Pattern pattern = Pattern.compile("pwd|secret|password|passwd|token");
        Set<String> names = new TreeSet<>(allProperties.stringPropertyNames());
        names.stream().forEach((name) -> {
            String value = pattern.matcher(name).find() ? "********" : allProperties.get(name).toString();
            sb.append("\t").append(name).append(" : ").append(value).append("\n");
        });
        return sb.toString();
    }

    //~ Enums ****************************************************************************************************************************************

    /**
     * Supported properties. The target.environment property can be one of 'test'
     *
     * @author  Tom Valine (tvaline@salesforce.com), Bhinav Sura (bhinav.sura@salesforce.com)
     */
    public enum Property {

        BUILD("system.property.build", "XXXX-XX"),
        LOG_LEVEL("system.property.log.level", "INFO"),
        VERSION("system.property.version", "X.X"),
        ADMIN_EMAIL("system.property.admin.email", "someone@mycompany.com"),

        EMAIL_ENABLED("system.property.mail.enabled", "false"),
        GOC_ENABLED("system.property.goc.enabled", "false"),
        GUS_ENABLED("system.property.gus.enabled", "false"),
        CALLBACK_ENABLED("system.property.callback.enabled", "false"),

        EMAIL_EXCEPTIONS("system.property.mail.exceptions", "false"),

        ARGUS_GUS_GROUP_ID("notifier.property.argus.gus.group_id", "groupId"),

        REFOCUS_ENABLED("system.property.refocus.enabled", "false"),
        REFOCUS_CLIENT_THREADS("system.property.refocus.client.threads", "1"),
        REFOCUS_CLIENT_CONNECT_TIMEOUT("system.property.refocus.client.connect.timeout", "10000"),

        DATA_LAG_THRESHOLD("system.property.data.lag.threshold.millis", "300000"),
        DATA_LAG_MONITOR_ENABLED("system.property.monitor.data.lag", "false"),
        DATA_LAG_WHITE_LISTED_SCOPES("system.property.data.lag.whitelisted.scopes", "whiteListedScope"),
        DATA_LAG_WHITE_LISTED_USERS("system.property.data.lag.whitelisted.username", "default"),
        DATA_LAG_QUERY_EXPRESSION("system.property.data.lag.expression.list","expressionListPerDC"),
        DATA_LAG_DEFAULT_EXPRESSION("system.property.data.lag.default.expression","defaultExpression"),

        CLIENT_THREADS("system.property.client.threads", "2"),
        CLIENT_CONNECT_TIMEOUT("system.property.client.connect.timeout", "10000"),


        DC_DEFAULT("system.property.dc.default", "defaultDC"),
        DC_LIST("system.property.dc.list", "DC1,DC2,DC3,DC4,DC5"),

        CACHE_SERVICE_IMPL_CLASS("service.binding.cache", "com.salesforce.dva.argus.service.cache.NoOperationCacheService"),
        CACHE_SERVICE_PROPERTY_FILE("service.config.cache","placeholder_redis.properties"),

        MQ_SERVICE_IMPL_CLASS("service.binding.mq", "com.salesforce.dva.argus.service.mq.kafka.KafkaMessageService"),
        MQ_SERVICE_PRODUCER_IMPL_CLASS("service.binding.mq.producer", "com.salesforce.dva.argus.service.mq.kafka.DefaultProducer"),
        MQ_SERVICE_CONSUMER_IMPL_CLASS("service.binding.mq.consumer", "com.salesforce.dva.argus.service.mq.kafka.DefaultConsumer"),
        MQ_SERVICE_PROPERTY_FILE("service.config.mq","placeholder_kafka.properties"),

        ALERT_SERVICE_IMPL_CLASS("service.binding.alert", "com.salesforce.dva.argus.service.alert.DefaultAlertService"),
        ALERT_SERVICE_PROPERTY_FILE("service.config.alert","placeholder_alert.properties"),
        NOTIFIER_PROPERTY_FILE("service.config.notifier","placeholder_notifier.properties"),

        SCHEDULING_SERVICE_IMPL_CLASS("service.binding.scheduling", "com.salesforce.dva.argus.service.schedule.DefaultSchedulingService"),
        SCHEDULING_SERVICE_PROPERTY_FILE("service.config.scheduling","placeholder_scheduling.properties"),

        MAIL_SERVICE_IMPL_CLASS("service.binding.mail", "com.salesforce.dva.argus.service.mail.DefaultMailService"),
        MAIL_SERVICE_PROPERTY_FILE("service.config.mail","placeholder_mail.properties"),

        CALLBACK_SERVICE_IMPL_CLASS("service.binding.callback", "com.salesforce.dva.argus.service.callback.DefaultCallbackService"),
        CALLBACK_SERVICE_PROPPERTY_FILE("service.config.callback", "placeholder_callback.properties"),

        AUTH_SERVICE_IMPL_CLASS("service.binding.auth", "com.salesforce.dva.argus.service.auth.LDAPAuthService"),
        AUTH_SERVICE_PROPERTY_FILE("service.config.auth","placeholder_ldap.properties"),

        SCHEMA_SERVICE_IMPL_CLASS("service.binding.schema", "com.salesforce.dva.argus.service.schema.AsyncHbaseSchemaService"),
        SCHEMA_SERVICE_PROPERTY_FILE("service.config.schema","placeholder_schema.properties"),

        QUERYSTORE_SERVICE_IMPL_CLASS("service.binding.querystore", "com.salesforce.dva.argus.service.querystore.NoOperationQueryStoreService"),
        QUERYSTORE_SERVICE_PROPERTY_FILE("service.config.querystore","placeholder_querystore.properties"),

        HISTORY_SERVICE_IMPL_CLASS("service.binding.history", "com.salesforce.dva.argus.service.history.HBaseHistoryService"),
        HISTORY_SERVICE_PROPERTY_FILE("service.config.history","placeholder_history.properties"),

        AUDIT_SERVICE_IMPL_CLASS("service.binding.audit", "com.salesforce.dva.argus.service.audit.DefaultAuditService"),
        AUDIT_SERVICE_PROPERTY_FILE("service.config.audit","placeholder_audit.properties"),

        ASYNCHBASE_PROPERTY_FILE("service.config.asynchbase", "placeholder_asynchbase.properties"),

        TSDB_SERVICE_IMPL_CLASS("service.binding.tsdb", "com.salesforce.dva.argus.service.tsdb.DefaultTSDBService"),
        TSDB_SERVICE_PROPERTY_FILE("service.config.tsdb","placeholder_cachedtsdb.properties"),

        WARDEN_SERVICE_IMPL_CLASS("service.binding.warden", "com.salesforce.dva.argus.service.warden.DefaultWardenService"),
        WARDEN_SERVICE_PROPERTY_FILE("service.config.warden", "placeholder_warden.properties"),

        OAUTH_SERVICE_PROPERTY_FILE("service.config.oauth","placeholder_oauth.properties"),
        
        DISCOVERY_SERVICE_IMPL_CLASS("service.binding.discovery", "com.salesforce.dva.argus.service.schema.CachedDiscoveryService"),
        
        // the default value of 5 million datapoints roughly correspond to 500mb of memory
        MAX_DATAPOINTS_ALLOWED_PER_QUERY("system.property.max.datapoints.query", "5000000"),
        
        ENFORCE_DATAPOINTS_LIMIT("system.property.enforce.datapoints.limit", "false"),

        PKI_MONITORED_DIRECTORY("pki.monitored.directory", "/etc/pki_service/sfdc/argus-client"),
        PKI_CA_DIRECTORY("pki.ca.directory", "/etc/pki_service/ca");

        private final String _name;
        private final String _defaultValue;

        private Property(String name, String defaultValue) {
            _name = name;
            _defaultValue = defaultValue;
        }

        private String defaultValue() {
            return _defaultValue;
        }

        private String key() {
            return _name;
        }
    }

}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

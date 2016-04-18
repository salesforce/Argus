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
	 
package com.salesforce.dva.argus.service.alert.notifier;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.AnnotationService;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.NotificationContext;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;

import java.net.URLEncoder;
import java.sql.Date;
import java.text.MessageFormat;
import java.util.Properties;

import javax.persistence.EntityManager;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.apache.commons.httpclient.params.HttpConnectionManagerParams;
import org.slf4j.Logger;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Implementation of notifier interface for notifying GOC++.
 *
 * @author  Fiaz Hossain (fiaz.hossain@salesforce.com)
 */
public class GOCNotifier extends AuditNotifier {

    //~ Instance fields ******************************************************************************************************************************

	@SLF4JTypeListener.InjectLogger
    private Logger _logger;

   
    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new GOC notifier.
     *
     * @param  metricService      The metric service. Cannot be null.
     * @param  annotationService  The annotation service. Cannot be null.
     * @param  auditService       The audit service. Cannot be null.
     * @param  gocService         The GOC service. Cannot be null.
     * @param  config             The system configuration. Cannot be null.
     * @param  emf                The entity manager factory. Cannot be null.
     */
    @Inject
    public GOCNotifier(MetricService metricService, AnnotationService annotationService, AuditService auditService,
        SystemConfiguration config, Provider<EntityManager> emf) {
        super(metricService, annotationService, auditService, config, emf);
        requireArgument(config != null, "The configuration cannot be null.");
    }

    //~ Methods **************************************************************************************************************************************
    
    private PostMethod getRequestMethod(boolean refresh, String id) {
    	GOCTransport gocTransport = new GOCTransport();
        EndpointInfo endpointInfo = gocTransport.getEndpointInfo(_config, _logger, refresh);

        // Create upsert URI with PATCH method
        PostMethod post = new PostMethod(String.format("%s/services/data/v25.0/sobjects/SM_Alert__c/%s/%s", endpointInfo.getEndPoint(),
                GOCData.SM_ALERT_ID__C_FIELD, id)) {

                @Override
                public String getName() {
                    return "PATCH";
                }
            };
        post.setRequestHeader("Authorization", "Bearer " + endpointInfo.getToken());
        return post;
    }

    /**
     * Sends an GOC++ message.
     *
     * @param  severity      The message severity
     * @param  className     The alert class name
     * @param  elementName   The element/instance name
     * @param  eventName     The event name
     * @param  message       The message body.
     * @param  lastNotified  The last message time. (typically current time)
     */
    public void sendMessage(Severity severity, String className, String elementName, String eventName, String message, long lastNotified) {
        requireArgument(elementName != null && !elementName.isEmpty(), "ElementName cannot be null or empty.");
        requireArgument(eventName != null && !eventName.isEmpty(), "EventName cannot be null or empty.");
        if (Boolean.valueOf(_config.getValue(com.salesforce.dva.argus.system.SystemConfiguration.Property.GOC_ENABLED))) {
            try {
                GOCDataBuilder builder = new GOCDataBuilder();

                builder.withClassName(className).withElementName(elementName).withEventName(eventName).withEventText(message);
                if (severity == Severity.OK) {
                    builder.withActive(false).withClearedAt(lastNotified);
                } else {
                    builder.withActive(true).withCreatedAt(lastNotified);
                }
                builder.withLastNotifiedAt(lastNotified);

                GOCData gocData = builder.build();
                boolean refresh = false;
                GOCTransport gocTransport = new GOCTransport();
                HttpClient httpclient = gocTransport.getHttpClient(_config);

                for (int i = 0; i < 2; i++) {
                	
                    PostMethod post = getRequestMethod(refresh, gocData.getsm_Alert_Id__c());

                    try {
                        post.setRequestEntity(new StringRequestEntity(gocData.toJSON(), "application/json", null));

                        int respCode = httpclient.executeMethod(post);

                        // Check for success
                        if (respCode == 201 || respCode == 204) {
                            _logger.info("Success - send GOC++ having element '{}' event '{}' severity {}.", elementName, eventName, severity.name());
                            break;
                        } else if (respCode == 401) {
                            // Indication that the session timedout, Need to refresh and retry
                            refresh = true;
                        } else {
                            _logger.error("Failure - send GOC++ having element '{}' event '{}' severity {}. Response code '{}' response '{}'",
                                elementName, eventName, severity.name(), respCode, post.getResponseBodyAsString());
                        }
                    } catch (Exception e) {
                        _logger.error("Failure - send GOC++ having element '{}' event '{}' severity {}. Exception '{}'", elementName, eventName,
                            severity.name(), e);
                    } finally {
                        post.releaseConnection();
                    }
                }
            } catch (RuntimeException ex) {
                throw new SystemException("Failed to send an GOC++ notification.", ex);
            }
        } else {
            _logger.info("Sending GOC++ notification is disabled.  Not sending message for element '{}' event '{}' severity {}.", elementName,
                eventName, severity.name());
        }
    }
    @Override
    public String getName() {
        return GOCNotifier.class.getName();
    }

    @Override
    protected void sendAdditionalNotification(NotificationContext context) {
        _sendAdditionalNotification(context, NotificationStatus.TRIGGERED);
    }

    @Override
    protected void clearAdditionalNotification(NotificationContext context) {
        _sendAdditionalNotification(context, NotificationStatus.CLEARED);
    }

    /**
     * Update the state of the notification to indicate whether the triggering condition exists or has been cleared.
     *
     * @param  context  The notification context.  Cannot be null.
     * @param  status   The notification status.  If null, will set the notification severity to <tt>ERROR</tt>
     */
    protected void _sendAdditionalNotification(NotificationContext context, NotificationStatus status) {
        requireArgument(context != null, "Notification context cannot be null.");
        super.sendAdditionalNotification(context);

        Notification notification = null;
        Trigger trigger = null;

        for (Notification tempNotification : context.getAlert().getNotifications()) {
            if (tempNotification.getName().equalsIgnoreCase(context.getNotification().getName())) {
                notification = tempNotification;
                break;
            }
        }
        requireArgument(notification != null, "Notification in notification context cannot be null.");
        for (Trigger tempTrigger : context.getAlert().getTriggers()) {
            if (tempTrigger.getName().equalsIgnoreCase(context.getTrigger().getName())) {
                trigger = tempTrigger;
                break;
            }
        }
        requireArgument(trigger != null, "Trigger in notification context cannot be null.");

        String body = getGOCMessageBody(notification, trigger, context);
        Severity sev = status == NotificationStatus.CLEARED ? Severity.OK : Severity.ERROR;

        sendMessage(sev, context.getNotification().getName(), context.getAlert().getName(), context.getTrigger().getName(), body,
            context.getTriggerFiredTime());
    }

    /**
     * Returns the goc message body content.
     *
     * @param   notification  The source notification.
     * @param   trigger       The source trigger.
     * @param   context       The notification context.
     *
     * @return  The goc++ message body.
     */
    protected String getGOCMessageBody(Notification notification, Trigger trigger, NotificationContext context) {
        StringBuilder sb = new StringBuilder();

        sb.append(MessageFormat.format("Alert {0}  was triggered at {1}\n", context.getAlert().getName(),
                DATE_FORMATTER.get().format(new Date(context.getTriggerFiredTime()))));
        sb.append(MessageFormat.format("Notification:  {0}\n", notification.getName()));
        sb.append(MessageFormat.format("Triggered by:  {0}\n", trigger.getName()));
        sb.append(MessageFormat.format("Notification is on cooldown until:  {0}\n",
                DATE_FORMATTER.get().format(new Date(context.getCoolDownExpiration()))));
        sb.append(MessageFormat.format("Evaluated metric expression:  {0}\n", context.getAlert().getExpression()));
        sb.append(MessageFormat.format("Trigger details: {0}\n", getTriggerDetails(trigger)));
        sb.append(MessageFormat.format("Triggering event value:  {0}\n", context.getTriggerEventValue()));
        sb.append("\n");
        for (String metricToAnnotate : notification.getMetricsToAnnotate()) {
            sb.append(MessageFormat.format("Annotated series for {0}: {1}\n", metricToAnnotate,
                    getMetricUrl(metricToAnnotate, context.getTriggerFiredTime())));
        }
        sb.append("\n");
        sb.append(MessageFormat.format("Alert definition:  {0}\n", getAlertUrl(notification.getAlert().getId())));
        return sb.toString();
    }
    
    @Override
    public Properties getNotifierProperties() {
            Properties notifierProps= super.getNotifierProperties();

            for(Property property:Property.values()){
                    notifierProps.put(property.getName(), property.getDefaultValue());
            }
            return notifierProps;
    }
    
    //~ Enums ****************************************************************************************************************************************

    /**
     * Sets the severity of the message.
     *
     * @author  Fiaz Hossain (fiaz.hossain@salesforce.com)
     */
    public enum Severity {

        OK,
        WARN,
        ERROR
    }
    
    /**
     * Enumerates implementation specific configuration properties.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public enum Property {

        /** The GOC endpoint. */
        GOC_ENDPOINT("notifier.property.goc.endpoint", "https://test.com"),
        /** The GOC user with which to authenticate. */
        GOC_USER("notifier.property.goc.username", "test_user"),
        /** The GOC password with which to authenticate. */
        GOC_PWD("notifier.property.goc.password", "test_password"),
        /** The GOC proxy host. */
        GOC_PROXY_HOST("notifier.property.goc.proxy.host", ""),
        /** The GOC port. */
        GOC_PROXY_PORT("notifier.property.goc.proxy.port", ""),
        /** The GOC client ID. */
        GOC_CLIENT_ID("notifier.property.goc.client.id", "default_client_id"),
        /** The GOC client secret. */
        GOC_CLIENT_SECRET("notifier.property.goc.client.secret", "default_pass"),
        /** The alert URL template to be included with GOC notifications. */
        EMAIL_ALERT_URL_TEMPLATE("notifier.property.goc.alerturl.template", "http://localhost:8080/argus/alertId"),
        /** The metric URL template to be included with GOC notifications. */
        EMAIL_METRIC_URL_TEMPLATE("notifier.property.goc.metricurl.template", "http://localhost:8080/argus/metrics");

        private final String _name;
        private final String _defaultValue;

        private Property(String name, String defaultValue) {
            _name = name;
            _defaultValue = defaultValue;
        }

        /**
         * Returns the property name.
         *
         * @return  The property name.
         */
        public String getName() {
            return _name;
        }

        /**
         * Returns the default property value.
         *
         * @return  The default property value.
         */
        public String getDefaultValue() {
            return _defaultValue;
        }
    }
    
    //~ Inner classes ********************************************************************************************************************************
    
    /**
     * GOCData object to generate JSON.
     *
     * @author  Fiaz Hossain (fiaz.hossain@salesforce.com)
     */
    public class GOCData {

        //~ Static fields/initializers *******************************************************************************************************************
        private static final String SM_ACTIVE__C_FIELD = "SM_Active__c";

        /** 
         * The name of the GOC alert ID field.  
         * @todo Move this to DefaultGOCService. 
         */
        public static final String SM_ALERT_ID__C_FIELD = "SM_Alert_Id__c";
        private static final String SM_CLASSNAME__C_FIELD = "SM_ClassName__c";
        private static final String SM_CLEAREDAT__C_FIELD = "SM_ClearedAt__c";
        private static final String SM_CREATEDAT__C_FIELD = "SM_CreatedAt__c";
        private static final String SM_ELEMENTNAME__C_FIELD = "SM_ElementName__c";
        private static final String SM_EVENTNAME__C_FIELD = "SM_EventName__c";
        private static final String SM_EVENTTEXT__C_FIELD = "SM_EventText__c";
        private static final String SM_LASTNOTIFIEDAT__C_FIELD = "SM_LastNotifiedAt__c";
        private static final String SM_SEVERITY__C_FIELD = "SM_Severity__c";
        private static final String SM_SOURCEDOMAIN__C_FIELD = "SM_SourceDomain__c";
        private static final String SR_ACTIONABLE__C_FIELD = "SR_Actionable__c";

        //~ Instance fields ******************************************************************************************************************************

        private final boolean smActivec; // true when alert is active, false when alert is cleared
        private final String smAlertIdc; // Text(200) (External ID) --> sm_ElementName__c + ALERT_ID_SEPARATOR + sm_EventName__c
        private final String smClassNamec; // Text(50)
        private final long smClearedAtc; // Date/Time --> timestamp when the alert cleared, null while alert is still active
        private final long smCreatedAtc; // Date/Time --> timestamp when the alert last became active
        private final String smElementNamec; // Text(100) --> hostname
        private final String smEventNamec; // Text(50)
        private final String smEventTextc; // Long Text Area(32768)
        private final long smLastNotifiedAtc; // Date/Time --> timestamp
        private final int smSeverityc; // Number(1, 0) (External ID) --> 0 through 5
        private final String smSourceDomainc;
        private final boolean srActionablec; // Checkbox --> true if SR needs to respond to this alert

        //~ Constructors *********************************************************************************************************************************

        private GOCData(final boolean smActivec, final String smAlertIdc, final String smClassNamec, final long smClearedAtc, final long smCreatedAtc,
            final String smElementNamec, final String smEventNamec, final String smEventTextc, final long smLastNotifiedAtc, final int smSeverityc,
            final String smSourceDomainc, final boolean srActionablec) {
            this.smActivec = smActivec;
            this.smAlertIdc = smAlertIdc;
            this.smClassNamec = smClassNamec;
            this.smClearedAtc = smClearedAtc;
            this.smCreatedAtc = smCreatedAtc;
            this.smElementNamec = smElementNamec;
            this.smEventNamec = smEventNamec;
            this.smEventTextc = smEventTextc;
            this.smLastNotifiedAtc = smLastNotifiedAtc;
            this.smSeverityc = smSeverityc;
            this.smSourceDomainc = smSourceDomainc;
            this.srActionablec = srActionablec;
        }

        //~ Methods **************************************************************************************************************************************

        /**
         * Returns the GOC alert ID field name.
         *
         * @return  The GOC alert ID field name.
         */
        public String getsm_Alert_Id__c() {
            return smAlertIdc;
        }

        /**
         * Convert data to a JSON string.
         *
         * @return  JSON string
         */
        public String toJSON() {
            JsonObject gocData = new JsonObject();

            gocData.addProperty(SM_ACTIVE__C_FIELD, smActivec);

            /**
             * SM_ALERT_ID__C_FIELD will be in the URI and should not in sObject data
             */
            gocData.addProperty(SM_CLASSNAME__C_FIELD, smClassNamec);
            if (smClearedAtc > 0) {
                gocData.addProperty(SM_CLEAREDAT__C_FIELD, smClearedAtc);
            }
            if (smCreatedAtc > 0) {
                gocData.addProperty(SM_CREATEDAT__C_FIELD, smCreatedAtc);
            }
            gocData.addProperty(SM_ELEMENTNAME__C_FIELD, smElementNamec);
            gocData.addProperty(SM_EVENTNAME__C_FIELD, smEventNamec);
            gocData.addProperty(SM_EVENTTEXT__C_FIELD, smEventTextc);
            gocData.addProperty(SM_LASTNOTIFIEDAT__C_FIELD, smLastNotifiedAtc);
            gocData.addProperty(SM_SEVERITY__C_FIELD, smSeverityc);
            gocData.addProperty(SM_SOURCEDOMAIN__C_FIELD, smSourceDomainc);
            //gocData.addProperty(SR_ACTIONABLE__C_FIELD, srActionablec);
            return gocData.toString();
        }

        //~ Inner Classes ********************************************************************************************************************************
        
    }
    
    /**
     * Utility builder.
     *
     * @author  Fiaz Hossain (fiaz.hossain@salesforce.com)
     */
    public class GOCDataBuilder {

    	 private static final String SM_SOURCE_DOMAIN__C = "Argus";
         private static final String ALERT_ID_SEPARATOR = ".";
         
        private boolean smActivec; // true when alert is active, false when alert is cleared
        private String smClassNamec; // Text(50)
        private long smClearedAtc; // Date/Time --> timestamp when the alert cleared, null while alert is still active
        private long smCreatedAtc; // Date/Time --> timestamp when the alert last became active
        private String smElementNamec; // Text(100) --> hostname
        private String smEventNamec; // Text(50)
        private String smEventTextc; // Long Text Area(32768)
        private long smLastNotifiedAtc; // Date/Time --> timestamp
        private int smSeverityc = 5; // Number(1, 0) (External ID) --> 0 through 5
        private boolean srActionablec = false;

        /** Creates a new GOCDataBuilder object. */
        public GOCDataBuilder() { }

        /**
         * Specifies the active status.
         *
         * @param   smActivec  True if active.
         *
         * @return  The updated builder object.
         */
        public GOCDataBuilder withActive(final boolean smActivec) {
            this.smActivec = smActivec;
            return this;
        }

        /**
         * Indicates the class name.
         *
         * @param   smClassNamec  The class name.
         *
         * @return  The updated builder object.
         */
        public GOCDataBuilder withClassName(final String smClassNamec) {
            this.smClassNamec = smClassNamec;
            return this;
        }

        /**
         * Specifies the cleared at time.
         *
         * @param   smClearedAtc  The cleared time.
         *
         * @return  The updated builder object.
         */
        public GOCDataBuilder withClearedAt(final long smClearedAtc) {
            this.smClearedAtc = smClearedAtc;
            return this;
        }

        /**
         * Specifies the created at time.
         *
         * @param   smCreatedAtc  The created at time.
         *
         * @return  The updated builder object.
         */
        public GOCDataBuilder withCreatedAt(final long smCreatedAtc) {
            this.smCreatedAtc = smCreatedAtc;
            return this;
        }

        /**
         * Specifies the element name.
         *
         * @param   smElementNamec  The element name.
         *
         * @return  The updated builder object.
         */
        public GOCDataBuilder withElementName(final String smElementNamec) {
            this.smElementNamec = smElementNamec;
            return this;
        }

        /**
         * Specifies the event name.
         *
         * @param   smEventNamec  The event name.
         *
         * @return  The updated builder object.
         */
        public GOCDataBuilder withEventName(final String smEventNamec) {
            this.smEventNamec = smEventNamec;
            return this;
        }

        /**
         * Specifies the event text.
         *
         * @param   smEventTextc  The event text.
         *
         * @return  The updated builder object.
         */
        public GOCDataBuilder withEventText(final String smEventTextc) {
            this.smEventTextc = smEventTextc;
            return this;
        }

        /**
         * Specifies the last notified date.
         *
         * @param   smLastNotifiedAtc  The last notified date.
         *
         * @return  The updated builder object.
         */
        public GOCDataBuilder withLastNotifiedAt(final long smLastNotifiedAtc) {
            this.smLastNotifiedAtc = smLastNotifiedAtc;
            return this;
        }

        /**
         * Specifies the severity.
         *
         * @param   smSeverityc  The severity.
         *
         * @return  The updated builder object.
         */
        public GOCDataBuilder withSeverity(final int smSeverityc) {
            this.smSeverityc = smSeverityc;
            return this;
        }

        /**
         * Specifies whether the alert is actionable.
         *
         * @param   sRActionablec  True if actionable.
         *
         * @return  The updated builder object.
         */
        public GOCDataBuilder withSRActionable(final boolean sRActionablec) {
            this.srActionablec = sRActionablec;
            return this;
        }

        /**
         * Create the GOCData object, use defaults where needed.
         *
         * @return  GOCData created based on builder data
         */
        public GOCData build() {
            return new GOCData(smActivec, smElementNamec + ALERT_ID_SEPARATOR + smEventNamec, smClassNamec, smClearedAtc, smCreatedAtc,
                smElementNamec, smEventNamec, smEventTextc, smLastNotifiedAtc, smSeverityc, SM_SOURCE_DOMAIN__C, srActionablec);
        }
    }
    
    
    /**
     * Manage GOC connections, oAuth and timeouts.
     *
     * @author  Fiaz Hossain (fiaz.hossain@salesforce.com)
     */
    public class GOCTransport {

        //~ Static fields/initializers *******************************************************************************************************************

    	private static final String UTF_8 = "UTF-8";
        private static final String NO_TOKEN = "NO_TOKEN";
        private static final long MIN_SESSION_REFRESH_THRESHOLD_MILLIS = 5 * 60 * 1000; // Wait at least 5 minutes between refresh attemps
        private static final int CONNECTION_TIMEOUT_MILLIS = 10000;
        private static final int READ_TIMEOUT_MILLIS = 10000;
        private volatile EndpointInfo theEndpointInfo = null;
        private volatile long lastRefresh = 0;
        private final MultiThreadedHttpConnectionManager theConnectionManager;
        {
            theConnectionManager = new MultiThreadedHttpConnectionManager();

            HttpConnectionManagerParams params = theConnectionManager.getParams();

            params.setConnectionTimeout(CONNECTION_TIMEOUT_MILLIS);
            params.setSoTimeout(READ_TIMEOUT_MILLIS);
        }

        //~ Methods **************************************************************************************************************************************

        /**
         * Get authenticated endpoint and token.
         *
         * @param   config   The system configuration.  Cannot be null.
         * @param   logger   The logger.  Cannot be null.
         * @param   refresh  - If true get a new token even if one exists.
         *
         * @return  EndpointInfo - with valid endpoint and token. The token can be a dummy or expired.
         */
        public  EndpointInfo getEndpointInfo(SystemConfiguration config, Logger logger, boolean refresh) {
            if (theEndpointInfo == null || refresh) {
                updateEndpoint(config, logger, lastRefresh);
            }
            return theEndpointInfo;
        }

        /**
         * Get HttpClient with proper proxy and timeout settings.
         *
         * @param   config  The system configuration.  Cannot be null.
         *
         * @return  HttpClient
         */
        public  HttpClient getHttpClient(SystemConfiguration config) {
            HttpClient httpclient = new HttpClient(theConnectionManager);

            httpclient.getParams().setParameter("http.connection-manager.timeout", 2000L); // Wait for 2 seconds to get a connection from pool

            String host = config.getValue(Property.GOC_PROXY_HOST.getName(), Property.GOC_PROXY_HOST.getDefaultValue());

            if (host != null && host.length() > 0) {
                httpclient.getHostConfiguration().setProxy(host,
                    Integer.parseInt(config.getValue(Property.GOC_PROXY_PORT.getName(), Property.GOC_PROXY_PORT.getDefaultValue())));
            }
            return httpclient;
        }

        /**
         * Update the global 'theEndpointInfo' state with a valid endpointInfo if login is successful or a dummy value if not successful.
         *
         * @param  config           The system configuration.  Cannot be null.
         * @param  logger           The logger.  Cannot be null.
         * @param  previousRefresh  The last refresh time.
         */
        private synchronized void updateEndpoint(SystemConfiguration config, Logger logger, long previousRefresh) {
            long diff = System.currentTimeMillis() - previousRefresh;

            if (diff > MIN_SESSION_REFRESH_THRESHOLD_MILLIS) {
                lastRefresh = System.currentTimeMillis();

                PostMethod post = new PostMethod(config.getValue(Property.GOC_ENDPOINT.getName(), Property.GOC_ENDPOINT.getDefaultValue()) +
                    "/services/oauth2/token");

                try {
                    post.addParameter("grant_type", "password");
                    post.addParameter("client_id",
                        URLEncoder.encode(config.getValue(Property.GOC_CLIENT_ID.getName(), Property.GOC_CLIENT_ID.getDefaultValue()), UTF_8));
                    post.addParameter("client_secret",
                        URLEncoder.encode(config.getValue(Property.GOC_CLIENT_SECRET.getName(), Property.GOC_CLIENT_SECRET.getDefaultValue()), UTF_8));
                    post.addParameter("username", config.getValue(Property.GOC_USER.getName(), Property.GOC_USER.getDefaultValue()));
                    post.addParameter("password", config.getValue(Property.GOC_PWD.getName(), Property.GOC_PWD.getDefaultValue()));

                    HttpClient httpclient = getHttpClient(config);
                    int respCode = httpclient.executeMethod(post);

                    // Check for success
                    if (respCode == 200) {
                        JsonObject authResponse = new Gson().fromJson(post.getResponseBodyAsString(), JsonObject.class);
                        String endpoint = authResponse.get("instance_url").getAsString();
                        String token = authResponse.get("access_token").getAsString();

                        logger.info("Success - getting access_token for endpoint '{}'", endpoint);
                        logger.debug("access_token '{}'", token);
                        theEndpointInfo = new EndpointInfo(endpoint, token);
                    }
                    else {
                    	logger.error("Failure - getting oauth2 token, check username/password: '{}'", post.getResponseBodyAsString());
                    } 

                } catch (Exception e) {
                    logger.error("Failure - exception getting access_token '{}'", e);
                } finally {
                    if (theEndpointInfo == null) {
                        theEndpointInfo = new EndpointInfo(config.getValue(Property.GOC_ENDPOINT.getName(), Property.GOC_ENDPOINT.getDefaultValue()),
                            NO_TOKEN);
                    }
                    post.releaseConnection();
                }
            }
        }

        //~ Inner Classes ********************************************************************************************************************************
        
    }
    
    /**
     * Utility class for endpoint information.
     *
     * @author  fiaz.hossain
     */
    public class EndpointInfo {

        private final String endPoint;
        private final String token;

        private EndpointInfo(final String endPoint, final String token) {
            this.endPoint = endPoint;
            this.token = token;
        }

        /**
         * Valid endpoint. Either from config or endpont after authentication
         *
         * @return  endpoint
         */
        public String getEndPoint() {
            return endPoint;
        }

        /**
         * Token can be either active, expired or a dummy value.
         *
         * @return  token
         */
        public String getToken() {
            return token;
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

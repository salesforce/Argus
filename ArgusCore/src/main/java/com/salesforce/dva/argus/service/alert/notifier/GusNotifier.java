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
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.AnnotationService;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.MailService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.NotificationContext;
import com.salesforce.dva.argus.system.SystemConfiguration;
import joptsimple.internal.Strings;
import org.apache.http.HttpResponse;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.params.CookiePolicy;
import org.apache.http.client.params.HttpClientParams;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import java.io.IOException;
import java.net.URLEncoder;
import java.sql.Date;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import javax.persistence.EntityManager;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Chatter Notifier: api user can only post alert to PUBLIC group
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
@SuppressWarnings("deprecation")
public class GusNotifier extends AuditNotifier {

    //~ Static fields/initializers *******************************************************************************************************************

    /**
     * CredentialPair stores name/value pair in the request.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    private static final SystemConfiguration _sysConfig = new SystemConfiguration(new Properties());

    //~ Instance fields ******************************************************************************************************************************

    @SLF4JTypeListener.InjectLogger
    private Logger _logger;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new GusNotifier object.
     *
     * @param  metricService      The metric service to use.  Cannot be null.
     * @param  annotationService  The annotation service to use.  Cannot be null.
     * @param  auditService       The audit service to use.  Cannot be null.
     * @param  mailService        The mail service to use.  Cannot be null.
     * @param  config             The system configuration.  Cannot be null.
     * @param  emf                The entity manager factory to use.  Cannot be null.
     */
    @Inject
    public GusNotifier(MetricService metricService, AnnotationService annotationService, AuditService auditService, MailService mailService,
        SystemConfiguration config, Provider<EntityManager> emf) {
        super(metricService, annotationService, auditService, config, emf);
        requireArgument(mailService != null, "Mail service cannot be null.");
        requireArgument(config != null, "The configuration cannot be null.");
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public String getName() {
        return GusNotifier.class.getName();
    }

    @Override
    protected void sendAdditionalNotification(NotificationContext context) {
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

        Set<String> to = new HashSet<>(notification.getSubscriptions());
        String feed = generateGusFeed(notification, trigger, context);

        postToGus(to, feed);
    }

    private String generateGusFeed(Notification notification, Trigger trigger, NotificationContext context) {
        StringBuilder sb = new StringBuilder();
        String notificationName = context.getNotification().getName();
        String alertName = context.getAlert().getName();
        String triggerFiredTime = DATE_FORMATTER.get().format(new Date(context.getTriggerFiredTime()));
        String triggerName = trigger.getName();
        String notificationCooldownExpiraton = DATE_FORMATTER.get().format(new Date(context.getCoolDownExpiration()));
        String metricExpression = context.getAlert().getExpression();
        String triggerDetails = getTriggerDetails(trigger);
        String triggerEventValue = context.getTriggerEventValue();
        Object[] arguments = new Object[] {
                notificationName, alertName, triggerFiredTime, triggerName, notificationCooldownExpiraton, metricExpression, triggerDetails,
                triggerEventValue
            };

        /** gus feed template for notification information. */
        String gusFeedNotificationTemplate = "Alert Notification {0} is triggered, more info as following:\n" + "Alert {1}  was triggered at {2}\n" +
            "Notification:   {0}\n" +
            "Triggered by:   {3}\n" + "Notification is on cooldown until:   {4}\n" +
            "Evaluated metric expression:   {5}\n" + "Trigger details:  {6}\n" +
            "Triggering event value:   {7}\n\n";

        sb.append(MessageFormat.format(gusFeedNotificationTemplate, arguments));

        /** gus feed template for links. */
        String gusFeedLinkTemplate = "Click here to view {0}\n{1}\n";

        for (String metricToAnnotate : notification.getMetricsToAnnotate()) {
            sb.append(MessageFormat.format(gusFeedLinkTemplate, "the annotated series for",
                    super.getMetricUrl(metricToAnnotate, context.getTriggerFiredTime())));
        }
        sb.append(MessageFormat.format(gusFeedLinkTemplate, "alert definition.", super.getAlertUrl(notification.getAlert().getId())));
        return sb.toString();
    }

    private void postToGus(Set<String> to, String feed) {
        // So far works for only one group, will accept a set of string in future.
        String groupId = to.toArray(new String[to.size()])[0];
        HttpPost chatterIt = new HttpPost();

        try {
            String gusPost = MessageFormat.format("{0}&subjectId={1}&text={2}", Endpoint.POST_URL.getUrl(), groupId,
                URLEncoder.encode(feed.toString(), "UTF-8"));

            chatterIt = new HttpPost(gusPost);
            chatterIt.setHeader("Authorization", "Bearer " + generateAccessToken());

            @SuppressWarnings("resource")
            DefaultHttpClient httpclient = new DefaultHttpClient();

            httpclient.execute(chatterIt);
        } catch (Exception e) {
            _logger.debug("Throws Exception when posting to gus group {} with subject {}", groupId, feed);
        } finally {
            chatterIt.releaseConnection();
        }
    }

    private String generateAccessToken() {
        // Set up an HTTP client that makes a connection to REST API.
        @SuppressWarnings("resource")
        DefaultHttpClient client = new DefaultHttpClient();
        HttpParams params = client.getParams();

        HttpClientParams.setCookiePolicy(params, CookiePolicy.RFC_2109);
        params.setParameter(HttpConnectionParams.CONNECTION_TIMEOUT, 30000);

        HttpPost oauthPost = new HttpPost();

        try {
            // Send a post request to the OAuth URL.
            oauthPost = new HttpPost(Endpoint.CALLBACK_URL.getUrl());

            // generate the request body
            List<String> paraList = Arrays.asList("grant_type", "username", "password", "client_id", "client_secret");

            oauthPost.setEntity(new UrlEncodedFormEntity(generateParameterBody(paraList), HTTP.UTF_8));

            // Execute the request.
            HttpResponse response = client.execute(oauthPost);

            // Get access token
            @SuppressWarnings("unchecked")
            Map<String, String> oauthLoginResponse = new Gson().fromJson(EntityUtils.toString(response.getEntity()), Map.class);
            String accessToken = oauthLoginResponse.get("access_token");

            return accessToken;
        } catch (RuntimeException | IOException e) {
            _logger.debug("Encoding Exception when generating access token", CredentialPair.ARGUS_GUS_USER.getValue());
        } finally {
            oauthPost.releaseConnection();
        }
        return Strings.EMPTY;
    }

    /**
     * Enumerates the name value pairs to insert into the notification body.
     *
     * @param   paraList  The parameter list.  Cannot be null, but may be empty.
     *
     * @return  The list of corresponding name value pairs.  Will never return null, but may be empty.
     */
    public List<BasicNameValuePair> generateParameterBody(List<String> paraList) {
        List<BasicNameValuePair> parametersBody = new ArrayList<>();

        for (String str : paraList) {
            parametersBody.add(generateNameValuePair(str));
        }
        return parametersBody;
    }

    /**
     * Generates a basic name value pair based on the given parameter name.
     *
     * @param   paraName  The parameter name.  Cannot be null or empty.
     *
     * @return  The corresponding name value pair.
     *
     * @throws  UnsupportedOperationException  If the specified parameter doesn't have a corresponding GUS property.
     */
    public BasicNameValuePair generateNameValuePair(String paraName) {
        CredentialPair credentialPara = CredentialPair.fromString(paraName);

        switch (credentialPara) {
            case GRANT_TYPE_PWD:
                return new BasicNameValuePair(CredentialPair.GRANT_TYPE_PWD.getName(), CredentialPair.GRANT_TYPE_PWD.getValue());
            case ARGUS_GUS_USER:
                return new BasicNameValuePair(CredentialPair.ARGUS_GUS_USER.getName(), CredentialPair.ARGUS_GUS_USER.getValue());
            case ARGUS_GUS_PWD:
                return new BasicNameValuePair(CredentialPair.ARGUS_GUS_PWD.getName(), CredentialPair.ARGUS_GUS_PWD.getValue());
            case GUS_CLIENT_ID:
                return new BasicNameValuePair(CredentialPair.GUS_CLIENT_ID.getName(), CredentialPair.GUS_CLIENT_ID.getValue());
            case GUS_CLIENT_SECRET:
                return new BasicNameValuePair(CredentialPair.GUS_CLIENT_SECRET.getName(), CredentialPair.GUS_CLIENT_SECRET.getValue());
            default:
                throw new UnsupportedOperationException(paraName);
        }
    }
    
    @Override
    public Properties getNotifierProperties() {
    	Properties result = super.getNotifierProperties();

    	for( Property property : Property.values()) {
    		result.put(property.getName(), property.getDefaultValue());
    	}
    	return result;
    }

    //~ Enums ****************************************************************************************************************************************

    /**
     * CredentialPair stores name/value pair in the request.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public enum CredentialPair {

        GRANT_TYPE_PWD("grant_type", _sysConfig.getValue(Property.GRANT_TYPE_PWD.getName(), Property.GRANT_TYPE_PWD.getDefaultValue())),
        ARGUS_GUS_USER("username", _sysConfig.getValue(Property.ARGUS_GUS_USER.getName(), Property.ARGUS_GUS_USER.getDefaultValue())),
        ARGUS_GUS_PWD("password", _sysConfig.getValue(Property.ARGUS_GUS_PWD.getName(), Property.ARGUS_GUS_PWD.getDefaultValue())),
        GUS_CLIENT_ID("client_id", _sysConfig.getValue(Property.GUS_CLIENT_ID.getName(), Property.GUS_CLIENT_ID.getDefaultValue())),
        GUS_CLIENT_SECRET("client_secret", _sysConfig.getValue(Property.GUS_CLIENT_SECRET.getName(), Property.GUS_CLIENT_SECRET.getDefaultValue()));

        private final String _name, _value;

        private CredentialPair(String name, String value) {
            _name = name;
            _value = value;
        }

        /**
         * Returns the credential parameter name corresponding to the input string.
         *
         * @param   paraName  name The name of the credential parameter.
         *
         * @return  The corresponding parameter name.
         *
         * @throws  IllegalArgumentException  If no element exists for the name.
         */
        public static CredentialPair fromString(String paraName) {
            if (paraName != null) {
                for (CredentialPair para : CredentialPair.values()) {
                    if (paraName.equalsIgnoreCase(para.getName())) {
                        return para;
                    }
                }
            }
            throw new IllegalArgumentException(paraName);
        }

        /**
         * Returns the credential parameter name.
         *
         * @return  The credential parameter name.
         */
        public String getName() {
            return _name;
        }

        /**
         * Returns the credential parameter value.
         *
         * @return  The credential parameter value.
         */
        public String getValue() {
            return _value;
        }
    }

    /**
     * Endpoint stors callback url and post url.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public enum Endpoint {

        CALLBACK_URL(_sysConfig.getValue(Property.GUS_ENDPOINT.getName(), Property.GUS_ENDPOINT.getDefaultValue())),
        POST_URL(_sysConfig.getValue(Property.POST_ENDPOINT.getName(), Property.POST_ENDPOINT.getDefaultValue()));

        private final String _url;

        private Endpoint(String url) {
            _url = url;
        }

        /**
         * Returns the url from config.
         *
         * @return  The url from config.
         */
        public String getUrl() {
            return _url;
        }
    }
    
    public enum Property {
    	/** The GUS grant type password. */
        GRANT_TYPE_PWD("notifier.property.alert.grant_type_pwd", "password"),
        /** The GUS user name. */
        ARGUS_GUS_USER("notifier.property.alert.gus_user", "test@test.com"),
        /** The GUS password. */
        ARGUS_GUS_PWD("notifier.property.alert.gus_pwd", "password"),
        /** The GUS endpoint. */
        GUS_ENDPOINT("notifier.property.alert.gus_endpoint", "https://gus.test.com"),
        /** The GUS client ID. */
        GUS_CLIENT_ID("notifier.property.alert.gus_client_id", "test123"),
        /** The GUS client secret. */
        GUS_CLIENT_SECRET("notifier.property.alert.gus_client_secret", "password"),
        /** The GUS post endpoint. */
        POST_ENDPOINT("notifier.property.alert.gus_post_endpoint", "https://gus.test.com");
    	
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
          * Returns the default value.
          *
          * @return  The default value.
          */
         public String getDefaultValue() {
             return _defaultValue;
         }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

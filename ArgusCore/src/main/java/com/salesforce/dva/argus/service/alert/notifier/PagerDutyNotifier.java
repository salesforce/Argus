/*
 * Copyright (c) 2019, Salesforce.com, Inc.
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

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.service.AnnotationService;
import com.salesforce.dva.argus.service.ArgusTransport;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.NotificationContext;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.util.AlertUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.TimeZone;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;


/**
 * PagerDuty notifier
 *
 * @author Phil Liew (pliew@salesforce.com)
 */
@Singleton
public class PagerDutyNotifier extends AuditNotifier {

    private static final Logger LOGGER = LoggerFactory.getLogger(PagerDutyNotifier.class);
    private static final long DEFAULT_HTTP_RESPONSE_CODE_429_RETRY_DELAY_TIME = 1000;

    public static final ThreadLocal<DateFormat> DATE_FORMAT = new ThreadLocal<DateFormat>() {

        @Override
        protected DateFormat initialValue() {
            DateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
            sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
            return sdf;
        }
    };

    private final MonitorService monitorService;
    private final String endpoint;
    private final String token;
    private final ArgusTransport transport;
    private final long httpResponseCode429RetryDelayTime;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new GusNotifier object.
     *
     * @param metricService     The metric service to use.  Cannot be null.
     * @param annotationService The annotation service to use.  Cannot be null.
     * @param auditService      The audit service to use.  Cannot be null.
     * @param config            The system configuration.  Cannot be null.
     * @param emf               The entity manager factory to use.  Cannot be null.
     */
    @Inject
    public PagerDutyNotifier(MetricService metricService, AnnotationService annotationService, AuditService auditService,
                             SystemConfiguration config, Provider<EntityManager> emf, MonitorService monitorService) {
        this(metricService,
                annotationService,
                auditService,
                config,
                emf,
                monitorService,
                config.getValue(Property.PAGERDUTY_ENDPOINT.getName(), Property.PAGERDUTY_ENDPOINT.getDefaultValue()),
                config.getValue(Property.PAGERDUTY_TOKEN.getName(), Property.PAGERDUTY_TOKEN.getDefaultValue()),
                DEFAULT_HTTP_RESPONSE_CODE_429_RETRY_DELAY_TIME);
    }

    protected PagerDutyNotifier(MetricService metricService, AnnotationService annotationService, AuditService auditService,
                                SystemConfiguration config, Provider<EntityManager> emf, MonitorService monitorService,
                                String endpoint, String token, long httpResponseCode429RetryDelayTime) {
        super(metricService, annotationService, auditService, config, emf);
        requireArgument(config != null, "The configuration cannot be null.");
        requireArgument(monitorService != null, "The monitorService cannot be null.");
        requireArgument(!StringUtils.isBlank(endpoint), "The endpoint cannot be blank.");
        requireArgument(!StringUtils.isBlank(token), "The token cannot be blank.");
        this.monitorService = monitorService;
        this.endpoint = endpoint;
        this.token = token;

        String proxyHostString = config.getValue(Property.PAGERDUTY_PROXY_HOST.getName(), Property.PAGERDUTY_PROXY_HOST.getDefaultValue());
        String proxyPortString = config.getValue(Property.PAGERDUTY_PROXY_PORT.getName(), Property.PAGERDUTY_PROXY_PORT.getDefaultValue());
        boolean isValidProxy = ArgusTransport.validateProxyHostAndPortStrings(proxyHostString, proxyPortString);
        this.transport = new ArgusTransport(isValidProxy ? Optional.of(proxyHostString) : Optional.empty(),
                isValidProxy ? Optional.of(Integer.parseInt(proxyPortString)) : Optional.empty(),
                Integer.parseInt(config.getValue(Property.PAGERDUTY_CONNECTION_POOL_MAX_SIZE.getName(), Property.PAGERDUTY_CONNECTION_POOL_MAX_SIZE.getDefaultValue())),
                Integer.parseInt(config.getValue(Property.PAGERDUTY_CONNECTION_POOL_MAX_PER_ROUTE.getName(), Property.PAGERDUTY_CONNECTION_POOL_MAX_PER_ROUTE.getDefaultValue())));
        this.httpResponseCode429RetryDelayTime = httpResponseCode429RetryDelayTime;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public String getName() {
        return PagerDutyNotifier.class.getName();
    }

    @Override
    protected boolean sendAdditionalNotification(NotificationContext context) {
        requireArgument(context != null, "Notification context cannot be null.");
        super.sendAdditionalNotification(context);
        return sendPagerDutyNotification(context, NotificationStatus.TRIGGERED);
    }

    @Override
    protected boolean clearAdditionalNotification(NotificationContext context) {
        requireArgument(context != null, "Notification context cannot be null.");
        super.clearAdditionalNotification(context);
        return sendPagerDutyNotification(context, NotificationStatus.CLEARED);
    }

    protected boolean sendPagerDutyNotification(NotificationContext context, NotificationStatus status) {
        Notification notification = context.getAlertNotification();
        requireArgument(notification != null, "Notification in notification context cannot be null.");
        Trigger trigger = context.getAlertTrigger();
        requireArgument(trigger != null, "Trigger in notification context cannot be null.");

        List<String> routingKeys = context.getNotification().getSubscriptions();
        requireArgument(routingKeys != null && !routingKeys.isEmpty(), "PagerDuty routing keys (subscriptions) cannot be empty.");

        if (routingKeys != null && routingKeys.size() > 1) {
            // Only support one PagerDuty integration, there is no point for customers to get duplicate pager duty incidents for one notification
            String warnMsg = MessageFormat.format("Only one PagerDuty integration key is supported, thus only {0} will be used", routingKeys.get(0));
            LOGGER.warn(warnMsg);
            context.getHistory().appendMessageNUpdateHistory(warnMsg, null, 0);
        }
        String routingKey = routingKeys.get(0);

        String dedupKey = hashNotificationTriggerAndMetric(notification, trigger, context.getTriggeredMetric());
        PagerDutyMessage message = new PagerDutyMessage(routingKey,
                NotificationStatus.TRIGGERED == status ? PagerDutyMessage.EventAction.TRIGGER : PagerDutyMessage.EventAction.RESOLVE,
                dedupKey);
        message.setClient("Argus Alert");
        message.setClientUrl(getAlertUrl(context.getAlert().getId()));
        //message.addLink("Argus alert definition", getAlertUrl(context.getAlert().getId()));
        message.setSummary("[Argus] Notification for Alert: " + context.getAlert().getName() +
                " Notification: " + context.getNotification().getName() + " Trigger: " + context.getTrigger().getName());
        String expression = AlertUtils.getExpressionWithAbsoluteStartAndEndTimeStamps(context);
        message.setEvaluatedMetricExpression(expression);
        message.addLink("Argus metric expression", getExpressionUrl(expression));
        for (String metricToAnnotate : notification.getMetricsToAnnotate()) {
            message.addLink("Argus triggered metrics", getMetricUrl(metricToAnnotate, context.getTriggerFiredTime()));
        }
        message.setTriggerEvaluationTime(new Date(context.getTriggerFiredTime()));
        message.setCooldown(new Date(context.getCoolDownExpiration()));
        message.setTriggeredMetric(context.getTriggeredMetric().getIdentifier());
        message.setTriggerDetails(getTriggerDetails(context.getTrigger(), context));
        message.setTriggeringEventValue(Double.toString(context.getTriggerEventValue()));
        if (null != context.getNotification().getCustomText()) {
            message.setCustomerText(context.getNotification().getCustomText());
        }
        if (null != context.getNotification().getMetricsToAnnotate() && !context.getNotification().getMetricsToAnnotate().isEmpty()) {
            message.setMetricsToAnnotate(context.getNotification().getMetricsToAnnotate());
        }
        message.setSource("Argus");
        message.setSeverity(PagerDutyMessage.Severity.ofLevel(notification.getSeverityLevel()));
        message.setTimestamp(new Date());
        //message.setComponent();
        //message.setGroup();
        //message.setEventClass();

        return sendMessage(context.getHistory(), message);
    }

    protected String hashNotificationTriggerAndMetric(Notification n, Trigger t, Metric m) {
        requireArgument(n != null && n.getId() != null,
                "Notification cannot be null and notification id cannot be null.");
        requireArgument(t != null, "Trigger cannot be null.");
        requireArgument(m != null && m.getIdentifier() != null,
                "Metric cannot be null and metric id cannot be null.");

        String notificationId = n.getId().toString();
        String triggerId = t.getId() != null ? t.getId().toString() : "0";
        int metricId = m.getIdentifier().hashCode();
        return String.format("%s$$%s$$%d", notificationId, triggerId, metricId);
    }

    protected boolean sendMessage(History history, PagerDutyMessage message) {
        boolean result = false;
        String loggerMsg = null;
        int retries = 0;

        String routingKey = message.getRoutingKey();
        PagerDutyMessage.EventAction eventAction = message.getEventAction();
        String dedupKey = message.getDedupKey();

        if (Boolean.valueOf(_config.getValue(SystemConfiguration.Property.PAGERDUTY_ENABLED))) {
            int maxPostAttempts = Integer.parseInt(_config.getValue(Property.PAGERDUTY_POST_MAX_ATTEMPTS.getName(), Property.PAGERDUTY_POST_MAX_ATTEMPTS.getDefaultValue()));

            CloseableHttpClient httpClient = transport.getHttpClient();
            CloseableHttpResponse response = null;

            String messageJson = generateJson(message);

            LOGGER.debug("PagerDuty request=" + messageJson);
            RequestBuilder rb = RequestBuilder.post()
                    .setHeader("Authorization", "Token token=" + token)
                    .setEntity(new StringEntity(messageJson, ContentType.create("application/json")))
                    .setUri(String.format("%s/v2/enqueue", endpoint));

            for (int i = 0; i < maxPostAttempts; i++) {
                try {
                    retries = i;
                    response = httpClient.execute(rb.build());
                    LOGGER.debug("PagerDuty response=" + response);
                    int respCode = response.getStatusLine().getStatusCode();

                    // Check for success
                    if (respCode == HttpStatus.SC_ACCEPTED) {
                        loggerMsg = MessageFormat.format("Success - send PagerDuty Message for PD routingKey {0} to {1} incident (dedupKey: {2})",
                                routingKey, eventAction.getEventActionString(), dedupKey);
                        LOGGER.info(loggerMsg);
                        result = true;
                        break;
                    } else if (respCode == HttpStatus.SC_BAD_REQUEST) {
                        // Bad request, no need to retry
                        final String gusPostResponseBody = EntityUtils.toString(response.getEntity());
                        loggerMsg = MessageFormat.format("Failure - send PagerDuty Message for PD routingKey {0} to {1} incident (dedupKey: {2}) due to bad request, response {3}.",
                                routingKey, eventAction.getEventActionString(), dedupKey, gusPostResponseBody);
                        LOGGER.error(loggerMsg);

                        break;
                    } else if (respCode == 429) {
                        // Too many requests, try again if possible
                        loggerMsg = MessageFormat.format("Failure - send PagerDuty Message for PD routingKey {0} to {1} incident (dedupKey: {2}) due to too many requests.",
                                routingKey, eventAction.getEventActionString(), dedupKey);
                        LOGGER.warn(loggerMsg);

                        /* From PagerDuty documentation:
                           "If your client is throttled, its rate limit will be reset after a minute interval.
                           Your client should expect and be able to handle this error code by waiting a minute before
                           making additional requests."
                           https://v2.developer.pagerduty.com/docs/rate-limiting
                         */
                        Thread.sleep(httpResponseCode429RetryDelayTime);

                        continue;
                    } else if (respCode == HttpStatus.SC_INTERNAL_SERVER_ERROR ||
                            respCode == HttpStatus.SC_BAD_GATEWAY ||
                            respCode == HttpStatus.SC_SERVICE_UNAVAILABLE ||
                            respCode == HttpStatus.SC_GATEWAY_TIMEOUT) {
                        /* From PagerDuty documentation:
                           Retry on 500 or 5XX.
                           https://v2.developer.pagerduty.com/docs/events-api-v2#api-response-codes--retry-logic
                         */
                        loggerMsg = MessageFormat.format("Failure - send PagerDuty Message for PD routingKey {0} to {1} incident (dedupKey: {2}) due to session time out.",
                                routingKey, eventAction.getEventActionString(), dedupKey);
                        LOGGER.warn(loggerMsg);

                        continue;
                    } else {
                        final String postResponseBody = EntityUtils.toString(response.getEntity());
                        loggerMsg = MessageFormat.format("Failure - send PagerDuty Message for PD routingKey {0} to {1} incident (dedupKey: {2}). Response code {3} response {4}",
                                routingKey, eventAction.getEventActionString(), dedupKey, respCode, postResponseBody);
                        LOGGER.error(loggerMsg);

                        break; // don't retry
                    }
                } catch (InterruptedIOException e) {
                    loggerMsg = MessageFormat.format("Interruption failure - send PagerDuty Message for PD routingKey {0} to {1} incident (dedupKey: {2}). Exception {3}",
                            routingKey, eventAction.getEventActionString(), dedupKey, e.getMessage());
                    LOGGER.warn(loggerMsg, e);

                    continue; // retry
                } catch (Exception e) {
                    loggerMsg = MessageFormat.format("Failure - send PagerDuty Message for PD routingKey {0} to {1} incident (dedupKey: {2}). Exception {3}",
                            routingKey, eventAction.getEventActionString(), dedupKey, e.getMessage());
                    LOGGER.error(loggerMsg, e);

                    break; // don't retry
                } finally {
                    try {
                        if (response != null) {
                            response.close();
                        }
                    } catch (IOException e) {
                        LOGGER.error("Exception while attempting to close post to GUS response", e);
                    }
                }
            }
            monitorService.modifyCounter(MonitorService.Counter.PAGERDUTY_NOTIFICATIONS_RETRIES, retries, null);
            monitorService.modifyCounter(MonitorService.Counter.PAGERDUTY_NOTIFICATIONS_FAILED, result ? 0 : 1, null);
        } else {
            loggerMsg = MessageFormat.format("Sending PagerDuty notification is disabled.  Not sending message for PD integration {0} to {1} incident (dedupKey: {2}).",
                    routingKey, eventAction.getEventActionString(), dedupKey);
            LOGGER.warn(loggerMsg);
        }

        if (StringUtils.isNotBlank(loggerMsg)) {
            history.appendMessageNUpdateHistory(loggerMsg, null, 0);
        }
        return result;
    }

    protected String generateJson(PagerDutyMessage message) {
        JsonObject messageJson = new JsonObject();

        messageJson.addProperty(PagerDutyMessage.ROUTING_KEY_FIELD, message.getRoutingKey());
        messageJson.addProperty(PagerDutyMessage.EVENT_ACTION_FIELD, message.getEventAction().getEventActionString());
        messageJson.addProperty(PagerDutyMessage.DEDUP_KEY_FIELD, message.getDedupKey());
        messageJson.addProperty(PagerDutyMessage.CLIENT_FIELD, message.getClient());
        messageJson.addProperty(PagerDutyMessage.CLIENT_URL_FIELD, message.getClientUrl());

        JsonObject payloadJson = new JsonObject();
        payloadJson.addProperty(PagerDutyMessage.SUMMARY_FIELD, message.getSummary());
        if (message.getTimestamp() != null) {
            payloadJson.addProperty(PagerDutyMessage.TIMESTAMP_FIELD, message.getTimestamp());
        }
        payloadJson.addProperty(PagerDutyMessage.SOURCE_FIELD, message.getSource());
        if (message.getSeverity() != null) {
            payloadJson.addProperty(PagerDutyMessage.SEVERITY_FIELD, message.getSeverity().getSeverityString());
        }
        if (message.getComponent() != null) {
            payloadJson.addProperty(PagerDutyMessage.COMPONENT_FIELD, message.getComponent());
        }
        if (message.getGroup() != null) {
            payloadJson.addProperty(PagerDutyMessage.GROUP_FIELD, message.getGroup());
        }
        if (message.getEventClass() != null) {
            payloadJson.addProperty(PagerDutyMessage.CLASS_FIELD, message.getEventClass());
        }

        Map<String, String> customDetailsMap = message.getCustomDetailsMap();
        if (customDetailsMap != null && customDetailsMap.size() > 0) {
            JsonObject customDetailsJson = new JsonObject();
            for (Map.Entry<String, String> e : customDetailsMap.entrySet()) {
                customDetailsJson.addProperty(e.getKey(), e.getValue());
            }
            payloadJson.add(PagerDutyMessage.CUSTOM_DETAILS_FIELD, customDetailsJson);
        }
        messageJson.add(PagerDutyMessage.PAYLOAD_FIELD, payloadJson);

        List<Map<String, String>> links = message.getLinksList();
        if (links != null && links.size() > 0) {
            JsonArray linksArrayJson = new JsonArray();
            for (Map<String, String> l : links) {
                JsonObject linkJson = new JsonObject();
                for (Map.Entry<String, String> e : l.entrySet()) {
                    linkJson.addProperty(e.getKey(), e.getValue());
                }
                linksArrayJson.add(linkJson);
            }
            messageJson.add(PagerDutyMessage.LINKS_FIELD, linksArrayJson);
        }
        return messageJson.toString();
    }

    @Override
    public Properties getNotifierProperties() {
        Properties result = super.getNotifierProperties();

        for (Property property : Property.values()) {
            result.put(property.getName(), property.getDefaultValue());
        }
        return result;
    }

    public enum Property {

        /**
         * The PagerDuty endpoint.
         */
        PAGERDUTY_ENDPOINT("notifier.property.pagerduty.endpoint", "https://events.pagerduty.com"),
        /**
         * The PagerDuty access token.
         */
        PAGERDUTY_TOKEN("notifier.property.pagerduty.token", "TestToken"),
        /**
         * The PagerDuty proxy host.
         */
        PAGERDUTY_PROXY_HOST("notifier.property.pagerduty.proxy.host", ""),
        /**
         * The PagerDuty port.
         */
        PAGERDUTY_PROXY_PORT("notifier.property.pagerduty.proxy.port", ""),
        /**
         * The PagerDuty connection max attempts to post notification.
         */
        PAGERDUTY_POST_MAX_ATTEMPTS("notifier.property.pagerduty.maxPostAttempts", "3"),
        /**
         * The connection pool size for connecting to PagerDuty
         */
        PAGERDUTY_CONNECTION_POOL_MAX_SIZE("notifier.property.pagerduty.connectionpool.maxsize", "55"),
        /**
         * The connection pool max per route for connecting to PagerDuty
         */
        PAGERDUTY_CONNECTION_POOL_MAX_PER_ROUTE("notifier.property.pagerduty.connectionpool.maxperroute", "20");

        private final String _name;
        private final String _defaultValue;

        private Property(String name, String defaultValue) {
            _name = name;
            _defaultValue = defaultValue;
        }

        /**
         * Returns the property name.
         *
         * @return The property name.
         */
        public String getName() {
            return _name;
        }

        /**
         * Returns the default property value.
         *
         * @return The default property value.
         */
        public String getDefaultValue() {
            return _defaultValue;
        }
    }

    public static class PagerDutyMessage {
        public static final String DEFAULT_CLIENT = "Argus Alert";

        public static final String ROUTING_KEY_FIELD = "routing_key";
        public static final String EVENT_ACTION_FIELD = "event_action";
        public static final String DEDUP_KEY_FIELD = "dedup_key";
        public static final String PAYLOAD_FIELD = "payload";
        public static final String CLIENT_FIELD = "client";
        public static final String CLIENT_URL_FIELD = "client_url";

        // subfields for payload
        public static final String SUMMARY_FIELD = "summary";
        public static final String SOURCE_FIELD = "source";
        public static final String SEVERITY_FIELD = "severity";
        public static final String TIMESTAMP_FIELD = "timestamp";
        public static final String COMPONENT_FIELD = "component";
        public static final String GROUP_FIELD = "group";
        public static final String CLASS_FIELD = "class";
        public static final String CUSTOM_DETAILS_FIELD = "custom_details";
        public static final String IMAGES_FIELD = "images";
        public static final String LINKS_FIELD = "links";

        // subfields for custom_details
        public static final String METRIC_EXPRESSION_SUB_FIELD = "Evaluated Metric Expression";
        public static final String EVALUATION_TIME_SUB_FIELD = "Trigger Evaluation Time";
        public static final String COOLDOWN_SUB_FIELD = "Cooldown till";
        public static final String TRIGGERRED_METRIC_SUB_FIELD = "Triggered on Metric";
        public static final String TRIGGERED_DETAILS_SUB_FIELD = "Triggered Details";
        public static final String TRIGGERING_EVENT_VALUE_SUB_FIELD = "Triggering Event Value";
        public static final String CUSTOMER_TEXT_SUB_FIELD = "Customer Text";
        public static final String METRICS_TO_ANNOTATE_SUB_FIELD = "Metrics to annotate";

        //subfields for links
        public static final String LINK_HREF_SUB_FIELD = "href";
        public static final String LINK_TEXT_SUB_FIELD = "text";

        private final String routingKey;
        private final EventAction eventAction;
        private final String dedupKey;
        private String summary;
        private String source;
        private Severity severity;
        private String timestamp;
        private String component;
        private String group;
        private String eventClass;
        private String client;
        private String clientUrl;

        private final Map<String, Map<String, String>> payload = new HashMap<>();
        private final Map<String, String> customDetailsMap = new HashMap<>();
        private final List<Map<String, String>> linksList = new LinkedList<Map<String, String>>();

        protected PagerDutyMessage(String routingKey, EventAction eventAction, String dedupKey) {
            this.routingKey = routingKey;
            this.eventAction = eventAction;
            this.dedupKey = dedupKey;
            this.client = DEFAULT_CLIENT;
            payload.put(CUSTOM_DETAILS_FIELD, customDetailsMap);
        }

        protected void setEvaluatedMetricExpression(String metricExpression) {
            customDetailsMap.put(METRIC_EXPRESSION_SUB_FIELD, metricExpression);
        }

        protected void setTriggerEvaluationTime(Date evaluationTime) {
            customDetailsMap.put(EVALUATION_TIME_SUB_FIELD, DATE_FORMAT.get().format(evaluationTime));
        }

        protected void setCooldown(Date cooldown) {
            customDetailsMap.put(COOLDOWN_SUB_FIELD, DATE_FORMAT.get().format(cooldown));
        }

        protected void setTriggeredMetric(String metric) {
            customDetailsMap.put(TRIGGERRED_METRIC_SUB_FIELD, metric);
        }

        protected void setTriggerDetails(String triggerDetails) {
            customDetailsMap.put(TRIGGERED_DETAILS_SUB_FIELD, triggerDetails);
        }

        protected void setTriggeringEventValue(String eventValue) {
            customDetailsMap.put(TRIGGERING_EVENT_VALUE_SUB_FIELD, eventValue);
        }

        protected void setCustomerText(String customerText) {
            customDetailsMap.put(CUSTOMER_TEXT_SUB_FIELD, customerText);
        }

        protected void setMetricsToAnnotate(List<String> metrics) {
            customDetailsMap.put(METRICS_TO_ANNOTATE_SUB_FIELD, StringUtils.join(metrics));
        }

        protected void addLink(String label, String link) {
            Map<String, String> linkMap = new HashMap<>();
            linkMap.put(LINK_TEXT_SUB_FIELD, label);
            linkMap.put(LINK_HREF_SUB_FIELD, link);
            linksList.add(linkMap);
        }

        protected String getRoutingKey() {
            return routingKey;
        }

        protected EventAction getEventAction() {
            return eventAction;
        }

        protected String getDedupKey() {
            return dedupKey;
        }

        protected String getSummary() {
            return summary;
        }

        protected void setSummary(String summary) {

            this.summary = StringUtils.left(summary, 1024); // summary max length is 1024
        }

        protected String getSource() {
            return source;
        }

        protected void setSource(String source) {
            this.source = source;
        }

        protected Severity getSeverity() {
            return severity;
        }

        protected void setSeverity(Severity severity) {
            this.severity = severity;
        }

        protected String getTimestamp() {
            return timestamp;
        }

        protected void setTimestamp(Date timestamp) {
            this.timestamp = DATE_FORMAT.get().format(timestamp);
        }

        protected String getComponent() {
            return component;
        }

        protected void setComponent(String component) {
            this.component = component;
        }

        protected String getGroup() {
            return group;
        }

        protected void setGroup(String group) {
            this.group = group;
        }

        protected String getEventClass() {
            return eventClass;
        }

        protected void setEventClass(String eventClass) {
            this.eventClass = eventClass;
        }

        protected String getClient() {
            return client;
        }

        protected void setClient(String client) {
            this.client = client;
        }

        protected String getClientUrl() {
            return clientUrl;
        }

        protected void setClientUrl(String clientUrl) {
            this.clientUrl = clientUrl;
        }

        protected Map<String, Map<String, String>> getPayload() {
            return payload;
        }

        protected Map<String, String> getCustomDetailsMap() {
            return customDetailsMap;
        }

        protected List<Map<String, String>> getLinksList() {
            return linksList;
        }

        protected enum EventAction {
            TRIGGER("trigger"),
            ACKNOWLEDGE("acknowledge"),
            RESOLVE("resolve");

            private final String eventActionString;

            EventAction(String eventActionString) {
                this.eventActionString = eventActionString;
            }

            String getEventActionString() {
                return eventActionString;
            }
        }

        protected enum Severity {
            CRITICAL("critical", 1),
            ERROR("error", 2),
            WARNING("warning", 3),
            INFO("info", 4);

            private static final Map<Integer, Severity> levelMap = generateLevelMap();
            private final String severityString;
            private final int severityLevel;

            Severity(String severityString, int severityLevel) {
                this.severityString = severityString;
                this.severityLevel = severityLevel;
            }

            String getSeverityString() {
                return severityString;
            }

            int getSeverityLevel() {
                return severityLevel;
            }

            /**
             * Get Severity enum based on integer severity level; defaults to INFO.
             * @param severityLevel int severity level
             * @return severity enum
             */
            static Severity ofLevel(int severityLevel) {
                return levelMap.getOrDefault(severityLevel, INFO);
            }

            private static Map<Integer, Severity> generateLevelMap() {
                Map<Integer, Severity> map = new HashMap<>();
                for (Severity s : Severity.values()) {
                    map.put(s.getSeverityLevel(), s);
                }
                return ImmutableMap.copyOf(map);
            }
        }
    }

}
/* Copyright (c) 2019, Salesforce.com, Inc.  All rights reserved. */
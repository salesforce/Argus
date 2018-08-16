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

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import com.salesforce.dva.argus.service.metric.MetricReader;
import org.junit.Test;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.NumberOperations;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.entity.Trigger.TriggerType;
import com.salesforce.dva.argus.service.AlertService.Notifier;
import com.salesforce.dva.argus.service.AlertService.SupportedNotifier;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.NotificationContext;
import com.salesforce.dva.argus.service.alert.notifier.AuditNotifier;

public class NotifierTest extends AbstractTest {

    private static final String expression =
        "DIVIDE(-1h:argus.jvm:file.descriptor.open{host=unknown-host}:avg, -1h:argus.jvm:file.descriptor.max{host=unknown-host}:avg)";

    @Test
    public void testDBNotifier() {
        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert_name", expression, "* * * * *");
        Notification notification = new Notification("notification_name", alert, "notifier_ame", new ArrayList<String>(), 23);
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "trigger_name", NumberOperations.bd(2D), 5);

        alert.setNotifications(Arrays.asList(new Notification[] { notification }));
        alert.setTriggers(Arrays.asList(new Trigger[] { trigger }));
        alert = system.getServiceFactory().getAlertService().updateAlert(alert);

        NotificationContext context = new NotificationContext(alert, alert.getTriggers().get(0), notification, 1418319600000L, 0.0, new Metric("scope", "metric"));
        int count = 1 + random.nextInt(5);

        for (int i = 0; i < count; i++) {
            Notifier notifier = system.getServiceFactory().getAlertService().getNotifier(SupportedNotifier.DATABASE);

            notifier.sendNotification(context);
        }

        Notifier notifier = system.getServiceFactory().getAlertService().getNotifier(SupportedNotifier.DATABASE);

        assertEquals(count, AuditNotifier.class.cast(notifier).getAllNotifications(alert).size());
    }

    @Test
    public void testUpdatingTriggerName() {
        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert_name", expression, "* * * * *");
        Notification notification = new Notification("notification_name", alert, "notifier_name", new ArrayList<String>(), 23);
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "${sCopE}-trigger_name-${MEtriC}-trigger_metric-${tag1}-trigger_tag1-${tag2}-trigger_tag2-${tag3}-${tAg2}", 2D, 5);

        alert.setNotifications(Arrays.asList(new Notification[] { notification }));
        alert.setTriggers(Arrays.asList(new Trigger[] { trigger }));
        alert = system.getServiceFactory().getAlertService().updateAlert(alert);

        Metric m = new Metric("scope", "metric");
        Map<String, String> tags = new HashMap<>();
        tags.put("tag1","val1");
        tags.put("tag2", "val2");
        m.setTags(tags);
        NotificationContext context = new NotificationContext(alert, alert.getTriggers().get(0), notification, 1418319600000L, 0.0, m);
        Notifier notifier = system.getServiceFactory().getAlertService().getNotifier(SupportedNotifier.GOC);
        notifier.sendNotification(context);
        assertEquals("${sCopE}-trigger_name-${MEtriC}-trigger_metric-${tag1}-trigger_tag1-${tag2}-trigger_tag2-${tag3}-${tAg2}", context.getTrigger().getName());
        assertEquals("scope-trigger_name-metric-trigger_metric-val1-trigger_tag1-val2-trigger_tag2-${tag3}-val2", system.getNotifierFactory().getGOCNotifier().replaceTemplatesInName(context.getTrigger().getName(), "scope", "metric", tags));


    }

    @Test
    public void testTemplateNaming() {
        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "${sCopE}-trigger_name-${MEtriC}-trigger_metric-${tag1}-trigger_tag1-${tag2}-trigger_tag2-${TAg3}-${tAg2}", expression, "* * * * *");
        Notification notification = new Notification("notification_name", alert, "notifier_name", new ArrayList<String>(), 23);
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "trigger_name", 2D, 5);

        alert.setNotifications(Arrays.asList(new Notification[] { notification }));
        alert.setTriggers(Arrays.asList(new Trigger[] { trigger }));
        alert = system.getServiceFactory().getAlertService().updateAlert(alert);

        Metric m = new Metric("scope", "metric");
        Map<String, String> tags = new HashMap<>();
        tags.put("tag1","val1");
        tags.put("tag2", "val2");
        m.setTags(tags);
        NotificationContext context = new NotificationContext(alert, alert.getTriggers().get(0), notification, 1418319600000L, 0.0, m);
        Notifier notifier = system.getServiceFactory().getAlertService().getNotifier(SupportedNotifier.GOC);
        notifier.sendNotification(context);
        assertEquals("${sCopE}-trigger_name-${MEtriC}-trigger_metric-${tag1}-trigger_tag1-${tag2}-trigger_tag2-${TAg3}-${tAg2}", context.getAlert().getName());
        assertEquals("scope-trigger_name-metric-trigger_metric-val1-trigger_tag1-val2-trigger_tag2-${TAg3}-val2", system.getNotifierFactory().getGOCNotifier().replaceTemplatesInName(context.getAlert().getName(), "scope", "metric", tags));


    }

    @Test
    public void testAbsoluteTimeStampsInExpression() {

        Long alertEnqueueTime = 1418319600000L;
        ArrayList<String> expressionArray = new ArrayList<String> (Arrays.asList(
                "-20m:-0d:scone.*.*.cs19:acs.DELETERequestProcessingTime_95thPercentile{device=*acs2-1*}:avg",
                "  SCALE( SUM( DIVIDE( DIFF( DOWNSAMPLE( SUM( CULL_BELOW( DERIVATIVE(                   -40m:core.*.*.na63:SFDC_type-Stats-name1-Search-name2-Client-name3-Query_Count__SolrLive.Count{device=na63-app*}:sum:1m-max ), #0.001#, #value# ), #union# ), #10m-sum# ), DOWNSAMPLE( SUM( CULL_BELOW( DERIVATIVE(                   -40m:core.*.*.na63:SFDC_type-Stats-name1-Search-name2-Client-name3-Search_Fallbacks__SolrLive.Count{device=na63-app*}:sum:1m-max ), #0.01#, #value# ), #union# ), #10m-sum# ), #union# ), CULL_BELOW( DOWNSAMPLE( SUM( CULL_BELOW( DERIVATIVE( -40m:core.*.*.na63:SFDC_type-Stats-name1-Search-name2-Client-name3-Query_Count__SolrLive.Count{device=na63-app*}:sum:1m-max ), #0.001#, #value# ), #union# ), #10m-sum# ), #1000#, #value# ) ), #-1# ), #-100# ) ",
                "ABOVE(-1d:scope:metric:avg:4h-avg, #0.5#, #avg#)",
                "ABOVE(-1h:scope:metric:avg:4h-avg, #0.5#)",
                "ALIASBYTAG(-1s:scope:metric{device=*,source=*}:sum)",
                "FILL( #-1D#, #-0d#,#4h#,#0m#,#100#)",
                "GROUPBY(-2d:-1d:scope:metricA{host=*}:avg,#(myhost[1-9])#, #SUM#, #union#)",
                "LIMIT( -21d:-1d:scope:metricA:avg:4h-avg, -1d:scope:metricB:avg:4h-avg,#1#)",
                "RANGE(-10d:scope:metric[ABCD]:avg:1d-max)",
                "DOWNSAMPLE(DOWNSAMPLE(GROUPBYTAG(CULL_BELOW(-115m:-15m:iot-provisioning-server.PRD.SP2.-:health.status{device=provisioning-warden-*}:avg:1m-max, #1#, #value#), #DeploymentName#, #MAX#), #1m-max#), #10m-count#)",
                "DOWNSAMPLE(CULL_BELOW(DERIVATIVE(-115m:-15m:iot-container.PRD.NONE.-:iot.flows.state.load.errors_count{flowsnakeEnvironmentName=iot-prd-stmfa-00ds70000000mqy}:zimsum:1m-sum), #0#, #value#), #10m-sum#)"
        ));

        ArrayList<String> expectedOutput = new ArrayList<String> (Arrays.asList(
                "1418318400000:1418319600000:scone.*.*.cs19:acs.DELETERequestProcessingTime_95thPercentile{device=*acs2-1*}:avg",
                "SCALE(SUM(DIVIDE(DIFF(DOWNSAMPLE(SUM(CULL_BELOW(DERIVATIVE(1418317200000:core.*.*.na63:SFDC_type-Stats-name1-Search-name2-Client-name3-Query_Count__SolrLive.Count{device=na63-app*}:sum:1m-max),#0.001#,#value#),#union#),#10m-sum#),DOWNSAMPLE(SUM(CULL_BELOW(DERIVATIVE(1418317200000:core.*.*.na63:SFDC_type-Stats-name1-Search-name2-Client-name3-Search_Fallbacks__SolrLive.Count{device=na63-app*}:sum:1m-max),#0.01#,#value#),#union#),#10m-sum#),#union#),CULL_BELOW(DOWNSAMPLE(SUM(CULL_BELOW(DERIVATIVE(1418317200000:core.*.*.na63:SFDC_type-Stats-name1-Search-name2-Client-name3-Query_Count__SolrLive.Count{device=na63-app*}:sum:1m-max),#0.001#,#value#),#union#),#10m-sum#),#1000#,#value#)),#-1#),#-100#)",
                "ABOVE(1418233200000:scope:metric:avg:4h-avg,#0.5#,#avg#)",
                "ABOVE(1418316000000:scope:metric:avg:4h-avg,#0.5#)",
                "ALIASBYTAG(1418319599000:scope:metric{device=*,source=*}:sum)",
                "FILL(#1418233200000#,#1418319600000#,#4h#,#0m#,#100#)",
                "GROUPBY(1418146800000:1418233200000:scope:metricA{host=*}:avg,#(myhost[1-9])#,#SUM#,#union#)",
                "LIMIT(1416505200000:1418233200000:scope:metricA:avg:4h-avg,1418233200000:scope:metricB:avg:4h-avg,#1#)",
                "RANGE(1417455600000:scope:metric[ABCD]:avg:1d-max)",
                "DOWNSAMPLE(DOWNSAMPLE(GROUPBYTAG(CULL_BELOW(1418312700000:1418318700000:iot-provisioning-server.PRD.SP2.-:health.status{device=provisioning-warden-*}:avg:1m-max,#1#,#value#),#DeploymentName#,#MAX#),#1m-max#),#10m-count#)",
                "DOWNSAMPLE(CULL_BELOW(DERIVATIVE(1418312700000:1418318700000:iot-container.PRD.NONE.-:iot.flows.state.load.errors_count{flowsnakeEnvironmentName=iot-prd-stmfa-00ds70000000mqy}:zimsum:1m-sum),#0#,#value#),#10m-sum#)"
                ));

        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert_name", expressionArray.get(0), "* * * * *");
        Notification notification = new Notification("notification_name", alert, "notifier_name", new ArrayList<String>(), 23);
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "trigger_name", 2D, 5);

        alert.setNotifications(Arrays.asList(new Notification[] { notification }));
        alert.setTriggers(Arrays.asList(new Trigger[] { trigger }));
        alert = system.getServiceFactory().getAlertService().updateAlert(alert);

        NotificationContext context = new NotificationContext(alert, alert.getTriggers().get(0), notification, 1418320200000L, 0.0, new Metric("scope", "metric"));
        context.setAlertEnqueueTimestamp(alertEnqueueTime);

        ArrayList<String> actualOutput = new ArrayList<String>();
        for (String currentExpression: expressionArray) {
            alert.setExpression(currentExpression);
            actualOutput.add(system.getNotifierFactory().getGOCNotifier().getExpressionWithAbsoluteStartAndEndTimeStamps(context));
        }

        assertEquals(expectedOutput, actualOutput);
    }

}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

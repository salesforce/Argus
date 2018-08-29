package com.salesforce.dva.argus.util;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.service.AlertService;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class TemplateReplacementTest extends AbstractTest {

    private static final String expression =
            "DIVIDE(-1h:argus.jvm:file.descriptor.open{host=unknown-host}:avg, -1h:argus.jvm:file.descriptor.max{host=unknown-host}:avg)";

    @Test
    public void testTemplateNaming() {
        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "${sCopE}-trigger_name-${MEtriC}-trigger_metric-${tag1}-trigger_tag1-${tag2}-trigger_tag2-${TAg3}-${tAg2}", expression, "* * * * *");
        Notification notification = new Notification("notification_name", alert, "notifier_name", new ArrayList<String>(), 23);
        Trigger trigger = new Trigger(alert, Trigger.TriggerType.GREATER_THAN_OR_EQ, "${sCopE}-trigger_name-${MEtriC}-trigger_metric-${tag1}-trigger_tag1-${tag2}-trigger_tag2-${tag3}-${tAg2}", 2D, 5);

        alert.setNotifications(Arrays.asList(new Notification[] { notification }));
        alert.setTriggers(Arrays.asList(new Trigger[] { trigger }));
        alert = system.getServiceFactory().getAlertService().updateAlert(alert);

        Metric m = new Metric("scope", "metric");
        Map<String, String> tags = new HashMap<>();
        tags.put("tag1","val1");
        tags.put("tag2", "val2");
        tags.put("tag3", "val3");
        m.setTags(tags);
        DefaultAlertService.NotificationContext context = new DefaultAlertService.NotificationContext(alert, alert.getTriggers().get(0), notification, 1418319600000L, 0.0, m);
        AlertService.Notifier notifier = system.getServiceFactory().getAlertService().getNotifier(AlertService.SupportedNotifier.GOC);
        notifier.sendNotification(context);
        assertEquals("${sCopE}-trigger_name-${MEtriC}-trigger_metric-${tag1}-trigger_tag1-${tag2}-trigger_tag2-${TAg3}-${tAg2}", context.getAlert().getName());
        assertEquals("${sCopE}-trigger_name-${MEtriC}-trigger_metric-${tag1}-trigger_tag1-${tag2}-trigger_tag2-${tag3}-${tAg2}", context.getTrigger().getName());
        assertEquals("scope-trigger_name-metric-trigger_metric-val1-trigger_tag1-val2-trigger_tag2-val3-val2", TemplateReplacement.applyTemplateChanges(context, context.getAlert().getName()));
    }

    @Test
    public void testObjectTemplateReplacement() {

        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert_name-${mETRIc}", expression, "* * * * *");
        Notification notification = new Notification("notification_name-${tAg}", alert, "notifier_name", new ArrayList<String>(), 23);
        Trigger trigger = new Trigger(alert, Trigger.TriggerType.GREATER_THAN_OR_EQ, "${sCopE}-trigger_name", 2D, 7D,5);

        alert.setNotifications(Arrays.asList(new Notification[] { notification }));
        alert.setTriggers(Arrays.asList(new Trigger[] { trigger }));
        alert = system.getServiceFactory().getAlertService().updateAlert(alert);

        Metric m = new Metric("scope", "metric");
        Map<String, String> tags = new HashMap<>();
        tags.put("tag","val");
        m.setTags(tags);
        DefaultAlertService.NotificationContext context = new DefaultAlertService.NotificationContext(alert, alert.getTriggers().get(0), notification, 1418319600000L, 0.0, m);

        String customTemplate = "Alert Name = ${alert.name}, \n" +
                "Alert Expression = ${alert.expression}, \n" +
                "Alert cronEntry = ${alert.cronEntry}, \n" +
                "Alert enabled = ${alert.enabled?then('alert enabled', 'alert not enabled')}, \n" +
                "Alert Expression = ${alert.expression}, \n" +
                "Trigger Name = ${trigger.name}, \n" +
                "Trigger type = ${trigger.type}, \n" +
                "Trigger threshold = ${trigger.threshold}, \n" +
                "Trigger secondaryThreshold = ${trigger.secondaryThreshold}, \n" +
                "Trigger Inertia = ${trigger.inertia}, \n" +
                "Trigger Value = ${triggerValue}, \n" +
                "Trigger Timestamp = ${triggerTimestamp}, \n" +
                "Notification Name = ${notification.name}, \n" +
                "Notification cooldownPeriod = ${notification.cooldownPeriod}, \n" +
                "Notification SRActionable = ${notification.SRActionable?then('SR Actionable','Not SR Actionable')}, \n" +
                "Notification severityLevel = ${notification.severityLevel}";

        String expectedOutput = "Alert Name = alert_name-metric, \n" +
                "Alert Expression = DIVIDE(-1h:argus.jvm:file.descriptor.open{host=unknown-host}:avg, -1h:argus.jvm:file.descriptor.max{host=unknown-host}:avg), \n" +
                "Alert cronEntry = * * * * *, \n" +
                "Alert enabled = alert not enabled, \n" +
                "Alert Expression = DIVIDE(-1h:argus.jvm:file.descriptor.open{host=unknown-host}:avg, -1h:argus.jvm:file.descriptor.max{host=unknown-host}:avg), \n" +
                "Trigger Name = scope-trigger_name, \n" +
                "Trigger type = GREATER_THAN_OR_EQ, \n" +
                "Trigger threshold = 2, \n" +
                "Trigger secondaryThreshold = 7, \n" +
                "Trigger Inertia = 5, \n" +
                "Trigger Value = 0, \n" +
                "Trigger Timestamp = 1,418,319,600,000, \n" +
                "Notification Name = notification_name-val, \n" +
                "Notification cooldownPeriod = 23, \n" +
                "Notification SRActionable = Not SR Actionable, \n" +
                "Notification severityLevel = 5";
        assertEquals(expectedOutput, TemplateReplacement.applyTemplateChanges(context, customTemplate));
    }

}
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

public class TemplateReplacerTest extends AbstractTest {

    private static final String expression =
            "DIVIDE(-1h:argus.jvm:file.descriptor.open{host=unknown-host}:avg, -1h:argus.jvm:file.descriptor.max{host=unknown-host}:avg)";

    @Test
    public void testTemplateNaming() {
        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "${sCopE}-trigger_name-${MEtriC}-trigger_metric-${tag.tag1}-trigger_tag1-${tag.tag2}-trigger_tag2-${tag.TAg3}-${tag.tAg2}", expression, "* * * * *");
        Notification notification = new Notification("notification_name", alert, "notifier_name", new ArrayList<String>(), 23);
        Trigger trigger = new Trigger(alert, Trigger.TriggerType.GREATER_THAN_OR_EQ, "${sCopE}-trigger_name-${MEtriC}-trigger_metric-${tag.tag1}-trigger_tag1-${tag.tag2}-trigger_tag2-${tag.tag3}-${tag.tAg2}", 2D, 5);

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
        assertEquals("${sCopE}-trigger_name-${MEtriC}-trigger_metric-${tag.tag1}-trigger_tag1-${tag.tag2}-trigger_tag2-${tag.TAg3}-${tag.tAg2}", context.getAlert().getName());
        assertEquals("${sCopE}-trigger_name-${MEtriC}-trigger_metric-${tag.tag1}-trigger_tag1-${tag.tag2}-trigger_tag2-${tag.tag3}-${tag.tAg2}", context.getTrigger().getName());
        assertEquals("scope-trigger_name-metric-trigger_metric-val1-trigger_tag1-val2-trigger_tag2-val3-val2", TemplateReplacer.applyTemplateChanges(context, context.getAlert().getName()));
    }

    @Test
    public void testObjectTemplateReplacement() {

        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert_name-${mETRIc}", expression, "* * * * *");
        Notification notification = new Notification("notification_name-${tag.tAg}", alert, "notifier_name", new ArrayList<String>(), 23);
        Trigger trigger = new Trigger(alert, Trigger.TriggerType.GREATER_THAN_OR_EQ, "${sCopE}-trigger_name", 2D, 7D,5);

        alert.setNotifications(Arrays.asList(new Notification[] { notification }));
        alert.setTriggers(Arrays.asList(new Trigger[] { trigger }));
        alert = system.getServiceFactory().getAlertService().updateAlert(alert);

        Metric m = new Metric("scope", "metric");
        Map<String, String> tags = new HashMap<>();
        tags.put("tag","val");
        m.setTags(tags);
        DefaultAlertService.NotificationContext context = new DefaultAlertService.NotificationContext(alert, alert.getTriggers().get(0), notification, 1418319600000L, 0.0, m);

        String customTemplate = "Alert Name = ${alert.name?upper_case}, \n" +
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
                "Trigger Timestamp = ${triggerTimestamp?datetime?iso('GMT')}, \n" +
                "Notification Name = ${notification.name?cap_first}, \n" +
                "Notification cooldownPeriod = ${notification.cooldownPeriod}, \n" +
                "Notification SRActionable = ${notification.SRActionable?then('SR Actionable','Not SR Actionable')}, \n" +
                "Notification severityLevel = ${notification.severityLevel}";

        String expectedOutput = "Alert Name = ALERT_NAME-metric, \n" +
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
                "Trigger Timestamp = 2014-12-11T17:40:00Z, \n" +
                "Notification Name = Notification_name-val, \n" +
                "Notification cooldownPeriod = 23, \n" +
                "Notification SRActionable = Not SR Actionable, \n" +
                "Notification severityLevel = 5";
        assertEquals(expectedOutput, TemplateReplacer.applyTemplateChanges(context, customTemplate));
    }

    @Test
    public void testConditionalOutput() {
        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert_name", expression, "* * * * *");
        Notification notification = new Notification("notification_name", alert, "notifier_name", new ArrayList<String>(), 23);
        Trigger trigger = new Trigger(alert, Trigger.TriggerType.GREATER_THAN_OR_EQ, "trigger_name", 2D, 7.1D,5);

        alert.setNotifications(Arrays.asList(new Notification[] { notification }));
        alert.setTriggers(Arrays.asList(new Trigger[] { trigger }));
        alert = system.getServiceFactory().getAlertService().updateAlert(alert);

        Metric m = new Metric("scope", "metric");
        Map<String, String> tags = new HashMap<>();
        tags.put("tag","val");
        m.setTags(tags);
        DefaultAlertService.NotificationContext context = new DefaultAlertService.NotificationContext(alert, alert.getTriggers().get(0), notification, 1418319600000L, 1.5, m);

        String customTemplate = "<#if trigger.threshold <= 4> Primary Threshold is less than 4 </#if>, \n" +
                "<#if (trigger.secondaryThreshold == 7.1)> Secondary Threshold is 7.1 </#if>, \n" +
                "<#if trigger.inertia == 5 && (trigger.threshold > 5)> Inertia is 5, Primary Threshold more than 5 <#elseif  (trigger.threshold > 5)>Primary Threshold more than 5 <#elseif trigger.inertia == 5> Inertia is 5 </#if>, \n" +
                "<#if trigger.name?matches('trigger_name') && triggerValue < 2.0> Trigger name matches and trigger value is < 1 </#if>, \n" +
                "<#if triggerValue?round == 2> Trigger fired rounded value is 2 </#if>, \n" +
                "<#assign dt = triggerTimestamp?datetime> Trigger fired date-time: ${dt?iso('GMT')}, \n" +
                " Time before 2.5 hrs of firing: ${dt?iso('GMT-02:30')}";


        String expectedOutput = " Primary Threshold is less than 4 , \n" +
                " Secondary Threshold is 7.1 , \n" +
                " Inertia is 5 , \n" +
                " Trigger name matches and trigger value is < 1 , \n" +
                " Trigger fired rounded value is 2 , \n" +
                " Trigger fired date-time: 2014-12-11T17:40:00Z, \n" +
                " Time before 2.5 hrs of firing: 2014-12-11T15:10:00-02:30";
        assertEquals(expectedOutput, TemplateReplacer.applyTemplateChanges(context, customTemplate));
    }

    @Test
    public void testCornerCases() {
        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "${alert.name}", expression, "* * * * *");
        Notification notification = new Notification("${notification.name}", alert, "notifier_name", new ArrayList<String>(), 23);
        Trigger trigger = new Trigger(alert, Trigger.TriggerType.GREATER_THAN_OR_EQ, "${trigger.name}", 2D, 7.1D,5);

        alert.setNotifications(Arrays.asList(new Notification[] { notification }));
        alert.setTriggers(Arrays.asList(new Trigger[] { trigger }));
        alert = system.getServiceFactory().getAlertService().updateAlert(alert);

        Metric m = new Metric("scope", "metric");
        Map<String, String> tags = new HashMap<>();
        tags.put("tag","val");
        m.setTags(tags);
        DefaultAlertService.NotificationContext context = new DefaultAlertService.NotificationContext(alert, alert.getTriggers().get(0), notification, 1418319600000L, 1.5, m);

        String customTemplate = "Alert Name = ${alert.name}, Notification Name = ${notification.name}, Trigger Name = ${trigger.name}";


        String expectedOutput = customTemplate;
        assertEquals(expectedOutput, TemplateReplacer.applyTemplateChanges(context, customTemplate));
    }

}
package com.salesforce.dva.argus.entity;

import com.salesforce.dva.argus.service.AlertService;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.salesforce.dva.argus.TestUtils.generateAlert;
import static com.salesforce.dva.argus.TestUtils.generateNotification;
import static com.salesforce.dva.argus.TestUtils.generateTrigger;
import static org.junit.Assert.*;

public class NotificationTest {

    @Test
    public void testSetSubscriptionsForEmailValidationForValidMails() {
        Notification testNotification = new Notification();
        testNotification.setNotifierName(AlertService.SupportedNotifier.EMAIL.getName());
        List<String> validSubscriptions = new ArrayList<>();
        validSubscriptions.add("foo@company.com");
        validSubscriptions.add("bar@com.pany.com");
        validSubscriptions.add("first.mid.last@company.com");
        validSubscriptions.add("first_mid.last@com.pany.co.in");
        testNotification.setSubscriptions(validSubscriptions);
        assertEquals(validSubscriptions, testNotification.getSubscriptions());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetSubscriptionsForEmailValidationForInvalidMails() {
        Notification testNotification = new Notification();
        testNotification.setNotifierName(AlertService.SupportedNotifier.EMAIL.getName());
        List<String> validSubscriptions = new ArrayList<>();
        validSubscriptions.add("bar");
        validSubscriptions.add("");
        validSubscriptions.add("    ");
        testNotification.setSubscriptions(validSubscriptions);
    }

    @Test
    public void testSetSubject() {
        String subject = "TEST_SUBJECT";
        Notification testNotification = new Notification();
        testNotification.setEmailSubject(subject);
        assertEquals(subject, testNotification.getEmailSubject());
    }

    @Test
    public void testEnableClearNotificationDefaultValue() {
        Notification testNotification = new Notification();
        assertTrue(testNotification.isEnableClearNotification());
    }

    @Test
    public void testEnableClearNotificationFalseValue() {
        Notification testNotification = new Notification();
        testNotification.setEnableClearNotification(false);
        assertFalse(testNotification.isEnableClearNotification());
    }

    @Test
    public void testIsEquals() {
        String expr = "DIVIDE(-1h:argus.jvm:file.descriptor.open{host=unknown-host}:avg, -1h:argus.jvm:file.descriptor.max{host=unknown-host}:avg)";
        PrincipalUser admin = new PrincipalUser(null, "test-user", "test-user@salesforce.com");
        Alert alert = generateAlert("alert-name", admin, expr);
        Trigger trigger = generateTrigger("trigger-name", alert);
        Notification notification = generateNotification("notification-name", alert, Arrays.asList(new Trigger[]{trigger}));

        assertEquals(notification, new Notification(
                notification.getName(),
                alert,
                notification.getNotifierName(),
                notification.getSubscriptions(),
                notification.getCooldownPeriod()));
    }
}
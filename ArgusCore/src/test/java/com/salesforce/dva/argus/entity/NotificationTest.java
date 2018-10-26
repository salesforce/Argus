package com.salesforce.dva.argus.entity;

import com.salesforce.dva.argus.service.AlertService;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

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
}
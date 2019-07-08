package com.salesforce.dva.argus.entity;

import org.junit.Ignore;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static com.salesforce.dva.argus.TestUtils.generateAlert;
import static com.salesforce.dva.argus.TestUtils.generateTrigger;
import static com.salesforce.dva.argus.TestUtils.generateNotification;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;


public class AlertTest {

    private static final String EXPRESSION =
            "DIVIDE(-1h:argus.jvm:file.descriptor.open{host=unknown-host}:avg, -1h:argus.jvm:file.descriptor.max{host=unknown-host}:avg)";
    private static PrincipalUser admin = new PrincipalUser(null, "test-user", "test-user@salesforce.com");

    @Test
    public void testCopyConstructor_noNotificationsAndTriggers() throws Exception {
        Alert alert = generateAlert("alert-name", admin, EXPRESSION);
        PrincipalUser newUser = new PrincipalUser(admin, "second-user", "second-user@salesforce.com");
        Alert copy = new Alert(alert, alert.getName()+"_copy", newUser);

        assertTrue(alert.isEnabled() == copy.isEnabled());
        assertTrue(copy.getName().equals(alert.getName()+"_copy"));
        assertTrue(copy.getOwner().equals(newUser));
        assertTrue(copy.getExpression().equals(alert.getExpression()));
        assertTrue(copy.getCronEntry().equals(alert.getCronEntry()));
        assertTrue(copy.isMissingDataNotificationEnabled() == alert.isMissingDataNotificationEnabled());
        assertTrue(copy.isShared() == alert.isShared());
        assertTrue(copy.isValid() == alert.isValid());
        assertTrue(copy.getNotifications().isEmpty());
        assertTrue(copy.getTriggers().isEmpty());
        assertFalse(copy.toString().equals(alert.toString()));
    }

    @Test
    public void testCopyConstructor_withNotificationsAndTriggers() throws Exception {
        Alert alert = generateAlert("alert-name", admin, EXPRESSION);
        Trigger trigger = generateTrigger("trigger-name", alert);
        Trigger triggerNoNotification = generateTrigger("trigger-no-notification", alert);

        Notification notification = generateNotification("notification-name", alert, Arrays.asList(new Trigger[]{trigger}));
        alert.setNotifications(Arrays.asList(new Notification[]{notification}));
        alert.setTriggers(Arrays.asList(new Trigger[]{trigger, triggerNoNotification}));

        PrincipalUser newUser = new PrincipalUser(admin, "second-user", "second-user@salesforce.com");
        Alert copy = new Alert(alert, alert.getName()+"_copy", newUser);

        assertTrue(alert.isEnabled() == copy.isEnabled());
        assertTrue(copy.getName().equals(alert.getName()+"_copy"));
        assertTrue(copy.getOwner().equals(newUser));
        assertTrue(copy.getExpression().equals(alert.getExpression()));
        assertTrue(copy.getCronEntry().equals(alert.getCronEntry()));
        assertTrue(copy.isMissingDataNotificationEnabled() == alert.isMissingDataNotificationEnabled());
        assertTrue(copy.isShared() == alert.isShared());
        assertTrue(copy.isValid() == alert.isValid());
        assertFalse(copy.toString().equals(alert.toString()));

        Notification copiedNotification = copy.getNotifications().get(0);
        assertFalse(copiedNotification.equals(notification));
        assertTrue(copiedNotification.getName().equals(notification.getName()));
        assertTrue(copiedNotification.getNotifierName().equals(notification.getNotifierName()));
        assertTrue(copiedNotification.getCreatedBy().equals(newUser));
        assertTrue(copiedNotification.getSRActionable() == notification.getSRActionable());

        Trigger copiedTrigger1 = copy.getTriggers().get(0);

        assertFalse(copiedTrigger1.equals(trigger));
        assertTrue(copiedTrigger1.getName().equals(trigger.getName()));
        assertTrue(copiedTrigger1.getThreshold().equals(trigger.getThreshold()));
        assertTrue(copiedTrigger1.getCreatedBy().equals(newUser));
        assertTrue(copiedTrigger1.getType().equals(trigger.getType()));
        assertTrue(copiedTrigger1.getInertia() == trigger.getInertia());

        Trigger copiedTrigger2 = copy.getTriggers().get(1);
        assertFalse(copiedTrigger2.equals(triggerNoNotification));
        assertTrue(copiedTrigger2.getName().equals(triggerNoNotification.getName()));
        assertTrue(copiedTrigger2.getThreshold().equals(triggerNoNotification.getThreshold()));
        assertTrue(copiedTrigger2.getCreatedBy().equals(newUser));
        assertTrue(copiedTrigger2.getType().equals(triggerNoNotification.getType()));
        assertTrue(copiedTrigger2.getInertia() == triggerNoNotification.getInertia());
    }

    @Test
    public void testIsEqual_areEqual() {
        Alert alert = generateAlert("alert-name", admin, EXPRESSION);
        Trigger trigger = generateTrigger("trigger-name", alert);
        Notification notification = generateNotification("notification-name", alert, Arrays.asList(new Trigger[]{trigger}));
        alert.setNotifications(Arrays.asList(new Notification[]{notification}));
        alert.setTriggers(Arrays.asList(new Trigger[]{trigger}));
        alert.setEnabled(true);

        Alert test = alert;

        assertTrue(alert.equals(test));
    }

    @Test
    public void testIsEqual_areEqual2() throws Exception {
        Alert alert = generateAlert("alert-name", admin, EXPRESSION);
        Trigger trigger = generateTrigger("trigger-name", alert);
        Notification notification = generateNotification("notification-name", alert, Arrays.asList(new Trigger[]{trigger}));
        alert.setNotifications(Arrays.asList(new Notification[]{notification}));
        alert.setTriggers(Arrays.asList(new Trigger[]{trigger}));
        alert.setEnabled(true);

        Alert test = new Alert(alert, alert.getName(), admin);

        assertTrue(alert.equals(test));
    }

    @Test
    public void testIsEqual_areNotEqual() {
        Alert alert = generateAlert("alert-name", admin, EXPRESSION);
        Trigger trigger = generateTrigger("trigger-name", alert);
        Notification notification = generateNotification("notification-name", alert, Arrays.asList(new Trigger[]{trigger}));
        alert.setNotifications(Arrays.asList(new Notification[]{notification}));
        alert.setTriggers(Arrays.asList(new Trigger[]{trigger}));
        alert.setEnabled(true);

        Alert other = generateAlert("different-name", admin, EXPRESSION);
        Trigger trigger2 = generateTrigger("trigger-name", other);
        Notification notification2 = generateNotification("notification-name", other, Arrays.asList(new Trigger[]{trigger2}));
        other.setNotifications(Arrays.asList(new Notification[]{notification2}));
        other.setTriggers(Arrays.asList(new Trigger[]{trigger2}));
        other.setEnabled(true);

        assertFalse(alert.equals(other));
    }

    @Test
    public void testIsEqual_areNotEqual_fieldChanged() throws Exception {
        // create an alert
        Alert original = generateAlert("alert-name", admin, EXPRESSION);
        Trigger trigger = generateTrigger("trigger-name", original);
        Notification notification = generateNotification("notification-name", original, Arrays.asList(new Trigger[]{trigger}));
        original.setNotifications(Arrays.asList(new Notification[]{notification}));
        original.setTriggers(Arrays.asList(new Trigger[]{trigger}));
        original.setEnabled(true);

        // create copy
        Alert different = new Alert(original, original.getName(), admin);
        // change one field
        different.setShared(true);

        assertFalse(original.equals(different));
    }

    @Test
    public void testIsEqual_areNotEqual_triggerChanged() throws Exception {
        // create an alert
        Alert original = generateAlert("alert-name", admin, EXPRESSION);
        Trigger trigger = generateTrigger("trigger-name", original);
        Notification notification = generateNotification("notification-name", original, Arrays.asList(new Trigger[]{trigger}));
        original.setNotifications(Arrays.asList(new Notification[]{notification}));
        original.setTriggers(Arrays.asList(new Trigger[]{trigger}));
        original.setEnabled(true);

        // create copy
        Alert different = new Alert(original, original.getName(), admin);
        ((Trigger) different.getTriggers().toArray()[0]).setName("a-new-trigger-name");

        assertFalse(original.equals(different));
    }
}
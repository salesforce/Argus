package com.salesforce.dva.argus.service.alert.notifier;

import com.google.common.collect.ImmutableList;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.service.AnnotationService;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.MailService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.NotificationContext;
import com.salesforce.dva.argus.service.mail.EmailContext;
import com.salesforce.dva.argus.system.SystemConfiguration;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatcher;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;

import static com.salesforce.dva.argus.TestUtils.generateAlert;
import static com.salesforce.dva.argus.TestUtils.getHistory;
import static com.salesforce.dva.argus.TestUtils.getNotification;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class EmailNotifierTest {
    private static final SecureRandom random = new SecureRandom();
    private static final String ALERT_NAME = "TestName";
    private static final String METRIC_NAME = "argus.jvm:cores.active:max";
    private static final String METRIC_SCOPE_NAME = "MetricScope";
    private static final Trigger.TriggerType TRIGGER_TYPE = Trigger.TriggerType.GREATER_THAN;
    private static final String TRIGGER_NAME = "TestTrigger";
    private static final String TRIGGER_NAME2 = "TestTrigger2";
    private static final String TRIGGER_THRESHOLD = "1.0";
    private static final String TRIGGER_THRESHOLD2 = "2.0";
    private static final String TRIGGER_INERTIA_MILLIS = "0";
    private static final String TRIGGER_INERTIA_MILLIS2 = "1";
    private static final int TRIGGER_EVENT_VALUE = 5;
    private static final long TRIGGER_FIRED_TIME_SECONDS = 1349333576;
    private static String TRIGGER_FIRED_DATE_GMT = getDateFormat(TRIGGER_FIRED_TIME_SECONDS);
    private static String TRIGGERED = "Triggered";
    private static String CLEARED = "Cleared";
    private static final String EMAIL_NOTIFICATION_NAME = "TEST EMAIL NOTIFICATION";
    private static final String EMAIL_NOTIFIER_NAME = "TEST_EMAIL_NOTIFIER";
    private static final String PAGER_DUTY_NOTIFICATION_NAME = "TEST PAGER DUTY NOTIFICATION";
    private static final String PAGER_DUTY_NOTIFIER_NAME = "TEST_PAGER_DUTY_NOTIFIER";
    private static final String IMAGE_ID = "img1";
    private static final byte[] IMAGE_BYTE_ARRAY =  "TestString".getBytes();
    private static final String IMAGE_URL = "https://argus-ws.data.sfdc.net/argusws/images/id/img1";
    private static final String TRACKING_ID = "1_" + TRIGGER_FIRED_TIME_SECONDS;

    private static final List<String> subscriptionList = Arrays.asList("test-subscription");
    private static final Set<String> subscriptionSet = new HashSet<>(subscriptionList);

    @Mock
    private MailService mailServiceMock;

    @Mock
    private AnnotationService annotationServiceMock;

    @Mock
    private AuditService auditServiceMock;

    @Mock
    private MetricService metricServiceMock;

    @Mock
    private PrincipalUser principalUserMock;

    private SystemConfiguration systemConfiguration;
    private EmailNotifier emailNotifier;
    private NotificationContext notificationContext;

    @Before
    public void setup() {
        Properties properties = new Properties();
        systemConfiguration = new SystemConfiguration(properties);
        emailNotifier = new EmailNotifier(metricServiceMock, annotationServiceMock, auditServiceMock, mailServiceMock,
                systemConfiguration, null);
        Alert alert = generateAlert(ALERT_NAME, principalUserMock, "-1h:"+METRIC_NAME);
        alert.setEnabled(true);

        Trigger trigger = getTrigger(alert, TRIGGER_TYPE, TRIGGER_NAME, TRIGGER_THRESHOLD, TRIGGER_INERTIA_MILLIS);
        List<Trigger> triggerList = ImmutableList.of(trigger);
        alert.setTriggers(triggerList);

        Notification notification = getNotification(EMAIL_NOTIFICATION_NAME, EMAIL_NOTIFIER_NAME, alert, subscriptionList);
        alert.addNotification(notification);

        Metric metric = getMetric();
        History history = getHistory();

        notificationContext = new NotificationContext(alert, trigger, notification,
                TRIGGER_FIRED_TIME_SECONDS*1000, TRIGGER_EVENT_VALUE, metric, history,
                Pair.of(IMAGE_ID, IMAGE_BYTE_ARRAY), IMAGE_URL, TRACKING_ID);
    }

    @Test
    public void testGetNameReturnsEmailNotifierClassName() {
        assertEquals(emailNotifier.getName(), "com.salesforce.dva.argus.service.alert.notifier.EmailNotifier");
    }

    @Test
    public void testEmailNotificationWhenTheStatusIsTriggered() {
        String expectedNotificationSubject = getNotificationSubjectSingleNotification();
        String expectedNotificationBody = getEmailBodyForSingleNotificationOnTriggered();

        ArgumentMatcher<EmailContext> emailContext = new ArgumentMatcher<EmailContext>() {
            @Override
            public boolean matches(EmailContext emailContext) {
                boolean isEmailBodyEqual = emailContext.getEmailBody().equals(expectedNotificationBody);
                boolean isEmailSubjectEqual = emailContext.getSubject().equals(expectedNotificationSubject);
                return isEmailBodyEqual && isEmailSubjectEqual;
            }
        };
        when(mailServiceMock.sendMessage(argThat(emailContext))).thenReturn(true);

        boolean isEmailSent = emailNotifier.sendAdditionalNotification(notificationContext);

        verify(mailServiceMock, times(1)).sendMessage(argThat(emailContext));

        assertTrue(isEmailSent);
    }

    @Test
    public void testEmailNotificationWhenTheStatusIsTriggeredAndImageDetainsAreNull() {
        Alert alert = generateAlert(ALERT_NAME, principalUserMock, "-1h:"+METRIC_NAME);
        alert.setEnabled(true);

        Trigger trigger = getTrigger(alert, TRIGGER_TYPE, TRIGGER_NAME, TRIGGER_THRESHOLD, TRIGGER_INERTIA_MILLIS);
        List<Trigger> triggerList = ImmutableList.of(trigger);
        alert.setTriggers(triggerList);

        Notification notification = getNotification(EMAIL_NOTIFICATION_NAME, EMAIL_NOTIFIER_NAME, alert, subscriptionList);
        alert.addNotification(notification);

        Metric metric = getMetric();
        History history = getHistory();

        notificationContext = new NotificationContext(alert, trigger, notification,
                TRIGGER_FIRED_TIME_SECONDS*1000, TRIGGER_EVENT_VALUE, metric, history);

        String expectedNotificationSubject = getNotificationSubjectSingleNotification();
        String expectedNotificationBody = getEmailBodyForSingleNotificationOnTriggeredWhenImageDetailsAreNotPresent();

        ArgumentMatcher<EmailContext> emailContext = new ArgumentMatcher<EmailContext>() {
            @Override
            public boolean matches(EmailContext emailContext) {
                boolean isEmailBodyEqual = emailContext.getEmailBody().equals(expectedNotificationBody);
                boolean isEmailSubjectEqual = emailContext.getSubject().equals(expectedNotificationSubject);
                return isEmailBodyEqual && isEmailSubjectEqual;
            }
        };
        when(mailServiceMock.sendMessage(argThat(emailContext))).thenReturn(true);

        boolean isEmailSent = emailNotifier.sendAdditionalNotification(notificationContext);

        verify(mailServiceMock, times(1)).sendMessage(argThat(emailContext));

        assertTrue(isEmailSent);
    }

    @Test
    public void testEmailNotificationWhenTheStatusIsCleared() {
        String expectedNotificationSubject = getNotificationSubjectSingleNotification();
        String expectedNotificationBody = getEmailBodyForSingleNotificationOnCleared();

        ArgumentMatcher<EmailContext> emailContext = new ArgumentMatcher<EmailContext>() {
            @Override
            public boolean matches(EmailContext emailContext) {
                boolean isEmailBodyEqual = emailContext.getEmailBody().equals(expectedNotificationBody);
                boolean isEmailSubjectEqual = emailContext.getSubject().equals(expectedNotificationSubject);
                return isEmailBodyEqual && isEmailSubjectEqual;
            }
        };
        when(mailServiceMock.sendMessage(argThat(emailContext))).thenReturn(true);
        boolean isEmailSent = emailNotifier.clearAdditionalNotification(notificationContext);

        verify(mailServiceMock, times(1)).sendMessage(argThat(emailContext));

        assertTrue(isEmailSent);
    }

    @Test
    public void testEmailNotificationWhenTheNotificationIsNotSentOnClearedNotification() {
        String expectedNotificationSubject = getNotificationSubjectSingleNotification();
        String expectedNotificationBody = getEmailBodyForSingleNotificationOnCleared();

        ArgumentMatcher<EmailContext> emailContext = new ArgumentMatcher<EmailContext>() {
            @Override
            public boolean matches(EmailContext emailContext) {
                boolean isEmailBodyEqual = emailContext.getEmailBody().equals(expectedNotificationBody);
                boolean isEmailSubjectEqual = emailContext.getSubject().equals(expectedNotificationSubject);
                return isEmailBodyEqual && isEmailSubjectEqual;
            }
        };
        when(mailServiceMock.sendMessage(argThat(emailContext))).thenReturn(false);
        boolean isEmailSent = emailNotifier.clearAdditionalNotification(notificationContext);

        verify(mailServiceMock, times(1)).sendMessage(argThat(emailContext));

        assertTrue(notificationContext.getHistory().getMessage().contains(getAuditMessageWhenEmailNotSent(CLEARED)));
        assertFalse(isEmailSent);
    }

    @Test
    public void testEmailNotificationWhenTheNotificationIsNotSentOnTriggeredNotification() {
        String expectedNotificationSubject = getNotificationSubjectSingleNotification();
        String expectedNotificationBody = getEmailBodyForSingleNotificationOnTriggered();

        ArgumentMatcher<EmailContext> emailContext = new ArgumentMatcher<EmailContext>() {
            @Override
            public boolean matches(EmailContext emailContext) {
                boolean isEmailBodyEqual = emailContext.getEmailBody().equals(expectedNotificationBody);
                boolean isEmailSubjectEqual = emailContext.getSubject().equals(expectedNotificationSubject);
                return isEmailBodyEqual && isEmailSubjectEqual;
            }
        };
        when(mailServiceMock.sendMessage(argThat(emailContext))).thenReturn(false);

        boolean isEmailSent = emailNotifier.sendAdditionalNotification(notificationContext);

        verify(mailServiceMock, times(1)).sendMessage(argThat(emailContext));

        assertTrue(notificationContext.getHistory().getMessage().contains(getAuditMessageWhenEmailNotSent(TRIGGERED)));
        assertFalse(isEmailSent);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmailNotifierWithMailServiceAsNull() {
        Properties properties = new Properties();
        systemConfiguration = new SystemConfiguration(properties);
        emailNotifier = new EmailNotifier(metricServiceMock, annotationServiceMock, auditServiceMock, null,
                systemConfiguration, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testEmailNotifierWithConfigSetAsNull() {
        Properties properties = new Properties();
        systemConfiguration = new SystemConfiguration(properties);
        emailNotifier = new EmailNotifier(metricServiceMock, annotationServiceMock, auditServiceMock, mailServiceMock,
                null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSendAdditionalNotificationWhenNotificationContextIsNull() {
        boolean isSent = emailNotifier.sendAdditionalNotification(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testClearAdditionalNotificationWhenNotificationContextIsNull() {
        boolean isSent = emailNotifier.sendAdditionalNotification(null);
    }

    @Test
    public void testSendingNotificationsWhenThereAreMultipleNotificationsForAnAlert() {
        Alert alert = generateAlert(ALERT_NAME, principalUserMock, "-1h:" + METRIC_NAME);;
        alert.setEnabled(true);

        Trigger trigger = getTrigger(alert, TRIGGER_TYPE, TRIGGER_NAME, TRIGGER_THRESHOLD, TRIGGER_INERTIA_MILLIS);
        List<Trigger> triggerList = ImmutableList.of(trigger);
        alert.setTriggers(triggerList);

        Notification emailNotification = getNotification(EMAIL_NOTIFICATION_NAME, EMAIL_NOTIFIER_NAME, alert, subscriptionList);
        alert.addNotification(emailNotification);

        Notification pagerDutyNotification = getNotification(PAGER_DUTY_NOTIFICATION_NAME, PAGER_DUTY_NOTIFIER_NAME, alert, subscriptionList);
        alert.addNotification(pagerDutyNotification);

        Metric metric = getMetric();
        History history = getHistory();

        notificationContext = new NotificationContext(alert, trigger, emailNotification,
                TRIGGER_FIRED_TIME_SECONDS*1000, TRIGGER_EVENT_VALUE, metric, history, Pair.of(IMAGE_ID, IMAGE_BYTE_ARRAY), IMAGE_URL, TRACKING_ID);

        String expectedNotificationSubject = getNotificationSubjectMultipleNotifications();
        String expectedNotificationBody = getEmailBodyForMultipleNotificationsOnTriggered();

        Pair<String, byte[]> imageData = Pair.of(IMAGE_ID, IMAGE_BYTE_ARRAY);
        ArgumentMatcher<EmailContext> emailContext = new ArgumentMatcher<EmailContext>() {
            @Override
            public boolean matches(EmailContext emailContext) {
                boolean isEmailBodyEqual = emailContext.getEmailBody().equals(expectedNotificationBody);
                boolean isEmailSubjectEqual = emailContext.getSubject().equals(expectedNotificationSubject);
                return isEmailBodyEqual && isEmailSubjectEqual;
            }
        };
        when(mailServiceMock.sendMessage(argThat(emailContext))).thenReturn(true);

        boolean isEmailSent = emailNotifier.sendAdditionalNotification(notificationContext);

        verify(mailServiceMock, times(1)).sendMessage(argThat(emailContext));

        assertTrue(isEmailSent);
    }

    @Test
    public void testSendingNotificationsWhenThereAreMultipleTriggersForAnAlert() {
        Alert alert = generateAlert(ALERT_NAME, principalUserMock, "-1h:" + METRIC_NAME);;
        alert.setEnabled(true);

        Trigger trigger1 = getTrigger(alert, TRIGGER_TYPE, TRIGGER_NAME, TRIGGER_THRESHOLD, TRIGGER_INERTIA_MILLIS);
        Trigger trigger2 = getTrigger(alert, TRIGGER_TYPE, TRIGGER_NAME2, TRIGGER_THRESHOLD2, TRIGGER_INERTIA_MILLIS2);
        List<Trigger> triggerList = ImmutableList.of(trigger1, trigger2);
        alert.setTriggers(triggerList);

        Notification emailNotification = getNotification(EMAIL_NOTIFICATION_NAME, EMAIL_NOTIFIER_NAME, alert, subscriptionList);
        alert.addNotification(emailNotification);

        Metric metric = getMetric();
        History history = getHistory();

        Trigger triggerThatTriggered = trigger1;
        notificationContext = new NotificationContext(alert, triggerThatTriggered, emailNotification,
                TRIGGER_FIRED_TIME_SECONDS*1000, TRIGGER_EVENT_VALUE, metric, history, Pair.of(IMAGE_ID, IMAGE_BYTE_ARRAY), IMAGE_URL, TRACKING_ID);

        String expectedNotificationSubject = getNotificationSubjectMultipleTriggers(triggerThatTriggered.getName());
        String expectedNotificationBody = getEmailBodyForMultipleTriggersOnTriggered(triggerThatTriggered.getName());

        Pair<String, byte[]> imageData = Pair.of(IMAGE_ID, IMAGE_BYTE_ARRAY);
        ArgumentMatcher<EmailContext> emailContext = new ArgumentMatcher<EmailContext>() {
            @Override
            public boolean matches(EmailContext emailContext) {
                boolean isEmailBodyEqual = emailContext.getEmailBody().equals(expectedNotificationBody);
                boolean isEmailSubjectEqual = emailContext.getSubject().equals(expectedNotificationSubject);
                return isEmailBodyEqual && isEmailSubjectEqual;
            }
        };
        when(mailServiceMock.sendMessage(argThat(emailContext))).thenReturn(true);

        boolean isEmailSent = emailNotifier.sendAdditionalNotification(notificationContext);

        verify(mailServiceMock, times(1)).sendMessage(argThat(emailContext));

        assertTrue(isEmailSent);
    }


    private String getEmailBodyForSingleNotificationOnTriggered() {
        return "<h3>Alert " + ALERT_NAME + " was " + TRIGGERED + " at " + TRIGGER_FIRED_DATE_GMT + "</h3><b>Tracking ID:</b> " + TRACKING_ID +"<br/><b>Notification is on cooldown until:  </b> 01/01/1970 00:00:00 GMT<br/><img src=\"cid:" + IMAGE_ID + "\" margin-top: 5px; margin-left: 5px; margin-bottom: 5px;'><p><a href='https://argus-ws.data.sfdc.net/argusws/images/id/" + IMAGE_ID + "'>Snapshot of the evaluated metric data.</a><br/><br/><b>Evaluated metric expression:  </b> -3600000:0:" + METRIC_NAME + "<br/><p><a href='http://localhost:8080/argus/#/viewmetrics?expression=-1h%3Aargus.jvm%3Acores.active%3Amax'>Click here for the current view of the metric data.</a><br/><br/><b>Triggered on Metric:  </b> MetricScope:argus.jvm:cores.active:max{source=unittest}<br/><b>Trigger details: </b> type=" + TRIGGER_TYPE + ", name=" + TRIGGER_NAME + ", threshold=" + TRIGGER_THRESHOLD + ", inertia="+ TRIGGER_INERTIA_MILLIS + "<br/><b>Triggering event value:  </b> " + TRIGGER_EVENT_VALUE + "<br/><p><p><a href='http://localhost:8080/argus/#/alerts/null'>Click here to view alert definition.</a><br/><p><small>Disclaimer:  This alert was evaluated using the time series data as it existed at the time of evaluation.  If the data source has inherent lag or a large aggregation window is used during data collection, it is possible for the time series data to be updated such that the alert condition is no longer met.  This may be avoided by ensuring the time" +
                " window used in alert expression is outside the range of the datasource lag.</small><p><small>You received this notification because you, or a distribution list you belong to is listed as a subscriber of the alert.</small>";
    }

    private String getEmailBodyForSingleNotificationOnTriggeredWhenImageDetailsAreNotPresent() {
        return "<h3>Alert " + ALERT_NAME + " was " + TRIGGERED + " at " + TRIGGER_FIRED_DATE_GMT + "</h3><b>Notification is on cooldown until:  </b> 01/01/1970 00:00:00 GMT<br/><p><a href='http://localhost:8080/argus/#/viewmetrics?expression=-3600000%3A0%3Aargus.jvm%3Acores.active%3Amax'>Click here to view the evaluated metric data.</a><br/><br/><b>Evaluated metric expression:  </b> -3600000:0:" + METRIC_NAME + "<br/><p><a href='http://localhost:8080/argus/#/viewmetrics?expression=-1h%3Aargus.jvm%3Acores.active%3Amax'>Click here for the current view of the metric data.</a><br/><br/><b>Triggered on Metric:  </b> MetricScope:argus.jvm:cores.active:max{source=unittest}<br/><b>Trigger details: </b> type=" + TRIGGER_TYPE + ", name=" + TRIGGER_NAME + ", threshold=" + TRIGGER_THRESHOLD + ", inertia="+ TRIGGER_INERTIA_MILLIS + "<br/><b>Triggering event value:  </b> " + TRIGGER_EVENT_VALUE + "<br/><p><p><a href='http://localhost:8080/argus/#/alerts/null'>Click here to view alert definition.</a><br/><p><small>Disclaimer:  This alert was evaluated using the time series data as it existed at the time of evaluation.  If the data source has inherent lag or a large aggregation window is used during data collection, it is possible for the time series data to be updated such that the alert condition is no longer met.  This may be avoided by ensuring the time" +
                " window used in alert expression is outside the range of the datasource lag.</small><p><small>You received this notification because you, or a distribution list you belong to is listed as a subscriber of the alert.</small>";
    }

    private String getEmailBodyForSingleNotificationOnCleared() {
        return "<h3>Alert " + ALERT_NAME + " was " + CLEARED + " at " + TRIGGER_FIRED_DATE_GMT + "</h3><b>Tracking ID:</b> " + TRACKING_ID +"<br/><img src=\"cid:" + IMAGE_ID + "\" margin-top: 5px; margin-left: 5px; margin-bottom: 5px;'><p><a href='https://argus-ws.data.sfdc.net/argusws/images/id/" + IMAGE_ID + "'>Snapshot of the evaluated metric data.</a><br/><br/><b>Evaluated metric expression:  </b> -3600000:0:" + METRIC_NAME + "<br/><p><a href='http://localhost:8080/argus/#/viewmetrics?expression=-1h%3Aargus.jvm%3Acores.active%3Amax'>Click here for the current view of the metric data.</a><br/><br/><b>" + CLEARED + " on Metric:  </b> " + METRIC_SCOPE_NAME + ":" + METRIC_NAME + "{source=unittest}<br/><b>Trigger details: </b> type="+ TRIGGER_TYPE +", name=" + TRIGGER_NAME + ", threshold=" + TRIGGER_THRESHOLD + ", inertia=" + TRIGGER_INERTIA_MILLIS + "<br/><p><p><a href='http://localhost:8080/argus/#/alerts/null'>Click here to view alert definition.</a><br/><p><small>Disclaimer:  This alert was evaluated using the time series data as it existed at the time of evaluation.  If the data source has inherent lag or a large aggregation window is used during data collection, it is possible for the time series data to be updated such that the alert condition is no longer met.  This may be avoided by ensuring the time window used in alert expression is outside the range of the datasource lag.</small><p><small>You received this notification because you, or a distribution list you belong to is listed as a subscriber of the alert.</small>";
    }

    private String getEmailBodyForMultipleNotificationsOnTriggered() {
        return  "<h3>Alert " + ALERT_NAME + " was " + TRIGGERED + " at " + TRIGGER_FIRED_DATE_GMT + "</h3><b>Tracking ID:</b> " + TRACKING_ID +"<br/><b>Notification:  </b> " + EMAIL_NOTIFICATION_NAME + "<br/><b>Notification is on cooldown until:  </b> 01/01/1970 00:00:00 GMT<br/><img src=\"cid:" + IMAGE_ID + "\" margin-top: 5px; margin-left: 5px; margin-bottom: 5px;'><p><a href='https://argus-ws.data.sfdc.net/argusws/images/id/" + IMAGE_ID + "'>Snapshot of the evaluated metric data.</a><br/><br/><b>Evaluated metric expression:  </b> -3600000:0:" + METRIC_NAME + "<br/><p><a href='http://localhost:8080/argus/#/viewmetrics?expression=-1h%3Aargus.jvm%3Acores.active%3Amax'>Click here for the current view of the metric data.</a><br/><br/><b>Triggered on Metric:  </b> MetricScope:argus.jvm:cores.active:max{source=unittest}<br/><b>Trigger details: </b> type=" + TRIGGER_TYPE + ", name=" + TRIGGER_NAME + ", threshold=" + TRIGGER_THRESHOLD + ", inertia="+ TRIGGER_INERTIA_MILLIS + "<br/><b>Triggering event value:  </b> " + TRIGGER_EVENT_VALUE + "<br/><p><p><a href='http://localhost:8080/argus/#/alerts/null'>Click here to view alert definition.</a><br/><p><small>Disclaimer:  This alert was evaluated using the time series data as it existed at the time of evaluation.  If the data source has inherent lag or a large aggregation window is used during data collection, it is possible for the time series data to be updated such that the alert condition is no longer met.  This may be avoided by ensuring the time" +
                " window used in alert expression is outside the range of the datasource lag.</small><p><small>You received this notification because you, or a distribution list you belong to is listed as a subscriber of the alert.</small>";
    }

    private String getEmailBodyForMultipleTriggersOnTriggered(String triggerName) {
        return "<h3>Alert " + ALERT_NAME + " was " + TRIGGERED + " at " + TRIGGER_FIRED_DATE_GMT + "</h3><b>Tracking ID:</b> " + TRACKING_ID +"<br/><b>Triggered by:  </b> " + triggerName + "<br/><b>Notification is on cooldown until:  </b> 01/01/1970 00:00:00 GMT<br/><img src=\"cid:" + IMAGE_ID + "\" margin-top: 5px; margin-left: 5px; margin-bottom: 5px;'><p><a href='https://argus-ws.data.sfdc.net/argusws/images/id/" + IMAGE_ID + "'>Snapshot of the evaluated metric data.</a><br/><br/><b>Evaluated metric expression:  </b> -3600000:0:" + METRIC_NAME + "<br/><p><a href='http://localhost:8080/argus/#/viewmetrics?expression=-1h%3Aargus.jvm%3Acores.active%3Amax'>Click here for the current view of the metric data.</a><br/><br/><b>" + TRIGGERED + " on Metric:  </b> " + METRIC_SCOPE_NAME + ":" + METRIC_NAME + "{source=unittest}<br/><b>Trigger details: </b> type=" + TRIGGER_TYPE + ", name=" + TRIGGER_NAME + ", threshold=" + TRIGGER_THRESHOLD + ", inertia=" + TRIGGER_INERTIA_MILLIS + "<br/><b>Triggering event value:  </b> " + TRIGGER_EVENT_VALUE + "<br/><p><p><a href='http://localhost:8080/argus/#/alerts/null'>Click here to view alert definition.</a><br/><p><small>Disclaimer:  This alert was evaluated using the time series data as it existed at the time of evaluation.  If the data source has inherent lag or a large aggregation window is used during data collection, it is possible for the time series data to be updated such that the alert condition is no longer met.  This may be avoided by ensuring the time window used in alert expression is outside the range of the datasource lag.</small><p><small>You received this notification because you, or a distribution list you belong to is listed as a subscriber of the alert.</small>";
    }

    private String getAuditMessageWhenEmailNotSent(String notificationStatus) {
        return " Not able to send email for " + notificationStatus.toLowerCase() + " notification: `" + EMAIL_NOTIFICATION_NAME +
                ".` to recipient " + subscriptionList;
    }

    private String getNotificationSubjectSingleNotification() {
        return "[Argus] Notification for Alert: " + ALERT_NAME ;
    }

    private String getNotificationSubjectMultipleNotifications() {
        return getNotificationSubjectSingleNotification() + " Notification: " + EMAIL_NOTIFICATION_NAME;
    }

    private String getNotificationSubjectMultipleTriggers(String triggerName) {
        return getNotificationSubjectSingleNotification() + " Trigger:" + triggerName;
    }

    private Trigger getTrigger(Alert alert, Trigger.TriggerType triggerType, String triggerName, String triggerThreshold, String triggerInertiaMillis) {
        return new Trigger(alert, triggerType, triggerName, Double.parseDouble(triggerThreshold), Long.parseLong(triggerInertiaMillis));
    }

    private static Metric getMetric() {
        SecureRandom random = new SecureRandom();
        return createMetric(((int) (random.nextDouble() * 500)) + 1);
    }

    private static Metric createMetric(int datapointCount) {
        Metric result = new Metric(METRIC_SCOPE_NAME, METRIC_NAME);
        Map<Long, Double> datapoints = new TreeMap<>();

        for (int i = 0; i < datapointCount; i++) {
            datapoints.put(System.currentTimeMillis(), random.nextDouble() * 500);
        }

        Map<String, String> tags = new HashMap<>();
        tags.put("source", "unittest");
        result.setDatapoints(datapoints);
        result.setTags(tags);
        return result;
    }

    private static String getDateFormat(long timeSeconds) {
        SimpleDateFormat sdf = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss z");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));
        return sdf.format(timeSeconds*1000);
    }
}

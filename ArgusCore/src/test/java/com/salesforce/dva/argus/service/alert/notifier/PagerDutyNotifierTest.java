package com.salesforce.dva.argus.service.alert.notifier;

import com.google.common.collect.ImmutableList;
import com.google.inject.Provider;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.Audit;
import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.service.AnnotationService;
import com.salesforce.dva.argus.service.ArgusTransport;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.NotificationContext;
import com.salesforce.dva.argus.system.SystemConfiguration;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.persistence.EntityManager;
import java.io.InterruptedIOException;
import java.math.BigInteger;
import java.util.Properties;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

@RunWith(PowerMockRunner.class)
@PrepareForTest({PagerDutyNotifier.class, ArgusTransport.class, EntityUtils.class})
public class PagerDutyNotifierTest {
    /* Constants */
    private static final String SYSTEM_CONFIG_PAGERDUTY_ENABLED = "system.property.pagerduty.enabled";
    private static final String PAGERDUTY_NOTIFIER_ENDPOINT = "notifier.property.pagerduty.endpoint";
    private static final String PAGERDUTY_NOTIFIER_TOKEN = "notifier.property.pagerduty.token";
    private static final String PAGERDUTY_NOTIFIER_PROXY_HOST = "notifier.property.pagerduty.proxy.host";
    private static final String PAGERDUTY_NOTIFIER_PROXY_PORT = "notifier.property.pagerduty.proxy.port";
    private static final String PAGERDUTY_NOTIFIER_MAX_POST_ATTEMPTS = "notifier.property.pagerduty.maxPostAttempts";
    private static final String PAGERDUTY_NOTIFIER_CONNECTION_POOL_MAX_SIZE = "notifier.property.pagerduty.connectionpool.maxsize";
    private static final String PAGERDUTY_NOTIFIER_CONNECTION_POOL_MAX_PER_ROUTE = "notifier.property.pagerduty.connectionpool.maxperroute";
    private static final int MAX_POST_ATTEMPTS = 3;

    /* Test mocks */
    private MetricService metricService;
    private AnnotationService annotationService;
    private AuditService auditService;
    private Provider<EntityManager> emf;
    private MonitorService monitorService;
    private CloseableHttpClient httpClient;
    private Audit auditResult;
    private ArgusTransport argusTransport;
    private CloseableHttpResponse httpResponse;
    private StatusLine httpResponseStatusLine;
    private HttpEntity httpResponseEntity;

    /* Class being tested */
    private PagerDutyNotifier notifier;

    /* Test data */
    private SystemConfiguration config;
    private Properties properties;
    private NotificationContext context;
    private Alert alert;
    private Trigger trigger;
    private Notification notification;
    private Metric metric;
    private History history;

    @Before
    public void setup() throws Exception {
        // create mocks
        metricService = mock(MetricService.class);
        annotationService = mock(AnnotationService.class);
        auditService = mock(AuditService.class);
        emf = mock(Provider.class);
        monitorService = mock(MonitorService.class);
        httpClient = mock(CloseableHttpClient.class);
        auditResult = mock(Audit.class);
        argusTransport = mock(ArgusTransport.class);
        httpResponse = mock(CloseableHttpResponse.class);
        httpResponseStatusLine = mock(StatusLine.class);
        httpResponseEntity = mock(HttpEntity.class);

        mockStatic(EntityUtils.class);

        // set up test SystemConfiguration properties
        properties = new Properties();
        properties.setProperty(SYSTEM_CONFIG_PAGERDUTY_ENABLED, "true");
        properties.setProperty(PAGERDUTY_NOTIFIER_ENDPOINT, "https://test_pd_ep.com");
        properties.setProperty(PAGERDUTY_NOTIFIER_TOKEN, "test_token");
        properties.setProperty(PAGERDUTY_NOTIFIER_MAX_POST_ATTEMPTS, Integer.toString(MAX_POST_ATTEMPTS));
        properties.setProperty(PAGERDUTY_NOTIFIER_CONNECTION_POOL_MAX_SIZE, "10");
        properties.setProperty(PAGERDUTY_NOTIFIER_CONNECTION_POOL_MAX_PER_ROUTE, "5");
        properties.setProperty(PAGERDUTY_NOTIFIER_PROXY_HOST, "test_proxy_host");
        properties.setProperty(PAGERDUTY_NOTIFIER_PROXY_PORT, "9090");
        config = new SystemConfiguration(properties);

        // set up test data
        alert = new Alert(new PrincipalUser(null, "test_creator", "test_creator@salesforce.com"),
                new PrincipalUser(null, "test_owner", "test_owner@salesforce.com"),
                "test_alert_name",
                "-1h:test:metric:avg",
                "test_alert_cron_entry");
        trigger = new Trigger(alert, Trigger.TriggerType.EQUAL, "test_trigger_name", 3.14, 1000);
        alert.setTriggers(ImmutableList.<Trigger>of(trigger));
        notification = new Notification("test_notification_name",
                alert,
                "test_notifier_name",
                ImmutableList.<String>of("test_subscription"),
                3000);
        FieldUtils.writeField(notification, "id", BigInteger.valueOf(12345L), true);
        alert.addNotification(notification);
        metric = new Metric("test_scope", "test_metric_name");
        history = new History("test_message", "test_host_name", BigInteger.valueOf(456), History.JobStatus.STARTED);
        context = new NotificationContext(alert, trigger, notification, System.currentTimeMillis(), 3.14, metric, history);
    }

    @Test
    public void sendAdditionalNotification_testPostPagerDutyNotificationResponseCode202() throws Exception {
        sendOrClearAdditionalNotification_mockBehaviorTemplate();
        when(httpResponseStatusLine.getStatusCode()).thenReturn(202);

        notifier = new PagerDutyNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        boolean result = notifier.sendAdditionalNotification(context);
        assertTrue(result);

        sendOrClearAdditionalNotification_verifyMocksTemplate(1, 0, 0, result);
    }

    @Test
    public void sendAdditionalNotification_testPostPagerDutyNotificationResponseCode400() throws Exception {
        sendOrClearAdditionalNotification_mockBehaviorTemplate();
        when(httpResponseStatusLine.getStatusCode()).thenReturn(400);

        notifier = new PagerDutyNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        boolean result = notifier.sendAdditionalNotification(context);
        assertFalse(result);

        sendOrClearAdditionalNotification_verifyMocksTemplate(1, 0, 1, result);
    }

    @Test
    public void sendAdditionalNotification_testPostPagerDutyNotificationResponseCode429RetryMaxTimesAndFail() throws Exception {
        sendAdditionalNotification_testPostPagerDutyNotificationResponseCodeXXXRetryMaxTimesAndFail(429);
    }

    @Test
    public void sendAdditionalNotification_testPostPagerDutyNotificationResponseCode429RetryAndPass() throws Exception {
        sendOrClearAdditionalNotification_mockBehaviorTemplate();
        when(httpResponseStatusLine.getStatusCode()).thenReturn(429).thenReturn(202);

        notifier = new PagerDutyNotifier(metricService, annotationService, auditService, config, emf, monitorService,
                properties.getProperty(PAGERDUTY_NOTIFIER_ENDPOINT),
                properties.getProperty(PAGERDUTY_NOTIFIER_TOKEN),
                1L);

        boolean result = notifier.sendAdditionalNotification(context);
        assertTrue(result);

        sendOrClearAdditionalNotification_verifyMocksTemplate(2, 0, 0, result);
    }

    @Test
    public void sendAdditionalNotification_testPostPagerDutyNotificationResponseCode4XX() throws Exception {
        sendOrClearAdditionalNotification_mockBehaviorTemplate();
        when(httpResponseStatusLine.getStatusCode()).thenReturn(402);

        notifier = new PagerDutyNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        boolean result = notifier.sendAdditionalNotification(context);
        assertFalse(result);

        sendOrClearAdditionalNotification_verifyMocksTemplate(1, 0, 1, result);
    }

    @Test
    public void sendAdditionalNotification_testPostPagerDutyNotificationResponseCode500RetryMaxTimesAndFail() throws Exception {
        sendAdditionalNotification_testPostPagerDutyNotificationResponseCodeXXXRetryMaxTimesAndFail(500);
    }

    @Test
    public void sendAdditionalNotification_testPostPagerDutyNotificationResponseCode500RetryAndPass() throws Exception {
        sendOrClearAdditionalNotification_mockBehaviorTemplate();
        when(httpResponseStatusLine.getStatusCode()).thenReturn(500).thenReturn(202);

        notifier = new PagerDutyNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        boolean result = notifier.sendAdditionalNotification(context);
        assertTrue(result);

        sendOrClearAdditionalNotification_verifyMocksTemplate(2, 0, 0, result);
    }

    @Test
    public void sendAdditionalNotification_testPostPagerDutyNotificationResponseCode502RetryMaxTimesAndFail() throws Exception {
        sendAdditionalNotification_testPostPagerDutyNotificationResponseCodeXXXRetryMaxTimesAndFail(502);
    }

    @Test
    public void sendAdditionalNotification_testPostPagerDutyNotificationThrowsInterruptedIOException() throws Exception {
        sendOrClearAdditionalNotification_mockBehaviorTemplate();
        when(httpClient.execute(any())).thenThrow(new InterruptedIOException("TEST"));

        notifier = new PagerDutyNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        boolean result = notifier.sendAdditionalNotification(context);
        assertFalse(result);

        sendOrClearAdditionalNotification_verifyMocksTemplate(MAX_POST_ATTEMPTS, MAX_POST_ATTEMPTS, 0, result);
    }

    @Test
    public void sendAdditionalNotification_testPostPagerDutyNotificationThrowsException() throws Exception {
        sendOrClearAdditionalNotification_mockBehaviorTemplate();
        when(httpClient.execute(any())).thenThrow(new RuntimeException("TEST"));

        notifier = new PagerDutyNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        boolean result = notifier.sendAdditionalNotification(context);
        assertFalse(result);

        sendOrClearAdditionalNotification_verifyMocksTemplate(1, 1, 0, result);
    }

    @Test
    public void clearAdditionalNotification_testPostPagerDutyNotificationResponseCode202() throws Exception {
        sendOrClearAdditionalNotification_mockBehaviorTemplate();
        when(httpResponseStatusLine.getStatusCode()).thenReturn(202);

        notifier = new PagerDutyNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        boolean result = notifier.clearAdditionalNotification(context);
        assertTrue(result);

        sendOrClearAdditionalNotification_verifyMocksTemplate(1, 0, 0, result);
    }

    private void sendAdditionalNotification_testPostPagerDutyNotificationResponseCodeXXXRetryMaxTimesAndFail(int statusCode) throws Exception {
        sendOrClearAdditionalNotification_mockBehaviorTemplate();
        when(httpResponseStatusLine.getStatusCode()).thenReturn(statusCode);

        notifier = new PagerDutyNotifier(metricService, annotationService, auditService, config, emf, monitorService,
                properties.getProperty(PAGERDUTY_NOTIFIER_ENDPOINT),
                properties.getProperty(PAGERDUTY_NOTIFIER_TOKEN),
                1L);

        boolean result = notifier.sendAdditionalNotification(context);
        assertFalse(result);

        sendOrClearAdditionalNotification_verifyMocksTemplate(MAX_POST_ATTEMPTS, 0, 0, result);
    }

    private void sendOrClearAdditionalNotification_mockBehaviorTemplate() throws Exception {
        whenNew(ArgusTransport.class).withAnyArguments().thenReturn(argusTransport);
        when(argusTransport.getHttpClient()).thenReturn(httpClient);
        when(httpClient.execute(any())).thenReturn(httpResponse);
        when(httpResponse.getStatusLine()).thenReturn(httpResponseStatusLine);
        when(EntityUtils.toString(any())).thenReturn("default");
    }

    private void sendOrClearAdditionalNotification_verifyMocksTemplate(int tries, int exceptionsThrown, int getResponseBodyAsStringTimes, boolean success) throws Exception {
        verify(argusTransport, times(1)).getHttpClient();
        verify(httpClient, times(tries)).execute(any());
        verify(httpResponse, times(tries - exceptionsThrown)).getStatusLine();
        verify(httpResponseStatusLine, times(tries - exceptionsThrown)).getStatusCode();
        verify(httpResponse, times(tries - exceptionsThrown)).close();

        verifyStatic(EntityUtils.class, times(getResponseBodyAsStringTimes));
        EntityUtils.toString(any());

        verify(monitorService).modifyCounter(MonitorService.Counter.PAGERDUTY_NOTIFICATIONS_RETRIES, tries - 1, null);
        verify(monitorService).modifyCounter(MonitorService.Counter.PAGERDUTY_NOTIFICATIONS_FAILED, success ? 0 : 1, null);
    }

}
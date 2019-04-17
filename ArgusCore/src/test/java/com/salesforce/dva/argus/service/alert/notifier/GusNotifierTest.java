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
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.NotificationContext;
import com.salesforce.dva.argus.system.SystemConfiguration;
import org.apache.http.HttpEntity;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import java.math.BigInteger;
import java.util.Properties;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

@RunWith(PowerMockRunner.class)
@PrepareForTest({GusNotifier.class, GusTransport.class, EntityUtils.class})
public class GusNotifierTest {
    /* Constants */
    private static final String SYSTEM_CONFIG_GUS_ENABLED = "system.property.gus.enabled";
    private static final String GUS_NOTIFIER_GUS_USER = "notifier.property.alert.gus_user";
    private static final String GUS_NOTIFIER_GUS_PWD = "notifier.property.alert.gus_pwd";
    private static final String GUS_NOTIFIER_GUS_CLIENT_ID = "notifier.property.alert.gus_client_id";
    private static final String GUS_NOTIFIER_GUS_CLIENT_SECRET = "notifier.property.alert.gus_client_secret";
    private static final String GUS_NOTIFIER_GUS_ENDPOINT = "notifier.property.alert.gus_endpoint";
    private static final String GUS_NOTIFIER_GUS_POST_ENDPOINT = "notifier.property.alert.gus_post_endpoint";
    private static final String GUS_NOTIFIER_PROXY_HOST = "notifier.property.proxy.host";
    private static final String GUS_NOTIFIER_PROXY_PORT = "notifier.property.proxy.port";

    private static final String TEST_INSTANCE_URL = "https://test_instance_url.com";
    private static final String TEST_TOKEN = "test_token";

    private static final int MAX_ATTEMPTS_GUS_POST = 3;

    /* Test mocks */
    private MetricService metricService;
    private AnnotationService annotationService;
    private AuditService auditService;
    private Provider<EntityManager> emf;
    private MonitorService monitorService;
    private CloseableHttpClient httpClient;
    private Audit auditResult;
    private GusTransport gusTransport;
    private CloseableHttpResponse httpResponse;
    private StatusLine httpResponseStatusLine;
    private HttpEntity httpResponseEntity;

    /* Class being tested */
    private GusNotifier notifier;

    /* Test data */
    private SystemConfiguration config;
    private Properties properties;
    private NotificationContext context;
    private Alert alert;
    private Trigger trigger;
    private Notification notification;
    private Metric metric;
    private History history;

    private static ch.qos.logback.classic.Logger apacheLogger;
    private static ch.qos.logback.classic.Logger myClassLogger;

    @BeforeClass
    static public void setUpClass() {
        myClassLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("com.salesforce.dva.argus.service.alert.GusNotifierTest");
        myClassLogger.setLevel(ch.qos.logback.classic.Level.OFF);
        apacheLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.apache");
        apacheLogger.setLevel(ch.qos.logback.classic.Level.OFF);
    }

    @Before
    public void setup() {
        // create mocks
        metricService = mock(MetricService.class);
        annotationService = mock(AnnotationService.class);
        auditService = mock(AuditService.class);
        emf = mock(Provider.class);
        monitorService = mock(MonitorService.class);
        httpClient = mock(CloseableHttpClient.class);
        auditResult = mock(Audit.class);
        gusTransport = mock(GusTransport.class);
        httpResponse = mock(CloseableHttpResponse.class);
        httpResponseStatusLine = mock(StatusLine.class);
        httpResponseEntity = mock(HttpEntity.class);

        mockStatic(EntityUtils.class);

        // set up test SystemConfiguration properties
        properties = new Properties();
        properties.setProperty(SYSTEM_CONFIG_GUS_ENABLED, "true");
        properties.setProperty(GUS_NOTIFIER_GUS_USER, "test_gus_user");
        properties.setProperty(GUS_NOTIFIER_GUS_PWD, "test_gus_pw");
        properties.setProperty(GUS_NOTIFIER_GUS_CLIENT_ID, "test_gus_client_id");
        properties.setProperty(GUS_NOTIFIER_GUS_CLIENT_SECRET, "test_gus_client_secret");
        properties.setProperty(GUS_NOTIFIER_GUS_ENDPOINT, "https://test_gus_ep.com");
        properties.setProperty(GUS_NOTIFIER_GUS_POST_ENDPOINT, "https://test_gus_post_ep.com");
        properties.setProperty(GUS_NOTIFIER_PROXY_HOST, "test_proxy_host");
        properties.setProperty(GUS_NOTIFIER_PROXY_PORT, "9090");
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
        alert.addNotification(notification);
        metric = new Metric("test_scope", "test_metric_name");
        history = new History("test_message", "test_host_name", BigInteger.valueOf(456), History.JobStatus.STARTED);
        context = new NotificationContext(alert, trigger, notification, System.currentTimeMillis(), 3.14, metric, history);
    }

    @Test
    public void sendAdditionalNotification_testPostGusNotificationRespCode201() throws Exception {
        boolean result = sendAdditionalNotification_testHappyCaseTemplate(201);
        assertTrue(result);
    }

    @Test
    public void sendAdditionalNotification_testPostGusNotificationRespCode204() throws Exception {
        boolean result = sendAdditionalNotification_testHappyCaseTemplate(204);
        assertTrue(result);
    }

    @Test
    public void sendAdditionalNotification_testGusPostFailThenRetryAndPass() throws Exception {
        int[] postNotificationResponseCode = {405, 201};
        // define mock behavior
        sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate();
        when(httpResponseStatusLine.getStatusCode()).thenReturn(postNotificationResponseCode[0])
                .thenReturn(postNotificationResponseCode[1]);
        when(EntityUtils.toString(any())).thenReturn("[{\"message\": \"BLAH\", \"errorCode\": \"BLAH2\"}]");

        // create object under test
        notifier = new GusNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.sendAdditionalNotification(context);
        assertTrue(result);

        // verify mocks
        sendOrClearAdditionalNotification_verifyGusPostMethodMockTemplate(2, 0, 1, true);
    }

    @Test
    public void sendAdditionalNotification_testGusPostFailRetryMaxTimes() throws Exception {
        int postNotificationResponseCode = 404;
        // define mock behavior
        sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate();
        when(httpResponseStatusLine.getStatusCode()).thenReturn(postNotificationResponseCode);
        when(EntityUtils.toString(any())).thenReturn("[{\"message\": \"BLAH\", \"errorCode\": \"BLAH2\"}]");

        // create object under test
        notifier = new GusNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.sendAdditionalNotification(context);
        assertFalse(result);

        // verify mocks
        sendOrClearAdditionalNotification_verifyGusPostMethodMockTemplate(MAX_ATTEMPTS_GUS_POST, 0, MAX_ATTEMPTS_GUS_POST, false);
    }

    @Test
    public void sendAdditionalNotification_testAuthHeaderFailThenRetryAndPass() throws Exception {
        int[] postNotificationResponseCode = {404, 201};
        // define mock behavior
        sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate();
        when(httpResponseStatusLine.getStatusCode()).thenReturn(postNotificationResponseCode[0])
                .thenReturn(postNotificationResponseCode[1]);
        when(EntityUtils.toString(any())).thenReturn("[{\"message\":\"INVALID_HEADER_TYPE\",\"errorCode\":\"INVALID_AUTH_HEADER\"}]");

        // create object under test
        notifier = new GusNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.sendAdditionalNotification(context);
        assertTrue(result);

        // verify mocks
        sendOrClearAdditionalNotification_verifyGusPostMethodMockTemplate(2, 1, 1, true);
    }

    @Test
    public void sendAdditionalNotification_testAuthHeaderFailRetryMaxTimes() throws Exception {
        int postNotificationResponseCode = 404;
        // define mock behavior
        sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate();
        when(httpResponseStatusLine.getStatusCode()).thenReturn(postNotificationResponseCode);
        when(EntityUtils.toString(any())).thenReturn("[{\"message\":\"INVALID_HEADER_TYPE\",\"errorCode\":\"INVALID_AUTH_HEADER\"}]");

        // create object under test
        notifier = new GusNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.sendAdditionalNotification(context);
        assertFalse(result);

        // verify mocks
        sendOrClearAdditionalNotification_verifyGusPostMethodMockTemplate(MAX_ATTEMPTS_GUS_POST, 2, MAX_ATTEMPTS_GUS_POST, false);
    }

    @Test
    public void clearAdditionalNotification_testPostGusNotificationRespCode201() throws Exception {
        boolean result = clearAdditionalNotification_testHappyCaseTemplate(201);
        assertTrue(result);
    }

    @Test
    public void clearAdditionalNotification_testPostGusNotificationRespCode204() throws Exception {
        boolean result = clearAdditionalNotification_testHappyCaseTemplate(204);
        assertTrue(result);
    }

    private boolean sendAdditionalNotification_testHappyCaseTemplate(int postNotificationResponseCode) throws Exception {
        // define mock behavior
        sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate();
        when(httpResponseStatusLine.getStatusCode()).thenReturn(postNotificationResponseCode);

        // create object under test
        notifier = new GusNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.sendAdditionalNotification(context);

        // verify mocks
        sendOrClearAdditionalNotification_verifyMocksHappyCaseTemplate();

        return result;
    }

    private boolean clearAdditionalNotification_testHappyCaseTemplate(int postNotificationResponseCode) throws Exception {
        // define mock behavior
        sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate();
        when(httpResponseStatusLine.getStatusCode()).thenReturn(postNotificationResponseCode);

        // create object under test
        notifier = new GusNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.clearAdditionalNotification(context);

        // verify mocks
        sendOrClearAdditionalNotification_verifyMocksHappyCaseTemplate();

        return result;
    }

    private void sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate() throws Exception {
        // define mock behavior
        when(auditService.createAudit(any())).thenReturn(auditResult);
        whenNew(GusTransport.class).withAnyArguments().thenReturn(gusTransport);
        when(gusTransport.getEndpointInfo(anyBoolean())).thenReturn(new GusTransport.EndpointInfo(TEST_INSTANCE_URL, TEST_TOKEN));
        when(gusTransport.getHttpClient()).thenReturn(httpClient);
        when(httpClient.execute(any())).thenReturn(httpResponse);
        when(httpResponse.getStatusLine()).thenReturn(httpResponseStatusLine);
    }

    private void sendOrClearAdditionalNotification_verifyMocksHappyCaseTemplate() throws Exception {
        // verify mocks
        sendOrClearAdditionalNotification_verifyGusPostMethodMockTemplate(1, 0, 0, true);
    }

    private void sendOrClearAdditionalNotification_verifyGusPostMethodMockTemplate(int tries, int refreshCacheTries, int getResponseBodyAsStringTimes, boolean success) throws Exception {
        verify(gusTransport, times(tries - refreshCacheTries)).getEndpointInfo(false);
        verify(gusTransport, times(refreshCacheTries)).getEndpointInfo(true);
        verify(httpClient, times(tries)).execute(any());
        verify(httpResponse, times(tries)).getStatusLine();
        verify(httpResponseStatusLine, times(tries)).getStatusCode();
        verify(httpResponse, times(tries)).close();

        verifyStatic(EntityUtils.class, times(getResponseBodyAsStringTimes));
        EntityUtils.toString(any());

        verify(monitorService).modifyCounter(MonitorService.Counter.GUS_NOTIFICATIONS_RETRIES, tries - 1, null);
        verify(monitorService).modifyCounter(MonitorService.Counter.GUS_NOTIFICATIONS_FAILED, success ? 0 : 1, null);
    }

}

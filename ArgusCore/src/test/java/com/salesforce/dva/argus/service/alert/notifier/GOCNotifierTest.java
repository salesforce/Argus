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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.persistence.EntityManager;
import java.math.BigInteger;
import java.net.SocketTimeoutException;
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
@PrepareForTest({GOCNotifier.class, GusTransport.class, EntityUtils.class})
public class GOCNotifierTest {
    /* Constants */
    private static final String SYSTEM_CONFIG_GOC_ENABLED = "system.property.goc.enabled";
    private static final String GOC_NOTIFIER_GOC_USER = "notifier.property.goc.username";
    private static final String GOC_NOTIFIER_GOC_PWD = "notifier.property.goc.password";
    private static final String GOC_NOTIFIER_GOC_CLIENT_ID = "notifier.property.goc.client.id";
    private static final String GOC_NOTIFIER_GOC_CLIENT_SECRET = "notifier.property.goc.client.secret";
    private static final String GOC_NOTIFIER_GOC_ENDPOINT = "notifier.property.goc.endpoint";
    private static final String GOC_NOTIFIER_PROXY_HOST = "notifier.property.proxy.host";
    private static final String GOC_NOTIFIER_PROXY_PORT = "notifier.property.proxy.port";

    private static final String TEST_INSTANCE_URL = "https://test_instance_url.com";
    private static final String TEST_TOKEN = "test_token";

    private static final int MAX_ATTEMPTS_GOC_POST = 3;

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
    private GOCNotifier notifier;

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
        properties.setProperty(SYSTEM_CONFIG_GOC_ENABLED, "true");
        properties.setProperty(GOC_NOTIFIER_GOC_USER, "test_goc_user");
        properties.setProperty(GOC_NOTIFIER_GOC_PWD, "test_goc_pw");
        properties.setProperty(GOC_NOTIFIER_GOC_CLIENT_ID, "test_goc_client_id");
        properties.setProperty(GOC_NOTIFIER_GOC_CLIENT_SECRET, "test_goc_client_secret");
        properties.setProperty(GOC_NOTIFIER_GOC_ENDPOINT, "https://test_goc_ep.com");
        properties.setProperty(GOC_NOTIFIER_PROXY_HOST, "test_proxy_host");
        properties.setProperty(GOC_NOTIFIER_PROXY_PORT, "9090");
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
    public void sendAdditionalNotification_testPostGOCNotificationRespCode201() throws Exception {
        boolean result = sendAdditionalNotification_testTemplate(201);
        assertTrue(result);
    }

    @Test
    public void sendAdditionalNotification_testPostGOCNotificationRespCode204() throws Exception {
        boolean result = sendAdditionalNotification_testTemplate(204);
        assertTrue(result);
    }

    @Test
    public void sendAdditionalNotification_testPostGOCNotificationRespCode401RetryAndPass() throws Exception {
        int[] postNotificationResponseCode = {401, 201};
        sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate();
        when(httpResponseStatusLine.getStatusCode()).thenReturn(postNotificationResponseCode[0])
                .thenReturn(postNotificationResponseCode[1]);

        // create object under test
        notifier = new GOCNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.sendAdditionalNotification(context);
        assertTrue(result);

        // verify mocks
        sendOrClearAdditionalNotification_verifyMocksTemplate(2, 0, 1, 1, true);
    }

    @Test
    public void sendAdditionalNotification_testPostGOCNotificationRespCode401RetryMaxTimes() throws Exception {
        int postNotificationResponseCode = 401;
        sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate();
        when(httpResponseStatusLine.getStatusCode()).thenReturn(postNotificationResponseCode);

        // create object under test
        notifier = new GOCNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.sendAdditionalNotification(context);
        assertFalse(result);

        // verify mocks
        sendOrClearAdditionalNotification_verifyMocksTemplate(MAX_ATTEMPTS_GOC_POST, 0, 2, MAX_ATTEMPTS_GOC_POST, false);
    }

    @Test
    public void sendAdditionalNotification_testPostGOCNotificationRespCode500RetryAndPass() throws Exception {
        int[] postNotificationResponseCode = {500, 201};
        sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate();
        when(httpResponseStatusLine.getStatusCode()).thenReturn(postNotificationResponseCode[0])
                .thenReturn(postNotificationResponseCode[1]);

        // create object under test
        notifier = new GOCNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.sendAdditionalNotification(context);
        assertTrue(result);

        // verify mocks
        sendOrClearAdditionalNotification_verifyMocksTemplate(2, 0, 0, 1, true);
    }

    @Test
    public void sendAdditionalNotification_testPostGOCNotificationRespCode500RetryMaxTimes() throws Exception {
        int postNotificationResponseCode = 500;
        sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate();
        when(httpResponseStatusLine.getStatusCode()).thenReturn(postNotificationResponseCode);

        // create object under test
        notifier = new GOCNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.sendAdditionalNotification(context);
        assertFalse(result);

        // verify mocks
        sendOrClearAdditionalNotification_verifyMocksTemplate(MAX_ATTEMPTS_GOC_POST, 0, 0, MAX_ATTEMPTS_GOC_POST, false);
    }

    @Test
    public void sendAdditionalNotification_testSocketTimeoutExceptionRetryAndPass() throws Exception {
        sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate();
        when(httpClient.execute(any())).thenThrow(new SocketTimeoutException())
                .thenReturn(httpResponse);
        when(httpResponseStatusLine.getStatusCode()).thenReturn(201);

        // create object under test
        notifier = new GOCNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.sendAdditionalNotification(context);
        assertTrue(result);

        // verify mocks
        sendOrClearAdditionalNotification_verifyMocksTemplate(2, 1, 0, 0, true);
    }

    @Test
    public void sendAdditionalNotification_testSocketTimeoutExceptionRetryMaxTimes() throws Exception {
        sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate();
        when(httpClient.execute(any())).thenThrow(new SocketTimeoutException());

        // create object under test
        notifier = new GOCNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.sendAdditionalNotification(context);
        assertFalse(result);

        // verify mocks
        sendOrClearAdditionalNotification_verifyMocksTemplate(MAX_ATTEMPTS_GOC_POST, 3, 0, 0, false);
    }

    @Test
    public void sendAdditionalNotification_testRespCode400AuthHeaderFailRetryMaxTimes() throws Exception {
        int postNotificationResponseCode = 400;
        sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate();
        when(httpResponseStatusLine.getStatusCode()).thenReturn(postNotificationResponseCode);
        when(EntityUtils.toString(any())).thenReturn("[{\"message\":\"INVALID_HEADER_TYPE\", \"errorCode\":\"INVALID_AUTH_HEADER\"}]");

        // create object under test
        notifier = new GOCNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.sendAdditionalNotification(context);
        assertFalse(result);

        // verify mocks
        sendOrClearAdditionalNotification_verifyMocksTemplate(MAX_ATTEMPTS_GOC_POST, 0, 2, MAX_ATTEMPTS_GOC_POST, false);
    }

    @Test
    public void sendAdditionalNotification_testRespCode400UnknownFailureNoRetries() throws Exception {
        boolean result = sendAdditionalNotification_testNoRetriesTemplate(400);
        assertFalse(result);
    }

    @Test
    public void sendAdditionalNotification_testUnknownFailureNoRetries() throws Exception {
        boolean result = sendAdditionalNotification_testNoRetriesTemplate(404);
        assertFalse(result);
    }

    @Test
    public void clearAdditionalNotification_testPostGOCNotificationRespCode201() throws Exception {
        boolean result = clearAdditionalNotification_testTemplate(201);
        assertTrue(result);
    }

    @Test
    public void clearAdditionalNotification_testPostGOCNotificationRespCode204() throws Exception {
        boolean result = clearAdditionalNotification_testTemplate(204);
        assertTrue(result);
    }

    @Test
    public void clearAdditionalNotification_testPostGOCNotificationRespCode401RetryAndPass() throws Exception {
        int[] postNotificationResponseCode = {401, 201};
        sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate();
        when(httpResponseStatusLine.getStatusCode()).thenReturn(postNotificationResponseCode[0])
                .thenReturn(postNotificationResponseCode[1]);

        // create object under test
        notifier = new GOCNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.clearAdditionalNotification(context);
        assertTrue(result);

        // verify mocks
        sendOrClearAdditionalNotification_verifyMocksTemplate(2, 0, 1, 1, true);
    }

    private boolean sendAdditionalNotification_testTemplate(int postNotificationResponseCode) throws Exception {
        // define mock behavior
        sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate();
        when(httpResponseStatusLine.getStatusCode()).thenReturn(postNotificationResponseCode);

        // create object under test
        notifier = new GOCNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.sendAdditionalNotification(context);

        // verify mocks
        sendOrClearAdditionalNotification_verifyMocksHappyCaseTemplate();

        return result;
    }

    private boolean sendAdditionalNotification_testNoRetriesTemplate(int postNotificationResponseCode) throws Exception {
        sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate();
        when(httpResponseStatusLine.getStatusCode()).thenReturn(postNotificationResponseCode);
        when(EntityUtils.toString(any())).thenReturn("bad response");

        // create object under test
        notifier = new GOCNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.sendAdditionalNotification(context);

        // verify mocks
        sendOrClearAdditionalNotification_verifyMocksTemplate(1, 0, 0, 1, false);
        return result;
    }


    private boolean clearAdditionalNotification_testTemplate(int postNotificationResponseCode) throws Exception {
        // define mock behavior
        sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate();
        when(httpResponseStatusLine.getStatusCode()).thenReturn(postNotificationResponseCode);

        // create object under test
        notifier = new GOCNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.clearAdditionalNotification(context);

        // verify mocks
        sendOrClearAdditionalNotification_verifyMocksHappyCaseTemplate();

        return result;
    }

    private void sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate() throws Exception {
        sendOrClearAdditionalNotification_mockBehaviorTemplate(new GusTransport.EndpointInfo(TEST_INSTANCE_URL, TEST_TOKEN));
    }

    private void sendOrClearAdditionalNotification_mockBehaviorTemplate(GusTransport.EndpointInfo ei) throws Exception {
        // define mock behavior
        when(auditService.createAudit(any())).thenReturn(auditResult);
        whenNew(GusTransport.class).withAnyArguments().thenReturn(gusTransport);
        when(gusTransport.getEndpointInfo(anyBoolean())).thenReturn(ei);
        when(gusTransport.getHttpClient()).thenReturn(httpClient);
        when(httpClient.execute(any())).thenReturn(httpResponse);
        when(httpResponse.getStatusLine()).thenReturn(httpResponseStatusLine);
        when(EntityUtils.toString(any())).thenReturn("default");
    }

    private void sendOrClearAdditionalNotification_verifyMocksHappyCaseTemplate() throws Exception {
        // verify mocks
        sendOrClearAdditionalNotification_verifyMocksTemplate(1, 0, 0, 0, true);
    }

    private void sendOrClearAdditionalNotification_verifyMocksTemplate(int tries, int exceptionsThrown, int refreshCacheTries, int getResponseBodyAsStringTimes, boolean success) throws Exception {
        verify(gusTransport, times(tries - refreshCacheTries)).getEndpointInfo(false);
        verify(gusTransport, times(refreshCacheTries)).getEndpointInfo(true);
        verify(httpClient, times(tries)).execute(any());
        verify(httpResponse, times(tries - exceptionsThrown)).getStatusLine();
        verify(httpResponseStatusLine, times(tries - exceptionsThrown)).getStatusCode();
        verify(httpResponse, times(tries - exceptionsThrown)).close();

        verifyStatic(EntityUtils.class, times(getResponseBodyAsStringTimes));
        EntityUtils.toString(any());

        verify(monitorService).modifyCounter(MonitorService.Counter.GOC_NOTIFICATIONS_RETRIES, tries - 1, null);
        verify(monitorService).modifyCounter(MonitorService.Counter.GOC_NOTIFICATIONS_FAILED, success ? 0 : 1, null);
    }
}

package com.salesforce.dva.argus.service.alert.notifier;

import com.salesforce.dva.argus.entity.Audit;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.alert.notifier.GOCNotifier.PatchMethod;
import com.google.common.collect.ImmutableList;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.service.AnnotationService;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.NotificationContext;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.google.inject.Provider;
import org.apache.commons.httpclient.HostConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.params.HttpClientParams;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import javax.persistence.EntityManager;
import java.math.BigInteger;
import java.util.Properties;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.verifyNew;
import static org.powermock.api.mockito.PowerMockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

@RunWith(PowerMockRunner.class)
@PrepareForTest({GOCNotifier.class, GusTransport.class})
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
    private HttpClient httpClient;
    private HttpClientParams httpClientParams;
    private PostMethod postMethod;
    private PatchMethod patchMethod;
    private Audit auditResult;

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
        httpClient = mock(HttpClient.class);
        httpClientParams = mock(HttpClientParams.class);
        postMethod = mock(PostMethod.class);
        patchMethod = mock(PatchMethod.class);
        auditResult = mock(Audit.class);

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
        when(postMethod.getResponseBodyAsString())
                .thenReturn("{\"instance_url\": \"" + TEST_INSTANCE_URL + "\", \"access_token\": \"" + TEST_TOKEN + "\"}")
                .thenReturn("{\"instance_url\": \"" + TEST_INSTANCE_URL + "2\", \"access_token\": \"" + TEST_TOKEN + "2\"}");
        when(httpClient.executeMethod(patchMethod)).thenReturn(postNotificationResponseCode[0])
                .thenReturn(postNotificationResponseCode[1]);

        // create object under test
        notifier = new GOCNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.sendAdditionalNotification(context);
        assertTrue(result);

        // verify mocks
        sendOrClearAdditionalNotification_verifyPostMethodMockHappyCaseTemplate(2);

        boolean[] refreshValuePerRetry = {true};
        sendOrClearAdditionalNotification_verifyPatchMethodMockTemplate(2, refreshValuePerRetry, 1, true);
    }

    @Test
    public void sendAdditionalNotification_testPostGOCNotificationRespCode401RetryMaxTimes() throws Exception {
        int postNotificationResponseCode = 401;
        sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate();
        when(postMethod.getResponseBodyAsString())
                .thenReturn("{\"instance_url\": \"" + TEST_INSTANCE_URL + "\", \"access_token\": \"" + TEST_TOKEN + "\"}")
                .thenReturn("{\"instance_url\": \"" + TEST_INSTANCE_URL + "2\", \"access_token\": \"" + TEST_TOKEN + "2\"}")
                .thenReturn("{\"instance_url\": \"" + TEST_INSTANCE_URL + "3\", \"access_token\": \"" + TEST_TOKEN + "3\"}");
        when(httpClient.executeMethod(patchMethod)).thenReturn(postNotificationResponseCode);

        // create object under test
        notifier = new GOCNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.sendAdditionalNotification(context);
        assertFalse(result);

        // verify mocks
        sendOrClearAdditionalNotification_verifyPostMethodMockHappyCaseTemplate(MAX_ATTEMPTS_GOC_POST);

        boolean[] refreshValuePerRetry = {true, true};
        sendOrClearAdditionalNotification_verifyPatchMethodMockTemplate(MAX_ATTEMPTS_GOC_POST, refreshValuePerRetry, MAX_ATTEMPTS_GOC_POST, false);
    }

    @Test
    public void sendAdditionalNotification_testPostGOCNotificationRespCode500RetryAndPass() throws Exception {
        int[] postNotificationResponseCode = {500, 201};
        sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate();
        when(postMethod.getResponseBodyAsString())
                .thenReturn("{\"instance_url\": \"" + TEST_INSTANCE_URL + "\", \"access_token\": \"" + TEST_TOKEN + "\"}");
        when(httpClient.executeMethod(patchMethod)).thenReturn(postNotificationResponseCode[0])
                .thenReturn(postNotificationResponseCode[1]);

        // create object under test
        notifier = new GOCNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.sendAdditionalNotification(context);
        assertTrue(result);

        // verify mocks
        sendOrClearAdditionalNotification_verifyPostMethodMockHappyCaseTemplate(1);

        boolean[] refreshValuePerRetry = {};
        sendOrClearAdditionalNotification_verifyPatchMethodMockTemplate(2, refreshValuePerRetry, 1, true);
    }

    @Test
    public void sendAdditionalNotification_testPostGOCNotificationRespCode500RetryMaxTimes() throws Exception {
        int postNotificationResponseCode = 500;
        sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate();
        when(postMethod.getResponseBodyAsString())
                .thenReturn("{\"instance_url\": \"" + TEST_INSTANCE_URL + "\", \"access_token\": \"" + TEST_TOKEN + "\"}");
        when(httpClient.executeMethod(patchMethod)).thenReturn(postNotificationResponseCode);

        // create object under test
        notifier = new GOCNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.sendAdditionalNotification(context);
        assertFalse(result);

        // verify mocks
        sendOrClearAdditionalNotification_verifyPostMethodMockHappyCaseTemplate(1);

        boolean[] refreshValuePerRetry = {};
        sendOrClearAdditionalNotification_verifyPatchMethodMockTemplate(MAX_ATTEMPTS_GOC_POST, refreshValuePerRetry, MAX_ATTEMPTS_GOC_POST, false);
    }

    @Test
    public void sendAdditionalNotification_testAuthHeaderFailRetryMaxTimes() throws Exception {
        int postNotificationResponseCode = 404;
        sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate();
        when(postMethod.getResponseBodyAsString())
                .thenReturn("{\"instance_url\": \"" + TEST_INSTANCE_URL + "\", \"access_token\": \"" + TEST_TOKEN + "\"}")
                .thenReturn("{\"instance_url\": \"" + TEST_INSTANCE_URL + "2\", \"access_token\": \"" + TEST_TOKEN + "2\"}")
                .thenReturn("{\"instance_url\": \"" + TEST_INSTANCE_URL + "3\", \"access_token\": \"" + TEST_TOKEN + "3\"}");
        when(httpClient.executeMethod(patchMethod)).thenReturn(postNotificationResponseCode);
        when(patchMethod.getResponseBodyAsString()).thenReturn("[{\"message\":\"INVALID_HEADER_TYPE\",\"errorCode\":\"INVALID_AUTH_HEADER\"}]");

        // create object under test
        notifier = new GOCNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.sendAdditionalNotification(context);
        assertFalse(result);

        // verify mocks
        sendOrClearAdditionalNotification_verifyPostMethodMockHappyCaseTemplate(MAX_ATTEMPTS_GOC_POST);

        boolean[] refreshValuePerRetry = {true, true};
        sendOrClearAdditionalNotification_verifyPatchMethodMockTemplate(MAX_ATTEMPTS_GOC_POST, refreshValuePerRetry, MAX_ATTEMPTS_GOC_POST, false);
    }

    @Test
    public void sendAdditionalNotification_testUnknownFailureNoRetries() throws Exception {
        int postNotificationResponseCode = 404;
        sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate();
        when(postMethod.getResponseBodyAsString())
                .thenReturn("{\"instance_url\": \"" + TEST_INSTANCE_URL + "\", \"access_token\": \"" + TEST_TOKEN + "\"}");
        when(httpClient.executeMethod(patchMethod)).thenReturn(postNotificationResponseCode);
        when(patchMethod.getResponseBodyAsString()).thenReturn("bad response");

        // create object under test
        notifier = new GOCNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.sendAdditionalNotification(context);
        assertFalse(result);

        // verify mocks
        sendOrClearAdditionalNotification_verifyPostMethodMockHappyCaseTemplate(1);

        boolean[] refreshValuePerRetry = {};
        sendOrClearAdditionalNotification_verifyPatchMethodMockTemplate(1, refreshValuePerRetry, 1, false);
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
        when(postMethod.getResponseBodyAsString())
                .thenReturn("{\"instance_url\": \"" + TEST_INSTANCE_URL + "\", \"access_token\": \"" + TEST_TOKEN + "\"}")
                .thenReturn("{\"instance_url\": \"" + TEST_INSTANCE_URL + "2\", \"access_token\": \"" + TEST_TOKEN + "2\"}");
        when(httpClient.executeMethod(patchMethod)).thenReturn(postNotificationResponseCode[0])
                .thenReturn(postNotificationResponseCode[1]);

        // create object under test
        notifier = new GOCNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.clearAdditionalNotification(context);
        assertTrue(result);

        // verify mocks
        sendOrClearAdditionalNotification_verifyPostMethodMockHappyCaseTemplate(2);

        boolean[] refreshValuePerRetry = {true};
        sendOrClearAdditionalNotification_verifyPatchMethodMockTemplate(2, refreshValuePerRetry, 1, true);
    }

    private boolean sendAdditionalNotification_testTemplate(int postNotificationResponseCode) throws Exception {
        // define mock behavior
        sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate();
        when(httpClient.executeMethod(patchMethod)).thenReturn(postNotificationResponseCode);

        // create object under test
        notifier = new GOCNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.sendAdditionalNotification(context);

        // verify mocks
        sendOrClearAdditionalNotification_verifyMocksHappyCaseTemplate();

        return result;
    }

    private boolean clearAdditionalNotification_testTemplate(int postNotificationResponseCode) throws Exception {
        // define mock behavior
        sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate();
        when(httpClient.executeMethod(patchMethod)).thenReturn(postNotificationResponseCode);

        // create object under test
        notifier = new GOCNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.clearAdditionalNotification(context);

        // verify mocks
        sendOrClearAdditionalNotification_verifyMocksHappyCaseTemplate();

        return result;
    }

    private void sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate() throws Exception {
        sendOrClearAdditionalNotification_mockBehaviorTemplate(200,
                "{\"instance_url\": \"" + TEST_INSTANCE_URL + "\",  \"access_token\": \"" + TEST_TOKEN + "\"}");
    }

    private void sendOrClearAdditionalNotification_mockBehaviorTemplate(int getTokenResponseCode, String getTokenResponseBody) throws Exception {
        // define mock behavior
        when(auditService.createAudit(any())).thenReturn(auditResult);
        whenNew(HttpClient.class).withAnyArguments().thenReturn(httpClient);
        when(httpClient.getParams()).thenReturn(httpClientParams);
        when(httpClient.getHostConfiguration()).thenReturn(new HostConfiguration());
        whenNew(PostMethod.class).withAnyArguments().thenReturn(postMethod);
        when(httpClient.executeMethod(postMethod)).thenReturn(getTokenResponseCode); // get token response code
        whenNew(PatchMethod.class).withAnyArguments().thenReturn(patchMethod);
        when(postMethod.getResponseBodyAsString()).thenReturn(getTokenResponseBody);
    }

    private void sendOrClearAdditionalNotification_verifyMocksHappyCaseTemplate() throws Exception {
        // verify mocks
        sendOrClearAdditionalNotification_verifyPostMethodMockHappyCaseTemplate(1);
        sendOrClearAdditionalNotification_verifyPatchMethodMockHappyCaseTemplate();
    }

    private void sendOrClearAdditionalNotification_verifyPostMethodMockHappyCaseTemplate(int tries) throws Exception {
        verifyNew(PostMethod.class, times(tries)).withArguments(properties.get(GOC_NOTIFIER_GOC_ENDPOINT) + "/services/oauth2/token");
        verify(postMethod, times(tries)).addParameter("grant_type", "password");
        verify(postMethod, times(tries)).addParameter("username", properties.getProperty(GOC_NOTIFIER_GOC_USER));
        verify(postMethod, times(tries)).addParameter("password", properties.getProperty(GOC_NOTIFIER_GOC_PWD));
        verify(postMethod, times(tries)).addParameter("client_id", properties.getProperty(GOC_NOTIFIER_GOC_CLIENT_ID));
        verify(postMethod, times(tries)).addParameter("client_secret", properties.getProperty(GOC_NOTIFIER_GOC_CLIENT_SECRET));
        verify(postMethod, times(tries)).releaseConnection();
    }

    private void sendOrClearAdditionalNotification_verifyPatchMethodMockHappyCaseTemplate() throws Exception {
        boolean[] refreshValuePerRetry = {};
        sendOrClearAdditionalNotification_verifyPatchMethodMockTemplate(1, refreshValuePerRetry, 0, true);
    }

    private void sendOrClearAdditionalNotification_verifyPatchMethodMockTemplate(int tries, boolean[] refreshValuePerRetry, int getResponseBodyAsStringTimes, boolean success) throws Exception {
        int defaultParamPatchMethodCount = refreshValuePerRetry.length > 0 ? 1 : tries;
        verifyNew(PatchMethod.class, times(defaultParamPatchMethodCount)).withArguments(String.format("%s/services/data/v25.0/sobjects/SM_Alert__c/SM_Alert_Id__c/%s%s%s.%s",
                TEST_INSTANCE_URL,
                metric.hashCode(),
                "%20",
                alert.getName(),
                trigger.getName()));
        verify(patchMethod, times(defaultParamPatchMethodCount)).setRequestHeader("Authorization", "Bearer " + TEST_TOKEN);
        if (refreshValuePerRetry.length >= 1 && refreshValuePerRetry[0] && tries > 0) {
            verifyNew(PatchMethod.class, times(1)).withArguments(String.format("%s/services/data/v25.0/sobjects/SM_Alert__c/SM_Alert_Id__c/%s%s%s.%s",
                    TEST_INSTANCE_URL + "2",
                    metric.hashCode(),
                    "%20",
                    alert.getName(),
                    trigger.getName()));
            verify(patchMethod, times(1)).setRequestHeader("Authorization", "Bearer " + TEST_TOKEN);
        }
        if (refreshValuePerRetry.length >= 2 && refreshValuePerRetry[1] && tries > 0) {
            verifyNew(PatchMethod.class, times(1)).withArguments(String.format("%s/services/data/v25.0/sobjects/SM_Alert__c/SM_Alert_Id__c/%s%s%s.%s",
                    TEST_INSTANCE_URL + "3",
                    metric.hashCode(),
                    "%20",
                    alert.getName(),
                    trigger.getName()));
            verify(patchMethod, times(1)).setRequestHeader("Authorization", "Bearer " + TEST_TOKEN);
        }
        verify(patchMethod, times(getResponseBodyAsStringTimes)).getResponseBodyAsString();
        verify(patchMethod, times(tries)).setRequestEntity(any());
        verify(patchMethod, times(tries)).releaseConnection();
        verify(monitorService).modifyCounter(MonitorService.Counter.GOC_NOTIFICATIONS_RETRIES, tries - 1, null);
        verify(monitorService).modifyCounter(MonitorService.Counter.GOC_NOTIFICATIONS_FAILED, success ? 0 : 1, null);
    }
}

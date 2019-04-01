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
@PrepareForTest({GusNotifier.class, GusTransport.class})
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
    private HttpClient httpClient;
    private HttpClientParams httpClientParams;
    private PostMethod oauthPostMethod;
    private PostMethod gusPostMethod;
    private Audit auditResult;

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
        oauthPostMethod = mock(PostMethod.class);
        gusPostMethod = mock(PostMethod.class);
        auditResult = mock(Audit.class);

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
        when(oauthPostMethod.getResponseBodyAsString())
                .thenReturn("{\"instance_url\": \"" + TEST_INSTANCE_URL + "\", \"access_token\": \"" + TEST_TOKEN + "\"}");
        when(httpClient.executeMethod(gusPostMethod)).thenReturn(postNotificationResponseCode[0])
                .thenReturn(postNotificationResponseCode[1]);
        when(gusPostMethod.getResponseBodyAsString()).thenReturn("");

        // create object under test
        notifier = new GusNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.sendAdditionalNotification(context);
        assertTrue(result);

        // verify mocks
        sendOrClearAdditionalNotification_verifyOauthPostMethodMockHappyCaseTemplate(1);
        boolean[] refreshValuePerRetry = {};
        sendOrClearAdditionalNotification_verifyGusPostMethodMockTemplate(2, refreshValuePerRetry, 1, true);
    }

    @Test
    public void sendAdditionalNotification_testGusPostFailRetryMaxTimes() throws Exception {
        int postNotificationResponseCode = 404;
        // define mock behavior
        sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate();
        when(oauthPostMethod.getResponseBodyAsString())
                .thenReturn("{\"instance_url\": \"" + TEST_INSTANCE_URL + "\", \"access_token\": \"" + TEST_TOKEN + "\"}");
        when(httpClient.executeMethod(gusPostMethod)).thenReturn(postNotificationResponseCode);
        when(gusPostMethod.getResponseBodyAsString()).thenReturn("");

        // create object under test
        notifier = new GusNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.sendAdditionalNotification(context);
        assertFalse(result);

        // verify mocks
        sendOrClearAdditionalNotification_verifyOauthPostMethodMockHappyCaseTemplate(1);
        boolean[] refreshValuePerRetry = {};
        sendOrClearAdditionalNotification_verifyGusPostMethodMockTemplate(MAX_ATTEMPTS_GUS_POST, refreshValuePerRetry, MAX_ATTEMPTS_GUS_POST, false);
    }

    @Test
    public void sendAdditionalNotification_testAuthHeaderFailThenRetryAndPass() throws Exception {
        int[] postNotificationResponseCode = {404, 201};
        // define mock behavior
        sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate();
        when(oauthPostMethod.getResponseBodyAsString())
                .thenReturn("{\"instance_url\": \"" + TEST_INSTANCE_URL + "\", \"access_token\": \"" + TEST_TOKEN + "\"}")
                .thenReturn("{\"instance_url\": \"" + TEST_INSTANCE_URL + "2\", \"access_token\": \"" + TEST_TOKEN + "2\"}");
        when(httpClient.executeMethod(gusPostMethod)).thenReturn(postNotificationResponseCode[0])
                .thenReturn(postNotificationResponseCode[1]);
        when(gusPostMethod.getResponseBodyAsString()).thenReturn("[{\"message\":\"INVALID_HEADER_TYPE\",\"errorCode\":\"INVALID_AUTH_HEADER\"}]")
                .thenReturn("");

        // create object under test
        notifier = new GusNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.sendAdditionalNotification(context);
        assertTrue(result);

        // verify mocks
        sendOrClearAdditionalNotification_verifyOauthPostMethodMockHappyCaseTemplate(2);
        boolean[] refreshValuePerRetry = {true};
        sendOrClearAdditionalNotification_verifyGusPostMethodMockTemplate(2, refreshValuePerRetry, 1, true);
    }

    @Test
    public void sendAdditionalNotification_testAuthHeaderFailRetryMaxTimes() throws Exception {
        int postNotificationResponseCode = 404;
        // define mock behavior
        sendOrClearAdditionalNotification_mockBehaviorHappyCaseTemplate();
        when(oauthPostMethod.getResponseBodyAsString())
                .thenReturn("{\"instance_url\": \"" + TEST_INSTANCE_URL + "\", \"access_token\": \"" + TEST_TOKEN + "\"}")
                .thenReturn("{\"instance_url\": \"" + TEST_INSTANCE_URL + "2\", \"access_token\": \"" + TEST_TOKEN + "2\"}")
                .thenReturn("{\"instance_url\": \"" + TEST_INSTANCE_URL + "2\", \"access_token\": \"" + TEST_TOKEN + "3\"}");
        when(httpClient.executeMethod(gusPostMethod)).thenReturn(postNotificationResponseCode);
        when(gusPostMethod.getResponseBodyAsString()).thenReturn("[{\"message\":\"INVALID_HEADER_TYPE\",\"errorCode\":\"INVALID_AUTH_HEADER\"}]");

        // create object under test
        notifier = new GusNotifier(metricService, annotationService, auditService, config, emf, monitorService);

        // test
        boolean result = notifier.sendAdditionalNotification(context);
        assertFalse(result);

        // verify mocks
        sendOrClearAdditionalNotification_verifyOauthPostMethodMockHappyCaseTemplate(MAX_ATTEMPTS_GUS_POST);
        boolean[] refreshValuePerRetry = {true, true};
        sendOrClearAdditionalNotification_verifyGusPostMethodMockTemplate(MAX_ATTEMPTS_GUS_POST, refreshValuePerRetry, MAX_ATTEMPTS_GUS_POST, false);
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
        when(httpClient.executeMethod(gusPostMethod)).thenReturn(postNotificationResponseCode); // post notification response code

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
        when(httpClient.executeMethod(gusPostMethod)).thenReturn(postNotificationResponseCode); // post notification response code

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
        whenNew(HttpClient.class).withAnyArguments().thenReturn(httpClient);
        when(httpClient.getParams()).thenReturn(httpClientParams);
        when(httpClient.getHostConfiguration()).thenReturn(new HostConfiguration());
        whenNew(PostMethod.class).withArguments(properties.getProperty(GUS_NOTIFIER_GUS_ENDPOINT)).thenReturn(oauthPostMethod);
        when(httpClient.executeMethod(oauthPostMethod)).thenReturn(200); // get token response code
        when(oauthPostMethod.getResponseBodyAsString())
                .thenReturn("{\"instance_url\": \"" + TEST_INSTANCE_URL + "\", \"access_token\": \"" + TEST_TOKEN + "\"}");
        whenNew(PostMethod.class).withArguments(properties.getProperty(GUS_NOTIFIER_GUS_POST_ENDPOINT)).thenReturn(gusPostMethod);
    }

    private void sendOrClearAdditionalNotification_verifyMocksHappyCaseTemplate() throws Exception {
        // verify mocks
        sendOrClearAdditionalNotification_verifyOauthPostMethodMockHappyCaseTemplate(1);
        boolean[] refreshValuePerRetry = {};
        sendOrClearAdditionalNotification_verifyGusPostMethodMockTemplate(1, refreshValuePerRetry, 0, true);
    }

    private void sendOrClearAdditionalNotification_verifyOauthPostMethodMockHappyCaseTemplate(int tries) throws Exception {
        verifyNew(PostMethod.class, times(tries)).withArguments(properties.get(GUS_NOTIFIER_GUS_ENDPOINT));
        verify(oauthPostMethod, times(tries)).addParameter("grant_type", "password");
        verify(oauthPostMethod, times(tries)).addParameter("username", properties.getProperty(GUS_NOTIFIER_GUS_USER));
        verify(oauthPostMethod, times(tries)).addParameter("password", properties.getProperty(GUS_NOTIFIER_GUS_PWD));
        verify(oauthPostMethod, times(tries)).addParameter("client_id", properties.getProperty(GUS_NOTIFIER_GUS_CLIENT_ID));
        verify(oauthPostMethod, times(tries)).addParameter("client_secret", properties.getProperty(GUS_NOTIFIER_GUS_CLIENT_SECRET));
        verify(oauthPostMethod, times(tries)).releaseConnection();
    }

    private void sendOrClearAdditionalNotification_verifyGusPostMethodMockTemplate(int tries, boolean[] refreshValuePerRetry, int getResponseBodyAsStringTimes, boolean success) throws Exception {
        verifyNew(PostMethod.class, times(tries)).withArguments(properties.get(GUS_NOTIFIER_GUS_POST_ENDPOINT));
        int defaultParamPatchMethodCount = refreshValuePerRetry.length > 0 ? 1 : tries;
        verify(gusPostMethod, times(defaultParamPatchMethodCount)).setRequestHeader("Authorization", "Bearer " + TEST_TOKEN);
        if (refreshValuePerRetry.length >= 1 && refreshValuePerRetry[0] && tries > 0) {
            verify(gusPostMethod, times(defaultParamPatchMethodCount)).setRequestHeader("Authorization", "Bearer " + TEST_TOKEN + "2");
        }
        if (refreshValuePerRetry.length >= 2 && refreshValuePerRetry[1] && tries > 0) {
            verify(gusPostMethod, times(defaultParamPatchMethodCount)).setRequestHeader("Authorization", "Bearer " + TEST_TOKEN + "3");
        }
        verify(gusPostMethod, times(tries)).setRequestEntity(any());
        verify(gusPostMethod, times(getResponseBodyAsStringTimes)).getResponseBodyAsString();
        verify(gusPostMethod, times(tries)).releaseConnection();
        verify(monitorService).modifyCounter(MonitorService.Counter.GUS_NOTIFICATIONS_RETRIES, tries - 1, null);
        verify(monitorService).modifyCounter(MonitorService.Counter.GUS_NOTIFICATIONS_FAILED, success ? 0 : 1, null);
    }

}

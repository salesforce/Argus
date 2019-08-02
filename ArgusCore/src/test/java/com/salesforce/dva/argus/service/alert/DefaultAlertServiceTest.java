package com.salesforce.dva.argus.service.alert;

import static com.salesforce.dva.argus.TestUtils.generateAlert;
import static com.salesforce.dva.argus.TestUtils.getHistory;
import static com.salesforce.dva.argus.TestUtils.getMetric;
import static com.salesforce.dva.argus.TestUtils.getNotification;
import static com.salesforce.dva.argus.TestUtils.getTrigger;
import static com.salesforce.dva.argus.service.metric.ElasticSearchConsumerOffsetMetricsService.METRIC_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

import javax.persistence.EntityManager;
import javax.persistence.Persistence;

import com.google.common.collect.ImmutableList;
import com.salesforce.dva.argus.service.*;
import com.salesforce.dva.argus.service.alert.notifier.EmailNotifier;
import com.salesforce.dva.argus.service.alert.notifier.RefocusNotifier;
import com.salesforce.dva.argus.service.alert.retriever.ImageDataRetrievalContext;
import com.salesforce.dva.argus.service.alert.retriever.ImageDataRetriever;
import com.salesforce.dva.argus.service.alert.testing.AlertTestResults;
import com.salesforce.dva.argus.service.metric.MetricQueryResult;

import com.salesforce.dva.argus.service.metric.transform.TransformFactory;
import com.salesforce.dva.argus.service.monitor.DataLagService;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.util.RequestContextHolder;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.*;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.google.inject.Provider;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.entity.Trigger.TriggerType;
import com.salesforce.dva.argus.service.MQService.MQQueue;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.AlertWithTimestamp;
import com.salesforce.dva.argus.service.alert.notifier.AuditNotifier;
import org.powermock.reflect.Whitebox;

import com.salesforce.dva.argus.system.SystemMain;
import com.salesforce.dva.argus.TestUtils;

@RunWith(org.mockito.junit.MockitoJUnitRunner.class)
public class DefaultAlertServiceTest {
    private static final String EXPRESSION =
            "DIVIDE(-1h:argus.jvm:file.descriptor.open{host=unknown-host}:avg, -1h:argus.jvm:file.descriptor.max{host=unknown-host}:avg)";

    @Mock private Provider<EntityManager> _emProviderMock;
    @Mock private MQService _mqServiceMock;
    @Mock private MetricService _metricServiceMock;
    @Mock private TSDBService _tsdbServiceMock;
    @Mock private MailService _mailServiceMock;
    @Mock private HistoryService _historyServiceMock;
    @Mock private MonitorService _monitorServiceMock;
    @Mock private AuditService _auditServiceMock;
    @Mock private ImageDataRetriever _imageDataRetrieverMock;
    @Mock private ObjectMapper _mapper;

    private DefaultAlertService alertService;
    private EntityManager em;

    static private SystemMain system;

    @BeforeClass
    static public void setUpClass() {
        system = TestUtils.getInstance();
        system.start();
    }

    @AfterClass
    static public void tearDownClass() {
        if (system != null) {
            system.getServiceFactory().getManagementService().cleanupRecords();
            system.stop();
        }
    }

    @Before
    public void setup() {
        em = mock(EntityManager.class);
        alertService = new DefaultAlertService(system.getConfiguration(), _mqServiceMock, _metricServiceMock, _auditServiceMock,
                _tsdbServiceMock, _mailServiceMock, _historyServiceMock, _monitorServiceMock, _imageDataRetrieverMock, system.getNotifierFactory(),
                _emProviderMock);
        try {
            Field field = alertService.getClass().getDeclaredField("_mapper");
            field.setAccessible(true);
            field.set(alertService, _mapper);
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            fail("Failed to set mocked ObjectMapper using reflection.");
        }
    }

    @After
    public void teardown() {
        // forcing the gc to clean up. Otherwise the EM created gets injected by guice in ut's that run afterwards. So weird
        em = null;
        System.gc();
    }


    @Test
    public void testExecuteScheduledAlerts_ForOneTimeSeries() {
        ServiceFactory sFactory = system.getServiceFactory();
        UserService userService = sFactory.getUserService();

        int triggerMinValue = 50, inertiaPeriod = 1000 * 60 * 5;
        int cooldownPeriod = 1000 * 5;
        final AtomicInteger notificationCount = new AtomicInteger(0);
        final AtomicInteger clearCount = new AtomicInteger(0);

        Metric metric = _createMetric(TestUtils.createRandomName(), TestUtils.createRandomName(), triggerMinValue, inertiaPeriod);

        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "testAlert", "-1h:scope:metric:avg", "* * * * *");
        _setAlertId(alert, "100001");
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "testTrigger", triggerMinValue, inertiaPeriod);
        _setTriggerId(trigger, "100002");
        Notification notification = new Notification("testNotification", alert, AuditNotifier.class.getName(), new ArrayList<String>(),
                cooldownPeriod);
        _setNotificationId(notification, "100003");

        alert.setTriggers(Arrays.asList(trigger));
        alert.setNotifications(Arrays.asList(notification));
        notification.setTriggers(alert.getTriggers());
        alert.setEnabled(true);

        DefaultAlertService spyAlertService = _initializeSpyAlertServiceWithStubs(notificationCount, clearCount,
        Arrays.asList(metric), alert, notification, false);

        assertEquals(new Integer(1), spyAlertService.executeScheduledAlerts(1, 1000));

        assertEquals(1, notificationCount.get());
    }

    @Test
    public void testExecuteScheduledAlerts_ForOneTimeSeriesMultipleTriggers() {
        ServiceFactory sFactory = system.getServiceFactory();
        UserService userService = sFactory.getUserService();

        final AtomicInteger notificationCount = new AtomicInteger(0);
        final AtomicInteger clearCount = new AtomicInteger(0);

        Metric metric = new Metric("scope", "metric");
        Map<Long, String> dps = new HashMap<Long, String>();
        dps.put(1000L, "11");
        dps.put(2000L, "21");
        dps.put(3000L, "31");
        metric.setDatapoints(_convertDatapoints(dps));

        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "testAlert", "-1h:scope:metric:avg", "* * * * *");
        _setAlertId(alert, "100001");
        Trigger trigger1 = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "testTrigger1", 10, 0);
        _setTriggerId(trigger1, "100002");
        Trigger trigger2 = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "testTrigger2", 5, 0);
        _setTriggerId(trigger2, "100003");
        Notification notification = new Notification("testNotification", alert, AuditNotifier.class.getName(), new ArrayList<String>(), 0);
        _setNotificationId(notification, "100004");

        alert.setTriggers(Arrays.asList(trigger1, trigger2));
        alert.setNotifications(Arrays.asList(notification));
        notification.setTriggers(alert.getTriggers());
        alert.setEnabled(true);

        DefaultAlertService spyAlertService = _initializeSpyAlertServiceWithStubs(notificationCount, clearCount,
                Arrays.asList(metric), alert, notification, false);

        assertEquals(new Integer(1), spyAlertService.executeScheduledAlerts(1, 1000));

        assertEquals(2, notificationCount.get());
    }

    @Test
    public void testExecuteScheduledAlerts_ForNoDataTrigger() {
        ServiceFactory sFactory = system.getServiceFactory();
        UserService userService = sFactory.getUserService();

        final AtomicInteger notificationCount = new AtomicInteger(0);
        final AtomicInteger clearCount = new AtomicInteger(0);

        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "testAlert", "-1h:scope:metric:avg", "* * * * *");
        _setAlertId(alert, "100001");
        Trigger trigger1 = new Trigger(alert, TriggerType.NO_DATA, "testTrigger1", 10, 1000*60);
        _setTriggerId(trigger1, "100002");
        Notification notification = new Notification("testNotification", alert, AuditNotifier.class.getName(), new ArrayList<String>(), 0);
        _setNotificationId(notification, "100004");

        alert.setTriggers(Arrays.asList(trigger1));
        alert.setNotifications(Arrays.asList(notification));
        notification.setTriggers(alert.getTriggers());
        alert.setEnabled(true);

        DefaultAlertService spyAlertService = _initializeSpyAlertServiceWithStubs(notificationCount, clearCount,
                Arrays.asList(), alert, notification, false);

        assertEquals(new Integer(1), spyAlertService.executeScheduledAlerts(1, 1000));

        assertEquals(1, notificationCount.get());
    }

    @Test
    public void testExecuteScheduledAlerts_OnCooldown() {
        ServiceFactory sFactory = system.getServiceFactory();
        UserService userService = sFactory.getUserService();

        final AtomicInteger notificationCount = new AtomicInteger(0);
        final AtomicInteger clearCount = new AtomicInteger(0);

        Metric metric = new Metric("scope", "metric");
        Map<Long, String> dps = new HashMap<Long, String>();
        dps.put(1000L, "11");
        dps.put(2000L, "21");
        dps.put(3000L, "31");
        metric.setDatapoints(_convertDatapoints(dps));

        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "testAlert", "-1h:scope:metric:avg", "* * * * *");
        _setAlertId(alert, "100001");
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "testTrigger", 10, 0);
        _setTriggerId(trigger, "100002");
        Notification notification = new Notification("testNotification", alert, AuditNotifier.class.getName(), new ArrayList<String>(),
                600000);
        _setNotificationId(notification, "100003");

        alert.setTriggers(Arrays.asList(new Trigger[] { trigger }));
        alert.setNotifications(Arrays.asList(new Notification[] { notification }));
        notification.setTriggers(alert.getTriggers());
        alert.setEnabled(true);

        DefaultAlertService spyAlertService = _initializeSpyAlertServiceWithStubs(notificationCount, clearCount,
                Arrays.asList(metric), alert, notification, false);

        //This will set the notification on cooldown for the given metric and trigger.
        assertEquals(new Integer(1), spyAlertService.executeScheduledAlerts(1, 1000));
        //This evaluation should not send notification. Hence notificationCount count would still be 1.
        assertEquals(new Integer(1), spyAlertService.executeScheduledAlerts(1, 1000));

        assertEquals(1, notificationCount.get());
        assertEquals(1, notification.getCooldownExpirationMap().size());
    }

    @Test
    public void testExecuteScheduledAlerts_ForMoreThanOneTimeSeries() {
        UserService userService = system.getServiceFactory().getUserService();
        final AtomicInteger notificationCount = new AtomicInteger(0);
        final AtomicInteger clearCount = new AtomicInteger(0);

        Metric metric1 = new Metric("scope1", "metric");
        Map<Long, String> dps1 = new HashMap<Long, String>();
        dps1.put(1000L, "1");
        dps1.put(2000L, "2");
        dps1.put(3000L, "3");
        metric1.setDatapoints(_convertDatapoints(dps1));

        Metric metric2 = new Metric("scope2", "metric");
        Map<Long, String> dps2 = new HashMap<Long, String>();
        dps2.put(4000L, "11");
        dps2.put(5000L, "20");
        dps2.put(6000L, "30");
        metric2.setDatapoints(_convertDatapoints(dps2));

        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "testAlert", "-1h:scope:metric:avg", "* * * * *");
        _setAlertId(alert, "100001");
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "testTrigger", 10, 0);
        _setTriggerId(trigger, "100002");
        Notification notification = new Notification("testNotification", alert, AuditNotifier.class.getName(), new ArrayList<String>(), 0);
        _setNotificationId(notification, "100003");

        alert.setTriggers(Arrays.asList(new Trigger[] { trigger }));
        alert.setNotifications(Arrays.asList(new Notification[] { notification }));
        notification.setTriggers(alert.getTriggers());
        alert.setEnabled(true);

        DefaultAlertService spyAlertService = _initializeSpyAlertServiceWithStubs(notificationCount, clearCount,
                Arrays.asList(metric1, metric2), alert, notification, false);

        assertEquals(new Integer(1), spyAlertService.executeScheduledAlerts(10, 1000));

        assertEquals(1, notificationCount.get());
    }

    @Test
    public void testExecuteScheduledAlerts_ClearNotification() {
        UserService userService = system.getServiceFactory().getUserService();
        final AtomicInteger notificationCount = new AtomicInteger(0);
        final AtomicInteger clearCount = new AtomicInteger(0);

        Metric metric = new Metric("scope", "metric");
        Map<Long, String> dps = new HashMap<Long, String>();
        dps.put(4000L, "11");
        dps.put(5000L, "20");
        dps.put(6000L, "30");
        metric.setDatapoints(_convertDatapoints(dps));

        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "testAlert", "-1h:scope:metric:avg", "* * * * *");
        _setAlertId(alert, "100001");
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "testTrigger", 10, 0);
        _setTriggerId(trigger, "100002");
        Notification notification = new Notification("testNotification", alert, AuditNotifier.class.getName(), new ArrayList<String>(), 0);
        _setNotificationId(notification, "100003");

        alert.setTriggers(Arrays.asList(new Trigger[] { trigger }));
        alert.setNotifications(Arrays.asList(new Notification[] { notification }));
        notification.setTriggers(alert.getTriggers());
        alert.setEnabled(true);
        DefaultAlertService spyAlertService = _initializeSpyAlertServiceWithStubs(notificationCount, clearCount,
                Arrays.asList(metric), alert, notification, false);

        spyAlertService.executeScheduledAlerts(10, 1000);
        assertEquals(1, notificationCount.get());
        //assertEquals(true, notification.isActiveForTriggerAndMetric(trigger, metric));

        notificationCount.set(0);
        clearCount.set(0);

        dps = new HashMap<Long, String>();
        dps.put(4000L, "1");
        dps.put(5000L, "2");
        dps.put(6000L, "3");
        metric.setDatapoints(_convertDatapoints(dps));

        spyAlertService = _initializeSpyAlertServiceWithStubs(notificationCount, clearCount, Arrays.asList(metric),
                alert, notification, false);

        assertEquals(new Integer(1), spyAlertService.executeScheduledAlerts(10, 1000));
        assertEquals(0, notificationCount.get());
        assertEquals(1, clearCount.get());
        //assertEquals(false, notification.isActiveForTriggerAndMetric(trigger, metric));

    }

    @Test
    public void testExecuteScheduledAlerts_OnCooldownWithRefocusNotifier() {
        ServiceFactory sFactory = system.getServiceFactory();
        UserService userService = sFactory.getUserService();

        final AtomicInteger notificationCount = new AtomicInteger(0);
        final AtomicInteger clearCount = new AtomicInteger(0);

        Metric metric = new Metric("scope", "metric");
        Map<Long, String> dps = new HashMap<Long, String>();
        dps.put(1000L, "11");
        dps.put(2000L, "21");
        dps.put(3000L, "31");
        metric.setDatapoints(_convertDatapoints(dps));

        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "testAlert", "-1h:scope:metric:avg", "* * * * *");
        _setAlertId(alert, "100001");
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "testTrigger", 10, 0);
        _setTriggerId(trigger, "100002");
        Notification notification = new Notification("testNotification", alert, RefocusNotifier.class.getName(), new ArrayList<String>(),
                600000); //cool down logic does not apply to Refocus notifier
        _setNotificationId(notification, "100003");

        alert.setTriggers(Arrays.asList(new Trigger[] { trigger }));
        alert.setNotifications(Arrays.asList(new Notification[] { notification }));
        notification.setTriggers(alert.getTriggers());
        alert.setEnabled(true);

        DefaultAlertService spyAlertService = _initializeSpyAlertServiceWithStubs(notificationCount, clearCount,
                Arrays.asList(metric), alert, notification, false);

        //This will set the notification on cooldown for the given metric and trigger.
        assertEquals(new Integer(1), spyAlertService.executeScheduledAlerts(1, 1000));
        //This evaluation should still send notification for refocus. Hence notificationCount count would increase by 1.
        assertEquals(new Integer(1), spyAlertService.executeScheduledAlerts(1, 1000));

        assertEquals(false, notification.isActiveForTriggerAndMetric(trigger, metric)); // refocus notification is stateless

        assertEquals(2, notificationCount.get()); //notification was sent out even on cool down for refocus
        assertEquals(0, notification.getCooldownExpirationMap().size()); //refocuse notifier does not record/persist cooldown info
    }

    @Test
    public void testExecuteScheduledAlerts_ClearNotificationWithRefocusNotifier() {
        UserService userService = system.getServiceFactory().getUserService();
        final AtomicInteger notificationCount = new AtomicInteger(0);
        final AtomicInteger clearCount = new AtomicInteger(0);

        Metric metric = new Metric("scope", "metric");
        Map<Long, String> dps = new HashMap<Long, String>();
        dps.put(4000L, "11");
        dps.put(5000L, "20");
        dps.put(6000L, "30");
        metric.setDatapoints(_convertDatapoints(dps));

        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "testAlert", "-1h:scope:metric:avg", "* * * * *");
        _setAlertId(alert, "100001");
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "testTrigger", 10, 0);
        _setTriggerId(trigger, "100002");
        Notification notification = new Notification("testNotification", alert, RefocusNotifier.class.getName(), new ArrayList<String>(), 0);
        _setNotificationId(notification, "100003");

        alert.setTriggers(Arrays.asList(new Trigger[] { trigger }));
        alert.setNotifications(Arrays.asList(new Notification[] { notification }));
        notification.setTriggers(alert.getTriggers());
        alert.setEnabled(true);

        DefaultAlertService spyAlertService = _initializeSpyAlertServiceWithStubs(notificationCount, clearCount,
                Arrays.asList(metric), alert, notification, false);

        assertEquals(new Integer(1), spyAlertService.executeScheduledAlerts(10, 1000));
        assertEquals(1, notificationCount.get());
        assertEquals(false, notification.isActiveForTriggerAndMetric(trigger, metric)); // refocus notification is stateless

        notificationCount.set(0);
        clearCount.set(0);

        dps = new HashMap<Long, String>();
        dps.put(4000L, "1");
        dps.put(5000L, "2");
        dps.put(6000L, "3");
        metric.setDatapoints(_convertDatapoints(dps));

        spyAlertService = _initializeSpyAlertServiceWithStubs(notificationCount, clearCount, Arrays.asList(metric),
                alert, notification, false);

        spyAlertService.executeScheduledAlerts(10, 1000);
        assertEquals(0, notificationCount.get());
        assertEquals(1, clearCount.get());
        assertEquals(false, notification.isActiveForTriggerAndMetric(trigger, metric)); // refocus notification is stateless

    }


    @Test
    public void testExecuteScheduledAlerts_AlertWithMultipleMetricsNotificationSentForEach() {
        UserService userService = system.getServiceFactory().getUserService();
        final AtomicInteger notificationCount = new AtomicInteger(0);
        final AtomicInteger clearCount = new AtomicInteger(0);

        Metric metric1 = new Metric("scope1", "metric");
        Map<Long, String> dps1 = new HashMap<Long, String>();
        dps1.put(1000L, "11");
        dps1.put(2000L, "20");
        dps1.put(3000L, "30");
        metric1.setDatapoints(_convertDatapoints(dps1));

        Metric metric2 = new Metric("scope2", "metric");
        Map<Long, String> dps2 = new HashMap<Long, String>();
        dps2.put(4000L, "11");
        dps2.put(5000L, "20");
        dps2.put(6000L, "30");
        metric2.setDatapoints(_convertDatapoints(dps2));

        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "testAlert", "-1h:scope:metric:avg", "* * * * *");
        _setAlertId(alert, "100001");
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "testTrigger", 10, 0);
        _setTriggerId(trigger, "100002");
        Notification notification = new Notification("testNotification", alert, AuditNotifier.class.getName(), new ArrayList<String>(), 300000);
        _setNotificationId(notification, "100003");

        alert.setTriggers(Arrays.asList(new Trigger[] { trigger }));
        alert.setNotifications(Arrays.asList(new Notification[] { notification }));
        notification.setTriggers(alert.getTriggers());
        alert.setEnabled(true);

        DefaultAlertService spyAlertService = _initializeSpyAlertServiceWithStubs(notificationCount, clearCount,
                Arrays.asList(metric1, metric2), alert, notification, false);

        assertEquals(new Integer(1), spyAlertService.executeScheduledAlerts(10, 1000));

        assertEquals(2, notificationCount.get());
        assertEquals(2, notification.getCooldownExpirationMap().size());
        assertEquals(2, notification.getActiveStatusMap().size());
    }

    /**
     * This test case is for the following scenario:
     *
     * Evaluation1:
     *      - metric1 violates threshold, notification sent out, notification set on cooldown for metric1.
     *      - metric2 does not violate threshold.
     * Evaluation2:
     *      - metric1 goes back to normal state, since notification was in active state a clear notification is sent out.
     *      - metric2 violates threshold, notification is sent out, notification set on cooldown for metric2.
     */
    @Test
    public void testExecuteScheduledAlerts_Scenario1() {
        UserService userService = system.getServiceFactory().getUserService();
        final AtomicInteger notificationCount = new AtomicInteger(0);
        final AtomicInteger clearCount = new AtomicInteger(0);

        Metric metric1 = new Metric("scope1", "metric");
        Map<Long, String> dps1 = new HashMap<Long, String>();
        dps1.put(1000L, "11");
        dps1.put(2000L, "20");
        dps1.put(3000L, "30");
        metric1.setDatapoints(_convertDatapoints(dps1));

        Metric metric2 = new Metric("scope2", "metric");
        Map<Long, String> dps2 = new HashMap<Long, String>();
        dps2.put(4000L, "1");
        dps2.put(5000L, "2");
        dps2.put(6000L, "3");
        metric2.setDatapoints(_convertDatapoints(dps2));

        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "testAlert", "-1h:scope:metric:avg", "* * * * *");
        _setAlertId(alert, "100001");
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "testTrigger", 10, 0);
        _setTriggerId(trigger, "100002");
        Notification notification = new Notification("testNotification", alert, AuditNotifier.class.getName(), new ArrayList<String>(), 300000);
        _setNotificationId(notification, "100003");

        alert.setTriggers(Arrays.asList(new Trigger[] { trigger }));
        alert.setNotifications(Arrays.asList(new Notification[] { notification }));
        notification.setTriggers(alert.getTriggers());
        alert.setEnabled(true);

        DefaultAlertService spyAlertService = _initializeSpyAlertServiceWithStubs(notificationCount, clearCount,
                Arrays.asList(metric1, metric2), alert, notification, false);
        assertEquals(new Integer(1), spyAlertService.executeScheduledAlerts(10, 1000));

        assertEquals(1, notificationCount.get());
        assertEquals(1, notification.getCooldownExpirationMap().size());
        assertEquals(1, notification.getActiveStatusMap().size());

        notificationCount.set(0);
        clearCount.set(0);

        metric1 = new Metric("scope1", "metric");
        dps1 = new HashMap<Long, String>();
        dps1.put(1000L, "1");
        dps1.put(2000L, "2");
        dps1.put(3000L, "3");
        metric1.setDatapoints(_convertDatapoints(dps1));

        metric2 = new Metric("scope2", "metric");
        dps2 = new HashMap<Long, String>();
        dps2.put(4000L, "11");
        dps2.put(5000L, "21");
        dps2.put(6000L, "31");
        metric2.setDatapoints(_convertDatapoints(dps2));

        spyAlertService = _initializeSpyAlertServiceWithStubs(notificationCount, clearCount, Arrays.asList(metric1, metric2),
                alert, notification, false);
        assertEquals(new Integer(1), spyAlertService.executeScheduledAlerts(10, 1000));

        assertEquals(1, notificationCount.get());
        assertEquals(1, clearCount.get());
        assertEquals(2, notification.getCooldownExpirationMap().size());
        assertEquals(1, _getActiveSize(notification.getActiveStatusMap()));
    }

    private int _getActiveSize(Map<String, Boolean> activeStatusMap) {
        int size = 0;
        for(Map.Entry<String, Boolean> entry : activeStatusMap.entrySet()) {
            if(entry.getValue()) {
                size++;
            }
        }
        return size;
    }

    @Test
    public void testSendNotificationWhenImageSendingIsEnabled() {
        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = generateAlert("testAlert", userService.findAdminUser(), "-1h:"+METRIC_NAME);
        alert.setEnabled(true);
        _setAlertId(alert, "1");

        Trigger trigger = getTrigger(alert, TriggerType.GREATER_THAN, "testTrigger", "2.0", "1");
        List<Trigger> triggerList = ImmutableList.of(trigger);
        alert.setTriggers(triggerList);

        Notification notification = getNotification("EmailNotifier",
                EmailNotifier.class.getName(), alert, ImmutableList.of("test@salesforce.com"));
        alert.addNotification(notification);

        Metric metric = createMetric();
        History history = getHistory();

        Pair<String, byte[]> evaluatedMetricSnapshotDetails = Pair.of("img1", "Test String".getBytes());
        when(_imageDataRetrieverMock.getAnnotatedImage(any(ImageDataRetrievalContext.class))).thenReturn(evaluatedMetricSnapshotDetails);
        when(_imageDataRetrieverMock.getImageURL(evaluatedMetricSnapshotDetails)).thenReturn("https://localhost:8080/img1");

        alertService.sendNotification(trigger, metric, history, notification, alert, 2L, 500L, "triggered");

        verify(_imageDataRetrieverMock, times(1)).getAnnotatedImage(any(ImageDataRetrievalContext.class));
        verify(_imageDataRetrieverMock, times(1)).getImageURL(evaluatedMetricSnapshotDetails);
    }

    @Test
    public void testSendNotificationWhenImageSendingIsDisabled() {
        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = generateAlert("testAlert", userService.findAdminUser(), "-1h:"+METRIC_NAME);
        alert.setEnabled(true);
        _setAlertId(alert, "1");

        Trigger trigger = getTrigger(alert, TriggerType.GREATER_THAN, "testTrigger", "2.0", "1");
        List<Trigger> triggerList = ImmutableList.of(trigger);
        alert.setTriggers(triggerList);

        Notification notification = getNotification("EmailNotifier",
                EmailNotifier.class.getName(), alert, ImmutableList.of("test@salesforce.com"));
        alert.addNotification(notification);

        Metric metric = createMetric();
        History history = getHistory();

        Pair<String, byte[]> evaluatedMetricSnapshotDetails = Pair.of("img1", "Test String".getBytes());

        alertService.sendNotification(trigger, metric, history, notification, alert, 2L, 500L, "notified");

        verify(_imageDataRetrieverMock, never()).getAnnotatedImage(any(ImageDataRetrievalContext.class));
        verify(_imageDataRetrieverMock, never()).getImageURL(evaluatedMetricSnapshotDetails);
    }

    @Test
    public void testClearNotificationWhenImageSendingIsEnabled() {
        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = generateAlert("testAlert", userService.findAdminUser(), "-1h:"+METRIC_NAME);
        alert.setEnabled(true);
        _setAlertId(alert, "1");

        Trigger trigger = getTrigger(alert, TriggerType.GREATER_THAN, "testTrigger", "2.0", "1");
        List<Trigger> triggerList = ImmutableList.of(trigger);
        alert.setTriggers(triggerList);

        Notification notification = getNotification("EmailNotifier",
                EmailNotifier.class.getName(), alert, ImmutableList.of("test@salesforce.com"));
        alert.addNotification(notification);

        Metric metric = createMetric();
        History history = getHistory();

        Pair<String, byte[]> evaluatedMetricSnapshotDetails = Pair.of("img1", "Test String".getBytes());
        when(_imageDataRetrieverMock.getAnnotatedImage(any(ImageDataRetrievalContext.class))).thenReturn(evaluatedMetricSnapshotDetails);
        when(_imageDataRetrieverMock.getImageURL(evaluatedMetricSnapshotDetails)).thenReturn("https://localhost:8080/img1");

        alertService.sendClearNotification(trigger, metric, history, notification, alert, 2L, "cleared");

        verify(_imageDataRetrieverMock, times(1)).getAnnotatedImage(any(ImageDataRetrievalContext.class));
        verify(_imageDataRetrieverMock, times(1)).getImageURL(evaluatedMetricSnapshotDetails);
    }

    @Test
    public void testClearNotificationWhenImageSendingIsDisabled() {
        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = generateAlert("testAlert", userService.findAdminUser(), "-1h:"+METRIC_NAME);
        alert.setEnabled(true);
        _setAlertId(alert, "1");

        Trigger trigger = getTrigger(alert, TriggerType.GREATER_THAN, "testTrigger", "2.0", "1");
        List<Trigger> triggerList = ImmutableList.of(trigger);
        alert.setTriggers(triggerList);

        Notification notification = getNotification("EmailNotifier",
                EmailNotifier.class.getName(), alert, ImmutableList.of("test@salesforce.com"));
        alert.addNotification(notification);

        Metric metric = createMetric();
        History history = getHistory();

        Pair<String, byte[]> evaluatedMetricSnapshotDetails = Pair.of("img1", "Test String".getBytes());

        alertService.sendClearNotification(trigger, metric, history, notification, alert, 2L, "missingdata");

        verify(_imageDataRetrieverMock, never()).getAnnotatedImage(any(ImageDataRetrievalContext.class));
        verify(_imageDataRetrieverMock, never()).getImageURL(evaluatedMetricSnapshotDetails);
    }

    private static Metric createMetric() {
        SecureRandom random = new SecureRandom();
        int datapointCount = ((int) (random.nextDouble() * 500)) + 1;
        Metric result = new Metric("testScopeName", "TestMetric");
        Map<Long, Double> datapoints = new TreeMap<>();

        long timestamp = 1L;
        for (int i = 0; i < datapointCount; i++) {
            datapoints.put(timestamp+1, random.nextDouble() * 500);
        }

        Map<String, String> tags = new HashMap<>();
        tags.put("source", "unittest");
        result.setDatapoints(datapoints);
        result.setTags(tags);
        return result;
    }

    @Test
    public void testGetTriggerFiredDatapointTime() {
        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name_test", EXPRESSION, "* * * * *");
        Metric metric = new Metric("scope", "metric");
        double thresholdValue = 90;
        long inertia = 10;
        long startTime = 1;
        long expectedTriggerTime;
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "name_test", thresholdValue, inertia);
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.putAll(_createDatapoints(inertia + 1, thresholdValue, startTime, true));
        metric.setDatapoints(_convertDatapoints(datapoints));
        expectedTriggerTime = datapoints.size();

        long actualValue = alertService.getTriggerFiredDatapointTime(trigger, metric, alert.getExpression(), startTime);

        assertEquals(expectedTriggerTime, actualValue);
        startTime = datapoints.size() + 1;
        datapoints.putAll(_createDatapoints(201, thresholdValue, startTime, false));
        metric.setDatapoints(_convertDatapoints(datapoints));
        actualValue = alertService.getTriggerFiredDatapointTime(trigger, metric, alert.getExpression(), startTime);
        assertEquals(expectedTriggerTime, actualValue);
        startTime = datapoints.size() + 1;
        datapoints.putAll(_createDatapoints(inertia - 1, thresholdValue, startTime, true));
        metric.setDatapoints(_convertDatapoints(datapoints));
        actualValue = alertService.getTriggerFiredDatapointTime(trigger, metric, alert.getExpression(), startTime);
        assertEquals(expectedTriggerTime, actualValue);
        startTime = datapoints.size() + 1;
        datapoints.putAll(_createDatapoints(inertia + 1, thresholdValue, startTime, true));
        metric.setDatapoints(_convertDatapoints(datapoints));
        actualValue = alertService.getTriggerFiredDatapointTime(trigger, metric, alert.getExpression(), startTime);
        expectedTriggerTime = datapoints.size();
        assertEquals(expectedTriggerTime, actualValue);
        startTime = datapoints.size() + 1;
        datapoints.putAll(_createDatapoints(201, thresholdValue, startTime, false));
        metric.setDatapoints(_convertDatapoints(datapoints));
        actualValue = alertService.getTriggerFiredDatapointTime(trigger, metric, alert.getExpression(), startTime);
        assertEquals(expectedTriggerTime, actualValue);
    }

    @Test
    public void testGetTriggerFiredDatapointTimeWhenOneDatapointAndZeroInertia() {
        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name_test", EXPRESSION, "* * * * *");
        Metric metric = new Metric("scope", "metric");
        double thresholdValue = 90;
        long inertia = 0;
        long startTime = 1000;
        long expectedTriggerTime;
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "name_test", thresholdValue, inertia);
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.putAll(_createDatapoints(1, thresholdValue, startTime, true));
        metric.setDatapoints(_convertDatapoints(datapoints));
        expectedTriggerTime = startTime;

        long actualValue = alertService.getTriggerFiredDatapointTime(trigger, metric, alert.getExpression(), startTime);

        assertEquals(expectedTriggerTime, actualValue);
    }

    @Test
    public void testGetTriggerFiredDatapointTimeWhenOneDatapointAndInertiaOne() {
        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name_test", EXPRESSION, "* * * * *");
        Metric metric = new Metric("scope", "metric");
        double thresholdValue = 90;
        long inertia = 1;
        long startTime = 1000;
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "name_test", thresholdValue, inertia);
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.putAll(_createDatapoints(1, thresholdValue, startTime, true));
        metric.setDatapoints(_convertDatapoints(datapoints));

        Long actualValue = alertService.getTriggerFiredDatapointTime(trigger, metric, alert.getExpression(), startTime);

        assertNull(actualValue);
    }

    @Test
    public void testGetTriggerFiredDatapointTimeWhenNoDatapoints() {
        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name_test", EXPRESSION, "* * * * *");
        Metric metric = new Metric("scope", "metric");
        double thresholdValue = 90;
        long inertia = 0;
        long startTime = 1000;
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "name_test", thresholdValue, inertia);
        Map<Long, String> datapoints = new HashMap<Long, String>();

        datapoints.putAll(_createDatapoints(0, thresholdValue, startTime, true));
        metric.setDatapoints(_convertDatapoints(datapoints));

        Long actualValue = alertService.getTriggerFiredDatapointTime(trigger, metric, alert.getExpression(), startTime);

        assertNull(actualValue);
    }

    @Test
    public void testGetTriggerFiredDatapointTimeWhenMissingTimeStamps() {
        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name_test", EXPRESSION, "* * * * *");
        Metric metric = new Metric("scope", "metric");
        double thresholdValue = 1;
        long inertia = 5*60*1000;
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "name_test", thresholdValue, inertia);
        Map<Long, String> datapoints = new HashMap<Long, String>();
        datapoints.put(0L, "1");
        datapoints.put(inertia, "1");
        metric.setDatapoints(_convertDatapoints(datapoints));
        long actualValue = alertService.getTriggerFiredDatapointTime(trigger, metric, alert.getExpression(), 1L);
        long expectedTriggerTime=5*60*1000;
        assertEquals(expectedTriggerTime, actualValue);
    }

    @Test
    public void testGetTriggerFiredDatapointTimeWhenMissingTimeStamps2() {
        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name_test", EXPRESSION, "* * * * *");
        Metric metric = new Metric("scope", "metric");
        double thresholdValue = 1;
        long inertia = 5*60*1000;
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "name_test", thresholdValue, inertia);
        Map<Long, String> datapoints = new HashMap<Long, String>();
        datapoints.put(0L, "1");
        datapoints.put(3*60*1000L, "1");
        datapoints.put(inertia, "1");
        metric.setDatapoints(_convertDatapoints(datapoints));
        long actualValue = alertService.getTriggerFiredDatapointTime(trigger, metric, alert.getExpression(), 1L);
        long expectedTriggerTime=5*60*1000;
        assertEquals(expectedTriggerTime, actualValue);
    }

    @Test
    public void testGetTriggerFiredDatapointTimeWhenMissingTimeStamps3() {
        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name_test", EXPRESSION, "* * * * *");
        Metric metric = new Metric("scope", "metric");
        double thresholdValue = 1;
        long inertia = 5*60*1000;
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "name_test", thresholdValue, inertia);
        Map<Long, String> datapoints = new HashMap<Long, String>();
        datapoints.put(0L, "1");
        datapoints.put(9*60*1000L, "1");
        metric.setDatapoints(_convertDatapoints(datapoints));
        long actualValue = alertService.getTriggerFiredDatapointTime(trigger, metric, alert.getExpression(), 1L);
        long expectedTriggerTime=9*60*1000;
        assertEquals(expectedTriggerTime, actualValue);
    }

    private Map<Long, Double> _convertDatapoints(Map<Long, String> datapoints) {
        Map<Long, Double> newDps = new HashMap<>();
        for(Map.Entry<Long, String> dp : datapoints.entrySet()) {
            newDps.put(dp.getKey(), Double.parseDouble(dp.getValue()));
        }
        return newDps;
    }

    @Test
    public void testGetTriggerFiredDatapointTimeWhenMissingTimeStamps4() {
        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name_test", EXPRESSION, "* * * * *");
        Metric metric = new Metric("scope", "metric");
        double thresholdValue = 1;
        long inertia = 5*60*1000;
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "name_test", thresholdValue, inertia);
        Map<Long, Double> datapoints = new HashMap<Long, Double>();
        datapoints.put(0L, 2.0);
        datapoints.put(3*60*1000L, 2.0);
        datapoints.put(6*60*1000L, 2.0);
        datapoints.put(7*60*1000L, 0.0);
        datapoints.put(9*60*1000L, 2.0);
        metric.setDatapoints(datapoints);
        long actualValue = alertService.getTriggerFiredDatapointTime(trigger, metric, alert.getExpression(), 1L);
        long expectedTriggerTime = 6 * 60 * 1000;
        assertEquals(expectedTriggerTime, actualValue);
    }

    @Test
    public void testGetTriggerFiredDatapointTimeWhenMissingTimeStamps5() {
        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name_test", EXPRESSION, "* * * * *");
        Metric metric = new Metric("scope", "metric");
        double thresholdValue = 1;
        long inertia = 5*60*1000;
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "name_test", thresholdValue, inertia);
        Map<Long, Double> datapoints = new HashMap<>();
        datapoints.put(0L, 2.0);
        datapoints.put(3*60*1000L, 0.0);
        datapoints.put(6*60*1000L, 2.0);
        datapoints.put(7*60*1000L, 0.0);
        datapoints.put(9*60*1000L, 2.0);
        metric.setDatapoints(datapoints);
        Long actualValue = alertService.getTriggerFiredDatapointTime(trigger, metric, alert.getExpression(), 1L);
        assertNull(actualValue);
    }

    @Test
    public void testGetTriggerFiredDatapointTimeWhenMissingTimeStampsReturnNull() {
        UserService userService = system.getServiceFactory().getUserService();
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name_test", EXPRESSION, "* * * * *");
        Metric metric = new Metric("scope", "metric");
        double thresholdValue = 1;
        long inertia = 5*60*1000;
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "name_test", thresholdValue, inertia);
        Map<Long, Double> datapoints = new HashMap<>();
        datapoints.put(0L, 1.0);
        datapoints.put(2*60*1000L, 0.0);
        datapoints.put(inertia, 1.0);
        metric.setDatapoints(datapoints);
        Long actualValue = alertService.getTriggerFiredDatapointTime(trigger, metric, alert.getExpression(), 1L);
        assertNull(actualValue);
    }

    @Test
    public void testExecuteScheduledAlerts_DuringDatalagPresentWithNoDataTriggerWithSkippedEvaluation() {
        ServiceFactory sFactory = system.getServiceFactory();
        UserService userService = sFactory.getUserService();

        int triggerMinValue = 50, inertiaPeriod = 1000;
        int cooldownPeriod = 1000 * 5;
        final AtomicInteger notificationCount = new AtomicInteger(0);
        final AtomicInteger clearCount = new AtomicInteger(0);

        Metric metric = new Metric("scope", "metric");
        Map<Long, Double> datapoints = new HashMap<>();
        metric.setDatapoints(datapoints);

        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "testAlert", "-1h:scope:metric:avg", "* * * * *");
        _setAlertId(alert, "100001");
        Trigger trigger = new Trigger(alert, TriggerType.NO_DATA, "testTrigger", triggerMinValue, inertiaPeriod);
        _setTriggerId(trigger, "100002");
        Notification notification = new Notification("testNotification", alert, AuditNotifier.class.getName(), new ArrayList<String>(),
                cooldownPeriod);
        _setNotificationId(notification, "100003");

        alert.setTriggers(Arrays.asList(trigger));
        alert.setNotifications(Arrays.asList(notification));
        notification.setTriggers(alert.getTriggers());
        alert.setEnabled(true);

        DefaultAlertService spyAlertService = _initializeSpyAlertServiceWithStubs(notificationCount, clearCount,
                Arrays.asList(), alert, notification, true);

        MetricQueryResult queryResult = new MetricQueryResult();
        queryResult.addInboundMetricQuery(new MetricQuery("scope", "metric", null, 0L, 5000L));
        when(_metricServiceMock.extractDCFromMetricQuery(anyList())).thenReturn(new ArrayList<>(Arrays.asList("DC1")));

        when(_metricServiceMock.getMetrics(anyString(), anyLong())).thenReturn(queryResult);

        assertEquals(new Integer(0), spyAlertService.executeScheduledAlerts(1, 1000));

        assertEquals(0, notificationCount.get());
        enableDatalagMonitoring(false);
    }


    @Test
    public void testExecuteScheduledAlerts_DuringDatalagPresentWithSuccessfulEvaluation() {
        ServiceFactory sFactory = system.getServiceFactory();
        UserService userService = sFactory.getUserService();

        int triggerMinValue = 10, inertiaPeriod = 1;
        int cooldownPeriod = 1000 * 5;

        final AtomicInteger notificationCount = new AtomicInteger(0);
        final AtomicInteger clearCount = new AtomicInteger(0);
        final AtomicInteger refocusCount = new AtomicInteger(0);

        Metric metric = new Metric("scope", "metric");
        Map<Long, String> dps1 = new HashMap<Long, String>();
        dps1.put(1000L, "11");
        dps1.put(2000L, "20");
        dps1.put(3000L, "30");
        metric.setDatapoints(_convertDatapoints(dps1));

        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "testAlert", "-1h:scope:metric:avg", "* * * * *");
        _setAlertId(alert, "100001");
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "testTrigger", triggerMinValue, inertiaPeriod);
        _setTriggerId(trigger, "100002");
        Notification notification = new Notification("testNotification", alert, AuditNotifier.class.getName(), new ArrayList<String>(),
                cooldownPeriod);
        _setNotificationId(notification, "100003");

        alert.setTriggers(Arrays.asList(trigger));
        alert.setNotifications(Arrays.asList(notification));
        notification.setTriggers(alert.getTriggers());
        alert.setEnabled(true);


        DefaultAlertService spyAlertService = _initializeSpyAlertServiceWithStubs(notificationCount, clearCount,
                Arrays.asList(metric), alert, notification, false);

        enableDatalagMonitoring(true);

        assertEquals(new Integer(1), spyAlertService.executeScheduledAlerts(1, 1000));

        assertEquals(1, notificationCount.get());

        enableDatalagMonitoring(false);
    }

    @Test
    public void testExecuteScheduledAlerts_DuringDatalagPresentWithSkippedEvaluation() {
        ServiceFactory sFactory = system.getServiceFactory();
        UserService userService = sFactory.getUserService();

        int triggerMinValue = 10, inertiaPeriod = 1;
        int cooldownPeriod = 1000 * 5;
        final AtomicInteger clearCount = new AtomicInteger(0);
        final AtomicInteger notificationCount = new AtomicInteger(0);

        Metric metric = new Metric("scope", "metric");
        Map<Long, String> dps1 = new HashMap<Long, String>();
        dps1.put(1000L, "11");
        dps1.put(2000L, "20");
        dps1.put(3000L, "30");
        metric.setDatapoints(_convertDatapoints(dps1));

        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "testAlert", "-1h:scope:metric:avg", "* * * * *");
        _setAlertId(alert, "100001");
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "testTrigger", triggerMinValue, inertiaPeriod);
        _setTriggerId(trigger, "100002");
        Notification notification = new Notification("testNotification", alert, AuditNotifier.class.getName(), new ArrayList<String>(),
                cooldownPeriod);
        _setNotificationId(notification, "100003");

        alert.setTriggers(Arrays.asList(trigger));
        alert.setNotifications(Arrays.asList(notification));
        notification.setTriggers(alert.getTriggers());
        alert.setEnabled(true);

        DefaultAlertService spyAlertService = _initializeSpyAlertServiceWithStubs(notificationCount, clearCount,
                Arrays.asList(metric), alert, notification, true);

        assertEquals(new Integer(0), spyAlertService.executeScheduledAlerts(1, 1000));

        assertEquals(0, notificationCount.get());
        enableDatalagMonitoring(false);
    }

    @Test
    public void testExecuteScheduledAlerts_DuringDatalagPresentWithDcDetectionFailedWithSkippedEvaluation() {
        ServiceFactory sFactory = system.getServiceFactory();
        UserService userService = sFactory.getUserService();

        int triggerMinValue = 10, inertiaPeriod = 1;
        int cooldownPeriod = 1000 * 5;
        final AtomicInteger clearCount = new AtomicInteger(0);
        final AtomicInteger notificationCount = new AtomicInteger(0);

        Metric metric = new Metric("scope", "metric");
        Map<Long, String> dps1 = new HashMap<Long, String>();
        dps1.put(1000L, "11");
        dps1.put(2000L, "20");
        dps1.put(3000L, "30");
        metric.setDatapoints(_convertDatapoints(dps1));

        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "testAlert", "-1h:scope:metric:avg", "* * * * *");
        _setAlertId(alert, "100001");
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "testTrigger", triggerMinValue, inertiaPeriod);
        _setTriggerId(trigger, "100002");
        Notification notification = new Notification("testNotification", alert, AuditNotifier.class.getName(), new ArrayList<String>(),
                cooldownPeriod);
        _setNotificationId(notification, "100003");

        alert.setTriggers(Arrays.asList(trigger));
        alert.setNotifications(Arrays.asList(notification));
        notification.setTriggers(alert.getTriggers());
        alert.setEnabled(true);

        DefaultAlertService spyAlertService = _initializeSpyAlertServiceWithStubs(notificationCount, clearCount,
                Arrays.asList(metric), alert, notification, true);

        when(_metricServiceMock.extractDCFromMetricQuery(anyList())).thenReturn(new ArrayList<>());

        assertEquals(new Integer(0), spyAlertService.executeScheduledAlerts(1, 1000));

        assertEquals(0, notificationCount.get());
        enableDatalagMonitoring(false);
    }

    @Test
    public void testExecuteScheduledAlerts_DuringDatalagPresentWithTransformsWithSkippedEvaluation() {
        ServiceFactory sFactory = system.getServiceFactory();
        UserService userService = sFactory.getUserService();

        int triggerMinValue = 10, inertiaPeriod = 1;
        int cooldownPeriod = 1000 * 5;

        final AtomicInteger clearCount = new AtomicInteger(0);
        final AtomicInteger notificationCount = new AtomicInteger(0);

        Metric metric = new Metric("scope", "metric");
        Map<Long, String> dps1 = new HashMap<Long, String>();
        dps1.put(1000L, "11");
        dps1.put(2000L, "20");
        dps1.put(3000L, "30");
        metric.setDatapoints(_convertDatapoints(dps1));

        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "testAlert", "COUNT(-1h:scope:metric:avg, -1h:scope:metric:avg, -1h:scope:metric:avg)", "* * * * *");
        _setAlertId(alert, "100001");
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "testTrigger", triggerMinValue, inertiaPeriod);
        _setTriggerId(trigger, "100002");
        Notification notification = new Notification("testNotification", alert, AuditNotifier.class.getName(), new ArrayList<String>(),
                cooldownPeriod);
        _setNotificationId(notification, "100003");

        alert.setTriggers(Arrays.asList(trigger));
        alert.setNotifications(Arrays.asList(notification));
        notification.setTriggers(alert.getTriggers());
        alert.setEnabled(true);

        DefaultAlertService spyAlertService = _initializeSpyAlertServiceWithStubs(notificationCount, clearCount,
                Arrays.asList(metric), alert, notification, true);

        MetricQueryResult queryResult = new MetricQueryResult();
        queryResult.setMetricsList(new ArrayList<Metric>(Arrays.asList(metric)));
        queryResult.addTransform(TransformFactory.Function.COUNT);
        queryResult.addInboundMetricQuery(new MetricQuery("COUNT", "metric", null, 0L, 5000L));
        when(_metricServiceMock.getMetrics(anyString(), anyLong())).thenReturn(queryResult);
        when(_metricServiceMock.extractDCFromMetricQuery(anyList())).thenReturn(new ArrayList<>(Arrays.asList("DC1", "DC2", "DC3")));

        enableDatalagMonitoring(true);

        assertEquals(new Integer(0), spyAlertService.executeScheduledAlerts(1, 1000));

        assertEquals(0, notificationCount.get());
        enableDatalagMonitoring(false);
    }

    @Test
    public void testExecuteScheduledAlerts_DuringDatalagPresentWithTransformsWithSuccessfulEvaluation() {
        ServiceFactory sFactory = system.getServiceFactory();
        UserService userService = sFactory.getUserService();

        int triggerMinValue = 10, inertiaPeriod = 1;
        int cooldownPeriod = 1000 * 5;

        final AtomicInteger clearCount = new AtomicInteger(0);
        final AtomicInteger notificationCount = new AtomicInteger(0);

        Metric metric = new Metric("scope", "metric");
        Map<Long, String> dps1 = new HashMap<Long, String>();
        dps1.put(1000L, "11");
        dps1.put(2000L, "20");
        dps1.put(3000L, "30");
        metric.setDatapoints(_convertDatapoints(dps1));

        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "testAlert", "COUNT(-1h:scope:metric:avg, -1h:scope:metric:avg, -1h:scope:metric:avg)", "* * * * *");
        _setAlertId(alert, "100001");
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "testTrigger", triggerMinValue, inertiaPeriod);
        _setTriggerId(trigger, "100002");
        Notification notification = new Notification("testNotification", alert, AuditNotifier.class.getName(), new ArrayList<String>(),
                cooldownPeriod);
        _setNotificationId(notification, "100003");

        alert.setTriggers(Arrays.asList(trigger));
        alert.setNotifications(Arrays.asList(notification));
        notification.setTriggers(alert.getTriggers());
        alert.setEnabled(true);

        DefaultAlertService spyAlertService = _initializeSpyAlertServiceWithStubs(notificationCount, clearCount,
                Arrays.asList(metric), alert, notification, false);

        MetricQueryResult queryResult = new MetricQueryResult();
        queryResult.setMetricsList(new ArrayList<Metric>(Arrays.asList(metric)));
        queryResult.addTransform(TransformFactory.Function.COUNT);
        queryResult.addInboundMetricQuery(new MetricQuery("COUNT", "metric", null, 0L, 5000L));
        when(_metricServiceMock.getMetrics(anyString(), anyLong())).thenReturn(queryResult);
        when(_metricServiceMock.extractDCFromMetricQuery(anyList())).thenReturn(new ArrayList<>(Arrays.asList("DC1", "DC2", "DC3")));

        enableDatalagMonitoring(true);

        assertEquals(new Integer(1), spyAlertService.executeScheduledAlerts(1, 1000));

        assertEquals(1, notificationCount.get());
        enableDatalagMonitoring(false);
    }

    @Test
    public void testExecuteScheduledAlerts_DuringDatalagPresentWithWhiteListedScopeWithNoDataWithSuccessfulEvaluation() {
        ServiceFactory sFactory = system.getServiceFactory();
        UserService userService = sFactory.getUserService();

        int triggerMinValue = 50, inertiaPeriod = 1000;
        int cooldownPeriod = 1000 * 5;
        final AtomicInteger notificationCount = new AtomicInteger(0);
        final AtomicInteger clearCount = new AtomicInteger(0);

        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "testAlert", "-1h:whiteListedScope:metric:avg", "* * * * *");
        _setAlertId(alert, "100001");
        Trigger trigger = new Trigger(alert, TriggerType.NO_DATA, "testTrigger", triggerMinValue, inertiaPeriod);
        _setTriggerId(trigger, "100002");
        Notification notification = new Notification("testNotification", alert, AuditNotifier.class.getName(), new ArrayList<String>(),
                cooldownPeriod);
        _setNotificationId(notification, "100003");

        alert.setTriggers(Arrays.asList(trigger));
        alert.setNotifications(Arrays.asList(notification));
        notification.setTriggers(alert.getTriggers());
        alert.setEnabled(true);

        DefaultAlertService spyAlertService = _initializeSpyAlertServiceWithStubs(notificationCount, clearCount,
                Arrays.asList(), alert, notification, true);

        assertEquals(new Integer(1), spyAlertService.executeScheduledAlerts(1, 1000));

        assertEquals(1, notificationCount.get());
        enableDatalagMonitoring(false);
    }

    @Test
    public void testExecuteScheduledAlerts_DuringDatalagPresentWithWhiteListedUserWithNoDataWithSuccessfulEvaluation() {
        ServiceFactory sFactory = system.getServiceFactory();
        UserService userService = sFactory.getUserService();

        int triggerMinValue = 50, inertiaPeriod = 1000;
        int cooldownPeriod = 1000 * 5;
        final AtomicInteger notificationCount = new AtomicInteger(0);
        final AtomicInteger clearCount = new AtomicInteger(0);

        Alert alert = new Alert(userService.findDefaultUser(), userService.findDefaultUser(), "testAlert", "-1h:scope:metric:avg", "* * * * *");
        _setAlertId(alert, "100001");
        Trigger trigger = new Trigger(alert, TriggerType.NO_DATA, "testTrigger", triggerMinValue, inertiaPeriod);
        _setTriggerId(trigger, "100002");
        Notification notification = new Notification("testNotification", alert, AuditNotifier.class.getName(), new ArrayList<String>(),
                cooldownPeriod);
        _setNotificationId(notification, "100003");

        alert.setTriggers(Arrays.asList(trigger));
        alert.setNotifications(Arrays.asList(notification));
        notification.setTriggers(alert.getTriggers());
        alert.setEnabled(true);

        DefaultAlertService spyAlertService = _initializeSpyAlertServiceWithStubs(notificationCount, clearCount,
                Arrays.asList(), alert, notification, true);

        assertEquals(new Integer(1), spyAlertService.executeScheduledAlerts(1, 1000));

        assertEquals(1, notificationCount.get());
        enableDatalagMonitoring(false);
    }

    @Test
    public void testExecuteScheduledAlerts_DuringDatalagPresentWithWhiteListedScopeWithMetricDataWithSuccessfulEvaluation() {
        ServiceFactory sFactory = system.getServiceFactory();
        UserService userService = sFactory.getUserService();

        int triggerMinValue = 10, inertiaPeriod = 1;
        int cooldownPeriod = 1000 * 5;

        final AtomicInteger clearCount = new AtomicInteger(0);
        final AtomicInteger notificationCount = new AtomicInteger(0);

        Metric metric = new Metric("scope", "metric");
        Map<Long, String> dps1 = new HashMap<Long, String>();
        dps1.put(1000L, "11");
        dps1.put(2000L, "20");
        dps1.put(3000L, "30");
        metric.setDatapoints(_convertDatapoints(dps1));

        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "testAlert", "-1h:whiteListedScope:metric:avg", "* * * * *");
        _setAlertId(alert, "100001");
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "testTrigger", triggerMinValue, inertiaPeriod);
        _setTriggerId(trigger, "100002");
        Notification notification = new Notification("testNotification", alert, AuditNotifier.class.getName(), new ArrayList<String>(),
                cooldownPeriod);
        _setNotificationId(notification, "100003");

        alert.setTriggers(Arrays.asList(trigger));
        alert.setNotifications(Arrays.asList(notification));
        notification.setTriggers(alert.getTriggers());
        alert.setEnabled(true);

        DefaultAlertService spyAlertService = _initializeSpyAlertServiceWithStubs(notificationCount, clearCount,
                Arrays.asList(metric), alert, notification, true);

        assertEquals(new Integer(1), spyAlertService.executeScheduledAlerts(1, 1000));

        assertEquals(1, notificationCount.get());
        enableDatalagMonitoring(false);
    }

    @Test
    public void testExecuteScheduledAlerts_DuringDatalagPresentWithWhiteListedUserWithMetricDataWithSuccessfulEvaluation() {
        ServiceFactory sFactory = system.getServiceFactory();
        UserService userService = sFactory.getUserService();

        int triggerMinValue = 10, inertiaPeriod = 1;
        int cooldownPeriod = 1000 * 5;

        final AtomicInteger clearCount = new AtomicInteger(0);
        final AtomicInteger notificationCount = new AtomicInteger(0);

        Metric metric = new Metric("scope", "metric");
        Map<Long, String> dps1 = new HashMap<Long, String>();
        dps1.put(1000L, "11");
        dps1.put(2000L, "20");
        dps1.put(3000L, "30");
        metric.setDatapoints(_convertDatapoints(dps1));

        Alert alert = new Alert(userService.findDefaultUser(), userService.findDefaultUser(), "testAlert", "-1h:scope:metric:avg", "* * * * *");
        _setAlertId(alert, "100001");
        Trigger trigger = new Trigger(alert, TriggerType.NO_DATA, "testTrigger", triggerMinValue, inertiaPeriod);
        _setTriggerId(trigger, "100002");
        Notification notification = new Notification("testNotification", alert, AuditNotifier.class.getName(), new ArrayList<String>(),
                cooldownPeriod);
        _setNotificationId(notification, "100003");

        alert.setTriggers(Arrays.asList(trigger));
        alert.setNotifications(Arrays.asList(notification));
        notification.setTriggers(alert.getTriggers());
        alert.setEnabled(true);

        DefaultAlertService spyAlertService = _initializeSpyAlertServiceWithStubs(notificationCount, clearCount,
                Arrays.asList(metric), alert, notification, true);

        assertEquals(new Integer(1), spyAlertService.executeScheduledAlerts(1, 1000));

        assertEquals(1, notificationCount.get());
        enableDatalagMonitoring(false);
    }

    @Test
    public void testExecuteScheduledAlerts_DuringDatalagPresentWithWhiteListedScopeWithTransformSuccessfulEvaluation() {
        ServiceFactory sFactory = system.getServiceFactory();
        UserService userService = sFactory.getUserService();

        int triggerMinValue = 10, inertiaPeriod = 1;
        int cooldownPeriod = 1000 * 5;

        final AtomicInteger clearCount = new AtomicInteger(0);
        final AtomicInteger notificationCount = new AtomicInteger(0);

        Metric metric = new Metric("scope", "metric");
        Map<Long, String> dps1 = new HashMap<Long, String>();
        dps1.put(1000L, "11");
        dps1.put(2000L, "20");
        dps1.put(3000L, "30");
        metric.setDatapoints(_convertDatapoints(dps1));

        Alert alert = new Alert(userService.findDefaultUser(), userService.findDefaultUser(), "testAlert", "COUNT(-1h:whitelistedScope:metric1:avg,-1h:scope:metric2:avg,-1h:scope:metric3:avg)", "* * * * *");
        _setAlertId(alert, "100001");
        Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "testTrigger", triggerMinValue, inertiaPeriod);
        _setTriggerId(trigger, "100002");
        Notification notification = new Notification("testNotification", alert, AuditNotifier.class.getName(), new ArrayList<String>(),
                cooldownPeriod);
        _setNotificationId(notification, "100003");

        alert.setTriggers(Arrays.asList(trigger));
        alert.setNotifications(Arrays.asList(notification));
        notification.setTriggers(alert.getTriggers());
        alert.setEnabled(true);

        DefaultAlertService spyAlertService = _initializeSpyAlertServiceWithStubs(notificationCount, clearCount,
                Arrays.asList(metric), alert, notification, true);

        MetricQueryResult queryResult = new MetricQueryResult();
        queryResult.setMetricsList(new ArrayList<Metric>(Arrays.asList(metric)));
        queryResult.addTransform(TransformFactory.Function.COUNT);
        queryResult.addInboundMetricQuery(new MetricQuery("COUNT", "metric", null, 0L, 5000L));
        when(_metricServiceMock.getMetrics(anyString(), anyLong())).thenReturn(queryResult);
        when(_metricServiceMock.extractDCFromMetricQuery(anyList())).thenReturn(new ArrayList<>(Arrays.asList("DC1", "DC2", "DC3")));

        assertEquals(new Integer(1), spyAlertService.executeScheduledAlerts(1, 1000));

        assertEquals(1, notificationCount.get());
        enableDatalagMonitoring(false);
    }

    @Test
    public void updateRequestContext_test() {
        ServiceFactory sFactory = system.getServiceFactory();
        UserService userService = sFactory.getUserService();
        Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name_test", EXPRESSION, "* * * * *");

        alertService.updateRequestContext(alert);

        assertNotNull(RequestContextHolder.getRequestContext());
        assertEquals(userService.findAdminUser().getUserName() + "-alert", RequestContextHolder.getRequestContext().getUserName());
    }

    // ------------------------------------------------------------------------------------------
    //  Historical testing unit tests
    // ------------------------------------------------------------------------------------------

    @Test
    public void testAlertsHistorical_WithWhiteListedUserWithMetricDataWithSuccessfulEvaluation() {
        ServiceFactory sFactory = system.getServiceFactory();
        UserService userService = sFactory.getUserService();

        int triggerMinValue = 10, inertiaPeriod = 1;
        int cooldownPeriod = 1000 * 5;

        final AtomicInteger clearCount = new AtomicInteger(0);
        final AtomicInteger notificationCount = new AtomicInteger(0);

        Metric metric = new Metric("scope", "metric");
        Map<Long, String> dps1 = new HashMap<Long, String>();
        dps1.put(1000L, "11");
        dps1.put(2000L, "20");
        dps1.put(3000L, "30");
        metric.setDatapoints(_convertDatapoints(dps1));

        Alert alert = new Alert(userService.findDefaultUser(), userService.findDefaultUser(), "testAlert", "-1h:scope:metric:avg", "* * * * *");
        _setAlertId(alert, "100001");
        Trigger trigger = new Trigger(alert, TriggerType.NO_DATA, "testTrigger", triggerMinValue, inertiaPeriod);
        _setTriggerId(trigger, "100002");
        Notification notification = new Notification("testNotification", alert, AuditNotifier.class.getName(), new ArrayList<String>(),
                cooldownPeriod);
        _setNotificationId(notification, "100003");

        alert.setTriggers(Arrays.asList(trigger));
        alert.setNotifications(Arrays.asList(notification));
        notification.setTriggers(alert.getTriggers());
        alert.setEnabled(true);

        DefaultAlertService spyAlertService = _initializeSpyAlertServiceWithStubs(notificationCount, clearCount,
                Arrays.asList(metric), alert, notification, false);

        // spyAlertService.executeScheduledAlerts(1, 1000);
        AlertTestResults testResults = new AlertTestResults("myUuid");
        spyAlertService.testEvaluateAlert( alert, 3010L, testResults);

        // assertEquals(1, notificationCount.get());
        enableDatalagMonitoring(false);
    }


    // Support Methods --------------------------------------------------------------------------

    private DefaultAlertService _initializeSpyAlertServiceWithStubs(final AtomicInteger notificationCount, final AtomicInteger clearCount,
                                                                    List<Metric> metrics, Alert alert, Notification notification, boolean isDataLagging) {
        DefaultAlertService spyAlertService = spy(alertService);
        when(_emProviderMock.get()).thenReturn(em);

        Long enqueueTime = System.currentTimeMillis();
        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(Alert.class, new Alert.Serializer());
        module.addSerializer(Trigger.class, new Trigger.Serializer());
        module.addSerializer(Notification.class, new Notification.Serializer());
        module.addSerializer(PrincipalUser.class, new Alert.PrincipalUserSerializer());
        mapper.registerModule(module);

        try {
            AlertWithTimestamp alertWithTimestamp = new AlertWithTimestamp(mapper.writeValueAsString(alert), enqueueTime);
            when(_mqServiceMock.dequeue(eq(MQQueue.ALERT.getQueueName()), eq(AlertWithTimestamp.class), anyInt(), anyInt())).
                    thenReturn(Arrays.asList(alertWithTimestamp));
        } catch (JsonProcessingException e) {
            fail("Failed to serialize Alert");
        }

        try {
            doReturn(alert).when(_mapper).readValue(mapper.writeValueAsString(alert), Alert.class);
        } catch (IOException e) {
            fail("Failed to deserialize Alert");
        }

        MetricQueryResult queryResult = new MetricQueryResult();
        queryResult.setMetricsList(metrics);
        when(_metricServiceMock.getMetrics(anyString(), anyLong())).thenReturn(queryResult);
        when(_monitorServiceMock.isDataLagging(any())).thenReturn(isDataLagging);

        enableDatalagMonitoring(isDataLagging);

        doAnswer(new Answer<Notification>() {

            @Override
            public Notification answer(InvocationOnMock invocation) throws Throwable {
                return invocation.getArgument(1, Notification.class);
            }
        }).when(spyAlertService).mergeEntity(em, notification);


        doAnswer(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                return null;
            }

        }).when(spyAlertService).updateNotificationsActiveStatusAndCooldown(Arrays.asList(notification));


        doAnswer(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                notificationCount.incrementAndGet();
                return null;
            }
        }).when(spyAlertService).sendNotification(any(Trigger.class),
                                                            any(Metric.class),
                                                            any(History.class),
                                                            any(Notification.class),
                                                            any(Alert.class),
                                                            anyLong(),
                                                            anyLong(),
                                                            anyString());

        doAnswer(new Answer<Void>() {

            @Override
            public Void answer(InvocationOnMock invocation) throws Throwable {
                clearCount.incrementAndGet();
                return null;
            }
        }).when(spyAlertService).sendClearNotification(any(Trigger.class),
                                                            any(Metric.class),
                                                            any(History.class),
                                                            any(Notification.class),
                                                            any(Alert.class),
                                                            anyLong(),
                                                            anyString());

        return spyAlertService;
    }

    private void enableDatalagMonitoring(boolean isDataLagging) {
        TestUtils.setField(DataLagService.Property.DATA_LAG_MONITOR_ENABLED, "_defaultValue", Boolean.toString(isDataLagging));
    }

    private Metric _createMetric(String scope, String metricName, int triggerMinValue, int inertiaPeriod) {

        long startTime = 1L;
        inertiaPeriod = inertiaPeriod / (1000 * 60);

        Metric result = new Metric(scope, metricName);
        Map<Long, Double> datapoints = new HashMap<>();
        int index = 0;

        for (int j = 0; j <= TestUtils.random.nextInt(10); j++) {
            datapoints.put(startTime + (++index * 60000L), (double)(TestUtils.random.nextInt(triggerMinValue)));
        }
        for (int j = 0; j <= inertiaPeriod; j++) {
            datapoints.put(startTime + (++index * 60000L), (double)(triggerMinValue + TestUtils.random.nextInt(10)));
        }
        for (int j = 0; j <= TestUtils.random.nextInt(10); j++) {
            datapoints.put(startTime + (++index * 60000L), (double)(TestUtils.random.nextInt(triggerMinValue)));
        }
        result.setDatapoints(datapoints);
        result.setDisplayName(TestUtils.createRandomName());
        result.setUnits(TestUtils.createRandomName());
        return result;
    }

    private void _setAlertId(Alert alert, String id) {
        try {
            Field idField = Alert.class.getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(alert, new BigInteger(id));
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            fail("Failed to set alert id using reflection.");
        }
    }

    private void _setTriggerId(Trigger trigger, String id) {
        try {
            Field idField = Trigger.class.getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(trigger, new BigInteger(id));
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            fail("Failed to set alert id using reflection.");
        }
    }

    private void _setNotificationId(Notification notification, String id) {
        try {
            Field idField = Notification.class.getSuperclass().getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(notification, new BigInteger(id));
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            fail("Failed to set alert id using reflection.");
        }
    }

    private Map<Long, String> _createDatapoints(long size, Double value, long startTime, boolean greaterThan) {
        Map<Long, String> result = new HashMap<Long, String>();

        for (int i = 0; i < size; i++) {
            double dataPointValue = TestUtils.random.nextInt(value.intValue()) + (greaterThan ? (value + 2) : -1);

            result.put(startTime++, String.valueOf(dataPointValue));
        }
        return result;
    }

}

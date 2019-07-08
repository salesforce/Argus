package com.salesforce.dva.argus.service.alert;

import com.salesforce.dva.argus.TestUtils;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.service.AlertService;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.system.SystemMain;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.sql.DriverManager;
import java.sql.SQLNonTransientConnectionException;
import java.util.Arrays;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertEquals;

//@Ignore("These new tests are failing in the build pipeline due to persistence issues. @TODO: remove @ignore when pipeline issues are resolved")
public class AlertDefinitionsCacheRefresherThreadTest {
    private static final String EXPRESSION =
            "DIVIDE(-1h:argus.jvm:file.descriptor.open{host=unknown-host}:avg, -1h:argus.jvm:file.descriptor.max{host=unknown-host}:avg)";

    private  SystemMain system;
    private  PrincipalUser admin;
    private  AlertService alertService;
    private  UserService userService;

    private static ch.qos.logback.classic.Logger apacheLogger;
    private static ch.qos.logback.classic.Logger myClassLogger;

    @BeforeClass
    static public void setUpClass() {
        myClassLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AlertDefinitionsCacheRefresherThreadTest.class);
        myClassLogger.setLevel(ch.qos.logback.classic.Level.OFF);
        apacheLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.apache");
        apacheLogger.setLevel(ch.qos.logback.classic.Level.OFF);

    }

    @AfterClass
    static public void tearDownClass() {
    }

    @Before
    public void setup() {
        system = TestUtils.getInstance();
        system.start();
        userService = system.getServiceFactory().getUserService();
        admin = userService.findAdminUser();
        alertService = system.getServiceFactory().getAlertService();
        alertService.findAllAlerts(false).forEach(a -> alertService.deleteAlert(a));

        try {
            Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
            DriverManager.getConnection("jdbc:derby:memory:argus;create=true").close();

        } catch (Exception ex) {
            LoggerFactory.getLogger(AlertServiceTest.class).error("Exception in setUp:{}", ex.getMessage());
            fail("Exception during database startup.");
        }
    }

    @After
    public void tearDown() {
		alertService.findAllAlerts(false).forEach(a -> alertService.deleteAlert(a));
    	if (system != null) {
			system.getServiceFactory().getManagementService().cleanupRecords();
			system.stop();
		}

        try {
            DriverManager.getConnection("jdbc:derby:memory:argus;shutdown=true").close();
        } catch (SQLNonTransientConnectionException ex) {
            if (ex.getErrorCode() >= 50000 || ex.getErrorCode() < 40000) {
                throw new RuntimeException(ex);
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Test
    public void testInitializeCache() {
        AlertDefinitionsCache cache = new AlertDefinitionsCache(alertService, false);
        AlertDefinitionsCacheRefresherThread refresherThread = new AlertDefinitionsCacheRefresherThread(cache, alertService);
        refresherThread.interrupt();

        Alert alert = TestUtils.generateAlert("alert-name", admin, EXPRESSION);
        Trigger trigger = TestUtils.generateTrigger("trigger-name", alert);
        Notification notification = TestUtils.generateNotification("notification-name", alert, Arrays.asList(new Trigger[]{trigger}));
        alert.setNotifications(Arrays.asList(new Notification[]{notification}));
        alert.setTriggers(Arrays.asList(new Trigger[]{trigger}));
        alert.setEnabled(true);
        Alert expectedAlert = alertService.updateAlert(alert);

        refresherThread.initializeAlertDefinitionsCache();
        Map<BigInteger, Alert> alertsMapById = cache.getAlertsMapById();

        assertEquals(1, alertsMapById.size());
        Alert actualAlert = (Alert) alertsMapById.values().toArray()[0];

        assertTrue(actualAlert.equals(expectedAlert));
        assertTrue(actualAlert.getTriggers().toArray()[0].equals(trigger));
        assertTrue(actualAlert.getNotifications().toArray()[0].equals(notification));
    }

    @Ignore
    @Test
    public void testRefreshCache() {
        SystemMain system = TestUtils.getInstance();
        AlertDefinitionsCache cache = new AlertDefinitionsCache(alertService, false);
        AlertDefinitionsCacheRefresherThread refresherThread = new AlertDefinitionsCacheRefresherThread(cache, alertService);

        Alert alert = TestUtils.generateAlert("an-alert", admin, EXPRESSION);
        Trigger trigger = TestUtils.generateTrigger("trigger-name", alert);
        Notification notification = TestUtils.generateNotification("notification-name", alert, Arrays.asList(new Trigger[]{trigger}));
        alert.setNotifications(Arrays.asList(new Notification[]{notification}));
        alert.setTriggers(Arrays.asList(new Trigger[]{trigger}));
        alert.setEnabled(true);
        alert = alertService.updateAlert(alert);

        refresherThread.initializeAlertDefinitionsCache();
        Map<BigInteger, Alert> alertsMapById = cache.getAlertsMapById();

        assertEquals(1, alertsMapById.size());

        Alert expected = alertService.findAlertByPrimaryKey(alert.getId());
        expected.setShared(true);
        alertService.updateAlert(expected);

        long currentExecutionTime = System.currentTimeMillis();
        refresherThread.refreshAlertDefinitionsCache(0, 0, 0, currentExecutionTime);
        alertsMapById = cache.getAlertsMapById();
        Alert actualAlert = (Alert) alertsMapById.values().toArray()[0];

        assertFalse(actualAlert.equals(alert));
        assertTrue(actualAlert.equals(expected));
    }
}
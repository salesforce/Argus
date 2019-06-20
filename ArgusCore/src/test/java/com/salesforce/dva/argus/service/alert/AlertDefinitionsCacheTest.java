package com.salesforce.dva.argus.service.alert;

import com.salesforce.dva.argus.TestUtils;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.system.SystemMain;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mock;

import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.sql.DriverManager;
import java.util.*;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


public class AlertDefinitionsCacheTest {
    static final long MILLISECONDS_PER_MINUTE = 60 * 1000L;

    private static ch.qos.logback.classic.Logger apacheLogger;
    private static ch.qos.logback.classic.Logger myClassLogger;

    @Mock private AlertDefinitionsCacheRefresherThread refreshThreadMock;
    private AlertDefinitionsCache alertDefinitionsCache;
    private SystemMain system;
    private PrincipalUser admin;
    private UserService userService;

    @BeforeClass
    static public void setUpClass() {
        myClassLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AlertDefinitionsCacheRefresherThreadTest.class);
        myClassLogger.setLevel(ch.qos.logback.classic.Level.OFF);
        apacheLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("org.apache");
        apacheLogger.setLevel(ch.qos.logback.classic.Level.OFF);
    }


    @Before
    public void setup() {
        system = TestUtils.getInstance();
        system.start();
        userService = system.getServiceFactory().getUserService();
        admin = userService.findAdminUser();
        try {
            Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
            DriverManager.getConnection("jdbc:derby:memory:argus;create=true").close();
        } catch (Exception ex) {
            LoggerFactory.getLogger(AlertServiceTest.class).error("Exception in setUp:{}", ex.getMessage());
            fail("Exception during database startup.");
        }
        alertDefinitionsCache = new AlertDefinitionsCache(refreshThreadMock);
    }

    @Test
    public void testSetAlertsMapByCronEntry(){
        List<BigInteger> alertsIDList1 = new ArrayList<>();
        alertsIDList1.add(new BigInteger("1"));
        alertsIDList1.add(new BigInteger("2"));

        Map<String, List<BigInteger>> alertMapByCronEntry = new HashMap<>();
        alertMapByCronEntry.put("* * * * *", alertsIDList1);
        alertDefinitionsCache.setAlertsMapByCronEntry(alertMapByCronEntry);

        assertTrue(alertDefinitionsCache.getAlertsMapByCronEntry() == alertMapByCronEntry);
    }

    @Test
    public void testSetAlertsMapById(){
        Map<BigInteger, Alert> alertMapById = new HashMap<>();
        Alert alert1 = new Alert(userService.findAdminUser(), admin, "testAlert1", "COUNT(-1h:scope:metric:avg, -1h:scope:metric:avg, -1h:scope:metric:avg)", "* * * * *");
        alertMapById.put(new BigInteger("1"), alert1);

        Alert alert2 = new Alert(userService.findAdminUser(), admin, "testAlert2", "COUNT(-1h:scope:metric:avg, -1h:scope:metric:avg, -1h:scope:metric:avg)", "* * * * *");
        alertMapById.put(new BigInteger("2"), alert2);

        alertDefinitionsCache.setAlertsMapById(alertMapById);

        assertTrue(alertDefinitionsCache.getAlertsMapById() == alertMapById);
    }

    @Test
    public void testGetEnabledAlertsForMinute(){
        List<BigInteger> alertsIDList1 = new ArrayList<>();
        alertsIDList1.add(new BigInteger("1"));
        alertsIDList1.add(new BigInteger("2"));

        Map<String, List<BigInteger>> alertMapByCronEntry = new HashMap<>();
        alertMapByCronEntry.put("* * * * *", alertsIDList1);
        alertDefinitionsCache.setAlertsMapByCronEntry(alertMapByCronEntry);

        Map<BigInteger, Alert> alertMapById = new HashMap<>();
        Alert alert1 = new Alert(userService.findAdminUser(), admin, "testAlert1", "COUNT(-1h:scope:metric:avg, -1h:scope:metric:avg, -1h:scope:metric:avg)", "* * * * *");
        alertMapById.put(new BigInteger("1"), alert1);

        Alert alert2 = new Alert(userService.findAdminUser(), admin, "testAlert2", "COUNT(-1h:scope:metric:avg, -1h:scope:metric:avg, -1h:scope:metric:avg)", "* * * * *");
        alertMapById.put(new BigInteger("2"), alert2);

        alertDefinitionsCache.setAlertsMapById(alertMapById);

        Long timeInMillis = new Date().getTime();
        Long flooredMinuteInMillis = timeInMillis - (timeInMillis % MILLISECONDS_PER_MINUTE);
        List<Alert> alerts = alertDefinitionsCache.getEnabledAlertsForMinute(flooredMinuteInMillis);
        assertTrue(alerts.size() == 2);
    }
}

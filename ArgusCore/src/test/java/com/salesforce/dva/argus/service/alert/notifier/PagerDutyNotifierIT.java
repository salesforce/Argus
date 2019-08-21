package com.salesforce.dva.argus.service.alert.notifier;

import com.salesforce.dva.argus.AbstractTestIT;
import com.salesforce.dva.argus.IntegrationTest;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.service.AlertService.Notifier.NotificationStatus;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.NotificationContext;
import com.salesforce.dva.argus.system.SystemConfiguration;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertTrue;

@Category(IntegrationTest.class)
public class PagerDutyNotifierIT extends AbstractTestIT {
    private static final String TOKEN = "{INSERT TOKEN HERE}";
    private static final String ROUTING_KEY = "{INSERT KEY HERE}"; // this is the integration key/routing key from pager duty config
    private PagerDutyNotifier notifier;

    @Override
    @Before
    public void setUp() {
        super.setUpZkTestServer();
        super.setupEmbeddedKafka();
        Properties p = new Properties();
        p.setProperty("notifier.property.pagerduty.token", TOKEN);
        p.setProperty("notifier.property.pagerduty.endpoint", "https://events.pagerduty.com");
        p.setProperty("system.property.pagerduty.enabled", "true");
        //p.setProperty("notifier.property.pagerduty.proxy.host", "myhostname.abc.com");
        //p.setProperty("notifier.property.pagerduty.proxy.port", "8080");
        system = getInstance(p);
        system.start();
    }

    @Test
    public void sendPagerDutyNotification_test() throws Exception {
        notifier = system.getNotifierFactory().getPagerDutyNotifier();

        UserService userService = system.getServiceFactory().getUserService();
        PrincipalUser user = userService.findAdminUser();
        Alert a = new Alert(user, user, createRandomName(), "-1h:argus.jvm:cores.active:max", "* * * * *");
        a.setEnabled(true);

        Trigger t = new Trigger(a, Trigger.TriggerType.GREATER_THAN, "TEST TRIGGER IGNORE", 1, 0);
        List<Trigger> triggerList = new LinkedList<>();
        triggerList.add(t);
        a.setTriggers(triggerList);

        List<String> notificationArgList = new ArrayList<String>();
        notificationArgList.add(ROUTING_KEY); // DVA Argus Alerting Subsystem service routing key
        Notification n = new Notification("TEST NOTIF IGNORE", a, "TEST PAGERDUTY NOTIFIER", notificationArgList, 5000L);
        FieldUtils.writeField(n, "id", BigInteger.valueOf(12345L), true);
        n.setSeverityLevel(4);
        a.addNotification(n);

        Metric m = createMetric();

        History h = new History("TEST HISTORY MESSAGE", SystemConfiguration.getHostname(), new BigInteger("100002"), History.JobStatus.STARTED, 10, System.currentTimeMillis() - 86400000);

        NotificationContext context = new NotificationContext(a, t, n, System.currentTimeMillis(), 5, m, h);
        boolean result = notifier.sendPagerDutyNotification(context, NotificationStatus.TRIGGERED);
        assertTrue(result);

        Thread.sleep(10000L);
        n.setName("TEST NOTIF IGNORE DUPLICATE");
        result = notifier.sendPagerDutyNotification(context, NotificationStatus.TRIGGERED);
        assertTrue(result);

        Thread.sleep(120000L);
        result = notifier.sendPagerDutyNotification(context, NotificationStatus.CLEARED);
        assertTrue(result);
    }
}

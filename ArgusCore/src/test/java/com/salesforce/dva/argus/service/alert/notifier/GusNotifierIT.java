package com.salesforce.dva.argus.service.alert.notifier;

import com.salesforce.dva.argus.AbstractTest;
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
public class GusNotifierIT extends AbstractTest {
    private GusNotifier notifier;

    @Override
    @Before
    public void setUp() {
        super.setUpZkTestServer();
        super.setupEmbeddedKafka();
        Properties p = new Properties();
        p.setProperty("notifier.property.alert.gus_client_id", "{INSERT VALUE}");
        p.setProperty("notifier.property.alert.gus_client_secret", "{INSERT VALUE}");
        p.setProperty("notifier.property.alert.gus_user", "{INSERT VALUE}");
        p.setProperty("notifier.property.alert.gus_pwd", "{INSERT VALUE}");
        p.setProperty("notifier.property.alert.gus_endpoint", "https://gus.my.salesforce.com/services/oauth2/token");
        p.setProperty("notifier.property.alert.gus_post_endpoint", "https://gus.my.salesforce.com/services/data/v35.0/chatter/feed-elements?feedElementType=FeedItem");
        p.setProperty("system.property.gus.enabled", "true");
        system = getInstance(p);
        system.start();
    }

    @Test
    public void sendGusNotification_test() {
        notifier = system.getNotifierFactory().getGusNotifier();

        UserService userService = system.getServiceFactory().getUserService();
        PrincipalUser user = userService.findAdminUser();
        Alert a = new Alert(user, user, createRandomName(), "-1h:argus.jvm:cores.active:max", "* * * * *");
        a.setEnabled(true);

        Trigger t = new Trigger(a, Trigger.TriggerType.GREATER_THAN, "TEST TRIGGER IGNORE", 1, 0);
        List<Trigger> triggerList = new LinkedList<>();
        triggerList.add(t);
        a.setTriggers(triggerList);

        List<String> notificationArgList = new ArrayList<String>();
        notificationArgList.add("0F9B0000000IZlDKAW");
        Notification n = new Notification("TEST NOTIF IGNORE", a, "TEST GUS NOTIFIER", notificationArgList, 5000L);
        a.addNotification(n);

        Metric m = createMetric();

        History h = new History("TEST HISTORY MESSAGE", SystemConfiguration.getHostname(), new BigInteger("100002"), History.JobStatus.STARTED, 10, System.currentTimeMillis() - 86400000);

        NotificationContext context = new NotificationContext(a, t, n, System.currentTimeMillis(), 5, m, h);
        boolean result = notifier.sendGusNotification(context, NotificationStatus.TRIGGERED);
        assertTrue(result);
    }
}

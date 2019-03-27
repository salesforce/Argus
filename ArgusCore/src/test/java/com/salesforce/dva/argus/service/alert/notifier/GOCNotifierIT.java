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
public class GOCNotifierIT extends AbstractTest {
    private GOCNotifier notifier;

    @Override
    @Before
    public void setUp() {
        super.setUpZkTestServer();
        super.setupEmbeddedKafka();
        Properties p = new Properties();
        p.setProperty("notifier.property.goc.client.id", "{INSERT VALUE}");
        p.setProperty("notifier.property.goc.client.secret", "{INSERT VALUE}");
        p.setProperty("notifier.property.goc.username", "{INSERT VALUE}");
        p.setProperty("notifier.property.goc.password", "{INSERT VALUE}");
        p.setProperty("notifier.property.goc.endpoint", "https://login.salesforce.com");
        p.setProperty("system.property.goc.enabled", "true");
        system = getInstance(p);
        system.start();
    }

    @Test
    public void _sendAdditionalNotification_test() {
        notifier = system.getNotifierFactory().getGOCNotifier();

        UserService userService = system.getServiceFactory().getUserService();
        PrincipalUser user = userService.findAdminUser();
        Alert a = new Alert(user, user, createRandomName(), "-1h:argus.jvm:cores.active:max", "* * * * *");
        a.setEnabled(true);

        Trigger t = new Trigger(a, Trigger.TriggerType.GREATER_THAN, "TEST TRIGGER IGNORE", 1, 0);
        List<Trigger> triggerList = new LinkedList<>();
        triggerList.add(t);
        a.setTriggers(triggerList);

        List<String> notificationArgList = new ArrayList<String>();
        notificationArgList.add("TEST SUBSCRIPTION");
        Notification n = new Notification("TEST NOTIF IGNORE", a, "TEST GOC NOTIFIER", notificationArgList, 5000L);
        n.setSRActionable(false);
        n.setSeverityLevel(5);

        n.setCustomText("INTEGRATION CUSTOM TEXT");
        n.setEventName("INTEGRATION TEST EVENT NAME");
        n.setElementName("TEST ELEMENT NAME IGNORE");
        n.setProductTag("a1aB0000000QA0QIAW");
        n.setArticleNumber("TEST ARTICLE NUMBER");

        a.addNotification(n);

        Metric m = createMetric();

        History h = new History("TEST HISTORY MESSAGE", SystemConfiguration.getHostname(), new BigInteger("100002"), History.JobStatus.STARTED, 10, System.currentTimeMillis() - 86400000);

        NotificationContext context = new NotificationContext(a, t, n, System.currentTimeMillis(), 5, m, h);
        boolean result = notifier._sendAdditionalNotification(context, NotificationStatus.TRIGGERED);
        assertTrue(result);
    }
}

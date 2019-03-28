package com.salesforce.dva.argus.service;

import java.math.BigInteger;
import java.util.Collections;
import java.util.stream.IntStream;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.entity.History.JobStatus;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.NotificationContext;
import com.salesforce.dva.argus.service.alert.notifier.CallbackNotifier;
import org.junit.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import org.junit.BeforeClass;
import org.junit.AfterClass;
import com.salesforce.dva.argus.system.SystemMain;
import com.salesforce.dva.argus.TestUtils;


/**
 * Created by mingzhong on 26.01.17.
 */
public class CallbackServiceTest {
	private static final String expression =
			"DIVIDE(-1h:argus.jvm:file.descriptor.open{host=unknown-host}:avg, -1h:argus.jvm:file.descriptor.max{host=unknown-host}:avg)";

    static private SystemMain system;
    static AlertService alertService;
    static UserService userService;

    @BeforeClass
    static public void setUpClass() {
        system = TestUtils.getInstance();
        system.start();
        alertService = system.getServiceFactory().getAlertService();
        userService = system.getServiceFactory().getUserService();
    }

    @AfterClass
    static public void tearDownClass() {
        if (system != null) {
            system.getServiceFactory().getManagementService().cleanupRecords();
            system.stop();
        }
    }

	@Test
	public void testCallbackNotifier() {
		WireMockServer mockServer = new WireMockServer(9600);
		mockServer.start();
		WireMock.configureFor("localhost", mockServer.port());
		stubFor(post(anyUrl()).willReturn(aResponse().withStatus(200)));

		Alert alert = new Alert(userService.findAdminUser(),
				userService.findAdminUser(),
				"alert_name",
				expression,
				"* * * * *");
		final Trigger trigger = new Trigger(alert, Trigger.TriggerType.GREATER_THAN_OR_EQ, "trigger_name", 2D, 5);

		final Notification notification = new Notification("notification_name",
				alert,
				"notifier_name",
				Collections.singletonList("http://localhost:9600"),
				23);

		notification.setCustomText("{ \"triggerName\": \"${trigger.name}\", \"alertName\": \"${alert.name}\" }");

		alert.setTriggers(Collections.singletonList(trigger));
		alert.setNotifications(Collections.singletonList(notification));
		alert = alertService.updateAlert(alert);

		History history = new History(JobStatus.SUCCESS.getDescription(), "localhost", BigInteger.ONE, JobStatus.SUCCESS);

		NotificationContext context = new NotificationContext(alert,
				alert.getTriggers().get(0),
				notification,
				System.currentTimeMillis(),
				0.0,
				new Metric("scope", "metric"), history);
		// Test
		CallbackNotifier notifier = (CallbackNotifier) system.getServiceFactory()
				.getAlertService()
				.getNotifier(AlertService.SupportedNotifier.CALLBACK);
		int notificationCounter = 5;

		IntStream.range(0, notificationCounter).forEach(i -> notifier.sendNotification(context));
		assertThat("Unexpected number of triggered alerts.",
				notifier.getAllNotifications(alert).size(),
				is(notificationCounter));
		mockServer.shutdownServer();
	}
}

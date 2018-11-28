package com.salesforce.dva.argus.service;

import java.math.BigInteger;
import java.util.Collections;
import java.util.stream.IntStream;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.entity.History.JobStatus;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.NotificationContext;
import com.salesforce.dva.argus.service.alert.notifier.CallbackNotifier;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

/**
 * Created by mingzhong on 26.01.17.
 */
public class CallbackServiceTest extends AbstractTest {
	private static final String expression =
			"DIVIDE(-1h:argus.jvm:file.descriptor.open{host=unknown-host}:avg, -1h:argus.jvm:file.descriptor.max{host=unknown-host}:avg)";

	@Test
	public void testCallbackNotifier() {

		final UserService userService = system.getServiceFactory().getUserService();
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
		alert = system.getServiceFactory().getAlertService().updateAlert(alert);

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
	}
}

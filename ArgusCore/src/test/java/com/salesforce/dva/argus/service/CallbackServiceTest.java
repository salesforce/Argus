package com.salesforce.dva.argus.service;

import java.util.Collections;
import java.util.stream.IntStream;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Alert;
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

		final String jsonBody = "{ \"uri\" : \"localhost:8080\", \"method\" : \"POST\", \"header\": { \"Content-Type\": \"application/json\" }, \"body\": \"{\\\"triggerName\\\": \\\"«trigger.name>»\\\", \\\"alertName\\\": \\\"«alert.name»\\\"}\",\"template\": \"ST4\"}";
		final Notification notification = new Notification("notification_name",
				alert,
				"notifier_ame",
				Collections.singletonList(jsonBody),
				23);

		alert.setTriggers(Collections.singletonList(trigger));
		alert.setNotifications(Collections.singletonList(notification));
		alert = system.getServiceFactory().getAlertService().updateAlert(alert);

		NotificationContext context = new NotificationContext(alert,
				alert.getTriggers().get(0),
				notification,
				System.currentTimeMillis(),
				0.0,
				new Metric("scope", "metric"));
		// Test
		CallbackNotifier notifier = (CallbackNotifier) system.getServiceFactory()
				.getAlertService()
				.getNotifier(AlertService.SupportedNotifier.CALLBACK);
		int notificationCounter = 3;
		IntStream.range(0, notificationCounter).forEach(i -> notifier.sendNotification(context));
		assertThat("Unexpected number of triggered alerts.",
				notifier.getAllNotifications(alert).size(),
				is(notificationCounter));
	}
}

/*
 * Copyright (c) 2016, Salesforce.com, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.salesforce.dva.argus.service;

import static com.salesforce.dva.argus.service.MQService.MQQueue.ALERT;
import static org.junit.Assert.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.entity.Trigger.TriggerType;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.AlertWithTimestamp;

public class AlertServiceTest extends AbstractTest {

	private static final String EXPRESSION =
			"DIVIDE(-1h:argus.jvm:file.descriptor.open{host=unknown-host}:avg, -1h:argus.jvm:file.descriptor.max{host=unknown-host}:avg)";
	private PrincipalUser admin;
	
	@Before
	public void setup() {
		admin = system.getServiceFactory().getUserService().findAdminUser();
	}
	
	@Test
	public void testUpdateAlert() {
		UserService userService = system.getServiceFactory().getUserService();
		AlertService alertService = system.getServiceFactory().getAlertService();
		Alert expected = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name", EXPRESSION, "* * * * *");
		Notification notification = new Notification("notification", expected, "notifier-name", new ArrayList<String>(), 5000L);
		Trigger trigger = new Trigger(expected, TriggerType.GREATER_THAN, "trigger-name", 0.95, 60000);

		notification.setAlert(expected);
		expected.setNotifications(Arrays.asList(new Notification[] { notification }));
		expected.setTriggers(Arrays.asList(new Trigger[] { trigger }));
		assertTrue(!expected.isEnabled());
		expected = alertService.updateAlert(expected);

		Alert actual = alertService.findAlertByNameAndOwner(expected.getName(), userService.findAdminUser());

		assertEquals(expected.getId(), actual.getId());
		assertEquals(expected.getTriggers(), actual.getTriggers());
		actual.setEnabled(true);
		actual = alertService.updateAlert(actual);
		assertTrue(actual.isEnabled());
		alertService.deleteAlert(actual.getName(), userService.findAdminUser());
		assertTrue(alertService.findAlertByNameAndOwner(expected.getName(), expected.getOwner()) == null);
	}

	@Test
	public void testDeleteAlert() {
		UserService userService = system.getServiceFactory().getUserService();
		AlertService alertService = system.getServiceFactory().getAlertService();
		Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name", EXPRESSION, "* * * * *");
		Notification notification1 = new Notification("notification1", alert, "notifier-name1", new ArrayList<String>(), 5000L);
		Notification notification2 = new Notification("notification2", alert, "notifier-name2", new ArrayList<String>(), 5000L);
		Trigger trigger1 = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "trigger-name1", 0.95, 60000);
		Trigger trigger2 = new Trigger(alert, TriggerType.GREATER_THAN, "trigger-name2", 0.95, 60000);

		alert.setNotifications(Arrays.asList(new Notification[] { notification1, notification2 }));
		alert.setTriggers(Arrays.asList(new Trigger[] { trigger1, trigger2 }));
		for (Notification notification : alert.getNotifications()) {
			notification.setTriggers(alert.getTriggers());
		}
		alert = alertService.updateAlert(alert);
		for (Notification notification : alert.getNotifications()) {
			alertService.deleteNotification(notification);
		}
		for (Trigger trigger : alert.getTriggers()) {
			alertService.deleteTrigger(trigger);
		}
		alert.setTriggers(null);
		alert.setNotifications(null);
		alert = alertService.updateAlert(alert);

		Trigger trigger3 = new Trigger(alert, TriggerType.GREATER_THAN, "trigger-name3", 0.95, 60000);
		Notification notification3 = new Notification("notification3", alert, "notifier-name3", new ArrayList<String>(), 5000L);

		notification3.setTriggers(Arrays.asList(new Trigger[] { trigger3 }));
		alert.setTriggers(Arrays.asList(new Trigger[] { trigger3 }));
		alert.setNotifications(Arrays.asList(new Notification[] { notification3 }));
		alert = alertService.updateAlert(alert);
		alertService.markAlertForDeletion(alert);
		assertNull("Failed to mark alert as deleted", alertService.findAlertByPrimaryKey(alert.getId()));

		Alert result = null;
		List<Alert> results = alertService.findAlertsMarkedForDeletion();

		for (Alert a : results) {
			if (alert.getId().equals(a.getId())) {
				result = a;
				break;
			}
		}
		assertNotNull("Could not find the alert that was marked for deletion", result);
		assertTrue(result.isDeleted());
		alertService.deleteAlert(alert);
		assertNull("Failed to delete alert", alertService.findAlertByPrimaryKey(alert.getId()));
	}

	@Test
	public void testFindAlertByNameAndOwner() {
		AlertService alertService = system.getServiceFactory().getAlertService();
		String alertName = "testAlert";
		PrincipalUser expectedUser = new PrincipalUser(admin, "testUser", "testuser@testcompany.com");
		Alert expectedAlert = new Alert(expectedUser, expectedUser, alertName, EXPRESSION, "* * * * *");

		expectedAlert = alertService.updateAlert(expectedAlert);

		Alert actualAlert = alertService.findAlertByNameAndOwner(alertName, expectedAlert.getOwner());

		assertNotNull(actualAlert);
		assertEquals(expectedAlert.getId(), actualAlert.getId());
	}

	@Test
	public void testfindAlertsByOwner() {
		UserService userService = system.getServiceFactory().getUserService();
		AlertService alertService = system.getServiceFactory().getAlertService();
		String userName = createRandomName();
		int alertsCount = random.nextInt(20) + 1;
		PrincipalUser user = new PrincipalUser(admin ,userName, userName + "@testcompany.com");

		user = userService.updateUser(user);

		List<Alert> expectedAlerts = new ArrayList<>();

		for (int i = 0; i < alertsCount; i++) {
			expectedAlerts.add(alertService.updateAlert(new Alert(user, user, "alert_" + i, EXPRESSION, "* * * * *")));
		}

		List<Alert> actualAlerts = alertService.findAlertsByOwner(user, false);

		assertEquals(actualAlerts.size(), expectedAlerts.size());

		Set<Alert> actualSet = new HashSet<>();

		actualSet.addAll(actualAlerts);
		for (Alert alert : expectedAlerts) {
			assertTrue(actualSet.contains(alert));
		}
	}
	
	@Test
	public void testfindAlertsByOwnerMeta() {
		UserService userService = system.getServiceFactory().getUserService();
		AlertService alertService = system.getServiceFactory().getAlertService();
		String userName = createRandomName();
		int alertsCount = random.nextInt(20) + 1;
		PrincipalUser user = new PrincipalUser(admin, userName, userName + "@testcompany.com");

		user = userService.updateUser(user);

		List<Alert> expectedAlerts = new ArrayList<>();

		for (int i = 0; i < alertsCount; i++) {
			expectedAlerts.add(alertService.updateAlert(new Alert(user, user, "alert_" + i, EXPRESSION, "* * * * *")));
		}

		List<Alert> actualAlerts = alertService.findAlertsByOwner(user, true);

		assertEquals(actualAlerts.size(), expectedAlerts.size());

		Set<Alert> actualSet = new HashSet<>();

		actualSet.addAll(actualAlerts);
		for (Alert alert : expectedAlerts) {
			assertTrue(actualSet.contains(alert));
		}
	}

	@Test
	public void findAllAlerts() {
		UserService userService = system.getServiceFactory().getUserService();
		AlertService alertService = system.getServiceFactory().getAlertService();
		String userName = createRandomName();
		int alertsCount = random.nextInt(100) + 1;
		PrincipalUser user = new PrincipalUser(admin, userName, userName + "@testcompany.com");

		user = userService.updateUser(user);

		List<Alert> expectedAlerts = new ArrayList<>();

		for (int i = 0; i < alertsCount; i++) {
			expectedAlerts.add(alertService.updateAlert(new Alert(user, user, "alert_" + i, EXPRESSION, "* * * * *")));
		}

		List<Alert> actualAlerts = alertService.findAllAlerts(false);

		assertEquals(actualAlerts.size(), expectedAlerts.size());

		Set<Alert> actualSet = new HashSet<>();

		actualSet.addAll(actualAlerts);
		for (Alert alert : expectedAlerts) {
			assertTrue(actualSet.contains(alert));
		}
	}
	
	@Test
	public void testFindAllAlertsMeta() {
		UserService userService = system.getServiceFactory().getUserService();
		AlertService alertService = system.getServiceFactory().getAlertService();
		String userName = createRandomName();
		int alertsCount = random.nextInt(100) + 1;
		PrincipalUser user = new PrincipalUser(admin, userName, userName + "@testcompany.com");

		user = userService.updateUser(user);

		List<Alert> expectedAlerts = new ArrayList<>();

		for (int i = 0; i < alertsCount; i++) {
			expectedAlerts.add(alertService.updateAlert(new Alert(user, user, "alert_" + i, EXPRESSION, "* * * * *")));
		}

		List<Alert> actualAlerts = alertService.findAllAlerts(true);

		assertEquals(actualAlerts.size(), expectedAlerts.size());

		Set<Alert> actualSet = new HashSet<>();

		actualSet.addAll(actualAlerts);
		for (Alert alert : expectedAlerts) {
			assertTrue(actualSet.contains(alert));
		}
	}

	@Test
	public void testAlertDelete() {
		UserService userService = system.getServiceFactory().getUserService();
		AlertService alertService = system.getServiceFactory().getAlertService();
		PrincipalUser user = userService.findAdminUser();
		String alertName = createRandomName();
		Alert expectedAlert = new Alert(user, user, alertName, EXPRESSION, "* * * * *");

		expectedAlert = alertService.updateAlert(expectedAlert);
		alertService.deleteAlert(expectedAlert);
		assertNull(alertService.findAlertByNameAndOwner(alertName, user));
	}

	@Test
	public void testDeletedTriggersInNotifications() {
		UserService userService = system.getServiceFactory().getUserService();
		AlertService alertService = system.getServiceFactory().getAlertService();
		Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name", EXPRESSION, "* * * * *");
		Notification notification1 = new Notification("notification1", alert, "notifier-name1", new ArrayList<String>(), 5000L);
		Notification notification2 = new Notification("notification2", alert, "notifier-name2", new ArrayList<String>(), 5000L);
		Notification notification3 = new Notification("notification3", alert, "notifier-name3", new ArrayList<String>(), 5000L);
		Trigger trigger1 = new Trigger(alert, TriggerType.GREATER_THAN_OR_EQ, "trigger-name1", 0.95, 60000);

		alert.setNotifications(Arrays.asList(new Notification[] { notification1, notification2, notification3 }));
		alert.setTriggers(Arrays.asList(new Trigger[] { trigger1 }));
		notification1.setTriggers(alert.getTriggers());
		notification2.setTriggers(alert.getTriggers());
		notification3.setTriggers(alert.getTriggers());
		alert = alertService.updateAlert(alert);
		for (Notification notification : alert.getNotifications()) {
			notification.setTriggers(null);
		}
		alert.setTriggers(new ArrayList<Trigger>());
		alert = alertService.updateAlert(alert);
		for (Notification notification : alert.getNotifications()) {
			assertTrue(notification.getTriggers().isEmpty());
		}
	}

	@Test
	public void testAlertDeleteCreateAnotherAlertWithSameName() {
		UserService userService = system.getServiceFactory().getUserService();
		AlertService alertService = system.getServiceFactory().getAlertService();
		PrincipalUser user = userService.findAdminUser();
		String alertName = createRandomName();
		Alert alert = new Alert(user, user, alertName, EXPRESSION, "* * * * *");

		alert = alertService.updateAlert(alert);
		alertService.markAlertForDeletion(alert);
		assertNull(alertService.findAlertByNameAndOwner(alertName, user));
		alert = new Alert(user, user, alertName, EXPRESSION, "* * * * *");
		alert = alertService.updateAlert(alert);
		assertNotNull((alertService.findAlertByNameAndOwner(alertName, user)));
	}
	
	@Test
	public void testAlertEnqueue() {
		UserService userService = system.getServiceFactory().getUserService();
		AlertService alertService = system.getServiceFactory().getAlertService();
		MQService mqService = system.getServiceFactory().getMQService();
		PrincipalUser user = userService.findAdminUser();
		List<Alert> actualAlertList = new ArrayList<>();

		for (int i = 0; i < 5; i++) {
			actualAlertList.add(alertService.updateAlert(new Alert(user, user, createRandomName(), EXPRESSION, "* * * * *")));
		}
		alertService.enqueueAlerts(actualAlertList);

		List<AlertWithTimestamp> expectedList = mqService.dequeue(ALERT.getQueueName(), AlertWithTimestamp.class, 1000, 10);

		assertEquals(actualAlertList.size(), expectedList.size());
	}
	
	@Test
	public void testSharedAlertWhenOneSharedAlert() {
		UserService userService = system.getServiceFactory().getUserService();
		AlertService alertService = system.getServiceFactory().getAlertService();
		PrincipalUser user1 = userService.updateUser(new PrincipalUser(admin, "test1", "test1@salesforce.com"));
		
		alertService.updateAlert(new Alert(user1, user1, "alert-name1", EXPRESSION, "* * * * *"));
		Alert alertShared = alertService.updateAlert(new Alert(user1, user1, "alert-name-shared2", EXPRESSION, "* * * * *"));
		
		alertShared.setShared(true);
		alertService.updateAlert(alertShared);
		
		List<Alert> expectedSharedResult = new ArrayList<>();
		expectedSharedResult.add(alertShared);
		List<Alert> actualResult=alertService.findSharedAlerts(false, null, null);
		assertEquals(expectedSharedResult, actualResult);
	}
	
	@Test
	public void testSharedAlertWhenTwoSharedAlert() {
		UserService userService = system.getServiceFactory().getUserService();
		AlertService alertService = system.getServiceFactory().getAlertService();
		PrincipalUser user1 = userService.updateUser(new PrincipalUser(admin, "test1", "test1@salesforce.com"));
		PrincipalUser user2 = userService.updateUser(new PrincipalUser(admin, "test2", "test2@salesforce.com"));
		
		Alert alertSharedUser1 = alertService.updateAlert(new Alert(user1, user1, "alert-name_shared1", EXPRESSION, "* * * * *"));
		Alert alertSharedUser2 = alertService.updateAlert(new Alert(user2, user2, "alert-name-shared2", EXPRESSION, "* * * * *"));
		
		alertSharedUser1.setShared(true);
		alertService.updateAlert(alertSharedUser1);
		alertSharedUser2.setShared(true);
		alertService.updateAlert(alertSharedUser2);
		
		List<Alert> expectedSharedResult = new ArrayList<>();
		expectedSharedResult.add(alertSharedUser1);
		expectedSharedResult.add(alertSharedUser2);
		
		
		assertEquals(expectedSharedResult, alertService.findSharedAlerts(false, null, null));
	}
	
	@Test
	public void testFindSharedAlertsMeta() {
		UserService userService = system.getServiceFactory().getUserService();
		AlertService alertService = system.getServiceFactory().getAlertService();
		PrincipalUser user1 = userService.updateUser(new PrincipalUser(admin, "test1", "test1@salesforce.com"));
		PrincipalUser user2 = userService.updateUser(new PrincipalUser(admin, "test2", "test2@salesforce.com"));
		
		Alert alertSharedUser1 = alertService.updateAlert(new Alert(user1, user1, "alert-name_shared1", EXPRESSION, "* * * * *"));
		Alert alertSharedUser2 = alertService.updateAlert(new Alert(user2, user2, "alert-name-shared2", EXPRESSION, "* * * * *"));
		
		alertSharedUser1.setShared(true);
		alertService.updateAlert(alertSharedUser1);
		alertSharedUser2.setShared(true);
		alertService.updateAlert(alertSharedUser2);
		
		List<Alert> expectedSharedResult = new ArrayList<>();
		expectedSharedResult.add(alertSharedUser1);
		expectedSharedResult.add(alertSharedUser2);
		
		
		assertEquals(expectedSharedResult, alertService.findSharedAlerts(true, null, null));
	}

	@Test
	public void testFindSharedAlertsByOwner() {
		UserService userService = system.getServiceFactory().getUserService();
		AlertService alertService = system.getServiceFactory().getAlertService();
		PrincipalUser user1 = userService.updateUser(new PrincipalUser(admin, "test1", "test1@salesforce.com"));
		PrincipalUser user2 = userService.updateUser(new PrincipalUser(admin, "test2", "test2@salesforce.com"));
		
		Alert alertSharedUser1 = alertService.updateAlert(new Alert(user1, user1, "alert-name_shared1", EXPRESSION, "* * * * *"));
		Alert alertSharedUser2 = alertService.updateAlert(new Alert(user2, user2, "alert-name-shared2", EXPRESSION, "* * * * *"));
		Alert alertSharedAdmin = alertService.updateAlert(new Alert(admin, admin, "alert-name-shared3", EXPRESSION, "* * * * *"));
		
		alertSharedUser1.setShared(true);
		alertService.updateAlert(alertSharedUser1);
		alertSharedUser2.setShared(true);
		alertService.updateAlert(alertSharedUser2);
		alertSharedAdmin.setShared(true);
		alertService.updateAlert(alertSharedAdmin);
		
		List<Alert> expectedSharedResult = new ArrayList<>();
		expectedSharedResult.add(alertSharedUser1);
		expectedSharedResult.add(alertSharedUser2);
		expectedSharedResult.add(alertSharedAdmin);
		
	    assertEquals(3, alertService.findSharedAlerts(false, null, null).size());
		
		List<Alert> expectedSharedResultUser1 = new ArrayList<>();
		expectedSharedResultUser1.add(alertSharedUser1);
		assertEquals(expectedSharedResultUser1, alertService.findSharedAlerts(false, user1, null));
		
		List<Alert> expectedSharedResultUser2 = new ArrayList<>();
		expectedSharedResultUser2.add(alertSharedUser2);
		assertEquals(expectedSharedResultUser2, alertService.findSharedAlerts(false, user2, null));

		List<Alert> expectedSharedResultAdmin = new ArrayList<>();
		expectedSharedResultAdmin.add(alertSharedAdmin);
		assertEquals(expectedSharedResultAdmin, alertService.findSharedAlerts(false, admin, null));
		
		alertSharedAdmin.setShared(false);
		alertService.updateAlert(alertSharedAdmin);
		assertEquals(new ArrayList<Alert>(), alertService.findSharedAlerts(false, admin, null));
	}
	
	@Test
	public void testFindSharedAlertsMetaByOwner() {
		UserService userService = system.getServiceFactory().getUserService();
		AlertService alertService = system.getServiceFactory().getAlertService();
		PrincipalUser user1 = userService.updateUser(new PrincipalUser(admin, "test1", "test1@salesforce.com"));
		PrincipalUser user2 = userService.updateUser(new PrincipalUser(admin, "test2", "test2@salesforce.com"));
		
		Alert alertSharedUser1 = alertService.updateAlert(new Alert(user1, user1, "alert-name_shared1", EXPRESSION, "* * * * *"));
		Alert alertSharedUser2 = alertService.updateAlert(new Alert(user2, user2, "alert-name-shared2", EXPRESSION, "* * * * *"));
		Alert alertSharedAdmin = alertService.updateAlert(new Alert(admin, admin, "alert-name-shared3", EXPRESSION, "* * * * *"));
		
		alertSharedUser1.setShared(true);
		alertService.updateAlert(alertSharedUser1);
		alertSharedUser2.setShared(true);
		alertService.updateAlert(alertSharedUser2);
		alertSharedAdmin.setShared(true);
		alertService.updateAlert(alertSharedAdmin);
		
		List<Alert> expectedSharedResult = new ArrayList<>();
		expectedSharedResult.add(alertSharedUser1);
		expectedSharedResult.add(alertSharedUser2);
		expectedSharedResult.add(alertSharedAdmin);
		
	    assertEquals(3, alertService.findSharedAlerts(true, null, null).size());
		
		List<Alert> expectedSharedResultUser1 = new ArrayList<>();
		expectedSharedResultUser1.add(alertSharedUser1);
		assertEquals(expectedSharedResultUser1, alertService.findSharedAlerts(true, user1, null));
		
		List<Alert> expectedSharedResultUser2 = new ArrayList<>();
		expectedSharedResultUser2.add(alertSharedUser2);
		assertEquals(expectedSharedResultUser2, alertService.findSharedAlerts(true, user2, null));

		List<Alert> expectedSharedResultAdmin = new ArrayList<>();
		expectedSharedResultAdmin.add(alertSharedAdmin);
		assertEquals(expectedSharedResultAdmin, alertService.findSharedAlerts(true, admin, null));
		
		alertSharedAdmin.setShared(false);
		alertService.updateAlert(alertSharedAdmin);
		assertEquals(new ArrayList<Alert>(), alertService.findSharedAlerts(true, admin, null));
	}
	
	@Test
	public void testAlertSerDes() {
		
		UserService userService = system.getServiceFactory().getUserService();
		AlertService alertService = system.getServiceFactory().getAlertService();
		Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name", EXPRESSION, "* * * * *");
		Notification notification = new Notification("notification", alert, "notifier-name", new ArrayList<String>(), 5000L);
		Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN, "trigger-name", 0.95, 60000);

		alert.setNotifications(Arrays.asList(notification));
		alert.setTriggers(Arrays.asList(trigger));

		notification.setAlert(alert);
		notification.setTriggers(Arrays.asList(trigger));
		
		alert = alertService.updateAlert(alert);
		
		notification = alert.getNotifications().get(0);
		trigger = alert.getTriggers().get(0);
		
		notification.setCooldownExpirationByTriggerAndMetric(trigger, new Metric("scope", "metric1"),  System.currentTimeMillis());
		notification.setCooldownExpirationByTriggerAndMetric(trigger, new Metric("scope", "metric2"),  System.currentTimeMillis());
		
		ObjectMapper mapper = new ObjectMapper();
		SimpleModule module = new SimpleModule();
		module.addSerializer(Alert.class, new Alert.Serializer());
		module.addSerializer(Trigger.class, new Trigger.Serializer());
		module.addSerializer(Notification.class, new Notification.Serializer());
		module.addSerializer(PrincipalUser.class, new Alert.PrincipalUserSerializer());
		module.addDeserializer(Alert.class, new Alert.Deserializer());
		
		mapper.registerModule(module);
		
		try {
			String serializedAlert = mapper.writeValueAsString(alert);
			Alert deserializedAlert = mapper.readValue(serializedAlert, Alert.class);
			
			assertEquals(alert.getId(), deserializedAlert.getId());
			assertEquals(alert.getName(),  deserializedAlert.getName());
			assertEquals(alert.getExpression(), deserializedAlert.getExpression());
			assertEquals(alert.getCronEntry(), deserializedAlert.getCronEntry());
			assertEquals(alert.isEnabled(), deserializedAlert.isEnabled());
			assertEquals(alert.isMissingDataNotificationEnabled(), deserializedAlert.isMissingDataNotificationEnabled());
			assertEquals(alert.getOwner(), deserializedAlert.getOwner());
			
			assertEquals(alert.getTriggers().size(), deserializedAlert.getTriggers().size());
			for(int i=0; i<alert.getTriggers().size(); i++) {
				_assertEquals(alert.getTriggers().get(i), deserializedAlert.getTriggers().get(i));
			}
			
			assertEquals(alert.getNotifications().size(), deserializedAlert.getNotifications().size());
			for(int i=0; i<alert.getNotifications().size(); i++) {
				_assertEquals(alert.getNotifications().get(i), deserializedAlert.getNotifications().get(i));
			}
			
		} catch (IOException e) {
			fail("IOException while serializing/deserializing alert.");
		} 
		
	}

	private void _assertEquals(Notification expected, Notification actual) {
		assertEquals(expected.getId(), actual.getId());
		assertEquals(expected.getName(), actual.getName());
		assertEquals(expected.getNotifierName(), actual.getNotifierName());
		assertEquals(expected.getSRActionable(), actual.getSRActionable());
		assertEquals(expected.getSeverityLevel(), actual.getSeverityLevel());
		assertEquals(expected.getCooldownPeriod(), actual.getCooldownPeriod());
		assertEquals(expected.getCustomText(), actual.getCustomText());
		assertEquals(expected.getSubscriptions(), actual.getSubscriptions());
		assertEquals(expected.getMetricsToAnnotate(), actual.getMetricsToAnnotate());
		assertEquals(expected.getTriggers(), actual.getTriggers());
		assertEquals(expected.getCooldownExpirationMap(), actual.getCooldownExpirationMap());
		assertEquals(expected.getActiveStatusMap(), actual.getActiveStatusMap());
	}

	private void _assertEquals(Trigger expected, Trigger actual) {
		assertEquals(expected.getId(), actual.getId());
		assertEquals(expected.getName(), actual.getName());
		assertEquals(expected.getType(), actual.getType());
		assertEquals(expected.getThreshold(), actual.getThreshold());
		assertEquals(expected.getSecondaryThreshold(), actual.getSecondaryThreshold());
		assertEquals(expected.getInertia(), actual.getInertia());
	}
	
	@Test
	public void testUpdateNotification() {
		UserService userService = system.getServiceFactory().getUserService();
		AlertService alertService = system.getServiceFactory().getAlertService();
		
		Alert expected = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert-name", EXPRESSION, "* * * * *");
		Notification notification = new Notification("notification", expected, "notifier-name", new ArrayList<String>(), 5000L);
		Trigger trigger = new Trigger(expected, TriggerType.GREATER_THAN, "trigger-name", 0.95, 60000);

		notification.setAlert(expected);
		expected.setNotifications(Arrays.asList(new Notification[] { notification }));
		expected.setTriggers(Arrays.asList(new Trigger[] { trigger }));
		assertTrue(!expected.isEnabled());
		expected = alertService.updateAlert(expected);
		
		notification = expected.getNotifications().get(0);
		trigger = expected.getTriggers().get(0);
		
		notification.setActiveForTriggerAndMetric(trigger, new Metric("s", "m"), false);
		notification.setCooldownExpirationByTriggerAndMetric(trigger, new Metric("s", "m"), 0);
		expected = alertService.updateAlert(expected);

		expected.getNotifications().get(0).getActiveStatusMap().clear();
		expected.getNotifications().get(0).getCooldownExpirationMap().clear();
		alertService.updateNotificationsActiveStatusAndCooldown(Arrays.asList(expected.getNotifications().get(0)));
		
		Notification n = expected.getNotifications().get(0);
		assertTrue(n.getActiveStatusMap().size() == 1);
		assertTrue(n.getCooldownExpirationMap().size() == 1);
	}
	
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

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

package com.salesforce.dva.argus.service.alert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.salesforce.dva.argus.TestUtils;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.entity.Trigger.TriggerType;
import com.salesforce.dva.argus.service.AlertService;
import com.salesforce.dva.argus.service.MQService;
import com.salesforce.dva.argus.service.ManagementService;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService.AlertWithTimestamp;
import com.salesforce.dva.argus.system.SystemMain;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.MethodRule;
import org.junit.rules.TestWatchman;
import org.junit.runners.model.FrameworkMethod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.persistence.EntityManager;
import java.io.IOException;
import java.sql.DriverManager;
import java.sql.SQLNonTransientConnectionException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.salesforce.dva.argus.service.MQService.MQQueue.ALERT;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;

public class AlertServiceTest{

	private static final String EXPRESSION =
			"DIVIDE(-1h:argus.jvm:file.descriptor.open{host=unknown-host}:avg, -1h:argus.jvm:file.descriptor.max{host=unknown-host}:avg)";


    private  SystemMain system;
    private  PrincipalUser admin;
    private  AlertService alertService;
    private  UserService userService;
    private  MQService mqService;
    private  ManagementService managementService;
    final Logger logger = LoggerFactory.getLogger(getClass());
    private EntityManager em;


    private static ch.qos.logback.classic.Logger apacheLogger;
    private static ch.qos.logback.classic.Logger myClassLogger;

    @Rule public MethodRule watchman = new TestWatchman() {
            public void starting(FrameworkMethod method) {
                logger.info("now running {}", method.getName());
            }
        };


    @BeforeClass
    static public void setUpClass() {
        myClassLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("com.salesforce.dva.argus.service.alert.AlertServiceTest");
        myClassLogger.setLevel(ch.qos.logback.classic.Level.INFO);
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
			mqService = system.getServiceFactory().getMQService();
			managementService = system.getServiceFactory().getManagementService();
			alertService.findAllAlerts(false).forEach(a -> alertService.deleteAlert(a));
            try {
                Class.forName("org.apache.derby.jdbc.EmbeddedDriver");
                DriverManager.getConnection("jdbc:derby:memory:argus;create=true").close();
                em = mock(EntityManager.class);

            } catch (Exception ex) {
                LoggerFactory.getLogger(getClass()).error("Exception in setUp:{}", ex.getMessage());
                fail("Exception during database startup.");
            }



	}

    @After
    public void tearDown() {
		alertService.findAllAlerts(false).forEach(a -> alertService.deleteAlert(a));
		if (system != null) {
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

    // Should be used to mock EntityManager
//    void mockQuery(String nameQuery, Long outputValue) {
//		TypedQuery<Long> mockedQuery = mock(TypedQuery.class);
//		when(mockedQuery.setHint(anyString(), any())).thenReturn(mockedQuery);
//		when(mockedQuery.getSingleResult()).thenReturn(outputValue);
//		when(em.createNamedQuery(nameQuery, Long.class)).thenReturn(mockedQuery);
//		when(em.createNamedQuery(anyString())).thenReturn(mockedQuery);
//	}
//
//	void mockQuery(String nameQuery, List<Alert> outputValue) {
//		TypedQuery<Alert> mockedQuery = mock(TypedQuery.class);
//		CriteriaBuilder cb = mock(CriteriaBuilder.class);
//		CriteriaQuery<Tuple> cq = mock(CriteriaQuery.class);
//		TypedQuery<Tuple> mockedQuery2 = mock(TypedQuery.class);
//
//		when(mockedQuery.setHint(anyString(), any())).thenReturn(mockedQuery);
//		when(mockedQuery.getResultList()).thenReturn(outputValue);
//		when(mockedQuery2.setHint(anyString(), any())).thenReturn(mockedQuery2);
//		//when(mockedQuery2.getResultList()).thenReturn(outputValue);
//
//		when(cb.createTupleQuery()).thenReturn(cq);
//
//		when(em.getCriteriaBuilder()).thenReturn(cb);
//		when(em.createQuery(cq)).thenReturn(mockedQuery2);
//		when(em.createNamedQuery(nameQuery, Alert.class)).thenReturn(mockedQuery);
//		when(em.createNamedQuery(anyString())).thenReturn(mockedQuery);
//	}


	@Test
	public void testUpdateAlert() {
        String alertName = "alertname-" + TestUtils.createRandomName();
		Alert expected = new Alert(userService.findAdminUser(), userService.findAdminUser(), alertName, EXPRESSION, "* * * * *");
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
            String alertName = "alertname-" + TestUtils.createRandomName();

		Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), alertName, EXPRESSION, "* * * * *");
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
		String alertName = "testAlert";
		String userName = TestUtils.createRandomName();
                PrincipalUser expectedUser = new PrincipalUser(admin, userName, "testuser@testcompany.com");
		Alert expectedAlert = new Alert(expectedUser, expectedUser, alertName, EXPRESSION, "* * * * *");

		expectedAlert = alertService.updateAlert(expectedAlert);

		Alert actualAlert = alertService.findAlertByNameAndOwner(alertName, expectedAlert.getOwner());

		assertNotNull(actualAlert);
		assertEquals(expectedAlert.getId(), actualAlert.getId());
	}

	@Test
	public void testfindAlertsByOwner() {
		String userName = TestUtils.createRandomName();
		int alertsCount = TestUtils.random.nextInt(20) + 1;
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
		String userName = TestUtils.createRandomName();
		int alertsCount = TestUtils.random.nextInt(20) + 1;
		PrincipalUser user = new PrincipalUser(admin, userName, userName + "@testcompany.com");

		user = userService.updateUser(user);

		List<Alert> expectedAlerts = new ArrayList<>();

		for (int i = 0; i < alertsCount; i++) {
			expectedAlerts.add(alertService.updateAlert(new Alert(user, user, "alert_" + i, EXPRESSION, "* * * * *")));
		}

		List<Alert> actualAlerts = alertService.findAlertsByOwner(user, true);

		assertEquals(actualAlerts.size(), expectedAlerts.size());
		//mockQuery("Alert.findByOwner", expectedAlerts);
		//assertEquals(expectedAlerts, Alert.findByOwnerMeta(em, user));

		Set<Alert> actualSet = new HashSet<>();

		actualSet.addAll(actualAlerts);
		for (Alert alert : expectedAlerts) {
			assertTrue(actualSet.contains(alert));
		}
	}

	@Test
	public void testFindAlertsByOwnerPaged() {
		String userName = TestUtils.createRandomName();
		int alertsCount = 25;
		PrincipalUser user = new PrincipalUser(admin, userName, userName + "@testcompany.com");

		user = userService.updateUser(user);

		List<Alert> expectedAlerts = new ArrayList<>();

		for (int i = 0; i < alertsCount; i++) {
			expectedAlerts.add(alertService.updateAlert(new Alert(user, user, "alert_" + i, EXPRESSION, "* * * * *")));
		}

		int limit = 10; // Page size
		List<Alert> actualAlerts = new ArrayList<>();

		// Fetch first page
		List<Alert> page = alertService.findAlertsByOwnerPaged(user, limit, 0, null, null, null);
		assertEquals(page.size(), limit);
		actualAlerts.addAll(page);

		// Fetch second page
		page = alertService.findAlertsByOwnerPaged(user, limit, actualAlerts.size(), null, null, null);
		assertEquals(page.size(), limit);
		actualAlerts.addAll(page);

		// Fetch remaining alerts (less than a page)
		page = alertService.findAlertsByOwnerPaged(user, limit, actualAlerts.size(), null, null, null);
		assertEquals(page.size(), expectedAlerts.size() - actualAlerts.size());
		actualAlerts.addAll(page);

		// Try to fetch again should be empty result
		page = alertService.findAlertsByOwnerPaged(user, limit, actualAlerts.size(), null, null, null);
		assertEquals(0, page.size());

		Set<Alert> actualSet = new HashSet<>();

		actualSet.addAll(actualAlerts);
		for (Alert alert : expectedAlerts) {
			assertTrue(actualSet.contains(alert));
		}
	}

	@Test
	public void testFindAlertsByOwnerPagedWithSearchText() {
		String userName = TestUtils.createRandomName();
		int alertsCount = 25;
		PrincipalUser user = new PrincipalUser(admin, userName, userName + "@testcompany.com");

		user = userService.updateUser(user);

		List<Alert> expectedAlerts = new ArrayList<>();
		List<Alert> expectedEvenAlerts = new ArrayList<>();
		List<Alert> expectedOddAlerts = new ArrayList<>();

		for (int i = 0; i < alertsCount; i++) {
			if (i % 2 == 0) {
				Alert evenAlert = alertService.updateAlert(new Alert(user, user, "even_alert_" + i, EXPRESSION, "* * * * *"));
				expectedAlerts.add(evenAlert);
				expectedEvenAlerts.add(evenAlert);
			} else {
				Alert oddAlert = alertService.updateAlert(new Alert(user, user, "odd_alert_" + i, EXPRESSION, "* * * * *"));
				expectedAlerts.add(oddAlert);
				expectedOddAlerts.add(oddAlert);
			}
		}

		// ==================================================
		// Test search by owner's name
		// ==================================================
		int limit = 10; // Page size
		List<Alert> actualAlerts = new ArrayList<>();

		// Fetch first page
		List<Alert> page = alertService.findAlertsByOwnerPaged(user, limit, 0, userName, null, null);
		assertEquals(page.size(), limit);
		actualAlerts.addAll(page);

		// Fetch with invalid owner's name should be empty
		page = alertService.findAlertsByOwnerPaged(user, limit, 0, "invalid_owner", null, null);
		assertEquals(page.size(), 0);

		// Fetch second page
		page = alertService.findAlertsByOwnerPaged(user, limit, actualAlerts.size(), userName, null, null);
		assertEquals(page.size(), limit);
		actualAlerts.addAll(page);

		// Fetch remaining alerts (less than a page)
		page = alertService.findAlertsByOwnerPaged(user, limit, actualAlerts.size(), userName, null, null);
		assertEquals(page.size(), expectedAlerts.size() - actualAlerts.size());
		actualAlerts.addAll(page);

		// Try to fetch again should be empty result
		page = alertService.findAlertsByOwnerPaged(user, limit, actualAlerts.size(), userName, null, null);
		assertEquals(0, page.size());

		Set<Alert> actualSet = new HashSet<>();

		actualSet.addAll(actualAlerts);
		for (Alert alert : expectedAlerts) {
			assertTrue(actualSet.contains(alert));
		}

		// ==================================================
		// Test search by alerts name
		// ==================================================

		List<Alert> actualEvenAlerts = new ArrayList<>();

		// Fetch with invalid alert name should be empty
		page = alertService.findAlertsByOwnerPaged(user, limit, 0, "invalid_alert_name", null, null);
		assertEquals(page.size(), 0);

		// Fetch first page of even number alerts
		page = alertService.findAlertsByOwnerPaged(user, limit, 0, "e*eN", null, null);
		assertEquals(page.size(), limit);
		actualEvenAlerts.addAll(page);

		// Fetch first page of even number alerts case insensitive
		page = alertService.findAlertsByOwnerPaged(user, limit, 0, "EvEn", null, null);
		assertEquals(page.size(), limit);

		// Fetch second page of even number alerts (less than a page)
		page = alertService.findAlertsByOwnerPaged(user, limit, actualEvenAlerts.size(), "even", null, null);
		assertEquals(page.size(), expectedEvenAlerts.size() - actualEvenAlerts.size());
		actualEvenAlerts.addAll(page);

		// Try to fetch again should be empty result
		page = alertService.findAlertsByOwnerPaged(user, limit, actualEvenAlerts.size(), "even", null, null);
		assertEquals(0, page.size());

		Set<Alert> actualEvenSet = new HashSet<>();

		actualEvenSet.addAll(actualEvenAlerts);
		for (Alert alert : expectedEvenAlerts) {
			assertTrue(actualEvenSet.contains(alert));
		}

		page = alertService.findAlertsByOwnerPaged(user, limit, 0, "O*d", null, null);
        assertEquals(limit, page.size());
	}

	@Test
	public void testFindAlertsByOwnerPagedWithSorting() {
            String userName = TestUtils.createRandomName();
		PrincipalUser user1 = userService.updateUser(new PrincipalUser(admin, userName, userName + "test1@salesforce.com"));

		Alert alert1 = alertService.updateAlert(new Alert(user1, user1, "alert1", EXPRESSION, "* * * * *"));
		try{
			Thread.sleep(1000);
		}catch(Exception e) {
		}
		Alert alert2 = alertService.updateAlert(new Alert(user1, user1, "alert2", EXPRESSION, "* * * * *"));
		try{
			Thread.sleep(1000);
		}catch(Exception e) {
		}
		Alert alert3 = alertService.updateAlert(new Alert(user1, user1, "alert3", EXPRESSION, "* * * * *"));
		try{
			Thread.sleep(1000);
		}catch(Exception e) {
		}

		//Change modified date
		alert1.setShared(true);
		alertService.updateAlert(alert1);
		try{
			Thread.sleep(1000);
		}catch(Exception e) {
		}

		//sort by alert name ascending
		List<Alert> page = alertService.findAlertsByOwnerPaged(user1, 10, 0, null, "name", "ASC");
		assertEquals(alert1.getName(), page.get(0).getName());
		assertEquals(alert2.getName(), page.get(1).getName());
		assertEquals(alert3.getName(), page.get(2).getName());

		//sort by alert name descending
		page = alertService.findAlertsByOwnerPaged(user1, 10, 0, null, "name", "DESC");
		assertEquals(alert3.getName(), page.get(0).getName());
		assertEquals(alert2.getName(), page.get(1).getName());
		assertEquals(alert1.getName(), page.get(2).getName());

		//sort by create date ascending
		page = alertService.findAlertsByOwnerPaged(user1, 10, 0, null, "createdDate", "ASC");
		assertEquals(alert1.getName(), page.get(0).getName());
		assertEquals(alert2.getName(), page.get(1).getName());
		assertEquals(alert3.getName(), page.get(2).getName());

		//sort by create date descending
		page = alertService.findAlertsByOwnerPaged(user1, 10, 0, null, "createdDate", "DESC");
		assertEquals(alert3.getName(), page.get(0).getName());
		assertEquals(alert2.getName(), page.get(1).getName());
		assertEquals(alert1.getName(), page.get(2).getName());

		//sort by modified date ascending
		page = alertService.findAlertsByOwnerPaged(user1, 10, 0, null, "modifiedDate", "ASC");
		assertEquals(alert2.getName(), page.get(0).getName());
		assertEquals(alert3.getName(), page.get(1).getName());
		assertEquals(alert1.getName(), page.get(2).getName());

		//sort by modified date descending
		page = alertService.findAlertsByOwnerPaged(user1, 10, 0, null, "modifiedDate", "DESC");
		assertEquals(alert1.getName(), page.get(0).getName());
		assertEquals(alert3.getName(), page.get(1).getName());
		assertEquals(alert2.getName(), page.get(2).getName());

		//invalid column
		try {
			page = alertService.findAlertsByOwnerPaged(user1, 10, 0, null, "invalidColumn", "DESC");
		} catch (IllegalArgumentException ex){
			assertNotNull(ex);
		}
		try {
			page = alertService.findAlertsByOwnerPaged(user1, 10, 0, null, "", "DESC");
		} catch (IllegalArgumentException ex){
			assertNotNull(ex);
		}
	}

	@Test
	public void testCountAlertsByOwner() {
		String userName = TestUtils.createRandomName();
		int alertsCount = TestUtils.random.nextInt(20) + 1;
		PrincipalUser user = new PrincipalUser(admin, userName, userName + "@testcompany.com");

		user = userService.updateUser(user);

		List<Alert> expectedAlerts = new ArrayList<>();

		for (int i = 0; i < alertsCount; i++) {
			expectedAlerts.add(alertService.updateAlert(new Alert(user, user, "alert_" + i, EXPRESSION, "* * * * *")));
		}

		AlertsCountContext context = new AlertsCountContext.AlertsCountContextBuilder().countUserAlerts().setPrincipalUser(user).build();
		int cnt = alertService.countAlerts(context);

		assertEquals(cnt, expectedAlerts.size());
		//mockQuery("Alert.countByOwner", Long.valueOf(expectedAlerts.size()));
		//assertEquals(expectedAlerts.size(), Alert.countByOwner(em, user, null));
	}

	@Test
	public void testCountAlertsByOwnerWithSearchText() {
    	String namedQuery = "Alert.countByOwnerWithSearchText";
		String userName = TestUtils.createRandomName();
		int alertsCount = TestUtils.random.nextInt(20) + 1;
		PrincipalUser user = new PrincipalUser(admin, userName, userName + "@testcompany.com");

		user = userService.updateUser(user);

		List<Alert> expectedAlerts = new ArrayList<>();
		List<Alert> expectedEvenAlerts = new ArrayList<>();
		List<Alert> expectedOddAlerts = new ArrayList<>();

		for (int i = 0; i < alertsCount; i++) {
			if (i % 2 == 0) {
				Alert evenAlert = alertService.updateAlert(new Alert(user, user, "another_even_alert_" + i, EXPRESSION, "* * * * *"));
				expectedEvenAlerts.add(evenAlert);
				expectedAlerts.add(evenAlert);
			} else {
				Alert oddAlert = alertService.updateAlert(new Alert(user, user, "another_odd_alert_" + i, EXPRESSION, "* * * * *"));
				expectedOddAlerts.add(oddAlert);
				expectedAlerts.add(oddAlert);
			}
		}

		// Filter on user name
		AlertsCountContext context = new AlertsCountContext.AlertsCountContextBuilder().countUserAlerts().setPrincipalUser(user).setSearchText(userName).build();
		int cnt = alertService.countAlerts(context);
		assertEquals(cnt, expectedAlerts.size());
		//mockQuery(namedQuery, Long.valueOf(expectedAlerts.size()));
		//assertEquals(expectedAlerts.size(), Alert.countByOwner(em, user, userName));


		// Count alerts have "even" in its name
		context = new AlertsCountContext.AlertsCountContextBuilder().countUserAlerts().setPrincipalUser(user).setSearchText("even").build();
		cnt = alertService.countAlerts(context);
		assertEquals(cnt, expectedEvenAlerts.size());
		//mockQuery(namedQuery, Long.valueOf(expectedEvenAlerts.size()));
		//assertEquals(expectedEvenAlerts.size(), Alert.countByOwner(em, user, "even"));

		// Count alerts have "even" in its name case insensitive
		context = new AlertsCountContext.AlertsCountContextBuilder().countUserAlerts().setPrincipalUser(user).setSearchText("EvEn").build();
		cnt = alertService.countAlerts(context);
		assertEquals(cnt, expectedEvenAlerts.size());

		// Count alerts have "odd" in its name
		context = new AlertsCountContext.AlertsCountContextBuilder().countUserAlerts().setPrincipalUser(user).setSearchText("odd").build();
		cnt = alertService.countAlerts(context);
		assertEquals(cnt, expectedOddAlerts.size());

		// Count alerts have "odd" in its name  case insensitive
		context = new AlertsCountContext.AlertsCountContextBuilder().countUserAlerts().setPrincipalUser(user).setSearchText("OdD").build();
		cnt = alertService.countAlerts(context);
		assertEquals(cnt, expectedOddAlerts.size());
		//mockQuery(namedQuery, Long.valueOf(expectedOddAlerts.size()));
		//assertEquals(expectedOddAlerts.size(), Alert.countByOwner(em, user, "OdD"));

		// Invalid alert name
		context = new AlertsCountContext.AlertsCountContextBuilder().countUserAlerts().setPrincipalUser(user).setSearchText("invalid_alert_name").build();
		cnt = alertService.countAlerts(context);
		assertEquals(cnt, 0);

		// Test with wildcard expressions.
		context = new AlertsCountContext.AlertsCountContextBuilder().countUserAlerts().setPrincipalUser(user).setSearchText("E*n").build();
		cnt = alertService.countAlerts(context);
		assertEquals(cnt, expectedEvenAlerts.size());

		// Test with wildcard expressions.
		context = new AlertsCountContext.AlertsCountContextBuilder().countUserAlerts().setPrincipalUser(user).setSearchText("o*D").build();
		cnt = alertService.countAlerts(context);
		assertEquals(cnt, expectedOddAlerts.size());

		context = new AlertsCountContext.AlertsCountContextBuilder().countUserAlerts().setPrincipalUser(user).setSearchText(userName.replace("-","*")).build();
		cnt = alertService.countAlerts(context);
	}

	@Test
	public void findAllAlerts() {
		String userName = TestUtils.createRandomName();
		int alertsCount = TestUtils.random.nextInt(100) + 1;
		PrincipalUser user = new PrincipalUser(admin, userName, userName + "@testcompany.com");

		user = userService.updateUser(user);

		List<Alert> expectedAlerts = new ArrayList<>();

		for (int i = 0; i < alertsCount; i++) {
			expectedAlerts.add(alertService.updateAlert(new Alert(user, user, "alert_" + i, EXPRESSION, "* * * * *")));
		}

		List<Alert> actualAlerts = alertService.findAllAlerts(false);

		//mockQuery("Alert.findAll", expectedAlerts);
		//assertEquals(expectedAlerts, Alert.findAll(em));

		assertEquals(actualAlerts.size(), expectedAlerts.size());

		Set<Alert> actualSet = new HashSet<>();

		actualSet.addAll(actualAlerts);
		for (Alert alert : expectedAlerts) {
			assertTrue(actualSet.contains(alert));
		}
	}

	@Test
	public void testFindAllAlertsMeta() {
		String userName = TestUtils.createRandomName();
		int alertsCount = TestUtils.random.nextInt(100) + 1;
		PrincipalUser user = new PrincipalUser(admin, userName, userName + "@testcompany.com");

		user = userService.updateUser(user);

		List<Alert> expectedAlerts = new ArrayList<>();

		for (int i = 0; i < alertsCount; i++) {
			expectedAlerts.add(alertService.updateAlert(new Alert(user, user, "alert_" + i, EXPRESSION, "* * * * *")));
		}

		List<Alert> actualAlerts = alertService.findAllAlerts(true);

		assertEquals(actualAlerts.size(), expectedAlerts.size());
		//mockQuery("Alert.findAll", expectedAlerts);
		//assertEquals(expectedAlerts, Alert.findAllMeta(em));

		Set<Alert> actualSet = new HashSet<>();

		actualSet.addAll(actualAlerts);
		for (Alert alert : expectedAlerts) {
			assertTrue(actualSet.contains(alert));
		}
	}

	@Test
	public void findAlertsInRange() {
		String userName = TestUtils.createRandomName();
		int alertsCount = 50;
		PrincipalUser user = new PrincipalUser(admin, userName, userName + "@testcompany.com");

		user = userService.updateUser(user);

		List<Alert> insertedAlerts = new ArrayList<>();

		for (int i = 0; i < alertsCount; i++) {
			Alert a = alertService.updateAlert(new Alert(user, user, "alert_" + i, EXPRESSION, "* * * * *"));
			insertedAlerts.add(a);
		}

		List<Alert> expectedAlerts = insertedAlerts.subList(0, 20);

		List<Alert> actualAlerts = alertService.findAlertsByRangeAndStatus(insertedAlerts.get(0).getId(), insertedAlerts.get(19).getId(), false);

		assertEquals(20, actualAlerts.size());

		Set<Alert> actualSet = new HashSet<>();

		actualSet.addAll(actualAlerts);
		for (Alert alert : expectedAlerts) {
			assertTrue(actualSet.remove(alert));
		}

		assertEquals(actualSet.size(), 0);
	}

	@Test
	public void findAlertsModifiedAfterDate() {
		String userName = TestUtils.createRandomName();
		PrincipalUser user = new PrincipalUser(admin, userName, userName + "@testcompany.com");

		user = userService.updateUser(user);

		List<Alert> insertedAlerts = new ArrayList<>();

		for (int i = 0; i < 25; i++) {
			insertedAlerts.add(alertService.updateAlert(new Alert(user, user, "alert_" + i, EXPRESSION, "* * * * *")));
		}

		try {
			Thread.sleep(5000);
		}catch(Exception e) {

		}

		Date currentDate = new Date();
		for (int i = 25; i < 50; i++) {
			insertedAlerts.add(alertService.updateAlert(new Alert(user, user, "alert_" + i, EXPRESSION, "* * * * *")));
		}

		List<Alert> expectedAlerts = insertedAlerts.subList(25, 50);

		List<Alert> actualAlerts = alertService.findAlertsModifiedAfterDate(currentDate);

		assertEquals(actualAlerts.size(), 25);

		Set<Alert> actualSet = new HashSet<>();

		actualSet.addAll(actualAlerts);
		for (Alert alert : expectedAlerts) {
			assertTrue(actualSet.remove(alert));
		}
		assertEquals(actualSet.size(), 0);
	}

	@Test
	public void findFullAlertObjectRetrieval() {
		String userName = TestUtils.createRandomName();

		PrincipalUser user = new PrincipalUser(admin, userName, userName + "@testcompany.com");

		user = userService.updateUser(user);

		Alert alert1 = new Alert(user, user, "alert_1", EXPRESSION, "* * * * *");
		Notification not1 = new Notification("notification_1", alert1, "com.salesforce.dva.argus.service.alert.notifier.AuditNotifier", null, 5000);
		Notification not2 = new Notification("notification_2", alert1, "com.salesforce.dva.argus.service.alert.notifier.AuditNotifier", null, 5000);
		Trigger trig1 = new Trigger(alert1, TriggerType.LESS_THAN, "trigger_1", 20, 0);
		Trigger trig2 = new Trigger(alert1, TriggerType.LESS_THAN, "trigger_2", 40, 0);
		alert1.setNotifications(Arrays.asList(new Notification[] {not1, not2}));
		alert1.setTriggers(Arrays.asList(new Trigger[] {trig1, trig2}));
		not1.setTriggers(Arrays.asList(new Trigger[] {trig1}));
		not1.setSubscriptions(Arrays.asList(new String[] {"subscription_1"}));
		not1.setMetricsToAnnotate(Arrays.asList(new String[] {"logbus.was.SP3.cs21:Logstash.rate_15m:sum"}));
		Metric sampleMetric = new Metric("argus", "cpu");
		long currTime = System.currentTimeMillis();
		not1.setCooldownExpirationByTriggerAndMetric(trig1, sampleMetric, currTime);
		not1.setActiveForTriggerAndMetric(trig1, sampleMetric, true);
		alertService.updateAlert(alert1);

		Alert alert2 = new Alert(user, user, "alert_2", EXPRESSION, "* * * * *");
		alertService.updateAlert(alert2);

		Alert alert3 = new Alert(user, user, "alert_3", EXPRESSION, "* * * * *");
		Trigger trig3 = new Trigger(alert3, TriggerType.LESS_THAN, "trigger_3", 40, 0);
		alert3.setTriggers(Arrays.asList(new Trigger[] {trig3}));
		alertService.updateAlert(alert3);

		List<Alert> actualAlerts = alertService.findAlertsByStatus(false);

		assertEquals(actualAlerts.size(), 3);
		Alert fetchedAlert1 = actualAlerts.get(0);
		assertEquals(fetchedAlert1.getName(), "alert_1");
		assertEquals(fetchedAlert1.getNotifications().size(), 2);
		assertEquals(fetchedAlert1.getTriggers().size(), 2);

		Map<String, Notification> notificationsMap = fetchedAlert1.getNotifications().stream().collect(Collectors.toMap(x -> x.getName(), x -> x));
		Map<String, Trigger> triggersMap = fetchedAlert1.getTriggers().stream().collect(Collectors.toMap(x -> x.getName(), x -> x));
		assertTrue(notificationsMap.containsKey("notification_1"));
		assertTrue(notificationsMap.containsKey("notification_2"));
		assertTrue(triggersMap.containsKey("trigger_1"));
		assertTrue(triggersMap.containsKey("trigger_2"));
		assertEquals(notificationsMap.get("notification_1").getTriggers().size(), 1);
		assertEquals(notificationsMap.get("notification_2").getTriggers().size(), 0);
		assertEquals(triggersMap.get("trigger_1").getNotifications().size(), 1);
		assertEquals(triggersMap.get("trigger_2").getNotifications().size(), 0);
		assertEquals(notificationsMap.get("notification_1").getTriggers().get(0).getName(), "trigger_1");
		assertEquals(triggersMap.get("trigger_1").getNotifications().get(0).getName(), "notification_1");
		Notification fetchedNotification1 = notificationsMap.get("notification_1");
		assertEquals(fetchedNotification1.getSubscriptions().size(),1);
		assertEquals(fetchedNotification1.getSubscriptions().get(0),"subscription_1");
		assertEquals(fetchedNotification1.getMetricsToAnnotate().size(),1);
		assertEquals(fetchedNotification1.getMetricsToAnnotate().get(0),"logbus.was.SP3.cs21:Logstash.rate_15m:sum");
		assertEquals(fetchedNotification1.getCooldownExpirationByTriggerAndMetric(trig1, sampleMetric), currTime);
		assertEquals(fetchedNotification1.getActiveStatusMap().keySet().size(), 1);

		Alert fetchedAlert2 = actualAlerts.get(1);
		assertEquals(fetchedAlert2.getName(), "alert_2");
		assertEquals(fetchedAlert2.getNotifications().size(), 0);
		assertEquals(fetchedAlert2.getTriggers().size(), 0);

		Alert fetchedAlert3 = actualAlerts.get(2);
		assertEquals(fetchedAlert3.getName(), "alert_3");
		assertEquals(fetchedAlert3.getNotifications().size(), 0);
		assertEquals(fetchedAlert3.getTriggers().size(), 1);
		assertEquals(fetchedAlert3.getTriggers().get(0).getName(), "trigger_3");
	}

	@Test
	public void testAlertDelete() {
		PrincipalUser user = userService.findAdminUser();
		String alertName = TestUtils.createRandomName();
		Alert expectedAlert = new Alert(user, user, alertName, EXPRESSION, "* * * * *");

		expectedAlert = alertService.updateAlert(expectedAlert);
		alertService.deleteAlert(expectedAlert);
		assertNull(alertService.findAlertByNameAndOwner(alertName, user));
	}

	@Test
	public void testDeletedTriggersInNotifications() {
            String alertName = "alertname-" + TestUtils.createRandomName();

		Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), alertName, EXPRESSION, "* * * * *");
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
		PrincipalUser user = userService.findAdminUser();
		String alertName = TestUtils.createRandomName();
		Alert alert = new Alert(user, user, alertName, EXPRESSION, "* * * * *");

		alert = alertService.updateAlert(alert);
		alertService.markAlertForDeletion(alert);
		assertNull(alertService.findAlertByNameAndOwner(alertName, user));
		alert = new Alert(user, user, alertName, EXPRESSION, "* * * * *");
		alert = alertService.updateAlert(alert);
		assertNotNull((alertService.findAlertByNameAndOwner(alertName, user)));
	}

    @Ignore("re-do this test, it tests things similar to defaultalertservicetest and mqservicetest,and takes longer")
	@Test
	public void testAlertEnqueue() {

		PrincipalUser user = userService.findAdminUser();
		List<Alert> actualAlertList = new ArrayList<>();

                int count=5;
		for (int i = 0; i < count; i++) {
			actualAlertList.add(alertService.updateAlert(new Alert(user, user, TestUtils.createRandomName(), EXPRESSION, "* * * * *")));
		}
		alertService.enqueueAlerts(actualAlertList);
                List<AlertWithTimestamp> expectedList = mqService.dequeue(ALERT.getQueueName(), AlertWithTimestamp.class, 10000, count);

                assertEquals(actualAlertList.size(), expectedList.size());
	}

	@Test
	public void testSharedAlertWhenOneSharedAlert() {
            String userName = TestUtils.createRandomName();
		PrincipalUser user1 = userService.updateUser(new PrincipalUser(admin, userName, userName+ "test1@salesforce.com"));

                String alertName1 = "alertname-" + TestUtils.createRandomName();

		alertService.updateAlert(new Alert(user1, user1, alertName1, EXPRESSION, "* * * * *"));
                String alertName2 = "alertname-" + TestUtils.createRandomName();
		Alert alertShared = alertService.updateAlert(new Alert(user1, user1, alertName2, EXPRESSION, "* * * * *"));

		alertShared.setShared(true);
		alertService.updateAlert(alertShared);

		List<Alert> expectedSharedResult = new ArrayList<>();
		expectedSharedResult.add(alertShared);
		List<Alert> actualResult=alertService.findSharedAlerts(false, null, null);
		assertEquals(expectedSharedResult, actualResult);
	}

	@Test
	public void testSharedAlertWhenTwoSharedAlert() {
            String userName1 = TestUtils.createRandomName();
            String userName2 = TestUtils.createRandomName();
		PrincipalUser user1 = userService.updateUser(new PrincipalUser(admin, userName1, userName1 + "test1@salesforce.com"));
		PrincipalUser user2 = userService.updateUser(new PrincipalUser(admin, userName2, userName2 + "test2@salesforce.com"));

                String alertName1 = "alertname-" + TestUtils.createRandomName();
		Alert alertSharedUser1 = alertService.updateAlert(new Alert(user1, user1, alertName1, EXPRESSION, "* * * * *"));
                String alertName2 = "alertname-" + TestUtils.createRandomName();
		Alert alertSharedUser2 = alertService.updateAlert(new Alert(user2, user2, alertName2, EXPRESSION, "* * * * *"));

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
            String userName1 = TestUtils.createRandomName();
            String userName2 = TestUtils.createRandomName();
		PrincipalUser user1 = userService.updateUser(new PrincipalUser(admin, userName1, userName1 + "test1@salesforce.com"));
		PrincipalUser user2 = userService.updateUser(new PrincipalUser(admin, userName2, userName2 + "test2@salesforce.com"));

                String alertName1 = "alertname-" + TestUtils.createRandomName();
		Alert alertSharedUser1 = alertService.updateAlert(new Alert(user1, user1, alertName1, EXPRESSION, "* * * * *"));
                String alertName2 = "alertname-" + TestUtils.createRandomName();
		Alert alertSharedUser2 = alertService.updateAlert(new Alert(user2, user2, alertName2, EXPRESSION, "* * * * *"));

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
	public void testFindSharedAlertsMetaPaged() {
            String userName1 = TestUtils.createRandomName();
            String userName2 = TestUtils.createRandomName();
		PrincipalUser user1 = userService.updateUser(new PrincipalUser(admin, userName1, userName1 + "test1@salesforce.com"));
		PrincipalUser user2 = userService.updateUser(new PrincipalUser(admin, userName2, userName2 + "test2@salesforce.com"));

		Alert alert1 = alertService.updateAlert(new Alert(user1, user1, "alert1", EXPRESSION, "* * * * *"));
		Alert alert2 = alertService.updateAlert(new Alert(user2, user2, "alert2", EXPRESSION, "* * * * *"));
		Alert alert3 = alertService.updateAlert(new Alert(user2, user2, "alert3", EXPRESSION, "* * * * *"));

		alert1.setShared(true);
		alert1.setMissingDataNotificationEnabled(true);
		alertService.updateAlert(alert1);
		alert2.setShared(true);
		alert2.setMissingDataNotificationEnabled(false);
		alertService.updateAlert(alert2);
		alert3.setShared(false);
		alertService.updateAlert(alert3);


		Set<String> sharedAlerts = new HashSet<>();
		sharedAlerts.add("alert1");
		sharedAlerts.add("alert2");

		// First page
		List<Alert> page = alertService.findSharedAlertsPaged(1, 0, null, null, null);
		assertEquals(1, page.size());
		assertTrue(sharedAlerts.contains(page.get(0).getName()));
		assertEquals(true, page.get(0).isMissingDataNotificationEnabled());

		// Second page
		page = alertService.findSharedAlertsPaged(1, 1, null, null, null);
		assertEquals(1, page.size());
		assertTrue(sharedAlerts.contains(page.get(0).getName()));
		assertEquals(page.get(0).isMissingDataNotificationEnabled(), false);

		// Thrid page should be zero
		page = alertService.findSharedAlertsPaged(1, 2, null, null, null);
		assertEquals(0, page.size());
	}

	@Test
	public void testFindSharedAlertsMetaPagedWithSearchText() {
            String userName1 = TestUtils.createRandomName();
            String userName2 = TestUtils.createRandomName();
		PrincipalUser user1 = userService.updateUser(new PrincipalUser(admin, userName1, userName1 + "test1@salesforce.com"));
		PrincipalUser user2 = userService.updateUser(new PrincipalUser(admin, userName2, userName2 + "test2@salesforce.com"));

		Alert alert1 = alertService.updateAlert(new Alert(user1, user1, "alert1", EXPRESSION, "* * * * *"));
		Alert alert2 = alertService.updateAlert(new Alert(user2, user2, "alert2", EXPRESSION, "* * * * *"));
		Alert alert3 = alertService.updateAlert(new Alert(user2, user2, "alert3", EXPRESSION, "* * * * *"));


		alert1.setShared(true);
		alertService.updateAlert(alert1);
		alert2.setShared(true);
		alertService.updateAlert(alert2);
		alert3.setShared(false);
		alertService.updateAlert(alert3);

		Set<String> sharedAlerts = new HashSet<>();
		sharedAlerts.add("alert1");
		sharedAlerts.add("alert2");

		// Search by owner name
		List<Alert> page = alertService.findSharedAlertsPaged(10, 0, userName1, null, null);
		assertEquals(1, page.size());
		assertTrue(userName1.equals(page.get(0).getOwner().getUserName()));

		// Search by owner name case insensitive
		page = alertService.findSharedAlertsPaged(10, 0, userName1.toUpperCase(), null, null);
		assertEquals(1, page.size());
		assertTrue(userName1.equals(page.get(0).getOwner().getUserName()));

		// Search by alert name
		page = alertService.findSharedAlertsPaged(10, 0, "alert2", null, null);
		assertEquals(1, page.size());
		assertTrue("alert2".equals(page.get(0).getName()));

		// Search by alert name case insensitive
		page = alertService.findSharedAlertsPaged(10, 0, "aLeRt2", null, null);
		assertEquals(1, page.size());
		assertTrue("alert2".equals(page.get(0).getName()));

		// Search private alert
		page = alertService.findSharedAlertsPaged(1, 2, "alert3", null, null);
		assertEquals(0, page.size());

		// Invalid search text
		page = alertService.findSharedAlertsPaged(1, 2, "invalid_search_text", null, null);
		assertEquals(0, page.size());
	}

	@Test
	public void testFindSharedAlertsMetaPagedWithSorting() {
            String userName1 = "test1-" + TestUtils.createRandomName();
            String userName2 = "test2-" + TestUtils.createRandomName();
            String userName3 = "test3-" + TestUtils.createRandomName();


		PrincipalUser user1 = userService.updateUser(new PrincipalUser(admin, userName1, userName1 + "test1@salesforce.com"));
		PrincipalUser user2 = userService.updateUser(new PrincipalUser(admin, userName2, userName2 + "test2@salesforce.com"));
		PrincipalUser user3 = userService.updateUser(new PrincipalUser(admin, userName3, userName3 + "test3@salesforce.com"));


		Alert alert1 = alertService.updateAlert(new Alert(user1, user1, "alert1", EXPRESSION, "* * * * *"));
		try{
			Thread.sleep(1000);
		}catch(Exception e) {
		}
		Alert alert2 = alertService.updateAlert(new Alert(user2, user2, "alert2", EXPRESSION, "* * * * *"));
		try{
			Thread.sleep(1000);
		}catch(Exception e) {
		}
		Alert alert3 = alertService.updateAlert(new Alert(user3, user3, "alert3", EXPRESSION, "* * * * *"));
		try{
			Thread.sleep(1000);
		}catch(Exception e) {
		}

		//Change modified date
		alert2.setShared(true);
		alertService.updateAlert(alert2);
		try{
			Thread.sleep(1000);
		}catch(Exception e) {
		}

		alert3.setShared(true);
		alertService.updateAlert(alert3);
		try{
			Thread.sleep(1000);
		}catch(Exception e) {
		}

		alert1.setShared(true);
		alertService.updateAlert(alert1);
		try{
			Thread.sleep(1000);
		}catch(Exception e) {
		}


		//sort by owner name ascending
		List<Alert> page = alertService.findSharedAlertsPaged(10, 0, null, "ownerName", "ASC");
		assertEquals(3, page.size());
		assertEquals(alert1.getName(), page.get(0).getName());
		assertEquals(alert2.getName(), page.get(1).getName());
		assertEquals(alert3.getName(), page.get(2).getName());

		//sort by owner name descending
		page = alertService.findSharedAlertsPaged(10, 0, null, "ownerName", "DESC");
		assertEquals(3, page.size());
		assertEquals(alert3.getName(), page.get(0).getName());
		assertEquals(alert2.getName(), page.get(1).getName());
		assertEquals(alert1.getName(), page.get(2).getName());

		//sort by alert name ascending
		page = alertService.findSharedAlertsPaged(10, 0, null, "name", "ASC");
		assertEquals(alert1.getName(), page.get(0).getName());
		assertEquals(alert2.getName(), page.get(1).getName());
		assertEquals(alert3.getName(), page.get(2).getName());

		//sort by alert name descending
		page = alertService.findSharedAlertsPaged(10, 0, null, "name", "DESC");
		assertEquals(alert3.getName(), page.get(0).getName());
		assertEquals(alert2.getName(), page.get(1).getName());
		assertEquals(alert1.getName(), page.get(2).getName());

		//sort by create date ascending
		page = alertService.findSharedAlertsPaged(10, 0, null, "createdDate", "ASC");
		assertEquals(alert1.getName(), page.get(0).getName());
		assertEquals(alert2.getName(), page.get(1).getName());
		assertEquals(alert3.getName(), page.get(2).getName());

		//sort by create date descending
		page = alertService.findSharedAlertsPaged(10, 0, null, "createdDate", "DESC");
		assertEquals(alert3.getName(), page.get(0).getName());
		assertEquals(alert2.getName(), page.get(1).getName());
		assertEquals(alert1.getName(), page.get(2).getName());

		//sort by modified date ascending
		page = alertService.findSharedAlertsPaged(10, 0, null, "modifiedDate", "ASC");
		assertEquals(alert2.getName(), page.get(0).getName());
		assertEquals(alert3.getName(), page.get(1).getName());
		assertEquals(alert1.getName(), page.get(2).getName());

		//sort by modified date descending
		page = alertService.findSharedAlertsPaged(10, 0, null, "modifiedDate", "DESC");
		assertEquals(alert1.getName(), page.get(0).getName());
		assertEquals(alert3.getName(), page.get(1).getName());
		assertEquals(alert2.getName(), page.get(2).getName());

		//invalid column
		try {
			page = alertService.findSharedAlertsPaged(10, 0, null, "invalidColumn", "DESC");
		} catch (IllegalArgumentException ex){
			assertNotNull(ex);
		}
		try {
			page = alertService.findSharedAlertsPaged(10, 0, null, "", "DESC");
		} catch (IllegalArgumentException ex) {
			assertNotNull(ex);
		}
	}
	@Test
	public void testCountSharedAlertsMetaPaged() {
            String userName1 = TestUtils.createRandomName();
            String userName2 = TestUtils.createRandomName();
		PrincipalUser user1 = userService.updateUser(new PrincipalUser(admin, userName1, userName1 + "test1@salesforce.com"));
		PrincipalUser user2 = userService.updateUser(new PrincipalUser(admin, userName2, userName2 + "test2@salesforce.com"));

		Alert alert1 = alertService.updateAlert(new Alert(user1, user1, "alert1", EXPRESSION, "* * * * *"));
		Alert alert2 = alertService.updateAlert(new Alert(user2, user2, "alert2", EXPRESSION, "* * * * *"));
		Alert alert3 = alertService.updateAlert(new Alert(user2, user2, "alert3", EXPRESSION, "* * * * *"));


		alert1.setShared(true);
		alertService.updateAlert(alert1);
		alert2.setShared(true);
		alertService.updateAlert(alert2);
		alert3.setShared(false);
		alertService.updateAlert(alert3);

		AlertsCountContext context = new AlertsCountContext.AlertsCountContextBuilder().countSharedAlerts().build();
		assertEquals(2, alertService.countAlerts(context));
	}

	@Test
	public void testCountSharedAlertsMetaPagedWithSearchText() {
            String userName1 = TestUtils.createRandomName();
            String userName2 = TestUtils.createRandomName();
		PrincipalUser user1 = userService.updateUser(new PrincipalUser(admin, userName1, userName1 + "test1@salesforce.com"));
		PrincipalUser user2 = userService.updateUser(new PrincipalUser(admin, userName2, userName2 + "test2@salesforce.com"));

		Alert alert1 = alertService.updateAlert(new Alert(user1, user1, "alert1", EXPRESSION, "* * * * *"));
		Alert alert2 = alertService.updateAlert(new Alert(user2, user2, "alert2", EXPRESSION, "* * * * *"));
		Alert alert3 = alertService.updateAlert(new Alert(user2, user2, "alert3", EXPRESSION, "* * * * *"));


		alert1.setShared(true);
		alertService.updateAlert(alert1);
		alert2.setShared(true);
		alertService.updateAlert(alert2);
		alert3.setShared(false);
		alertService.updateAlert(alert3);

		AlertsCountContext context = new AlertsCountContext.AlertsCountContextBuilder().countSharedAlerts().setSearchText("alert").build();
		assertEquals(2, alertService.countAlerts(context));

		// count by alert name
		context = new AlertsCountContext.AlertsCountContextBuilder().countSharedAlerts().setSearchText("alert1").build();
		assertEquals(1, alertService.countAlerts(context));

		// count by alert name case insensitive
		context = new AlertsCountContext.AlertsCountContextBuilder().countSharedAlerts().setSearchText("aLeRt1").build();
		assertEquals(1, alertService.countAlerts(context));

		// count by user name
		context = new AlertsCountContext.AlertsCountContextBuilder().countSharedAlerts().setSearchText(userName1).build();
		assertEquals(1, alertService.countAlerts(context));

		// count by user name case insensitive
		context = new AlertsCountContext.AlertsCountContextBuilder().countSharedAlerts().setSearchText(userName1.toUpperCase()).build();
		assertEquals(1, alertService.countAlerts(context));

		// Invalid search text
		context = new AlertsCountContext.AlertsCountContextBuilder().countSharedAlerts().setSearchText("invalid_search_text").build();
		assertEquals(0, alertService.countAlerts(context));
	}

	@Test
	public void testFindSharedAlertsByOwner() {
            String userName1 = TestUtils.createRandomName();
            String userName2 = TestUtils.createRandomName();

		PrincipalUser user1 = userService.updateUser(new PrincipalUser(admin, userName1, userName1 + "test1@salesforce.com"));
		PrincipalUser user2 = userService.updateUser(new PrincipalUser(admin, userName2, userName2 + "test2@salesforce.com"));

                String alertName1 = "alertname-" + TestUtils.createRandomName();
		Alert alertSharedUser1 = alertService.updateAlert(new Alert(user1, user1, alertName1, EXPRESSION, "* * * * *"));
                String alertName2 = "alertname-" + TestUtils.createRandomName();
		Alert alertSharedUser2 = alertService.updateAlert(new Alert(user2, user2, alertName2, EXPRESSION, "* * * * *"));
                String alertName3 = "alertname-" + TestUtils.createRandomName();
		Alert alertSharedAdmin = alertService.updateAlert(new Alert(admin, admin, alertName3, EXPRESSION, "* * * * *"));

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
            String userName1 = TestUtils.createRandomName();
            String userName2 = TestUtils.createRandomName();

		PrincipalUser user1 = userService.updateUser(new PrincipalUser(admin, userName1, userName1 + "test1@salesforce.com"));
		PrincipalUser user2 = userService.updateUser(new PrincipalUser(admin, userName2, userName2 + "test2@salesforce.com"));

                String alertName1 = "alertname-" + TestUtils.createRandomName();
		Alert alertSharedUser1 = alertService.updateAlert(new Alert(user1, user1, alertName1, EXPRESSION, "* * * * *"));
                String alertName2 = "alertname-" + TestUtils.createRandomName();
		Alert alertSharedUser2 = alertService.updateAlert(new Alert(user2, user2, alertName2, EXPRESSION, "* * * * *"));
                String alertName3 = "alertname-" + TestUtils.createRandomName();
		Alert alertSharedAdmin = alertService.updateAlert(new Alert(admin, admin, alertName3, EXPRESSION, "* * * * *"));

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
	public void testFindPrivateAlertsPagedForNonPrivilegedUser() {

		// By default user is not privileged
            String userName1 = TestUtils.createRandomName();
            String userName2 = TestUtils.createRandomName();

		PrincipalUser user1 = userService.updateUser(new PrincipalUser(admin, userName1, userName1 + "test1@salesforce.com"));
		PrincipalUser user2 = userService.updateUser(new PrincipalUser(admin, userName2, userName2 + "test2@salesforce.com"));


                String alertName1 = "alertname-" + TestUtils.createRandomName();
		Alert alert1 = alertService.updateAlert(new Alert(user1, user1, alertName1, EXPRESSION, "* * * * *"));
                String alertName2 = "alertname-" + TestUtils.createRandomName();
		Alert alert2 = alertService.updateAlert(new Alert(user2, user2, alertName2, EXPRESSION, "* * * * *"));
                String alertName3 = "alertname-" + TestUtils.createRandomName();
		Alert alert3 = alertService.updateAlert(new Alert(user2, user2, alertName3, EXPRESSION, "* * * * *"));

		alert1.setShared(false);
		alertService.updateAlert(alert1);
		alert2.setShared(false);
		alertService.updateAlert(alert2);
		alert3.setShared(false);
		alertService.updateAlert(alert3);

		// Assert result is empty for non-privileged user
		assertEquals(0, alertService.findPrivateAlertsForPrivilegedUserPaged(user1, 100, 0, null, null, null).size());
	}

	@Test
	public void testCountPrivateAlertsForNonPrivilegedUser() {

		// By default user is not privileged
            String userName1 = TestUtils.createRandomName();
            String userName2 = TestUtils.createRandomName();

		PrincipalUser user1 = userService.updateUser(new PrincipalUser(admin, userName1, userName1 + "test1@salesforce.com"));
		PrincipalUser user2 = userService.updateUser(new PrincipalUser(admin, userName2, userName2 + "test2@salesforce.com"));

                String alertName1 = "alertname-" + TestUtils.createRandomName();
		Alert alert1 = alertService.updateAlert(new Alert(user1, user1, alertName1, EXPRESSION, "* * * * *"));
                String alertName2 = "alertname-" + TestUtils.createRandomName();
		Alert alert2 = alertService.updateAlert(new Alert(user2, user2, alertName2, EXPRESSION, "* * * * *"));
                String alertName3 = "alertname-" + TestUtils.createRandomName();
		Alert alert3 = alertService.updateAlert(new Alert(user2, user2, alertName3, EXPRESSION, "* * * * *"));

		alert1.setShared(false);
		alertService.updateAlert(alert1);
		alert2.setShared(false);
		alertService.updateAlert(alert2);
		alert3.setShared(false);
		alertService.updateAlert(alert3);

		// Assert non-privileged user see zero private alerts
		AlertsCountContext context = new AlertsCountContext.AlertsCountContextBuilder().countPrivateAlerts().setPrincipalUser(user1).build();
		assertEquals(0, alertService.countAlerts(context));
	}

	@Test
	public void testFindPrivateAlertsPagedForPrivilegedUser() {

		// By default user is not privileged
            String userName1 = TestUtils.createRandomName();
            String userName2 = TestUtils.createRandomName();

		PrincipalUser user1 = userService.updateUser(new PrincipalUser(admin, userName1, userName1 + "test1@salesforce.com"));
		managementService.setAdministratorPrivilege(user1, true);
		PrincipalUser user2 = userService.updateUser(new PrincipalUser(admin, userName2, userName2 + "test2@salesforce.com"));


                String alertName1 = "alertname-" + TestUtils.createRandomName();
		Alert alert1 = alertService.updateAlert(new Alert(user1, user1, alertName1, EXPRESSION, "* * * * *"));
                String alertName2 = "alertname-" + TestUtils.createRandomName();
		Alert alert2 = alertService.updateAlert(new Alert(user2, user2, alertName2, EXPRESSION, "* * * * *"));
                String alertName3 = "alertname-" + TestUtils.createRandomName();
		Alert alert3 = alertService.updateAlert(new Alert(user2, user2, alertName3, EXPRESSION, "* * * * *"));

		alert1.setShared(false);
		alertService.updateAlert(alert1);
		alert2.setShared(false);
		alertService.updateAlert(alert2);
		alert3.setShared(true);
		alertService.updateAlert(alert3);

		Set<String> alertNames = new HashSet<>();

		// Fetch first page
		List<Alert> page = alertService.findPrivateAlertsForPrivilegedUserPaged(user1, 1, 0, null, null, null);
		assertEquals(1, page.size());
		alertNames.add(page.get(0).getName());

		// Fetch second page
		page = alertService.findPrivateAlertsForPrivilegedUserPaged(user1, 1, 1, null, null, null);
		assertEquals(1, page.size());
		alertNames.add(page.get(0).getName());

		// Fetch third page, should be empty
		page = alertService.findPrivateAlertsForPrivilegedUserPaged(user1, 1, 2, null, null, null);
		assertEquals(0, page.size());

		// Assert all private alerts are fetched
		assertTrue(alertNames.contains(alertName1));
		assertTrue(alertNames.contains(alertName2));
	}

	@Test
	public void testFindPrivateAlertsPagedForPrivilegedUserWithSearchText() {

		// By default user is not privileged
            String userName1 = TestUtils.createRandomName();
            String userName2 = TestUtils.createRandomName();

		PrincipalUser user1 = userService.updateUser(new PrincipalUser(admin, userName1, userName1 + "test1@salesforce.com"));
		managementService.setAdministratorPrivilege(user1, true);
		PrincipalUser user2 = userService.updateUser(new PrincipalUser(admin, userName2, userName2 + "test2@salesforce.com"));


                String alertName1 = "alert-name_private1" + TestUtils.createRandomName();
		Alert alert1 = alertService.updateAlert(new Alert(user1, user1, alertName1, EXPRESSION, "* * * * *"));
                String alertName2 = "alert-name-private2" + TestUtils.createRandomName();
		Alert alert2 = alertService.updateAlert(new Alert(user2, user2, alertName2, EXPRESSION, "* * * * *"));
                String alertName3 = "alert-name-shared3" + TestUtils.createRandomName();
		Alert alert3 = alertService.updateAlert(new Alert(user2, user2, alertName3, EXPRESSION, "* * * * *"));

		alert1.setShared(false);
		alertService.updateAlert(alert1);
		alert2.setShared(false);
		alertService.updateAlert(alert2);
		alert3.setShared(true);
		alertService.updateAlert(alert3);

		// Search by alert name
		List<Alert> page = alertService.findPrivateAlertsForPrivilegedUserPaged(user1, 10, 0, "alert-name", null, null);
		assertEquals(2, page.size());

		page = alertService.findPrivateAlertsForPrivilegedUserPaged(user1, 10, 0, "private1", null, null);
		assertEquals(1, page.size());

		// Search by alert name case insensitive
		page = alertService.findPrivateAlertsForPrivilegedUserPaged(user1, 10, 0, "aLerT-NamE", null, null);
		assertEquals(2, page.size());

		page = alertService.findPrivateAlertsForPrivilegedUserPaged(user1, 10, 0, "PrIvAtE1", null, null);
		assertEquals(1, page.size());

		// Search shared alert name
		page = alertService.findPrivateAlertsForPrivilegedUserPaged(user1, 10, 0, "shared3", null, null);
		assertEquals(0, page.size());

		// Search shared alert name case insensitive
		page = alertService.findPrivateAlertsForPrivilegedUserPaged(user1, 10, 0, "SHaReD3", null, null);
		assertEquals(0, page.size());

		// Search by owner name
		page = alertService.findPrivateAlertsForPrivilegedUserPaged(user1, 10, 0, userName2, null, null);
		assertEquals(1, page.size());
		assertEquals(userName2, page.get(0).getOwner().getUserName());

		// Search by owner name case insensitive
		page = alertService.findPrivateAlertsForPrivilegedUserPaged(user1, 10, 0, userName2.toUpperCase(), null, null);
		assertEquals(1, page.size());
		assertEquals(userName2, page.get(0).getOwner().getUserName());
	}

	@Test
	public void testFindPrivateAlertsPagedForPrivilegedUserWithSorting() {

		// By default user is not privileged
            String userName1 = TestUtils.createRandomName();

		PrincipalUser user1 = userService.updateUser(new PrincipalUser(admin, userName1, userName1 + "test1@salesforce.com"));
		managementService.setAdministratorPrivilege(user1, true);

		Alert alert1 = alertService.updateAlert(new Alert(user1, user1, "alert1", EXPRESSION, "* * * * *"));
		try{
			Thread.sleep(1000);
		}catch(Exception e) {
		}
		Alert alert2 = alertService.updateAlert(new Alert(user1, user1, "alert2", EXPRESSION, "* * * * *"));
		try{
			Thread.sleep(1000);
		}catch(Exception e) {
		}
		Alert alert3 = alertService.updateAlert(new Alert(user1, user1, "alert3", EXPRESSION, "* * * * *"));
		try{
			Thread.sleep(1000);
		}catch(Exception e) {
		}

		//Change modified date
		alert1.setShared(false);
		alertService.updateAlert(alert1);
		try{
			Thread.sleep(1000);
		}catch(Exception e) {
		}

		//sort by alert name ascending
		List<Alert> page = alertService.findPrivateAlertsForPrivilegedUserPaged(user1, 10, 0, null, "name", "ASC");
		assertEquals(alert1.getName(), page.get(0).getName());
		assertEquals(alert2.getName(), page.get(1).getName());
		assertEquals(alert3.getName(), page.get(2).getName());

		//sort by alert name descending
		page = alertService.findPrivateAlertsForPrivilegedUserPaged(user1, 10, 0, null, "name", "DESC");
		assertEquals(alert3.getName(), page.get(0).getName());
		assertEquals(alert2.getName(), page.get(1).getName());
		assertEquals(alert1.getName(), page.get(2).getName());

		//sort by create date ascending
		page = alertService.findPrivateAlertsForPrivilegedUserPaged(user1, 10, 0, null, "createdDate", "ASC");
		assertEquals(alert1.getName(), page.get(0).getName());
		assertEquals(alert2.getName(), page.get(1).getName());
		assertEquals(alert3.getName(), page.get(2).getName());

		//sort by create date descending
		page = alertService.findPrivateAlertsForPrivilegedUserPaged(user1, 10, 0, null, "createdDate", "DESC");
		assertEquals(alert3.getName(), page.get(0).getName());
		assertEquals(alert2.getName(), page.get(1).getName());
		assertEquals(alert1.getName(), page.get(2).getName());

		//sort by modified date ascending
		page = alertService.findPrivateAlertsForPrivilegedUserPaged(user1, 10, 0, null, "modifiedDate", "ASC");
		assertEquals(alert2.getName(), page.get(0).getName());
		assertEquals(alert3.getName(), page.get(1).getName());
		assertEquals(alert1.getName(), page.get(2).getName());

		//sort by modified date descending
		page = alertService.findPrivateAlertsForPrivilegedUserPaged(user1, 10, 0, null, "modifiedDate", "DESC");
		assertEquals(alert1.getName(), page.get(0).getName());
		assertEquals(alert3.getName(), page.get(1).getName());
		assertEquals(alert2.getName(), page.get(2).getName());

		//invalid column
		try {
			page = alertService.findPrivateAlertsForPrivilegedUserPaged(user1, 10, 0, null, "invalidColumn", "DESC");
		} catch (IllegalArgumentException ex) {
			assertNotNull(ex);
		}
		try {
			page = alertService.findPrivateAlertsForPrivilegedUserPaged(user1, 10, 0, null, "", "DESC");
		} catch (IllegalArgumentException ex) {
			assertNotNull(ex);
		}
	}

	@Test
	public void testCountPrivateAlertsForPrivilegedUser() {

		// By default user is not privileged
            String userName1 = TestUtils.createRandomName();
            String userName2 = TestUtils.createRandomName();

		PrincipalUser user1 = userService.updateUser(new PrincipalUser(admin, userName1, userName1 + "test1@salesforce.com"));
		managementService.setAdministratorPrivilege(user1, true);
		PrincipalUser user2 = userService.updateUser(new PrincipalUser(admin, userName2, userName2 + "test2@salesforce.com"));


                String alertName1 = "alertname-" + TestUtils.createRandomName();
		Alert alert1 = alertService.updateAlert(new Alert(user1, user1, alertName1, EXPRESSION, "* * * * *"));
                String alertName2 = "alertname-" + TestUtils.createRandomName();
		Alert alert2 = alertService.updateAlert(new Alert(user2, user2, alertName2, EXPRESSION, "* * * * *"));
                String alertName3 = "alertname-" + TestUtils.createRandomName();
		Alert alert3 = alertService.updateAlert(new Alert(user2, user2, alertName3, EXPRESSION, "* * * * *"));

		alert1.setShared(false);
		alertService.updateAlert(alert1);
		alert2.setShared(false);
		alertService.updateAlert(alert2);
		alert3.setShared(true);
		alertService.updateAlert(alert3);

		AlertsCountContext context = new AlertsCountContext.AlertsCountContextBuilder().countPrivateAlerts().setPrincipalUser(user1).build();
		assertEquals(2, alertService.countAlerts(context));
		//mockQuery("Alert.countPrivateAlertsForPrivilegedUser",2L);
		//assertEquals(2, Alert.countPrivateAlertsForPrivilegedUser(em, admin, context.getSearchText()));

	}

	@Test
	public void testCountPrivateAlertsForPrivilegedUserWithSearchText() {

    	String namedQuery = "Alert.countPrivateAlertsForPrivilegedUserWithSearchText";
		// By default user is not privileged
            String userName1 = TestUtils.createRandomName();
            String userName2 = TestUtils.createRandomName();

		PrincipalUser user1 = userService.updateUser(new PrincipalUser(admin, userName1, userName1 + "test1@salesforce.com"));
		managementService.setAdministratorPrivilege(user1, true);
		PrincipalUser user2 = userService.updateUser(new PrincipalUser(admin, userName2, userName2 + "test2@salesforce.com"));


                String alertName1 = "alertname-" + TestUtils.createRandomName();
		Alert alert1 = alertService.updateAlert(new Alert(user1, user1, alertName1, EXPRESSION, "* * * * *"));
                String alertName2 = "alertname-" + TestUtils.createRandomName();
		Alert alert2 = alertService.updateAlert(new Alert(user2, user2, alertName2, EXPRESSION, "* * * * *"));
                String alertName3 = "alertname-" + TestUtils.createRandomName();
		Alert alert3 = alertService.updateAlert(new Alert(user2, user2, alertName3, EXPRESSION, "* * * * *"));

		alert1.setShared(false);
		alertService.updateAlert(alert1);
		alert2.setShared(false);
		alertService.updateAlert(alert2);
		alert3.setShared(true);
		alertService.updateAlert(alert3);

		// count by alert name
		AlertsCountContext context = new AlertsCountContext.AlertsCountContextBuilder().countPrivateAlerts().setPrincipalUser(user1).setSearchText("alert").build();
		assertEquals(2, alertService.countAlerts(context));
		//mockQuery(namedQuery,2L);
		//assertEquals(2, Alert.countPrivateAlertsForPrivilegedUser(em, admin, context.getSearchText()));

		// count by alert name case insensitive
		context = new AlertsCountContext.AlertsCountContextBuilder().countPrivateAlerts().setPrincipalUser(user1).setSearchText("AlErT").build();
		assertEquals(2, alertService.countAlerts(context));
		//mockQuery(namedQuery,2L);
		//assertEquals(2, Alert.countPrivateAlertsForPrivilegedUser(em, admin, context.getSearchText()));

		// count by alert name
		context = new AlertsCountContext.AlertsCountContextBuilder().countPrivateAlerts().setPrincipalUser(user1).setSearchText(alertName1).build();
		assertEquals(1, alertService.countAlerts(context));
		//mockQuery(namedQuery,1L);
		//assertEquals(1, Alert.countPrivateAlertsForPrivilegedUser(em, admin, context.getSearchText()));

		// count by owner name
		context = new AlertsCountContext.AlertsCountContextBuilder().countPrivateAlerts().setPrincipalUser(user1).setSearchText(userName2).build();
		assertEquals(1, alertService.countAlerts(context));
		//mockQuery(namedQuery,1L);
		//assertEquals(1, Alert.countPrivateAlertsForPrivilegedUser(em, admin, context.getSearchText()));

		// count by owner name case insensitive
		context = new AlertsCountContext.AlertsCountContextBuilder().countPrivateAlerts().setPrincipalUser(user1).setSearchText(userName2.toUpperCase()).build();
		assertEquals(1, alertService.countAlerts(context));
		//mockQuery(namedQuery,1L);
		//assertEquals(1, Alert.countPrivateAlertsForPrivilegedUser(em, admin, context.getSearchText()));

		// count by invalid name
		context = new AlertsCountContext.AlertsCountContextBuilder().countPrivateAlerts().setPrincipalUser(user1).setSearchText("invalid_name").build();
		assertEquals(0, alertService.countAlerts(context));
		//mockQuery(namedQuery,0L);
		//assertEquals(0, Alert.countPrivateAlertsForPrivilegedUser(em, admin, context.getSearchText()));
	}

	@Test
	public void testAlertSerDes() {

            String alertName1 = "alertname-" + TestUtils.createRandomName();
		Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), alertName1, EXPRESSION, "* * * * *");
		Notification notification = new Notification("notification", alert, "notifier-name", new ArrayList<String>(), 5000L);
		notification.setArticleNumber("an");
		notification.setSRActionable(true);
		notification.setProductTag("pT");
		notification.setElementName("elN");
		notification.setEventName("evN");
		Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN, "trigger-name", 0.95, 60000);

		alert.setNotifications(Arrays.asList(notification));
		alert.setTriggers(Arrays.asList(trigger));

		notification.setAlert(alert);
		notification.setTriggers(Arrays.asList(trigger));

		alert = alertService.updateAlert(alert);

		notification = alert.getNotifications().get(0);
		trigger = alert.getTriggers().get(0);

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

            String alertName = "alertname-" + TestUtils.createRandomName();
		Alert expected = new Alert(userService.findAdminUser(), userService.findAdminUser(), alertName, EXPRESSION, "* * * * *");
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

	@Test
	public void testAlertsCountContext() {
		String userName = TestUtils.createRandomName();
		PrincipalUser user = new PrincipalUser(admin, userName, userName + "@testcompany.com");

		// Test count user alerts context
		// Normal case
		AlertsCountContext userCtx1 = new AlertsCountContext.AlertsCountContextBuilder().countUserAlerts()
				.setPrincipalUser(user).build();
		assertTrue(userCtx1.isCountUserAlerts());

		// Missing user
		AlertsCountContext userCtx2 = new AlertsCountContext.AlertsCountContextBuilder().countUserAlerts().build();
		assertFalse(userCtx2.isCountUserAlerts());

		// Not mutual exclusive
		AlertsCountContext userCtx3 = new AlertsCountContext.AlertsCountContextBuilder().countUserAlerts()
				.countSharedAlerts().setPrincipalUser(user).build();
		assertFalse(userCtx3.isCountUserAlerts());

		// Test count shared alerts context
		// Normal case
		AlertsCountContext sharedCtx1 = new AlertsCountContext.AlertsCountContextBuilder().countSharedAlerts().build();
		assertTrue(sharedCtx1.isCountSharedAlerts());

		// Not mutual exclusive
		AlertsCountContext sharedCtx2 = new AlertsCountContext.AlertsCountContextBuilder().countSharedAlerts()
				.countPrivateAlerts().build();
		assertFalse(sharedCtx2.isCountSharedAlerts());

		// Test count private alerts context
		// Normal case
		AlertsCountContext privateCtx1 = new AlertsCountContext.AlertsCountContextBuilder().countPrivateAlerts()
				.setPrincipalUser(user).build();
		assertTrue(privateCtx1.isCountPrivateAlerts());

		// Missing user
		AlertsCountContext privateCtx2 = new AlertsCountContext.AlertsCountContextBuilder().countPrivateAlerts()
				.build();
		assertFalse(privateCtx2.isCountPrivateAlerts());

		// Not mutual exclusive
		AlertsCountContext privateCtx3 = new AlertsCountContext.AlertsCountContextBuilder().countPrivateAlerts()
				.countUserAlerts().setPrincipalUser(user).build();
		assertFalse(privateCtx3.isCountPrivateAlerts());
	}

	@Test
	public void testTriggerInertiaSetting() {
		ArrayList<String> expressionArray = new ArrayList<String> (Arrays.asList(
				"ABOVE(-1d:scope:metric:avg:4h-avg, #0.5#, #avg#)",
				"LIMIT( -21d:-1d:scope:metricA:avg:4h-avg, -1d:scope:metricB:avg:4h-avg,#1#)",
				"-20m:-0d:scone.*.*.cs19:acs.DELETERequestProcessingTime_95thPercentile{device=*acs2-1*}:avg",
				"DOWNSAMPLE(-2d:argus.alerts:scheduled{alertId=1429851}:zimsum, #5m-sum#,#-2d#, #-0m#, #0#)"
		));
		for (String currentExpression: expressionArray) {
                    String alertName = "alertname-" + TestUtils.createRandomName();
			Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), alertName, currentExpression, "* * * * *");
			try {
				Trigger trigger = new Trigger(alert, TriggerType.GREATER_THAN, "trigger-name", 0.95, 120000);
			} catch (IllegalArgumentException ex) {
				fail("Should not failed in the test for inertia setting.");
			}
		}
	}
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

package com.salesforce.dva.argus.util;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;
import static com.salesforce.dva.argus.system.SystemAssert.requireArgumentP;
import static java.math.BigInteger.ZERO;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Method;
import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

import com.google.inject.Singleton;
import com.google.inject.persist.Transactional;
import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.*;
import com.salesforce.dva.argus.service.AlertService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService;
import com.salesforce.dva.argus.service.metric.MetricReader;
import com.salesforce.dva.argus.service.jpa.DefaultJPAService;
import com.salesforce.dva.argus.service.AuditService;
import com.salesforce.dva.argus.system.SystemConfiguration;


import com.salesforce.dva.argus.system.SystemException;
import org.junit.After;
import org.junit.Before;
import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.TriggerBuilder;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;

import com.google.inject.Provider;
import com.google.inject.Inject;
import javax.persistence.Persistence;
import javax.persistence.EntityManager;


import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@RunWith(org.mockito.runners.MockitoJUnitRunner.class)
public class AlertUtilsTest extends AbstractTest {

	private PrincipalUser admin;
	private AlertService alertService;
	private UserService userService;

	@Before
	public void setUp() {
	 	super.setUp();
	 	userService = system.getServiceFactory().getUserService();
		admin = userService.findAdminUser();
		alertService = system.getServiceFactory().getAlertService();
    }

	@Test
	public void isScopePresentInWhiteListTest() {
		List<Pattern> scopesSet = new ArrayList<Pattern>(Arrays.asList(new Pattern[] {Pattern.compile("argus.core"), Pattern.compile("kafka.broker.*.ajna_local")}));
		
		assertTrue(AlertUtils.isScopePresentInWhiteList("-1d:argus.core:alerts.scheduled:zimsum:15m-sum",scopesSet));
		assertTrue(AlertUtils.isScopePresentInWhiteList("COUNT(-75m:-15m:kafka.broker.CHI.NONE.ajna_local:kafka.server.BrokerTopicMetrics.BytesInPerSec.BytesCount{device=*}:avg:1m-avg)", scopesSet));
		assertFalse(AlertUtils.isScopePresentInWhiteList("COUNT(-75m:-15m:kafka1.broker.CHI.NONE.ajna_local:kafka.server.BrokerTopicMetrics.BytesInPerSec.BytesCount{device=*}:avg:1m-avg)", scopesSet));
	}

	private static long _toBeginOfMinute(long millis){
		return millis-(millis % (60*1000));
	}

	// @Test
	public void testCronLoop() {
		for(int i = 0; i < 5 * 120; i++)
		{
			try {
				Thread.sleep(200);
			}catch (Exception e) {
				System.out.println("Exiting");
				return;
			}
			testCronTrigger();
		}
	}

	@Test
	public void testCronTrigger() {

		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

		String cronEntry = "* * * * *";
		String quartzCronEntry = Cron.convertToQuartzCronEntry(cronEntry);

		long minuteStartTimeMillis = _toBeginOfMinute(System.currentTimeMillis());
		Date fireTime = new Date(minuteStartTimeMillis);
		Date previousMinuteLastSecondTime = new Date(minuteStartTimeMillis - 1000);

		CronTrigger cronTrigger = TriggerBuilder.newTrigger().withSchedule(CronScheduleBuilder.cronSchedule(quartzCronEntry)).startAt(previousMinuteLastSecondTime).build();

		Date nextFireTime = cronTrigger.getFireTimeAfter(previousMinuteLastSecondTime);

		if(nextFireTime.equals(fireTime))
		{
			System.out.println(String.format("Current Time %s: Fire Time %s Matches", sdf.format(new Date()), sdf.format(nextFireTime)));
		} else {
			System.out.println(String.format("Current Time %s: Fire Time %s", sdf.format(new Date()), sdf.format(nextFireTime)));
		}

		assertTrue(nextFireTime.equals(fireTime));
	}

	@Test
	public void testAbsoluteTimeStampsInExpression() {

		Long alertEnqueueTime = 1418319600000L;
		ArrayList<String> expressionArray = new ArrayList<String> (Arrays.asList(
				"-20m:-0d:scone.*.*.cs19:acs.DELETERequestProcessingTime_95thPercentile{device=*acs2-1*}:avg",
				"  SCALE( SUM( DIVIDE( DIFF( DOWNSAMPLE( SUM( CULL_BELOW( DERIVATIVE(                   -1h:-40m:core.*.*.eu11:SFDC_type-Stats-name1-Search-name2-Client-name3-Query_Count__SolrLive.Count{device=eu11-app*}:sum:1m-max ), #0.001#, #value# ), #union# ), #10m-sum# ), DOWNSAMPLE( SUM( CULL_BELOW( DERIVATIVE(                   -2h:-40m:core.*.*.eu11:SFDC_type-Stats-name1-Search-name2-Client-name3-Search_Fallbacks__SolrLive.Count{device=eu11-app*}:sum:1m-max ), #0.01#, #value# ), #union# ), #10m-sum# ), #union# ), CULL_BELOW( DOWNSAMPLE( SUM( CULL_BELOW( DERIVATIVE( -40m:core.*.*.eu11:SFDC_type-Stats-name1-Search-name2-Client-name3-Query_Count__SolrLive.Count{device=eu11-app*}:sum:1m-max ), #0.001#, #value# ), #union# ), #10m-sum# ), #1000#, #value# ) ), #-1# ), #-100# ) ",
				"ABOVE(-1d:scope:metric:avg:4h-avg, #0.5#, #avg#)",
				"ABOVE(-1h:scope:metric:avg:4h-avg, #0.5#)",
				"ALIASBYTAG(-1s:scope:metric{device=*,source=*}:sum)",
				"FILL( #-1D#, #-0d#,#4h#,#0m#,#100#)",
				"GROUPBY(-2d:-1d:scope:metricA{host=*}:avg,#(myhost[1-9])#, #SUM#, #union#)",
				"LIMIT( -21d:-1d:scope:metricA:avg:4h-avg, -1d:scope:metricB:avg:4h-avg,#1#)",
				"RANGE(-10d:scope:metric[ABCD]:avg:1d-max)",
				"DOWNSAMPLE(DOWNSAMPLE(GROUPBYTAG(CULL_BELOW(-115m:-15m:iot-provisioning-server.PRD.SP2.-:health.status{device=provisioning-warden-*}:avg:1m-max, #1#, #value#), #DeploymentName#, #MAX#), #1m-max#), #10m-count#)",
				"DOWNSAMPLE(CULL_BELOW(DERIVATIVE(-115m:-15m:iot-container.PRD.NONE.-:iot.flows.state.load.errors_count{flowsnakeEnvironmentName=iot-prd-stmfa-00ds70000000mqy}:zimsum:1m-sum), #0#, #value#), #10m-sum#)",
				"DOWNSAMPLE(-2d:alerts.scheduled:alert-1429851:zimsum, #5m-sum#,#-2d#, #-0m#, #0#)"
		));

		ArrayList<String> expectedOutput = new ArrayList<String> (Arrays.asList(
				"1418318400000:1418319600000:scone.*.*.cs19:acs.DELETERequestProcessingTime_95thPercentile{device=*acs2-1*}:avg",
				"SCALE(SUM(DIVIDE(DIFF(DOWNSAMPLE(SUM(CULL_BELOW(DERIVATIVE(1418316000000:1418317200000:core.*.*.eu11:SFDC_type-Stats-name1-Search-name2-Client-name3-Query_Count__SolrLive.Count{device=eu11-app*}:sum:1m-max),#0.001#,#value#),#union#),#10m-sum#),DOWNSAMPLE(SUM(CULL_BELOW(DERIVATIVE(1418312400000:1418317200000:core.*.*.eu11:SFDC_type-Stats-name1-Search-name2-Client-name3-Search_Fallbacks__SolrLive.Count{device=eu11-app*}:sum:1m-max),#0.01#,#value#),#union#),#10m-sum#),#union#),CULL_BELOW(DOWNSAMPLE(SUM(CULL_BELOW(DERIVATIVE(1418317200000:1418319600000:core.*.*.eu11:SFDC_type-Stats-name1-Search-name2-Client-name3-Query_Count__SolrLive.Count{device=eu11-app*}:sum:1m-max),#0.001#,#value#),#union#),#10m-sum#),#1000#,#value#)),#-1#),#-100#)",
				"ABOVE(1418233200000:1418319600000:scope:metric:avg:4h-avg,#0.5#,#avg#)",
				"ABOVE(1418316000000:1418319600000:scope:metric:avg:4h-avg,#0.5#)",
				"ALIASBYTAG(1418319599000:1418319600000:scope:metric{device=*,source=*}:sum)",
				"FILL(#1418233200000#,#1418319600000#,#4h#,#0m#,#100#)",
				"GROUPBY(1418146800000:1418233200000:scope:metricA{host=*}:avg,#(myhost[1-9])#,#SUM#,#union#)",
				"LIMIT(1416505200000:1418233200000:scope:metricA:avg:4h-avg,1418233200000:1418319600000:scope:metricB:avg:4h-avg,#1#)",
				"RANGE(1417455600000:1418319600000:scope:metric[ABCD]:avg:1d-max)",
				"DOWNSAMPLE(DOWNSAMPLE(GROUPBYTAG(CULL_BELOW(1418312700000:1418318700000:iot-provisioning-server.PRD.SP2.-:health.status{device=provisioning-warden-*}:avg:1m-max,#1#,#value#),#DeploymentName#,#MAX#),#1m-max#),#10m-count#)",
				"DOWNSAMPLE(CULL_BELOW(DERIVATIVE(1418312700000:1418318700000:iot-container.PRD.NONE.-:iot.flows.state.load.errors_count{flowsnakeEnvironmentName=iot-prd-stmfa-00ds70000000mqy}:zimsum:1m-sum),#0#,#value#),#10m-sum#)",
				"DOWNSAMPLE(1418146800000:1418319600000:alerts.scheduled:alert-1429851:zimsum,#5m-sum#,#1418146800000#,#1418319600000#,#0#)"
		));

		UserService userService = system.getServiceFactory().getUserService();
		Alert alert = new Alert(userService.findAdminUser(), userService.findAdminUser(), "alert_name", expressionArray.get(0), "* * * * *");
		Notification notification = new Notification("notification_name", alert, "notifier_name", new ArrayList<String>(), 23);
		Trigger trigger = new Trigger(alert, Trigger.TriggerType.GREATER_THAN_OR_EQ, "trigger_name", 2D, 5);

		alert.setNotifications(Arrays.asList(new Notification[] { notification }));
		alert.setTriggers(Arrays.asList(new Trigger[] { trigger }));
		alert = system.getServiceFactory().getAlertService().updateAlert(alert);

		History history = new History(History.JobStatus.SUCCESS.getDescription(), "localhost", BigInteger.ONE, History.JobStatus.SUCCESS);
		DefaultAlertService.NotificationContext context = new DefaultAlertService.NotificationContext(alert, alert.getTriggers().get(0), notification,
				1418320200000L, 0.0, new Metric("scope", "metric"), history);
		context.setAlertEnqueueTimestamp(alertEnqueueTime);

		ArrayList<String> actualOutput = new ArrayList<String>();
		for (String currentExpression: expressionArray) {
			alert.setExpression(currentExpression);
			String currentOutput = AlertUtils.getExpressionWithAbsoluteStartAndEndTimeStamps(context);
			actualOutput.add(currentOutput);
			assertEquals(true, MetricReader.isValid(currentOutput));
		}

		assertEquals(expectedOutput, actualOutput);
	}

	@Test
	public void testDetectDCFromExpression() {
		MetricService _mService = system.getServiceFactory().getMetricService();
		int idx = 0;
		ArrayList<String> expressionList = new ArrayList<>(Arrays.asList(
				"-2h:system.DC1.service:metric:max",
				"-1m:system.DC2.service:metric{tagk=tagv}:min",
				"DIVIDE(-15m:system.DC3.service:metric1:avg, -15m:system.DC4.service:metric2:avg)",
				"-75m:system.dc5.service:metric:sum"));
		String [][] actualOutput =  new String[][]{{"DC1"},{"DC2"},{"DC4","DC3"},{"DC5"}};
		for(String currentExpression: expressionList) {
			List<String> expectedOutput = _mService.getDCFromExpression(currentExpression);
			assertEquals(expectedOutput, new ArrayList<>(Arrays.asList(actualOutput[idx++])));
		}
	}


	// ------------------------------------------------------------------------------------------------
	// Alert Setter & Getter Tests
	// ------------------------------------------------------------------------------------------------

	@Test
	public void testAlert_setExpression()
	{
        String expression = "ABOVE(-4h:scope:metric:avg:4h-avg,#0.5#)";

        Alert a = alertService.updateAlert(new Alert(admin, admin, "sample", expression, "* * * * *"));
        String returned_expression = a.getExpression();
        assertEquals(expression, returned_expression);

        a.setExpression(expression);
        returned_expression = a.getExpression();
        assertEquals(expression, returned_expression);

        a = alertService.updateAlert(a);
        alertService.deleteAlert(a.getName(), userService.findAdminUser());
	}

	@Test
	public void testAlert_setInvalidExpression()
	{
	    Alert a = null;
        String expression = "ABOVE(-4h:scope:metric:avg:4h-avg,#0.5#)";
        String invalid_expression = ")(Ao23890OAOjkfak:0a89s--8103";

        try
		{
        	a = alertService.updateAlert(new Alert(admin, admin, "sample", expression, "* * * * *"));
        	String returned_expression = a.getExpression();
        	assertEquals(expression, returned_expression);

            a.setExpression(invalid_expression);
            assertTrue(false);
		}
		catch (RuntimeException e)
		{
           assertNotNull(a);
           String returned_expression = a.getExpression();
           assertEquals(expression, returned_expression);
           assertNotEquals( expression, invalid_expression);
		}

        alertService.deleteAlert(a.getName(), userService.findAdminUser());

        a = null;

        try
        {
            a = new Alert(admin, admin, "sample", invalid_expression, "* * * * *");
            assertTrue(false);
        }
        catch (RuntimeException e)
        {
           assertNull(a); // TODO - verify this
        }

	}


	@Test
	public void testAlert_setCron()
	{
        String expression = "ABOVE(-4h:scope:metric:avg:4h-avg,#0.5#)";
        String other_cron = "* */4 * * *";
        String valid_cron = "* * * * *";

        Alert a = alertService.updateAlert(new Alert(admin, admin, "sample", expression, valid_cron));

        String returned_cron = a.getCronEntry();
        assertEquals(valid_cron, returned_cron);

        a.setCronEntry(other_cron);
        returned_cron = a.getCronEntry();
        assertEquals(other_cron, returned_cron);

        a = alertService.updateAlert(a);
        alertService.deleteAlert(a.getName(), userService.findAdminUser());
	}

	@Test
	public void testAlert_setInvalidCron()
	{
        Alert a = null;
        String expression = "ABOVE(-4h:scope:metric:avg:4h-avg,#0.5#)";
        String invalid_cron = "+ + + + + +";
        String valid_cron = "* * * * *";

        try
        {
            a = alertService.updateAlert(new Alert(admin, admin, "sample", expression, valid_cron));
            a.setCronEntry(invalid_cron);
            assertTrue(false);
        }
        catch (RuntimeException e)
        {
            assertNotNull(a);
            String returned_cron = a.getCronEntry();
            assertEquals(valid_cron, returned_cron);
            assertNotEquals(invalid_cron, returned_cron);
        }

        alertService.deleteAlert(a.getName(), userService.findAdminUser());
        a = null;

        try
        {
            a = new Alert(admin, admin, "sample", expression, invalid_cron);
            assertTrue(false);
        }
        catch (RuntimeException e)
        {
            assertNull(a); // TODO - verify this
        }
	}


	@Test
	public void testAlert_setOwner()
	{
		Alert a = null;
		String expression = "ABOVE(-4h:scope:metric:avg:4h-avg,#0.5#)";
		String valid_cron = "* * * * *";

		a = alertService.updateAlert(new Alert(admin, admin, "sample", expression, valid_cron));
		PrincipalUser u = a.getOwner();
		assertEquals(admin, u);

		PrincipalUser expectedUser = userService.updateUser(new PrincipalUser(admin, "testUser", "testuser@testcompany.com"));
		a.setOwner(expectedUser);
		a = alertService.updateAlert(a);

		u = a.getOwner();
		assertEquals(expectedUser, u);

		PrincipalUser expectedUser2 = userService.updateUser(new PrincipalUser(admin, "testUser2", "testuser2@testcompany.com"));
		a.setOwner(expectedUser2);
		a = alertService.updateAlert(a);

		u = a.getOwner();
		assertEquals(expectedUser2, u);

		alertService.deleteAlert(a.getName(), userService.findUserByUsername("testUser2"));
		userService.deleteUser(userService.findUserByUsername("testUser"));
		userService.deleteUser(userService.findUserByUsername("testUser2"));
	}


	@Test
	public void testAlert_setInvalidOwner()
	{
        Alert a = null;
        String expression = "ABOVE(-4h:scope:metric:avg:4h-avg,#0.5#)";
        String valid_cron = "* * * * *";

        a = alertService.updateAlert(new Alert(admin, admin, "sample", expression, valid_cron));
        PrincipalUser u = a.getOwner();
        assertEquals( admin, u );

        try
        {
            a.setOwner(null);
            assertTrue( false );
        }
        catch (RuntimeException e)
        {
            u = a.getOwner();
            assertEquals( admin, u );
        }

        alertService.deleteAlert(a.getName(), userService.findAdminUser());
	}


	@Test
	public void testAlert_setName()
	{
        String expression = "ABOVE(-4h:scope:metric:avg:4h-avg,#0.5#)";
        String valid_cron = "* * * * *";
        String name = "sample";
        String name2 = "sample2";

        Alert a = alertService.updateAlert(new Alert(admin, admin, name, expression, valid_cron));
        String n = a.getName();
        assertEquals( name, n );

        a.setName(name2);
		a = alertService.updateAlert(a);
        n = a.getName();
        assertEquals(name2, n);

        alertService.deleteAlert(a.getName(), userService.findAdminUser());
   	}

	@Test
	public void testAlert_setInvalidName()
	{
        Alert a = null;
        String expression = "ABOVE(-4h:scope:metric:avg:4h-avg,#0.5#)";
        String valid_cron = "* * * * *";
        String name = "sample";

        a = alertService.updateAlert(new Alert(admin, admin, name, expression, valid_cron));
        String n = a.getName();
        assertEquals( name, n );

        try
        {
            a.setName(null);
            assertTrue( false );
        }
        catch (RuntimeException e)
        {
            n = a.getName();
            assertEquals( name, n );
        }

        // Is an empty name also valid or invalid?
        try
        {
            a.setName("");
            assertTrue( false );
        }
        catch (RuntimeException e)
        {
            n = a.getName();
            assertEquals( name, n );
        }

        alertService.deleteAlert(a.getName(), userService.findAdminUser());
	}

	@Test
	public void testAlert_setShared()
	{
        String expression = "ABOVE(-4h:scope:metric:avg:4h-avg,#0.5#)";
        String valid_cron = "* * * * *";
        String name = "sample";

        Alert a = alertService.updateAlert(new Alert(admin, admin, name, expression, valid_cron));
        boolean b = a.isShared();
        assertFalse( b );

        a.setShared(true);
        a = alertService.updateAlert(a);
        b = a.isShared();
        assertTrue( b );

        a.setShared(false);
        b = a.isShared();
        assertFalse( b );
        a = alertService.updateAlert(a);

        alertService.deleteAlert(a.getName(), userService.findAdminUser());
	}

	@Test
	public void testAlert_setMissingDataNotificationEnabled()
	{
        String expression = "ABOVE(-4h:scope:metric:avg:4h-avg,#0.5#)";
        String valid_cron = "* * * * *";
        String name = "sample";

        Alert a = alertService.updateAlert(new Alert(admin, admin, name, expression, valid_cron));
        boolean b = a.isMissingDataNotificationEnabled();
        assertFalse( b );

        a.setMissingDataNotificationEnabled(true);
        a = alertService.updateAlert(a);
        b = a.isMissingDataNotificationEnabled();
        assertTrue( b );

        a.setMissingDataNotificationEnabled(false);
        a = alertService.updateAlert(a);
        b = a.isMissingDataNotificationEnabled();
        assertFalse( b );

        alertService.deleteAlert(a.getName(), userService.findAdminUser());
	}


	@Test
	public void testAlert_setEnabled()
	{
        String expression = "ABOVE(-4h:scope:metric:avg:4h-avg,#0.5#)";
        String valid_cron = "* * * * *";
        String name = "sample";

        Alert a = alertService.updateAlert(new Alert(admin, admin, name, expression, valid_cron));

        boolean b = a.isEnabled();
        assertFalse( b );

        a.setEnabled(true);
        a = alertService.updateAlert(a);
        b = a.isEnabled();
        assertTrue( b );

        a.setEnabled(false);
        a = alertService.updateAlert(a);
        b = a.isEnabled();
        assertFalse( b );

        alertService.deleteAlert(a.getName(), userService.findAdminUser());
	}

	@Test
	public void testAlert_setTriggers()
	{
		String expression = "ABOVE(-4h:scope:metric:avg:4h-avg,#0.5#)";
		String valid_cron = "* * * * *";
		String name = "sample";

		Alert a = alertService.updateAlert(new Alert(admin, admin, name, expression, valid_cron));
		Trigger trigger1 = new Trigger(a, Trigger.TriggerType.GREATER_THAN_OR_EQ, "warning", 2D, 100);
		Trigger trigger2 = new Trigger(a, Trigger.TriggerType.GREATER_THAN,       "critical", 50, 100);

		a.setTriggers(Arrays.asList(new Trigger[] { trigger1, trigger2 }));
		a = alertService.updateAlert(a);

		for (Trigger trigger : a.getTriggers()) {
			alertService.deleteTrigger(trigger);
		}
		a.setTriggers(null);
		a = alertService.updateAlert(a);

        alertService.deleteAlert(a.getName(), userService.findAdminUser());
	}

	@Test
	public void testAlert_setInvalidTriggers()
	{
		Alert a = null;
		String expression = "ABOVE(-4h:scope:metric:avg:4h-avg,#0.5#)";
		String valid_cron = "* * * * *";
		String name = "sample";

		a = alertService.updateAlert(new Alert(admin, admin, name, expression, valid_cron));

		// Pass null list
		a.setTriggers(null);
		List<Trigger> triggers = a.getTriggers();
		assertTrue(triggers.isEmpty());

		// Pass empty list
		a.setTriggers(Arrays.asList(new Trigger[0]));
		triggers = a.getTriggers();
		assertTrue(triggers.isEmpty());

		// Pass list of null triggers.
		// TODO - Alert should handle this case by filtering null triggers from the list OR raising an exception

        alertService.deleteAlert(a.getName(), userService.findAdminUser());
	}


//	@Test
//	public void testAlert_setNotifications()
//	{
//		String expression = "ABOVE(-4h:scope:metric:avg:4h-avg,#0.5#)";
//		String valid_cron = "* * * * *";
//		String name = "sample";
//
//		Alert a = alertService.updateAlert(new Alert(admin, admin, name, expression, valid_cron));
//
//		// Create and add 2 triggers
//		Trigger trigger1 = new Trigger(a, Trigger.TriggerType.GREATER_THAN_OR_EQ, "warning", 2D, 100);
//		Trigger trigger2 = new Trigger(a, Trigger.TriggerType.GREATER_THAN,       "critical", 50, 100);
//		a.setTriggers(Arrays.asList(new Trigger[] { trigger1, trigger2 }));
//		// NOTE - putting alertService.updatealert() here causes a duplicate key exception
//
//		// Create and add 2 notifications, one for each trigger
//		Notification not1 = new Notification("notification_1x", a, "not1", null, 5000);
//		Notification not2 = new Notification("notification_2x", a, "not2", null, 5000);
//
//		a.setNotifications(Arrays.asList(new Notification[] {not1, not2}));
//		not1.setTriggers(Arrays.asList(new Trigger[] {trigger1, trigger2}));
//		not2.setTriggers(Arrays.asList(new Trigger[] {trigger1, trigger2}));
//
//		a = alertService.updateAlert(a);
//
//
//		// Clean up the Alert
//		for (Trigger trigger : a.getTriggers()) {
//			alertService.deleteTrigger(trigger);
//		}
//		a.setTriggers(null);
//		a = alertService.updateAlert(a);
//
//		for (Notification notification : a.getNotifications()) {
//			notification.setTriggers(null);
//			alertService.deleteNotification(notification);
//		}
//		a.setNotifications(null);
//		a = alertService.updateAlert(a);
//
//		alertService.deleteAlert(a.getName(), userService.findAdminUser());
//	}


//	 @Test
//	 public void testAlert_addNotification()
//	 {
//		 // set to list of notifications
//		 // set to empty or null -> result is empty.
//		 // invalid is not a valid scenario
//
//		 String expression = "ABOVE(-4h:scope:metric:avg:4h-avg,#0.5#)";
//		 String valid_cron = "* * * * *";
//		 String name = "sample";
//
//		 Alert a = alertService.updateAlert(new Alert(admin, admin, name, expression, valid_cron));
//
//		 // Create and add 2 triggers
//		 Trigger trigger1 = new Trigger(a, Trigger.TriggerType.GREATER_THAN_OR_EQ, "warning", 2D, 100);
//		 Trigger trigger2 = new Trigger(a, Trigger.TriggerType.GREATER_THAN,       "critical", 50, 100);
//		 a.setTriggers(Arrays.asList(new Trigger[] { trigger1, trigger2 }));
//
//		 // Create and add 2 notifications, one for each trigger
//		 Notification not1 = new Notification("notification_1", a, "not1", null, 5000);
//		 Notification not2 = new Notification("notification_2", a, "not2", null, 5000);
//		 a.setNotifications(Arrays.asList(new Notification[] {not1, not2}));
//
//		 not1.setTriggers(Arrays.asList(new Trigger[] {trigger1}));
//		 not2.setTriggers(Arrays.asList(new Trigger[] {trigger2}));
//
//		 a = alertService.updateAlert(a);
//
//
//		 // Add a trigger and Notification
//
//		 // Add a trigger
//		 Trigger trigger3 = new Trigger(a, Trigger.TriggerType.GREATER_THAN_OR_EQ, "notice", 10, 100);
//		 List<Trigger> t = new ArrayList<Trigger>( a.getTriggers());
//		 t.add(trigger3);
//		 a.setTriggers(t);
//
//		 // Add a notification
//		 Notification not3 = new Notification("notification_3", a, "not3", null, 5000);
//		 a.addNotification(not3);
//		 not3.setTriggers(Arrays.asList(new Trigger[] { trigger3 }));
//		 a = alertService.updateAlert(a);
//
//
//		 // Clean up the Alert
//		 for (Trigger trigger : a.getTriggers()) {
//			 alertService.deleteTrigger(trigger);
//		 }
//		 a.setTriggers(null);
//		 a = alertService.updateAlert(a);
//
//		 for (Notification notification : a.getNotifications()) {
//			 notification.setTriggers(null);
//			 alertService.deleteNotification(notification);
//		 }
//		 a.setNotifications(null);
//		 a = alertService.updateAlert(a);
//
//		 alertService.deleteAlert(a.getName(), userService.findAdminUser());
//	 }
//
//
//	@Test
//	public void testAlert_removeNotification()
//	{
//		// set to list of notifications
//		// set to empty or null -> result is empty.
//		// invalid is not a valid scenario
//
//		String expression = "ABOVE(-4h:scope:metric:avg:4h-avg,#0.5#)";
//		String valid_cron = "* * * * *";
//		String name = "sample";
//
//		Alert a = alertService.updateAlert(new Alert(admin, admin, name, expression, valid_cron));
//
//		// Create and add 3 triggers
//		Trigger trigger1 = new Trigger(a, Trigger.TriggerType.GREATER_THAN_OR_EQ, "warning", 2D, 100);
//		Trigger trigger2 = new Trigger(a, Trigger.TriggerType.GREATER_THAN,       "critical", 50, 100);
//		Trigger trigger3 = new Trigger(a, Trigger.TriggerType.GREATER_THAN_OR_EQ, "notice", 10, 100);
//		a.setTriggers(Arrays.asList(new Trigger[] { trigger1, trigger2 }));
//
//		// Create and add 2 notifications, one for each trigger
//		Notification not1 = new Notification("notification_1", a, "not1", null, 5000);
//		Notification not2 = new Notification("notification_2", a, "not2", null, 5000);
//		Notification not3 = new Notification("notification_3", a, "not3", null, 5000);
//		a.setNotifications(Arrays.asList(new Notification[] {not1, not2, not3}));
//
//		not1.setTriggers(Arrays.asList(new Trigger[] {trigger1}));
//		not2.setTriggers(Arrays.asList(new Trigger[] {trigger2}));
//		not3.setTriggers(Arrays.asList(new Trigger[] {trigger3}));
//
//		a = alertService.updateAlert(a);
//
//
//		// Remove a trigger and notification
//
//		// Remove trigger3 from the alert
//		List<Trigger> t = new ArrayList<Trigger>( a.getTriggers());
//		t.remove(trigger3);
//		a.setTriggers(t);
//
//		// Remove not3 from the alert and trigger
//		List<Notification> ns = new ArrayList(a.getNotifications());
//		ns.remove(not3);
//		not3.setTriggers(null);
//		a.setNotifications(ns);
//
//		// alertService.deleteNotification(not3);  // This seems not to be necessary but I'm not sure hwo the notification gets deleted
//
//		a = alertService.updateAlert(a);
//
//
//		// Clean up the Alert
//		for (Trigger trigger : a.getTriggers()) {
//			alertService.deleteTrigger(trigger);
//		}
//		a.setTriggers(null);
//		a = alertService.updateAlert(a);
//
//		for (Notification notification : a.getNotifications()) {
//			notification.setTriggers(null);
//			alertService.deleteNotification(notification);
//		}
//		a.setNotifications(null);
//		a = alertService.updateAlert(a);
//
//		alertService.deleteAlert(a.getName(), userService.findAdminUser());
//	}

	// TODO - validate notification content and test validation.
	// TODO - validate trigger content and test validation.
}

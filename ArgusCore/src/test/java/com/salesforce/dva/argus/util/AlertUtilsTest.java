package com.salesforce.dva.argus.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

import com.google.inject.Provider;
import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Alert;
import com.salesforce.dva.argus.entity.History;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.Notification;
import com.salesforce.dva.argus.entity.Trigger;
import com.salesforce.dva.argus.service.CacheService;
import com.salesforce.dva.argus.service.DiscoveryService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.UserService;
import com.salesforce.dva.argus.service.alert.DefaultAlertService;
import com.salesforce.dva.argus.service.metric.DefaultMetricService;
import com.salesforce.dva.argus.service.metric.MetricReader;
import com.salesforce.dva.argus.service.schema.CachedDiscoveryService;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import javafx.util.Pair;
import org.junit.Test;
import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.TriggerBuilder;

public class AlertUtilsTest extends AbstractTest {

	private static final String CACHED_QUERIES_0 = "[{\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB0\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC1.service1\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB1\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC2.service2\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB5\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC1.service1\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB6\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC2.service2\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB10\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC1.service1\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB11\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC2.service2\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB15\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC1.service1\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB16\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC2.service2\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB20\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC1.service1\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB21\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC2.service2\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB25\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC1.service1\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB26\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC2.service2\"}]";
	private static final String CACHED_QUERIES_1 = "[{\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB0\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC1.service1\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB1\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC2.service2\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB2\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC3.service3\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB3\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC4.service4\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB4\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC5.service5\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB5\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC1.service1\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB6\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC2.service2\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB7\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC3.service3\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB8\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC4.service4\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB9\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC5.service5\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB10\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC1.service1\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB11\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC2.service2\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB12\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC3.service3\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB13\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC4.service4\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB14\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC5.service5\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB15\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC1.service1\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB16\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC2.service2\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB17\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC3.service3\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB18\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC4.service4\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB19\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC5.service5\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB20\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC1.service1\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB21\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC2.service2\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB22\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC3.service3\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB23\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC4.service4\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB24\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC5.service5\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB25\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC1.service1\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB26\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC2.service2\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB27\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC3.service3\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB28\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC4.service4\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB29\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"system.DC5.service5\"} ]";

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

		Map<String, List<String>> testSuite = new HashMap<>();

		testSuite.put("-30d:system.[DC1|DC2].[service1|service2]:metric:avg", Arrays.asList("DC1", "DC2"));
		testSuite.put("-30d:*DC*:metric:max", Arrays.asList("DC1", "DC2", "DC3", "DC4", "DC5"));
		testSuite.put("-2h:system.DC1.service:metric:max", Arrays.asList("DC1"));
		testSuite.put("-1m:system.DC2.service:metric{tagk=tagv}:min", Arrays.asList("DC2"));
		testSuite.put("DIVIDE(-15m:system.DC3.service:metric1:avg, -15m:system.DC4.service:metric2:avg)", Arrays.asList("DC3", "DC4"));
		testSuite.put("-75m:system.dc5.service:metric:sum", Arrays.asList("DC5"));


		CacheService cacheServiceMock = mock(CacheService.class);
		when(cacheServiceMock.get("system.[DC1|DC2].[service1|service2]:metric{{}}")).thenReturn(CACHED_QUERIES_0);
		when(cacheServiceMock.get("*DC*:metric{{}}")).thenReturn(CACHED_QUERIES_1);
		DiscoveryService discoveryServiceMock = mock(DiscoveryService.class);

		CachedDiscoveryService service = new CachedDiscoveryService(cacheServiceMock, discoveryServiceMock, system.getConfiguration());
		Provider<MetricReader<MetricQuery>> queryprovider = new Provider<MetricReader<MetricQuery>>() {
			@Override
			public MetricReader<MetricQuery> get() {
				MetricReader<MetricQuery> mReaderMock = new MetricReader<>(system.getServiceFactory().getTSDBService(), service,null);
				return mReaderMock;
			}
		};
		DefaultMetricService _mServiceMock = new DefaultMetricService(system.getServiceFactory().getMonitorService(),null, queryprovider, system.getConfiguration());

		for(Map.Entry<String, List<String>> currentSuite: testSuite.entrySet()) {
			List<String> actualOutput = _mServiceMock.getDCFromExpression(currentSuite.getKey());
			Collections.sort(actualOutput);
			assertEquals(currentSuite.getValue(), actualOutput);
		}
	}
}

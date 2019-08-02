/*
 *
 *  * Copyright (c) 2016, Salesforce.com, Inc.
 *  * All rights reserved.
 *  *
 *  * Redistribution and use in source and binary forms, with or without
 *  * modification, are permitted provided that the following conditions are met:
 *  *
 *  * 1. Redistributions of source code must retain the above copyright notice,
 *  * this list of conditions and the following disclaimer.
 *  *
 *  * 2. Redistributions in binary form must reproduce the above copyright notice,
 *  * this list of conditions and the following disclaimer in the documentation
 *  * and/or other materials provided with the distribution.
 *  *
 *  * 3. Neither the name of Salesforce.com nor the names of its contributors may
 *  * be used to endorse or promote products derived from this software without
 *  * specific prior written permission.
 *  *
 *  * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *  * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *  * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 *  * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 *  * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *  * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 *  * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 *  * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 *  * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *  * POSSIBILITY OF SUCH DAMAGE.
 *
 */

package com.salesforce.dva.argus.service.monitor;

import com.salesforce.dva.argus.TestUtils;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.MailService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.MetricStorageService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.mail.DefaultMailService;
import com.salesforce.dva.argus.service.metric.DefaultMetricService;
import com.salesforce.dva.argus.service.metric.ElasticSearchConsumerOffsetMetricsService;
import com.salesforce.dva.argus.service.tsdb.DefaultTSDBService;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.system.SystemConfiguration;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.powermock.api.mockito.PowerMockito.when;

@RunWith(PowerMockRunner.class)
@PrepareForTest(SystemConfiguration.class)
public class DataLagServiceTest {

	private DataLagMonitorGoldenMetric goldenMetricService;
	private DataLagMonitorConsumerOffset consumerOffsetService;
	@Mock SystemConfiguration systemConfigGoldenMetric;
	@Mock SystemConfiguration systemConfigConsumerOffset;
	@Mock MetricService mockedMetricService;
	@Mock MetricStorageService mockedMetricStorageService;
	@Mock TSDBService mockedTSDBService;
	@Mock MailService mailService;

	private static final List<String> DC_LIST = Arrays.asList("DC1", "DC2", "DC3", "DC4");
	private static final String DEFAULT_EXPRESSION = "-5m:scope.default:metric.default:max:1m-max";

	@Before
	public void setUp() {
		setupMockServices();
		goldenMetricService = spy(new DataLagMonitorGoldenMetric(systemConfigGoldenMetric, mockedMetricService, mockedTSDBService));
		consumerOffsetService = spy(new DataLagMonitorConsumerOffset(systemConfigConsumerOffset, mockedMetricStorageService, mockedMetricService, mockedTSDBService, mailService));
	}

	private void setupMockServices() {
		systemConfigGoldenMetric = mock(SystemConfiguration.class);
		systemConfigConsumerOffset = mock(SystemConfiguration.class);
		mockedMetricService = mock(DefaultMetricService.class);
		mockedMetricStorageService = mock(ElasticSearchConsumerOffsetMetricsService.class);
		mockedTSDBService = mock(DefaultTSDBService.class);
		mailService = mock(DefaultMailService.class);
		when(mailService.sendMessage(any())).thenReturn(true);

		when(systemConfigConsumerOffset.getValue(SystemConfiguration.Property.DC_LIST)).thenReturn(String.join(",", DC_LIST));
		setupDefaultSysConfigValues(systemConfigConsumerOffset, DataLagService.Property.DATA_LAG_ENFORCE_DC_LIST, "DC4");
		setupDefaultSysConfigValues(systemConfigConsumerOffset, DataLagMonitorConsumerOffset.Property.DATA_LAG_DEFAULT_EXPRESSION, DEFAULT_EXPRESSION);
		setupDefaultSysConfigValues(systemConfigConsumerOffset, DataLagMonitorConsumerOffset.Property.DATA_LAG_INERTIA, "60000");
		setupDefaultSysConfigValues(systemConfigConsumerOffset, DataLagMonitorConsumerOffset.Property.DATA_LAG_CLEAR_THRESHOLD, "{\"16\":[\"DC1\"], \"13\": [\"DC3\"]}");
		setupDefaultSysConfigValues(systemConfigConsumerOffset, DataLagMonitorConsumerOffset.Property.DATA_LAG_DEFAULT_CLEAR_THRESHOLD, "10");
		setupDefaultSysConfigValues(systemConfigConsumerOffset, DataLagMonitorConsumerOffset.Property.DATA_LAG_TRIGGER_THRESHOLD, "{\"36\":[\"DC1\",\"DC2\"], \"33\": [\"DC3\"]}");
		setupDefaultSysConfigValues(systemConfigConsumerOffset, DataLagMonitorConsumerOffset.Property.DATA_LAG_DEFAULT_TRIGGER_THRESHOLD, "20");
		setupDefaultSysConfigValues(systemConfigConsumerOffset, DataLagMonitorConsumerOffset.Property.DATA_LAG_QUERY_EXPRESSION, "{\"-5m:scope.test:metric.test{groupId=*testGroupId*,topic=*test.#DC#.topic*}:max:1m-max\":[\"DC1\",\"DC2\",\"DC3\"]}");
		setupDefaultSysConfigValues(systemConfigConsumerOffset, DataLagMonitorConsumerOffset.Property.DATA_LAG_DEBUG, "false");
		setupDefaultSysConfigValues(systemConfigConsumerOffset, DataLagMonitorConsumerOffset.Property.DATA_LAG_EMAIL, "test@example.com");

		when(systemConfigGoldenMetric.getValue(SystemConfiguration.Property.DC_LIST)).thenReturn(String.join(",", DC_LIST));
		setupDefaultSysConfigValues(systemConfigGoldenMetric, DataLagService.Property.DATA_LAG_ENFORCE_DC_LIST, "DC4");
		setupDefaultSysConfigValues(systemConfigGoldenMetric, DataLagMonitorGoldenMetric.Property.DATA_LAG_DEFAULT_EXPRESSION, DEFAULT_EXPRESSION);
		setupDefaultSysConfigValues(systemConfigGoldenMetric, DataLagMonitorGoldenMetric.Property.DATA_LAG_THRESHOLD, "10000"); // 10 seconds threshold.
		setupDefaultSysConfigValues(systemConfigGoldenMetric, DataLagMonitorGoldenMetric.Property.DATA_LAG_QUERY_EXPRESSION, "{\"-1h:scope.#DC#.test:metric.test:avg:1m-sum\":[\"DC1\",\"DC2\"], \"-4h:scope.#DC#.test2:metric.test2:avg:1m-sum\": [\"DC3\"]}");

	}

	@Test
	public void testQueryForDC() {
		Map<String, String> tags = new HashMap<>();
		tags.put("groupId", "*testGroupId*");
		Long currentTime = System.currentTimeMillis();
		for( String dc: DC_LIST) {
			//ConsumerOffset.
			tags.put("topic", "*test." + dc + ".topic*");
			String expression = "-5m:scope.test:metric.test{groupId=*testGroupId*,topic=*test." + dc.toLowerCase() + ".topic*}:max:1m-max";
			if (dc.equalsIgnoreCase("DC4")) {
				expression = DEFAULT_EXPRESSION;
			}
			List<MetricQuery> mQList = Arrays.asList(new MetricQuery("scope.test", "metric.test", tags, currentTime - 5 * 60 * 1000L, currentTime));
			when(mockedMetricService.getQueries(expression, currentTime)).thenReturn(mQList);
			assertEquals(dc, consumerOffsetService.getDCFromTopic("*test." + dc.toLowerCase() + ".topic*"));

			//GoldenMetric.
			if(dc.equalsIgnoreCase("DC4")) {
				when(mockedMetricService.getMetrics(DEFAULT_EXPRESSION, currentTime)).thenReturn(null);
			} else if(dc.equalsIgnoreCase("DC3")) {
				when(mockedMetricService.getMetrics("-1h:scope." + dc + ".test2:metric.test2:avg:1m-sum", currentTime)).thenReturn(null);
			} else {
				when(mockedMetricService.getMetrics("-1h:scope." + dc + ".test:metric.test:avg:1m-sum", currentTime)).thenReturn(null);
			}
		}
		consumerOffsetService.queryMetricsForDC(new HashSet<>(DC_LIST), currentTime);
		goldenMetricService.queryMetricsForDC(new HashSet<>(DC_LIST), currentTime);
	}

	@Test
	public void testComputeDataLag() {
		Metric triggerM = new Metric("scope.test", "metric.test");
		Metric clearM = new Metric("scope.test", "metric.test");
		Metric noChange = new Metric("scope.test", "metric.test");
		Map<String, Boolean> lagState = new HashMap<>();
		Long currTime = System.currentTimeMillis();
		for(int i = 0 ; i < 10; i++) {
			triggerM.addDatapoint(  currTime - (i + 1) * 20_000, 42.0  + i % 3);
			clearM.addDatapoint(currTime - i * 1000, 1.0 + i % 4);
			noChange.addDatapoint(currTime - i * 3000, i * 2.0); // Not all datapoints satisfy clear criterion.
		}

		assertTrue(consumerOffsetService.computeDataLag("DC1", Arrays.asList(triggerM)));
		assertFalse(consumerOffsetService.computeDataLag("DC1", Arrays.asList(clearM)));

		assertTrue(goldenMetricService.computeDataLag("DC1", Arrays.asList(triggerM)));
		assertFalse(goldenMetricService.computeDataLag("DC1", Arrays.asList(clearM)));

		lagState.put("DC1", true); // Default to true and it should not change.
		TestUtils.setField(consumerOffsetService, "lagStatePerDC", lagState);
		assertTrue(consumerOffsetService.computeDataLag("DC1", Arrays.asList(noChange)));
		assertFalse(goldenMetricService.computeDataLag("DC1", Arrays.asList(noChange)));
	}

	@Test
	public void testIsDataLagging() {
		assertTrue(consumerOffsetService.isDataLagging("DC4")); //enforceLagPresentSet.
		assertFalse(consumerOffsetService.isDataLagging("Dc1 "));
		assertFalse(consumerOffsetService.isDataLagging("dc5"));
		assertTrue(goldenMetricService.isDataLagging("DC4")); //enforceLagPresentSet.
		assertFalse(goldenMetricService.isDataLagging("dc2 "));
		assertFalse(goldenMetricService.isDataLagging("dc5"));

		Map<String, Boolean> lagState = new HashMap<>();
		// Set all dc to enable data lag.
		DC_LIST.forEach(dc -> lagState.put(dc, true));
		TestUtils.setField(consumerOffsetService, "lagStatePerDC", lagState);
		assertTrue(consumerOffsetService.isDataLagging(" dC8 "));
		assertTrue(consumerOffsetService.isDataLagging("  dC3"));

		TestUtils.setField(goldenMetricService, "_isDataLaggingbyDCMap", lagState);
		assertTrue(goldenMetricService.isDataLagging(" dC8 "));
		assertTrue(goldenMetricService.isDataLagging("  dC1"));

		//Set some dc data lag state to be true and check for rest.
		List<String> enableDataLagForSomeDC = Arrays.asList("DC1", "DC3");
		DC_LIST.forEach(dc -> lagState.put(dc, false));
		enableDataLagForSomeDC.forEach(dc -> lagState.put(dc, true));
		TestUtils.setField(consumerOffsetService, "lagStatePerDC", lagState);
		TestUtils.setField(goldenMetricService, "_isDataLaggingbyDCMap", lagState);
		DC_LIST.forEach(dc -> {
			if (enableDataLagForSomeDC.contains(dc) || dc.equalsIgnoreCase("DC4")) {
				assertTrue(consumerOffsetService.isDataLagging(dc));
				assertTrue(goldenMetricService.isDataLagging(dc));
			} else {
				assertFalse(consumerOffsetService.isDataLagging(dc));
				assertFalse(goldenMetricService.isDataLagging(dc));
			}
		} );
	}

	@Test
	public void testPushMetric() {
		ArgumentCaptor<List<Metric>> captor = ArgumentCaptor.forClass(List.class);
		List<Metric> expectedOutput = new ArrayList<>();
		doNothing().when(mockedTSDBService).putMetrics(anyList());
		for( String dc: DC_LIST) {
			Long currentTime = System.currentTimeMillis();
			Metric m = new Metric("argus.core", "datalag.offset");
			m.setTag("dc", dc);
			m.setTag("host", SystemConfiguration.getHostname());
			m.addDatapoint(currentTime, 1.0);
			consumerOffsetService.pushMetric(currentTime, 1.0, dc);
			expectedOutput.add(m);

			m = new Metric(m);
			m.setMetric("datalag.seconds");
			goldenMetricService.pushMetric(currentTime, 1.0, dc);
			expectedOutput.add(m);
		}
		verify(mockedTSDBService, times(8)).putMetrics(captor.capture());
		List<Metric> actualOutput = captor.getAllValues().stream().flatMap(Collection::stream).collect(Collectors.toList());
		Collections.sort(actualOutput);
		Collections.sort(expectedOutput);
		assertEquals(expectedOutput, actualOutput);
	}

	private void setupDefaultSysConfigValues(SystemConfiguration mocksysConfig, DataLagService.Property p, String reply) {
		when(mocksysConfig.getValue(p.getName(), p.getDefaultValue())).thenReturn(reply);
	}

	private void setupDefaultSysConfigValues(SystemConfiguration mocksysConfig, DataLagMonitorConsumerOffset.Property p, String reply) {
		when(mocksysConfig.getValue(p.getName(), p.getDefaultValue())).thenReturn(reply);
	}

	private void setupDefaultSysConfigValues(SystemConfiguration mocksysConfig, DataLagMonitorGoldenMetric.Property p, String reply) {
		when(mocksysConfig.getValue(p.getName(), p.getDefaultValue())).thenReturn(reply);
	}
}

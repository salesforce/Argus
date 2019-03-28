package com.salesforce.dva.argus.service.tsdb;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.CacheService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.system.SystemConfiguration;


import com.salesforce.dva.argus.system.SystemMain;
import com.salesforce.dva.argus.TestUtils;
import org.junit.BeforeClass;
import org.junit.AfterClass;


@RunWith(MockitoJUnitRunner.class)
public class CachedTSDBServiceTest {
	@Mock
	private MonitorService monitorService;
	@Mock
	private CacheService cacheService;
	@Mock
	private TSDBService tsdbService;
	@Mock
	CachedTSDBService cachedTSDBService;

    static private SystemMain system;


    @BeforeClass
    static public void setUpClass() {
        system = TestUtils.getInstance();
        system.start();
    }

    @AfterClass
    static public void tearDownClass() {
        if (system != null) {
            system.getServiceFactory().getManagementService().cleanupRecords();
            system.stop();
        }
    }

	@Before
	public void setup() throws Exception{
		Constructor<CachedTSDBService> constructor = CachedTSDBService.class.getDeclaredConstructor(SystemConfiguration.class,
				MonitorService.class,CacheService.class,TSDBService.class);
		constructor.setAccessible(true);
		cachedTSDBService = constructor.newInstance(system.getConfiguration(), monitorService,cacheService,tsdbService);
	}

	@Test
	public void testWhenCacheMissShouldCallTSDBServiceGetMetricsMethod() throws Exception {
		long startTime = System.currentTimeMillis()-60*60*1000, endTime = System.currentTimeMillis();
		MetricQuery query = new MetricQuery("scope", "metric", new HashMap<>(),
				startTime, endTime);
		cachedTSDBService.getMetrics(Arrays.asList(query));
		verify(tsdbService,times(1)).getMetrics(any());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testWhenCacheMissShouldReturnMetricsFromOnlyTSDBNShouldNotCallCacheService() {
		long startTime = System.currentTimeMillis()-60*60*1000, endTime = System.currentTimeMillis();
		MetricQuery query = new MetricQuery("scope", "metric", new HashMap<>(),
				startTime, endTime);

		Metric metric = new Metric("scope", "metric");
		Map<Long, Double> dps = new HashMap<>();
		dps.put(startTime,1d);
		metric.setDatapoints(dps);
		Map<MetricQuery, List<Metric>> metricsFromTSDB = new HashMap<>();
		metricsFromTSDB.put(query, Arrays.asList(metric));

		when(tsdbService.getMetrics(any())).thenReturn(metricsFromTSDB);
		Map<MetricQuery, List<Metric>> actual = cachedTSDBService.getMetrics(Arrays.asList(query));
		assertEquals(metricsFromTSDB, actual);
		verify(cacheService, never()).getRange(anySet(), anyInt(), anyInt());
	}

	@SuppressWarnings("unchecked")
	@Test
	public void testWhenMetricsExistInCacheShouldReturnMetricsFromBothCacheNTSDB() throws Exception {
		long currTime = System.currentTimeMillis();
		long startTime = currTime-4*24*60*60*1000, endTime = currTime;
		MetricQuery query = new MetricQuery("scope", "metric", new HashMap<>(),
				startTime, endTime);

		Metric lastHourMetric= new Metric("scope", "metric");
		Map<Long, Double> lastHourDps = new HashMap<>();
		lastHourDps.put(endTime, 2d);
		lastHourMetric.setDatapoints(lastHourDps);
		Map<MetricQuery, List<Metric>> lastHourMetrics = mock(Map.class);
		when(lastHourMetrics.get(any())).thenReturn(Arrays.asList(lastHourMetric));
		when(tsdbService.getMetrics(any())).thenReturn(lastHourMetrics);

		StringBuilder cacheKey = new StringBuilder();
		cacheKey.append(startTime);
		cacheKey.append(":null:scope:metric:{}:AVG:AVG:300000");
		String cacheKeyValue = "{\"metatagsRecord\":null,\"datapoints\":{\""+ startTime+ "\":1},"
				+ "\"metric\":\"metric\",\"units\":null,\"namespace\":null,\"query\":{\"aggregator\":\"AVG\",\"downsampler\":\"AVG\","
				+ "\"downsamplingPeriod\":300000,\"metric\":\"metric\",\"startTimestamp\":1544659200000,\"endTimestamp\":1544816134802,"
				+ "\"scope\":\"scope\",\"tags\":{}},\"displayName\":null,\"scope\":\"scope\",\"uid\":null,\"tags\":{}}";
		Map<String, List<String>> metricsFromCache = new HashMap<>();
		metricsFromCache.put(cacheKey.toString(), Arrays.asList(cacheKeyValue));
		when(cacheService.getRange(anySet(), anyInt(), anyInt())).thenReturn(metricsFromCache);

		Map<MetricQuery, List<Metric>> actual = cachedTSDBService.getMetrics(Arrays.asList(query));

		Metric expectedMetric= new Metric("scope", "metric");
		Map<Long, Double> dps = new HashMap<>();
		dps.put(startTime, 1d);
		dps.put(endTime, 2d);
		expectedMetric.setDatapoints(dps);
		List<Metric> expected = new ArrayList<>();
		expected.add(expectedMetric);
		assertEquals(expected, actual.get(query));
	}
}

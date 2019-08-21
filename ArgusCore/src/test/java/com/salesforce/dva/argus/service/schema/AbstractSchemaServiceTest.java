package com.salesforce.dva.argus.service.schema;

import com.salesforce.dva.argus.TestUtils;
import com.salesforce.dva.argus.entity.Metric;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.service.MonitorService;


import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;

import java.util.Properties;


import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import static org.mockito.Mockito.mock;


/**
 * This test suite tests the bloom filter caching in the AbstractSchemaService class. Although we are instantiating
 * ElasticSearchSchemaService object, the implemtationSpecificPut (which is part of ES Schema Service) has been
 * mocked out. In essence, these tests only test the caching functionality.
 *
 * @author Bhinav Sura (bhinav.sura@salesforce.com)
 *
 */
public class AbstractSchemaServiceTest {

	private int scopesCreatedCount = 0;
	private int metricsCreatedCount = 0;

    private ElasticSearchSchemaService _esSchemaService;
    private SystemConfiguration systemConfig;
    private String myClassName = AbstractSchemaServiceTest.class.getSimpleName();


    @Before
    public void setUpClass() {
        Properties config = new Properties();
        systemConfig = new SystemConfiguration(config);
        MonitorService mockedMonitor = mock(MonitorService.class);
        ElasticSearchUtils mockedElasticSearchUtils = mock(ElasticSearchUtils.class);
        _esSchemaService = new ElasticSearchSchemaService(systemConfig, mockedMonitor, mockedElasticSearchUtils);
    }

	@Test
	public void testPutEverythingCached() {
            List<Metric> metrics = TestUtils.createRandomMetrics(myClassName, "test-scope", "test-metric", 10);

		metrics.addAll(TestUtils.createRandomMetrics(myClassName, null, null, 10));

		ElasticSearchSchemaService spyService = _initializeSpyService(_esSchemaService);

		spyService.put(metrics);

		Set<String> scopeNames = new HashSet<>();

		for(Metric m : metrics)
		{
			scopeNames.add(m.getScope());
		}

		assertEquals(metricsCreatedCount, metrics.size());
		assertEquals(scopesCreatedCount, scopeNames.size());

		spyService.put(metrics);
		initCounters();
		assertEquals(metricsCreatedCount, 0);
		assertEquals(scopesCreatedCount, 0);
	}

	@Test
	public void testPutPartialCached() {
		List<Metric> metrics = TestUtils.createRandomMetrics(myClassName, "test-scope", "test-metric", 10);

		ElasticSearchSchemaService spyService = _initializeSpyService(_esSchemaService);

		spyService.put(metrics);

		Set<String> scopeNames = new HashSet<>();

		for(Metric m : metrics)
		{
			scopeNames.add(m.getScope());
		}

		assertEquals(metricsCreatedCount, metrics.size());
		assertEquals(scopesCreatedCount, scopeNames.size());

		List<Metric> newMetrics = TestUtils.createRandomMetrics(myClassName, null, null, 10);

		// 1st metric already in cache (partial case scenario), and now we call put with both list of metrics

		initCounters();
		spyService.put(metrics);
		spyService.put(newMetrics);

		scopeNames.clear();

		for(Metric m : newMetrics)
		{
			scopeNames.add(m.getScope());
		}

		assertEquals(metricsCreatedCount, newMetrics.size());
		assertEquals(scopesCreatedCount, scopeNames.size());
	}

	@Test
	public void testPutSameMetricWithDifferentTags() {
		List<Metric> metrics = TestUtils.createRandomMetrics(myClassName, "test-scope", "test-metric", 1);
		Metric metric = metrics.get(0);

		ElasticSearchSchemaService spyService = _initializeSpyService(_esSchemaService);
		Set<String> scopeNames = new HashSet<>();
		scopeNames.add(metric.getScope());
		spyService.put(metrics);
		// Both metadata and scope are new
		assertEquals(metricsCreatedCount, 1);
		assertEquals(scopesCreatedCount, 1);

		Map.Entry<String,String> originalTagEntry = metric.getTags().entrySet().iterator().next();
		String originalTagKey = originalTagEntry.getKey();
		String originalTagValue = originalTagEntry.getValue();
		String randomTagKey = TestUtils.createRandomName(AbstractSchemaServiceTest.class.getSimpleName());
		String randomTagValue = TestUtils.createRandomName(AbstractSchemaServiceTest.class.getSimpleName());

		// New tagvalue for same scope:metric should update metric
		initCounters();
		metrics.get(0).setTag(originalTagKey, randomTagValue);
		spyService.put(metrics);
		assertEquals(metricsCreatedCount, 1);
		assertEquals(scopesCreatedCount, 0);

		// New tagkey should update metric
		initCounters();
		metrics.get(0).setTag(randomTagKey, originalTagValue);
		spyService.put(metrics);
		assertEquals(metricsCreatedCount, 1);
		assertEquals(scopesCreatedCount, 0);

		// Same scope:metric:{seentag1=seenvalue1,seentag2=seenvalue2} doesn't need update and shouldn't
		initCounters();
		metrics.get(0).setTag(randomTagKey, originalTagValue);
		spyService.put(metrics);
		assertEquals(metricsCreatedCount, 0);
		assertEquals(scopesCreatedCount, 0);
	}

	@Test
	public void testPutNothingCached() {
		List<Metric> metrics = TestUtils.createRandomMetrics(myClassName, "test-scope", "test-metric", 10);

		metrics.addAll(TestUtils.createRandomMetrics(myClassName, null, null, 10));

		ElasticSearchSchemaService spyService = _initializeSpyService(_esSchemaService);

		// Make implementationSpecificPut specifically NOT add to the bloomfilters on a put
		Mockito.doAnswer((Answer<Void>) invocation -> {
			@SuppressWarnings("unchecked")
			Set<Metric> metricsToCreate = Set.class.cast(invocation.getArguments()[0]);
			Set<String> scopeNamesToCreate = Set.class.cast(invocation.getArguments()[1]);

			metricsCreatedCount += metricsToCreate.size();
			scopesCreatedCount += scopeNamesToCreate.size();

			return null;
		}).when(spyService).implementationSpecificPut(Mockito.any(), Mockito.any(), Mockito.any());

		spyService.put(metrics);

		Set<String> scopeNames = new HashSet<>();

		for(Metric m : metrics)
		{
			scopeNames.add(m.getScope());
		}

		assertEquals(metricsCreatedCount, metrics.size());
		assertEquals(scopesCreatedCount, scopeNames.size());

		spyService.put(metrics);

		assertEquals(metricsCreatedCount, 2 * metrics.size());
		assertEquals(scopesCreatedCount, 2 * scopeNames.size());
	}

	private ElasticSearchSchemaService _initializeSpyService(ElasticSearchSchemaService service) {
		ElasticSearchSchemaService spyService = Mockito.spy(service);
		initCounters();

		Mockito.doAnswer((Answer<Void>) invocation -> {
			@SuppressWarnings("unchecked")
			Set<Metric> metricsToCreate = Set.class.cast(invocation.getArguments()[0]);
			Set<String> scopeNamesToCreate = Set.class.cast(invocation.getArguments()[1]);

			metricsCreatedCount += metricsToCreate.size();
			scopesCreatedCount += scopeNamesToCreate.size();

			// Simulate a successful put, which will add to the corresponding bloomsfilters
			if (metricsToCreate.size() > 0) {
				service._addToModifiedBloom(spyService._fracture(metricsToCreate).get(0));
			}
			if (scopeNamesToCreate.size() > 0) {
				service._addToModifiedBloom(spyService._fractureScopes(scopeNamesToCreate).get(0));
			}

			return null;
		}).when(spyService).implementationSpecificPut(Mockito.any(), Mockito.any(), Mockito.any());
		return spyService;
	}

	private void initCounters() {
		scopesCreatedCount = 0;
		metricsCreatedCount = 0;
	}

	@Test
	public void testNumHoursUntilNextClearBloomFilter() {
		Calendar calendar = Calendar.getInstance();
		int hour = calendar.get(Calendar.HOUR_OF_DAY);
		// Will wait 24 hours before next flush if at same hour boundary
		int secondsUntil = _esSchemaService.getNumSecondsUntilNthHourOfDay(hour, calendar);
		assertTrue("secondsUntil should be between 23 hours and 24 hours but was " + secondsUntil,secondsUntil >= 23 * 60 * 60 && secondsUntil <= 24 * 60 * 60);

		calendar.set(Calendar.HOUR_OF_DAY, Math.floorMod(hour - 2, 24));
		secondsUntil = _esSchemaService.getNumSecondsUntilNthHourOfDay(hour, calendar);
		assertTrue("secondsUntil should be between 1 hours and 2 hours but was " + secondsUntil,secondsUntil >= 1 * 60 * 60 && secondsUntil <= 2 * 60 * 60);

		calendar.set(Calendar.HOUR_OF_DAY, Math.floorMod(hour + 2, 24));
		secondsUntil = _esSchemaService.getNumSecondsUntilNthHourOfDay(hour, calendar);
		assertTrue("secondsUntil should be between 21 hours and 22 hours but was " + secondsUntil, secondsUntil >= 21 * 60 * 60 && secondsUntil <= 22 * 60 * 60);
	}

	@Test
	public void testNumHoursUntilNextFlushBloomFilter() {
		// use Wednesday 6 AM this week as start date
		Calendar wedAtSix = Calendar.getInstance();
		wedAtSix.set(Calendar.HOUR_OF_DAY, 6);
		wedAtSix.set(Calendar.DAY_OF_WEEK, 4);

		// Test Sunday, Monday Tuesday, Wednesday of next week @ 4 AM
		for (int dayIndex = 0; dayIndex < 3; dayIndex++) {
			int nthHour = dayIndex * 24 + 4;
			int secondsUntil = _esSchemaService.getNumSecondsUntilNthHourOfWeek(nthHour, wedAtSix);
			int floorHoursUntil = secondsUntil / 60 / 60;
			int expectedHours = (4 + dayIndex) * 24 - 2;
			assertTrue("hoursUntil should be between " + (expectedHours - 1) + " and " + expectedHours, expectedHours - 1 <= floorHoursUntil && floorHoursUntil <= expectedHours);
		}
		// Test Wednesday Thursday, Fri, Sat of this week @ 8 AM
		for (int dayIndex = 3; dayIndex < 7; dayIndex++) {
			int nthHour = dayIndex * 24 + 8;
			int secondsUntil = _esSchemaService.getNumSecondsUntilNthHourOfWeek(nthHour, wedAtSix);
			int floorHoursUntil = secondsUntil / 60 / 60;
			int expectedHours = (dayIndex - 3) * 24 + 2;
			assertTrue("hoursUntil should be between " + (expectedHours - 1) + " and " + expectedHours, expectedHours - 1 <= floorHoursUntil && floorHoursUntil <= expectedHours);
		}
	}
}

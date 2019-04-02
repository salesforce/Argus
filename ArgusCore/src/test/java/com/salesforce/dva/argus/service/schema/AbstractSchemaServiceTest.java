package com.salesforce.dva.argus.service.schema;

import com.salesforce.dva.argus.TestUtils;
import com.salesforce.dva.argus.entity.Metric;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import com.salesforce.dva.argus.system.SystemMain;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import com.salesforce.dva.argus.service.MonitorService;


import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Before;
import org.junit.BeforeClass;
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
	private int scopesModifiedCount = 0;
	private int metricsModifiedCount = 0;

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
		assertEquals(metricsModifiedCount, 0);
		assertEquals(scopesModifiedCount, 0);

		spyService.put(metrics);
		initCounters();
		assertEquals(metricsCreatedCount, 0);
		assertEquals(scopesCreatedCount, 0);
		assertEquals(metricsModifiedCount, 0);
		assertEquals(scopesModifiedCount, 0);
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
		assertEquals(metricsModifiedCount, 0);
		assertEquals(scopesModifiedCount, 0);
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
		assertEquals(metricsModifiedCount, 0);
		assertEquals(scopesModifiedCount, 0);

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
		assertEquals(metricsModifiedCount, 0);
		assertEquals(scopesModifiedCount, 0);

		// New tagkey should update metric
		initCounters();
		metrics.get(0).setTag(randomTagKey, originalTagValue);
		spyService.put(metrics);
		assertEquals(metricsCreatedCount, 1);
		assertEquals(scopesCreatedCount, 0);
		assertEquals(metricsModifiedCount, 0);
		assertEquals(scopesModifiedCount, 0);

		// Same scope:metric:{seentag1=seenvalue1,seentag2=seenvalue2} doesn't need update and shouldn't
		initCounters();
		metrics.get(0).setTag(randomTagKey, originalTagValue);
		spyService.put(metrics);
		assertEquals(metricsCreatedCount, 0);
		assertEquals(scopesCreatedCount, 0);
		assertEquals(metricsModifiedCount, 0);
		assertEquals(scopesModifiedCount, 0);
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
			Set<Metric> metricsToUpdate = Set.class.cast(invocation.getArguments()[1]);

			Set<String> scopeNamesToCreate = Set.class.cast(invocation.getArguments()[2]);
			Set<String> scopeNamesToUpdate = Set.class.cast(invocation.getArguments()[3]);

			metricsCreatedCount += metricsToCreate.size();
			metricsModifiedCount += metricsToUpdate.size();
			scopesCreatedCount += scopeNamesToCreate.size();
			scopesModifiedCount += scopeNamesToUpdate.size();

			return null;
		}).when(spyService).implementationSpecificPut(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());

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
			Set<Metric> metricsToUpdate = Set.class.cast(invocation.getArguments()[1]);

			Set<String> scopeNamesToCreate = Set.class.cast(invocation.getArguments()[2]);
			Set<String> scopeNamesToUpdate = Set.class.cast(invocation.getArguments()[3]);

			metricsCreatedCount += metricsToCreate.size();
			metricsModifiedCount += metricsToUpdate.size();
			scopesCreatedCount += scopeNamesToCreate.size();
			scopesModifiedCount += scopeNamesToUpdate.size();

			// Simulate a successful put, which will add to the corresponding bloomsfilters
			if (metricsToCreate.size() > 0) {
				service._addToCreatedBloom(spyService._fracture(metricsToCreate).get(0));
				service._addToModifiedBloom(spyService._fracture(metricsToCreate).get(0));
			}
			if (scopeNamesToCreate.size() > 0) {
				service._addToCreatedBloom(spyService._fractureScopes(scopeNamesToCreate).get(0));
				service._addToModifiedBloom(spyService._fractureScopes(scopeNamesToCreate).get(0));
			}
			if (metricsToUpdate.size() > 0) {
				service._addToModifiedBloom(spyService._fracture(metricsToUpdate).get(0));
			}
			if (scopeNamesToUpdate.size() > 0) {
				service._addToModifiedBloom(spyService._fractureScopes(scopeNamesToUpdate).get(0));
			}

			return null;
		}).when(spyService).implementationSpecificPut(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
		return spyService;
	}

	private void initCounters() {
		scopesCreatedCount = 0;
		metricsCreatedCount = 0;
		scopesModifiedCount = 0;
		metricsModifiedCount = 0;
	}

	@Test
	public void testNumHoursUntilNextFlushBloomFilter() {
		Calendar calendar = Calendar.getInstance();

		// Will wait 24 hours before next flush if at same hour boundary
		int hour = calendar.get(Calendar.HOUR_OF_DAY);
		int secondsUntil = _esSchemaService.getNumSecondsUntilTargetHour(hour);
		assertTrue(secondsUntil >= 23 * 60 * 60 && secondsUntil <= 24 * 60 * 60);
	}
}

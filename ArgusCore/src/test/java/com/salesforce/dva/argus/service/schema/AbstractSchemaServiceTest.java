package com.salesforce.dva.argus.service.schema;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.tuple.Pair;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Metric;


/**
 * This test suite tests the bloom filter caching in the AbstractSchemaService class. Although we are instantiating
 * ElasticSearchSchemaService object, the implemtationSpecificPut (which is part of ES Schema Service) has been
 * mocked out. In essence, these tests only test the caching functionality.
 *
 * @author Bhinav Sura (bhinav.sura@salesforce.com)
 *
 */
public class AbstractSchemaServiceTest extends AbstractTest {

	private int scopesCount = 0;
	private int scopeAndMetricsCount = 0;
	private int metricsCount = 0;

	@Test
	public void testPutEverythingCached() {
		List<Metric> metrics = createRandomMetrics("test-scope", "test-metric", 10);

		metrics.addAll(createRandomMetrics(null, null, 10));

		ElasticSearchSchemaService service = new ElasticSearchSchemaService(system.getConfiguration(), system.getServiceFactory().getMonitorService());

		ElasticSearchSchemaService spyService = _initializeSpyService(service);

		spyService.put(metrics);

		Set<String> scopeNames = new HashSet<>();
		Set<Pair<String, String>> scopeAndMetricNames = new HashSet<>();

		for(Metric m : metrics)
		{
			scopeNames.add(m.getScope());
			scopeAndMetricNames.add(Pair.of(m.getScope(), m.getMetric()));
		}

		assertEquals(metricsCount, metrics.size());
		assertEquals(scopeAndMetricsCount, scopeAndMetricNames.size());
		assertEquals(scopesCount, scopeNames.size());

		// add to bloom filter cache
		spyService._addToBloomFilter(spyService._fracture(metrics).get(0));
		spyService._addToBloomFilterScopeAndMetricOnly(spyService._fractureScopeAndMetrics(scopeAndMetricNames).get(0));
		spyService._addToBloomFilterScopeOnly(spyService._fractureScopes(scopeNames).get(0));

		spyService.put(metrics);
		// count should be same since we are re-reading cached value

		assertEquals(metricsCount, metrics.size());
		assertEquals(scopeAndMetricsCount, scopeAndMetricNames.size());
		assertEquals(scopesCount, scopeNames.size());
	}

	@Test
	public void testPutPartialCached() {
		List<Metric> metrics = createRandomMetrics("test-scope", "test-metric", 10);

		ElasticSearchSchemaService service = new ElasticSearchSchemaService(system.getConfiguration(), system.getServiceFactory().getMonitorService());
		ElasticSearchSchemaService spyService = _initializeSpyService(service);

		spyService.put(metrics);

		Set<String> scopeNames = new HashSet<>();
		Set<Pair<String, String>> scopeAndMetricNames = new HashSet<>();

		for(Metric m : metrics)
		{
			scopeNames.add(m.getScope());
			scopeAndMetricNames.add(Pair.of(m.getScope(), m.getMetric()));
		}

		assertEquals(metricsCount, metrics.size());
		assertEquals(scopeAndMetricsCount, scopeAndMetricNames.size());
		assertEquals(scopesCount, scopeNames.size());

		// add to bloom filter cache
		spyService._addToBloomFilter(spyService._fracture(metrics).get(0));
		spyService._addToBloomFilterScopeAndMetricOnly(spyService._fractureScopeAndMetrics(scopeAndMetricNames).get(0));
		spyService._addToBloomFilterScopeOnly(spyService._fractureScopes(scopeNames).get(0));

		List<Metric> newMetrics = createRandomMetrics(null, null, 10);

		// 1st metric already in cache (partial case scenario), and now we call put with both list of metrics

		initCounters();
		spyService.put(metrics);
		spyService.put(newMetrics);

		scopeNames.clear();
		scopeAndMetricNames.clear();

		for(Metric m : newMetrics)
		{
			scopeNames.add(m.getScope());
			scopeAndMetricNames.add(Pair.of(m.getScope(), m.getMetric()));
		}

		assertEquals(metricsCount, newMetrics.size());
		assertEquals(scopeAndMetricsCount, scopeAndMetricNames.size());
		assertEquals(scopesCount, scopeNames.size());
	}

	@Test
	public void testPutNothingCached() {
		List<Metric> metrics = createRandomMetrics("test-scope", "test-metric", 10);

		metrics.addAll(createRandomMetrics(null, null, 10));

		ElasticSearchSchemaService service = new ElasticSearchSchemaService(system.getConfiguration(), system.getServiceFactory().getMonitorService());
		ElasticSearchSchemaService spyService = _initializeSpyService(service);

		spyService.put(metrics);

		Set<String> scopeNames = new HashSet<>();
		Set<Pair<String, String>> scopeAndMetricNames = new HashSet<>();

		for(Metric m : metrics)
		{
			scopeNames.add(m.getScope());
			scopeAndMetricNames.add(Pair.of(m.getScope(), m.getMetric()));
		}

		assertEquals(metricsCount, metrics.size());
		assertEquals(scopeAndMetricsCount, scopeAndMetricNames.size());
		assertEquals(scopesCount, scopeNames.size());

		spyService.put(metrics);

		assertEquals(metricsCount, 2 * metrics.size());
		assertEquals(scopeAndMetricsCount, 2 * scopeAndMetricNames.size());
		assertEquals(scopesCount, 2 * scopeNames.size());
	}

	private ElasticSearchSchemaService _initializeSpyService(ElasticSearchSchemaService service) {
		ElasticSearchSchemaService spyService = Mockito.spy(service);
		initCounters();

		Mockito.doAnswer(new Answer<Void>() {
			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				@SuppressWarnings("unchecked")
				List<Metric> metrics = List.class.cast(invocation.getArguments()[0]);

				Set<String> scopeNames = Set.class.cast(invocation.getArguments()[1]);

				Set<Pair<String, String>> scopeAndMetricNames = Set.class.cast(invocation.getArguments()[2]);

				scopesCount += scopeNames.size();
				scopeAndMetricsCount += scopeAndMetricNames.size();
				metricsCount += metrics.size();

				return null;
			}
		}).when(spyService).implementationSpecificPut(Mockito.any(), Mockito.any(), Mockito.any(), Mockito.any());
		return spyService;
	}

	private void initCounters() {
		scopesCount = 0;
		scopeAndMetricsCount = 0;
		metricsCount = 0;
	}

	@Test
	public void testNumHoursUntilNextFlushBloomFilter() {
		ElasticSearchSchemaService service = new ElasticSearchSchemaService(system.getConfiguration(), system.getServiceFactory().getMonitorService());

		Calendar calendar = Calendar.getInstance();

		// Will wait 24 hours before next flush if at same hour boundary
		int hour = calendar.get(Calendar.HOUR_OF_DAY);
		assertTrue(service.getNumHoursUntilTargetHour(hour) == 24);
	}
}

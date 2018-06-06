package com.salesforce.dva.argus.service.schema;

import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.MetricSchemaRecord;
import com.salesforce.dva.argus.service.schema.ElasticSearchSchemaService;


/**
 * This test suite tests the bloom filter caching in the AbstractSchemaService class. Although we are instantiating 
 * ElasticSearchSchemaService object, the implemtationSpecificPut (which is part of ES Schema Service) has been 
 * mocked out. In essence, these tests only test the caching functionality. 
 * 
 * @author Bhinav Sura (bhinav.sura@salesforce.com)
 *
 */
public class AbstractSchemaServiceTest extends AbstractTest {
	
	@Test
	public void testPutEverythingCached() {
		List<Metric> metrics = createRandomMetrics("test-scope", "test-metric", 10);
		ElasticSearchSchemaService service = new ElasticSearchSchemaService(system.getConfiguration(), system.getServiceFactory().getMonitorService());
		final AtomicInteger count = new AtomicInteger();
		ElasticSearchSchemaService spyService = _initializeSpyService(service, count);
		
		spyService.put(metrics);
		// add to bloom filter cache
		spyService._addToBloomFilter(spyService._fracture(metrics).get(0));
		assertTrue(count.get() == metrics.size());
		spyService.put(metrics);
		// count should be same since we are re-reading cached value
		assertTrue(count.get() == metrics.size());
	}

	@Test
	public void testPutPartialCached() {
		List<Metric> metrics = createRandomMetrics("test-scope", "test-metric", 10);
		List<Metric> newMetrics = createRandomMetrics("test-scope", "test-metric1", 5);
		Set<Metric> total = new HashSet<>(metrics);
		total.addAll(newMetrics);
		
		ElasticSearchSchemaService service = new ElasticSearchSchemaService(system.getConfiguration(), system.getServiceFactory().getMonitorService());
		final AtomicInteger count = new AtomicInteger();
		ElasticSearchSchemaService spyService = _initializeSpyService(service, count);
		
		spyService.put(metrics);
		// 1st metric cached
		spyService._addToBloomFilter(spyService._fracture(metrics).get(0));
		assertTrue(count.get() == metrics.size());
		// 1st metric already in cache (partial case scenario), and now 2nd metric will also be added to cache.
		// Total number of metrics in cache = metric1.size() and metric2.size()
		spyService.put(new ArrayList<>(total));
		spyService._addToBloomFilter(spyService._fracture(new ArrayList<>(total)).get(0));
		assertTrue(count.get() == total.size());
	}
	
	@Test
	public void testPutNothingCached() {
		List<Metric> metrics = createRandomMetrics("test-scope", "test-metric", 10);
		List<Metric> newMetrics = createRandomMetrics("test-scope", "test-metric1", 5);
		
		ElasticSearchSchemaService service = new ElasticSearchSchemaService(system.getConfiguration(), system.getServiceFactory().getMonitorService());
		final AtomicInteger count = new AtomicInteger();
		ElasticSearchSchemaService spyService = _initializeSpyService(service, count);
		
		spyService.put(metrics);
		assertTrue(count.get() == metrics.size());
		spyService.put(newMetrics);
		assertTrue(count.get() == metrics.size() + newMetrics.size());
	}

	private ElasticSearchSchemaService _initializeSpyService(ElasticSearchSchemaService service, final AtomicInteger count) {
		ElasticSearchSchemaService spyService = Mockito.spy(service);
		
		Mockito.doAnswer(new Answer<Void>() {
			@Override
			public Void answer(InvocationOnMock invocation) throws Throwable {
				@SuppressWarnings("unchecked")
				List<Metric> metrics = List.class.cast(invocation.getArguments()[0]);
				 count.addAndGet(metrics.size());
				 return null;
			}
		}).when(spyService).implementationSpecificPut(Mockito.anyListOf(Metric.class));
		return spyService;
	}
	
	@Test
	public void getNumHoursUntilNextFlushBloomFilter() {
		ElasticSearchSchemaService service = new ElasticSearchSchemaService(system.getConfiguration(), system.getServiceFactory().getMonitorService());
		
		Calendar calendar = Calendar.getInstance();
		
		// Will wait 24 hours before next flush if at same hour boundary
		int hour = calendar.get(Calendar.HOUR_OF_DAY);
		assertTrue(service.getNumHoursUntilTargetHour(hour) == 24);
	}
}

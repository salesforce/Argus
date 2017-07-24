package com.salesforce.dva.argus.service.metric.transform;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Test;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.service.tsdb.MetricScanner;

public class DivideValueReducerOrMappingScannerTest extends AbstractTest {

	protected static MetricQuery toQuery(Metric metric) {
		TreeMap<Long, Double> datapoints = new TreeMap<>(metric.getDatapoints());
		Long start = datapoints.firstKey();
		Long end = datapoints.lastKey();
		
		return new MetricQuery(metric.getScope(), metric.getMetric(), metric.getTags(), start, end);
	}
	
	private List<MetricQuery> toQueries(List<Metric> expected) {
		List<MetricQuery> queries = new LinkedList<>();
		
		for (Metric metric : expected) {
			queries.add(toQuery(metric));
		}
		return queries;
	}

	private Map<MetricQuery, List<Metric>> filterOver(Metric m, Long startTime, MetricQuery query) {
		Metric mini = new Metric(m);
		Map<Long, Double> filteredDP = new TreeMap<>();
		for (Long key : m.getDatapoints().keySet()) {
			if (key >= startTime) {
				filteredDP.put(key, m.getDatapoints().get(key));
			}
		}
		mini.setDatapoints(filteredDP);
		Map<MetricQuery, List<Metric>> result = new HashMap<>();
		List<Metric> l = new ArrayList<>();
		l.add(mini);
		result.put(query, l);
		return result;
	}
	
	private Map<MetricQuery, List<Metric>> outOfBounds() {
		return new HashMap<>();
	}
	
	private Metric lowElems(Metric metric, Long bound) {
		Metric mini = new Metric(metric);
		Map<Long, Double> filteredDP = new TreeMap<Long, Double>();
		
		for (Long time : metric.getDatapoints().keySet()) {
			if (time <= bound) {
				filteredDP.put(time, metric.getDatapoints().get(time));
			}
		}
		mini.setDatapoints(filteredDP);
		return mini;
	}
	
	@Test
	public void testDiffValueReducerWithoutNullOrZero() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		List<MetricQuery> queries = toQueries(metrics);
		List<MetricScanner> scanners = new ArrayList<>();
		
		for (int i = 0; i < metrics.size(); i++) {
			Metric m = metrics.get(i);
			MetricQuery q = queries.get(i);
						
			Long bound = q.getStartTimestamp() + (q.getEndTimestamp() - q.getStartTimestamp()) / 2;
			List<MetricQuery> highQuery = new ArrayList<>();
			highQuery.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), bound, q.getEndTimestamp()));
			List<MetricQuery> tooHigh = new ArrayList<>();
			tooHigh.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getEndTimestamp(), q.getEndTimestamp()));
			
			MetricScanner s = new MetricScanner(lowElems(m, bound), q, serviceMock, bound);
			scanners.add(s);
			
			when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
			when(serviceMock.getMetrics(highQuery)).thenReturn(filterOver(m, bound, highQuery.get(0)));
		}
		
		DivideValueReducerOrMapping redMap = new DivideValueReducerOrMapping();
		
		for (int i = 0; i < metrics.size(); i++) {
			Double expected = redMap.reduce(new ArrayList<>(metrics.get(i).getDatapoints().values()));
			Double actual = redMap.reduceScanner(scanners.get(i));
			
			assert(expected.equals(actual));
		}
	}
	
	@Test
	public void testDivideValueReducerWithNull() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		
		for (Metric m : metrics) {
			Long timestamp = Collections.min(m.getDatapoints().keySet()) + (Collections.max(m.getDatapoints().keySet()) - Collections.min(m.getDatapoints().keySet())) / 2;
			if (m.getDatapoints().size() == 1) {
				timestamp = Collections.min(m.getDatapoints().keySet()) + 1000L;	// set it to something else so the first time is not null
			}
			Map<Long, Double> dps = new HashMap<>(m.getDatapoints());
			dps.put(timestamp, null);
			m.setDatapoints(dps);
		}
		
		List<MetricQuery> queries = toQueries(metrics);
		List<MetricScanner> scanners = new ArrayList<>();
		
		for (int i = 0; i < metrics.size(); i++) {
			Metric m = metrics.get(i);
			MetricQuery q = queries.get(i);
						
			Long bound = q.getStartTimestamp() + (q.getEndTimestamp() - q.getStartTimestamp()) / 2;
			List<MetricQuery> highQuery = new ArrayList<>();
			highQuery.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), bound, q.getEndTimestamp()));
			List<MetricQuery> tooHigh = new ArrayList<>();
			tooHigh.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getEndTimestamp(), q.getEndTimestamp()));
			
			MetricScanner s = new MetricScanner(lowElems(m, bound), q, serviceMock, bound);
			scanners.add(s);
			
			when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
			when(serviceMock.getMetrics(highQuery)).thenReturn(filterOver(m, bound, highQuery.get(0)));
		}
		
		DivideValueReducerOrMapping redMap = new DivideValueReducerOrMapping();
		
		for (int i = 0; i < metrics.size(); i++) {
			Double expected = redMap.reduce(new ArrayList<>(metrics.get(i).getDatapoints().values()));
			Double actual = redMap.reduceScanner(scanners.get(i));
			
			if (metrics.get(i).getDatapoints().size() > 1) {
				assert(expected.equals(actual));
			}
		}
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testDivideValueReducerWithZero() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		
		for (Metric m : metrics) {
			Long third = (Collections.max(m.getDatapoints().keySet()) - Collections.min(m.getDatapoints().keySet())) / 3;
			Long timestamp2 = Collections.max(m.getDatapoints().keySet()) - third;
			Map<Long, Double> dps = new HashMap<>(m.getDatapoints());
			dps.put(timestamp2, 0.0);
			m.setDatapoints(dps);
		}
		
		List<MetricQuery> queries = toQueries(metrics);
		List<MetricScanner> scanners = new ArrayList<>();
		
		for (int i = 0; i < metrics.size(); i++) {
			Metric m = metrics.get(i);
			MetricQuery q = queries.get(i);
						
			Long bound = q.getStartTimestamp() + (q.getEndTimestamp() - q.getStartTimestamp()) / 2;
			List<MetricQuery> highQuery = new ArrayList<>();
			highQuery.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), bound, q.getEndTimestamp()));
			List<MetricQuery> tooHigh = new ArrayList<>();
			tooHigh.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getEndTimestamp(), q.getEndTimestamp()));
			
			MetricScanner s = new MetricScanner(lowElems(m, bound), q, serviceMock, bound);
			scanners.add(s);
			
			when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
			when(serviceMock.getMetrics(highQuery)).thenReturn(filterOver(m, bound, highQuery.get(0)));
		}
		
		DivideValueReducerOrMapping redMap = new DivideValueReducerOrMapping();
		
		for (int i = 0; i < metrics.size(); i++) {
			if (metrics.get(i).getDatapoints().size() > 1) {	// if there is only one datapoint, which is zero, that's fine
				redMap.reduceScanner(scanners.get(i));
			}
		}
	}
	
	@Test
	public void testDivideValueMapping() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		List<MetricQuery> queries = toQueries(metrics);
		List<MetricScanner> scanners = new ArrayList<>();
		
		for (int i = 0; i < metrics.size(); i++) {
			Metric m = metrics.get(i);
			MetricQuery q = queries.get(i);
						
			Long bound = q.getStartTimestamp() + (q.getEndTimestamp() - q.getStartTimestamp()) / 2;
			List<MetricQuery> highQuery = new ArrayList<>();
			highQuery.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), bound, q.getEndTimestamp()));
			List<MetricQuery> tooHigh = new ArrayList<>();
			tooHigh.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getEndTimestamp(), q.getEndTimestamp()));
			
			MetricScanner s = new MetricScanner(lowElems(m, bound), q, serviceMock, bound);
			scanners.add(s);
			
			when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
			when(serviceMock.getMetrics(highQuery)).thenReturn(filterOver(m, bound, highQuery.get(0)));
		}
		
		DivideValueReducerOrMapping redMap = new DivideValueReducerOrMapping();
		List<String> constants = new ArrayList<>();
		constants.add("" + random.nextDouble());
		
		assert(!constants.get(0).equals("0.0"));
		
		for (int i = 0; i < metrics.size(); i++) {
			Map<Long, Double> expected = redMap.mapping(metrics.get(i).getDatapoints(), constants);
			Map<Long, Double> actual = redMap.mappingScanner(scanners.get(i), constants);
			
			assert(expected.equals(actual));
			assert(!MetricScanner.existingScanner(metrics.get(i), queries.get(i)));
			constants.set(0, "" + random.nextDouble());
		}
	}
	
	@Test
	public void testDPIntegrityAndDisposal() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		
		for (Metric m : metrics) {
			Long timestamp = Collections.min(m.getDatapoints().keySet()) + (Collections.max(m.getDatapoints().keySet()) - Collections.min(m.getDatapoints().keySet())) / 2;
			if (m.getDatapoints().size() == 1) {
				timestamp = Collections.min(m.getDatapoints().keySet()) + 1000L;	// set it to something else so the first time is not null
			}
			Map<Long, Double> dps = new HashMap<>(m.getDatapoints());
			dps.put(timestamp, null);
			m.setDatapoints(dps);
		}
		
		List<MetricQuery> queries = toQueries(metrics);
		List<MetricScanner> scanners = new ArrayList<>();
		
		for (int i = 0; i < metrics.size(); i++) {
			Metric m = metrics.get(i);
			MetricQuery q = queries.get(i);
						
			Long bound = q.getStartTimestamp() + (q.getEndTimestamp() - q.getStartTimestamp()) / 2;
			List<MetricQuery> highQuery = new ArrayList<>();
			highQuery.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), bound, q.getEndTimestamp()));
			List<MetricQuery> tooHigh = new ArrayList<>();
			tooHigh.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getEndTimestamp(), q.getEndTimestamp()));
			
			MetricScanner s = new MetricScanner(lowElems(m, bound), q, serviceMock, bound);
			scanners.add(s);
			
			when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
			when(serviceMock.getMetrics(highQuery)).thenReturn(filterOver(m, bound, highQuery.get(0)));
		}
		
		DivideValueReducerOrMapping redMap = new DivideValueReducerOrMapping();
		
		for (int i = 0; i < metrics.size(); i++) {
			Double expected = redMap.reduce(new ArrayList<>(metrics.get(i).getDatapoints().values()));
			Double actual = redMap.reduceScanner(scanners.get(i));
			
			if (metrics.get(i).getDatapoints().size() > 1) {
				assert(expected.equals(actual));
				assert(!MetricScanner.existingScanner(metrics.get(i), queries.get(i)));
			}
		}
	}
	
}

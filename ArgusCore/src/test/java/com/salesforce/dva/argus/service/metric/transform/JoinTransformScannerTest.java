package com.salesforce.dva.argus.service.metric.transform;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
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

public class JoinTransformScannerTest extends AbstractTest {

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
	public void testJoinTransformSingleList() {
		
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
		
		JoinTransform transform = new JoinTransform();
		
		List<Metric> expected = transform.transform(metrics);
		List<Metric> actual = transform.transformScanner(scanners);
		
		assert(expected.equals(actual));
	}
	
	@Test
	public void testJoinTransformThreeLists() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<List<Metric>> metrics = new ArrayList<>();
		List<List<MetricQuery>> queries = new ArrayList<>();
		List<Metric> metrics1 = createRandomMetrics(null, null, 10);
		List<MetricQuery> queries1 = toQueries(metrics1);
		metrics.add(metrics1);
		queries.add(queries1);
		List<Metric> metrics2 = createRandomMetrics(null, null, 10);
		List<MetricQuery> queries2 = toQueries(metrics2);
		metrics.add(metrics2);
		queries.add(queries2);
		List<Metric> metrics3 = createRandomMetrics(null, null, 10);
		List<MetricQuery> queries3 = toQueries(metrics3);
		metrics.add(metrics3);
		queries.add(queries3);
		
		List<List<MetricScanner>> scanners = new ArrayList<>();
		List<MetricScanner> scanners1 = new ArrayList<>();
		List<MetricScanner> scanners2 = new ArrayList<>();
		List<MetricScanner> scanners3 = new ArrayList<>();
		scanners.add(scanners1);
		scanners.add(scanners2);
		scanners.add(scanners3);
		
		for (int j = 0; j < metrics.size(); j++) {
			for (int i = 0; i < metrics.get(j).size(); i++) {
				Metric m = metrics.get(j).get(i);
				MetricQuery q = queries.get(j).get(i);
							
				Long bound = q.getStartTimestamp() + (q.getEndTimestamp() - q.getStartTimestamp()) / 2;
				List<MetricQuery> highQuery = new ArrayList<>();
				highQuery.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), bound, q.getEndTimestamp()));
				List<MetricQuery> tooHigh = new ArrayList<>();
				tooHigh.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getEndTimestamp(), q.getEndTimestamp()));
				
				MetricScanner s = new MetricScanner(lowElems(m, bound), q, serviceMock, bound);
				scanners.get(j).add(s);
				
				when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
				when(serviceMock.getMetrics(highQuery)).thenReturn(filterOver(m, bound, highQuery.get(0)));
			}
		}
	
		JoinTransform transform = new JoinTransform();
		
		List<Metric> expected = transform.transform(metrics1, metrics2, metrics3);
		List<Metric> actual = transform.transformScanner(scanners1, scanners2, scanners3);
				
		assert(expected.size() == actual.size());
		assert(expected.equals(actual));
	}
	
	@Test
	public void testDPIntegrityAndDisposal() {
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<List<Metric>> metrics = new ArrayList<>();
		List<List<MetricQuery>> queries = new ArrayList<>();
		List<Metric> metrics1 = createRandomMetrics(null, null, 10);
		List<MetricQuery> queries1 = toQueries(metrics1);
		metrics.add(metrics1);
		queries.add(queries1);
		List<Metric> metrics2 = createRandomMetrics(null, null, 10);
		List<MetricQuery> queries2 = toQueries(metrics2);
		metrics.add(metrics2);
		queries.add(queries2);
		List<Metric> metrics3 = createRandomMetrics(null, null, 10);
		List<MetricQuery> queries3 = toQueries(metrics3);
		metrics.add(metrics3);
		queries.add(queries3);
		
		List<List<MetricScanner>> scanners = new ArrayList<>();
		List<MetricScanner> scanners1 = new ArrayList<>();
		List<MetricScanner> scanners2 = new ArrayList<>();
		List<MetricScanner> scanners3 = new ArrayList<>();
		scanners.add(scanners1);
		scanners.add(scanners2);
		scanners.add(scanners3);
		
		for (int j = 0; j < metrics.size(); j++) {
			for (int i = 0; i < metrics.get(j).size(); i++) {
				Metric m = metrics.get(j).get(i);
				MetricQuery q = queries.get(j).get(i);
							
				Long bound = q.getStartTimestamp() + (q.getEndTimestamp() - q.getStartTimestamp()) / 2;
				List<MetricQuery> highQuery = new ArrayList<>();
				highQuery.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), bound, q.getEndTimestamp()));
				List<MetricQuery> tooHigh = new ArrayList<>();
				tooHigh.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getEndTimestamp(), q.getEndTimestamp()));
				
				MetricScanner s = new MetricScanner(lowElems(m, bound), q, serviceMock, bound);
				scanners.get(j).add(s);
				
				when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
				when(serviceMock.getMetrics(highQuery)).thenReturn(filterOver(m, bound, highQuery.get(0)));
			}
		}
		
		JoinTransform transform = new JoinTransform();
		
		List<Metric> expected = transform.transform(metrics1, metrics2, metrics3);
		List<Metric> actual = transform.transformScanner(scanners1, scanners2, scanners3);
				
		for (int i = 0; i < expected.size(); i++) {
			assert(expected.get(i).getDatapoints().equals(actual.get(i).getDatapoints()));
		}
		for (int i = 0; i < metrics.size(); i++) {
			for (int j = 0; j < metrics.get(i).size(); j++) {
				assert(!MetricScanner.existingScanner(metrics.get(i).get(j), queries.get(i).get(j)));
			}
		}
	}
	
}

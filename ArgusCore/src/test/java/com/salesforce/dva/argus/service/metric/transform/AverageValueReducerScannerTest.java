package com.salesforce.dva.argus.service.metric.transform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.service.tsdb.MetricScanner;



public class AverageValueReducerScannerTest extends AbstractTest {

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
	
	protected List<Metric> createRandomMetrics(String scope, String metric, int count) {
		List<Metric> result = new ArrayList<>(count);
		
		scope = scope == null ? createRandomName() : scope;
		
		String tag = createRandomName();
		
		for (int i = 0; i < count; i++) {
			String metricName = metric == null ? createRandomName() : metric;
			Metric met = new Metric(scope, metricName);
			int datapointCount = random.nextInt(25) + 1;
			Map<Long, Double> datapoints = new HashMap<>();
			long start = System.currentTimeMillis() - 60000L;
			
			for (int j = 0; j < datapointCount; j++) {
				datapoints.put(start - (j * 60000L), (double)(random.nextInt(100) + 1));
			}
			
			met.setDatapoints(datapoints);
			met.setDisplayName(createRandomName());
			met.setTag(tag, String.valueOf(i));
			result.add(met);
		}
		return result;
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
	public void testReducerWithoutNull() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		List<MetricQuery> queries = toQueries(metrics);
		List<MetricScanner> scanners = new ArrayList<>();
		
		for (int i = 0; i < metrics.size(); i++) {
			Long bound = queries.get(i).getStartTimestamp() + (queries.get(i).getEndTimestamp() - queries.get(i).getStartTimestamp()) / 2;
			List<MetricQuery> highQuery = new ArrayList<>();
			highQuery.add(new MetricQuery(queries.get(i).getScope(), queries.get(i).getMetric(), queries.get(i).getTags(), bound, queries.get(i).getEndTimestamp()));
			List<MetricQuery> tooHigh = new ArrayList<>();
			tooHigh.add(new MetricQuery(queries.get(i).getScope(), queries.get(i).getMetric(), queries.get(i).getTags(), queries.get(i).getEndTimestamp(), queries.get(i).getEndTimestamp()));
			
			scanners.add(new MetricScanner(lowElems(metrics.get(i), bound), queries.get(i), serviceMock, bound));
			
			when(serviceMock.getMetrics(highQuery)).thenReturn(filterOver(metrics.get(i), bound, highQuery.get(0)));
			when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
		}
		
		AverageValueReducer reducer = new AverageValueReducer();
		
		for (int i = 0; i < metrics.size(); i++) {
			List<Double> values = new ArrayList<Double>(metrics.get(i).getDatapoints().values());
			Double expected = reducer.reduce(values);
			Double actual = reducer.reduceScanner(scanners.get(i));
			
			assert(expected.equals(actual));
		}
	}
	
	@Test
	public void testReducerWithNull() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		
		for (Metric m : metrics) {
			Map<Long, Double> dps = new HashMap<>(m.getDatapoints());
			Long timestamp = Collections.min(dps.keySet()) + (Collections.max(dps.keySet()) - Collections.min(dps.keySet())) / 2;
			dps.put(timestamp, null);
			m.setDatapoints(dps);
		}
		
		List<MetricQuery> queries = toQueries(metrics);
		List<MetricScanner> scanners = new ArrayList<>();
		
		for (int i = 0; i < metrics.size(); i++) {
			Long bound = queries.get(i).getStartTimestamp() + (queries.get(i).getEndTimestamp() - queries.get(i).getStartTimestamp()) / 2;
			List<MetricQuery> highQuery = new ArrayList<>();
			highQuery.add(new MetricQuery(queries.get(i).getScope(), queries.get(i).getMetric(), queries.get(i).getTags(), bound, queries.get(i).getEndTimestamp()));
			List<MetricQuery> tooHigh = new ArrayList<>();
			tooHigh.add(new MetricQuery(queries.get(i).getScope(), queries.get(i).getMetric(), queries.get(i).getTags(), queries.get(i).getEndTimestamp(), queries.get(i).getEndTimestamp()));
			
			scanners.add(new MetricScanner(lowElems(metrics.get(i), bound), queries.get(i), serviceMock, bound));
			
			when(serviceMock.getMetrics(highQuery)).thenReturn(filterOver(metrics.get(i), bound, highQuery.get(0)));
			when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
		}
		
		AverageValueReducer reducer = new AverageValueReducer();
		
		for (int i = 0; i < metrics.size(); i++) {
			List<Double> values = new ArrayList<Double>(metrics.get(i).getDatapoints().values());
			Double expected = reducer.reduce(values);
			Double actual = reducer.reduceScanner(scanners.get(i));
			
			assert(expected.equals(actual));
		}
	}
	
	@Test
	public void testDPIntegrityAndDisposal() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		
		for (Metric m : metrics) {
			Map<Long, Double> dps = new HashMap<>(m.getDatapoints());
			Long timestamp = Collections.min(dps.keySet()) + (Collections.max(dps.keySet()) - Collections.min(dps.keySet())) / 2;
			dps.put(timestamp, null);
			m.setDatapoints(dps);
		}
		
		List<MetricQuery> queries = toQueries(metrics);
		List<MetricScanner> scanners = new ArrayList<>();
		
		for (int i = 0; i < metrics.size(); i++) {
			Long bound = queries.get(i).getStartTimestamp() + (queries.get(i).getEndTimestamp() - queries.get(i).getStartTimestamp()) / 2;
			List<MetricQuery> highQuery = new ArrayList<>();
			highQuery.add(new MetricQuery(queries.get(i).getScope(), queries.get(i).getMetric(), queries.get(i).getTags(), bound, queries.get(i).getEndTimestamp()));
			List<MetricQuery> tooHigh = new ArrayList<>();
			tooHigh.add(new MetricQuery(queries.get(i).getScope(), queries.get(i).getMetric(), queries.get(i).getTags(), queries.get(i).getEndTimestamp(), queries.get(i).getEndTimestamp()));
			
			scanners.add(new MetricScanner(lowElems(metrics.get(i), bound), queries.get(i), serviceMock, bound));
			
			when(serviceMock.getMetrics(highQuery)).thenReturn(filterOver(metrics.get(i), bound, highQuery.get(0)));
			when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
		}
		
		AverageValueReducer reducer = new AverageValueReducer();
		
		for (int i = 0; i < metrics.size(); i++) {
			List<Double> values = new ArrayList<Double>(metrics.get(i).getDatapoints().values());
			Double expected = reducer.reduce(values);
			Double actual = reducer.reduceScanner(scanners.get(i));
			
			assert(expected.equals(actual));
			
			assert(!MetricScanner.existingScanner(metrics.get(i), queries.get(i)));
		}
	}
}
}

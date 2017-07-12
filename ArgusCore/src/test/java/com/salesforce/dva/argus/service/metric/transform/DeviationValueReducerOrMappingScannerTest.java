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

public class DeviationValueReducerOrMappingScannerTest extends AbstractTest {

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
	public void testReductionNoNull() {
		
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
		
		DeviationValueReducerOrMapping redMap = new DeviationValueReducerOrMapping();
		List<String> constants = new ArrayList<>();
		constants.add("" + 0.1);
		//Double epsilon = Math.pow(1, -6);
		
		for (int i = 0; i < metrics.size(); i++) {
			List<Double> vals = new ArrayList<>(metrics.get(i).getDatapoints().values());
			Double expected = redMap.reduce(vals, constants);
			Double actual = redMap.reduceScanner(scanners.get(i), constants);
			
			assert(expected.equals(actual));
		}
	}
	
	@Test
	public void testReductionAllNull() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		for (Metric m : metrics) {
			Map<Long, Double> dps = new HashMap<>(m.getDatapoints());
			for (Map.Entry<Long, Double> entry : dps.entrySet()) {
				entry.setValue(null);
			}
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
		
		DeviationValueReducerOrMapping redMap = new DeviationValueReducerOrMapping();
		List<String> constants = new ArrayList<>();
		constants.add("" + 0.1);
		//Double epsilon = Math.pow(1, -6);
		
		for (int i = 0; i < metrics.size(); i++) {
			List<Double> vals = new ArrayList<>(metrics.get(i).getDatapoints().values());
			Double expected = redMap.reduce(vals, constants);
			Double actual = redMap.reduceScanner(scanners.get(i), constants);
			assert((expected == null && actual == null) || expected.equals(actual));
		}
	}
	
	@Test
	public void testReductionSomeNull() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		for (Metric m : metrics) {
			Map<Long, Double> dps = new HashMap<>(m.getDatapoints());
			if (random.nextInt(2) == 0) {
				Long timestamp = Collections.min(dps.keySet()) + (Collections.max(dps.keySet()) - Collections.min(dps.keySet())) / 2;
				dps.put(timestamp, null);
			}
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
		
		DeviationValueReducerOrMapping redMap = new DeviationValueReducerOrMapping();
		List<String> constants = new ArrayList<>();
		constants.add("" + 0.1);
		//Double epsilon = Math.pow(1, -6);
		
		for (int i = 0; i < metrics.size(); i++) {
			List<Double> cleanedvals = new ArrayList<>(metrics.get(i).getDatapoints().values());
			double nulls = 0.0;
			for (Double val : metrics.get(i).getDatapoints().values()) {
				if (val != null) {
					//cleanedvals.add(val);
				} else {
					nulls++;
				}
			}
						
			Double expected = redMap.reduce(cleanedvals, constants);
			Double actual = redMap.reduceScanner(scanners.get(i), constants);

			assert((nulls / metrics.get(i).getDatapoints().size() > Double.parseDouble(constants.get(0))) || expected.equals(actual));
		}
	}
	
	@Test
	public void testMapping() {
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		for (Metric m : metrics) {
			Map<Long, Double> dps = new HashMap<>(m.getDatapoints());
			if (random.nextInt(2) == 0) {
				Long timestamp = Collections.min(dps.keySet()) + (Collections.max(dps.keySet()) - Collections.min(dps.keySet())) / 2;
				dps.put(timestamp, null);
			}
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
		
		DeviationValueReducerOrMapping redMap = new DeviationValueReducerOrMapping();
		List<String> constants = new ArrayList<>();
		constants.add("" + 0.1);
		
		for (int i = 0; i < metrics.size(); i++) {
			List<Double> cleanedvals = new ArrayList<>(metrics.get(i).getDatapoints().values());
			double nulls = 0.0;
			for (Double val : metrics.get(i).getDatapoints().values()) {
				if (val != null) {
					//cleanedvals.add(val);
				} else {
					nulls++;
				}
			}
						
			Map<Long, Double> expected = redMap.mapping(new TreeMap<>(metrics.get(i).getDatapoints()), constants);
			Map<Long, Double> actual = redMap.mappingScanner(scanners.get(i), constants);

			assert((nulls / metrics.get(i).getDatapoints().size() > Double.parseDouble(constants.get(0))) || expected.equals(actual));
			assert(!MetricScanner.existingScanner(metrics.get(i), queries.get(i)));
		}
	}
	
	@Test
	public void testDPIntegrityAndDisposal() {
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		for (Metric m : metrics) {
			Map<Long, Double> dps = new HashMap<>(m.getDatapoints());
			if (random.nextInt(2) == 0) {
				Long timestamp = Collections.min(dps.keySet()) + (Collections.max(dps.keySet()) - Collections.min(dps.keySet())) / 2;
				dps.put(timestamp, null);
			}
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
		
		DeviationValueReducerOrMapping redMap = new DeviationValueReducerOrMapping();
		List<String> constants = new ArrayList<>();
		constants.add("" + 0.1);
		
		for (int i = 0; i < metrics.size(); i++) {
			List<Double> cleanedvals = new ArrayList<>(metrics.get(i).getDatapoints().values());
			double nulls = 0.0;
			for (Double val : metrics.get(i).getDatapoints().values()) {
				if (val != null) {
					//cleanedvals.add(val);
				} else {
					nulls++;
				}
			}
						
			Double expected = redMap.reduce(cleanedvals, constants);
			Double actual = redMap.reduceScanner(scanners.get(i), constants);

			assert((nulls / metrics.get(i).getDatapoints().size() > Double.parseDouble(constants.get(0))) || expected.equals(actual));
			assert(!MetricScanner.existingScanner(metrics.get(i), queries.get(i)));
		}
	}
}

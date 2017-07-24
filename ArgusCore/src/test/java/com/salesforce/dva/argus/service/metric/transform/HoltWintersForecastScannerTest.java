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
import org.mockito.Matchers;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.service.tsdb.MetricScanner;

public class HoltWintersForecastScannerTest extends AbstractTest {

protected static final long MILLIS_IN_A_WEEK = 7 * 24 * 60 * 60 * 100;
	
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
	
	private Map<MetricQuery, List<Metric>> filterUnder(Metric m, Long endTime, MetricQuery query) {
		Metric mini = new Metric(m);
		Map<Long, Double> filteredDP = new TreeMap<>();
		for (Long key : m.getDatapoints().keySet()) {
			if (key <= endTime) {
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
	public void testTransformWithRandomABGShortSeasonLength() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		List<MetricQuery> queries = toQueries(metrics);
		List<MetricScanner> scanners = new ArrayList<>();
		Map<MetricQuery, List<Metric>> earlier = new HashMap<>();
		Map<MetricQuery, List<MetricScanner>> earlierS = new HashMap<>();
		
		for (int i = 0; i < metrics.size(); i++) {
			Metric m = metrics.get(i);
			MetricQuery q = queries.get(i);
			m.setQuery(q);
			
			Metric earlierMetric = new Metric(m);
			Map<Long, Double> dps = new HashMap<>();
			for (Map.Entry<Long, Double> entry : earlierMetric.getDatapoints().entrySet()) {
				dps.put(entry.getKey() - MILLIS_IN_A_WEEK, entry.getValue());
			}

			MetricQuery earlierQ = new MetricQuery(q);
			earlierQ.setStartTimestamp(q.getStartTimestamp() - MILLIS_IN_A_WEEK);
			earlierQ.setEndTimestamp(q.getStartTimestamp());
			if (!earlier.containsKey(earlierQ)) {
				earlier.put(earlierQ, new ArrayList<>());
			}
			earlier.get(earlierQ).add(earlierMetric);
						
			Long bound = q.getStartTimestamp() + (q.getEndTimestamp() - q.getStartTimestamp()) / 2;
			List<MetricQuery> highQuery = new ArrayList<>();
			highQuery.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), bound, q.getEndTimestamp()));
			List<MetricQuery> tooHigh = new ArrayList<>();
			tooHigh.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getEndTimestamp(), q.getEndTimestamp()));
			List<MetricQuery> copyHighQuery = new ArrayList<>();
			copyHighQuery.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getStartTimestamp() - MILLIS_IN_A_WEEK + bound, q.getStartTimestamp()));
			List<MetricQuery> copyLowQuery = new ArrayList<>();
			copyLowQuery.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getStartTimestamp() - MILLIS_IN_A_WEEK, q.getStartTimestamp() - MILLIS_IN_A_WEEK + bound));
			List<MetricQuery> copyTooHigh = new ArrayList<>();
			copyTooHigh.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getStartTimestamp(), q.getStartTimestamp()));
			
			MetricScanner s = new MetricScanner(lowElems(m, bound), q, serviceMock, bound);
			scanners.add(s);
			
			MetricScanner earlierScanner = new MetricScanner(lowElems(earlierMetric, q.getStartTimestamp() - MILLIS_IN_A_WEEK + bound), earlierQ, serviceMock, q.getStartTimestamp() - MILLIS_IN_A_WEEK + bound);
			if (!earlierS.containsKey(earlierQ)) {
				earlierS.put(earlierQ, new ArrayList<>());
			}
			earlierS.get(earlierQ).add(earlierScanner);
			
			when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
			when(serviceMock.getMetrics(highQuery)).thenReturn(filterOver(m, bound, highQuery.get(0)));
			when(serviceMock.getMetrics(copyTooHigh)).thenReturn(outOfBounds());
			when(serviceMock.getMetrics(copyHighQuery)).thenReturn(filterOver(earlierMetric, q.getStartTimestamp() - MILLIS_IN_A_WEEK + bound, copyHighQuery.get(0)));
			when(serviceMock.getMetrics(copyLowQuery)).thenReturn(filterUnder(earlierMetric, q.getStartTimestamp() - MILLIS_IN_A_WEEK + bound, copyLowQuery.get(0)));
		}

		when(serviceMock.getMetrics(Matchers.anyListOf(MetricQuery.class))).thenReturn(earlier);
		when(serviceMock.getMetricScanners(Matchers.anyListOf(MetricQuery.class))).thenReturn(earlierS);

		Transform transform = new HoltWintersForecast(serviceMock);
		List<String> constants = new ArrayList<>();
		constants.add("" + random.nextDouble() * 10);
		constants.add("" + random.nextDouble() * 10);
		constants.add("" + random.nextDouble() * 10);
		constants.add("2");
		
		List<Metric> expected = transform.transform(metrics, constants);
		List<Metric> actual = transform.transformScanner(scanners, constants);
		
		assert(actual.equals(expected));
	}
	
	@Test
	public void testTransformWithRandomABGLongSeasonLength() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		List<MetricQuery> queries = toQueries(metrics);
		List<MetricScanner> scanners = new ArrayList<>();
		Map<MetricQuery, List<Metric>> earlier = new HashMap<>();
		Map<MetricQuery, List<MetricScanner>> earlierS = new HashMap<>();
		
		for (int i = 0; i < metrics.size(); i++) {
			Metric m = metrics.get(i);
			MetricQuery q = queries.get(i);
			m.setQuery(q);
			
			Metric earlierMetric = new Metric(m);
			Map<Long, Double> dps = new HashMap<>();
			for (Map.Entry<Long, Double> entry : earlierMetric.getDatapoints().entrySet()) {
				dps.put(entry.getKey() - MILLIS_IN_A_WEEK, entry.getValue());
			}
			
			MetricQuery earlierQ = new MetricQuery(q);
			earlierQ.setStartTimestamp(q.getStartTimestamp() - MILLIS_IN_A_WEEK);
			earlierQ.setEndTimestamp(q.getStartTimestamp());
			if (!earlier.containsKey(earlierQ)) {
				earlier.put(earlierQ, new ArrayList<>());
			}
			earlier.get(earlierQ).add(earlierMetric);
						
			Long bound = q.getStartTimestamp() + (q.getEndTimestamp() - q.getStartTimestamp()) / 2;			

					
			List<MetricQuery> highQuery = new ArrayList<>();
			highQuery.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), bound, q.getEndTimestamp()));
			List<MetricQuery> tooHigh = new ArrayList<>();
			tooHigh.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getEndTimestamp(), q.getEndTimestamp()));
			List<MetricQuery> copyHighQuery = new ArrayList<>();
			copyHighQuery.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getStartTimestamp() - MILLIS_IN_A_WEEK + bound, q.getStartTimestamp()));
			List<MetricQuery> copyLowQuery = new ArrayList<>();
			copyLowQuery.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getStartTimestamp() - MILLIS_IN_A_WEEK, q.getStartTimestamp() - MILLIS_IN_A_WEEK + bound));
			List<MetricQuery> copyTooHigh = new ArrayList<>();
			copyTooHigh.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getStartTimestamp(), q.getStartTimestamp()));
			
			MetricScanner s = new MetricScanner(lowElems(m, bound), q, serviceMock, bound);
			scanners.add(s);
			
			MetricScanner earlierScanner = new MetricScanner(lowElems(earlierMetric, q.getStartTimestamp() - MILLIS_IN_A_WEEK + bound), earlierQ, serviceMock, q.getStartTimestamp() - MILLIS_IN_A_WEEK + bound);
			if (!earlierS.containsKey(earlierQ)) {
				earlierS.put(earlierQ, new ArrayList<>());
			}
			earlierS.get(earlierQ).add(earlierScanner);
			
			when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
			when(serviceMock.getMetrics(highQuery)).thenReturn(filterOver(m, bound, highQuery.get(0)));
			when(serviceMock.getMetrics(copyTooHigh)).thenReturn(outOfBounds());
			when(serviceMock.getMetrics(copyHighQuery)).thenReturn(filterOver(earlierMetric, q.getStartTimestamp() - MILLIS_IN_A_WEEK + bound, copyHighQuery.get(0)));
			when(serviceMock.getMetrics(copyLowQuery)).thenReturn(filterUnder(earlierMetric, q.getStartTimestamp() - MILLIS_IN_A_WEEK + bound, copyLowQuery.get(0)));
		}

		when(serviceMock.getMetrics(Matchers.anyListOf(MetricQuery.class))).thenReturn(earlier);
		when(serviceMock.getMetricScanners(Matchers.anyListOf(MetricQuery.class))).thenReturn(earlierS);

		Transform transform = new HoltWintersForecast(serviceMock);
		List<String> constants = new ArrayList<>();
		constants.add("" + random.nextDouble() * 10);
		constants.add("" + random.nextDouble() * 10);
		constants.add("" + random.nextDouble() * 10);
		constants.add("8");
		
		List<Metric> expected = transform.transform(metrics, constants);
		List<Metric> actual = transform.transformScanner(scanners, constants);
		
		assert(actual.equals(expected));
	}
	
	@Test
	public void testTransformWithRandomABGRandomSeasonLength() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		List<MetricQuery> queries = toQueries(metrics);
		List<MetricScanner> scanners = new ArrayList<>();
		Map<MetricQuery, List<Metric>> earlier = new HashMap<>();
		Map<MetricQuery, List<MetricScanner>> earlierS = new HashMap<>();
		
		for (int i = 0; i < metrics.size(); i++) {
			Metric m = metrics.get(i);
			MetricQuery q = queries.get(i);
			m.setQuery(q);
			
			Metric earlierMetric = new Metric(m);
			Map<Long, Double> dps = new HashMap<>();
			for (Map.Entry<Long, Double> entry : earlierMetric.getDatapoints().entrySet()) {
				dps.put(entry.getKey() - MILLIS_IN_A_WEEK, entry.getValue());
			}

			MetricQuery earlierQ = new MetricQuery(q);
			earlierQ.setStartTimestamp(q.getStartTimestamp() - MILLIS_IN_A_WEEK);
			earlierQ.setEndTimestamp(q.getStartTimestamp());
			if (!earlier.containsKey(earlierQ)) {
				earlier.put(earlierQ, new ArrayList<>());
			}
			earlier.get(earlierQ).add(earlierMetric);
						
			Long bound = q.getStartTimestamp() + (q.getEndTimestamp() - q.getStartTimestamp()) / 2;
			List<MetricQuery> highQuery = new ArrayList<>();
			highQuery.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), bound, q.getEndTimestamp()));
			List<MetricQuery> tooHigh = new ArrayList<>();
			tooHigh.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getEndTimestamp(), q.getEndTimestamp()));
			List<MetricQuery> copyHighQuery = new ArrayList<>();
			copyHighQuery.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getStartTimestamp() - MILLIS_IN_A_WEEK + bound, q.getStartTimestamp()));
			List<MetricQuery> copyLowQuery = new ArrayList<>();
			copyLowQuery.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getStartTimestamp() - MILLIS_IN_A_WEEK, q.getStartTimestamp() - MILLIS_IN_A_WEEK + bound));
			List<MetricQuery> copyTooHigh = new ArrayList<>();
			copyTooHigh.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getStartTimestamp(), q.getStartTimestamp()));
			
			MetricScanner s = new MetricScanner(lowElems(m, bound), q, serviceMock, bound);
			scanners.add(s);
			
			MetricScanner earlierScanner = new MetricScanner(lowElems(earlierMetric, q.getStartTimestamp() - MILLIS_IN_A_WEEK + bound), earlierQ, serviceMock, q.getStartTimestamp() - MILLIS_IN_A_WEEK + bound);
			if (!earlierS.containsKey(earlierQ)) {
				earlierS.put(earlierQ, new ArrayList<>());
			}
			earlierS.get(earlierQ).add(earlierScanner);
			
			when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
			when(serviceMock.getMetrics(highQuery)).thenReturn(filterOver(m, bound, highQuery.get(0)));
			when(serviceMock.getMetrics(copyTooHigh)).thenReturn(outOfBounds());
			when(serviceMock.getMetrics(copyHighQuery)).thenReturn(filterOver(earlierMetric, q.getStartTimestamp() - MILLIS_IN_A_WEEK + bound, copyHighQuery.get(0)));
			when(serviceMock.getMetrics(copyLowQuery)).thenReturn(filterUnder(earlierMetric, q.getStartTimestamp() - MILLIS_IN_A_WEEK + bound, copyLowQuery.get(0)));
		}

		when(serviceMock.getMetrics(Matchers.anyListOf(MetricQuery.class))).thenReturn(earlier);
		when(serviceMock.getMetricScanners(Matchers.anyListOf(MetricQuery.class))).thenReturn(earlierS);

		Transform transform = new HoltWintersForecast(serviceMock);
		List<String> constants = new ArrayList<>();
		constants.add("" + random.nextDouble() * 10);
		constants.add("" + random.nextDouble() * 10);
		constants.add("" + random.nextDouble() * 10);
		constants.add("" + random.nextInt());
		
		List<Metric> expected = transform.transform(metrics, constants);
		List<Metric> actual = transform.transformScanner(scanners, constants);
		
		assert(actual.equals(expected));
	}
	
	@Test
	public void testDPIntegrityAndDisposal() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		List<MetricQuery> queries = toQueries(metrics);
		List<MetricScanner> scanners = new ArrayList<>();
		Map<MetricQuery, List<Metric>> earlier = new HashMap<>();
		Map<MetricQuery, List<MetricScanner>> earlierS = new HashMap<>();
		
		for (int i = 0; i < metrics.size(); i++) {
			Metric m = metrics.get(i);
			MetricQuery q = queries.get(i);
			m.setQuery(q);
			
			Metric earlierMetric = new Metric(m);
			Map<Long, Double> dps = new HashMap<>();
			for (Map.Entry<Long, Double> entry : earlierMetric.getDatapoints().entrySet()) {
				dps.put(entry.getKey() - MILLIS_IN_A_WEEK, entry.getValue());
			}

			MetricQuery earlierQ = new MetricQuery(q);
			earlierQ.setStartTimestamp(q.getStartTimestamp() - MILLIS_IN_A_WEEK);
			earlierQ.setEndTimestamp(q.getStartTimestamp());
			if (!earlier.containsKey(earlierQ)) {
				earlier.put(earlierQ, new ArrayList<>());
			}
			earlier.get(earlierQ).add(earlierMetric);
						
			Long bound = q.getStartTimestamp() + (q.getEndTimestamp() - q.getStartTimestamp()) / 2;
			List<MetricQuery> highQuery = new ArrayList<>();
			highQuery.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), bound, q.getEndTimestamp()));
			List<MetricQuery> tooHigh = new ArrayList<>();
			tooHigh.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getEndTimestamp(), q.getEndTimestamp()));
			List<MetricQuery> copyHighQuery = new ArrayList<>();
			copyHighQuery.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getStartTimestamp() - MILLIS_IN_A_WEEK + bound, q.getStartTimestamp()));
			List<MetricQuery> copyLowQuery = new ArrayList<>();
			copyLowQuery.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getStartTimestamp() - MILLIS_IN_A_WEEK, q.getStartTimestamp() - MILLIS_IN_A_WEEK + bound));
			List<MetricQuery> copyTooHigh = new ArrayList<>();
			copyTooHigh.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getStartTimestamp(), q.getStartTimestamp()));
			
			MetricScanner s = new MetricScanner(lowElems(m, bound), q, serviceMock, bound);
			scanners.add(s);
			
			MetricScanner earlierScanner = new MetricScanner(lowElems(earlierMetric, q.getStartTimestamp() - MILLIS_IN_A_WEEK + bound), earlierQ, serviceMock, q.getStartTimestamp() - MILLIS_IN_A_WEEK + bound);
			if (!earlierS.containsKey(earlierQ)) {
				earlierS.put(earlierQ, new ArrayList<>());
			}
			earlierS.get(earlierQ).add(earlierScanner);
			
			when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
			when(serviceMock.getMetrics(highQuery)).thenReturn(filterOver(m, bound, highQuery.get(0)));
			when(serviceMock.getMetrics(copyTooHigh)).thenReturn(outOfBounds());
			when(serviceMock.getMetrics(copyHighQuery)).thenReturn(filterOver(earlierMetric, q.getStartTimestamp() - MILLIS_IN_A_WEEK + bound, copyHighQuery.get(0)));
			when(serviceMock.getMetrics(copyLowQuery)).thenReturn(filterUnder(earlierMetric, q.getStartTimestamp() - MILLIS_IN_A_WEEK + bound, copyLowQuery.get(0)));
		}

		when(serviceMock.getMetrics(Matchers.anyListOf(MetricQuery.class))).thenReturn(earlier);
		when(serviceMock.getMetricScanners(Matchers.anyListOf(MetricQuery.class))).thenReturn(earlierS);

		Transform transform = new HoltWintersForecast(serviceMock);
		List<String> constants = new ArrayList<>();
		constants.add("" + random.nextDouble() * 10);
		constants.add("" + random.nextDouble() * 10);
		constants.add("" + random.nextDouble() * 10);
		constants.add("" + random.nextInt());
		
		List<Metric> expected = transform.transform(metrics, constants);
		List<Metric> actual = transform.transformScanner(scanners, constants);
		
		for (int i = 0; i < expected.size(); i++) {
			assert(expected.get(i).getDatapoints().equals(actual.get(i).getDatapoints()));
		}
		for (int i = 0; i < metrics.size(); i++) {
			assert(!MetricScanner.existingScanner(metrics.get(i), queries.get(i)));
		}
	}
}

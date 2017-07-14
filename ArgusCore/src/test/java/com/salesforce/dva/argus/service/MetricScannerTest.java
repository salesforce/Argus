package com.salesforce.dva.argus.service;

//import java.util.*;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;

//import org.apache.jasper.tagplugins.jstl.core.Set;
import org.junit.Test;
import org.mockito.Matchers;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.service.tsdb.MetricScanner;

import static org.mockito.Mockito.*;


public class MetricScannerTest extends AbstractTest {
	
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
	
	private List<MetricQuery> toQueriesEmpty(List<Metric> expected) {
		List<MetricQuery> queries = new LinkedList<>();
		
		for (Metric metric : expected) {
			long time = System.currentTimeMillis();
			MetricQuery q = new MetricQuery(metric.getScope(), metric.getMetric(), metric.getTags(), time, time + 6000L);
			queries.add(q);
		}
		return queries;
	}
	
	private void checkDatapointMatch(Map<Long, Double> expected, MetricScanner ms) {
		Map<Long, Double> actual = new TreeMap<Long, Double>();
		while (ms.hasNextDP()) {
			Map.Entry<Long, Double> dp = ms.getNextDP();
			actual.put(dp.getKey(), dp.getValue());
		}
		
		//System.out.println(actual + " is actual");
		//System.out.println(expected + " is expected");
		assert(actual.equals(expected));
	}
	
	private void checkDatapointChronology(MetricScanner ms) {
		List<Map<Long, Double>> pts = new LinkedList<>();
		
		while (ms.hasNextDP()) {
			Map.Entry<Long, Double> dp = ms.getNextDP();
			Map<Long, Double> map = new HashMap<>();
			map.put(dp.getKey(), dp.getValue());
			pts.add(map);
		}
		
		for (int i = 1; i < pts.size(); i++) {
			for (Long oldTime : pts.get(i-1).keySet()) {
				for (Long time : pts.get(i).keySet()) {
					assert (oldTime < time);
				}
			}
		}
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
	
	private Map<MetricQuery, List<Metric>> doubleFilterOver(Metric m1, Metric m2, Long startTime, MetricQuery query) {
		Map<MetricQuery, List<Metric>> res = filterOver(m1, startTime, query);
		res.get(query).addAll(filterOver(m2, startTime, query).get(query));
		return res;
	}
	
	private Map<MetricQuery, List<Metric>> filterBetween(Metric m, Long low, Long high, MetricQuery query) {
		Metric mini = new Metric(m);
		Map<Long, Double> filteredDP = new HashMap<>();
		for (Long key : m.getDatapoints().keySet()) {
			if (key >= low && key <= high) {
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
	
	private Map<MetricQuery, List<Metric>> outOfBounds() {
		return new HashMap<>();
	}
	
	private void addTo(Map.Entry<Long, Double> dp, Map<Long, Double> runningTotal) {
		if (runningTotal.containsKey(dp.getKey())) {
			if (dp.getValue() != null) {
				runningTotal.put(dp.getKey(), runningTotal.get(dp.getKey()) + dp.getValue());
			}
			else {
				runningTotal.put(dp.getKey(), 0.0);
			}
		}
		else {
			if (dp.getValue() != null) {
				runningTotal.put(dp.getKey(), dp.getValue());
			}
			else {
				runningTotal.put(dp.getKey(), 0.0);
			}
		}
	}
	
	private void checkSumMatch(List<Metric> metrics, List<MetricScanner> scanners) {
		Map<Long, Double> metricSum = new HashMap<>();
		Map<Long, Double> scannerSum = new HashMap<>();
		
		for (Metric m : metrics) {
			Map<Long, Double> dps = m.getDatapoints();
			for (Map.Entry<Long, Double> dp : dps.entrySet()) {
				addTo(dp, metricSum);
			}
		}
		
		boolean done = true;
		do {
			done = true;
			for (MetricScanner ms : scanners) {
				if (ms.hasNextDP()) {
					Map.Entry<Long, Double> dp = ms.getNextDP();
					done = false;
					addTo(dp, scannerSum);
				}
			}
		} while (!done);
		
		assert(metricSum.equals(scannerSum));
	}
	
	private void testConcurrentMetricScan(Metric m, MetricQuery q, TSDBService serviceMock) {
		MetricScanner s = MetricScanner.findScanner(m, q, serviceMock, null).iterator().next();
		Map<Long, Double> sdps = new HashMap<>();
				
		int num = random.nextInt(m.getDatapoints().size());
		
		m.setNamespace(createRandomName());
		
		Long bound = q.getStartTimestamp() + (q.getEndTimestamp() - q.getStartTimestamp()) / 2;
		List<MetricQuery> highQuery = new ArrayList<>();
		highQuery.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), bound, q.getEndTimestamp()));
		List<MetricQuery> tooHigh = new ArrayList<>();
		
		// have to do this up here so it knows to return the updated version of this metric
		when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
		when(serviceMock.getMetrics(highQuery)).thenReturn(filterOver(m, bound, highQuery.get(0)));
		
		int i = 0;
		while (i < num && s.hasNextDP()) {
			Map.Entry<Long, Double> dp = s.getNextDP();
			sdps.put(dp.getKey(), dp.getValue());
			i++;
		}
		
		s = MetricScanner.findScanner(m, q, serviceMock, null).iterator().next();

		while (s.hasNextDP()) {
			Map.Entry<Long, Double> dp = s.getNextDP();
			sdps.put(dp.getKey(), dp.getValue());
		}
				
		assert(sdps.equals(m.getDatapoints()));
		assert((s.getMetric().getNamespace() == null && m.getNamespace() == null) || s.getCallsToFetch() == 0 || s.getMetric().getNamespace().equals(m.getNamespace()));
	}
	
	private void checkDatapointFilling(Metric m, MetricScanner scanner, MetricScanner copy, TSDBService serviceMock) {
		checkDatapointMatch(m.getDatapoints(), scanner);
		Map<Long, Double> dps = new HashMap<>();
		while (copy.hasNextDP()) {
			Map.Entry<Long, Double> dp = copy.getNextDP();
			dps.put(dp.getKey(), dp.getValue());
		}
		assert(m.getDatapoints().equals(dps) || m.getDatapoints().size() == 1);
	}
	
	private void checkDatapointMatchFromEmpty(Map<Long, Double> dps, MetricScanner scanner) {
		try {
			checkDatapointMatch(dps, scanner);
		} catch (AssertionError e) {
			assert(dps.size() == 1);
		}
	}
	
	@Test
	public void testMetricScannerCreationAndReturnWithNoResultQuery() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		int NUM_QUERIES = 12;
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, NUM_QUERIES - 1);
		List<MetricQuery> queries = toQueries(metrics);
		Metric extra = createRandomMetrics(null, null, 1).get(0);
		queries.add(toQuery(extra));
		
		double percentage = 1.0;	// already know continuous fetching is fine, just want to check this creation piece
		
		for (MetricQuery query : queries) {
			if (queries.indexOf(query) == 11) {	// is the last query with no associated metric
				when(serviceMock.getMetrics(Matchers.anyListOf(MetricQuery.class))).thenReturn(new HashMap<>());
				continue;
			}
			List<MetricQuery> miniQuery = new LinkedList<>();
			miniQuery.add(query);
			when(serviceMock.getMetrics(miniQuery)).thenReturn(filterOver(metrics.get(queries.indexOf(query)), query.getStartTimestamp(), query));
			List<MetricQuery> tooHigh = new LinkedList<>();
			tooHigh.add(new MetricQuery(query.getScope(), query.getMetric(), query.getTags(), query.getEndTimestamp(), query.getEndTimestamp()));
			when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
		}
				
		Map<MetricQuery, List<MetricScanner>> scannerMap = new HashMap<>();
		
		for (MetricQuery query : queries) {
			Long chunkTime = (Long) Math.round(((query.getEndTimestamp() - query.getStartTimestamp()) * percentage));
			int additionalChunkTime = -1;
		
			scannerMap.put(query, new ArrayList<>());
			
			Map<MetricQuery, List<Metric>> metricMap;
			MetricQuery miniQuery;
			Long stopTime;
			Long startTime;
			
			do {
				additionalChunkTime++;
				startTime = query.getStartTimestamp() + chunkTime * additionalChunkTime;
				stopTime = Math.max(query.getEndTimestamp(), startTime + chunkTime);
				
				miniQuery = new MetricQuery(query.getScope(), query.getMetric(), query.getTags(), startTime, stopTime);
				List<MetricQuery> miniQueries = new ArrayList<>();
				miniQueries.add(miniQuery);
				metricMap = serviceMock.getMetrics(miniQueries);
			
			} while (!metricMap.containsKey(miniQuery) && (startTime + chunkTime) < query.getEndTimestamp());
			
			if (!metricMap.containsKey(miniQuery)) {scannerMap.put(miniQuery, new ArrayList<>()); continue;} // no metric data for this query
			
			List<Metric> miniMetrics = metricMap.get(miniQuery);
			
			for (Metric miniMetric : miniMetrics) {
				MetricScanner scanner = new MetricScanner(miniMetric, query, serviceMock, stopTime);
				scannerMap.get(query).add(scanner);
			}
		}
				
		for (MetricQuery query : queries) {
			if (queries.indexOf(query) < NUM_QUERIES - 1) {	// isn't that last, unpaired query
				assert(scannerMap.containsKey(query));
			}
			for (MetricScanner scanner : scannerMap.get(query)) {
				checkDatapointMatch(metrics.get(queries.indexOf(query)).getDatapoints(), scanner);
			}
		}
	}
	
	@Test
	public void testScannerDatapointMaintenance() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		Metric metric = createRandomMetrics(null, null, 1).get(0);
		MetricQuery query = toQuery(metric);
		
		List<MetricQuery> miniQueriesHigher = new LinkedList<>();
		
		Long boundary = query.getStartTimestamp() + (query.getEndTimestamp() - query.getStartTimestamp()) / 2;
		
		miniQueriesHigher.add(new MetricQuery(query.getScope(), query.getMetric(), query.getTags(), boundary, query.getEndTimestamp()));
				
		MetricQuery tooHigh = new MetricQuery(query.getScope(), query.getMetric(), query.getTags(), query.getEndTimestamp(), query.getEndTimestamp());
		List<MetricQuery> highQuery = new ArrayList<MetricQuery>();
		highQuery.add(tooHigh);
		
		when(serviceMock.getMetrics(miniQueriesHigher)).thenReturn(filterOver(metric, boundary, miniQueriesHigher.get(0)));
		when(serviceMock.getMetrics(highQuery)).thenReturn(outOfBounds());
		
		when(serviceMock.getMetrics(Matchers.anyListOf(MetricQuery.class))).thenReturn(filterOver(metric, boundary, miniQueriesHigher.get(0)));
		MetricScanner scanner = new MetricScanner(lowElems(metric, boundary), query, serviceMock, boundary);
		
		checkDatapointMatch(metric.getDatapoints(), scanner);
	}
	
	@Test
	public void testScannerChronology() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		Metric metric = createRandomMetrics(null, null, 1).get(0);
		MetricQuery query = toQuery(metric);
		
		List<MetricQuery> miniQueriesLower = new LinkedList<>();
		List<MetricQuery> miniQueriesHigher = new LinkedList<>();
		
		Long boundary = query.getStartTimestamp() + (query.getEndTimestamp() - query.getStartTimestamp()) / 2;
		
		miniQueriesLower.add(new MetricQuery(query.getScope(), query.getMetric(), query.getTags(), query.getStartTimestamp(), boundary));
		miniQueriesHigher.add(new MetricQuery(query.getScope(), query.getMetric(), query.getTags(), boundary, query.getEndTimestamp()));
		
		MetricQuery tooHigh = new MetricQuery(query.getScope(), query.getMetric(), query.getTags(), query.getEndTimestamp(), query.getEndTimestamp());
		List<MetricQuery> highQuery = new ArrayList<MetricQuery>();
		highQuery.add(tooHigh);
		
		when(serviceMock.getMetrics(miniQueriesLower)).thenReturn(filterUnder(metric, boundary, miniQueriesLower.get(0)));
		when(serviceMock.getMetrics(miniQueriesHigher)).thenReturn(filterOver(metric, boundary, miniQueriesHigher.get(0)));
		when(serviceMock.getMetrics(highQuery)).thenReturn(outOfBounds());
		
		when(serviceMock.getMetrics(Matchers.anyListOf(MetricQuery.class))).thenReturn(filterOver(metric, boundary, miniQueriesHigher.get(0)));
		MetricScanner scanner = new MetricScanner(lowElems(metric, boundary), query, serviceMock, boundary);
		
		checkDatapointChronology(scanner);
	}
	
	@Test
	public void testScannerIdentifierClearing() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		Metric metric = createRandomMetrics(null, null, 1).get(0);
		MetricQuery query = toQuery(metric);
		
		List<MetricQuery> miniQueriesHigher = new LinkedList<>();
		
		Long boundary = query.getStartTimestamp() + (query.getEndTimestamp() - query.getStartTimestamp()) / 2;
		
		miniQueriesHigher.add(new MetricQuery(query.getScope(), query.getMetric(), query.getTags(), boundary, query.getEndTimestamp()));
		
		MetricQuery tooHigh = new MetricQuery(query.getScope(), query.getMetric(), query.getTags(), query.getEndTimestamp(), query.getEndTimestamp());
		List<MetricQuery> highQuery = new ArrayList<MetricQuery>();
		highQuery.add(tooHigh);
		
		when(serviceMock.getMetrics(miniQueriesHigher)).thenReturn(filterOver(metric, boundary, miniQueriesHigher.get(0)));
		when(serviceMock.getMetrics(highQuery)).thenReturn(outOfBounds());
		//when(serviceMock.getMetrics(Matchers.anyListOf(MetricQuery.class))).thenReturn(filterOver(metric, boundary, miniQueriesHigher.get(0)));
		
		MetricScanner scanner = new MetricScanner(lowElems(metric, boundary), query, serviceMock, boundary);
		MetricScanner scannerCopy = new MetricScanner(lowElems(metric, boundary), query, serviceMock, boundary);
		
		assert(MetricScanner.existingScanner(metric, query));
		
		int size = MetricScanner.findScanner(metric, query, serviceMock, null).size();
		
		while (scanner.hasNextDP()) {
			scanner.getNextDP();
		}
		
		assert(MetricScanner.findScanner(metric, query, serviceMock, null).size() == size - 1);
		
		while (scannerCopy.hasNextDP()) {
			scannerCopy.getNextDP();
		}
		
		assert(!MetricScanner.existingScanner(metric, query));
	}
	
	@Test
	public void testNoNamingCollision() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 1);
		metrics.add(metrics.get(0));	// are both the same metric but will give different namespaces to their queries
		List<MetricQuery> queries = toQueries(metrics);
		MetricQuery mq1 = queries.get(0);
		MetricQuery mq2 = queries.get(1);
		
		mq1.setNamespace(createRandomName());
		mq2.setNamespace(createRandomName());
				
		Long bound1 = mq1.getStartTimestamp() + (mq1.getEndTimestamp() - mq1.getStartTimestamp()) / 2;
		Long bound2 = mq2.getStartTimestamp() + (mq2.getEndTimestamp() - mq2.getStartTimestamp()) / 2;
		
		List<MetricQuery> miniMQ1Higher = new LinkedList<>();
		List<MetricQuery> miniMQ2Higher = new LinkedList<>();

		MetricQuery miniMQ1High = new MetricQuery(mq1);
		miniMQ1High.setStartTimestamp(bound1);
		MetricQuery miniMQ2High = new MetricQuery(mq2);
		miniMQ2High.setStartTimestamp(bound2);
		
		miniMQ1Higher.add(miniMQ1High);
		miniMQ2Higher.add(miniMQ2High);

		List<MetricQuery> highQ1 = new LinkedList<>();
		List<MetricQuery> highQ2 = new LinkedList<>();
		MetricQuery high1 = new MetricQuery(mq1);
		high1.setStartTimestamp(mq1.getEndTimestamp());
		high1.setEndTimestamp(mq1.getEndTimestamp());
		MetricQuery high2 = new MetricQuery(mq2);
		high2.setStartTimestamp(mq2.getEndTimestamp());
		high2.setEndTimestamp(mq2.getEndTimestamp());
		highQ1.add(high1);
		highQ2.add(high2);
				
		when(serviceMock.getMetrics(Matchers.anyListOf(MetricQuery.class))).thenReturn(filterOver(metrics.get(0), bound1, miniMQ1Higher.get(0)));
		when(serviceMock.getMetrics(Matchers.anyListOf(MetricQuery.class))).thenReturn(filterOver(metrics.get(0), bound2, miniMQ2Higher.get(0)));
		
		when(serviceMock.getMetrics(highQ1)).thenReturn(outOfBounds());
		when(serviceMock.getMetrics(highQ2)).thenReturn(outOfBounds());
		
		//System.out.println("About to make the scanners");
		MetricScanner scanner1 = new MetricScanner(lowElems(metrics.get(0), bound1), mq1, serviceMock, bound1);
		//System.out.println("Made the first scanner");
		MetricScanner scanner2 = new MetricScanner(lowElems(metrics.get(0), bound2), mq2, serviceMock, bound2);
		
		Map<Long, Double> dps = metrics.get(0).getDatapoints();
		//System.out.println("Original datapoints are " + dps.toString());
		//System.out.println("Starting on scanner 1");
		checkDatapointMatch(dps, scanner1);
		//System.out.println("Starting on scanner 2");
		checkDatapointMatch(dps, scanner2);
	}
	
	@Test
	public void testSetComposition() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 1);
		MetricQuery query1 = toQuery(metrics.get(0));
		MetricQuery query2 = new MetricQuery(query1);
		
		assert(query1.equals(query2));
		
		new MetricScanner(metrics.get(0), query1, serviceMock, null);
		new MetricScanner(metrics.get(0), query2, serviceMock, null);
		
		assert(MetricScanner.findScanner(metrics.get(0), query1, serviceMock, null).size() == 2);
	}
	
	public Map<MetricQuery, List<Metric>> countOutOfBounds() {
		return outOfBounds();
	}
	
	@Test
	public void testFinishEarlyWhenDone() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		Metric metric = createRandomMetrics(null, null, 1).get(0);
		MetricQuery query = toQuery(metric);
						
		MetricQuery tooHigh = new MetricQuery(query.getScope(), query.getMetric(), query.getTags(), query.getEndTimestamp(), query.getEndTimestamp());
		List<MetricQuery> high = new ArrayList<MetricQuery>();
		high.add(tooHigh);
		
		when(serviceMock.getMetrics(high)).thenReturn(countOutOfBounds());
		
		MetricScanner scanner = new MetricScanner(metric, query, serviceMock, query.getEndTimestamp());
		
		checkDatapointMatch(metric.getDatapoints(), scanner);
		assert(scanner.getCallsToFetch() == 0);
	}
	
	@Test
	public void testSumNScanners() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		int n = 8;
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, n-1);
		metrics.add(new Metric(metrics.get(0)));	// just give it a clone of this but change scope to make sure there will be some overlap in datapoints
		metrics.get(n-1).setScope(createRandomName());
		List<MetricQuery> queries = toQueries(metrics);
		List<Long> boundaries = new ArrayList<>();
				
		for (int i = 0; i < n; i++) {
			//System.out.println("Metric #" + i + " had dps " + metrics.get(i).getDatapoints().toString());
			List<MetricQuery> upperHalf = new ArrayList<>();
			Long bound = queries.get(i).getStartTimestamp() + (queries.get(i).getEndTimestamp() - queries.get(i).getStartTimestamp()) / 2;
			boundaries.add(bound);
			upperHalf.add(new MetricQuery(queries.get(i).getScope(), queries.get(i).getMetric(), queries.get(i).getTags(), bound, queries.get(i).getEndTimestamp()));
			List<MetricQuery> tooHigh = new ArrayList<>();
			tooHigh.add(new MetricQuery(queries.get(i).getScope(), queries.get(i).getMetric(), queries.get(i).getTags(), queries.get(i).getEndTimestamp(), queries.get(i).getEndTimestamp()));
			
			//System.out.println(metrics.get(i).getDatapoints().toString());
			
			when(serviceMock.getMetrics(upperHalf)).thenReturn(filterOver(metrics.get(i), bound, queries.get(i)));
			when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
		}
		
		List<MetricScanner> scanners = new ArrayList<>();
		
		for (int i = 0; i < n; i++) {
			MetricScanner scanner = new MetricScanner(lowElems(metrics.get(i), boundaries.get(i)), queries.get(i), serviceMock, boundaries.get(i));
			scanners.add(scanner);
		}
		
		checkSumMatch(metrics, scanners);
	}
	
	@Test
	public void testOneToManyQueryToMetricResults() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		Metric m1 = createRandomMetrics(null, null, 1).get(0);
		Metric m2 = new Metric(m1);
		
		m2.setTags(new HashMap<>());
		String tag2 = createRandomName();
		m2.setTag(tag2, "" + (Double) 2.0);
		
		MetricQuery query = toQuery(m1);
		query.setTags(new HashMap<>());	// query * across all tags
		MetricQuery query2 = toQuery(m2);
		query2.setTags(new HashMap<>());
		
		assert(!m1.equals(m2));
		assert(query.equals(query2));
		
		Long boundary = query.getStartTimestamp() + (query.getEndTimestamp() - query.getStartTimestamp()) / 2;
		
		List<MetricQuery> highQuery = new ArrayList<>();
		highQuery.add(new MetricQuery(query.getScope(), query.getMetric(), query.getTags(), boundary, query.getEndTimestamp()));
		
		when(serviceMock.getMetrics(highQuery)).thenReturn(doubleFilterOver(m1, m2, boundary, query));
		
		MetricScanner scanner1 = new MetricScanner(lowElems(m1, boundary), query, serviceMock, boundary);
		MetricScanner scanner2 = new MetricScanner(lowElems(m2, boundary), query, serviceMock, boundary);
		
		assert(scanner1.getMetric().equals(m1));
		assert(scanner2.getMetric().equals(m2));
		
		checkDatapointMatch(m1.getDatapoints(), scanner1);
		checkDatapointMatch(m2.getDatapoints(), scanner2);
		
		assert(scanner1.getCallsToFetch() + scanner2.getCallsToFetch() <= 1); // could be zero if there is only one datapoint included in this metric
	}
	
	@Test
	public void testHandleMissingData() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		for (Metric m : metrics) {
			Map<Long, Double> dps = new HashMap<>(m.getDatapoints());
			for (Long time : dps.keySet()) {
				dps.put(time, null);	 // set all of the data to be missing
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
		
		for (int i = 0; i < metrics.size(); i++) {
			checkDatapointMatch(metrics.get(i).getDatapoints(), scanners.get(i));
		}
		
	}	
	
	@Test
	public void testDatapointPushToMetric() {
		
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
		
		for (int i = 0; i < metrics.size(); i++) {
			while (scanners.get(i).hasNextDP()) {
				scanners.get(i).getNextDP();
			}
			
			assert(scanners.get(i).getMetric().getDatapoints().equals(metrics.get(i).getDatapoints()));
		}
	}
	
	@Test
	public void testUpdateMetricWithConcurrencyConcern() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		List<MetricQuery> queries = toQueries(metrics);
		List<MetricScanner> scanners = new ArrayList<>();
		
		for (int i = 0; i < metrics.size(); i++) {
			Metric m = metrics.get(i);
			MetricQuery q = queries.get(i);
						
			Long bound = q.getStartTimestamp() + (q.getEndTimestamp() - q.getStartTimestamp()) / 2;
			MetricScanner s = new MetricScanner(lowElems(m, bound), q, serviceMock, bound);
			scanners.add(s);
		}
		
		for (int i = 0; i < metrics.size(); i++) {
			testConcurrentMetricScan(metrics.get(i), queries.get(i), serviceMock);
		}
	}
	
	@Test(expected = IllegalStateException.class)
	public void testExceptionIfTooManyDatapointsRequested() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 1);
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
		
		while(scanners.get(0).hasNextDP()) {
			scanners.get(0).getNextDP();
		}
		
		assert(true); // should get at least this far
		
		scanners.get(0).getNextDP(); // this should throw the expected error
	}
	
	@Test
	public void testFillInMetricDataGaps() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		List<MetricQuery> queries = toQueries(metrics);
		List<MetricScanner> scanners = new ArrayList<>();
		List<MetricScanner> scannerCopies = new ArrayList<>();
		
		for (int i = 0; i < metrics.size(); i++) {
			Metric m = metrics.get(i);
			MetricQuery q = queries.get(i);
						
			Long bound = q.getStartTimestamp() + (q.getEndTimestamp() - q.getStartTimestamp()) / 2;
			List<MetricQuery> highQuery = new ArrayList<>();
			highQuery.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), bound, q.getEndTimestamp()));
			List<MetricQuery> tooHigh = new ArrayList<>();
			tooHigh.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getEndTimestamp(), q.getEndTimestamp()));
			
			List<MetricQuery> lowQuery = new ArrayList<>();
			lowQuery.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getStartTimestamp(), bound));
			
			MetricScanner s = new MetricScanner(lowElems(m, bound), q, serviceMock, bound);
			scanners.add(s);
			Metric mCopy = new Metric(m);
			mCopy.setDatapoints(new HashMap<Long, Double>());
			MetricScanner s2 = new MetricScanner(mCopy, q, serviceMock, q.getStartTimestamp());
			scannerCopies.add(s2);
			
			when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
			when(serviceMock.getMetrics(highQuery)).thenReturn(filterOver(m, bound, highQuery.get(0)));
			
			when(serviceMock.getMetrics(lowQuery)).thenReturn(filterUnder(m, bound, lowQuery.get(0)));
		}
		
		for (int i = 0; i < metrics.size(); i++) {
			checkDatapointFilling(metrics.get(i), scanners.get(i), scannerCopies.get(i), serviceMock);
		}
	}
	
	@Test
	public void testMakeScannerForMetricWithNoDatapoints() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		for (Metric m : metrics) {
			m.setDatapoints(new HashMap<>());
		}
		List<MetricQuery> queries = toQueriesEmpty(metrics);
		List<MetricScanner> scanners = new ArrayList<>();
		List<MetricScanner> scannerCopies = new ArrayList<>();
		
		for (int i = 0; i < metrics.size(); i++) {
			Metric m = metrics.get(i);
			MetricQuery q = queries.get(i);
						
			Long bound = q.getStartTimestamp() + (q.getEndTimestamp() - q.getStartTimestamp()) / 2;
			List<MetricQuery> highQuery = new ArrayList<>();
			highQuery.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), bound, q.getEndTimestamp()));
			List<MetricQuery> tooHigh = new ArrayList<>();
			tooHigh.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getEndTimestamp(), q.getEndTimestamp()));
			
			List<MetricQuery> lowQuery = new ArrayList<>();
			lowQuery.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getStartTimestamp(), bound));
			
			MetricScanner s = new MetricScanner(lowElems(m, bound), q, serviceMock, bound);
			scanners.add(s);
			Metric mCopy = new Metric(m);
			mCopy.setTags(new HashMap<>());
			mCopy.setTag(createRandomName(), "" + random.nextDouble()*100);
			mCopy.setDatapoints(new HashMap<Long, Double>());
			MetricScanner s2 = new MetricScanner(mCopy, q, serviceMock, q.getStartTimestamp());
			scannerCopies.add(s2);
			
			when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
			when(serviceMock.getMetrics(highQuery)).thenReturn(filterOver(m, bound, highQuery.get(0)));
			
			when(serviceMock.getMetrics(lowQuery)).thenReturn(filterUnder(mCopy, bound, lowQuery.get(0)));
		}
		
		for (int i = 0; i < metrics.size(); i++) {
			checkDatapointMatch(metrics.get(i).getDatapoints(), scanners.get(i));
		}
	}
	
	@Test
	public void testScannerWithNoInitialDatapoints() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		List<MetricQuery> queries = toQueries(metrics);
		List<MetricScanner> scanners = new ArrayList<>();
		
		for (int i = 0; i < metrics.size(); i++) {
			Metric m = metrics.get(i);
			MetricQuery q = queries.get(i);
						
			Long bound = q.getStartTimestamp() + (q.getEndTimestamp() - q.getStartTimestamp()) / 2;
			Map<Long, Double> dps = new HashMap<>();
			for (Long time : dps.keySet()) {
				if (time > bound) {
					dps.put(time, dps.get(time));
				}
			}
			m.setDatapoints(dps);
			metrics.get(i).setDatapoints(dps);

			List<MetricQuery> highQuery = new ArrayList<>();
			highQuery.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), bound, q.getEndTimestamp()));
			List<MetricQuery> tooHigh = new ArrayList<>();
			tooHigh.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getEndTimestamp(), q.getEndTimestamp()));
			
			List<MetricQuery> lowQuery = new ArrayList<>();
			lowQuery.add(new MetricQuery(q.getScope(), q.getMetric(), q.getTags(), q.getStartTimestamp(), bound));
			
			MetricScanner s = new MetricScanner(lowElems(m, bound), q, serviceMock, bound);
			scanners.add(s);
			
			when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
			when(serviceMock.getMetrics(highQuery)).thenReturn(filterOver(m, bound, highQuery.get(0)));
			when(serviceMock.getMetrics(lowQuery)).thenReturn(filterUnder(m, bound, lowQuery.get(0)));
		}
		
		for (int i = 0; i < metrics.size(); i++) {
			checkDatapointMatchFromEmpty(metrics.get(i).getDatapoints(), scanners.get(i));
		}
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testScannerForTooFarArgument() {
		
		TSDBService serviceMock = mock(TSDBService.class);
		Metric m = createRandomMetrics(null, null, 1).get(0);
		MetricQuery q = new MetricQuery(m.getScope(), m.getMetric(), m.getTags(), Long.MIN_VALUE, Long.MAX_VALUE);
		
		Long bound = q.getStartTimestamp() + (q.getEndTimestamp() - q.getStartTimestamp()) / 2;
		
		MetricScanner s = new MetricScanner(lowElems(m, bound), q, serviceMock, bound);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testOutOfRangeChunkResetTooHigh() {
		MetricScanner.setChunkPercentage(1.0);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testOutOfRangeChunkResetTooLow() {
		MetricScanner.setChunkPercentage(-0.25);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testOutOfRangeFetchResetTooHigh() {
		MetricScanner.setFetchPercentage(1.25);
	}
	
	@Test(expected = IllegalArgumentException.class)
	public void testOutOfRangeFetchResetTooLow() {
		MetricScanner.setFetchPercentage(0.0);
	}
	
	@Test
	public void testResetChunkPercentage() {
		Double perc = random.nextDouble();
		if (perc.equals(0.0)) {
			perc = 0.12;
		}
		
		MetricScanner.setChunkPercentage(perc);
		assert(perc.equals(MetricScanner.getChunkPercentage()));
	}
	
	@Test
	public void testResetFetchPercentage() {
		Double perc = random.nextDouble();
		if (perc.equals(0.0)) {
			perc = 0.88;
		}
		MetricScanner.setFetchPercentage(perc);
		assert(perc.equals(MetricScanner.getFetchPercentage()));
	}
	
	@Test
	public void testLongQueryTimeRange() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		Metric m = createRandomMetrics(null, null, 1).get(0);
		Map<Long, Double> dps = new HashMap<>();
		for (Long time : m.getDatapoints().keySet()) {
			if (time >= 0L) {
				dps.put(time, m.getDatapoints().get(time));
			}
		}
		m.setDatapoints(dps);
		MetricQuery q = toQuery(m);
		
		q.setStartTimestamp(0L);
		q.setEndTimestamp(Long.MAX_VALUE - 1000L);
		
		//System.out.println("end time is " + q.getEndTimestamp());
		//System.out.println(q + " is original query \n\n");
		
		Long bound = q.getStartTimestamp() + (q.getEndTimestamp() - q.getStartTimestamp()) / 2;
		MetricScanner s = new MetricScanner(lowElems(m, bound), q, serviceMock, bound);
		
		List<MetricQuery> highQuery = new ArrayList<>();
		highQuery.add(new MetricQuery(m.getScope(), m.getMetric(), m.getTags(), bound, q.getEndTimestamp()));
		List<MetricQuery> tooHigh = new ArrayList<>();
		tooHigh.add(new MetricQuery(m.getScope(), m.getMetric(), m.getTags(), q.getEndTimestamp(), q.getEndTimestamp()));
		
		when(serviceMock.getMetrics(tooHigh)).thenReturn(filterOver(m, bound, highQuery.get(0)));
		when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
		
		checkDatapointMatch(m.getDatapoints(), s);
	}
	
	@Test
	public void testWithChunkPercentageAtThird() {
		
		double perc = 1.0 / 3;
		MetricScanner.setChunkPercentage(perc);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		List<MetricQuery> queries = toQueries(metrics);
		List<MetricScanner> scanners = new ArrayList<>();
		
		for (int i = 0; i < metrics.size(); i++) {
			
			Long bound1 = queries.get(i).getStartTimestamp() + (queries.get(i).getEndTimestamp() - queries.get(i).getStartTimestamp()) / 3;
			Long bound2 = queries.get(i).getStartTimestamp() + 2 * (queries.get(i).getEndTimestamp() - queries.get(i).getStartTimestamp()) / 3;
			
			List<MetricQuery> middleThird = new ArrayList<>();
			middleThird.add(new MetricQuery(metrics.get(i).getScope(), metrics.get(i).getMetric(), metrics.get(i).getTags(), bound1, bound2));
			List<MetricQuery> highThird = new ArrayList<>();
			highThird.add(new MetricQuery(metrics.get(i).getScope(), metrics.get(i).getMetric(), metrics.get(i).getTags(), bound2, queries.get(i).getEndTimestamp()));
			List<MetricQuery> tooHigh = new ArrayList<>();
			tooHigh.add(new MetricQuery(metrics.get(i).getScope(), metrics.get(i).getMetric(), metrics.get(i).getTags(), queries.get(i).getEndTimestamp(), queries.get(i).getEndTimestamp()));
		
			MetricScanner scanner = new MetricScanner(lowElems(metrics.get(i), bound1), queries.get(i), serviceMock, bound1);
			scanners.add(scanner);
			
			when(serviceMock.getMetrics(middleThird)).thenReturn(filterBetween(metrics.get(i), bound1, bound2, middleThird.get(0)));
			when(serviceMock.getMetrics(highThird)).thenReturn(filterOver(metrics.get(i), bound2, highThird.get(0)));
			when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
		}
		
		for (int i = 0; i < metrics.size(); i++) {
			checkDatapointMatch(metrics.get(i).getDatapoints(), scanners.get(i));
		}
	}
	
	@Test
	public void checkClone() {
		
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
			MetricScanner s2 = new MetricScanner(s);
			scanners.add(s2);
			
			when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
			when(serviceMock.getMetrics(highQuery)).thenReturn(filterOver(m, bound, highQuery.get(0)));
		}
		
		for (int i = 0; i < metrics.size(); i++) {
			while (scanners.get(i).hasNextDP()) {
				scanners.get(i).getNextDP();
			}
			
			assert(scanners.get(i).getMetric().getDatapoints().equals(metrics.get(i).getDatapoints()));
		}
		
	}
}

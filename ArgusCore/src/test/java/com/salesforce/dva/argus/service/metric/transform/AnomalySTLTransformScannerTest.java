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
import com.salesforce.dva.argus.service.tsdb.MetricPager;
import com.salesforce.dva.argus.service.tsdb.MetricPagerTransform;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.service.tsdb.MetricScanner;

public class AnomalySTLTransformScannerTest extends AbstractTest {

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
	public void testTransformNoConstantUnder104DatapointsOver4Datapoints() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		for (Metric m : metrics) {
			Map<Long, Double> datapoints = new HashMap<>(m.getDatapoints());
			if (m.getDatapoints().size() < 4) {
				Long baseTime = datapoints.keySet().iterator().next();
				datapoints.put(baseTime + 1000L, random.nextDouble() * 500);
				datapoints.put(baseTime + 2000L, random.nextDouble() * 500);
				datapoints.put(baseTime + 3000L, random.nextDouble() * 500);
			}
			if (datapoints.size() % 2 == 0) {
				datapoints.put(Collections.max(datapoints.keySet()) + 1000L, random.nextDouble() * 500); // guaranteed to be a unique point
			}
			m.setDatapoints(datapoints);
			assert(m.getDatapoints().size() >= 4);
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

		
		AnomalySTLTransform transform = new AnomalySTLTransform();
		
		for (int i = 0; i < metrics.size(); i++) {
			List<Metric> mList = new ArrayList<>();
			mList.add(metrics.get(i));
			List<MetricScanner> msList = new ArrayList<>();
			msList.add(scanners.get(i));
			
			if (metrics.get(i).getDatapoints().size() < 52 * 2) {
				List<Metric> expected = transform.transform(mList);
				List<Metric> actual = transform.transformScanner(msList);
			
				assert(expected.equals(actual));
			}
		}
	}
	
	@Test
	public void testTransformNoConstantOver104Datapoints() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		for (Metric m : metrics) {
			Map<Long, Double> datapoints = new HashMap<>(m.getDatapoints());
			while(datapoints.size() < 52 * 2) {
				datapoints.put(Collections.max(datapoints.keySet()) + 1000L, random.nextDouble() * 500.0);
			}
			if (datapoints.size() % 2 == 0) {
				datapoints.put(Collections.max(datapoints.keySet()) + 1000L, random.nextDouble() * 500); // guaranteed to be a unique point
			}
			m.setDatapoints(datapoints);
			assert(m.getDatapoints().size() >= 104);
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

		
		AnomalySTLTransform transform = new AnomalySTLTransform();
		
		for (int i = 0; i < metrics.size(); i++) {
			List<Metric> mList = new ArrayList<>();
			mList.add(metrics.get(i));
			List<MetricScanner> msList = new ArrayList<>();
			msList.add(scanners.get(i));
			
			if (metrics.get(i).getDatapoints().size() < 52 * 2) {
				List<Metric> expected = transform.transform(mList);
				List<Metric> actual = transform.transformScanner(msList);
			
				assert(expected.equals(actual));
			}
		}
	}
	
	@Test
	public void testTransformUnder4DatapointsForFailure() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		for (Metric m : metrics) {
			Map<Long, Double> datapoints = new HashMap<>();
			int dpCount = random.nextInt(4);
			datapoints.put(System.currentTimeMillis(), random.nextDouble() * 500);
			for (int i = 1; i < dpCount; i++) {
				datapoints.put(Collections.max(datapoints.keySet()) + 1000L, random.nextDouble() * 500);
			}
			m.setDatapoints(datapoints);
			assert(m.getDatapoints().size() < 4);
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

		
		AnomalySTLTransform transform = new AnomalySTLTransform();
		
		for (int i = 0; i < metrics.size(); i++) {
			List<MetricScanner> msList = new ArrayList<>();
			msList.add(scanners.get(i));
			
			if (metrics.get(i).getDatapoints().size() < 52 * 2) {
				try {
					transform.transformScanner(msList);
					assert(false); // should fail before this
				} catch (IllegalStateException e) {
					assert(true);
				}
			}
		}
	}
	
	@Test
	public void testTransformWithTwoConstantsResid() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		for (Metric m : metrics) {
			Map<Long, Double> datapoints = new HashMap<>(m.getDatapoints());
			if (m.getDatapoints().size() < 4) {
				Long baseTime = datapoints.keySet().iterator().next();
				datapoints.put(baseTime + 1000L, random.nextDouble() * 500);
				datapoints.put(baseTime + 2000L, random.nextDouble() * 500);
				datapoints.put(baseTime + 3000L, random.nextDouble() * 500);
			}
			if (datapoints.size() % 2 == 0) {
				datapoints.put(Collections.max(datapoints.keySet()) + 1000L, random.nextDouble() * 500); // guaranteed to be a unique point
			}
			m.setDatapoints(datapoints);
			assert(m.getDatapoints().size() >= 4);
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

		
		AnomalySTLTransform transform = new AnomalySTLTransform();
		
		List<String> constants = new ArrayList<>(2);
		constants.add(null);
		constants.add("resid");
		
		for (int i = 0; i < metrics.size(); i++) {
			List<Metric> mList = new ArrayList<>();
			mList.add(metrics.get(i));
			List<MetricScanner> msList = new ArrayList<>();
			msList.add(scanners.get(i));
			
			int lower = metrics.get(i).getDatapoints().size() / 2 == 2 ? 0 : random.nextInt(metrics.get(i).getDatapoints().size() / 2 - 2);
			constants.set(0, "" + (lower + 2));
			
			List<Metric> expected = transform.transform(mList, constants);
			List<Metric> actual = transform.transformScanner(msList, constants);
			
			assert(expected.equals(actual));
		}
	}
	
	@Test
	public void testTransformWithTwoConstantsAnomaly() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		for (Metric m : metrics) {
			Map<Long, Double> datapoints = new HashMap<>(m.getDatapoints());
			if (m.getDatapoints().size() < 4) {
				Long baseTime = datapoints.keySet().iterator().next();
				datapoints.put(baseTime + 1000L, random.nextDouble() * 500);
				datapoints.put(baseTime + 2000L, random.nextDouble() * 500);
				datapoints.put(baseTime + 3000L, random.nextDouble() * 500);
			}
			if (datapoints.size() % 2 == 0) {
				datapoints.put(Collections.max(datapoints.keySet()) + 1000L, random.nextDouble() * 500); // guaranteed to be a unique point
			}
			m.setDatapoints(datapoints);
			assert(m.getDatapoints().size() >= 4);
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

		
		AnomalySTLTransform transform = new AnomalySTLTransform();
		
		List<String> constants = new ArrayList<>(2);
		constants.add(null);
		constants.add("anomalyScore");
		
		for (int i = 0; i < metrics.size(); i++) {
			List<Metric> mList = new ArrayList<>();
			mList.add(metrics.get(i));
			List<MetricScanner> msList = new ArrayList<>();
			msList.add(scanners.get(i));
			
			int lower = metrics.get(i).getDatapoints().size() / 2 == 2 ? 0 : random.nextInt(metrics.get(i).getDatapoints().size() / 2 - 2);
			constants.set(0, "" + (lower + 2));
			
			List<Metric> expected = transform.transform(mList, constants);
			List<Metric> actual = transform.transformScanner(msList, constants);
			
			assert(expected.equals(actual));
		}
	}
	
	@Test
	public void testTransformWithOneConstantAnomalyByDefault() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		for (Metric m : metrics) {
			Map<Long, Double> datapoints = new HashMap<>(m.getDatapoints());
			if (m.getDatapoints().size() < 4) {
				Long baseTime = datapoints.keySet().iterator().next();
				datapoints.put(baseTime + 1000L, random.nextDouble() * 500);
				datapoints.put(baseTime + 2000L, random.nextDouble() * 500);
				datapoints.put(baseTime + 3000L, random.nextDouble() * 500);
			}
			if (datapoints.size() % 2 == 0) {
				datapoints.put(Collections.max(datapoints.keySet()) + 1000L, random.nextDouble() * 500); // guaranteed to be a unique point
			}
			m.setDatapoints(datapoints);
			assert(m.getDatapoints().size() >= 4);
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

		
		AnomalySTLTransform transform = new AnomalySTLTransform();
		
		List<String> constants = new ArrayList<>(1);
		constants.add(null);
		
		for (int i = 0; i < metrics.size(); i++) {
			List<Metric> mList = new ArrayList<>();
			mList.add(metrics.get(i));
			List<MetricScanner> msList = new ArrayList<>();
			msList.add(scanners.get(i));
			
			int lower = metrics.get(i).getDatapoints().size() / 2 == 2 ? 0 : random.nextInt(metrics.get(i).getDatapoints().size() / 2 - 2);
			constants.set(0, "" + (lower + 2));
			
			List<Metric> expected = transform.transform(mList, constants);
			List<Metric> actual = transform.transformScanner(msList, constants);
			
			assert(expected.equals(actual));
		}
	}
	
	@Test
	public void testDPIntegrityAndDisposal() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		for (Metric m : metrics) {
			Map<Long, Double> datapoints = new HashMap<>(m.getDatapoints());
			if (m.getDatapoints().size() < 4) {
				Long baseTime = datapoints.keySet().iterator().next();
				datapoints.put(baseTime + 1000L, random.nextDouble() * 500);
				datapoints.put(baseTime + 2000L, random.nextDouble() * 500);
				datapoints.put(baseTime + 3000L, random.nextDouble() * 500);
			}
			if (datapoints.size() % 2 == 0) {
				datapoints.put(Collections.max(datapoints.keySet()) + 1000L, random.nextDouble() * 500); // guaranteed to be a unique point
			}
			m.setDatapoints(datapoints);
			assert(m.getDatapoints().size() >= 4);
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

		
		AnomalySTLTransform transform = new AnomalySTLTransform();
		
		List<String> constants = new ArrayList<>(1);
		constants.add(null);
		
		for (int i = 0; i < metrics.size(); i++) {
			List<Metric> mList = new ArrayList<>();
			mList.add(metrics.get(i));
			List<MetricScanner> msList = new ArrayList<>();
			msList.add(scanners.get(i));
			
			int lower = metrics.get(i).getDatapoints().size() / 2 == 2 ? 0 : random.nextInt(metrics.get(i).getDatapoints().size() / 2 - 2);
			constants.set(0, "" + (lower + 2));
			
			List<Metric> expected = transform.transform(mList, constants);
			List<Metric> actual = transform.transformScanner(msList, constants);
			
			assert(expected.equals(actual));
			
			double epsilon = Math.pow(10, -6);
			for (int j = 0; j < expected.size(); j++) {
				for (Long time : expected.get(j).getDatapoints().keySet()) {
					assert(Math.abs(expected.get(j).getDatapoints().get(time) - actual.get(j).getDatapoints().get(time)) < epsilon);
				}
			}
			assert(!MetricScanner.existingScanner(metrics.get(i), queries.get(i)));
		}
	}
	
	private Metric filterUnder(Metric m, Long bound) {
		Metric res = new Metric(m);
		Map<Long, Double> dps = new HashMap<>();
		for (Long key : m.getDatapoints().keySet()) {
			if (key <= bound) {
				dps.put(key, m.getDatapoints().get(key));
			}
		}
		res.setDatapoints(dps);
		return res;
	}
	
	@Test
	public void testPager() {
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 3);
		for (Metric m : metrics) {
			Map<Long, Double> datapoints = new HashMap<>(m.getDatapoints());
			if (m.getDatapoints().size() < 4) {
				Long baseTime = datapoints.keySet().iterator().next();
				datapoints.put(baseTime + 1000L, random.nextDouble() * 500);
				datapoints.put(baseTime + 2000L, random.nextDouble() * 500);
				datapoints.put(baseTime + 3000L, random.nextDouble() * 500);
			}
			if (datapoints.size() % 2 == 0) {
				datapoints.put(Collections.max(datapoints.keySet()) + 1000L, random.nextDouble() * 500); // guaranteed to be a unique point
			}
			m.setDatapoints(datapoints);
			assert(m.getDatapoints().size() >= 4);
		}
		List<MetricQuery> queries = toQueries(metrics);
		List<MetricScanner> scanners = new ArrayList<>();
		
		for (int i = 0; i < metrics.size(); i++) {
			List<MetricQuery> upperHalf = new ArrayList<>();
			Long bound = queries.get(i).getStartTimestamp() + (queries.get(i).getEndTimestamp() - queries.get(i).getStartTimestamp()) / 2;
			upperHalf.add(new MetricQuery(queries.get(i).getScope(), queries.get(i).getMetric(), queries.get(i).getTags(), bound, queries.get(i).getEndTimestamp()));
			List<MetricQuery> tooHigh = new ArrayList<>();
			tooHigh.add(new MetricQuery(queries.get(i).getScope(), queries.get(i).getMetric(), queries.get(i).getTags(), queries.get(i).getEndTimestamp(), queries.get(i).getEndTimestamp()));
			
			scanners.add(new MetricScanner(filterUnder(metrics.get(i), bound), queries.get(i), serviceMock, bound));
			
			when(serviceMock.getMetrics(upperHalf)).thenReturn(filterOver(metrics.get(i), bound, upperHalf.get(0)));
			when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
		}
		
		Transform transform = new AnomalySTLTransform();
		
		for (int i = 0; i < metrics.size(); i++) {
			List<Metric> mList = new ArrayList<>();
			mList.add(metrics.get(i));
			List<MetricScanner> msList = new ArrayList<>();
			msList.add(scanners.get(i));
			
			List<Metric> expected = transform.transform(mList);
			Long chunkTime = (queries.get(i).getEndTimestamp() - queries.get(i).getStartTimestamp()) / 7;
			MetricPager stream = new MetricPagerTransform(msList, chunkTime, transform);			
			
			int chunk = random.nextInt(stream.getNumberChunks());
			Long start = stream.getStartTime() + (chunk) * chunkTime;
			Long end = Math.min(start + chunkTime, stream.getEndTime());
			List<Metric> resChunk = stream.getMetricChunk(chunk);
			for (Metric m : expected) {
				TreeMap<Long, Double> dps = new TreeMap<>(m.getDatapoints());
				if (!dps.subMap(start, end + 1).isEmpty()) {
					assert(resChunk.contains(m));
					int index = resChunk.indexOf(m);
					assert(dps.subMap(start, end + 1).equals(resChunk.get(index).getDatapoints()));
				} else {
					assert(resChunk.contains(m));
					assert(resChunk.get(resChunk.indexOf(m)).getDatapoints().isEmpty());
				}
			}
			
			List<Metric> act = stream.getMetricChunk(0);
			for (int j = 1; j < stream.getNumberChunks(); j++) {
				List<Metric> b = stream.getMetricChunk(j);
				for (Metric m : b) {
					act.get(act.indexOf(m)).addDatapoints(m.getDatapoints());
				}
			}
			
			for (Metric m : expected) {
				assert(act.contains(m));
				assert(m.getDatapoints().equals(act.get(act.indexOf(m)).getDatapoints()));
			}
		}
	}
}

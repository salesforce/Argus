package com.salesforce.dva.argus.service.metric.transform;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.Test;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.metric.transform.AnomalyDetectionKMeansTransform;
import com.salesforce.dva.argus.service.tsdb.MetricPager;
import com.salesforce.dva.argus.service.tsdb.MetricPagerTransform;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.service.tsdb.MetricScanner;

public class AnomalyDetectionKMeansTransformScannerTest extends AbstractTest {

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
	public void testTransformOneCluster() {
		
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
		
		AnomalyDetectionKMeansTransform transformMetrics = new AnomalyDetectionKMeansTransform();
		AnomalyDetectionKMeansTransform transformScanners = new AnomalyDetectionKMeansTransform();
		List<String> constants = new ArrayList<>();
		constants.add("1");
		
		for (int i = 0; i < metrics.size(); i++) {
			List<Metric> mList = new ArrayList<>();
			mList.add(metrics.get(i));
			List<MetricScanner> msList = new ArrayList<>();
			msList.add(scanners.get(i));
			
			List<Metric> expected = transformMetrics.transform(mList, constants);
			List<Metric> actual = transformScanners.transformScanner(msList, constants);
			
			assert(actual.equals(expected));
		}
	}
	
	@Test
	public void testTransformFiveClusters() {
		
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
		
		AnomalyDetectionKMeansTransform transformMetrics = new AnomalyDetectionKMeansTransform();
		AnomalyDetectionKMeansTransform transformScanners = new AnomalyDetectionKMeansTransform();
		List<String> constants = new ArrayList<>();
		constants.add("5");
		
		for (int i = 0; i < metrics.size(); i++) {
			List<Metric> mList = new ArrayList<>();
			mList.add(metrics.get(i));
			List<MetricScanner> msList = new ArrayList<>();
			msList.add(scanners.get(i));
			
			List<Metric> expected = transformMetrics.transform(mList, constants);
			List<Metric> actual = transformScanners.transformScanner(msList, constants);
			
			assert(actual.equals(expected));
		}
	}
	
	@Test
	public void testTransformTenClusters() {
		
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
		
		AnomalyDetectionKMeansTransform transformMetrics = new AnomalyDetectionKMeansTransform();
		AnomalyDetectionKMeansTransform transformScanners = new AnomalyDetectionKMeansTransform();
		List<String> constants = new ArrayList<>();
		constants.add("10");
		
		for (int i = 0; i < metrics.size(); i++) {
			List<Metric> mList = new ArrayList<>();
			mList.add(metrics.get(i));
			List<MetricScanner> msList = new ArrayList<>();
			msList.add(scanners.get(i));
			
			List<Metric> expected = transformMetrics.transform(mList, constants);
			List<Metric> actual = transformScanners.transformScanner(msList, constants);
			
			assert(actual.equals(expected));
		}
	}
	
	@Test
	public void testDPIntegrityAndDisposal() {
		
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
		
		AnomalyDetectionKMeansTransform transformMetrics = new AnomalyDetectionKMeansTransform();
		AnomalyDetectionKMeansTransform transformScanners = new AnomalyDetectionKMeansTransform();
		List<String> constants = new ArrayList<>();
		constants.add("10");
		
		for (int i = 0; i < metrics.size(); i++) {
			List<Metric> mList = new ArrayList<>();
			mList.add(metrics.get(i));
			List<MetricScanner> msList = new ArrayList<>();
			msList.add(scanners.get(i));
			
			List<Metric> expected = transformMetrics.transform(mList, constants);
			List<Metric> actual = transformScanners.transformScanner(msList, constants);
			
			assert(actual.equals(expected));
			
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
		
		Transform transform = new AnomalyDetectionKMeansTransform();
		List<String> constants = new ArrayList<>();
		constants.add("3");
	
		for (int i = 0; i < metrics.size(); i++) {
			List<Metric> l = new ArrayList<>();
			l.add(metrics.get(i));
			List<MetricScanner> ms = new ArrayList<>();
			ms.add(scanners.get(i));
			List<Metric> expected = transform.transform(l, constants);
			Long chunkTime = (queries.get(i).getEndTimestamp() - queries.get(i).getStartTimestamp()) / 7;
			MetricPager stream = new MetricPagerTransform(ms, chunkTime, transform, constants);
		
			int chunk = random.nextInt(stream.getNumberChunks());
			Long start = stream.getStartTime() + (chunk) * stream.getChunkTime();
			Long end = Math.min(start + stream.getChunkTime(), stream.getEndTime());
			List<Metric> resChunk = stream.getMetricChunk(chunk);
			for (Metric m : expected) {
				TreeMap<Long, Double> dps = new TreeMap<>(m.getDatapoints());
				if (!dps.subMap(start, end + 1).isEmpty()) {
					assert(resChunk.contains(m));
					int index = resChunk.indexOf(m);
					Double epsilon = Math.pow(10, -6);
					
					for (int j = 0; j < dps.subMap(start, end + 1).size(); j++) {
						for (Long time : dps.subMap(start, end + 1).keySet()) {
							assert(Math.abs(dps.subMap(start, end + 1).get(time) - resChunk.get(index).getDatapoints().get(time)) < epsilon);
						}
					}
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
	
	@Test
	public void testPagerWithSlightlyProcessedScanner() {
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 1);
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
		
		Transform transform = new AnomalyDetectionKMeansTransform();
		List<String> constants = new ArrayList<>();
		constants.add("3");
		
		/* take one datapoint from the scanner */
		scanners.get(0).getNextDP();
	
		for (int i = 0; i < metrics.size(); i++) {
			List<Metric> l = new ArrayList<>();
			l.add(metrics.get(i));
			List<MetricScanner> ms = new ArrayList<>();
			ms.add(scanners.get(i));
			List<Metric> expected = transform.transform(l, constants);
			Long chunkTime = (queries.get(i).getEndTimestamp() - queries.get(i).getStartTimestamp()) / 7;
			MetricPager stream = new MetricPagerTransform(ms, chunkTime, transform, constants);
		
			int chunk = random.nextInt(stream.getNumberChunks());
			Long start = stream.getStartTime() + (chunk) * stream.getChunkTime();
			Long end = Math.min(start + stream.getChunkTime(), stream.getEndTime());
			List<Metric> resChunk = stream.getMetricChunk(chunk);
			for (Metric m : expected) {
				TreeMap<Long, Double> dps = new TreeMap<>(m.getDatapoints());
				if (!dps.subMap(start, end + 1).isEmpty()) {
					assert(resChunk.contains(m));
					int index = resChunk.indexOf(m);
					Double epsilon = Math.pow(10, -6);
					
					for (int j = 0; j < dps.subMap(start, end + 1).size(); j++) {
						for (Long time : dps.subMap(start, end + 1).keySet()) {
							assert(Math.abs(dps.subMap(start, end + 1).get(time) - resChunk.get(index).getDatapoints().get(time)) < epsilon);
						}
					}
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


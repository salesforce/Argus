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

public class MetricZipperTransformScannerTest extends AbstractTest {

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
	public void testMetricZipperTransformNoConstantsWithoutNullUsingDiffValueZipper() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		
		for (Metric m : metrics) {
			if (m.getDatapoints().size() < 2) {
				Map<Long, Double> dps = new HashMap<>(m.getDatapoints());
				dps.put(random.nextLong(), random.nextDouble() * 100.0);
				m.setDatapoints(dps);
			}
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
		
		ValueZipper zip = new DiffValueZipper();
		MetricZipperTransform transform = new MetricZipperTransform(zip);
		
		List<Metric> expected = transform.transform(metrics);
		List<Metric> actual = transform.transformScanner(scanners);
		
		assert(expected.equals(actual));
	}
	
	@Test
	public void testMetricZipperTransformNoConstantsWithNullUsingDiffValueZipper() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		
		for (Metric m : metrics) {
			Map<Long, Double> dps = new HashMap<>(m.getDatapoints());
			if (m.getDatapoints().size() < 2) {
				dps.put(random.nextLong(), random.nextDouble() * 100.0);
			}
			Long timestamp = Collections.min(dps.keySet()) + (Collections.max(dps.keySet()) - Collections.min(dps.keySet())) / 2;
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
		
		ValueZipper zip = new DiffValueZipper();
		MetricZipperTransform transform = new MetricZipperTransform(zip);
		
		List<Metric> expected = transform.transform(metrics);
		List<Metric> actual = transform.transformScanner(scanners);
		
		assert(expected.equals(actual));
	}
	
	@Test
	public void testMetricZipperZipWithoutNullUsingDiffValueZipper() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		
		for (Metric m : metrics) {
			if (m.getDatapoints().size() < 2) {
				Map<Long, Double> dps = new HashMap<>(m.getDatapoints());
				Long timestamp = Collections.min(dps.keySet()) + 1000L;
				dps.put(timestamp, random.nextDouble() * 100.0);
				m.setDatapoints(dps);
			}
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
		
		ValueZipper zip = new DiffValueZipper();
		MetricZipperTransform transform = new MetricZipperTransform(zip);
		
		Metric baseMetric = metrics.remove(0);
		MetricScanner baseScanner = scanners.remove(0);
		
		List<Metric> expected = transform.zip(metrics, baseMetric);
		List<Metric> actual = transform.zipScanner(scanners, baseScanner);
		
		assert(expected.equals(actual));
	}
	
	@Test
	public void testMetricZipperZipWithNullUsingDiffValueZipper() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		
		for (Metric m : metrics) {
			Map<Long, Double> dps = new HashMap<>(m.getDatapoints());
			if (m.getDatapoints().size() < 2) {
				Long timestamp = Collections.min(dps.keySet()) + 1000L;
				dps.put(timestamp, random.nextDouble() * 100.0);
			}
			Long timestamp = Collections.min(dps.keySet()) + (Collections.max(dps.keySet()) - Collections.min(dps.keySet())) / 2;
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
		
		ValueZipper zip = new DiffValueZipper();
		MetricZipperTransform transform = new MetricZipperTransform(zip);
		
		Metric baseMetric = metrics.remove(0);
		MetricScanner baseScanner = scanners.remove(0);
		
		List<Metric> expected = transform.zip(metrics, baseMetric);
		List<Metric> actual = transform.zipScanner(scanners, baseScanner);
		
		assert(expected.equals(actual));
	}
}
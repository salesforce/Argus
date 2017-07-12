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

public class ZeroIfMissingSumScannerTest extends AbstractTest {
	
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
	
	@Test
	public void testSumWithoutNull() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 2);
		
		for (int i = 0; i < metrics.size(); i++) {
			metrics.get(i).setNamespace(createRandomName());
			metrics.get(i).setDisplayName(createRandomName());
			metrics.get(i).setUnits("" + (double) i);
		}
		
		List<MetricQuery> queries = toQueries(metrics);
		List<Long> boundaries = new ArrayList<>();
		
		for (int i = 0; i < queries.size(); i++) {
			List<MetricQuery> upperHalf = new ArrayList<>();
			Long bound = queries.get(i).getStartTimestamp() + (queries.get(i).getEndTimestamp() - queries.get(i).getStartTimestamp()) / 2;
			boundaries.add(bound);
			upperHalf.add(new MetricQuery(queries.get(i).getScope(), queries.get(i).getMetric(), queries.get(i).getTags(), bound, queries.get(i).getEndTimestamp()));
			List<MetricQuery> tooHigh = new ArrayList<>();
			tooHigh.add(new MetricQuery(queries.get(i).getScope(), queries.get(i).getMetric(), queries.get(i).getTags(), queries.get(i).getEndTimestamp(), queries.get(i).getEndTimestamp()));
			
			when(serviceMock.getMetrics(upperHalf)).thenReturn(filterOver(metrics.get(i), bound, queries.get(i)));
			when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
		}
		
		List<MetricScanner> scanners = new ArrayList<>();
		for (int i = 0; i < queries.size(); i++) {
			scanners.add(new MetricScanner(metrics.get(i), queries.get(i), serviceMock, boundaries.get(i)));
		}
		
		
		ZeroIfMissingSum transform1 = new ZeroIfMissingSum();
		List<Metric> resultFromMetrics = transform1.transform(metrics);
	
		ZeroIfMissingSum transform2 = new ZeroIfMissingSum();
		List<Metric> resultFromScanners = transform2.transformScanner(scanners);
		
		assert(resultFromMetrics.equals(resultFromScanners));
	}
	
	@Test
	public void testSumWithNull() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 6);
		
		for (int i = 0; i < metrics.size(); i++) {
			metrics.get(i).setNamespace(createRandomName());
			metrics.get(i).setDisplayName(createRandomName());
			metrics.get(i).setUnits("" + (double) i);
			//Long randTime = random.nextLong();
			Long dpStart = Collections.min(metrics.get(i).getDatapoints().keySet());
			Long dpEnd = Collections.max(metrics.get(i).getDatapoints().keySet());
			Long randTime = dpStart + (dpStart / dpEnd);	// still put within this range or will take forever and be an outlier
			assert(randTime != null);
			//System.out.println(randTime);
			Map<Long, Double> dps = new HashMap<>(metrics.get(i).getDatapoints());
			//System.out.println("old dps were " + dps.toString());
			dps.put(randTime, null);
			//System.out.println("Got here");
			metrics.get(i).setDatapoints(dps);
			//System.out.println(metrics.get(i).getDatapoints());
		}
		
		
		List<MetricQuery> queries = toQueries(metrics);
		List<Long> boundaries = new ArrayList<>();
		
		for (int i = 0; i < queries.size(); i++) {
			//System.out.println("Metric #" + i + " had dps " + metrics.get(i).getDatapoints().toString());
			List<MetricQuery> upperHalf = new ArrayList<>();
			Long bound = queries.get(i).getStartTimestamp() + (queries.get(i).getEndTimestamp() - queries.get(i).getStartTimestamp()) / 2;
			boundaries.add(bound);
			upperHalf.add(new MetricQuery(queries.get(i).getScope(), queries.get(i).getMetric(), queries.get(i).getTags(), bound, queries.get(i).getEndTimestamp()));
			List<MetricQuery> tooHigh = new ArrayList<>();
			tooHigh.add(new MetricQuery(queries.get(i).getScope(), queries.get(i).getMetric(), queries.get(i).getTags(), queries.get(i).getEndTimestamp(), queries.get(i).getEndTimestamp()));
			
			when(serviceMock.getMetrics(upperHalf)).thenReturn(filterOver(metrics.get(i), bound, queries.get(i)));
			when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
		}
		
		List<MetricScanner> scanners = new ArrayList<>();
		for (int i = 0; i < queries.size(); i++) {
			scanners.add(new MetricScanner(metrics.get(i), queries.get(i), serviceMock, boundaries.get(i)));
		}
		
		ZeroIfMissingSum transform1 = new ZeroIfMissingSum();
		List<Metric> resultFromMetrics = transform1.transform(metrics);
		
		ZeroIfMissingSum transform2 = new ZeroIfMissingSum();
		List<Metric> resultFromScanners = transform2.transformScanner(scanners);
		
		assert(resultFromMetrics.equals(resultFromScanners));
	}
	
	@Test
	public void testDPIntegrityAndDisposal() {
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		
		for (int i = 0; i < metrics.size(); i++) {
			metrics.get(i).setNamespace(createRandomName());
			metrics.get(i).setDisplayName(createRandomName());
			metrics.get(i).setUnits("" + (double) i);
			//Long randTime = random.nextLong();
			Long dpStart = Collections.min(metrics.get(i).getDatapoints().keySet());
			Long dpEnd = Collections.max(metrics.get(i).getDatapoints().keySet());
			Long randTime = dpStart + (dpStart / dpEnd);	// still put within this range or will take forever and be an outlier
			assert(randTime != null);
			//System.out.println(randTime);
			Map<Long, Double> dps = new HashMap<>(metrics.get(i).getDatapoints());
			//System.out.println("old dps were " + dps.toString());
			dps.put(randTime, null);
			//System.out.println("Got here");
			metrics.get(i).setDatapoints(dps);
			//System.out.println(metrics.get(i).getDatapoints());
		}
		
		
		List<MetricQuery> queries = toQueries(metrics);
		List<Long> boundaries = new ArrayList<>();
		
		for (int i = 0; i < queries.size(); i++) {
			//System.out.println("Metric #" + i + " had dps " + metrics.get(i).getDatapoints().toString());
			List<MetricQuery> upperHalf = new ArrayList<>();
			Long bound = queries.get(i).getStartTimestamp() + (queries.get(i).getEndTimestamp() - queries.get(i).getStartTimestamp()) / 2;
			boundaries.add(bound);
			upperHalf.add(new MetricQuery(queries.get(i).getScope(), queries.get(i).getMetric(), queries.get(i).getTags(), bound, queries.get(i).getEndTimestamp()));
			List<MetricQuery> tooHigh = new ArrayList<>();
			tooHigh.add(new MetricQuery(queries.get(i).getScope(), queries.get(i).getMetric(), queries.get(i).getTags(), queries.get(i).getEndTimestamp(), queries.get(i).getEndTimestamp()));
			
			when(serviceMock.getMetrics(upperHalf)).thenReturn(filterOver(metrics.get(i), bound, queries.get(i)));
			when(serviceMock.getMetrics(tooHigh)).thenReturn(outOfBounds());
		}
		
		List<MetricScanner> scanners = new ArrayList<>();
		for (int i = 0; i < queries.size(); i++) {
			scanners.add(new MetricScanner(metrics.get(i), queries.get(i), serviceMock, boundaries.get(i)));
		}
		
		ZeroIfMissingSum transform1 = new ZeroIfMissingSum();
		List<Metric> resultFromMetrics = transform1.transform(metrics);
		
		ZeroIfMissingSum transform2 = new ZeroIfMissingSum();
		List<Metric> resultFromScanners = transform2.transformScanner(scanners);
		
		for (int i = 0; i < resultFromMetrics.size(); i++) {
			assert(resultFromMetrics.get(i).getDatapoints().equals(resultFromScanners.get(i).getDatapoints()));
		}
		for (int i = 0; i < metrics.size(); i++) {
			assert(!MetricScanner.existingScanner(metrics.get(i), queries.get(i)));
		}
	}
}

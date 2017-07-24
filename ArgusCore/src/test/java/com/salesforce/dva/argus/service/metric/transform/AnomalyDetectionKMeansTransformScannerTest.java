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
import com.salesforce.dva.argus.service.metric.transform.AnomalyDetectionKMeansTransform;
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
	
}


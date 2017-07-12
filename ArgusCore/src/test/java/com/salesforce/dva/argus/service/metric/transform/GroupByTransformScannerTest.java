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

public class GroupByTransformScannerTest extends AbstractTest {

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
	public void testGroupByTransformyDataClusterWithoutUncapturedGroupNoConstantsRemaining() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		
		Map<Long, Double> datapoints = new HashMap<Long, Double>();
        datapoints.put(1000L, 1.0);
		
		List<Metric> metrics = new ArrayList<>();
		Metric metric1 = new Metric("system.WAS.na1", "metric1");
        metric1.setDatapoints(datapoints);
        Metric metric2 = new Metric("system.WAS.na2", "metric1");
        metric2.setDatapoints(datapoints);
        Metric metric3 = new Metric("system.CHI.na1", "metric1");
        metric3.setDatapoints(datapoints);
        Metric metric4 = new Metric("system.CHI.na2", "metric1");
        metric4.setDatapoints(datapoints);
        metrics.add(metric1);
        metrics.add(metric2);
        metrics.add(metric3);
        metrics.add(metric4);
        
        
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
		
		GroupByTransform transform = new GroupByTransform(new TransformFactory(system.getServiceFactory().getTSDBService()));
		List<String> constants1 = new ArrayList<>();
		constants1.add("system\\.([A-Z]+)\\.na.");
		constants1.add("SUM");
		
		List<String> constants2 = new ArrayList<>();
		constants2.add("system\\.([A-Z]+)\\.na.");
		constants2.add("SUM");
		
		List<Metric> expected = transform.transform(metrics, constants1);
		List<Metric> actual = transform.transformScanner(scanners, constants2);
		
		assert(expected.equals(actual));
	}
	
	@Test
	public void testGroupByTransformyDataClusterWithUncapturedGroupNoConstantsRemaining() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		
		Map<Long, Double> datapoints = new HashMap<Long, Double>();
        datapoints.put(1000L, 1.0);
		
		List<Metric> metrics = new ArrayList<>();
		Metric metric1 = new Metric("system.WAS.na1", "metric1");
        metric1.setDatapoints(datapoints);
        Metric metric2 = new Metric("system.WAS.na2", "metric1");
        metric2.setDatapoints(datapoints);
        Metric metric3 = new Metric("bla1", "metric1");
        metric3.setDatapoints(datapoints);
        Metric metric4 = new Metric("bla2", "metric1");
        metric4.setDatapoints(datapoints);
        metrics.add(metric1);
        metrics.add(metric2);
        metrics.add(metric3);
        metrics.add(metric4);
        
        
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
		
		GroupByTransform transform = new GroupByTransform(new TransformFactory(system.getServiceFactory().getTSDBService()));
		List<String> constants1 = new ArrayList<>();
		constants1.add("system\\.([A-Z]+)\\.na.");
		constants1.add("SUM");
		
		List<String> constants2 = new ArrayList<>();
		constants2.add("system\\.([A-Z]+)\\.na.");
		constants2.add("SUM");
		
		List<Metric> expected = transform.transform(metrics, constants1);
		List<Metric> actual = transform.transformScanner(scanners, constants2);
		
		assert(expected.equals(actual));
	}
	
	@Test
	public void testGroupByTransformyWithoutUncapturedGroupWithConstantsRemaining() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		
        Map<Long, Double> datapoints = new HashMap<Long, Double>();
        datapoints.put(1000L, 1.0);
		
		List<Metric> metrics = new ArrayList<>();
		Metric metric1 = new Metric("system.CHI.SP1.na1", "latency");
		metric1.setTag("device", "na1-app1-1-chi.ops.sfdc.net");
        metric1.setDatapoints(datapoints);
        Metric metric11 = new Metric("system.CHI.SP1.na1", "latency");
		metric11.setTag("device", "na1-app1-2-chi.ops.sfdc.net");
        metric11.setDatapoints(datapoints);
        Metric metric2 = new Metric("system.CHI.SP1.na2", "latency");
		metric2.setTag("device", "na2-app1-1-chi.ops.sfdc.net");
        metric2.setDatapoints(datapoints);
        Metric metric21 = new Metric("system.CHI.SP1.na2", "latency");
		metric21.setTag("device", "na2-app1-2-chi.ops.sfdc.net");
        metric21.setDatapoints(datapoints);
        Metric metric3 = new Metric("system.CHI.SP1.na3", "latency");
		metric3.setTag("device", "na3-app1-1-chi.ops.sfdc.net");
        metric3.setDatapoints(datapoints);
        Metric metric31 = new Metric("system.CHI.SP1.na3", "latency");
		metric31.setTag("device", "na3-app1-2-chi.ops.sfdc.net");
        metric31.setDatapoints(datapoints);
        metrics.add(metric1);
        metrics.add(metric11);
        metrics.add(metric2);
        metrics.add(metric21);
        metrics.add(metric3);
        metrics.add(metric31);
        
        
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
		
		GroupByTransform transform = new GroupByTransform(new TransformFactory(system.getServiceFactory().getTSDBService()));
		List<String> constants1 = new ArrayList<>();
		constants1.add("(system\\.CHI\\.SP1\\..*:latency)");
		constants1.add("PERCENTILE");
		constants1.add("90");
		//constants1.add("2m");
		
		List<String> constants2 = new ArrayList<>();
		constants2.add("(system\\.CHI\\.SP1\\..*:latency)");
		constants2.add("PERCENTILE");
		constants2.add("90");
		//constants2.add("2m");
		
		List<Metric> expected = transform.transform(metrics, constants1);
		List<Metric> actual = transform.transformScanner(scanners, constants2);
		
		assert(expected.equals(actual));
	}
	
	@Test
	public void testDPIntegrityAndDisposal() {
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		
		Map<Long, Double> datapoints = new HashMap<Long, Double>();
        datapoints.put(1000L, 1.0);
		
		List<Metric> metrics = new ArrayList<>();
		Metric metric1 = new Metric("system.WAS.na1", "metric1");
        metric1.setDatapoints(datapoints);
        Metric metric2 = new Metric("system.WAS.na2", "metric1");
        metric2.setDatapoints(datapoints);
        Metric metric3 = new Metric("system.CHI.na1", "metric1");
        metric3.setDatapoints(datapoints);
        Metric metric4 = new Metric("system.CHI.na2", "metric1");
        metric4.setDatapoints(datapoints);
        metrics.add(metric1);
        metrics.add(metric2);
        metrics.add(metric3);
        metrics.add(metric4);
        
        
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
		
		GroupByTransform transform = new GroupByTransform(new TransformFactory(system.getServiceFactory().getTSDBService()));
		List<String> constants1 = new ArrayList<>();
		constants1.add("system\\.([A-Z]+)\\.na.");
		constants1.add("SUM");
		
		List<String> constants2 = new ArrayList<>();
		constants2.add("system\\.([A-Z]+)\\.na.");
		constants2.add("SUM");
		
		List<Metric> expected = transform.transform(metrics, constants1);
		List<Metric> actual = transform.transformScanner(scanners, constants2);
		
		for (int i = 0; i < expected.size(); i++) {
			assert(expected.get(i).getDatapoints().equals(actual.get(i).getDatapoints()));
		}
		for (int i = 0; i < metrics.size(); i++) {
			assert(!MetricScanner.existingScanner(metrics.get(i), queries.get(i)));
		}
	}
}


package com.salesforce.dva.argus.service.metric.transform;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
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
import com.salesforce.dva.argus.service.tsdb.MetricPagerValueMapping;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.service.tsdb.MetricScanner;

public class CullAboveValueMappingScannerTest extends AbstractTest {

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
	public void testCullAboveTransformPercentile() {
		
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
		
		CullAboveValueMapping map = new CullAboveValueMapping();
		List<String> constants = new ArrayList<>(2);
		constants.add(null);
		constants.add("percentile");
		
		for (int i = 0; i < metrics.size(); i++) {
			Double limit = random.nextDouble() * 100.0;
			constants.set(0, "" + limit);
			Map<Long, Double> expected = map.mapping(metrics.get(i).getDatapoints(), constants);
			Map<Long, Double> actual = map.mappingScanner(scanners.get(i), constants);
			
			assert(expected.equals(actual));
		}
	}

	@Test
	public void testCullAboveTransformValue() {
		
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
		
		CullAboveValueMapping map = new CullAboveValueMapping();
		List<String> constants = new ArrayList<>(2);
		constants.add(null);
		constants.add("value");
		
		for (int i = 0; i < metrics.size(); i++) {
			Double limit = Collections.min(metrics.get(i).getDatapoints().values()) + (Collections.max(metrics.get(i).getDatapoints().values()) - Collections.min(metrics.get(i).getDatapoints().values())) / 2;
			constants.set(0, "" + limit);
			
			Map<Long, Double> expected = map.mapping(metrics.get(i).getDatapoints(), constants);
			Map<Long, Double> actual = map.mappingScanner(scanners.get(i), constants);
			
			assert(expected.equals(actual));
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
		
		CullAboveValueMapping map = new CullAboveValueMapping();
		List<String> constants = new ArrayList<>(2);
		constants.add(null);
		constants.add("value");
		
		for (int i = 0; i < metrics.size(); i++) {
			Double limit = Collections.min(metrics.get(i).getDatapoints().values()) + (Collections.max(metrics.get(i).getDatapoints().values()) - Collections.min(metrics.get(i).getDatapoints().values())) / 2;
			constants.set(0, "" + limit);
			
			Map<Long, Double> expected = map.mapping(metrics.get(i).getDatapoints(), constants);
			Map<Long, Double> actual = map.mappingScanner(scanners.get(i), constants);
			
			assert(expected.equals(actual));
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
		List<Metric> metrics = createRandomMetrics(null, null, 5);

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
		
		ValueMapping mapping = new CullAboveValueMapping();
		List<String> constants = new ArrayList<>();
		constants.add("75.0");
		constants.add("value");
		
		for (int i = 0; i < scanners.size(); i++) {
			if (i % 2 == 0) {
				constants.set(1, "value");
			} else {
				constants.set(1, "percentile");
			}
			TreeMap<Long, Double> expected = new TreeMap<>(mapping.mapping(metrics.get(i).getDatapoints(), constants));
			Long chunkTime = metrics.get(i).getDatapoints().size() == 1 ? 0L :
				(Collections.max(metrics.get(i).getDatapoints().keySet()) - Collections.min(metrics.get(i).getDatapoints().keySet())) / 3;
			MetricPager stream = new MetricPagerValueMapping(Arrays.asList(scanners.get(i)), chunkTime, mapping, constants);
			int chunk = random.nextInt(stream.getNumberChunks());
			Long start = stream.getStartTime() + chunk * stream.getChunkTime();
			Long end = Math.min(stream.getEndTime(), start + stream.getChunkTime());
			
			Map<Long, Double> actual = new TreeMap<>();
			assert(expected.subMap(start, end + 1).equals(stream.getDPChunk(chunk)));			
			for (int j = 0; j < stream.getNumberChunks(); j++) {
				actual.putAll(stream.getDPChunk(j));
			}

			assert(expected.equals(actual));
		}
	}
}

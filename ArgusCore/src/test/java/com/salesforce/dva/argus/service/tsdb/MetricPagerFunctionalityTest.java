package com.salesforce.dva.argus.service.tsdb;

import org.junit.Test;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.metric.transform.AbsoluteValueMapping;
import com.salesforce.dva.argus.service.metric.transform.AnomalyDetectionKMeansTransform;
import com.salesforce.dva.argus.service.metric.transform.DivideValueReducerOrMapping;
import com.salesforce.dva.argus.service.metric.transform.DownsampleTransform;
import com.salesforce.dva.argus.service.metric.transform.IdentityTransform;
import com.salesforce.dva.argus.service.metric.transform.PropagateTransform;
import com.salesforce.dva.argus.service.metric.transform.ShiftValueMapping;
import com.salesforce.dva.argus.service.metric.transform.Transform;
import com.salesforce.dva.argus.service.metric.transform.TransformFactory;
import com.salesforce.dva.argus.service.metric.transform.ValueMapping;
import com.salesforce.dva.argus.service.metric.transform.ValueReducerOrMapping;
import com.salesforce.dva.argus.service.tsdb.MetricPager;
import com.salesforce.dva.argus.service.tsdb.MetricPagerTransform;
import com.salesforce.dva.argus.service.tsdb.MetricPagerValueMapping;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.service.tsdb.MetricScanner;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MetricPagerFunctionalityTest extends AbstractTest {
	
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
	
	private Metric filterUnder(Metric m, Long bound) {
		Metric miniMetric = new Metric(m);
		Map<Long, Double> dps = new HashMap<>();
		for (Long time : m.getDatapoints().keySet()) {
			if (time <= bound) {
				dps.put(time, m.getDatapoints().get(time));
			}
		}
		miniMetric.setDatapoints(dps);
		return miniMetric;
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
	public void testGetTimeRangeScannerWithAbsoluteValueMapping() {
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 3);
		Map<Long, Double> m = new HashMap<>();
		m.put(1000L, 10.0);
		metrics.get(0).setDatapoints(m);
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
				
		ValueMapping mapping = new AbsoluteValueMapping();
		
		for (int i = 0; i < scanners.size(); i++) {
			TreeMap<Long, Double> expected = new TreeMap<>(mapping.mapping(metrics.get(i).getDatapoints()));
			Long chunkTime = (queries.get(i).getEndTimestamp() - queries.get(i).getStartTimestamp()) / 3;
			MetricPager stream = new MetricPagerValueMapping(Arrays.asList(scanners.get(i)), chunkTime, mapping);
			Long start = stream.getStartTime() + stream.getChunkTime() / 2;
			Long end = start + 3 * stream.getChunkTime() / 2;
			
			assert(expected.subMap(start, end + 1).equals(stream.getDPWindowInputTimeRange(start, end)));			
		}
	}
	
	@Test
	public void testGetTimeRangeScannerWithIdentityTransform() {
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 3);
		List<MetricQuery> queries = toQueries(metrics);
		Long max = null;
		Long min = null;
		for (MetricQuery q : queries) {
			if (min == null || q.getStartTimestamp() < min) {
				min = q.getStartTimestamp();
			}
			if (max == null || q.getEndTimestamp() > max) {
				max = q.getEndTimestamp();
			}
		}
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
		
		Transform transform = new IdentityTransform();
		
		Long chunkTime = (max - min) / 3;
		List<Metric> expected = transform.transform(metrics);
		MetricPager stream = new MetricPagerTransform(scanners, chunkTime, transform);
		
		Long start = stream.getStartTime() + chunkTime / 2;
		Long end = stream.getStartTime() +  3 * chunkTime / 2;
		
		List<Metric> actual = stream.getMetricWindowInputTimeRange(start, end);
		
		for (Metric m : expected) {
			assert(actual.contains(m));
			assert(new TreeMap<>(m.getDatapoints()).subMap(start, end + 1).equals(actual.get(actual.indexOf(m)).getDatapoints()));
		}
	}
	
	@Test
	public void testGetTimeRangeScannerWithShiftValueMapping() {
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
				
		ValueMapping mapping = new ShiftValueMapping();
		List<String> constants = Arrays.asList("1s");
		
		for (int i = 0; i < scanners.size(); i++) {
			TreeMap<Long, Double> expected = new TreeMap<>(mapping.mapping(metrics.get(i).getDatapoints(), constants));
			Long chunkTime = (queries.get(i).getEndTimestamp() - queries.get(i).getStartTimestamp()) / 3;
			MetricPager stream = new MetricPagerValueMapping(Arrays.asList(scanners.get(i)), chunkTime, mapping, constants);
			Long start = stream.getStartTime() + stream.getChunkTime() / 2;
			Long end = start + 3 * stream.getChunkTime() / 2;

			assert(expected.subMap(Math.max(stream.getStartTime() + 1000, start), Math.max(stream.getStartTime() + 1001, end + 1)).equals(stream.getDPWindowOutputTimeRange(Math.max(start, stream.getStartTime() + 1000), Math.max(end, stream.getStartTime() + 1000))));
			assert(expected.subMap(start + 1000, end + 1001).equals(stream.getDPWindowInputTimeRange(start, end)));					
		}
	}
	
	@Test
	public void testGetTimeRangeScannerWithDownsampleTransform() {
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 3);
		List<MetricQuery> queries = toQueries(metrics);
		Long max = null;
		Long min = null;
		for (MetricQuery q : queries) {
			if (min == null || q.getStartTimestamp() < min) {
				min = q.getStartTimestamp();
			}
			if (max == null || q.getEndTimestamp() > max) {
				max = q.getEndTimestamp();
			}
		}
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
		
		Transform transform = new DownsampleTransform();
		List<String> constants = new ArrayList<>();
		constants.add("10m-p90");
		
		Long chunkTime = (max - min) / 3;
		List<Metric> expected = transform.transform(metrics, constants);
		MetricPager stream = new MetricPagerTransform(scanners, chunkTime, transform, constants);
		
		Long start = stream.getStartTime() + chunkTime / 2;
		Long end = stream.getStartTime() +  3 * chunkTime / 2;
		
		Long millis = (start % (10 * 60 * 1000));
		
		List<Metric> actualInp = stream.getMetricWindowInputTimeRange(start, end);
		List<Metric> actualOut = stream.getMetricWindowOutputTimeRange(Math.max(start, stream.getStartTime() - millis), Math.min(stream.getEndTime() - (stream.getEndTime() % 10 * 60 * 1000), end));
		
		for (Metric m : expected) {
			assert(actualInp.contains(m));
			
			assert(new TreeMap<>(m.getDatapoints()).subMap(Math.max(start, stream.getStartTime()), end).equals(actualOut.get(actualOut.indexOf(m)).getDatapoints()));
			assert(new TreeMap<>(m.getDatapoints()).subMap(start - millis, end + 1).equals(actualInp.get(actualInp.indexOf(m)).getDatapoints()));
		}
	}
	
	@Test
	public void testChainedShiftMapping() {
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
				
		ValueMapping mappingInner = new ShiftValueMapping();
		ValueMapping mappingOuter = new ShiftValueMapping();
		List<String> constantsInner = Arrays.asList("1s");
		List<String> constantsOuter = Arrays.asList("2s");
		
		for (int i = 0; i < scanners.size(); i++) {
			TreeMap<Long, Double> expected = new TreeMap<>(mappingOuter.mapping(mappingInner.mapping(metrics.get(i).getDatapoints(), constantsInner), constantsOuter));
			Long chunkTime = (queries.get(i).getEndTimestamp() - queries.get(i).getStartTimestamp()) / 3;
			MetricPager inner = new MetricPagerValueMapping(Arrays.asList(scanners.get(i)), chunkTime, mappingInner, constantsInner);
			MetricPager outer = new MetricPagerValueMapping(Arrays.asList(inner.getScanner()), chunkTime, mappingOuter, constantsOuter);
			Long start = outer.getStartTime() + outer.getChunkTime() / 2;
			Long end = start + 3 * outer.getChunkTime() / 2;
			
			assert(expected.subMap(start, end).equals(outer.getDPWindowOutputTimeRange(Math.max(start, outer.getStartTime() + 3000), Math.min(end, outer.getEndTime() + 3000))));
			assert(expected.subMap(start + 3000, end + 3001).equals(outer.getDPWindowInputTimeRange(start, end)));					
		}
	}
	
	@Test
	public void testMetricValueMappingTransformToPropagateChaining() {
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 5);
		List<MetricQuery> queries = toQueries(metrics);
		Long max = null;
		Long min = null;
		for (MetricQuery q : queries) {
			if (min == null || q.getStartTimestamp() < min) {
				min = q.getStartTimestamp();
			}
			if (max == null || q.getEndTimestamp() > max) {
				max = q.getEndTimestamp();
			}
		}
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
		
		Transform transformInner = new TransformFactory(serviceMock).getTransform("SHIFT");
		List<String> constantsInner = Arrays.asList("1s");
		Transform transformOuter = new PropagateTransform();
		List<String> constantsOuter = Arrays.asList("2s");
		
		Long chunkTime = (max - min) / 3;
		List<Metric> expected = transformOuter.transform(transformInner.transform(metrics, constantsInner), constantsOuter);
		MetricPager streamInner = new MetricPagerTransform(scanners, chunkTime, transformInner, constantsInner);
		MetricPager streamOuter = new MetricPagerTransform(streamInner.getScannerList(), chunkTime, transformOuter, constantsOuter);
		Long start = streamOuter.getStartTime() + chunkTime / 2;
		Long end = streamOuter.getStartTime() +  3 * chunkTime / 2;
				
		List<Metric> actualInp = streamOuter.getMetricWindowInputTimeRange(start, end);
		Long sKey = Math.max(start, streamOuter.getStartTime() + 1000);
		Long eKey = Math.max(end, streamOuter.getStartTime() + 1000);
		List<Metric> actualOut = streamOuter.getMetricWindowOutputTimeRange(sKey, eKey);
		
		for (Metric m : expected) {
			assert(actualInp.contains(m));
			assert(new TreeMap<>(m.getDatapoints()).subMap(sKey, eKey + 1).equals(actualOut.get(actualOut.indexOf(m)).getDatapoints()));
			assert(new TreeMap<>(m.getDatapoints()).subMap(start + 1000, end + 1001).equals(actualInp.get(actualInp.indexOf(m)).getDatapoints()));
		}
	}
	
	@Test
	public void testGetNextAndPrevPageFromCustomLocationMappingDP() {
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
				
		ValueReducerOrMapping redMap = new DivideValueReducerOrMapping();
		List<String> constants = Arrays.asList("2.0");
		
		for (int i = 0; i < scanners.size(); i++) {
			if (metrics.get(i).getDatapoints().size() == 1) {
				continue; // won't be able to get the next page in this case
			}
			TreeMap<Long, Double> expected = new TreeMap<>(redMap.mapping(metrics.get(i).getDatapoints(), constants));
			Long chunkTime = (queries.get(i).getEndTimestamp() - queries.get(i).getStartTimestamp()) / 3;
			MetricPager stream = new MetricPagerValueReducerOrMapping(Arrays.asList(scanners.get(i)), chunkTime, redMap, constants);
			Long start = stream.getStartTime() + stream.getChunkTime() / 2;
			Long end = start + 3 * stream.getChunkTime() / 2;
			
			stream.getDPWindowInputTimeRange(start, end);
			assert(expected.subMap(start + (end - start), Math.min(end + (end - start), stream.getEndTime()) + 1).equals(stream.getNextWindowOfDP()));
			assert(expected.subMap(end - stream.getCurrentViewingWindowLength(), end + 1).equals(stream.getPrevWindowOfDP()));
		}
	}
	
	@Test
	public void testGetNextAndPrevPageFromCustomLocationMappingMetric() {
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
		
		for (int i = 0; i < scanners.size(); i++) {
			if (metrics.get(i).getDatapoints().size() == 1) {
				continue; // won't be able to get the next page in this case
			}
			List<Metric> expected = transform.transform(Arrays.asList(metrics.get(i)), constants);
			Long chunkTime = (queries.get(i).getEndTimestamp() - queries.get(i).getStartTimestamp()) / 3;
			MetricPager stream = new MetricPagerTransform(Arrays.asList(scanners.get(i)), chunkTime, transform, constants);
			Long start = stream.getStartTime() + stream.getChunkTime() / 2;
			Long end = start + 3 * stream.getChunkTime() / 2;
			
			stream.getMetricWindowInputTimeRange(start, end);
			List<Metric> actualNext = stream.getNextWindowOfMetric();
			List<Metric> actualPrev = stream.getPrevWindowOfMetric();
			for (Metric m : expected) {
				assert(actualNext.contains(m));
				assert(actualPrev.contains(m));
				
				assert(new TreeMap<>(m.getDatapoints()).subMap(start + (end - start), Math.min(end + (end - start), stream.getEndTime()) + 1).equals(actualNext.get(actualNext.indexOf(m)).getDatapoints()));
				assert(new TreeMap<>(m.getDatapoints()).subMap(end - stream.getCurrentViewingWindowLength(), end + 1).equals(actualPrev.get(actualPrev.indexOf(m)).getDatapoints()));
			}
		}
	}
	
	@Test
	public void testCreateNewPageFromStartTimesDP() {
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
				
		ValueMapping mapping = new ShiftValueMapping();
		List<String> constants = Arrays.asList("3s");
		
		for (int i = 0; i < scanners.size(); i++) {
			TreeMap<Long, Double> expected = new TreeMap<>(mapping.mapping(metrics.get(i).getDatapoints(), constants));
			Long chunkTime = (queries.get(i).getEndTimestamp() - queries.get(i).getStartTimestamp()) / 3;
			MetricPager stream = new MetricPagerValueMapping(Arrays.asList(scanners.get(i)), chunkTime, mapping, constants);
			Long start = stream.getStartTime() + stream.getChunkTime() / 3;
			
			assert(expected.subMap(start, start + stream.getChunkTime() + 1).equals(stream.getNewDPPageFromStartOutput(Math.max(start, stream.getStartTime() + 3000))));
			assert(expected.subMap(start + 3000, start + stream.getChunkTime() + 3001).equals(stream.getNewDPPageFromStartInput(start)));					
		}
	}
	
	@Test
	public void testCreateNewPageFromStartTimesMetric() {
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 3);
		List<MetricQuery> queries = toQueries(metrics);
		Long max = null;
		Long min = null;
		for (MetricQuery q : queries) {
			if (min == null || q.getStartTimestamp() < min) {
				min = q.getStartTimestamp();
			}
			if (max == null || q.getEndTimestamp() > max) {
				max = q.getEndTimestamp();
			}
		}
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
		
		Transform transform = new DownsampleTransform();
		List<String> constants = new ArrayList<>();
		constants.add("10m-p90");
		
		Long chunkTime = (max - min) / 3;
		List<Metric> expected = transform.transform(metrics, constants);
		MetricPager stream = new MetricPagerTransform(scanners, chunkTime, transform, constants);
		
		Long start = stream.getStartTime() + chunkTime / 3;
		
		Long millis = (start % (10 * 60 * 1000));
		
		List<Metric> actualInp = stream.getNewMetricPageFromStartInput(start);
		List<Metric> actualOut = stream.getNewMetricPageFromStartOutput(Math.min(start, stream.getEndTime() - millis));
		
		for (Metric m : expected) {
			assert(actualInp.contains(m));
			assert(actualOut.contains(m));
			
			assert(new TreeMap<>(m.getDatapoints()).subMap(start, start + stream.getChunkTime()).equals(actualOut.get(actualOut.indexOf(m)).getDatapoints()));
			assert(new TreeMap<>(m.getDatapoints()).subMap(start - millis, start + stream.getChunkTime() + 1).equals(actualInp.get(actualInp.indexOf(m)).getDatapoints()));
		}
	}
}

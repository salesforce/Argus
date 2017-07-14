package com.salesforce.dva.argus.service.metric.transform;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.mockito.Matchers;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.service.tsdb.MetricScanner;

public class AbsoluteValueMappingScannerTest extends AbstractTest {
	
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

	@Test
	public void testAbsoluteValue() {
		
		MetricScanner.setChunkPercentage(0.50);
		
		TSDBService serviceMock = mock(TSDBService.class);
		List<Metric> metrics = createRandomMetrics(null, null, 1);
		
		for (int i = 0; i < metrics.size(); i++) {
			metrics.get(i).setNamespace(createRandomName());
			metrics.get(i).setDisplayName(createRandomName());
			metrics.get(i).setUnits("" + (double) i);
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
		
		AbsoluteValueMapping transform1 = new AbsoluteValueMapping();
		Map<Long, Double> newDataExpected = transform1.mapping(metrics.get(0).getDatapoints());
		Map<Long, Double> newDataActual = transform1.mappingScanner(scanners.get(0));
		
		assert(newDataExpected.equals(newDataActual));
		
	}
	
}

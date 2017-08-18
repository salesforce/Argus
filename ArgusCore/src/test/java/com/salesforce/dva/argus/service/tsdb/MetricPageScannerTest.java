package com.salesforce.dva.argus.service.tsdb;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Test;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.tsdb.MetricScanner;

public class MetricPageScannerTest extends AbstractTest {

	private final ScannerShiftFunction NOP = (Long t) -> t;
	
	private void testDPIntegrity(Map<Long, Double> dps, MetricScanner s) {
		Map<Long, Double> actual = new HashMap<>();
		while (s.hasNextDP()) {
			Map.Entry<Long, Double> dp = s.getNextDP();
			actual.put(dp.getKey(), dp.getValue());
		}
		s.dispose();
		assert(!s.hasNextDP());
		assert(dps.equals(actual));
	}
	
	private Metric filterUnder(Metric m, Double perc) {
		Map<Long, Double> dps = new HashMap<>();
		int i = 0;
		for (Long time : m.getDatapoints().keySet()) {
			if (i > m.getDatapoints().size() * perc) {
				break;
			}
			dps.put(time, m.getDatapoints().get(time));
			i++;
		}
		Metric m2 = new Metric(m);
		m2.setDatapoints(dps);
		return m2;
	}
	
	private Metric filterOver(Metric m, Double perc) {
		Map<Long, Double> dps = new HashMap<>();
		int i = 0;
		for (Long time : m.getDatapoints().keySet()) {
			if (i <= m.getDatapoints().size() * perc) {
				i++;
				continue;
			}
			dps.put(time, m.getDatapoints().get(time));
		}
		Metric m2 = new Metric(m);
		m2.setDatapoints(dps);
		return m2;
	}
	
	@Test
	public void testPushMetricAllAtOnce() {
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		
		for (Metric m : metrics) {
			MetricPageScanner s = new MetricPageScanner(m, NOP);
			s.setDonePushingData();
			testDPIntegrity(m.getDatapoints(), s);
		}
	}
	
	@Test
	public void testPushMetricHalfAndHalf() {
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		
		for (Metric m : metrics) {
			Metric low = filterUnder(m, .5);
			Metric high = filterOver(m, .5);
			MetricPageScanner s = new MetricPageScanner(filterUnder(m, .5), NOP);
			Map<Long, Double> actual = new TreeMap<>();
			for (int i = 0; i <= low.getDatapoints().size() / 2; i++) {
				Map.Entry<Long, Double> dp = s.getNextDP();
				actual.put(dp.getKey(), dp.getValue());
			}
			s.updateState(high);
			s.setDonePushingData();
			while (s.hasNextDP()) {
				Map.Entry<Long, Double> dp = s.getNextDP();
				actual.put(dp.getKey(), dp.getValue());
			}
			assert(m.getDatapoints().equals(actual));
			assert(s.getMetric().equals(m));
		}
	}
	
	@Test
	public void testPushMapAllAtOnce() {
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		
		for (Metric m : metrics) {
			MetricPageScanner s = new MetricPageDatapointScanner(m.getDatapoints(), NOP, Collections.min(m.getDatapoints().keySet()),
					Collections.max(m.getDatapoints().keySet()));
			s.setDonePushingData();
			testDPIntegrity(m.getDatapoints(), s);
		}
	}
	
	@Test
	public void testPushMapHalfAndHalf() {
		List<Metric> metrics = createRandomMetrics(null, null, 10);
		
		for (Metric m : metrics) {
			Map<Long, Double> actual = new HashMap<>();
			TreeMap<Long, Double> dps = new TreeMap<>(m.getDatapoints());
			Long bound = dps.firstKey() + (dps.lastKey() - dps.firstKey()) / 2;
			MetricPageScanner s = new MetricPageDatapointScanner(dps.subMap(dps.firstKey(), bound), NOP, Collections.min(m.getDatapoints().keySet()),
					Collections.max(m.getDatapoints().keySet()));
			for (int i = 0; i < dps.subMap(dps.firstKey(), bound).size(); i++) {
				Map.Entry<Long, Double> dp = s.getNextDP();
				actual.put(dp.getKey(), dp.getValue());
			}
			s.updateState(dps.subMap(bound, dps.lastKey() + 1));
			s.setDonePushingData();
			while (s.hasNextDP()) {
				Map.Entry<Long, Double> dp = s.getNextDP();
				actual.put(dp.getKey(), dp.getValue());
			}
			assert(actual.equals(m.getDatapoints()));
			s.dispose();
			assert(!s.hasNextDP());
		}
	}
}

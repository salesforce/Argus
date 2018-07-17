package com.salesforce.dva.argus.service.metric.transform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.List;

import org.junit.Test;

import com.salesforce.dva.argus.entity.Metric;

public class AboveValueFilterTest {

	@Test
	public void testAboveFilterNew() {
		Metric m = new Metric("test-scope", "test-metric");
		Map<Metric, String> map1 = new HashMap<>();
		map1.put(m, "2.0");
		
		Metric m2 = new Metric(m);
		map1.put(m2, "4");
		
		String lim1 = "3";
		String lim2 = "3.0";
		
		AboveValueFilter avf = new AboveValueFilter();
		List<Metric> res1 = avf.filter(map1, lim1);
		List<Metric> res2 = avf.filter(map1, lim2);
		
		assertTrue(res1.size() == 1);
		assertTrue(res2.size() == 1);
		assertEquals(res1, res2);
	}
}

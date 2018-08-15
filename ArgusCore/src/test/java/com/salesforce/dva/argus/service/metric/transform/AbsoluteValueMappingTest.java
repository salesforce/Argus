package com.salesforce.dva.argus.service.metric.transform;

import java.util.Map;

import static org.junit.Assert.assertTrue;

import java.util.HashMap;

import org.junit.Test;

public class AbsoluteValueMappingTest {

	@Test
	public void testMappingNew() {
		Map<Long, Number> datapoints = new HashMap<>();
		
		datapoints.put(1L, 1.0);
		datapoints.put(2L, -1);
		datapoints.put(3L, -Long.MAX_VALUE/2);
		
		AbsoluteValueMapping avm = new AbsoluteValueMapping();
		
		Map<Long, Number> res = avm.mapping(datapoints);
		
		assertTrue(res.get(1L).equals(1.0));
		assertTrue(res.get(2L).equals(1L));
		assertTrue(res.get(3L).equals(Long.MAX_VALUE / 2));
	}
}

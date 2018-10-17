package com.salesforce.dva.argus.service.metric.transform;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.salesforce.dva.argus.entity.Metric;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class GroupByTagTransformTest {
	
	@Test
	public void testGroupBySingleCommonTag() {
		
		GroupByTagTransform transform = new GroupByTagTransform(new TransformFactory(null));
		
		Map<Long, Double> datapoints = new HashMap<>();
        datapoints.put(1000L, 1.0);
		
		List<Metric> metrics = new ArrayList<>();
		
		Metric metric1 = new Metric("system.DCA.xy1", "metric1");
        metric1.setDatapoints(datapoints);
        metric1.setTags(new HashMap<String, String>(){{
			put("dc", "DCA");
            put("tag1", "value1");
            put("tag2", "value1");
        }});
        
        Metric metric2 = new Metric("system.DCA.xy2", "metric1");
        metric2.setDatapoints(datapoints);
		metric2.setTags(new HashMap<String, String>(){{
			put("dc", "DCA");
			put("tag1", "value2");
			put("tag2", "value2");
		}});

        Metric metric3 = new Metric("system.DCB.xy1", "metric1");
        metric3.setDatapoints(datapoints);
		metric3.setTags(new HashMap<String, String>(){{
			put("dc", "DCB");
			put("tag1", "value3");
			put("tag2", "value3");
		}});

        Metric metric4 = new Metric("system.DCB.xy2", "metric1");
        metric4.setDatapoints(datapoints);
		metric4.setTags(new HashMap<String, String>(){{
			put("dc", "DCB");
			put("tag1", "value4");
			put("tag2", "value4");
		}});

		metrics.add(metric1);
		metrics.add(metric2);
		metrics.add(metric3);
		metrics.add(metric4);
		
		List<String> constants = new ArrayList<>();
		constants.add("dc");
		constants.add("SUM");
		
		List<Metric> result = transform.transform(null, metrics, constants);
		assertTrue(result.size() == 2);
		for(Metric r : result) {
			assertEquals(new Double(2.0), r.getDatapoints().get(1000L));
			assertTrue(r.getScope().equals("DCA") || r.getScope().equals("DCB"));
			assertTrue(r.getMetric().equals("metric1"));
			assertTrue(r.getTag("dc") != null);
		}
	}
	
	@Test
	public void testGroupByTagTwoCommonTags() {

		Transform transform = new GroupByTagTransform(new TransformFactory(null));
		
		Map<Long, Double> datapoints = new HashMap<Long, Double>();
        	datapoints.put(1000L, 1.0);
		
		List<Metric> metrics = new ArrayList<>();

		Metric metric1 = new Metric("system.DCA.xy1", "metric1");
		metric1.setDatapoints(datapoints);
		metric1.setTags(new HashMap<String, String>(){{
			put("dc", "DCA");
			put("sp", "SP1");
			put("tag1", "value1");
			put("tag2", "value1");
		}});

		Metric metric2 = new Metric("system.DCA.xy2", "metric1");
		metric2.setDatapoints(datapoints);
		metric2.setTags(new HashMap<String, String>(){{
			put("dc", "DCA");
			put("sp", "SP2");
			put("tag1", "value2");
			put("tag2", "value2");
		}});

		Metric metric3 = new Metric("system.DCB.xy1", "metric1");
		metric3.setDatapoints(datapoints);
		metric3.setTags(new HashMap<String, String>(){{
			put("dc", "DCB");
			put("sp", "SP1");
			put("tag1", "value3");
			put("tag2", "value3");
		}});

		Metric metric4 = new Metric("system.DCB.xy2", "metric1");
		metric4.setDatapoints(datapoints);
		metric4.setTags(new HashMap<String, String>(){{
			put("dc", "DCB");
			put("sp", "SP1");
			put("tag1", "value4");
			put("tag2", "value4");
		}});
		
		metrics.add(metric1);
		metrics.add(metric2);
		metrics.add(metric3);
		metrics.add(metric4);
		
		List<String> constants = new ArrayList<>();
		constants.add("dc");
		constants.add("sp");
		constants.add("SUM");
		
		List<Metric> result = transform.transform(null, metrics, constants);
		assertEquals(3, result.size());
		for(Metric r : result) {
			assertTrue(
				r.getScope().equals("DCA,SP1") ||
					r.getScope().equals("DCA,SP2") ||
					r.getScope().equals("DCB,SP1"));
			assertEquals("metric1", r.getMetric());
			assertNotNull(r.getTag("dc"));
			assertNotNull(r.getTag("sp"));
		}
	}
	
	@Test
	public void testGroupByTagTwoTagsOnePartial() {
		
		Transform transform = new GroupByTagTransform(new TransformFactory(null));
		
		Map<Long, Double> datapoints = new HashMap<Long, Double>();
        	datapoints.put(1000L, 1.0);
		
		List<Metric> metrics = new ArrayList<>();

		Metric metric1 = new Metric("system.DCA.xy1", "metric1");
		metric1.setDatapoints(datapoints);
		metric1.setTags(new HashMap<String, String>(){{
			put("dc", "DCA");
			put("sp", "SP1");
			put("tag1", "value1");
			put("tag2", "value1");
		}});

		Metric metric2 = new Metric("system.DCA.xy2", "metric1");
		metric2.setDatapoints(datapoints);
		metric2.setTags(new HashMap<String, String>(){{
			put("dc", "DCA");
			put("sp", "SP2");
			put("tag1", "value2");
			put("tag2", "value2");
		}});

		Metric metric3 = new Metric("system.DCB.xy1", "metric1");
		metric3.setDatapoints(datapoints);
		metric3.setTags(new HashMap<String, String>(){{
			put("dc", "DCB");
			put("tag1", "value3");
			put("tag2", "value3");
		}});

		Metric metric4 = new Metric("system.DCB.xy2", "metric1");
		metric4.setDatapoints(datapoints);
		metric4.setTags(new HashMap<String, String>(){{
			put("dc", "DCB");
			put("tag1", "value4");
			put("tag2", "value4");
		}});
        
		metrics.add(metric1);
		metrics.add(metric2);
		metrics.add(metric3);
		metrics.add(metric4);

		List<String> constants = new ArrayList<>();
		constants.add("dc");
		constants.add("sp");
		constants.add("SUM");
		
		List<Metric> result = transform.transform(null, metrics, constants);
		assertEquals(3, result.size());
		for(Metric r : result) {
			assertTrue(
				r.getScope().equals("DCA,SP1") ||
					r.getScope().equals("DCA,SP2") ||
					r.getScope().equals("DCB"));
			assertEquals("metric1", r.getMetric());
			assertNotNull(r.getTag("dc"));
			if (r.getTag("dc").equals("DCA")) {
				assertNotNull(r.getTag("sp"));
			}
			else {
				assertNull(r.getTag("sp"));
			}
		}
	}

	@Test
	public void testGroupByTagOnePartial() {

		Transform transform = new GroupByTagTransform(new TransformFactory(null));

		Map<Long, Double> datapoints = new HashMap<Long, Double>();
		datapoints.put(1000L, 1.0);

		List<Metric> metrics = new ArrayList<>();

		Metric metric1 = new Metric("system.DCA.xy1", "metric1");
		metric1.setDatapoints(datapoints);
		metric1.setTags(new HashMap<String, String>(){{
			put("dc", "DCA");
			put("sp", "SP1");
			put("tag1", "value1");
			put("tag2", "value1");
		}});

		Metric metric2 = new Metric("system.DCA.xy2", "metric1");
		metric2.setDatapoints(datapoints);
		metric2.setTags(new HashMap<String, String>(){{
			put("dc", "DCA");
			put("sp", "SP2");
			put("tag1", "value2");
			put("tag2", "value2");
		}});

		Metric metric3 = new Metric("system.DCB.xy1", "metric1");
		metric3.setDatapoints(datapoints);
		metric3.setTags(new HashMap<String, String>(){{
			put("dc", "DCB");
			put("tag1", "value3");
			put("tag2", "value3");
		}});

		Metric metric4 = new Metric("system.DCB.xy2", "metric1");
		metric4.setDatapoints(datapoints);
		metric4.setTags(new HashMap<String, String>(){{
			put("dc", "DCB");
			put("tag1", "value4");
			put("tag2", "value4");
		}});

		metrics.add(metric1);
		metrics.add(metric2);
		metrics.add(metric3);
		metrics.add(metric4);

		List<String> constants = new ArrayList<>();
		constants.add("sp");
		constants.add("SUM");

		List<Metric> result = transform.transform(null, metrics, constants);
		assertEquals(3, result.size());
		for(Metric r : result) {
			assertTrue(
				r.getScope().equals("SP1") ||
					r.getScope().equals("SP2") ||
					r.getScope().equals("uncaptured-group"));
			assertEquals("metric1", r.getMetric());
			if (!r.getScope().equals("uncaptured-group")) {
				assertNotNull(r.getTag("sp"));
			}
			else {
				assertNull(r.getTag("sp"));
			}
		}
	}

	@Test
	public void testGroupByTagWithTransformConstant() {
		
		Transform transform = new GroupByTagTransform(new TransformFactory(null));
		
		Map<Long, Double> datapoints = new HashMap<Long, Double>();
        	datapoints.put(1000L, 1.0);
		
		List<Metric> metrics = new ArrayList<>();

		Metric metric1 = new Metric("system.DCA.xy1", "metric1");
		metric1.setDatapoints(datapoints);
		metric1.setTags(new HashMap<String, String>(){{
			put("dc", "DCA");
			put("sp", "SP1");
			put("tag1", "value1");
			put("tag2", "value1");
		}});

		Metric metric2 = new Metric("system.DCA.xy2", "metric1");
		metric2.setDatapoints(datapoints);
		metric2.setTags(new HashMap<String, String>(){{
			put("dc", "DCA");
			put("sp", "SP2");
			put("tag1", "value2");
			put("tag2", "value2");
		}});

		Metric metric3 = new Metric("system.DCB.xy1", "metric1");
		metric3.setDatapoints(datapoints);
		metric3.setTags(new HashMap<String, String>(){{
			put("dc", "DCB");
			put("sp", "SP2");
			put("tag1", "value3");
			put("tag2", "value3");
		}});

		Metric metric4 = new Metric("system.DCB.xy2", "metric1");
		metric4.setDatapoints(datapoints);
		metric4.addDatapoints(new HashMap<Long, Double>() {{
			put(2000L, 1.0);
		}});
		metric4.setTags(new HashMap<String, String>(){{
			put("dc", "DCB");
			put("sp", "SP2");
			put("tag1", "value4");
			put("tag2", "value4");
		}});
		
		List<String> constants = new ArrayList<>();
		constants.add("sp");
		constants.add("dc");
		constants.add("SUM");
		constants.add("union");

		metrics.add(metric1);
		metrics.add(metric2);
		metrics.add(metric3);
		metrics.add(metric4);

		List<Metric> result = transform.transform(null, metrics, constants);
		assertEquals(3, result.size());
		for(Metric r : result) {
			assertTrue(
				r.getScope().equals("SP1,DCA") ||
					r.getScope().equals("SP2,DCA") ||
					r.getScope().equals("SP2,DCB"));
			assertEquals("metric1", r.getMetric());
			assertNotNull(r.getTag("dc"));
			assertNotNull(r.getTag("sp"));

			if (r.getTag("dc").equals("DCA")) {
				assertEquals(new Double(1.0), r.getDatapoints().get(1000L));
			}
			else {
				assertEquals(new Double(2.0), r.getDatapoints().get(1000L));
				assertEquals(new Double(1.0), r.getDatapoints().get(2000L));
			}
		}
	}
	
	@Test
	public void testGroupByTagNoTags() {
		
		Transform transform = new GroupByTagTransform(new TransformFactory(null));
		
		Map<Long, Double> datapoints = new HashMap<Long, Double>();
        datapoints.put(1000L, 1.0);
		
		List<Metric> metrics = new ArrayList<>();
		
		Metric metric1 = new Metric("scope", "latency");
		metric1.setTag("device", "device1");
		metric1.setDatapoints(datapoints);
        
        Metric metric2 = new Metric("scope", "latency");
		metric2.setTag("device", "device2");
        metric2.setDatapoints(datapoints);


        metrics.add(metric1);
        metrics.add(metric2);

		List<String> constants = new ArrayList<>();
		constants.add("SUM");
		constants.add("union");

		try {
			List<Metric> result = transform.transform(null, metrics, constants);
			fail("Should fail because no tags is provided");
		}
		catch (UnsupportedOperationException ex) {
			assertTrue(ex.getMessage().contains("one tag to be provided"));
		}
	}

	@Test
	public void testGroupByTagNoFunction() {

		Transform transform = new GroupByTagTransform(new TransformFactory(null));

		Map<Long, Double> datapoints = new HashMap<Long, Double>();
		datapoints.put(1000L, 1.0);

		List<Metric> metrics = new ArrayList<>();

		Metric metric1 = new Metric("scope", "latency");
		metric1.setTag("device", "device1");
		metric1.setDatapoints(datapoints);

		Metric metric2 = new Metric("scope", "latency");
		metric2.setTag("device", "device2");
		metric2.setDatapoints(datapoints);


		metrics.add(metric1);
		metrics.add(metric2);

		List<String> constants = new ArrayList<>();
		constants.add("device");
		constants.add("union");

		try {
			List<Metric> result = transform.transform(null, metrics, constants);
			fail("Should fail because no function is provided");
		}
		catch (UnsupportedOperationException ex) {
			assertTrue(ex.getMessage().contains("function name to be provided"));
		}
	}

}

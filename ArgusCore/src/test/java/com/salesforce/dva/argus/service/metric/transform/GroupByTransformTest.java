package com.salesforce.dva.argus.service.metric.transform;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.salesforce.dva.argus.entity.Metric;

public class GroupByTransformTest {
	
	@Test
	public void testGroupByDC() {
		
		GroupByTransform transform = new GroupByTransform(new TransformFactory(null));
		
		Map<Long, Double> datapoints = new HashMap<Long, Double>();
        	datapoints.put(1000L, 1.0);
		
		List<Metric> metrics = new ArrayList<>();
		
		Metric metric1 = new Metric("system.DCA.xy1", "metric1");
        	metric1.setDatapoints(datapoints);
        
        	Metric metric2 = new Metric("system.DCA.xy2", "metric1");
        	metric2.setDatapoints(datapoints);
        
	        Metric metric3 = new Metric("system.DCB.xy1", "metric1");
        	metric3.setDatapoints(datapoints);
        
	        Metric metric4 = new Metric("system.DCB.xy2", "metric1");
        	metric4.setDatapoints(datapoints);
        
		metrics.add(metric1);
		metrics.add(metric2);
		metrics.add(metric3);
		metrics.add(metric4);
		
		List<String> constants = new ArrayList<>();
		constants.add("system\\.([A-Z]+)\\.xy.");
		constants.add("SUM");
		
		List<Metric> result = transform.transform(null, metrics, constants);
		assertTrue(result.size() == 2);
		for(Metric r : result) {
			assertEquals(new Double(2.0), r.getDatapoints().get(1000L));
		}
	}
	
	@Test
	public void testGroupByDCAndUncapturedGroup() {
		
		GroupByTransform transform = new GroupByTransform(new TransformFactory(null));
		
		Map<Long, Double> datapoints = new HashMap<Long, Double>();
        	datapoints.put(1000L, 1.0);
		
		List<Metric> metrics = new ArrayList<>();
		
		Metric metric1 = new Metric("system.DCA.xy1", "metric1");
		metric1.setDatapoints(datapoints);
		
		Metric metric2 = new Metric("system.DCA.xy2", "metric1");
		metric2.setDatapoints(datapoints);
		
		Metric metric3 = new Metric("bla1", "metric1");
		metric3.setDatapoints(datapoints);
		
		Metric metric4 = new Metric("bla2", "metric1");
		metric4.setDatapoints(datapoints);
		
		metrics.add(metric1);
		metrics.add(metric2);
		metrics.add(metric3);
		metrics.add(metric4);
		
		List<String> constants = new ArrayList<>();
		constants.add("system\\.([A-Z]+)\\.xy.");
		constants.add("SUM");
		
		List<Metric> result = transform.transform(null, metrics, constants);
		assertTrue(result.size() == 2);
		for(Metric r : result) {
			assertEquals(new Double(2.0), r.getDatapoints().get(1000L));
		}
	}
	
	@Test
	public void testGroupByDCAndPodPrefix() {
		
		GroupByTransform transform = new GroupByTransform(new TransformFactory(null));
		
		Map<Long, Double> datapoints = new HashMap<Long, Double>();
        	datapoints.put(1000L, 1.0);
		
		List<Metric> metrics = new ArrayList<>();
		
		Metric metric1 = new Metric("system.DCA.SP1.ab1", "metric1");
		metric1.setDatapoints(datapoints);
		
		Metric metric2 = new Metric("system.DCA.SP1.ab2", "metric1");
		metric2.setDatapoints(datapoints);
		
		Metric metric3 = new Metric("system.DCA.SP1.xy1", "metric1");
		metric3.setDatapoints(datapoints);
		
		Metric metric4 = new Metric("system.DCA.SP1.xy2", "metric1");
		metric4.setDatapoints(datapoints);
		
		Metric metric5 = new Metric("system.DCB.SP1.ab1", "metric1");
		metric5.setDatapoints(datapoints);
		
		Metric metric6 = new Metric("system.DCB.SP1.ab2", "metric1");
		metric6.setDatapoints(datapoints);
		
		Metric metric7 = new Metric("system.DCB.SP1.xy1", "metric1");
		metric7.setDatapoints(datapoints);
		
		Metric metric8 = new Metric("system.DCB.SP1.xy2", "metric1");
		metric8.setDatapoints(datapoints);
        
		metrics.add(metric1);
		metrics.add(metric2);
		metrics.add(metric3);
		metrics.add(metric4);
		metrics.add(metric5);
		metrics.add(metric6);
		metrics.add(metric7);
		metrics.add(metric8);
		
		List<String> constants = new ArrayList<>();
		constants.add("system\\.([A-Z]+)\\.SP.\\.([a-z]+).");
		constants.add("DIVIDE");
		
		List<Metric> result = transform.transform(null, metrics, constants);
		assertTrue(result.size() == 4);
		for(Metric r : result) {
			assertEquals(new Double(1.0), r.getDatapoints().get(1000L));
		}
	}
	
	@Test
	public void testGroupByDCAndPodNumber() {
		
		GroupByTransform transform = new GroupByTransform(new TransformFactory(null));
		
		Map<Long, Double> datapoints = new HashMap<Long, Double>();
        	datapoints.put(1000L, 1.0);
		
		List<Metric> metrics = new ArrayList<>();
		
		Metric metric1 = new Metric("system.DCA.SP1.ab1", "metric1");
		metric1.setDatapoints(datapoints);
		
		Metric metric2 = new Metric("system.DCA.SP1.ab2", "metric1");
		metric2.setDatapoints(datapoints);
		
		Metric metric3 = new Metric("system.DCA.SP1.xy1", "metric2");
		metric3.setDatapoints(datapoints);
		
		Metric metric4 = new Metric("system.DCA.SP1.xy2", "metric2");
		metric4.setDatapoints(datapoints);
		
		Metric metric5 = new Metric("system.DCB.SP1.ab1", "metric1");
		metric5.setDatapoints(datapoints);
		
		Metric metric6 = new Metric("system.DCB.SP1.ab2", "metric1");
		metric6.setDatapoints(datapoints);
		
		Metric metric7 = new Metric("system.DCB.SP1.xy1", "metric2");
		metric7.setDatapoints(datapoints);
		
		Metric metric8 = new Metric("system.DCB.SP1.xy2", "metric2");
		metric8.setDatapoints(datapoints);
		
		metrics.add(metric1);
		metrics.add(metric2);
		metrics.add(metric3);
		metrics.add(metric4);
		metrics.add(metric5);
		metrics.add(metric6);
		metrics.add(metric7);
		metrics.add(metric8);
		
		List<String> constants = new ArrayList<>();
		constants.add("system\\.([A-Z]+)\\.SP.\\.[a-z][a-z]([0-9])");
		constants.add("SUM");
		
		List<Metric> result = transform.transform(null, metrics, constants);
		assertTrue(result.size() == 4);
		
	}
	
	@Test
	public void testWeightedAvgUsingGroupBy() {
		
		GroupByTransform transform = new GroupByTransform(new TransformFactory(null));
		
		Map<Long, Double> datapoints = new HashMap<Long, Double>();
        	datapoints.put(1000L, 1.0);
		
		List<Metric> metrics = new ArrayList<>();
		
		Metric metric1 = new Metric("scope", "latency");
		metric1.setTag("device", "device1");
        	metric1.setDatapoints(datapoints);
        
        	Metric metric2 = new Metric("scope", "latency");
		metric2.setTag("device", "device2");
        	metric2.setDatapoints(datapoints);
        
        	Metric metric3 = new Metric("scope", "latency");
		metric3.setTag("device", "device3");
        	metric3.setDatapoints(datapoints);
        
	        Metric metric4 = new Metric("scope", "latency");
		metric4.setTag("device", "device4");
        	metric4.setDatapoints(datapoints);
        
        
        	Metric metric5 = new Metric("scope", "count");
		metric5.setTag("device", "device1");
        	metric5.setDatapoints(datapoints);
        
        	Metric metric6 = new Metric("scope", "count");
		metric6.setTag("device", "device2");
        	metric6.setDatapoints(datapoints);
        
        	Metric metric7 = new Metric("scope", "count");
		metric7.setTag("device", "device3");
        	metric7.setDatapoints(datapoints);
        
        	Metric metric8 = new Metric("scope", "count");
		metric8.setTag("device", "device4");
        	metric8.setDatapoints(datapoints);
        
        	metrics.add(metric1);
        	metrics.add(metric2);
        	metrics.add(metric3);
        	metrics.add(metric4);
        	metrics.add(metric5);
        	metrics.add(metric6);
        	metrics.add(metric7);
        	metrics.add(metric8);
		
		List<String> constants = new ArrayList<>();
		constants.add("(device.*)");
		constants.add("SCALE");
		
		List<Metric> result = transform.transform(null, metrics, constants);
		assertTrue(result.size() == 4);
	}
	
	@Test
	public void testGroupByPod() {
		
		GroupByTransform transform = new GroupByTransform(new TransformFactory(null));
		
		Map<Long, Double> datapoints = new HashMap<Long, Double>();
        	datapoints.put(1000L, 1.0);
		
		List<Metric> metrics = new ArrayList<>();
		
		Metric metric1 = new Metric("system.pod1", "latency");
		metric1.setTag("device", "pod1-device1");
	        metric1.setDatapoints(datapoints);
	        
	        Metric metric11 = new Metric("system.pod1", "latency");
		metric11.setTag("device", "pod1-device2");
	        metric11.setDatapoints(datapoints);
        
        
	        Metric metric2 = new Metric("system.pod2", "latency");
		metric2.setTag("device", "pod2-device1");
	        metric2.setDatapoints(datapoints);
        
	        Metric metric21 = new Metric("system.pod2", "latency");
		metric21.setTag("device", "pod2-device2");
	        metric21.setDatapoints(datapoints);
        
	        Metric metric3 = new Metric("system.pod3", "latency");
		metric3.setTag("device", "pod3-device1");
	        metric3.setDatapoints(datapoints);
        
	        Metric metric31 = new Metric("system.pod3", "latency");
		metric31.setTag("device", "pod3-device2");
	        metric31.setDatapoints(datapoints);
        
	        metrics.add(metric1);
	        metrics.add(metric11);
	        metrics.add(metric2);
	        metrics.add(metric21);
	        metrics.add(metric3);
	        metrics.add(metric31);
        
		
		List<String> constants = new ArrayList<>();
		constants.add("(system\\.pod[0-9]:latency)");
		constants.add("SUM");
		
		List<Metric> result = transform.transform(null, metrics, constants);
		assertTrue(result.size() == 3);
		for(Metric r : result) {
			assertEquals(new Double(2.0), r.getDatapoints().get(1000L));
		}
	}
	
	@Test
	public void testGroupByWithFunctionTakingConstants() {
		
		GroupByTransform transform = new GroupByTransform(new TransformFactory(null));
		
		Map<Long, Double> datapoints = new HashMap<Long, Double>();
        	datapoints.put(1000L, 1.0);
		
		List<Metric> metrics = new ArrayList<>();
		
		Metric metric1 = new Metric("system.pod1", "latency");
		metric1.setTag("device", "pod1-device1");
	        metric1.setDatapoints(datapoints);
	        
	        Metric metric11 = new Metric("system.pod1", "latency");
		metric11.setTag("device", "pod1-device2");
	        metric11.setDatapoints(datapoints);
        
        
	        Metric metric2 = new Metric("system.pod2", "latency");
		metric2.setTag("device", "pod2-device1");
	        metric2.setDatapoints(datapoints);
        
	        Metric metric21 = new Metric("system.pod2", "latency");
		metric21.setTag("device", "pod2-device2");
	        metric21.setDatapoints(datapoints);
        
	        Metric metric3 = new Metric("system.pod3", "latency");
		metric3.setTag("device", "pod3-device1");
	        metric3.setDatapoints(datapoints);
        
	        Metric metric31 = new Metric("system.pod3", "latency");
		metric31.setTag("device", "pod3-device2");
	        metric31.setDatapoints(datapoints);
        
	        metrics.add(metric1);
	        metrics.add(metric11);
	        metrics.add(metric2);
	        metrics.add(metric21);
	        metrics.add(metric3);
	        metrics.add(metric31);
        
		
		List<String> constants = new ArrayList<>();
		constants.add("(system\\.pod[0-9]:latency)");
		constants.add("PERCENTILE");
		constants.add("90");
		
		List<Metric> result = transform.transform(null, metrics, constants);
		assertTrue(result.size() == 3);
		for(Metric r : result) {
			assertEquals(new Double(1.0), r.getDatapoints().get(1000L));
		}
	}

}

package com.salesforce.dva.argus.service.metric.transform;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Metric;

public class GroupByTransformTest extends AbstractTest {
	
	@Test
	public void testGroupByDC() {
		
		GroupByTransform transform = new GroupByTransform(new TransformFactory(system.getServiceFactory().getTSDBService()));
		
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
		
		List<String> constants = new ArrayList<>();
		constants.add("system\\.([A-Z]+)\\.na.");
		constants.add("SUM");
		
		List<Metric> result = transform.transform(metrics, constants);
		assertTrue(result.size() == 2);
		for(Metric r : result) {
			assertEquals(new Double(2.0), r.getDatapoints().get(1000L));
		}
	}
	
	@Test
	public void testGroupByDCAndUncapturedGroup() {
		
		GroupByTransform transform = new GroupByTransform(new TransformFactory(system.getServiceFactory().getTSDBService()));
		
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
		
		List<String> constants = new ArrayList<>();
		constants.add("system\\.([A-Z]+)\\.na.");
		constants.add("SUM");
		
		List<Metric> result = transform.transform(metrics, constants);
		assertTrue(result.size() == 2);
		for(Metric r : result) {
			assertEquals(new Double(2.0), r.getDatapoints().get(1000L));
		}
	}
	
	@Test
	public void testGroupByDCAndPodPrefix() {
		
		GroupByTransform transform = new GroupByTransform(new TransformFactory(system.getServiceFactory().getTSDBService()));
		
		Map<Long, Double> datapoints = new HashMap<Long, Double>();
        datapoints.put(1000L, 1.0);
		
		List<Metric> metrics = new ArrayList<>();
		
		Metric metric1 = new Metric("system.WAS.SP1.na1", "metric1");
        metric1.setDatapoints(datapoints);
        
        Metric metric2 = new Metric("system.WAS.SP1.na2", "metric1");
        metric2.setDatapoints(datapoints);
        
        Metric metric3 = new Metric("system.WAS.SP1.cs1", "metric1");
        metric3.setDatapoints(datapoints);
        
        Metric metric4 = new Metric("system.WAS.SP1.cs2", "metric1");
        metric4.setDatapoints(datapoints);
        
        Metric metric5 = new Metric("system.CHI.SP1.na1", "metric1");
        metric5.setDatapoints(datapoints);
        
        Metric metric6 = new Metric("system.CHI.SP1.na2", "metric1");
        metric6.setDatapoints(datapoints);
        
        Metric metric7 = new Metric("system.CHI.SP1.cs1", "metric1");
        metric7.setDatapoints(datapoints);
        
        Metric metric8 = new Metric("system.CHI.SP1.cs2", "metric1");
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
		
		List<Metric> result = transform.transform(metrics, constants);
		assertTrue(result.size() == 4);
		for(Metric r : result) {
			assertEquals(new Double(1.0), r.getDatapoints().get(1000L));
		}
	}
	
	@Test
	public void testGroupByDCAndPodNumber() {
		
		GroupByTransform transform = new GroupByTransform(new TransformFactory(system.getServiceFactory().getTSDBService()));
		
		Map<Long, Double> datapoints = new HashMap<Long, Double>();
        datapoints.put(1000L, 1.0);
		
		List<Metric> metrics = new ArrayList<>();
		
		Metric metric1 = new Metric("system.WAS.SP1.na1", "metric1");
        metric1.setDatapoints(datapoints);
        
        Metric metric2 = new Metric("system.WAS.SP1.na2", "metric1");
        metric2.setDatapoints(datapoints);
        
        Metric metric3 = new Metric("system.WAS.SP1.cs1", "metric2");
        metric3.setDatapoints(datapoints);
        
        Metric metric4 = new Metric("system.WAS.SP1.cs2", "metric2");
        metric4.setDatapoints(datapoints);
        
        Metric metric5 = new Metric("system.CHI.SP1.na1", "metric1");
        metric5.setDatapoints(datapoints);
        
        Metric metric6 = new Metric("system.CHI.SP1.na2", "metric1");
        metric6.setDatapoints(datapoints);
        
        Metric metric7 = new Metric("system.CHI.SP1.cs1", "metric2");
        metric7.setDatapoints(datapoints);
        
        Metric metric8 = new Metric("system.CHI.SP1.cs2", "metric2");
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
		
		List<Metric> result = transform.transform(metrics, constants);
		assertTrue(result.size() == 4);
		
	}
	
	@Test
	public void testWeightedAvgUsingGroupBy() {
		
		GroupByTransform transform = new GroupByTransform(new TransformFactory(system.getServiceFactory().getTSDBService()));
		
		Map<Long, Double> datapoints = new HashMap<Long, Double>();
        datapoints.put(1000L, 1.0);
		
		List<Metric> metrics = new ArrayList<>();
		
		Metric metric1 = new Metric("system.CHI.SP1.na10", "latency");
		metric1.setTag("device", "na10-app1-1-chi.ops.sfdc.net");
        metric1.setDatapoints(datapoints);
        
        Metric metric2 = new Metric("system.CHI.SP1.na10", "latency");
		metric2.setTag("device", "na10-app1-2-chi.ops.sfdc.net");
        metric2.setDatapoints(datapoints);
        
        Metric metric3 = new Metric("system.CHI.SP1.na10", "latency");
		metric3.setTag("device", "na10-app2-1-chi.ops.sfdc.net");
        metric3.setDatapoints(datapoints);
        
        Metric metric4 = new Metric("system.CHI.SP1.na10", "latency");
		metric4.setTag("device", "na10-app2-2-chi.ops.sfdc.net");
        metric4.setDatapoints(datapoints);
        
        
        Metric metric5 = new Metric("system.CHI.SP1.na10", "count");
		metric5.setTag("device", "na10-app1-1-chi.ops.sfdc.net");
        metric5.setDatapoints(datapoints);
        
        Metric metric6 = new Metric("system.CHI.SP1.na10", "count");
		metric6.setTag("device", "na10-app1-2-chi.ops.sfdc.net");
        metric6.setDatapoints(datapoints);
        
        Metric metric7 = new Metric("system.CHI.SP1.na10", "count");
		metric7.setTag("device", "na10-app2-1-chi.ops.sfdc.net");
        metric7.setDatapoints(datapoints);
        
        Metric metric8 = new Metric("system.CHI.SP1.na10", "count");
		metric8.setTag("device", "na10-app2-2-chi.ops.sfdc.net");
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
		constants.add("(app.*-chi)");
		constants.add("SCALE");
		
		List<Metric> result = transform.transform(metrics, constants);
		assertTrue(result.size() == 4);
	}
	
	@Test
	public void testGroupByPod() {
		
		GroupByTransform transform = new GroupByTransform(new TransformFactory(system.getServiceFactory().getTSDBService()));
		
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
        
		
		List<String> constants = new ArrayList<>();
		constants.add("(system\\.CHI\\.SP1\\..*:latency)");
		constants.add("SUM");
		
		List<Metric> result = transform.transform(metrics, constants);
		assertTrue(result.size() == 3);
		for(Metric r : result) {
			assertEquals(new Double(2.0), r.getDatapoints().get(1000L));
		}
	}
	
	@Test
	public void testGroupByWithFunctionTakingConstants() {
		
		GroupByTransform transform = new GroupByTransform(new TransformFactory(system.getServiceFactory().getTSDBService()));
		
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
        
		
		List<String> constants = new ArrayList<>();
		constants.add("(system\\.CHI\\.SP1\\..*:latency)");
		constants.add("PERCENTILE");
		constants.add("90");
		
		List<Metric> result = transform.transform(metrics, constants);
		assertTrue(result.size() == 3);
		for(Metric r : result) {
			assertEquals(new Double(1.0), r.getDatapoints().get(1000L));
		}
	}

}

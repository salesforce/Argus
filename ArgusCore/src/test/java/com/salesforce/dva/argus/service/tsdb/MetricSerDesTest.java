package com.salesforce.dva.argus.service.tsdb;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.salesforce.dva.argus.entity.Metric;

public class MetricSerDesTest {
	
	private ObjectMapper _mapper;
	
	@Before
	public void initialize() {
		_mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        module.addSerializer(Metric.class, new MetricTransform.Serializer());
        module.addDeserializer(ResultSet.class, new MetricTransform.MetricListDeserializer());
        _mapper.registerModule(module);
	}
	
	@Test
	public void testMetricDeserializationList() {
        
        String content = "[{\"metric\":\"mem.heap.used-__-argus.jvm\",\"tags\":{\"host\":\"host1\",\"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOiJieXRlcyJ9\"},\"aggregateTags\":[],\"tsuids\":[\"00000000000E000000000001000000000003000000000002000000000002\"],\"dps\":{\"1477386300\":4.940423168E9}},{\"metric\":\"mem.heap.used-__-argus.jvm\",\"tags\":{\"host\":\"host2\",\"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOiJieXRlcyJ9\"},\"aggregateTags\":[],\"tsuids\":[\"00000000000E00000000000100000000000300000000000200000000000A\"],\"dps\":{\"1477386300\":4.940423168E9}}]";
        
        try {
			ResultSet set = _mapper.readValue(content, new TypeReference<ResultSet>() { });
			List<Metric> metrics = set.getMetrics();
			
			assertTrue(metrics.size() == 2);
		} catch (IOException e) {
			fail("Failed to deserialize metrics. Reason: " + e.getMessage());
		}
	}
	
	@Test
	public void testMetricDeserializationEmptyList() {
		
        String content = "[]";
        try {
			ResultSet set = _mapper.readValue(content, new TypeReference<ResultSet>() { });
			List<Metric> metrics = set.getMetrics();
			assertTrue("Metrics list must be empty.", metrics.isEmpty());
		} catch (IOException e) {
			fail("Failed to deserialize metrics. Reason: " + e.getMessage());
		}
	}
	
	@Test
	public void testMetricDeserializationListWithEmptyDatapointSet() {
        
        String content = "[{\"metric\":\"mem.heap.used-__-argus.jvm\",\"tags\":{\"host\":\"host1\",\"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOiJieXRlcyJ9\"},\"aggregateTags\":[],\"tsuids\":[\"00000000000E000000000001000000000003000000000002000000000002\"],\"dps\":{}},{\"metric\":\"mem.heap.used-__-argus.jvm\",\"tags\":{\"host\":\"host2\",\"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOiJieXRlcyJ9\"},\"aggregateTags\":[],\"tsuids\":[\"00000000000E00000000000100000000000300000000000200000000000A\"],\"dps\":{\"1477386300\":4.940423168E9}}]";
        
        try {
			ResultSet set = _mapper.readValue(content, new TypeReference<ResultSet>() { });
			List<Metric> metrics = set.getMetrics();
			
			assertTrue(metrics.size() == 1);
		} catch (IOException e) {
			fail("Failed to deserialize metrics. Reason: " + e.getMessage());
		}
	}

}

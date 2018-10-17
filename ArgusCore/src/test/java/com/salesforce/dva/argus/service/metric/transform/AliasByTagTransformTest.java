package com.salesforce.dva.argus.service.metric.transform;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;

import org.junit.Test;

import com.salesforce.dva.argus.entity.Metric;

public class AliasByTagTransformTest {
	
	private Transform aliasByTagTransform = new AliasByTagTransform();
	
	@Test
	public void testAliasByTagTransformWithoutConstants() {
		
		Metric metric = new Metric("scope", "metric");
		metric.setTag("device", "someDevice");
		metric.setTag("target", "someTarget");
		
		Metric actual = aliasByTagTransform.transform(null, Arrays.asList(metric)).get(0);
		assertEquals("someDevice" + AliasByTagTransform.DELIMITER + "someTarget", actual.getDisplayName());
	}
	
	@Test
	public void testAliasByTagTransformSingleTagKey() {
		
		Metric metric = new Metric("scope", "metric");
		metric.setTag("device", "someDevice");
		metric.setTag("target", "someTarget");
		
		Metric actual = aliasByTagTransform.transform(null, Arrays.asList(metric), Arrays.asList("device")).get(0);
		assertEquals("someDevice", actual.getDisplayName());
	}
	
	@Test
	public void testAliasByTagTransformMultipleTagKeys() {
		
		Metric metric = new Metric("scope", "metric");
		metric.setTag("device", "someDevice");
		metric.setTag("target", "someTarget");
		
		Metric actual = aliasByTagTransform.transform(null, Arrays.asList(metric), Arrays.asList("device", "target")).get(0);
		assertEquals("someDevice" + AliasByTagTransform.DELIMITER + "someTarget", actual.getDisplayName());
	}
	
	@Test
	public void testAliasByTagTransformInvalidTagKey() {
		Metric metric = new Metric("scope", "metric");
		metric.setTag("device", "someDevice");
		metric.setTag("target", "someTarget");
		
		Metric actual = aliasByTagTransform.transform(null, Arrays.asList(metric), Arrays.asList("devicessss")).get(0);
		assertEquals(null, actual.getDisplayName());
	}
	
	@Test
	public void testAliasByTagTransformOneValidOneInvalidTagKey() {
		Metric metric = new Metric("scope", "metric");
		metric.setTag("device", "someDevice");
		metric.setTag("target", "someTarget");
		
		Metric actual = aliasByTagTransform.transform(null, Arrays.asList(metric), Arrays.asList("devicessss", "target")).get(0);
		assertEquals("someTarget", actual.getDisplayName());
	}
	
	@Test 
	public void testAliasByTagTransformEmptyTagKey() {
		Metric metric = new Metric("scope", "metric");
		metric.setTag("device", "someDevice");
		metric.setTag("target", "someTarget");
		
		Metric actual = aliasByTagTransform.transform(null, Arrays.asList(metric), Arrays.asList("")).get(0);
		assertEquals(null, actual.getDisplayName());
	}

}

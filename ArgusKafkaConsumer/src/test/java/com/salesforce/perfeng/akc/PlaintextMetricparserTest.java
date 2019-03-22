package com.salesforce.perfeng.akc;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.salesforce.mandm.ajna.Metric;
import com.salesforce.perfeng.akc.consumer.PlaintextMetricParser;


public class PlaintextMetricparserTest {
	@Test
	public void testPlainTextParser() {
		PlaintextMetricParser parser = new PlaintextMetricParser();
		String data="core.ASG.SP1.ap0.ap0-ffx5-14-asg-ops-sfdc-net.SFDC_type-Stats-name1-System-name2-AveGCUsage.FifteenMinuteAverage 0.4170034302120294 1472683430";
		Metric actual=parser.parse(data.getBytes());
		Map<CharSequence, CharSequence> tags = new HashMap<>();
		tags.put("datacenter", "ASG");
		tags.put("superpod", "SP1");
		tags.put("pod", "ap0");
		tags.put("device", "ap0-ffx5-14-asg.ops.sfdc.net");
		List<Object> metricNames= new ArrayList<>();
		metricNames.add("SFDC_type-Stats-name1-System-name2-AveGCUsage");
		metricNames.add("FifteenMinuteAverage");
		Metric expected = new Metric("uli_core", null,0, tags,metricNames, 0.4170034302120294, 1472683430L, null);
		assertEquals(expected.getService(), actual.getService());
		assertEquals(expected.getTags(), actual.getTags());
		assertEquals(expected.getMetricName(), actual.getMetricName());
		assertEquals(expected.getMetricValue(), actual.getMetricValue());
		assertEquals(expected.getTimestamp(), actual.getTimestamp());
	}
	
	@Test
	public void testPlainTextParserWithExponentialValues() {
		PlaintextMetricParser parser = new PlaintextMetricParser();
		String data="core.CHI.SP1.na9.na9-ffx1-10-chi-ops-sfdc-net.java-lang_type-GarbageCollector-name-PS_Scavenge 1.190454441e90 1472082139";
		Metric actual=parser.parse(data.getBytes());
		Map<CharSequence, CharSequence> tags = new HashMap<>();
		tags.put("datacenter", "CHI");
		tags.put("superpod", "SP1");
		tags.put("pod", "na9");
		tags.put("device", "na9-ffx1-10-chi.ops.sfdc.net");
		List<Object> metricNames= new ArrayList<>();
		metricNames.add("java-lang_type-GarbageCollector-name-PS_Scavenge");
		Metric expected = new Metric("uli_core", null,0, tags,metricNames, 1.190454441e90, 1472082139L,null);
		assertEquals(expected.getService(), actual.getService());
		assertEquals(expected.getTags(), actual.getTags());
		assertEquals(expected.getMetricName(), actual.getMetricName());
		assertEquals(expected.getMetricValue(), actual.getMetricValue());
		assertEquals(expected.getTimestamp(), actual.getTimestamp());
	}

	@Test
	public void testReplaceDeviceNameWithDot(){
		PlaintextMetricParser parser = new PlaintextMetricParser();
		String actual=parser.replaceDeviceNameWithDot("na9-ffx1-10-chi-ops-sfdc-net");
		String expected="na9-ffx1-10-chi.ops.sfdc.net";
		assertEquals(expected, actual); 
	}
	
	@Test
	public void testReplaceDeviceNameWithDotExpectedNull(){
		PlaintextMetricParser parser = new PlaintextMetricParser();
		String actual=parser.replaceDeviceNameWithDot(null);
		assertNull(actual); 
	}
	@Test
	public void testReplaceCharFromEndExpectedNoChanges(){
		PlaintextMetricParser parser = new PlaintextMetricParser();
		String actual=parser.replaceDeviceNameWithDot("na9-ffx1-10-chi.ops.sfdc.net");
		String expected="na9-ffx1-10-chi.ops.sfdc.net";
		assertEquals(expected, actual); 
	}

}

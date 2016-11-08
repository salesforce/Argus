/*
 * Copyright (c) 2016, Salesforce.com, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
package com.salesforce.dva.argus.service.metric.transform;

import static org.junit.Assert.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Test;

import com.salesforce.dva.argus.entity.Metric;

public class MetricIteratingTransformTest {
	private static final String TEST_SCOPE = "test-scope";
	private static final String TEST_METRIC = "test-metric";

	 @Test(expected = UnsupportedOperationException.class)
	 public void SMetricIteratingTransformTestWithEmtpyConstant(){
		Transform transform = new MetricIteratingTransform();	
        List<Metric> metrics = new ArrayList<Metric>();     
        List<List<Metric>> result = ((TransformIterator) transform).iterate(metrics);
	 }
	 
	 @Test(expected = IllegalArgumentException.class)
	 public void MetricIteratingTransformTestWithEmtpyMetric(){
		Transform transform = new MetricIteratingTransform();	
        List<Metric> metrics = new ArrayList<Metric>();     
        List<String> constants = new ArrayList<String>();
        constants.add("device");
        List<List<Metric>> result = ((TransformIterator) transform).iterate(metrics,constants);
	 }
	 
	 @Test(expected = IllegalArgumentException.class)
	 public void MetricIteratingTransformTestWithInvalidTagName(){
		Transform transform = new MetricIteratingTransform();	
        Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);
        Map<Long, String> datapoints_1 = new HashMap<Long, String>();
        datapoints_1.put(100L, "200.0");
        datapoints_1.put(200L, "100.0");
        metric_1.setDatapoints(datapoints_1);
        metric_1.setTag("device", "na11-app1-1-chi.ops.sfdc.net");
        metric_1.setMetric("TrustTime");
        
        List<Metric> metrics = new ArrayList<Metric>();
        metrics.add(metric_1);       
        List<String> constants = new ArrayList<String>();
        constants.add("podId");
        List<List<Metric>> result = ((TransformIterator) transform).iterate(metrics,constants);
	 }
	 
	 @Test
	 public void MetricIteratingTransformTestCaseWithOneConstant() {
		Transform transform=new MetricIteratingTransform();
	 	
		Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);
        Map<Long, String> datapoints_1 = new HashMap<Long, String>();
        datapoints_1.put(100L, "900.0");
        datapoints_1.put(200L, "100.0");
        metric_1.setDatapoints(datapoints_1);
        metric_1.setTag("device", "host1.net");
        metric_1.setMetric("M1");
        
        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);
        Map<Long, String> datapoints_2 = new HashMap<Long, String>();
        datapoints_2.put(100L, "200.0");
        datapoints_2.put(200L, "300.0");
        metric_2.setDatapoints(datapoints_2);
        metric_2.setTag("device", "host2.net");
        metric_2.setMetric("M1");
        
        Metric metric_3 = new Metric(TEST_SCOPE, TEST_METRIC);
        Map<Long, String> datapoints_3 = new HashMap<Long, String>();
        datapoints_3.put(100L, "3.0");
        datapoints_3.put(200L, "9.0");
        metric_3.setDatapoints(datapoints_3);
        metric_3.setTag("device", "host1.net");
        metric_3.setMetric("M2");
        
        Metric metric_4 = new Metric(TEST_SCOPE, TEST_METRIC);
        Map<Long, String> datapoints_4 = new HashMap<Long, String>();
        datapoints_4.put(100L, "1.0");
        datapoints_4.put(200L, "2.0");
        metric_4.setDatapoints(datapoints_4);
        metric_4.setTag("device", "host2.net");
        metric_4.setMetric("M2");
        
        List<Metric> metrics = new ArrayList<Metric>();
        metrics.add(metric_1);
        metrics.add(metric_2);
        metrics.add(metric_3);
        metrics.add(metric_4);
        
        List<String> constants = new ArrayList<String>();
        constants.add("device");
        constants.add("M.*");
        
        List<List<Metric>> result = ((TransformIterator) transform).iterate(metrics,constants);
        List<String> expectedMetric=new ArrayList<String>();
        expectedMetric.add("M1");
        expectedMetric.add("M2");
        
        assertEquals(result.size(), 2);
        assertTrue("Returning result should be two executable pair with same length each", result.stream().filter(m->m.size()==2).collect(Collectors.toList()).size()==2);
        result.forEach(ms -> assertTrue("Returning result should be two executable pair with same datapoint each",ms.size()==2));
        result.forEach(ms -> assertTrue("Returning result should be two executable pair with same metrics name each",ms.stream().filter(m -> expectedMetric.contains(m.getMetric())).collect(Collectors.toList()).size()==2));
	 }
	 
	 @Test
	 public void MetricIteratingTransformTestBaseCase() {
		Transform transform=new MetricIteratingTransform();
	 	
		Metric metric_1 = new Metric(TEST_SCOPE, TEST_METRIC);
        Map<Long, String> datapoints_1 = new HashMap<Long, String>();
        datapoints_1.put(100L, "900.0");
        datapoints_1.put(200L, "100.0");
        metric_1.setDatapoints(datapoints_1);
        metric_1.setTag("device", "host1.net");
        metric_1.setMetric("M1");
        
        Metric metric_2 = new Metric(TEST_SCOPE, TEST_METRIC);
        Map<Long, String> datapoints_2 = new HashMap<Long, String>();
        datapoints_2.put(100L, "200.0");
        datapoints_2.put(200L, "300.0");
        metric_2.setDatapoints(datapoints_2);
        metric_2.setTag("device", "host2.net");
        metric_2.setMetric("M1");
        
        Metric metric_3 = new Metric(TEST_SCOPE, TEST_METRIC);
        Map<Long, String> datapoints_3 = new HashMap<Long, String>();
        datapoints_3.put(100L, "3.0");
        datapoints_3.put(200L, "9.0");
        metric_3.setDatapoints(datapoints_3);
        metric_3.setTag("device", "host1.net");
        metric_3.setMetric("M2");
        
        Metric metric_4 = new Metric(TEST_SCOPE, TEST_METRIC);
        Map<Long, String> datapoints_4 = new HashMap<Long, String>();
        datapoints_4.put(100L, "1.0");
        datapoints_4.put(200L, "2.0");
        metric_4.setDatapoints(datapoints_4);
        metric_4.setTag("device", "host2.net");
        metric_4.setMetric("M2");
        
        List<Metric> metrics = new ArrayList<Metric>();
        metrics.add(metric_1);
        metrics.add(metric_2);
        metrics.add(metric_3);
        metrics.add(metric_4);
        
        List<String> constants = new ArrayList<String>();
        constants.add("device");
        
        List<List<Metric>> result = ((TransformIterator) transform).iterate(metrics,constants);
        List<String> expectedMetric=new ArrayList<String>();
        expectedMetric.add("M1");
        expectedMetric.add("M2");
        
        assertEquals(result.size(), 2);
        assertTrue("Returning result should be two executable pair with same length each", result.stream().filter(m->m.size()==2).collect(Collectors.toList()).size()==2);
        result.forEach(ms -> assertTrue("Returning result should be two executable pair with same datapoint each",ms.size()==2));
        result.forEach(ms -> assertTrue("Returning result should be two executable pair with same metrics name each",ms.stream().filter(m -> expectedMetric.contains(m.getMetric())).collect(Collectors.toList()).size()==2));
	 }

}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

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

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Test;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.metric.transform.InterpolateTransform.InterpolationType;
import com.salesforce.dva.argus.service.tsdb.MetricQuery.Aggregator;

public class InterpolateTest {

	@Test
	public void sumWithTwoTimeSeries() {
		Map<Long, Double> datapoints = new HashMap<Long, Double>();

		datapoints.put(1000L, 1.0);
		datapoints.put(2000L, 2.0);
		datapoints.put(3000L, 3.0);
		datapoints.put(5000L, 10.0);
		datapoints.put(6000L, 2.0);
		datapoints.put(7000L, 3.0);
		datapoints.put(10000L, 15.0);

		Metric metric = new Metric("test-scope", "test-metric");

		metric.setDatapoints(datapoints);
		metric.setTag("user", "user1");
		metric.setTag("host", "host1");

		List<Metric> metrics = new ArrayList<Metric>();
		metrics.add(metric);

		datapoints = new HashMap<>();
		datapoints.put(1000L, 2.0);
		datapoints.put(2000L, 2.0);
		datapoints.put(3000L, 3.0);
		datapoints.put(4000L, 10.0);
		datapoints.put(8000L, 2.0);
		datapoints.put(10000L, 14.0);
		metric = new Metric("test-scope", "test-metric");
		metric.setDatapoints(datapoints);
		metric.setTag("user", "user1");
		metric.setTag("host", "host2");
		metrics.add(metric);

		InterpolateTransform interpolate = new InterpolateTransform();
		List<Metric> result = interpolate.transform(null,metrics, new ArrayList<String>(Arrays.asList(Aggregator.SUM.toString())));

		Map<Long, Double> expectedDatapoints = new TreeMap<Long, Double>(); 
		expectedDatapoints.put(1000L, 3.0);
		expectedDatapoints.put(2000L, 4.0);
		expectedDatapoints.put(3000L, 6.0);
		expectedDatapoints.put(4000L, 16.5);
		expectedDatapoints.put(5000L, 18.0);
		expectedDatapoints.put(6000L, 8.0);
		expectedDatapoints.put(7000L, 7.0);
		expectedDatapoints.put(8000L, 9.0);
		expectedDatapoints.put(10000L, 29.0);

		assertEquals(expectedDatapoints.size(), result.get(0).getDatapoints().size());
		assertEquals(expectedDatapoints, result.get(0).getDatapoints());
	}
	
	@Test
	public void averageWithTwoTimeSeries() {
		Map<Long, Double> datapoints = new HashMap<Long, Double>();

		datapoints.put(1000L, 1.0);
		datapoints.put(2000L, 2.0);
		datapoints.put(3000L, 3.0);
		datapoints.put(5000L, 10.0);
		datapoints.put(6000L, 2.0);
		datapoints.put(7000L, 3.0);
		datapoints.put(10000L, 15.0);

		Metric metric = new Metric("test-scope", "test-metric");

		metric.setDatapoints(datapoints);
		metric.setTag("user", "user1");
		metric.setTag("host", "host1");

		List<Metric> metrics = new ArrayList<Metric>();
		metrics.add(metric);

		datapoints = new HashMap<>();
		datapoints.put(1000L, 2.0);
		datapoints.put(2000L, 2.0);
		datapoints.put(3000L, 3.0);
		datapoints.put(4000L, 10.0);
		datapoints.put(8000L, 2.0);
		datapoints.put(10000L, 14.0);
		metric = new Metric("test-scope", "test-metric");
		metric.setDatapoints(datapoints);
		metric.setTag("user", "user1");
		metric.setTag("host", "host2");
		metrics.add(metric);

		InterpolateTransform interpolate = new InterpolateTransform();
		List<Metric> result = interpolate.transform(null,metrics, new ArrayList<String>(Arrays.asList(Aggregator.AVG.toString())));

		Map<Long, Double> expectedDatapoints = new TreeMap<Long, Double>(); 
		expectedDatapoints.put(1000L, 1.5);
		expectedDatapoints.put(2000L, 2.0);
		expectedDatapoints.put(3000L, 3.0);
		expectedDatapoints.put(4000L, 8.25);
		expectedDatapoints.put(5000L, 9.0);
		expectedDatapoints.put(6000L, 4.0);
		expectedDatapoints.put(7000L, 3.5);
		expectedDatapoints.put(8000L, 4.5);
		expectedDatapoints.put(10000L, 14.5);

		assertEquals(expectedDatapoints.size(), result.get(0).getDatapoints().size());
		assertEquals(expectedDatapoints, result.get(0).getDatapoints());
	}

	
	@Test
	public void minimumWithTwoTimeSeries() {
		Map<Long, Double> datapoints = new HashMap<Long, Double>();

		datapoints.put(1000L, 1.0);
		datapoints.put(2000L, 2.0);
		datapoints.put(3000L, 3.0);
		datapoints.put(5000L, 10.0);
		datapoints.put(6000L, 2.0);
		datapoints.put(7000L, 3.0);
		datapoints.put(10000L, 15.0);

		Metric metric = new Metric("test-scope", "test-metric");

		metric.setDatapoints(datapoints);
		metric.setTag("user", "user1");
		metric.setTag("host", "host1");

		List<Metric> metrics = new ArrayList<Metric>();
		metrics.add(metric);

		datapoints = new HashMap<>();
		datapoints.put(1000L, 2.0);
		datapoints.put(2000L, 2.0);
		datapoints.put(3000L, 3.0);
		datapoints.put(4000L, 10.0);
		datapoints.put(8000L, 2.0);
		datapoints.put(10000L, 14.0);
		metric = new Metric("test-scope", "test-metric");
		metric.setDatapoints(datapoints);
		metric.setTag("user", "user1");
		metric.setTag("host", "host2");
		metrics.add(metric);

		InterpolateTransform interpolate = new InterpolateTransform();
		List<Metric> result = interpolate.transform(null,metrics, new ArrayList<String>(Arrays.asList(Aggregator.MIN.toString())));

		Map<Long, Double> expectedDatapoints = new TreeMap<Long, Double>(); 
		expectedDatapoints.put(1000L, 1.0);
		expectedDatapoints.put(2000L, 2.0);
		expectedDatapoints.put(3000L, 3.0);
		expectedDatapoints.put(4000L, 6.5);
		expectedDatapoints.put(5000L, 8.0);
		expectedDatapoints.put(6000L, 2.0);
		expectedDatapoints.put(7000L, 3.0);
		expectedDatapoints.put(8000L, 2.0);
		expectedDatapoints.put(10000L, 14.0);

		assertEquals(expectedDatapoints.size(), result.get(0).getDatapoints().size());
		assertEquals(expectedDatapoints, result.get(0).getDatapoints());
	}
	
	@Test
	public void countWithTwoTimeSeries() {
		Map<Long, Double> datapoints = new HashMap<Long, Double>();

		datapoints.put(1000L, 1.0);
		datapoints.put(2000L, 2.0);
		datapoints.put(3000L, 3.0);
		datapoints.put(5000L, 10.0);
		datapoints.put(6000L, 2.0);
		datapoints.put(7000L, 3.0);
		datapoints.put(10000L, 15.0);

		Metric metric = new Metric("test-scope", "test-metric");

		metric.setDatapoints(datapoints);
		metric.setTag("user", "user1");
		metric.setTag("host", "host1");

		List<Metric> metrics = new ArrayList<Metric>();
		metrics.add(metric);

		datapoints = new HashMap<>();
		datapoints.put(1000L, 2.0);
		datapoints.put(2000L, 2.0);
		datapoints.put(3000L, 3.0);
		datapoints.put(4000L, 10.0);
		datapoints.put(8000L, 2.0);
		datapoints.put(10000L, 14.0);
		metric = new Metric("test-scope", "test-metric");
		metric.setDatapoints(datapoints);
		metric.setTag("user", "user1");
		metric.setTag("host", "host2");
		metrics.add(metric);

		InterpolateTransform interpolate = new InterpolateTransform();
		List<Metric> result = interpolate.transform(null,metrics, new ArrayList<String>(Arrays.asList(Aggregator.COUNT.toString())));

		Map<Long, Double> expectedDatapoints = new TreeMap<Long, Double>(); 
		expectedDatapoints.put(1000L, 2.0);
		expectedDatapoints.put(2000L, 2.0);
		expectedDatapoints.put(3000L, 2.0);
		expectedDatapoints.put(4000L, 2.0);
		expectedDatapoints.put(5000L, 2.0);
		expectedDatapoints.put(6000L, 2.0);
		expectedDatapoints.put(7000L, 2.0);
		expectedDatapoints.put(8000L, 2.0);
		expectedDatapoints.put(10000L, 2.0);

		assertEquals(expectedDatapoints.size(), result.get(0).getDatapoints().size());
		assertEquals(expectedDatapoints, result.get(0).getDatapoints());
	}
	@Test
	public void maximumWithTwoTimeSeries() {
		Map<Long, Double> datapoints = new HashMap<Long, Double>();

		datapoints.put(1000L, 1.0);
		datapoints.put(2000L, 2.0);
		datapoints.put(3000L, 3.0);
		datapoints.put(5000L, 10.0);
		datapoints.put(6000L, 2.0);
		datapoints.put(7000L, 3.0);
		datapoints.put(10000L, 15.0);

		Metric metric = new Metric("test-scope", "test-metric");

		metric.setDatapoints(datapoints);
		metric.setTag("user", "user1");
		metric.setTag("host", "host1");

		List<Metric> metrics = new ArrayList<Metric>();
		metrics.add(metric);

		datapoints = new HashMap<>();
		datapoints.put(1000L, 2.0);
		datapoints.put(2000L, 2.0);
		datapoints.put(3000L, 3.0);
		datapoints.put(4000L, 10.0);
		datapoints.put(8000L, 2.0);
		datapoints.put(10000L, 14.0);
		metric = new Metric("test-scope", "test-metric");
		metric.setDatapoints(datapoints);
		metric.setTag("user", "user1");
		metric.setTag("host", "host2");
		metrics.add(metric);

		InterpolateTransform interpolate = new InterpolateTransform();
		List<Metric> result = interpolate.transform(null,metrics, new ArrayList<String>(Arrays.asList(Aggregator.MAX.toString())));

		Map<Long, Double> expectedDatapoints = new TreeMap<Long, Double>(); 
		expectedDatapoints.put(1000L, 2.0);
		expectedDatapoints.put(2000L, 2.0);
		expectedDatapoints.put(3000L, 3.0);
		expectedDatapoints.put(4000L, 10.0);
		expectedDatapoints.put(5000L, 10.0);
		expectedDatapoints.put(6000L, 6.0);
		expectedDatapoints.put(7000L, 4.0);
		expectedDatapoints.put(8000L, 7.0);
		expectedDatapoints.put(10000L, 15.0);

		assertEquals(expectedDatapoints.size(), result.get(0).getDatapoints().size());
		assertEquals(expectedDatapoints, result.get(0).getDatapoints());
	}
	
	@Test
	public void zimSumWithTwoTimeSeries() {
		Map<Long, Double> datapoints = new HashMap<Long, Double>();

		datapoints.put(1000L, 1.0);
		datapoints.put(2000L, 2.0);
		datapoints.put(3000L, 3.0);
		datapoints.put(5000L, 10.0);
		datapoints.put(6000L, 2.0);
		datapoints.put(7000L, 3.0);
		datapoints.put(10000L, 15.0);

		Metric metric = new Metric("test-scope", "test-metric");

		metric.setDatapoints(datapoints);
		metric.setTag("user", "user1");
		metric.setTag("host", "host1");

		List<Metric> metrics = new ArrayList<Metric>();
		metrics.add(metric);

		datapoints = new HashMap<>();
		datapoints.put(1000L, 2.0);
		datapoints.put(2000L, 2.0);
		datapoints.put(3000L, 3.0);
		datapoints.put(4000L, 10.0);
		datapoints.put(8000L, 2.0);
		datapoints.put(10000L, 14.0);
		metric = new Metric("test-scope", "test-metric");
		metric.setDatapoints(datapoints);
		metric.setTag("user", "user1");
		metric.setTag("host", "host2");
		metrics.add(metric);


		
		InterpolateTransform interpolate = new InterpolateTransform();
		List<Metric> result = interpolate.transform(null,metrics, new ArrayList<String>(Arrays.asList(InterpolationType.ZIMSUM.toString())));

		Map<Long, Double> expectedDatapoints = new TreeMap<Long, Double>(); 
		expectedDatapoints.put(1000L, 3.0);
		expectedDatapoints.put(2000L, 4.0);
		expectedDatapoints.put(3000L, 6.0);
		expectedDatapoints.put(4000L, 10.0);
		expectedDatapoints.put(5000L, 10.0);
		expectedDatapoints.put(6000L, 2.0);
		expectedDatapoints.put(7000L, 3.0);
		expectedDatapoints.put(8000L, 2.0);
		expectedDatapoints.put(10000L, 29.0);

		assertEquals(expectedDatapoints.size(), result.get(0).getDatapoints().size());
		assertEquals(expectedDatapoints, result.get(0).getDatapoints());
	}	
	

	@Test
	public void linearInterpolateWithOneTimeSeries() {
		Map<Long, Double> datapoints = new HashMap<Long, Double>();

		datapoints.put(1000L, 1.0);
		datapoints.put(2000L, 2.0);
		datapoints.put(3000L, 3.0);

		Metric metric = new Metric("test-scope", "test-metric");

		metric.setDatapoints(datapoints);
		metric.setTag("user", "user1");
		metric.setTag("host", "host1");

		List<Metric> metrics = new ArrayList<Metric>();
		metrics.add(metric);

		InterpolateTransform interpolate = new InterpolateTransform();
		List<Metric> result = interpolate.transform(null, metrics, new ArrayList<String>(Arrays.asList(Aggregator.SUM.toString())));

		assertEquals(datapoints, result.get(0).getDatapoints());
	}

	@Test
	public void linearInterpolateWithTwoTimeSeriesStartEndDontAlign() {
		Map<Long, Double> datapoints = new HashMap<Long, Double>();

		datapoints.put(1000L, 1.0);
		datapoints.put(2000L, 2.0);
		datapoints.put(3000L, 3.0);
		datapoints.put(5000L, 10.0);
		datapoints.put(6000L, 2.0);
		datapoints.put(7000L, 3.0);
		datapoints.put(10000L, 15.0);

		Metric metric = new Metric("test-scope", "test-metric");

		metric.setDatapoints(datapoints);
		metric.setTag("user", "user1");
		metric.setTag("host", "host1");

		List<Metric> metrics = new ArrayList<Metric>();
		metrics.add(metric);

		datapoints = new HashMap<>();
		datapoints.put(2000L, 2.0);
		datapoints.put(3000L, 3.0);
		datapoints.put(4000L, 10.0);
		datapoints.put(8000L, 2.0);
		metric = new Metric("test-scope", "test-metric");
		metric.setDatapoints(datapoints);
		metric.setTag("user", "user1");
		metric.setTag("host", "host2");
		metrics.add(metric);

		InterpolateTransform interpolate = new InterpolateTransform();
		List<Metric> result = interpolate.transform(null, metrics, new ArrayList<String>(Arrays.asList(Aggregator.SUM.toString())));

		Map<Long, Double> expectedDatapoints = new TreeMap<Long, Double>();
		expectedDatapoints.put(1000L, 1.0);
		expectedDatapoints.put(2000L, 4.0);
		expectedDatapoints.put(3000L, 6.0);
		expectedDatapoints.put(4000L, 16.5);
		expectedDatapoints.put(5000L, 18.0);
		expectedDatapoints.put(6000L, 8.0);
		expectedDatapoints.put(7000L, 7.0);
		expectedDatapoints.put(8000L, 9.0);
		expectedDatapoints.put(10000L, 15.0);

		assertEquals(expectedDatapoints, result.get(0).getDatapoints());
	}
	
	@Test
	public void zimsumWithTwoTimeSeriesStartEndDontAlign() {
		Map<Long, Double> datapoints = new HashMap<Long, Double>();

		datapoints.put(1000L, 1.0);
		datapoints.put(2000L, 2.0);
		datapoints.put(3000L, 3.0);
		datapoints.put(5000L, 10.0);
		datapoints.put(6000L, 2.0);
		datapoints.put(7000L, 3.0);
		datapoints.put(10000L, 15.0);

		Metric metric = new Metric("test-scope", "test-metric");

		metric.setDatapoints(datapoints);
		metric.setTag("user", "user1");
		metric.setTag("host", "host1");

		List<Metric> metrics = new ArrayList<Metric>();
		metrics.add(metric);

		datapoints = new HashMap<>();
		datapoints.put(2000L, 2.0);
		datapoints.put(3000L, 3.0);
		datapoints.put(4000L, 10.0);
		datapoints.put(8000L, 2.0);
		metric = new Metric("test-scope", "test-metric");
		metric.setDatapoints(datapoints);
		metric.setTag("user", "user1");
		metric.setTag("host", "host2");
		metrics.add(metric);

		InterpolateTransform interpolate = new InterpolateTransform();
		List<Metric> result = interpolate.transform(null, metrics, new ArrayList<String>(Arrays.asList(InterpolationType.ZIMSUM.toString())));

		Map<Long, Double> expectedDatapoints = new TreeMap<Long, Double>();
		expectedDatapoints.put(1000L, 1.0);
		expectedDatapoints.put(2000L, 4.0);
		expectedDatapoints.put(3000L, 6.0);
		expectedDatapoints.put(4000L, 10.0);
		expectedDatapoints.put(5000L, 10.0);
		expectedDatapoints.put(6000L, 2.0);
		expectedDatapoints.put(7000L, 3.0);
		expectedDatapoints.put(8000L, 2.0);
		expectedDatapoints.put(10000L, 15.0);

		assertEquals(expectedDatapoints, result.get(0).getDatapoints());
	}
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

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

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.util.QueryContext;
import com.salesforce.dva.argus.util.QueryUtils;

import org.junit.Test;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

import static org.junit.Assert.assertEquals;

public class DownsampleTransformTest {

	private static final String TEST_SCOPE = "test-scope";
	private static final String TEST_METRIC = "test-metric";

	@Test(expected = IllegalArgumentException.class)
	public void testDownsampleTransformWithIllegalUnit() {
		Transform downsampleTransform = new DownsampleTransform();
		Map<Long, Double> datapoints = new HashMap<Long, Double>();

		datapoints.put(1000L, 1.0);

		Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

		metric.setDatapoints(datapoints);

		List<Metric> metrics = new ArrayList<Metric>();

		metrics.add(metric);

		List<String> constants = new ArrayList<String>();

		constants.add("2k-avg");
		downsampleTransform.transform(null, metrics, constants);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testDownsampelTransformWithIllegalType() {
		Transform downsampleTransform = new DownsampleTransform();
		Map<Long, Double> datapoints = new HashMap<Long, Double>();

		datapoints.put(1000L, 1.0);

		Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

		metric.setDatapoints(datapoints);

		List<Metric> metrics = new ArrayList<Metric>();

		metrics.add(metric);

		List<String> constants = new ArrayList<String>();

		constants.add("2s-foobar");
		downsampleTransform.transform(null, metrics, constants);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testDownsampleTransformWithoutUnit() {
		Transform downsampleTransform = new DownsampleTransform();
		Map<Long, Double> datapoints = new HashMap<Long, Double>();

		datapoints.put(1000L, 1.0);

		Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

		metric.setDatapoints(datapoints);

		List<Metric> metrics = new ArrayList<Metric>();

		metrics.add(metric);

		List<String> constants = new ArrayList<String>();

		constants.add("-min");
		downsampleTransform.transform(null, metrics, constants);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testDownsampleTransformWithoutType() {
		Transform downsampleTransform = new DownsampleTransform();
		Map<Long, Double> datapoints = new HashMap<Long, Double>();

		datapoints.put(1000L, 1.0);

		Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

		metric.setDatapoints(datapoints);

		List<Metric> metrics = new ArrayList<Metric>();

		metrics.add(metric);

		List<String> constants = new ArrayList<String>();

		constants.add("6s-");
		downsampleTransform.transform(null, metrics, constants);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testDownsampleTransformWithIllegalExpFormat() {
		Transform downsampleTransform = new DownsampleTransform();
		Map<Long, Double> datapoints = new HashMap<Long, Double>();

		datapoints.put(1000L, 1.0);

		Metric metric = new Metric(TEST_SCOPE, TEST_METRIC);

		metric.setDatapoints(datapoints);

		List<Metric> metrics = new ArrayList<Metric>();

		metrics.add(metric);

		List<String> constants = new ArrayList<String>();

		constants.add("***test");
		downsampleTransform.transform(null, metrics, constants);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testDownsampleTransformWithoutMetrics() {
		Transform downsampleTransform = new DownsampleTransform();
		List<Metric> metrics = null;
		List<String> constants = new ArrayList<String>();

		constants.add("2");
		constants.add("average");
		downsampleTransform.transform(null, metrics, constants);
	}

	@Test
	public void testDownsampleTransformAvgOneMetric() {
		Transform downsampleTransform = new DownsampleTransform();
		Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

		datapoints_1.put(0L, 1.0);
		datapoints_1.put(1000L, 1.0);
		datapoints_1.put(2000L, 2.0);
		datapoints_1.put(3000L, 3.0);
		datapoints_1.put(4000L, 4.0);
		datapoints_1.put(5000L, 5.0);
		datapoints_1.put(6000L, 6.0);
		datapoints_1.put(7000L, 7.0);
		datapoints_1.put(8000L, 8.0);
		datapoints_1.put(9000L, 9.0);

		Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

		metric_1.setDatapoints(datapoints_1);

		List<Metric> metrics = new ArrayList<Metric>();

		metrics.add(metric_1);

		List<String> constants = new ArrayList<String>();

		constants.add("2s-avg");

		Map<Long, Double> expected_1 = new HashMap<Long, Double>();

		expected_1.put(0L, 1.0);
		expected_1.put(2000L, 2.5);
		expected_1.put(4000L, 4.5);
		expected_1.put(6000L, 6.5);
		expected_1.put(8000L, 8.5);

		List<Metric> result = downsampleTransform.transform(null, metrics, constants);

		assertEquals(result.size(), 1);
		assertEquals(expected_1, result.get(0).getDatapoints());
	}

	@Test
	public void testDownsampleTransformMinOneMetric() {
		Transform downsampleTransform = new DownsampleTransform();
		Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

		datapoints_1.put(1L, 1.0);
		datapoints_1.put(1000L, 1.0);
		datapoints_1.put(2000L, 2.0);
		datapoints_1.put(3000L, 3.0);
		datapoints_1.put(4000L, 4.0);
		datapoints_1.put(5000L, 5.0);
		datapoints_1.put(6000L, 6.0);
		datapoints_1.put(7000L, 7.0);
		datapoints_1.put(8000L, 8.0);
		datapoints_1.put(9000L, 9.0);

		Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

		metric_1.setDatapoints(datapoints_1);

		List<Metric> metrics = new ArrayList<Metric>();

		metrics.add(metric_1);

		List<String> constants = new ArrayList<String>();

		constants.add("2s-min");

		Map<Long, Double> expected_1 = new HashMap<Long, Double>();

		expected_1.put(0L, 1.0);
		expected_1.put(2000L, 2.0);
		expected_1.put(4000L, 4.0);
		expected_1.put(6000L, 6.0);
		expected_1.put(8000L, 8.0);

		List<Metric> result = downsampleTransform.transform(null, metrics, constants);

		assertEquals(result.size(), 1);
		assertEquals(expected_1, result.get(0).getDatapoints());
	}

	@Test
	public void testDownsampleTransformMaxOneMetric() {
		Transform downsampleTransform = new DownsampleTransform();
		Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

		datapoints_1.put(1L, 1.0);
		datapoints_1.put(1000L, 1.0);
		datapoints_1.put(2000L, 2.0);
		datapoints_1.put(3000L, 3.0);
		datapoints_1.put(4000L, 4.0);
		datapoints_1.put(5000L, 5.0);
		datapoints_1.put(6000L, 6.0);
		datapoints_1.put(7000L, 7.0);
		datapoints_1.put(8000L, 8.0);
		datapoints_1.put(9000L, 9.0);

		Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

		metric_1.setDatapoints(datapoints_1);

		List<Metric> metrics = new ArrayList<Metric>();

		metrics.add(metric_1);

		List<String> constants = new ArrayList<String>();

		constants.add("2s-max");

		Map<Long, Double> expected_1 = new HashMap<Long, Double>();

		expected_1.put(0L, 1.0);
		expected_1.put(2000L, 3.0);
		expected_1.put(4000L, 5.0);
		expected_1.put(6000L, 7.0);
		expected_1.put(8000L, 9.0);

		List<Metric> result = downsampleTransform.transform(null, metrics, constants);

		assertEquals(result.size(), 1);
		assertEquals(expected_1, result.get(0).getDatapoints());
	}

	@Test
	public void testDownsampleTransformShouldReturnSameMetric() {
		Transform downsampleTransform = new DownsampleTransform();
		Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

		datapoints_1.put(1000L, 1.0);
		datapoints_1.put(2000L, 2.0);
		datapoints_1.put(3000L, 3.0);
		datapoints_1.put(4000L, 4.0);
		datapoints_1.put(5000L, 5.0);


		Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

		metric_1.setDatapoints(datapoints_1);

		List<Metric> metrics = new ArrayList<Metric>();

		metrics.add(metric_1);

		List<String> constants = new ArrayList<String>();

		constants.add("1s-sum");

		Map<Long, Double> expected_1 = new HashMap<Long, Double>();

		expected_1.put(1000L, 1.0);
		expected_1.put(2000L, 2.0);
		expected_1.put(3000L, 3.0);
		expected_1.put(4000L, 4.0);
		expected_1.put(5000L, 5.0);


		List<Metric> result = downsampleTransform.transform(null, metrics, constants);

		assertEquals(result.size(), 1);
		assertEquals(expected_1, result.get(0).getDatapoints());
	}

	@Test
	public void testDownsampleTransformSumOneMetric() {
		Transform downsampleTransform = new DownsampleTransform();
		Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

		datapoints_1.put(1L, 0.0);
		datapoints_1.put(1000L, 1.0);
		datapoints_1.put(2000L, 2.0);
		datapoints_1.put(3000L, 3.0);
		datapoints_1.put(4000L, 4.0);
		datapoints_1.put(5000L, 5.0);
		datapoints_1.put(6000L, 6.0);
		datapoints_1.put(7000L, 7.0);
		datapoints_1.put(8000L, 8.0);
		datapoints_1.put(9000L, 9.0);

		Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

		metric_1.setDatapoints(datapoints_1);

		List<Metric> metrics = new ArrayList<Metric>();

		metrics.add(metric_1);

		List<String> constants = new ArrayList<String>();

		constants.add("2s-sum");

		Map<Long, Double> expected_1 = new HashMap<Long, Double>();

		expected_1.put(0L, 1.0);
		expected_1.put(2000L, 5.0);
		expected_1.put(4000L, 9.0);
		expected_1.put(6000L, 13.0);
		expected_1.put(8000L, 17.0);

		List<Metric> result = downsampleTransform.transform(null, metrics, constants);

		assertEquals(result.size(), 1);
		assertEquals(expected_1, result.get(0).getDatapoints());
	}

	@Test
	public void testDownsampleTransformDevOneMetric() {
		Transform downsampleTransform = new DownsampleTransform();
		Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

		datapoints_1.put(0L, 0.0);
		datapoints_1.put(1000L, 1.0);
		datapoints_1.put(2000L, 2.0);
		datapoints_1.put(3000L, 3.0);
		datapoints_1.put(4000L, 4.0);
		datapoints_1.put(5000L, 5.0);
		datapoints_1.put(6000L, 6.0);
		datapoints_1.put(7000L, 7.0);
		datapoints_1.put(8000L, 8.0);
		datapoints_1.put(9000L, 9.0);
		datapoints_1.put(10000L, 9.0);


		Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

		metric_1.setDatapoints(datapoints_1);

		List<Metric> metrics = new ArrayList<Metric>();

		metrics.add(metric_1);

		List<String> constants = new ArrayList<String>();

		constants.add("3s-dev");

		Map<Long, Double> expected_1 = new HashMap<Long, Double>();

		expected_1.put(0L, 1.0);
		expected_1.put(3000L, 1.0);
		expected_1.put(6000L, 1.0);
		expected_1.put(9000L, 0.0);
		List<Metric> result = downsampleTransform.transform(null, metrics, constants);

		assertEquals(result.size(), 1);
		assertEquals(expected_1, result.get(0).getDatapoints());
	}

	@Test
	public void testDownsampleTransformAvgMultipleMetrics() {
		Transform downsampleTransform = new DownsampleTransform();
		Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

		datapoints_1.put(1000L, 1.0);
		datapoints_1.put(2000L, 2.0);
		datapoints_1.put(3000L, 3.0);
		datapoints_1.put(4000L, 4.0);
		datapoints_1.put(5000L, 5.0);
		datapoints_1.put(6000L, 6.0);
		datapoints_1.put(7000L, 7.0);
		datapoints_1.put(8000L, 8.0);
		datapoints_1.put(9000L, 9.0);

		Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

		metric_1.setDatapoints(datapoints_1);

		Map<Long, Double> datapoints_2 = new HashMap<Long, Double>();

		datapoints_2.put(1L, 0.0);
		datapoints_2.put(1000L, 100.0);
		datapoints_2.put(2000L, 200.0);
		datapoints_2.put(3000L, 300.0);
		datapoints_2.put(4000L, 400.0);
		datapoints_2.put(5000L, 500.0);
		datapoints_2.put(6000L, 600.0);
		datapoints_2.put(7000L, 700.0);
		datapoints_2.put(8000L, 800.0);
		datapoints_2.put(9000L, 900.0);

		Metric metric_2 = new Metric(TEST_SCOPE + "2", TEST_METRIC);

		metric_2.setDatapoints(datapoints_2);

		List<Metric> metrics = new ArrayList<Metric>();

		metrics.add(metric_1);
		metrics.add(metric_2);

		List<String> constants = new ArrayList<String>();

		constants.add("2s-avg");

		Map<Long, Double> expected_1 = new HashMap<Long, Double>();

		expected_1.put(1000L, 1.5);
		expected_1.put(3000L, 3.5);
		expected_1.put(5000L, 5.5);
		expected_1.put(7000L, 7.5);
		expected_1.put(9000L, 9.0);

		Map<Long, Double> expected_2 = new HashMap<Long, Double>();

		expected_2.put(0L, 50.0);
		expected_2.put(2000L, 250.0);
		expected_2.put(4000L, 450.0);
		expected_2.put(6000L, 650.0);
		expected_2.put(8000L, 850.0);

		List<Metric> result = downsampleTransform.transform(null, metrics, constants);

		assertEquals(result.size(), 2);
		assertEquals(expected_1, result.get(0).getDatapoints());
		assertEquals(expected_2, result.get(1).getDatapoints());
	}

	@Test
	public void testDownsampleTransformWindowGreaterThanRangeOneMetric() {
		Transform downsampleTransform = new DownsampleTransform();
		Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

		datapoints_1.put(1000L, 1.0);
		datapoints_1.put(2000L, 2.0);
		datapoints_1.put(3000L, 3.0);
		datapoints_1.put(4000L, 4.0);
		datapoints_1.put(5000L, 5.0);
		datapoints_1.put(6000L, 6.0);
		datapoints_1.put(7000L, 7.0);
		datapoints_1.put(8000L, 8.0);
		datapoints_1.put(9000L, 9.0);

		Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

		metric_1.setDatapoints(datapoints_1);

		List<Metric> metrics = new ArrayList<Metric>();

		metrics.add(metric_1);

		List<String> constants = new ArrayList<String>();

		constants.add("100s-avg");

		Map<Long, Double> expected_1 = new HashMap<Long, Double>();

		expected_1.put(1000L, 5.0);

		List<Metric> result = downsampleTransform.transform(null, metrics, constants);

		assertEquals(result.size(), 1);
		assertEquals(expected_1, result.get(0).getDatapoints());
	}

	@Test
	public void testDownsampleTransformWindowLessThanUnitOneMetric() {
		Transform downsampleTransform = new DownsampleTransform();
		Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

		datapoints_1.put(1000L, 1.0);
		datapoints_1.put(5000L, 5.0);
		datapoints_1.put(9000L, 9.0);

		Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

		metric_1.setDatapoints(datapoints_1);

		List<Metric> metrics = new ArrayList<Metric>();

		metrics.add(metric_1);

		List<String> constants = new ArrayList<String>();

		constants.add("2s-avg");

		Map<Long, Double> expected_1 = new HashMap<Long, Double>();

		expected_1.put(1000L, 1.0);
		expected_1.put(5000L, 5.0);
		expected_1.put(9000L, 9.0);

		List<Metric> result = downsampleTransform.transform(null, metrics, constants);

		assertEquals(result.size(), 1);
		assertEquals(expected_1, result.get(0).getDatapoints());
	}

	@Test
	public void testDownsampleTransformMinOneMetricHavingNull() {
		Transform downsampleTransform = new DownsampleTransform();
		Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

		datapoints_1.put(1000L, null);
		datapoints_1.put(2000L, null);
		datapoints_1.put(3000L, 3.0);
		datapoints_1.put(4000L, null);
		datapoints_1.put(5000L, 5.0);
		datapoints_1.put(6000L, null);
		datapoints_1.put(7000L, 7.0);
		datapoints_1.put(8000L, null);
		datapoints_1.put(9000L, 9.0);

		Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

		metric_1.setDatapoints(datapoints_1);

		List<Metric> metrics = new ArrayList<Metric>();

		metrics.add(metric_1);

		List<String> constants = new ArrayList<String>();

		constants.add("2s-min");

		Map<Long, Double> expected_1 = new HashMap<Long, Double>();

		expected_1.put(1000L, 0.0);
		expected_1.put(3000L, 0.0);
		expected_1.put(5000L, 0.0);
		expected_1.put(7000L, 0.0);
		expected_1.put(9000L, 9.0);
		List<Metric> result = downsampleTransform.transform(null, metrics, constants);

		assertEquals(result.size(), 1);
		assertEquals(expected_1, result.get(0).getDatapoints());
	}

	@Test//_W-2905322
	public void testDownsampleTransformBug_OnHourLevel() {
		Transform downsampleTransform = new DownsampleTransform();
		Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

		datapoints_1.put(1453798890000L, 1.0);
		datapoints_1.put(1453802750000L, 2.0);
		datapoints_1.put(1453806510000L, 3.0);
		datapoints_1.put(1453809690000L, 4.0);

		Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

		metric_1.setDatapoints(datapoints_1);

		List<Metric> metrics = new ArrayList<Metric>();

		metrics.add(metric_1);

		List<String> constants = new ArrayList<String>();

		constants.add("1h-min");

		Map<Long, Double> expected_1 = new HashMap<Long, Double>();

		expected_1.put(1453798800000L, 1.0);
		expected_1.put(1453802400000L, 2.0);
		expected_1.put(1453806000000L, 3.0);
		expected_1.put(1453809600000L, 4.0);

		List<Metric> result = downsampleTransform.transform(null, metrics, constants);

		assertEquals(result.size(), 1);
		assertEquals(expected_1, result.get(0).getDatapoints());
	}


	@Test
	public void testDownsampleTransformCountOneMetric() {
		Transform downsampleTransform = new DownsampleTransform();
		Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

		datapoints_1.put(1L, 1.0);
		datapoints_1.put(1000L, 1.0);
		datapoints_1.put(2000L, 2.0);
		datapoints_1.put(3000L, 3.0);
		datapoints_1.put(4000L, 4.0);
		datapoints_1.put(5000L, 5.0);
		datapoints_1.put(6000L, 6.0);
		datapoints_1.put(7000L, 7.0);
		datapoints_1.put(8000L, 8.0);
		datapoints_1.put(9000L, 9.0);

		Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

		metric_1.setDatapoints(datapoints_1);

		List<Metric> metrics = new ArrayList<Metric>();

		metrics.add(metric_1);

		List<String> constants = new ArrayList<String>();

		constants.add("3s-count");

		Map<Long, Double> expected_1 = new HashMap<Long, Double>();

		expected_1.put(0L, 3.0);
		expected_1.put(3000L, 3.0);
		expected_1.put(6000L, 3.0);
		expected_1.put(9000L, 1.0);

		List<Metric> result = downsampleTransform.transform(null, metrics, constants);
		assertEquals(result.size(), 1);
		assertEquals(expected_1, result.get(0).getDatapoints());
	}

	@Test
	public void testDownsampleTransformCountOneMetricHavingNull() {
		Transform downsampleTransform = new DownsampleTransform();
		Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

		datapoints_1.put(1L, null);
		datapoints_1.put(1000L, null);
		datapoints_1.put(2000L, null);
		datapoints_1.put(3000L, 3.0);
		datapoints_1.put(4000L, null);
		datapoints_1.put(5000L, 5.0);
		datapoints_1.put(6000L, null);
		datapoints_1.put(7000L, 7.0);
		datapoints_1.put(8000L, null);
		datapoints_1.put(9000L, 9.0);

		Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

		metric_1.setDatapoints(datapoints_1);

		List<Metric> metrics = new ArrayList<Metric>();

		metrics.add(metric_1);

		List<String> constants = new ArrayList<String>();

		constants.add("3s-count");

		Map<Long, Double> expected_1 = new HashMap<Long, Double>();

		expected_1.put(0L, 0.0);
		expected_1.put(3000L, 2.0);
		expected_1.put(6000L, 1.0);
		expected_1.put(9000L, 1.0);

		List<Metric> result = downsampleTransform.transform(null, metrics, constants);
		assertEquals(result.size(), 1);
		assertEquals(expected_1, result.get(0).getDatapoints());
	}

	@Test
	public void testDownsampleTransformMetricIsAllNull() {
		Transform downsampleTransform = new DownsampleTransform();
		Map<Long, Double> datapoints = new HashMap<Long, Double>();

		Metric metric = new Metric(TEST_SCOPE + "1", TEST_METRIC);
		metric.setDatapoints(datapoints);
		List<Metric> metrics = new ArrayList<Metric>();
		metrics.add(metric);

		List<String> constants = new ArrayList<String>();
		constants.add("3s-count");
		Map<Long, Double> expected = new HashMap<Long, Double>();
		List<Metric> result = downsampleTransform.transform(null, metrics, constants);
		assertEquals(result.size(), 1);
		assertEquals(expected, result.get(0).getDatapoints());
	}

	@Test
	public void testDownsampleTransformPercentileOneMetric() {
		Transform downsampleTransform = new DownsampleTransform();
		Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

		datapoints_1.put(1000L, 1.0);
		datapoints_1.put(2000L, 2.0);
		datapoints_1.put(3000L, 3.0);
		datapoints_1.put(4000L, 4.0);
		datapoints_1.put(5000L, 5.0);
		datapoints_1.put(6000L, 6.0);
		datapoints_1.put(7000L, 7.0);
		datapoints_1.put(8000L, 8.0);
		datapoints_1.put(9000L, 9.0);

		Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

		metric_1.setDatapoints(datapoints_1);

		List<Metric> metrics = new ArrayList<Metric>();

		metrics.add(metric_1);

		List<String> constants = new ArrayList<String>();

		constants.add("2s-p90");

		Map<Long, Double> expected_1 = new HashMap<Long, Double>();

		expected_1.put(1000L, 2.0);
		expected_1.put(3000L, 4.0);
		expected_1.put(5000L, 6.0);
		expected_1.put(7000L, 8.0);
		expected_1.put(9000L, 9.0);

		List<Metric> result = downsampleTransform.transform(null, metrics, constants);

		assertEquals(result.size(), 1);
		assertEquals(expected_1, result.get(0).getDatapoints());
	}

	@Test
	public void testDownsampleTransformPercentileMultipleMetrics() {
		Transform downsampleTransform = new DownsampleTransform();
		Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();

		datapoints_1.put(000L, 10.0);
		datapoints_1.put(1000L, 1.0);
		datapoints_1.put(2000L, 2.0);
		datapoints_1.put(3000L, 3.0);
		datapoints_1.put(4000L, 4.0);
		datapoints_1.put(5000L, 5.0);
		datapoints_1.put(6000L, 6.0);
		datapoints_1.put(7000L, 7.0);
		datapoints_1.put(8000L, 8.0);
		datapoints_1.put(9000L, 9.0);

		Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

		metric_1.setDatapoints(datapoints_1);

		Map<Long, Double> datapoints_2 = new HashMap<Long, Double>();

		datapoints_2.put(0L, 1.0);
		datapoints_2.put(1000L, 20.0);
		datapoints_2.put(2000L, 30.0);
		datapoints_2.put(3000L, 40.0);
		datapoints_2.put(4000L, 50.0);
		datapoints_2.put(5000L, 60.0);
		datapoints_2.put(6000L, 70.0);
		datapoints_2.put(7000L, 80.0);
		datapoints_2.put(8000L, 90.0);
		datapoints_2.put(9000L, 100.0);


		Metric metric_2 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

		metric_2.setDatapoints(datapoints_2);

		List<Metric> metrics = new ArrayList<Metric>();

		metrics.add(metric_1);
		metrics.add(metric_2);

		List<String> constants = new ArrayList<String>();
		constants.add("10s-p90");

		Map<Long, Double> expected_1 = new HashMap<Long, Double>();

		expected_1.put(0L, 9.9);

		Map<Long, Double> expected_2 = new HashMap<Long, Double>();

		expected_2.put(0L, 99.0);

		List<Metric> result = downsampleTransform.transform(null, metrics, constants);

		assertEquals(2, result.size());
		assertEquals(expected_1, result.get(0).getDatapoints());
		assertEquals(expected_2, result.get(1).getDatapoints());
	}

	@Test
	public void testSnappingSeconds(){
		Transform downsampleTransform = new DownsampleTransform();
		Map<Long, Double> datapoints = new HashMap<Long, Double>();

		datapoints.put(1002L, 1.0);
		datapoints.put(2002L, 1.0);
		datapoints.put(2010L, 1.0);
		datapoints.put(4001L, 1.0);

		datapoints.put(7000L, 1.0);
		datapoints.put(8000L, 1.0);


		Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

		metric_1.setDatapoints(datapoints);

		List<Metric> metrics = new ArrayList<Metric>();

		metrics.add(metric_1);

		List<String> constants = new ArrayList<String>();

		constants.add("2s-sum");

		Map<Long, Double> expected_1 = new HashMap<Long, Double>();

		expected_1.put(1000L, 3.0);
		expected_1.put(3000L, 1.0);
		expected_1.put(7000L, 2.0);

		List<Metric> result = downsampleTransform.transform(null, metrics, constants);

		assertEquals(result.size(), 1);
		assertEquals(expected_1, result.get(0).getDatapoints());
	}

	@Test
	public void testSnappingMinutes(){
		Transform downsampleTransform = new DownsampleTransform();
		Map<Long, Double> datapoints = new HashMap<Long, Double>();

		datapoints.put(61002L, 1.0);
		datapoints.put(120002L, 1.0);
		datapoints.put(180010L, 1.0);
		datapoints.put(540000L, 1.0);


		Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

		metric_1.setDatapoints(datapoints);

		List<Metric> metrics = new ArrayList<Metric>();

		metrics.add(metric_1);

		List<String> constants = new ArrayList<String>();

		constants.add("3m-sum");

		Map<Long, Double> expected_1 = new HashMap<Long, Double>();

		expected_1.put(60000L, 3.0);
		expected_1.put(420000L, 1.0);

		List<Metric> result = downsampleTransform.transform(null, metrics, constants);

		assertEquals(result.size(), 1);
		assertEquals(expected_1, result.get(0).getDatapoints());
	}

	@Test
	public void testSnappingHours(){
		Transform downsampleTransform = new DownsampleTransform();
		Map<Long, Double> datapoints = new HashMap<Long, Double>();

		GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
		calendar.set(2010, 11, 9, 3, 31, 31); 
		datapoints.put(calendar.getTimeInMillis(), 1.0);
		calendar.set(Calendar.HOUR_OF_DAY, 4);
		datapoints.put(calendar.getTimeInMillis(), 1.0);
		calendar.set(Calendar.HOUR_OF_DAY, 5);
		datapoints.put(calendar.getTimeInMillis(), 1.0);
		calendar.set(Calendar.HOUR_OF_DAY, 6);
		datapoints.put(calendar.getTimeInMillis(), 1.0);


		Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

		metric_1.setDatapoints(datapoints);

		List<Metric> metrics = new ArrayList<Metric>();

		metrics.add(metric_1);

		List<String> constants = new ArrayList<String>();

		constants.add("3h-sum");

		Map<Long, Double> expected_1 = new HashMap<Long, Double>();

		calendar.set(2010, 11, 9, 3, 0, 0); 
		calendar.set(Calendar.MILLISECOND, 0);

		expected_1.put(calendar.getTimeInMillis(), 3.0);
		calendar.set(Calendar.HOUR_OF_DAY, 6);
		expected_1.put(calendar.getTimeInMillis(), 1.0);

		List<Metric> result = downsampleTransform.transform(null, metrics, constants);

		assertEquals(result.size(), 1);
		assertEquals(expected_1, result.get(0).getDatapoints());
	}

	@Test
	public void testSnappingDays(){
		Transform downsampleTransform = new DownsampleTransform();
		Map<Long, Double> datapoints = new HashMap<Long, Double>();

		GregorianCalendar calendar = new GregorianCalendar(TimeZone.getTimeZone("GMT"));
		calendar.set(2010, 11, 9, 3, 31, 31); 
		datapoints.put(calendar.getTimeInMillis(), 1.0);
		calendar.set(Calendar.DAY_OF_MONTH, 10);
		datapoints.put(calendar.getTimeInMillis(), 1.0);
		calendar.set(Calendar.DAY_OF_MONTH, 11);
		datapoints.put(calendar.getTimeInMillis(), 1.0);
		calendar.set(Calendar.DAY_OF_MONTH, 12);
		datapoints.put(calendar.getTimeInMillis(), 1.0);

		calendar.set(Calendar.DAY_OF_MONTH, 18);
		datapoints.put(calendar.getTimeInMillis(), 1.0);
		calendar.set(Calendar.HOUR_OF_DAY, 18);
		datapoints.put(calendar.getTimeInMillis(), 1.0);


		Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

		metric_1.setDatapoints(datapoints);

		List<Metric> metrics = new ArrayList<Metric>();

		metrics.add(metric_1);

		List<String> constants = new ArrayList<String>();

		constants.add("7d-sum");

		Map<Long, Double> expected_1 = new HashMap<Long, Double>();

		calendar.set(2010, 11, 9, 0, 0, 0); 
		calendar.set(Calendar.MILLISECOND, 0);

		expected_1.put(calendar.getTimeInMillis(), 4.0);
		calendar.set(Calendar.DAY_OF_MONTH, 16);
		expected_1.put(calendar.getTimeInMillis(), 2.0);

		List<Metric> result = downsampleTransform.transform(null, metrics, constants);

		assertEquals(result.size(), 1);
		assertEquals(expected_1, result.get(0).getDatapoints());
	}

	@Test
	public void testDownsampleWithFillDefaultValues() {
		Transform downsampleTransform = new DownsampleTransform();
		Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();
        long startMillis = 1534368960000L; 
		datapoints_1.put(startMillis, 1.0);
		datapoints_1.put(startMillis+60000L, 1.0);
		datapoints_1.put(startMillis+2*60000L, 2.0);
		datapoints_1.put(startMillis+3*60000L, 3.0);
		datapoints_1.put(startMillis+4*60000L, 4.0);
		datapoints_1.put(startMillis+7*60000L, 7.0);
		datapoints_1.put(startMillis+8*60000L, 8.0);

		Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

		metric_1.setDatapoints(datapoints_1);

		List<Metric> metrics = new ArrayList<Metric>();

		metrics.add(metric_1);

		List<String> constants = new ArrayList<String>();

		constants.add("1m-sum");
		constants.add("-10m");
		constants.add("-0m");
		constants.add("0.0");
		Map<Long, Double> expected_1 = new HashMap<Long, Double>();

		expected_1.put(startMillis, 1.0);
		expected_1.put(startMillis+60000L, 1.0);
		expected_1.put(startMillis+2*60000L, 2.0);
		expected_1.put(startMillis+3*60000L, 3.0);
		expected_1.put(startMillis+4*60000L, 4.0);
		expected_1.put(startMillis+5*60000L, 0.0);
		expected_1.put(startMillis+6*60000L, 0.0);
		expected_1.put(startMillis+7*60000L, 7.0);
		expected_1.put(startMillis+8*60000L, 8.0);
		expected_1.put(startMillis+9*60000L, 0.0);
		
		List<Metric> result = downsampleTransform.transform(null, metrics, constants);

		assertEquals(result.size(), 1);
		assertEquals(expected_1, result.get(0).getDatapoints());
	}

	@Test
	public void testDownsampleWithAbsoluteIntervals() {
		Transform downsampleTransform = new DownsampleTransform();
		Map<Long, Double> datapoints_1 = new HashMap<Long, Double>();
        long startMillis = 1534368960000L; 
		datapoints_1.put(startMillis, 1.0);
		datapoints_1.put(startMillis+60000L, 1.0);
		datapoints_1.put(startMillis+2*60000L, 2.0);
		datapoints_1.put(startMillis+3*60000L, 3.0);
		datapoints_1.put(startMillis+4*60000L, 4.0);
		datapoints_1.put(startMillis+7*60000L, 7.0);
		datapoints_1.put(startMillis+8*60000L, 8.0);

		Metric metric_1 = new Metric(TEST_SCOPE + "1", TEST_METRIC);

		metric_1.setDatapoints(datapoints_1);

		List<Metric> metrics = new ArrayList<Metric>();

		metrics.add(metric_1);

		List<String> constants = new ArrayList<String>();

		constants.add("1m-sum");
		constants.add("0.0");
		constants.add("abs");
		Map<Long, Double> expected_1 = new TreeMap<Long, Double>();

		expected_1.put(startMillis, 1.0);
		expected_1.put(startMillis+60000L, 1.0);
		expected_1.put(startMillis+2*60000L, 2.0);
		expected_1.put(startMillis+3*60000L, 3.0);
		expected_1.put(startMillis+4*60000L, 4.0);
		expected_1.put(startMillis+5*60000L, 0.0);
		expected_1.put(startMillis+6*60000L, 0.0);
		expected_1.put(startMillis+7*60000L, 7.0);
		expected_1.put(startMillis+8*60000L, 8.0);
		expected_1.put(startMillis+9*60000L, 0.0);	

        QueryContext context = QueryUtils.getQueryContext(1534368960000L+":"+(1534368960000L+10*60000L)+":argus.core:alerts.evaluated:zimsum:1m-sum", 0L);
		List<Metric> result = downsampleTransform.transform(context, metrics, constants);
		
		assertEquals(result.size(), 1);
		assertEquals(expected_1, result.get(0).getDatapoints());
	}

}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

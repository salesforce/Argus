package com.salesforce.dva.argus.service.metric.transform;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.util.QueryContext;
import com.salesforce.dva.argus.util.TSDBQueryExpression;

public class RateTransformTest {
	private static long MINUTE=60*1000;

	@Test
	public void testRateWithNoMissingDP() {
		long startTimestamp=1*MINUTE;
		double counter=1;
		RateTransform rateTransform = new RateTransform();
		Map<Long, Double> actualDPs = new HashMap<>();
		for(int i=1;i<=60;i++) {
			actualDPs.put(i*MINUTE, counter++);
		}
		List<Metric> metrics = new ArrayList<>();
		Metric actualMetric= new Metric("testScope", "testMetric");
		actualMetric.setDatapoints(actualDPs);
		metrics.add(actualMetric);

		Map<Long, Double> expectedDPs = new HashMap<>();
		for(int i=2;i<=60;i++) {
			expectedDPs.put(i*MINUTE, 1d);
		}
		List<Metric> expectedMetrics = new ArrayList<>();
		Metric expectedMetric= new Metric("testScope", "testMetric");
		expectedMetric.setDatapoints(expectedDPs);
		expectedMetrics.add(expectedMetric);
		QueryContext queryContext = _getQueryContext(startTimestamp, 60*MINUTE);
		List<String> constants = Arrays.asList("1m","FALSE","FALSE");
		List<Metric> actualMetrics= rateTransform.transform(queryContext, metrics, constants);
		assertEquals(expectedMetrics, actualMetrics); 
		assertEquals(expectedMetrics.get(0).getDatapoints(), actualMetrics.get(0).getDatapoints()); 
	}

	@Test
	public void testRateWithMissingDPShouldInterpolate() {
		long startTime=1*MINUTE, endTime=11*MINUTE;
		RateTransform rateTransform = new RateTransform();
		List<Metric> metrics = new ArrayList<>();
		Metric actualMetric= new Metric("testScope", "testMetric");
		Map<Long, Double> actualDPs = new HashMap<>();
		actualDPs.put(startTime, 1d);
		actualDPs.put(9*60*1000l, 9d);
		actualDPs.put(endTime, 11d);
		actualMetric.setDatapoints(actualDPs);
		metrics.add(actualMetric);
		QueryContext queryContext = _getQueryContext(startTime, endTime);
		List<String> constants = Arrays.asList("1m","FALSE","TRUE");
		List<Metric> actualMetrics= rateTransform.transform(queryContext, metrics, constants);
		List<Metric> expectedMetrics = new ArrayList<>();
		Metric expectedMetric= new Metric("testScope", "testMetric");
		Map<Long, Double> expectedDPs = new HashMap<>();
		for(int i=2;i<=11;i++) {
			expectedDPs.put(i*MINUTE, 1d);
		}
		expectedMetric.setDatapoints(expectedDPs);
		expectedMetrics.add(expectedMetric);
		assertEquals(expectedMetrics, actualMetrics); 
		assertEquals(expectedMetrics.get(0).getDatapoints(), actualMetrics.get(0).getDatapoints());
	}

	@Test
	public void testRateWhenOneDPShouldReturnSameDP() {
		long startTime=1, endTime=10*MINUTE;
		RateTransform rateTransform = new RateTransform();
		List<Metric> metrics = new ArrayList<>();
		Metric actualMetric= new Metric("testScope", "testMetric");
		Map<Long, Double> actualDPs = new HashMap<>();
		actualDPs.put(startTime, 1d);
		actualMetric.setDatapoints(actualDPs);
		metrics.add(actualMetric);
		List<Metric> expectedMetrics = new ArrayList<>();
		Metric expectedMetric= new Metric("testScope", "testMetric");
		Map<Long, Double> expectedDPs = new HashMap<>();
		expectedDPs.put(startTime, 1d);
		expectedMetric.setDatapoints(expectedDPs);
		expectedMetrics.add(expectedMetric);
		QueryContext queryContext = _getQueryContext(startTime, endTime);
		List<String> constants = Arrays.asList("1m","FALSE","FALSE");
		List<Metric> actualMetrics1 = rateTransform.transform(queryContext, metrics, constants);
		constants = Arrays.asList("1m","TRUE","TRUE");
		List<Metric> actualMetrics2 = rateTransform.transform(queryContext, metrics, constants);
		assertEquals(expectedMetrics.get(0).getDatapoints(), actualMetrics1.get(0).getDatapoints());
		assertEquals(expectedMetrics.get(0).getDatapoints(), actualMetrics2.get(0).getDatapoints());
	}

	@Test
	public void testRateWithTwoDPNNoInterpolationShouldReturnOneDP() {
		long startTime=1*MINUTE, endTime=10*MINUTE;
		RateTransform rateTransform = new RateTransform();
		List<Metric> metrics = new ArrayList<>();
		Metric actualMetric= new Metric("testScope", "testMetric");
		Map<Long, Double> actualDPs = new HashMap<>();
		actualDPs.put(startTime, 1d);
		actualDPs.put(endTime, 10d);
		actualMetric.setDatapoints(actualDPs);
		metrics.add(actualMetric);
		List<Metric> expectedMetrics = new ArrayList<>();
		Metric expectedMetric= new Metric("testScope", "testMetric");
		Map<Long, Double> expectedDPs = new HashMap<>();
		expectedDPs.put(10*MINUTE, 1d);
		expectedMetric.setDatapoints(expectedDPs);
		expectedMetrics.add(expectedMetric);
		QueryContext queryContext = _getQueryContext(startTime, endTime);
		List<String> constants = Arrays.asList("1m","FALSE","FALSE");
		List<Metric> actualMetrics =  rateTransform.transform(queryContext, metrics, constants);
		assertEquals(expectedMetrics.get(0).getDatapoints(), actualMetrics.get(0).getDatapoints());
	}

	@Test
	public void testRateWithInterpolationWhenTwoDPShouldReturnInterpolatedDP() {
		long startTime=1*MINUTE, endTime=10*MINUTE;
		RateTransform rateTransform = new RateTransform();
		List<Metric> metrics = new ArrayList<>();
		Metric actualMetric= new Metric("testScope", "testMetric");
		Map<Long, Double> actualDPs = new HashMap<>();
		actualDPs.put(startTime, 1d);
		actualDPs.put(10*MINUTE, 10d);
		actualMetric.setDatapoints(actualDPs);
		metrics.add(actualMetric);
		List<Metric> expectedMetrics = new ArrayList<>();
		Metric expectedMetric= new Metric("testScope", "testMetric");
		Map<Long, Double> expectedDPs = new HashMap<>();
		for(int i=2;i<=10;i++) {
			expectedDPs.put(i*MINUTE, 1d);
		}
		expectedMetric.setDatapoints(expectedDPs);
		expectedMetrics.add(expectedMetric);
		List<String> constants = Arrays.asList("1m","FALSE","TRUE");
		QueryContext queryContext = _getQueryContext(startTime, endTime);
		List<Metric> actualMetrics = rateTransform.transform(queryContext, metrics, constants);
		assertEquals(expectedMetrics.get(0).getDatapoints(), actualMetrics.get(0).getDatapoints());
	}

	@Test
	public void testRateWithMissingFirstNLastDP() {
		long startTime=1*MINUTE, endTime=10*MINUTE;
		RateTransform rateTransform = new RateTransform();
		List<Metric> metrics = new ArrayList<>();
		Metric actualMetric= new Metric("testScope", "testMetric");
		Map<Long, Double> actualDPs = new HashMap<>();
		actualDPs.put(4*MINUTE, 4d);
		actualDPs.put(5*MINUTE, 5d);
		actualMetric.setDatapoints(actualDPs);
		metrics.add(actualMetric);
		List<Metric> expectedMetrics = new ArrayList<>();
		Metric expectedMetric= new Metric("testScope", "testMetric");
		Map<Long, Double> expectedDPs = new HashMap<>();
		for(int i=2;i<=10;i++) {
			expectedDPs.put(i*MINUTE, 1d);
		}
		expectedMetric.setDatapoints(expectedDPs);
		expectedMetrics.add(expectedMetric);
		List<String> constants = Arrays.asList("1m","FALSE","TRUE");
		QueryContext queryContext = _getQueryContext(startTime, endTime);
		List<Metric> actualMetrics = rateTransform.transform(queryContext, metrics,constants);
		assertEquals(expectedMetrics.get(0).getDatapoints(), actualMetrics.get(0).getDatapoints());
	}

	@Test
	public void testRateWithCounterResetWithNoInterpolation() {
		long startTime=1*MINUTE, endTime=5*MINUTE;
		RateTransform rateTransform = new RateTransform();
		List<Metric> metrics = new ArrayList<>();
		Metric actualMetric= new Metric("testScope", "testMetric");
		Map<Long, Double> actualDPs = new HashMap<>();
		actualDPs.put(1*MINUTE, 1d);
		actualDPs.put(2*MINUTE, 2d);
		actualDPs.put(3*MINUTE, 0d);
		actualDPs.put(4*MINUTE, 1d);
		actualDPs.put(5*MINUTE, 2d);

		actualMetric.setDatapoints(actualDPs);
		metrics.add(actualMetric);
		List<Metric> expectedMetrics = new ArrayList<>();
		Metric expectedMetric= new Metric("testScope", "testMetric");
		Map<Long, Double> expectedDPs = new HashMap<>();

		expectedDPs.put(2*MINUTE, 1d);
		expectedDPs.put(3*MINUTE, -2d);
		expectedDPs.put(4*MINUTE, 1d);
		expectedDPs.put(5*MINUTE, 1d);

		expectedMetric.setDatapoints(expectedDPs);
		expectedMetrics.add(expectedMetric);
		List<String> constants = Arrays.asList("1m","FALSE","FALSE");
		QueryContext queryContext = _getQueryContext(startTime, endTime);
		List<Metric> actualMetrics = rateTransform.transform(queryContext, metrics,constants);
		assertEquals(expectedMetrics.get(0).getDatapoints(), actualMetrics.get(0).getDatapoints());
	}

	@Test
	public void testRateWithCounterResetWithNoSkipNegativeValuesNInterpolation() {
		long startTime=1*MINUTE, endTime=8*MINUTE;
		RateTransform rateTransform = new RateTransform();
		List<Metric> metrics = new ArrayList<>();
		Metric actualMetric= new Metric("testScope", "testMetric");
		Map<Long, Double> actualDPs = new HashMap<>();
		actualDPs.put(1*MINUTE, 1d);
		actualDPs.put(2*MINUTE, 2d);
		actualDPs.put(3*MINUTE, 3d);
		actualDPs.put(6*MINUTE, 0d);
		actualDPs.put(8*MINUTE, 4d);

		actualMetric.setDatapoints(actualDPs);
		metrics.add(actualMetric);
		List<Metric> expectedMetrics = new ArrayList<>();
		Metric expectedMetric= new Metric("testScope", "testMetric");
		Map<Long, Double> expectedDPs = new HashMap<>();

		expectedDPs.put(2*MINUTE, 1d);
		expectedDPs.put(3*MINUTE, 1d);
		expectedDPs.put(4*MINUTE, -1d);
		expectedDPs.put(5*MINUTE, -1d);
		expectedDPs.put(6*MINUTE, -1d);
		expectedDPs.put(7*MINUTE, 2d);
		expectedDPs.put(8*MINUTE, 2d);

		expectedMetric.setDatapoints(expectedDPs);
		expectedMetrics.add(expectedMetric);
		List<String> constants = Arrays.asList("1m","FALSE","TRUE");
		QueryContext queryContext = _getQueryContext(startTime, endTime);
		List<Metric> actualMetrics = rateTransform.transform(queryContext, metrics,constants);
		assertEquals(expectedMetrics.get(0).getDatapoints(), actualMetrics.get(0).getDatapoints());
	}

	@Test
	public void testRateWithCounterResetWithSkipNegativeValuesNInterpolation() {
		long startTime=1*MINUTE, endTime=8*MINUTE;
		RateTransform rateTransform = new RateTransform();
		List<Metric> metrics = new ArrayList<>();
		Metric actualMetric= new Metric("testScope", "testMetric");
		Map<Long, Double> actualDPs = new HashMap<>();
		actualDPs.put(1*MINUTE, 2d);
		actualDPs.put(2*MINUTE, 3d);
		actualDPs.put(5*MINUTE, 0d);
		actualDPs.put(8*MINUTE, 3d);

		actualMetric.setDatapoints(actualDPs);
		metrics.add(actualMetric);
		List<Metric> expectedMetrics = new ArrayList<>();
		Metric expectedMetric= new Metric("testScope", "testMetric");
		Map<Long, Double> expectedDPs = new HashMap<>();

		expectedDPs.put(2*MINUTE, 1d);
		expectedDPs.put(6*MINUTE, 1d);
		expectedDPs.put(7*MINUTE, 1d);
		expectedDPs.put(8*MINUTE, 1d);

		expectedMetric.setDatapoints(expectedDPs);
		expectedMetrics.add(expectedMetric);
		List<String> constants = Arrays.asList("1m","TRUE","TRUE");
		QueryContext queryContext = _getQueryContext(startTime, endTime);
		List<Metric> actualMetrics = rateTransform.transform(queryContext, metrics,constants);
		assertEquals(expectedMetrics.get(0).getDatapoints(), actualMetrics.get(0).getDatapoints());
	}

	@Test
	public void testRateWithCounterResetWithSkipNegativeValuesNInterpolationNonUniform() {
		long startTime=1*MINUTE, endTime=8*MINUTE;
		RateTransform rateTransform = new RateTransform();
		List<Metric> metrics = new ArrayList<>();
		Metric actualMetric= new Metric("testScope", "testMetric");
		Map<Long, Double> actualDPs = new HashMap<>();
		actualDPs.put(1*MINUTE, 2d);
		actualDPs.put(2*MINUTE, 3d);
		actualDPs.put(5*MINUTE, 0d);
		actualDPs.put(8*MINUTE, 6d);

		actualMetric.setDatapoints(actualDPs);
		metrics.add(actualMetric);
		List<Metric> expectedMetrics = new ArrayList<>();
		Metric expectedMetric= new Metric("testScope", "testMetric");
		Map<Long, Double> expectedDPs = new HashMap<>();

		expectedDPs.put(2*MINUTE, 1d);
		expectedDPs.put(6*MINUTE, 2d);
		expectedDPs.put(7*MINUTE, 2d);
		expectedDPs.put(8*MINUTE, 2d);

		expectedMetric.setDatapoints(expectedDPs);
		expectedMetrics.add(expectedMetric);
		List<String> constants = Arrays.asList("1m","TRUE","TRUE");
		QueryContext queryContext = _getQueryContext(startTime, endTime);
		List<Metric> actualMetrics = rateTransform.transform(queryContext, metrics,constants);
		assertEquals(expectedMetrics.get(0).getDatapoints(), actualMetrics.get(0).getDatapoints());
	}

	@Test
	public void testRateWithNoConstants() {
		long startTime=1*MINUTE, endTime=8*MINUTE;
		RateTransform rateTransform = new RateTransform();
		List<Metric> metrics = new ArrayList<>();
		Metric actualMetric= new Metric("testScope", "testMetric");
		Map<Long, Double> actualDPs = new HashMap<>();
		actualDPs.put(1*MINUTE, 2d);
		actualDPs.put(2*MINUTE, 3d);
		actualDPs.put(5*MINUTE, 0d);
		actualDPs.put(8*MINUTE, 6d);

		actualMetric.setDatapoints(actualDPs);
		metrics.add(actualMetric);
		List<Metric> expectedMetrics = new ArrayList<>();
		Metric expectedMetric= new Metric("testScope", "testMetric");
		Map<Long, Double> expectedDPs = new HashMap<>();

		expectedDPs.put(2*MINUTE, 1d);
		expectedDPs.put(6*MINUTE, 2d);
		expectedDPs.put(7*MINUTE, 2d);
		expectedDPs.put(8*MINUTE, 2d);

		expectedMetric.setDatapoints(expectedDPs);
		expectedMetrics.add(expectedMetric);
		QueryContext queryContext = _getQueryContext(startTime, endTime);
		List<Metric> actualMetrics = rateTransform.transform(queryContext, metrics);
		assertEquals(expectedMetrics.get(0).getDatapoints(), actualMetrics.get(0).getDatapoints());
	}

	private QueryContext _getQueryContext(long startTimestamp, long endTimestamp) {
		QueryContext result = new QueryContext();
		TSDBQueryExpression expr = new TSDBQueryExpression();
		expr.setStartTimestamp(startTimestamp);
		expr.setEndTimestamp(endTimestamp); 
		result.setChildExpressions(Arrays.asList(expr)); 
		return result;
	}
}

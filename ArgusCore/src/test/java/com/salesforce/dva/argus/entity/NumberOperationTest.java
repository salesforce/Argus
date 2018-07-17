package com.salesforce.dva.argus.entity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class NumberOperationTest {

	private static final Number long1 = new Long(14);
	private static final Number long2 = (long) 1;
	private static final Number double1 = new Double(18);
	private static final Number double2 = 4.4;
	private static final Number int1 = 12;
	private static final Number int2 = -4;
	
	
	@Test
	public void testAdd() {
		Number sum1 = NumberOperations.add(long1, long2);
		assertEquals(sum1, new Long(15));
		Number sum2 = NumberOperations.add(double1, double2);
		assertEquals(sum2, new Double(22.4));
		Number sum3 = NumberOperations.add(long1, double1);
		assertEquals(sum3, new Double(32));
		Number sum4 = NumberOperations.add(long1, int2);
		assertEquals(sum4, new Long(10));
		Number sum5 = NumberOperations.add(double1, int1);
		assertEquals(sum5, new Double(30));
	}
	
	@Test
	public void testSubtract() {
		Number min1 = NumberOperations.subtract(long1, long2);
		assertEquals(min1, new Long(13));
		Number min2 = NumberOperations.subtract(double1, double2);
		assertEquals(min2, new Double(13.6));
		Number min3 = NumberOperations.subtract(long1, double1);
		assertEquals(min3, new Double(-4));
		Number min4 = NumberOperations.subtract(long1, int2);
		assertEquals(min4, new Long(18));
		Number min5 = NumberOperations.subtract(double1, int1);
		assertEquals(min5, new Double(6));
	}
	
	@Test
	public void testMultiply() {
		Number mult1 = NumberOperations.multiply(long1, long2);
		assertEquals(mult1, new Long(14));
		Number mult2 = NumberOperations.multiply(double1, double2);
		assertEquals(mult2, new Double(18 * 4.4));
		Number mult3 = NumberOperations.multiply(long1, double1);
		assertEquals(mult3, new Double(14 * 18));
		Number mult4 = NumberOperations.multiply(long1, int2);
		assertEquals(mult4, new Long(14*-4));
		Number mult5 = NumberOperations.multiply(double1, int1);
		assertEquals(mult5, new Double(18*12));
	}
	
	@Test
	public void testDivide() {
		Number div1 = NumberOperations.divide(long1, long2);
		assertEquals(div1, new Double(14));
		Number div2 = NumberOperations.divide(double1, double2);
		assertEquals(div2, new Double(18/4.4));
		Number div3 = NumberOperations.divide(long1, double1);
		assertEquals(div3, new Double(14.0/18));
		Number div4 = NumberOperations.divide(long1, int2);
		assertEquals(div4, new Double(14.0/(-4)));
		Number div5 = NumberOperations.divide(double1, int1);
		assertEquals(div5, new Double(18.0/12));
	}
	
	@Test
	public void testGetMax() {
		Number max1 = NumberOperations.getMax(long1, long2);
		assertEquals(max1, new Long(14));
		Number max2 = NumberOperations.getMax(double1, double2);
		assertEquals(max2, new Double(18));
		Number max3 = NumberOperations.getMax(long1, double1);
		assertEquals(max3, new Double(18));
		Number max4 = NumberOperations.getMax(long1, int2);
		assertEquals(max4, new Long(14));
		Number max5 = NumberOperations.getMax(double1, int1);
		assertEquals(max5, new Double(18.0));
	}
	
	@Test
	public void testGetMin() {
		Number min1 = NumberOperations.getMin(long1, long2);
		assertEquals(min1, new Long(1));
		Number min2 = NumberOperations.getMin(double1, double2);
		assertEquals(min2, new Double(4.4));
		Number min3 = NumberOperations.getMin(long1, double1);
		assertEquals(min3, new Double(14));
		Number min4 = NumberOperations.getMin(long1, int2);
		assertEquals(min4, new Long(-4));
		Number min5 = NumberOperations.getMin(double1, int1);
		assertEquals(min5, new Double(12));
	}

	@Test
	public void testIsGreaterThan() {
		boolean g1 = NumberOperations.isGreaterThan(long1, long2);
		assertTrue(g1);
		boolean g2 = NumberOperations.isGreaterThan(double1, double2);
		assertTrue(g2);
		boolean g3 = NumberOperations.isGreaterThan(long1, double1);
		assertTrue(!g3);
		boolean g4 = NumberOperations.isGreaterThan(long1, int2);
		assertTrue(g4);
		boolean g5 = NumberOperations.isGreaterThan(double1, int1);
		assertTrue(g5);
		boolean g6 = NumberOperations.isGreaterThan(long1, 14.0);
		assertTrue(!g6);
		boolean g7 = NumberOperations.isGreaterThan(double1, 18);
		assertTrue(!g7);
	}
	
	@Test
	public void testIsGreaterThanOrEqualTo() {
		boolean g1 = NumberOperations.isGreaterThanOrEqualTo(long1, long2);
		assertTrue(g1);
		boolean g2 = NumberOperations.isGreaterThanOrEqualTo(double1, double2);
		assertTrue(g2);
		boolean g3 = NumberOperations.isGreaterThanOrEqualTo(long1, double1);
		assertTrue(!g3);
		boolean g4 = NumberOperations.isGreaterThanOrEqualTo(long1, int2);
		assertTrue(g4);
		boolean g5 = NumberOperations.isGreaterThanOrEqualTo(double1, int1);
		assertTrue(g5);
		boolean g6 = NumberOperations.isGreaterThanOrEqualTo(long1, 14.0);
		assertTrue(g6);
		boolean g7 = NumberOperations.isGreaterThanOrEqualTo(double1, 18);
		assertTrue(g7);
	}
	
	@Test
	public void testIsLessThan() {
		boolean g1 = NumberOperations.isLessThan(long1, long2);
		assertTrue(!g1);
		boolean g2 = NumberOperations.isLessThan(double1, double2);
		assertTrue(!g2);
		boolean g3 = NumberOperations.isLessThan(long1, double1);
		assertTrue(g3);
		boolean g4 = NumberOperations.isLessThan(long1, int2);
		assertTrue(!g4);
		boolean g5 = NumberOperations.isLessThan(double1, int1);
		assertTrue(!g5);
		boolean g6 = NumberOperations.isLessThan(long1, 14.0);
		assertTrue(!g6);
		boolean g7 = NumberOperations.isLessThan(double1, 18);
		assertTrue(!g7);
	}
	
	@Test
	public void testIsLessThanOrEqualTo() {
		boolean g1 = NumberOperations.isLessThanOrEqualTo(long1, long2);
		assertTrue(!g1);
		boolean g2 = NumberOperations.isLessThanOrEqualTo(double1, double2);
		assertTrue(!g2);
		boolean g3 = NumberOperations.isLessThanOrEqualTo(long1, double1);
		assertTrue(g3);
		boolean g4 = NumberOperations.isLessThanOrEqualTo(long1, int2);
		assertTrue(!g4);
		boolean g5 = NumberOperations.isLessThanOrEqualTo(double1, int1);
		assertTrue(!g5);
		boolean g6 = NumberOperations.isLessThanOrEqualTo(long1, 14.0);
		assertTrue(g6);
		boolean g7 = NumberOperations.isLessThanOrEqualTo(double1, 18);
		assertTrue(g7);
	}
	
	@Test
	public void testIsEqualTo() {
		boolean g1 = NumberOperations.isEqualTo(long1, long2);
		assertTrue(!g1);
		boolean g2 = NumberOperations.isEqualTo(double1, double2);
		assertTrue(!g2);
		boolean g3 = NumberOperations.isEqualTo(long1, double1);
		assertTrue(!g3);
		boolean g4 = NumberOperations.isEqualTo(long1, int2);
		assertTrue(!g4);
		boolean g5 = NumberOperations.isEqualTo(double1, int1);
		assertTrue(!g5);
		boolean g6 = NumberOperations.isEqualTo(long1, 14.0);
		assertTrue(g6);
		boolean g7 = NumberOperations.isEqualTo(double1, 18);
		assertTrue(g7);
	}
	
	@Test
	public void testLog() {
		Number l1 = NumberOperations.log(new Long(1));
		assertTrue(NumberOperations.isEqualTo(l1, 0));
		Number l2 = NumberOperations.log(Math.E);
		assertTrue(NumberOperations.isEqualTo(l2, 1));
		Number l3 = NumberOperations.log(1);
		assertTrue(NumberOperations.isEqualTo(l3, 0));		
	}
	
	@Test
	public void testGetAbsValue() {
		Number abs1 = NumberOperations.getAbsValue(long1);
		assertEquals(abs1, new Long(14));
		Number abs2 = NumberOperations.getAbsValue(double2);
		assertEquals(abs2, new Double(4.4));
		Number abs3 = NumberOperations.getAbsValue(int2);
		assertEquals(abs3, new Long(4));
	}
	
	@Test
	public void testCompare() {
		Number c1 = NumberOperations.compare(long1, long2);
		assertEquals(c1, 1);
		Number c2 = NumberOperations.compare(double1, double2);
		assertEquals(c2, 1);
		Number c3 = NumberOperations.compare(long1, double1);
		assertEquals(c3, -1);
		Number c4 = NumberOperations.compare(long1, int2);
		assertEquals(c4, 1);
		Number c5 = NumberOperations.compare(double1, int1);
		assertEquals(c5, 1);
		Number c6 = NumberOperations.compare(long1, 14.0);
		assertEquals(c6, 0);
		Number c7 = NumberOperations.compare(double1, 18);
		assertEquals(c7, 0);
	}
	
	@Test
	public void testFloor() {
		Number f1 = NumberOperations.floor(long1);
		assertEquals(f1, 14);
		Number f2 = NumberOperations.floor(double2);
		assertEquals(f2, 4);
		Number f3 = NumberOperations.floor(int2);
		assertEquals(f3, -4);
	}
	
	@Test
	public void testSquare() {
		Number sq1 = NumberOperations.square(long1);
		assertTrue(NumberOperations.isLessThan(NumberOperations.subtract(sq1, 196), 0.0001));
		Number sq2 = NumberOperations.square(double2);
		assertTrue(NumberOperations.isLessThan(NumberOperations.subtract(sq2, 19.36), 0.0001));
		Number sq3 = NumberOperations.square(int2);
		assertTrue(NumberOperations.isLessThan(NumberOperations.subtract(sq3, 16), 0.0001));
	}
	
	@Test
	public void testExp() {
		Number e1 = NumberOperations.exp(long2);
		assertEquals(e1, Math.E);
		Number e2 = NumberOperations.exp(-1.0);
		assertEquals(e2, 1/Math.E);
		Number e3 = NumberOperations.exp(0);
		assertEquals(e3, 1.0);
	}
	
	@Test
	public void testSquareRoot() {
		Number sq1 = NumberOperations.sqrt(new Long(25));
		assertEquals(sq1, 5.0);
		Number sq2 = NumberOperations.sqrt(0.25);
		assertEquals(sq2, 0.5);
		Number sq3 = NumberOperations.sqrt(1);
		assertEquals(sq3, 1.0);
		Number sq4 = NumberOperations.sqrt(int2);
		assertEquals(sq4, Double.NaN);
	}
	
	@Test
	public void testIsNaN() {
		boolean nan1 = NumberOperations.isNaN(long1);
		assertTrue(!nan1);
		boolean nan2 = NumberOperations.isNaN(Double.NaN);
		assertTrue(nan2);
		boolean nan3 = NumberOperations.isNaN(-1);
		assertTrue(!nan3);
		boolean nan4 = NumberOperations.isNaN(Math.sqrt(-1));
		assertTrue(nan4);
	}
	
	@Test
	public void testMin() {
		List<Number> vals = new ArrayList<>();
		vals.add(long1);
		vals.add(long2);
		vals.add(double1);
		vals.add(double2);
		vals.add(int1);
		vals.add(int2);
		
		Number min = NumberOperations.min(vals);
		assertEquals(min, -4);
	}
	
	@Test
	public void testMax() {
		List<Number> vals = new ArrayList<>();
		vals.add(long1);
		vals.add(long2);
		vals.add(double1);
		vals.add(double2);
		vals.add(int1);
		vals.add(int2);
		
		Number min = NumberOperations.max(vals);
		assertEquals(min, 18.0);
	}
	
	@Test
	public void testSum() {
		List<Number> vals = new ArrayList<>();
		vals.add(long1);
		vals.add(long2);
		vals.add(double1);
		vals.add(double2);
		vals.add(int1);
		vals.add(int2);
		
		Number sum = NumberOperations.sum(vals);
		assertEquals(sum, 45.4);
		
		List<Number> empty = new ArrayList<>();
		assertEquals(NumberOperations.sum(empty), 0);
	}
	
	@Test
	public void testFindMedian() {
		Number[] vals = new Number[5];
		vals[0] = long1;
		vals[1] = long2;
		vals[2] = double1;
		vals[3] = double2;
		vals[4] = int2;
		
		Number med = NumberOperations.findMedian(vals);
		assertEquals(double2, med);
	}
	
	@Test
	public void testFindMedianSingleton() {
		Number[] vals = new Number[1];
		vals[0] = int2;
		
		Number med = NumberOperations.findMedian(vals);
		assertEquals(int2, med);
	}
	
	@Test
	public void testFindMedianInterpolate() {
		Number[] vals = new Number[2];
		vals[0] = long1;
		vals[1] = long2;
		
		Number med = NumberOperations.findMedian(vals);
		assertEquals(new Double(7.5), med);
	}
	
	@Test
	public void testGetListAsDoublesAllDoubles() {
		List<Number> vals = new ArrayList<>();
		vals.add(double1);
		vals.add(double2);
		
		List<Double> expected = Arrays.asList((Double) double1, (Double) double2);
		List<Double> actual = NumberOperations.getListAsDoubles(vals);
		assertEquals(expected, actual);
	}
	
	@Test (expected = IllegalArgumentException.class)
	public void testGetListAsDoublesHasLong() {
		List<Number> vals = new ArrayList<>();
		vals.add(double1);
		vals.add(long1);
		
		NumberOperations.getListAsDoubles(vals);
	}
	
	@Test (expected = IllegalArgumentException.class)
	public void testGetListAsDoublesHasInt() {
		List<Number> vals = Arrays.asList(double1, double2, int2);
		NumberOperations.getListAsDoubles(vals);
	}
	
	@Test
	public void testGetMapAsDoublesAllDoubles() {
		Map<Long, Number> map = new HashMap<>();
		map.put(1000L, double2);
		map.put(2000L, double1);
		
		Map<Long, Double> expected = new HashMap<>();
		expected.put(1000L, (Double) double2);
		expected.put(2000L, (Double) double1);
		
		Map<Long, Double> actual = NumberOperations.getMapAsDoubles(map);
		assertEquals(expected, actual);
	}
	
	@Test (expected = IllegalArgumentException.class)
	public void testGetMapAsDoublesHasLong() {
		Map<Long, Number> map = new HashMap<>();
		map.put(1000L, double2);
		map.put(2000L, long2);
		map.put(3000L, double1);
		
		NumberOperations.getMapAsDoubles(map);
	}
	
	@Test (expected = IllegalArgumentException.class)
	public void testGetMapAsDoublesHasInt() {
		Map<Long, Number> map = new HashMap<>();
		map.put(1000L, int2);
		
		NumberOperations.getMapAsDoubles(map);
	}
	
	@Test
	public void testGetNumberAsBigDecimal() {
		BigDecimal res1 = NumberOperations.bd(long1);
		BigDecimal res2 = NumberOperations.bd(double2);
		BigDecimal res3 = NumberOperations.bd(int2);
		BigDecimal res4 = NumberOperations.bd(null);
		
		assertEquals(new BigDecimal(14), res1);
		assertEquals(new BigDecimal(4.4), res2);
		assertEquals(new BigDecimal(-4), res3);
		assertEquals(null, res4);
	}
}

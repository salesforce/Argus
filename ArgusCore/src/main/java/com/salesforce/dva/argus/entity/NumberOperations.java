package com.salesforce.dva.argus.entity;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.dva.argus.system.SystemAssert;

/**
 * Defines mathematical operations between two Number data objects.
 * 
 * @author a.chambers
 *
 */
public class NumberOperations {
	
	protected final static Logger _logger = LoggerFactory.getLogger(NumberOperations.class);
	
	private static Number performOp(Number n1, Number n2, OperationNumeric oper) {
		ValueType n1Type = ValueType.value(n1);
		ValueType n2Type = ValueType.value(n2);
		
		switch (n1Type) {
			case INT: // do the same thing as Long
			case LONG:
				Long l1 = n1.longValue();
				switch (n2Type) {
					case INT:
					case LONG:
						return oper.op(l1, n2.longValue());
					case DOUBLE:
						return oper.op(l1, n2.doubleValue());
					default:
						throw new IllegalArgumentException();
				}
			case DOUBLE:
				Double d1 = n1.doubleValue();
				switch (n2Type) {
					case INT:
					case LONG:
						return oper.op(d1, n2.longValue());
					case DOUBLE:
						return oper.op(d1, n2.doubleValue());
					default:
						throw new IllegalArgumentException();
				}
			default:
				throw new IllegalArgumentException();
		}
	}
	
	private static boolean performOp(Number n1, Number n2, OperationBoolean oper) {
		ValueType n1Type = ValueType.value(n1);
		ValueType n2Type = ValueType.value(n2);
		
		switch (n1Type) {
			case INT:
			case LONG:
				Long l1 = n1.longValue();
				switch (n2Type) {
					case INT:
					case LONG:
						return oper.op(l1, n2.longValue());
					case DOUBLE:
						return oper.op(l1, n2.doubleValue());
					default:
						throw new IllegalArgumentException();
				}
			case DOUBLE:
				Double d1 = n1.doubleValue();
				switch (n2Type) {
					case INT:
					case LONG:
						return oper.op(d1, n2.longValue());
					case DOUBLE:
						return oper.op(d1, n2.doubleValue());
					default:
						throw new IllegalArgumentException();
				}
			default:
				throw new IllegalArgumentException();
		}
	}
	
	private static Number performOp(Number n, OperationUnaryNumeric oper) {
		switch (ValueType.value(n)) {
			case INT:
			case LONG:
				return oper.op(n.longValue());
			case DOUBLE:
				return oper.op(n.doubleValue());
			default:
				throw new IllegalArgumentException();
		}
	}
	
	/**
	 * @param n1 the first number to sum
	 * @param n2 the second number to sum
	 * @return n1 + n2
	 */
	public static Number add(Number n1, Number n2) {
		OperationNumeric op = new OperationNumeric() {
			@Override
			public Number op(Long l1, Long l2) {
				return l1 + l2;
			}

			@Override
			public Number op(Long l, Double d) {
				return l + d;
			}

			@Override
			public Number op(Double d, Long l) {
				return d + l;
			}

			@Override
			public Number op(Double d1, Double d2) {
				return d1 + d2;
			}
		};
		
		return performOp(n1, n2, op);
	}
	
	/**
	 * @param n1 the number to subtract from
	 * @param n2 the number to subtract
	 * @return n1 - n2
	 */
	public static Number subtract(Number n1, Number n2) {
		OperationNumeric op = new OperationNumeric() {
			@Override
			public Number op(Long l1, Long l2) {
				return l1 - l2;
			}

			@Override
			public Number op(Long l, Double d) {
				return l - d;
			}

			@Override
			public Number op(Double d, Long l) {
				return d - l;
			}

			@Override
			public Number op(Double d1, Double d2) {
				return d1 - d2;
			}
		};
		
		return performOp(n1, n2, op);
	}
	
	/**
	 * @param n1 the first number to multiply
	 * @param n2 the second number to multiply
	 * @return n1 * n2
	 */
	public static Number multiply(Number n1, Number n2) {
		OperationNumeric op = new OperationNumeric() {
			@Override
			public Number op(Long l1, Long l2) {
				return l1 * l2;
			}

			@Override
			public Number op(Long l, Double d) {
				return l * d;
			}

			@Override
			public Number op(Double d, Long l) {
				return d * l;
			}

			@Override
			public Number op(Double d1, Double d2) {
				return d1 * d2;
			}
		};
		
		return performOp(n1, n2, op);
	}
	
	/**
	 * @param n1 the number to divide
	 * @param n2 the number to divide by
	 * @return n1 / n2.
	 */
	public static Number divide(Number n1, Number n2) {
		OperationNumeric op = new OperationNumeric() {
			@Override
			public Number op(Long l1, Long l2) {
				return l1 / (double) l2;
			}

			@Override
			public Number op(Long l, Double d) {
				return l / d;
			}

			@Override
			public Number op(Double d, Long l) {
				return d / l;
			}

			@Override
			public Number op(Double d1, Double d2) {
				return d1 / d2;
			}
		};
		
		return performOp(n1, n2, op);
	}
	
	/**
	 * @param n1 the first number
	 * @param n2 the second number
	 * @return the max of n1 and n2
	 */
	public static Number getMax(Number n1, Number n2) {
		OperationNumeric op = new OperationNumeric() {
			@Override
			public Number op(Long l1, Long l2) {
				return Math.max(l1, l2);
			}

			@Override
			public Number op(Long l, Double d) {
				return Math.max(l, d);
			}

			@Override
			public Number op(Double d, Long l) {
				return Math.max(d, l);
			}

			@Override
			public Number op(Double d1, Double d2) {
				return Math.max(d1, d2);
			}
		};
		
		return performOp(n1, n2, op);
	}
	
	/**
	 * @param n1 the first number
	 * @param n2 the second number
	 * @return the min of n1 and n2
	 */
	public static Number getMin(Number n1, Number n2) {
		OperationNumeric op = new OperationNumeric() {
			@Override
			public Number op(Long l1, Long l2) {
				return Math.min(l1, l2);
			}

			@Override
			public Number op(Long l, Double d) {
				return Math.min(l, d);
			}

			@Override
			public Number op(Double d, Long l) {
				return Math.min(d, l);
			}

			@Override
			public Number op(Double d1, Double d2) {
				return Math.min(d1, d2);
			}
		};
		
		return performOp(n1, n2, op);
	}
	
	/**
	 * @param n1 the first number
	 * @param n2 the second number
	 * @return true iff n1 > n2
	 */
	public static boolean isGreaterThan(Number n1, Number n2) {
		OperationBoolean op = new OperationBoolean() {
			@Override
			public boolean op(Long l1, Long l2) {
				return l1 > l2;
			}

			@Override
			public boolean op(Long l, Double d) {
				return l > d;
			}

			@Override
			public boolean op(Double d, Long l) {
				return d > l;
			}

			@Override
			public boolean op(Double d1, Double d2) {
				return d1 > d2;
			}
		};
		
		return performOp(n1, n2, op);
	}
	
	/**
	 * @param n1 the first number
	 * @param n2 the second number
	 * @return true iff n1 >= n2
	 */
	public static boolean isGreaterThanOrEqualTo(Number n1, Number n2) {
		OperationBoolean op = new OperationBoolean() {
			@Override
			public boolean op(Long l1, Long l2) {
				return l1 >= l2;
			}

			@Override
			public boolean op(Long l, Double d) {
				return l >= d;
			}

			@Override
			public boolean op(Double d, Long l) {
				return d >= l;
			}

			@Override
			public boolean op(Double d1, Double d2) {
				return d1 >= d2;
			}
		};
		
		return performOp(n1, n2, op);
	}
	
	/**
	 * @param n1 the first number
	 * @param n2 the second number
	 * @return true iff n1 < n2
	 */
	public static boolean isLessThan(Number n1, Number n2) {
		OperationBoolean op = new OperationBoolean() {
			@Override
			public boolean op(Long l1, Long l2) {
				return l1 < l2;
			}

			@Override
			public boolean op(Long l, Double d) {
				return l < d;
			}

			@Override
			public boolean op(Double d, Long l) {
				return d < l;
			}

			@Override
			public boolean op(Double d1, Double d2) {
				return d1 < d2;
			}
		};
		
		return performOp(n1, n2, op);
	}
	
	/**
	 * @param n1 the first number
	 * @param n2 the second number
	 * @return true iff n1 <= n2
	 */
	public static boolean isLessThanOrEqualTo(Number n1, Number n2) {
		OperationBoolean op = new OperationBoolean() {
			@Override
			public boolean op(Long l1, Long l2) {
				return l1 <= l2;
			}

			@Override
			public boolean op(Long l, Double d) {
				return l <= d;
			}

			@Override
			public boolean op(Double d, Long l) {
				return d <= l;
			}

			@Override
			public boolean op(Double d1, Double d2) {
				return d1 <= d2;
			}
		};
		
		return performOp(n1, n2, op);
	}
	
	/**
	 * @param n1 the first number
	 * @param n2 the second number
	 * @return true iff n1 and n2 represent the same value, though not necessarily the same type.
	 * 			Considers long/int values as doubles if compared to a double.
	 */
	public static boolean isEqualTo(Number n1, Number n2) {
		OperationBoolean op = new OperationBoolean() {
			@Override
			public boolean op(Long l1, Long l2) {
				return l1.longValue() == l2.longValue();
			}

			@Override
			public boolean op(Long l, Double d) {
				return l.doubleValue() == d.doubleValue();
			}

			@Override
			public boolean op(Double d, Long l) {
				return d.doubleValue() == l.doubleValue();
			}

			@Override
			public boolean op(Double d1, Double d2) {
				return d1.doubleValue() == d2.doubleValue();
			}
		};
		
		return performOp(n1, n2, op);
	}
	
	/**
	 * @param n the number to take the log of
	 * @return the natural log of n
	 */
	public static Number log(Number n) {
		OperationUnaryNumeric op = new OperationUnaryNumeric() {

			@Override
			public Number op(Long l) {
				return Math.log(l);
			}

			@Override
			public Number op(Double d) {
				return Math.log(d);
			}
			
		};
		
		return performOp(n, op);
	}
	
	/**
	 * @param n the number to take the absolute value of
	 * @return |n|
	 */
	public static Number getAbsValue(Number n) {
		OperationUnaryNumeric op = new OperationUnaryNumeric() {

			@Override
			public Number op(Long l) {
				return Math.abs(l);
			}

			@Override
			public Number op(Double d) {
				return Math.abs(d);
			}
			
		};
		
		return performOp(n, op);
	}
	
	/**
	 * @param n1 the first number
	 * @param n2 the second number
	 * @return 1 if n1 > n2; 0 if n1 = n2; -1 if n1 < n2.
	 */
	public static int compare(Number n1, Number n2) {
		if (isGreaterThan(n1, n2)) {
			return 1;
		} else if (isLessThan(n1, n2)) {
			return -1;
		} else {
			return 0;
		}
	}
	
	/**
	 * @param n the number to take the floor of
	 * @return the largest int <= n
	 */
	public static int floor(Number n) {
		switch (ValueType.value(n)) {
			case INT:
				return (int) n;
			case LONG:
				return (int) Math.floor(n.longValue());
			case DOUBLE:
				return (int) Math.floor(n.doubleValue());
			default:
				throw new IllegalArgumentException();
		}
	}
	
	/**
	 * @param n the number to square
	 * @return n * n
	 */
	public static Number square(Number n) {
		switch (ValueType.value(n)) {
			case INT:
				return n.intValue() * n.intValue();
			case LONG:
				return n.longValue() * n.longValue();
			case DOUBLE:
				return n.doubleValue() * n.doubleValue();
			default:
				throw new IllegalArgumentException();
		}
	}
	
	/**
	 * @param n the exponent to use
	 * @return e^n
	 */
	public static Number exp(Number n) {
		switch (ValueType.value(n)) {
			case INT:
				return Math.exp(n.intValue());
			case LONG:
				return Math.exp(n.longValue());
			case DOUBLE:
				return Math.exp(n.doubleValue());
			default:
				throw new IllegalArgumentException();
		}
	}
	
	/**
	 * @param n the number to take the root of
	 * @return the positive square root of n. Returns NaN if n < 0.
	 */
	public static Number sqrt(Number n) {
		switch (ValueType.value(n)) {
			case INT:
				return Math.sqrt(n.intValue());
			case LONG:
				return Math.sqrt(n.longValue());
			case DOUBLE:
				return Math.sqrt(n.doubleValue());
			default:
				throw new IllegalArgumentException();
		}
	}
	
	/**
	 * @param n the Number object to test
	 * @return true iff n is not a number (e.g. Double.NaN, imaginary numbers)
	 */
	public static boolean isNaN(Number n) {
		switch (ValueType.value(n)) {
			case INT:
				return false;
			case LONG:
				return false;
			case DOUBLE:
				return Double.isNaN(n.doubleValue());
			default:
				throw new IllegalArgumentException();
		}
	}
	
	/**
	 * @param vals the list of values to find the minimum of. Must be nonempty and contain no null elements.
	 * @return the minimum element in vals.
	 */
	public static Number min(List<Number> vals) {
		return Collections.min(vals, new Comparator<Number>() {

			@Override
			public int compare(Number o1, Number o2) {
				return NumberOperations.compare(o1, o2);
			}
			
		});
	}
	
	/**
	 * @param vals the list of values to find the maximum of. Must be nonempty and contain no null elements.
	 * @return the maximum element in vals.
	 */
	public static Number max(List<Number> vals) {
		return Collections.max(vals, new Comparator<Number>() {

			@Override
			public int compare(Number o1, Number o2) {
				return NumberOperations.compare(o1, o2);
			}
			
		});
	}
	
	/**
	 * @param vals the numbers to sum. Must contain no null elements.
	 * @return the sum of all elements in vals.
	 */
	public static Number sum(List<Number> vals) {
		return vals.stream()
				.reduce(0, (a, b) -> add(a, b));
	}
	
	/**
	 * @param vals the list of numbers
	 * @return the list of doubles contained in vals. If one element of vals is null, will preserve it as null.
	 * @throws IllegalArgumentException if one of the numbers in vals is not a double
	 */
	public static List<Double> getListAsDoubles(List<Number> vals) throws IllegalArgumentException {
		List<Double> doubles = new ArrayList<Double>();
		for (Number val : vals) {
			if (val == null) {
				doubles.add(null);
				continue;
			}
			if (ValueType.value(val) != ValueType.DOUBLE) {
				throw new IllegalArgumentException();
			}
			doubles.add(val.doubleValue());
		}
		return doubles;
	}
	
	/**
	 * @param map a map from long keys to number values
	 * @return a map from long keys to double values, where the value is the double contained in map
	 * @throws IllegalArgumentException if one of the values in map is not a double. If one of the values in map is null, will preserve it as null.
	 */
	public static Map<Long, Double> getMapAsDoubles(Map<Long, Number> map) throws IllegalArgumentException {
		Map<Long, Double> doubles = new HashMap<>();
		for (Map.Entry<Long, Number> entry : map.entrySet()) {
			if (entry.getValue() == null) {
				doubles.put(entry.getKey(), null);
				continue;
			}
			if (ValueType.value(entry.getValue()) != ValueType.DOUBLE) {
				throw new IllegalArgumentException();
			}
			doubles.put(entry.getKey(), entry.getValue().doubleValue());
		}
		return doubles;
	}
	
	/**
	 * Computes the mean of the data set.
	 * 
	 * @param vals the data values
	 * @return the mean of vals
	 */
	public static Number mean(Number[] vals) {
		Number sum = 0;
		
		for (Number val : vals) {
			sum = add(sum, val);
		}
		return divide(sum, vals.length);
	}
	
	/**
	 * Determines the median of a set of values.
	 * 
	 * @param vals the set of values
	 * @return the median value of vals. Uses interpolation if an even number of values.
	 */
	public static Number findMedian(Number[] vals) {
		SystemAssert.requireArgument(vals != null && vals.length > 0, "vals cannot be null or empty");
		double middle = 0.5 * (vals.length + 1);
		if (vals.length == 1) {
			return vals[0];
		}
		
		Arrays.sort(vals, new Comparator<Number>() {

			@Override
			public int compare(Number o1, Number o2) {
				return NumberOperations.compare(o1, o2);
			}
			
			
		});
		
		int lowIndex = (int) Math.floor(middle);
		double diff = middle - lowIndex;
		Number low = vals[lowIndex - 1]; // account for 0-based indexing
		int highIndex = lowIndex + 1 <= vals.length ? lowIndex + 1 : lowIndex;
		Number high = vals[highIndex - 1];

		return add(low, multiply(diff, subtract(high, low)));
	}
	
	/**
	 * Converts a number into its big decimal format.
	 * 
	 * @param n the number to convert
	 * @return the big decimal with the same value as n
	 */
	public static BigDecimal bd(Number n) {
		if (n == null) {
			return null;
		}
		switch (ValueType.value(n)) {
			case INT:
			case LONG:
				return new BigDecimal(n.longValue());
			case DOUBLE:
				return new BigDecimal(n.doubleValue());
			default:
				throw new IllegalStateException();
		}
	}
	
	/**
	 * Defines the type of a Number object as either Long, Double, or Int.
	 * 
	 * @author a.chambers
	 *
	 */
	public enum ValueType {
		
		LONG,
		INT,
		DOUBLE;
		
		/**
		 * Returns the type to use when representing a given number.
		 * 
		 * @param n The number to represent
		 * @return the type to use, either long, int, or double, to use when performing arithmetic with n.
		 */
		public static ValueType value(Number n) {
			if (Long.class.isInstance(n)) {
				return LONG;
			} else if (Double.class.isInstance(n)) {
				return DOUBLE;
			} else if (Integer.class.isInstance(n)) {
				return INT;
			} else if (n == null) {
				return LONG;
			} else {
				String strValue = n.toString();
				try {
					Long.parseLong(strValue);
					_logger.debug("Converting " + n + " of type " + n.getClass() + " to a long. May result in a loss of precision or an overflow error.");
					return LONG;
				} catch (NumberFormatException nfe) {
					try {
						Double.parseDouble(strValue);
						_logger.debug("Converting " + n + " of type " + n.getClass() + " to a double. May result in a loss of precision or an overflow error.");
						return DOUBLE;
					} catch (NumberFormatException nfe2) {
						_logger.error("Could not convert " + n + " of type " + n.getClass() + " to a long or a double.");
						throw new IllegalArgumentException();
					}
				}
			}
		}
	}
		
	private interface OperationNumeric {
		Number op(Long l1, Long l2);
		Number op(Long l, Double d);
		Number op(Double d, Long l);
		Number op(Double d1, Double d2);
	}
	
	private interface OperationBoolean {
		boolean op(Long l1, Long l2);
		boolean op(Long l, Double d);
		boolean op(Double d, Long l);
		boolean op(Double d1, Double d2);
	}
	
	private interface OperationUnaryNumeric {
		Number op(Long l);
		Number op(Double d);
	}
}

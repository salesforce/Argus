package com.salesforce.dva.argus.service.metric.transform;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.slf4j.LoggerFactory;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.tsdb.MetricQuery.Aggregator;
import com.salesforce.dva.argus.util.QueryContext;

/**
 * Interpolates multiple time series that will be used for aggregation.
 * Fills in the interpolated value after a time series first datapoint is found until the last datapoint.
 *
 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
 */
public class InterpolateTransform implements Transform {

	/** Internal buffer of current and next timestamps from each time series that will be used for interpolation */
	private long[] timestamps;
	/** Internal buffer of current and next values from each time series that will be used for interpolation */
	private Double[] values;
	/** Array of Iterators for each time series */
	private Iterator<Entry<Long, Double>>[] iterators;
	/** The index in current time series being used. This timestamp, will then be used to interpolate other time series. */
	int current = 0;
	/** The index of time series to interpolate. */
	private int indexToInterpolate;
	private static final long MARK_END_TIME_SERIES  = Long.MAX_VALUE;

	public enum InterpolationType {
		LININT,   /* linear interpolation */
		ZIMSUM   /* 0 when a data point is missing */
	}

	@Override
	public List<Metric> transform(QueryContext context, List<Metric> metrics) {
		throw new UnsupportedOperationException("Interpolation Transform needs an interpolation type to be specified");
	}

	@SuppressWarnings("unchecked")
	@Override
	public List<Metric> transform(QueryContext queryContext, List<Metric> metrics, List<String> constants) {

		List<Metric> resultMetric = new ArrayList<Metric>();

		if (metrics.isEmpty()) {
			return resultMetric;
		}		

		Metric result = new Metric(metrics.get(0).getScope(), metrics.get(0).getMetric());
		result.setNamespace(metrics.get(0).getNamespace());
		result.setDisplayName(metrics.get(0).getDisplayName());
		result.setTags(metrics.get(0).getTags());

		Map<Long, Double> resultDatapoints = new HashMap<>();
		Aggregator aggregator = Aggregator.valueOf(constants.get(0));

		int size = metrics.size();
		LoggerFactory.getLogger(getClass()).info(("Num time series # " + size));

		timestamps = new long[size * 2];
		values = new Double[size * 2];
		iterators = new Iterator[size];

		/*
		 * Add data points to internal buffer so we can interpolate
		 */
		for (int i = 0; i < size; i++) {
			iterators[i] = metrics.get(i).getDatapoints().entrySet().iterator();
			if (!iterators[i].hasNext()) {
				markEndTimeSeries(i);
				continue;
			}

			Entry<Long, Double> datapoint = (Entry<Long, Double>) iterators[i].next();
			putDataPoint(size + i, datapoint);
		}

		switch(aggregator){
		case NONE:
			break;
		case ZIMSUM:
			interpolateSum(resultDatapoints, InterpolationType.ZIMSUM);
			break;
		case SUM:
			interpolateSum(resultDatapoints, InterpolationType.LININT);
			break;			
		case MAX:
			interpolateMax(resultDatapoints, InterpolationType.LININT);
			break;
		case MIN:
			interpolateMin(resultDatapoints, InterpolationType.LININT);
			break;
		case AVG:
			interpolateAverage(resultDatapoints, InterpolationType.LININT);
			break;
		case COUNT:
			interpolateCount(resultDatapoints, InterpolationType.ZIMSUM);
			break;			
		default:
			throw new UnsupportedOperationException("Unsupported aggregator specified");
		}

		result.addDatapoints(resultDatapoints);
		resultMetric.add(result);

		return resultMetric;
	}

	private void interpolateSum(Map<Long, Double> resultDatapoints, InterpolationType interpolationType){
		while(doesAnyTimeSeriesHaveData()){
			long timestamp = updateBufferChronologically();
			indexToInterpolate = -1;

			double value = 0.0;

			value = fillInterpolatedValues(interpolationType);
			resultDatapoints.put(timestamp, value);

			// Fill all timestamps with interpolated values
			while (shouldDoInterpolation()) {
				value += fillInterpolatedValues(interpolationType);
				resultDatapoints.put(timestamp, value);
			}
		}
	}

	private void interpolateAverage(Map<Long, Double> resultDatapoints, InterpolationType interpolationType){
		while(doesAnyTimeSeriesHaveData()){
			long timestamp = updateBufferChronologically();
			indexToInterpolate = -1;

			double value = 0.0;

			int num = 1;
			value = fillInterpolatedValues(interpolationType);

			// Fill all timestamps with interpolated values
			while (shouldDoInterpolation()) {
				value += fillInterpolatedValues(interpolationType);
				num++;
			}

			resultDatapoints.put(timestamp, value/num);
		}
	}

	private void interpolateMin(Map<Long, Double> resultDatapoints, InterpolationType interpolationType){
		while(doesAnyTimeSeriesHaveData()){
			long timestamp = updateBufferChronologically();
			indexToInterpolate = -1;

			double value = fillInterpolatedValues(interpolationType);
			double min = Double.isNaN(value)? Double.POSITIVE_INFINITY : value;

			// Fill all timestamps with interpolated values
			while (shouldDoInterpolation()) {
				value = fillInterpolatedValues(interpolationType);
				if(!Double.isNaN(value) && value < min){
					min = value;
				}
			}
			resultDatapoints.put(timestamp, (Double.POSITIVE_INFINITY == min) ? Double.NaN : min);
		}
	}

	private void interpolateMax(Map<Long, Double> resultDatapoints, InterpolationType interpolationType){
		while(doesAnyTimeSeriesHaveData()){
			long timestamp = updateBufferChronologically();
			indexToInterpolate = -1;

			double value = fillInterpolatedValues(interpolationType);
			double max = Double.isNaN(value) ? Double.NEGATIVE_INFINITY : value;

			// Fill all timestamps with interpolated values
			while (shouldDoInterpolation()) {
				value = fillInterpolatedValues(interpolationType);

				if(!Double.isNaN(value) && value > max){
					max = value;
				}				
			}
			resultDatapoints.put(timestamp, (Double.POSITIVE_INFINITY == max) ? Double.NaN : max);
		}
	}

	private void interpolateCount(Map<Long, Double> resultDatapoints, InterpolationType interpolationType){
		while(doesAnyTimeSeriesHaveData()){
			long timestamp = updateBufferChronologically();
			indexToInterpolate = -1;

			double count = 0.0;

			// Fill all timestamps with interpolated values
			while (shouldDoInterpolation()) {
				double value = fillInterpolatedValues(interpolationType);
				if (!Double.isNaN(value)) {
					count++;
				}
				resultDatapoints.put(timestamp, count);
			}
		}
	}

	@Override
	public List<Metric> transform(QueryContext queryContext, List<Metric>... listOfList) {
		throw new UnsupportedOperationException("Interpolation Transform is not supposed to be used with a list of metric list!");
	}

	@Override
	public String getResultScopeName() {
		return TransformFactory.Function.INTERPOLATE.name();
	}

	/**
	 * Puts the next data point of an iterator in the next section of internal buffer.
	 * @param i The index of the iterator.
	 * @param datapoint The last data point returned by that iterator.
	 */
	private void putDataPoint(int i, Entry<Long, Double> datapoint) {
		timestamps[i] = datapoint.getKey();
		values[i] = datapoint.getValue();
	}

	/**
	 * Indicates if there are values still to be read from any time series, by inspecting the internal timestamp buffer 
	 */
	private boolean doesAnyTimeSeriesHaveData() {
		for (int i = 0; i < iterators.length; i++) {
			if ((timestamps[iterators.length + i]) !=  MARK_END_TIME_SERIES) {
				return true;
			}
		}
		return false;
	}

	/**
	 *  Choose smallest timestamp timeseries from the next section, and update the current and next section of that time series 
	 */
	private long updateBufferChronologically() {
		long minTimestamp = Long.MAX_VALUE;
		long timestamp = 0;

		// Mark the internal timestamp buffer as done, when we have reached the end of that time series
		for (int i = current; i < iterators.length; i++) {
			if (timestamps[i] != 0L && timestamps[i + iterators.length] == MARK_END_TIME_SERIES) {
				timestamps[i] = 0L;
			}
		}

		current = -1;
		boolean isMultipleSeriesWithMinimum = false;
		for (int i = 0; i < iterators.length; i++) {
			timestamp = timestamps[iterators.length + i];
			if (timestamp < minTimestamp) {
				minTimestamp = timestamp;
				current = i;
				isMultipleSeriesWithMinimum = false;
			} else if (timestamp == minTimestamp) {
				isMultipleSeriesWithMinimum = true;
			}
		}

		updateCurrentAndNextSectionOfBuffer(current);
		if (isMultipleSeriesWithMinimum) {
			for (int i = current + 1; i < iterators.length; i++) {
				timestamp = timestamps[iterators.length + i];
				if (timestamp == minTimestamp) {
					updateCurrentAndNextSectionOfBuffer(i);
				}
			}
		}

		return minTimestamp;
	}

	/**
	 * Makes iterator number i move forward to the next data point in internal buffer
	 * Copies the next datapoint to current datapoint, and uses the iterator to populate the next section
	 * @param i The index of the iterator.
	 */
	private void updateCurrentAndNextSectionOfBuffer(int i) {
		int next = iterators.length + i;
		timestamps[i] = timestamps[next];
		values[i] = values[next];
		if (iterators[i].hasNext()) {
			putDataPoint(next, (Entry<Long, Double>) iterators[i].next());
		} else {
			markEndTimeSeries(i);
		}
	}

	/**
	 * Mark the timestamp buffer as reached the end, if the iterator has reached the end.
	 * @param i The index of the iterator.
	 */
	private void markEndTimeSeries(int i) {
		timestamps[iterators.length + i] = MARK_END_TIME_SERIES;
		iterators[i] = null;
	}

	private boolean shouldDoInterpolation() {
		return shouldDoInterpolation(false);
	}

	/**
	 * Returns whether or not there are more values to interpolate by checking the timestamp internal buffer.
	 * @param incrementIndexToInterpolate Whether or not to also move the internal pointer
	 *
	 * @return true if there are more values to interpolate, false otherwise.
	 */
	private boolean shouldDoInterpolation(boolean incrementIndexToInterpolate) {
		for (int i = indexToInterpolate + 1; i < iterators.length; i++) {
			if (timestamps[i] != 0L) {
				if (incrementIndexToInterpolate) {
					indexToInterpolate = i;
				}
				return true;
			}
		}
		return false;
	}	

	/* 
	 * Fills interpolated value for a missing timestamp.
	 * If there is already a value for a given timestamp, then don't do any operation. 
	 */
	private double fillInterpolatedValues(InterpolationType interpolationType) {
		double interpolatedValue = 0;
		if (shouldDoInterpolation(true)) {

			double y1 = values[indexToInterpolate];

			if (current == indexToInterpolate) {
				return y1;
			}

			long x = timestamps[current];
			long x1 = timestamps[indexToInterpolate];
			if (x == x1) {
				return y1;
			}
			int next = indexToInterpolate + iterators.length;
			double y2 = values[next];
			long x2 = timestamps[next];
			if (x == x2) {
				return y2;
			}

			switch(interpolationType){
			case LININT:
				interpolatedValue = (y2 - y1) / (x2 - x1) * (x - x1) + y1;
				break;
			case ZIMSUM:
				interpolatedValue = 0;
				break;
			default:
				throw new IllegalArgumentException("Invalid interpolation type specified");
			}
		}
		return interpolatedValue;
	}
}
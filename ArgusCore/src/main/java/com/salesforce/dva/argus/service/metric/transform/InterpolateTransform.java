package com.salesforce.dva.argus.service.metric.transform;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.salesforce.dva.argus.entity.Metric;

/**
 * Interpolates multiple time series that will be used for aggregation.
 * Fills in the interpolated value after a time series first datapoint is found until the last datapoint.
 *
 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
 */
public class InterpolateTransform implements Transform {
	/** Internal buffer of current and next timestamps from each time series that will be used for interpolation */
	private Long[] timestamps;
	/** Internal buffer of current and next values from each time series that will be used for interpolation */
	private Double[] values;
	/** Array of Iterators for each time series */
	private Iterator<Entry<Long, Double>>[] iterators;
	/** The index in current time series being used. This timestamp, will then be used to interpolate other time series. */
	int current = 0;
	/** The index of time series to interpolate. */
	private int indexToInterpolate;
	/** Datapoints that are added to each time series as a result of interpolation. */
	private Map<Long, Double>[] addedDatapointsArray;
	private static final long MARK_END_TIME_SERIES  = Long.MAX_VALUE;

	@SuppressWarnings("unchecked")
	@Override
	public List<Metric> transform(List<Metric> metrics) {
		int size = metrics.size();
		timestamps = new Long[size * 2];
		values = new Double[size * 2];
		iterators = new Iterator[size];
		addedDatapointsArray =  (Map<Long, Double>[]) new Map[size];

		for(int i=0; i<size;  i++){
			addedDatapointsArray[i] = new TreeMap<Long, Double>();
		}

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

		while(doesAnyTimeSeriesHaveData()){
			updateBufferChronologically();
			indexToInterpolate = -1;
			fillInterpolatedValues();
			while (shouldDoInterpolation()) {
				fillInterpolatedValues();
			}
		}

		for(int i=0; i<size;  i++){
			metrics.get(i).addDatapoints(addedDatapointsArray[i]);
		}

		return metrics;
	}

	@Override
	public List<Metric> transform(List<Metric> metrics, List<String> constants) {
		throw new UnsupportedOperationException("Zero if missing Sum Transform is not supposed to be used with a constant");
	}

	@Override
	public List<Metric> transform(List<Metric>... listOfList) {
		throw new UnsupportedOperationException("Zero if missing Sum Transform is not supposed to be used with a list of metric list!");
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
	private void updateBufferChronologically() {
		long minTimestamp = Long.MAX_VALUE;

		// Mark the internal timestamp buffer as done, when we have reached the end of that time series
		for (int i = current; i < iterators.length; i++) {
			if (timestamps[i + iterators.length] == MARK_END_TIME_SERIES) {
				timestamps[i] = MARK_END_TIME_SERIES;
			}
		}

		current = -1;
		boolean isMultipleSeriesWithMinimum = false;
		for (int i = 0; i < iterators.length; i++) {
			long timestamp = timestamps[iterators.length + i];
			if (timestamp < minTimestamp) {
				minTimestamp = timestamp;
				current = i;
				isMultipleSeriesWithMinimum = false;
			} else if (timestamp == minTimestamp) {
				isMultipleSeriesWithMinimum = true;
			}
		}

		if (current < 0) {
			// no more elements
			return;
		}

		updateCurrentAndNextSectionOfBuffer(current);
		if (isMultipleSeriesWithMinimum) {
			for (int i = current + 1; i < iterators.length; i++) {
				long timestamp = timestamps[iterators.length + i];
				if (timestamp == minTimestamp) {
					updateCurrentAndNextSectionOfBuffer(i);
				}
			}
		}
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
			if (timestamps[i] != null && timestamps[i] != MARK_END_TIME_SERIES) {
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
	private void fillInterpolatedValues() {
		double interpolatedValue = 0;
		if (shouldDoInterpolation(true)) {
			if(values[indexToInterpolate] == null){
				return;
			}

			double y1 = values[indexToInterpolate];

			if (current == indexToInterpolate) {
				return;
			}

			long x = timestamps[current];
			long x1 = timestamps[indexToInterpolate];
			if (x == x1) {
				return;
			}
			int next = indexToInterpolate + iterators.length;
			double y2 = values[next];
			long x2 = timestamps[next];
			if (x == x2) {
				return;
			}

			interpolatedValue = (y2 - y1) / (x2 - x1) * (x - x1) + y1;
			addedDatapointsArray[indexToInterpolate].put(x, interpolatedValue);
		}
	}
}
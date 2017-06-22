package com.salesforce.dva.argus.service.metric.transform;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import com.salesforce.dva.argus.entity.Metric;

public class Interpolate implements Transform {

	Long[] timestamps;
	Double[] values;
	Iterator<Entry<Long, Double>>[] iterators;
	long endTime;
	/** The index in current time series being used. */
	int current = 0;
	/** The index in values of the current value being aggregated. */
	private int pos;
	List<Metric> metrics;
	Map<Long, Double>[] addedDatapointsArray;

	protected static final long TIME_MASK  = 0x7FFFFFFFFFFFFFFFL;

	/**
	 * Puts the next data point of an iterator in the next section of internal buffer.
	 * @param i The index of the iterator.
	 * @param datapoint The last data point returned by that iterator.
	 */
	private void putDataPoint(int i, Entry<Long, Double> datapoint) {
		timestamps[i] = datapoint.getKey();
		values[i] = datapoint.getValue();
	}

	public boolean hasNext() {
		final int size = iterators.length;
		for (int i = 0; i < size; i++) {
			if ((timestamps[size + i]) !=  TIME_MASK) {
				return true;
			}
		}
		return false;
	}


	public void next() {
		final int size = iterators.length;
		long minTimestamp = Long.MAX_VALUE;

		// In case we reached the end of one or more Spans, we need to make sure
		// we mark them as such by zeroing their current timestamp.  There may
		// be multiple Spans that reached their end at once, so check them all.
		for (int i = current; i < size; i++) {
			if (timestamps[i + size] == TIME_MASK) {
				timestamps[i] = 0L;
			}
		}

		// Now we need to find which Span we'll consume next.  We'll pick the
		// one that has the data point with the smallest timestamp since we want to
		// return them in chronological order.
		current = -1;
		// If there's more than one Span with the same smallest timestamp, we'll
		// set this to true so we can fetch the next data point in all of them at
		// the same time.
		boolean multiple = false;
		for (int i = 0; i < size; i++) {
			final long timestamp = timestamps[size + i];
			if (timestamp < minTimestamp) {
				minTimestamp = timestamp;
				current = i;
				// We just found a new minimum so right now we can't possibly have
				// multiple Spans with the same minimum.
				multiple = false;
			} else if (timestamp == minTimestamp) {
				multiple = true;
			}
		}

		if (current < 0) {
			// no more elements
			return;
		}

		moveToNext(current);
		if (multiple) {
			for (int i = current + 1; i < size; i++) {
				final long timestamp = timestamps[size + i] & TIME_MASK;
				if (timestamp == minTimestamp) {
					moveToNext(i);
				}
			}
		}
	}

	/**
	 * Makes iterator number i move forward to the next data point in internal buffer
	 * Copies the next datapoint to current datapoint, and uses the iterator to populate the next section
	 * @param i The index of the iterator.
	 */
	private void moveToNext(final int i) {
		final int next = iterators.length + i;
		timestamps[i] = timestamps[next];
		values[i] = values[next];
		if (iterators[i].hasNext()) {
			putDataPoint(next, (Entry<Long, Double>) iterators[i].next());
		} else {
			endReached(i);
		}
	}

	/**
	 * Indicates that an iterator in {@link #iterators} has reached the end.
	 * @param i The index in {@link #iterators} of the iterator.
	 */
	private void endReached(final int i) {
		timestamps[iterators.length + i] = TIME_MASK;
		iterators[i] = null;
	}

	public boolean hasNextValue() {
		return hasNextValue(false);
	}

	/**
	 * Returns whether or not there are more values to interpolate.
	 * @param updatePos Whether or not to also move the internal pointer
	 * {@link #pos} to the index of the next value to interpolate.
	 * @return true if there are more values to interpolate, false otherwise.
	 */
	private boolean hasNextValue(boolean updatePos) {
		final int size = iterators.length;
		for (int i = pos + 1; i < size; i++) {
			if (timestamps[i] != null && timestamps[i] != 0) {
				if (updatePos) {
					pos = i;
				}
				return true;
			}
		}
		return false;
	}	


	/* 
	 * Return the value, timestamp and corresponding time series that has been interpolated 
	 */
	public void fillInterpolatedValues() {
		double r = 0;
		if (hasNextValue(true)) {
			if(values[pos] == null){
				return;
			}

			final double y0 = values[pos];
			// current is time series that is being interpolated
			if (current == pos) {
				return;
			}

			final long x = timestamps[current];
			final long x0 = timestamps[pos];
			if (x == x0) {
				return;
			}
			final int next = pos + iterators.length;
			final double y1 = values[next];
			final long x1 = timestamps[next];
			if (x == x1) {
				return;
			}

			r = y0 + (x - x0) * (y1 - y0) / (x1 - x0);
			addedDatapointsArray[pos].put(x, r);
		}
	}

	@Override
	public List<Metric> transform(List<Metric> metrics) {
		int size = metrics.size();
		timestamps = new Long[size * 2];
		values = new Double[size *2];
		iterators = new Iterator[size];
		addedDatapointsArray =  (Map<Long, Double>[]) new Map[size];
		for(int i=0; i<size;  i++){
			addedDatapointsArray[i] = new TreeMap<Long, Double>();
		}
		this.metrics = metrics;

		for (int i = 0; i < size; i++) {
			iterators[i] = metrics.get(i).getDatapoints().entrySet().iterator();
			if (!iterators[i].hasNext()) {
				endReached(i);
				continue;
			}

			Entry<Long, Double> datapoint = (Entry<Long, Double>) iterators[i].next();
			putDataPoint(size + i, datapoint);
		}

		while(hasNext()){
			next();
			pos = -1;
			fillInterpolatedValues();
			while (hasNextValue()) {
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
		// TODO Auto-generated method stub
		return null;
	}
}

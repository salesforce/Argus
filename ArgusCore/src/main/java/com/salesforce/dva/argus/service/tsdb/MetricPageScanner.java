package com.salesforce.dva.argus.service.tsdb;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;

import org.apache.commons.lang.IllegalClassException;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.service.tsdb.MetricScanner;
import com.salesforce.dva.argus.system.SystemAssert;

public class MetricPageScanner extends MetricScanner {

	protected Condition hasData;
	private AtomicBoolean donePushingData;
	protected ScannerShiftFunction shiftFunction;
	
	/**
	 * Constructs a scanner for MetricPagerTransform. Encapsulates a metric and allows for standard scanner operations.
	 * 
	 * @param metric The metric whose data to encapsulate.
	 * @param shiftFunction The ScannerShiftFunction describing the shift from the timestamps of the initial input through this scanner object.
	 */
	public MetricPageScanner(Metric metric, ScannerShiftFunction shiftFunction) {		
		super(metric);
		
		lock.writeLock().lock();
		hasData = lock.writeLock().newCondition();
		donePushingData = new AtomicBoolean(false);
		this.shiftFunction = shiftFunction;
		lock.writeLock().unlock();
	}
	
	/**
	 * Constructs a scanner based off of a datapoint map, allowing for standard scanner operations.
	 * 
	 * @param datapoints The map of datapoints to encapsulate.
	 * @param shiftFunction The ScannerShiftFunction describing the shift from the timestamps of the initial input through this scanner object.
	 */
	protected MetricPageScanner(Map<Long, Double> datapoints, ScannerShiftFunction shiftFunction) {
		super(datapoints);
		
		lock.writeLock().lock();
		hasData = lock.writeLock().newCondition();
		donePushingData = new AtomicBoolean(false);
		this.shiftFunction = shiftFunction;
		lock.writeLock().unlock();
	}
	
	@Override
	public Map.Entry<Long, Double> getNextDP() {
		SystemAssert.requireState(this.hasNextDP(), "The Scanner must contain more datapoints in order to return the next datapoint.");
		lock.readLock().lock();
		Map<Long, Double> dpMap = new HashMap<Long, Double>();
		if (pointer != null) {
			dpMap.put(pointer.get(), datapoints.get(pointer.get()));
		}
		Map.Entry<Long, Double> dp = null;
		for (Map.Entry<Long, Double> elem : dpMap.entrySet()) {
			dp = elem;
		}
		lock.readLock().unlock();
		pointer = nextTime();
		return dp;
	}
	
	@Override
	public Map.Entry<Long, Double> peek() {
		if (!this.hasNextDP()) {
			return null;
		}
		lock.readLock().lock();
		Map<Long, Double> dpMap = new HashMap<>();
		if (pointer != null) {
			dpMap.put(pointer.get(), datapoints.get(pointer.get()));
		}
		Map.Entry<Long, Double> dp = null;
		for (Map.Entry<Long, Double> elem : dpMap.entrySet()) {
			dp = elem;
		}
		lock.readLock().unlock();
		return dp;
	}
	
	@Override
	public MetricQuery getQuery() {
		if (metric.getQuery() != null) {
			return new MetricQuery(metric.getQuery());
		}
		return null;
	}
	
	@Override
	synchronized public boolean hasNextDP() {
		lock.writeLock().lock();
		// if we are out of data but expect more to come
		while (pointer == null && !donePushingData.get()) {
			try {
				hasData.await();
			} catch (InterruptedException ex) {
				_logger.warn("The thread was interrupted");
			} finally {
				lock.writeLock().unlock();
			}
		}
		if (lock.isWriteLockedByCurrentThread()) {
			lock.writeLock().unlock();
		}
		// if we are done with the scanner
		if (pointer == null) {
			return false;
		}
		return true;
	}
	
	@Override
	public void dispose() {
		lock.writeLock().lock();
		try {
			inUse.set(false);
			donePushingData.set(true);
			hasData.signalAll(); // no more data is coming
		} finally {
			lock.writeLock().unlock();
		}
	}
	
	@Override
	public int getCallsToFetch() {
		throw new IllegalClassException("Metric Page Scanners do not actively fetch data!");
	}
	
	@Override
	protected ScannerShiftFunction getShift() {
		return shiftFunction;
	}
	
	protected void setDonePushingData() {
		try {
			lock.writeLock().lock();
			donePushingData.set(true);
		} finally {
			lock.writeLock().unlock();
		}
	}
	
	protected void updateState(Metric metric) {
		lock.writeLock().lock();
		try {
			pushDatapoints(metric.getDatapoints());
			updateMetric(metric);
			hasData.signalAll();
		} finally {
			lock.writeLock().unlock();
		}
	}
	
	protected void updateState(Map<Long, Double> datapoints) {
		throw new IllegalClassException("Metric Page Scanner must update based on a metric, not a datapoint map!");
	}
}

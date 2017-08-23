package com.salesforce.dva.argus.service.tsdb;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.lang.IllegalClassException;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.system.SystemAssert;

public class MetricPageDatapointScanner extends MetricPageScanner {
		
	/**
	 * Generates a scanner for MetricPagerValueMapping or MetricPageValueReducerOrMapping. Encapsulates a map of datapoints and
	 * allows for standard scanner operations.
	 * 
	 * @param datapoints The Map of datapoints to encapsulate.
	 * @param shiftFunction The ScannerShiftFunction describing the shift of a timestamp from the initial input to the output data of this scanner.
	 * @param start Long timestamp representing the start of the query that resulted in this scanner object.
	 * @param end Long timestamp representing the end of the query that resulted in this scanner object.
	 */
	protected MetricPageDatapointScanner(Map<Long, Double> datapoints, ScannerShiftFunction shiftFunction, Long start, Long end) {
		super(datapoints, shiftFunction);
		lock.writeLock().lock();
		this.query = new MetricQuery("PageScanner", "PageScanner", new HashMap<>(), start, end); 
		this.metric = new Metric("PageScanner", "PageScanner");
		metric.setDatapoints(datapoints);
		lock.writeLock().unlock();
	}
	
	@Override
	public MetricQuery getQuery() {
		return new MetricQuery(query);
	}
	
	@Override
	public String getMetricName() {
		throw new IllegalClassException("A map of datapoints doesn't have a corresponding metric name!");
	}
	
	@Override
	public String getMetricScope() {
		throw new IllegalClassException("A map of datapoints doesn't have a corresponding metric scope!");
	}
	
	@Override
	public Map<String, String> getMetricTags() {
		throw new IllegalClassException("A map of datapoints doesn't have corresponding metric tags!");
	}
	
	@Override
	protected void updateMetric(Metric metric) {
		throw new IllegalClassException("A map of datapoints doesn't have a corresponding metric!");
	}
	
	@Override
	public int hashCode() {
		final int prime = 23;
		
		int result = prime * this.metric.hashCode();
		result += prime * (pointer == null ? prime : (int) pointer.get());
		return result;
	}
	
	@Override
	protected void pushDatapoints(Map<Long, Double> datapoints) {
		SystemAssert.requireState(lock.writeLock().isHeldByCurrentThread(), "This thread must have the writing lock to push more datpoints!");
		if (pointer == null) {
			Set<Long> possiblePointers = new HashSet<>();
			for (Long newTime : datapoints.keySet()) {
				if (!this.datapoints.containsKey(newTime)) {
					possiblePointers.add(newTime);
				}
			}
			pointer = possiblePointers.size() == 0 ? null : new AtomicLong(Collections.min(possiblePointers));
		}
		this.datapoints.putAll(datapoints);
	}
	
	@Override
	protected void updateState(Map<Long, Double> datapoints) {
		lock.writeLock().lock();
		try {
			pushDatapoints(datapoints);
			if (!datapoints.isEmpty()) {
				hasData.signalAll();
			}
		} finally {
			lock.writeLock().unlock();
		}
	}
}

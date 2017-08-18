package com.salesforce.dva.argus.service.tsdb;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.concurrent.ThreadSafe;

import org.apache.commons.lang.IllegalClassException;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.metric.transform.ValueReducerOrMapping;
import com.salesforce.dva.argus.system.SystemAssert;

@ThreadSafe
public class MetricPagerValueReducerOrMapping extends MetricPager {

	private ValueReducerOrMapping redMap;
	private MetricPageScanner pageScanner;
	
	public MetricPagerValueReducerOrMapping(List<MetricScanner> scanners, Long chunkTime, ValueReducerOrMapping redMap) {
		SystemAssert.requireArgument(scanners != null, "Cannot return value reducer or mapping of null scanners.");
		SystemAssert.requireArgument(scanners.size() == 1, "Value reducer or mappings can only be performed on a single scanner.");
		
		start = new AtomicLong(scanners.get(0).getQuery().getStartTimestamp());
		end = new AtomicLong(scanners.get(0).getQuery().getEndTimestamp());
		
		SystemAssert.requireArgument((chunkTime > 0 && chunkTime <= (end.get() - start.get())) || end.get() == start.get(), 
				"The chunk time to show must be greater than zero and cannot exceed the length of the query time.");
		
		lock = new ReentrantReadWriteLock();
		hasDataDP = lock.writeLock().newCondition();
		lock.writeLock().lock();
		this.scanners.addAll(scanners);
		this.chunkTime = new AtomicLong(chunkTime);
		this.redMap = redMap;
		numChunks = this.chunkTime.get() == 0 ? 1 : (int) Math.ceil(((double)(end.get() - start.get())) / this.chunkTime.get());
		lastEndTimestamp = new AtomicLong(start.get());
		inUse = new AtomicBoolean(true);
		complete = new AtomicBoolean(false);
		pageScanner = new MetricPageDatapointScanner(new TreeMap<>(),
				PageScannerShifts.getValueReducerOrMappingShift(scanners.get(0).getShift(), redMap, constants), start.get(), end.get());
		currentChunkStartTime = start.get();
		currentViewingWindow = new Long(chunkTime);
		lock.writeLock().unlock();
				
		_scheduledExecutorService = _createScheduledExecutorService();
		_startScheduledExecutorService();
	}
	
	public MetricPagerValueReducerOrMapping(List<MetricScanner> scanners, Long chunkTime, ValueReducerOrMapping redMap, List<String> constants) {
		SystemAssert.requireArgument(scanners != null, "Cannot return value reducer or mapping of null scanners.");
		SystemAssert.requireArgument(scanners.size() == 1, "Value reducer or mapping can only be performed on a single scanner.");
		
		start = new AtomicLong(scanners.get(0).getQuery().getStartTimestamp());
		end = new AtomicLong(scanners.get(0).getQuery().getEndTimestamp());
		
		SystemAssert.requireArgument((chunkTime > 0 && chunkTime <= (end.get() - start.get())) || end.get() == start.get(), 
				"The chunk time to show must be greater than zero and cannot exceed the length of the query time.");
		
		lock = new ReentrantReadWriteLock();
		hasDataDP = lock.writeLock().newCondition();
		lock.writeLock().lock();
		this.scanners.addAll(scanners);
		if (constants != null) {
			this.constants = new ArrayList<>(constants);
		}
		this.chunkTime = new AtomicLong(chunkTime);
		this.redMap = redMap;
		numChunks = this.chunkTime.get() == 0 ? 1 : (int) Math.ceil(((double) (end.get() - start.get())) / this.chunkTime.get());
		lastEndTimestamp = new AtomicLong(start.get());
		inUse = new AtomicBoolean(true);
		complete = new AtomicBoolean(false);
		pageScanner = new MetricPageDatapointScanner(new TreeMap<>(),
				PageScannerShifts.getValueReducerOrMappingShift(scanners.get(0).getShift(), redMap, constants), start.get(), end.get());
		currentChunkStartTime = start.get();
		currentViewingWindow = new Long(chunkTime);
		lock.writeLock().unlock();
		
		_scheduledExecutorService = _createScheduledExecutorService();
		_startScheduledExecutorService();
	}
	
	@Override
	public List<Metric> getMetricChunk(int chunkNumber) {
		throw new IllegalClassException("Can only get the chunk of metrics in a transform");
	}
	
	@Override
	public List<Metric> getNextWindowOfMetric() {
		throw new IllegalClassException("Can only get the window of metrics in a transform");
	}
	
	@Override
	public List<Metric> getPrevWindowOfMetric() {
		throw new IllegalClassException("Can only get the window of metrics in a transform");
	}
	
	@Override
	public List<Metric> getMetricWindowInputTimeRange(Long startTime, Long endTime) {
		throw new IllegalClassException("Can only get the window of datapoints in a value mapping or value reducer and mapping transform");
	}
	
	@Override
	public List<Metric> getMetricWindowOutputTimeRange(Long startTime, Long endTime) {
		throw new IllegalClassException("Can only get the window of datapoints in a value mapping or value reducer and mapping transform");
	}
	
	@Override
	public List<Metric> getNewMetricPageFromStartInput(Long startTime) {
		throw new IllegalClassException("Can only get the window of datapoints in a value mapping or value reducer and mapping transform");
	}
	
	@Override
	public List<Metric> getNewMetricPageFromStartOutput(Long startTime) {
		throw new IllegalClassException("Can only get the window of datapoints in a value mapping or value reducer and mapping transform");
	}
	
	@Override
	public void pushMetrics(List<Metric> metrics) {
		throw new IllegalClassException("Can only push metrics in a transform");
	}
	
	@Override
	public MetricPageScanner getScanner() {
		return pageScanner;
	}
	
	@Override
	public List<MetricScanner> getScannerList() {
		throw new IllegalClassException("Only transforms can return result as a list of scanners");
	}
	
	@Override
	protected ScannerShiftFunction shift() {
		return pageScanner.getShift();
	}
	
	@Override
	protected void performTransform() {
		lock.readLock().lock();
		Long startTime = lastEndTimestamp.get();
		Long endTime = Math.min(end.get(), startTime + chunkTime.get());
		ScannerShiftFunction prevShift = scanners.isEmpty() ? (Long t) -> t : scanners.get(0).getShift();
		Map<Long, Double> result = constants == null ? redMap.mappingToPager(scanners.get(0), prevShift.shift(startTime), prevShift.shift(endTime)) :
			redMap.mappingToPager(scanners.get(0), constants, prevShift.shift(startTime), prevShift.shift(endTime));
		lock.readLock().unlock();
		
		lock.writeLock().lock();
		chunksProcessed++;
		pageScanner.updateState(result);
		pushDatapoints(result);
		lastEndTimestamp.set(endTime);
		
		if (lastEndTimestamp.get() == end.get()) {
			pageScanner.setDonePushingData();
			complete.set(true);
			for (MetricScanner scanner : scanners) {
				if (scanner.isInUse()) {
					scanner.dispose();
				}
			}
		}
		hasDataDP.signalAll();
		lock.writeLock().unlock();
		
		if (complete.get()) {
			_shutdownScheduledExecutorService();
		}
	}
}

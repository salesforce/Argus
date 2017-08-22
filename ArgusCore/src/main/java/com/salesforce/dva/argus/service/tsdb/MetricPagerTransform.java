package com.salesforce.dva.argus.service.tsdb;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.concurrent.ThreadSafe;

import org.apache.commons.lang.IllegalClassException;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.metric.transform.JoinTransform;
import com.salesforce.dva.argus.service.metric.transform.Transform;
import com.salesforce.dva.argus.system.SystemAssert;

@ThreadSafe
public class MetricPagerTransform extends MetricPager {
	
	private Transform transform;
	private List<List<MetricScanner>> listOfList;	
	private List<MetricPageScanner> pageScanners;
	private AtomicBoolean scannersCreated;
	private Condition scannable;
	
	public MetricPagerTransform(List<MetricScanner> scanners, Long chunkTime, Transform transform) {
		SystemAssert.requireArgument(scanners != null, "Cannot transform null scanners.");
		
		start = null;
		end = null;
		for (MetricScanner scanner : scanners) {
			if (start == null || scanner.getQuery().getStartTimestamp() < start.get()) {
				start = new AtomicLong(scanner.getQuery().getStartTimestamp());
			}
			if (end == null  || scanner.getQuery().getEndTimestamp() > end.get()) {
				end = new AtomicLong(scanner.getQuery().getEndTimestamp());
			}
		}
		
		SystemAssert.requireArgument((chunkTime > 0 && chunkTime <= (end.get() - start.get())) || end.get() == start.get(), 
				"The chunk time to show must be greater than zero and cannot exceed the length of the query time.");
		
		lock = new ReentrantReadWriteLock();
		hasDataMet = lock.writeLock().newCondition();
		lock.writeLock().lock();
		this.scanners.addAll(scanners);
		this.chunkTime = new AtomicLong(chunkTime);
		this.transform = transform;
		numChunks = this.chunkTime.get() == 0 ? 1 : (int) Math.ceil(((double)(end.get() - start.get())) / this.chunkTime.get());
		lastEndTimestamp = new AtomicLong(start.get());
		inUse = new AtomicBoolean(true);
		complete = new AtomicBoolean(false);
		pageScanners = new ArrayList<>();
		scannersCreated = new AtomicBoolean(false);
		scannable = lock.writeLock().newCondition();
		currentChunkStartTime = start.get();
		currentViewingWindow = new Long(chunkTime);
		lock.writeLock().unlock();
		
		_scheduledExecutorService = _createScheduledExecutorService();
		_startScheduledExecutorService();
	}
	
	public MetricPagerTransform(List<MetricScanner> scanners, Long chunkTime, Transform transform, List<String> constants) {
		SystemAssert.requireArgument(scanners != null, "Cannot transform null scanners.");
		
		start = null;
		end = null;
		for (MetricScanner scanner : scanners) {
			if (start == null || scanner.getQuery().getStartTimestamp() < start.get()) {
				start = new AtomicLong(scanner.getQuery().getStartTimestamp());
			}
			if (end == null  || scanner.getQuery().getEndTimestamp() > end.get()) {
				end = new AtomicLong(scanner.getQuery().getEndTimestamp());
			}
		}
		SystemAssert.requireArgument((chunkTime > 0 && chunkTime <= (end.get() - start.get())) || end.get() == start.get(), 
				"The chunk time to show must be greater than zero and cannot exceed the length of the query time.");
		
		lock = new ReentrantReadWriteLock();
		hasDataMet = lock.writeLock().newCondition();
		lock.writeLock().lock();
		this.scanners.addAll(scanners);
		if (constants != null) {
			this.constants = new ArrayList<>(constants);
		}
		this.chunkTime = new AtomicLong(chunkTime);
		this.transform = transform;
		numChunks = this.chunkTime.get() == 0 ? 1 : (int) Math.ceil(((double)(end.get() - start.get())) / this.chunkTime.get());
		lastEndTimestamp = new AtomicLong(start.get());
		inUse = new AtomicBoolean(true);
		complete = new AtomicBoolean(false);
		pageScanners = new ArrayList<>();
		scannersCreated = new AtomicBoolean(false);
		scannable = lock.writeLock().newCondition();
		currentChunkStartTime = start.get();
		currentViewingWindow = new Long(chunkTime);
		lock.writeLock().unlock();
		
		_scheduledExecutorService = _createScheduledExecutorService();
		_startScheduledExecutorService();
	}
	
	public MetricPagerTransform(Long chunkTime, Transform transform, List<MetricScanner>... listOfList) {
		SystemAssert.requireArgument(listOfList != null, "Cannot transform null scanners.");
		
		start = null;
		end = null;
		this.listOfList = new ArrayList<>();
		for (List<MetricScanner> scanners : listOfList) {
			SystemAssert.requireArgument(scanners != null, "Cannot transform null scanners.");
			this.listOfList.add(scanners);
			for (MetricScanner scanner : scanners) {
				if (start == null || scanner.getQuery().getStartTimestamp() < start.get()) {
					start = new AtomicLong(scanner.getQuery().getStartTimestamp());
				}
				if (end == null  || scanner.getQuery().getEndTimestamp() > end.get()) {
					end = new AtomicLong(scanner.getQuery().getEndTimestamp());
				}
			}
		}
		
		SystemAssert.requireArgument((chunkTime > 0 && chunkTime <= (end.get() - start.get())) || end.get() == start.get(), 
				"The chunk time to show must be greater than zero and cannot exceed the length of the query time.");
		
		lock = new ReentrantReadWriteLock();
		hasDataMet = lock.writeLock().newCondition();
		lock.writeLock().lock();
		this.scanners.addAll(scanners);
		this.chunkTime = new AtomicLong(chunkTime);
		this.transform = transform;
		numChunks = this.chunkTime.get() == 0 ? 1 : (int) Math.ceil(((double)(end.get() - start.get())) / this.chunkTime.get());
		lastEndTimestamp = new AtomicLong(start.get());
		inUse = new AtomicBoolean(true);
		complete = new AtomicBoolean(false);
		pageScanners = new ArrayList<>();
		scannersCreated = new AtomicBoolean(false);
		scannable = lock.writeLock().newCondition();
		currentChunkStartTime = start.get();
		currentViewingWindow = new Long(chunkTime);
		lock.writeLock().unlock();
		
		_scheduledExecutorService = _createScheduledExecutorService();
		_startScheduledExecutorService();
	}
	
	@Override
	public Map<Long, Double> getDPChunk(int chunkNumber) {
		throw new IllegalClassException("Can only get the chunk of datapoints in a value mapping or value reducer and mapping transform");
	}
	
	@Override
	public Map<Long, Double> getNextWindowOfDP() {
		throw new IllegalClassException("Can only get the window of datapoints in a value mapping or value reducer and mapping transform");
	}
	
	@Override
	public Map<Long, Double> getPrevWindowOfDP() {
		throw new IllegalClassException("Can only get the window of datapoints in a value mapping or value reducer and mapping transform");
	}
	
	@Override
	public Map<Long, Double> getDPWindowInputTimeRange(Long startTime, Long endTime) {
		throw new IllegalClassException("Can only get the window of datapoints in a value mapping or value reducer and mapping transform");
	}
	
	@Override
	public Map<Long, Double> getDPWindowOutputTimeRange(Long startTime, Long endTime) {
		throw new IllegalClassException("Can only get the window of datapoints in a value mapping or value reducer and mapping transform");
	}
	
	@Override
	public Map<Long, Double> getNewDPPageFromStartInput(Long startTime) {
		throw new IllegalClassException("Can only get the window of datapoints in a value mapping or value reducer and mapping transform");
	}
	
	@Override
	public Map<Long, Double> getNewDPPageFromStartOutput(Long startTime) {
		throw new IllegalClassException("Can only get the window of datapoints in a value mapping or value reducer and mapping transform");
	}
	
	@Override
	public void pushDatapoints(Map<Long, Double> datapoints) {
		throw new IllegalClassException("Can only push datapoints in a value mapping or value reducer and mapping transform");
	}
	
	@Override
	public MetricPageScanner getScanner() {
		throw new IllegalClassException("Transforms must return a list of scanners");
	}
	
	@Override
	public List<MetricScanner> getScannerList() {
		lock.writeLock().lock();
		while (!scannersCreated.get()) {
			try {
				scannable.await();
			} catch (InterruptedException ex) {
				_logger.warn("The thread was interrupted");
			} finally {
				lock.writeLock().unlock();
			}
		}
		if (lock.writeLock().isHeldByCurrentThread()) {
			lock.writeLock().unlock();
		}
		return new ArrayList<MetricScanner>(pageScanners);
	}
	
	private void setPageScannersComplete() {
		for (MetricPageScanner scanner : pageScanners) {
			scanner.setDonePushingData();
		}
	}
	
	@Override
	protected ScannerShiftFunction shift() {
		if (!scannersCreated.get()) {
			lock.writeLock().lock();
			while (!scannersCreated.get()) {
				try {
					scannable.await();
				} catch (InterruptedException ex) {
					_logger.warn("The thread was interrupted");
				} finally {
					lock.writeLock().unlock();
				}
			}
			if (lock.writeLock().isHeldByCurrentThread()) {
				lock.writeLock().unlock();
			}
		}
		return pageScanners.isEmpty() ? (Long t) -> t : pageScanners.get(0).getShift();
	}
	
	private void sendToScanner(List<Metric> results) { 
		SystemAssert.requireState(lock.writeLock().isHeldByCurrentThread(), "The write lock must be held by this thread to update the associated scanners!");
		for (Metric res : results) {
			boolean found = false;
			for (MetricPageScanner scanner : pageScanners) {
				if (scanner.getMetric().equals(res)) {
					found = true;
					scanner.updateState(res);
					break;
				}
			}
			if (!found) {
				ScannerShiftFunction prevShift = scanners.isEmpty() ? (Long t) -> t : scanners.get(0).getShift();
				pageScanners.add(new MetricPageScanner(res, PageScannerShifts.getTransformShift(prevShift, transform, constants)));
			}
		}
	}
	
	protected void performTransform() {
		lock.readLock().lock();
		Long startTime = lastEndTimestamp.get();
		Long endTime = Math.min(end.get(), startTime + chunkTime.get());
		List<Metric> result = null;
		ScannerShiftFunction prevShift = scanners.isEmpty() ? (Long t) -> t : scanners.get(0).getShift();
		if (listOfList == null) {
			result = constants == null ? transform.transformToPager(scanners, prevShift.shift(startTime), prevShift.shift(endTime)) :
				transform.transformToPager(scanners, new ArrayList<>(constants), prevShift.shift(startTime), prevShift.shift(endTime));
		} else {
			SystemAssert.requireArgument(JoinTransform.class.isInstance(transform), "List of list can only be used with join transform!");
			result = transform.transformToPagerListOfList(listOfList, prevShift.shift(startTime), prevShift.shift(endTime));
		}
		lock.readLock().unlock();

		lock.writeLock().lock();
		sendToScanner(result);
		if (!scannersCreated.get()) {
			scannersCreated.set(true);
			scannable.signalAll();
		}
		chunksProcessed++;
		pushMetrics(result);
		lastEndTimestamp.set(endTime);
		if (lastEndTimestamp.get() == end.get()) {
			setPageScannersComplete();
			complete.set(true);
			for (MetricScanner scanner : scanners) {
				if (scanner.isInUse()) {
					scanner.dispose();
				}
			}
		}
		hasDataMet.signalAll();
		lock.writeLock().unlock();
		if (complete.get()) {
			_shutdownScheduledExecutorService();
		}
	}
	
}

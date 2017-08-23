package com.salesforce.dva.argus.service.tsdb;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.concurrent.ThreadSafe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.system.SystemAssert;

@ThreadSafe
public abstract class MetricPager {

	protected AtomicLong chunkTime;
	protected Long currentChunkStartTime;
	protected Long currentViewingWindow;
	protected TreeMap<Long, Double> datapoints = new TreeMap<>();
	protected int chunksProcessed = 0;
	protected ReentrantReadWriteLock lock;
	protected Condition hasDataDP;
	protected Condition hasDataMet;
	protected AtomicLong lastEndTimestamp;
	protected List<MetricScanner> scanners = new ArrayList<>();
	protected List<Metric> metrics = new ArrayList<>();
	protected List<String> constants = null;
	protected AtomicBoolean inUse;
	protected AtomicBoolean complete;
	
	protected AtomicLong start;
	protected AtomicLong end;
	protected int numChunks;
	protected AtomicBoolean transformingData;
	protected AtomicBoolean changingChunk;
	
	protected final Logger _logger = LoggerFactory.getLogger(getClass());
	protected ScheduledExecutorService _scheduledExecutorService;
	protected AtomicBoolean fetching = new AtomicBoolean(false);
	
	private ScheduledFuture<?> task;
	
	private void resetChunkTimeMap(Long chunkTime) {
		SystemAssert.requireArgument((chunkTime > 0 && chunkTime <= (end.get() - start.get())) || start.get() == end.get(),
				"The chunk time to show must be greater than zero and cannot exceed the length of the query time.");
		
		this.chunkTime.set(chunkTime.longValue());
		numChunks = (int) Math.ceil((end.get() - start.get()) * 1.0 / this.chunkTime.get());
	}
	
	private void resetChunkTimeMetric(Long chunkTime) {
		SystemAssert.requireArgument((chunkTime > 0 && chunkTime <= (end.get() - start.get())) || end.get() == start.get(), 
				"The chunk time to show must be greater than zero and cannot exceed the length of the query time.");
		
		this.chunkTime.set(chunkTime.longValue());
		numChunks = (int) Math.ceil((end.get() - start.get()) * 1.0 / this.chunkTime.get());
	}
	
	/**
	 * Resets the amount of time included in each chunk.
	 * 
	 * @param chunkTime The Long representing the milliseconds to consider in each chunk.
	 */
	public void resetChunkTime(Long chunkTime) {
		if (datapoints != null) {
			resetChunkTimeMap(new Long(chunkTime));
		} else {
			resetChunkTimeMetric(new Long(chunkTime));
		}
	}
	
	/**
	 * Gets the current time included in each chunk.
	 * 
	 * @return the Long representing the number of milliseconds in the current chunk size.
	 */
	public Long getChunkTime() {
		return chunkTime.get();
	}
	
	/**
	 * Gets the number of chunks included in the query.
	 * 
	 * @return the number of chunks included in the query given the chunk size.
	 */
	public int getNumberChunks() {
		return numChunks;
	}
	
	/**
	 * Gets the starting time of the pager object from the queries provided.
	 *
	 * @return the Long representing the millisecond start of the pager object.
	 */
	public Long getStartTime() {
		return start.get();
	}
	
	/**
	 * Gets the ending time of the pager object from the queries provided.
	 * 
	 * @return the Long representing the millisecond end of the pager object.
	 */
	public Long getEndTime() {
		return end.get();
	}
	
	/**
	 * Disposes of the current pager object. Shuts down the pager's associated scheduled executor
	 * service.
	 */
	public void dispose() {
		lock.writeLock().lock();
		try {
			SystemAssert.requireState(inUse.get(), "Cannot dispose of a MetricPager object that is no longer in use.");
			inUse.set(false);
		} finally {
			lock.writeLock().unlock();
		}
		_shutdownScheduledExecutorService();
	}

	/**
	 * Waits for datapoints to be available to return to the user as the next page is requested.
	 * 
	 * @param prevSize The number of pages that had been processed upon method invocation.
	 */
	synchronized private void getDataDPs(int prevSize) {
		lock.writeLock().lock();
		while (chunksProcessed == prevSize) {
			try {
				hasDataDP.await();
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
	
	/**
	 * Waits for metric data to be available to return to the user as the next page is requested.
	 * 
	 * @param prevSize The number of pages that had been processed upon method invocation.
	 */
	synchronized private void getDataMets(int prevSize) {
		lock.writeLock().lock();
		while (chunksProcessed == prevSize) {
			try {
				hasDataMet.await();
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
	
	/**
	 * Waits for datapoints to be available to the user as the next page is requested
	 * through the time provided.
	 * 
	 * @param endTime The Long timestamp of the final time of interest.
	 */
	private void waitForTimeDPs(Long endTime) {
		while (chunksProcessed == 0 || (shift().shift(lastEndTimestamp.get()) <= endTime && !complete.get())) {
			lock.readLock().lock();
			int currentlyProcessed = chunksProcessed;
			lock.readLock().unlock();
			getDataDPs(currentlyProcessed);
		}
	}
	
	/**
	 * Waits for metric data to be available to the user as the next page is requested
	 * through the time provided.
	 * 
	 * @param endTime The Long timestamp of the final time of interest.
	 */
	private void waitForTimeMets(Long endTime) {
		while (chunksProcessed == 0 || (shift().shift(lastEndTimestamp.get()) <= endTime && !complete.get())) {
			lock.readLock().lock();
			int currentlyProcessed = chunksProcessed;
			lock.readLock().unlock();
			getDataMets(currentlyProcessed);
		}
	}
	
	/**
	 * Generates the transformed datapoints for a specific window of time in the output (transformed) data.
	 * 
	 * @param startTime The Long representing the beginning of the transformed window, inclusive.
	 * @param endTime The Long representing the end of the transformed window, inclusive.
	 * @return The Map of transformed datapoints within the specified window according to the output times.
	 */
	synchronized private Map<Long, Double> getDPWindow(Long startTime, Long endTime) {
		waitForTimeDPs(endTime);
		lock.readLock().lock();
		Map<Long, Double> window = Collections.unmodifiableMap(datapoints.subMap(startTime, endTime + 1));
		lock.readLock().unlock();
		lock.writeLock().lock();
		currentChunkStartTime = new Long(startTime);
		currentViewingWindow = new Long(endTime - startTime);
		lock.writeLock().unlock();
		return window;
	}
	
	/**
	 * Generates the transformed metrics with data within a specific window of time in the 
	 * output (transformed) data.
	 * 
	 * @param startTime The Long representing the beginning of the transformed window, inclusive.
	 * @param endTime The Long representing the end of the transformed window, inclusive.
	 * @return The List of Metric containing the transformed metric data within the specified output window.
	 */
	synchronized private List<Metric> getMetricWindow(Long startTime, Long endTime) {
		//System.out.println("Immediately in method, start is " + startTime);
		waitForTimeMets(endTime);
		//System.out.println("DONE WAITING IN WINDOW FOR DATA, start time is " + startTime);
		//System.out.println("Metric has " + metrics);
		List<Metric> filteredMetric = new ArrayList<>();
		lock.readLock().lock();
		for (Metric m : metrics) {
			//System.out.println("Range is " + startTime + " - " + endTime);
			//System.out.println("M has " + new TreeMap<>(m.getDatapoints()));
			Metric mWindow = new Metric(m);
			mWindow.setDatapoints(new TreeMap<>(m.getDatapoints()).subMap(startTime, endTime + 1));
			//System.out.println("Set dps at " + mWindow.getDatapoints());
			filteredMetric.add(mWindow);
		}
		lock.readLock().unlock();
		lock.writeLock().lock();
		currentChunkStartTime = new Long(startTime);
		currentViewingWindow = new Long(endTime - startTime);
		lock.writeLock().unlock();
		//System.out.println("About to return " + filteredMetric);
		return Collections.unmodifiableList(filteredMetric);
	}
	
	/**
	 * Gets a specific range of datapoints produced by the transform, partitioned from the
	 * beginning of the pager with length according to the chunk size.
	 * 
	 * @param chunkNumber The chunk index of the chunk to view, beginning from zero for the first chunk.
	 * @return The Map containing the transformed datapoints within this chunk.
	 */
	public Map<Long, Double> getDPChunk(int chunkNumber) {
		SystemAssert.requireArgument(0 <= chunkNumber && chunkNumber < numChunks, "Cannot display a time outside the valid time window.");
		lock.readLock().lock();
		Long startTime = start.get() + chunkNumber * chunkTime.get();
		Long endTime = Math.min(startTime + chunkTime.get(), end.get());
		lock.readLock().unlock();
		return getDPWindowInputTimeRange(startTime, endTime);
	}
	
	/**
	 * Gets the metrics produced by the transform with datapoints corresponding to this
	 * metric's data within the chunk specified. The chunk is partitioned from the beginning
	 * of the pager with length according to the chunk size.
	 * 
	 * @param chunkNumber The chunk index of the chunk to view, beginning from zero for the first chunk.
	 * @return The List of Metric containing the transformed metrics and their datapoints within this chunk.
	 */
	synchronized public List<Metric> getMetricChunk(int chunkNumber) {
		//System.out.println("Getting chunk " + chunkNumber);
		SystemAssert.requireArgument(0 <= chunkNumber && chunkNumber < numChunks, "Cannot display a page outside the valid range of pages.");
		lock.readLock().lock();
		Long startTime = start.get() + chunkNumber * chunkTime.get();
		Long endTime = Math.min(startTime + chunkTime.get(), end.get());
		lock.readLock().unlock();
		//System.out.println("Calling for window " + startTime + ", " + endTime);
		return getMetricWindowInputTimeRange(startTime, endTime);
	}
	
	/**
	 * Gets the start time of the most recently viewed page.
	 * 
	 * @return The Long representing the millisecond start time of the most recently generated page.
	 */
	public Long getCurrentStartTime() {
		return new Long(currentChunkStartTime);
	}
	
	/**
	 * Gets the length of time considered in the most recently viewed page.
	 * 
	 * @return The Long representing the millisecond time range of the most recently viewed page.
	 */
	public Long getCurrentViewingWindowLength() {
		return new Long(currentViewingWindow);
	}
	
	/**
	 * Generates the map of transformed datapoints corresponding to the next page,
	 * beginning immediately after the most recently viewed page and of the same
	 * duration. The most recently viewed page cannot be the last page.
	 * 
	 * @return The Map containing the transformed datapoints for the next window of time.
	 */
	public Map<Long, Double> getNextWindowOfDP() {
		lock.readLock().lock();
		Long nextStart = currentChunkStartTime + currentViewingWindow;
		Long nextEnd = Math.min(nextStart + currentViewingWindow, end.get());
		lock.readLock().unlock();
		SystemAssert.requireState(nextStart < end.get(), "The last page was already being viewed. The next page does not exist.");
		
		return getDPWindow(nextStart, nextEnd);
	}
	
	/**
	 * Generates the list of transformed metrics corresponding to the next page,
	 * beginning immediately after the most recently viewed page and of the same
	 * duration. The most recently viewed page cannot be the last page.
	 * 
	 * @return The List of Metric containing the transformed metrics for the next window of time.
	 */
	public List<Metric> getNextWindowOfMetric() {
		lock.readLock().lock();
		Long nextStart = currentChunkStartTime + currentViewingWindow;
		Long nextEnd = Math.min(nextStart + currentViewingWindow, end.get());
		lock.readLock().unlock();
		SystemAssert.requireState(nextStart < Math.max(end.get(), shift().shift(end.get())), "The last page was already being viewed. The next page does not exist.");
		
		return getMetricWindow(nextStart, nextEnd);
	}
	
	/**
	 * Generates the map of transformed datapoints corresponding to the previous page,
	 * beginning immediately before the most recently viewed page and of the same
	 * duration. The most recently viewed page cannot be the first page.
	 * 
	 * @return The Map containing the transformed datapoints for the previous window of time.
	 */
	public Map<Long, Double> getPrevWindowOfDP() {
		lock.readLock().lock();
		Long prevStart = Math.max(currentChunkStartTime - currentViewingWindow, start.get());
		Long prevEnd = currentChunkStartTime;
		lock.readLock().unlock();
		SystemAssert.requireState(prevEnd > start.get(), "The first page was already being viewed. The previous page does not exist.");
		
		return getDPWindow(prevStart, prevEnd);
	}
	
	/**
	 * Generates the list of transformed metrics corresponding to the previous page,
	 * beginning immediately before the most recently viewed page and of the same
	 * duration. The most recently viewed page cannot be the first page.
	 * 
	 * @return The List of Metric containing the transformed metrics for the previous window of time.
	 */
	public List<Metric> getPrevWindowOfMetric() {
		lock.readLock().lock();
		Long prevStart = Math.max(getCurrentStartTime() - getCurrentViewingWindowLength(), start.get());
		Long prevEnd = getCurrentStartTime();
		lock.readLock().unlock();
		SystemAssert.requireState(prevEnd > start.get(), "The first page was already being viewed. The previous page does not exist.");
		
		return getMetricWindow(prevStart, prevEnd);
	}
	
	/**
	 * Generates the map of transformed datapoints corresponding to the transformation
	 * of the initial input points within the time range. These input timestamps may be different
	 * from the output timestamps but takes into account any shifting of the timestamps
	 * that have occurred in this transformation or in previous, chained transformations.
	 * 
	 * 		Ex: For a ShiftValueMapping Pager, let:
	 * 				input_datapoints = { 1000 : 20.0, 2000 : 25.0, 3000 : 22.0, 4000 : 30.0, 5000 : 33.0 }
	 * 				offset = 1 second (i.e. 1000ms)
	 * 			Then, getDPWindowInputTimeRange(1000, 3000) returns:
	 * 				output_datapoints = { 2000 : 20.0, 3000 : 25.0, 4000 : 22.0 }
	 * 
	 * @param startTime The Long timestamp of the input datapoint to begin the window, inclusive.
	 * @param endTime The Long timestamp of the input datapoint to end the window, inclusive.
	 * @return The Map of transformed datapoints that correspond to the input data of the specified window.
	 */
	public Map<Long, Double> getDPWindowInputTimeRange(Long startTime, Long endTime) {
		SystemAssert.requireArgument(startTime >= start.get() && startTime <= end.get(), "Please provide a valid start time within the query window.");
		SystemAssert.requireArgument(endTime >= start.get() && endTime <= end.get(), "Please provide a valid end time within the query window.");
		SystemAssert.requireArgument(startTime <= endTime, "The start time must be before the end time.");
		
		return getDPWindow(shift().shift(startTime), shift().shift(endTime));
	}

	/**
	 * Generates the list of transformed metrics corresponding to the transformation
	 * of the initial input points within the time range. These input timestamps may
	 * be different from the output timestamps but takes into account any shifting of
	 * the timestamps that have occurred in this transformation or previous, chained
	 * transformations.
	 * 
	 * 		Ex: For a MovingValueMapping Pager used with ShiftValueMapping, let:
	 *				input_metric_datpoints = { 1000 : 20.0, 2000 : 25.0, 3000 : 22.0, 4000 : 30.0, 5000 : 33.0 }
	 *				offset = 1 second (i.e. 1000ms)
	 *			Then, getMetricWindowInputTimeRange(1000, 3000) returns: 
	 *				output_metric_datapoints = { 2000 : 20.0, 3000 : 25.0, 4000 : 22.0 }
	 * 
	 * @param startTime The Long timestamp of the input data to begin the window, inclusive.
	 * @param endTime The Long timestamp of the input data to end the window, inclusive.
	 * @return The List of transformed Metrics with data corresponding to the input metric data of the specified window.
	 */
	public List<Metric> getMetricWindowInputTimeRange(Long startTime, Long endTime) {
		SystemAssert.requireArgument(startTime >= start.get() && startTime <= end.get(), "Please provide a valid start time within the query window.");
		SystemAssert.requireArgument(endTime >= start.get() && endTime <= end.get(), "Please provide a valid end time within the query window.");
		SystemAssert.requireArgument(startTime <= endTime, "The start time must be before the end time.");
		
		return getMetricWindow(shift().shift(startTime), shift().shift(endTime));
	}
	
	/**
	 * Generates the map of transformed datapoints whose transformed timestamps lie
	 * within the window specified. These output timestamps may be different from the
	 * initial input timestamps due to shifting that occurs within the transform or
	 * within previous chained transforms.
	 * The start and end times provided for the window must lie within the start and
	 * end time of the query, plus the additional shift included in this pager. That is,
	 * if the start time of the query is 1000 and the shift of the pager is +1000, the
	 * window start time 1500 would be invalid.
	 * 
	 * @param startTime The Long timestamp of the output data to begin the window, inclusive.
	 * @param endTime The Long timestamp of the output data to end the window, inclusive.
	 * @return The Map of transformed datapoints whose timestamps lie within the defined window.
	 */
	public Map<Long, Double> getDPWindowOutputTimeRange(Long startTime, Long endTime) {
		SystemAssert.requireArgument(startTime >= shift().shift(start.get()) && startTime <= shift().shift(end.get()),
				"Please provide a valid start time within the output data window.");
		SystemAssert.requireArgument(endTime >= shift().shift(start.get()) && endTime <= shift().shift(end.get()),
				"Please provide a valid end time within the output data window.");
		SystemAssert.requireArgument(startTime <= endTime, "The start time must be before the end time.");
		
		return getDPWindow(startTime, endTime);
	}
	
	/**
	 * Generates the list of transformed metric with metric data whose timestamps
	 * lie within the window specified. These output timestamps may be different
	 * from the initial input timestamps due to shifting that occurs within the
	 * transform or within previous transforms.
	 * The start and end times provided for the window must lie within the start and
	 * end time of the query, plus the additional shift included in this pager. That
	 * is, if the start time of the query is 1000 and the shift of the pager is +1000,
	 * the window start time 1500 would be invalid.
	 * 
	 * @param startTime The Long timestamp of the output data to begin the window, inclusive.
	 * @param endTime The Long timestamp of the ouptut data to end the window, inclusive.
	 * @return The List of transformed Metric with data whose timestamps lie within the defined window.
	 */
	public List<Metric> getMetricWindowOutputTimeRange(Long startTime, Long endTime) {
		SystemAssert.requireArgument(startTime >= shift().shift(start.get()) && startTime <= shift().shift(end.get()), 
				"Please provide a valid start time within the output data window.");
		SystemAssert.requireArgument(endTime >= shift().shift(start.get()) && endTime <= shift().shift(end.get()),
				"Please provide a valid end time within the output data window.");
		SystemAssert.requireArgument(startTime <= endTime, "The start time must be before the end time.");
		
		return getMetricWindow(startTime, endTime);
	}
	
	/**
	 * Gets a map of transformed datapoints from the start timestamp relative to the
	 * initial input datapoints and of the same duration as the most recently viewed page.
	 * 
	 * @param startTime The Long timestamp of the input data to begin the window at, inclusive.
	 * @return The Map containing the transformed datapoints within this window.
	 */
	public Map<Long, Double> getNewDPPageFromStartInput(Long startTime) {
		SystemAssert.requireArgument(startTime >= start.get() && startTime <= end.get(),
				"Please provide a valid start time for the page.");
		
		lock.readLock().lock();
		Long endTime = Math.min(startTime + currentViewingWindow, end.get());
		lock.readLock().unlock();
		return getDPWindowInputTimeRange(new Long(startTime), endTime);
	}
	
	/**
	 * Gets a list of transformed metrics with data corresponding to the next window
	 * from the start timestamp relative to the initial input datapoints and of the
	 * same duration as the most recently viewed page.
	 * 
	 * @param startTime The Long timestamp of the input data to begin the window at, inclusive.
	 * @return The List of transformed Metric with data lying within the specified window.
	 */
	public List<Metric> getNewMetricPageFromStartInput(Long startTime) {
		SystemAssert.requireArgument(startTime >= start.get() && startTime <= end.get(),
				"Please provide a valid start time for the page.");
		
		lock.readLock().lock();
		Long endTime = Math.min(startTime + currentViewingWindow, end.get());
		lock.readLock().unlock();
		
		return getMetricWindowInputTimeRange(new Long(startTime), endTime);
	}
	
	/**
	 * Gets a range of datapoints from the start timestamp relative to the output
	 * (transformed) datapoints and of the same duration as the most recently viewed
	 * page.
	 * The start timestamp must lie within the shifted bounds of the query. That is,
	 * if the query begins at 1000 and the pager has a shift of +1000, the start time
	 * 1500 is invalid.
	 * 
	 * @param startTime The Long timestamp of the transformed data to begin the window at, inclusive.
	 * @return The Map containing the transformed datapoints within this window.
	 */
	public Map<Long, Double> getNewDPPageFromStartOutput(Long startTime) {
		SystemAssert.requireArgument(startTime >= shift().shift(start.get()) && startTime <= shift().shift(end.get()),
				"Please provide a valid start time for the page.");
		
		lock.readLock().lock();
		Long endTime = Math.min(startTime + currentViewingWindow, shift().shift(end.get()));
		lock.readLock().unlock();
		
		return getDPWindowOutputTimeRange(new Long(startTime), endTime);
	}
	
	/**
	 * Gets the transformed metrics with data in the range from the start timestamp
	 * relative to the output (transformed) metric data timestamps and of the same 
	 * duration as the most recently viewed page.
	 * The start timestamp must lie within the shifted bounds of the query. That is, if
	 * the query begins at 1000 and the pager has a shift of +1000, the start time 1500
	 * is invalid.
	 * 
	 * @param startTime The Long timestamp of the transformed data to begin the window at, inclusive.
	 * @return The List of transformed Metric containing the data lying within the specified window.
	 */
	public List<Metric> getNewMetricPageFromStartOutput(Long startTime) {
		SystemAssert.requireArgument(startTime >= shift().shift(start.get()) && startTime <= shift().shift(end.get()),
				"Please provide a valid start time for the page.");
		lock.readLock().lock();
		Long fullPage = startTime + currentViewingWindow;
		lock.readLock().unlock();
		/* must release the lock so to acquire write lock */
		Long endTime = Math.min(fullPage, shift().shift(end.get()));
		return getMetricWindowOutputTimeRange(new Long(startTime), endTime);
	}

	/**
	 * Updates the end timestamp to reflect the timestamp of the most recently transformed data.
	 * 
	 * @param lastEndTimestamp The AtomicLong with the new end timestamp to use.
	 */
	protected void setEndTimestamp(AtomicLong lastEndTimestamp) {
		SystemAssert.requireState(lock.writeLock().isHeldByCurrentThread(), "This thread must have the writing lock to change the end timestamp!");
		this.lastEndTimestamp = lastEndTimestamp;
	}
	
	/**
	 * Updates the internal transformed datapoint representation to include the most
	 * recently transformed datapoints.
	 * 
	 * @param datapoints The Map of transformed datapoints for the calculated page.
	 */
	protected void pushDatapoints(Map<Long, Double> datapoints) {
		SystemAssert.requireState(lock.writeLock().isHeldByCurrentThread(), "This thread must have the writing lock to push more datapoints!");
		this.datapoints.putAll(datapoints);
	}
	
	/**
	 * Updates the internal transformed metric representation to include the most recently
	 * transformed metrics and their datapoints.
	 * 
	 * @param metrics The List of transformed Metric data for the calculated page.
	 */
	protected void pushMetrics(List<Metric> metrics) {
		SystemAssert.requireState(lock.writeLock().isHeldByCurrentThread(), "This thread must have the writing lock to push more metrics!");
		for (Metric m : metrics) {
			if (this.metrics.contains(m)) {
				this.metrics.get(this.metrics.indexOf(m)).addDatapoints(new HashMap<>(m.getDatapoints()));
			} else {
				Metric newMetric = new Metric(m);
				newMetric.setDatapoints(new HashMap<>(m.getDatapoints()));
				this.metrics.add(newMetric);
			}
		}
	}
	
	protected ScheduledExecutorService _createScheduledExecutorService() {
		return Executors.newScheduledThreadPool(1);
	}
	
	protected void _startScheduledExecutorService() {
		FetchTransformedDataThread fetchTransformedDataThread = new FetchTransformedDataThread();
		lock.writeLock().lock();
		try {
			task = _scheduledExecutorService.scheduleWithFixedDelay(fetchTransformedDataThread, 0L, 1000L, TimeUnit.MILLISECONDS);
		} finally {
			lock.writeLock().unlock();
		}
	}
	
	protected void _shutdownScheduledExecutorService() {
		_logger.info("Shutting down scheduled fetch transformed metric data executor service");
		_scheduledExecutorService.shutdown();
		try {
			if(!_scheduledExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
				_logger.warn("Shutdown of scheduled fetch transformed metric data timed out after 5 seconds.");
				_scheduledExecutorService.shutdownNow();
			}
		} catch (InterruptedException ex) {
			_logger.warn("Shutdown of executor service was interrupted.");
			Thread.currentThread().interrupt();
		}
	}
	
	/**
	 * Performs the transformation specified by this pager object on each page
	 * range to incrementally build up the transformed data for the pager.
	 */
	protected abstract void performTransform();
	
	/**
	 * Gets the MetricPageScanner object containing the data that is the
	 * result of the transformation specified by this pager.
	 * 
	 * @return The MetricPageScanner encapsulating the data resulting from this transformation.
	 */
	protected abstract MetricPageScanner getScanner();
	
	/**
	 * Gets the list of MetricPageScanner objects containing the data resulting
	 * from the transformation specified by this pager.
	 * @return
	 */
	protected abstract List<MetricScanner> getScannerList();
	
	/**
	 * Gets the shifting function associated with this pager object.
	 * @return The ScannerShiftFunction representing the shift of a timestamp from input to output.
	 */
	protected abstract ScannerShiftFunction shift();
	
	/**
	 * Inner class to concurrently process and transform the data while
	 * also making it available in these pages to the pager object.
	 * 
	 * @author adelaide.chambers
	 *
	 */
	private class FetchTransformedDataThread implements Runnable {
		
		@Override
		public void run() {
			performTransform();
			if (complete.get()) {
				task.cancel(true);
			}
		}
	}
}

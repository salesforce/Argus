package com.salesforce.dva.argus.service.tsdb;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.system.SystemAssert;

import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.annotation.concurrent.ThreadSafe;

import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.salesforce.dva.argus.system.SystemAssert.*;

/**
 * Encapsulates information about metric data resulting from a query that is returned to the client while a
 * synchronously gathering more metric data. Thread-safe class.
 * 
 * <p>Fields that determine uniqueness are:</p>
 * 
 * <ul>
 * 	<li>metric</li>
 * 	<li>query</li>
 * 	<li>pointer</li>
 * </ul>
 * 
 * @author Addie Chambers (adelaide.chambers@salesforce.com)
 *
 */
@ThreadSafe
public class MetricScanner {
	//~ Static fields ***************************************************************************************************************************************************************************
	
	private static Map<Metric, Map<MetricQuery, Set<MetricScanner>>> scanners = Collections.synchronizedMap(new HashMap<>());
	private static Double CHUNK_PERCENTAGE = 0.1;
	private static Double FETCH_PERCENTAGE = 0.5;
	
	//~ Instance fields *************************************************************************************************************************************************************************
	
	protected AtomicLong pointer;
	protected Map<Long, Double> datapoints = new HashMap<>();
	protected ReentrantReadWriteLock lock;
	private Long chunkSize;
	private AtomicLong lastStartTimestamp;
	private AtomicLong lastEndTimestamp;
	protected MetricQuery query;
	private TSDBService service;
	protected Metric metric;
	protected AtomicBoolean inUse;
	private int callsToFetch = 0;
	protected final Logger _logger = LoggerFactory.getLogger(getClass());
	protected ScheduledExecutorService _scheduledExecutorService;
	private AtomicBoolean fetching = new AtomicBoolean(false);
	
	//~ Constructors ****************************************************************************************************************************************************************************
	
	/**
	 * Creates a new metric scanner object.
	 * 
	 * Metric cannot be null. Intended to be used with a MetricPageScanner
	 * to push additional data.
	 * 
	 * @param metric The Metric to encapsulate.
	 */
	public MetricScanner(Metric metric) {
		SystemAssert.requireArgument(metric != null, "The metric cannot be null!");
		lock = new ReentrantReadWriteLock();
		
		lock.writeLock().lock();
		this.metric = new Metric(metric);
		this.metric.setDatapoints(new HashMap<>(metric.getDatapoints()));
		datapoints.putAll(metric.getDatapoints());
		pointer = datapoints.isEmpty() ? null : new AtomicLong(Collections.min(datapoints.keySet()));
		inUse = new AtomicBoolean(true);
		lock.writeLock().unlock();
	}
	
	/**
	 * Creates a new metric scanner object.
	 * 
	 * Datapoints cannot be null. Intended to be used with a
	 * MetricPageDatapointScanner to push additional data.
	 * 
	 * @param datapoints The Map of datapoints to encapsulate.
	 */
	public MetricScanner(Map<Long, Double> datapoints) {
		SystemAssert.requireArgument(datapoints != null, "The datapoints cannot be null!");
		lock = new ReentrantReadWriteLock();
		
		lock.writeLock().lock();
		this.datapoints.putAll(datapoints);
		pointer = datapoints.isEmpty() ? null : new AtomicLong(Collections.min(datapoints.keySet()));
		inUse = new AtomicBoolean(true);
		lock.writeLock().unlock();
	}
	
	/**
	 * Creates a new metric scanner object.
	 * 
	 * TSDB service, metric, and query cannot be null.
	 * 
	 * @param metric The Metric object encapsulating the data from the start of the query result.
	 * @param query The MetricQuery object to query.
	 * @param service The TSDBService object to use to communicate with the database.
	 * @param lastEndTimestamp The Long representing the final time from which we currently have data.
	 */
	public MetricScanner(Metric metric, MetricQuery query, TSDBService service, Long lastEndTimestamp) {
		requireArgument(service != null , "TSDBService cannot be null");
		requireArgument(metric != null , "Metric cannot be null");
		requireArgument(query != null , "Query cannot be null");
		requireState((CHUNK_PERCENTAGE > 0) && (CHUNK_PERCENTAGE <= 1) , "Chunk_percentage c must lie in the range 0 < c <= 1");
		requireState((FETCH_PERCENTAGE >= 0) && (FETCH_PERCENTAGE <= 1) , "Fetch_percentage f must lie in the range 0 <= f <= 1");
		
		lock = new ReentrantReadWriteLock();
		this.metric = new Metric(metric);
		this.metric.setDatapoints(new HashMap<>(metric.getDatapoints()));
		this.query = new MetricQuery(query);
		this.metric.setQuery(this.query);
		this.service = service;
		if (scanners.containsKey(metric) && scanners.get(metric).containsKey(query)) {
			scanners.get(metric).get(query).add(this);
		} else if (scanners.containsKey(metric)) {
			Set<MetricScanner> s = new HashSet<>();
			s.add(this);
			scanners.get(metric).put(query, s);
		} else {
			Set<MetricScanner> s = new HashSet<>();
			s.add(this);
			Map<MetricQuery, Set<MetricScanner>> entry = new HashMap<>();
			entry.put(query, s);
			scanners.put(metric, entry);
		}

		lock.writeLock().lock();
		datapoints.putAll(this.metric.getDatapoints() == null ? datapoints : this.metric.getDatapoints());
		this.lastEndTimestamp = lastEndTimestamp != null ? new AtomicLong(lastEndTimestamp) : datapoints.isEmpty() ? null : new AtomicLong(Collections.max(datapoints.keySet()));
		lastStartTimestamp = datapoints.isEmpty() ? new AtomicLong(query.getStartTimestamp()) : new AtomicLong(Collections.min(datapoints.keySet()));
		pointer = datapoints.isEmpty() ? null : new AtomicLong(Collections.min(datapoints.keySet()));
		inUse = new AtomicBoolean(true);
		lock.writeLock().unlock();
		
		chunkSize = Math.round((query.getEndTimestamp() - query.getStartTimestamp()) * CHUNK_PERCENTAGE);
		SystemAssert.requireArgument(!chunkSize.equals(0L) || query.getEndTimestamp().equals(query.getStartTimestamp()), "The start and end times of the query are too far apart. Don't do this to me!");
		_scheduledExecutorService = _createScheduledExecutorService();
	}
	
	/**
	 * Creates a copy of a metric scanner object.
	 * 
	 * @param scanner The valid Metric Scanner to clone.
	 */
	public MetricScanner(MetricScanner scanner) {
		lock = new ReentrantReadWriteLock();
		this.metric = scanner.getMetric();
		this.metric.setDatapoints(new HashMap<>(metric.getDatapoints()));
		this.query = scanner.getQuery();
		this.metric.setQuery(this.query);
		this.service = scanner.service;
		if (scanners.containsKey(metric) && scanners.get(metric).containsKey(query)) {
			scanners.get(metric).get(query).add(this);
		} else if (scanners.containsKey(metric)) {
			Set<MetricScanner> s = new HashSet<>();
			s.add(this);
			scanners.get(metric).put(query, s);
		} else {
			Set<MetricScanner> s = new HashSet<>();
			s.add(this);
			Map<MetricQuery, Set<MetricScanner>> entry = new HashMap<>();
			entry.put(query, s);
			scanners.put(metric, entry);
		}
		
		lock.writeLock().lock();
		datapoints.putAll(this.metric.getDatapoints() == null ? datapoints : this.metric.getDatapoints());
		this.lastEndTimestamp = new AtomicLong(scanner.getLastEndTimestamp());
		lastStartTimestamp = datapoints.isEmpty() ? new AtomicLong(query.getStartTimestamp()) : new AtomicLong(Collections.min(datapoints.keySet()));
		pointer = datapoints.isEmpty() ? null : new AtomicLong(Collections.min(datapoints.keySet()));
		inUse = new AtomicBoolean(true);
		lock.writeLock().unlock();
		
		chunkSize = Math.round((query.getEndTimestamp() - query.getStartTimestamp()) * CHUNK_PERCENTAGE);
		SystemAssert.requireArgument(!chunkSize.equals(0L) || query.getEndTimestamp().equals(query.getStartTimestamp()), "The start and end times of the query are too far apart. Don't do this to me!");
		_scheduledExecutorService = _createScheduledExecutorService();
	}
	
	//~ Methods ********************************************************************************************************************************************************************************
	
	/**
	 * Static method to determine whether there is already exists a metric scanner in use for a metric, query pair.
	 * 
	 * @param metric The Metric associated with the metric scanner.
	 * @param query The MetricQuery associated with the metric scanner.
	 * @return Whether there is at least one metric scanner in use for the given metric as a result of the given query.
	 */
	public static boolean existingScanner(Metric metric, MetricQuery query) {
		return scanners.containsKey(metric) && scanners.get(metric).containsKey(query);
	}
	
	/**
	 * Static method to find the MetricScanner object currently in use for a given metric, query pair. Creates
	 * one if no such scanner exists.
	 * 
	 * @param metric The Metric of the desired scanner.
	 * @param query The MetricQuery of the desired scanner.
	 * @param callingService The TSDBService to assign to the scanner if no such scanner already exists.
	 * @param endTime The Long for the final end timestamp to assign to the scanner if no such scanner already exists.
	 * @return The MetricScanner with the desired metric, query pair.
	 */
	public static Set<MetricScanner> findScanner(Metric metric, MetricQuery query, TSDBService callingService, Long endTime) {
		if (existingScanner(metric, query)) {
			return scanners.get(metric).get(query);
		}
		Set<MetricScanner> s = new HashSet<>();
		s.add(new MetricScanner(metric, query, callingService, endTime));
		Map<MetricQuery, Set<MetricScanner>> entry = new HashMap<>();
		entry.put(query, s);
		scanners.put(metric, entry);
		return entry.get(query);
	}
	
	/**
	 * Static method to reset the chunk percentage used for the metric scanners. This is the amount of the total
	 * query that is fetched at each point.
	 * 
	 * @param chunkPercentage The Double with the new chunk percentage to use. Must be within (0, 1).
	 */
	public static void setChunkPercentage(Double chunkPercentage) {
		SystemAssert.requireArgument(chunkPercentage > 0.0 && chunkPercentage < 1.0, "That is an invalid percentage for the chunk percentage, which must be in the range (0.0, 1.0)");
		MetricScanner.CHUNK_PERCENTAGE = new Double(chunkPercentage);
	}
	
	/**
	 * Static method to reset the fetch percentage used for the metric scanners. This is the percentage of data in
	 * our current chunk that, when reached, should cause the class to fetch more data.
	 * 
	 * @param fetchPercentage The Double with the new fetch percentage to use. Must be within (0, 1).
	 */
	public static void setFetchPercentage(Double fetchPercentage) {
		SystemAssert.requireArgument(fetchPercentage > 0.0 && fetchPercentage < 1.0, "That is an invalid percentage for the fetch percentage, which must be in the range (0.0, 1.0)");
		MetricScanner.FETCH_PERCENTAGE = new Double(fetchPercentage);
	}
	
	/**
	 * Static method to view the chunk percentage used by metric scanners.
	 * 
	 * @return The Double representing the chunk percentage, the amount of the total query fetched at each call to the TSDB.
	 */
	public static Double getChunkPercentage() {
		return new Double(CHUNK_PERCENTAGE);
	}
	
	/**
	 * Static method to view the fetch percentage used by metric scanners.
	 * 
	 * @return The Double representing the fetch percentage, the amount of the current chunk that should be processed before TSDB is called.
	 */
	public static Double getFetchPercentage() {
		return new Double(FETCH_PERCENTAGE);
	}
	
	//~ Methods ********************************************************************************************************************************************************************************
	
	/**
	 * Disposes of this metric scanner by removing this scanner from the list of active scanners, freeing up
	 * the metric, query pair for use in a different scanner object.
	 */
	public void dispose() {
		SystemAssert.requireState(existingScanner(metric, query), "Cannot dispose of a MetricScanner object that is no longer in use.");
		scanners.get(metric).get(query).remove(this);
		if (scanners.get(metric).get(query).isEmpty()) {
			scanners.get(metric).remove(query);
		}
		if (scanners.get(metric).isEmpty()) {
			scanners.remove(metric);
		}
		inUse.set(false);
		_shutdownScheduledExecutorService();
	}
	
	/**
	 * Retrieves the next data point of the metric. Checks if more data needs to be fetched asynchronously. Another
	 * data point must exist in the scanner to call this method.
	 * 
	 * @return The Map.Entry representing the next data point stored in the metric in chronological order.
	 */
	public Map.Entry<Long, Double> getNextDP() {
		asyncFetch();
		lock.readLock().lock();
		SystemAssert.requireState(this.hasNextDP(), "The Scanner must contain more datapoints in order to return the next datapoint.");
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
	
	/**
	 * Returns the next data point of the metric, or null if no more datapoints exist. This point may later be
	 * re-accessed by the scanner.
	 * 
	 * @return The Map.Entry representing the next data point stored in the metric in chronological order, or null if no more exist.
	 */
	public Map.Entry<Long, Double> peek() {
		asyncFetch();
		
		if (!this.hasNextDP()) {
			return null;
		}
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
		return dp;
	}
	
	/**
	 * Provides a copy of the metric query that generated this scanner.
	 * 
	 * @return The MetricQuery representing a clone of the query that generated this scanner.
	 */
	public MetricQuery getQuery() {
		return new MetricQuery(query);
	}
	
	/**
	 * Provides a copy of the metric encapsulated by this scanner.
	 * 
	 * @return The Metric representing a clone of this scanner's metric.
	 */
	public Metric getMetric() {
		lock.readLock().lock();
		Metric m = null;
		try {
			m = new Metric(metric);
			m.setDatapoints(new HashMap<>(m.getDatapoints()));
		} finally {
			lock.readLock().unlock();
		}
		return m;
	}
	
	/**
	 * Provides the identifying name of the metric encapsulated by the scanner.
	 * 
	 * @return The String representing the metric name.
	 */
	public String getMetricName() {
		return this.metric.getMetric();
	}
	
	/**
	 * Provides the identifying scope of the metric encapsulated by the scanner.
	 * 
	 * @return The String representing the metric scope.
	 */
	public String getMetricScope() {
		return this.metric.getScope();
	}
	
	/**
	 * Provides the identifying tags of the metric encapsulated by the scanner.
	 * 
	 * @return The Map of String -> String representing the metric's tags.
	 */
	public Map<String, String> getMetricTags() {
		return this.metric.getTags();
	}
	
	/**
	 * Whether the current scanner has been disposed.
	 * @return The boolean for whether the scanner is still in use or not.
	 */
	public boolean isInUse() {
		return inUse.get();
	}
	
	/**
	 * Provides the final timestamp currently included in the encapsulated data.
	 * 
	 * @return The last timestamp currently reached by this metric scanner object.
	 */
	private Long getLastEndTimestamp() {
		lock.readLock().lock();
		Long time = lastEndTimestamp.get();
		lock.readLock().unlock();
		return time;
	}
	
	/**
	 * Whether the metric scanner contains another data point as part of the query result.
	 * If not, removes the scanner from the list to allow for the scanner's metric, query pair to be reused.
	 * 
	 * @return If there are more data points as part of the result of the query.
	 */
	public boolean hasNextDP() {
		if (!inUse.get()) {
			return false;
		}
		if (pointer == null) {
			asyncFetch();
			
			while (pointer == null && lastEndTimestamp.get() < query.getEndTimestamp()) {
				asyncFetch();
			}
			if (pointer == null) {
				inUse.set(false);
				dispose();
				return false;
			}
			return true;
		}
		return true;
	}
	
	/**
	 * Finds the timestamp of the next data point included in this metric scanner.
	 * 
	 * @return The timestamp of the next data point in the metric scanner, or null if there are no later data points.
	 */
	protected AtomicLong nextTime() {
		Long next = null;
		lock.readLock().lock();
		for (Long time : datapoints.keySet()) {
			if ((pointer != null) && (time > pointer.get()) && ((next == null) || (time < next))) {
				next = time;
			}
		}
		lock.readLock().unlock();
		return next != null ? new AtomicLong(next) : null;
	}
	
	/**
	 * Checks whether more data should be fetched for the metric scanner. Fetches if we are out of data but have not yet
	 * completed the query window, or if we are FETCH_PERCENTAGE through our latest fetched data.
	 */
	private void asyncFetch() {
		lock.readLock().lock();
		Long point = null;
		Long lastEnd = null;
		Long lastStart = null;
		boolean fetch = true;
		try {
			point = pointer == null ? null : pointer.get();
			lastEnd = lastEndTimestamp.get();
			lastStart = lastStartTimestamp.get();
			fetch = fetching.get();
		} finally {
			lock.readLock().unlock();
		}
		if (point != null && lastEnd < query.getEndTimestamp()) {	
			if (point > Math.round((FETCH_PERCENTAGE * (lastEnd - lastStart))) + lastStart) {
				if (!fetch) {
					_startScheduledExecutorService();
				}
			}
		}
		else {
			if (!query.getEndTimestamp().equals(lastEnd)) {
				if (!fetch) {
					_startScheduledExecutorService();
				}
			}
		}
	}
	
	/**
	 * Updates the pointer to the timestamp of the final data point included in the metric scanner's data.
	 * 
	 * @param lastEndTimestamp The timestamp of the final data point contained by this metric scanner.
	 */
	private void setEndTimestamp(AtomicLong lastEndTimestamp) {
		SystemAssert.requireState(lock.writeLock().isHeldByCurrentThread(), "This thread must have the writing lock to change the end timestamp!");
		this.lastEndTimestamp = lastEndTimestamp;
	}
	
	/**
	 * Updates the data points associated with this metric as a result of fetching more data.
	 * Also updates the pointer to the final timestamp included in the provided data points if it has changed.
	 * Must be called from a method where the write lock is invoked to maintain thread safety.
	 * 
	 * @param datapoints The data points contained in the slice of time returned by the query.
	 */
	protected void pushDatapoints(Map<Long, Double> datapoints) {
		SystemAssert.requireState(lock.writeLock().isHeldByCurrentThread(), "This thread must have the writing lock to push more datapoints!");
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
		this.metric.addDatapoints(datapoints);
	}
	
	/**
	 * Counts the number of times we have fetched data for a metric scanner object.
	 * 
	 * @return The number of calls to fetch by this metric scanner.
	 */
	public int getCallsToFetch() {	
		return callsToFetch;
	}
	
	/**
	 * Updates the mutable attributes of the metric encapsulated in this metric scanner if they have
	 * been changed concurrently.
	 * 
	 * @param metric The metric returned by the most recent section of the query.
	 */
	protected void updateMetric(Metric metric) {
		this.metric.setNamespace(metric.getNamespace());
		this.metric.setDisplayName(metric.getDisplayName());
		this.metric.setUnits(metric.getUnits());
	}
	
	/**
	 * Gets more metric data for the next time slice in the query. Uses the TSDBService to obtain the metric data.
	 * Pushes the received data to metric scanners associated with all of the metrics received, including this one.
	 * Fills in gaps if we have generated lated data for a scanner but not the earlier data.
	 */
	private void fetch() throws InterruptedException {
		callsToFetch++;
		lock.readLock().lock();
		Long startTime = lastEndTimestamp.get();
		Long endTime = Math.min(query.getEndTimestamp(), startTime + chunkSize);
		if (Long.MAX_VALUE - chunkSize < startTime) {
			endTime = Long.MAX_VALUE;
		}
		lock.readLock().unlock();

		MetricQuery miniQuery = new MetricQuery(query.getScope(), query.getMetric(), query.getTags(), startTime, endTime);
		List<MetricQuery> miniQueries = new ArrayList<MetricQuery>();
		miniQueries.add(miniQuery);		
				
		Map<MetricQuery, List<Metric>> miniMetricMap = service.getMetrics(miniQueries);
		for (List<Metric> metrics : miniMetricMap.values()) {
			for (Metric miniMetric : metrics) {
				if (!scanners.containsKey(miniMetric) || !scanners.get(miniMetric).containsKey(query)) {	
					Set<MetricScanner> s = new HashSet<>();
					s.add(new MetricScanner(miniMetric, miniQuery, service, endTime));
					Map<MetricQuery, Set<MetricScanner>> entry = new HashMap<>();
					entry.put(query, s);
					if (!scanners.containsKey(miniMetric)) {
						scanners.put(miniMetric, new HashMap<>());
					}
					scanners.get(miniMetric).putAll(entry);
				}
				else {
					for (MetricScanner s : scanners.get(miniMetric).get(query)) {
						if (s.getLastEndTimestamp() == null || s.getLastEndTimestamp() < endTime) {
							if (startTime > s.getLastEndTimestamp()) {
								List<MetricQuery> filler = new ArrayList<>();
								filler.add(new MetricQuery(query.getScope(), query.getMetric(), query.getTags(),
										s.getLastEndTimestamp(), startTime));
								List<Metric> gaps = service.getMetrics(filler).get(filler.get(0));
								Metric gapMetric = gaps.get(gaps.indexOf(miniMetric));
								s.pushDatapoints(gapMetric.getDatapoints());
							}
							s.lock.writeLock().lock();
							s.pushDatapoints(miniMetric.getDatapoints());
							s.setEndTimestamp(new AtomicLong(endTime));
							s.updateMetric(miniMetric);
							s.lock.writeLock().unlock();
						}
					}
				}
			}
		}
		lock.writeLock().lock();
		lastStartTimestamp.set(startTime);
		setEndTimestamp(new AtomicLong(endTime)); // regardless of whether new data was received, update the time
		lock.writeLock().unlock();
	}
	
	/**
	 * Gets the shift function already encapsulated by a metric scanner
	 * upon its creation. A metric scanner that interacts with the database
	 * has no previous shift, so its shift function is a NOP.
	 * 
	 * @return A ShiftFunction representing a NOP.
	 */
	protected ScannerShiftFunction getShift() {
		ScannerShiftFunction shift = (Long t) -> t;
		return shift;
	}
	
	/**
	 * Converts a metric scanner into a string based off of its associated metric, query, and current pointer value.
	 */
	@Override
	public String toString() {
		String repr = "Metric : " + metric.toString() + " : Query : " + query.toString() + " : Pointer : " + pointer;
		return repr;
	}
	
	/**
	 * Determines whether two metric scanners are equal based off of the associated metric and query.
	 */
	@Override
	public boolean equals(Object other) {
		if (other == null) {
			return false;
		}
		if (this == other) {
			return true;
		}
		if (getClass() != other.getClass()) {
			return false;
		}
		
		MetricScanner o = (MetricScanner) other;
		
		return this.metric == o.metric && this.query == o.query && ((this.pointer == null && o.pointer == null) || this.pointer.equals(o.pointer));
	}
	
	/**
	 * Generates a hash code for the metric scanner based off of its encapsulated Metric and Metric Query.
	 */
	@Override
	public int hashCode() {
		final int prime = 17;
		
		int result = prime * this.metric.hashCode();
		result = result + prime * this.query.hashCode();
		result = result + prime * (pointer == null ? prime : (int) pointer.get());
		return result;
	}
	
	/**
	 * Creates the scheduled executor service to manage asynchronously fetching chunks of data.
	 * 
	 * @return The scheduled executor service for the metric scanner.
	 */
	protected ScheduledExecutorService _createScheduledExecutorService() {
		return Executors.newScheduledThreadPool(1);
	}
	
	/**
	 * Starts the scheduled executor service. Keeps track of when data is being fetched so that
	 * this is not repeatedly restarted while waiting for data.
	 */
	private void _startScheduledExecutorService() {
		FetchDataChunkThread fetchDataChunkThread = new FetchDataChunkThread();
		lock.writeLock().lock();
		try {
			fetching.set(true);
			_scheduledExecutorService.scheduleWithFixedDelay(fetchDataChunkThread, 0L, 500L, TimeUnit.MILLISECONDS);
		} finally {
			lock.writeLock().unlock();
		}
	}
	
	/**
	 * Shuts down the scheduled executor service.
	 */
	protected void _shutdownScheduledExecutorService() {
		_logger.info("Shutting down scheduled fetch metric scanner data chunk executor service");
		_scheduledExecutorService.shutdown();
		try {
			if (!_scheduledExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
				_logger.warn("Shutdown of scheduled fetch metric scanner data chunk service timed out after 5 seconds.");
				_scheduledExecutorService.shutdownNow();
			}
		} catch(InterruptedException ex) {
			_logger.warn("Shutdown of executor service was interrupted.");
			Thread.currentThread().interrupt();
		}
	}
	
	//~ Inner Classes  ******************************************************************************************************************************************
	
	/**
	 * Thread that fetches the next chunk of the query to be supplied to the metric scanner.
	 * 
	 * @author adelaide.chambers (adelaide.chambers@salesforce.com)
	 *
	 */
	private class FetchDataChunkThread implements Runnable {
		
		@Override
		public void run() {
			try {
				fetch();
			} catch (InterruptedException e) {
				_logger.info("Fetching was interrupted.");
			} finally {
				lock.writeLock().lock();
				try {
					fetching.set(false);
				} finally {
					lock.writeLock().unlock();
				}
			}
		}
	}
}
/* Copyright (c) 2-17, Salesforce.com, Inc. All rights reserved. */

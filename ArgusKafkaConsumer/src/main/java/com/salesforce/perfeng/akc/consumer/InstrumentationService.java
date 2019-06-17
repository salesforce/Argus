package com.salesforce.perfeng.akc.consumer;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.ImmutableMap;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.monitor.CounterMetric;
import com.salesforce.dva.argus.service.monitor.GaugeMetric;
import com.salesforce.dva.argus.service.monitor.MetricMXBean;
import com.salesforce.perfeng.akc.AKCConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.DoubleAdder;

import static com.salesforce.perfeng.akc.AKCAssert.requireArgument;
import static com.salesforce.perfeng.akc.AKCUtil.replaceUnsupportedChars;

public class InstrumentationService {

	private static final Logger LOGGER = LoggerFactory.getLogger(InstrumentationService.class);
	private static final String SCOPE = "ajna.consumer";
	private static final String HOSTNAME = getHostname();
	private final AtomicReference<Map<Metric, CounterMetric>> instrumentedCounterMetrics;
	private final Map<Metric, MetricMXBean> registeredMetrics;
	private static final int MAX_ADD_LATENCY_METRIC_ATTEMPTS = 100;

	// There only needs to be one running InstrumentationService instance
	private static InstrumentationService instance = null;

	// Counter metric names
	static final String DATAPOINTS_CONSUMED = "datapoints.consumed";
	static final String DATAPOINTS_CONSUMED_LATEST = "datapoints.consumed.latest";
	static final String DATAPOINTS_POSTED = "datapoints.posted";
	static final String DATAPOINTS_DROPPED_TOOLARGE = "datapoints.dropped.maxSizeExceeded";
	static final String ANNOTATIONS_CONSUMED = "annotations.consumed";
	static final String ANNOTATIONS_POSTED = "annotations.posted";
	static final String QUOTA_SERVICE_CONSUMED = "quota.consumed.service";
	static final String QUOTA_SUBSERVICE_CONSUMED = "quota.consumed.subservice";
	static final String QUOTA_CONSUMED = "quota.consumed";
	static final String SCHEMA_CONSUMED = "schema.consumed";
	static final String SCHEMA_POSTED = "schema.posted";
	static final String SCHEMA_DROPPED_TOOLARGE = "schema.dropped.maxSizeExceeded";
	static final String METRIC_BATCH_COUNT = "metric.batch.count";
	static final String ANNOTATIONS_BATCH_COUNT = "annotations.batch.count";
	static final String SCHEMA_BATCH_COUNT = "schema.batch.count";
	// Bad / dirty object counters
	static final String METRIC_DATAPOINTS_DEDUPED = "metric.datapoints.deduped";
	static final String DATAPOINTS_DROPPED = "datapoints.dropped";
	static final String DATAPOINTS_BLOCKED = "datapoints.blocked";
	static final String DATAPOINTS_TOO_OLD = "datapoints.dropped.late.arrival";
	static final String DATAPOINTS_TIMESTAMP_INVALID = "datapoints.dropped.timestamp.invalid";
	static final String ANNOTATIONS_DROPPED = "annotations.dropped";
	static final String ANNOTATIONS_DROPPED_TOOLARGE = "annotations.dropped.maxSizeExceeded";
	static final String ANNOTATIONS_BLOCKED = "annotations.blocked";
	static final String SCHEMA_DROPPED = "schema.dropped";
	static final String SCHEMA_BLOCKED = "schema.blocked";
	static final String SCHEMA_TOO_OLD = "schema.dropped.late.arrival";
	static final String SCHEMA_TIMESTAMP_INVALID = "schema.dropped.timestamp.invalid";
	// Latency metric names
	static final String METRIC_OVERALL_LATENCY = "metric.overall.latency";
	static final String ANNOTATIONS_OVERALL_LATENCY = "annotations.overall.latency";
	static final String SCHEMA_OVERALL_LATENCY = "schema.overall.latency";
	static final String METRIC_POLL_LATENCY = "metric.poll.latency";
	static final String ANNOTATIONS_POLL_LATENCY = "annotations.poll.latency";
	static final String SCHEMA_POLL_LATENCY = "schema.poll.latency";
	static final String METRIC_HANDLEBATCH_LATENCY = "metric.handleBatch.latency";
	static final String ANNOTATIONS_HANDLEBATCH_LATENCY = "annotations.handleBatch.latency";
	static final String SCHEMA_HANDLEBATCH_LATENCY = "schema.handleBatch.latency";
	static final String METRIC_PROCESS_LATENCY = "metric.process.latency";
	static final String ANNOTATIONS_PROCESS_LATENCY = "annotations.process.latency";
	static final String SCHEMA_PROCESS_LATENCY = "schema.process.latency";
	static final String QUOTA_EVALUATE_LATENCY = "quota.evaluate.latency";

	//histogram metric names
	static final String HISTOGRAM_CONSUMED = "histogram.consumed";
	static final String HISTOGRAM_POSTED = "histogram.posted";
	static final String HISTOGRAM_BATCH_COUNT = "histogram.batch.count";
	static final String HISTOGRAM_DROPPED = "histogram.dropped";
	static final String HISTOGRAM_DROPPED_TOOLARGE = "histogram.dropped.maxSizeExceeded";
	static final String HISTOGRAM_BLOCKED = "histogram.blocked";
	static final String HISTOGRAM_TOO_OLD = "histogram.dropped.late.arrival";
	static final String HISTOGRAM_TIMESTAMP_INVALID = "histogram.dropped.timestamp.invalid";
	//static final String HISTOGRAM_OVERALL_LATENCY = "";
	//static final String HISTOGRAM_POLL_LATENCY = "";
	//static final String HISTOGRAM_HANDLEBATCH_LATENCY = "";
	static final String HISTOGRAM_PROCESS_LATENCY = "histogram.process.latency";
	static final String HISTOGRAM_SCHEMA_CONSUMED = "histogram.schema.consumed";
	static final String HISTOGRAM_SCHEMA_POSTED = "histogram.schema.posted";
	static final String HISTOGRAM_SCHEMA_DROPPED = "histogram.schema.dropped";
	static final String HISTOGRAM_SCHEMA_DROPPED_TOOLARGE = "histogram.schema.dropped.maxSizeExceeded";
	static final String HISTOGRAM_SCHEMA_BLOCKED = "histogram.schema.blocked";
	static final String HISTOGRAM_SCHEMA_TOO_OLD = "histogram.schema.dropped.late.arrival";
	static final String HISTOGRAM_SCHEMA_TIMESTAMP_INVALID = "histogram.schema.dropped.timestamp.invalid";
	static final String HISTOGRAM_SCHEMA_BATCH_COUNT = "histogram.schema.batch.count";
	static final String HISTOGRAM_SCHEMA_PROCESS_LATENCY = "histogram.schema.process.latency";

	//Lag offset metric names
	static final String METRIC_CONSUMER_LAG = "metric.consumer.lag";


	@VisibleForTesting
	protected static final List<String> DATAPOINT_METRICS = Arrays.asList(
			DATAPOINTS_CONSUMED,
			DATAPOINTS_POSTED,
			METRIC_DATAPOINTS_DEDUPED,
			DATAPOINTS_BLOCKED,
			DATAPOINTS_TOO_OLD,
			DATAPOINTS_TIMESTAMP_INVALID,
			DATAPOINTS_DROPPED_TOOLARGE,
			DATAPOINTS_DROPPED,
			HISTOGRAM_CONSUMED,
			HISTOGRAM_POSTED,
			HISTOGRAM_BLOCKED,
			HISTOGRAM_TOO_OLD,
			HISTOGRAM_TIMESTAMP_INVALID,
			HISTOGRAM_DROPPED_TOOLARGE,
			HISTOGRAM_DROPPED);
	private static final List<String> SCHEMA_METRICS = Arrays.asList(
			SCHEMA_CONSUMED,
			SCHEMA_POSTED,
			SCHEMA_BLOCKED,
			SCHEMA_DROPPED_TOOLARGE,
			SCHEMA_TOO_OLD,
			SCHEMA_TIMESTAMP_INVALID,
			SCHEMA_DROPPED,
			HISTOGRAM_SCHEMA_CONSUMED,
			HISTOGRAM_SCHEMA_POSTED,
			HISTOGRAM_SCHEMA_BLOCKED,
			HISTOGRAM_SCHEMA_DROPPED_TOOLARGE,
			HISTOGRAM_SCHEMA_TOO_OLD,
			HISTOGRAM_SCHEMA_TIMESTAMP_INVALID,
			HISTOGRAM_SCHEMA_DROPPED);
	private static final List<String> ANNOTATIONS_METRICS = Arrays.asList(
			ANNOTATIONS_CONSUMED,
			ANNOTATIONS_POSTED,
			ANNOTATIONS_BLOCKED,
			ANNOTATIONS_DROPPED,
			ANNOTATIONS_DROPPED_TOOLARGE);

	private static final double[] HIGH_LATENCY_METRIC_BUCKET_LIMITS_MILLISECONDS = new double[] {2000, 5000, 10000, 20000, 40000, 80000};
	private static final double[] MID_LATENCY_METRIC_BUCKET_LIMITS_MILLISECONDS = new double[] {500, 1000, 2000, 5000, 10000, 20000};
	private static final double[] LOW_LATENCY_METRIC_BUCKET_LIMITS_MILLISECONDS = new double[] {10, 20, 50, 100, 200, 500};
	private static final double[] DEFAULT_LATENCY_METRIC_BUCKET_LIMITS_MILLISECONDS = LOW_LATENCY_METRIC_BUCKET_LIMITS_MILLISECONDS;

	static final Map<String, double[]> LATENCY_METRIC_BUCKET_LIMITS_MILLISECONDS = new ImmutableMap.Builder<String, double[]>()
			.put(METRIC_OVERALL_LATENCY, MID_LATENCY_METRIC_BUCKET_LIMITS_MILLISECONDS)
			.put(METRIC_POLL_LATENCY, MID_LATENCY_METRIC_BUCKET_LIMITS_MILLISECONDS)
			.put(METRIC_HANDLEBATCH_LATENCY, MID_LATENCY_METRIC_BUCKET_LIMITS_MILLISECONDS)
			.put(METRIC_PROCESS_LATENCY, LOW_LATENCY_METRIC_BUCKET_LIMITS_MILLISECONDS)
			.put(HISTOGRAM_PROCESS_LATENCY, LOW_LATENCY_METRIC_BUCKET_LIMITS_MILLISECONDS)
			.put(ANNOTATIONS_OVERALL_LATENCY, MID_LATENCY_METRIC_BUCKET_LIMITS_MILLISECONDS)
			.put(ANNOTATIONS_POLL_LATENCY, MID_LATENCY_METRIC_BUCKET_LIMITS_MILLISECONDS)
			.put(ANNOTATIONS_HANDLEBATCH_LATENCY, MID_LATENCY_METRIC_BUCKET_LIMITS_MILLISECONDS)
			.put(ANNOTATIONS_PROCESS_LATENCY, LOW_LATENCY_METRIC_BUCKET_LIMITS_MILLISECONDS)
			.put(SCHEMA_OVERALL_LATENCY, HIGH_LATENCY_METRIC_BUCKET_LIMITS_MILLISECONDS)
			.put(SCHEMA_POLL_LATENCY, HIGH_LATENCY_METRIC_BUCKET_LIMITS_MILLISECONDS)
			.put(SCHEMA_HANDLEBATCH_LATENCY, MID_LATENCY_METRIC_BUCKET_LIMITS_MILLISECONDS)
			.put(SCHEMA_PROCESS_LATENCY, LOW_LATENCY_METRIC_BUCKET_LIMITS_MILLISECONDS)
			.put(QUOTA_EVALUATE_LATENCY, new double[] {5, 10, 20, 50, 100})
			.build();

	private final TSDBService tsdbService;
	private Thread instrumentMetricsThread;
	private final String site;

	private final AtomicReference<Map<Metric, LatencyMetricValue>> instrumentedLatencyMetrics;

	private static boolean exportToJMX = true;
	private final MBeanServer mbeanServer;

	synchronized static InstrumentationService getInstance(TSDBService tsdbService) {
		if (instance == null) {
			instance = new InstrumentationService(tsdbService);
			instance.instrument();
		}
		return instance;
	}

	@VisibleForTesting
	Map<Metric, CounterMetric> getInstrumentedCounterMetrics() {
		return instrumentedCounterMetrics.get();
	}

	@VisibleForTesting
	Map<Metric, LatencyMetricValue> getInstrumentedLatencyMetrics() {
		return instrumentedLatencyMetrics.get();
	}

	@VisibleForTesting
	Map<Metric, MetricMXBean> getRegisteredMetrics() {
		return registeredMetrics;
	}

	InstrumentationService(TSDBService tsdbService) {
		this(tsdbService, true, ManagementFactory.getPlatformMBeanServer());
	}
	
	@VisibleForTesting
	InstrumentationService(TSDBService tsdbService, boolean isExporting, MBeanServer mBeanServer) {
		String bootstrapServers = AKCConfiguration.getParameter(AKCConfiguration.Parameter.BOOTSTRAP_SERVERS);
		site = replaceUnsupportedChars(bootstrapServers);
		this.tsdbService = tsdbService;
		this.instrumentedLatencyMetrics = new AtomicReference<>();
		this.instrumentedLatencyMetrics.set(new ConcurrentHashMap<>());
		InstrumentationService.exportToJMX = isExporting;
		this.instrumentedCounterMetrics = new AtomicReference<Map<Metric, CounterMetric>>();
		this.instrumentedCounterMetrics.set(new ConcurrentHashMap<Metric, CounterMetric>());
		this.registeredMetrics = new ConcurrentHashMap<>();
		this.mbeanServer = mBeanServer;
	}

	private static String getHostname() {
		String hostname = System.getenv("HOSTNAME");
		if (hostname != null) {
			return hostname;
		}
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (Exception ex) {
			return "unknown-host";
		}
	}

	public void instrument() {
		if(instrumentMetricsThread != null && instrumentMetricsThread.isAlive()) {
			LOGGER.info("Instrument metrics thread already running.");
		} else {
			LOGGER.info("Starting singleton instrument metrics thread.");
			instrumentMetricsThread = new Thread(new InstrumentMetricsThread(), "instrument-metrics-thread");
			instrumentMetricsThread.start();
			LOGGER.info("Singleton instrument metrics thread started for ConsumerType " + AKCConfiguration.getParameter(AKCConfiguration.Parameter.CONSUMER_TYPE));
		}
	}

	public void setCounterValue(String metric, Double value, Map<String, String> tags) {
		requireArgument(metric != null && !metric.isEmpty(), "Metric to instrument cannot be null or empty.");

		Metric key = constructMetric(metric, tags, false);
		instrumentedCounterMetrics.get().computeIfAbsent(key, k -> getCounterMXBeanInstance(k)).setValue(value);
	}

	public void updateCounter(String metric, double delta, Map<String, String> tags) {
		updateCounter(metric, delta, tags, false);
	}

	public void updateCounter(String metric, double delta, Map<String, String> tags, boolean useQuotaOptimizeTags) {
		requireArgument(metric != null && !metric.isEmpty(), "Metric to instrument cannot be null or empty.");

		Metric key = constructMetric(metric, tags, useQuotaOptimizeTags);
		instrumentedCounterMetrics.get().computeIfAbsent(key, k -> getCounterMXBeanInstance(k)).addValue(delta);
	}

	public void updateTimer(String metric, double latencyMS, Map<String, String> tags) {
		updateTimer(metric, latencyMS, tags, false);
	}

	public void updateTimer(String metric, double latencyMS, Map<String, String> tags, boolean useQuotaOptimizeTags) {
		requireArgument(metric != null && !metric.isEmpty(), "Metric to instrument cannot be null or empty.");

		Metric key = constructMetric(metric, tags, useQuotaOptimizeTags);
		Map<Metric, LatencyMetricValue> latencyMetricsMap = instrumentedLatencyMetrics.get();
		for (int i = 0; i < MAX_ADD_LATENCY_METRIC_ATTEMPTS; i++) {
			LatencyMetricValue currentLMV = latencyMetricsMap.get(key);
			double[] latencyBucketLimits = getLatencyMetricBucketLimits(metric);
			if (currentLMV == null) {
				LatencyMetricValue newLMV = new LatencyMetricValue(latencyBucketLimits, latencyMS);
				currentLMV = latencyMetricsMap.putIfAbsent(key, newLMV);
				if (currentLMV == null) {
					return;
				}
			} else {
				LatencyMetricValue newLMV = new LatencyMetricValue(latencyBucketLimits, currentLMV, latencyMS);
				if (latencyMetricsMap.replace(key, currentLMV, newLMV)) {
					return;
				}
			}
		}
		LOGGER.trace("Reached max attempts to add latency metric, giving up for metric=" + metric + " value=" + latencyMS);
	}

	@VisibleForTesting
	Metric constructMetric(String metricName, Map<String, String> tags, boolean useQuotaOptimizeTags) {
		Metric metric = new Metric(SCOPE, metricName);
		metric.setTags(tags);
		if(!useQuotaOptimizeTags) {
			metric.setTag("cluster", site);
			metric.setTag("uuid", HOSTNAME + AKCConfiguration.getParameter(AKCConfiguration.Parameter.ID));
		} else {
			metric.setTag("device", HOSTNAME);
			metric.setTag("groupId", AKCConfiguration.getParameter(AKCConfiguration.Parameter.GROUP_ID));
		}
		return metric;
	}

	@VisibleForTesting
	Metric constructMetricCopy(Metric m, String metric, long timestamp, double value) {
		Metric copy = new Metric(m);
		copy.setMetric(metric);
		Map<Long, Double> dataPoints = new HashMap<>(1);
		dataPoints.put(timestamp, value);
		copy.setDatapoints(dataPoints);
		return copy;
	}

	@VisibleForTesting
	double[] getLatencyMetricBucketLimits(String latencyMetricName) {
		return Optional.<double[]>ofNullable(LATENCY_METRIC_BUCKET_LIMITS_MILLISECONDS.get(latencyMetricName))
				.orElse(DEFAULT_LATENCY_METRIC_BUCKET_LIMITS_MILLISECONDS);
	}

	@VisibleForTesting
	protected CounterMetric getCounterMXBeanInstance(Metric m) {
		LOGGER.debug("Get CounterMetric=" + m.getMetric() + m.getTags());
		if (InstrumentationService.exportToJMX) {
			return (CounterMetric) registeredMetrics.computeIfAbsent(m, k -> createAndRegisterCounterMXBean(k));
		} else {
			return createCounterMXBean(m);
		}
	}

	private CounterMetric createAndRegisterCounterMXBean(Metric m) {
		CounterMetric b = createCounterMXBean(m);
		registerMBean(b);
		LOGGER.debug("Created and registered CounterMetric=" + b.getObjectName());
		return b;
	}

	private CounterMetric createCounterMXBean(Metric m) {
	    return new CounterMetric(m, ".count");
    }

	protected GaugeMetric getGaugeMXBeanInstance(Metric m) {
		LOGGER.debug("Get GaugeMetric=" + m.getMetric() + m.getTags());
		if (InstrumentationService.exportToJMX) {
			return (GaugeMetric) registeredMetrics.computeIfAbsent(m, k -> createAndRegisterGaugeMXBean(k));
		} else {
			return new GaugeMetric(m);
		}
	}

	private GaugeMetric createAndRegisterGaugeMXBean(Metric m) {
		GaugeMetric b = new GaugeMetric(m);
		registerMBean(b);
		LOGGER.debug("Created and registered GaugeMetric=" + b.getObjectName());
		return b;
	}

	private void registerMBean(MetricMXBean b) {
		try {
			mbeanServer.registerMBean(b, new ObjectName(b.getObjectName()));
		} catch (InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException
				| MalformedObjectNameException e) {
			LOGGER.error("Error registering MetricMXBean with name={}:", b.getObjectName(), e);
		}
	}

	class InstrumentMetricsThread implements Runnable {

		private static final long INTERVAL_IN_MILLIS = 60000;

		@Override
		public void run() {
			while(!Thread.currentThread().isInterrupted()) {
				pushInstrumentedMetrics();
				sleep();
			}

			if(Thread.currentThread().isInterrupted()) {
				LOGGER.info("Instrument metrics thread was interrupted. Will post remaining metrics and exit.");
				pushInstrumentedMetrics();
			} else {
				LOGGER.error("This should never happen.");
			}
			LOGGER.info("Instrument metrics thread execution completed.");
		}

		void sleep() {
			try {
				LOGGER.info("Sleeping for {}s before pushing instrumented metrics.", INTERVAL_IN_MILLIS / 1000);
				Thread.sleep(INTERVAL_IN_MILLIS);
			} catch (InterruptedException ie) {
				LOGGER.warn("Metrics instrumentation interrupted.");
				Thread.currentThread().interrupt();
			}
		}

		void pushInstrumentedMetrics() {

			boolean consumedNothing = false;
			if (instrumentedCounterMetrics.get().isEmpty()) {
				consumedNothing = true;
			}

			// pull snapshot of current counters and reset map
			final Map<Metric, CounterMetric> countersSnapshot = instrumentedCounterMetrics.getAndSet(new ConcurrentHashMap<Metric, CounterMetric>());

			// Record default 0 for basic metrics in case nothing ever got dropped, posted, etc.
			if (AKCConfiguration.getParameter(AKCConfiguration.Parameter.CONSUMER_TYPE).equals(ConsumerType.METRICS.toString())) {
				DATAPOINT_METRICS.forEach(metric -> countersSnapshot.computeIfAbsent(constructMetric(metric, null, false), k -> getCounterMXBeanInstance(k)));
			} else if (AKCConfiguration.getParameter(AKCConfiguration.Parameter.CONSUMER_TYPE).equals(ConsumerType.SCHEMA.toString())) {
				SCHEMA_METRICS.forEach(metric -> countersSnapshot.computeIfAbsent(constructMetric(metric, null, false), k -> getCounterMXBeanInstance(k)));
			} else if (AKCConfiguration.getParameter(AKCConfiguration.Parameter.CONSUMER_TYPE).equals(ConsumerType.ANNOTATIONS.toString())) {
				ANNOTATIONS_METRICS.forEach(metric -> countersSnapshot.computeIfAbsent(constructMetric(metric, null, false), k -> getCounterMXBeanInstance(k)));
			}

			final long timestamp = (System.currentTimeMillis() / 60000) * 60000L;
			for (Entry<Metric, CounterMetric> entry : countersSnapshot.entrySet()) {
				Map<Long, Double> dataPoints = new HashMap<>(1);

				dataPoints.put(timestamp, entry.getValue().computeNewGaugeValueAndResetGaugeAdder());
				entry.getKey().setDatapoints(dataPoints);
			}

			// pull snapshot of current latencies and reset map
			final Map<Metric, LatencyMetricValue> latenciesSnapshot = instrumentedLatencyMetrics.getAndSet(new ConcurrentHashMap<Metric, LatencyMetricValue>());

			final String bucketCountSuffix = ".bucketCount";
			final String bucketLimitTagName = "bucketLimit";
			final Map<Metric, GaugeMetric> latencyMetrics = new HashMap<>();
			for (Entry<Metric, LatencyMetricValue> e : latenciesSnapshot.entrySet()) {
				Metric key = e.getKey();
				LatencyMetricValue value = e.getValue();
				Metric mMax = constructMetricCopy(key, key.getMetric() + ".max", timestamp, value.getMaxValue());
				GaugeMetric gMax = getGaugeMXBeanInstance(mMax);
				gMax.setValue(value.getMaxValue());
				latencyMetrics.put(mMax, gMax);
				Metric mMin = constructMetricCopy(key, key.getMetric() + ".min", timestamp, value.getMinValue());
				GaugeMetric gMin = getGaugeMXBeanInstance(mMin);
				gMin.setValue(value.getMinValue());
				latencyMetrics.put(mMin, gMin);
				Metric mMean = constructMetricCopy(key, key.getMetric() + ".mean", timestamp, value.getMeanValue());
				GaugeMetric gMean = getGaugeMXBeanInstance(mMean);
				gMean.setValue(value.getMeanValue());
				latencyMetrics.put(mMean, gMean);
				Metric mSum = constructMetricCopy(key, key.getMetric() + ".sum", timestamp, value.getSum());
				GaugeMetric gSum = getGaugeMXBeanInstance(mSum);
				gSum.setValue(value.getSum());
				latencyMetrics.put(mSum, gSum);
				Metric mCount = constructMetricCopy(key, key.getMetric() + ".count", timestamp, value.getCount());
				GaugeMetric gCount = getGaugeMXBeanInstance(mCount);
				gCount.setValue((double)value.getCount());
				latencyMetrics.put(mCount, gCount);

				double[] latencyBucketLimits = getLatencyMetricBucketLimits(key.getMetric());
				double prevBucketMax = 0;
				int i = 0;
				for (; i < latencyBucketLimits.length; i++) {
					double currentBucketMax = latencyBucketLimits[i];
					String bucketLimit = String.format("%.0f-%.0f", prevBucketMax, currentBucketMax);
					Metric m = constructMetric(key.getMetric() + bucketCountSuffix,
							ImmutableMap.of(bucketLimitTagName, bucketLimit),
							false);
					m.setDatapoints(ImmutableMap.<Long, Double>of(timestamp, (double)value.getBucketCounts()[i]));
					GaugeMetric gm = getGaugeMXBeanInstance(m);
					gm.setValue((double)value.getBucketCounts()[i]);
					latencyMetrics.put(m, gm);

					prevBucketMax = currentBucketMax;
				}
				String bucketLimit = String.format("%.0f-max", prevBucketMax);
				Metric m = constructMetric(key.getMetric() + bucketCountSuffix,
						ImmutableMap.of(bucketLimitTagName, bucketLimit),
						false);
				m.setDatapoints(ImmutableMap.<Long, Double>of(timestamp, (double)value.getBucketCounts()[i]));
				GaugeMetric gm = getGaugeMXBeanInstance(m);
				gm.setValue((double)value.getBucketCounts()[i]);
				latencyMetrics.put(m, gm);
			}

			latencyMetrics.values().stream().forEach(g -> g.computeNewGaugeValueAndResetGaugeAdder());

			try {
				if (consumedNothing) {
					LOGGER.info("Last 60 seconds: Did not consume or post any objects of type " + AKCConfiguration.getParameter(AKCConfiguration.Parameter.CONSUMER_TYPE));
				} else {
					if (AKCConfiguration.getParameter(AKCConfiguration.Parameter.CONSUMER_TYPE).equals(ConsumerType.METRICS.toString())) {
						logSummary(countersSnapshot, DATAPOINT_METRICS);
					} else if (AKCConfiguration.getParameter(AKCConfiguration.Parameter.CONSUMER_TYPE).equals(ConsumerType.SCHEMA.toString())) {
						logSummary(countersSnapshot, SCHEMA_METRICS);
					} else if (AKCConfiguration.getParameter(AKCConfiguration.Parameter.CONSUMER_TYPE).equals(ConsumerType.ANNOTATIONS.toString())) {
						logSummary(countersSnapshot, ANNOTATIONS_METRICS);
					}
				}
				final List<Metric> metrics = new LinkedList<Metric>(latencyMetrics.keySet());
				metrics.addAll(countersSnapshot.keySet());
				LOGGER.trace("pushInstrumentedMetrics metrics=" + metrics);

				tsdbService.putMetrics(metrics);
			} catch(Exception e) {
				LOGGER.warn("Failed to post metrics to TSDB", e);
			}
		}
	}

	private void logSummary(Map<Metric, CounterMetric> countersSnapshot, List<String> labels) {

		Map<String, DoubleAdder> summaryCounters = new HashMap<>();

		StringBuilder builder = new StringBuilder();

		for (Metric metric: countersSnapshot.keySet()) {

			String metricName = metric.getMetric();

			if (labels.contains(metricName)) {
			    if (metric.getDatapoints() != null && metric.getDatapoints().values() != null &&
                        !metric.getDatapoints().values().isEmpty()) {
			        Double val = metric.getDatapoints().values().iterator().next();
			        summaryCounters.computeIfAbsent(metricName, k -> new DoubleAdder()).add(val);
                }
			}
		}

		for (String metricName : labels) {
			builder.append("\n\t" + metricName + " " + summaryCounters.get(metricName).doubleValue());
		}

		LOGGER.info("Last 60 seconds:" + builder.toString());
	}

	public void dispose() {
		if(instrumentMetricsThread != null && instrumentMetricsThread.isAlive()) {
			LOGGER.info("Stopping instrument metrics thread");
			instrumentMetricsThread.interrupt();
			try {
				instrumentMetricsThread.join();
				tsdbService.dispose();
			} catch (InterruptedException ex) {
				LOGGER.warn("Force stopping final instrumentation during InstrumentationService shutdown phase");
			}
		}
	}

	/**
	 * This class provides a summary of latency metric values.
	 */
	static class LatencyMetricValue {
		// The max latency value
		private final double maxValue;
		// The min latency value
		private final double minValue;
		// The sum of all latency values
		private final double sum;
		// The count of latency values
		private final int count;
		/**
		 * This represents the frequency distribution of latencies. It contains the counts of latency values which fall
		 * into each bucket. Buckets represent a range of latencies, for example:
		 * bucket[0] contains count of latencies between 0-100ms, bucket[1] between 100-200ms,
		 * bucket[2] between 200ms-500ms, bucket[3] for greater than 500ms.
		 */
		private final int[] bucketCounts;

		/**
		 *
		 * @param bucketLimits Each element in this array describes the max latency of the corresponding bucket, the min
		 *                     latency for the first bucket is assumed to be 0. This will be used to track the frequency
		 *                     distribution of latencies in buckets.
		 * @param newValue New latency value.
		 */
		LatencyMetricValue(double[] bucketLimits, double newValue) {
			this.maxValue = newValue;
			this.minValue = newValue;
			this.sum = newValue;
			this.count = 1;

			validateBucketLimits(bucketLimits);
			this.bucketCounts = new int[bucketLimits.length + 1];
			for (int j = 0; j < bucketLimits.length; j++) {
				if (newValue < bucketLimits[j]) {
					this.bucketCounts[j]++;
					return;
				}
			}
			this.bucketCounts[bucketLimits.length]++;
		}

		/**
		 *
		 * @param bucketLimits Each element in this array describes the max latency of the corresponding bucket, the min
		 *                     latency for the first bucket is assumed to be 0. This will be used to track the frequency
		 *                     distribution of latencies in buckets.
		 * @param lmv Existing LatencyMetricValue summary that will be updated with new latency value (newValue).
		 * @param newValue New latency value.
		 */
		LatencyMetricValue(double[] bucketLimits, LatencyMetricValue lmv, double newValue) {
			this.maxValue = Math.max(newValue, lmv.maxValue);
			this.minValue = Math.min(newValue, lmv.minValue);
			this.sum = lmv.sum + newValue;
			this.count = lmv.count + 1;

			validateBucketLimits(bucketLimits);
			boolean added = false;
			int len = bucketLimits.length;
			this.bucketCounts = new int[len + 1];
			for (int j = 0; j < len; j++) {
				if (newValue < bucketLimits[j] && !added) {
					this.bucketCounts[j] = lmv.bucketCounts[j] + 1;
					added = true;
				} else {
					this.bucketCounts[j] = lmv.bucketCounts[j];
				}
			}
			this.bucketCounts[len] = added ? lmv.bucketCounts[len] : lmv.bucketCounts[len] + 1;
		}

		double getMaxValue() {
			return maxValue;
		}
		double getMinValue() {
			return minValue;
		}
		double getSum() {
			return sum;
		}
		int getCount() {
			return count;
		}
		double getMeanValue() {
			return sum/count;
		}
		int[] getBucketCounts() {
			return bucketCounts;
		}

		private void validateBucketLimits(double[] bucketLimits) {
			requireArgument(bucketLimits != null && bucketLimits.length > 0, "Metric to instrument cannot be null or empty.");
			double i = 0;
			for (double j : bucketLimits) {
				if (j <= i) {
					throw new IllegalArgumentException(String.format("bucketLimits values should be greater than zero and in ascending order", bucketLimits));
				}
				i = j;
			}
		}
	}
}

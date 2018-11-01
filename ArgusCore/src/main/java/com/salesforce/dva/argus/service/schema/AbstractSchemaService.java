package com.salesforce.dva.argus.service.schema;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.HashMap;
import java.util.Map;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

import com.salesforce.dva.argus.entity.KeywordQuery;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.MetricSchemaRecord;
import com.salesforce.dva.argus.entity.MetricSchemaRecordQuery;
import com.salesforce.dva.argus.entity.MetatagsRecord;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemConfiguration;

/**
 * Implementation of the abstract schema service class
 *
 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
 */
public abstract class AbstractSchemaService extends DefaultService implements SchemaService {
	private static final long POLL_INTERVAL_MS = 10 * 60 * 1000L;
	private static final int DAY_IN_SECONDS = 24 * 60 * 60;
	private static final int HOUR_IN_SECONDS = 60 * 60;


	/* Have three separate bloom filters one for metrics schema, one only for scope names schema and one only for scope name and metric name schema.
	 * Since scopes will continue to repeat more often on subsequent kafka batch reads, we can easily check this from the  bloom filter for scopes only.
	 * Hence we can avoid the extra call to populate scopenames index on ES in subsequent Kafka reads.
	 * The same logic applies to scope name and metric name schema.
	 */
	protected static BloomFilter<CharSequence> bloomFilter;
	protected static BloomFilter<CharSequence> bloomFilterScopeOnly;
	protected static BloomFilter<CharSequence> bloomFilterScopeAndMetricOnly;
	protected static BloomFilter<CharSequence> bloomFilterMetatags;

	protected final MonitorService _monitorService;
        private int randomBloomAppend;
	private int bloomFilterExpectedNumberInsertions;
	private double bloomFilterErrorRate;
	private int bloomFilterScopeOnlyExpectedNumberInsertions;
	private double bloomFilterScopeOnlyErrorRate;
	private int bloomFilterScopeAndMetricOnlyExpectedNumberInsertions;
	private double bloomFilterScopeAndMetricOnlyErrorRate;
	private int bloomFilterMetatagsExpectedNumberInsertions;
	private double bloomFilterMetatagsErrorRate;
	private final Logger _logger = LoggerFactory.getLogger(getClass());
	private final Thread _bloomFilterMonitorThread;
	protected final boolean _syncPut;
	private int bloomFilterFlushHourToStartAt;
	private ScheduledExecutorService scheduledExecutorService;
        private String bfTagsStateFilename;
	protected final boolean bloomFileWritingEnabled;


	protected AbstractSchemaService(SystemConfiguration config, MonitorService monitorService) {
		super(config);

		_monitorService = monitorService;

                try {
                    randomBloomAppend = Math.abs(InetAddress.getLocalHost().getHostName().hashCode());
                } catch (IOException io) {
                    _logger.error("failed to create randomBloomAppend", io);
                    randomBloomAppend = 12345;
                }
		bloomFileWritingEnabled = Boolean.parseBoolean(config.getValue(Property.BLOOM_FILE_WRITING_ENABLED.getName(),
                                                                               Property.BLOOM_FILE_WRITING_ENABLED.getDefaultValue()));

                String bfStateBaseDir = config.getValue(Property.BF_STATE_BASE_DIR.getName(),
                                                        Property.BF_STATE_BASE_DIR.getDefaultValue());
                bfTagsStateFilename = bfStateBaseDir + "/bloomfilter_tags.state." +
                    config.getValue(SystemConfiguration.ARGUS_INSTANCE_ID, "noid");

		bloomFilterExpectedNumberInsertions = Integer.parseInt(config.getValue(Property.BLOOMFILTER_EXPECTED_NUMBER_INSERTIONS.getName(),
				Property.BLOOMFILTER_EXPECTED_NUMBER_INSERTIONS.getDefaultValue()));
		bloomFilterErrorRate = Double.parseDouble(config.getValue(Property.BLOOMFILTER_ERROR_RATE.getName(),
				Property.BLOOMFILTER_ERROR_RATE.getDefaultValue()));

		bloomFilterScopeOnlyExpectedNumberInsertions = Integer.parseInt(config.getValue(Property.BLOOMFILTER_SCOPE_ONLY_EXPECTED_NUMBER_INSERTIONS.getName(),
				Property.BLOOMFILTER_SCOPE_ONLY_EXPECTED_NUMBER_INSERTIONS.getDefaultValue()));
		bloomFilterScopeOnlyErrorRate = Double.parseDouble(config.getValue(Property.BLOOMFILTER_SCOPE_ONLY_ERROR_RATE.getName(),
				Property.BLOOMFILTER_SCOPE_ONLY_ERROR_RATE.getDefaultValue()));

		bloomFilterScopeAndMetricOnlyExpectedNumberInsertions = Integer.parseInt(config.getValue(Property.BLOOMFILTER_SCOPE_AND_METRIC_ONLY_EXPECTED_NUMBER_INSERTIONS.getName(),
				Property.BLOOMFILTER_SCOPE_AND_METRIC_ONLY_EXPECTED_NUMBER_INSERTIONS.getDefaultValue()));
		bloomFilterScopeAndMetricOnlyErrorRate = Double.parseDouble(config.getValue(Property.BLOOMFILTER_SCOPE_AND_METRIC_ONLY_ERROR_RATE.getName(),
				Property.BLOOMFILTER_SCOPE_AND_METRIC_ONLY_ERROR_RATE.getDefaultValue()));

		bloomFilterMetatagsExpectedNumberInsertions =
                    Integer.parseInt(config.getValue(Property.BLOOMFILTER_METATAGS_EXPECTED_NUMBER_INSERTIONS.getName(),
                                                     Property.BLOOMFILTER_METATAGS_EXPECTED_NUMBER_INSERTIONS.getDefaultValue()));
		bloomFilterMetatagsErrorRate =
                    Double.parseDouble(config.getValue(Property.BLOOMFILTER_METATAGS_ERROR_RATE.getName(),
                                                       Property.BLOOMFILTER_METATAGS_ERROR_RATE.getDefaultValue()));
                createOrReadBloomFilter();

		bloomFilterScopeOnly = BloomFilter.create(Funnels.stringFunnel(Charset.defaultCharset()), bloomFilterScopeOnlyExpectedNumberInsertions , bloomFilterScopeOnlyErrorRate);
		bloomFilterScopeAndMetricOnly = BloomFilter.create(Funnels.stringFunnel(Charset.defaultCharset()),
				bloomFilterScopeAndMetricOnlyExpectedNumberInsertions , bloomFilterScopeAndMetricOnlyErrorRate);
		bloomFilterMetatags = BloomFilter.create(Funnels.stringFunnel(Charset.defaultCharset()),
				bloomFilterMetatagsExpectedNumberInsertions , bloomFilterMetatagsErrorRate);

		_syncPut = Boolean.parseBoolean(
				config.getValue(Property.SYNC_PUT.getName(), Property.SYNC_PUT.getDefaultValue()));

		_bloomFilterMonitorThread = new Thread(new BloomFilterMonitorThread(), "bloom-filter-monitor");
		_bloomFilterMonitorThread.start();

		bloomFilterFlushHourToStartAt = getBloomFilterFlushHourToStartAt();
		createScheduledExecutorService(bloomFilterFlushHourToStartAt);
	}

	@Override
	public void put(Metric metric) {
		requireNotDisposed();
		SystemAssert.requireArgument(metric != null, "Metric cannot be null.");
		put(Arrays.asList(metric));
	}

	@Override
	public void put(List<Metric> metrics) {
		requireNotDisposed();
		SystemAssert.requireArgument(metrics != null, "Metric list cannot be null.");

		// Create a list of metricsToPut that do not exist on the BLOOMFILTER and then call implementation
		// specific put with only those subset of metricsToPut.
		List<Metric> metricsToPut = new ArrayList<>(metrics.size());
		Set<String> scopesToPut = new HashSet<>(metrics.size());

		Set<Pair<String, String>> scopesAndMetricsNamesToPut = new HashSet<>(metrics.size());
                Map<String, MetatagsRecord> metatagsToPut = new HashMap<>();

		for(Metric metric : metrics) {
			// check metric schema bloom filter
			if(metric.getTags().isEmpty()) {
				// if metric does not have tags
				String key = constructKey(metric, null, null);
				boolean found = bloomFilter.mightContain(key);
				if(!found) {
					metricsToPut.add(metric);
				}
			} else {
				// if metric has tags
				boolean newTags = false;
				for(Entry<String, String> tagEntry : metric.getTags().entrySet()) {
					String key = constructKey(metric, tagEntry.getKey(), tagEntry.getValue());
					boolean found = bloomFilter.mightContain(key);
					if(!found) {
						newTags = true;
					}
				}

				if(newTags) {
					metricsToPut.add(metric);
				}
			}

			String scopeName = metric.getScope();
			String metricName = metric.getMetric();

			// Check scope only bloom filter
			String key = constructScopeOnlyKey(scopeName);
			boolean found = bloomFilterScopeOnly.mightContain(key);
			if(!found) {
				scopesToPut.add(scopeName);
			}

			// Check scope and metric only bloom filter
			key = constructScopeAndMetricOnlyKey(scopeName, metricName);
			found = bloomFilterScopeAndMetricOnly.mightContain(key);
			if(!found) {
				scopesAndMetricsNamesToPut.add(Pair.of(scopeName, metricName));
			}

                        // Check if metatags are present and populate accordingly
                        MetatagsRecord mtags = metric.getMetatagsRecord();
                        if(mtags != null) {
                            key = mtags.getKey();
                            if(key != null) {
                                found = bloomFilterMetatags.mightContain(key);
                                if(!found) {
                                    metatagsToPut.put(key, mtags);
                                }
                            }
                        }
		}

		implementationSpecificPut(metricsToPut, scopesToPut, scopesAndMetricsNamesToPut, metatagsToPut);
	}

	/*
	 * Calls the implementation specific write for indexing the records
	 *
	 * @param  metrics    The metrics metadata that will be written to a separate index.
	 * @param  scopeNames The scope names that will be written to a separate index.
	 * @param  scopesAndMetricNames The scope and metric names that will be written to a separate index.
	 */
	protected abstract void implementationSpecificPut(List<Metric> metrics,
                                                          Set<String> scopeNames,
                                                          Set<Pair<String, String>> scopesAndMetricNames,
                                                          Map<String, MetatagsRecord> metatagsToPut);

	@Override
	public void dispose() {
		requireNotDisposed();
		if (_bloomFilterMonitorThread != null && _bloomFilterMonitorThread.isAlive()) {
			_logger.info("Stopping bloom filter monitor thread.");
			_bloomFilterMonitorThread.interrupt();
			_logger.info("Bloom filter monitor thread interrupted.");
			try {
				_logger.info("Waiting for bloom filter monitor thread to terminate.");
				_bloomFilterMonitorThread.join();
			} catch (InterruptedException ex) {
				_logger.warn("Bloom filter monitor thread was interrupted while shutting down.");
			}
			_logger.info("System monitoring stopped.");
		} else {
			_logger.info("Requested shutdown of bloom filter monitor thread aborted, as it is not yet running.");
		}
		shutdownScheduledExecutorService();
	}

	@Override
	public abstract Properties getServiceProperties();

	@Override
	public abstract List<MetricSchemaRecord> get(MetricSchemaRecordQuery query);

	@Override
	public abstract List<MetricSchemaRecord> getUnique(MetricSchemaRecordQuery query, RecordType type);

	@Override
	public abstract List<String> browseUnique(MetricSchemaRecordQuery query, RecordType type, int indexLevel);

	@Override
	public abstract List<MetricSchemaRecord> keywordSearch(KeywordQuery query);

	protected String constructKey(Metric metric, String tagk, String tagv) {
		return constructKey(metric.getScope(),
				metric.getMetric(),
				tagk,
				tagv,
				metric.getNamespace(),
				metric.getMetatagsRecord()==null?null:metric.getMetatagsRecord().getMetatagValue(MetricSchemaRecord.RETENTION_DISCOVERY));
	}

	protected String constructKey(String scope, String metric, String tagk, String tagv, String namespace, String retention) {

		StringBuilder sb = new StringBuilder(scope);

		if(!StringUtils.isEmpty(metric)) {
			sb.append('\0').append(metric);
		}

		if(!StringUtils.isEmpty(namespace)) {
			sb.append('\0').append(namespace);
		}

		if(!StringUtils.isEmpty(tagk)) {
			sb.append('\0').append(tagk);
		}

		if(!StringUtils.isEmpty(tagv)) {
			sb.append('\0').append(tagv);
		}

		//there is use case where users simply want to update the retention without touching rest of a metric
		if(!StringUtils.isEmpty(retention)) {
			sb.append('\0').append(retention);
		}

		// Add randomness for each instance of bloom filter running on different
		// schema clients to reduce probability of false positives that metric schemas are not written to ES
		sb.append('\0').append(randomBloomAppend);

		return sb.toString();
	}

	protected String constructScopeOnlyKey(String scope) {

		return constructKey(scope, null, null, null, null, null);
	}

	protected String constructScopeAndMetricOnlyKey(String scope, String metric) {

		return constructKey(scope, metric, null, null, null, null);
	}

	protected int getNumHoursUntilTargetHour(int targetHour){
		_logger.info("Initialized bloom filter flushing out, at {} hour of day", targetHour);
		Calendar calendar = Calendar.getInstance();
		int hour = calendar.get(Calendar.HOUR_OF_DAY);
		return hour < targetHour ? (targetHour - hour) : (targetHour + 24 - hour);
	}

	/*
	 * Have a different flush start hour for schema committers based on hostname, to prevent thundering herd problem.
	 */
	private int getBloomFilterFlushHourToStartAt() {
		int bloomFilterFlushHourToStartAt = 0;
		try {
			bloomFilterFlushHourToStartAt = Math.abs(InetAddress.getLocalHost().getHostName().hashCode() % 24);
		} catch (UnknownHostException e) {
			_logger.warn("BloomFilter UnknownHostException", e);
		}
		_logger.info("BloomFilter flush hour to start at {}th hour of day", bloomFilterFlushHourToStartAt);
		return bloomFilterFlushHourToStartAt;
	}

    private void createOrReadBloomFilter() {
        File bfFile = new File(this.bfTagsStateFilename);
        if (bloomFileWritingEnabled && bfFile.exists() ) {
            _logger.info("State file for bloom tags exists, using it to pre-populate bloom");
            try (InputStream inputStream = new FileInputStream(bfFile)) {
                this.bloomFilter = BloomFilter.readFrom(inputStream,
                                                        Funnels.stringFunnel(Charset.defaultCharset()));
            } catch (IOException io) {
                _logger.error("tags bloomfilter read error, not using prev state", io);
                this.bloomFilter = BloomFilter.create(Funnels.stringFunnel(Charset.defaultCharset()),
                                                      bloomFilterExpectedNumberInsertions ,
                                                      bloomFilterErrorRate);
            }
            return;
        }

        _logger.info("State file for bloom tags NOT present or bloomFileWritingEnabled is false, starting fresh bloom");
        this.bloomFilter = BloomFilter.create(Funnels.stringFunnel(Charset.defaultCharset()),
                                              bloomFilterExpectedNumberInsertions ,
                                              bloomFilterErrorRate);
    }

    private void writeTagsBloomFilterToFile() {
        if (!bloomFileWritingEnabled) {
            return;
        }

        File bfTagsFile = new File(this.bfTagsStateFilename);
        if (!bfTagsFile.getParentFile().exists()) {
            bfTagsFile.getParentFile().mkdir();
        }
        try (OutputStream out = new FileOutputStream(bfTagsFile)) {
            bloomFilter.writeTo(out);
            _logger.info("Succesfully wrote tags bloomfilter to file {}", this.bfTagsStateFilename);
        } catch (IOException io) {
            _logger.error("Failed to write tags bf to file", io);
        }
    }


	private void createScheduledExecutorService(int targetHourToStartAt){
		scheduledExecutorService = Executors.newScheduledThreadPool(1);
		int initialDelayInSeconds = getNumHoursUntilTargetHour(targetHourToStartAt) * HOUR_IN_SECONDS;
		BloomFilterFlushThread bloomFilterFlushThread = new BloomFilterFlushThread();
		scheduledExecutorService.scheduleAtFixedRate(bloomFilterFlushThread, initialDelayInSeconds, DAY_IN_SECONDS, TimeUnit.SECONDS);
	}

	private void shutdownScheduledExecutorService(){
		_logger.info("Shutting down scheduled bloom filter flush executor service");
		scheduledExecutorService.shutdown();
		try {
			scheduledExecutorService.awaitTermination(10, TimeUnit.SECONDS);
		} catch (InterruptedException ex) {
			_logger.warn("Shutdown of executor service was interrupted.");
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * The set of implementation specific configuration properties.
	 *
	 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
	 */
	public enum Property {
		SYNC_PUT("service.property.schema.sync.put", "false"),
	        BLOOM_FILE_WRITING_ENABLED("service.property.schema.bloom.file.writing.enabled", "false"),
                BF_STATE_BASE_DIR("service.property.schema.bf.state.base.dir", "bloomstate"),
		BLOOMFILTER_EXPECTED_NUMBER_INSERTIONS("service.property.schema.bloomfilter.expected.number.insertions", "40"),
		BLOOMFILTER_ERROR_RATE("service.property.schema.bloomfilter.error.rate", "0.00001"),

		/*
		* Estimated Filter Size using bloomFilter 1 million entries
		* https://hur.st/bloomfilter/?n=1000000&p=1.0E-5&m=&k= 2.86MiB
		* Storing in a Set 100K entries with avg length of 15 chars would be 100K * 15 * 2 B = 30B * 100K = 3 MB
		* If # of entries is 1 million, then it would be 30 MB resulting in savings in space.
		*/

		BLOOMFILTER_SCOPE_ONLY_EXPECTED_NUMBER_INSERTIONS("service.property.schema.bloomfilter.scope.only.expected.number.insertions", "40"),
		BLOOMFILTER_SCOPE_ONLY_ERROR_RATE("service.property.schema.bloomfilter.scope.only.error.rate", "0.00001"),

		/*
		 * Estimated Filter Size using bloomFilter 500 million entries
		 * https://hur.st/bloomfilter/?n=10000000&p=1.0E-5&m=&k= 1.39GiB
		 * Storing in a Set 100M entries with avg length of 30 chars would be 100M * 30 * 2 B = 60B * 100M = 6 GB
		 * If # of entries is 500 million, then it would be 30 GB resulting in savings in space.
		*/

		BLOOMFILTER_SCOPE_AND_METRIC_ONLY_EXPECTED_NUMBER_INSERTIONS("service.property.schema.bloomfilter.scope.and.metric.only.expected.number.insertions", "40"),
		BLOOMFILTER_SCOPE_AND_METRIC_ONLY_ERROR_RATE("service.property.schema.bloomfilter.scope.and.metric.only.error.rate", "0.00001"),

		/*
		* Estimated Filter Size using bloomFilter 1 million entries
		* https://hur.st/bloomfilter/?n=1000000&p=1.0E-5&m=&k= 2.86MiB
		* Storing in a Set 100K entries with avg length of 15 chars would be 100K * 15 * 2 B = 30B * 100K = 3 MB
		* If # of entries is 1 million, then it would be 30 MB resulting in savings in space.
		*/

		BLOOMFILTER_METATAGS_EXPECTED_NUMBER_INSERTIONS("service.property.schema.bloomfilter.metatags.expected.number.insertions", "1000000"),
		BLOOMFILTER_METATAGS_ERROR_RATE("service.property.schema.bloomfilter.metatags.error.rate", "0.00001");


		private final String _name;
		private final String _defaultValue;

		private Property(String name, String defaultValue) {
			_name = name;
			_defaultValue = defaultValue;
		}

		/**
		 * Returns the property name.
		 *
		 * @return  The property name.
		 */
		public String getName() {
			return _name;
		}

		/**
		 * Returns the default value for the property.
		 *
		 * @return  The default value.
		 */
		public String getDefaultValue() {
			return _defaultValue;
		}
	}


	//~ Inner Classes ********************************************************************************************************************************

	/**
	 * Bloom Filter monitoring thread.
	 *
	 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
	 */
	private class BloomFilterMonitorThread implements Runnable {
		@Override
		public void run() {
			_logger.info("Initialized randomBloomAppend for bloom filter key = {}", randomBloomAppend);
			while (!Thread.currentThread().isInterrupted()) {
				_sleepForPollPeriod();
				if (!Thread.currentThread().isInterrupted()) {
					try {
						_checkBloomFilterUsage();
					} catch (Exception ex) {
						_logger.warn("Exception occurred while checking bloom filter usage.", ex);
					}
				}
			}
		}

		private void _checkBloomFilterUsage() {
			_monitorService.modifyCounter(MonitorService.Counter.BLOOMFILTER_APPROXIMATE_ELEMENT_COUNT, bloomFilter.approximateElementCount(), null);
			_monitorService.modifyCounter(MonitorService.Counter.BLOOMFILTER_SCOPE_ONLY_APPROXIMATE_ELEMENT_COUNT, bloomFilterScopeOnly.approximateElementCount(), null);
			_monitorService.modifyCounter(MonitorService.Counter.BLOOMFILTER_SCOPE_AND_METRIC_ONLY_APPROXIMATE_ELEMENT_COUNT, bloomFilterScopeAndMetricOnly.approximateElementCount(), null);
			_monitorService.modifyCounter(MonitorService.Counter.BLOOMFILTER_METATAGS_APPROXIMATE_ELEMENT_COUNT, bloomFilterMetatags.approximateElementCount(), null);

			_logger.info("Metrics Bloom expected error rate = {}", bloomFilter.expectedFpp());
			_logger.info("Scope only Bloom expected error rate = {}", bloomFilterScopeOnly.expectedFpp());
			_logger.info("Scope and metric only Bloom expected error rate = {}", bloomFilterScopeAndMetricOnly.expectedFpp());
			_logger.info("Metic Metatags Bloom expected error rate = {}", bloomFilterMetatags.expectedFpp());
		}

		private void _sleepForPollPeriod() {
			try {
				_logger.info("Sleeping for {}s before checking bloom filter statistics.", POLL_INTERVAL_MS / 1000);
				Thread.sleep(POLL_INTERVAL_MS);
			} catch (InterruptedException ex) {
				_logger.warn("AbstractSchemaService memory monitor thread was interrupted.");
				Thread.currentThread().interrupt();
			}
		}
	}

	private class BloomFilterFlushThread implements Runnable {
		@Override
		public void run() {
			try{
				_flushBloomFilter();
			} catch (Exception ex) {
				_logger.warn("Exception occurred while flushing bloom filter.", ex);
			}
		}

		private void _flushBloomFilter() {
			_logger.info("Flushing out bloom filter entries");
                        // Write the main tags bloom filter to file first before flushing
                        writeTagsBloomFilterToFile();

			bloomFilter = BloomFilter.create(Funnels.stringFunnel(Charset.defaultCharset()), bloomFilterExpectedNumberInsertions , bloomFilterErrorRate);
			bloomFilterScopeOnly = BloomFilter.create(Funnels.stringFunnel(Charset.defaultCharset()), bloomFilterScopeOnlyExpectedNumberInsertions , bloomFilterScopeOnlyErrorRate);
			bloomFilterScopeAndMetricOnly = BloomFilter.create(Funnels.stringFunnel(Charset.defaultCharset()),
					bloomFilterScopeAndMetricOnlyExpectedNumberInsertions , bloomFilterScopeAndMetricOnlyErrorRate);
			bloomFilterMetatags = BloomFilter.create(Funnels.stringFunnel(Charset.defaultCharset()),
                                                                       bloomFilterMetatagsExpectedNumberInsertions , bloomFilterMetatagsErrorRate);
			/* Don't need explicit synchronization to prevent slowness majority of the time*/
		}
	}
}

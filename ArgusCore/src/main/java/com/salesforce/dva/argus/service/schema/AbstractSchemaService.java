package com.salesforce.dva.argus.service.schema;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;
import com.salesforce.dva.argus.entity.AbstractSchemaRecord;
import com.salesforce.dva.argus.entity.KeywordQuery;
import com.salesforce.dva.argus.entity.MetatagsRecord;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.MetricSchemaRecord;
import com.salesforce.dva.argus.entity.MetricSchemaRecordQuery;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Implementation of the abstract schema service class
 *
 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
 */
public abstract class AbstractSchemaService extends DefaultService implements SchemaService {
	private static final long POLL_INTERVAL_MS = 10 * 60 * 1000L;
	private static final int DAY_IN_SECONDS = 24 * 60 * 60;


	/* Have three separate bloom filters one for metrics schema, one only for scope names schema and one only for scope name and metric name schema.
	 * Since scopes will continue to repeat more often on subsequent kafka batch reads, we can easily check this from the  bloom filter for scopes only.
	 * Hence we can avoid the extra call to populate scopenames index on ES in subsequent Kafka reads.
	 * The same logic applies to scope name and metric name schema.
	 */
	protected static BloomFilter<CharSequence> createdBloom;
	protected static BloomFilter<CharSequence> modifiedBloom;

	protected final MonitorService _monitorService;


	private int createdBloomExpectedNumberInsertions;
	private double createdBloomErrorRate;
	private int modifiedBloomExpectedNumberInsertions;
	private double modifiedBloomErrorRate;

	private final Logger _logger = LoggerFactory.getLogger(getClass());
	private final Thread _bloomFilterMonitorThread;
	private final SystemConfiguration config;
	protected final boolean _syncPut;
	private int bloomFilterFlushHourToStartAt;
	private ScheduledExecutorService scheduledExecutorService;
	private String createdBloomFileName;
	private String modifiedBloomFileName;
	protected final boolean bloomFileWritingEnabled;


	protected AbstractSchemaService(SystemConfiguration config, MonitorService monitorService) {
		super(config);
		this.config = config;
		_monitorService = monitorService;

		bloomFileWritingEnabled = Boolean.parseBoolean(config.getValue(Property.BLOOM_FILE_WRITING_ENABLED.getName(),
				Property.BLOOM_FILE_WRITING_ENABLED.getDefaultValue()));

		String bfStateBaseDir = config.getValue(Property.BF_STATE_BASE_DIR.getName(),
				Property.BF_STATE_BASE_DIR.getDefaultValue());
		createdBloomFileName = bfStateBaseDir + "/created_bloom.state." +
				config.getValue(SystemConfiguration.ARGUS_INSTANCE_ID, "noid");
		modifiedBloomFileName = bfStateBaseDir + "/modified_bloom.state." +
				config.getValue(SystemConfiguration.ARGUS_INSTANCE_ID, "noid");
		createdBloomExpectedNumberInsertions = Integer.parseInt(config.getValue(Property.CREATED_BLOOM_EXPECTED_NUMBER_INSERTIONS.getName(),
				Property.CREATED_BLOOM_EXPECTED_NUMBER_INSERTIONS.getDefaultValue()));
		createdBloomErrorRate = Double.parseDouble(config.getValue(Property.CREATED_BLOOM_ERROR_RATE.getName(),
				Property.CREATED_BLOOM_ERROR_RATE.getDefaultValue()));
		modifiedBloomExpectedNumberInsertions = Integer.parseInt(config.getValue(Property.MODIFIED_BLOOM_EXPECTED_NUMBER_INSERTIONS.getName(),
				Property.MODIFIED_BLOOM_EXPECTED_NUMBER_INSERTIONS.getDefaultValue()));
		modifiedBloomErrorRate = Double.parseDouble(config.getValue(Property.MODIFIED_BLOOM_ERROR_RATE.getName(),
				Property.MODIFIED_BLOOM_ERROR_RATE.getDefaultValue()));
		createdBloom = createOrReadBloomFilter(createdBloomFileName, createdBloomExpectedNumberInsertions, createdBloomErrorRate);
		modifiedBloom = createOrReadBloomFilter(modifiedBloomFileName, modifiedBloomExpectedNumberInsertions, modifiedBloomErrorRate);

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

		Set<Metric> metricsToCreate = new HashSet<>(metrics.size());
		Set<Metric> metricsToUpdate = new HashSet<>(metrics.size());
		Set<String> scopesToCreate = new HashSet<>();
		Set<String> scopesToUpdate = new HashSet<>();
		Set<MetatagsRecord> metatagsToCreate = new HashSet<>();
		Set<MetatagsRecord> metatagsToUpdate = new HashSet<>();

		for(Metric metric : metrics) {
			// Enqueue the entire metric for creating/updating accordingly
			if(metric.getTags().isEmpty()) {
				String key = AbstractSchemaRecord.constructKey(metric, null, null);
				if(!createdBloom.mightContain(key)) {
					metricsToCreate.add(metric);
				} else if (!modifiedBloom.mightContain(key)) {
					metricsToUpdate.add(metric);
				}
			} else {
				for(Entry<String, String> tagEntry : metric.getTags().entrySet()) {
					String key = AbstractSchemaRecord.constructKey(metric, tagEntry.getKey(), tagEntry.getValue());
					if (!createdBloom.mightContain(key)) {
						metricsToCreate.add(metric);
						break;
					} else if (!modifiedBloom.mightContain(key)) {
						metricsToUpdate.add(metric);
						break;
					}
				}
			}

			// Enqueue only-the-scope for creating/updating accordingly
			String scopeName = metric.getScope();
			String key = AbstractSchemaRecord.constructKey(scopeName);
			if (!createdBloom.mightContain(key)) {
				scopesToCreate.add(scopeName);
			} else if (!modifiedBloom.mightContain(key)) {
				scopesToUpdate.add(scopeName);
			}

			// Enqueue the metatags for creating/updating accordingly
			MetatagsRecord mtags = metric.getMetatagsRecord();
			if(mtags != null) {
				key = mtags.getKey();
				if(key != null) {
					if (!createdBloom.mightContain(key)) {
						metatagsToCreate.add(mtags);
					} else if (!modifiedBloom.mightContain(key)) {
						metatagsToUpdate.add(mtags);
					}
				}
			}
		}

		implementationSpecificPut(
				metricsToCreate,
				metricsToUpdate,
				scopesToCreate,
				scopesToUpdate,
				metatagsToCreate,
				metatagsToUpdate);
	}

	/**
	 * @param metricsToCreate	Metrics not seen by this instance before
	 * @param metricsToUpdate	Metrics that need a modified-timestamp update
	 * @param scopesToCreate		Scopes not seen by this instance before
	 * @param scopesToUpdate		Scopes that need a modified-timestamp update
	 * @param metatagsToCreate	Metatags not seen by this instance before
	 * @param metatagsToUpdate	Metatags that need a modified-timestamp update
	 */
	protected abstract void implementationSpecificPut(Set<Metric> metricsToCreate,
													  Set<Metric> metricsToUpdate,
													  Set<String> scopesToCreate,
													  Set<String> scopesToUpdate,
													  Set<MetatagsRecord> metatagsToCreate,
													  Set<MetatagsRecord> metatagsToUpdate);

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

	protected int getNumSecondsUntilTargetHour(int targetHour){
		_logger.info("Initialized bloom filter flushing out, at {} hour of day", targetHour);
		Calendar calendar = Calendar.getInstance();
		int hour = calendar.get(Calendar.HOUR_OF_DAY);
		int secondsPastHour = calendar.get(Calendar.MINUTE) * 60;
		int hoursUntil = hour < targetHour ? (targetHour - hour) : (targetHour + 24 - hour);
		return hoursUntil * 60 * 60 - secondsPastHour;
	}

	/*
	 * Have a different flush start hour for schema committers based on hostname, to prevent thundering herd problem.
	 */
	private int getBloomFilterFlushHourToStartAt() {
		int bloomFilterFlushHourToStartAt = 0;
		try {
			bloomFilterFlushHourToStartAt = Math.abs((InetAddress.getLocalHost().getHostName() + config.getValue(config.ARGUS_INSTANCE_ID, "")).hashCode() % 24);
		} catch (UnknownHostException e) {
			_logger.warn("BloomFilter UnknownHostException", e);
		}
		_logger.info("BloomFilter flush hour to start at {}th hour of day", bloomFilterFlushHourToStartAt);
		return bloomFilterFlushHourToStartAt;
	}

    private BloomFilter<CharSequence> createOrReadBloomFilter(String filename, int expectedNumberInsertions, double errorRate) {
		File bfFile = new File(filename);
		if (bloomFileWritingEnabled && bfFile.exists()) {
			_logger.info("Bloomfilter state file {} exists, using it to pre-populate bloom", filename);
			try (InputStream inputStream = new FileInputStream(bfFile)) {
				return BloomFilter.readFrom(inputStream, Funnels.stringFunnel(Charset.defaultCharset()));
			} catch (IOException io) {
				_logger.error("Bloomfilter state file {} read error, not using prev state: {}", filename, io);
				return BloomFilter.create(Funnels.stringFunnel(Charset.defaultCharset()), expectedNumberInsertions, errorRate);
			}
		}
		_logger.info("Bloomfilter state file {} NOT present or bloomFileWritingEnabled is false, starting fresh bloom", filename);
		return BloomFilter.create(Funnels.stringFunnel(Charset.defaultCharset()), expectedNumberInsertions, errorRate);
    }

    private void writeBloomsToFile() {
        if (!bloomFileWritingEnabled) {
            return;
        }

        File createdBloomFile = new File(this.createdBloomFileName);
        createdBloomFile.mkdirs();
        try (OutputStream out = new FileOutputStream(createdBloomFile)) {
            createdBloom.writeTo(out);
            _logger.info("Succesfully wrote created-metrics bloomfilter to file {}", this.createdBloomFileName);
        } catch (IOException io) {
            _logger.error("Failed to write to createdBloom file", io);
        }

		File modifiedBloomFile = new File(this.modifiedBloomFileName);
		try (OutputStream out = new FileOutputStream(modifiedBloomFile)) {
			modifiedBloom.writeTo(out);
			_logger.info("Succesfully wrote modified-metrics bloomfilter to file {}", this.modifiedBloomFileName);
		} catch (IOException io) {
			_logger.error("Failed to write to modifiedBloom file", io);
		}
    }


	private void createScheduledExecutorService(int targetHourToStartAt){
		scheduledExecutorService = Executors.newScheduledThreadPool(1);
		int initialDelayInSeconds = getNumSecondsUntilTargetHour(targetHourToStartAt);
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

		/*
		 * (Comment from old bloom filter pattern)
		 * Estimated Filter Size using bloomFilter 1 million entries
		 * https://hur.st/bloomfilter/?n=1000000&p=1.0E-5&m=&k= 2.86MiB
		 * Storing in a Set 100K entries with avg length of 15 chars would be 100K * 15 * 2 B = 30B * 100K = 3 MB
		 * If # of entries is 1 million, then it would be 30 MB resulting in savings in space.
		 */
		CREATED_BLOOM_EXPECTED_NUMBER_INSERTIONS("service.property.schema.bloomfilter.created.expected.number.insertions", "40"),
		CREATED_BLOOM_ERROR_RATE("service.property.schema.bloomfilter.created.error.rate", "0.00001"),
		MODIFIED_BLOOM_EXPECTED_NUMBER_INSERTIONS("service.property.schema.bloomfilter.modified.expected.number.insertions", "40"),
		MODIFIED_BLOOM_ERROR_RATE("service.property.schema.bloomfilter.modified.error.rate", "0.00001");


		private final String _name;
		private final String _defaultValue;

		Property(String name, String defaultValue) {
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
			_logger.info("Initialized randomBloomAppend for bloom filter key = {}", AbstractSchemaRecord.getBloomAppend());
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
			_monitorService.modifyCounter(MonitorService.Counter.BLOOM_CREATED_APPROXIMATE_ELEMENT_COUNT, createdBloom.approximateElementCount(), null);
			_monitorService.modifyCounter(MonitorService.Counter.BLOOM_MODIFIED_APPROXIMATE_ELEMENT_COUNT, modifiedBloom.approximateElementCount(), null);

			_logger.info("Bloom for created-timestamp expected error rate = {}", createdBloom.expectedFpp());
			_logger.info("Bloom for modified-timestamp expected error rate = {}", modifiedBloom.expectedFpp());
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

	/**
	 *	Writes the createdBloom and modifiedBloom to disk. Clears the modifiedBloom.
	 */
	private class BloomFilterFlushThread implements Runnable {
		@Override
		public void run() {
			try {
				_flushBloomFilter();
			} catch (Exception ex) {
				_logger.warn("Exception occurred while flushing bloom filter.", ex);
			}
		}

		private void _flushBloomFilter() {
			_logger.info("Flushing out bloom filter entries");
			writeBloomsToFile();
			modifiedBloom = BloomFilter.create(Funnels.stringFunnel(Charset.defaultCharset()), modifiedBloomExpectedNumberInsertions, modifiedBloomErrorRate);
			/* Don't need explicit synchronization to prevent slowness majority of the time*/
		}
	}
}

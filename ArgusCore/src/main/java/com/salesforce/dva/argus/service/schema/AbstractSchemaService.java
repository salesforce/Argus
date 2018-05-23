package com.salesforce.dva.argus.service.schema;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

import com.salesforce.dva.argus.entity.KeywordQuery;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.MetricSchemaRecord;
import com.salesforce.dva.argus.entity.MetricSchemaRecordQuery;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemConfiguration;

public abstract class AbstractSchemaService extends DefaultService implements SchemaService {

	private static final long POLL_INTERVAL_MS = 10 * 60 * 1000L;
	private static final long BLOOM_FILTER_FLUSH_INTERVAL_HOUR = 60 * 60 * 1000L;
	private static final int MIN_FLUSH_HOUR_INTERVAL = 6;
	private static final int MAX_FLUSH_HOUR_INTERVAL = 15;
	protected static BloomFilter<CharSequence> BLOOMFILTER;
	private static boolean _writesToBloomFilterEnabled = true;

	private int bloomFilterExpectedNumberInsertions;
	private double bloomFilterErrorRate;
	private final Logger _logger = LoggerFactory.getLogger(getClass());
	private final Thread _bloomFilterMonitorThread;
	private final boolean _cacheEnabled;
	protected final boolean _syncPut;

	protected AbstractSchemaService(SystemConfiguration config) {
		super(config);

		bloomFilterExpectedNumberInsertions = Integer.parseInt(config.getValue(Property.BLOOMFILTER_EXPECTED_NUMBER_INSERTIONS.getName(), 
				Property.BLOOMFILTER_EXPECTED_NUMBER_INSERTIONS.getDefaultValue()));
		bloomFilterErrorRate = Double.parseDouble(config.getValue(Property.BLOOMFILTER_ERROR_RATE.getName(), 
				Property.BLOOMFILTER_ERROR_RATE.getDefaultValue()));
		BLOOMFILTER = BloomFilter.create(Funnels.stringFunnel(Charset.defaultCharset()), bloomFilterExpectedNumberInsertions , bloomFilterErrorRate);

		_cacheEnabled = Boolean.parseBoolean(
				config.getValue(Property.CACHE_SCHEMARECORDS.getName(), Property.CACHE_SCHEMARECORDS.getDefaultValue()));
		_syncPut = Boolean.parseBoolean(
				config.getValue(Property.SYNC_PUT.getName(), Property.SYNC_PUT.getDefaultValue()));

		_bloomFilterMonitorThread = new Thread(new BloomFilterMonitorThread(), "bloom-filter-monitor");
		if(_cacheEnabled) {
			_bloomFilterMonitorThread.start();
		}
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

		//If cache is not enabled, call implementation specific put with the list of metrics. 
		if(!_cacheEnabled) {
			implementationSpecificPut(metrics);
			return;
		}

		//If cache is enabled, create a list of metricsToPut that do not exist on the BLOOMFILTER and then call implementation 
		// specific put with only those subset of metricsToPut. 
		List<Metric> metricsToPut = new ArrayList<>(metrics.size());

		for(Metric metric : metrics) {
			if(metric.getTags().isEmpty()) {
				String key = constructKey(metric, null);
				boolean found = BLOOMFILTER.mightContain(key);
				if(!found) {
					metricsToPut.add(metric);
					if(_writesToBloomFilterEnabled) {
						BLOOMFILTER.put(key);
					}
				}
			} else {
				boolean newTags = false;
				for(Entry<String, String> tagEntry : metric.getTags().entrySet()) {
					String key = constructKey(metric, tagEntry);
					boolean found = BLOOMFILTER.mightContain(key);
					if(!found) {
						newTags = true;
						if(_writesToBloomFilterEnabled) {
							BLOOMFILTER.put(key);
						}
					}
				}

				if(newTags) {
					metricsToPut.add(metric);
				}
			}
		}

		implementationSpecificPut(metricsToPut);
	}

	protected abstract void implementationSpecificPut(List<Metric> metrics);

	protected String constructKey(Metric metric, Entry<String, String> tagEntry) {
		StringBuilder sb = new StringBuilder(metric.getScope());
		sb.append('\0').append(metric.getMetric());

		if(metric.getNamespace() != null) {
			sb.append('\0').append(metric.getNamespace());
		}

		if(tagEntry != null) {
			sb.append('\0').append(tagEntry.getKey()).append('\0').append(tagEntry.getValue());
		}

		return sb.toString();
	}

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
	}

	@Override
	public abstract Properties getServiceProperties();

	@Override
	public abstract List<MetricSchemaRecord> get(MetricSchemaRecordQuery query);

	@Override
	public abstract List<MetricSchemaRecord> getUnique(MetricSchemaRecordQuery query, RecordType type);

	@Override
	public abstract List<MetricSchemaRecord> keywordSearch(KeywordQuery query);


	/**
	 * The set of implementation specific configuration properties.
	 *
	 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
	 */
	public enum Property {

		/* If set to true, schema records will be cached on writes. This helps to check if a schema records already exists,
		 * and if it does then do not rewrite. Provide more heap space when using this option. */
		CACHE_SCHEMARECORDS("service.property.schema.cache.schemarecords", "false"),
		SYNC_PUT("service.property.schema.sync.put", "false"),

		BLOOMFILTER_EXPECTED_NUMBER_INSERTIONS("service.property.schema.bloomfilter.expected.number.insertions", "400000000"),
		BLOOMFILTER_ERROR_RATE("service.property.schema.bloomfilter.error.rate", "0.00001");

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

		long startTimeBeforeFlushInMillis;
		int flushAtHour;

		@Override
		public void run() {
			startTimeBeforeFlushInMillis = System.currentTimeMillis();
			flushAtHour = getRandomHourBetweenRange(MIN_FLUSH_HOUR_INTERVAL, MAX_FLUSH_HOUR_INTERVAL);
			_logger.info("Initialized bloom filter flushing out, after {} hours", flushAtHour);
			while (!Thread.currentThread().isInterrupted()) {
				_sleepForPollPeriod();
				if (!Thread.currentThread().isInterrupted()) {
					try {
						_checkBloomFilterUsage();

						//  flush out bloom filter every K hours
						_flushBloomFilter();
					} catch (Exception ex) {
						_logger.warn("Exception occurred while checking bloom filter usage.", ex);
					}
				}
			}
		}

		private void _checkBloomFilterUsage() {
			_logger.info("Bloom approx no. elements = {}", BLOOMFILTER.approximateElementCount());
			_logger.info("Bloom expected error rate = {}", BLOOMFILTER.expectedFpp());
		}

		private void _flushBloomFilter() {
			long currentTimeInMillis = System.currentTimeMillis();
			if((currentTimeInMillis - startTimeBeforeFlushInMillis) > BLOOM_FILTER_FLUSH_INTERVAL_HOUR * flushAtHour){
				_logger.info("Flushing out bloom filter entries, after {} hours", flushAtHour);
				BLOOMFILTER = BloomFilter.create(Funnels.stringFunnel(Charset.defaultCharset()), bloomFilterExpectedNumberInsertions , bloomFilterErrorRate);
				flushAtHour = getRandomHourBetweenRange(MIN_FLUSH_HOUR_INTERVAL, MAX_FLUSH_HOUR_INTERVAL);
				startTimeBeforeFlushInMillis = currentTimeInMillis;
			}
		}

		private int getRandomHourBetweenRange(int minHour, int maxHour){
			return (int) (Math.random() * (maxHour - minHour +1) + minHour); 
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
}

package com.salesforce.dva.argus.service.tsdb;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.redisson.Redisson;
import org.redisson.api.RBatch;
import org.redisson.api.RFuture;
import org.redisson.api.RSet;
import org.redisson.api.RSetAsync;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisException;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.Longs;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.NamedBinding;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.cache.RedisCacheService;
import com.salesforce.dva.argus.service.metric.transform.Transform;
import com.salesforce.dva.argus.service.metric.transform.TransformFactory;
import com.salesforce.dva.argus.service.tsdb.MetricQuery.Aggregator;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;

@Singleton
public class WriteThroughCachedTSDBService extends DefaultService implements TSDBService {
	
	private static long ONE_HOUR_IN_MILLIS = 60 * 60 * 1000;
	private static long ONE_HOUR_IN_SECS = 60 * 60;
	private static int STORE_HOURS = 4;
	private static int EXPIRY_IN_SECONDS = (STORE_HOURS + 1) * 60 * 60;
	private static String DELIMITER = ":";
	
	//TODO: Append "TEST:" to cache keys for development purposes. Change this to an empty string when using in production.  
	private static String APPEND_IDENTIFIER_TO_CACHEKEYS = "TEST" + DELIMITER;
	private static final Logger LOGGER = LoggerFactory.getLogger(WriteThroughCachedTSDBService.class);
	
	//~ Instance fields ******************************************************************************************************************************
	
    private final TSDBService _defaultTsdbService;
    private final MonitorService _monitorService;
    private final TransformFactory _transformFactory;
    private RedissonClient _redissonClient;
    private boolean _writeThroughCacheEnabled = false;
    
  //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new Cached TSDB Service
     *
     * @param   config               The system _configuration used to configure the service.
     * @param   monitorService       The monitor service used to collect query time window counters. Cannot be null.
     * @param   cacheService         The caching service to use in the cached tsdb service implementation
     * @param   defaultTSDBService          The tsdb service to use when data is not present in cache
     *
     */
    @Inject
    public WriteThroughCachedTSDBService(SystemConfiguration sysConfig, MonitorService monitorService, 
    		@NamedBinding TSDBService defaultTSDBService, TransformFactory transformFactory) {
    	super(sysConfig);
        requireArgument(monitorService != null, "Monitor service cannot be null.");
        _monitorService = monitorService;
        _defaultTsdbService = defaultTSDBService;
        _transformFactory = transformFactory;
        
        _writeThroughCacheEnabled = Boolean.parseBoolean(
        		sysConfig.getValue(Property.WRITE_THROUGH_CACHE_ENABLED.getName(), Property.WRITE_THROUGH_CACHE_ENABLED.getDefaultValue()));
        
        _initializeRedissonClient(sysConfig);
    }
    
	private void _initializeRedissonClient(SystemConfiguration sysConfig) {
		Config config = new Config();
		ClusterServersConfig clusterConfig = config.useClusterServers();
		
		String[] nodes = sysConfig.getValue(
				RedisCacheService.Property.REDIS_CLUSTER.getName(), RedisCacheService.Property.REDIS_CLUSTER.getDefaultValue()).split(",");
		for(String node : nodes) {
			clusterConfig.addNodeAddress("redis://" + node);
		}
		config.setCodec(new StringCodec());
		_redissonClient = Redisson.create(config);
	}
    
	
    @Override
    public void dispose() {
    	super.dispose();
    	_redissonClient.shutdown();
    	_defaultTsdbService.dispose();
    }

        
    @Override
    public void putMetrics(List<Metric> metrics) {
    	requireNotDisposed();
		requireArgument(metrics != null, "Metrics list cannot be null.");
    	
		if(_writeThroughCacheEnabled) {
			long start = System.currentTimeMillis();
	    	RBatch batch = _redissonClient.createBatch();
	    	for(Metric metric : metrics) {
				CharSequence cacheKeyWithoutBaseTimestamp = _constructCackeKeyWithoutBaseTimestamp(metric);
				_addToBatch(cacheKeyWithoutBaseTimestamp, metric.getDatapoints(), batch);
			}
	    	
	    	_addTagInfoToBatch(metrics, batch);
	    	
	    	batch.execute();
	    	LOGGER.debug("Time to write " + metrics.size() + " metrics to cache: " + (System.currentTimeMillis() - start));
		}
    	
    	_defaultTsdbService.putMetrics(metrics);
    }
    
    private void _addToBatch(CharSequence cacheKeyWithoutBaseTimestamp, Map<Long, Double> datapoints, RBatch batch) {
		
		Map<Long, Map<Long, Double>> datapointsBrokenOutByHourlyBoundary = _breakDatapointsByHourlyBoundary(datapoints);
		
		for(Map.Entry<Long, Map<Long, Double>> entry : datapointsBrokenOutByHourlyBoundary.entrySet()) {
			long baseTimestamp = entry.getKey();
			String cacheKey =  new StringBuilder().append(APPEND_IDENTIFIER_TO_CACHEKEYS).append(baseTimestamp).append(DELIMITER).append(cacheKeyWithoutBaseTimestamp).toString();
			List<String> values = _convertDatapointToByteString(entry.getKey(), entry.getValue());
			RSetAsync<String> set = batch.getSet(cacheKey);
			set.addAllAsync(values);
			set.expireAsync(EXPIRY_IN_SECONDS, TimeUnit.SECONDS);
		}
	}
    
    private void _addTagInfoToBatch(List<Metric> metrics, RBatch batch) {
		
		Map<String, List<String>> map = new HashMap<>();
		
		for(Metric metric : metrics) {
			StringBuilder keySB = new StringBuilder(metric.getScope()).append(DELIMITER).append(metric.getMetric()).append(DELIMITER);
			StringBuilder valueSB = new StringBuilder();
			
			Map<String, String> sortedTags = new TreeMap<>(metric.getTags());
			for(Map.Entry<String, String> entry : sortedTags.entrySet()) {
				valueSB.append(entry.getKey()).append(DELIMITER).append(entry.getValue()).append(DELIMITER);
			}
			
			
			if(!map.containsKey(keySB.toString())) {
				List<String> values = new ArrayList<>();
				map.put(keySB.toString(), values);
			}
			
			map.get(keySB.toString()).add(valueSB.toString());
		}
		
		for(Map.Entry<String, List<String>> entry : map.entrySet()) {
			RSetAsync<String> set = batch.getSet(entry.getKey());
			set.addAllAsync(entry.getValue());
		}
	}

	static CharSequence _constructCackeKeyWithoutBaseTimestamp(Metric metric) {
		
		StringBuilder sb = new StringBuilder(metric.getScope()).append(DELIMITER).append(metric.getMetric()).append(DELIMITER);
		
		Map<String, String> sortedTags = new TreeMap<>(metric.getTags());
		for(Map.Entry<String, String> entry : sortedTags.entrySet()) {
			sb.append(entry.getKey()).append(DELIMITER).append(entry.getValue()).append(DELIMITER);
		}
		
		return sb;
	}
	
	static List<String> _convertDatapointToByteString(long baseTimestampInSecs, Map<Long, Double> datapointsWithTimestampsInSecs) {
		
		List<String> datapoints = new ArrayList<>();
		
		//int destPos = 0;
		for(Map.Entry<Long, Double> entry : datapointsWithTimestampsInSecs.entrySet()) {
			
			//10 bytes per datapoint, 2 for long timestamp and 8 for double value.
			byte[] datapointsByteArr = new byte[10];
			
			long deltaInSeconds = entry.getKey() - baseTimestampInSecs;
			byte[] deltaInBytes = Longs.toByteArray(deltaInSeconds);
			byte[] valueInBytes = Longs.toByteArray(Double.doubleToLongBits(entry.getValue()));
			
			//Since we are only storing 1 hour of data in a single row and Argus does not support ms granularity,
			//the maximum datapoints that can fit in this row is 3600. Hence we can capture max_detla (which will be 3600)
			//using 2 bytes.
			System.arraycopy(deltaInBytes, 6, datapointsByteArr, 0, 2);
			System.arraycopy(valueInBytes, 0, datapointsByteArr, 2, 8);
			
			datapoints.add(new String(datapointsByteArr, Charset.forName("ISO-8859-1")));
		}
		
		return datapoints;
	}
	
	static Map<Long, Double> _convertByteStringToDatapoint(long baseTimestampInSecs, Set<String> datapointsSet, long start, long end) {
		
		//TODO: Handle cases where start and end are in seconds and not milliseconds.
		
		if(datapointsSet == null || datapointsSet.isEmpty()) {
			return new TreeMap<>();
		}
		
		long baseTimestampInMillis = baseTimestampInSecs * 1000;
		boolean trim = false;
		if(baseTimestampInMillis < start || (baseTimestampInMillis + ONE_HOUR_IN_MILLIS) > end) {
			trim = true;
		}
		
		Map<Long, Double> datapoints = new TreeMap<>();
		for(String datapoint : datapointsSet) {
			
			byte[] dpInBytes = datapoint.getBytes(Charset.forName("ISO-8859-1"));
			SystemAssert.requireArgument(dpInBytes.length % 10 == 0, "Datapoint byte array was not properly formatted.");
			
			byte[] timestampInBytes = new byte[8];
			timestampInBytes[6] = dpInBytes[0];
			timestampInBytes[7] = dpInBytes[1];
			
			long timestamp = Longs.fromByteArray(timestampInBytes) * 1000 + baseTimestampInMillis;
			
			if(trim && (timestamp < start || timestamp > end)) {
				continue;
			}
			
			byte[] valueInBytes = new byte[8];
			valueInBytes[0] = dpInBytes[2];
			valueInBytes[1] = dpInBytes[3];
			valueInBytes[2] = dpInBytes[4];
			valueInBytes[3] = dpInBytes[5];
			valueInBytes[4] = dpInBytes[6];
			valueInBytes[5] = dpInBytes[7];
			valueInBytes[6] = dpInBytes[8];
			valueInBytes[7] = dpInBytes[9];
			
			datapoints.put(timestamp, Double.longBitsToDouble(Longs.fromByteArray(valueInBytes)));
		}
		
		return datapoints;
	}
 
	static Map<Long, Map<Long, Double>> _breakDatapointsByHourlyBoundary(Map<Long, Double> datapoints) {
		
		Map<Long, Map<Long, Double>> datapointsBrokenOnHourlyBoundary = new HashMap<>();
		
		for(Map.Entry<Long, Double> entry : datapoints.entrySet()) {
			
			long timestampInSecs = String.valueOf(entry.getKey()).length() >= 12 ? entry.getKey() / 1000 : entry.getKey();
			long baseTimestampInSecs = timestampInSecs - (timestampInSecs % ONE_HOUR_IN_SECS);
			
			if(!datapointsBrokenOnHourlyBoundary.containsKey(baseTimestampInSecs)) {
				datapointsBrokenOnHourlyBoundary.put(baseTimestampInSecs, new TreeMap<>());
			}
			datapointsBrokenOnHourlyBoundary.get(baseTimestampInSecs).put(timestampInSecs, entry.getValue());
		}
		
		return datapointsBrokenOnHourlyBoundary;
	}
	
	private Set<String> _constructCacheKeys(MetricQuery query) {
		
		Set<String> cacheKeys = new HashSet<>();
		
		Map<String, String> sortedTags = new TreeMap<>(query.getTags());
		
		String key = new StringBuilder(query.getScope()).append(DELIMITER).append(query.getMetric()).append(DELIMITER).toString();
		
		
		RSet<String> values = _redissonClient.getSet(key);
		Set<String> keysWithoutBaseTimestamp = new HashSet<>();
		for(String value : values) {
			keysWithoutBaseTimestamp.add(key + value);
		}
		
		Set<String> matchedKeys = new HashSet<>();
		String pattern = _constructKeyPattern(query.getScope(), query.getMetric(), sortedTags);
		for(String keyWithoutBaseTimestamp : keysWithoutBaseTimestamp) {
			if(keyWithoutBaseTimestamp.matches(pattern)) {
				matchedKeys.add(keyWithoutBaseTimestamp);
			}
		}
		
		//TODO: Handle cases where start and end are in seconds.
		long start = query.getStartTimestamp();
		long end = query.getEndTimestamp();
		
		long baseTimestamp = start - (start % ONE_HOUR_IN_MILLIS);
		while(baseTimestamp < end) {
			_addBaseTimestampToKeys(cacheKeys, matchedKeys, baseTimestamp / 1000);
			baseTimestamp += ONE_HOUR_IN_MILLIS;
		}
		
		return cacheKeys;
	}
	
	private static String _constructKeyPattern(String scope, String metric, Map<String, String> sortedTags) {
		StringBuilder sb = new StringBuilder(scope).append(DELIMITER).append(metric).append(DELIMITER);
		
		for(Map.Entry<String, String> entry : sortedTags.entrySet()) {
			String tagValue = entry.getValue();
			if("*".equals(tagValue)) {
				tagValue = ".*";
			}
			
			sb.append(".*").append(entry.getKey()).append(DELIMITER).append("(?:").append(tagValue).append(")").append(DELIMITER);
		}
		
		sb.append(".*");
		return sb.toString();
	}

	private static void _addBaseTimestampToKeys(Set<String> dest, Set<String> src, long baseTimestamp) {
		for(String key : src) {
			dest.add(APPEND_IDENTIFIER_TO_CACHEKEYS + baseTimestamp + DELIMITER + key); 
		}
	}

	
	@Override
	public Map<MetricQuery, List<Metric>> getMetrics(List<MetricQuery> queries) {
		requireNotDisposed();
		requireArgument(queries != null, "Queries cannot be null");
		
		if(!_writeThroughCacheEnabled) {
			return _defaultTsdbService.getMetrics(queries);
		}
		
		
		long totalStart = System.currentTimeMillis();
		Map<MetricQuery, List<Metric>> metricsMap = new HashMap<>(queries.size());
		
		for(MetricQuery query : queries) {
			SystemAssert.requireArgument(query != null, "MetricQuery cannot be null.");
			
			long queryExecutionStartTime = System.currentTimeMillis();
			long currentHourInMillis = queryExecutionStartTime - (queryExecutionStartTime % ONE_HOUR_IN_MILLIS); 
			if(query.getStartTimestamp() < currentHourInMillis - STORE_HOURS * 1000 * 3600) {
				LOGGER.info("Query start exceeds time window for data stored in cache. Will read from PERSISTENT STORE.");
				List<Metric> metrics = _defaultTsdbService.getMetrics(Arrays.asList(query)).get(query);
				metricsMap.put(query, metrics);
			} else {
				LOGGER.info("Query start is within the time window for data stored in cache. Will read from CACHE.");
				
				long start = System.currentTimeMillis();
				Set<String> cacheKeys = _constructCacheKeys(query);
				LOGGER.info("Time to construct keys for a query: " + (System.currentTimeMillis() - start));
				
				if(cacheKeys.isEmpty()) {
					LOGGER.info("CACHE MISS. Will read from PERSISTENT STORE.");
					metricsMap.putAll(_defaultTsdbService.getMetrics(Arrays.asList(query)));
				} else {
					LOGGER.info("CACHE HIT. Will read from CACHE.");
					
					Map<String, Set<String>> data = new HashMap<>();
					
					try {
						start = System.currentTimeMillis();
						RBatch batch = _redissonClient.createBatch();
						Map<String, RFuture<Set<String>>> futures = new HashMap<>();
						for(String cacheKey : cacheKeys) {
							RSetAsync<String> set = batch.getSet(cacheKey);
							futures.put(cacheKey, set.readAllAsync());
						}
						batch.execute();
						
						for(Map.Entry<String, RFuture<Set<String>>> entry : futures.entrySet()) {
							Set<String> set = entry.getValue().get();
							data.put(entry.getKey(), set);
							
						}
						LOGGER.info("Time to read data for " + cacheKeys.size() + " keys: " + (System.currentTimeMillis() - start));
						
						if(data.isEmpty() || data.size() < cacheKeys.size()) {
							LOGGER.info("Failed to read data from CACHE. Will read from PERSISTENT STORE.");
							metricsMap.putAll(_defaultTsdbService.getMetrics(Arrays.asList(query)));
						} else {
							List<Metric> metrics = new ArrayList<>();
							
							for(Map.Entry<String, Set<String>> entry : data.entrySet()) {
								Metric m = _constructMetric(entry, query);
								metrics.add(m);
							}
							
							TSDBService.collate(metrics);
							
							Transform downsampleTransform = _transformFactory.getTransform(TransformFactory.Function.DOWNSAMPLE.getName());
							TSDBService.downsample(query, metrics, downsampleTransform);
							
							Map<String, List<Metric>>groupedMetricsMap = TSDBService.groupMetricsForAggregation(metrics, query);
							Transform transform = Aggregator.correspondingTransform(query.getAggregator(), _transformFactory);
							metricsMap.put(query, TSDBService.aggregate(groupedMetricsMap, transform));
						}
						
					} catch (InterruptedException e) {
						LOGGER.warn("Interrupted while reading data from CACHE", e);
						Thread.currentThread().interrupt();
					} catch (RedisException | ExecutionException e) {
						LOGGER.error("Failed to read data from CACHE. Will read from PERSISTENT STORE.", e);
						metricsMap.putAll(_defaultTsdbService.getMetrics(Arrays.asList(query)));
					}
				}
			}
			
			TSDBService.instrumentQueryLatency(_monitorService, query, queryExecutionStartTime, MeasurementType.METRICS);
		}
		
		LOGGER.debug("Time to get metrics: " + (System.currentTimeMillis() - totalStart));
		
		
		return metricsMap;
	}

	private Metric _constructMetric(Entry<String, Set<String>> cacheEntry, MetricQuery query) {
		String cacheKey = cacheEntry.getKey();
		
		String[] parts = cacheKey.split(DELIMITER);
		long baseTimestampInSecs = Long.parseLong(parts[1]);
		Metric metric = new Metric(parts[2], parts[3]);
		Map<Long, Double> datapoints = _convertByteStringToDatapoint(baseTimestampInSecs, cacheEntry.getValue(), query.getStartTimestamp(), query.getEndTimestamp());
		metric.setDatapoints(datapoints);
		
		for(int i=4; i<parts.length; i++) {
			if(i+1 == parts.length ) {
				throw new SystemException("Cache Key was incorreclty formatted.");
			}
			
			metric.setTag(parts[i], parts[i+1]);
			i++;
		}
		
		return metric;
	}

	@Override
	public void putAnnotations(List<Annotation> annotations) {
		_defaultTsdbService.putAnnotations(annotations);
	}

	@Override
	public List<Annotation> getAnnotations(List<AnnotationQuery> queries) {
		return _defaultTsdbService.getAnnotations(queries);
	}
	
	
	/**
     * The set of implementation specific configuration properties.
     *
     * @author  Bhinav Sura (bhinav.sura@salesforce.com)
     */
    public enum Property {
        
    	WRITE_THROUGH_CACHE_ENABLED("service.property.tsdb.writethrough.cache.enabled", "false");

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
	
}

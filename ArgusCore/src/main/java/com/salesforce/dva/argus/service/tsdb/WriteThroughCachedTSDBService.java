package com.salesforce.dva.argus.service.tsdb;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

import java.io.IOException;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.Longs;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.CacheService;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.NamedBinding;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.cache.RedisCacheService.Property;
import com.salesforce.dva.argus.service.metric.transform.Transform;
import com.salesforce.dva.argus.service.metric.transform.TransformFactory;
import com.salesforce.dva.argus.service.tsdb.MetricQuery.Aggregator;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.ScanParams;
import redis.clients.jedis.ScanResult;

@Singleton
public class WriteThroughCachedTSDBService extends DefaultService implements TSDBService {
	
	private static long ONE_HOUR_IN_MILLIS = 60 * 60 * 1000;
	private static long ONE_HOUR_IN_SECS = 60 * 60;
	private static int STORE_HOURS = 4;
	private static int EXPIRY_IN_SECONDS = (STORE_HOURS + 1) * 60 * 60;
	private static String DELIMITER = ":";
	private static String APPEND_IDENTIFIER_TO_CACHEKEYS = "TEST" + DELIMITER;
	//private static final RadixTree<VoidValue> TRIE = new ConcurrentRadixTree<>(new SmartArrayBasedNodeFactory());
	private static final Logger LOGGER = LoggerFactory.getLogger(WriteThroughCachedTSDBService.class);
	
	
	//~ Instance fields ******************************************************************************************************************************
	
    private final TSDBService _defaultTsdbService;
    private final CacheService _cacheService;
    private final MonitorService _monitorService;
    private final boolean _isWriteThroughCachingEnabled = true;
    private final TransformFactory _transformFactory;
    JedisCluster _jedisClusterClient;
    
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
    public WriteThroughCachedTSDBService(SystemConfiguration config, MonitorService monitorService, CacheService cacheService, 
    		@NamedBinding TSDBService defaultTSDBService, TransformFactory transformFactory) {
    	super(config);
        requireArgument(monitorService != null, "Monitor service cannot be null.");
        requireArgument(cacheService != null, "Cache service cannot be null.");
        _cacheService = cacheService;
        _monitorService = monitorService;
        _defaultTsdbService = defaultTSDBService;
        _transformFactory = transformFactory;
        //TODO: read wrtieThrough as a configuration option
        
        _initializeJedisClusterClient(config);
        
    }

	private void _initializeJedisClusterClient(SystemConfiguration config) {
		GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        poolConfig.setMaxTotal(Integer.parseInt(
                config.getValue(Property.REDIS_SERVER_MAX_CONNECTIONS.getName(), Property.REDIS_SERVER_MAX_CONNECTIONS.getDefaultValue())));

        String[] hostsPorts = config.getValue(Property.REDIS_CLUSTER.getName(), Property.REDIS_CLUSTER.getDefaultValue()).split(",");

        Set<HostAndPort> jedisClusterNodes = new HashSet<HostAndPort>();
        for (String hostPort : hostsPorts) {
            String[] hostPortPair = hostPort.split(":");

            jedisClusterNodes.add(new HostAndPort(hostPortPair[0], Integer.parseInt(hostPortPair[1])));
        }
        _jedisClusterClient = new JedisCluster(jedisClusterNodes, poolConfig);
	}
	
    
    @Override
    public void dispose() {
    	super.dispose();
    	try {
			_jedisClusterClient.close();
		} catch (IOException e) {
			LOGGER.warn("IOException while trying to close JedisClusterClient", e);
		}
    }

	@Override
	public void putMetrics(List<Metric> metrics) {
		requireNotDisposed();
		requireArgument(metrics != null, "Metrics list cannot be null.");
		
		ExecutorService service = Executors.newFixedThreadPool(2);
		Future<?> cachePutFuture = service.submit(new Runnable() {
			
			@Override
			public void run() {
				if(_isWriteThroughCachingEnabled) {
					LOGGER.debug("Adding " + metrics.size() + " metrics to cache.");
					for(Metric metric : metrics) {
						CharSequence cacheKeyWithoutBaseTimestamp = _constructCackeKeyWithoutBaseTimestamp(metric);
						_addToCache(cacheKeyWithoutBaseTimestamp, metric.getDatapoints());
						_addTagInfoToCache(metric);
					}
				}
			}
		});
		
		Future<?> persistentStorePutFuture = service.submit(new Runnable() {
			
			@Override
			public void run() {
				_defaultTsdbService.putMetrics(metrics);
			}
		});
		
		try {
			cachePutFuture.get();
			persistentStorePutFuture.get();
			service.shutdownNow();
		} catch (InterruptedException e) {
			LOGGER.warn("Interrupted while waiting on putMetrics to complete.", e);
			Thread.currentThread().interrupt();
		} catch (ExecutionException e) {
			throw new SystemException("Exception occurred when trying to put metrics.", e);
		}
	}

	private void _addTagInfoToCache(Metric metric) {
		
		StringBuilder keySB = new StringBuilder(metric.getScope()).append(DELIMITER).append(metric.getMetric()).append(DELIMITER);
		StringBuilder valueSB = new StringBuilder();
		
		Map<String, String> sortedTags = new TreeMap<>(metric.getTags());
		for(Map.Entry<String, String> entry : sortedTags.entrySet()) {
			valueSB.append(entry.getKey()).append(DELIMITER).append(entry.getValue()).append(DELIMITER);
		}
		
		_jedisClusterClient.sadd(keySB.toString().getBytes(), valueSB.toString().getBytes());
		
	}

	static CharSequence _constructCackeKeyWithoutBaseTimestamp(Metric metric) {
		
		StringBuilder sb = new StringBuilder(metric.getScope()).append(DELIMITER).append(metric.getMetric()).append(DELIMITER);
		
		Map<String, String> sortedTags = new TreeMap<>(metric.getTags());
		for(Map.Entry<String, String> entry : sortedTags.entrySet()) {
			sb.append(entry.getKey()).append(DELIMITER).append(entry.getValue()).append(DELIMITER);
		}
		
		return sb;
	}

	private void _addToCache(CharSequence cacheKeyWithoutBaseTimestamp, Map<Long, Double> datapoints) {
		
		Map<Long, Map<Long, Double>> datapointsBrokenOutByHourlyBoundary = _breakDatapointsByHourlyBoundary(datapoints);
		
		for(Map.Entry<Long, Map<Long, Double>> entry : datapointsBrokenOutByHourlyBoundary.entrySet()) {
			long baseTimestamp = entry.getKey();
			StringBuilder cacheKey =  new StringBuilder().append(APPEND_IDENTIFIER_TO_CACHEKEYS).append(baseTimestamp).append(DELIMITER).append(cacheKeyWithoutBaseTimestamp);
			byte[] cacheValue = _convertDatapointsMapToBytes(entry.getKey(), entry.getValue());
			_cacheService.append(cacheKey.toString().getBytes(), cacheValue, EXPIRY_IN_SECONDS);
		}
	}
	
	
	static byte[] _convertDatapointsMapToBytes(long baseTimestampInSecs, Map<Long, Double> datapointsWithTimestampsInSecs) {
		
		//10 bytes per datapoint, 2 for long timestamp and 8 for double value.
		int size = datapointsWithTimestampsInSecs.size() * 10;
		byte[] datapointsByteArr = new byte[size];
		
		int destPos = 0;
		for(Map.Entry<Long, Double> entry : datapointsWithTimestampsInSecs.entrySet()) {
			
			long deltaInSeconds = entry.getKey() - baseTimestampInSecs;
			byte[] deltaInBytes = Longs.toByteArray(deltaInSeconds);
			byte[] valueInBytes = Longs.toByteArray(Double.doubleToLongBits(entry.getValue()));
			
			//Since we are only storing 1 hour of data in a single row and Argus does not support ms granularity,
			//the maximum datapoints that can fit in this row is 3600. Hence we can capture max_detla (which will be 3600)
			//using 2 bytes.
			System.arraycopy(deltaInBytes, 6, datapointsByteArr, destPos, 2);
			destPos += 2;
			System.arraycopy(valueInBytes, 0, datapointsByteArr, destPos, 8);
			destPos += 8;
		}
		
		return datapointsByteArr;
	}
	
	static Map<Long, Double> _convertDatapointsByteArrToMap(long baseTimestampInSecs, byte[] datapointsByteArr, long start, long end) {
		
		//TODO: Handle cases where start and end are in seconds and not milliseconds.
		
		if(datapointsByteArr == null) {
			return new TreeMap<>();
		}
		
		SystemAssert.requireArgument(datapointsByteArr.length % 10 == 0, "Datapoint byte array was not properly formatted.");
		
		long baseTimestampInMillis = baseTimestampInSecs * 1000;
		boolean trim = false;
		if(baseTimestampInMillis < start || (baseTimestampInMillis + ONE_HOUR_IN_MILLIS) > end) {
			trim = true;
		}
		
		int size = datapointsByteArr.length/10;
		Map<Long, Double> datapoints = new TreeMap<>();
		
		for(int i=0; i<size; i++) {
			byte[] timestampInBytes = new byte[8];
			timestampInBytes[6] = datapointsByteArr[10 * i + 0];
			timestampInBytes[7] = datapointsByteArr[10 * i + 1];
			
			long timestamp = Longs.fromByteArray(timestampInBytes) * 1000 + baseTimestampInMillis;
			if(trim && (timestamp < start || timestamp > end)) {
				continue;
			}
			
			byte[] valueInBytes = new byte[8];
			valueInBytes[0] = datapointsByteArr[10 * i + 2];
			valueInBytes[1] = datapointsByteArr[10 * i + 3];
			valueInBytes[2] = datapointsByteArr[10 * i + 4];
			valueInBytes[3] = datapointsByteArr[10 * i + 5];
			valueInBytes[4] = datapointsByteArr[10 * i + 6];
			valueInBytes[5] = datapointsByteArr[10 * i + 7];
			valueInBytes[6] = datapointsByteArr[10 * i + 8];
			valueInBytes[7] = datapointsByteArr[10 * i + 9];
			
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
		
		ScanParams params = new ScanParams();
		params.count(100);
		
		String cursor = "0";
		Set<String> keysWithoutBaseTimestamp = new HashSet<>();
		do {
			ScanResult<String> scanResult = _jedisClusterClient.sscan(key, cursor, params);
			
			List<String> values = scanResult.getResult();
			for(String value : values) {
				keysWithoutBaseTimestamp.add(key + value);
			}
			
			cursor = scanResult.getStringCursor();
		} while(!"0".equals(cursor));
		
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

	private static Metric _constructMetric(Entry<String, byte[]> cacheEntry, MetricQuery query) {
		
		String cacheKey = cacheEntry.getKey();
		
		String[] parts = cacheKey.split(DELIMITER);
		long baseTimestampInSecs = Long.parseLong(parts[1]);
		Metric metric = new Metric(parts[2], parts[3]);
		Map<Long, Double> datapoints = _convertDatapointsByteArrToMap(baseTimestampInSecs, cacheEntry.getValue(), query.getStartTimestamp(), query.getEndTimestamp());
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
	public Map<MetricQuery, List<Metric>> getMetrics(List<MetricQuery> queries) {
		requireNotDisposed();
		requireArgument(queries != null, "Queries cannot be null");
		
		Map<MetricQuery, List<Metric>> metricsMap = new HashMap<>(queries.size());
		
		for(MetricQuery query : queries) {
			SystemAssert.requireArgument(query != null, "MetricQuery cannot be null.");
			
			long now = System.currentTimeMillis();
			long currentHourInMillis = now - (now % ONE_HOUR_IN_MILLIS);
			if(query.getStartTimestamp() < currentHourInMillis - STORE_HOURS * 3600 * 1000) {
				LOGGER.info("Query start exceeds time window for data stored in cache. Will read from persistent storage.");
				List<Metric> metrics = _defaultTsdbService.getMetrics(Arrays.asList(query)).get(query);
				metricsMap.put(query, metrics);
			} else {
				LOGGER.info("Query start is within the time window for data stored in cache. Will read from cache.");
				Set<String> cacheKeys = _constructCacheKeys(query);
				
				if(cacheKeys.isEmpty()) {
					//Data does not exist in cache. Get it from persistent storage.
					LOGGER.info("CACHE MISS. Will get data from persistent storage.");
					metricsMap.putAll(_defaultTsdbService.getMetrics(Arrays.asList(query)));
				} else {
					LOGGER.info("CACHE HIT. Will get data from cache storage.");
					Map<String, byte[]> data = _cacheService.get(cacheKeys);
					
					if(data == null || data.isEmpty()) {
						LOGGER.info("Error occured while getting data from CACHE. Will get data from persistent storage.");
						metricsMap.putAll(_defaultTsdbService.getMetrics(Arrays.asList(query)));
					} else {
						List<Metric> metrics = new ArrayList<>();
						for(Map.Entry<String, byte[]> entry : data.entrySet()) {
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
				}
			}
		}
		
		return metricsMap;
	}

	@Override
	public void putAnnotations(List<Annotation> annotations) {
		_defaultTsdbService.putAnnotations(annotations);
	}

	@Override
	public List<Annotation> getAnnotations(List<AnnotationQuery> queries) {
		return _defaultTsdbService.getAnnotations(queries);
	}
	
}

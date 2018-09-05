package com.salesforce.dva.argus.service.schema;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.salesforce.dva.argus.entity.MetricSchemaRecord;
import com.salesforce.dva.argus.entity.MetricSchemaRecordQuery;
import com.salesforce.dva.argus.entity.SchemaQuery;
import com.salesforce.dva.argus.service.CacheService;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.DiscoveryService;
import com.salesforce.dva.argus.service.NamedBinding;
import com.salesforce.dva.argus.service.SchemaService.RecordType;
import com.salesforce.dva.argus.service.tsdb.AnnotationQuery;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;

@Singleton
public class CachedDiscoveryService extends DefaultService implements DiscoveryService {
	
	private static final ObjectMapper MAPPER = new ObjectMapper();
	private static final int EXPIRY_TIME_SECS = 3600;
	private final long UPPER_LIMIT_TIME_GET_QUERIES_IN_MILLIS;
	
	//~ Instance fields ******************************************************************************************************************************

    protected final Logger _logger = LoggerFactory.getLogger(getClass());
    private final DiscoveryService _discoveryService;
    private final CacheService _cacheService;
    private final ExecutorService _executorService;

    //~ Constructors *********************************************************************************************************************************

    @Inject
    public CachedDiscoveryService(CacheService cacheService, @NamedBinding DiscoveryService discoveryService, SystemConfiguration config) {
    	super(config);
    	SystemAssert.requireArgument(cacheService != null, "Cache Service cannot be null.");
        SystemAssert.requireArgument(discoveryService != null, "Discovery Service cannot be null.");
		
        UPPER_LIMIT_TIME_GET_QUERIES_IN_MILLIS = Long.parseLong(config.getValue(Property.UPPER_LIMIT_TIME_GET_QUERIES_IN_MIILIS.getName(), 
				Property.UPPER_LIMIT_TIME_GET_QUERIES_IN_MIILIS.getDefaultValue()));
        
        _cacheService = cacheService;
        _discoveryService = discoveryService;
        _executorService = Executors.newCachedThreadPool();
    }

    //~ Methods **************************************************************************************************************************************

	@Override
	public void dispose() {
		super.dispose();
		_cacheService.dispose();
		_discoveryService.dispose();
		_executorService.shutdown();
		_disposeExecutorService();
	}

	private void _disposeExecutorService() {
		boolean terminated = false;
		try {
			terminated = _executorService.awaitTermination(30, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			_logger.warn("Await Termination Interrupted. Will force shutdown now.");
		}
		if(!terminated) {
			_executorService.shutdownNow();
		}
	}

	@Override
	public List<MetricSchemaRecord> filterRecords(SchemaQuery query) {
		return _discoveryService.filterRecords(query);
	}

	@Override
	public List<MetricSchemaRecord> getUniqueRecords(MetricSchemaRecordQuery query, RecordType type) {
		return _discoveryService.getUniqueRecords(query, type);
	}

	@Override
	public List<String> browseRecords(MetricSchemaRecordQuery query, RecordType type, int indexLevel) {
		return _discoveryService.browseRecords(query, type, indexLevel);
	}

	@Override
	public List<MetricQuery> getMatchingQueries(MetricQuery query) {
		requireNotDisposed();
		SystemAssert.requireArgument(query != null, "MetricQuery cannot be null.");
		
		long start = System.nanoTime();
		List<MetricQuery> queries = new ArrayList<>();
		
		if(DiscoveryService.isWildcardQuery(query)) {
			String value = _cacheService.get(_getKey(query));
			if(value == null) { // Cache Miss
				_logger.info(MessageFormat.format("CACHE MISS for Wildcard Query: '{'{0}'}'. Will read from persistent storage.", query));
				queries = _discoveryService.getMatchingQueries(query);
				
				long timeToGetQueriesMillis = (System.nanoTime() - start) / 1000000;
				_logger.info("Time to get matching queries from store in ms: " + timeToGetQueriesMillis);
				if(timeToGetQueriesMillis > UPPER_LIMIT_TIME_GET_QUERIES_IN_MILLIS){
					_logger.warn("Long time to get matching queries in ms: {} for query {}", timeToGetQueriesMillis, query);
				}
				
				_executorService.submit(new CacheInsertWorker(query, queries));
			} else { // Cache Hit
				_logger.info(MessageFormat.format("CACHE HIT for Wildcard Query: '{'{0}'}'", query));
				_logger.info("Time to get matching queries from cache in ms: " + (System.nanoTime() - start) / 1000000);
				try {
					JavaType type = MAPPER.getTypeFactory().constructCollectionType(List.class, MetricQuery.class);
					List<MetricQuery> matchedQueries = MAPPER.readValue(value, type);
					_checkIfExceedsLimits(query, matchedQueries);
					for(int i=0; i<matchedQueries.size(); i++) {
						MetricQuery q = new MetricQuery(query);
						_replaceWildcardFieldsFromCachedQuery(matchedQueries.get(i), q);
						queries.add(q);
					}
				} catch (IOException e) {
					_logger.warn("IOException when trying to deserialize cached data into metric queries. Will read from persistent storage.", e);
					queries = _discoveryService.getMatchingQueries(query);
					_executorService.submit(new CacheInsertWorker(query, queries));
				}
			}
		} else {
			_logger.info(MessageFormat.format("MetricQuery'{'{0}'}' does not have any wildcards", query));
			queries.add(query);
		}

		return queries;
	}
	
	//~ Enums ****************************************************************************************************************************************
	/**
	 * The set of implementation specific configuration properties.
	 *
	 * @author Dilip Devaraj (ddevaraj@salesforce.com)
	 */
	public enum Property {
		UPPER_LIMIT_TIME_GET_QUERIES_IN_MIILIS("service.property.schema.upper.limit.time.get.queries.in.millis", "3000");

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
	
	private void _checkIfExceedsLimits(MetricQuery query, List<MetricQuery> matchedQueries) {
		
		int noOfTimeseriesAllowed = DiscoveryService.maxTimeseriesAllowed(query);
		int numOfExpandedTimeseries = 1;
		for(MetricQuery mq : matchedQueries) {
			numOfExpandedTimeseries += DiscoveryService.numApproxTimeseriesForQuery(mq);
		}
		
		if(numOfExpandedTimeseries > noOfTimeseriesAllowed) {
			throw new WildcardExpansionLimitExceededException(EXCEPTION_MESSAGE);
		}
	}

	private void _replaceWildcardFieldsFromCachedQuery(MetricQuery cachedQuery, MetricQuery result) {
		result.setNamespace(cachedQuery.getNamespace());
		result.setTags(cachedQuery.getTags());
		
		//Set metric and scope using reflection.
		try {
			Method setMetricMethod = AnnotationQuery.class.getDeclaredMethod("setMetric", String.class);
			setMetricMethod.setAccessible(true);
			setMetricMethod.invoke(result, cachedQuery.getMetric());
			
			Method setScopeMethod = AnnotationQuery.class.getDeclaredMethod("setScope", String.class);
			setScopeMethod.setAccessible(true);
			setScopeMethod.invoke(result, cachedQuery.getScope());
		} catch (NoSuchMethodException | SecurityException | IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
			throw new SystemException("Failed to set metric name and scope using reflection.", e);
		}
	}

	private class CacheInsertWorker implements Runnable {
		
		private MetricQuery _wildcardQuery;
		private List<MetricQuery> _matchedQueries;
		
		CacheInsertWorker(final MetricQuery wildcardQuery, final List<MetricQuery> matchedQueries) {
			_wildcardQuery = wildcardQuery;
			_matchedQueries = matchedQueries;
		}

		@Override
		public void run() {
			try {
				String key = _getKey(_wildcardQuery);
				if(key != null) {
					_logger.debug("CacheInsertThread: Inserting key = {}, value = {}", _wildcardQuery, _matchedQueries);
					String value = MAPPER.writeValueAsString(_matchedQueries);
					_cacheService.put(key, value, EXPIRY_TIME_SECS);
				}
			} catch (JsonProcessingException e) {
				_logger.warn("CacheInsertThread: Failed to serialize list of metric queries.", e);
			}
		}
	}

	private String _getKey(MetricQuery query) {
		try {
			Map<String, String> sortedTags = Collections.emptyMap();
			if(query.getTags() != null && !query.getTags().isEmpty()) {
				sortedTags = new TreeMap<>(query.getTags());
			}
			
			String key = MessageFormat.format("{0}:{1}'{'{2}'}'", query.getScope(), query.getMetric(), MAPPER.writeValueAsString(sortedTags));
			if(query.getNamespace() != null) {
				key = query.getNamespace() + ":" + key;
			}
			return key;
		} catch (JsonProcessingException e) {
			_logger.warn("Failed to serialize tags to string.", e);
		}
		
		return null;
	}

}

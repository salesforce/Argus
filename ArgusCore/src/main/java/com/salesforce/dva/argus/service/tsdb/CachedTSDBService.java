/*
 * Copyright (c) 2016, Salesforce.com, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
	 
package com.salesforce.dva.argus.service.tsdb;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.CacheService;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.NamedBinding;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TimeZone;
import java.util.TreeMap;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * The cached implementation of TSDBService. Overrides the necessary methods of DefaultTSDBService by making use of a caching service.
 *
 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
 */
@Singleton
public class CachedTSDBService extends DefaultService implements TSDBService {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final Long DURATION_IN_MILLIS = 86400000L;
    private static final Long LOWER_START_TIME_LIMIT_IN_MILLIS = 86400000L;
    private static final Long UPPER_START_TIME_LIMIT_IN_MILLIS = 86400000 * 61L;
    private static final Long END_TIME_LIMIT_IN_MILLIS = 60000L;
    private static final String QUERY_LATENCY_COUNTER = "query.latency";
    private static final String QUERY_COUNT_COUNTER = "query.count"; 

    //~ Instance fields ******************************************************************************************************************************

    protected Logger _logger = LoggerFactory.getLogger(getClass());
    private final TSDBService _defaultTsdbService;
    private final CacheService _cacheService;
    private final MonitorService _monitorService;
    private final ObjectMapper _mapper;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new Cached TSDB Service
     *
     * @param   config               The system _configuration used to configure the service.
     * @param   monitorService       The monitor service used to collect query time window counters. Cannot be null.
     * @param   cacheService         The caching service to use in the cached tsdb service implementation
     * @param   tsdbService          The tsdb service to use when data is not present in cache
     *
     */
    @Inject
    private CachedTSDBService(SystemConfiguration config, MonitorService monitorService,
    		CacheService cacheService, @NamedBinding TSDBService tsdbService) {
    	super(config);
        requireArgument(tsdbService != null, "TSDBService cannot be null.");
        requireArgument(monitorService != null, "Monitor service cannot be null.");
        requireArgument(cacheService != null, "Cache service cannot be null.");
        _cacheService = cacheService;
        _monitorService = monitorService;
        _defaultTsdbService = tsdbService;
        _mapper = new ObjectMapper();
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Following queries will not no go through cache. It will be a direct call to TSDB
     * <ul>
     * <li>Queries with start time less than a day</li>
     * <li>Queries with start time greater than 60 days</li>
     * <li>Queries having a wildcard for tags</li>
     * <li>Queries with end time not equal to current time.</li>
     *
     * @param   queries  The queries to retrieve data for.
     *
     * @return  The resulting metrics.
     */
    @Override
    public Map<MetricQuery, List<Metric>> getMetrics(List<MetricQuery> queries) {
        // Copy the metric query since the passed in list may be a
        // fixed length array backed list, and we cannot remove queries from that list.
        List<MetricQuery> queryList = new ArrayList<MetricQuery>(queries);
        Map<MetricQuery, List<Metric>> result = new HashMap<MetricQuery, List<Metric>>();
        List<MetricQuery> filterMetricQueries = new ArrayList<MetricQuery>();
        Map<MetricQuery, MetricQueryTimestamp> map = new HashMap<MetricQuery, MetricQueryTimestamp>();

        for (MetricQuery query : queryList) {
            if (compulsoryCacheMiss(query)) {
                _logger.debug("Not using cache for this metric query");
                filterMetricQueries.add(query);
            }
        }
        queryList.removeAll(filterMetricQueries);
        if (!queryList.isEmpty()) {
            long beforeTime = System.currentTimeMillis();
            List<MetricQueryTimestamp> uncached = _getCachedMetricValues(queryList, result);
            long afterTime = System.currentTimeMillis();

            _logger.info("Time spent in _getCachedMetricValues: {}", afterTime - beforeTime);
            for (MetricQueryTimestamp queryWithTimestamp : uncached) {
                // get back original metric query
                // Make a TSDB query from modified start time to original end time
                MetricQuery metricQuery = queryWithTimestamp.getMetricQuery();

                metricQuery.setEndTimestamp(queryWithTimestamp.orignalEndTimestamp);
                filterMetricQueries.add(metricQuery);
                map.put(metricQuery, queryWithTimestamp);
            }
        }

        Map<MetricQuery, List<Metric>> metricsMap = _defaultTsdbService.getMetrics(filterMetricQueries);

        for (Entry<MetricQuery, List<Metric>> entry : metricsMap.entrySet()) {
            MetricQuery metricQuery = entry.getKey();

            MetricQueryTimestamp queryWithTimestamp = map.get(metricQuery);

            if (!compulsoryCacheMiss(metricQuery)) {
                // start thread to insert into cache
                Thread cacheInsertThread = new CacheInsertThread(entry.getValue(), metricQuery);

                cacheInsertThread.start();

                List<Metric> metrics = new ArrayList<Metric>();
                long beforeTime = System.currentTimeMillis();

                for (Metric metric : entry.getValue()) {
                    Metric tempMetric = new Metric(metric);
                    Map<Long, Double> filteredDatapoints = new LinkedHashMap<>();

                    // Trim values based on user time
                    for (Map.Entry<Long, Double> datapoint : metric.getDatapoints().entrySet()) {
                        if (datapoint.getKey() >= queryWithTimestamp.getOriginalStartTimestamp()) {
                            filteredDatapoints.put(datapoint.getKey(), datapoint.getValue());
                        }
                    }
                    tempMetric.setDatapoints(filteredDatapoints);
                    metrics.add(tempMetric);
                }
                result.put(metricQuery, metrics);
                _logger.info("Time spent in trimming metric values: {}", System.currentTimeMillis() - beforeTime);
            } else {
                result.put(metricQuery, entry.getValue());
            }
        }
        return result;
    }

    private boolean compulsoryCacheMiss(MetricQuery query) {
        return query.getStartTimestamp() > System.currentTimeMillis() || query.getEndTimestamp() > System.currentTimeMillis() ||
            (System.currentTimeMillis() - query.getStartTimestamp() < LOWER_START_TIME_LIMIT_IN_MILLIS) ||
            (System.currentTimeMillis() - query.getStartTimestamp() > UPPER_START_TIME_LIMIT_IN_MILLIS) ||
            (System.currentTimeMillis() - query.getEndTimestamp() > END_TIME_LIMIT_IN_MILLIS) || isQueryHavingTagWildcard(query);
    }

    /**
     * From metric query and metric tag generate the cache keys. Fracture the metric data points into each key which stores one day worth of data.
     *
     * @param   metrics  The metrics to generate cache keys for.
     * @param   query    The query to generate cache keys for.
     *
     * @return  The mapping of fractured metrics to their corresponding keys.
     */
    private Map<String, List<Metric>> fractureMetricIntoDayBoundary(List<Metric> metrics, MetricQuery query) {
        Map<String, List<Metric>> cacheMap = new TreeMap<String, List<Metric>>();
        String cacheKey;
        Long nextTimeStampDay;
        Long previousTimeStampDay;
        Long startTimeStampDay = query.getStartTimestamp();
        Long endTimeStampDay = query.getEndTimestamp();

        for (Metric metric : metrics) {
            previousTimeStampDay = startTimeStampDay;
            nextTimeStampDay = getNextDayBoundaryTimeStamp(startTimeStampDay);

            Metric tempMetric = new Metric(metric);
            Map<Long, Double> dataPoints = new LinkedHashMap<>();

            for (Map.Entry<Long, Double> dataPoint : metric.getDatapoints().entrySet()) {
                if (dataPoint.getKey() < nextTimeStampDay) {
                    dataPoints.put(dataPoint.getKey(), dataPoint.getValue());
                } else {
                    while (dataPoint.getKey() >= nextTimeStampDay) {
                        tempMetric.setDatapoints(dataPoints);
                        cacheKey = constructMetricQueryKey(previousTimeStampDay, metric, query);
                        cacheMap.put(cacheKey, new ArrayList<Metric>(Arrays.asList(tempMetric)));
                        cacheKey = constructMetricQueryKey(previousTimeStampDay, query);
                        if (cacheMap.containsKey(cacheKey)) {
                            cacheMap.get(cacheKey).addAll(Arrays.asList(tempMetric));
                        } else {
                            cacheMap.put(cacheKey, new ArrayList<Metric>(Arrays.asList(tempMetric)));
                        }
                        tempMetric = new Metric(metric);
                        dataPoints = new LinkedHashMap<>();
                        previousTimeStampDay = nextTimeStampDay;
                        nextTimeStampDay = getNextDayBoundaryTimeStamp(nextTimeStampDay);
                    }
                    dataPoints.put(dataPoint.getKey(), dataPoint.getValue());
                }
            }
            while (nextTimeStampDay < getNextDayBoundaryTimeStamp(endTimeStampDay)) {
                tempMetric.setDatapoints(dataPoints);
                cacheKey = constructMetricQueryKey(previousTimeStampDay, metric, query);
                cacheMap.put(cacheKey, new ArrayList<Metric>(Arrays.asList(tempMetric)));
                cacheKey = constructMetricQueryKey(previousTimeStampDay, query);
                if (cacheMap.containsKey(cacheKey)) {
                    cacheMap.get(cacheKey).addAll(Arrays.asList(tempMetric));
                } else {
                    cacheMap.put(cacheKey, new ArrayList<Metric>(Arrays.asList(tempMetric)));
                }
                tempMetric = new Metric(metric);
                dataPoints = new LinkedHashMap<>();
                previousTimeStampDay = nextTimeStampDay;
                nextTimeStampDay = getNextDayBoundaryTimeStamp(nextTimeStampDay);
            }
        }
        return cacheMap;
    }

    /**
     * Constructs a cache key from start time stamp boundary, returned metric tags and metric query.
     *
     * @param   startTimeStampBoundary  The start time stamp boundary.
     * @param   metric                  The metric to construct the cache key for.
     * @param   query                   The query to use to construct the cache key.
     *
     * @return  The cache key.
     */
    private String constructMetricQueryKey(Long startTimeStampBoundary, Metric metric, MetricQuery query) {
        StringBuilder sb = new StringBuilder();

        sb.append(startTimeStampBoundary).append(":");
        sb.append(query.getNamespace()).append(":");
        sb.append(query.getScope()).append(":");
        sb.append(query.getMetric()).append(":");

        Map<String, String> treeMap = new TreeMap<>();

        treeMap.putAll(metric.getTags());
        sb.append(treeMap).append(":");
        sb.append(query.getAggregator()).append(":");
        sb.append(query.getDownsampler()).append(":");
        sb.append(query.getDownsamplingPeriod());
        return sb.toString();
    }

    /**
     * Constructs a cache key from start time stamp boundary, and tags in metric query.
     *
     * @param   startTimeStampBoundary  The start time stamp boundary.
     * @param   query                   The query to use to construct the cache key.
     *
     * @return  The cache key.
     */
    private String constructMetricQueryKey(Long startTimeStampBoundary, MetricQuery query) {
        StringBuilder sb = new StringBuilder();

        sb.append(startTimeStampBoundary).append(":");
        sb.append(query.getNamespace()).append(":");
        sb.append(query.getScope()).append(":");
        sb.append(query.getMetric()).append(":");

        // sort the tag key and values within each tag key alphabetically
        Map<String, String> treeMap = new TreeMap<String, String>();

        for (Map.Entry<String, String> tag : query.getTags().entrySet()) {
            String[] tagValues = tag.getValue().split("\\|");

            Arrays.sort(tagValues);

            StringBuilder sbTag = new StringBuilder();
            String separator = "";

            for (String tagValue : tagValues) {
                sbTag.append(separator);
                separator = "|";
                sbTag.append(tagValue);
            }
            treeMap.put(tag.getKey(), sbTag.toString());
        }
        sb.append(treeMap).append(":");
        sb.append(query.getAggregator()).append(":");
        sb.append(query.getDownsampler()).append(":");
        sb.append(query.getDownsamplingPeriod());
        return sb.toString();
    }

    /**
     * Constructs a cache key from start time stamp boundary, and tags in metric query.
     *
     * @param   query                   The query to use to construct the cache key.
     *
     * @return  The cache key.
     */
    private List<String> constructMetricQueryKeys(MetricQuery query) {
        Long startTimeQuery = query.getStartTimestamp();
        Long endTimeQuery = query.getEndTimestamp();
        List<String> metricQueryKeys = new ArrayList<String>();

        for (Long timeStamp = startTimeQuery; timeStamp < endTimeQuery; timeStamp = timeStamp + DURATION_IN_MILLIS) {
            StringBuilder sb = new StringBuilder();

            sb.append(timeStamp).append(":");
            sb.append(query.getNamespace()).append(":");
            sb.append(query.getScope()).append(":");
            sb.append(query.getMetric()).append(":");

            // sort the tag key and values within each tag key alphabetically
            Map<String, String> treeMap = new TreeMap<String, String>();

            for (Map.Entry<String, String> tag : query.getTags().entrySet()) {
                String[] tagValues = tag.getValue().split("\\|");

                Arrays.sort(tagValues);

                StringBuilder sbTag = new StringBuilder();
                String separator = "";

                for (String tagValue : tagValues) {
                    sbTag.append(separator);
                    separator = "|";
                    sbTag.append(tagValue);
                }
                treeMap.put(tag.getKey(), sbTag.toString());
            }
            sb.append(treeMap).append(":");
            sb.append(query.getAggregator()).append(":");
            sb.append(query.getDownsampler()).append(":");
            sb.append(query.getDownsamplingPeriod());
            metricQueryKeys.add(sb.toString());
        }
        return metricQueryKeys;
    }

    /**
     * From a list of queries, gets the metric result for queries that are found in cache, and returns back the metric queries that were uncached
     * along with their original timestamps. If the metric query end time is current time then the last but one hour of data only is returned from
     * the cache, with the last hour of data always returned from TSDB. - This is because we can get the last 1 hour of data from TSDB quickly and it
     * might constantly be getting updated. Hence reading this last 1 hour of data directly from the data store instead of cache.
     *
     * @param   queries     The metric queries.
     * @param   resultsMap  Metrics that were found in cache plus the last 1 hour worth of data from TSDB if necessary.
     *
     * @return  List of metric queries that were not found in cache along with original timestamps.
     */
    private List<MetricQueryTimestamp> _getCachedMetricValues(List<MetricQuery> queries, Map<MetricQuery, List<Metric>> resultsMap) {
        List<MetricQueryTimestamp> uncached = new ArrayList<>(queries.size());
        Long originalStartTimestamp;
        Long originalEndTimestamp;
        long beforeTime;
        long afterTime;

        for (MetricQuery query : queries) {
            List<Metric> metricsForThisQuery = new ArrayList<Metric>();

            originalStartTimestamp = query.getStartTimestamp();
            originalEndTimestamp = query.getEndTimestamp();
            query.setStartTimestamp(convertTimeStampToStartOfDay(query.getStartTimestamp()));
            query.setEndTimestamp(convertTimeStampToBeginningHour(query.getEndTimestamp()));

            long startExecutionTime = System.currentTimeMillis();
            
            // fracture metric query into day boundary
            List<String> cacheMetricQueryKeys = constructMetricQueryKeys(query);

            try {
            	beforeTime = System.currentTimeMillis();
            	
                Map<String, List<String>> keyValueMap = _cacheService.getRange(new LinkedHashSet<String>(cacheMetricQueryKeys), 0, -1);
                boolean allCachedKeysFound = true;

                if (keyValueMap == null) {
                    uncached.add(new MetricQueryTimestamp(query, originalStartTimestamp, originalEndTimestamp, query.getStartTimestamp(),
                            query.getEndTimestamp()));
                    continue;
                }
                for (List<String> value : keyValueMap.values()) {
                    if (value == null || value.isEmpty()) {
                        uncached.add(new MetricQueryTimestamp(query, originalStartTimestamp, originalEndTimestamp, query.getStartTimestamp(),
                                query.getEndTimestamp()));
                        _logger.info("Query not found in cache");
                        allCachedKeysFound = false;
                        break;
                    }
                }
                afterTime = System.currentTimeMillis();
                _logger.info("Time spent in checking if all keys and getting values from cache: {}", afterTime - beforeTime);
                if (allCachedKeysFound) {
                    _logger.info("Query found in cache");
                    beforeTime = System.currentTimeMillis();

                    Metric combinedMetric = null;
                    Map<String, Metric> tagNameAndMetricMap = new HashMap<String, Metric>();

                    for (List<String> value : keyValueMap.values()) {
                        List<Metric> metrics = _mapper.readValue(value.toString(), new TypeReference<List<Metric>>() { });
                        for (Metric metric : metrics) {
                            if (!tagNameAndMetricMap.containsKey(metric.getTags().toString())) {
                                combinedMetric = new Metric(metric);
                                tagNameAndMetricMap.put(metric.getTags().toString(), combinedMetric);
                                combinedMetric.setDatapoints(null);
                            } else {
                                combinedMetric = tagNameAndMetricMap.get(metric.getTags().toString());
                            }

                            Map<Long, Double> filteredDatapoints = new LinkedHashMap<>();

                            // Trim values based on user time
                            for (Map.Entry<Long, Double> datapoint : metric.getDatapoints().entrySet()) {
                                if (datapoint.getKey() >= originalStartTimestamp) {
                                    filteredDatapoints.put(datapoint.getKey(), datapoint.getValue());
                                }
                            }
                            combinedMetric.addDatapoints(filteredDatapoints);
                        }
                    }
                    afterTime = System.currentTimeMillis();
                    _logger.info("Time spent in trimming data: {}", afterTime - beforeTime);
                    
                    // Make a TSDB query from current time to previous hour boundary from current time
                    MetricQuery metricQueryFromLastHour = new MetricQuery(query.getScope(), query.getMetric(), query.getTags(),
                        query.getEndTimestamp(), System.currentTimeMillis());

                    metricQueryFromLastHour.setNamespace(query.getNamespace());
                    metricQueryFromLastHour.setAggregator(query.getAggregator());
                    metricQueryFromLastHour.setDownsampler(query.getDownsampler());
                    metricQueryFromLastHour.setDownsamplingPeriod(query.getDownsamplingPeriod());
                    beforeTime = System.currentTimeMillis();

                    List<Metric> metricsFromLastHour = _defaultTsdbService.getMetrics(Arrays.asList(new MetricQuery[] { metricQueryFromLastHour }))
                        .get(metricQueryFromLastHour);

                    afterTime = System.currentTimeMillis();
                    _logger.info("Time spent in getting last 1 hour of TSDB data: {}", afterTime - beforeTime);
                    beforeTime = System.currentTimeMillis();
                    for (Metric metric : metricsFromLastHour) {
                        combinedMetric = null;
                        for (Map.Entry<String, Metric> tagNameAndMetric : tagNameAndMetricMap.entrySet()) {
                            String tsdbMetricTag = metric.getTags().toString();
                            String localMapMetricTag = tagNameAndMetric.getKey();

                            if (tsdbMetricTag.substring(1, tsdbMetricTag.length() - 1).contains(
                                        localMapMetricTag.substring(1, localMapMetricTag.length() - 1))) {
                                combinedMetric = tagNameAndMetric.getValue();
                                break;
                            }
                        }
                        if (combinedMetric != null) {
                            combinedMetric.addDatapoints(metric.getDatapoints());
                        } else {
                            metricsForThisQuery.add(metric);
                        }
                    }
                    for (Metric metric : tagNameAndMetricMap.values()) {
                        metricsForThisQuery.add(metric);
                    }
                    resultsMap.put(query, metricsForThisQuery);
                    instrumentQueryLatency(_monitorService, query, startExecutionTime);
                    
                    afterTime = System.currentTimeMillis();
                    _logger.info("Time spent in mapping tags in tsdb metrics to tags in cache: {}", afterTime - beforeTime);
                } // end if
            } catch (RuntimeException | IOException ex) {
                _logger.error("Error occurred Reason:", ex.toString());
                uncached.add(new MetricQueryTimestamp(query, originalStartTimestamp, originalEndTimestamp, query.getStartTimestamp(),
                        query.getEndTimestamp()));
            } // end try-catch
        } // end for
        return uncached;
    }

    private Long convertTimeStampToStartOfDay(Long timestamp) {
        Calendar c = GregorianCalendar.getInstance();

        c.setTimeZone(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(timestamp);
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private Long convertTimeStampToBeginningHour(Long timestamp) {
        Calendar c = GregorianCalendar.getInstance();

        c.setTimeZone(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(timestamp);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    private Long getNextDayBoundaryTimeStamp(Long timestampAtHourBoundary) {
        long hourDurationInMillis = DURATION_IN_MILLIS;

        return timestampAtHourBoundary + hourDurationInMillis;
    }

    boolean isQueryHavingTagWildcard(MetricQuery query) {
        Map<String, String> tagMap = query.getTags();

        for (String value : tagMap.values()) {
            if ("*".equals(value)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void dispose() {
        super.dispose();
        _cacheService.dispose();
        _defaultTsdbService.dispose();
    }

    @Override
    public void putMetrics(List<Metric> metrics) {
        _defaultTsdbService.putMetrics(metrics);
    }

    @Override
    public void putAnnotations(List<Annotation> annotations) {
        _defaultTsdbService.putAnnotations(annotations);
    }

    @Override
    public List<Annotation> getAnnotations(List<AnnotationQuery> queries) {
        return _defaultTsdbService.getAnnotations(queries);
    }
    
    private void instrumentQueryLatency(final MonitorService monitorService, final AnnotationQuery query, final long start) {
		String timeWindow = QueryTimeWindow.getWindow(query.getEndTimestamp() - query.getStartTimestamp());
		Map<String, String> tags = new HashMap<String, String>();
		tags.put("type", "metrics");
		tags.put("timeWindow", timeWindow);
		tags.put("cached", "true");
		monitorService.modifyCustomCounter(QUERY_LATENCY_COUNTER, (System.currentTimeMillis() - start), tags);
        monitorService.modifyCustomCounter(QUERY_COUNT_COUNTER, 1, tags);
	}
    
    //~ Inner Classes ********************************************************************************************************************************

    /**
     * Metric Query having the additional information of original start, end timestamp and modified start end timestamp. This will be used to
     * reconstruct the metric query from the modified cached boundary times to user's original query times
     *
     * @author  Dilip Devaraj (ddevaraj@salesforce.com)
     */
    public static final class MetricQueryTimestamp {

        MetricQuery metricQuery;
        Long originalStartTimestamp;
        Long orignalEndTimestamp;
        Long modifiedStartTimestamp;
        Long modifiedEndTimestamp;

        /**
         * Creates a new MetricQueryTimestamp object.
         *
         * @param  metricQuery             The metric query.
         * @param  originalStartTimestamp  The original metric query start timestamp.
         * @param  orignalEndTimestamp     The original metric query end timestamp.
         * @param  modifiedStartTimestamp  The modified metric query start timestamp.
         * @param  modifiedEndTimestamp    The modified metric query end timestamp.
         */
        public MetricQueryTimestamp(MetricQuery metricQuery, Long originalStartTimestamp, Long orignalEndTimestamp, Long modifiedStartTimestamp,
            Long modifiedEndTimestamp) {
            this.metricQuery = metricQuery;
            this.originalStartTimestamp = originalStartTimestamp;
            this.orignalEndTimestamp = orignalEndTimestamp;
            this.modifiedStartTimestamp = modifiedStartTimestamp;
            this.modifiedEndTimestamp = modifiedEndTimestamp;
        }

        /**
         * Returns the metric query.
         *
         * @return  The metric query.
         */
        public MetricQuery getMetricQuery() {
            return metricQuery;
        }

        /**
         * Returns the original start timestamp.
         *
         * @return  The original start timestamp.
         */
        public Long getOriginalStartTimestamp() {
            return originalStartTimestamp;
        }

        /**
         * Returns the original end timestamp.
         *
         * @return  The original end timestamp.
         */
        public Long getOrignalEndTimestamp() {
            return orignalEndTimestamp;
        }

        /**
         * Returns the modified start timestamp.
         *
         * @return  The modified start timestamp.
         */
        public Long getModifiedStartTimestamp() {
            return modifiedStartTimestamp;
        }

        /**
         * Returns the modified end timestamp.
         *
         * @return  The modified end timestamp.
         */
        public Long getModifiedEndTimestamp() {
            return modifiedEndTimestamp;
        }
    }

    /**
     * Thread that inserts metric results to cache using metric query as a basis for the key.
     *
     * @author  Dilip Devaraj (ddevaraj@salesforce.com)
     */
    private class CacheInsertThread extends Thread {

        List<Metric> metrics;
        MetricQuery metricQuery;

        /**
         * Creates a new CacheInsertThread object.
         *
         * @param  metrics      The metrics to insert.
         * @param  metricQuery  The metric corresponding metric query.
         */
        public CacheInsertThread(List<Metric> metrics, MetricQuery metricQuery) {
            this.metrics = metrics;
            this.metricQuery = metricQuery;
        }

        @Override
        public void run() {
            try {
                _insertIntoCache();
            } catch (Exception ex) {
                _logger.error("Error occurred Reason:", ex.toString());
            }
        }

        private int getTimeUntilEndOfHour(Long timestamp) {
            Long timestampPlusOneHour = timestamp + 3600 * 1000;
            Long endHourTimeStamp = convertTimeStampToBeginningHour(timestampPlusOneHour);

            return (int) (endHourTimeStamp - timestamp) / 1000;
        }

        private void _insertIntoCache() {
            // fracture metric into day boundary from returned metrics
            Map<String, List<Metric>> cacheMap = (TreeMap<String, List<Metric>>) fractureMetricIntoDayBoundary(metrics, metricQuery);

            _logger.info("Inserting {} keys to cache", cacheMap.size());
            try {
                for (Map.Entry<String, List<Metric>> entry : cacheMap.entrySet()) {
                    for (Metric metric : entry.getValue()) {
                        _cacheService.append(entry.getKey(), _mapper.writeValueAsString(metric), _cacheService.getCacheExpirationTime());
                        _cacheService.expire(entry.getKey(), getTimeUntilEndOfHour(System.currentTimeMillis()));
                    }
                }
            } catch (Exception e) {
                _logger.error("Error occurred Reason:", e.toString());
            }
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

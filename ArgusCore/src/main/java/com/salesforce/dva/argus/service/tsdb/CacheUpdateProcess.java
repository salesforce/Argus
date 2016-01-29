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

import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.CacheService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.tsdb.MetricQuery.Aggregator;
import com.salesforce.dva.argus.system.SystemConfiguration;
import org.slf4j.LoggerFactory;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Process that runs at beginning of every hour and updates the cache entry for the previous hour boundary.
 *
 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
 */
public class CacheUpdateProcess {

    //~ Static fields/initializers *******************************************************************************************************************

    private static long DURATION_DAY_MILLIS = 86400000L;
    private static long TIME_BETWEEN_UPDATES_MILLIS = 60 * 60 * 1000;
    private static long HOUR_IN_MILLIS = 3600 * 1000;
    private static int TIMEOUT_WORKER_EXECUTION_COMPLETION_SECONDS = 5 * 60;
    private static int NUMBER_EXECUTOR_THREADS = 50;

    //~ Instance fields ******************************************************************************************************************************

    Logger _logger = (Logger) LoggerFactory.getLogger(CacheUpdateProcess.class);
    private final CacheService _cacheService;
    private final TSDBService _tsdbService;
    ScheduledExecutorService _scheduledExecutorService;
    private ObjectMapper _mapper = new ObjectMapper();

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new CacheUpdateProcess object.
     *
     * @param  config        The system configuration.  Cannot be null.
     * @param  cacheService  The cache service.  Cannot be null.
     */
    public CacheUpdateProcess(SystemConfiguration config, CacheService cacheService) {
        _mapper = new ObjectMapper();
        _cacheService = cacheService;
        _tsdbService = new DefaultTSDBService(config);
        _scheduledExecutorService = _createScheduledExecutorService();
    }

    //~ Methods **************************************************************************************************************************************

    private static Long convertTimeStampToBeginningHour(Long timestamp) {
        Calendar c = GregorianCalendar.getInstance();

        c.setTimeZone(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(timestamp);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        return c.getTimeInMillis();
    }

    //~ Methods **************************************************************************************************************************************

    private ScheduledExecutorService _createScheduledExecutorService() {
        return Executors.newScheduledThreadPool(1);
    }

    /** Starts the scheduled executor service. */
    public void start() {
        Long currentTimeStamp = System.currentTimeMillis();
        Long initialDelay = (convertTimeStampToBeginningHour(currentTimeStamp) + TIME_BETWEEN_UPDATES_MILLIS) - currentTimeStamp;
        CacheUpdateThread cacheUpdateThread = new CacheUpdateThread();

        _scheduledExecutorService.scheduleAtFixedRate(cacheUpdateThread, initialDelay, TIME_BETWEEN_UPDATES_MILLIS, TimeUnit.MILLISECONDS);
    }

    /** Shuts down the scheduled executor service. */
    public void dispose() {
        _logger.info("Shutting down scheduled cache update executor service");
        _scheduledExecutorService.shutdown();
        try {
            if (!_scheduledExecutorService.awaitTermination(5, TimeUnit.SECONDS)) {
                _logger.warn("Shutdown of scheduled cache update executor service timed out after 10 seconds.");
                _scheduledExecutorService.shutdownNow();
            }
        } catch (InterruptedException ex) {
            _logger.warn("Shutdown of executor service was interrupted.");
            Thread.currentThread().interrupt();
        }
    }

    //~ Inner Classes ********************************************************************************************************************************

    /**
     * Worker threads that get invoked through Executor Service from CacheUpdateThread. Each worker thread updates one cache key
     *
     * @author  Dilip Devaraj (ddevaraj@salesforce.com)
     */
    public class WorkerThread implements Runnable {

        private String key;

        /**
         * Creates a new WorkerThread object.
         *
         * @param  key  The cache key to update.
         */
        public WorkerThread(String key) {
            this.key = key;
        }

        @Override
        public void run() {
            try {
                updateCacheKey();
            } catch (Exception e) {
                _logger.error("Exception while updating cache", e.getMessage());
            }
        }

        private void updateCacheKey() throws JsonProcessingException {
            long beforeTime;
            long afterTime;
            MetricQuery metricQuery = generateMetricQueryFromKey(key);

            beforeTime = System.currentTimeMillis();
            _logger.info("Getting data for metric query from TSDB {}", metricQuery);

            List<Metric> metrics = _tsdbService.getMetrics(Arrays.asList(new MetricQuery[] { metricQuery })).get(metricQuery);

            afterTime = System.currentTimeMillis();
            _logger.info("Time to get data from TSDB {}", afterTime - beforeTime);
            for (Metric metric : metrics) {
                _cacheService.append(key, _mapper.writeValueAsString(metric));
            }
            _logger.info("{} Metrics read from TSDB to Cache in last hour. Updated key {}", metrics.size(), key);
        }

        private MetricQuery generateMetricQueryFromKey(String key) {
            MetricQuery metricQuery = null;

            if (key != null) {
                String[] keyParts = key.split(":");
                Map<String, String> tagMap = new HashMap<String, String>();

                keyParts[4] = keyParts[4].substring(1, keyParts[4].length() - 1);

                String[] pairs = keyParts[4].split(",");

                for (int i = 0; i < pairs.length; i++) {
                    String pair = pairs[i];

                    if (!pair.isEmpty()) {
                        String[] keyValue = pair.split("=");

                        tagMap.put(keyValue[0], keyValue[1]);
                    }
                }
                metricQuery = new MetricQuery(keyParts[2], keyParts[3], tagMap,
                    convertTimeStampToBeginningHour(System.currentTimeMillis() - HOUR_IN_MILLIS),
                    convertTimeStampToBeginningHour(System.currentTimeMillis()));
                if (!"null".equals(keyParts[1])) {
                    metricQuery.setNamespace(keyParts[1]);
                }
                metricQuery.setAggregator(Aggregator.fromString(keyParts[5]));
                if (!"null".equals(keyParts[6])) {
                    metricQuery.setDownsampler(Aggregator.fromString(keyParts[6]));
                }
                if (!"null".equals(keyParts[7])) {
                    metricQuery.setDownsamplingPeriod(Long.parseLong(keyParts[7]));
                }
            }
            return metricQuery;
        }
    }

    /**
     * Scheduled cache update thread that runs at beginning of every hour, and updates all keys in cache that begin from current day, with the
     * previous hour data.
     *
     * @author  Dilip Devaraj (ddevaraj@salesforce.com)
     */
    private class CacheUpdateThread implements Runnable {

        @Override
        public void run() {
            try {
                _updateCache();
            } catch (Exception ex) {
                _logger.error("Error occured Reason: {}", ex.getMessage());
            }
        }

        private void _updateCache() {
            long totalBeforeTime = System.currentTimeMillis();
            boolean shouldCopyPreviousDaysKeys = false;
            String cacheKeyPrefixPattern = null;
            Map<String, Future<?>> mapFutureAndKeys = new HashMap<>();

            if (isTimeStampAtBeginningHourOfDay(System.currentTimeMillis())) {
                cacheKeyPrefixPattern = convertTimeStampToStartOfDay(System.currentTimeMillis() - HOUR_IN_MILLIS).toString() + ":*";
            } else if (isTimeStampFromFirstHourOfDay(System.currentTimeMillis())) {
                shouldCopyPreviousDaysKeys = true;
                cacheKeyPrefixPattern = convertTimeStampToStartOfDay(System.currentTimeMillis() - 2 * HOUR_IN_MILLIS).toString() + ":*";
            } else {
                cacheKeyPrefixPattern = convertTimeStampToStartOfDay(System.currentTimeMillis()).toString() + ":*";
            }

            long beforeTime = System.currentTimeMillis();
            Set<String> cacheMetricQueryKeys = _cacheService.getKeysByPattern(cacheKeyPrefixPattern);

            if (shouldCopyPreviousDaysKeys) {
                cacheMetricQueryKeys = modifyCacheMetricQueryKeysTimeStamp(cacheMetricQueryKeys);
            }

            long afterTime = System.currentTimeMillis();

            _logger.info("Time to get all keys matching pattern {}", afterTime - beforeTime);
            if ((cacheMetricQueryKeys != null) && !cacheMetricQueryKeys.isEmpty()) {
                ExecutorService executor = Executors.newFixedThreadPool(NUMBER_EXECUTOR_THREADS);

                for (String key : cacheMetricQueryKeys) {
                    Runnable worker = new WorkerThread(key);

                    mapFutureAndKeys.put(key, executor.submit(worker));
                }
                executor.shutdown();
                try {
                    if (!executor.awaitTermination(TIMEOUT_WORKER_EXECUTION_COMPLETION_SECONDS, TimeUnit.SECONDS)) {
                        _logger.warn("Shutdown of scheduled cache update executor service timed out after {} seconds",
                            TIMEOUT_WORKER_EXECUTION_COMPLETION_SECONDS);
                        executor = Executors.newFixedThreadPool(NUMBER_EXECUTOR_THREADS);

                        // Retry one time the pending worker thread tasks that may have not have completed
                        // due to delay in getting metrics from TSDB
                        for (Entry<String, Future<?>> entry : mapFutureAndKeys.entrySet()) {
                            if (entry.getValue().cancel(true)) {
                                executor.execute(new WorkerThread(entry.getKey()));
                            }
                        }
                        executor.shutdown();
                        if (!executor.awaitTermination(TIMEOUT_WORKER_EXECUTION_COMPLETION_SECONDS, TimeUnit.SECONDS)) {
                            _logger.warn("Shutdown of scheduled cache update executor service timed out after {} seconds",
                                TIMEOUT_WORKER_EXECUTION_COMPLETION_SECONDS);
                            executor.shutdownNow();
                        }
                    }
                } catch (InterruptedException e) {
                    _logger.warn("Shutdown of executor service was interrupted.");
                    Thread.currentThread().interrupt();
                }
                _logger.info("All threads completed");

                long totalAfterTime = System.currentTimeMillis();

                _logger.info("Total Time to update data {}", totalAfterTime - totalBeforeTime);
            }
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

        private boolean isTimeStampAtBeginningHourOfDay(Long timestamp) {
            Calendar c = GregorianCalendar.getInstance();

            c.setTimeZone(TimeZone.getTimeZone("UTC"));
            c.setTimeInMillis(timestamp);
            return (c.get(Calendar.HOUR_OF_DAY) == 0) ? true : false;
        }

        private boolean isTimeStampFromFirstHourOfDay(Long timestamp) {
            Calendar c = GregorianCalendar.getInstance();

            c.setTimeZone(TimeZone.getTimeZone("UTC"));
            c.setTimeInMillis(timestamp);
            return (c.get(Calendar.HOUR_OF_DAY) == 1) ? true : false;
        }

        private Set<String> modifyCacheMetricQueryKeysTimeStamp(Set<String> cacheMetricQueryKeys) {
            Set<String> keys = new HashSet<String>();

            for (String key : cacheMetricQueryKeys) {
                int endTimeStampIndex = key.indexOf(':');
                Long timeStampOfNextDay = Long.parseLong(key.substring(0, endTimeStampIndex)) + DURATION_DAY_MILLIS;

                keys.add(timeStampOfNextDay.toString() + ":" + key.substring(endTimeStampIndex + 1));
            }
            return keys;
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

package com.salesforce.dva.argus.service.async.metric;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.salesforce.dva.argus.entity.AsyncBatchedMetricQuery;
import com.salesforce.dva.argus.entity.BatchMetricQuery;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.CacheService;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.MetricQueueService;
import com.salesforce.dva.argus.service.metric.MetricReader;
import com.salesforce.dva.argus.service.metric.ParseException;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Created by cguan on 6/1/16.
 */
public class CacheBasedMetricQueueService extends DefaultService implements MetricQueueService {
    private static final Logger LOGGER = LoggerFactory.getLogger(CacheBasedMetricQueueService.class);
    private final SystemConfiguration _config;
    private final CacheService _cacheService;
    private final Provider<MetricReader<Metric>> _metricReaderProviderForMetrics;

    // Keys to store async queries at
    private static final String ROOT = "async/";
    private static final String LOW_HEAD = ROOT + "low/front";
    private static final String LOW_TAIL = ROOT + "low/back";
    private static final String LOW_SIZE = ROOT + "low/size";
    private static final String HIGH_HEAD = ROOT + "high/front";
    private static final String HIGH_TAIL = ROOT + "high/back";
    private static final String HIGH_SIZE = ROOT + "high/size";

    @Inject
    public CacheBasedMetricQueueService(SystemConfiguration config, CacheService cacheService, Provider<MetricReader<Metric>> metricsprovider) {
        super(config);
        requireArgument(cacheService != null, "Cache service cannot be null.");
        _config = config;
        _cacheService = cacheService;
        _metricReaderProviderForMetrics = metricsprovider;
        if (_cacheService.get(LOW_SIZE) == null) {
            _cacheService.put(LOW_HEAD, String.valueOf(1), Integer.MAX_VALUE);
            _cacheService.put(LOW_TAIL, String.valueOf(0), Integer.MAX_VALUE);
            _cacheService.put(LOW_SIZE, String.valueOf(0), Integer.MAX_VALUE);
        }
        if (_cacheService.get(HIGH_SIZE) == null) {
            _cacheService.put(HIGH_HEAD, String.valueOf(1), Integer.MAX_VALUE);
            _cacheService.put(HIGH_TAIL, String.valueOf(0), Integer.MAX_VALUE);
            _cacheService.put(HIGH_SIZE, String.valueOf(0), Integer.MAX_VALUE);
        }
    }

    @Override
    public synchronized void enqueue(AsyncBatchedMetricQuery query, int priority) {
        String tailPath, sizePath;
        if (BatchMetricQuery.Priority.fromInt(priority) == BatchMetricQuery.Priority.HIGH) {
            tailPath = HIGH_TAIL;
            sizePath = HIGH_SIZE;
        } else {
            tailPath = LOW_TAIL;
            sizePath = LOW_SIZE;
        }
        long tailId = Long.valueOf(_cacheService.get(tailPath)) + 1;
        long size = Long.valueOf(_cacheService.get(sizePath));
        _cacheService.put(tailPath, String.valueOf(tailId), Integer.MAX_VALUE);
        _cacheService.put(sizePath, String.valueOf(size + 1), Integer.MAX_VALUE);
        query.setQueueId(tailId);
        query.setStatus(BatchMetricQuery.Status.QUEUED);
        query.save(_cacheService);
    }

    @Override
    public synchronized AsyncBatchedMetricQuery dequeueAndProcess(int priority) {
        String headPath, sizePath;
        if (BatchMetricQuery.Priority.fromInt(priority) == BatchMetricQuery.Priority.HIGH) {
            headPath = HIGH_HEAD;
            sizePath = HIGH_SIZE;
        } else {
            headPath = LOW_HEAD;
            sizePath = LOW_SIZE;
        }
        long size = Long.valueOf(_cacheService.get(sizePath));
        if (size == 0) {
            return null;
        }
        long headId = Long.valueOf(_cacheService.get(headPath));
        AsyncBatchedMetricQuery query = findById(headId);
        if (query != null) {
            _cacheService.put(headPath, String.valueOf(headId + 1), Integer.MAX_VALUE);
            _cacheService.put(sizePath, String.valueOf(size - 1), Integer.MAX_VALUE);
            LOGGER.info("MetricQueueService/Dequeued " + headId);
        } else {
            LOGGER.info("DequeueAndProcess hit invalid null findById case");
            return null;
        }
        query.setStatus(BatchMetricQuery.Status.PROCESSING);
        query.save(_cacheService);
        MetricReader<Metric> reader = _metricReaderProviderForMetrics.get();
        try {
            LOGGER.info("MetricQueueService/parsing " + query.getExpression());
            List<Metric> results = reader.parse(query.getExpression(), query.getOffset(), Metric.class);
            if (results.size() == 0) {
                LOGGER.error("MetricQueueService/Parsed out size 0");
                return null;
            }
            query.setResult(results.get(0));
            query.setStatus(BatchMetricQuery.Status.DONE);
            query.save(_cacheService);
            return query;
        } catch (ParseException ex) {
            throw new SystemException("Failed to parse the given expression", ex);
        }
    }

    public AsyncBatchedMetricQuery findById(long id) {
        String json = _cacheService.get(ROOT + id);
        if (json == null) {
            LOGGER.error("MetricQueueService/Unable to find key " + ROOT + id);
        }
        return new AsyncBatchedMetricQuery(json);
    }
}

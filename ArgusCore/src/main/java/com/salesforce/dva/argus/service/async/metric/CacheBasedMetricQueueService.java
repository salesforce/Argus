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
    private static final Object cacheLock = new Object();
    private final SystemConfiguration _config;
    private final CacheService _cacheService;
    private final Provider<MetricReader<Metric>> _metricReaderProviderForMetrics;

    // Keys to store async queries at
    private static final String ROOT = "async/";
    private static final String LOW_HEAD = ROOT + "low/front";
    private static final String LOW_TAIL = ROOT + "low/back";
    private static final String HIGH_HEAD = ROOT + "high/front";
    private static final String HIGH_TAIL = ROOT + "high/back";

    @Inject
    public CacheBasedMetricQueueService(SystemConfiguration config, CacheService cacheService, Provider<MetricReader<Metric>> metricsprovider) {
        super(config);
        requireArgument(cacheService != null, "Cache service cannot be null.");
        _config = config;
        _cacheService = cacheService;
        _metricReaderProviderForMetrics = metricsprovider;
        if (_cacheService.get(LOW_HEAD) == null) {
            _cacheService.put(LOW_HEAD, String.valueOf(1), Integer.MAX_VALUE);
            _cacheService.put(LOW_TAIL, String.valueOf(0), Integer.MAX_VALUE);
        }
        if (_cacheService.get(HIGH_HEAD) == null) {
            _cacheService.put(HIGH_HEAD, String.valueOf(1), Integer.MAX_VALUE);
            _cacheService.put(HIGH_TAIL, String.valueOf(0), Integer.MAX_VALUE);
        }
    }

    @Override
    public synchronized void enqueue(AsyncBatchedMetricQuery query, int priority) {
        String tailPath;
        if (BatchMetricQuery.Priority.fromInt(priority) == BatchMetricQuery.Priority.HIGH) {
            tailPath = HIGH_TAIL;
        } else {
            tailPath = LOW_TAIL;
        }

        long tailId = Long.valueOf(_cacheService.get(tailPath)) + 1;
        _cacheService.put(tailPath, String.valueOf(tailId), Integer.MAX_VALUE);
        LOGGER.info("MetricQueueService.enqueue/" + tailId + "/" + query.getExpression());

        query.setQueueId(tailId);
        query.setStatus(BatchMetricQuery.Status.QUEUED);
        query.save(_cacheService);
    }

    @Override
    public synchronized AsyncBatchedMetricQuery dequeueAndProcess(int priority) {
        String headPath;
        if (BatchMetricQuery.Priority.fromInt(priority) == BatchMetricQuery.Priority.HIGH) {
            headPath = HIGH_HEAD;
        } else {
            headPath = LOW_HEAD;
        }

        long headId = Long.valueOf(_cacheService.get(headPath));
        AsyncBatchedMetricQuery query = findById(headId);
        if (query != null) {
            _cacheService.put(headPath, String.valueOf(headId + 1), Integer.MAX_VALUE);
            LOGGER.info("MetricQueueService.dequeue/" + headId + "/" + query.getExpression());
        } else {
            return null;
        }

        query.setStatus(BatchMetricQuery.Status.PROCESSING);
        query.save(_cacheService);
        MetricReader<Metric> reader = _metricReaderProviderForMetrics.get();
        try {
            LOGGER.info("MetricQueueService/parsing " + query.getExpression());
            List<Metric> results = reader.parse(query.getExpression(), query.getOffset(), Metric.class);
            query.setStatus(BatchMetricQuery.Status.DONE);
            if (results.size() == 0) {
                query.save(_cacheService);
                return null;
            }
            query.setResult(results.get(0));
            query.save(_cacheService);
            return query;
        } catch (ParseException ex) {
            throw new SystemException("Failed to parse the given expression", ex);
        }
    }

    public AsyncBatchedMetricQuery findById(long id) {
        String json = _cacheService.get(ROOT + id);
        if (json == null) {
            return null;
        }
        return new AsyncBatchedMetricQuery(json);
    }
}

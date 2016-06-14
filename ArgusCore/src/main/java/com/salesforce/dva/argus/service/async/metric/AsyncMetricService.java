package com.salesforce.dva.argus.service.async.metric;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.salesforce.dva.argus.entity.AsyncBatchedMetricQuery;
import com.salesforce.dva.argus.entity.BatchMetricQuery;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.*;
import com.salesforce.dva.argus.service.metric.MetricReader;
import com.salesforce.dva.argus.service.metric.ParseException;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Created by cguan on 6/1/16.
 */
public class AsyncMetricService extends DefaultService implements MetricService {
    //~ Instance fields ******************************************************************************************************************************

    @SLF4JTypeListener.InjectLogger
    private Logger _logger;
    private final MonitorService _monitorService;
    private final MetricQueueService _metricQueueService;
    private final CacheService _cacheService;
    private final Provider<MetricReader<Metric>> _metricReaderProviderForMetrics;
    private final Provider<MetricReader<MetricQuery>> _metricReaderProviderForQueries;

    //~ Constructors *********************************************************************************************************************************

    @Inject
    protected AsyncMetricService(MonitorService monitorService, Provider<MetricReader<Metric>> metricsprovider,
                                 Provider<MetricReader<MetricQuery>> queryprovider, MetricQueueService metricQueueService, CacheService cacheService, SystemConfiguration config) {
        super(config);
        requireArgument(monitorService != null, "Monitor service cannot be null.");
        requireArgument(metricQueueService != null, "Metric queue service cannot be null.");
        requireArgument(cacheService != null, "Cache service cannot be null.");
        _monitorService = monitorService;
        _metricQueueService = metricQueueService;
        _cacheService = cacheService;
        _metricReaderProviderForMetrics = metricsprovider;
        _metricReaderProviderForQueries = queryprovider;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public String getAsyncMetrics(List<String> expressions, long offset, long ttl, String ownerName) {
        int priority = 1;
        BatchMetricQuery batchQuery = new BatchMetricQuery(expressions, offset, priority, ttl, ownerName);
        for (AsyncBatchedMetricQuery query: batchQuery.getQueries()) {
            _metricQueueService.enqueue(query, 1);
        }
        batchQuery.save(_cacheService);
        return batchQuery.getBatchId();
    }

    @Override
    public List<Metric> getMetrics(String expression) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Metric> getMetrics(String expression, long offset) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Metric> getMetrics(List<String> expressions) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Metric> getMetrics(List<String> expressions, long offset) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<MetricQuery> getQueries(String expression) {
        requireNotDisposed();
        return getQueries(expression, 0);
    }

    @Override
    public List<MetricQuery> getQueries(String expression, long offset) {
        requireNotDisposed();
        return getQueries(Arrays.asList(new String[] { expression }), offset);
    }

    @Override
    public List<MetricQuery> getQueries(List<String> expressions) {
        requireNotDisposed();
        return getQueries(expressions, 0);
    }

    @Override
    public List<MetricQuery> getQueries(List<String> expressions, long offset) {
        requireNotDisposed();
        SystemAssert.requireArgument(MetricReader.isValid(expressions), "Illegal metric expression found: " + expressions);

        MetricReader<MetricQuery> reader = _metricReaderProviderForQueries.get();
        List<MetricQuery> queries = new ArrayList<>(expressions.size());

        try {
            for (String expression : expressions) {
                _logger.debug("Creating metric query for expression {}", expression);
                queries.addAll(reader.parse(expression, offset, MetricQuery.class));
            }
        } catch (ParseException ex) {
            throw new SystemException("Failed to parse the given expression", ex);
        }
        return queries;
    }

    @Override
    public void dispose() {
        super.dispose();
        // _tsdbService.dispose();
    }

    private long _getDatapointsAcrossMetrics(List<Metric> metrics) {
        long dataPointsSize = 0;

        for (Metric metric : metrics) {
            dataPointsSize += metric.getDatapoints().size();
        }
        return dataPointsSize;
    }
}

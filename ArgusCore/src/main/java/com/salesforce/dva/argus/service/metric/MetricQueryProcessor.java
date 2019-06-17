package com.salesforce.dva.argus.service.metric;

import com.google.inject.Inject;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.DiscoveryService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.MonitorService.Counter;
import com.salesforce.dva.argus.service.QueryStoreService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.TSDBService.QueryStartTimeWindow;
import com.salesforce.dva.argus.service.TSDBService.QueryTimeSeriesExpansion;
import com.salesforce.dva.argus.service.TSDBService.QueryTimeWindow;
import com.salesforce.dva.argus.service.metric.transform.Transform;
import com.salesforce.dva.argus.service.metric.transform.TransformFactory;
import com.salesforce.dva.argus.service.metric.transform.TransformFactory.Function;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.service.tsdb.MetricQuery.Aggregator;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.util.QueryContext;
import com.salesforce.dva.argus.util.TSDBQueryExpression;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * This class has methods which are used to evaluate the metric query expression once it is parsed
 */
public class MetricQueryProcessor {

    private static Logger logger = LoggerFactory.getLogger(MetricQueryProcessor.class);

    private DiscoveryService _discoveryService;

    private TSDBService _tsdbService;
    
    private MonitorService _monitorService;

    private QueryStoreService _queryStoreService;

    private TransformFactory _factory;
    
    private static final String HOSTNAME;
    
    static {
        HOSTNAME = SystemConfiguration.getHostname();
    }


    @Inject
    public MetricQueryProcessor(TSDBService tsdbService, DiscoveryService discoveryService, MonitorService monitorService, TransformFactory factory, QueryStoreService queryStoreService) {
        _tsdbService = tsdbService;
        _discoveryService = discoveryService;
        _monitorService=monitorService;
        _queryStoreService=queryStoreService;
        _factory = factory;
    }

    public MetricQueryResult evaluateQuery(QueryContext context, long relativeTo){
        MetricQueryResult queryResult = new MetricQueryResult();
        if(context.getChildContexts()!=null && context.getChildContexts().size()!=0) {
            for(QueryContext childCtx : context.getChildContexts()) {
                mergeQueryResults(queryResult, evaluateQuery(childCtx, relativeTo));
            }
        }

        if(context.getExpression()!=null) {
            mergeQueryResults(queryResult, evaluateTSDBQuery(context.getExpression()));
        }



        if(context.getTransform()!=null) {
            boolean constantsOnly = false;
            // fill transform needs to know whether its generating a constant line or its filling gaps in some computed metrics
            if((TransformFactory.Function.FILL.equals(context.getTransform()) && (context.getChildContexts()==null || context.getChildContexts().size()==0) && context.getExpression()==null)) {
                constantsOnly = true;
            }
            queryResult.setMetricsList(evaluateTransform(context.getTransform(), queryResult.getMetricsList(), context.getConstants(), relativeTo, constantsOnly, context));
            queryResult.addTransform(context.getTransform());
        }
        queryResult.setExpandedTimeSeriesRange(QueryTimeSeriesExpansion.getExpandedTimeSeriesRange(queryResult.getNumTSDBResults()));
        queryResult.setQueryTimeWindow(QueryTimeWindow.getWindow(queryResult.getQueryTimeRangeInMillis()));
        queryResult.setQueryStartTimeWindow(QueryStartTimeWindow.getWindow(relativeTo - queryResult.getQueryStartTimeMillis()));
        return queryResult;
    }

    public void mergeQueryResults(MetricQueryResult parentResult, MetricQueryResult childResult) {
        parentResult.getMetricsList().addAll(childResult.getMetricsList());
        parentResult.setNumTSDBResults(parentResult.getNumTSDBResults() + childResult.getNumTSDBResults());
        parentResult.setNumDiscoveryQueries(parentResult.getNumDiscoveryQueries() + childResult.getNumDiscoveryQueries());
        parentResult.setNumDiscoveryResults(parentResult.getNumDiscoveryResults() + childResult.getNumDiscoveryResults());
        parentResult.addInboundMetricQueries(childResult.getInboundMetricQueries());
        parentResult.addTransforms(childResult.getTransforms());
        if(childResult.getQueryTimeRangeInMillis() > parentResult.getQueryTimeRangeInMillis()) {
            parentResult.setQueryTimeRangeInMillis(childResult.getQueryTimeRangeInMillis());
        }
        if(childResult.getQueryStartTimeMillis() < parentResult.getQueryStartTimeMillis()) {
            parentResult.setQueryStartTimeMillis(childResult.getQueryStartTimeMillis());
        }
    }

    private MetricQueryResult evaluateTSDBQuery(TSDBQueryExpression expression)  {
        final long start = System.currentTimeMillis();

        MetricQueryResult queryResult = new MetricQueryResult();
        Long startTimestamp = expression.getStartTimestamp();
        Long endTimestamp = expression.getEndTimestamp();
        String namespace = expression.getNamespace();
        String scope = expression.getScope();
        String metric = expression.getMetric();
        Aggregator aggregator = expression.getAggregator();
        Map<String, String> tags = expression.getTags();
        Aggregator downsampler = expression.getDownsampler();
        Long downsamplingPeriod = expression.getDownsamplingPeriod();

        MetricQuery query = new MetricQuery(scope, metric, tags, startTimestamp, endTimestamp);
        query.setNamespace(namespace);
        query.setDownsampler(downsampler);
        query.setDownsamplingPeriod(downsamplingPeriod);
        query.setPercentile(expression.getPercentile());
        query.setShowHistogramBuckets(expression.isShowHistogramBuckets());
        if(!query.getShowHistogramBuckets() && query.getPercentile()==null) {
            query.setAggregator(getSubstituteAggregator(aggregator));
        }else {
            query.setAggregator(aggregator);
        }

        List<MetricQuery> queries = _discoveryService.getMatchingQueries(query);

        if (queries.size() == 0) { // No metrics inflow to argus in last DEFAULT_RETENTION_DISCOVERY_DAYS days. Save the raw query processed within inBoundMetricQuery.
            queryResult.addInboundMetricQuery(query);
        }

        // Stores all the user queries
        List<Metric> metricsQueried = new ArrayList<>();
        for (MetricQuery metricQuery:queries) {
            metricsQueried.add(new Metric(metricQuery.getScope(),metricQuery.getMetric()));
            queryResult.addInboundMetricQuery(metricQuery);
        }

        try {
            _queryStoreService.putArgusWsQueries(metricsQueried);
        }
        catch (Exception e)
        {
            logger.warn("Inserting Queries to QueryStore failed due to "+e);
        }
        List<Metric> metrics = new ArrayList<Metric>();
        Map<MetricQuery, List<Metric>> metricsMap = _tsdbService.getMetrics(queries);
        for(List<Metric> m : metricsMap.values()) {
            metrics.addAll(m);
        }
        Collections.sort(metrics);
        queryResult.setMetricsList(metrics);
        queryResult.setQueryTimeRangeInMillis(endTimestamp-startTimestamp);
        queryResult.setQueryStartTimeMillis(startTimestamp);
        if(queries.size() !=1 || queries.get(0) != query) {
            queryResult.setNumDiscoveryResults(queries.size());
            queryResult.setNumDiscoveryQueries(1);
        }
        queryResult.setNumTSDBResults(metrics.size());

        final long time = System.currentTimeMillis() - start;
        _monitorService.modifyCounter(Counter.METRICQUERYPROCESSOR_EVALUATETSDBQUERY_LATENCY, time, null);
        _monitorService.modifyCounter(Counter.METRICQUERYPROCESSOR_EVALUATETSDBQUERY_COUNT, 1, null);


        return queryResult;
    }

    /*
     * We replace the aggregator to provide a non-interpolated default behavior for MIN, MAX and SUM
     */
    private Aggregator getSubstituteAggregator(Aggregator aggregator) {
        switch (aggregator) {
        case MIN:
            return Aggregator.MIMMIN;
        case MAX: 
            return Aggregator.MIMMAX;
        case SUM:
            return Aggregator.ZIMSUM;
        case IMIN:
            return Aggregator.MIN;
        case IMAX:
            return Aggregator.MAX;
        case ISUM:
            return Aggregator.SUM;
        default:
            return aggregator;
        }
    }

    private List<Metric> evaluateTransform(Function function, List<Metric> result, List<String> constants, long relativeTo, boolean constantsOnly, QueryContext currentQueryContext)  {
        if(TransformFactory.Function.FILL.getName().equals(function.getName())) {
            constants.add(String.valueOf(relativeTo));
            constants.add(String.valueOf(constantsOnly));
        }

        Transform transform = _factory.getTransform(function.getName());
        Map<String, String> tags = new HashMap<>();
        tags.put("host", HOSTNAME);
        tags.put("transform", function.getName());
        _monitorService.modifyCounter(Counter.TRANSFORMS_EVALUATED, 1, tags);
        List<Metric> metrics = ((constants == null || constants.isEmpty()) ? transform.transform(currentQueryContext, result) : transform.transform(currentQueryContext, result, constants));
        Collections.sort(metrics);
        return metrics;
    }
}

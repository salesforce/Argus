package com.salesforce.dva.argus.service.metric;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.google.inject.Inject;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.DiscoveryService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.TSDBService.QueryStartTimeWindow;
import com.salesforce.dva.argus.service.TSDBService.QueryTimeSeriesExpansion;
import com.salesforce.dva.argus.service.TSDBService.QueryTimeWindow;
import com.salesforce.dva.argus.service.metric.transform.Transform;
import com.salesforce.dva.argus.service.metric.transform.TransformFactory;
import com.salesforce.dva.argus.service.metric.transform.TransformFactory.Function;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.service.tsdb.MetricQuery.Aggregator;
import com.salesforce.dva.argus.util.QueryContext;
import com.salesforce.dva.argus.util.TSDBQueryExpression;

/*
 * This class has methods which are used to evaluate the metric query expression once it is parsed
 */
public class MetricQueryProcessor {

	private DiscoveryService _discoveryService;

	private TSDBService _tsdbService;

	private TransformFactory _factory;

	@Inject
	public MetricQueryProcessor(TSDBService tsdbService, DiscoveryService discoveryService, TransformFactory factory) {
		_tsdbService = tsdbService;
		_discoveryService = discoveryService;
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
		if(childResult.getQueryTimeRangeInMillis() > parentResult.getQueryTimeRangeInMillis()) {
			parentResult.setQueryTimeRangeInMillis(childResult.getQueryTimeRangeInMillis());
		}
		if(childResult.getQueryStartTimeMillis() < parentResult.getQueryStartTimeMillis()) {
			parentResult.setQueryStartTimeMillis(childResult.getQueryStartTimeMillis());
		}
	}

	private MetricQueryResult evaluateTSDBQuery(TSDBQueryExpression expression)  {
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

		List<Metric> metrics = new ArrayList<Metric>();
		Map<MetricQuery, List<Metric>> metricsMap = _tsdbService.getMetrics(queries);
		for(List<Metric> m : metricsMap.values()) {
			metrics.addAll(m);
		}
		queryResult.setMetricsList(metrics);
		queryResult.setQueryTimeRangeInMillis(endTimestamp-startTimestamp);
		queryResult.setQueryStartTimeMillis(startTimestamp);
		if(queries.size() !=1 || queries.get(0) != query) {
			queryResult.setNumDiscoveryResults(queries.size());
			queryResult.setNumDiscoveryQueries(1);
		}
		queryResult.setNumTSDBResults(metrics.size());
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
		return ((constants == null || constants.isEmpty()) ? transform.transform(currentQueryContext, result) : transform.transform(currentQueryContext, result, constants));
	}
}

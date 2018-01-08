package com.salesforce.dva.argus.service.tsdb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;

/**
 * The base class for performing query federations/forks and metric result merge/join.
 *
 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
 */
public abstract class QueryFederation{
	/**
	 *  Federate queries to obtain a new queries list 
	 *
	 * @param   queries  The list of queries to federate.
	 * @return  The federated list of queries
	 */
	public abstract Map<MetricQuery, List<MetricQuery>> federateQueries(List<MetricQuery> queries);

	/**
	 *  Merge metrics from smaller sub queries 
	 *
	 * @param   mapQuerySubQueries  Mapping from original query to list of sub queries
	 * @param   subQueryMetricsMap  Result map of sub queries to metric
	 * @return  Map from original query to merged metrics
	 */
	public Map<MetricQuery, List<Metric>> join(Map<MetricQuery, List<MetricQuery>> mapQuerySubQueries, Map<MetricQuery, List<Metric>> subQueryMetricsMap) {
		Map<MetricQuery, List<Metric>> queryMetricsMap = new HashMap<>();
		Map<String, Metric> metricMergeMap = new HashMap<>();
		String metricIdentifier = null;
		for (Map.Entry<MetricQuery, List<MetricQuery>> entry : mapQuerySubQueries.entrySet()) {
			List<Metric> metrics = new ArrayList<>();
			MetricQuery query = entry.getKey();
			List<MetricQuery> subQueries = entry.getValue();
			for (MetricQuery subQuery : subQueries) {
				List<Metric> metricsFromSubQuery = subQueryMetricsMap.get(subQuery);
				if (metricsFromSubQuery != null) {
					for (Metric metric : metricsFromSubQuery) {
						if (metric != null) {
							metricIdentifier = metric.getIdentifier();
							Metric finalMetric = metricMergeMap.get(metricIdentifier);
							if (finalMetric == null) {
								metric.setQuery(query);
								metricMergeMap.put(metricIdentifier, metric);
							} else {
								finalMetric.addDatapoints(metric.getDatapoints());
							}
						}
					}
				}
			}
			metrics.addAll(metricMergeMap.values());
			queryMetricsMap.put(query, metrics);
		}
		return queryMetricsMap;
	}

}

package com.salesforce.dva.argus.service.tsdb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Federation across multiple endpoints
 *
 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
 */
public class EndPointQueryFederation extends QueryFederation{
	private final List<String> _readEndPoints;

	public EndPointQueryFederation(List<String> readEndPoints) {
		_readEndPoints = readEndPoints;
	}

	@Override
	public Map<MetricQuery, List<MetricQuery>> federateQueries(List<MetricQuery> queries) {

		Map<MetricQuery, List<MetricQuery>> mapQuerySubQueries = new HashMap<>();
		for (MetricQuery query : queries) {
			List<MetricQuery> subQueries = new ArrayList<>();
			for (String readEndPoint : _readEndPoints) {
				MetricQuery mq = new MetricQuery(query);
				MetricQuery.MetricQueryContext metricQueryContext = mq.new MetricQueryContext();
				metricQueryContext.setReadEndPoint(readEndPoint);
				mq.setMetricQueryContext(metricQueryContext);
				subQueries.add(mq);
			}
			mapQuerySubQueries.put(query, subQueries);
		}
		return mapQuerySubQueries;
	}
}
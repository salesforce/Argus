package com.salesforce.dva.argus.service.tsdb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Federation by forking large range queries into smaller range sub queries
 *
 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
 */
public class TimeQueryFederation extends QueryFederation{

	private static final long TIME_FEDERATE_LIMIT_MILLIS = 86400000L;

	@Override
	public Map<MetricQuery, List<MetricQuery>> federateQueries(List<MetricQuery> queries) {
		Map<MetricQuery, List<MetricQuery>> mapQuerySubQueries = new HashMap<>();
		for (MetricQuery query : queries) {

			List<MetricQuery> metricSubQueries = new ArrayList<>();
			if (query.getEndTimestamp() - query.getStartTimestamp() > TIME_FEDERATE_LIMIT_MILLIS) {
				for (long time = query.getStartTimestamp(); time <= query.getEndTimestamp(); time = time + TIME_FEDERATE_LIMIT_MILLIS) {
					MetricQuery mq = new MetricQuery(query);
					mq.setStartTimestamp(time);
					if (time + TIME_FEDERATE_LIMIT_MILLIS > query.getEndTimestamp()) {
						mq.setEndTimestamp(query.getEndTimestamp());
					} else {
						mq.setEndTimestamp(time + TIME_FEDERATE_LIMIT_MILLIS);
					}
					metricSubQueries.add(mq);
				}
				mapQuerySubQueries.put(query, metricSubQueries);
			} else {
				metricSubQueries.add(query);
				mapQuerySubQueries.put(query, metricSubQueries);
			}
		}
		return mapQuerySubQueries;
	}
}
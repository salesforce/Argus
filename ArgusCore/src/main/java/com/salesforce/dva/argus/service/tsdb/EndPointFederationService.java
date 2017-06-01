package com.salesforce.dva.argus.service.tsdb;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EndPointFederationService extends QueryFederation{
	private final List<String> _readEndPoints;

	EndPointFederationService(List<String> readEndPoints) {
		_readEndPoints = readEndPoints;
	}

	@Override
	public Map<MetricQuery, List<MetricQuery>> federateQueries(List<MetricQuery> queries) {

		Map<MetricQuery, List<MetricQuery>> mapQuerySubQueries = new HashMap<>();
		for (MetricQuery query : queries) {
			List<MetricQuery> subQueries = new ArrayList<>();
			for (String readEndPoint : _readEndPoints) {
				MetricQuery mq = new MetricQuery(query);
				mq.setReadEndPoint(readEndPoint);
				subQueries.add(mq);
			}
			mapQuerySubQueries.put(query, subQueries);
		}
		return mapQuerySubQueries;
	}
}
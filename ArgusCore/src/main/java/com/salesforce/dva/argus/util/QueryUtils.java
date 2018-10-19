package com.salesforce.dva.argus.util;

import java.util.LinkedList;
import java.util.Queue;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.metric.MetricReader;
import com.salesforce.dva.argus.service.metric.ParseException;


public class QueryUtils {

	private static MetricReader metricReader =  new MetricReader(null, null,null);

	public static Long[] getStartAndEndTimesWithMaxInterval(String expression, Long relativeTo) {
		return getStartAndEndTimesWithMaxInterval(getQueryContext(expression, relativeTo));
	}
	
	public static Long[] getStartAndEndTimesWithMaxInterval(QueryContext context) {
		Long[] queryStartAndEndTimes = new Long[2];
		Queue<QueryContext> bfsQueue = new LinkedList<QueryContext>();
		if(context!=null) {
			bfsQueue.add(context);
		}
		while(!bfsQueue.isEmpty()) {
			QueryContext currContext = bfsQueue.poll();
			if(currContext.getChildExpressions()!=null) {
				for(TSDBQueryExpression expression : currContext.getChildExpressions()) {
					if(queryStartAndEndTimes[0]==0L || queryStartAndEndTimes[0]>expression.getStartTimestamp()) {
						queryStartAndEndTimes[0] = expression.getStartTimestamp();
					}
					
					if(queryStartAndEndTimes[1]==0L || queryStartAndEndTimes[1]<expression.getEndTimestamp()) {
						queryStartAndEndTimes[1] = expression.getEndTimestamp();
					}
				}
				
				if(currContext.getChildContexts()!=null) {
			        bfsQueue.addAll(currContext.getChildContexts());
				}
			}
		}
		return queryStartAndEndTimes;
	}

	public static QueryContext getQueryContext(String expression, Long relativeTo) {
		try {
			QueryContextHolder contextHolder = new QueryContextHolder();
			metricReader.parse(expression, relativeTo, Metric.class, contextHolder, true);
			return contextHolder.getCurrentQueryContext();
		}catch(ParseException e) {
			throw new RuntimeException(e);
		}
	}
}

package com.salesforce.dva.argus.util;


import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.metric.MetricReader;
import com.salesforce.dva.argus.service.metric.ParseException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;


public class QueryUtils {

    private static MetricReader metricReader =  new MetricReader(null, null,null);

    public static Long[] getStartAndEndTimesWithMaxInterval(String expression, Long relativeTo) {
        return getStartAndEndTimesWithMaxInterval(getQueryContext(expression, relativeTo));
    }

    public static Long[] getStartAndEndTimesWithMaxInterval(QueryContext context) {
        Long[] queryStartAndEndTimes = new Long[]{0L, 0L};
        Queue<QueryContext> bfsQueue = new LinkedList<QueryContext>();
        if(context!=null) {
            bfsQueue.add(context);
        }
        while(!bfsQueue.isEmpty()) {
            QueryContext currContext = bfsQueue.poll();
            if(currContext.getExpression()!=null) {
                if(queryStartAndEndTimes[0]==0L || queryStartAndEndTimes[0]>currContext.getExpression().getStartTimestamp()) {
                    queryStartAndEndTimes[0] = currContext.getExpression().getStartTimestamp();
                }

                if(queryStartAndEndTimes[1]==0L || queryStartAndEndTimes[1]<currContext.getExpression().getEndTimestamp()) {
                    queryStartAndEndTimes[1] = currContext.getExpression().getEndTimestamp();
                }
            }

            if(currContext.getChildContexts()!=null) {
                bfsQueue.addAll(currContext.getChildContexts());
            }
        }
        // rounding up to nearest second
        queryStartAndEndTimes[0] = (queryStartAndEndTimes[0]/1000)*1000;
        queryStartAndEndTimes[1] = (queryStartAndEndTimes[1]/1000)*1000;
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

    public static List<String> getScopesFromExpression(String expression) {
        return getScopesFromExpression(expression, System.currentTimeMillis());
    }

    public static List<String> getScopesFromExpression(String expression, long relativeTo) {
        return getScopesFromExpression(getQueryContext(expression, relativeTo));
    }

    private static List<String> getScopesFromExpression(QueryContext queryContext) {
        Set<String> scopes = new HashSet<>();
        Queue<QueryContext> bfsQueue = new LinkedList<QueryContext>();
        if(queryContext != null) {
            bfsQueue.add(queryContext);
        }
        while(!bfsQueue.isEmpty()) {
            QueryContext currContext = bfsQueue.poll();
            if(currContext.getExpression()!=null) {
                TSDBQueryExpression expression = currContext.getExpression();
                String currentScope = expression.getScope();
                scopes.add(currentScope); //TODO: If the dc gets transferred to tags, we need to update this.
            }

            if(currContext.getChildContexts()!=null) {
                bfsQueue.addAll(currContext.getChildContexts());
            }
        }
        return new ArrayList<>(scopes);
    }
}
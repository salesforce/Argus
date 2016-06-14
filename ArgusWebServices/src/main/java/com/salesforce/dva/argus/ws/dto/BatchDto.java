package com.salesforce.dva.argus.ws.dto;

import com.salesforce.dva.argus.entity.AsyncBatchedMetricQuery;
import com.salesforce.dva.argus.entity.BatchMetricQuery;
import com.salesforce.dva.argus.entity.Metric;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by cguan on 6/7/16.
 */
public class BatchDto {
    private String status;
    private String priority;
    private long ttl;
    private String ownerName;
    private List<QueryDto> queries;

    public static BatchDto transformToDto(BatchMetricQuery batch) {
        BatchDto result = new BatchDto();
        result.status = batch.getStatus().toString();
        result.priority = batch.getPriority().toString();
        result.ttl = batch.getTtl();
        result.ownerName = batch.getOwnerName();

        List<AsyncBatchedMetricQuery> batchQueries = batch.getQueries();
        result.queries = new ArrayList<>(batchQueries.size());
        for (AsyncBatchedMetricQuery query: batchQueries) {
            result.queries.add(new QueryDto(query.getExpression(), query.getResult()));
        }
        return result;
    }

    private static class QueryDto {
        String expression;
        Metric result;

        QueryDto(String expression, Metric result) {
            this.expression = expression;
            this.result = result;
        }

        public String getExpression() {
            return expression;
        }

        public void setExpression(String expression) {
            this.expression = expression;
        }

        public Metric getResult() {
            return result;
        }

        public void setResult(Metric result) {
            this.result = result;
        }
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public long getTtl() {
        return ttl;
    }

    public void setTtl(long ttl) {
        this.ttl = ttl;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName;
    }

    public List<QueryDto> getQueries() {
        return queries;
    }

    public void setQueries(List<QueryDto> queries) {
        this.queries = queries;
    }
}

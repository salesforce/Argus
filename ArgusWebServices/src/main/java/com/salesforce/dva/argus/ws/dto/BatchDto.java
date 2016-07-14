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

    //~ Instance fields ******************************************************************************************************************************

    private String status;
    private int ttl;
    private long createdDate;
    private String ownerName;
    private List<QueryDto> queries;

    //~ Methods **************************************************************************************************************************************

    public static BatchDto transformToDto(BatchMetricQuery batch) {
        BatchDto result = new BatchDto();
        result.status = batch.getStatus().toString();
        result.ttl = batch.getTtl();
        result.createdDate = batch.getCreatedDate();
        result.ownerName = batch.getOwnerName();

        List<AsyncBatchedMetricQuery> batchQueries = batch.getQueries();
        result.queries = new ArrayList<>(batchQueries.size());
        for (AsyncBatchedMetricQuery query: batchQueries) {
            result.queries.add(new QueryDto(query.getExpression(), query.getResult(), query.getMessage()));
        }
        return result;
    }

    private static class QueryDto {
        String expression;
        Metric result;
        String message;

        QueryDto(String expression, Metric result, String message) {
            this.expression = expression;
            this.result = result;
            this.message = message;
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

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }

    public String getStatus() {
        return status;
    }

    public int getTtl() {
        return ttl;
    }

    public long getCreatedDate() {
        return createdDate;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public List<QueryDto> getQueries() {
        return queries;
    }

}

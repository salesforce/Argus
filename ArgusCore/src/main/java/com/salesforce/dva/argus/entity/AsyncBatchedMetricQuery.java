package com.salesforce.dva.argus.entity;

import java.io.Serializable;
import java.util.UUID;

import static com.salesforce.dva.argus.entity.BatchMetricQuery.Status;

/**
 * Created by cguan on 6/1/16.
 */
public class AsyncBatchedMetricQuery implements Serializable {

    //~ Instance fields ******************************************************************************************************************************

    private String _expression;
    private long _offset;
    private String _batchId;
    private String _queryId;
    private BatchMetricQuery.Status _status;
    private Metric _result;

    //~ Constructors *********************************************************************************************************************************

    public AsyncBatchedMetricQuery(String expression, long offset, String batchId) {
        _expression = expression;
        _offset = offset;
        _batchId = batchId;
        _queryId = UUID.randomUUID().toString();
        _status = Status.QUEUED;
    }

    public AsyncBatchedMetricQuery(String expression, long offset, String batchId, String queryId,
                                   Status status, Metric result) {
        _expression = expression;
        _offset = offset;
        _batchId = batchId;
        _queryId = queryId;
        _status = status;
        _result = result;
    }

    public AsyncBatchedMetricQuery() {
    }

    //~ Methods **************************************************************************************************************************************


    public String getExpression() {
        return _expression;
    }

    public void setExpression(String expression) {
        _expression = expression;
    }

    public long getOffset() {
        return _offset;
    }

    public void setOffset(long offset) {
        _offset = offset;
    }

    public String getBatchId() {
        return _batchId;
    }

    public void setBatchId(String batchId) {
        _batchId = batchId;
    }

    public String getQueryId() {
        return _queryId;
    }

    public void setQueryId(String queryId) {
        _queryId = queryId;
    }

    public Status getStatus() {
        return _status;
    }

    public void setStatus(Status status) {
        _status = status;
    }

    public Metric getResult() {
        return _result;
    }

    public void setResult(Metric result) {
        _result = result;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

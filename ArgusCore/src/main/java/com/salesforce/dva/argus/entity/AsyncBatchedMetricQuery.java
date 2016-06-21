package com.salesforce.dva.argus.entity;

/**
 * Created by cguan on 6/1/16.
 */
public class AsyncBatchedMetricQuery {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final String ROOT = "async/";

    //~ Instance fields ******************************************************************************************************************************

    String _expression;
    long _offset;
    long _queueId;
    String _batchId;
    BatchMetricQuery.Status _status;
    BatchMetricQuery.Priority _priority;
    Metric _result;

    //~ Constructors *********************************************************************************************************************************

    public AsyncBatchedMetricQuery(String expression, long offset, String batchId, BatchMetricQuery.Priority priority) {
        _expression = expression;
        _offset = offset;
        _batchId = batchId;
        _priority = priority;
        _status = BatchMetricQuery.Status.QUEUED;
    }

    public AsyncBatchedMetricQuery(String expression, long offset, long queueId, String batchId,
                                   BatchMetricQuery.Status status, BatchMetricQuery.Priority priority, Metric result) {
        _expression = expression;
        _offset = offset;
        _queueId = queueId;
        _batchId = batchId;
        _status = status;
        _priority = priority;
        _result = result;
    }

    //~ Methods **************************************************************************************************************************************

    public String getExpression() {
        return _expression;
    }

    public long getOffset() {
        return _offset;
    }

    public void setQueueId(long queueId) {
        _queueId = queueId;
    }

    public long getQueueId() {
        return _queueId;
    }

    public String getBatchId() {
        return _batchId;
    }

    public BatchMetricQuery.Status getStatus() {
        return _status;
    }

    public void setStatus(BatchMetricQuery.Status status) {
        _status = status;
    }

    public BatchMetricQuery.Priority getPriority() {
        return _priority;
    }

    public Metric getResult() {
        return _result;
    }

    public void setResult(Metric result) {
        _result = result;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

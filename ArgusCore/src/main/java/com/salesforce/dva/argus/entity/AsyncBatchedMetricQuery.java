package com.salesforce.dva.argus.entity;

import java.io.Serializable;

import static com.salesforce.dva.argus.entity.BatchMetricQuery.Status;

/**
 * Created by cguan on 6/1/16.
 */
public class AsyncBatchedMetricQuery implements Serializable {

    //~ Instance fields ******************************************************************************************************************************

    private String _expression;
    private long _offset;
    private String _batchId;
    private int _index;
    private Status _status;
    private Metric _result;
    private String _message;

    //~ Constructors *********************************************************************************************************************************

    public AsyncBatchedMetricQuery(String expression, long offset, String batchId, int index) {
        _expression = expression;
        _offset = offset;
        _batchId = batchId;
        _index = index;
        _status = Status.QUEUED;
    }

    public AsyncBatchedMetricQuery(String expression, long offset, String batchId, int index,
                                   Status status, Metric result, String message) {
        _expression = expression;
        _offset = offset;
        _batchId = batchId;
        _index = index;
        _status = status;
        _result = result;
        _message = message;
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

    public int getIndex() {
        return _index;
    }

    public void setIndex(int index) {
        _index = index;
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

    public String getMessage() {
        return _message;
    }

    public void setMessage(String message) {
        _message = message;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

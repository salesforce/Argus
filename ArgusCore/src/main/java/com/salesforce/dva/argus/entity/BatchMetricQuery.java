package com.salesforce.dva.argus.entity;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Default implementation of the batch service.
 *
 * @author Colby Guan (cguan@salesforce.com)
 */
public class BatchMetricQuery implements Serializable {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final String ROOT = "batch/";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    //~ Instance fields ******************************************************************************************************************************

    private Status _status;
    private int _ttl;
    private long _createdDate;
    private String _batchId;
    private String _ownerName;
    private List<AsyncBatchedMetricQuery> _queries;

    //~ Constructors *********************************************************************************************************************************

    public BatchMetricQuery(List<String> expressions, long offset, int ttl, String ownerName) {
        _status = Status.QUEUED;
        _ttl = ttl;
        _createdDate = System.currentTimeMillis();
        _batchId = UUID.randomUUID().toString();
        _ownerName = ownerName;
        _queries = new ArrayList<>(expressions.size());
        for (String expression: expressions) {
            _queries.add(new AsyncBatchedMetricQuery(expression, offset, _batchId));
        }
    }

    public BatchMetricQuery(Status status, int ttl, long createdDate, String batchId, String ownerName,
                            List<AsyncBatchedMetricQuery> queries) {
        _status = status;
        _ttl = ttl;
        _createdDate = createdDate;
        _batchId = batchId;
        _ownerName = ownerName;
        _queries = queries;
    }

    //~ Methods **************************************************************************************************************************************

    public void updateStatus() {
        boolean allDone = true;
        if (_status == Status.DONE) {
            return;
        }
        for (AsyncBatchedMetricQuery query: _queries) {
            Status status = query.getStatus();
            allDone &= (status == Status.DONE);
            if (status == Status.PROCESSING) {
                _status = Status.PROCESSING;
                break;
            }
        }
        if (allDone) {
            _status = Status.DONE;
        }
    }

    public Status getStatus() {
        return _status;
    }

    public int getTtl() {
        return _ttl;
    }

    public long getCreatedDate() {
        return _createdDate;
    }

    public String getBatchId()
    {
        return _batchId;
    }

    public String getOwnerName() {
        return _ownerName;
    }

    public List<AsyncBatchedMetricQuery> getQueries() {
        return _queries;
    }

    //~ Enums ****************************************************************************************************************************************

    public enum Status {
        QUEUED("queued"),
        PROCESSING("processing"),
        DONE("done");

        private final String _key;

        Status(String key) {
            _key = key;
        }

        public static Status fromInt(int status) {
            if (status >= 2) {
                return DONE;
            } else if (status == 1) {
                return PROCESSING;
            } else {
                return QUEUED;
            }
        }
        
        public int toInt() {
        	if (this == DONE) {
        		return 2;
        	} else if (this == PROCESSING) {
        		return 1;
        	} else {
        		return 0;
        	}
        }

        @Override
        public String toString() {
            return _key;
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

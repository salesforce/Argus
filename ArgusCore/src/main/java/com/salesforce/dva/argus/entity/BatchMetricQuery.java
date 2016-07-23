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
        for (int i = 0; i < expressions.size(); i++) {
            _queries.add(new AsyncBatchedMetricQuery(expressions.get(i), offset, _batchId, i));
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
        _updateStatus();
    }

    //~ Methods **************************************************************************************************************************************

    private void _updateStatus() {
        boolean doneWithoutErrors = true;
        boolean doneWithErrors = true;
        boolean finishedOne = false;
        if (_status == Status.DONE || _status == Status.ERROR) {
            return;
        }
        for (AsyncBatchedMetricQuery query: _queries) {
            Status status = query.getStatus();
            if (status == Status.PROCESSING) {
                _status = Status.PROCESSING;
                return;
            }
            boolean queryFinished = (status == Status.DONE || status == Status.ERROR);
            if (queryFinished) {
                finishedOne = true;
            }
            doneWithErrors &= queryFinished;
            doneWithoutErrors &= (status == Status.DONE);
        }
        if (!doneWithoutErrors && doneWithErrors) {
            _status = Status.ERROR;
        } else if (doneWithoutErrors) {
            _status = Status.DONE;
        } else if (finishedOne) {
            _status = Status.PROCESSING;
        } else {
            _status = Status.QUEUED;
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
        /** Status if the batch has no queries being processed */
        QUEUED("queued"),
        /** Status if the batch has at least one query being processed */
        PROCESSING("processing"),
        /** Status if all the queries in the batch have been evaluated */
        DONE("done"),
        /** Status if all queries have been evaluated , but at least one of the queries resulted in an error */
        ERROR("error");

        private final String _key;

        Status(String key) {
            _key = key;
        }

        public static Status fromInt(int status) {
            if (status == 2) {
                return DONE;
            } else if (status == 1) {
                return PROCESSING;
            } else if (status == 0) {
                return QUEUED;
            } else {
                return ERROR;
            }
        }
        
        public int toInt() {
        	if (this == DONE) {
        		return 2;
        	} else if (this == PROCESSING) {
        		return 1;
        	} else if (this == QUEUED) {
        		return 0;
        	} else {
                return 3;
            }
        }

        @Override
        public String toString() {
            return _key;
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

/*
 * Copyright (c) 2016, Salesforce.com, Inc.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 * this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 *
 * 3. Neither the name of Salesforce.com nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
	 
package com.salesforce.dva.argus.service;

import com.salesforce.dva.argus.entity.MetricSchemaRecord;
import com.salesforce.dva.argus.service.SchemaService.RecordType;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import java.util.List;

/**
 * Provides a means to query metric schema meta data to determine the existence of metrics.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public interface DiscoveryService extends Service {

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns a list of metric schema records which match the filtering criteria.  At least one field must be filtered.
     *
     * @param   namespaceRegex  A regular expression to match against the namespace field. Can be null.
     * @param   scopeRegex      A regular expression to match against the scope field.  Can be null.
     * @param   metricRegex     A regular expression to match against the metric name field.  Can be null.
     * @param   tagkRegex       A regular expression to match against the tag key field.  Can be null.
     * @param   tagvRegex       A regular expression to match against the tag value field.  Can be null.
     * @param   limit           The maximum set of results to return.  Must be a positive integer.
     * @param   page            The page of results to return.
     *
     * @return  A list of metric schema records matching the filtering criteria.  Will never return null, but may be empty.
     */
    List<MetricSchemaRecord> filterRecords(String namespaceRegex, String scopeRegex, String metricRegex, String tagkRegex, String tagvRegex,
        int limit, int page);

    /**
     * @param   namespaceRegex  A regular expression to match against the namespace field. Can be null.
     * @param   scopeRegex      A regular expression to match against the scope field.  Can be null.
     * @param   metricRegex     A regular expression to match against the metric name field.  Can be null.
     * @param   tagkRegex       A regular expression to match against the tag key field.  Can be null.
     * @param   tagvRegex       A regular expression to match against the tag value field.  Can be null.
     * @param   type            The field to return.  Cannot be null.
     * @param   limit           The maximum set of results to return.  Must be a positive integer.
     * @param   page            The page of results to return.
     *
     * @return  A unique list of values for the specified field.  Will never return null, but may be empty.
     */
    List<String> getUniqueRecords(String namespaceRegex, String scopeRegex, String metricRegex, String tagkRegex, String tagvRegex, RecordType type,
        int limit, int page);

    /**
     * Expands a given wildcard query into a list of distinct queries.
     *
     * @param   query  The wildcard query to expand.  Cannot be null.
     *
     * @return  The list of distinct queries representing the wildcard expansion.  Will never return null, but may be empty.
     */
    List<MetricQuery> getMatchingQueries(MetricQuery query);

    /**
     * Indicates whether a query is a wildcard query.
     *
     * @param   query  The query to evaluate.  Cannot be null.
     *
     * @return  True if the query is a wildcard query.
     */
    boolean isWildcardQuery(MetricQuery query);
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

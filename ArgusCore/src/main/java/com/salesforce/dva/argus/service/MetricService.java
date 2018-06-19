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

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;

import java.util.List;

/**
 * Provides methods for reading/transforming time series metrics.
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 * @see     <a href="http://google.com">Grammar for this Service</a>
 */
public interface MetricService extends Service {

    //~ Methods **************************************************************************************************************************************

    /**
     * Evaluates the given expressions and returns a list of time series Metrics.
     *
     * @param   expressions  	A list of query expressions
     *
     * @return  A list of time series Metrics for the given expressions. A transformed Metric if specified so by the expression. The method will never
     *          return null. Returns an empty list if all expressions return nothing.
     */
    List<Metric> getMetrics(List<String> expressions);

    /**
     * Evaluates the given expressions and returns a list of time series Metrics.
     *
     * @param   expressions  	A list of query expressions
     * @param   relativeTo      The timestamp from which the start and end times should be relative to. Only applied when using 
     * 							relative timestamps in expressions. 
     * 							For e.g. If the expression is -1h:argus.jvm:mem.heap.used:avg, 1 hour should be subtracted from
     * 							relativeTo
     *
     * @return  A list of time series Metrics for the given expressions. A transformed Metric if specified so by the expression. The method will never
     *          return null. Returns an empty list if all expressions return nothing.
     */
    List<Metric> getMetrics(List<String> expressions, long relativeTo);

    /**
     * Evaluates the given expression to construct MetricQuery object and returns the time series for the corresponding expression. The query
     * expression might also include a transformation function in which case the transformed series must be returned. Errors in query syntax will
     * result in runtime exceptions. Errors in transformation will result in Unsupported Operation Exception.
     *
     * @param   expression  	A query expression of the form function(startTs:endTs:scope:metric{tagk1=tagv1...}:aggregator:downsampler) startTs, scope,
     *                      	metric and aggregator are the required fields for an expression. E.g.
     *                      	sum(1414799283000:1414799294000:00D300000062.na1:app_record.count{recordType=A, source=splunk}:avg:15m-avg) E.g.
     *                      	identity(1414799283000:00D300000062.na1:app_record.count{recordType=A, source=splunk}:avg:15m-avg) E.g.
     *                      	identity(-15h:-10h:00D300000062.na1:app_record.count{recordType=A, source=splunk}:avg)
     *
     * @return  A time series for the given expression. A transformed time series if specified so by the expression. Null if no such time series
     *          found.
     */
    List<Metric> getMetrics(String expression);

    /**
     * Evaluates the given expression and returns a list of time series Metrics.
     *
     * @param   expression  	A query expression
     * @param   relativeTo      The timestamp from which the start and end times should be relative to. Only applied when using 
     * 							relative timestamps in expressions. 
     * 							For e.g. If the expression is -1h:argus.jvm:mem.heap.used:avg, 1 hour should be subtracted from
     * 							relativeTo
     *
     * @return  A list of time series Metrics for the given expressions. A transformed Metric if specified so by the expression. The method will never
     *          return null. Returns an empty list if all expressions return nothing.
     */
    List<Metric> getMetrics(String expression, long relativeTo);

    /**
     * Batch and enqueue the given expressions and return the batch ID.
     *
     * @param   expressions  	A list of query expressions
     * @param   relativeTo      The timestamp from which the start and end times should be relative to. Only applied when using 
     * 							relative timestamps in expressions. 
     * 							For e.g. If the expression is -1h:argus.jvm:mem.heap.used:avg, 1 hour should be subtracted from
     * 							relativeTo
     * @param   ttl          	Time to live for the results of the expressions after the full batch has been computed
     * @param   ownerName    	Username of the user making the request
     *
     * @return  The ID of the batch in canonical UUID form
     */
    String getAsyncMetrics(List<String> expressions, long relativeTo, int ttl, String ownerName);

    /**
     * Returns a list of <tt>MetricQuery</tt> objects corresponding to the given expression.
     *
     * @param   expression  	The metric expression to evaluate.  Cannot be null and must be a valid metric expression.
     *
     * @return  The corresponding list of metric query objects.  Will never return null.
     */
    List<MetricQuery> getQueries(String expression);

    /**
     * Returns a list of <tt>MetricQuery</tt> objects corresponding to the given expression where the query time range is relativeTo by the given value.
     *
     * @param   expression  	The metric expression to evaluate.  Cannot be null and must be a valid metric expression.
     * @param   relativeTo      The timestamp from which the start and end times should be relative to. Only applied when using 
     * 							relative timestamps in expressions. 
     * 							For e.g. If the expression is -1h:argus.jvm:mem.heap.used:avg, 1 hour should be subtracted from
     * 							relativeTo
     *
     * @return  The corresponding list of metric query objects.  Will never return null.
     */
    List<MetricQuery> getQueries(String expression, long relativeTo);

    /**
     * Returns a list of <tt>MetricQuery</tt> objects corresponding to the given expression.
     *
     * @param   expression  	The list of metric expressions to evaluate.  Cannot be null, but may be empty. All entries must be a valid metric expression.
     *
     * @return  The corresponding list of metric query objects.  Will never return null.
     */
    List<MetricQuery> getQueries(List<String> expression);

    /**
     * Returns a list of <tt>MetricQuery</tt> objects corresponding to the given expression where the query time range is relativeTo by the given value.
     *
     * @param   expression  	The list of metric expressions to evaluate.  Cannot be null, but may be empty.  All entries must be a valid metric expression.
     * @param   relativeTo      The timestamp from which the start and end times should be relative to. Only applied when using 
     * 							relative timestamps in expressions. 
     * 							For e.g. If the expression is -1h:argus.jvm:mem.heap.used:avg, 1 hour should be subtracted from
     * 							relativeTo
     *
     * @return  The corresponding list of metric query objects.  Will never return null.
     */
    List<MetricQuery> getQueries(List<String> expression, long relativeTo);
    
    /**
     * Returns the range of time series that current query has expanded to.
     *
     *
     * @return  The range of time series that current query has expanded to.
     */    
    String getExpandedTimeSeriesRange();
    
    /**
     * Returns the query time window of current query
     *
     *
     * @return  The query time window corresponding to current query.
     */    
    String getQueryTimeWindow();
    
    /**
     * Returns the number of discovery results that current query has expanded to.
     *
     *
     * @return  The number of discovery results that current query has expanded to.
     */    
    Integer getNumDiscoveryResults();
    
    /**
     * Returns the number of discovery  queries that has been made to the discovery service.
     *
     *
     * @return  The number of discovery  queries that has been made to the discovery service.
     */    
    Integer getNumDiscoveryQueries();
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

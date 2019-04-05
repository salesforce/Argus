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
import com.salesforce.dva.argus.entity.MetricSchemaRecordQuery;
import com.salesforce.dva.argus.entity.SchemaQuery;
import com.salesforce.dva.argus.entity.TSDBEntity;
import com.salesforce.dva.argus.service.MonitorService.Counter;
import com.salesforce.dva.argus.service.SchemaService.RecordType;
import com.salesforce.dva.argus.service.schema.WildcardExpansionLimitExceededException;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.util.RequestContextHolder;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;

/**
 * Provides a means to query metric schema meta data to determine the existence of metrics.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public interface DiscoveryService extends Service {

    /** We enforce a soft limit of 1 minute on the datapoint sampling frequency through WardenService and hence assume this 
     * to be the same. */
    static final long DATAPOINT_SAMPLING_FREQ_IN_MILLIS = 60 * 1000L;

    static final String EXCEPTION_MESSAGE = "Your query may return more than {0} datapoints in all. Please modify your query. "
            + "You may either reduce the time window or narrow your wildcard search or use downsampling.";

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns a list of metric schema records which match the filtering criteria.  At least one field must be filtered.
     *
     * @param query 	The query to filter by
     * @return A list of metric schema records matching the filtering criteria.  Will never return null, but may be empty.
     */
    List<MetricSchemaRecord> filterRecords(SchemaQuery query);

    /**
     * @param query             The query
     * @param type            The field to return.  Cannot be null.
     * @return A unique list of MetricSchemaRecords.  Will never return null, but may be empty.
     */
    List<MetricSchemaRecord> getUniqueRecords(MetricSchemaRecordQuery query, RecordType type);

    /**
     * @param query             The query
     * @param type            The field to return.  Cannot be null.
     * @param indexLevel      The index of the tokenized results to return.
     * @return A unique list of tokenized results.  Will never return null, but may be empty.
     */
    List<String> browseRecords(MetricSchemaRecordQuery query, RecordType type, int indexLevel);

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
    static boolean isWildcardQuery(MetricQuery query) {

        if (SchemaService.containsWildcard(query.getScope()) 
                || SchemaService.containsWildcard(query.getMetric()) 
                || SchemaService.containsWildcard(query.getNamespace())) {
            return true;
        }

        if (query.getTags() != null) {
            for (String tagKey : query.getTags().keySet()) {
                if (SchemaService.containsWildcard(tagKey) || 
                        (!"*".equals(query.getTag(tagKey)) && SchemaService.containsWildcard(query.getTag(tagKey)))) {
                    return true;
                }
            }
        }
        return false;
    }

    static int maxTimeseriesAllowed(MetricQuery query, long maxDataPointsPerResponse) {
        long timeWindowInMillis = query.getEndTimestamp() - query.getStartTimestamp();
        long downsamplingDivisor = (query.getDownsamplingPeriod() == null || query.getDownsamplingPeriod() <= 0) ? 60000l : query.getDownsamplingPeriod();
        downsamplingDivisor = (timeWindowInMillis > downsamplingDivisor) ? downsamplingDivisor : timeWindowInMillis;
        long samplingPeriod = (downsamplingDivisor>DATAPOINT_SAMPLING_FREQ_IN_MILLIS) ? DATAPOINT_SAMPLING_FREQ_IN_MILLIS : downsamplingDivisor;

        double numRawDPsPerSeries = (timeWindowInMillis*1.0)/samplingPeriod;
        double numDownsampledDPsPerSeries = (numRawDPsPerSeries) / (downsamplingDivisor/(samplingPeriod*1.0));

        numDownsampledDPsPerSeries = numDownsampledDPsPerSeries <= 0 ? 1 : numDownsampledDPsPerSeries; 

        return (int) (maxDataPointsPerResponse / numDownsampledDPsPerSeries);
    }

    static int numApproxTimeseriesForQuery(MetricQuery mq) {
        int count = 1;
        for(String tagValue : mq.getTags().values()) {
            String splits[] = tagValue.split("\\|");
            count *= splits.length;
        }

        return count;
    }

    static void throwMaximumDatapointsExceededException(MetricQuery query, long maxDataPointsPerQuery, MonitorService monitorService, Logger logger) throws WildcardExpansionLimitExceededException{
        // We are throwing the exception only when the downsampler is absent, 
        // as we want to give users some time to adjust their queries which have downsampler in them

        if(query.getDownsamplingPeriod()==null || query.getDownsamplingPeriod()==0) {
            throw new WildcardExpansionLimitExceededException(MessageFormat.format(EXCEPTION_MESSAGE, maxDataPointsPerQuery)) ;
        }
        
        if(monitorService!=null) {
            Map<String, String> tags = new HashMap<>();
            tags.put("scope", TSDBEntity.replaceUnsupportedChars(query.getScope()));
            tags.put("metric", TSDBEntity.replaceUnsupportedChars(query.getMetric()));
            if(RequestContextHolder.getRequestContext()!=null) {
                tags.put("user", RequestContextHolder.getRequestContext().getUserName());
            }else {
                tags.put("user", "unknown");
            }
            monitorService.modifyCounter(Counter.QUERY_DATAPOINTS_LIMIT_EXCEEDED, 1, tags);
            logger.error("Maximum datapoints limit execeeded for query - " + query.toString() + ", user - "+tags.get("user"));
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

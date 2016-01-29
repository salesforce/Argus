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
import com.salesforce.dva.argus.entity.MetricSchemaRecord;
import com.salesforce.dva.argus.entity.MetricSchemaRecordQuery;
import java.util.List;

/**
 * Provides methods to update metric schema records for use in wildcard expansion and metric discovery.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public interface SchemaService extends Service {

    //~ Methods **************************************************************************************************************************************

    /**
     * Creates a metric schema record using the meta data of the provided metric.
     *
     * @param  metric  The metric for which to create a schema record for.  Cannot be null.
     */
    void put(Metric metric);

    /**
     * Creates metric schema records using the meta data of the provided metrics.
     *
     * @param  metrics  The metrics for which to create schema records for.  Cannot be null, but may be empty.
     */
    void put(List<Metric> metrics);

    /**
     * Returns a list of schema records matched by the given query.
     *
     * @param   query  The query to evaluate.  Cannot be null.
     * @param   limit  The maximum number of records to return.  Must be a positive integer.
     * @param   page   The page of records to return.  Must be a non-negative integer.
     *
     * @return  The list of matching schema records.
     */
    List<MetricSchemaRecord> get(MetricSchemaRecordQuery query, int limit, int page);

    /**
     * Returns a list of unique names for the given record type.
     *
     * @param   query  The query to evaluate.  Cannot be null.
     * @param   limit  The maximum number of records to return.  Must be a positive integer.
     * @param   page   The page of results to return.  Must be a non-negative integer.
     * @param   type   The record type for which to return unique names.
     *
     * @return  A list of unique names for the give record type.  Will never return null, but may be empty.
     */
    List<String> getUnique(MetricSchemaRecordQuery query, int limit, int page, RecordType type);

    //~ Enums ****************************************************************************************************************************************

    /**
     * Indicates the schema record field to be used for matching.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public static enum RecordType {

        /** Match against the namespace field. */
        NAMESPACE("namespace"),
        /** Match against the scope field. */
        SCOPE("scope"),
        /** Match against the metric field. */
        METRIC("metric"),
        /** Match against the tag key field. */
        TAGK("tagk"),
        /** Match against the tag value field. */
        TAGV("tagv");

        private String _name;

        private RecordType(String name) {
            _name = name;
        }

        /**
         * Returns a given record type corresponding to the given name.
         *
         * @param   name  The case sensitive name to match against.  Cannot be null.
         *
         * @return  The corresponding record type or null if no matching record type exists.
         */
        public static RecordType fromName(String name) {
            for (RecordType type : RecordType.values()) {
                if (type.getName().equals(name)) {
                    return type;
                }
            }
            return null;
        }

        /**
         * Returns the record type name.
         *
         * @return  The record type name.
         */
        public String getName() {
            return _name;
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.salesforce.dva.argus.entity.KeywordQuery;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.MetricSchemaRecord;
import com.salesforce.dva.argus.entity.MetricSchemaRecordQuery;
import com.salesforce.dva.argus.system.SystemAssert;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Provides methods to update metric schema records for use in wildcard expansion and metric discovery.
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 */
public interface SchemaService extends Service {

	static final char[] WILDCARD_CHARSET = new char[] { '*', '?', '[', ']', '|' };

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
     *
     * @return  The list of matching schema records.
     */
    List<MetricSchemaRecord> get(MetricSchemaRecordQuery query);

    /**
     * Returns a list of unique names for the given record type.
     *
     * @param   query  The query to evaluate.  Cannot be null.
     * @param   type   The record type for which to return unique names.
     * @return  A list of MetricSchemaRecords for the give record type.  Will never return null, but may be empty.
     */
    List<MetricSchemaRecord> getUnique(MetricSchemaRecordQuery query, RecordType type);

    List<String> browseUnique(MetricSchemaRecordQuery query, RecordType type, int indexLevel);

    List<MetricSchemaRecord> keywordSearch(KeywordQuery query);

    static boolean containsWildcard(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }

        char[] arr = str.toCharArray();
        for (char ch : arr) {
            if (isWildcardCharacter(ch)) {
                return true;
            }
        }
        return false;
    }

    static boolean isWildcardCharacter(char ch) {
        for (char c : WILDCARD_CHARSET) {
            if (c == ch) {
                return true;
            }
        }
        return false;
    }

    static boolean containsFilter(String str) {
    	if(str == null || str.isEmpty()) {
    		return false;
    	}

		Pattern pattern = Pattern.compile("\\**");
		Matcher matcher = pattern.matcher(str);

		return !matcher.matches();
	}

    static String convertToRegex(String wildcardStr) {
        if (wildcardStr == null || wildcardStr.isEmpty()) {
            return wildcardStr;
        }

        char[] arr = wildcardStr.toCharArray();
        char[] result = new char[arr.length * 3];
        boolean flag = false;
        int j = -1, k = 0;

        for (int i = 0; i < arr.length; i++, k++) {
            k = replace(result, arr, k, i);
            if (arr[i] == '[') {
                j = k;
            }
            if (arr[i] == '|') {
                if (j != -1) {
                    result[j] = '(';
                    while (i < arr.length && arr[i] != ']') {
                        k = replace(result, arr, k, i);
                        i++;
                        k++;
                    }
                    if (i < arr.length) {
                        result[k] = ')';
                        j = -1;
                    }
                } else {
                    flag = true;
                }
            }
        }
        if (flag) {
            return "(" + new String(result).trim() + ")";
        }
        return new String(result).trim();
    }

    static int replace(char[] dest, char[] orig, int destIndex, int origIndex) {
        if (orig[origIndex] == '?') {
            dest[destIndex] = '.';
            return destIndex;
        } else if (orig[origIndex] == '*') {
            dest[destIndex] = '.';
            dest[destIndex + 1] = '*';
            return destIndex + 1;
        } else if (orig[origIndex] == '.') {
            dest[destIndex] = '\\';
            dest[destIndex + 1] = '.';
            return destIndex + 1;
        }
        dest[destIndex] = orig[origIndex];
        return destIndex;
    }

    static MetricSchemaRecord constructMetricSchemaRecordForType(String str, RecordType type) {
    	SystemAssert.requireArgument(type != null, "type cannot be null.");

    	MetricSchemaRecord record = new MetricSchemaRecord();
    	switch(type) {
    		case SCOPE:
    			record.setScope(str);
    			break;
    		case METRIC:
    			record.setMetric(str);
    			break;
    		case TAGK:
    			record.setTagKey(str);
    			break;
    		case TAGV:
    			record.setTagValue(str);
    			break;
    		case NAMESPACE:
    			record.setNamespace(str);
    			break;
    		default:
    			throw new IllegalArgumentException("Invalid record type: " + type);
    	}

    	return record;
    }

    static List<MetricSchemaRecord> constructMetricSchemaRecordsForType(List<String> values, RecordType type) {

    	List<MetricSchemaRecord> records = new ArrayList<>(values.size());
		for(String value : values) {
			records.add(SchemaService.constructMetricSchemaRecordForType(value, type));
		}

		return records;
    }

    //~ Enums ****************************************************************************************************************************************

    /**
     * Indicates the schema record field to be used for matching.
     *
     * @author  Bhinav Sura (bhinav.sura@salesforce.com)
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
        TAGV("tagv"),
        /** Match against the metatags value field. */
        METATAGS("metatags"),
        /** Match against the retention discovery field */
        RETENTIONDISCOVERY(MetricSchemaRecord.RETENTION_DISCOVERY);

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
        @JsonCreator
        public static RecordType fromName(String name) {
            for (RecordType type : RecordType.values()) {
                if (type.getName().equalsIgnoreCase(name)) {
                    return type;
                }
            }

            throw new IllegalArgumentException("Illegal record type: " + name);
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

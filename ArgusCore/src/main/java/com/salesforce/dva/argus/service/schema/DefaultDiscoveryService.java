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
	 
package com.salesforce.dva.argus.service.schema;

import com.google.inject.Inject;
import com.salesforce.dva.argus.entity.MetricSchemaRecord;
import com.salesforce.dva.argus.entity.MetricSchemaRecordQuery;
import com.salesforce.dva.argus.inject.SLF4JTypeListener;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.DiscoveryService;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.service.SchemaService.RecordType;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemConfiguration;

import org.slf4j.Logger;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * The default implementation of the discover service. 
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class DefaultDiscoveryService extends DefaultService implements DiscoveryService {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final int HARD_LIMIT = 20;
    private static final char[] WILDCARD_CHARSET = new char[] { '*', '?', '[', ']', '|' };

    //~ Instance fields ******************************************************************************************************************************

    @SLF4JTypeListener.InjectLogger
    private Logger _logger;
    private final SchemaService _schemaService;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new DefaultDiscoveryService object.
     *
     * @param  schemaService  The schema service to use.
     * @param config Service properties
     */
    @Inject
    public DefaultDiscoveryService(SchemaService schemaService, SystemConfiguration config) {
    	super(config);
        this._schemaService = schemaService;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public List<MetricSchemaRecord> filterRecords(String namespaceRegex, String scopeRegex, String metricRegex, String tagkRegex, String tagvRegex,
        int limit, int page) {
        requireNotDisposed();
        SystemAssert.requireArgument(scopeRegex != null && !scopeRegex.isEmpty(), "Scope regex cannot be null or empty.");
        SystemAssert.requireArgument(metricRegex != null && !metricRegex.isEmpty(), "Metric regex cannot be null or empty.");
        SystemAssert.requireArgument(limit > 0, "Limit must be a positive integer");
        SystemAssert.requireArgument(page > 0, "Page must be a positive integer");

        MetricSchemaRecordQuery query = new MetricSchemaRecordQuery(namespaceRegex, scopeRegex, metricRegex, tagkRegex, tagvRegex);

        _logger.debug(query.toString());

        long start = System.nanoTime();
        List<MetricSchemaRecord> result = _schemaService.get(query, limit, page);

        _logger.debug("Time to filter records in ms: " + (System.nanoTime() - start) / 1000000);
        return result;
    }

    @Override
    public List<String> getUniqueRecords(String namespaceRegex, String scopeRegex, String metricRegex, String tagkRegex, String tagvRegex,
        RecordType type, int limit, int page) {
        requireNotDisposed();
        SystemAssert.requireArgument(scopeRegex != null && !scopeRegex.isEmpty(), "Scope regex cannot be null or empty.");
        SystemAssert.requireArgument(metricRegex != null && !metricRegex.isEmpty(), "Metric regex cannot be null or empty.");
        SystemAssert.requireArgument(limit > 0, "Limit must be a positive integer");
        SystemAssert.requireArgument(page > 0, "Page must be a positive integer");

        MetricSchemaRecordQuery query = new MetricSchemaRecordQuery(namespaceRegex, scopeRegex, metricRegex, tagkRegex, tagvRegex);

        _logger.debug(query.toString());

        long start = System.nanoTime();
        List<String> records = _schemaService.getUnique(query, limit, page, type);

        _logger.debug("Time to get Unique Records in ms: " + (System.nanoTime() - start) / 1000000);
        return records;
    }

    @Override
    public List<MetricQuery> getMatchingQueries(MetricQuery query) {
        requireNotDisposed();
        SystemAssert.requireArgument(query != null, "Metric query cannot be null.");

        Map<String, MetricQuery> queries = new HashMap<String, MetricQuery>(HARD_LIMIT);
        long start = System.nanoTime();

        if (isWildcardQuery(query)) {
            _logger.debug(MessageFormat.format("MetricQuery'{'{0}'}' contains wildcards. Will match against schema records.", query));
            if (query.getTags() == null || query.getTags().isEmpty()) {
                MetricSchemaRecordQuery schemaQuery = new MetricSchemaRecordQuery(query.getNamespace(), query.getScope(), query.getMetric(), "*",
                    "*");
                int page = 1;

                while (true) {
                    List<MetricSchemaRecord> records = _schemaService.get(schemaQuery, 200, page++);

                    if (records.isEmpty()) {
                        break;
                    }
                    for (MetricSchemaRecord record : records) {
                        String identifier = new StringBuilder(record.getScope()).append(record.getMetric()).append(record.getNamespace()).toString();

                        if (!queries.containsKey(identifier)) {
                            if (queries.size() == HARD_LIMIT) {
                                break;
                            }

                            MetricQuery mq = new MetricQuery(record.getScope(), record.getMetric(), null, 0L, 1L);

                            mq.setNamespace(record.getNamespace());
                            _copyRemainingProperties(mq, query);
                            queries.put(identifier, mq);
                        }
                    }
                    if (queries.size() == HARD_LIMIT) {
                        break;
                    }
                }
            } else {
                for (Entry<String, String> tag : query.getTags().entrySet()) {
                    MetricSchemaRecordQuery schemaQuery = new MetricSchemaRecordQuery(query.getNamespace(), query.getScope(), query.getMetric(),
                        tag.getKey(), tag.getValue());
                    int page = 1;

                    while (true) {
                        List<MetricSchemaRecord> records = _schemaService.get(schemaQuery, 200, page++);

                        if (records.isEmpty()) {
                            break;
                        }
                        for (MetricSchemaRecord record : records) {
                            String identifier = new StringBuilder(record.getScope()).append(record.getMetric()).append(record.getNamespace())
                                .toString();

                            if (queries.containsKey(identifier)) {
                                MetricQuery mq = queries.get(identifier);

                                if (mq.getTags().containsKey(record.getTagKey())) {
                                    String oldValue = mq.getTag(record.getTagKey());
                                    String newValue = oldValue + "|" + record.getTagValue();

                                    mq.setTag(record.getTagKey(), newValue);
                                } else {
                                    mq.setTag(record.getTagKey(), record.getTagValue());
                                }
                            } else {
                                if (queries.size() == HARD_LIMIT) {
                                    break;
                                }

                                Map<String, String> tags = new HashMap<String, String>();

                                tags.put(record.getTagKey(), record.getTagValue());

                                MetricQuery mq = new MetricQuery(record.getScope(), record.getMetric(), tags, 0L, 1L);

                                mq.setNamespace(record.getNamespace());
                                _copyRemainingProperties(mq, query);
                                queries.put(identifier, mq);
                            }
                        }
                        if (queries.size() == HARD_LIMIT) {
                            break;
                        }
                    }
                }
            } // end if-else
        } else {
            _logger.debug(MessageFormat.format("MetricQuery'{'{0}'}' does not have any wildcards", query));
            queries.put(null, query);
        } // end if-else
        _logger.debug("Time to get matching queries in ms: " + (System.nanoTime() - start) / 1000000);

        List<MetricQuery> queryList = new ArrayList<MetricQuery>(queries.values());

        _printMatchedQueries(queryList);
        return queryList;
    }

    @Override
    public boolean isWildcardQuery(MetricQuery query) {
        if (_containsWildcard(query.getScope()) || _containsWildcard(query.getMetric())) {
            return true;
        }
        if (_containsWildcard(query.getNamespace())) {
            return true;
        }
        if (query.getTags() != null) {
            for (String tagKey : query.getTags().keySet()) {
                if (_containsWildcard(tagKey) || (!"*".equals(query.getTag(tagKey)) && _containsWildcard(query.getTag(tagKey)))) {
                    return true;
                }
            }
        }
        return false;
    }

    private void _printMatchedQueries(List<MetricQuery> queryList) {
        _logger.debug("Matched Queries:");

        int i = 1;

        for (MetricQuery q : queryList) {
            _logger.debug(MessageFormat.format("MetricQuery{0} = {1}", i++, q));
        }
    }

    private boolean _containsWildcard(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }

        char[] arr = str.toCharArray();

        for (char ch : arr) {
            if (_isWildcard(ch)) {
                return true;
            }
        }
        return false;
    }

    private boolean _isWildcard(char ch) {
        for (char c : WILDCARD_CHARSET) {
            if (c == ch) {
                return true;
            }
        }
        return false;
    }

    private void _copyRemainingProperties(MetricQuery dest, MetricQuery orig) {
        dest.setStartTimestamp(orig.getStartTimestamp());
        dest.setEndTimestamp(orig.getEndTimestamp());
        dest.setAggregator(orig.getAggregator());
        dest.setDownsampler(orig.getDownsampler());
        dest.setDownsamplingPeriod(orig.getDownsamplingPeriod());
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

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
import com.salesforce.dva.argus.entity.KeywordQuery;
import com.salesforce.dva.argus.entity.MetricSchemaRecord;
import com.salesforce.dva.argus.entity.MetricSchemaRecordQuery;
import com.salesforce.dva.argus.entity.SchemaQuery;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.DiscoveryService;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.service.SchemaService.RecordType;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemConfiguration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * The default implementation of the discover service. 
 *
 * @author  Bhinav Sura (bsura@salesforce.com)
 */
public class DefaultDiscoveryService extends DefaultService implements DiscoveryService {

    //~ Instance fields ******************************************************************************************************************************
    
    private final Logger _logger = LoggerFactory.getLogger(DefaultDiscoveryService.class);
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
    public List<MetricSchemaRecord> filterRecords(SchemaQuery query) {
        requireNotDisposed();
        
        _logger.debug(query.toString());
        
        if(query instanceof MetricSchemaRecordQuery) {
        	long start = System.nanoTime();
        	List<MetricSchemaRecord> result = _schemaService.get(MetricSchemaRecordQuery.class.cast(query));
            _logger.debug("Time to filter records in ms: " + (System.nanoTime() - start) / 1000000);
            return result;
        } else {
        	long start = System.nanoTime();
        	List<MetricSchemaRecord> result = _schemaService.keywordSearch(KeywordQuery.class.cast(query));
            _logger.debug("Time to filter records in ms: " + (System.nanoTime() - start) / 1000000);
            return result;
        }
        
    }

    @Override
    public List<MetricSchemaRecord> getUniqueRecords(MetricSchemaRecordQuery query, RecordType type) {
        requireNotDisposed();

        _logger.debug(query.toString());

        long start = System.nanoTime();
        List<MetricSchemaRecord> records = _schemaService.getUnique(query, type);

        _logger.debug("Time to get Unique Records in ms: " + (System.nanoTime() - start) / 1000000);
        return records;
    }

    @Override
    public List<MetricQuery> getMatchingQueries(MetricQuery query) {
        requireNotDisposed();
        SystemAssert.requireArgument(query != null, "Metric query cannot be null.");

        int limit = 10000;
        List<MetricQuery> expandedQueryList = null;
        
        long start = System.nanoTime();

        if (DiscoveryService.isWildcardQuery(query)) {
            _logger.info(MessageFormat.format("MetricQuery'{'{0}'}' contains wildcards. Will match against schema records.", query));
            
            int noOfTimeseriesAllowed = DiscoveryService.maxTimeseriesAllowed(query);
            if(noOfTimeseriesAllowed == 0) {
            	throw new WildcardExpansionLimitExceededException(EXCEPTION_MESSAGE);
            }
            
            Map<String, MetricQuery> queries = new HashMap<>();
            if (query.getTags() == null || query.getTags().isEmpty()) {
                
            	MetricSchemaRecordQuery schemaQuery = new MetricSchemaRecordQuery.MetricSchemaRecordQueryBuilder().namespace(query.getNamespace())
																						            			  .scope(query.getScope())
																						            			  .metric(query.getMetric())
																						            			  .tagKey("*")
																						            			  .tagValue("*")
																						            			  .limit(limit)
																						            			  .page(1)
																						            			  .build();

                while (true) {
                	List<MetricSchemaRecord> records = _schemaService.get(schemaQuery);
                    for (MetricSchemaRecord record : records) {
                        String identifier = _getIdentifier(record);

                        if (!queries.containsKey(identifier)) {
                            if (queries.size() == noOfTimeseriesAllowed) {
                                throw new WildcardExpansionLimitExceededException(EXCEPTION_MESSAGE);
                            }

                            MetricQuery mq = new MetricQuery(record.getScope(), record.getMetric(), null, 0L, 1L);

                            mq.setNamespace(record.getNamespace());
                            _copyRemainingProperties(mq, query);
                            queries.put(identifier, mq);
                        }
                    }
                    
                    if (records.size() < limit) {
                        break;
                    }
                    
                    //scanStartRow = records.get(records.size() - 1);
                    schemaQuery.setScanFrom(records.get(records.size() - 1));
                    schemaQuery.setPage(schemaQuery.getPage()+1);
                }
                
                expandedQueryList = new ArrayList<>(queries.values());
            } else {
            	Map<String, Integer> timeseriesCount = new HashMap<>();
                for (Entry<String, String> tag : query.getTags().entrySet()) {
                	
                    MetricSchemaRecordQuery schemaQuery = new MetricSchemaRecordQuery.MetricSchemaRecordQueryBuilder().namespace(query.getNamespace())
																							            			  .scope(query.getScope())
																							            			  .metric(query.getMetric())
																							            			  .tagKey(tag.getKey())
																							            			  .tagValue(tag.getValue())
																							            			  .limit(limit)
																							            			  .page(1)
																							            			  .build();
                    
                    boolean containsWildcard = SchemaService.containsWildcard(query.getScope())
                							|| SchemaService.containsWildcard(query.getMetric())
                							|| SchemaService.containsWildcard(query.getNamespace())
                							|| SchemaService.containsWildcard(tag.getKey())
                							|| SchemaService.containsWildcard(tag.getValue());

                    while (true) {
                        List<MetricSchemaRecord> records;
                        
                        if(!containsWildcard) {
                    		records = Arrays.asList(new MetricSchemaRecord(query.getNamespace(), query.getScope(), query.getMetric(), 
                    				tag.getKey(), tag.getValue()));
                    	} else {
                    		records = _schemaService.get(schemaQuery);
                    	}

                        for (MetricSchemaRecord record : records) {
                        	if (_getTotalTimeseriesCount(timeseriesCount) == noOfTimeseriesAllowed) {
                                throw new WildcardExpansionLimitExceededException(EXCEPTION_MESSAGE);
                            }
                        	
                            String identifier = _getIdentifier(record);

                            if (queries.containsKey(identifier)) {
                                MetricQuery mq = queries.get(identifier);

                                if (mq.getTags().containsKey(record.getTagKey())) {
                                    String oldValue = mq.getTag(record.getTagKey());
                                    String newValue = oldValue + "|" + record.getTagValue();

                                    mq.setTag(record.getTagKey(), newValue);
                                } else {
                                    mq.setTag(record.getTagKey(), record.getTagValue());
                                }
                                timeseriesCount.put(identifier, DiscoveryService.numApproxTimeseriesForQuery(mq));
                            } else {
                                Map<String, String> tags = new HashMap<String, String>();

                                tags.put(record.getTagKey(), record.getTagValue());

                                MetricQuery mq = new MetricQuery(record.getScope(), record.getMetric(), tags, 0L, 1L);

                                mq.setNamespace(record.getNamespace());
                                _copyRemainingProperties(mq, query);
                                queries.put(identifier, mq);
                                timeseriesCount.put(identifier, 1);
                            }
                        }
                        
                        if (records.size() < limit) {
                            break;
                        }
                        
                        //scanStartRow = records.get(records.size() - 1);
                        schemaQuery.setScanFrom(records.get(records.size() - 1));
                        schemaQuery.setPage(schemaQuery.getPage()+1);
                        
                    }
                }
                
                expandedQueryList = new ArrayList<>(queries.size());
                for(Map.Entry<String, MetricQuery> entry : queries.entrySet()) {
                	MetricQuery q = entry.getValue();
                	if(q.getTags().size() == query.getTags().size()) {
                		expandedQueryList.add(q);
                	}
                }
                
            } // end if-else
        } else {
            _logger.info(MessageFormat.format("MetricQuery'{'{0}'}' does not have any wildcards", query));
            expandedQueryList = Arrays.asList(query);
        } // end if-else
        _logger.debug("Time to get matching queries in ms: " + (System.nanoTime() - start) / 1000000);

        _logMatchedQueries(expandedQueryList);
        return expandedQueryList;
    }

	private int _getTotalTimeseriesCount(Map<String, Integer> timeseriesCountMap) {
    	int sum = 0;
    	for(Integer count : timeseriesCountMap.values()) {
    		sum += count;
    	}
    	
    	return sum;
    }

	private String _getIdentifier(MetricSchemaRecord record) {
		String identifier = new StringBuilder(record.getScope()).
													append(record.getMetric()).
													append(record.getNamespace()).
													toString();
		return identifier;
	}

    private void _logMatchedQueries(List<MetricQuery> queryList) {
        _logger.info("Matched Queries:");

        int i = 1;
        for (MetricQuery q : queryList) {
            _logger.info(MessageFormat.format("MetricQuery{0} = {1}", i++, q));
        }
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

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

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.MetricSchemaRecord;
import com.salesforce.dva.argus.entity.MetricSchemaRecordQuery;
import com.salesforce.dva.argus.entity.MetricSchemaRecordQuery.MetricSchemaRecordQueryBuilder;
import com.salesforce.dva.argus.service.SchemaService;
import com.salesforce.dva.argus.service.schema.DefaultDiscoveryService;
import com.salesforce.dva.argus.service.schema.WildcardExpansionLimitExceededException;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.service.tsdb.MetricQuery.Aggregator;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;


public class DefaultDiscoveryServiceTest extends AbstractTest {
	
    @Test
    public void testWildcardQueriesMatchWithinLimit() {
    	
    	SchemaService schemaServiceMock = mock(SchemaService.class);
        List<MetricSchemaRecord> records = new ArrayList<>();
        records.add(new MetricSchemaRecord(null, "scope0", "metric0", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope1", "metric1", "source", "unittest"));
        when(schemaServiceMock.get(any(MetricSchemaRecordQuery.class))).thenReturn(records);
        DefaultDiscoveryService discoveryService = new DefaultDiscoveryService(schemaServiceMock, system.getConfiguration());

        Map<String, String> tags = new HashMap<String, String>();
        tags.put("source", "unittest");

        MetricQuery query = new MetricQuery("scope[0|1]", "metric[0|1]", tags, 1L, 2L);
        List<MetricQuery> queries = discoveryService.getMatchingQueries(query);
        
        assertEquals(2, queries.size());

        assertEquals(new MetricQuery("scope0", "metric0", tags, 1L, 2L), queries.get(0));
        assertEquals(new MetricQuery("scope1", "metric1", tags, 1L, 2L), queries.get(1));
    }
    
    /**
	 * Assume that following schemarecords exist in the database:
	 * scope0,metric0,source,unittest0,null
	 * scope0,metric0,source,unittest1,null
	 * scope0,metric0,device,device0,null
	 */
    @Test
    public void testWildcardQueriesMatchMultipleTags() {
    	
    	SchemaService schemaServiceMock = mock(SchemaService.class);
        
        MetricSchemaRecordQuery queryForTag1 = new MetricSchemaRecordQueryBuilder().scope("scope0")
																        		   .metric("metric0")
																        		   .tagKey("source")
																        		   .tagValue("unittest0")
																        		   .limit(500)
																        		   .build();

        MetricSchemaRecordQuery queryForTag2 = new MetricSchemaRecordQueryBuilder().scope("scope0")
																	     		   .metric("metric0")
																	     		   .tagKey("device")
																	     		   .tagValue("device[1]")
																	     		   .limit(500)
																	     		   .build();
        
        when(schemaServiceMock.get(queryForTag1))
        				.thenReturn(Arrays.asList(new MetricSchemaRecord(null, "scope0", "metric0", "source", "unittest0")));
        when(schemaServiceMock.get(queryForTag2)).thenReturn(new ArrayList<>());
        
        DefaultDiscoveryService discoveryService = new DefaultDiscoveryService(schemaServiceMock, system.getConfiguration());

        Map<String, String> tags = new HashMap<String, String>();
        tags.put("source", "unittest0");
        tags.put("device", "device[1]");

        MetricQuery query = new MetricQuery("scope0", "metric0", tags, 1L, 2L);
        List<MetricQuery> matchedQueries = discoveryService.getMatchingQueries(query);
        
        assertTrue(matchedQueries.isEmpty());
    }
    
    /**
	 * Assume that following schemarecords exist in the database:
	 * scope0,metric0,source,unittest0,null
	 * scope0,metric0,source,unittest1,null
	 * scope0,metric0,device,device0,null
	 * scope1,metric0,source,unittest0,null
	 * scope1,metric0,source,unittest1,null
	 * scope1,metric0,device,device0,null
	 */
    @Test
    public void testWildcardQueriesMatchMultipleTags1() {
    	
    	SchemaService schemaServiceMock = mock(SchemaService.class);
        
        MetricSchemaRecordQuery queryForTag1 = new MetricSchemaRecordQueryBuilder().scope("scope0")
																	     		   .metric("metric0")
																	     		   .tagKey("source")
																	     		   .tagValue("unittest0")
																	     		   .limit(500)
																	     		   .build();

        MetricSchemaRecordQuery queryForTag2 = new MetricSchemaRecordQueryBuilder().scope("scope0")
																	     		   .metric("metric0")
																	     		   .tagKey("device")
																	     		   .tagValue("device[1]")
																	     		   .limit(500)
																	     		   .build();
    	
        when(schemaServiceMock.get(queryForTag1)).thenReturn(Arrays.asList(
												       new MetricSchemaRecord(null, "scope0", "metric0", "source", "unittest0"), 
												       new MetricSchemaRecord(null, "scope1", "metric0", "source", "unittest0")));
        when(schemaServiceMock.get(queryForTag2)).thenReturn(new ArrayList<>());
        
        DefaultDiscoveryService discoveryService = new DefaultDiscoveryService(schemaServiceMock, system.getConfiguration());

        Map<String, String> tags = new HashMap<String, String>();
        tags.put("source", "unittest0");
        tags.put("device", "device[1]");

        MetricQuery query = new MetricQuery("scope?", "metric0", tags, 1L, 2L);
        List<MetricQuery> matchedQueries = discoveryService.getMatchingQueries(query);
        
        assertTrue(matchedQueries.isEmpty());
    }
    
    @Test(expected = WildcardExpansionLimitExceededException.class)
    public void testWildcardQueriesMatchExceedingLimit() {
    	
    	SchemaService schemaServiceMock = mock(SchemaService.class);
        List<MetricSchemaRecord> records = new ArrayList<>();
        records.add(new MetricSchemaRecord(null, "scope", "metric0", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric1", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric2", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric3", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric4", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric5", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric6", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric7", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric8", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric9", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric10", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric11", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric12", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric13", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric14", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric15", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric16", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric17", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric18", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric19", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric20", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric21", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric22", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric23", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric24", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric25", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric26", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric27", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric28", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric29", "source", "unittest"));
        
        when(schemaServiceMock.get(any(MetricSchemaRecordQuery.class))).thenReturn(records);
        DefaultDiscoveryService discoveryService = new DefaultDiscoveryService(schemaServiceMock, system.getConfiguration());
        
        Map<String, String> tags = new HashMap<String, String>();
        tags.put("source", "unittest");

        MetricQuery query = new MetricQuery("scope", "metric*", null, System.currentTimeMillis() - (100 * 24 * 60 * 60 * 1000L), System.currentTimeMillis());
        List<MetricQuery> queries = discoveryService.getMatchingQueries(query);
        assertEquals(30, queries.size());
    }
    
    @Test
    public void testWildcardQueriesMatchWithDownsampling() {
    	
    	SchemaService schemaServiceMock = mock(SchemaService.class);
        List<MetricSchemaRecord> records = new ArrayList<>();
        records.add(new MetricSchemaRecord(null, "scope", "metric0", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric1", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric2", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric3", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric4", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric5", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric6", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric7", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric8", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric9", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric10", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric11", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric12", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric13", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric14", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric15", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric16", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric17", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric18", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric19", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric20", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric21", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric22", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric23", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric24", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric25", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric26", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric27", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric28", "source", "unittest"));
        records.add(new MetricSchemaRecord(null, "scope", "metric29", "source", "unittest"));
        
        when(schemaServiceMock.get(any(MetricSchemaRecordQuery.class))).thenReturn(records);
        DefaultDiscoveryService discoveryService = new DefaultDiscoveryService(schemaServiceMock, system.getConfiguration());
        
        Map<String, String> tags = new HashMap<String, String>();
        tags.put("source", "unittest");

        MetricQuery query = new MetricQuery("scope", "metric*", null, System.currentTimeMillis() - (100 * 24 * 60 * 60 * 1000L), System.currentTimeMillis());
        query.setDownsampler(Aggregator.AVG);
        query.setDownsamplingPeriod(5 * 60 * 1000L);
        List<MetricQuery> queries = discoveryService.getMatchingQueries(query);
        assertEquals(30, queries.size());
    }

    @Test
    public void testWildcardQueriesNoMatch() {
    	
    	SchemaService schemaServiceMock = mock(SchemaService.class);
        List<MetricSchemaRecord> records = new ArrayList<>();
        when(schemaServiceMock.get(any(MetricSchemaRecordQuery.class))).thenReturn(records);
        DefaultDiscoveryService discoveryService = new DefaultDiscoveryService(schemaServiceMock, system.getConfiguration());

        Map<String, String> tags = new HashMap<String, String>();
        tags.put("source", "unittest");

        MetricQuery query = new MetricQuery("sdfg*", "ymdasdf*", tags, 1L, 2L);
        List<MetricQuery> queries = discoveryService.getMatchingQueries(query);

        assertEquals(0, queries.size());
    }

    @Test
    public void testNonWildcardQuery() {
    	
    	SchemaService schemaServiceMock = mock(SchemaService.class);
        List<MetricSchemaRecord> records = new ArrayList<>();
        when(schemaServiceMock.get(any(MetricSchemaRecordQuery.class))).thenReturn(records);
        DefaultDiscoveryService discoveryService = new DefaultDiscoveryService(schemaServiceMock, system.getConfiguration());
    	
        Map<String, String> tags = new HashMap<String, String>();
        tags.put("recordType", "A");

        MetricQuery query = new MetricQuery("system", "runtime", null, 1L, 2L);
        List<MetricQuery> queries = discoveryService.getMatchingQueries(query);

        assertEquals(1, queries.size());
        assertEquals(query, queries.get(0));
    }
    
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
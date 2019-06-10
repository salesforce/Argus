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


package com.salesforce.dva.argus.service.annotation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Properties;

import org.junit.BeforeClass;
import org.junit.Test;

import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.schema.ElasticSearchUtils;
import com.salesforce.dva.argus.service.tsdb.AnnotationQuery;
import com.salesforce.dva.argus.service.tsdb.DefaultTSDBService;
import com.salesforce.dva.argus.system.SystemConfiguration;

public class DualAnnotationServiceTest {
    private static SystemConfiguration systemConfig;
    private static DualAnnotationService dualAnnotationService;
    
    @BeforeClass
    public static void setUpClass() {
        Properties config = new Properties();
        config.put("service.property.tsdb.connection.count", "2");
        config.put("service.property.tsdb.endpoint.read", "http://tsdbread.mycompany.com:4466");
        config.put("service.property.tsdb.endpoint.write", "http://tsdbwrite.mycompany.com:4477");
        systemConfig =new SystemConfiguration(config);
        MonitorService mockedMonitor = mock(MonitorService.class);
        ElasticSearchUtils mockedElasticSearchUtils = mock(ElasticSearchUtils.class);
        DefaultTSDBService tsdbService = new DefaultTSDBService(systemConfig, mockedMonitor);
        ElasticSearchAnnotationService esAnnotationService = new ElasticSearchAnnotationService(systemConfig, mockedMonitor, mockedElasticSearchUtils);
        dualAnnotationService = new DualAnnotationService(systemConfig, esAnnotationService, tsdbService);
    }
    
    @Test
    public void testConvertTimestampToMillis(){
        AnnotationQuery annotationQuery = new AnnotationQuery("scope1", "metric1", null, "unittest", 1557809359073L, 1557809599073L);
        dualAnnotationService.convertTimestampToMillis(annotationQuery);
        assertEquals(1557809359073L, annotationQuery.getStartTimestamp().longValue());
        assertEquals(1557809599073L, annotationQuery.getEndTimestamp().longValue());
        
        annotationQuery = new AnnotationQuery("scope1", "metric1", null, "unittest", 1557809359L, 1557809599L);
        dualAnnotationService.convertTimestampToMillis(annotationQuery);
        assertEquals(1557809359000L, annotationQuery.getStartTimestamp().longValue());
        assertEquals(1557809599000L, annotationQuery.getEndTimestamp().longValue());

        annotationQuery = new AnnotationQuery("scope1", "metric1", null, "unittest", 1557809359123L, 1557809599L);
        dualAnnotationService.convertTimestampToMillis(annotationQuery);
        assertEquals(1557809359123L, annotationQuery.getStartTimestamp().longValue());
        assertEquals(1557809599000L, annotationQuery.getEndTimestamp().longValue());

        annotationQuery = new AnnotationQuery("scope1", "metric1", null, "unittest", 1557809359L, 1557809599456L);
        dualAnnotationService.convertTimestampToMillis(annotationQuery);
        assertEquals(1557809359000L, annotationQuery.getStartTimestamp().longValue());
        assertEquals(1557809599456L, annotationQuery.getEndTimestamp().longValue());
    }
    
    @Test
    public void testSplitQuery(){
        AnnotationQuery annotationQuery = new AnnotationQuery("scope1", "metric1", null, "unittest", 1559153223000L, 1559153226000L);
        List<AnnotationQuery> queries = dualAnnotationService.splitQuery(annotationQuery);
        assertEquals(2, queries.size());
        AnnotationQuery tsdbQuery = queries.get(0);
        AnnotationQuery esQuery = queries.get(1);
        assertEquals("scope1", tsdbQuery.getScope());
        assertEquals("metric1", tsdbQuery.getMetric());
        assertEquals("unittest", tsdbQuery.getType());
        assertEquals(1559153223000L, tsdbQuery.getStartTimestamp().longValue());
        assertEquals(1559153225000L, tsdbQuery.getEndTimestamp().longValue());
        
        assertEquals("scope1", esQuery.getScope());
        assertEquals("metric1", esQuery.getMetric());
        assertEquals("unittest", esQuery.getType());
        assertEquals(1559153225000L, esQuery.getStartTimestamp().longValue());
        assertEquals(1559153226000L, esQuery.getEndTimestamp().longValue());
    }
    
    @Test
    public void testQueryBeforeEpochCutOffTimestamp(){
        AnnotationQuery annotationQuery = new AnnotationQuery("scope1", "metric1", null, "unittest", 1559596094000L, 1559596095000L);
        assertFalse(dualAnnotationService.isQueryHavingEpochCutOff(annotationQuery));
    }
    
    @Test
    public void testQueryAfterEpochCutOffTimestamp(){
        AnnotationQuery annotationQuery = new AnnotationQuery("scope1", "metric1", null, "unittest", 1559594094000L, 1559594095000L);
        assertFalse(dualAnnotationService.isQueryHavingEpochCutOff(annotationQuery));
    }
    
    @Test
    public void testQueryAcrossEpochCutOffTimestamp(){
        AnnotationQuery annotationQuery = new AnnotationQuery("scope1", "metric1", null, "unittest", 1559153223000L, 1559153226000L);
        assertTrue(dualAnnotationService.isQueryHavingEpochCutOff(annotationQuery));
    }
}
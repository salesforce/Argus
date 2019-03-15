package com.salesforce.dva.argus.util;

import org.junit.Test;
import static org.junit.Assert.*;
import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.service.DiscoveryService;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;

public class DiscoveryUtilsTest extends AbstractTest {
    
    private static final long MAX_DATAPOINTS_PER_RESPONSE = 5000000;
    
    @Test
    public void testGetMaxTimeSeriesAllowed() {
        MetricQuery query = new MetricQuery("test","test",null,0L,0L);
        query.setStartTimestamp(0L);
        query.setEndTimestamp(60000l);
        query.setDownsamplingPeriod(60000l);
        assertEquals((long)DiscoveryService.maxTimeseriesAllowed(query, MAX_DATAPOINTS_PER_RESPONSE), MAX_DATAPOINTS_PER_RESPONSE);
        
        query.setDownsamplingPeriod(30000l);
        assertEquals((long)DiscoveryService.maxTimeseriesAllowed(query, MAX_DATAPOINTS_PER_RESPONSE), MAX_DATAPOINTS_PER_RESPONSE/2);
        
        query.setDownsamplingPeriod(0l);
        assertEquals((long)DiscoveryService.maxTimeseriesAllowed(query, MAX_DATAPOINTS_PER_RESPONSE), MAX_DATAPOINTS_PER_RESPONSE);
        
        query.setDownsamplingPeriod(120000l);
        assertEquals((long)DiscoveryService.maxTimeseriesAllowed(query, MAX_DATAPOINTS_PER_RESPONSE), MAX_DATAPOINTS_PER_RESPONSE);
        
        query.setStartTimestamp(0L);
        query.setEndTimestamp(480000l);
        query.setDownsamplingPeriod(60000l);
        assertEquals((long)DiscoveryService.maxTimeseriesAllowed(query, MAX_DATAPOINTS_PER_RESPONSE), MAX_DATAPOINTS_PER_RESPONSE/8);
        
        query.setDownsamplingPeriod(2*60000l);
        assertEquals((long)DiscoveryService.maxTimeseriesAllowed(query, MAX_DATAPOINTS_PER_RESPONSE), MAX_DATAPOINTS_PER_RESPONSE/4);
    }
}

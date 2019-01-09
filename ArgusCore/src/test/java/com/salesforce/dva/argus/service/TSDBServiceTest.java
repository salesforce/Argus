package com.salesforce.dva.argus.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.salesforce.dva.argus.AbstractTest;

public class TSDBServiceTest extends AbstractTest {
    @Test
    public void isTSDBServiceSingleton() {
        TSDBService service1 = system.getServiceFactory().getTSDBService();
        TSDBService service2 = system.getServiceFactory().getTSDBService();
        assertTrue(service1 == service2);
    }

    @Test
    public void testQueryWindow() {
        long differenceInMillis = 80L;
        assertEquals(TSDBService.QueryTimeWindow.WITHIN_24_HRS.getName(), TSDBService.QueryTimeWindow.getWindow(differenceInMillis));
        differenceInMillis = 87000000L;
        assertEquals(TSDBService.QueryTimeWindow.WITHIN_24_HRS_AND_30_DAYS.getName(), TSDBService.QueryTimeWindow.getWindow(differenceInMillis));
        differenceInMillis = 26000000000L;
        assertEquals(TSDBService.QueryTimeWindow.GREATER_THAN_30_DAYS.getName(), TSDBService.QueryTimeWindow.getWindow(differenceInMillis));
    }

    @Test
    public void testQueryTimeSeriesExpanssion() {
        int numExpandedTimeSeries  = 0;
        assertEquals(TSDBService.QueryTimeSeriesExpansion.TS_0.getName(), TSDBService.QueryTimeSeriesExpansion.getExpandedTimeSeriesRange(numExpandedTimeSeries));
        numExpandedTimeSeries  = 1;
        assertEquals(TSDBService.QueryTimeSeriesExpansion.TS_1.getName(), TSDBService.QueryTimeSeriesExpansion.getExpandedTimeSeriesRange(numExpandedTimeSeries));
        numExpandedTimeSeries  = 2;
        assertEquals(TSDBService.QueryTimeSeriesExpansion.TS_2_10.getName(), TSDBService.QueryTimeSeriesExpansion.getExpandedTimeSeriesRange(numExpandedTimeSeries));
        numExpandedTimeSeries  = 3;
        assertEquals(TSDBService.QueryTimeSeriesExpansion.TS_2_10.getName(), TSDBService.QueryTimeSeriesExpansion.getExpandedTimeSeriesRange(numExpandedTimeSeries));
        numExpandedTimeSeries  = 10;
        assertEquals(TSDBService.QueryTimeSeriesExpansion.TS_2_10.getName(), TSDBService.QueryTimeSeriesExpansion.getExpandedTimeSeriesRange(numExpandedTimeSeries));
        numExpandedTimeSeries  = 11;
        assertEquals(TSDBService.QueryTimeSeriesExpansion.TS_11_100.getName(), TSDBService.QueryTimeSeriesExpansion.getExpandedTimeSeriesRange(numExpandedTimeSeries));
        numExpandedTimeSeries  = 12;
        assertEquals(TSDBService.QueryTimeSeriesExpansion.TS_11_100.getName(), TSDBService.QueryTimeSeriesExpansion.getExpandedTimeSeriesRange(numExpandedTimeSeries));
        numExpandedTimeSeries  = 100;
        assertEquals(TSDBService.QueryTimeSeriesExpansion.TS_11_100.getName(), TSDBService.QueryTimeSeriesExpansion.getExpandedTimeSeriesRange(numExpandedTimeSeries));
        numExpandedTimeSeries  = 101;
        assertEquals(TSDBService.QueryTimeSeriesExpansion.TS_101_1000.getName(), TSDBService.QueryTimeSeriesExpansion.getExpandedTimeSeriesRange(numExpandedTimeSeries));
        numExpandedTimeSeries  = 102;
        assertEquals(TSDBService.QueryTimeSeriesExpansion.TS_101_1000.getName(), TSDBService.QueryTimeSeriesExpansion.getExpandedTimeSeriesRange(numExpandedTimeSeries));
        numExpandedTimeSeries  = 1000;
        assertEquals(TSDBService.QueryTimeSeriesExpansion.TS_101_1000.getName(), TSDBService.QueryTimeSeriesExpansion.getExpandedTimeSeriesRange(numExpandedTimeSeries));
        numExpandedTimeSeries  = 1001;
        assertEquals(TSDBService.QueryTimeSeriesExpansion.TS_1001_10000.getName(), TSDBService.QueryTimeSeriesExpansion.getExpandedTimeSeriesRange(numExpandedTimeSeries));
        numExpandedTimeSeries  = 1002;
        assertEquals(TSDBService.QueryTimeSeriesExpansion.TS_1001_10000.getName(), TSDBService.QueryTimeSeriesExpansion.getExpandedTimeSeriesRange(numExpandedTimeSeries));
        numExpandedTimeSeries  = 10000;
        assertEquals(TSDBService.QueryTimeSeriesExpansion.TS_1001_10000.getName(), TSDBService.QueryTimeSeriesExpansion.getExpandedTimeSeriesRange(numExpandedTimeSeries));
        numExpandedTimeSeries  = 10001;
        assertEquals(TSDBService.QueryTimeSeriesExpansion.TS_GREATER_THAN_10000.getName(), TSDBService.QueryTimeSeriesExpansion.getExpandedTimeSeriesRange(numExpandedTimeSeries));
    }
}

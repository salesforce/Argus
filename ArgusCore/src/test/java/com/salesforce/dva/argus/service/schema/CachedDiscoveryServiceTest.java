package com.salesforce.dva.argus.service.schema;

import org.junit.Test;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.service.CacheService;
import com.salesforce.dva.argus.service.DiscoveryService;
import com.salesforce.dva.argus.service.schema.CachedDiscoveryService;
import com.salesforce.dva.argus.service.schema.WildcardExpansionLimitExceededException;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;

public class CachedDiscoveryServiceTest extends AbstractTest {
	
	private static final String CACHED_QUERIES = "[{\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB0\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB1\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB2\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB3\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB4\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB5\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB6\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB7\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB8\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB9\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB10\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB11\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB12\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB13\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB14\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB15\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB16\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB17\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB18\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB19\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB20\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB21\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB22\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB23\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB24\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB25\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB26\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB27\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB28\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"}, {\"aggregator\":\"SUM\",\"metric\":\"winterfell.backupTimestamps-NyB29\",\"tags\":{\"device\":\"myhost-mycompany.com\"},\"endTimestamp\":1485904591853,\"startTimestamp\":1485903991000,\"scope\":\"scope\"} ]";
	
	@Test
	public void testWildcardExpansionWithinLimit() {
		
		CacheService cacheServiceMock = mock(CacheService.class);
		when(cacheServiceMock.get(anyString())).thenReturn(CACHED_QUERIES);
		DiscoveryService discoveryServiceMock = mock(DiscoveryService.class);
		
		CachedDiscoveryService service = new CachedDiscoveryService(cacheServiceMock, discoveryServiceMock, system.getConfiguration());
		List<MetricQuery> queries = service.getMatchingQueries(new MetricQuery("scope*", "metric", null, System.currentTimeMillis() - (30 * 24 * 60 * 60 * 1000L), System.currentTimeMillis()));
		assertEquals(30, queries.size());
	}
	
	@Test(expected = WildcardExpansionLimitExceededException.class)
	public void testWildcardExpansionExceedingLimit() {
		
		CacheService cacheServiceMock = mock(CacheService.class);
		when(cacheServiceMock.get(anyString())).thenReturn(CACHED_QUERIES);
		DiscoveryService discoveryServiceMock = mock(DiscoveryService.class);
		
		CachedDiscoveryService service = new CachedDiscoveryService(cacheServiceMock, discoveryServiceMock, system.getConfiguration());
		service.getMatchingQueries(new MetricQuery("scope*", "metric", null, System.currentTimeMillis() - (300 * 24 * 60 * 60 * 1000L), System.currentTimeMillis()));
	}

}

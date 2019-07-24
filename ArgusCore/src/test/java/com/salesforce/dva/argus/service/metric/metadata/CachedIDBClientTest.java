package com.salesforce.dva.argus.service.metric.metadata;

import com.google.common.collect.ImmutableMap;
import com.salesforce.dva.argus.service.CacheService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

public class CachedIDBClientTest {
    @Test
    public void testCacheMissAndHit() {
        CacheService cacheService = mock(CacheService.class);
        CachedIDBClient client = spy(new CachedIDBClient(new SystemConfiguration(new Properties()), cacheService, mock(MonitorService.class)));

        final AtomicInteger superClassCallCount = new AtomicInteger(0);
        final AtomicInteger cacheGetCallCount = new AtomicInteger(0);
        final String superClassInfo = "someStatus";
        doAnswer(invocationOnMock -> {
            Collection<IDBFieldQuery> queries = invocationOnMock.getArgument(0, Collection.class);
            superClassCallCount.incrementAndGet();
            return ImmutableMap.builder().put(queries.iterator().next(), Optional.ofNullable(superClassInfo)).build();
        }).when(client).onCacheMiss(any());
        doAnswer(invocationOnMock -> {
            cacheGetCallCount.incrementAndGet();
            return null;
        }).when(cacheService).get(eq("$idb.host.dc1.resource.status"));

        IDBFieldQuery query = new IDBFieldQuery(IDBFieldQuery.ResourceType.HOST, "dc1", "resource", "status");
        assertEquals(superClassInfo, client.get(Arrays.asList(query)).get(query).get());
        assertEquals(1, superClassCallCount.get());
        assertEquals(1, cacheGetCallCount.get());

        final String cacheInfo = "changedStatus";
        doAnswer(invocationOnMock -> {
            cacheGetCallCount.incrementAndGet();
            return cacheInfo;
        }).when(cacheService).get(eq("$idb.host.dc1.resource.status"));
        assertEquals(cacheInfo, client.get(Arrays.asList(query)).get(query).get());
        assertEquals(1, superClassCallCount.get());
        assertEquals(2, cacheGetCallCount.get());
    }
}

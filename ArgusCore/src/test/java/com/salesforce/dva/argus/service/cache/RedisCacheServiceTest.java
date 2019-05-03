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

package com.salesforce.dva.argus.service.cache;

import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.system.SystemException;
import org.junit.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.Properties;
import java.io.IOException;
import com.google.common.collect.Lists;
import java.util.Enumeration;



import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;


import org.junit.BeforeClass;
import org.junit.AfterClass;
import com.salesforce.dva.argus.system.SystemMain;
import com.salesforce.dva.argus.TestUtils;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.service.cache.CacheRedisClient;
import com.salesforce.dva.argus.service.cache.RedisCacheService;

import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import redis.clients.jedis.JedisCluster;


@RunWith(MockitoJUnitRunner.class)
public class RedisCacheServiceTest {

    private static SystemConfiguration config = TestUtils.getConfiguration();
    @Mock
    private MonitorService monitorService;

    @BeforeClass
    static public void setUpClass() {
    }

    @AfterClass
    static public void tearDownClass() {
    }


    @Test
    public void testGetKey() {
        String keyName = "blah";
        String expectedValue = "valueblah";
        JedisCluster mockJedisClient = mock(JedisCluster.class);
        CacheRedisClient mockedCachedRedisClient = mock(CacheRedisClient.class);
        when(mockedCachedRedisClient.getJedisClusterClient()).thenReturn(mockJedisClient);
        when(mockJedisClient.get(keyName)).thenReturn(expectedValue);

        RedisCacheService redisCacheService = new RedisCacheService(config, mockedCachedRedisClient, monitorService);
        String actualValue = redisCacheService.get(keyName);
        assertEquals(expectedValue, actualValue);
        actualValue = redisCacheService.get("nonexistant");
        assertEquals(null, actualValue);
    }

    @Test
    public void testGetKeySet() {
        String keyName1 = "blah1";
        String keyName2 = "blah2";
        String expectedValue = "valueblah";
        JedisCluster mockJedisClient = mock(JedisCluster.class);
        CacheRedisClient mockedCachedRedisClient = mock(CacheRedisClient.class);
        when(mockedCachedRedisClient.getJedisClusterClient()).thenReturn(mockJedisClient);
        when(mockJedisClient.get(keyName1)).thenReturn(expectedValue);
        when(mockJedisClient.get(keyName2)).thenReturn(expectedValue);

        RedisCacheService redisCacheService = new RedisCacheService(config, mockedCachedRedisClient, monitorService);

        Set<String> keySet = new HashSet<>(Arrays.asList(keyName1, keyName2));
        Map<String, String> actualMap = redisCacheService.get(keySet);
        Map<String, String> expectedMap = new HashMap<>();
        expectedMap.put(keyName1, expectedValue);
        expectedMap.put(keyName2, expectedValue);
        assertEquals(expectedMap, actualMap);

        keySet = new HashSet<>(Arrays.asList("non1", "non2"));
        Map<String, String> expectedMap2 = new HashMap<>();
        expectedMap2.put("non1", null);
        expectedMap2.put("non2", null);
        actualMap = redisCacheService.get(keySet);
        assertEquals(expectedMap2, actualMap);
    }

    @Test
    public void testPutKey() {
        String keyName = "blah";
        String expectedValue = "valueblah";
        JedisCluster mockJedisClient = mock(JedisCluster.class);
        CacheRedisClient mockedCachedRedisClient = mock(CacheRedisClient.class);
        when(mockedCachedRedisClient.getJedisClusterClient()).thenReturn(mockJedisClient);

        RedisCacheService redisCacheService = new RedisCacheService(config, mockedCachedRedisClient, monitorService);
        redisCacheService.put(keyName, expectedValue, 1);
        verify(mockJedisClient, times(1)).set(keyName, expectedValue);
        verify(mockJedisClient, times(1)).expire(keyName, 1);

    }

    @Test
    public void testPutKeySet() {
        JedisCluster mockJedisClient = mock(JedisCluster.class);
        CacheRedisClient mockedCachedRedisClient = mock(CacheRedisClient.class);
        when(mockedCachedRedisClient.getJedisClusterClient()).thenReturn(mockJedisClient);

        RedisCacheService redisCacheService = new RedisCacheService(config, mockedCachedRedisClient, monitorService);

        Map<String, String> entries = new HashMap<>();
        entries.put("non1", "blah");
        entries.put("non2", "blah");

        redisCacheService.put(entries, 10);
        verify(mockJedisClient, times(2)).set(anyString(), anyString());
        verify(mockJedisClient, times(2)).expire(anyString(), anyInt());

    }

    @Test
    public void testExpire() {
        JedisCluster mockJedisClient = mock(JedisCluster.class);
        CacheRedisClient mockedCachedRedisClient = mock(CacheRedisClient.class);
        when(mockedCachedRedisClient.getJedisClusterClient()).thenReturn(mockJedisClient);

        RedisCacheService redisCacheService = new RedisCacheService(config, mockedCachedRedisClient, monitorService);

        redisCacheService.expire("key1", 10);
        verify(mockJedisClient, times(1)).expire("key1", 10);

    }

    @Test
    public void testExpireKeySet() {
        JedisCluster mockJedisClient = mock(JedisCluster.class);
        CacheRedisClient mockedCachedRedisClient = mock(CacheRedisClient.class);
        when(mockedCachedRedisClient.getJedisClusterClient()).thenReturn(mockJedisClient);

        RedisCacheService redisCacheService = new RedisCacheService(config, mockedCachedRedisClient, monitorService);

        Set<String> keys = new HashSet<>(Arrays.asList("key1", "key2"));
        redisCacheService.expire(keys, 10);
        verify(mockJedisClient, times(2)).expire(anyString(), anyInt());

    }

    @Test
    public void testExist() {
        JedisCluster mockJedisClient = mock(JedisCluster.class);
        CacheRedisClient mockedCachedRedisClient = mock(CacheRedisClient.class);
        when(mockedCachedRedisClient.getJedisClusterClient()).thenReturn(mockJedisClient);

        RedisCacheService redisCacheService = new RedisCacheService(config, mockedCachedRedisClient, monitorService);

        redisCacheService.exist("key1");
        verify(mockJedisClient, times(1)).exists("key1");

    }

    @Test
    public void testExistKeySet() {
        JedisCluster mockJedisClient = mock(JedisCluster.class);
        CacheRedisClient mockedCachedRedisClient = mock(CacheRedisClient.class);
        when(mockedCachedRedisClient.getJedisClusterClient()).thenReturn(mockJedisClient);

        RedisCacheService redisCacheService = new RedisCacheService(config, mockedCachedRedisClient, monitorService);

        Set<String> keys = new HashSet<>(Arrays.asList("key1", "key2"));
        redisCacheService.exist(keys);
        verify(mockJedisClient, times(2)).exists(anyString());
    }

    @Test
    public void testDelete() {
        JedisCluster mockJedisClient = mock(JedisCluster.class);
        CacheRedisClient mockedCachedRedisClient = mock(CacheRedisClient.class);
        when(mockedCachedRedisClient.getJedisClusterClient()).thenReturn(mockJedisClient);

        RedisCacheService redisCacheService = new RedisCacheService(config, mockedCachedRedisClient, monitorService);

        redisCacheService.delete("key1");
        verify(mockJedisClient, times(1)).del("key1");
    }

    @Test
    public void testDeleteKeySet() {
        JedisCluster mockJedisClient = mock(JedisCluster.class);
        CacheRedisClient mockedCachedRedisClient = mock(CacheRedisClient.class);
        when(mockedCachedRedisClient.getJedisClusterClient()).thenReturn(mockJedisClient);

        RedisCacheService redisCacheService = new RedisCacheService(config, mockedCachedRedisClient, monitorService);

        Set<String> keys = new HashSet<>(Arrays.asList("key1", "key2"));
        redisCacheService.delete(keys);
        verify(mockJedisClient, times(2)).del(anyString());
    }

    @Test
    public void testAppend() {
        JedisCluster mockJedisClient = mock(JedisCluster.class);
        CacheRedisClient mockedCachedRedisClient = mock(CacheRedisClient.class);
        when(mockedCachedRedisClient.getJedisClusterClient()).thenReturn(mockJedisClient);

        RedisCacheService redisCacheService = new RedisCacheService(config, mockedCachedRedisClient, monitorService);

        redisCacheService.append("key1", "val1");
        verify(mockJedisClient, times(1)).rpush("key1", "val1");
    }

    @Test
    public void testGetRange() {
        JedisCluster mockJedisClient = mock(JedisCluster.class);
        CacheRedisClient mockedCachedRedisClient = mock(CacheRedisClient.class);
        when(mockedCachedRedisClient.getJedisClusterClient()).thenReturn(mockJedisClient);

        RedisCacheService redisCacheService = new RedisCacheService(config, mockedCachedRedisClient, monitorService);

        redisCacheService.getRange("key1", 2, 10);
        verify(mockJedisClient, times(1)).lrange("key1", 2, 10);
    }

    @Test
    public void testAppendWithTtl() {
        JedisCluster mockJedisClient = mock(JedisCluster.class);
        CacheRedisClient mockedCachedRedisClient = mock(CacheRedisClient.class);
        when(mockedCachedRedisClient.getJedisClusterClient()).thenReturn(mockJedisClient);

        RedisCacheService redisCacheService = new RedisCacheService(config, mockedCachedRedisClient, monitorService);

        redisCacheService.append("key1", "val1", 10);
        verify(mockJedisClient, times(1)).rpush("key1", "val1");
        verify(mockJedisClient, times(1)).expire("key1", 10);
    }

    @Test
    public void testGetRangeSet() {
        JedisCluster mockJedisClient = mock(JedisCluster.class);
        CacheRedisClient mockedCachedRedisClient = mock(CacheRedisClient.class);
        when(mockedCachedRedisClient.getJedisClusterClient()).thenReturn(mockJedisClient);

        RedisCacheService redisCacheService = new RedisCacheService(config, mockedCachedRedisClient, monitorService);

        Set<String> keys = new HashSet<>(Arrays.asList("key1", "key2"));
        redisCacheService.getRange(keys, 2, 10);
        verify(mockJedisClient, times(1)).lrange("key1", 2, 10);
        verify(mockJedisClient, times(1)).lrange("key2", 2, 10);
    }

    @Test
    public void testGetServiceProperties() {
        JedisCluster mockJedisClient = mock(JedisCluster.class);
        CacheRedisClient mockedCachedRedisClient = mock(CacheRedisClient.class);
        when(mockedCachedRedisClient.getJedisClusterClient()).thenReturn(mockJedisClient);

        RedisCacheService redisCacheService = new RedisCacheService(config, mockedCachedRedisClient, monitorService);

        Properties props = redisCacheService.getServiceProperties();
        Enumeration < ? > enumeration = props.propertyNames();
        List<String> namesList = Lists.newArrayList();
        while (enumeration.hasMoreElements()) {
            namesList.add((String) enumeration.nextElement());
        }
        String name1 = "service.property.cache.redis.cache.expiry.in.sec";
        assertTrue("propname [" + name1 + "] not found in " + namesList,
                   namesList.contains(name1));

        name1 = "service.property.cache.redis.cluster";
        assertTrue("propname [" + name1 + "] not found in " + namesList,
                   namesList.contains(name1));

        name1 = "service.property.cache.redis.server.max.connections";
        assertTrue("propname [" + name1 + "] not found in " + namesList,
                   namesList.contains(name1));

    }

    @Test
    public void testDispose() throws IOException {
        JedisCluster mockJedisClient = mock(JedisCluster.class);
        CacheRedisClient mockedCachedRedisClient = mock(CacheRedisClient.class);
        when(mockedCachedRedisClient.getJedisClusterClient()).thenReturn(mockJedisClient);

        RedisCacheService redisCacheService = new RedisCacheService(config, mockedCachedRedisClient, monitorService);

        redisCacheService.dispose();
        verify(mockJedisClient, times(1)).close();
    }

    @Test
    public void testGetByPattern() throws IOException {
        JedisCluster mockJedisClient = mock(JedisCluster.class);
        CacheRedisClient mockedCachedRedisClient = mock(CacheRedisClient.class);
        when(mockedCachedRedisClient.getJedisClusterClient()).thenReturn(mockJedisClient);

        RedisCacheService redisCacheService = new RedisCacheService(config, mockedCachedRedisClient, monitorService);

        redisCacheService.getByPattern("abc");
        verify(mockJedisClient, times(1)).getClusterNodes();
    }



}

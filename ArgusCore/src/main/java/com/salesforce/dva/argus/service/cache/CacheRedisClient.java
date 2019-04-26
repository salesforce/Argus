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

import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPool;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import com.salesforce.dva.argus.system.SystemConfiguration;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

/**
 * Cache service implementation that uses Redis client.
 *
 * @author  Kunal Nawale (knawale@salesforce.com)
 */
public class CacheRedisClient {
    private JedisCluster jedisClusterClient;

    public CacheRedisClient() {
    }

    public void init(SystemConfiguration config) {
        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        int maxTotal = Integer.parseInt(config.getValue(CacheProperty.REDIS_SERVER_MAX_CONNECTIONS.getName(),
                                                        CacheProperty.REDIS_SERVER_MAX_CONNECTIONS.getDefaultValue()));
        poolConfig.setMaxTotal(maxTotal);

        String[] hostsPorts = config.getValue(CacheProperty.REDIS_CLUSTER.getName(),
                                              CacheProperty.REDIS_CLUSTER.getDefaultValue()).split(",");

        Set<HostAndPort> jedisClusterNodes = new HashSet<>();
        for (String hostPort : hostsPorts) {
            String[] hostPortPair = hostPort.split(":");
            jedisClusterNodes.add(new HostAndPort(hostPortPair[0], Integer.parseInt(hostPortPair[1])));
        }
        jedisClusterClient = new JedisCluster(jedisClusterNodes, poolConfig);
    }

    public JedisCluster getJedisClusterClient() {
        return jedisClusterClient;
    }
}

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

import com.google.inject.Inject;
import com.salesforce.dva.argus.service.CacheService;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPool;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/**
 * Cache service implementation that uses Redis client.
 *
 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
 */
public class RedisCacheService extends DefaultService implements CacheService {

    //~ Instance fields ******************************************************************************************************************************

    private final SystemConfiguration _config;
    private final Set<HostAndPort> jedisClusterNodes;
    private Logger _logger = LoggerFactory.getLogger(getClass());
    private GenericObjectPoolConfig poolConfig;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new RedisCacheService object.
     *
     * @param  config  The system configuration.  Cannot be null.
     */
    @Inject
    public RedisCacheService(SystemConfiguration config) {
    	super(config);
        _config = config;
        poolConfig = new GenericObjectPoolConfig();
        poolConfig.setMaxTotal(Integer.parseInt(
                _config.getValue(Property.REDIS_SERVER_MAX_CONNECTIONS.getName(), Property.REDIS_SERVER_MAX_CONNECTIONS.getDefaultValue())));

        String[] hostsPorts = _config.getValue(Property.REDIS_CLUSTER.getName(), Property.REDIS_CLUSTER.getDefaultValue()).split(",");

        jedisClusterNodes = new HashSet<HostAndPort>();
        for (String hostPort : hostsPorts) {
            String[] hostPortPair = hostPort.split(":");

            jedisClusterNodes.add(new HostAndPort(hostPortPair[0], Integer.parseInt(hostPortPair[1])));
        }
    }

    //~ Methods **************************************************************************************************************************************

    @SuppressWarnings("unchecked")
    @Override
    public <V> V get(String key) {
        V returnValue = null;
        JedisCluster jc = new JedisCluster(jedisClusterNodes, poolConfig);

        try {
            returnValue = (V) jc.get(key);
        } finally {
            jc.close();
        }
        return returnValue;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <V> Map<String, V> get(Set<String> keySet) {
        Map<String, V> map = new HashMap<String, V>();

        try {
            for (String key : keySet) {
                map.put(key, (V) get(key));
            }
        } catch (Exception ex) {
            _logger.error("Exception in cache service: {} ", ex.getMessage());
            map = null;
        }
        return map;
    }

    @Override
    public <V> void put(String key, V value, int ttl) {
        JedisCluster jc = new JedisCluster(jedisClusterNodes, poolConfig);

        try {
            jc.set(key, (String) value);
            jc.expire(key, ttl);
        } catch (Exception ex) {
            _logger.error("Exception in cache service: {} ", ex.getMessage());
        } finally {
            jc.close();
        }
    }

    @Override
    public <V> void put(Map<String, V> entries, int ttl) {
        for (Map.Entry<String, V> entry : entries.entrySet()) {
            put(entry.getKey(), entry.getValue(), ttl);
        }
    }

    @Override
    public <V> void expire(String key, int ttl) {
        JedisCluster jc = new JedisCluster(jedisClusterNodes, poolConfig);

        try {
            jc.expire(key, ttl);
        } finally {
            jc.close();
        }
    }

    @Override
    public <V> void expire(Set<String> keys, int ttl) {
        for (String key : keys) {
            expire(key, ttl);
        }
    }

    @Override
    public void clear() {
        JedisCluster jc = new JedisCluster(jedisClusterNodes, poolConfig);
        Iterator<JedisPool> poolIterator = jc.getClusterNodes().values().iterator();

        while (poolIterator.hasNext()) {
            JedisPool pool = poolIterator.next();
            Jedis jedis = pool.getResource();

            try {
                jedis.flushAll();
            } catch (Exception ex) {
                _logger.error("Exception in cache service: {} ", ex.getMessage());
            } finally {
                jedis.close();
            }
        }
        jc.close();
    }

    @Override
    public boolean exist(String key) {
        JedisCluster jc = new JedisCluster(jedisClusterNodes, poolConfig);
        boolean isKeyExisting = false;

        try {
            isKeyExisting = jc.exists(key);
        } finally {
            jc.close();
        }
        return isKeyExisting;
    }

    @Override
    public Map<String, Boolean> exist(Set<String> keys) {
        Map<String, Boolean> map = new LinkedHashMap<String, Boolean>();

        for (String key : keys) {
            map.put(key, exist(key));
        }
        return map;
    }

    @Override
    public Set<String> getKeysByPattern(String pattern) {
        JedisCluster jc = new JedisCluster(jedisClusterNodes, poolConfig);
        Set<String> keysMatched = new TreeSet<String>();
        Iterator<JedisPool> poolIterator = jc.getClusterNodes().values().iterator();

        while (poolIterator.hasNext()) {
            JedisPool pool = poolIterator.next();
            Jedis jedis = pool.getResource();

            try {
                keysMatched.addAll(jedis.keys(pattern));
            } catch (Exception ex) {
                _logger.error("Exception in cache service: {} ", ex.getMessage());
            } finally {
                jedis.close();
            }
        }
        jc.close();
        return keysMatched;
    }

    @Override
    public <V> Map<String, V> getByPattern(String pattern) {
        return get(getKeysByPattern(pattern));
    }

    @Override
    public void delete(String key) {
        JedisCluster jc = new JedisCluster(jedisClusterNodes, poolConfig);

        try {
            jc.del(key);
        } catch (Exception ex) {
            _logger.error("Exception in cache service: {} ", ex.getMessage());
        } finally {
            jc.close();
        }
    }

    @Override
    public void delete(Set<String> keySet) {
        for (String key : keySet) {
            delete(key);
        }
    }

    @Override
    public <V> void append(String key, V value) {
        JedisCluster jc = new JedisCluster(jedisClusterNodes, poolConfig);

        try {
            jc.rpush(key, (String) value);
        } catch (Exception ex) {
            _logger.error("Exception in cache service: {} ", ex.getMessage());
        } finally {
            jc.close();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <V> List<V> getRange(String key, int startOffset, int endOffset) {
        List<V> returnValue = null;
        JedisCluster jc = new JedisCluster(jedisClusterNodes, poolConfig);

        try {
            returnValue = (List<V>) jc.lrange(key, startOffset, endOffset);
        } catch (Exception ex) {
            _logger.error("Exception in cache service: {} ", ex.getMessage());
            returnValue = null;
        } finally {
            jc.close();
        }
        return returnValue;
    }

    @Override
    public <V> void append(String key, V value, int ttl) {
        JedisCluster jc = new JedisCluster(jedisClusterNodes, poolConfig);

        try {
            append(key, value);
            jc.expire(key, ttl);
        } catch (Exception ex) {
            _logger.error("Exception in cache service: {} ", ex.getMessage());
        } finally {
            jc.close();
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public <V> Map<String, V> getRange(Set<String> keys, int startOffset, int endOffset) {
        Map<String, V> map = new HashMap<String, V>();

        try {
            for (String key : keys) {
                map.put(key, (V) getRange(key, startOffset, endOffset));
            }
        } catch (Exception ex) {
            _logger.error("Exception in cache service: {} ", ex.getMessage());
            map = null;
        }
        return map;
    }

    @Override
    public int getCacheExpirationTime() {
        return Integer.parseInt(_config.getValue(Property.REDIS_CACHE_EXPIRY_IN_SEC.getName(), Property.REDIS_CACHE_EXPIRY_IN_SEC.getDefaultValue()));
    }
    
    @Override
    public Properties getServiceProperties() {
            Properties serviceProps= new Properties();

            for(Property property:Property.values()){
                    serviceProps.put(property.getName(), property.getDefaultValue());
            }
            return serviceProps;
    }

    //~ Enums ****************************************************************************************************************************************

    /**
     * Enumerates the implementation specific configuration properties.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    public enum Property {

        /** The global cache expiry in seconds. */
        REDIS_CACHE_EXPIRY_IN_SEC("service.property.cache.redis.cache.expiry.in.sec", "3600"),
        /** The cache endpoint. */
        REDIS_CLUSTER("service.property.cache.redis.cluster", "default_value"),
        /** The maximum number of cache connections. */
        REDIS_SERVER_MAX_CONNECTIONS("service.property.cache.redis.server.max.connections", "100");

        private final String _name;
        private final String _defaultValue;

        private Property(String name, String defaultValue) {
            _name = name;
            _defaultValue = defaultValue;
        }

        /**
         * Returns the property name.
         *
         * @return  The property name.
         */
        public String getName() {
            return _name;
        }

        /**
         * Returns the default value for the property.
         *
         * @return The default value.
         */
        public String getDefaultValue() {
            return _defaultValue;
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

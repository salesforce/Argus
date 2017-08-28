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

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cache service implementation that always results in cache misses.
 *
 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
 */
public class NoOperationCacheService extends DefaultService implements CacheService {

    //~ Methods **************************************************************************************************************************************

    @Inject
    public NoOperationCacheService(SystemConfiguration config) {
        super(config);
    }

	@Override
    public <V> V get(String key) {
        return null;
    }

    @Override
    public <V> Map<String, V> get(Set<String> keys) {
        return null;
    }

    @Override
    public <V> void put(String key, V value, int ttl) {
        // This is a no operation service. This method should do nothing and result in a cache miss.
    }

    @Override
    public <V> void put(Map<String, V> entries, int ttl) {
        // This is a no operation service. This method should do nothing and result in a cache miss.
    }

    @Override
    public void clear() {
        // This is a no operation service. This method should do nothing and result in a cache miss.
    }

    @Override
    public boolean exist(String key) {
        return false;
    }

    @Override
    public <V> Map<String, V> getByPattern(String pattern) {
        return null;
    }

    @Override
    public <V> void expire(String key, int ttl) {
        // This is a no operation service. This method should do nothing and result in a cache miss.
    }

    @Override
    public <V> void expire(Set<String> keys, int ttl) {
        // This is a no operation service. This method should do nothing and result in a cache miss.
    }

    @Override
    public void delete(String key) {
        // This is a no operation service. This method should do nothing and result in a cache miss.
    }

    @Override
    public void delete(Set<String> keys) {
        // This is a no operation service. This method should do nothing and result in a cache miss.
    }

    @Override
    public Map<String, Boolean> exist(Set<String> keys) {
        return null;
    }

    @Override
    public <V> void append(String key, V value) {
        // This is a no operation service. This method should do nothing and result in a cache miss.
    }

    @Override
    public <V> List<V> getRange(String key, int startOffset, int endOffset) {
        return Collections.emptyList();
    }

    @Override
    public <V> void append(String key, V value, int ttl) {
        // This is a no operation service. This method should do nothing and result in a cache miss.
    }

    @Override
    public <V> Map<String, V> getRange(Set<String> keys, int startOffset, int endOffset) {
        return null;
    }

    @Override
    public Set<String> getKeysByPattern(String pattern) {
        return null;
    }

    @Override
    public int getCacheExpirationTime() {
        return 0;
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

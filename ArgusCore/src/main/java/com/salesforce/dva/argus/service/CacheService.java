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
	 
package com.salesforce.dva.argus.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provides methods for creating and retrieving cached data.
 *
 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
 */
public interface CacheService extends Service {

    //~ Methods **************************************************************************************************************************************

    /**
     * Gets a value from a key.
     *
     * @param   <V>  The value type.
     * @param   key  The cache key.  Cannot be null.
     *
     * @return  The value or null if no value exists for the given key.
     */
    <V> V get(String key);

    /**
     * Gets a map of corresponding key-values, given a set of keys.
     *
     * @param   <V>   The value type.
     * @param   keys  The set of cache keys.  Cannot be null, but may be empty.
     *
     * @return  The map of cache keys to cache values.  Cache keys having no cache value will have a map entry value of null.
     */
    <V> Map<String, V> get(Set<String> keys);

    /**
     * Puts a value into the key slot and set expiry of key in seconds.
     *
     * @param  <V>    The value type.
     * @param  key    The cache key.  Cannot be null.
     * @param  value  The cache value.  Cannot be null.
     * @param  ttl    Expiry in seconds.  Must be greater than or equal to zero.
     */
    <V> void put(String key, V value, int ttl);

    /**
     * Puts a map of key-value entries into cache and sets the expiry of each key.
     *
     * @param  <V>      The value type.
     * @param  entries  A map of cache keys to cache values.  Cannot be null, but may be empty.
     * @param  ttl      Expiry in seconds.  Must be greater than or equal to zero.
     */
    <V> void put(Map<String, V> entries, int ttl);

    /** Removes all entries from cache. */
    void clear();

    /**
     * Check if a key exists in cache.
     *
     * @param   key  The cache key.  Cannot be null or empty.
     *
     * @return  True if key exists. False otherwise.
     */
    boolean exist(String key);

    /**
     * Given a key pattern in form of wildcard returns the matching map of key-value pairs.
     *
     * @param   <V>      The value type.
     * @param   pattern  A glob style pattern.  Cannot be null.
     *
     * @return  Map of key-value pairs.  Will never be null, but may be empty.
     */
    <V> Map<String, V> getByPattern(String pattern);

    /**
     * Given a pattern in form of wildcard returns all the matching keys.
     *
     * @param   pattern  A glob style pattern.  Cannot be null.
     *
     * @return  The set of matching keys.  Will never be null, but may be empty.
     */
    Set<String> getKeysByPattern(String pattern);

    /**
     * Sets the timeout on specified key.
     *
     * @param  <V>  The value type.
     * @param  key  The cache key of the entry to expire.  Cannot be null.
     * @param  ttl  The timeout in seconds.  Must be greater than or equal to zero.
     */
    <V> void expire(String key, int ttl);

    /**
     * Sets the timeout for a set of keys.
     *
     * @param  <V>   The value type.
     * @param  keys  The set of cache keys for the entries to expire.  Cannot be null, but may be empty.
     * @param  ttl   The timeout in seconds.  Must be greater than or equal to zero.
     */
    <V> void expire(Set<String> keys, int ttl);

    /**
     * Deletes a specified key-value entry from cache.
     *
     * @param  key  The cache key of the entry to delete.  Cannot be null.
     */
    void delete(String key);

    /**
     * Deletes the set of key-value entries from cache.
     *
     * @param  keys  The set of keys of the entries to delete.  Cannot be null, but may be empty.
     */
    void delete(Set<String> keys);

    /**
     * Check if a set of keys exist in cache.
     *
     * @param   keys  The set of keys to examine.  Cannot be null, but may be empty.
     *
     * @return  A map having the same size as the input set of keys, indicating the existence of each key.  
     */
    Map<String, Boolean> exist(Set<String> keys);

    /**
     * Appends the value to an existing key or creates a new key if it does not exist.
     *
     * @param  <V>    The value type.
     * @param  key    The cache key.  Cannot be null or empty.
     * @param  value  The value to append.  Cannot be null.
     */
    <V> void append(String key, V value);

    /**
     * Gets the list of values from <tt>startOffset</tt> to <tt>endOffset</tt> for a key. To get all values in key entry startOffset=0 and endOffset=-1.
     *
     * @param   <V>          The value type.
     * @param   key          The cache key.  Cannot be null or empty.
     * @param   startOffset  Start of offset. First index is from 0.
     * @param   endOffset    End of offset.
     *
     * @return  The list of values for the given offsets.  Will never return null, but may be empty.
     */
    <V> List<V> getRange(String key, int startOffset, int endOffset);

    /**
     * Appends the value to an existing key or creates a new key if it does not exist and also sets the timeout.
     *
     * @param  <V>    The value type.
     * @param  key    The cache key.  Cannot be null.
     * @param  value  The value.  Cannot be null.
     * @param  ttl    The timeout in seconds.  Must be greater than zero.
     */
    <V> void append(String key, V value, int ttl);

    /**
     * Returns the map of key-values, for value list between <tt>startOffset</tt> to <tt>endOffset</tt> for a set of keys. To get all values in key entry startOffset=0
     * and endOffset=-1.
     *
     * @param   <V>          The value type.
     * @param   keys         The cache keys.  Cannot be null, but may be empty.
     * @param   startOffset  Start of offset. First index is from 0.
     * @param   endOffset    End of offset.
     *
     * @return  The corresponding cache entries.  Will never return null, but may be empty.
     */
    <V> Map<String, V> getRange(Set<String> keys, int startOffset, int endOffset);

    /**
     * Return the global cache expiration time in seconds.
     *
     * @return  The global cache expiration.
     */
    int getCacheExpirationTime();
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

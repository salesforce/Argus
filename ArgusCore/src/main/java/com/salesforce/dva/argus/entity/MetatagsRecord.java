/*
 * Copyright (c) 2018, Salesforce.com, Inc.
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

package com.salesforce.dva.argus.entity;

import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.TreeMap;
import com.salesforce.dva.argus.entity.TSDBEntity.ReservedField;


public class MetatagsRecord {
    private Map<String, String> _metatags = new HashMap<>(0);
    private String _key = null;

    public MetatagsRecord(Map<String, String> metatags, String key) {
        setMetatags(metatags, key);
    }

    /**
     * Returns an unmodifiable collection of metatags associated with the metric.
     *
     * @return  The metatags for a metric. Will never be null but may be empty.
     */
    public Map<String, String> getMetatags() {
        Map<String, String> result = new HashMap<>();

        for (Map.Entry<String, String> entry : _metatags.entrySet()) {
            String key = entry.getKey();

            if (!ReservedField.isReservedField(key)) {
                result.put(key, entry.getValue());
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Replaces the metatags for a metric. Metatags cannot use any of the reserved tag names.
     *
     * @param  metatags  The new metatags for the metric.
     */
    public void setMetatags(Map<String, String> metatags, String key) {
        if (metatags != null) {
            TSDBEntity.validateTags(metatags);
            _metatags.clear();
            _metatags.putAll(metatags);
            _key = key;
        }
    }

    /**
     *   Sets the key identifier
    */
    public void setKey(String key) {
        _key = key;
    }

    /**
     *   Returns the key identifier
    */
    public String getKey() {
        return _key;
    }
}

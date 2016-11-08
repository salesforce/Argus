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
	 
package com.salesforce.dva.argus.service.metric.transform;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Calculates the discrete time derivative.<br/>
 * <tt>DERIVATIVE(<expr>, <excludeType>)</tt><br/>
 * <tt>DERIVATIVE(<expr>)</tt>
 *
 * @param   metrics      The list of metrics to evaluate. Cannot be null or empty.
 * @param   excludeType  Indicates what type of value to cull from the results. Must be one of 'negative', 'positive', 'none'. If null the value
 *                       defaults to 'none'.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class DerivativeValueMapping implements ValueMapping {

    //~ Methods **************************************************************************************************************************************

    @Override
    public Map<Long, String> mapping(Map<Long, String> originalDatapoints) {
        Map<Long, String> sortedDatapoints = new TreeMap<>();

        sortedDatapoints.putAll(originalDatapoints);

        Map<Long, String> derivativeDatapoints = new HashMap<Long, String>();
        Double prev = null;

        for (Entry<Long, String> entry : sortedDatapoints.entrySet()) {
            Double curr = Double.valueOf(entry.getValue());

            if (prev != null) {
                derivativeDatapoints.put(entry.getKey(), String.valueOf(curr - prev));
            }
            prev = curr;
        }
        return derivativeDatapoints;
    }

    @Override
    public Map<Long, String> mapping(Map<Long, String> originalDatapoints, List<String> constants) {
        throw new UnsupportedOperationException("Derivative Transform doesn't accept constants!");
    }

    @Override
    public String name() {
        return TransformFactory.Function.DERIVATIVE.name();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

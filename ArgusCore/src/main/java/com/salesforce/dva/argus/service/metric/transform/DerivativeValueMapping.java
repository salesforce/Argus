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

import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.util.TransformUtil;

import java.util.TreeMap;

/**
 * Calculates the discrete time derivative.<br>
 * <tt>DERIVATIVE(&lt;expr&gt;, &lt;excludeType&gt;)</tt><br>
 * <tt>DERIVATIVE(&lt;expr&gt;)</tt>
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class DerivativeValueMapping implements ValueMapping {

    //~ Methods **************************************************************************************************************************************

    @Override
    public Map<Long, Double> mapping(Map<Long, Double> originalDatapoints) {
		SystemAssert.requireArgument(originalDatapoints != null, "Cannot transform null metric/metrics");
		return computeDerivedValues(originalDatapoints, -1L);
    }

    @Override
    public Map<Long, Double> mapping(Map<Long, Double> originalDatapoints, List<String> constants) {
		SystemAssert.requireArgument(originalDatapoints != null, "Cannot transform null datapoints");
		SystemAssert.requireArgument(constants.size() == 1,
				"Derivative Transform can have exactly one constant");
		String intervalSizeStr = constants.get(0);
		Long intervalSizeInSeconds = TransformUtil.getWindowInSeconds(intervalSizeStr) * 1000;
		return computeDerivedValues(originalDatapoints, intervalSizeInSeconds);
    }

    private Map<Long, Double> computeDerivedValues(Map<Long, Double> originalDatapoints, Long intervalSizeInSeconds) {
        Map<Long, Double> sortedDatapoints = new TreeMap<>();

        sortedDatapoints.putAll(originalDatapoints);

        Map<Long, Double> derivativeDatapoints = new HashMap<>();
		Entry<Long, Double> prevEntry = null;

        for (Entry<Long, Double> entry : sortedDatapoints.entrySet()) {
            if (prevEntry != null) {
            	if(intervalSizeInSeconds<=0) {
				    derivativeDatapoints.put(entry.getKey(), entry.getValue() - prevEntry.getValue());
				}else {
					derivativeDatapoints.put(entry.getKey(), ((entry.getValue() - prevEntry.getValue())*intervalSizeInSeconds)/(entry.getKey()-prevEntry.getKey()));
				}
            }
            prevEntry = entry;
        }
        return derivativeDatapoints;
	}

	@Override
    public String name() {
        return TransformFactory.Function.DERIVATIVE.name();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

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

import java.util.List;
import java.util.Map;

import com.salesforce.dva.argus.entity.NumberOperations;

/**
 * Reducer or mapping for range transform.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class RangeValueReducerOrMapping implements ValueReducerOrMapping {

    //~ Methods **************************************************************************************************************************************

    @Override
    public Number reduce(List<Number> values) {
        Number min = null;
        Number max = null;

        for (Number value : values) {
            if (value == null) {
                value = 0;
            }

            min = min == null ? value : NumberOperations.getMin(value, min);
            max = max == null ? value : NumberOperations.getMax(value, max);
        }
        return max == null ? Double.MAX_VALUE - Double.MIN_VALUE : NumberOperations.subtract(max, min);
    }

    @Override
    public Map<Long, Number> mapping(Map<Long, Number> originalDatapoints) {
        throw new UnsupportedOperationException("Range transform doesn't suppport mapping");
    }

    @Override
    public Map<Long, Number> mapping(Map<Long, Number> originalDatapoints, List<String> constants) {
        throw new UnsupportedOperationException("Range transform doesn't suppport mapping");
    }

    @Override
    public Number reduce(List<Number> values, List<String> constants) {
        throw new UnsupportedOperationException("Range transform doesn't suppport reduce with constant");
    }

    @Override
    public String name() {
        return TransformFactory.Function.RANGE.name();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

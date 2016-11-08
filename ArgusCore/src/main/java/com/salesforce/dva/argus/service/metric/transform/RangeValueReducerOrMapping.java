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

/**
 * Reducer or mapping for range transform.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class RangeValueReducerOrMapping implements ValueReducerOrMapping {

    //~ Methods **************************************************************************************************************************************

    @Override
    public String reduce(List<String> values) {
        double min = Double.MAX_VALUE;
        double max = Double.MIN_VALUE;

        for (String value : values) {
            if (value == null) {
                value = "0.0";
            }

            double candidate = Double.parseDouble(value);

            if (candidate < min) {
                min = candidate;
            }
            if (candidate > max) {
                max = candidate;
            }
        }
        return String.valueOf(max - min);
    }

    @Override
    public Map<Long, String> mapping(Map<Long, String> originalDatapoints) {
        throw new UnsupportedOperationException("Range transform doesn't suppport mapping");
    }

    @Override
    public Map<Long, String> mapping(Map<Long, String> originalDatapoints, List<String> constants) {
        throw new UnsupportedOperationException("Range transform doesn't suppport mapping");
    }

    @Override
    public String reduce(List<String> values, List<String> constants) {
        throw new UnsupportedOperationException("Range transform doesn't suppport reduce with constant");
    }

    @Override
    public String name() {
        return TransformFactory.Function.RANGE.name();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

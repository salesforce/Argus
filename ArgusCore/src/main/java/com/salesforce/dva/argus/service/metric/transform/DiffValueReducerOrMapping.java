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

import com.salesforce.dva.argus.entity.NumberOperations;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calculates an arithmetic difference. If a constant is provided, it is subtracted from each data point in the set of input metrics, otherwise the
 * data point values of each time stamp for all but the first metric are subtracted from the data point value of the first metric.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class DiffValueReducerOrMapping implements ValueReducerOrMapping {

    //~ Methods **************************************************************************************************************************************

    @Override
    public Number reduce(List<Number> values) {
        Number difference = values.get(0);

        for (int i = 1; i < values.size(); i++) {
            Number value = values.get(i);

            if (value == null) {
                continue;
            }
            difference = NumberOperations.subtract(difference, value);
        }
        return difference;
    }
    
    @Override
    public Map<Long, Number> mapping(Map<Long, Number> originalDatapoints) {
        throw new UnsupportedOperationException("Diff Transform with mapping is not supposed to be used without a constant");
    }

    @Override
    public Map<Long, Number> mapping(Map<Long, Number> originalDatapoints, List<String> constants) {
        SystemAssert.requireArgument(constants != null && constants.size() == 1,
            "If constants provided for diff transform, only exactly one constant allowed.");

        Map<Long, Number> diffDatapoints = new HashMap<>();

        Number subtrahend;
        try {
        	subtrahend = Long.parseLong(constants.get(0));
        } catch (NumberFormatException nfe) {
        	try {
        		subtrahend = Double.parseDouble(constants.get(0));
        	} catch (NumberFormatException nfe2) {
        		throw new SystemException("Illegal constant value " + constants.get(0) + " supplied to diff transform.");
        	}
        }
        
        for (Map.Entry<Long, Number> entry : originalDatapoints.entrySet()) {
        	diffDatapoints.put(entry.getKey(), NumberOperations.subtract(entry.getValue(), subtrahend));
        }
        
        return diffDatapoints;
    }
    
    @Override
    public Number reduce(List<Number> values, List<String> constants) {
        throw new UnsupportedOperationException("Diff Transform with reducer is not supposed to be used without a constant");
    }
    
    @Override
    public String name() {
        return TransformFactory.Function.DIFF.name();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

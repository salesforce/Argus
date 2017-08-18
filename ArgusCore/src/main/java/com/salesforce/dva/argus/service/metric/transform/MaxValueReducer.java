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
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.salesforce.dva.argus.service.tsdb.MetricScanner;

/**
 * Takes a list of doubles represents as Strings and returns the maximum value as a String. Values that do not convert to doubles are ignored.
 *
 * @author  seamus.carroll
 */
public class MaxValueReducer implements ValueReducer {

    //~ Methods **************************************************************************************************************************************

    @Override
    public Double reduce(List<Double> values) {
	    	if(values == null || values.isEmpty()) {
	    		return null;
	    	}
	    	
	    	Stream<Double> stream = StreamSupport.stream(values.spliterator(), true);
	    	if(stream.allMatch(o -> o == null)) {
	    		stream.close();
	    		return null;
	    	}
	    	stream.close();
    	
        double max = Double.NEGATIVE_INFINITY;
        for (Double value : values) {
            if (value == null) {
                continue;
            }

            double candidate = value;
            if (candidate > max) {
                max = candidate;
            }
        }
        return max;
    }
    
    @Override
    public Double reduceScanner(MetricScanner scanner) {
    		if (scanner == null || !scanner.hasNextDP()) {
    			return null;
    		}
    		
    		double max = Double.NEGATIVE_INFINITY;
    		boolean unchanged = true;
    		
    		while (scanner.hasNextDP()) {
    			Double value = scanner.getNextDP().getValue();
    			if (value == null) {
    				continue;
    			}
    			double candidate = value;
    			if (unchanged || candidate > max) {
    				unchanged = false;
    				max = candidate;
    			}
	    	}
    		return !unchanged ? max : null;
    }

    @Override
    public String name() {
        return TransformFactory.Function.MAX.name();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

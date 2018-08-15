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

import com.google.common.base.Predicate;
import com.google.common.collect.Maps;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.NumberOperations;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Calculate the difference between the maximum and minimum values at each time stamp. If a single time series is specified, the minimum and maximum
 * of all values in the time series is returned.
 *
 * <p>Tricky part here is that Range reducer behaviour is similar to the reducer in MetricReducerOrMapping not MetricReducer</p>
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class RangeTransformWrap implements Transform {

    //~ Methods **************************************************************************************************************************************

    @Override
    public List<Metric> transform(List<Metric> metrics) {
        if (metrics.size() == 1) {
            return rangeOfOneMetric(metrics.get(0));
        } else {
            return new MetricReducerOrMappingTransform(new RangeValueReducerOrMapping()).transform(metrics);
        }
    }

    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
        throw new UnsupportedOperationException("Range Transform doesn't accept constants!");
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.RANGE.name();
    }

    private List<Metric> rangeOfOneMetric(Metric metric) {
        Map<Long, Number> cleanDPs = new HashMap<>();

        for (Map.Entry<Long, Number> entry : metric.getDatapoints().entrySet()) {
            if (entry.getValue() == null) {
                cleanDPs.put(entry.getKey(), 0);
            } else {
                cleanDPs.put(entry.getKey(), entry.getValue());
            }
        }

        List<Metric> result = new ArrayList<Metric>();
        final List<Number> dpNum = new ArrayList<Number>();

        for (Number value : cleanDPs.values()) {
            dpNum.add(value);
        }
        
        Collections.sort(dpNum, new Comparator<Number>() {

			@Override
			public int compare(Number o1, Number o2) {
				return NumberOperations.compare(o1, o2);
			}
        	
        });

        @SuppressWarnings("serial")
        final Set<Number> minMaxSet = new HashSet<Number>() {

                {
                    add(dpNum.get(0));
                    add(dpNum.get(dpNum.size() - 1));
                }
            };

        Predicate<Map.Entry<Long, Number>> isMinMax = new Predicate<Map.Entry<Long, Number>>() {

                @Override
                public boolean apply(Map.Entry<Long, Number> datapoint) {
                    return minMaxSet.contains(datapoint.getValue());
                }
            };

        Map<Long, Number> resultDatapoints = new HashMap<>();

        resultDatapoints.putAll(Maps.filterEntries(cleanDPs, isMinMax));
        metric.setDatapoints(resultDatapoints);
        result.add(metric);
        return result;
    }
    
    @Override
    public List<Metric> transform(List<Metric>... listOfList) {
        throw new UnsupportedOperationException("Range Transform doesn't accept list of metric list!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

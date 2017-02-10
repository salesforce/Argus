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

import com.salesforce.dva.argus.entity.Metric;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Dilip Devaraj.
 *
 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
 */
public class ZeroIfMissingSum implements Transform {

    //~ Static fields/initializers *******************************************************************************************************************

    private static final String RESULT_METRIC_NAME = "result";

    //~ Methods **************************************************************************************************************************************

    @Override
    public List<Metric> transform(List<Metric> metrics) {
        if (metrics == null) {
            throw new MissingDataException("The metrics list cannot be null or empty while performing arithmetic transformations.");
        }

        List<Metric> resultMetrics = new ArrayList<>();

        if (metrics.isEmpty()) {
            return resultMetrics;
        }

        Metric result = new Metric(getResultScopeName(), RESULT_METRIC_NAME);
        Map<Long, Double> resultDatapoints = new HashMap<>();

        for (Metric metric : metrics) {
            Iterator<Entry<Long, Double>> it = metric.getDatapoints().entrySet().iterator();

            while (it.hasNext()) {
                Entry<Long, Double> entry = it.next();

                if (resultDatapoints.containsKey(entry.getKey())) {
                    resultDatapoints.put(entry.getKey(), performOperation(resultDatapoints.get(entry.getKey()), entry.getValue()));
                } else {
                    resultDatapoints.put(entry.getKey(), entry.getValue());
                }
            }
        }
        result.setDatapoints(resultDatapoints);
        MetricDistiller.setCommonAttributes(metrics, result);
        Collections.addAll(resultMetrics, result);
        return resultMetrics;
    }

    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
        throw new UnsupportedOperationException("Zero if missing Sum Transform is not supposed to be used with a constant");
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.ZEROIFMISSINGSUM.name();
    }

    private Double performOperation(Double operand1, Double operand2) {
        if(operand1 == null) {
        	return operand2;
        }
        
        if(operand2 == null) {
        	return operand1;
        }

        return operand1 + operand2;
    }

    @Override
    public List<Metric> transform(List<Metric>... listOfList) {
        throw new UnsupportedOperationException("Zero if missing Sum Transform is not supposed to be used with a list of metric list!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

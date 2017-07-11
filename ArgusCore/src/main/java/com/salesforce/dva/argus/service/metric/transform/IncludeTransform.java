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
import com.salesforce.dva.argus.service.tsdb.MetricScanner;
import com.salesforce.dva.argus.system.SystemAssert;
import java.util.ArrayList;
import java.util.List;

/**
 * Retains metrics based on the matching of a regular expression against the metric name.<br/>
 * <tt>INCLUDE(<expr>, <regex>)</tt>
 *
 * @param   metrics  The list of metrics to evaluate. Cannot be null or empty.
 * @param   filter   The regular expression to match against.
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class IncludeTransform implements Transform {

    //~ Methods **************************************************************************************************************************************

    @Override
    public List<Metric> transform(List<Metric> metrics) {
        throw new UnsupportedOperationException("Include Transform cannot be performed without an regular expression.");
    }
	
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners) {
        throw new UnsupportedOperationException("Include Transform cannot be performed without an regular expression.");
    }

    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform null metric/metrics");
        SystemAssert.requireArgument(constants != null && constants.size() == 1,
            "Include transform require regex, only exactly one constant allowed.");
        SystemAssert.requireArgument(!constants.get(0).equals(""), "Expression can't be an empty string");

        List<Metric> includeMetricList = new ArrayList<Metric>();
        String expr = constants.get(0);

        for (Metric metric : metrics) {
            String name = metric.getIdentifier();
            boolean isMatch = name.matches(expr);

            if (isMatch) {
                includeMetricList.add(metric);
            }
        }
        return includeMetricList;
    }
	
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners, List<String> constants) {
    	SystemAssert.requireArgument(scanners != null, "Cannot transform null metric scanner/scanners");
        SystemAssert.requireArgument(constants != null && constants.size() == 1,
            "Include transform require regex, only exactly one constant allowed.");
        SystemAssert.requireArgument(!constants.get(0).equals(""), "Expression can't be an empty string");
        
        List<Metric> includeMetricList = new ArrayList<Metric>();
        String expr = constants.get(0);
        
        for (MetricScanner scanner : scanners) {
        	if (scanner.getMetric().getIdentifier().matches(expr)) {
        		Metric m = setMetricData(scanner); // only do this if there is a match, generate all of the datapoints to store
        		includeMetricList.add(m);
        	}
        }
        return includeMetricList;
    }
	
    private Metric setMetricData(MetricScanner scanner) {
    	synchronized(scanner) {
	    	while (scanner.hasNextDP()) {
	    		scanner.getNextDP();
	    	}
    	}
       	return scanner.getMetric();
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.INCLUDE.name();
    }

    @Override
    public List<Metric> transform(List<Metric>... listOfList) {
        throw new UnsupportedOperationException("Include doesn't need list of list!");
    }
	
    @Override
    public List<Metric> transformScanner(List<MetricScanner>... listOfList) {
        throw new UnsupportedOperationException("Include doesn't need list of list!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

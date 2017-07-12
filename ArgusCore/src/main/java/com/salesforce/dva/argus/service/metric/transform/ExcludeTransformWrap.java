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
 * Culls metrics based on the matching of a regular expression against the metric name.<br/>
 * <tt>EXCLUDE(<expr>, <regex>)</tt>
 *
 * @param   metrics  The list of metrics to evaluate. Cannot be null or empty.
 * @param   filter   The regular expression to match against. Cannot be null or empty.
 *
 * @author  Ruofan Zhang(rzhang@salesforce.com)
 */
public class ExcludeTransformWrap implements Transform {

    //~ Methods **************************************************************************************************************************************

    @Override
    public List<Metric> transform(List<Metric> metrics) {
        throw new UnsupportedOperationException("Exclude Transform cannot be performed without a regular expression.");
    }
	
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners) {
        throw new UnsupportedOperationException("Exclude Transform cannot be performed without a regular expression.");
    }

    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
        List<Metric> removedMetrics = new IncludeTransform().transform(metrics, constants);

        try {
            metrics.removeAll(removedMetrics);
        } catch (Exception e) {
            throw new RuntimeException("Fail to remove some metrics for Exclude Transform!");
        }
        return metrics;
    }
	
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners, List<String> constants) {
    	SystemAssert.requireArgument(scanners != null, "Cannot transform null metric scanner/scanners");
        SystemAssert.requireArgument(constants != null && constants.size() == 1,
            "Include transform require regex, only exactly one constant allowed.");
        SystemAssert.requireArgument(!constants.get(0).equals(""), "Expression can't be an empty string");
        
        List<Metric> excludeMetricList = new ArrayList<Metric>();
        String expr = constants.get(0);
        
        for (MetricScanner scanner : scanners) {
	        	if (!scanner.getMetric().getIdentifier().matches(expr)) {
	        		setMetricData(scanner); // only do this if there is not a match, generate stored datapoints
	        		excludeMetricList.add(scanner.getMetric());
	        	}
				else {
	        		scanner.dispose();
	        	}
        }
        return excludeMetricList;
    }
	
    private void setMetricData(MetricScanner scanner) {	    	
	    synchronized(scanner) {
    		while (scanner.hasNextDP()) {
	    		scanner.getNextDP();
	    	}
	    }
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.EXCLUDE.name();
    }

    @Override
    public List<Metric> transform(List<Metric>... listOfList) {
        throw new UnsupportedOperationException("Exclude doesn't need list of list!");
    }
	
    @Override
    public List<Metric> transformScanner(List<MetricScanner>... listOfList) {
        throw new UnsupportedOperationException("Exclude doesn't need list of list!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

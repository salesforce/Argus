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
 * Identity transform for a time series metric.
 *
 * @author  Bhinav Sura (bsura@salesforce.com)
 */
public class IdentityTransform implements Transform {

    //~ Methods **************************************************************************************************************************************

    @Override
    public List<Metric> transform(List<Metric> metrics) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform null metric/metrics");
        return metrics;
    }
	
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners) {
        SystemAssert.requireArgument(scanners != null, "Cannot transform null metric scanner/scanners");
        List<Metric> metrics = new ArrayList<>();
        for (MetricScanner scanner : scanners) {   
        	synchronized(scanner) {
	        	while (scanner.hasNextDP()) {
	        		scanner.getNextDP();
	        	}
        	}
        	metrics.add(scanner.getMetric());
        }
        return metrics;
    }

    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
        throw new UnsupportedOperationException("Identity Transform is not supposed to be used with a constant");
    }
	
    @Override
    public List<Metric> transformScanner(List<MetricScanner> metrics, List<String> constants) {
        throw new UnsupportedOperationException("Identity Transform is not supposed to be used with a constant");
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.IDENTITY.name();
    }

    @Override
    public List<Metric> transform(List<Metric>... listOfList) {
        throw new UnsupportedOperationException("This class is deprecated!");
    }
	
    @Override
    public List<Metric> transformScanner(List<MetricScanner>... listOfList) {
        throw new UnsupportedOperationException("This class is deprecated!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

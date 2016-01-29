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
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Scales a time series.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class ScaleTransform implements Transform {

    //~ Instance fields ******************************************************************************************************************************

    private final Logger _logger = LoggerFactory.getLogger(ScaleTransform.class);

    //~ Methods **************************************************************************************************************************************

    @Override
    public List<Metric> transform(List<Metric> metrics) {
        throw new UnsupportedOperationException("Scale Transform requires a constant parameter");
    }

    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform null or empty metrics");
        if (metrics.isEmpty()) {
            return metrics;
        }
        SystemAssert.requireArgument(constants != null && constants.size() == 1, "Scale Transform must provide exactly 1 constant.");

        Double scaleBy = 0.0;

        try {
            scaleBy = Double.parseDouble(constants.get(0));
        } catch (NumberFormatException nfe) {
            throw new SystemException("Illegal constant value supplied to scale transform", nfe);
        }
        for (Metric metric : metrics) {
            Map<Long, String> resultDatapoints = new HashMap<Long, String>();

            for (Long timestamp : metric.getDatapoints().keySet()) {
                String oldValue = metric.getDatapoints().get(timestamp);

                try {
                    resultDatapoints.put(timestamp, String.valueOf(Double.parseDouble(oldValue) * scaleBy));
                } catch (NumberFormatException | NullPointerException e) {
                    _logger.warn("Failed to parse datapoint: " + oldValue + "Skipping this one.");
                }
            }
            metric.setDatapoints(resultDatapoints);
        }
        return metrics;
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.SCALE.name();
    }

    @Override
    public List<Metric> transform(List<Metric>... listOfList) {
        throw new UnsupportedOperationException("This class is deprecated!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

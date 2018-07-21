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

import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemException;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Calculates the standard deviation per timestamp for more than one metric, or the standard deviation for all data points for a single metric.<br>
 * <tt>DEVIATION(&lt;expr&gt;, &lt;tolerance&gt;, &lt;constant&gt;)</tt><br>
 * <tt>DEVIATION(&lt;expr&gt;, &lt;tolerance&gt;)</tt>
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class DeviationValueReducerOrMapping implements ValueReducerOrMapping {

    //~ Static fields/initializers *******************************************************************************************************************

    private static Double tolerance = 0.1;
    private static Long pointNum = Long.MIN_VALUE;

    //~ Methods **************************************************************************************************************************************

    @Override
    public Double reduce(List<Double> values) {
        throw new UnsupportedOperationException("Deviation Transform with reducer is not supposed to be used without a tolerance!");
    }

    @Override
    public Double reduce(List<Double> values, List<String> constants) {
        parseConstants(constants);
        return calculateDeviation(values, tolerance);
    }

    private void parseConstants(List<String> constants) {
        SystemAssert.requireArgument(constants != null && !constants.isEmpty(), "Deviation Transform must provide at least tolerance to evaluate.");
        SystemAssert.requireArgument(Double.parseDouble(constants.get(0)) > 0.0 && Double.parseDouble(constants.get(0)) < 1.0,
            "For Deviation Transform, 0.0 < tolerance < 1.0.");
        DeviationValueReducerOrMapping.tolerance = Double.parseDouble(constants.get(0));
        if (constants.size() > 1) {
            try {
                DeviationValueReducerOrMapping.pointNum = Long.parseLong(constants.get(1));
                SystemAssert.requireArgument(pointNum > 0L, "For Deviation Transform, point number must bigger then ZERO!");
            } catch (NumberFormatException nfe) {
                throw new SystemException("Illegal point number supplied to deviation transform", nfe);
            }
        }
    }

    private Double calculateDeviation(List<Double> values, Double tolerance) {
        if (!isUnderTolerance(values, tolerance)) {
            return null;
        }

        double[] elements = new double[values.size()];
        int k = 0;

        for (Double value : values) {
            elements[k] = value;
            k++;
        }

        double result = new StandardDeviation().evaluate(elements);

        return result;
    }

    private boolean isUnderTolerance(List<Double> values, Double tolearnce) {
        double missingPointNumber = 0;

        for (Double value : values) {
            if (value == null) {
                missingPointNumber++;
            }
        }
        return missingPointNumber / values.size() <= tolerance ? true : false;
    }

    @Override
    public Map<Long, Double> mapping(Map<Long, Double> originalDatapoints) {
        throw new UnsupportedOperationException("Deviation Transform with mapping is not supposed to be used without a tolerance!");
    }

    @Override
    public Map<Long, Double> mapping(Map<Long, Double> originalDatapoints, List<String> constants) {
        parseConstants(constants);
        return calculateNDeviationForOneMetric(originalDatapoints, tolerance, pointNum);
    }

    private Map<Long, Double> calculateNDeviationForOneMetric(Map<Long, Double> originalDatapoints, Double tolerance, Long pointNum) {
        if (pointNum > originalDatapoints.size()) {
            pointNum = (long) originalDatapoints.size();
        }

        // construct list of values
        Long count = 0L;
        List<Double> values = new ArrayList<>();
        TreeMap<Long, Double> sortedDatapoints = new TreeMap<>(originalDatapoints);
        Long lastTimestamp = sortedDatapoints.lastKey();

        while (count < pointNum) {
            Map.Entry<Long, Double> lastEntry = sortedDatapoints.pollLastEntry();

            values.add(lastEntry.getValue());
            count++;
        }

        // calculate the deviation against string list
        Double dev = calculateDeviation(values, tolerance);

        Map<Long, Double> deviationDatapoints = new TreeMap<>();

        deviationDatapoints.put(lastTimestamp, dev);
        return deviationDatapoints;
    }

    @Override
    public String name() {
        return TransformFactory.Function.DEVIATION.name();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

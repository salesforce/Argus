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

import com.github.brandtg.stl.StlDecomposition;
import com.github.brandtg.stl.StlResult;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.system.SystemAssert;

import java.util.*;
import org.apache.commons.math3.distribution.*;
import org.apache.commons.math3.exception.NullArgumentException;

/**
 * Created by vmuruganantham on 7/13/16.
 */
public class AnomalySTLTransform implements Transform {

    //~ Methods **************************************************************************************************************************************

    @Override
    public List<Metric> transform(List<Metric> metrics) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform null metric/metrics");
        SystemAssert.requireState(metrics.size() != 1, "Anomaly Detection Transform can only be used on one metric.");

        Metric metric = metrics.get(0);
        Map<Long, String> datapoints = metric.getDatapoints();
        double[] values = new double[datapoints.size()];
        List<Long> time_list = new ArrayList<>(datapoints.keySet());
        Collections.sort(time_list);
        double[] times = new double[datapoints.size()];
        for (int i = 0; time_list.size() > i; i++) {
            values[i] = Double.parseDouble(datapoints.get(time_list.get(i)));
            times[i] = (double) time_list.get(i);
        }

        // The argument to StlDecomposition specifies what fraction of a year one season is
        StlResult stl = new StlDecomposition(12).decompose(times, values);
        double[] remainder = stl.getRemainder();

        double mean = 0;
        for (double resid : remainder) {
            mean += resid;
        }
        mean /= remainder.length;

        double sd = 0;
        for (double resid : remainder) {
            sd += Math.pow(resid - mean, 2);
        }
        sd = Math.sqrt(sd);

        HashMap<Long, String> remainder_map = new HashMap<>();
        NormalDistribution norm = new NormalDistribution(mean, sd);
        for (int i = 0; i < time_list.size(); i++) {
            remainder_map.put((long) times[i], Double.toString(norm.probability(remainder[i])));
        }

        Metric remainder_metric = new Metric(getResultScopeName(), "residual");
        remainder_metric.setDatapoints(remainder_map);
        List<Metric> result = new ArrayList<>(metrics.size());
        result.add(0, remainder_metric);

        return result;
    }

    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
        throw new UnsupportedOperationException("Identity Transform is not supposed to be used with a constant");
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.ANOMALY_STL.name();
    }

    @Override
    public List<Metric> transform(List<Metric>... listOfList) {
        throw new UnsupportedOperationException("This class is deprecated!");
    }

}

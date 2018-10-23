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
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.util.QueryContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Calculates the Holt-Winters deviation.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class HoltWintersDeviation extends HoltWintersAnalysis implements Transform {

    //~ Instance fields ******************************************************************************************************************************

    private final TSDBService _tsdbService;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new HoltWintersDeviation object.
     *
     * @param  tsdbService  The TSDB service to use to seed the data.  Cannot be null.
     */
    public HoltWintersDeviation(TSDBService tsdbService) {
        _tsdbService = tsdbService;
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public List<Metric> transform(QueryContext context, List<Metric> metrics) {
    	throw new UnsupportedOperationException("HoltWintersDeviation is not supposed to be used without a constant");
    }

    @Override
    public List<Metric> transform(QueryContext queryContext, List<Metric> metrics, List<String> constants) {
        SystemAssert.requireArgument(metrics != null, "Metrics List cannot be null");
        SystemAssert.requireArgument(constants != null && constants.size() == 4, "Constants List cannot be null and its size must be equal to 4.");

        double alpha = Double.parseDouble(constants.get(0));
        double beta = Double.parseDouble(constants.get(1));
        double gamma = Double.parseDouble(constants.get(2));
        int seasonLength = Integer.parseInt(constants.get(3));
        List<Metric> result = new ArrayList<Metric>(metrics.size());

        for (Metric metric : metrics) {
            MetricQuery oneWeekBeforeQuery = new MetricQuery(metric.getQuery());

            oneWeekBeforeQuery.setEndTimestamp(oneWeekBeforeQuery.getStartTimestamp());
            oneWeekBeforeQuery.setStartTimestamp(oneWeekBeforeQuery.getStartTimestamp() - ONE_WEEK_IN_MILLIS);

            List<Metric> metricsList = _tsdbService.getMetrics(Arrays.asList(new MetricQuery[] { oneWeekBeforeQuery })).get(oneWeekBeforeQuery);
            Metric oneWeekBeforeMetric = null;

            for (Metric m : metricsList) {
                if (metric.equals(m)) {
                    oneWeekBeforeMetric = m;
                    break;
                }
            }

            Map<Long, Double> bootstrappedDps = new TreeMap<>(metric.getDatapoints());

            if (oneWeekBeforeMetric != null) {
                bootstrappedDps.putAll(oneWeekBeforeMetric.getDatapoints());
            }

            Metric resultMetric = new Metric(metric);

            resultMetric.setDatapoints(_performHoltWintersAnalysis(bootstrappedDps, alpha, beta, gamma, seasonLength,
                    metric.getQuery().getStartTimestamp().longValue()).getDeviationDatapoints());
            result.add(resultMetric);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    @Override
    public List<Metric> transform(QueryContext queryContext, List<Metric>... listOfList) {
    	throw new UnsupportedOperationException("HoltWintersDeviation doesn't need list of list!");
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.HW_DEVIATION.getName();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

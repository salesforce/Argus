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

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Performas a Holt-Winters analysis.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class HoltWintersAnalysis {

    //~ Static fields/initializers *******************************************************************************************************************

    protected static final long ONE_WEEK_IN_MILLIS = 7 * 24 * 60 * 60 * 100;

    //~ Methods **************************************************************************************************************************************

    HoltWintersData _performHoltWintersAnalysis(Map<Long, String> bootstrappedDps, double alpha, double beta, double gamma, int seasonLength,
        long startTimestamp) {
        DecimalFormat df = new DecimalFormat("#.#####");

        df.setRoundingMode(RoundingMode.CEILING);

        List<Double> intercepts = new ArrayList<Double>();
        List<Double> slopes = new ArrayList<Double>();
        List<Double> seasonals = new ArrayList<Double>();
        List<Double> deviations = new ArrayList<Double>();
        Map<Long, String> deviationDatapoints = new TreeMap<Long, String>();
        Map<Long, String> forecastedDatapoints = new TreeMap<Long, String>();
        double next_pred = 0.0, prediction = 0.0;
        int i = 0;

        for (Map.Entry<Long, String> entry : bootstrappedDps.entrySet()) {
            Long timestamp = entry.getKey();
            String value = entry.getValue();
            double lastIntercept = 0.0;
            double lastSlope = 0.0;

            if (i == 0) {
                lastIntercept = Double.parseDouble(value);
                lastSlope = 0;

                // seed the first prediction as the first actual
                prediction = Double.parseDouble(value);
            } else {
                lastIntercept = intercepts.get(i - 1);
                lastSlope = slopes.get(i - 1);
                if (lastIntercept == 0.0) {
                    lastIntercept = Double.parseDouble(value);
                }
                prediction = next_pred;
            }

            double last_seasonal = getLast(seasonals, i, seasonLength);
            double next_last_seasonal = getLast(seasonals, i + 1, seasonLength);
            double last_seasonal_dev = getLast(deviations, i, seasonLength);
            double intercept = _holtWintersIntercept(alpha, Double.parseDouble(value), last_seasonal, lastIntercept, lastSlope);
            double slope = _holtWintersSlope(beta, intercept, lastIntercept, lastSlope);
            double seasonal = _holtWintersSeasonal(gamma, Double.parseDouble(value), intercept, last_seasonal);

            next_pred = intercept + slope + next_last_seasonal;

            double deviation = _holtWintersDeviation(gamma, Double.parseDouble(value), prediction, last_seasonal_dev);

            intercepts.add(intercept);
            slopes.add(slope);
            seasonals.add(seasonal);
            deviations.add(deviation);
            if (timestamp >= startTimestamp) {
                // forecastedDatapoints.put(timestamp, String.format("%.6g", prediction));
                // deviationDatapoints.put(timestamp, String.format("%.6g", deviation));
                forecastedDatapoints.put(timestamp, df.format(prediction));
                deviationDatapoints.put(timestamp, df.format(deviation));
            }
            i++;
        }

        HoltWintersData data = new HoltWintersData(forecastedDatapoints, deviationDatapoints);

        return data;
    }

    private double _holtWintersDeviation(double gamma, double actualValue, double prediction, double last_seasonal_dev) {
        return gamma * Math.abs(actualValue - prediction) + (1 - gamma) * last_seasonal_dev;
    }

    private double _holtWintersSeasonal(double gamma, double actualValue, double intercept, double last_seasonal) {
        return gamma * (actualValue - intercept) + (1 - gamma) * last_seasonal;
    }

    private double _holtWintersSlope(double beta, double intercept, double last_intercept, double last_slope) {
        return beta * (intercept - last_intercept) + (1 - beta) * last_slope;
    }

    private double _holtWintersIntercept(double alpha, double actualValue, double last_seasonal, double last_intercept, double last_slope) {
        return alpha * (actualValue - last_seasonal) + (1 - alpha) * (last_intercept + last_slope);
    }

    private double getLast(List<Double> list, int i, int seasonLength) {
        int j = i - seasonLength;

        if (j >= 0 && j < list.size()) {
            return list.get(j);
        }
        return 0;
    }

    //~ Inner Classes ********************************************************************************************************************************

    /**
     * Encapsulates the analysis decomposition.
     *
     * @author  Tom Valine (tvaline@salesforce.com)
     */
    static class HoltWintersData {

        private final Map<Long, String> _forecastedDatapoints;
        private final Map<Long, String> _deviationDatapoints;

        /**
         * Creates a new HoltWintersData object.
         *
         * @param  forecastedDatapoints  The forecasted data points.
         * @param  deviationDatapoints   The calculated deviation data points.
         */
        public HoltWintersData(Map<Long, String> forecastedDatapoints, Map<Long, String> deviationDatapoints) {
            this._forecastedDatapoints = forecastedDatapoints;
            this._deviationDatapoints = deviationDatapoints;
        }

        /**
         * Returns the forecasted data points.
         *
         * @return  The forecasted data points.
         */
        public Map<Long, String> getForecastedDatapoints() {
            return _forecastedDatapoints;
        }

        /**
         * Returns the deviation data points.
         *
         * @return  The deviation data points.
         */
        public Map<Long, String> getDeviationDatapoints() {
            return _deviationDatapoints;
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

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
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

import com.salesforce.dva.argus.entity.NumberOperations;

/**
 * Performs a Holt-Winters analysis.
 *
 * @author  Tom Valine (tvaline@salesforce.com)
 */
public class HoltWintersAnalysis {

    //~ Static fields/initializers *******************************************************************************************************************

    protected static final long ONE_WEEK_IN_MILLIS = 7 * 24 * 60 * 60 * 100;
    protected static final NumberFormat DECIMAL_FORMAT = getDecimalFormat();

    private static NumberFormat getDecimalFormat() {
        DecimalFormat df = (DecimalFormat) NumberFormat.getInstance(Locale.US);
        df.applyPattern("#.#####");
        df.setRoundingMode(RoundingMode.CEILING);
        return df;
    }

    //~ Methods **************************************************************************************************************************************

    HoltWintersData _performHoltWintersAnalysis(Map<Long, Number> bootstrappedDps, double alpha, double beta, double gamma, int seasonLength,
            long startTimestamp) {

        List<Number> intercepts = new ArrayList<Number>();
        List<Number> slopes = new ArrayList<Number>();
        List<Number> seasonals = new ArrayList<Number>();
        List<Number> deviations = new ArrayList<Number>();
        Map<Long, Number> deviationDatapoints = new TreeMap<>();
        Map<Long, Number> forecastedDatapoints = new TreeMap<>();
        Number next_pred = 0, prediction = 0;
        int i = 0;

        for (Map.Entry<Long, Number> entry : bootstrappedDps.entrySet()) {
            Long timestamp = entry.getKey();
            Number value = entry.getValue();
            Number lastIntercept = 0;
            Number lastSlope = 0;

            if (i == 0) {
                lastIntercept = value;
                lastSlope = 0;

                // seed the first prediction as the first actual
                prediction = value;
            } else {
                lastIntercept = intercepts.get(i - 1);
                lastSlope = slopes.get(i - 1);
                if (NumberOperations.isEqualTo(lastIntercept, 0)) {
                    lastIntercept = value;
                }
                prediction = next_pred;
            }

            Number last_seasonal = getLast(seasonals, i, seasonLength);
            Number next_last_seasonal = getLast(seasonals, i + 1, seasonLength);
            Number last_seasonal_dev = getLast(deviations, i, seasonLength);
            Number intercept = _holtWintersIntercept(alpha, value, last_seasonal, lastIntercept, lastSlope);
            Number slope = _holtWintersSlope(beta, intercept, lastIntercept, lastSlope);
            Number seasonal = _holtWintersSeasonal(gamma, value, intercept, last_seasonal);

            next_pred = NumberOperations.add(NumberOperations.add(intercept, slope), next_last_seasonal);
            
            Number deviation = _holtWintersDeviation(gamma, value, prediction, last_seasonal_dev);

            intercepts.add(intercept);
            slopes.add(slope);
            seasonals.add(seasonal);
            deviations.add(deviation);
            
            if (timestamp >= startTimestamp) {
            	Number predictionFormat = DECIMAL_FORMAT.format(prediction).contains(".") ? Double.parseDouble(DECIMAL_FORMAT.format(prediction)) :
            			Long.parseLong(DECIMAL_FORMAT.format(prediction));
            	Number deviationFormat = DECIMAL_FORMAT.format(deviation).contains(".") ? Double.parseDouble(DECIMAL_FORMAT.format(deviation)) : 
            			Long.parseLong(DECIMAL_FORMAT.format(deviation));
                forecastedDatapoints.put(timestamp, predictionFormat);
                deviationDatapoints.put(timestamp, deviationFormat);
            }
            i++;
        }

        HoltWintersData data = new HoltWintersData(forecastedDatapoints, deviationDatapoints);

        return data;
    }
	
	private Number _holtWintersDeviation(double gamma, Number value, Number prediction, Number last_seasonal_dev) {
		Number first = NumberOperations.multiply(gamma, NumberOperations.getAbsValue(NumberOperations.subtract(value, prediction)));
		Number second = NumberOperations.multiply(1 - gamma, last_seasonal_dev);
		return NumberOperations.add(first, second);
	}
    
    private Number _holtWintersSeasonal(double gamma, Number value, Number intercept, Number last_seasonal) {
		Number first = NumberOperations.multiply(gamma, NumberOperations.subtract(value, intercept));
		Number second = NumberOperations.multiply(1 - gamma, last_seasonal);
		return NumberOperations.add(first, second);
	}
    
    private Number _holtWintersSlope(double beta, Number intercept, Number lastIntercept, Number lastSlope) {
		Number first = NumberOperations.multiply(beta, NumberOperations.subtract(intercept, lastIntercept));
		Number second = NumberOperations.multiply(1 - beta, lastSlope);
		return NumberOperations.add(first, second);
	}
    
    private Number _holtWintersIntercept(double alpha, Number value, Number last_seasonal, Number lastIntercept, Number lastSlope) {
		Number first = NumberOperations.multiply(alpha, NumberOperations.subtract(value, last_seasonal));
		Number second = NumberOperations.multiply(1 - alpha, NumberOperations.add(lastIntercept, lastSlope));
		return NumberOperations.add(first, second);
	}
    
    private Number getLast(List<Number> list, int i, int seasonLength) {
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

        private final Map<Long, Number> _forecastedDatapoints;
        private final Map<Long, Number> _deviationDatapoints;

        /**
         * Creates a new HoltWintersData object.
         *
         * @param  forecastedDatapoints  The forecasted data points.
         * @param  deviationDatapoints   The calculated deviation data points.
         */
        public HoltWintersData(Map<Long, Number> forecastedDatapoints, Map<Long, Number> deviationDatapoints) {
            this._forecastedDatapoints = forecastedDatapoints;
            this._deviationDatapoints = deviationDatapoints;
        }

        /**
         * Returns the forecasted data points.
         *
         * @return  The forecasted data points.
         */
        public Map<Long, Number> getForecastedDatapoints() {
            return _forecastedDatapoints;
        }

        /**
         * Returns the deviation data points.
         *
         * @return  The deviation data points.
         */
        public Map<Long, Number> getDeviationDatapoints() {
            return _deviationDatapoints;
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.salesforce.dva.argus.service.TSDBService;

/**
 * Factory for metric transforms.
 *
 * @author  Bhinav Sura (bsura@salesforce.com) Ruofan Zhang(rzhang@salesforce.com) *
 */
@Singleton
public class TransformFactory {

    //~ Static fields/initializers *******************************************************************************************************************

    /** Default metric name. */
    static final String DEFAULT_METRIC_NAME = "result";

    //~ Instance fields ******************************************************************************************************************************

    private final TSDBService _tsdbService;

    //~ Constructors *********************************************************************************************************************************

    /**
     * Creates a new TransformFactory object.
     *
     * @param  tsdbService  The TSDB service to use.
     */
    @Inject
    public TransformFactory(TSDBService tsdbService) {
        _tsdbService = tsdbService;
    }

    //~ Methods **************************************************************************************************************************************

    /**
     * Returns a transform for the corresponding name.
     *
     * @param   functionName  The function name.
     *
     * @return  The transform corresponding to the function name.
     *
     * @throws  UnsupportedOperationException  If no transform exists for the function name.
     */
    public Transform getTransform(String functionName) {
        Function function = Function.fromString(functionName);

        switch (function) {
            case DIVIDE:
                return new MetricReducerOrMappingTransform(new DivideValueReducerOrMapping());
            case SUM:
                return new MetricReducerOrMappingTransform(new SumValueReducerOrMapping());
            case MULTIPLY:
            case SCALE:
                return new MetricReducerOrMappingTransform(new ScaleValueReducerOrMapping());
            case DIFF:
                return new MetricReducerOrMappingTransform(new DiffValueReducerOrMapping());
            case PERCENTILE:
                return new MetricReducerOrMappingWithConstantTransform(new PercentileValueReducerOrMapping());
            case DEVIATION:
                return new MetricReducerOrMappingWithConstantTransform(new DeviationValueReducerOrMapping());
            case AVERAGE:
                return new MetricReducerTransform(new AverageValueReducer());
            case MIN:
                return new MetricReducerTransform(new MinValueReducer());
            case MAX:
                return new MetricReducerTransform(new MaxValueReducer());
            case PROPAGATE:
                return new PropagateTransform();
            case UNION:
                return new MetricUnionTransform(new UnionValueUnionReducer());
            case GROUP:
                return new GroupTransformWrapUnion();
            case COUNT:
                return new CountTransformWrapUnion();
            case SORT:
                return new SortTransformWrapAboveAndBelow();
            case DOWNSAMPLE:
                return new DownsampleTransform();
            case INTEGRAL:
                return new IntegralTransform();
            case JOIN:
                return new JoinTransform();
            case LIMIT:
                return new LimitTransform();
            case RANGE:
                return new RangeTransformWrap();
            case NORMALIZE:
                return new NormalizeTransformWrap();
            case FILL:
                return new FillTransform();
            case FILL_CALCULATE:
                return new FillCalculateTransform();
            case ALIAS:
                return new AliasTransform();
            case ALIASBYTAG:
                return new AliasByTagTransform();
            case INCLUDE:
                return new IncludeTransform();
            case EXCLUDE:
                return new ExcludeTransformWrap();
            case HW_FORECAST:
                return new HoltWintersForecast(_tsdbService);
            case HW_DEVIATION:
                return new HoltWintersDeviation(_tsdbService);
            case IDENTITY:
                return new IdentityTransform();
            case ZEROIFMISSINGSUM:
                return new ZeroIfMissingSum();
            case ABOVE:
                return new MetricFilterWithInteralReducerTransform(new AboveValueFilter());
            case BELOW:
                return new MetricFilterWithInteralReducerTransform(new BelowValueFilter());
            case HIGHEST:
                return new MetricFilterWithInteralReducerTransform(new HighestValueFilter());
            case LOWEST:
                return new MetricFilterWithInteralReducerTransform(new LowestValueFilter());
            case ABSOLUTE:
                return new MetricMappingTransform(new AbsoluteValueMapping());
            case DERIVATIVE:
                return new MetricMappingTransform(new DerivativeValueMapping());
            case CULL_ABOVE:
                return new MetricMappingTransform(new CullAboveValueMapping());
            case CULL_BELOW:
                return new MetricMappingTransform(new CullBelowValueMapping());
            case CONSECUTIVE:
                return new MetricMappingTransform(new ConsecutiveValueMapping());
            case LOG:
                return new MetricMappingTransform(new LogValueMapping());
            case SHIFT:
                return new MetricMappingTransform(new ShiftValueMapping());
            case MOVING:
                return new MetricMappingTransform(new MovingValueMapping());
            case SUM_V:
                return new MetricZipperTransform(new SumValueZipper());
            case SCALE_V:
                return new MetricZipperTransform(new ScaleValueZipper());
            case DIFF_V:
                return new MetricZipperTransform(new DiffValueZipper());
            case DIVIDE_V:
                return new MetricZipperTransform(new DivideValueZipper());
            case NORMALIZE_V:
                return new MetricZipperTransform(new DivideValueZipper());
            case ANOMALY_STL:
                return new AnomalySTLTransform();
            case GROUPBY:
            	return new GroupByTransform(this);
            case GROUPBYTAG:
                return new GroupByTagTransform(this);
            case ANOMALY_DENSITY:
                return new AnomalyDetectionGaussianDensityTransform();
            case ANOMALY_ZSCORE:
                return new AnomalyDetectionGaussianZScoreTransform();
            case ANOMALY_KMEANS:
                return new AnomalyDetectionKMeansTransform();
            case ANOMALY_RPCA:
                return new AnomalyDetectionRPCATransform();
            case INTERPOLATE:
                return new InterpolateTransform();
            default:
                throw new UnsupportedOperationException(functionName);
        } // end switch
    }

    //~ Enums ****************************************************************************************************************************************

    /**
     * The list of supported time series functions.
     *
     * @author  Bhinav Sura (bsura@salesforce.com)
     */
    public enum Function {
        IDENTITY("IDENTITY", "Performs an identity transform such that input = output"),
        SUM("SUM", "Performs an addition of the input metrics"),
        DIFF("DIFF", "Difference between timeseries"),
        DIVIDE("DIVIDE", "Divides one timeseries by another"),
        ABOVE("ABOVE", "Calculates all input metrics whose set of data point values, when evaluated, are above the limit."),
        BELOW("BELOW", "Calculates all input metrics whose set of data point values, when evaluated, are below the limit."),
        HIGHEST("HIGHEST",
            "Evaluates all input metrics based on an evaluation of the metric data point values, returning top metrics having the highest evaluated value."),
        LOWEST("LOWEST",
            "Evaluates all input metrics based on an evaluation of the metric data point values, returning top metrics having the lowest evaluated value."),
        SORT("SORT", "Sorts a list of metrics."),
        MULTIPLY("MULTIPLY", "Performs the multiplication of the input metrics"),
        INTEGRAL("INTEGRAL", "Performs integral for each metric"),
        DERIVATIVE("DERIVATIVE", "Perform derivative for each metric"),
        AVERAGE("AVERAGE", "Computes average series for the given time series metrics"),
        MIN("MIN", "Finds the MIN series for the given time series metrics."),
        MAX("MAX", "Finds the MAX series for the given time series metrics."),
        AVERAGEBELOW("AVERAGEBELOW", ""),
        PERCENTILE("PERCENTILE", ""),
        SCALE("SCALE", "Scale a TS by a given amount"),
        MOVINGAVERAGE("MOVINGAVERAGE", ""),
        ZEROIFMISSINGSUM("ZEROIFMISSINGSUM", "Performs an addition of the input metrics, adding 0 to non-matching time stamps"),
        ABSOLUTE("ABSOLUTE", " Converts the data point values to their corresponding absolute value."),
        MOVING("MOVING", "Evaluates input metrics using a moving window."),
        PROPAGATE("PROPAGATE", "Forward fills gaps with the last known value at the start (earliest occurring time) of the gap.."),
        ALIAS("ALIAS", "Transforms the name of one or more metrics/scopes."),
        ALIASBYTAG("ALIASBYTAG", "Sets the display name for the metric. It uses the provided tag key to get the value for that tag and uses that to set the display name."),
        NORMALIZE("NORMALIZE", "Normalizes the data point values of time series"),
        RANGE("RANGE", "Calculate the union of unique values in the input."),
        UNION("UNION", "Performs the union of all data points for the given time series."),
        GROUP("GROUP", "Calculates the union of all data points of time series which match the regular exception"),
        COUNT("COUNT",
            "Calculates a metric having the a set of timestamps that is the union of all input metric timestamp values where each timestamp value is the constant value of the count of input metrics."),
        DOWNSAMPLE("DOWNSAMPLE", "Down samples the one or more metric."),
        CULL_ABOVE("CULL_ABOVE", "Removes data points from metrics if their evaluated value is above a limit. "),
        CULL_BELOW("CULL_BELOW", "Removes data points from metrics if their evaluated value is below a limit. "),
        SHIFT("SHIFT", "Shifts the timestamp for each data point by the specified constant."),
        LOG("LOG", "Calculates the logarithm according to the specified base."),
        JOIN("JOIN", "Joins multiple lists of metrics into a single list."),
        LIMIT("LIMIT", "Returns a subset input metrics in stable order from the head of the list not to exceed the specified limit."),
        SUM_V("SUM_V", "Calculate sum in a vector style."),
        SCALE_V("SCALE_V", "Calculate multiplication in a vector style."),
        DIFF_V("DIFF_V", "Calculate subtraction in a vector style."),
        DIVIDE_V("DIVIDE_V", "Calculate quotient in a vector style."),
        NORMALIZE_V("NORMALIZE_V", "Perform normalization in a vector style."),
        DEVIATION("DEVIATION",
            "Calculates the standard deviation per timestamp for more than one metric, or the standard deviation for all data points for a single metric."),
        FILL("FILL", "Creates additional data points to fill gaps."),
        FILL_CALCULATE("FILL_CALCULATE", "Creates a constant line based on the calculated value."),
        INCLUDE("INCLUDE", "Retains metrics based on the matching of a regular expression against the metric name."),
        EXCLUDE("EXCLUDE", "Culls metrics based on the matching of a regular expression against the metric name."),
        CONSECUTIVE("CONSECUTIVE","Filter out all values that are non-consecutive"),
        HW_FORECAST("HW_FORECAST", "Performns HoltWinters Forecast."),
        HW_DEVIATION("HW_DEVIATION", "Performns HoltWinters Deviation."),
        ANOMALY_STL("ANOMALY_STL", "Performs a seasonal trend decomposition and returns the probability that each point is an anomaly based on the residual component."),
        GROUPBY("GROUPBY", "Creates groups of metrics based on some matching criteria and then performs the given aggregation."),
        GROUPBYTAG("GROUPBYTAG", "Creates groups of metrics based on tags and then performs the given aggregation."),
        ANOMALY_DENSITY("ANOMALY_DENSITY", "Calculates an anomaly score (0-100) for each value of the metric based on the probability density of each value with a Gaussian distribution."),
        ANOMALY_ZSCORE("ANOMALY_ZSCORE", "Calculates an anomaly score (0-100) for each value of the metric based on the z-score of each value with a Gaussian distribution."),
        ANOMALY_KMEANS("ANOMALY_KMEANS", "Calculates an anomaly score (0-100) for each value of the metric based on a K-means clustering of the metric data."),
        ANOMALY_RPCA("ANOMALY_RPCA", "Calculates an anomaly score (0-100) for each value of the metric based on the RPCA matrix decomposition algorithm."),
        INTERPOLATE("INTERPOLATE", "Performs interpolation of multiple time series, that can then be used for aggregation");

        private final String _name;
        private final String _description;

        private Function(String name, String description) {
            _name = name;
            _description = description;
        }

        /**
         * Returns the function element corresponding to the name.
         *
         * @param   name  The name of the function.
         *
         * @return  The corresponding element.
         *
         * @throws  IllegalArgumentException  If no element exists for the name.
         */
        public static Function fromString(String name) {
            if (name != null) {
                for (Function func : Function.values()) {
                    if (name.equalsIgnoreCase(func.getName())) {
                        return func;
                    }
                }
            }
            throw new IllegalArgumentException(name);
        }

        /**
         * Returns the function name.
         *
         * @return  The function name.
         */
        public String getName() {
            return _name;
        }

        /**
         * Returns the function description.
         *
         * @return  The function description.
         */
        public String getDescription() {
            return _description;
        }
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

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

import com.google.common.primitives.Doubles;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.metric.MetricReader;
import com.salesforce.dva.argus.system.SystemAssert;
import org.apache.commons.math.stat.descriptive.moment.Mean;
import org.apache.commons.math.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math.stat.descriptive.summary.Sum;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Down samples the one or more metric.<br/>
 * <tt>DOWNSAMPLE(<expr>, <downsampler>)</tt>
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class DownsampleTransform implements Transform {

    //~ Methods **************************************************************************************************************************************
   
	/**
     * Implements down sampling.
     *
     * @param   values       The values to down sample.
     * @param   reducerType  The type of down sampling to perform.
     *
     * @return  The down sampled result.
     *
     * @throws  UnsupportedOperationException  If an unknown down sampling type is specified.
     */
    public static String downsamplerReducer(List<String> values, String reducerType) {
        List<Double> operands = new ArrayList<Double>();

        for (String str : values) {
            if (str == null || str.equals("")) {
                operands.add(0.0);
            } else {
                operands.add(Double.parseDouble(str));
            }
        }

        InternalReducerType type = InternalReducerType.fromString(reducerType);

        switch (type) {
            case AVG:
                return String.valueOf((new Mean()).evaluate(Doubles.toArray(operands)));
            case MIN:
                return String.valueOf(Collections.min(operands));
            case MAX:
                return String.valueOf(Collections.max(operands));
            case SUM:
                return String.valueOf((new Sum()).evaluate(Doubles.toArray(operands), 0, operands.size()));
            case DEVIATION:
                return String.valueOf((new StandardDeviation()).evaluate(Doubles.toArray(operands)));
            default:
                throw new UnsupportedOperationException(reducerType);
        }
    }

    /**
     * Creating timestamp for downsampling in order to be consistent with TSDB downsampling func on hour/minute level
     *      
     * @param   millitimestamp       original transform timestamp in milliseconds.
     * @param   timeunit  The time unit of down sampling to perform.
     *
     * @return  new timestamp.
     *
     * @throws  UnsupportedOperationException  If an unknown down sampling type is specified.
     * 
     * i.e.
     * on hour level, 01:01:30 => 01:00:00
     * on minute level, 01:01:30 => 01:01:00 
     * on second level, 01:01:30 => 01:01:30 
     */
    public static Long downsamplerTimestamp(Long millitimestamp, String unitStr) {

        InternalTimeUnit unit = InternalTimeUnit.fromString(unitStr);

        switch (unit) {
            case HOUR:
            	return TimeUnit.MILLISECONDS.toHours(millitimestamp) * 60 * 60 * 1000;                
            case MINUTE:
            	return TimeUnit.MILLISECONDS.toMinutes(millitimestamp) * 60 * 1000;                
            case SECOND:
            	return TimeUnit.MILLISECONDS.toSeconds(millitimestamp) * 1000;                            
            default:
                throw new UnsupportedOperationException(unitStr);
        }
    }
    
    private enum InternalTimeUnit { 	 

        HOUR("h"),
        MINUTE("m"),
        SECOND("s");

        /** The timeunit name. */
        public final String unit;

        //~ Constructors *********************************************************************************************************************************

        private InternalTimeUnit(String unit) {
            this.unit = unit;
        }
        
        public static InternalTimeUnit fromString(String unitStr) {
            if ( unitStr != null) {
                for (InternalTimeUnit unit : InternalTimeUnit.values()) {
                    if (unitStr.equalsIgnoreCase(unit.getUnit())) {
                        return unit;
                    }
                }
            }
            throw new IllegalArgumentException(unitStr);
        }

        //~ Methods **************************************************************************************************************************************

        /**
         * Returns the time unit.
         *
         * @return  The time unit.
         */
        public String getUnit() {
            return unit;
        }
    }
    
    //~ Methods **************************************************************************************************************************************

    @Override
    public List<Metric> transform(List<Metric> metrics) {
        throw new UnsupportedOperationException("Downsample transform need constant input!");
    }

    @Override
    public List<Metric> transform(List<Metric> metrics, List<String> constants) {
        SystemAssert.requireArgument(metrics != null, "Cannot transform empty metric/metrics");
        if (metrics.isEmpty()) {
            return metrics;
        }
        SystemAssert.requireArgument(constants.size() == 1,
            "Downsampler Transform can only have exactly one constant which is downsampler expression");
        SystemAssert.requireArgument(constants.get(0).contains("-"), "This downsampler expression is not valid.");

        String[] expArr = constants.get(0).split("-");

        SystemAssert.requireArgument(expArr.length == 2, "This downsampler expression need both unit and type.");

        // init windowSize
        String windowSizeStr = expArr[0];
        Long windowSize = getWindowInSeconds(windowSizeStr) * 1000;
        String windowUnit = windowSizeStr.substring(windowSizeStr.length() - 1);
        // init downsample type
        Set<String> typeSet = new HashSet<String>(Arrays.asList("avg", "min", "max", "sum", "dev"));
        String downsampleType = expArr[1];

        SystemAssert.requireArgument(typeSet.contains(downsampleType), "Please input a valid type.");
        for (Metric metric : metrics) {
            metric.setDatapoints(createDownsampleDatapoints(metric.getDatapoints(), windowSize, downsampleType, windowUnit));
        }
        return metrics;
    }

    private Map<Long, String> createDownsampleDatapoints(Map<Long, String> originalDatapoints, long windowSize, String type, String windowUnit) {
        Map<Long, String> downsampleDatapoints = new HashMap<Long, String>();
        Map<Long, String> sortedDatapoints = new TreeMap<Long, String>(originalDatapoints);
        List<String> values = new ArrayList<>();
        Long windowStart = 0L;

        for (Map.Entry<Long, String> entry : sortedDatapoints.entrySet()) {
            Long timestamp = entry.getKey();
            String value = entry.getValue();

            if (values.isEmpty()) {
                values.add(value);
                windowStart = downsamplerTimestamp(timestamp, windowUnit);
            } else {
                if (timestamp > windowStart + windowSize) {
                    String fillingValue = downsamplerReducer(values, type);

                    downsampleDatapoints.put(windowStart, fillingValue);
                    values.clear();
                    windowStart = downsamplerTimestamp(timestamp, windowUnit);
                }
                values.add(value);
            }
        }
        if (!values.isEmpty()) {
            String fillingValue = downsamplerReducer(values, type);

            downsampleDatapoints.put(windowStart, fillingValue);
        }
        return downsampleDatapoints;
    }

    @Override
    public String getResultScopeName() {
        return TransformFactory.Function.DOWNSAMPLE.name();
    }

    private long getWindowInSeconds(String window) {
        MetricReader.TimeUnit timeunit = null;

        try {
            timeunit = MetricReader.TimeUnit.fromString(window.substring(window.length() - 1));

            long timeDigits = Long.parseLong(window.substring(0, window.length() - 1));

            return timeDigits * timeunit.getValue() / 1000;
        } catch (Exception t) {
            throw new IllegalArgumentException("Fail to parse window size!");
        }
    }

    @Override
    public List<Metric> transform(List<Metric>... listOfList) {
        throw new UnsupportedOperationException("Downsample doesn't need list of list!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
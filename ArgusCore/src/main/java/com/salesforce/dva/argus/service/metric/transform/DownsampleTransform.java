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
import com.salesforce.dva.argus.service.tsdb.MetricScanner;
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
    public static Double downsamplerReducer(List<Double> values, String reducerType) {
        List<Double> operands = new ArrayList<Double>();
        
        for (Double value : values) {
            if (value == null) {
                operands.add(0.0);
            } else {
                operands.add(value);
            }
        }
        InternalReducerType type = InternalReducerType.fromString(reducerType);
        switch (type) {
            case AVG:
                return new Mean().evaluate(Doubles.toArray(operands));
            case MIN:
                return Collections.min(operands);
            case MAX:
                return Collections.max(operands);
            case SUM:
                return new Sum().evaluate(Doubles.toArray(operands), 0, operands.size());
            case DEVIATION:
                return new StandardDeviation().evaluate(Doubles.toArray(operands));
            case COUNT:
            	values.removeAll(Collections.singleton(null));
            	return (double) values.size();
            default:
                throw new UnsupportedOperationException(reducerType);
        }
    }
	
    public static Double downsampleReducerScanner(MetricScanner scanner, String reducerType) {
    	
    	List<Double> operands = new ArrayList<>();
    	int nonNull = 0;
    	    	
    	synchronized(scanner) {
	    	while (scanner.hasNextDP()) {
	    		Double pt = scanner.getNextDP().getValue();
	    		if (pt == null) {
	    			operands.add(0.0);
	    		}
	    		else {
	    			operands.add(pt);
	    			nonNull++;
	    		}
	    	}
    	}
  
    	InternalReducerType type = InternalReducerType.fromString(reducerType);

    	switch(type) {
    		case AVG:
    			return new Mean().evaluate(Doubles.toArray(operands));
    		case MIN:
    			return Collections.min(operands);
    		case MAX:
    			return Collections.max(operands);
    		case SUM:
    			return new Sum().evaluate(Doubles.toArray(operands), 0, operands.size());
    		case DEVIATION:
    			return new StandardDeviation().evaluate(Doubles.toArray(operands));
    		case COUNT:
    			return (double) nonNull;
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
    public static Long downsamplerTimestamp(Long millitimestamp, long windowSize) {
    	return millitimestamp-(millitimestamp%windowSize);
    }

    //~ Methods **************************************************************************************************************************************

    @Override
    public List<Metric> transform(List<Metric> metrics) {
        throw new UnsupportedOperationException("Downsample transform need constant input!");
    }
	
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners) {
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
        Set<String> typeSet = new HashSet<String>(Arrays.asList("avg", "min", "max", "sum", "dev", "count"));
        String downsampleType = expArr[1];

        SystemAssert.requireArgument(typeSet.contains(downsampleType), "Please input a valid type.");
        for (Metric metric : metrics) {
            metric.setDatapoints(createDownsampleDatapoints(metric.getDatapoints(), windowSize, downsampleType, windowUnit));
        }
        return metrics;
    }
	
    @Override
    public List<Metric> transformScanner(List<MetricScanner> scanners, List<String> constants) {
    	SystemAssert.requireArgument(scanners != null, "Cannot transform empty metric scanner/scanners");
    	List<Metric> result = new ArrayList<>();
    	if (scanners.isEmpty()) {
    		return result;
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
        Set<String> typeSet = new HashSet<String>(Arrays.asList("avg", "min", "max", "sum", "dev", "count"));
        String downsampleType = expArr[1];

        SystemAssert.requireArgument(typeSet.contains(downsampleType), "Please input a valid type.");
        
        for (MetricScanner scanner : scanners) {
        		Metric m = new Metric(scanner.getMetric());
        		m.setDatapoints(createDownsampleDatapointsScanner(scanner, windowSize, downsampleType, windowUnit));
        		result.add(m);
        }
        return result;
    }

    private Map<Long, Double> createDownsampleDatapoints(Map<Long, Double> originalDatapoints, long windowSize, String type, String windowUnit) {
        Map<Long, Double> downsampleDatapoints = new HashMap<>();
        TreeMap<Long, Double> sortedDatapoints = new TreeMap<>(originalDatapoints);
        
        if (sortedDatapoints.isEmpty()){
        	return downsampleDatapoints;
        }
        
        Long windowStart = downsamplerTimestamp(sortedDatapoints.firstKey(),windowSize);

        List<Double> values = new ArrayList<>();
        for (Map.Entry<Long, Double> entry : sortedDatapoints.entrySet()) {
            Long timestamp = entry.getKey();
            Double value = entry.getValue();

            if (values.isEmpty()) {
                values.add(value);
            } else {
                if (timestamp >= windowStart + windowSize) {
                    Double fillingValue = downsamplerReducer(values, type);
                    downsampleDatapoints.put(windowStart, fillingValue);
                    values.clear();
                    windowStart = downsamplerTimestamp(timestamp, windowSize);
                }
                values.add(value);
            }
        }
        if (!values.isEmpty()) {
            Double fillingValue = downsamplerReducer(values, type);
            downsampleDatapoints.put(windowStart, fillingValue);
        }
        return downsampleDatapoints;
    }
	
    private Map<Long, Double> createDownsampleDatapointsScanner(MetricScanner scanner, long windowSize, String type, String windowUnit) {
	    	Map<Long, Double> downsampleDatapoints = new HashMap<>();
	    	Map.Entry<Long, Double>  firstDP = null;
	    	
	    	synchronized(scanner) {
		    	if (!scanner.hasNextDP()) {
		    		return downsampleDatapoints;
		    	}
		    	
		    	firstDP = scanner.getNextDP();
	    	}
	    	
	    	Long windowStart = downsamplerTimestamp(firstDP.getKey(), windowSize);
	    	List<Double> values = new ArrayList<>();
	    	values.add(firstDP.getValue());	// just add the first one ,  we know it will be empty this time
	    	
	    	synchronized(scanner) {
		    	while (scanner.hasNextDP()) {
		    		Map.Entry<Long, Double> dp = scanner.getNextDP();
		    		if (values.isEmpty()) {
		    			values.add(dp.getValue());
		    		}
		    		else {
			    		if (dp.getKey() >= windowStart + windowSize) {
			    			Double fillingValue = downsamplerReducer(values, type);
			    			downsampleDatapoints.put(windowStart, fillingValue);
			    			values.clear();
			    			windowStart = downsamplerTimestamp(dp.getKey(), windowSize);
			    		}
			    		values.add(dp.getValue());
		    		}
		    	}
	    	}
	    	if (!values.isEmpty()) {
	    		Double fillingValue = downsamplerReducer(values, type);
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

    @Override
    public List<Metric> transformScanner(List<MetricScanner>... listOfList) {
        throw new UnsupportedOperationException("Downsample doesn't need list of list!");
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

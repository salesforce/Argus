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
import com.salesforce.dva.argus.util.QueryContext;
import com.salesforce.dva.argus.util.QueryUtils;
import com.salesforce.dva.argus.util.TransformUtil;

import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.descriptive.rank.Percentile;
import org.apache.commons.math3.stat.descriptive.summary.Sum;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Down samples the one or more metric.<br>
 * <tt>DOWNSAMPLE(&lt;expr&gt;, &lt;downsampler&gt;)</tt>
 *
 * @author  Ruofan Zhang (rzhang@salesforce.com)
 */
public class DownsampleTransform implements Transform {

	//~ Methods **************************************************************************************************************************************

	public static final String ABSOLUTE = "abs";

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
		case PERCENTILE:
			return new Percentile().evaluate(Doubles.toArray(operands), Double.parseDouble(reducerType.substring(1)));
		default:
			throw new UnsupportedOperationException("Illegal type: " + reducerType + ". Please provide a valid type.");
		}
	}

	/**
	 * Creating timestamp for downsampling in order to be consistent with TSDB downsampling func on hour/minute level
	 *
	 * @param   millitimestamp       original transform timestamp in milliseconds.
	 * @param windowSize             Downsample window size
	 * @return  new timestamp.
	 *
	 * @throws  UnsupportedOperationException  If an unknown down sampling type is specified.
	 *
	 * i.e.
	 * on hour level, 01:01:30 =&gt; 01:00:00
	 * on minute level, 01:01:30 =&gt; 01:01:00
	 * on second level, 01:01:30 =&gt; 01:01:30
	 */
	public static Long downsamplerTimestamp(Long millitimestamp, long windowSize) {
		return millitimestamp-(millitimestamp%windowSize);
	}

	//~ Methods **************************************************************************************************************************************

	@Override
	public List<Metric> transform(QueryContext context, List<Metric> metrics) {
		throw new UnsupportedOperationException("Downsample transform need constant input!");
	}

	@Override
	public List<Metric> transform(QueryContext queryContext, List<Metric> metrics, List<String> constants) {
		SystemAssert.requireArgument(metrics != null, "Cannot transform null metrics");

		if (metrics.isEmpty()) {
			return metrics;
		}

		SystemAssert.requireArgument(constants.size()>=1, "Downsample transform needs atleast one constant to specify the rollup interval and aggregator");
		SystemAssert.requireArgument(constants.size()<=4, "Downsampler Transform can have a maximum of 4 constants");
		SystemAssert.requireArgument(constants.get(0).contains("-"), "The first constant in the downsample expression is in the wrong format. The format should be like e.g. #1h-sum#");

		String[] expArr = constants.get(0).split("-");

		SystemAssert.requireArgument(expArr.length == 2, "The first constant in the downsample expression needs to include both unit and type like e.g. #1h-sum#");

		if(constants.size()==2) {
			SystemAssert.requireArgument(!constants.get(1).toLowerCase().equals(ABSOLUTE), "ABS constant cannot be specified without specifying a default value constant before it");
		}
		
		if(constants.size()==3) {
			SystemAssert.requireArgument(constants.get(2).toLowerCase().equals(ABSOLUTE), "ABS has to be specified as the last argument when there are 3 constants for the downsampling function");
		}

		Long startTime = null;
		Long endTime = null;
		Double defaultValue = null;
		boolean useAbsInterval = false;
		if(constants.size()==4) {
			// calculating till end of current minute to be more in line with how opentsdb does downsampling
			long currMinuteEndTime = 60*1000*(System.currentTimeMillis()/(60*1000)) + 60*1000;
			startTime = MetricReader.getTime(currMinuteEndTime, constants.get(1));
			endTime = MetricReader.getTime(currMinuteEndTime, constants.get(2));
			defaultValue = Double.parseDouble(constants.get(3));
		}else if(constants.size()==2 || constants.size()==3){
			Long[] startAndEndTimes = QueryUtils.getStartAndEndTimesWithMaxInterval(queryContext);
			startTime = startAndEndTimes[0];
			endTime = startAndEndTimes[1];
			defaultValue = Double.parseDouble(constants.get(1));
			if(constants.size()==3 && constants.get(2).toLowerCase().equals(ABSOLUTE)) {
				useAbsInterval = true;
			}
		}
		// init windowSize
		String windowSizeStr = expArr[0];
		Long windowSize = TransformUtil.getWindowInSeconds(windowSizeStr) * 1000;
		String windowUnit = windowSizeStr.substring(windowSizeStr.length() - 1);
		String downsampleType = expArr[1];

		for (Metric metric : metrics) {
			metric.setDatapoints(createDownsampleDatapoints(metric.getDatapoints(), windowSize, downsampleType, windowUnit, startTime, endTime, defaultValue, useAbsInterval));
		}
		return metrics;
	}

	private Map<Long, Double> createDownsampleDatapoints(Map<Long, Double> originalDatapoints, long windowSize, String type, String windowUnit, Long startTime, Long endTime, Double defaultValue, boolean useAbsInterval) {
		Map<Long, Double> downsampleDatapoints = new HashMap<>();

		if (originalDatapoints==null || originalDatapoints.isEmpty()){
			return downsampleDatapoints;
		}

		Long windowStart = null;
		if(startTime==null) {
			TreeMap<Long, Double> sortedDatapoints = new TreeMap<>(originalDatapoints);
			windowStart = getWindowStartTime(sortedDatapoints.firstKey(),windowUnit,windowSize, useAbsInterval);
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
						windowStart = getWindowStartTime(windowStart, timestamp, windowSize); 
					}
					values.add(value);
				}
			}
			if (!values.isEmpty()) {
				Double fillingValue = downsamplerReducer(values, type);
				downsampleDatapoints.put(windowStart, fillingValue);
			}
		}else {
			
			List<Long> sortedTimeStamps =new ArrayList<Long>(originalDatapoints.keySet());
			Collections.sort(sortedTimeStamps);
			long firstTimeStampMinute = 60*1000*(sortedTimeStamps.get(0)/(60*1000));
			if(firstTimeStampMinute<startTime) {
				// this can happen due to difference in when the opentsdb query executed vs when this downsample transform is being applied
				long timeDrift = startTime - firstTimeStampMinute;
				startTime -= timeDrift;
				endTime -= timeDrift;
			}
			windowStart = getWindowStartTime(startTime, windowUnit, windowSize, useAbsInterval);
			List<Double> values = new ArrayList<>();
			while(windowStart<endTime) {
				long currWindowEndTime = windowStart + windowSize;
				values = new ArrayList<>();
				for(long timestamp : sortedTimeStamps) {
					if(timestamp >= windowStart && (timestamp < currWindowEndTime || timestamp==endTime)) {
						values.add(originalDatapoints.get(timestamp));
					}
				}
				if (!values.isEmpty()) {
					Double fillingValue = downsamplerReducer(values, type);
					downsampleDatapoints.put(windowStart, fillingValue);
				}else {
					downsampleDatapoints.put(windowStart, defaultValue);
				}
				windowStart+=windowSize;
			}
		}
		return downsampleDatapoints;
	}

	private long getWindowStartTime(long previousStartTime, long firstDatapoint, long windowSize){
		long result=previousStartTime;
		while(firstDatapoint>=(result+windowSize)){
			result+=windowSize;
		}
		return result;
	}

	private long getWindowStartTime(long time, String windowUnit, long windowSize, boolean useAbsInterval){
		if(useAbsInterval) {
			return time;
		}
		switch (windowUnit) {
		case "m":
			return truncateTimeField(time, Calendar.SECOND);
		case "h":
			return truncateTimeField(time, Calendar.MINUTE);
		case "d":
			return truncateTimeField(time, Calendar.HOUR_OF_DAY);
		default:
			return truncateTimeField(time, Calendar.MILLISECOND);
		}
	}

	@Override
	public String getResultScopeName() {
		return TransformFactory.Function.DOWNSAMPLE.name();
	}

	@Override
	public List<Metric> transform(QueryContext queryContext, List<Metric>... listOfList) {
		throw new UnsupportedOperationException("Downsample doesn't need list of list!");
	}

	private long truncateTimeField(long time, int field){
		long result, secondOffset=60, minuteOffset=60*secondOffset, HourOffset=24*minuteOffset;

		result=time/1000;
		switch(field){
		case Calendar.SECOND:
			result=result-(result%secondOffset);
			break;
		case Calendar.MINUTE:
			result=result-(result%minuteOffset);
			break;
		case Calendar.HOUR:
		case Calendar.HOUR_OF_DAY:
			result=result-(result%HourOffset);
		}

		return result*1000;
	}
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
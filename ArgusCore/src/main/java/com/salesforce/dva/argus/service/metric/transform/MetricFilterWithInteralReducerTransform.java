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
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.util.QueryContext;

import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Filter transform is used by transform functions which cull the metric based on the result of evaluation against their datapoints.
 *
 * <p>So far, this transform funcs use this filter ABOVE, BELOW, HIGHEST, LOWEST, SORT, DOWNSAMPLER</p>
 *
 * <p>evaluations covers: ABOVE/BELOW/HIGHEST/LOWEST: average, min, max, recent SORT : maxima, minima, name, dev DOWNSAMPLER : avg, min, max, sum, dev
 * </p>
 *
 * @author  Ruofan Zhang(rzhang@salesforce.com)
 */
public class MetricFilterWithInteralReducerTransform implements Transform {

	//~ Instance fields ******************************************************************************************************************************

	private final ValueFilter valueFilter;
	private final String defaultScope;

	//~ Constructors *********************************************************************************************************************************

	/**
	 * Creates a new ReduceTransform object.
	 *
	 * @param  valueFilter  valueUnionReducer valueReducerOrMapping The valueMapping.
	 */
	protected MetricFilterWithInteralReducerTransform(ValueFilter valueFilter) {
		this.valueFilter = valueFilter;
		this.defaultScope = valueFilter.name();
	}

	//~ Methods **************************************************************************************************************************************

	/**
	 * Reduces the give metric to a single value based on the specified reducer.
	 *
	 * @param   metric       The metric to reduce.
	 * @param   reducerType  The type of reduction to perform.
	 *
	 * @return  The reduced value.
	 *
	 * @throws  UnsupportedOperationException  If an unknown reducer type is specified.
	 */
	public static String internalReducer(Metric metric, String reducerType) {
		Map<Long, Double> sortedDatapoints = new TreeMap<>();
		List<Double> operands = new ArrayList<Double>();

		if(!reducerType.equals(InternalReducerType.NAME.getName())) {
			if(metric.getDatapoints()!=null && metric.getDatapoints().size()>0) {
				sortedDatapoints.putAll(metric.getDatapoints());
				for (Double value : sortedDatapoints.values()) {

					if (value == null) {
						operands.add(0.0);
					} else {
						operands.add(value);
					}
				}
			}else {
				return null;
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
		case RECENT:
			return String.valueOf(operands.get(operands.size() - 1));
		case MAXIMA:
			return String.valueOf(Collections.max(operands));
		case MINIMA:
			return String.valueOf(Collections.min(operands));
		case NAME:
			return metric.getMetric();
		case DEVIATION:
			return String.valueOf((new StandardDeviation()).evaluate(Doubles.toArray(operands)));
		default:
			throw new UnsupportedOperationException(reducerType);
		}
	}

	/**
	 * Sorts a metric.
	 *
	 * @param   map  The metrics to sort.
	 * @param reducerType 	Sort key / reducer to use
	 * @return  The sorted metrics.
	 */
	public static Map<Metric, String> sortByValue(Map<Metric, String> map, final String reducerType) {
		List<Map.Entry<Metric, String>> list = new LinkedList<>(map.entrySet());

		Collections.sort(list, new Comparator<Map.Entry<Metric, String>>() {

			@Override
			public int compare(Map.Entry<Metric, String> o1, Map.Entry<Metric, String> o2) {
				if(reducerType.equals("name")) {
					return o1.getValue().compareTo(o2.getValue());
				}

				Double d1 = Double.parseDouble(o1.getValue());
				Double d2 = Double.parseDouble(o2.getValue());
				return (d1.compareTo(d2));
			}
		});

		Map<Metric, String> result = new LinkedHashMap<>();

		for (Map.Entry<Metric, String> entry : list) {
			result.put(entry.getKey(), entry.getValue());
		}
		return result;
	}

	//~ Methods **************************************************************************************************************************************

	@Override
	public String getResultScopeName() {
		return defaultScope;
	}

	@Override
	public List<Metric> transform(QueryContext context, List<Metric> metrics) {
		throw new UnsupportedOperationException("Filter transform need constant input!");
	}

	@Override
	public List<Metric> transform(QueryContext queryContext, List<Metric> metrics, List<String> constants) {
		SystemAssert.requireArgument(metrics != null, "Cannot transform empty metric/metrics");
		if (metrics.isEmpty()) {
			return metrics;
		}
		SystemAssert.requireArgument(!constants.isEmpty(), "Filter Transform must provide at least limit");
		if (constants.size() == 1) {
			constants.add(InternalReducerType.AVG.reducerName);
		}

		String limit = constants.get(0);
		String type = constants.get(1);
		Map<Metric, String> extendedSortedMap = createExtendedMap(metrics, type);
		List<Metric> filteredMetricList = this.valueFilter.filter(extendedSortedMap, limit);

		return filteredMetricList;
	}

	private Map<Metric, String> createExtendedMap(List<Metric> metrics, String type) {
		Map<Metric, String> extendedSortedMap = new HashMap<Metric, String>();

		for (Metric metric : metrics) {
			String extendedEvaluation = internalReducer(metric, type);

			if(extendedEvaluation!=null) {
				extendedSortedMap.put(metric, extendedEvaluation);
			}
		}
		return sortByValue(extendedSortedMap, type);
	}

	@Override
	public List<Metric> transform(QueryContext queryContext, List<Metric>... listOfList) {
		throw new UnsupportedOperationException("Filter doesn't need list of list!");
	}
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

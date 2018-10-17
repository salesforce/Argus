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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.util.QueryContext;

/**
 * A general purpose metric reducer transform. Metrics are reduced based on the ValueReducer.
 *
 * @author  Seamus Carroll (seamus.carroll@salesforce.com)
 */
public class MetricReducerTransform implements Transform {

	//~ Instance fields ******************************************************************************************************************************

	private final ValueReducer valueReducer;
	private final String defaultScope;
	private final String defaultMetricName;

	//~ Constructors *********************************************************************************************************************************

	/**
	 * Creates a new ReduceTransform object.
	 *
	 * @param  valueReducer  The valueReducer.
	 */
	protected MetricReducerTransform(ValueReducer valueReducer) {
		this.valueReducer = valueReducer;
		this.defaultScope = valueReducer.name();
		this.defaultMetricName = TransformFactory.DEFAULT_METRIC_NAME;
	}

	//~ Methods **************************************************************************************************************************************

	@Override
	public List<Metric> transform(QueryContext context, List<Metric> metrics) {
		return Arrays.asList(reduce(metrics));
	}

	@Override
	public String getResultScopeName() {
		return defaultScope;
	}

	/**
	 * O(n * m), where n is the max number of data points and m is the number of metrics. This becomes O(n) as the number of data points becomes
	 * large.
	 *
	 * @param   metrics  The list of metrics to reduce.
	 *
	 * @return  The reduced metric.
	 */
	private Metric reduce(List<Metric> metrics) {
		SystemAssert.requireArgument(metrics != null, "Cannot transform empty metric/metrics");

		/*
		 * if (metrics.isEmpty()) { return new Metric(defaultScope, defaultMetricName); }
		 */
		MetricDistiller distiller = new MetricDistiller();

		distiller.distill(metrics);

		Map<Long, List<Double>> collated = collate(metrics);
		Map<Long, Double> minDatapoints = reduce(collated);
		String newMetricName = distiller.getMetric() == null ? defaultMetricName : distiller.getMetric();
		String newScopeName = distiller.getScope() == null ? defaultScope : distiller.getScope();
		Metric newMetric = new Metric(newScopeName, newMetricName);

		newMetric.setDisplayName(distiller.getDisplayName());
		newMetric.setUnits(distiller.getUnits());
		newMetric.setTags(distiller.getTags());
		newMetric.setDatapoints(minDatapoints);
		return newMetric;
	}

	/*
	 * Collate all datapoint values for a given timestamp 
	 */
	private Map<Long, List<Double>> collate(List<Metric> metrics) {
		Map<Long, List<Double>> collated = new HashMap<>();

		for (Metric metric : metrics) {
			for (Map.Entry<Long, Double> point : metric.getDatapoints().entrySet()) {
				if (!collated.containsKey(point.getKey())) {
					collated.put(point.getKey(), new ArrayList<Double>());
				}
				collated.get(point.getKey()).add(point.getValue());
			}
		}
		return collated;
	}

	private Map<Long, Double> reduce(Map<Long, List<Double>> collated) {
		Map<Long, Double> reducedDatapoints = new HashMap<>();

		for (Map.Entry<Long, List<Double>> entry : collated.entrySet()) {
			reducedDatapoints.put(entry.getKey(), this.valueReducer.reduce(entry.getValue()));
		}
		return reducedDatapoints;
	}

	@Override
	public List<Metric> transform(QueryContext queryContext, List<Metric> metrics, List<String> constants) {
		throw new UnsupportedOperationException("Metric Reducer Transform is not supposed to be used with a constant");
	}

	@Override
	public List<Metric> transform(QueryContext queryContext, List<Metric>... listOfList) {
		throw new UnsupportedOperationException("Reducer doesn't need list of list!");
	}
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

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
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.util.QueryContext;
import com.salesforce.dva.argus.util.TransformUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

/**
 * Implementation of Derivative transform.
 *
 * @author  Raj Sarkapally (rsarkapally@salesforce.com)
 */
public class DerivativeTransform implements Transform {

	//~ Methods **************************************************************************************************************************************

	@Override
	public List<Metric> transform(QueryContext context, List<Metric> metrics) {
		SystemAssert.requireArgument(metrics != null, "Cannot transform null metric/metrics");
		return computeDerivedValues(metrics, -1L);
	}

	@Override
	public List<Metric> transform(QueryContext queryContext, List<Metric> metrics, List<String> constants) {
		SystemAssert.requireArgument(metrics != null, "Cannot transform null metric/metrics");
		SystemAssert.requireArgument(constants.size() == 1,
				"Derivative Transform can have exactly one constant");
		String intervalSizeStr = constants.get(0);
		Long intervalSizeInSeconds = TransformUtil.getWindowInSeconds(intervalSizeStr) * 1000;
		return computeDerivedValues(metrics, intervalSizeInSeconds);
	}

	private List<Metric> computeDerivedValues(List<Metric> metrics, Long intervalWidth){
		List<Metric> result = new ArrayList<>(metrics.size());

		for (Metric metric : metrics) {
			Map<Long, Double> sortedDatapoints = new TreeMap<>();

			sortedDatapoints.putAll(metric.getDatapoints());

			Map<Long, Double> derivativeDatapoints = new HashMap<>();
			Entry<Long, Double> prevEntry = null;

			for (Entry<Long, Double> entry : sortedDatapoints.entrySet()) {

				if (prevEntry == null) {
					continue;
				} else {
					if(intervalWidth<=0) {
					    derivativeDatapoints.put(entry.getKey(), entry.getValue() - prevEntry.getValue());
					}else {
						derivativeDatapoints.put(entry.getKey(), ((entry.getValue() - prevEntry.getValue())*intervalWidth)/(entry.getKey()-prevEntry.getKey()));
					}
				}
				prevEntry = entry;
			}
			metric.setDatapoints(derivativeDatapoints);
			result.add(metric);
		}
		return result;
	}
	
	

	@Override
	public String getResultScopeName() {
		return TransformFactory.Function.DERIVATIVE.name();
	}

	@Override
	public List<Metric> transform(QueryContext queryContext, List<Metric>... listOfList) {
		throw new UnsupportedOperationException("This class is deprecated!");
	}
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
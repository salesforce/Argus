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

package com.salesforce.dva.argus.service;

import com.salesforce.dva.argus.entity.Annotation;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.metric.transform.Transform;
import com.salesforce.dva.argus.service.tsdb.AnnotationQuery;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Provides methods to read, write and modify TSDB entity data.
 *
 * @author  Tom Valine (tvaline@salesforce.com), Bhinav Sura (bhinav.sura@salesforce.com)
 */
public interface TSDBService extends Service {

	//~ Methods **************************************************************************************************************************************

	/**
	 * Writes metric data. Any existing data is overwritten.
	 *
	 * @param  metrics  The list of metrics to write. Cannot be null, but may be empty.
	 */
	void putMetrics(List<Metric> metrics);

	/**
	 * Reads metric data.
	 *
	 * @param   queries  The list of queries to execute. Cannot be null, but may be empty.
	 *
	 * @return  The query results as a map of query to the corresponding metrics it returns. Will never be null, but may be empty.
	 */
	Map<MetricQuery, List<Metric>> getMetrics(List<MetricQuery> queries);

	/**
	 * Writes annotation data. Any existing data is overwritten.
	 *
	 * @param  annotations  The list of annotations to write. Cannot be null, but may be empty.
	 */
	void putAnnotations(List<Annotation> annotations);

	/**
	 * Reads annotation data.
	 *
	 * @param   queries  The list of queries to execute. Cannot be null, but may be empty.
	 *
	 * @return  The query results. Will never be null, but may be empty.
	 */
	List<Annotation> getAnnotations(List<AnnotationQuery> queries);

	static void collate(List<Metric> metrics) {

		Map<String, Metric> collated = new HashMap<>();

		for(Metric m : metrics) {
			if(!collated.containsKey(m.getIdentifier())) {
				collated.put(m.getIdentifier(), m);
			} else {
				collated.get(m.getIdentifier()).addDatapoints(m.getDatapoints());
			}
		}

		metrics.clear();
		metrics.addAll(collated.values());
	}

	static Map<String, List<Metric>> groupMetricsForAggregation(List<Metric> metrics, MetricQuery query) {

		Set<String> queryTags = query.getTags().keySet();
		final Map<String, List<Metric>> groupedMetricsMap = new HashMap<>();

		for(Metric m : metrics) {

			Map<String, String> tags = new TreeMap<>(m.getTags());

			StringBuilder sb = new StringBuilder();
			for(Map.Entry<String, String> entry : tags.entrySet()) {
				if(queryTags.contains(entry.getKey())) {
					sb.append(entry.getValue());
				}
			}

			if(!groupedMetricsMap.containsKey(sb.toString())) {
				List<Metric> groupedMetrics = new ArrayList<>();
				groupedMetricsMap.put(sb.toString(), groupedMetrics);
			}

			groupedMetricsMap.get(sb.toString()).add(m);

		}

		return groupedMetricsMap;
	}

	static List<Metric> aggregate(Map<String, List<Metric>> groupedMetricsMap, Transform transform) {

		final List<Metric> aggregated = new ArrayList<>(groupedMetricsMap.size());

		for(Map.Entry<String, List<Metric>> entry : groupedMetricsMap.entrySet()) {
			aggregated.addAll(_aggregate(entry.getValue(), transform));
		}

		return aggregated;
	}

	static List<Metric> _aggregate(List<Metric> metrics, Transform transform) {
		if(metrics.size() == 1) {
			//Nothing to aggregate
			return metrics;
		}


		return transform.transform(null, metrics);
	}

	static void downsample(MetricQuery query, List<Metric> metrics, Transform downsampleTransform) {
		if(query.getDownsampler() == null) {
			return;
		}

		String downsampler = query.getDownsamplingPeriod() / 1000 + "s-" + query.getDownsampler().getDescription();
		metrics = downsampleTransform.transform(null, metrics, Arrays.asList(downsampler));
	}    

	/**
	 * Enumeration of time window for a query
	 *
	 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
	 */
	public static enum QueryTimeWindow {

		WITHIN_24_HRS("within_24_hrs"),
		WITHIN_24_HRS_AND_30_DAYS("within_24_hrs_and_30_days"),
		GREATER_THAN_30_DAYS("greater_than_30_days");

		private String _name;

		QueryTimeWindow(String name) {
			setName(name);
		}

		public String getName() {
			return _name;
		}

		public void setName(String _description) {
			this._name = _description;
		}

		public static String getWindow(long differenceInMillis) {
			if(differenceInMillis <= 86400000L) {
				return QueryTimeWindow.WITHIN_24_HRS.getName();
			} else if(differenceInMillis > 2592000000L) {
				return QueryTimeWindow.GREATER_THAN_30_DAYS.getName();
			} else {
				return QueryTimeWindow.WITHIN_24_HRS_AND_30_DAYS.getName();
			}
		}
	}


	/**
	 * Enumeration of number of expanded time series for a query
	 *
	 * @author  Dilip Devaraj (ddevaraj@salesforce.com)
	 */
	public static enum QueryTimeSeriesExpansion {
		TS_0("ts_0"),
		TS_1("ts_1"),
		TS_2_10("ts_2-10"),
		TS_11_100("ts_11-100"),
		TS_101_1000("ts_101-1000"),
		TS_1001_10000("ts_1001-10000"),
		TS_GREATER_THAN_10000("ts_greater_than_10000");

		private String _name;

		QueryTimeSeriesExpansion(String name) {
			setName(name);
		}

		public String getName() {
			return _name;
		}

		public void setName(String _description) {
			this._name = _description;
		}

		public static String getExpandedTimeSeriesRange(int numExpandedTimeSeries) {
			if(numExpandedTimeSeries == 0) {
				return QueryTimeSeriesExpansion.TS_0.getName();
			} else if(numExpandedTimeSeries == 1) {
				return QueryTimeSeriesExpansion.TS_1.getName();
			} else if (numExpandedTimeSeries >= 2 && numExpandedTimeSeries <=10) {
				return QueryTimeSeriesExpansion.TS_2_10.getName();
			} else if (numExpandedTimeSeries >= 11 && numExpandedTimeSeries <=100) {
				return QueryTimeSeriesExpansion.TS_11_100.getName();
			} else if (numExpandedTimeSeries >= 101 && numExpandedTimeSeries <=1000) {
				return QueryTimeSeriesExpansion.TS_101_1000.getName();
			} else if (numExpandedTimeSeries >= 1001 && numExpandedTimeSeries <=10000) {
				return QueryTimeSeriesExpansion.TS_1001_10000.getName();
			} else{
				return QueryTimeSeriesExpansion.TS_GREATER_THAN_10000.getName();
			}
		}
	}
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

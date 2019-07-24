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
import com.salesforce.dva.argus.entity.Histogram;
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
public interface TSDBService extends AnnotationStorageService, MetricStorageService {

	public static final long MILLIS_IN_A_DAY = 86400000L;
	
	public static final long METRICS_RETENTION_PERIOD_MILLIS = 400*MILLIS_IN_A_DAY;

	//~ Methods **************************************************************************************************************************************

	/**
	 * Writes histogram data. Any existing data is overwritten.
	 *
	 * @param  histograms  The list of histograms to write. Cannot be null, but may be empty.
	 */
	void putHistograms(List<Histogram> histograms);	



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

			Map<String, String> tags = m.getTags();

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
	 * Enumeration of time window for a query
	 *
	 * @author  Sundeep Tiyyagura (stiyyagura@salesforce.com)
	 */
	public static enum QueryStartTimeWindow {

		WITHIN_24_HRS("within_24_hrs", 0L, MILLIS_IN_A_DAY),
		WITHIN_24_HRS_AND_7_DAYS("within_24_hrs_and_7_days", MILLIS_IN_A_DAY+1, 7*MILLIS_IN_A_DAY),
		WITHIN_8_DAYS_AND_14_DAYS("within_8_days_and_14_days", 7*MILLIS_IN_A_DAY+1, 14*MILLIS_IN_A_DAY),
		WITHIN_15_DAYS_AND_30_DAYS("within_15_days_and_30_days", 14*MILLIS_IN_A_DAY+1, 30*MILLIS_IN_A_DAY),
		WITHIN_31_DAYS_AND_90_DAYS("within_31_days_and_90_days", 30*MILLIS_IN_A_DAY+1, 90*MILLIS_IN_A_DAY),
		GREATER_THAN_90_DAYS("greater_than_90_days", 90*MILLIS_IN_A_DAY +1, 600*MILLIS_IN_A_DAY);

		private String _name;

		private long _startMillis;

		private long _endMillis;

		QueryStartTimeWindow(String name, long startMillis, long endMillis) {
			this._name = name;
			this._startMillis = startMillis;
			this._endMillis = endMillis;
		}

		public String getName() {
			return _name;
		}

		public long getStartMillis() {
			return _startMillis;
		}

		public long getEndMillis() {
			return _endMillis;
		}

		public static String getWindow(long windowInMillis) {

			for(QueryStartTimeWindow window : QueryStartTimeWindow.values()) {
				if(windowInMillis>=window.getStartMillis() && windowInMillis<=window.getEndMillis()) {
					return window.getName();
				}
			}
			return GREATER_THAN_90_DAYS.getName();
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

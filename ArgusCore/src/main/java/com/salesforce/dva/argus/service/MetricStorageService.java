package com.salesforce.dva.argus.service;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;

import java.util.List;
import java.util.Map;

/**
 * Provides methods for putting or retrieving metrics from storage.
 *
 * @author  Sudhanshu Bahety (sudhanshu.bahety@salesforce.com)
 */
public interface MetricStorageService extends Service {
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
}
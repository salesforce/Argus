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

package com.salesforce.dva.argus.service.metric;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.DefaultService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.MonitorService.Counter;
import com.salesforce.dva.argus.service.TSDBService.QueryStartTimeWindow;
import com.salesforce.dva.argus.service.TSDBService.QueryTimeSeriesExpansion;
import com.salesforce.dva.argus.service.TSDBService.QueryTimeWindow;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;
import com.salesforce.dva.argus.util.QueryContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Default implementation of the metric service.
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com)
 * @author	Sudhanshu Bahety (sudhanshu.bahety@salesforce.com)
 */
public class DefaultMetricService extends DefaultService implements MetricService {

	//~ Instance fields ******************************************************************************************************************************
	private final Logger _logger = LoggerFactory.getLogger(DefaultMetricService.class);
	private final MonitorService _monitorService;
	private final Provider<MetricReader<Metric>> _metricReaderProviderForMetrics;
	private final Provider<MetricReader<MetricQuery>> _metricReaderProviderForQueries;
	private final SystemConfiguration _configuration;
	private MetricQueryProcessor _queryProcessor;
	private String dcListRegex;

	//~ Constructors *********************************************************************************************************************************

	/**
	 * Creates a new DefaultMetricService object.
	 *
	 * @param  monitorService   The monitor service instance to use. Cannot be null.
	 * @param  queryProcessor   The metric query processor used to evaluate queries
	 * @param  metricsprovider  The metric reader provider used to perform metric operations.  Cannot be null.
	 * @param  queryprovider    The metric reader provider used to construct metric queries without fetching data. Cannot be null.
	 * @param  config           The system configuration.  Cannot be null.
	 */
	@Inject
	public DefaultMetricService(MonitorService monitorService, MetricQueryProcessor queryProcessor, Provider<MetricReader<Metric>> metricsprovider,
			Provider<MetricReader<MetricQuery>> queryprovider, SystemConfiguration config) {
		super(config);
		requireArgument(monitorService != null, "Monitor service cannot be null.");
		_monitorService = monitorService;
		_metricReaderProviderForMetrics = metricsprovider;
		_metricReaderProviderForQueries = queryprovider;
		_configuration = config;
		_queryProcessor = queryProcessor;
		dcListRegex = _configuration.getValue(com.salesforce.dva.argus.system.SystemConfiguration.Property.DC_LIST).replaceAll(",", "|");
	}

	//~ Methods **************************************************************************************************************************************

	@Override
	public MetricQueryResult getMetrics(String expression) {
		requireNotDisposed();
		return getMetrics(expression, System.currentTimeMillis());
	}

	@Override
	public MetricQueryResult getMetrics(String expression, long relativeTo) {
		requireNotDisposed();
		return getMetrics(Arrays.asList(new String[] { expression }), relativeTo);
	}

	@Override
	public MetricQueryResult getMetrics(List<String> expressions) {
		requireNotDisposed();
		return getMetrics(expressions, System.currentTimeMillis());
	}

	@Override
	public MetricQueryResult getMetrics(List<String> expressions, long relativeTo) {
		requireNotDisposed();
		SystemAssert.requireArgument(MetricReader.isValid(expressions), "Illegal metric expression found: " + expressions);

		final long start = System.currentTimeMillis();
		MetricReader<Metric> reader = _metricReaderProviderForMetrics.get();
		MetricQueryResult queryResult = new MetricQueryResult();
		try {
			for (String expression : expressions) {
				_logger.debug("Reading metric for expression {}", expression);
				QueryContextHolder currCtxHolder = new QueryContextHolder();
				reader.parse(expression, relativeTo, Metric.class, currCtxHolder, true);
				_queryProcessor.mergeQueryResults(queryResult, _queryProcessor.evaluateQuery(currCtxHolder.getCurrentQueryContext(), relativeTo));
			}
		} catch (ParseException ex) {
			throw new SystemException("Failed to parse the given expression", ex);
		}
		// Removing metrics which has no datapoints
		List<Metric> metrics = queryResult.getMetricsList();
		if (metrics!=null) {
			Iterator<Metric> metricIterator = metrics.iterator();
			while (metricIterator.hasNext()) {
				Metric metric = metricIterator.next();
				if (metric.getDatapoints()==null || metric.getDatapoints().size() == 0) {
					metricIterator.remove();
				}
			}
			queryResult.setMetricsList(metrics);
		}
		_monitorService.modifyCounter(Counter.DATAPOINT_READS, _getDatapointsAcrossMetrics(queryResult.getMetricsList()), null);
		queryResult.setExpandedTimeSeriesRange(QueryTimeSeriesExpansion.getExpandedTimeSeriesRange(queryResult.getNumTSDBResults()));
		queryResult.setQueryStartTimeWindow(QueryStartTimeWindow.getWindow(relativeTo - queryResult.getQueryStartTimeMillis()));
		queryResult.setQueryTimeWindow(QueryTimeWindow.getWindow(queryResult.getQueryTimeRangeInMillis()));

		final long time = System.currentTimeMillis() - start;
		_monitorService.modifyCounter(Counter.METRICS_GETMETRICS_LATENCY, time, null);
		_monitorService.modifyCounter(Counter.METRICS_GETMETRICS_COUNT, expressions.size(), null);
		return queryResult;
	}

	@Override
	public String getAsyncMetrics(List<String> expressions, long relativeTo, int ttl, String ownerName) {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<MetricQuery> getQueries(String expression) {
		requireNotDisposed();
		return getQueries(expression, System.currentTimeMillis());
	}

	@Override
	public List<MetricQuery> getQueries(String expression, long relativeTo) {
		requireNotDisposed();
		return getQueries(Arrays.asList(new String[] { expression }), relativeTo);
	}

	@Override
	public List<MetricQuery> getQueries(List<String> expressions) {
		requireNotDisposed();
		return getQueries(expressions, System.currentTimeMillis());
	}

	@Override
	public List<MetricQuery> getQueries(List<String> expressions, long relativeTo) {
		requireNotDisposed();
		SystemAssert.requireArgument(MetricReader.isValid(expressions), "Illegal metric expression found: " + expressions);

		MetricReader<MetricQuery> reader = _metricReaderProviderForQueries.get();
		List<MetricQuery> queries = new ArrayList<>(expressions.size());

		try {
			for (String expression : expressions) {
				_logger.debug("Creating metric query for expression {}", expression);
				queries.addAll(reader.parse(expression, relativeTo, MetricQuery.class, new QueryContextHolder(), false));
			}
		} catch (ParseException ex) {
			throw new SystemException("Failed to parse the given expression", ex);
		}
		return queries;
	}
	
	@Override
	public void dispose() {
		super.dispose();
		// _tsdbService.dispose();
	}

	private long _getDatapointsAcrossMetrics(List<Metric> metrics) {
		long dataPointsSize = 0;

		for (Metric metric : metrics) {
			dataPointsSize += metric.getDatapoints().size();
		}
		return dataPointsSize;
	}

	private String getMatchedDCAgainstRegex(String scope, String regex) {

		if(scope == null || scope.isEmpty()) {
			_logger.warn("Can't retrieve DC from empty scope.");
			return null;
		}

		Matcher m;
		m = Pattern.compile(regex).matcher(scope);
		while (m.find()) {
			String dc = m.group().substring(1, m.group().length() - 1).toUpperCase();
			_logger.debug(MessageFormat.format("Retrieved DC: {0} from scope: {1}", dc, scope));
			return dc;
		}
		return null;
	}

	@Override
	public List<String> extractDCFromMetricQuery(List<MetricQuery> mQList) {
		Set<String> dcList = new HashSet<>();

		for (MetricQuery mQ: mQList) {
			String currentDC = getDCFromScope(mQ.getScope()); //TODO: If the dc gets transferred to tags, we need to update this.
			if (currentDC != null) {
				dcList.add(currentDC);
			}
		}

		_logger.debug("DCs detected: {}", dcList);
		return new ArrayList<>(dcList);
	}

	@Override
	public String extractDCFromMetric(Metric m) {
		return getDCFromScope(m.getScope()); //TODO: If the dc gets transferred to tags, we need to update this.
	}

	private String getDCFromScope(String scope) {

		if(scope == null || scope.isEmpty()) {
			_logger.warn("Can't retrieve DC from empty scope.");
			return null;
		}

		try {
			String dc = getMatchedDCAgainstRegex(scope, "\\.(?i)(" + dcListRegex + ")\\.");

			if (dc != null) {
				return dc;
			} else {
				_logger.debug(MessageFormat.format("Unable to identify dc from scope: {0}", scope));
				return null;
			}
		} catch (Exception ex) {
			_logger.error("Unable to retrieve DC from scope. Exception: {0}", ex);
			return null;
		}
	}
}
	/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

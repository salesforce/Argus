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
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.system.SystemConfiguration;
import com.salesforce.dva.argus.system.SystemException;

import com.salesforce.dva.argus.system.SystemMain;
import com.salesforce.dva.argus.util.QueryContextHolder;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	private String expandedTimeSeriesRange;
	private String queryTimeWindow;
	private Integer numDiscoveryResults = 0;
	private Integer numDiscoveryQueries = 0;

	//~ Constructors *********************************************************************************************************************************

	/**
	 * Creates a new DefaultMetricService object.
	 *
	 * @param  monitorService   The monitor service instance to use. Cannot be null.
	 * @param  metricsprovider  The metric reader provider used to perform metric operations.  Cannot be null.
	 * @param  queryprovider    The metric reader provider used to construct metric queries without fetching data. Cannot be null.
	 * @param  config           The system configuration.  Cannot be null.
	 */
	@Inject
	protected DefaultMetricService(MonitorService monitorService, Provider<MetricReader<Metric>> metricsprovider,
			Provider<MetricReader<MetricQuery>> queryprovider, SystemConfiguration config) {
		super(config);
		requireArgument(monitorService != null, "Monitor service cannot be null.");
		_monitorService = monitorService;
		_metricReaderProviderForMetrics = metricsprovider;
		_metricReaderProviderForQueries = queryprovider;
		_configuration = config;

	}

	//~ Methods **************************************************************************************************************************************

	@Override
	public List<Metric> getMetrics(String expression) {
		requireNotDisposed();
		return getMetrics(expression, System.currentTimeMillis());
	}

	@Override
	public List<Metric> getMetrics(String expression, long relativeTo) {
		requireNotDisposed();
		return getMetrics(Arrays.asList(new String[] { expression }), relativeTo);
	}

	@Override
	public List<Metric> getMetrics(List<String> expressions) {
		requireNotDisposed();
		return getMetrics(expressions, System.currentTimeMillis());
	}

	@Override
	public List<Metric> getMetrics(List<String> expressions, long relativeTo) {
		requireNotDisposed();
		SystemAssert.requireArgument(MetricReader.isValid(expressions), "Illegal metric expression found: " + expressions);

		MetricReader<Metric> reader = _metricReaderProviderForMetrics.get();
		List<Metric> metrics = new ArrayList<>(expressions.size());

		try {
			numDiscoveryResults = 0;
			numDiscoveryQueries = 0;
			for (String expression : expressions) {
				_logger.debug("Reading metric for expression {}", expression);
				metrics.addAll(reader.parse(expression, relativeTo, Metric.class, new QueryContextHolder(), false));
				expandedTimeSeriesRange = reader.getExpandedTimeSeriesRange();
				queryTimeWindow = reader.getQueryTimeWindow();
				numDiscoveryResults += reader.getNumDiscoveryResults();
				numDiscoveryQueries += reader.getNumDiscoveryQueries();
			}
		} catch (ParseException ex) {
			throw new SystemException("Failed to parse the given expression", ex);
		} catch(Exception ex) {
			_logger.error("Exception occured when evaluating metric expressions {} - {}" , Arrays.toString(expressions.toArray()), ExceptionUtils.getFullStackTrace(ex));
			throw ex;
		}
		_monitorService.modifyCounter(Counter.DATAPOINT_READS, _getDatapointsAcrossMetrics(metrics), null);
		return metrics;
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
	public String getExpandedTimeSeriesRange()
	{
		{
			return expandedTimeSeriesRange;
		}
	}
	
	@Override
	public String getQueryTimeWindow()
	{
		{
			return queryTimeWindow;
		}
	}
	
	@Override
	public Integer getNumDiscoveryResults()
	{
		{
			return numDiscoveryResults;
		}
	}
	
	@Override
	public Integer getNumDiscoveryQueries()
	{
		{
			return numDiscoveryQueries;
		}
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

	public List<String> getDCFromExpression(String expression) {
		Set<String> DC = new HashSet<String>();
		Matcher m;
		String dcList = _configuration.getValue(com.salesforce.dva.argus.system.SystemConfiguration.Property.DC_LIST).replaceAll(",","|");
		String defaultDC = _configuration.getValue(SystemConfiguration.Property.DC_DEFAULT);
		ArrayList<String> patterns = new ArrayList<>(Arrays.asList( "\\.(?i)(" + dcList + ")\\.",
				":argus\\."));

		if (expression == null || expression.isEmpty()) {
			_logger.error("Expression either null or empty. Cannot retrive DC from the expression. Returning default value " + defaultDC);
			DC.add(defaultDC);
		} else {
			// Get all the expanded queries from MetricService and identify all different DCs.
			List<MetricQuery> queries = getQueries(expression);
			for(MetricQuery currentQuery: queries) {
				m = Pattern.compile(patterns.get(0)).matcher(currentQuery.getScope());
				while (m.find()) DC.add(m.group().substring(1, m.group().length() - 1).toUpperCase());

				// If it matches ":argus., then it's argus metrics emitted by PRD."
				m = Pattern.compile(patterns.get(1)).matcher(currentQuery.getScope());
				if(m.find()) DC.add(defaultDC);
			}
		}
		if (DC.size() == 0) {
			_logger.info("Unable to identify DC from expression: " + expression +" . Returning default value PRD " + defaultDC);
			DC.add(defaultDC);
		}
		return new ArrayList<>(DC);
	}
}
	/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */
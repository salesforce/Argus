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

package com.salesforce.dva.argus.ws.filter;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.LoggerFactory;

import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.system.SystemMain;
import com.salesforce.dva.argus.ws.listeners.ArgusWebServletListener;

/**
 * Servlet filter to push end point performance numbers to monitoring service.
 *
 * @author  Kiran Gowdru (kgowdru@salesforce.com)
 */
public class PerfFilter implements Filter {

	//~ Instance fields ******************************************************************************************************************************

	protected final SystemMain system = ArgusWebServletListener.getSystem();
	private MonitorService monitorService = system.getServiceFactory().getMonitorService();
	private final String DATA_READ_PER_MIN = "perf.ws.read.count";
	private final String DATA_READ_QUERY_LATENCY = "perf.ws.read.latency";
	private final String DATA_WRITE_PER_MIN = "perf.ws.write.count";
	private final String DATA_WRITE_LATENCY = "perf.ws.write.latency";
	private final String DATA_READ_REQ_BYTES = "perf.ws.read.rxbytes";
	private final String DATA_READ_RESP_BYTES = "perf.ws.read.txbytes";
	private final String DATA_WRITE_REQ_BYTES = "perf.ws.write.rxbytes";
	private final String DATA_WRITE_RESP_BYTES = "perf.ws.write.txbytes";
	private final String TAGS_METHOD_KEY = "method";
	private final String TAGS_ENDPOINT_KEY = "endpoint";
	private final String TAGS_USER_KEY = "user";
	private final String TAGS_TIME_WINDOW_KEY = "timeWindow";
	private final String TAGS_EXPANDED_TIME_SERIES_RANGE_KEY = "expandedTimeSeriesRange";
	private final String DATA_READ_NUM_TIME_SERIES = "perf.ws.read.num.time.series";
	private final String DATA_READ_NUM_DISCOVERY_RESULTS = "perf.ws.read.num.discovery.results";
	private final String DATA_READ_NUM_DISCOVERY_QUERIES = "perf.ws.read.num.discovery.queries";

	//~ Methods **************************************************************************************************************************************

	@Override
	public void destroy() { }

	/**
	 * Updates performance counters using the Argus monitoring service.
	 *
	 * @param   request   The HTTP request.
	 * @param   response  The HTTP response.
	 * @param   chain     The filter chain to execute.
	 *
	 * @throws  IOException       If an I/O error occurs.
	 * @throws  ServletException  If an unknown error occurs.
	 * 
	 */
	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		HttpServletRequest req = HttpServletRequest.class.cast(request);
		long start = System.currentTimeMillis();

		try {
			chain.doFilter(request, response);
		} finally {
			long delta = System.currentTimeMillis() - start;
			HttpServletResponse resp = HttpServletResponse.class.cast(response);

			updateCounters(req, resp, delta);
		}
	}

	@Override
	public void init(FilterConfig filterConfig) throws ServletException { }

	private void updateCounters(HttpServletRequest req, HttpServletResponse resp, long delta) {
		try {
			Map<String, String> tags = new HashMap<>();

			String method = req.getMethod();
			tags.put(TAGS_METHOD_KEY, method);

			String endPoint = _getEndpoint(req);
			if (endPoint != null && !endPoint.isEmpty()) {
				tags.put(TAGS_ENDPOINT_KEY, endPoint);
			}

			Object user = req.getAttribute(AuthFilter.USER_ATTRIBUTE_NAME);
			String username = user != null ? String.class.cast(user) : "NULLUSER";
			if(!username.isEmpty()) {
				tags.put(TAGS_USER_KEY, username);
			}

			String contentLength = resp.getHeader("Content-Length");
			int respBytes = ((contentLength != null && contentLength.matches("[0-9]+")) ? Integer.parseInt(contentLength) : 0);
			int reqBytes = ((req.getContentLength() > 0) ? req.getContentLength() : 0);

			if (method.equals("GET")) {
				if(endPoint.equals("metrics")){

					String timeWindow = (String) req.getAttribute(TAGS_TIME_WINDOW_KEY);
					if(timeWindow == null){
						timeWindow = "NULL_TIME_WINDOW";
					}
					tags.put(TAGS_TIME_WINDOW_KEY, timeWindow);

					String expandedTimeSeriesRange = (String) req.getAttribute(TAGS_EXPANDED_TIME_SERIES_RANGE_KEY);
					if(expandedTimeSeriesRange == null){
						expandedTimeSeriesRange = "NULL_EXPANDED_TIME_SERIES_RANGE";
					}
					tags.put(TAGS_EXPANDED_TIME_SERIES_RANGE_KEY, expandedTimeSeriesRange);
					
					Integer numTimeSeries = (Integer) req.getAttribute("numTimeSeries");
					if(numTimeSeries != null){
						monitorService.modifyCustomCounter(DATA_READ_NUM_TIME_SERIES, numTimeSeries, tags);
					}
					
					Integer numDiscoveryResults = (Integer) req.getAttribute("numDiscoveryResults");

                                        /* Discovery service should not audit when no expansion performed, or number of expanded series equals 0*/
					if(numDiscoveryResults != null && numDiscoveryResults !=0 ){
						monitorService.modifyCustomCounter(DATA_READ_NUM_DISCOVERY_RESULTS, numDiscoveryResults, tags);
					}
					
					Integer numDiscoveryQueries = (Integer) req.getAttribute("numDiscoveryQueries");

					if(numDiscoveryQueries != null && numDiscoveryQueries !=0 ){
						monitorService.modifyCustomCounter(DATA_READ_NUM_DISCOVERY_QUERIES, numDiscoveryQueries, tags);
					}
				}

				monitorService.modifyCustomCounter(DATA_READ_PER_MIN, 1, tags);
				monitorService.modifyCustomCounter(DATA_READ_QUERY_LATENCY, delta, tags);
				monitorService.modifyCustomCounter(DATA_READ_REQ_BYTES, reqBytes, tags);
				monitorService.modifyCustomCounter(DATA_READ_RESP_BYTES, respBytes, tags);
			} else if (method.equals("POST") || method.equals("PUT") || method.equals("DELETE")) {
				monitorService.modifyCustomCounter(DATA_WRITE_PER_MIN, 1, tags);
				monitorService.modifyCustomCounter(DATA_WRITE_LATENCY, delta, tags);
				monitorService.modifyCustomCounter(DATA_WRITE_REQ_BYTES, reqBytes, tags);
				monitorService.modifyCustomCounter(DATA_WRITE_RESP_BYTES, respBytes, tags);
			}
		} catch (Exception e) {
			LoggerFactory.getLogger(getClass()).error(e.getMessage(), e);
		}
	}

	private String _getEndpoint(HttpServletRequest req) {
		String pathInfo = req.getPathInfo();
		if(pathInfo != null) {
			return pathInfo.replaceFirst("/", "").replaceAll("[0-9]+", "-");
		}

		return null;
	}
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

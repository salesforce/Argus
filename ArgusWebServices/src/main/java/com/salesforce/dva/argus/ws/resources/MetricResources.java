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
	 
package com.salesforce.dva.argus.ws.resources;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.entity.PrincipalUser;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.schema.WildcardExpansionLimitExceededException;
import com.salesforce.dva.argus.system.SystemAssert;
import com.salesforce.dva.argus.ws.annotation.Description;
import com.salesforce.dva.argus.ws.dto.MetricDto;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import java.util.*;
import java.util.Map.Entry;

/**
 * Provides methods to query and transform metrics.
 *
 * @author  Bhinav Sura (bhinav.sura@salesforce.com) Raj Sarkapally (rsarkapally@salesforce.com)
 */
@Path("/metrics")
@Description("Provides methods to query and transform metrics.")
public class MetricResources extends AbstractResource {

    //~ Instance fields ******************************************************************************************************************************

    private final String COMMA = ",";
    private final String NEW_LINE = "\n";
    private final String EMPTY = "";
    private final int DEFAULT_TTL = 1800;

    //~ Methods **************************************************************************************************************************************

    /**
     * Performs a metric query using the given expression.
     *
     * @param   req          The HttpServlet request object. Cannot be null.
     * @param   expressions  The expressions to evaluate.
     *
     * @return  The resulting metrics.
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON + ";qs=1")
    @Description("Performs a metric query using the given expression.")
    public List<MetricDto> getMetricsJSON(@Context HttpServletRequest req,
        @QueryParam("expression") List<String> expressions) {
        
    	try {
    		List<Metric> metrics = _getMetrics(req, expressions);

            return MetricDto.transformToDto(metrics);
    	} catch(Exception ex) {
    		throw new WebApplicationException(ex.getMessage(), Status.INTERNAL_SERVER_ERROR);
    	}
    }

    /**
     * Download the metric data for one or more metric expressions.
     *
     * @param   req          HTTPServlet request. Cannot be null.
     * @param   expressions  Expressions for metrics. Cannot be null but may be empty.
     *
     * @return  Metric data in CSV format
     */
    @GET
    @Produces("application/ms-excel;qs=0")
    @Description("Downloads the metric data in CSV format.")
    public Response getMetricsCSV(@Context HttpServletRequest req,
        @QueryParam("expression") List<String> expressions) {
        ResponseBuilder response = null;

        try {
            List<Metric> metrics = _getMetrics(req, expressions);

            response = Response.ok(_convertToCSV(metrics));
        } catch (Exception ex) {
            response = Response.status(Status.INTERNAL_SERVER_ERROR).encoding(ex.getMessage());
        }
        response.header("Content-Disposition", "attachment; filename=metrics.csv");
        return response.build();
    }

    /**
     * Start an async batch metric query
     *
     * @param   req          HTTPServlet request. Cannot be null.
     * @param   ttl          Time to live for the batch results when all computation finishes
     * @param   expressions  Expressions for metrics. Cannot be null but may be empty.
     *
     * @return  Batch ID and path to where metric-processing metadata is returned
     */
    @GET
    @Produces(MediaType.APPLICATION_JSON)
    @Path("/batch")
    @Description("Start an async batch metric query")
    public Response getAsyncMetricsJSON(@Context HttpServletRequest req,
        @QueryParam("ttl") int ttl,
        @QueryParam("expression") List<String> expressions) {
        if (ttl == 0) {
            ttl = DEFAULT_TTL;
        }
        Map<String, Object> body = new HashMap<>();
        String batchId = _getAsyncResponse(req, expressions, ttl);
        body.put("href", "/batches/" + batchId);
        body.put("id", batchId);
        return Response.accepted(body).build();
    }


    /**
     * Download the metric data for a given query. 
     * Single expression can consist of multiple queries. We will take the query with the longest time and instrument that.
     *
     * @param   req          HTTPServlet request. Cannot be null.
     * @param   expressions  Expressions for metrics. Cannot be null but may be empty. 
     *
     * @return  Metric data for given metric expressions. Will never return null but may be empty.
     */
    private List<Metric> _getMetrics(HttpServletRequest req, List<String> expressions) {
        validateAndGetOwner(req, null);
        SystemAssert.requireArgument(expressions != null && !expressions.isEmpty(), "Expression list cannot be null or empty");

        final MetricService metricService = system.getServiceFactory().getMetricService();
        List<Metric> metrics = new ArrayList<Metric>();

        for (String expression : expressions) {
        	try {
        		List<Metric> metricsForThisExpression = metricService.getMetrics(expression);
        		req.setAttribute("expandedTimeSeriesRange", metricService.getExpandedTimeSeriesRange());
        		req.setAttribute("timeWindow", metricService.getQueryTimeWindow());
        		metrics.addAll(metricsForThisExpression);
        	} catch(WildcardExpansionLimitExceededException e) {
        		metricService.dispose();
        		throw new WebApplicationException(e.getMessage(), Status.BAD_REQUEST);
        	}
        }
        
        metricService.dispose();
        req.setAttribute("numTimeSeries", metrics.size());
        req.setAttribute("numDiscoveryResults", metricService.getNumDiscoveryResults());
        req.setAttribute("numDiscoveryQueries", metricService.getNumDiscoveryQueries());
        return metrics;
    }

    private String _getAsyncResponse(HttpServletRequest req, List<String> expressions, int ttl) {
        SystemAssert.requireArgument(expressions != null && !expressions.isEmpty(), "Expression list cannot be null or empty");
        PrincipalUser owner = validateAndGetOwner(req, null);
        SystemAssert.requireArgument(owner != null, "Owner cannot be null");

        final MetricService metricService = system.getServiceFactory().getMetricService();
        return metricService.getAsyncMetrics(expressions, System.currentTimeMillis(), ttl, owner.getUserName());
    }

    private String _convertToCSV(List<Metric> metrics) {
        if (metrics == null || metrics.size() == 0) {
            return EMPTY;
        }

        StringBuilder result = new StringBuilder();

        result.append("Timestamp");
        result.append(COMMA);
        for (Metric metric : metrics) {
            result.append(_getMetricExpression(metric));
            result.append(COMMA);
        }
        result.deleteCharAt(result.length() - 1);

        TreeSet<Long> timestamps = new TreeSet<Long>();

        for (Metric metric : metrics) {
            timestamps.addAll(metric.getDatapoints().keySet());
        }
        for (Long timestamp : timestamps) {
            result.append(NEW_LINE);
            result.append(timestamp);
            result.append(COMMA);
            for (Metric metric : metrics) {
                result.append(metric.getDatapoints().get(timestamp) != null ? metric.getDatapoints().get(timestamp) : EMPTY);
                result.append(COMMA);
            }
            result.deleteCharAt(result.length() - 1);
        }
        return result.toString();
    }

    private String _getMetricExpression(Metric metric) {
        StringBuilder result = new StringBuilder();

        result.append("\"");
        if (metric != null) {
            result.append(metric.getScope());
            result.append(":" + metric.getMetric());
            if (metric.getTags() != null && metric.getTags().size() > 0) {
                result.append("{");
                for (Entry<String, String> tag : metric.getTags().entrySet()) {
                    result.append(tag.getKey() + "=" + tag.getValue());
                    result.append(COMMA);
                }
                result.deleteCharAt(result.length() - 1);
                result.append("}");
            }
        }
        result.append("\"");
        return result.toString();
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

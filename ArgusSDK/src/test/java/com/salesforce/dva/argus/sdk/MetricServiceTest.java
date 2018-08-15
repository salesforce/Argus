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
package com.salesforce.dva.argus.sdk;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.salesforce.dva.argus.sdk.ArgusService.PutResult;
import com.salesforce.dva.argus.sdk.entity.Metric;
import com.salesforce.dva.argus.sdk.exceptions.TokenExpiredException;

import org.junit.Test;
import java.io.IOException;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.Assert.*;

public class MetricServiceTest extends AbstractTest {

    @Test
    public void testPutMetrics() throws IOException, TokenExpiredException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/MetricServiceTest.json"))) {
            MetricService metricService = argusService.getMetricService();
            List<Metric> metrics = Arrays.asList(new Metric[] { _constructMetric() });
            PutResult result = metricService.putMetrics(metrics);

            assertEquals(_constructSuccessfulResult(metrics, 0), result);
        }
    }

    @Test
    public void testGetMetrics() throws IOException, TokenExpiredException {
        try(ArgusService argusService = new ArgusService(getMockedClient("/MetricServiceTest.json"))) {
            MetricService metricService = argusService.getMetricService();
            List<String> expressions = Arrays.asList(new String[] { "-1d:TestScope:TestMetric:TestNamespace{TestTag=TagValue}:sum" });
            List<Metric> result = metricService.getMetrics(expressions);
            List<Metric> expected = Arrays.asList(new Metric[] { _constructMetric() });

            assertEquals(expected, result);
        }
    }
    
    @Test
    public void testMaintainExpectedPrecision() throws IOException, TokenExpiredException {
    	try (ArgusService argusService = new ArgusService(getMockedClient("/MetricServiceTest.json"))) {
    		MetricService metricService = argusService.getMetricService();
    		for (Metric metric : Arrays.asList(_constructMetricLong(false), _constructMetric(), _constructMetricLong(true))) {
	    		List<Metric> metrics = Arrays.asList(new Metric[] { metric });
	    		PutResult returned = metricService.putMetrics(metrics);
	    		
	    		assertEquals(_constructSuccessfulResult(metrics, 0), returned);
	    		
	    		List<String> expressions = Arrays.asList(new String[] { "-1d:TestScope:TestMetric:TestNamespace{TestTag=TagValue}:sum" });
	    		List<Metric> result = metricService.getMetrics(expressions);
	    		List<Metric> expected = Arrays.asList(new Metric[] { metric });
	    		
	    		assertEquals(expected, result);
    		}
    	}
    }

    private Metric _constructMetric() throws JsonProcessingException {
        Metric result = new Metric();
        Map<String, String> fields = new TreeMap<>();
        Map<String, String> tags = new TreeMap<>();
        Map<Long, Number> datapoints = new TreeMap<>();

        datapoints.put(0L, 0.0);
        datapoints.put(1L, 101.0);
        fields.put("TestField", "FieldValue");
        tags.put("TestTag", "TagValue");
        result.setNamespace("TestNamespace");
        result.setMetric("TestMetric");
        result.setScope("TestScope");
        result.setTags(tags);
        return result;
    }
    
    private Metric _constructMetricLong(boolean useMax) throws JsonProcessingException {
    	Metric result = new Metric();
    	Map<String, String> fields = new TreeMap<>();
    	Map<String, String> tags = new TreeMap<>();
    	Map<Long, Number> datapoints = new TreeMap<>();
    	
    	datapoints.put(0L, useMax ? 9223372036854775807L : 101L);
    	fields.put("TestField", "FieldValue");
    	tags.put("TestTag", "TagValue");
    	result.setNamespace("TestNamespace");
    	result.setMetric("TestMetric");
    	result.setScope("TestScope");
    	result.setTags(tags);
    	return result;
    }

    private PutResult _constructSuccessfulResult(List<Metric> metrics, int errorCount) {
        String failCount = Integer.toString(errorCount);
        String successCount = Integer.toString(metrics.size() - errorCount);
        List<String> errorMessages = new LinkedList<>();

        if (errorCount > 0) {
            errorMessages.add(failCount);
        }
        return new PutResult(successCount, failCount, errorMessages);
    }
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

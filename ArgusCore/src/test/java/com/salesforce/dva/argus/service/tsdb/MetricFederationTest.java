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

package com.salesforce.dva.argus.service.tsdb;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.metric.transform.TransformFactory;

public class MetricFederationTest extends AbstractTest {
	private ObjectMapper _mapper;

	@Before
	public void initialize() {
		_mapper = new ObjectMapper();
		SimpleModule module = new SimpleModule();
		module.addSerializer(Metric.class, new MetricTransform.Serializer());
		module.addDeserializer(ResultSet.class, new MetricTransform.MetricListDeserializer());
		_mapper.registerModule(module);
	}

	@Test
	public void testTimeFederationFork() {
		MetricService metricService = system.getServiceFactory().getMetricService();
		List<MetricQuery> queries = metricService.getQueries("-2d:scope:metric{tagk=tagv}:avg:15m-avg");

		QueryFederation queryFederation = new TimeQueryFederation();
		Map<MetricQuery, List<MetricQuery>> mapQuerySubQueries = queryFederation.federateQueries(queries);
		/* Since the difference for large range values of timestamp is not exactly 2d */
		assertTrue(mapQuerySubQueries.get(queries.get(0)).size() == 3);
	}

	@Test
	public void testEndPointFederationForkJoinSumDownsamplerWithTag() {
		MetricService metricService = system.getServiceFactory().getMetricService();
		List<MetricQuery> queries = metricService.getQueries("-1h:scope:metric{tagk=tagv}:sum:15m-sum");
		List<String> readEndPoints = new ArrayList<String>();
		readEndPoints.add("http://localhost:4477");
		readEndPoints.add("http://localhost:4488");

		String content1 = "[{\"metric\":\"mem.heap.used-__-argus.jvm\",\"tags\":{\"host\":\"machineHost1\",\"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOiJieXRlcyJ9\"},\"aggregateTags\":[],\"tsuids\":[\"00000000000E000000000001000000000003000000000002000000000002\"],\"dps\":{\"1477386300\":4.940423168E9}},{\"metric\":\"mem.heap.used-__-argus.jvm\",\"tags\":{\"host\":\"machineHost3\",\"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOiJieXRlcyJ9\"},\"aggregateTags\":[],\"tsuids\":[\"00000000000E00000000000100000000000300000000000200000000000A\"],\"dps\":{\"1477386500\":4.940423168E9}}]";
		String content2 = "[{\"metric\":\"mem.heap.used-__-argus.jvm\",\"tags\":{\"host\":\"machineHost2\",\"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOiJieXRlcyJ9\"},\"aggregateTags\":[],\"tsuids\":[\"00000000000E000000000001000000000003000000000002000000000002\"],\"dps\":{\"1477386300\":4.940423168E9}},{\"metric\":\"mem.heap.used-__-argus.jvm\",\"tags\":{\"host\":\"machineHost3\",\"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOiJieXRlcyJ9\"},\"aggregateTags\":[],\"tsuids\":[\"00000000000E00000000000100000000000300000000000200000000000A\"],\"dps\":{\"1477386600\":4.940423168E9}}]";

		QueryFederation queryFederation = new EndPointQueryFederation(readEndPoints);
		Map<MetricQuery, List<MetricQuery>> mapQuerySubQueries = queryFederation.federateQueries(queries);
		assertEquals(2, mapQuerySubQueries.get(queries.get(0)).size());

		Map<MetricQuery, List<Metric>>  subQueryMetricsMap = new HashMap<MetricQuery, List<Metric>>();
		List<MetricQuery> subQueries = mapQuerySubQueries.get(queries.get(0));
		subQueryMetricsMap.put(subQueries.get(0), getMetricsFromMetricString(content1));
		subQueryMetricsMap.put(subQueries.get(1), getMetricsFromMetricString(content2));

		Map<MetricQuery, List<Metric>> queryMetricsMap = queryFederation.join(mapQuerySubQueries, subQueryMetricsMap);
		assertEquals(1, queryMetricsMap.size());
		assertEquals(3, queryMetricsMap.get(queries.get(0)).size());
		
		// Three time series
		assertEquals("{host=machineHost1}", queryMetricsMap.get(queries.get(0)).get(0).getTags().toString());
		assertEquals("{1477386300=4.940423168E9}", queryMetricsMap.get(queries.get(0)).get(0).getDatapoints().toString());
		assertEquals("{host=machineHost2}", queryMetricsMap.get(queries.get(0)).get(1).getTags().toString());
		assertEquals("{1477386300=4.940423168E9}", queryMetricsMap.get(queries.get(0)).get(1).getDatapoints().toString());
		assertEquals("{host=machineHost3}", queryMetricsMap.get(queries.get(0)).get(2).getTags().toString());
		assertEquals("{1477386500=4.940423168E9, 1477386600=4.940423168E9}", queryMetricsMap.get(queries.get(0)).get(2).getDatapoints().toString());
	}
	
	@Test
	public void testEndPointFederationForkJoinSumDownsamplerWithNoTag() {
		MetricService metricService = system.getServiceFactory().getMetricService();
		List<MetricQuery> queries = metricService.getQueries("-1h:scope:metric{tagk=tagv}:sum:15m-sum");
		List<String> readEndPoints = new ArrayList<String>();
		readEndPoints.add("http://localhost:4477");
		readEndPoints.add("http://localhost:4488");

		String content1 = "[{\"metric\":\"mem.heap.used-__-argus.jvm\",\"tags\":{\"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOiJieXRlcyJ9\"},\"aggregateTags\":[],\"tsuids\":[\"00000000000E000000000001000000000003000000000002000000000002\"],\"dps\":{\"1477386300\":3}},{\"metric\":\"mem.heap.used-__-argus.jvm\",\"tags\":{\"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOiJieXRlcyJ9\"},\"aggregateTags\":[],\"tsuids\":[\"00000000000E00000000000100000000000300000000000200000000000A\"],\"dps\":{\"1477386500\":6}}]";
		String content2 = "[{\"metric\":\"mem.heap.used-__-argus.jvm\",\"tags\":{\"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOiJieXRlcyJ9\"},\"aggregateTags\":[],\"tsuids\":[\"00000000000E000000000001000000000003000000000002000000000002\"],\"dps\":{\"1477386300\":4}},{\"metric\":\"mem.heap.used-__-argus.jvm\",\"tags\":{\"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOiJieXRlcyJ9\"},\"aggregateTags\":[],\"tsuids\":[\"00000000000E00000000000100000000000300000000000200000000000A\"],\"dps\":{\"1477386600\":7}}]";

		QueryFederation queryFederation = new EndPointQueryFederation(readEndPoints);
		Map<MetricQuery, List<MetricQuery>> mapQuerySubQueries = queryFederation.federateQueries(queries);
		assertEquals(2, mapQuerySubQueries.get(queries.get(0)).size());

		Map<MetricQuery, List<Metric>>  subQueryMetricsMap = new HashMap<MetricQuery, List<Metric>>();
		List<MetricQuery> subQueries = mapQuerySubQueries.get(queries.get(0));
		subQueryMetricsMap.put(subQueries.get(0), getMetricsFromMetricString(content1));
		subQueryMetricsMap.put(subQueries.get(1), getMetricsFromMetricString(content2));

		Map<MetricQuery, List<Metric>> queryMetricsMap = queryFederation.join(mapQuerySubQueries, subQueryMetricsMap);
		assertEquals(1, queryMetricsMap.size());
		assertEquals(1, queryMetricsMap.get(queries.get(0)).size());
		
		// One time series, since no tag specified
		assertEquals("{}", queryMetricsMap.get(queries.get(0)).get(0).getTags().toString());
		assertEquals("{1477386300=7.0, 1477386500=6.0, 1477386600=7.0}", queryMetricsMap.get(queries.get(0)).get(0).getDatapoints().toString());
	}
	
	@Test
	public void testEndPointFederationForkJoinMinDownsampler() {
		MetricService metricService = system.getServiceFactory().getMetricService();
		List<MetricQuery> queries = metricService.getQueries("-1h:scope:metric{tagk=tagv}:sum:15m-min");
		List<String> readEndPoints = new ArrayList<String>();
		readEndPoints.add("http://localhost:4477");
		readEndPoints.add("http://localhost:4488");

		String content1 = "[{\"metric\":\"mem.heap.used-__-argus.jvm\",\"tags\":{\"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOiJieXRlcyJ9\"},\"aggregateTags\":[],\"tsuids\":[\"00000000000E000000000001000000000003000000000002000000000002\"],\"dps\":{\"1477386300\":3}},{\"metric\":\"mem.heap.used-__-argus.jvm\",\"tags\":{\"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOiJieXRlcyJ9\"},\"aggregateTags\":[],\"tsuids\":[\"00000000000E00000000000100000000000300000000000200000000000A\"],\"dps\":{\"1477386500\":6}}]";
		String content2 = "[{\"metric\":\"mem.heap.used-__-argus.jvm\",\"tags\":{\"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOiJieXRlcyJ9\"},\"aggregateTags\":[],\"tsuids\":[\"00000000000E000000000001000000000003000000000002000000000002\"],\"dps\":{\"1477386300\":4}},{\"metric\":\"mem.heap.used-__-argus.jvm\",\"tags\":{\"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOiJieXRlcyJ9\"},\"aggregateTags\":[],\"tsuids\":[\"00000000000E00000000000100000000000300000000000200000000000A\"],\"dps\":{\"1477386600\":7}}]";

		QueryFederation queryFederation = new EndPointQueryFederation(readEndPoints);
		Map<MetricQuery, List<MetricQuery>> mapQuerySubQueries = queryFederation.federateQueries(queries);
		assertEquals(2, mapQuerySubQueries.get(queries.get(0)).size());

		Map<MetricQuery, List<Metric>>  subQueryMetricsMap = new HashMap<MetricQuery, List<Metric>>();
		List<MetricQuery> subQueries = mapQuerySubQueries.get(queries.get(0));
		subQueryMetricsMap.put(subQueries.get(0), getMetricsFromMetricString(content1));
		subQueryMetricsMap.put(subQueries.get(1), getMetricsFromMetricString(content2));

		Map<MetricQuery, List<Metric>> queryMetricsMap = queryFederation.join(mapQuerySubQueries, subQueryMetricsMap);
		assertEquals(1, queryMetricsMap.size());
		assertEquals(1, queryMetricsMap.get(queries.get(0)).size());
		
		// One time series, since no tag specified
		assertEquals("{}", queryMetricsMap.get(queries.get(0)).get(0).getTags().toString());
		assertEquals("{1477386300=3.0, 1477386500=6.0, 1477386600=7.0}", queryMetricsMap.get(queries.get(0)).get(0).getDatapoints().toString());
	}
	
	@Test
	public void testEndPointFederationForkJoinMaxDownsampler() {
		MetricService metricService = system.getServiceFactory().getMetricService();
		List<MetricQuery> queries = metricService.getQueries("-1h:scope:metric{tagk=tagv}:sum:15m-max");
		List<String> readEndPoints = new ArrayList<String>();
		readEndPoints.add("http://localhost:4477");
		readEndPoints.add("http://localhost:4488");

		String content1 = "[{\"metric\":\"mem.heap.used-__-argus.jvm\",\"tags\":{\"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOiJieXRlcyJ9\"},\"aggregateTags\":[],\"tsuids\":[\"00000000000E000000000001000000000003000000000002000000000002\"],\"dps\":{\"1477386300\":3}},{\"metric\":\"mem.heap.used-__-argus.jvm\",\"tags\":{\"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOiJieXRlcyJ9\"},\"aggregateTags\":[],\"tsuids\":[\"00000000000E00000000000100000000000300000000000200000000000A\"],\"dps\":{\"1477386500\":6}}]";
		String content2 = "[{\"metric\":\"mem.heap.used-__-argus.jvm\",\"tags\":{\"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOiJieXRlcyJ9\"},\"aggregateTags\":[],\"tsuids\":[\"00000000000E000000000001000000000003000000000002000000000002\"],\"dps\":{\"1477386300\":4}},{\"metric\":\"mem.heap.used-__-argus.jvm\",\"tags\":{\"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOiJieXRlcyJ9\"},\"aggregateTags\":[],\"tsuids\":[\"00000000000E00000000000100000000000300000000000200000000000A\"],\"dps\":{\"1477386600\":7}}]";

		QueryFederation queryFederation = new EndPointQueryFederation(readEndPoints);
		Map<MetricQuery, List<MetricQuery>> mapQuerySubQueries = queryFederation.federateQueries(queries);
		assertEquals(2, mapQuerySubQueries.get(queries.get(0)).size());

		Map<MetricQuery, List<Metric>>  subQueryMetricsMap = new HashMap<MetricQuery, List<Metric>>();
		List<MetricQuery> subQueries = mapQuerySubQueries.get(queries.get(0));
		subQueryMetricsMap.put(subQueries.get(0), getMetricsFromMetricString(content1));
		subQueryMetricsMap.put(subQueries.get(1), getMetricsFromMetricString(content2));

		Map<MetricQuery, List<Metric>> queryMetricsMap = queryFederation.join(mapQuerySubQueries, subQueryMetricsMap);
		assertEquals(1, queryMetricsMap.size());
		assertEquals(1, queryMetricsMap.get(queries.get(0)).size());
		
		// One time series, since no tag specified
		assertEquals("{}", queryMetricsMap.get(queries.get(0)).get(0).getTags().toString());
		assertEquals("{1477386300=4.0, 1477386500=6.0, 1477386600=7.0}", queryMetricsMap.get(queries.get(0)).get(0).getDatapoints().toString());
	}

	@Test
	public void testEndPointFederationForkJoinCountDownsampler() {
		MetricService metricService = system.getServiceFactory().getMetricService();
		List<MetricQuery> queries = metricService.getQueries("-1h:scope:metric{tagk=tagv}:count:15m-count");
		List<String> readEndPoints = new ArrayList<String>();
		readEndPoints.add("http://localhost:4477");
		readEndPoints.add("http://localhost:4488");

		String content1 = "[{\"metric\":\"mem.heap.used-__-argus.jvm\",\"tags\":{\"host\":\"machineHost1\",\"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOiJieXRlcyJ9\"},\"aggregateTags\":[],\"tsuids\":[\"00000000000E000000000001000000000003000000000002000000000002\"],\"dps\":{\"1477386300\":1}},{\"metric\":\"mem.heap.used-__-argus.jvm\",\"tags\":{\"host\":\"machineHost3\",\"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOiJieXRlcyJ9\"},\"aggregateTags\":[],\"tsuids\":[\"00000000000E00000000000100000000000300000000000200000000000A\"],\"dps\":{\"1477386500\":1}}]";
		String content2 = "[{\"metric\":\"mem.heap.used-__-argus.jvm\",\"tags\":{\"host\":\"machineHost2\",\"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOiJieXRlcyJ9\"},\"aggregateTags\":[],\"tsuids\":[\"00000000000E000000000001000000000003000000000002000000000002\"],\"dps\":{\"1477386300\":1}},{\"metric\":\"mem.heap.used-__-argus.jvm\",\"tags\":{\"host\":\"machineHost3\",\"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOiJieXRlcyJ9\"},\"aggregateTags\":[],\"tsuids\":[\"00000000000E00000000000100000000000300000000000200000000000A\"],\"dps\":{\"1477386600\":1}}]";

		QueryFederation queryFederation = new EndPointQueryFederation(readEndPoints);
		Map<MetricQuery, List<MetricQuery>> mapQuerySubQueries = queryFederation.federateQueries(queries);
		assertEquals(2, mapQuerySubQueries.get(queries.get(0)).size());

		Map<MetricQuery, List<Metric>>  subQueryMetricsMap = new HashMap<MetricQuery, List<Metric>>();
		List<MetricQuery> subQueries = mapQuerySubQueries.get(queries.get(0));
		subQueryMetricsMap.put(subQueries.get(0), getMetricsFromMetricString(content1));
		subQueryMetricsMap.put(subQueries.get(1), getMetricsFromMetricString(content2));

		Map<MetricQuery, List<Metric>> queryMetricsMap = queryFederation.join(mapQuerySubQueries, subQueryMetricsMap);
		assertEquals(1, queryMetricsMap.size());
		assertEquals(3, queryMetricsMap.get(queries.get(0)).size());
		
		// Three time series
		assertEquals("{host=machineHost1}", queryMetricsMap.get(queries.get(0)).get(0).getTags().toString());
		assertEquals("{1477386300=1.0}", queryMetricsMap.get(queries.get(0)).get(0).getDatapoints().toString());
		assertEquals("{host=machineHost2}", queryMetricsMap.get(queries.get(0)).get(1).getTags().toString());
		assertEquals("{1477386300=1.0}", queryMetricsMap.get(queries.get(0)).get(1).getDatapoints().toString());
		assertEquals("{host=machineHost3}", queryMetricsMap.get(queries.get(0)).get(2).getTags().toString());
		assertEquals("{1477386500=1.0, 1477386600=1.0}", queryMetricsMap.get(queries.get(0)).get(2).getDatapoints().toString());
	}
	
	@Test
	public void testEndPointFederationForkJoinCountDownsamplerWithNoTag() {
		MetricService metricService = system.getServiceFactory().getMetricService();
		List<MetricQuery> queries = metricService.getQueries("-1h:scope:metric{tagk=tagv}:count:15m-count");
		List<String> readEndPoints = new ArrayList<String>();
		readEndPoints.add("http://localhost:4477");
		readEndPoints.add("http://localhost:4488");

		String content1 = "[{\"metric\":\"mem.heap.used-__-argus.jvm\",\"tags\":{\"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOiJieXRlcyJ9\"},\"aggregateTags\":[],\"tsuids\":[\"00000000000E000000000001000000000003000000000002000000000002\"],\"dps\":{\"1477386300\":3}},{\"metric\":\"mem.heap.used-__-argus.jvm\",\"tags\":{\"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOiJieXRlcyJ9\"},\"aggregateTags\":[],\"tsuids\":[\"00000000000E00000000000100000000000300000000000200000000000A\"],\"dps\":{\"1477386500\":6}}]";
		String content2 = "[{\"metric\":\"mem.heap.used-__-argus.jvm\",\"tags\":{\"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOiJieXRlcyJ9\"},\"aggregateTags\":[],\"tsuids\":[\"00000000000E000000000001000000000003000000000002000000000002\"],\"dps\":{\"1477386300\":4}},{\"metric\":\"mem.heap.used-__-argus.jvm\",\"tags\":{\"meta\":\"eyJkaXNwbGF5TmFtZSI6bnVsbCwidW5pdHMiOiJieXRlcyJ9\"},\"aggregateTags\":[],\"tsuids\":[\"00000000000E00000000000100000000000300000000000200000000000A\"],\"dps\":{\"1477386600\":7}}]";

		QueryFederation queryFederation = new EndPointQueryFederation(readEndPoints);
		Map<MetricQuery, List<MetricQuery>> mapQuerySubQueries = queryFederation.federateQueries(queries);
		assertEquals(2, mapQuerySubQueries.get(queries.get(0)).size());

		Map<MetricQuery, List<Metric>>  subQueryMetricsMap = new HashMap<MetricQuery, List<Metric>>();
		List<MetricQuery> subQueries = mapQuerySubQueries.get(queries.get(0));
		subQueryMetricsMap.put(subQueries.get(0), getMetricsFromMetricString(content1));
		subQueryMetricsMap.put(subQueries.get(1), getMetricsFromMetricString(content2));

		Map<MetricQuery, List<Metric>> queryMetricsMap = queryFederation.join(mapQuerySubQueries, subQueryMetricsMap);
		assertEquals(1, queryMetricsMap.size());
		assertEquals(1, queryMetricsMap.get(queries.get(0)).size());
		
		// One time series, since no tag specified
		assertEquals("{}", queryMetricsMap.get(queries.get(0)).get(0).getTags().toString());
		assertEquals("{1477386300=7.0, 1477386500=6.0, 1477386600=7.0}", queryMetricsMap.get(queries.get(0)).get(0).getDatapoints().toString());
	}
	
	private  List<Metric> getMetricsFromMetricString(String content){
		List<Metric> metrics = null;
		try {
			ResultSet set = _mapper.readValue(content, new TypeReference<ResultSet>() { });
			metrics = set.getMetrics();
		} catch (IOException e) {
			fail("Failed to deserialize metrics. Reason: " + e.getMessage());
		}
		return metrics;
	}
}
/* Copyright (c) 2016, Salesforce.com, Inc.  All rights reserved. */

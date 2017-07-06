package com.salesforce.dva.argus.service.tsdb;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;

import com.salesforce.dva.argus.AbstractTest;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.metric.transform.Transform;
import com.salesforce.dva.argus.service.metric.transform.TransformFactory;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.service.tsdb.WriteThroughCachedTSDBService;
import com.salesforce.dva.argus.service.tsdb.MetricQuery.Aggregator;

@RunWith(org.mockito.runners.MockitoJUnitRunner.class)
public class WriteThroughCachedTSDBServiceTest extends AbstractTest {
	
	private static long PREV_HOUR_START = System.currentTimeMillis()/3600000 * 3600000 - 3600000;
	
	@Mock private TSDBService _tsdbServiceMock;
	private WriteThroughCachedTSDBService _writeThroughCachedTSDBService;
	
	@Before
	public void setup() {
		Mockito.doNothing().when(_tsdbServiceMock).putMetrics(Mockito.anyListOf(Metric.class));
		TransformFactory factory = new TransformFactory(_tsdbServiceMock);
		_writeThroughCachedTSDBService = new WriteThroughCachedTSDBService(system.getConfiguration(),
				system.getServiceFactory().getMonitorService(), system.getServiceFactory().getCacheService(), _tsdbServiceMock, factory);
		system.getServiceFactory().getCacheService().clear();
	}
	
	@Test
	public void testPutAndGetMetricsWithOneTag() {
		
		Map<String, String> tags = new HashMap<>();
		tags.put("host", "shared1-argusws1-1-prd.eng.sfdc.net");
		Metric metric = createMetric1(tags);
		_writeThroughCachedTSDBService.putMetrics(Arrays.asList(metric));
		
		MetricQuery query = new MetricQuery("scope", "metric", tags, PREV_HOUR_START, System.currentTimeMillis());
		
		Metric actualMetric = _writeThroughCachedTSDBService.getMetrics(Arrays.asList(query)).get(query).get(0);
		
		assertEquals(metric.getDatapoints(), actualMetric.getDatapoints());
	}
	
	@Test
	public void testPutAndGetMetricsWithOneTagAndDownsample() {
		
		Map<String, String> tags = new HashMap<>();
		tags.put("host", "shared1-argusws1-1-prd.eng.sfdc.net");
		Metric metric = createMetric1(tags);
		_writeThroughCachedTSDBService.putMetrics(Arrays.asList(metric));
		
		MetricQuery query = new MetricQuery("scope", "metric", tags, PREV_HOUR_START, System.currentTimeMillis());
		query.setDownsampler(Aggregator.AVG);
		query.setDownsamplingPeriod(5 * 60 * 1000L);
		Metric actualMetric = _writeThroughCachedTSDBService.getMetrics(Arrays.asList(query)).get(query).get(0);
		
		assertEquals(11, actualMetric.getDatapoints().size());
	}
	
	@Test
	public void testPutAndGetMetricsWithOneTagTrimData() {
		
		Map<String, String> tags = new HashMap<>();
		tags.put("host", "shared1-argusws1-1-prd.eng.sfdc.net");
		Metric metric = createMetric1(tags);
		_writeThroughCachedTSDBService.putMetrics(Arrays.asList(metric));
		
		long queryStart = PREV_HOUR_START + 10 * 60000;
		MetricQuery query = new MetricQuery("scope", "metric", tags, queryStart, System.currentTimeMillis());
		Metric actualMetric = _writeThroughCachedTSDBService.getMetrics(Arrays.asList(query)).get(query).get(0);
		
		Map<Long, Double> expected = new TreeMap<>();
		for(Long timestamp : metric.getDatapoints().keySet()) {
			if(timestamp >= queryStart) {
				expected.put(timestamp, metric.getDatapoints().get(timestamp));
			}
		}
		
		assertEquals(expected, actualMetric.getDatapoints());
	}
	
	
	@Test
	public void testPutAndGetTwoTags_AggregateSUMAcrossOneTag() {
		
		Map<String, String> tags = new HashMap<>();
		tags.put("host", "shared1-argusws1-1-prd.eng.sfdc.net");
		tags.put("port", "4466");
		Metric metric1 = createMetric1(tags);
		
		tags.clear();
		tags.put("host", "shared1-argusws1-1-prd.eng.sfdc.net");
		tags.put("port", "4477");
		Metric metric2 = createMetric1(tags);
		_writeThroughCachedTSDBService.putMetrics(Arrays.asList(metric1, metric2));
		
		Transform sumTransform = new TransformFactory(system.getServiceFactory().getTSDBService()).getTransform(TransformFactory.Function.SUM.getName());
		List<Metric> expected = sumTransform.transform(Arrays.asList(metric1, metric2));
		
		Map<String, String> queryTags = new HashMap<>();
		queryTags.put("host", "shared1-argusws1-1-prd.eng.sfdc.net");
		MetricQuery query = new MetricQuery("scope", "metric", queryTags, PREV_HOUR_START, System.currentTimeMillis());
		query.setAggregator(Aggregator.SUM);
		List<Metric> actual = _writeThroughCachedTSDBService.getMetrics(Arrays.asList(query)).get(query);
		
		
		assertEquals(1, actual.size());
		assertEquals(metric1.getDatapoints().size(), actual.get(0).getDatapoints().size());
		assertEquals(expected, actual);
	}
	
	@Test
	public void testPutAndGetTwoTags_AggregateAVGAcrossOneTag() {
		
		Map<String, String> tags = new HashMap<>();
		tags.put("host", "shared1-argusws1-1-prd.eng.sfdc.net");
		tags.put("port", "4466");
		Metric metric1 = createMetric1(tags);
		
		tags.clear();
		tags.put("host", "shared1-argusws1-1-prd.eng.sfdc.net");
		tags.put("port", "4477");
		Metric metric2 = createMetric1(tags);
		_writeThroughCachedTSDBService.putMetrics(Arrays.asList(metric1, metric2));
		
		Transform sumTransform = new TransformFactory(system.getServiceFactory().getTSDBService()).getTransform(TransformFactory.Function.SUM.getName());
		List<Metric> expected = sumTransform.transform(Arrays.asList(metric1, metric2));
		
		Map<String, String> queryTags = new HashMap<>();
		queryTags.put("host", "shared1-argusws1-1-prd.eng.sfdc.net");
		MetricQuery query = new MetricQuery("scope", "metric", queryTags, PREV_HOUR_START, System.currentTimeMillis());
		query.setAggregator(Aggregator.AVG);
		List<Metric> actual = _writeThroughCachedTSDBService.getMetrics(Arrays.asList(query)).get(query);
		
		
		assertEquals(1, actual.size());
		assertEquals(metric1.getDatapoints().size(), actual.get(0).getDatapoints().size());
		assertEquals(expected, actual);
	}
	
	@Test
	public void testPutAndGetThreeTags_AggregateAcrossOneTag() {
		
		Map<String, String> tags = new HashMap<>();
		tags.put("host", "shared1-argusws1-1-prd.eng.sfdc.net");
		tags.put("port", "4466");
		tags.put("route", "1");
		Metric metric1 = createMetric1(tags);
		
		tags.clear();
		tags.put("host", "shared1-argusws1-1-prd.eng.sfdc.net");
		tags.put("port", "4466");
		tags.put("route", "2");
		Metric metric2 = createMetric1(tags);
		
		tags.put("host", "shared1-argusws1-1-prd.eng.sfdc.net");
		tags.put("port", "4477");
		tags.put("route", "1");
		Metric metric3 = createMetric1(tags);
		
		tags.clear();
		tags.put("host", "shared1-argusws1-1-prd.eng.sfdc.net");
		tags.put("port", "4477");
		tags.put("route", "2");
		Metric metric4 = createMetric1(tags);
		
		tags.put("host", "shared1-argusws1-2-prd.eng.sfdc.net");
		tags.put("port", "4466");
		tags.put("route", "1");
		Metric metric5 = createMetric1(tags);
		
		tags.clear();
		tags.put("host", "shared1-argusws1-2-prd.eng.sfdc.net");
		tags.put("port", "4466");
		tags.put("route", "2");
		Metric metric6 = createMetric1(tags);
		
		tags.put("host", "shared1-argusws1-2-prd.eng.sfdc.net");
		tags.put("port", "4477");
		tags.put("route", "1");
		Metric metric7 = createMetric1(tags);
		
		tags.clear();
		tags.put("host", "shared1-argusws1-2-prd.eng.sfdc.net");
		tags.put("port", "4477");
		tags.put("route", "2");
		Metric metric8 = createMetric1(tags);
		
		
		_writeThroughCachedTSDBService.putMetrics(Arrays.asList(metric1, metric2, metric3, metric4, metric5, metric6, metric7, metric8));
		
		Map<String, String> queryTags = new HashMap<>();
		queryTags.put("host", "shared1-argusws1-1-prd.eng.sfdc.net");
		queryTags.put("port", "*");
		MetricQuery query = new MetricQuery("scope", "metric", queryTags, PREV_HOUR_START, System.currentTimeMillis());
		List<Metric> metrics = _writeThroughCachedTSDBService.getMetrics(Arrays.asList(query)).get(query);
		
		assertEquals(2, metrics.size());
	}
	
	
	@Test
	public void testPutAndGetSingleTagMultipleValues_QueryTagsContainOR() {
		
		Map<String, String> tags = new HashMap<>();
		tags.put("host", "shared1-argusws1-1-prd.eng.sfdc.net");
		Metric metric1 = createMetric1(tags);
		
		tags.clear();
		tags.put("host", "shared1-argusws1-2-prd.eng.sfdc.net");
		Metric metric2 = createMetric1(tags);
		
		tags.clear();
		tags.put("host", "shared1-argusws1-3-prd.eng.sfdc.net");
		Metric metric3 = createMetric1(tags);
		
		tags.clear();
		tags.put("host", "shared1-argusws1-4-prd.eng.sfdc.net");
		Metric metric4 = createMetric1(tags);
		
		_writeThroughCachedTSDBService.putMetrics(Arrays.asList(metric1, metric2, metric3, metric4));
		
		Map<String, String> queryTags = new HashMap<>();
		queryTags.put("host", "shared1-argusws1-2-prd.eng.sfdc.net|shared1-argusws1-4-prd.eng.sfdc.net");
		MetricQuery query = new MetricQuery("scope", "metric", queryTags, PREV_HOUR_START, System.currentTimeMillis());
		List<Metric> metrics = _writeThroughCachedTSDBService.getMetrics(Arrays.asList(query)).get(query);
		
		assertTrue(metrics.size() == 2);
		assertTrue(metrics.get(0).getTag("host").matches("shared1-argusws1-2-prd.eng.sfdc.net|shared1-argusws1-4-prd.eng.sfdc.net"));
		assertTrue(metrics.get(1).getTag("host").matches("shared1-argusws1-2-prd.eng.sfdc.net|shared1-argusws1-4-prd.eng.sfdc.net"));
	}
	
	@Test
	public void testPutAndGetSingleTagMultipleValues_QueryTagContainsStar() {
		
		Map<String, String> tags = new HashMap<>();
		tags.put("host", "shared1-argusws1-1-prd.eng.sfdc.net");
		Metric metric1 = createMetric1(tags);
		
		tags.clear();
		tags.put("host", "shared1-argusws1-2-prd.eng.sfdc.net");
		Metric metric2 = createMetric1(tags);
		
		tags.clear();
		tags.put("host", "shared1-argusws1-3-prd.eng.sfdc.net");
		Metric metric3 = createMetric1(tags);
		
		_writeThroughCachedTSDBService.putMetrics(Arrays.asList(metric1, metric2, metric3));
		
		Map<String, String> queryTags = new HashMap<>();
		queryTags.put("host", "*");
		MetricQuery query = new MetricQuery("scope", "metric", queryTags, PREV_HOUR_START, System.currentTimeMillis());
		List<Metric> metrics = _writeThroughCachedTSDBService.getMetrics(Arrays.asList(query)).get(query);
		
		assertTrue(metrics.size() == 3);
		assertTrue(metrics.get(0).getTag("host").matches("shared1-argusws1-1-prd.eng.sfdc.net|shared1-argusws1-2-prd.eng.sfdc.net|shared1-argusws1-3-prd.eng.sfdc.net"));
		assertTrue(metrics.get(1).getTag("host").matches("shared1-argusws1-1-prd.eng.sfdc.net|shared1-argusws1-2-prd.eng.sfdc.net|shared1-argusws1-3-prd.eng.sfdc.net"));
		assertTrue(metrics.get(2).getTag("host").matches("shared1-argusws1-1-prd.eng.sfdc.net|shared1-argusws1-2-prd.eng.sfdc.net|shared1-argusws1-3-prd.eng.sfdc.net"));
	}
	
	@Test
	public void testPutAndGetMultipleTagsMultipleValues_QueryTagsContainOR() {
		
		Map<String, String> tags = new HashMap<>();
		tags.put("host", "shared1-argusws1-1-prd.eng.sfdc.net");
		tags.put("port", "4466");
		Metric metric1 = createMetric1(tags);
		
		tags.clear();
		tags.put("host", "shared1-argusws1-1-prd.eng.sfdc.net");
		tags.put("port", "4477");
		Metric metric2 = createMetric1(tags);
		
		tags.clear();
		tags.put("host", "shared1-argusws1-2-prd.eng.sfdc.net");
		tags.put("port", "4466");
		Metric metric3 = createMetric1(tags);
		
		tags.clear();
		tags.put("host", "shared1-argusws1-2-prd.eng.sfdc.net");
		tags.put("port", "4477");
		Metric metric4 = createMetric1(tags);
		
		tags.clear();
		tags.put("host", "shared1-argusws1-3-prd.eng.sfdc.net");
		tags.put("port", "4466");
		Metric metric5 = createMetric1(tags);
		
		tags.clear();
		tags.put("host", "shared1-argusws1-3-prd.eng.sfdc.net");
		tags.put("port", "4477");
		Metric metric6 = createMetric1(tags);
		
		_writeThroughCachedTSDBService.putMetrics(Arrays.asList(metric1, metric2, metric3, metric4, metric5, metric6));
		
		Map<String, String> queryTags = new HashMap<>();
		queryTags.put("host", "shared1-argusws1-2-prd.eng.sfdc.net|shared1-argusws1-3-prd.eng.sfdc.net");
		queryTags.put("port", "4466|4488");
		MetricQuery query = new MetricQuery("scope", "metric", queryTags, PREV_HOUR_START, System.currentTimeMillis());
		List<Metric> metrics = _writeThroughCachedTSDBService.getMetrics(Arrays.asList(query)).get(query);
		
		assertTrue(metrics.size() == 2);
		assertTrue(metrics.get(0).getTag("host").matches("shared1-argusws1-2-prd.eng.sfdc.net|shared1-argusws1-3-prd.eng.sfdc.net"));
		assertTrue(metrics.get(0).getTag("port").equals("4466"));
		assertTrue(metrics.get(1).getTag("host").matches("shared1-argusws1-2-prd.eng.sfdc.net|shared1-argusws1-3-prd.eng.sfdc.net"));
		assertTrue(metrics.get(1).getTag("port").equals("4466"));
	}
	
	@Test
	public void testDatapointsBrokenOnHourlyBoundary() {
		
		Map<Long, Double> datapoints = new TreeMap<>();
		
		for(int i=0; i<100; i++) {
			datapoints.put((System.currentTimeMillis() / 60000 * 60000) + (i * 60000), 1.0);
		}
		
		Map<Long, Map<Long, Double>> datapointsBrokenOnHouryBoundary = WriteThroughCachedTSDBService._breakDatapointsByHourlyBoundary(datapoints);
		assertTrue(datapointsBrokenOnHouryBoundary.size() == 2 || datapointsBrokenOnHouryBoundary.size() == 3);
	}
	
	@Test
	public void testConvertDatapointsToByteArrAndBack() {
		
		Map<Long, Double> datapoints = new TreeMap<>();
		datapoints.put(1496772240L, 13488749694.984127);
		datapoints.put(1496772300L, 11917782622.47619);
		datapoints.put(1496772360L, 12190658378.15873);
		datapoints.put(1496772420L, 11933017011.809525);
		datapoints.put(1496772480L, 12047358319.74603);
		datapoints.put(1496772540L, 12686119925.84127);
		datapoints.put(1496772600L, 12632496244.825397);
		datapoints.put(1496772660L, 12231557863.36508);
		datapoints.put(1496772720L, 12454423044.825397);
		
		Map<Long, Double> expected = new TreeMap<>();
		for(Map.Entry<Long, Double> entry : datapoints.entrySet()) {
			expected.put(entry.getKey() * 1000, entry.getValue());
		}
		
		byte[] arr = WriteThroughCachedTSDBService._convertDatapointsMapToBytes(1496772000L, datapoints);
		Map<Long, Double> convertedDatapoints = WriteThroughCachedTSDBService._convertDatapointsByteArrToMap(1496772000L, arr, 0, Long.MAX_VALUE);
		assertEquals(expected, convertedDatapoints);
	}
	
	@Test
	public void testCacheKeyConstruction() {
		
		Metric metric = new Metric("scope", "metric");
		metric.setTag("device", "device1");
		metric.setTag("port", "port1");
		
		CharSequence seq = WriteThroughCachedTSDBService._constructCackeKeyWithoutBaseTimestamp(metric);
		assertEquals("scope:metric:device:device1:port:port1:", seq.toString());		
	}
	
	@Test
	public void testCacheKeyConstruction_NoTags() {
		
		Metric metric = new Metric("scope", "metric");
		
		CharSequence seq = WriteThroughCachedTSDBService._constructCackeKeyWithoutBaseTimestamp(metric);
		assertEquals("scope:metric:", seq.toString());		
	}
	
	
	private Metric createMetric1(Map<String, String> tags) {
		
		Metric metric = new Metric("scope", "metric");
		Map<Long, Double> datapoints = new TreeMap<>();
		
		for(int i=0; i<55; i++) {
			datapoints.put(PREV_HOUR_START + i * 60000, 1.0);
		}
		
		metric.setDatapoints(datapoints);
		metric.setTags(tags);
		return metric;
	}
	
}

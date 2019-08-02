package com.salesforce.dva.argus.service.metric;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.salesforce.dva.argus.TestUtils;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.schema.ConsumerOffsetRecordList;
import com.salesforce.dva.argus.service.schema.ElasticSearchUtils;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.system.SystemConfiguration;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.RestClient;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.mockStatic;
import static org.powermock.api.mockito.PowerMockito.verifyStatic;

@PowerMockIgnore("*.ssl.*")
@RunWith(PowerMockRunner.class)
@PrepareForTest(ElasticSearchUtils.class)
public class ElasticSearchConsumerOffsetMetricsServiceTest {

	private static SystemConfiguration systemConfig;
	private static ElasticSearchConsumerOffsetMetricsService esConsumerOffsetMetricsService;
	private RestClient restClient;
	private static ObjectMapper mapper = new ObjectMapper();

	private String successReply = String.join("\n",
			"{" +
					"    \"took\": 55," +
					"    \"errors\": false," +
					"    \"items\": [" +
					"        {" +
					"            \"create\": {" +
					"                \"_index\": \"argus-akc-consumer-offset-index\"," +
					"                \"_id\": \"cdfd12850d42746257f8217899647c8b\"," +
					"                \"_version\": 1," +
					"                \"result\": \"created\"," +
					"                \"_shards\": {" +
					"                    \"total\": 2," +
					"                    \"successful\": 1," +
					"                    \"failed\": 0" +
					"                }," +
					"                \"created\": true," +
					"                \"status\": 201" +
					"            }" +
					"        }" +
					"    ]" +
					"}");

	private String getReply = "\n" +
			"{\n" +
			"  \"took\" : 26,\n" +
			"  \"timed_out\" : false,\n" +
			"  \"_shards\" : {\n" +
			"    \"total\" : 5,\n" +
			"    \"successful\" : 5,\n" +
			"    \"skipped\" : 0,\n" +
			"    \"failed\" : 0\n" +
			"  },\n" +
			"  \"hits\" : {\n" +
			"    \"total\" : {\n" +
			"      \"value\" : 53,\n" +
			"      \"relation\" : \"eq\"\n" +
			"    },\n" +
			"    \"max_score\" : null,\n" +
			"    \"hits\" : [ ]\n" +
			"  },\n" +
			"  \"aggregations\" : {\n" +
			"    \"max_topic_offset_per_unit_time_greater_than\" : {\n" +
			"      \"doc_count_error_upper_bound\" : 0,\n" +
			"      \"sum_other_doc_count\" : 0,\n" +
			"      \"buckets\" : [\n" +
			"        {\n" +
			"          \"key\" : \"sfdc.prod.ajna__prd.ajna_local__metrics\",\n" +
			"          \"doc_count\" : 28,\n" +
			"          \"max_offset_per_unit_time_greater_than\" : {\n" +
			"            \"buckets\" : [\n" +
			"              {\n" +
			"                \"key_as_string\" : \"2019-06-19T00:00:00.000Z\",\n" +
			"                \"key\" : 1560902400000,\n" +
			"                \"doc_count\" : 7,\n" +
			"                \"max_offset_greater_than\" : {\n" +
			"                  \"value\" : 19676.0\n" +
			"                }\n" +
			"              },\n" +
			"              {\n" +
			"                \"key_as_string\" : \"2019-06-21T00:00:00.000Z\",\n" +
			"                \"key\" : 1561075200000,\n" +
			"                \"doc_count\" : 10,\n" +
			"                \"max_offset_greater_than\" : {\n" +
			"                  \"value\" : 21635.0\n" +
			"                }\n" +
			"              },\n" +
			"              {\n" +
			"                \"key_as_string\" : \"2019-06-23T00:00:00.000Z\",\n" +
			"                \"key\" : 1561248000000,\n" +
			"                \"doc_count\" : 11,\n" +
			"                \"max_offset_greater_than\" : {\n" +
			"                  \"value\" : 22659.0\n" +
			"                }\n" +
			"              }\n" +
			"            ]\n" +
			"          }\n" +
			"        },\n" +
			"        {\n" +
			"          \"key\" : \"sfdc.prod.ajna__phx.ajna_local__metrics\",\n" +
			"          \"doc_count\" : 25,\n" +
			"          \"max_offset_per_unit_time_greater_than\" : {\n" +
			"            \"buckets\" : [\n" +
			"              {\n" +
			"                \"key_as_string\" : \"2019-06-19T00:00:00.000Z\",\n" +
			"                \"key\" : 1560902400000,\n" +
			"                \"doc_count\" : 8,\n" +
			"                \"max_offset_greater_than\" : {\n" +
			"                  \"value\" : 21455.0\n" +
			"                }\n" +
			"              },\n" +
			"              {\n" +
			"                \"key_as_string\" : \"2019-06-21T00:00:00.000Z\",\n" +
			"                \"key\" : 1561075200000,\n" +
			"                \"doc_count\" : 7,\n" +
			"                \"max_offset_greater_than\" : {\n" +
			"                  \"value\" : 21981.0\n" +
			"                }\n" +
			"              },\n" +
			"              {\n" +
			"                \"key_as_string\" : \"2019-06-23T00:00:00.000Z\",\n" +
			"                \"key\" : 1561248000000,\n" +
			"                \"doc_count\" : 10,\n" +
			"                \"max_offset_greater_than\" : {\n" +
			"                  \"value\" : 22461.0\n" +
			"                }\n" +
			"              }\n" +
			"            ]\n" +
			"          }\n" +
			"        }\n" +
			"      ]\n" +
			"    }\n" +
			"  }\n" +
			"}\n";

	private String queryMustTermRange = "{\n" +
			"  \"aggs\": {\n" +
			"    \"max_topic_offset_per_unit_time_greater_than\": {\n" +
			"      \"terms\": {\n" +
			"        \"field\": \"topic.raw\"\n" +
			"      },\n" +
			"      \"aggs\": {\n" +
			"        \"max_offset_per_unit_time_greater_than\": {\n" +
			"          \"date_histogram\": {\n" +
			"            \"field\": \"ts\",\n" +
			"            \"interval\": \"5m\"\n" +
			"          },\n" +
			"          \"aggs\": {\n" +
			"            \"max_offset_greater_than\": {\n" +
			"              \"max\": {\n" +
			"                \"field\": \"value\"\n" +
			"              }\n" +
			"            }\n" +
			"          }\n" +
			"        }\n" +
			"      }\n" +
			"    }\n" +
			"  },\n" +
			"  \"query\": {\n" +
			"    \"bool\": {\n" +
			"      \"must\": [\n" +
			"        {\n" +
			"          \"range\": {\n" +
			"            \"ts\": {\n" +
			"              \"gte\": \"1557809359000\",\n" +
			"              \"lte\": \"1557809599000\"\n" +
			"            }\n" +
			"          }\n" +
			"        }\n" +
			"      ],\n" +
			"      \"filter\": [\n" +
			"        {\n" +
			"          \"regexp\": {\n" +
			"            \"topic.raw\": \"topic\"\n" +
			"          }\n" +
			"        }\n" +
			"      ]\n" +
			"    }\n" +
			"  },\n" +
			"  \"from\": 0,\n" +
			"  \"size\": 10000\n" +
			"}";

	private Map<String, String> defaultTags = new HashMap<>();

	private String convertToPrettyJson(String jsonString) {
		JsonParser parser = new JsonParser();
		Gson gson = new GsonBuilder().setPrettyPrinting().create();

		JsonElement el = parser.parse(jsonString);
		return gson.toJson(el);
	}

	@BeforeClass
	public static void setUpClass() {
		Properties config = new Properties();
		systemConfig = new SystemConfiguration(config);
		MonitorService mockedMonitor = mock(MonitorService.class);
		mockStatic(ElasticSearchUtils.class);
		ElasticSearchUtils mockedElasticSearchUtils = mock(ElasticSearchUtils.class);
		esConsumerOffsetMetricsService = new ElasticSearchConsumerOffsetMetricsService(systemConfig, mockedMonitor, mockedElasticSearchUtils);
	}

	@Before
	public void setUp() {
		TestUtils.setStaticField(ElasticSearchConsumerOffsetMetricsService.class, "INDEX_MAX_RESULT_WINDOW", 10000);
		defaultTags.put("key1", "val1");
		defaultTags.put("key2", "val2");
	}

	@Test
	public void testConstructQuery() throws IOException {
		Map<String, String> tags = new HashMap<>();
		tags.put("service", "scope*");
		tags.put("groupId", "groupId*");
		tags.put("topic", "*");
		esConsumerOffsetMetricsService = _initializeSpyService(esConsumerOffsetMetricsService, getReply, false);
		MetricQuery mQ = new MetricQuery("test", "test", tags, 0L, 1L);
		mQ.setDownsamplingPeriod((long)(2 * 60 * 1000));
		mQ.setDownsampler(MetricQuery.Aggregator.MAX);
		String  actualOutput = esConsumerOffsetMetricsService.constructQuery(mQ, 0, 0);
		String expectedOutput  = "{\n" +
				"  \"aggs\": {\n" +
				"    \"max_topic_offset_per_unit_time_greater_than\": {\n" +
				"      \"terms\": {\n" +
				"        \"field\": \"topic.raw\"\n" +
				"      },\n" +
				"      \"aggs\": {\n" +
				"        \"max_offset_per_unit_time_greater_than\": {\n" +
				"          \"date_histogram\": {\n" +
				"            \"field\": \"ts\",\n" +
				"            \"interval\": \"2m\"\n" +
				"          },\n" +
				"          \"aggs\": {\n" +
				"            \"max_offset_greater_than\": {\n" +
				"              \"max\": {\n" +
				"                \"field\": \"value\"\n" +
				"              }\n" +
				"            }\n" +
				"          }\n" +
				"        }\n" +
				"      }\n" +
				"    }\n" +
				"  },\n" +
				"  \"query\": {\n" +
				"    \"bool\": {\n" +
				"      \"must\": [\n" +
				"        {\n" +
				"          \"range\": {\n" +
				"            \"ts\": {\n" +
				"              \"gte\": \"0\",\n" +
				"              \"lte\": \"1000\"\n" +
				"            }\n" +
				"          }\n" +
				"        }\n" +
				"      ],\n" +
				"      \"filter\": [\n" +
				"        {\n" +
				"          \"regexp\": {\n" +
				"            \"topic.raw\": \".*\"\n" +
				"          }\n" +
				"        },\n" +
				"        {\n" +
				"          \"regexp\": {\n" +
				"            \"tags.raw\": \".*([\\\"]groupId[\\\"]:[\\\"]groupId.*[\\\"]).*\"\n" +
				"          }\n" +
				"        },\n" +
				"        {\n" +
				"          \"regexp\": {\n" +
				"            \"tags.raw\": \".*([\\\"]service[\\\"]:[\\\"]scope.*[\\\"]).*\"\n" +
				"          }\n" +
				"        }\n" +
				"      ]\n" +
				"    }\n" +
				"  },\n" +
				"  \"from\": 0,\n" +
				"  \"size\": 0\n" +
				"}";

		assertEquals(convertToPrettyJson(expectedOutput), convertToPrettyJson(actualOutput));
	}

	@Ignore
	@Test
	public void testConsumerOffsetSchemaRecordListMapper() throws IOException {
		mapper = ElasticSearchConsumerOffsetMetricsService.getMetricObjectMapper(new ConsumerOffsetRecordList.IndexSerializer(), new ConsumerOffsetRecordList.Deserializer());

		Long cTime = System.currentTimeMillis();
		ConsumerOffsetMetric record1 = new ConsumerOffsetMetric("metric", "topic", cTime, 0.0, defaultTags);
		ConsumerOffsetRecordList recordList = new ConsumerOffsetRecordList(Arrays.asList(record1), ElasticSearchUtils.HashAlgorithm.fromString("MD5"));

		String serialized = mapper.writeValueAsString(recordList);

		String[] lines = serialized.split("\\r?\\n");

		String expectedIndexName = getExpectedIndexName();
		JsonNode root = mapper.readTree(lines[0]);
		String actualIndexName = root.get("index").get("_index").asText();
		assertEquals(expectedIndexName, actualIndexName);

		String expectedSerializedMetric =
				"{\"metric\":\"metric\",\"topic\":\"topic\",\"value\":\"0.0\",\"ts\":\"" + cTime + "\",\"tags\":\"{\\\"key1\\\":\\\"val1\\\",\\\"key2\\\":\\\"val2\\\"}\"}";
		assertEquals(expectedSerializedMetric, lines[1]);
	}

	private String getExpectedIndexName() {
		String month = String.valueOf(Calendar.getInstance().get(Calendar.MONTH) + 1);
		String year = String.valueOf(Calendar.getInstance().get(Calendar.YEAR));
		String day = String.valueOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH));
		if (month.length() == 1) {
			month = "0" + month;
		}

		if (day.length() == 1) {
			day = "0" + day;
		}

		return MessageFormat.format("argus-akc-consumer-offset-{0}-{1}-{2}", year, month, day);

	}

	@Test
	public void testPutMetricsUsingOffsetIndex() throws IOException {
		Long currentTime = System.currentTimeMillis();
		ElasticSearchConsumerOffsetMetricsService spyService = _initializeSpyService(esConsumerOffsetMetricsService, successReply, true);
		List<Metric> metrics = new ArrayList<>();

		Metric record1 = new Metric("scope", "metric");
		record1.setTag("topic", "topicV");
		record1.addDatapoint(currentTime, 0.0);
		metrics.add(record1);

		Metric record2 = new Metric("scope2", "metric2");
		record2.setTag("topic", "topicV");
		record2.setTag("groupId", "groupId");
		record2.addDatapoint(currentTime, 1.0);
		metrics.add(record2);

		spyService.putMetrics(metrics);

		ArgumentCaptor<String> requestCaptorUrl = ArgumentCaptor.forClass(String.class);
		ArgumentCaptor<String> requestCaptorBody = ArgumentCaptor.forClass(String.class);

		verifyStatic(ElasticSearchUtils.class, times(1));
		ElasticSearchUtils.performESRequest(eq(restClient), requestCaptorUrl.capture(), requestCaptorBody.capture());

		String expectedURL = "_bulk";
		assertEquals(expectedURL, requestCaptorUrl.getValue());

		String[] lines = requestCaptorBody.getValue().split("\\r?\\n");

		String expectedIndexName = getExpectedIndexName();
		JsonNode root = mapper.readTree(lines[0]);
		String actualIndexName = root.get("index").get("_index").asText();
		assertEquals(expectedIndexName, actualIndexName);

		String expectedSerializedMetric1 =
				"{\"metric\":\"metric\",\"topic\":\"topicV\",\"value\":\"0.0\",\"ts\":\"" + currentTime + "\",\"tags\":\"{\\\"service\\\":\\\"scope\\\"}\"}";
		String expectedSerializedMetric2 =
				"{\"metric\":\"metric2\",\"topic\":\"topicV\",\"value\":\"1.0\",\"ts\":\"" + currentTime + "\",\"tags\":\"{\\\"groupId\\\":\\\"groupId\\\",\\\"service\\\":\\\"scope2\\\"}\"}";
		List<String> expectedOutput = new ArrayList<>(Arrays.asList(expectedSerializedMetric1, expectedSerializedMetric2));
		Collections.sort(expectedOutput);
		List<String> actualOutput = new ArrayList<>(Arrays.asList(lines[1], lines[3]));
		Collections.sort(actualOutput);
		assertEquals(Arrays.asList(expectedOutput), Arrays.asList(actualOutput));
	}

	@Test
	public void testGetMetrics() throws IOException {
		MetricQuery metricQuery = new MetricQuery("scope1", "metric1", null, 1557809359L, 1557809599L);
		metricQuery.setTag("topic", "topic");
		metricQuery.setDownsampler(MetricQuery.Aggregator.MAX);
		metricQuery.setDownsamplingPeriod(5 * 60 * 1000L);
		List<MetricQuery> queries = new ArrayList<>();
		queries.add(metricQuery);

		ElasticSearchConsumerOffsetMetricsService spyService = _initializeSpyService(esConsumerOffsetMetricsService, getReply, false);

		Map<MetricQuery, List<Metric>> metricsResult = spyService.getMetrics(queries);
		String expectedMetric =
				"[namespace=>null, scope=>ajna.consumer, metric=>metric.consumer.lag, tags=>{topic=sfdc.prod.ajna__phx.ajna_local__metrics}, datapoints=>{1560902400000=21455.0, 1561075200000=21981.0, 1561248000000=22461.0}, " +
				"namespace=>null, scope=>ajna.consumer, metric=>metric.consumer.lag, tags=>{topic=sfdc.prod.ajna__prd.ajna_local__metrics}, datapoints=>{1560902400000=19676.0, 1561075200000=21635.0, 1561248000000=22659.0}]";

		assertEquals(expectedMetric, metricsResult.get(metricQuery).toString());
		ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
		verify(restClient, times(1)).performRequest(requestCaptor.capture());
		Request capturedRequest = requestCaptor.getValue();

		String queryJson = convertToPrettyJson(EntityUtils.toString(capturedRequest.getEntity()));
		assertEquals(queryMustTermRange, queryJson);
		String expectedURL = "/argus-akc-consumer-offset-*/_search";
		assertEquals(expectedURL, capturedRequest.getEndpoint());
	}

	@Test (expected = RuntimeException.class)
	public void testGetMetricsExceedingLimit(){
		MetricQuery metricQuery = new MetricQuery("scope1", "metric1", null, 1557809359L, 1557809599L);
		metricQuery.setTag("topic", "topic");
		metricQuery.setDownsampler(MetricQuery.Aggregator.MAX);
		metricQuery.setDownsamplingPeriod(5 * 60 * 1000L);
		List<MetricQuery> queries = new ArrayList<>();
		queries.add(metricQuery);

		ElasticSearchConsumerOffsetMetricsService spyService = null;
		try {
			spyService = _initializeSpyService(esConsumerOffsetMetricsService, getReply, false);
		} catch (IOException e) {
			fail();
		}
		Whitebox.setInternalState(spyService, "INDEX_MAX_RESULT_WINDOW", 1);
		spyService.getMetrics(queries);
	}

	private ElasticSearchConsumerOffsetMetricsService _initializeSpyService(ElasticSearchConsumerOffsetMetricsService service,
																			String reply, boolean isPut) throws IOException {

		restClient = mock(RestClient.class);
		service.setESRestClient(restClient);
		mockStatic(ElasticSearchUtils.class);
		if (isPut) {
			when(ElasticSearchUtils.performESRequest(eq(restClient), any(), any())).thenReturn(mapper.readValue(reply, ElasticSearchUtils.PutResponse.class));
		} else {

			when(ElasticSearchUtils.extractResponse(any())).thenReturn(reply);
			mapper = ElasticSearchConsumerOffsetMetricsService.getMetricObjectMapper(new ConsumerOffsetRecordList.IndexSerializer(), new ConsumerOffsetRecordList.Deserializer());
			when(ElasticSearchUtils.toEntity(any(), any(),any())).thenCallRealMethod();
		}

		when(ElasticSearchUtils.convertTimestampToMillis(any())).thenCallRealMethod();
		ElasticSearchConsumerOffsetMetricsService spyService = spy(service);

		return spyService;
	}
}
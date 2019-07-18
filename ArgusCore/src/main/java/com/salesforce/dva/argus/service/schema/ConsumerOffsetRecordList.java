package com.salesforce.dva.argus.service.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.salesforce.dva.argus.service.metric.ConsumerOffsetMetric;
import com.salesforce.dva.argus.service.metric.ElasticSearchConsumerOffsetMetricsService;
import com.salesforce.dva.argus.service.schema.ElasticSearchUtils.HashAlgorithm;
import net.openhft.hashing.LongHashFunction;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.salesforce.dva.argus.service.metric.ElasticSearchConsumerOffsetMetricsService.METRIC_NAME;
import static com.salesforce.dva.argus.service.metric.ElasticSearchConsumerOffsetMetricsService.SCOPE_NAME;

public class ConsumerOffsetRecordList implements RecordFinder<ConsumerOffsetMetric> {

	private Map<String, ConsumerOffsetMetric> _idToMetricMap = new HashMap<>();
	private String _scrollID;
	private static ObjectMapper mapper = new ObjectMapper();

	public ConsumerOffsetRecordList(List<ConsumerOffsetMetric> metrics, String scrollID) {
		int count = 0;
		for(ConsumerOffsetMetric metric : metrics) {
			_idToMetricMap.put(String.valueOf(count++), metric);
		}
		setScrollID(scrollID);
	}

	public ConsumerOffsetRecordList(List<ConsumerOffsetMetric> metrics, HashAlgorithm algorithm) {
		for(ConsumerOffsetMetric metric : metrics) {
			String id = null;
			metric.setTime(ElasticSearchUtils.convertTimestampToMillis(metric.getTime()));
			String metricKey = ConsumerOffsetMetric.getIdentifierFieldsAsString(metric);
			if(HashAlgorithm.XXHASH.equals(algorithm)) {
				id = String.valueOf(LongHashFunction.xx().hashChars(metricKey));
			} else {
				id = DigestUtils.md5Hex(metricKey);
			}
			_idToMetricMap.put(id, metric);
		}
	}

	@Override
	public List<ConsumerOffsetMetric> getRecords() {
		return new ArrayList<>(_idToMetricMap.values());
	}

	@Override
	public Set<String> getIdSet() {
		return _idToMetricMap.keySet();
	}

	@Override
	public String getScrollID() {
		return _scrollID;
	}

	@Override
	public void setScrollID(String scrollID) {
		this._scrollID = scrollID;
	}

	@Override
	public ConsumerOffsetMetric getRecord(String id) {
		return _idToMetricMap.get(id);
	}

	public static class IndexSerializer extends JsonSerializer<ConsumerOffsetRecordList> {

		public static final long MILLIS_IN_A_DAY = 86400000L;
		public static final long MAX_METRIC_AGE_MS = 30 * MILLIS_IN_A_DAY;

		@Override
		public void serialize(ConsumerOffsetRecordList list, JsonGenerator jgen, SerializerProvider provider)
				throws IOException {

			mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

			for(Map.Entry<String, ConsumerOffsetMetric> entry : list._idToMetricMap.entrySet()) {
				ConsumerOffsetMetric offsetMetric = entry.getValue();

				if(isMetricTimestampOld(offsetMetric.getTime())) continue;

				jgen.writeRaw("{ \"index\" : " +
						"{\"_index\" : \"" + getMetricIndex(offsetMetric.getTime()) + "\"," +
						"\"_type\": \"_doc\"," +
						"\"_id\" : \"" + entry.getKey()
						+ "\"}}");
				jgen.writeRaw(System.lineSeparator());
				Map<String, String> fieldsData = new HashMap<>();
				fieldsData.put(ConsumerOffsetRecordType.METRIC.getName(), offsetMetric.getMetric());
				fieldsData.put(ConsumerOffsetRecordType.TOPIC.getName(), offsetMetric.getTopic());
				fieldsData.put(ConsumerOffsetRecordType.TIMESERIES.getName(), Long.toString(offsetMetric.getTime()));
				fieldsData.put(ConsumerOffsetRecordType.VALUE.getName(), Double.toString(offsetMetric.getValue()));
				fieldsData.put(ConsumerOffsetRecordType.TAGS.getName(), mapper.writeValueAsString(offsetMetric.getTags()));
				jgen.writeRaw(mapper.writeValueAsString(fieldsData));
				jgen.writeRaw(System.lineSeparator());
			}
		}

		private boolean isMetricTimestampOld(Long timestampMillis) {
			return System.currentTimeMillis() - timestampMillis > MAX_METRIC_AGE_MS;
		}

		private String getMetricIndex(Long epochTimestamp) {
			Date metricDate = new Date(epochTimestamp);
			SimpleDateFormat formatter = new SimpleDateFormat(ElasticSearchConsumerOffsetMetricsService.DATE_FORMAT);
			String indexNameToAppend = String.format(ElasticSearchConsumerOffsetMetricsService.INDEX_FORMAT,
					ElasticSearchConsumerOffsetMetricsService.INDEX_TEMPLATE_PATTERN_START,
					formatter.format(metricDate));
			return indexNameToAppend;
		}
	}

	public static class Deserializer extends JsonDeserializer<ConsumerOffsetRecordList> {

		@Override
		public ConsumerOffsetRecordList deserialize(JsonParser jp, DeserializationContext context)
				throws IOException {

			String scrollID = null;
			List<ConsumerOffsetMetric> result = new ArrayList<>();

			JsonNode rootNode = jp.getCodec().readTree(jp);
			if(rootNode.has("_scroll_id")) {
				scrollID = rootNode.get("_scroll_id").asText();
			}
			JsonNode recordsPerTopic = rootNode.get("aggregations").get("max_topic_offset_per_unit_time_greater_than").get("buckets");

			if(JsonNodeType.ARRAY.equals(recordsPerTopic.getNodeType())) {
				Iterator<JsonNode> topicIter = recordsPerTopic.elements();
				topicIter.forEachRemaining(topicJson -> {
					JsonNode topicNode = topicJson.get("key");
					JsonNode recordsPerUnitTime = topicJson.get("max_offset_per_unit_time_greater_than").get("buckets");
					if (JsonNodeType.ARRAY.equals(recordsPerUnitTime.getNodeType())) {
						Iterator<JsonNode> timeIter = recordsPerUnitTime.elements();
						timeIter.forEachRemaining(valJson -> {
							JsonNode timestampNode = valJson.get("key");
							JsonNode valueNode = valJson.get("max_offset_greater_than").get("value");
							Map<String, String> tags = new HashMap<>();
							tags.put("service", SCOPE_NAME);
							ConsumerOffsetMetric consumerOffsetMetric = new ConsumerOffsetMetric(METRIC_NAME, topicNode.asText(), timestampNode.asLong(), valueNode.asDouble(), tags);
							result.add(consumerOffsetMetric);
						});
					}
				});
			}
			return new ConsumerOffsetRecordList(result, scrollID);
		}
	}

	/**
	 * Indicates the schema record field to be used for indexing in ES.
	 *
	 * @author  Sudhanshu Bahety (sudhanshu.bahety@salesforce.com)
	 */
	public static enum ConsumerOffsetRecordType {

		/** Match against the metric field. */
		METRIC("metric"),
		/** Match against the topic field. */
		TOPIC("topic"),
		/** Match against the value field. */
		VALUE("value"),
		/** Match against the timeseries field */
		TIMESERIES("ts"),
		/** Match against the tags field. */
		TAGS("tags");

		private String _name;

		ConsumerOffsetRecordType(String name) {
			_name = name;
		}

		/**
		 * Returns a given record type corresponding to the given name.
		 *
		 * @param   name  The case sensitive name to match against.  Cannot be null.
		 *
		 * @return  The corresponding record type or null if no matching record type exists.
		 */
		@JsonCreator
		public static ConsumerOffsetRecordType fromName(String name) {
			for (ConsumerOffsetRecordType type : ConsumerOffsetRecordType.values()) {
				if (type.getName().equalsIgnoreCase(name)) {
					return type;
				}
			}

			throw new IllegalArgumentException("Illegal record type: " + name);
		}

		/**
		 * Returns the record type name.
		 *
		 * @return  The record type name.
		 */
		public String getName() {
			return _name;
		}
	}
}

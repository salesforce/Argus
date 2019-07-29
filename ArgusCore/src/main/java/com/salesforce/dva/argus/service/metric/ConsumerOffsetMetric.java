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

import com.salesforce.dva.argus.entity.Metric;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/**
 * Entity to model the storage of offset metrics.
 * @author sudhanshu.bahety
 */
public class ConsumerOffsetMetric {

	private String metric;
	private String topic;
	private Long time;
	private Double value;
	private Map<String, String> tags;

	private static final String TAG_TOPIC = "topic";
	private static final String TAG_SERVICE = "service";
	private static final String ARGUS_CORE = "argus.core";

	private static final Logger logger = LoggerFactory.getLogger(ConsumerOffsetMetric.class);

	/**
	 *
	 * @param metric name of the metric
	 * @param topic name of the topic
	 * @param time time when metric was emitted
	 * @param value offset number for the topic at time
	 * @param tags additional tags
	 */
	public ConsumerOffsetMetric(String metric, String topic, Long time, Double value, Map<String, String> tags) {
		requireArgument(metric != null && !metric.isEmpty(), "Metric cannot be null or empty");
		requireArgument(topic != null && !topic.isEmpty(), "Topic cannot be null or empty");
		requireArgument(time != null, "Time cannot be null");
		requireArgument(value != null, "Value cannot be null");
		this.metric = metric;
		this.topic = topic;
		this.tags = tags;
		this.time = time;
		this.value = value;
	}

	/**
	 *
	 * @param m TSDB entity metric to be converted to consumer offset metric.
	 */
	public ConsumerOffsetMetric(Metric m) {
		validateMetric(m);
		this.topic = m.getTag(TAG_TOPIC);
		m.removeTag(TAG_TOPIC);
		m.setTag(TAG_SERVICE, m.getScope());

		this.metric = m.getMetric();
		this.tags = new HashMap<>(m.getTags());

		m.getDatapoints().forEach((time, value) -> {
			this.time = time;
			this.value = value;
		});
	}

	/**
	 *
	 * @return converts to TSDB Entity metric.
	 */
	public Metric convertToMetric() {
		Metric m = new Metric(ARGUS_CORE, this.metric);
		if (this.tags.containsKey(TAG_SERVICE)) {
			m.setScope(tags.get(TAG_SERVICE));
		}
		this.tags.remove(TAG_SERVICE);
		m.setTags(this.tags);
		m.setTag(TAG_TOPIC, this.topic);
		m.addDatapoint(this.time, this.value);
		return m;
	}

	/**
	 *
	 * @param metricList The list of TSDB metric that needs to be converted
	 * @return The list of consumer offset metrics after conversion.
	 */
	public static List<ConsumerOffsetMetric> convertToConsumerOffsetMetrics(List<Metric> metricList) {
		List<ConsumerOffsetMetric> cOMetricList = new ArrayList<>();
		metricList.forEach(m -> {
			try {
				cOMetricList.add(new ConsumerOffsetMetric(m));
			} catch (Exception ex) {
				logger.error("Failed converting Metric {} to Consumer Offset Metric", m, ex);
			}
		});

		return cOMetricList;
	}

	/**
	 *
	 * @param m Checks for required tag field in metric m needed before conversion.
	 */
	private static void validateMetric(Metric m) {
		requireArgument(m != null, "Cannot convert null Metric to Consumer Offset Metric");
		requireArgument(m.getTag(TAG_TOPIC) != null, "Topic tag not present in Metric");
		requireArgument(m.getNumOfDatapoints() == 1, "In order to convert metric, you can only have a single data point.");
	}

	/**
	 *
	 * @param consumerOffsetMetrics The list of consumer offset metric
	 * @return the list of converted TSDB metric
	 */
	public static List<Metric> convertToMetrics(List<ConsumerOffsetMetric> consumerOffsetMetrics) {
		Map<String, Metric> metricMap = new HashMap<>();
		consumerOffsetMetrics.forEach(cOMetric -> {
			try {
				Metric metric = cOMetric.convertToMetric();
				String identifier = metric.getIdentifier();
				if (!metricMap.containsKey(identifier)) {
					metricMap.put(identifier, metric);
				} else {
					metricMap.get(identifier).addDatapoint(cOMetric.getTime(), cOMetric.getValue());
				}
			} catch (Exception ex) {
				logger.error("Failed while converting Consumer Offset Metric {} to Metric", cOMetric, ex);
			}
		});
		return new ArrayList<>(metricMap.values());
	}

	/**
	 *
	 * @param consumerOffsetMetric an instance of the consumer offset metric
	 * @return a unique identifier for hashing purposes.
	 */
	public static String getIdentifierFieldsAsString(ConsumerOffsetMetric consumerOffsetMetric) {
		return new StringBuilder(consumerOffsetMetric.getMetric()).append(":")
				.append(consumerOffsetMetric.getTopic())
				.append(consumerOffsetMetric.getTime())
				.append(consumerOffsetMetric.getValue())
				.append(consumerOffsetMetric.getTags()).toString();
	}

	@Override
	public String toString() {
		String format = "ConsumerOffsetMetric(metric=>{0}, topic=>{1}, time=>{2,number,#}, value=>{3,number,#}, tags=>{4})";
		Object [] params = {getMetric(), getTopic(), getTime(), getValue(), getTags()};
		return MessageFormat.format(format, params);
	}

	/*
	Getter and Setter functions.
	 */
	public String getMetric() {
		return metric;
	}

	public void setMetric(String _metric) {
		this.metric = _metric;
	}

	public String getTopic() {
		return topic;
	}

	public void setTopic(String _topic) {
		this.topic = _topic;
	}

	public Map<String, String> getTags() {
		return Collections.unmodifiableMap(new TreeMap<>(this.tags));
	}

	public void setTags(Map<String, String> _tags) {
		this.tags = _tags;
	}

	public void setTag(String _key, String _value) {
		this.tags.put(_key, _value);
	}

	public Long getTime() {
		return time;
	}

	public void setTime(Long _time) {
		this.time = _time;
	}

	public Double getValue() {
		return value;
	}

	public void setValue(Double _value) {
		this.value = _value;
	}
}

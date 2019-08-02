/*
 *
 *  * Copyright (c) 2016, Salesforce.com, Inc.
 *  * All rights reserved.
 *  *
 *  * Redistribution and use in source and binary forms, with or without
 *  * modification, are permitted provided that the following conditions are met:
 *  *
 *  * 1. Redistributions of source code must retain the above copyright notice,
 *  * this list of conditions and the following disclaimer.
 *  *
 *  * 2. Redistributions in binary form must reproduce the above copyright notice,
 *  * this list of conditions and the following disclaimer in the documentation
 *  * and/or other materials provided with the distribution.
 *  *
 *  * 3. Neither the name of Salesforce.com nor the names of its contributors may
 *  * be used to endorse or promote products derived from this software without
 *  * specific prior written permission.
 *  *
 *  * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 *  * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 *  * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 *  * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 *  * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 *  * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 *  * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 *  * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 *  * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 *  * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 *  * POSSIBILITY OF SUCH DAMAGE.
 *
 */

/**
 * Implements data lag detection on alert client side using consumer offset lag posted by kafka consumers in the upstream.
 * @author Sudhanshu.Bahety (sudhanshu.bahety@salesforce.com)
 * */
package com.salesforce.dva.argus.service.monitor;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.MailService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.MetricStorageService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.service.mail.EmailContext;
import com.salesforce.dva.argus.service.tsdb.MetricQuery;
import com.salesforce.dva.argus.system.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.NotFoundException;
import java.text.MessageFormat;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

public class DataLagMonitorConsumerOffset implements DataLagService {

	private String hostName;
	private Map<String, Long> dataLagTriggerThresholdPerDC = new HashMap<>();
	private Map<String, Long> dataLagClearThresholdPerDC = new HashMap<>();
	private Map<String, String> expressionPerDC = new HashMap<>();
	private Map<String, Boolean> lagStatePerDC = new HashMap<>();
	private Set<String> dcSet = new HashSet<>();
	private MetricStorageService consumerOffsetMetricService;
	private MetricService metricService;
	private TSDBService tsdbService;
	private MailService mailService;
	private Set<String> enforceLagPresentSet;
	private final Logger logger = LoggerFactory.getLogger(DataLagMonitorConsumerOffset.class);
	private SystemConfiguration sysConfig;

	private static final String TOPIC_TAG = "topic";
	private static final Long SLEEP_INTERVAL_MILLIS = 60 * 1000L;
	private static Long datalagInertia = 5 * 60 * 1000L;

	// TODO: Remove this once verification completes.
	private static Boolean isInDebugMode = false;
	private static String DEFAULT_EMAIL = "sudhanshu.bahety@salesforce.com";
	private static final String DEFAULT_SUBJECT = "Data Lag Consumer Offset Method detected a state change";
	private static String DEBUG_PREFIX;

	@Inject
	public DataLagMonitorConsumerOffset(SystemConfiguration config, MetricStorageService consumerOffsetMetricService, MetricService metricService, TSDBService tsdbService, MailService mailService) {
		this.sysConfig = config;
		initDebug();
		this.consumerOffsetMetricService = consumerOffsetMetricService;
		this.tsdbService = tsdbService;
		this.metricService = metricService;
		this.mailService = mailService;
		this.hostName = SystemConfiguration.getHostname();
		datalagInertia = Long.valueOf(sysConfig.getValue(Property.DATA_LAG_INERTIA.getName(), Property.DATA_LAG_INERTIA.getDefaultValue()));
		init();
		this.logger.info(DEBUG_PREFIX + "Data lag consumer offset monitor initialized");
	}

	// TODO: Remove this once verification completes.
	private void initDebug() {
		isInDebugMode = Boolean.valueOf(sysConfig.getValue(Property.DATA_LAG_DEBUG.getName(), Property.DATA_LAG_DEBUG.getDefaultValue()));
		DEFAULT_EMAIL = String.valueOf(sysConfig.getValue(Property.DATA_LAG_EMAIL.getName(), Property.DATA_LAG_EMAIL.getDefaultValue()));
		if (isInDebugMode) {
			DEBUG_PREFIX = "[DEBUG-DATALAG] ";
		} else {
			DEBUG_PREFIX = "";
		}
	}

	private void init() {
		dcSet = Sets.newHashSet(sysConfig.getValue(SystemConfiguration.Property.DC_LIST).split(","));
		enforceLagPresentSet = Sets.newHashSet(sysConfig.getValue(DataLagService.Property.DATA_LAG_ENFORCE_DC_LIST.getName(), DataLagService.Property.DATA_LAG_ENFORCE_DC_LIST.getDefaultValue()).trim().toUpperCase().split(","));
		// Read expression per DC from the config file
		String defaultExpression = sysConfig.getValue(Property.DATA_LAG_DEFAULT_EXPRESSION.getName(), Property.DATA_LAG_DEFAULT_EXPRESSION.getDefaultValue());
		try {
			JsonObject dataLagQueryExpressions = new JsonParser().parse(sysConfig.getValue(Property.DATA_LAG_QUERY_EXPRESSION.getName(), Property.DATA_LAG_QUERY_EXPRESSION.getDefaultValue())).getAsJsonObject();
			for (Map.Entry<String, JsonElement> entry : dataLagQueryExpressions.entrySet()) {
				String currentExpression = entry.getKey().trim();
				JsonArray dcList = entry.getValue().getAsJsonArray();
				for (JsonElement value : dcList) {
					try {
						String currentDC = value.getAsString().trim().toUpperCase();
						expressionPerDC.put(currentDC, currentExpression.replace("#DC#", currentDC.toLowerCase()));//Note: When we post from AKC, all DCs are in lower case.
						dcSet.add(currentDC);
					} catch (Exception ex) {
						logger.error(DEBUG_PREFIX + "Exception occured while parsing the datalag expression for DC: " + value + ", using default expression. Exception: " + ex);
					}
				}
			}
		} catch (Exception ex) {
			logger.error(DEBUG_PREFIX + "Exception occured while parsing the datalag expression json list, using default expression. Exception: ", ex);
		} finally {
			// Fill with default values
			for (String dc : dcSet) {
				dc = dc.trim().toUpperCase();
				if (!expressionPerDC.containsKey(dc)) {
					expressionPerDC.put(dc, defaultExpression);
				}
				lagStatePerDC.put(dc, false);
			}
		}

		// Read default thresholds.
		Long defaultTriggerThreshold = Long.valueOf(sysConfig.getValue(Property.DATA_LAG_DEFAULT_TRIGGER_THRESHOLD.getName(), Property.DATA_LAG_DEFAULT_TRIGGER_THRESHOLD.getDefaultValue()));
		Long defaultClearThreshold = Long.valueOf(sysConfig.getValue(Property.DATA_LAG_DEFAULT_CLEAR_THRESHOLD.getName(), Property.DATA_LAG_DEFAULT_CLEAR_THRESHOLD.getDefaultValue()));
		// Read DC per threshold from the config file.
		readThresholds(sysConfig.getValue(Property.DATA_LAG_TRIGGER_THRESHOLD.getName(), Property.DATA_LAG_TRIGGER_THRESHOLD.getDefaultValue()), dataLagTriggerThresholdPerDC, defaultTriggerThreshold);
		readThresholds(sysConfig.getValue(Property.DATA_LAG_CLEAR_THRESHOLD.getName(), Property.DATA_LAG_CLEAR_THRESHOLD.getDefaultValue()), dataLagClearThresholdPerDC, defaultClearThreshold);
	}

	private void readThresholds(String thresholdProperty, Map<String, Long> dataLagThresholdPerDC, Long defaultValue) {
		requireArgument(dataLagThresholdPerDC != null, "Data lag threshold per dc cannot be null");
		requireArgument(defaultValue != null, "Default threshold value cannot be null");
		JsonObject dataLagThresholdObject = null;
		try {
			dataLagThresholdObject = new JsonParser().parse(thresholdProperty).getAsJsonObject();
			Set<Map.Entry<String, JsonElement>> entriesForThreshold = dataLagThresholdObject.entrySet();
			for (Map.Entry<String, JsonElement> entry : entriesForThreshold) {
				Long currentThreshold = Long.valueOf(entry.getKey().trim());
				JsonArray dcList = entry.getValue().getAsJsonArray();
				for (JsonElement value : dcList) {
					try {
						String currentDC = value.getAsString().trim().toUpperCase();
						dataLagThresholdPerDC.put(currentDC, currentThreshold);
						dcSet.add(currentDC);
					} catch (Exception ex) {
						logger.error(DEBUG_PREFIX + "Exception occured while parsing threshold for DC: " + value + ", using default threshold. Exception: ", ex);
					}
				}
			}
		} catch (Exception ex) {
			logger.error(DEBUG_PREFIX + "Exception occured while parsing threshold value per dc. Exception: ", ex);
		} finally {
			dcSet.stream()
					.filter(dc -> !dataLagThresholdPerDC.containsKey(dc))
					.forEach(dc -> dataLagThresholdPerDC.put(dc, defaultValue));
		}
	}

	@Override
	public void run() {
		logger.info(DEBUG_PREFIX + "Data lag consumer offset monitor thread started");
		while (!Thread.currentThread().isInterrupted()) {
			try {
				Thread.sleep(SLEEP_INTERVAL_MILLIS);
				queryMetricsForDC(dcSet, System.currentTimeMillis()).forEach(this::computeDataLag);
			} catch (Exception e) {
				logger.error(DEBUG_PREFIX + "Exception thrown in data lag monitor thread: " + e);
			}
		}
	}

	@Override
	public Map<String, List<Metric>> queryMetricsForDC(Set<String> dcSet, Long startTime) {
		requireArgument(dcSet != null && !dcSet.isEmpty(), "DCs for which data lag is to be queried cannot be null or empty");
		if (startTime == null) {
			logger.warn(DEBUG_PREFIX + "Start time from which data lag is to be computed is null, taking current value by default");
			startTime = System.currentTimeMillis();
		}

		Long startTimeFinal = startTime;

		Map<String, List<Metric>> result = new HashMap<>();
		List<MetricQuery> mQList = dcSet.stream().parallel()
				.map(expressionPerDC::get)
				.map(expression -> metricService.parseToMetricQuery(expression, startTimeFinal))
				.flatMap(Collection::stream)
				.collect(Collectors.toList());
		if (mQList.size() != dcSet.size()) {
			logger.error(DEBUG_PREFIX + "Metric Query Size does not match number of dcs present. Metric Query: {}, DCs: {}", mQList, dcSet);
		}

		consumerOffsetMetricService.getMetrics(mQList).forEach((mQ, mList) -> result.put(getDCFromTopic(mQ.getTags().get(TOPIC_TAG)), mList));
		return result;
	}

	@VisibleForTesting
	protected String getDCFromTopic(String topic) {
		requireArgument(topic != null, "Topic for which dc is to be extracted cannot be null");
		String finalTopic = topic.toUpperCase();
		for(String s: dcSet) {
			if (finalTopic.contains(s)) {
				return s;
			}
		}
		throw new NotFoundException(DEBUG_PREFIX + "No Data center could be inferred from topic: " + topic);
	}

	/*
	In current implementation, inertia value is same as the look back window for which metric is queried.
	This helps in reducing the problem of only checking if all the value returned in time series violates the condition.
	TODO: If the look back window is different from the inertia period, the logic has to be changed.
	 */
	@Override
	public Boolean computeDataLag(String dc, List<Metric> metricList) {

		if (metricList.size() <= 0) {
			logger.error(DEBUG_PREFIX + "No Metrics could be obtained for dc: {}, disabling data lag by default.", dc);
			lagStatePerDC.put(dc, false);
			return false;
		}
		else if (metricList.size() != 1) {
			logger.warn(DEBUG_PREFIX + "More than 1 metrics returned for a single dc: {}, Metric list: {}\nCombining all data points to compute data lag.",dc, metricList);
		}
		requireArgument(dc != null, "Data center for which data lag is to be computed cannot be null");

		dc = dc.trim().toUpperCase();
		Map<Long, Double> datapoints = new HashMap<>();
		metricList.forEach(m -> datapoints.putAll(m.getDatapoints()));
		Long triggeringThreshold = dataLagTriggerThresholdPerDC.get(dc);
		Long clearingThreshold = dataLagClearThresholdPerDC.get(dc);
		boolean isTriggering = true, isClearing = true, initialState = lagStatePerDC.get(dc);

		if(datapoints.size() == 0) {
			logger.warn(DEBUG_PREFIX + "No metrics retrieved for Metrics: {}", metricList);
			logger.warn(DEBUG_PREFIX + "Enabling data lag for dc: {}", dc);
			lagStatePerDC.put(dc, true);
			// If we are unable to retrieve metric for the current minute, we are resorting to the default value of 0.
			pushMetric(System.currentTimeMillis(), 0.0, dc);
		} else {
			for (Double currentValue : datapoints.values()) {
				isTriggering &= (currentValue >= triggeringThreshold);
				isClearing &= (currentValue < clearingThreshold);

				if (!isTriggering && !isClearing) {
					break;
				}
			}

			if (isTriggering && isClearing) {
				logger.error(DEBUG_PREFIX + MessageFormat.format("Both Triggering and Clearing conditions cannot hold true at the same time. datapoints: {0}, Triggering threshold: {1}, Clearing threshold: {2}", datapoints, triggeringThreshold, clearingThreshold));
				lagStatePerDC.put(dc, true);
			}
			else if (isTriggering) {
				lagStatePerDC.put(dc, true);
			} else if (isClearing) {
				lagStatePerDC.put(dc, false);
			}
			pushMetric(System.currentTimeMillis(), Collections.max(datapoints.values()), dc);
		}

		// TODO: Remove this once verification completes.
		if ( isInDebugMode && (initialState ^ lagStatePerDC.get(dc)) ) { // Notify whenever there is a state change.

			StringBuilder message = new StringBuilder();
			String state =  isTriggering ? "Triggering" : "Clearing";

			message.append("<p>Data lag state change was detected by consumer offset method. </p>");
			message.append(MessageFormat.format("DC: {0}, State: {1}, Triggering Threshold: {2}, Clearing Threshold: {3}", dc, state, triggeringThreshold, clearingThreshold));
			message.append(MessageFormat.format("<br> List retrieved by ES: {0}", metricList));
			EmailContext.Builder emailContextBuilder = new EmailContext.Builder()
					.withRecipients(Sets.newHashSet(DEFAULT_EMAIL))
					.withSubject(DEFAULT_SUBJECT)
					.withEmailBody(message.toString())
					.withContentType("text/html; charset=utf-8")
					.withEmailPriority(MailService.Priority.HIGH);
			mailService.sendMessage(emailContextBuilder.build());
		}

		return lagStatePerDC.get(dc);
	}

	@Override
	public Boolean isDataLagging(String currentDC) {
		if (currentDC == null) {
			return false;
		}
		currentDC = currentDC.trim().toUpperCase();

		if (enforceLagPresentSet.contains(currentDC)) {
			return true;
		}
		if (lagStatePerDC.containsKey(currentDC)) {
			return lagStatePerDC.get(currentDC);
		}

		return lagStatePerDC.values()
				.stream()
				.reduce((e1, e2) -> (e1 || e2))
				.orElse(false);
	}

	@Override
	public void pushMetric(Long time, Double value, String dc) {
		requireArgument( value != null, "Value of conusmer offset metric cannot be null");
		if (time == null) {
			logger.warn("Time when the metric is pushed is null. Using current time");
			time = System.currentTimeMillis();
		}
		if (dc == null) {
			logger.warn("DC for which metric is pushed is null. Using NO_DC_SPECIFIED as value");
			dc = "NO_DC_SPECIFIED";
		}

		String finalDC = dc;

		Metric trackingMetric = new Metric(MonitorService.Counter.DATALAG_PER_DC_OFFSET_LAG.getScope(), MonitorService.Counter.DATALAG_PER_DC_OFFSET_LAG.getMetric());
		ExecutorService _executorService = Executors.newSingleThreadExecutor();
		Map<String, String> tags = new HashMap<>();

		tags.put("dc", dc);
		tags.put("host", hostName);

		trackingMetric.setTags(tags);
		Map<Long, Double> currentDatapoint = new HashMap<>();
		currentDatapoint.put(time, value);
		trackingMetric.setDatapoints(currentDatapoint);

		try {
			_executorService.submit(()->{
				tsdbService.putMetrics(Collections.singletonList(trackingMetric));
				logger.debug(DEBUG_PREFIX + MessageFormat.format("Pushing datalag metric - hostname:{0}, dc:{1}, offset:{2}", hostName, finalDC, value));
			});
		} catch (Exception ex) {
			logger.error(DEBUG_PREFIX + "Exception occurred while pushing datalag metric to tsdb: ", ex);
		} finally {
			_executorService.shutdown();
		}
	}

	/**
	 * The set of implementation specific configuration properties.
	 *
	 */
	public enum Property {

		/** Minute Threshold before you enable data lag */
		DATA_LAG_TRIGGER_THRESHOLD("system.property.data.lag.consumer.offset.trigger.threshold", "thresholdPerDC"),
		/** Minute Threshold before you disable data lag */
		DATA_LAG_CLEAR_THRESHOLD("system.property.data.lag.consumer.offset.clear.threshold", "thresholdPerDC"),
		/** Expression per dc to determine data lag */
		DATA_LAG_QUERY_EXPRESSION("system.property.data.lag.consumer.offset.expression.list","expressionListPerDC"),
		/** Default expression if the expression for dc cannot be queried. */
		DATA_LAG_DEFAULT_EXPRESSION("system.property.data.lag.consumer.offset.default.expression","defaultExpression"),
		/** Default threshold if the trigger threshold for dc is not specified. */
		DATA_LAG_DEFAULT_TRIGGER_THRESHOLD("system.property.data.lag.consumer.offset.default.trigger.threshold","23000"),
		/** Default threshold if the clear threshold for dc is not specified. */
		DATA_LAG_DEFAULT_CLEAR_THRESHOLD("system.property.data.lag.consumer.offset.default.clear.threshold","5000"),
		/** Inertia value for which data lag should continuously hold true. */
		DATA_LAG_INERTIA("system.property.data.lag.consumer.offset.default.inertia.millis","300000"),

		// TODO: Remove this once verification completes.
		/** Data lag to be run in debug mode to check the behaviour */
		DATA_LAG_DEBUG("system.property.data.lag.consumer.offset.debug.mode", "true"),
		/** Default email for debugging purposes */
		DATA_LAG_EMAIL("system.property.data.lag.consumer.offset.debug.email", "sudhanshu.bahety@salesforce.com");

		private final String _name;
		private final String _defaultValue;

		Property(String name, String defaultValue) {
			_name = name;
			_defaultValue = defaultValue;
		}

		/**
		 * Returns the property name.
		 *
		 * @return  The property name.
		 */
		public String getName() {
			return _name;
		}

		/**
		 * Returns the default value for the property.
		 *
		 * @return  The default value.
		 */
		public String getDefaultValue() {
			return _defaultValue;
		}
	}
}

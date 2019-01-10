package com.salesforce.dva.argus.service.monitor;

import java.text.MessageFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.AbstractMap.SimpleEntry;

import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.TSDBService;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.system.SystemConfiguration;

/*
 * This class runs a thread which periodically checks if there is data lag on Argus side.
 *
 */
public class DataLagMonitor extends Thread{


	private String _hostName;

	private String _defaultExpression;

	private long _dataLagThreshold;

	private Map<String, Boolean> _isDataLaggingbyDCMap = new TreeMap<>();

	private Map<String, String> _expressionPerDC = new TreeMap<>();

	private MetricService _metricService;

	private TSDBService _tsdbService;

	private static final Long SLEEP_INTERVAL_MILLIS = 60*1000L;

	private static final double MAX_LAG_TIME_MILLIS = 4*60*60*1000;

	private final Logger _logger = LoggerFactory.getLogger(DataLagMonitor.class);

	private SystemConfiguration _sysConfig;

	private final ExecutorCompletionService<SimpleEntry<String, List<Metric>>> _completionService;

	public DataLagMonitor(SystemConfiguration sysConfig, MetricService metricService, TSDBService tsdbService) {
		super("datalag-monitor");
		_sysConfig = sysConfig;
		_metricService = metricService;
		_tsdbService = tsdbService;
		_hostName = SystemConfiguration.getHostname();
		init();
		_completionService = new ExecutorCompletionService<>(Executors.newFixedThreadPool(5));
		_logger.info("Data lag monitor initialized");
	}

	private void init() {
		_defaultExpression = _sysConfig.getValue(SystemConfiguration.Property.DATA_LAG_DEFAULT_EXPRESSION);
		_dataLagThreshold = Long.valueOf(_sysConfig.getValue(SystemConfiguration.Property.DATA_LAG_THRESHOLD));
		try {
			JsonObject _dataLagQueryExpressions = new JsonParser().parse(_sysConfig.getValue(SystemConfiguration.Property.DATA_LAG_QUERY_EXPRESSION)).getAsJsonObject();
			Set<Map.Entry<String, JsonElement>> entries = _dataLagQueryExpressions.entrySet();
			for (Map.Entry<String, JsonElement> entry : entries) {
				String currentExpression = entry.getKey();
				JsonArray dcList = entry.getValue().getAsJsonArray();
				for (JsonElement value : dcList) {
					String currentDC = value.getAsString();
					_expressionPerDC.put(currentDC, currentExpression.replace("#DC#", currentDC));
					_isDataLaggingbyDCMap.put(currentDC, false);
				}
			}
		} catch (Exception ex) {
			_logger.error("Exception occured while parsing the datalag expression json list, using default expression. Exception: {}", ex);
		}

		for (String dc : _sysConfig.getValue(SystemConfiguration.Property.DC_LIST).split(",")) {
			if (!_expressionPerDC.containsKey(dc)) {
				_expressionPerDC.put(dc, _defaultExpression);
				_isDataLaggingbyDCMap.put(dc, false);
			}
		}
	}

	@Override
	public void run() {
		_logger.info("Data lag monitor thread started");
		boolean firstTime = true;
		String currentDC = null;
		while (!isInterrupted()) {
			try {
				if (!firstTime) {
					sleep(SLEEP_INTERVAL_MILLIS);
				} else {
					// waiting 5 seconds for everything to initialize
					sleep(5 * 1000);
					firstTime = false;
				}

				final Long currTime = System.currentTimeMillis();
				for (String dc : _expressionPerDC.keySet()) {
					_completionService.submit(() -> {
						List<Metric> metrics = new ArrayList<>();
						try {
							metrics = _metricService.getMetrics(_expressionPerDC.get(dc), currTime);
						} catch (Exception e) {
							metrics.clear();
							_logger.error("Metric Service failed to get metric for expression: " + _expressionPerDC.get(dc) + " while being queried by DataLagMonitor, for DC: " + dc);
						}

						return new SimpleEntry<>(dc, metrics);
					});
				}

				for (int idx = 0; idx < _expressionPerDC.size(); ++idx) {
					try {
						Future<SimpleEntry<String, List<Metric>>> future = _completionService.take();
						SimpleEntry<String, List<Metric>> result = future.get();
						currentDC = result.getKey();
						List<Metric> metrics = result.getValue();
						double latestLagTimeInMillis;

						if (metrics == null || metrics.isEmpty()) {
							_logger.info("Data lag detected as metric list is empty for DC: " + currentDC);
							latestLagTimeInMillis = MAX_LAG_TIME_MILLIS;
						} else {
							//Assuming only one time series in result
							Metric currMetric = metrics.get(0);
							if (currMetric.getDatapoints() == null || currMetric.getDatapoints().size() == 0) {
								_logger.info("Data lag detected as data point list is empty for DC: " + currentDC);
								latestLagTimeInMillis = MAX_LAG_TIME_MILLIS;
							} else {
								long lastDataPointTime = Collections.max(currMetric.getDatapoints().keySet());
								latestLagTimeInMillis = (currTime - lastDataPointTime);
							}
						}
						_isDataLaggingbyDCMap.put(currentDC, latestLagTimeInMillis > _dataLagThreshold);
						pushLagTimeMetric(currentDC, currTime, latestLagTimeInMillis / 1000.0);
					} catch (Exception ex) {
						_logger.error(MessageFormat.format("Exception thrown while evaluating lag time for dc: {0} with message: {1}", currentDC, ex));
					}
				}

			} catch (Exception e) {
				_logger.error("Exception thrown in data lag monitor thread - " + ExceptionUtils.getFullStackTrace(e));
			}
		}
	}

	private void pushLagTimeMetric(String currentDC, Long currTime, double lagTime) {
		Metric trackingMetric = new Metric(MonitorService.Counter.DATALAG_PER_DC_TIME_LAG.getScope(), MonitorService.Counter.DATALAG_PER_DC_TIME_LAG.getMetric());
		ExecutorService _executorService = Executors.newSingleThreadExecutor();
		Map<String, String> tags = new HashMap<>();

		tags.put("dc", currentDC);
		tags.put("host", _hostName);

		trackingMetric.setTags(tags);
		Map<Long, Double> currentDatapoint = new HashMap<>();
		currentDatapoint.put(currTime, lagTime);
		trackingMetric.setDatapoints(currentDatapoint);

		try {
			_executorService.submit(()->{
				_tsdbService.putMetrics(Collections.singletonList(trackingMetric));
				_logger.debug(MessageFormat.format("Pushing datalag metric - hostname:{0}, dc:{1}, lagTime:{2}",_hostName, currentDC, lagTime));
			});
		} catch (Exception ex) {
			_logger.error("Exception occurred while pushing datalag metric to tsdb - {}", ex.getMessage());
		} finally {
			_executorService.shutdown();
		}
	}

	public boolean isDataLagging(String currentDC) {
		try {
			if (currentDC != null && currentDC.length() > 0 && _isDataLaggingbyDCMap.containsKey(currentDC)) {
				return _isDataLaggingbyDCMap.get(currentDC);
			} else {
				return _isDataLaggingbyDCMap.values().stream()
						.reduce((e1, e2) -> (e1 || e2))
						.get();
			}
		} catch (Exception ex) {
			_logger.error(MessageFormat.format("Failed to identify whether DC {0} was lagging.", currentDC));
			return false;
		}
	}
}
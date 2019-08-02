package com.salesforce.dva.argus.service.monitor;

import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.service.MonitorService;
import com.salesforce.dva.argus.service.TSDBService;
import com.salesforce.dva.argus.system.SystemConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;
import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static com.salesforce.dva.argus.system.SystemAssert.requireArgument;

/*
 * This class runs a thread which periodically checks if there is data lag on Argus side.
 *
 */
public class DataLagMonitorGoldenMetric implements DataLagService {


	private String _hostName;
	private long _dataLagThreshold;
	private Map<String, Boolean> _isDataLaggingbyDCMap = new TreeMap<>();
	private Map<String, String> _expressionPerDC = new TreeMap<>();
	private Map<String, Double> _lagPerDC = new TreeMap<>();
	private Set<String> enforceLagPresentSet;
	private MetricService _metricService;
	private TSDBService _tsdbService;
	private static Long SLEEP_INTERVAL_MILLIS = 60*1000L;
	private static final Double MAX_LAG_TIME_MILLIS = 4.0*60*60*1000;
	private final Logger _logger = LoggerFactory.getLogger(DataLagMonitorGoldenMetric.class);
	private SystemConfiguration _sysConfig;
	private final ExecutorCompletionService<SimpleEntry<String, List<Metric>>> _completionService;

	@Inject
	public DataLagMonitorGoldenMetric(SystemConfiguration sysConfig, MetricService metricService, TSDBService tsdbService) {
		_sysConfig = sysConfig;
		_metricService = metricService;
		_tsdbService = tsdbService;
		_hostName = SystemConfiguration.getHostname();
		init();
		_completionService = new ExecutorCompletionService<>(Executors.newFixedThreadPool(5));
		_logger.info("Data lag golden metric monitor initialized");
	}

	private void init() {
		String _defaultExpression = _sysConfig.getValue(Property.DATA_LAG_DEFAULT_EXPRESSION.getName(), Property.DATA_LAG_DEFAULT_EXPRESSION.getDefaultValue());
		_dataLagThreshold = Long.valueOf(_sysConfig.getValue(Property.DATA_LAG_THRESHOLD.getName(), Property.DATA_LAG_THRESHOLD.getDefaultValue()));
		try {
			JsonObject _dataLagQueryExpressions = new JsonParser().parse(_sysConfig.getValue(Property.DATA_LAG_QUERY_EXPRESSION.getName(), Property.DATA_LAG_QUERY_EXPRESSION.getDefaultValue())).getAsJsonObject();
			Set<Map.Entry<String, JsonElement>> entries = _dataLagQueryExpressions.entrySet();
			for (Map.Entry<String, JsonElement> entry : entries) {
				String currentExpression = entry.getKey().trim();
				JsonArray dcList = entry.getValue().getAsJsonArray();
				for (JsonElement value : dcList) {
					try {
						String currentDC = value.getAsString().trim();
						_expressionPerDC.put(currentDC, currentExpression.replace("#DC#", currentDC));
						_isDataLaggingbyDCMap.put(currentDC, false);
					} catch (Exception ex) {
						_logger.error("Exception occured while parsing the datalag expression for DC: " + value + ", using default expression. Exception: {0}", ex);
					}
				}
			}
		} catch (Exception ex) {
			_logger.error("Exception occured while parsing the datalag expression json list, using default expression. Exception: ", ex);
		}

		for (String dc : _sysConfig.getValue(SystemConfiguration.Property.DC_LIST).split(",")) {
			if (!_expressionPerDC.containsKey(dc)) {
				_expressionPerDC.put(dc, _defaultExpression);
				_isDataLaggingbyDCMap.put(dc, false);
			}
			_lagPerDC.put(dc, 0.0);
		}

		enforceLagPresentSet = Sets.newHashSet(_sysConfig.getValue(DataLagService.Property.DATA_LAG_ENFORCE_DC_LIST.getName(), DataLagService.Property.DATA_LAG_ENFORCE_DC_LIST.getDefaultValue()).split(","));
	}

	@Override
	public void run() {
		_logger.info("Data lag golden metric monitor thread started");
		while (!Thread.currentThread().isInterrupted()) {
			try {
				Thread.sleep(SLEEP_INTERVAL_MILLIS);
				queryMetricsForDC(_expressionPerDC.keySet(), System.currentTimeMillis()).forEach(this::computeDataLag);
			} catch (Exception e) {
				_logger.error("Exception thrown in data lag golden metric monitor thread: ", e);
			}
		}
	}

	@Override
	public Boolean isDataLagging(String currentDC) {
		if (currentDC == null) {
			return false;
		}
		currentDC = currentDC.trim().toUpperCase();

		if (enforceLagPresentSet.contains(currentDC) ) {
			return true;
		}
		if (_isDataLaggingbyDCMap.containsKey(currentDC) ) {
			return _isDataLaggingbyDCMap.get(currentDC);
		}
		return _isDataLaggingbyDCMap.values()
				.stream()
				.reduce((e1, e2) -> (e1 || e2))
				.orElse(false);
	}

	@Override
	public Map<String, List<Metric> > queryMetricsForDC(Set<String> dcSet, Long startTime) {
		requireArgument(dcSet != null && !dcSet.isEmpty(), "DCs for which data lag is to be queried cannot be null or empty");
		requireArgument(startTime != null, "start time from which query begins cannot be empty");

		Map<String, List<Metric>> metricsPerDC = new HashMap<>();
		for (String dc : dcSet) {
			_completionService.submit(() -> {
				List<Metric> metrics = new ArrayList<>();
				try {
					metrics = _metricService.getMetrics(_expressionPerDC.get(dc), startTime).getMetricsList();
				} catch (Exception e) {
					metrics.clear();
					_logger.error("Metric Service failed to get metric for expression: " + _expressionPerDC.get(dc) + " while being queried by DataLagMonitorGoldenMetric, for DC: " + dc + " Exception: ", e);
				}

				return new SimpleEntry<>(dc, metrics);
			});
		}

		for (int idx = 0; idx < dcSet.size(); ++idx) {
			try {
				Future<SimpleEntry<String, List<Metric>>> future = _completionService.take();
				SimpleEntry<String, List<Metric>> result = future.get();
				String currentDC = result.getKey();
				List<Metric> metrics = result.getValue();
				metricsPerDC.put(currentDC, metrics);
			} catch (Exception e) {
				_logger.error(MessageFormat.format("Exception thrown while evaluating lag time for dc with message: ", e));
			}
		}
		return metricsPerDC;
	}

	@Override
	public Boolean computeDataLag(String dc, List<Metric> metrics) {
		requireArgument(dc != null, "Data center for which data lag is to be computed cannot be null");

		double lagTimeInMillis;
		Long currTime = System.currentTimeMillis();

		if (metrics == null || metrics.isEmpty()) {
			_logger.info("Data lag detected as metric list is empty for DC: " + dc);
			lagTimeInMillis = Math.min(MAX_LAG_TIME_MILLIS, _lagPerDC.get(dc) + SLEEP_INTERVAL_MILLIS);
		} else {
			if (metrics.size() > 1) {
				_logger.warn("More than 1 metric returned by the metric service while querying for data lag: {}", metrics);
			}
			//Assuming only one time series in result.
			Metric currMetric = metrics.get(0);
			if (currMetric.getDatapoints() == null || currMetric.getDatapoints().size() == 0) {
				_logger.info("Data lag detected as data point list is empty for DC: " + dc);
				lagTimeInMillis = Math.min(MAX_LAG_TIME_MILLIS, _lagPerDC.get(dc) + SLEEP_INTERVAL_MILLIS);
			} else {
				long lastDataPointTime = Collections.max(currMetric.getDatapoints().keySet());
				lagTimeInMillis = (currTime - lastDataPointTime);
			}
		}

		_lagPerDC.put(dc, lagTimeInMillis);
		_isDataLaggingbyDCMap.put(dc, lagTimeInMillis > _dataLagThreshold);
		pushMetric(currTime, lagTimeInMillis, dc);

		return lagTimeInMillis > _dataLagThreshold;
	}

	@Override
	public void pushMetric(Long currTime, Double lagTime, String currentDC) {
		requireArgument(currTime != null, "Time when the metric is pushed should not be null");
		requireArgument( lagTime != null, "Value of conusmer offset metric cannot be null");
		requireArgument(currentDC != null, "Should specify data center for which offset is being pushed");

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
			_logger.error("Exception occurred while pushing datalag metric to tsdb: ", ex);
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
		DATA_LAG_THRESHOLD("system.property.data.lag.threshold.millis", "300000"),
		/** Expression per dc to determine data lag */
		DATA_LAG_QUERY_EXPRESSION("system.property.data.lag.expression.list","expressionListPerDC"),
		/** Default expression if the expression for dc cannot be queried. */
		DATA_LAG_DEFAULT_EXPRESSION("system.property.data.lag.default.expression","defaultExpression");

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

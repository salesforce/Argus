package com.salesforce.dva.argus.service.monitor;

import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.*;

import com.salesforce.dva.argus.service.MonitorService;
import javafx.util.Pair;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.salesforce.dva.argus.entity.Metric;
import com.salesforce.dva.argus.service.MailService;
import com.salesforce.dva.argus.service.MetricService;
import com.salesforce.dva.argus.system.SystemConfiguration;

/*
 * This class runs a thread which periodically checks if there is data lag on Argus side.
 * 
 */
public class DataLagMonitor extends Thread{

	private String _dataLagQueryExpressions;

	private long _dataLagThreshold;

	private String _dataLagNotificationEmailId;

	private String _hostName;

	private Map<String, Boolean> _isDataLaggingbyDCMap = new TreeMap<>();

	private Map<String, String> _expressionPerDC = new TreeMap<>();

	private MetricService _metricService;

	private MonitorService _monitorService;

	private MailService _mailService;

	private static final Long SLEEP_INTERVAL_MILLIS = 60*1000L;

	private final Logger _logger = LoggerFactory.getLogger(DataLagMonitor.class);

	private SystemConfiguration _sysConfig;

    private Set<String> listOfDCForNotificationDataLagPresent = new HashSet<>();
    private Set<String> listOfDCForNotificationDataLagNotPresent = new HashSet<>();
    private enum isThereAnyChangesInDataLag  {NONE, CHANGES_IN_DATA_LAG_DC, CHANGES_IN_DATA_RESUMED_DC, CHANGES_IN_BOTH};
    private isThereAnyChangesInDataLag currentScenario = isThereAnyChangesInDataLag.NONE;

    public DataLagMonitor(SystemConfiguration sysConfig, MetricService metricService, MailService mailService, MonitorService monitorService) {
	    _sysConfig = sysConfig;
		_metricService = metricService;
		_mailService = mailService;
		_dataLagQueryExpressions = sysConfig.getValue(SystemConfiguration.Property.DATA_LAG_QUERY_EXPRESSION);
        _dataLagThreshold = Long.valueOf(sysConfig.getValue(SystemConfiguration.Property.DATA_LAG_THRESHOLD));
		_dataLagNotificationEmailId = sysConfig.getValue(SystemConfiguration.Property.DATA_LAG_NOTIFICATION_EMAIL_ADDRESS);
		_hostName = sysConfig.getHostname();
		_monitorService = monitorService;
		init(_dataLagQueryExpressions);
        _logger.info("Data lag monitor initialized");
	}

	private void init(String dataLagQueryExpressions) {
	    for(String expressionDCPair: dataLagQueryExpressions.split("&&")) {
	        String [] currentExpressionDC = expressionDCPair.split("\\|\\|");
	        String currentExpression = currentExpressionDC[0];
            for (String currentDC : currentExpressionDC[1].split(",")) {
                _expressionPerDC.put(currentDC, currentExpression.replace("#DC#", currentDC));
                _isDataLaggingbyDCMap.put(currentDC, false);
            }
        }

        for (String DC: _sysConfig.getValue(SystemConfiguration.Property.DC_LIST).split(",") ) {
            listOfDCForNotificationDataLagNotPresent.add(DC);
        }
    }

	@Override
	public void run() {
		_logger.info("Data lag monitor thread started");
        final ExecutorService pool = Executors.newFixedThreadPool(5);
        final ExecutorCompletionService<Pair<String, List<Metric>>> completionService = new ExecutorCompletionService<>(pool);
        long currTime = System.currentTimeMillis();
		boolean firstTime = true;

        while (!isInterrupted()) {
            currentScenario = isThereAnyChangesInDataLag.NONE;
            try {
                if (!firstTime) {
                    sleep(SLEEP_INTERVAL_MILLIS);
                } else {
                    // waiting 5 seconds for everything to initialize
                    sleep(5 * 1000);
                    firstTime = false;
                }

                for (String currentDC : _expressionPerDC.keySet()) {
                    completionService.submit(() -> {
                        List<Metric> metrics = new ArrayList<>();
                        try {
                            metrics = _metricService.getMetrics(_expressionPerDC.get(currentDC), currTime);
                        } catch (Exception e) {
                            metrics.clear();
                            _logger.error("Metric Service failed to get metric for expression: " + _expressionPerDC.get(currentDC) + " while being queried by DataLagMonitor, for DC: " + currentDC);
                        }
                        return new Pair<>(currentDC, metrics);
                    });
                }
                for (int idx = 0; idx < _expressionPerDC.size(); ++idx) {
                    Future<Pair<String, List<Metric>>> future = completionService.take();
                    Pair<String, List<Metric>> result = future.get();
                    String currentDC = result.getKey();
                    List<Metric> metrics = result.getValue();
                    boolean isDataLagging = _isDataLaggingbyDCMap.get(currentDC);
                    if (metrics == null || metrics.isEmpty()) {
                        _logger.info("Data lag detected as metric list is empty for DC: " + currentDC);
                        if (!isDataLagging) {
                            updateStatesForDataLag(currentDC, true);
                        }
                        continue;
                    }

                    //assuming only one time series in result
                    Metric currMetric = metrics.get(0);
                    if (currMetric.getDatapoints() == null || currMetric.getDatapoints().size() == 0) {
                        _logger.info("Data lag detected as data point list is empty, for DC: " + currentDC);
                        if (!isDataLagging) {
                            updateStatesForDataLag(currentDC, true);
                        }
                        continue;
                    } else {
                        long lastDataPointTime = 0L;
                        for (Long dataPointTime : currMetric.getDatapoints().keySet()) {
                            if (dataPointTime > lastDataPointTime) {
                                lastDataPointTime = dataPointTime;
                            }
                        }
                        if ((currTime - lastDataPointTime) > _dataLagThreshold) {
                            _logger.info("Data lag detected as the last data point recieved is more than the data threshold of " + _dataLagThreshold + " ms, for DC: " + currentDC);
                            if (!isDataLagging) {
                                updateStatesForDataLag(currentDC, true);
                            }
                            continue;
                        }
                    }
                    if (isDataLagging) {
                        updateStatesForDataLag(currentDC, false);
                    }
                }
                if (currentScenario == isThereAnyChangesInDataLag.CHANGES_IN_BOTH) {
                    sendDataLagNotification(true);
                    sendDataLagNotification(false);
                } else if (currentScenario == isThereAnyChangesInDataLag.CHANGES_IN_DATA_LAG_DC) {
                    sendDataLagNotification(true);
                } else if (currentScenario == isThereAnyChangesInDataLag.CHANGES_IN_DATA_RESUMED_DC){
                    sendDataLagNotification(false);
                }
            } catch (Exception e) {
                _logger.error("Exception thrown in data lag monitor thread - " + ExceptionUtils.getFullStackTrace(e));
            }
        }
	}

	private void updateStatesForDataLag(String currentDC, boolean isDataLaggingInDC) {
        this._isDataLaggingbyDCMap.put(currentDC, isDataLaggingInDC);
        Map<String, String> tags = new HashMap<>();
        if (isDataLaggingInDC) {
            if(currentScenario == isThereAnyChangesInDataLag.NONE) {
                currentScenario = isThereAnyChangesInDataLag.CHANGES_IN_DATA_LAG_DC;
            } else {
                currentScenario = isThereAnyChangesInDataLag.CHANGES_IN_BOTH;
            }
            listOfDCForNotificationDataLagPresent.add(currentDC);
            listOfDCForNotificationDataLagNotPresent.remove(currentDC);
            tags.put("DC", currentDC);
            _monitorService.modifyCounter(MonitorService.Counter.DATALAG_PER_DC_COUNT, 1, tags);
            _logger.info(MessageFormat.format("Incrementing count for DC: {0} due to data lag.", currentDC));
        } else {
            if(currentScenario == isThereAnyChangesInDataLag.NONE) {
                currentScenario = isThereAnyChangesInDataLag.CHANGES_IN_DATA_RESUMED_DC;
            } else {
                currentScenario = isThereAnyChangesInDataLag.CHANGES_IN_BOTH;
            }
            listOfDCForNotificationDataLagNotPresent.add(currentDC);
            listOfDCForNotificationDataLagPresent.remove(currentDC);
            tags.put("DC", currentDC);
            _monitorService.modifyCounter(MonitorService.Counter.DATALAG_PER_DC_COUNT, -1, tags);
            _logger.info(MessageFormat.format("Decrementing count for DC: {0} due to data lag resolution.", currentDC));
        }
    }

	private void sendDataLagNotification(boolean isDataLagDCList) {
		Set<String> emailAddresseses = new HashSet<String>();
		emailAddresseses.add(_dataLagNotificationEmailId);
		String subject = "", dcListString = "";
		Set<String> dcList;
        StringBuilder body = new StringBuilder();

		if(isDataLagDCList) {
		    dcList = listOfDCForNotificationDataLagPresent;
			subject = "Alert evaluation on host - "+ _hostName + " has been stopped due to metric data lag in the following datacenters: " + listOfDCForNotificationDataLagPresent.toString();
			body.append("DC with status unchanged: " + listOfDCForNotificationDataLagNotPresent.toString());
			_logger.info("Data Lag detected for the following DCs: " + listOfDCForNotificationDataLagPresent.toString());
		}else {
            dcList = listOfDCForNotificationDataLagPresent;
			subject = "Alert evaluation on host - "+ _hostName + " has been resumed as the metric data lag has cleared in the following datacenters: " + listOfDCForNotificationDataLagNotPresent.toString();
            body.append("DC with status unchanged: " + listOfDCForNotificationDataLagPresent.toString());
            _logger.info("Data Lag resolved for the following DCs: " + listOfDCForNotificationDataLagNotPresent.toString());
		}

        body.append("\n");
		body.append("<b>Evaluated metric expression:  </b> <br/>");
		for(String currentDC: dcList) {
		    body.append(_expressionPerDC.get(currentDC) + "<br/>");
        }
        body.append(MessageFormat.format("<b>Configured data lag threshold:  </b> {0}<br/>", _dataLagThreshold));
		_mailService.sendMessage(emailAddresseses, subject, body.toString(), "text/html; charset=utf-8", MailService.Priority.NORMAL);
	}

	public boolean isDataLagging(String currentDC) {
		return _isDataLaggingbyDCMap.get(currentDC);
	}
}

package com.salesforce.dva.argus.service.monitor;

import java.text.MessageFormat;
import java.util.*;

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

	private String _dataLagQueryExpressions, _dataLagQueryExpressionsWithExceptions;

	private ArrayList<String> _dataLagQueryDCLists, _dataLagQueryDCListsWithExceptions;

	private long _dataLagThreshold;

	private String _dataLagNotificationEmailId;

	private String _hostName;

	private Map<String, Boolean> _isDataLagging = new TreeMap<>();

	private Map<String, String> _expressionPerDC = new TreeMap<>();

	private MetricService _metricService;

	private MailService _mailService;

	private static final Long SLEEP_INTERVAL_MILLIS = 60*1000L;

	private final Logger _logger = LoggerFactory.getLogger(DataLagMonitor.class);

	public DataLagMonitor(SystemConfiguration sysConfig, MetricService metricService, MailService mailService) {
		_metricService = metricService;
		_mailService = mailService;
		_dataLagQueryExpressions = sysConfig.getValue(SystemConfiguration.Property.DATA_LAG_QUERY_EXPRESSION);
        _dataLagQueryExpressionsWithExceptions = sysConfig.getValue(SystemConfiguration.Property.DATA_LAG_QUERY_EXPRESSION_EXCEPTIONS);
        _dataLagQueryDCLists = new ArrayList<>(Arrays.asList(sysConfig.getValue(SystemConfiguration.Property.DATA_LAG_QUERY_DC_LIST).split(",")));
        _dataLagQueryDCListsWithExceptions = new ArrayList<>(Arrays.asList(sysConfig.getValue(SystemConfiguration.Property.DATA_LAG_QUERY_DC_LIST_EXCEPTIONS).split(",")));
        _dataLagThreshold = Long.valueOf(sysConfig.getValue(com.salesforce.dva.argus.system.SystemConfiguration.Property.DATA_LAG_THRESHOLD));
		_dataLagNotificationEmailId = sysConfig.getValue(com.salesforce.dva.argus.system.SystemConfiguration.Property.DATA_LAG_NOTIFICATION_EMAIL_ADDRESS);
		_hostName = sysConfig.getHostname();
		initExpressionList(_dataLagQueryDCLists, _dataLagQueryExpressions);
        initExpressionList(_dataLagQueryDCListsWithExceptions, _dataLagQueryExpressionsWithExceptions);
        _logger.info("Data lag monitor initialized");
	}

	private void initExpressionList(ArrayList<String> dcList, String expression) {
        for(String currentDC: dcList) {
            _expressionPerDC.put(currentDC, expression.replace("#DC#",currentDC));
            _isDataLagging.put(currentDC, false);
        }
    }

	@Override
	public void run() {
		_logger.info("Data lag monitor thread started");
        for (String currentDC: _expressionPerDC.keySet()) {
            while (!isInterrupted()) {
                try {
                    sleep(SLEEP_INTERVAL_MILLIS);
                    long currTime = System.currentTimeMillis();
                    List<Metric> metrics = _metricService.getMetrics(_expressionPerDC.get(currentDC), currTime);
                    boolean isDataLagging = _isDataLagging.get(currentDC);
                    if (metrics == null || metrics.isEmpty()) {
                        _logger.info("Data lag detected as metric list is empty");
                        if (!isDataLagging) {
                            _isDataLagging.put(currentDC, true);
                            sendDataLagEmailNotification(currentDC);
                        }
                        continue;
                    }

                    //assuming only one time series in result
                    Metric currMetric = metrics.get(0);
                    if (currMetric.getDatapoints() == null || currMetric.getDatapoints().size() == 0) {
                        _logger.info("Data lag detected as data point list is empty");
                        if (!isDataLagging) {
                            _isDataLagging.put(currentDC, true);
                            sendDataLagEmailNotification(currentDC);
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
                            _logger.info("Data lag detected as the last data point recieved is more than the data threshold of " + _dataLagThreshold + " ms");
                            if (!isDataLagging) {
                                _isDataLagging.put(currentDC, true);
                                sendDataLagEmailNotification(currentDC);
                            }
                            continue;
                        }
                    }
                    if (isDataLagging) {
                        _isDataLagging.put(currentDC, false);
                        sendDataLagEmailNotification(currentDC);
                    }
                } catch (Exception e) {
                    _logger.error("Exception thrown in data lag monitor thread - " + ExceptionUtils.getFullStackTrace(e));
                }
            }
		}
	}

	private void sendDataLagEmailNotification(String currentDC) {
		Set<String> emailAddresseses = new HashSet<String>();
		emailAddresseses.add(_dataLagNotificationEmailId);
		String subject = "";
		if(_isDataLagging.get(currentDC)) {
			subject = "Alert evaluation on host - "+ _hostName + " has been stopped due to metric data lag in datacenter " + currentDC;
		}else {
			subject = "Alert evaluation on host - "+ _hostName + " has been resumed as the metric data lag has cleared";
		}
		
		StringBuilder body = new StringBuilder();
		body.append(MessageFormat.format("<b>Evaluated metric expression:  </b> {0}<br/>", _expressionPerDC.get(currentDC)));
        body.append(MessageFormat.format("<b>Configured data lag threshold:  </b> {0}<br/>", _dataLagThreshold));
		
		_mailService.sendMessage(emailAddresseses, subject, body.toString(), "text/html; charset=utf-8", MailService.Priority.NORMAL);
	}

	public boolean isDataLagging(String currentDC) {
		return _isDataLagging.get(currentDC);
	}
}
